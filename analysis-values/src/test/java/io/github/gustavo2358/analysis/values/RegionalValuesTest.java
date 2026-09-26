package io.github.gustavo2358.analysis.values;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.analysis.query.*;
import io.github.gustavo2358.analysis.cfg.application.*;
import io.github.gustavo2358.analysis.cfg.extension.SemanticInterpreterRegistry;
import java.math.BigInteger;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static io.github.gustavo2358.analysis.values.ValuesFixtures.*;

class RegionalValuesTest {
    static final PublicationId P=new PublicationId("regional-values");
    static final UnitId U=new UnitId(P,"unit");
    static final StorageId R=new StorageId(P,"bytes");
    static final ObjectId WHOLE=new ObjectId(U,"whole"),PREFIX=new ObjectId(U,"prefix"),SUFFIX=new ObjectId(U,"suffix");
    static final Memory.Codec IBM=new Memory.ExtensionCodec("text.ebcdic.ibm1047","1",Types.known(Types.Builtin.TEXT));
    static Memory.ObjectDeclaration view(ObjectId id,int offset,int extent,Memory.Codec codec) {
        return new Memory.ObjectDeclaration(id,Optional.of("same display"),Types.known(Types.Builtin.TEXT),new Memory.ViewBinding(R,BigInteger.valueOf(offset),BigInteger.valueOf(extent),codec),Memory.Visibility.PRIVATE,origin(P),Evidence.CoverageStatus.MODELED,header(U,"metadata").precision());
    }
    static Publication regional(List<Sequence> sequences) {
        var base=publication(P,List.of(unit(U,List.of(entry(U,"entry","s0")),sequences,List.of(view(WHOLE,0,8,IBM),view(PREFIX,0,4,IBM),view(SUFFIX,4,4,IBM)))),
            List.of(new Memory.Region(new Memory.StorageHeader(R,Optional.of(U),Memory.Lifetime.PERSISTENT,Memory.Visibility.PRIVATE,origin(P)),Optional.of(BigInteger.valueOf(8)),Optional.empty())));
        return new Publication(P,base.airVersion(),new Capabilities.Manifest(List.of(Capabilities.MEMORY_REGIONS,Capabilities.IBM1047),List.of()),base.artifacts(),base.units(),base.storage(),base.resources(),base.artifactRelations(),base.origins(),base.coverage(),base.uncertainties(),base.premises());
    }
    static RegionalValuesAnalysis.Execution run(Publication p) {
        var admission=RegionalValuesAnalysis.prepare(session(p),StorageAnalysisMode.EXPERIMENTAL_PHYSICAL);assertEquals(RegionalValuesAnalysis.Status.ACCEPTED,admission.status(),admission.reason());
        return admission.analysis().orElseThrow().execute();
    }
    static RegionalValueFact at(RegionalValuesAnalysis.Execution execution,String operation,ObjectId object) {
        var q=new PointQuery<>(ProgramPoint.before(new EntryId(U,"entry"),new OperationId(U,operation)),object);
        var batch=execution.observe(List.of(q));assertEquals(ObservationBatch.Status.COMPLETE,batch.status());
        var observation=batch.observations().getFirst();assertEquals(ObservationBatch.QueryStatus.VALUE,observation.status());return observation.value();
    }
    static List<String> texts(RegionalValueFact fact){return fact.candidates().stream().map(Values.TextValue::value).toList();}
    @Test void missingStateIsUnknownAndUnreachableIsSeparate() {
        var p=regional(List.of(returning(U,"s0",List.of()),returning(U,"dead",List.of(assign(U,"dead-write",WHOLE,"XXXXXXXX")))));
        var execution=run(p);var initial=at(execution,"return-s0",WHOLE);
        assertTrue(initial.modelValueRemainder());assertEquals(List.of(),texts(initial));
        var dead=at(execution,"dead-write",WHOLE);assertEquals(ValueFact.Reachability.UNREACHABLE_IN_MODEL,dead.reachability());assertNull(dead.candidates());assertNull(dead.modelValueRemainder());
    }
    @Test void unknownPrefixPreservesKnownSuffixAndCannotReviveWholeCandidate() {
        var reason=new UncertaintyId(P,"unknown-prefix");var h=header(U,"erase-prefix");
        var havoc=new Operations.HavocMust(h,new Places.ObjectPlace(operand(h.id(),"destination",Operand.Role.VALUE_WRITE),PREFIX),reason);
        var p=regional(List.of(returning(U,"s0",List.of(assign(U,"write",WHOLE,"ABCDEFGH"),havoc))));
        p=replace(p,p.units(),p.coverage(),List.of(new Evidence.Uncertainty(reason,"ARBITRARY_REASON",List.of(Evidence.Dimension.VALUES),new Scopes.UnitScope(U),"unknown prefix",origin(P))),p.premises());
        var execution=run(p);assertEquals(List.of("EFGH"),texts(at(execution,"return-s0",SUFFIX)));assertFalse(at(execution,"return-s0",SUFFIX).modelValueRemainder());
        assertEquals(List.of(),texts(at(execution,"return-s0",WHOLE)));assertTrue(at(execution,"return-s0",WHOLE).modelValueRemainder());
        assertEquals(List.of("ABCDEFGH"),texts(at(execution,"erase-prefix",WHOLE)));
        var after=execution.observe(List.of(new PointQuery<>(ProgramPoint.after(new EntryId(U,"entry"),h.id()),SUFFIX))).observations().getFirst().value();assertEquals(List.of("EFGH"),texts(after));
    }
    @Test void composedWholeRetainsTheIndependentlyReadableHalves() {
        var execution=run(regional(List.of(returning(U,"s0",List.of(assign(U,"whole-write",WHOLE,"ABCDEFGH"),assign(U,"prefix-write",PREFIX,"WXYZ"))))));
        assertEquals(List.of("WXYZ"),texts(at(execution,"return-s0",PREFIX)));assertEquals(List.of("EFGH"),texts(at(execution,"return-s0",SUFFIX)));
        var whole=at(execution,"return-s0",WHOLE);assertFalse(whole.modelValueRemainder());assertEquals(List.of("WXYZEFGH"),texts(whole));
    }
    @Test void branchAndLoopSupportsConvergeWithoutPathHistories() {
        var b=branch(U,"s0","a","b");var a=jump(U,"a","join");var c=jump(U,"b","join");var join=branch(U,"join","s0","exit");
        var p=regional(List.of(b,new Sequence(a.label(),List.of(assign(U,"producer-a",WHOLE,"ABCDEFGH")),a.terminator(),origin(P)),
            new Sequence(c.label(),List.of(assign(U,"producer-b",WHOLE,"ABCDEFGH")),c.terminator(),origin(P)),join,returning(U,"exit",List.of())));
        var execution=run(p);var value=at(execution,"return-exit",WHOLE);assertEquals(List.of("ABCDEFGH"),texts(value));assertFalse(value.modelValueRemainder());
        assertEquals(Set.of("producer-a","producer-b"),new HashSet<>(value.candidateSupports().getFirst().producers().stream().map(s->s.evidence().localId()).toList()));
        assertTrue(execution.dataflow().metrics().nodesTransferred()<100);
        var unit=p.units().getFirst();var reversed=new ArrayList<>(unit.sequences());Collections.reverse(reversed);
        var reordered=replace(p,List.of(unit(U,unit.entries(),reversed,unit.objects())),p.coverage(),p.uncertainties(),p.premises());
        assertEquals(value,at(run(reordered),"return-exit",WHOLE));
    }
    @Test void sameBytesDifferentCodecsProduceDifferentText() {
        var p=regional(List.of(returning(U,"s0",List.of(assign(U,"write",WHOLE,"ABCDEFGH")))));
        var unit=p.units().getFirst();var ascii=new ObjectId(U,"ascii");var objects=new ArrayList<>(unit.objects());objects.add(view(ascii,0,8,Memory.AsciiText.INSTANCE));
        p=replace(p,List.of(unit(U,unit.entries(),unit.sequences(),objects)),p.coverage(),p.uncertainties(),p.premises());
        var execution=run(p);assertEquals(List.of("ABCDEFGH"),texts(at(execution,"return-s0",WHOLE)));
        var other=at(execution,"return-s0",ascii);assertTrue(other.modelValueRemainder());assertEquals(List.of(),texts(other));assertTrue(other.modelReasons().contains("INVALID_BYTES"));
    }
    @Test void identicalInitialConditionsRetainBothSupportsAndConflictsAreRefused() {
        var p=SupportSourceTest.seeded(false,false);var unit=p.units().getFirst();var entry=unit.entries().getFirst();var object=unit.objects().getFirst().id();
        var conditions=new ArrayList<Entries.InitialCondition>();
        for(int i=0;i<2;i++) {
            var owner=new EntryOwner(entry.id());
            var place=new Places.ObjectPlace(new Operand.Header(new OperandId(owner,"place-"+i),Operand.Role.VALUE_WRITE,origin(p.id())),object);
            var literal=new Expressions.Literal(new Operand.Header(new OperandId(owner,"literal-"+i),Operand.Role.VALUE_READ,origin(p.id())),new Values.TextValue("ABCDEFGH"));
            conditions.add(new Entries.InitialCondition(place,new Entries.LiteralInitial(literal),origin(p.id()),List.of()));
        }
        var seeded=new Entries.Entry(entry.id(),entry.initialLabel(),entry.signature(),new Entries.EntryState(conditions,List.of()),entry.origin());
        p=replace(p,List.of(unit(unit.id(),List.of(seeded),unit.sequences(),unit.objects())),p.coverage(),p.uncertainties(),p.premises());
        var value=run(p).observe(List.of(new PointQuery<>(ProgramPoint.entry(entry.id()),object))).observations().getFirst().value();assertEquals(List.of("ABCDEFGH"),texts(value));assertFalse(value.modelValueRemainder());
        assertEquals(Set.of("place-0","place-1"),new HashSet<>(value.candidateSupports().getFirst().producers().stream().map(support->support.evidence().localId()).toList()));
        var second=conditions.getLast();var literal=((Entries.LiteralInitial)second.value()).value();
        conditions.set(1,new Entries.InitialCondition(second.place(),new Entries.LiteralInitial(new Expressions.Literal(literal.header(),new Values.TextValue("XXXXXXXX"))),second.origin(),second.premises()));
        seeded=new Entries.Entry(entry.id(),entry.initialLabel(),entry.signature(),new Entries.EntryState(conditions,List.of()),entry.origin());
        p=replace(p,List.of(unit(unit.id(),List.of(seeded),unit.sequences(),unit.objects())),p.coverage(),p.uncertainties(),p.premises());
        assertEquals(CfgBuildResult.Status.INVALID_IR,new CfgBuildCoordinator(SemanticInterpreterRegistry.empty()).build(p,BuildOptions.defaults()).status());
    }
    @Test void scalarAndRegionAreIndependentWithoutSeparationPremise() {
        for(boolean separated:List.of(false,true)) {
            var cellId=new StorageId(P,"logical-cell");var object=new ObjectId(U,"cell-object");
            var p=regional(List.of(returning(U,"s0",List.of(assign(U,"cell-write",object,"CELL"),assign(U,"regional-write",WHOLE,"ABCDEFGH")))));
            var unit=p.units().getFirst();var objects=new ArrayList<>(unit.objects());
            objects.add(new Memory.ObjectDeclaration(object,Optional.of("same display"),Types.known(Types.Builtin.TEXT),new Memory.CellBinding(cellId),Memory.Visibility.PRIVATE,origin(P),Evidence.CoverageStatus.MODELED,header(U,"metadata").precision()));
            var storage=new ArrayList<>(p.storage());storage.add(new Memory.Cell(new Memory.StorageHeader(cellId,Optional.of(U),Memory.Lifetime.PERSISTENT,Memory.Visibility.PRIVATE,origin(P)),Types.known(Types.Builtin.TEXT)));
            var premises=separated?List.of(new Proofs.Premise(new PremiseId(P,"separate"),"manual contract","independent allocations",origin(P),new Proofs.DisjointStorage(List.of(R,cellId)))):List.<Proofs.Premise>of();
            p=new Publication(P,p.airVersion(),p.capabilities(),p.artifacts(),List.of(unit(U,unit.entries(),unit.sequences(),objects)),storage,p.resources(),p.artifactRelations(),p.origins(),p.coverage(),p.uncertainties(),premises);
            var execution=run(p);var logical=at(execution,"return-s0",object);assertEquals(List.of("CELL"),texts(logical));assertFalse(logical.modelValueRemainder());
            var regional=at(execution,"return-s0",WHOLE);assertEquals(List.of("ABCDEFGH"),texts(regional));assertFalse(regional.modelValueRemainder());
            assertTrue(regional.premises().isEmpty());
        }
    }
    @Test void wholeAreaCopyCapturesItsSourceBeforeLaterAssignment() {
        var destination=new StorageId(P,"copied-area");var copiedObject=new ObjectId(U,"copied-object");var h=header(U,"copy");
        java.util.function.BiFunction<String,Long,Expressions.Literal> integer=(name,value)->new Expressions.Literal(operand(h.id(),name,Operand.Role.ADDRESS_READ),new Values.IntValue(BigInteger.valueOf(value)));
        var sourceRange=new Memory.ByteRange(R,integer.apply("source-offset",0L),integer.apply("source-length",8L));
        var destinationRange=new Memory.ByteRange(destination,integer.apply("destination-offset",0L),integer.apply("destination-length",8L));
        var reads=new Scopes.WithinMemory(new Scopes.StorageMemory(List.of(R)));var writes=new Scopes.WithinMemory(new Scopes.StorageMemory(List.of(destination)));
        var fallback=new Envelopes.Envelope(new Envelopes.MemoryEnvelope(List.of(),reads,List.of(),writes,List.of()),new Control.ControlEnvelope(List.of(Control.ContinueAlternative.INSTANCE),Scopes.NoControl.INSTANCE),new Envelopes.DependencyEnvelope(List.of(),Scopes.NoResources.INSTANCE));
        var copy=new Operations.CopyBytes(h,destinationRange,sourceRange,BigInteger.valueOf(8),fallback);
        var p=regional(List.of(returning(U,"s0",List.of(assign(U,"source-producer",WHOLE,"ABCDEFGH"),copy,assign(U,"later-source",WHOLE,"XXXXXXXX")))));
        var unit=p.units().getFirst();var objects=new ArrayList<>(unit.objects());
        objects.add(new Memory.ObjectDeclaration(copiedObject,Optional.of("same display"),Types.known(Types.Builtin.TEXT),new Memory.ViewBinding(destination,BigInteger.ZERO,BigInteger.valueOf(8),IBM),Memory.Visibility.PRIVATE,origin(P),Evidence.CoverageStatus.MODELED,header(U,"metadata").precision()));
        var storage=new ArrayList<>(p.storage());storage.add(new Memory.Region(new Memory.StorageHeader(destination,Optional.of(U),Memory.Lifetime.PERSISTENT,Memory.Visibility.PRIVATE,origin(P)),Optional.of(BigInteger.valueOf(8)),Optional.empty()));
        p=new Publication(P,p.airVersion(),p.capabilities(),p.artifacts(),List.of(unit(U,unit.entries(),unit.sequences(),objects)),storage,p.resources(),p.artifactRelations(),p.origins(),p.coverage(),p.uncertainties(),List.of(new Proofs.Premise(new PremiseId(P,"separate"),"manual contract","independent allocations",origin(P),new Proofs.DisjointStorage(List.of(R,destination)))));
        var execution=run(p);var captured=at(execution,"return-s0",copiedObject);assertEquals(List.of("ABCDEFGH"),texts(captured));assertFalse(captured.modelValueRemainder());
        assertEquals(List.of("XXXXXXXX"),texts(at(execution,"return-s0",WHOLE)));
        assertEquals(List.of("source-producer"),captured.candidateSupports().getFirst().producers().stream().map(support->support.evidence().localId()).toList());
    }
    @Test void finiteConcreteOracleChecksFullWritesJoinsLoopsAndMissingBranches() {
        var random=new Random(817273);int queries=0;
        for(int trial=0;trial<48;trial++) {
            int n=5;String[] writes=new String[n];int[][] edges=new int[n][];var sequences=new ArrayList<Sequence>();
            for(int i=0;i<n;i++) {
                int pick=random.nextInt(4);writes[i]=pick==0?null:pick==1?"ABCDEFGH":pick==2?"PGM00001":"XXXXXXXX";
                edges[i]=i==n-1?new int[0]:random.nextBoolean()?new int[]{random.nextInt(n),random.nextInt(n)}:new int[]{random.nextInt(n)};
                var control=edges[i].length==0?returning(U,"s"+i,List.of()):edges[i].length==1?jump(U,"s"+i,"s"+edges[i][0]):branch(U,"s"+i,"s"+edges[i][0],"s"+edges[i][1]);
                sequences.add(new Sequence(control.label(),writes[i]==null?List.of():List.of(assign(U,"write-"+i,WHOLE,writes[i])),control.terminator(),origin(P)));
            }
            // Independent finite collecting semantics on whole strings. Null is an explicit entry unknown.
            var in=new ArrayList<Set<String>>();var out=new ArrayList<Set<String>>();for(int i=0;i<n;i++){in.add(new HashSet<>());out.add(new HashSet<>());}in.get(0).add(null);
            boolean changed;do {changed=false;for(int i=0;i<n;i++)if(!in.get(i).isEmpty()) {
                var next=new HashSet<String>();if(writes[i]==null)next.addAll(in.get(i));else next.add(writes[i]);
                changed|=out.get(i).addAll(next);for(int target:edges[i])changed|=in.get(target).addAll(next);
            }}while(changed);
            var execution=run(regional(sequences));
            for(int i=0;i<n;i++) {
                var value=at(execution,sequences.get(i).terminator().header().id().localId(),WHOLE);queries++;
                if(out.get(i).isEmpty())assertEquals(ValueFact.Reachability.UNREACHABLE_IN_MODEL,value.reachability());
                else {var expected=new TreeSet<String>();for(var text:out.get(i))if(text!=null)expected.add(text);
                    assertEquals(List.copyOf(expected),texts(value),"trial "+trial+" node "+i);assertEquals(out.get(i).contains(null),value.modelValueRemainder());}
            }
        }
        assertEquals(240,queries);
    }
}
