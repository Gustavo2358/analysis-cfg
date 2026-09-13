package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.air.json.AirJson;
import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.analysis.dataflow.*;
import io.github.gustavo2358.analysis.query.*;
import io.github.gustavo2358.analysis.storage.*;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.math.BigInteger;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static io.github.gustavo2358.analysis.adapters.ResultFixtures.*;

class RegionalWireTest {
    static final PublicationId P=new PublicationId("regional-wire");static final UnitId U=new UnitId(P,"unit");
    static final StorageId R=new StorageId(P,"region"),Y=new StorageId(P,"copy-region");
    static final ObjectId WHOLE=new ObjectId(U,"whole"),PREFIX=new ObjectId(U,"prefix"),DEST=new ObjectId(U,"dest");
    static Memory.ObjectDeclaration view(ObjectId id,StorageId storage,int offset,int length) {
        return new Memory.ObjectDeclaration(id,Optional.empty(),Types.known(Types.Builtin.TEXT),new Memory.ViewBinding(storage,BigInteger.valueOf(offset),BigInteger.valueOf(length),Memory.AsciiText.INSTANCE),Memory.Visibility.PRIVATE,origin(P),Evidence.CoverageStatus.MODELED,header(U,"meta").precision());
    }
    static Publication fixture() {
        var h=header(U,"copy");java.util.function.BiFunction<String,Integer,Expression> integer=(name,n)->new Expressions.Literal(operand(h.id(),name,Operand.Role.ADDRESS_READ),new Values.IntValue(BigInteger.valueOf(n)));
        var bounds=new Envelopes.Envelope(new Envelopes.MemoryEnvelope(List.of(),new Scopes.WithinMemory(new Scopes.StorageMemory(List.of(R))),List.of(),new Scopes.WithinMemory(new Scopes.StorageMemory(List.of(Y))),List.of()),new Control.ControlEnvelope(List.of(Control.ContinueAlternative.INSTANCE),Scopes.NoControl.INSTANCE),new Envelopes.DependencyEnvelope(List.of(),Scopes.NoResources.INSTANCE));
        var copy=new Operations.CopyBytes(h,new Memory.ByteRange(Y,integer.apply("d0",0),integer.apply("d8",8)),new Memory.ByteRange(R,integer.apply("s0",0),integer.apply("s8",8)),BigInteger.valueOf(8),bounds);
        var sequences=List.of(returning(U,"body",List.of(assign(U,"old",WHOLE,"ABCDEFGH"),assign(U,"prefix",PREFIX,"WXYZ"),copy,assign(U,"late",WHOLE,"XXXXXXXX"))),returning(U,"dead",List.of()));
        var unit=unit(U,List.of(entry(U,"entry","body")),sequences,List.of(view(WHOLE,R,0,8),view(PREFIX,R,0,4),view(DEST,Y,0,8)));
        var storage=List.<Memory.Storage>of(new Memory.Region(new Memory.StorageHeader(R,Optional.of(U),Memory.Lifetime.PERSISTENT,Memory.Visibility.PRIVATE,origin(P)),Optional.of(BigInteger.valueOf(8)),Optional.empty()),new Memory.Region(new Memory.StorageHeader(Y,Optional.of(U),Memory.Lifetime.PERSISTENT,Memory.Visibility.PRIVATE,origin(P)),Optional.of(BigInteger.valueOf(8)),Optional.empty()));
        var p=publication(P,List.of(unit),storage);var artifact=new ArtifactId(P,"manual-fixture");
        return new Publication(P,p.airVersion(),new Capabilities.Manifest(List.of(Capabilities.MEMORY_REGIONS),List.of(Capabilities.MEMORY_REGIONS)),List.of(new Origins.Artifact(artifact,"RegionalWireTest.java",Optional.empty())),p.units(),p.storage(),p.resources(),p.artifactRelations(),List.of(new Origins.Written(origin(P),artifact,Optional.empty(),List.of(),true)),p.coverage(),p.uncertainties(),List.of(new Proofs.Premise(new PremiseId(P,"disjoint"),"manual oracle","separate allocations",origin(P),new Proofs.DisjointStorage(List.of(R,Y)))));
    }
    static List<PointQuery<StorageSubject>> queries() {
        var subject=new StorageSubject.PhysicalRange(Y,StorageRange.exact(BigInteger.ZERO,BigInteger.valueOf(8)),Memory.AsciiText.INSTANCE);var e=new EntryId(U,"entry");
        return List.of(new PointQuery<>(ProgramPoint.before(e,new OperationId(U,"return-body")),subject),new PointQuery<>(ProgramPoint.before(e,new OperationId(U,"return-dead")),subject),new PointQuery<>(ProgramPoint.after(e,new OperationId(U,"return-body")),subject));
    }
    static byte[] encode(RegionalAnalysisResult result) throws IOException {var out=new ByteArrayOutputStream();new RegionalResultJson().write(result,out);return out.toByteArray();}
    @Test void fileProductCarriesIndependentCompositionAndCopyIntervals() throws Exception {
        var p=fixture();var codec=new AirJson();var restored=codec.decode(codec.encode(p));assertEquals(p,restored);
        var result=new RegionalAnalysis().prepare(restored,"manual-regional",queries());var bytes=encode(result);
        var out=Path.of("target/regional-wire");Files.createDirectories(out);Files.write(out.resolve("manual.air.json"),codec.encode(p));Files.write(out.resolve("manual.result.json"),bytes);
        var fact=result.observations().stream().filter(o->o.query().point().operation().localId().equals("return-body")&&o.values().value()!=null).findFirst().orElseThrow().values().value();
        assertEquals(List.of(new Values.TextValue("WXYZEFGH")),fact.candidates());assertFalse(fact.evidence().contains(new OperationId(U,"late")));
        assertArrayEquals(bytes,encode(new RegionalAnalysis().prepare(restored,"manual-regional",queries())));
    }
    @Test void inventoryAndQueryPermutationPreserveTheEntireWire() throws Exception {
        var p=fixture();var u=p.units().getFirst();var objects=new ArrayList<>(u.objects());Collections.reverse(objects);var sequences=new ArrayList<>(u.sequences());Collections.reverse(sequences);var storage=new ArrayList<>(p.storage());Collections.reverse(storage);
        var permuted=new Publication(P,p.airVersion(),p.capabilities(),p.artifacts(),List.of(unit(U,u.entries(),sequences,objects)),storage,p.resources(),p.artifactRelations(),p.origins(),p.coverage(),p.uncertainties(),p.premises());
        var queries=new ArrayList<>(queries());Collections.reverse(queries);
        assertArrayEquals(encode(new RegionalAnalysis().prepare(p,"stable",queries())),encode(new RegionalAnalysis().prepare(permuted,"stable",queries)));
    }
    @Test void hugeUnknownTailZeroAndLogicalCellsRetainTheirDistinctWireShapes() throws Exception {
        var huge=new StorageId(P,"huge");var open=new StorageId(P,"open");var cell=new StorageId(P,"cell");var gap=new UncertaintyId(P,"extent-gap");var logical=new ObjectId(U,"logical");
        var objects=List.of(new Memory.ObjectDeclaration(logical,Optional.empty(),Types.known(Types.Builtin.TEXT),new Memory.CellBinding(cell),Memory.Visibility.PRIVATE,origin(P),Evidence.CoverageStatus.MODELED,header(U,"meta").precision()));
        var storage=List.<Memory.Storage>of(new Memory.Region(new Memory.StorageHeader(huge,Optional.of(U),Memory.Lifetime.PERSISTENT,Memory.Visibility.PRIVATE,origin(P)),Optional.of(BigInteger.ONE.shiftLeft(100)),Optional.empty()),
            new Memory.Region(new Memory.StorageHeader(open,Optional.of(U),Memory.Lifetime.PERSISTENT,Memory.Visibility.PRIVATE,origin(P)),Optional.empty(),Optional.of(gap)),
            new Memory.Cell(new Memory.StorageHeader(cell,Optional.of(U),Memory.Lifetime.ACTIVATION,Memory.Visibility.PRIVATE,origin(P)),Types.known(Types.Builtin.TEXT)));
        var base=publication(P,List.of(unit(U,List.of(entry(U,"entry","body")),List.of(returning(U,"body",List.of())),objects)),storage);
        var uncertainty=new Evidence.Uncertainty(gap,"OPEN_EXTENT",List.of(Evidence.Dimension.STORAGE),new Scopes.EntityScope(List.of(open)),"unproved extent",origin(P));
        var p=new Publication(P,base.airVersion(),new Capabilities.Manifest(List.of(Capabilities.MEMORY_REGIONS),List.of(Capabilities.MEMORY_REGIONS)),base.artifacts(),base.units(),storage,base.resources(),base.artifactRelations(),base.origins(),base.coverage(),List.of(uncertainty),base.premises());
        var point=ProgramPoint.before(new EntryId(U,"entry"),new OperationId(U,"return-body"));
        var queries=List.<StorageSubject>of(new StorageSubject.PhysicalRange(huge,StorageRange.exact(BigInteger.ONE.shiftLeft(90),BigInteger.valueOf(3)),Memory.AsciiText.INSTANCE),
            new StorageSubject.PhysicalRange(open,new StorageRange(BigInteger.ZERO,Optional.empty()),Memory.IdentityBytes.INSTANCE),
            new StorageSubject.PhysicalRange(huge,StorageRange.exact(BigInteger.ZERO,BigInteger.ZERO),Memory.AsciiText.INSTANCE),new StorageSubject.NamedObject(logical));
        var result=new RegionalAnalysis().prepare(p,"limits",queries.stream().map(s->new PointQuery<>(point,s)).toList());
        assertEquals(4,result.observations().size());assertEquals(1,result.observations().stream().filter(o->Boolean.FALSE.equals(o.values().value().modelValueRemainder())).count());
        var out=Path.of("target/regional-wire");Files.createDirectories(out);Files.write(out.resolve("limits.result.json"),encode(result));
    }

    static Set<Object> retained(Object root) throws ReflectiveOperationException {
        var seen=Collections.newSetFromMap(new IdentityHashMap<Object,Boolean>());var pending=new ArrayDeque<Object>();pending.add(root);
        while(!pending.isEmpty()) {
            var value=pending.removeLast();if(!seen.add(value))continue;var type=value.getClass();
            assertFalse(value instanceof Publication||value instanceof Operation||value instanceof Unit||value instanceof Sequence,"AIR graph retained: "+type);
            assertFalse(type.getName().startsWith("io.github.gustavo2358.analysis.structure.")||type.getName().startsWith("io.github.gustavo2358.analysis.solver.")
                ||type.getName().contains("RegionalValuesAnalysis")||type.getName().contains("ByteImage"),"execution/domain retained: "+type);
            if(value instanceof String||value instanceof Number||value instanceof Boolean||type.isEnum())continue;
            if(value instanceof Collection<?> collection) {pending.addAll(collection);continue;}
            if(value instanceof Map<?,?> map) {pending.addAll(map.keySet());pending.addAll(map.values());continue;}
            if(value instanceof Optional<?> optional) {optional.ifPresent(pending::add);continue;}
            assertTrue(type.isRecord(),"every retained non-leaf type must be inspected: "+type);
            for(var component:type.getRecordComponents()) {var nested=component.getAccessor().invoke(value);if(nested!=null)pending.add(nested);}
        }
        return seen;
    }
    @Test void detachedResultRetentionContainsOnlyValuesMetadataAndReferenceIds() throws Exception {
        var p=fixture();var result=new RegionalAnalysis().prepare(p,"retention",queries());var graph=retained(result);
        assertFalse(graph.contains(p));assertTrue(graph.stream().anyMatch(v->v instanceof io.github.gustavo2358.analysis.values.StorageValueFact.Capture));
        assertThrows(AssertionError.class,()->retained(List.of(result,p)),"detector must reject a deliberately retained publication");
        var before=encode(result);new RegionalAnalysis().prepare(fixture(),"unrelated",queries());assertArrayEquals(before,encode(result));
        System.out.println("W5_RETENTION resultObjects="+graph.size()+" forbiddenRoots=0 wireBytes="+before.length);
    }
    @Test void regionalLiteralSeedsRemainAnExplicitValidatorLimit() {
        var p=fixture();var unit=p.units().getFirst();var entries=new ArrayList<Entries.Entry>();
        for(int i=0;i<2;i++) {
            var e=entry(U,"seed-"+i,"body");var owner=new EntryOwner(e.id());
            var place=new Places.ObjectPlace(new Operand.Header(new OperandId(owner,"place"),Operand.Role.VALUE_WRITE,origin(P)),WHOLE);
            var literal=new Expressions.Literal(new Operand.Header(new OperandId(owner,"literal"),Operand.Role.VALUE_READ,origin(P)),new Values.TextValue(i==0?"AAAABBBB":"CCCCDDDD"));
            entries.add(new Entries.Entry(e.id(),e.initialLabel(),e.signature(),new Entries.EntryState(List.of(new Entries.InitialCondition(place,new Entries.LiteralInitial(literal),origin(P),List.of())),List.of()),e.origin()));
        }
        p=new Publication(P,p.airVersion(),p.capabilities(),p.artifacts(),List.of(unit(U,entries,unit.sequences(),unit.objects())),p.storage(),p.resources(),p.artifactRelations(),p.origins(),p.coverage(),p.uncertainties(),p.premises());
        var queries=entries.stream().map(e->new PointQuery<StorageSubject>(ProgramPoint.entry(e.id()),new StorageSubject.NamedObject(WHOLE))).toList();
        var input=p;
        var failure=assertThrows(AnalysisDataflow.PreparationException.class,()->new RegionalAnalysis().prepare(input,"seeds",queries));
        assertEquals(AnalysisDataflow.Failure.EXTERNAL_SIZE_CAP_DEBT,failure.failure());
        assertTrue(io.github.gustavo2358.air.validation.AirValidator.validate(p).issues().stream().anyMatch(issue->issue.detail().contains("overlapping region initializers")));
    }

    @Test void distinctEntryPathsKeepRegionalContentsAndReachabilitySeparate() {
        var p=fixture();var unit=p.units().getFirst();var entries=List.of(entry(U,"a","seed-a"),entry(U,"b","seed-b"));
        var sequences=new ArrayList<>(unit.sequences());
        for(int i=0;i<2;i++) {
            String label=i==0?"seed-a":"seed-b";var control=jump(U,label,"body");
            sequences.add(new Sequence(control.label(),List.of(assign(U,"write-"+label,WHOLE,i==0?"AAAABBBB":"CCCCDDDD")),control.terminator(),origin(P)));
        }
        p=new Publication(P,p.airVersion(),p.capabilities(),p.artifacts(),List.of(unit(U,entries,sequences,unit.objects())),p.storage(),p.resources(),p.artifactRelations(),p.origins(),p.coverage(),p.uncertainties(),p.premises());
        var queries=new ArrayList<PointQuery<StorageSubject>>();
        for(var e:entries)for(var operation:List.of("old","write-seed-a","write-seed-b"))queries.add(new PointQuery<>(ProgramPoint.before(e.id(),new OperationId(U,operation)),new StorageSubject.NamedObject(WHOLE)));
        var result=new RegionalAnalysis().prepare(p,"entry-paths",queries);
        for(var o:result.observations()) {
            var e=o.query().point().entry();var op=o.query().point().operation().localId();
            if(op.equals("old")) {
                assertEquals(List.of(new Values.TextValue(e.localId().equals("a")?"AAAABBBB":"CCCCDDDD")),o.values().value().candidates());
                assertEquals(e,o.rd().value().definitions().getFirst().definition().entry());assertFalse(o.values().value().modelValueRemainder());
            } else if(op.equals("write-seed-"+e.localId()))assertTrue(o.values().value().modelValueRemainder());
            else {assertNull(o.values().value().candidates());assertEquals(io.github.gustavo2358.analysis.values.ValueFact.Reachability.UNREACHABLE_IN_MODEL,o.values().value().reachability());}
        }
    }
}
