package io.github.gustavo2358.analysis.values;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.analysis.query.*;
import io.github.gustavo2358.analysis.solver.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static io.github.gustavo2358.analysis.values.ValuesFixtures.*;

class ValuesTest {
    @Test void auxiliaryIntegerCellsPreserveTextCandidatesAndRequireDisjointness() {
        var p=graph(new String[]{"PROGA"},new int[][]{{}},2,true,true);var u=p.units().getFirst();var old=u.objects().get(1);
        var objects=new ArrayList<>(u.objects());var integer=Types.known(Types.Builtin.INT);
        objects.set(1,new Memory.ObjectDeclaration(old.id(),old.displayName(),integer,old.storage(),old.visibility(),old.origin(),old.coverage(),old.precision()));
        var storage=new ArrayList<>(p.storage());var cell=(Memory.Cell)storage.get(1);storage.set(1,new Memory.Cell(cell.header(),integer));
        var gap=new UncertaintyId(p.id(),"integer-open-value");
        var uncertainty=new Evidence.Uncertainty(gap,"UNKNOWN_WRITE",List.of(Evidence.Dimension.VALUES,Evidence.Dimension.EFFECTS),new Scopes.UnitScope(u.id()),"unknown integer update",origin(p.id()));
        var h=header(u.id(),"integer-update");var exact=h.precision().control();var open=new Evidence.Claim(new Scopes.UnitScope(u.id()),Evidence.PrecisionStatus.OPEN,List.of(gap));
        var effectHeader=new Operations.Header(h.id(),h.origin(),Evidence.CoverageStatus.ABSTRACTED,new Evidence.Precision(exact,exact,open,open,exact),List.of(gap));
        var s=u.sequences().getFirst();
        for(boolean all:List.of(false,true)) {
            Instruction effect=all?new Operations.HavocMay(effectHeader,new Scopes.AllMemory(p.id(),false),gap):new Operations.HavocMust(effectHeader,new Places.ObjectPlace(operand(h.id(),"destination",Operand.Role.VALUE_WRITE),old.id()),gap);
            var instructions=new ArrayList<Instruction>(s.instructions());instructions.add(effect);
            var changed=new Publication(p.id(),p.airVersion(),p.capabilities(),p.artifacts(),List.of(unit(u.id(),u.entries(),List.of(new Sequence(s.label(),instructions,s.terminator(),s.origin())),objects)),storage,p.resources(),p.artifactRelations(),p.origins(),p.coverage(),List.of(uncertainty),p.premises());
            var run=execute(changed);expected(fact(run,before(changed,0,0)),all,"PROGA");
            assertEquals(ObservationBatch.PointReason.UNSUPPORTED_SUBJECT,run.observe(List.of(before(changed,0,1))).batch().observations().getFirst().reason());
            var noProof=replace(changed,changed.units(),changed.coverage(),changed.uncertainties(),List.of());
            assertEquals(PossibleValuesAnalysis.Status.UNSUPPORTED,PossibleValuesAnalysis.prepare(session(noProof)).status(),"auxiliary numeric storage still needs disjointness");
        }
    }
    static PointQuery<ObjectId> before(Publication p,int seq,int object) {
        var u=p.units().getFirst();return new PointQuery<>(ProgramPoint.before(u.entries().getFirst().id(),u.sequences().get(seq).terminator().header().id()),u.objects().get(object).id());
    }
    static ValueFact fact(PossibleValuesAnalysis.Execution run,PointQuery<ObjectId> q) {return run.observe(List.of(q)).batch().observations().getFirst().value();}
    static void expected(ValueFact fact,boolean open,String... values) {
        assertNotNull(fact);assertEquals(ValueFact.Reachability.REACHABLE,fact.reachability());
        assertEquals(new TreeSet<>(List.of(values)),new TreeSet<>(fact.candidates().stream().map(Values.TextValue::value).toList()));
        assertEquals(open,fact.modelValueRemainder());
    }
    @Test void realVerticalMixedBatchHasOneObservationPerLogicalQuery() {
        var p=graph(new String[]{"PROGA"},new int[][]{{}},1,false,false);var u=p.units().getFirst();var e=u.entries().getFirst().id();var object=u.objects().getFirst().id();var seq=u.sequences().getFirst();
        var entry=new PointQuery<>(ProgramPoint.entry(e),object);
        var before=new PointQuery<>(ProgramPoint.before(e,seq.instructions().getFirst().header().id()),object);
        var after=new PointQuery<>(ProgramPoint.after(e,seq.instructions().getFirst().header().id()),object);
        var ret=before(p,0,0);var afterRet=new PointQuery<>(new ProgramPoint(e,ProgramPoint.Kind.AFTER,seq.terminator().header().id(),null),object);
        var outcome=new PointQuery<>(new ProgramPoint(e,ProgramPoint.Kind.OUTCOME,seq.terminator().header().id(),Control.NormalOutcome.INSTANCE),object);
        var run=execute(p);var dataflow=run.dataflow();var metrics=dataflow.metrics();
        var queries=List.of(ret,afterRet,before,after,entry,outcome,ret,afterRet);
        var result=run.observe(queries);assertEquals(ObservationBatch.Status.COMPLETE,result.batch().status());
        var answers=new HashMap<PointQuery<ObjectId>,ObservationBatch.Observation<ObjectId,ValueFact>>();
        for(var answer:result.batch().observations())assertNull(answers.put(answer.query(),answer));
        assertEquals(new HashSet<>(queries),answers.keySet(),"mixed batch complete query coverage");
        expected(answers.get(before).value(),true);expected(answers.get(entry).value(),true);
        expected(answers.get(after).value(),false,"PROGA");expected(answers.get(ret).value(),false,"PROGA");
        assertFalse(answers.get(ret).value().sourceUnknownRemainder());assertFalse(answers.get(ret).value().effectiveUnknownRemainder());
        assertEquals(ObservationBatch.QueryStatus.UNSUPPORTED_POINT,answers.get(afterRet).status(),"after terminator unsupported");
        assertNull(answers.get(afterRet).value());assertEquals(ObservationBatch.PointReason.AFTER_TERMINATOR,answers.get(afterRet).reason());
        assertEquals(ObservationBatch.PointReason.OUTCOME_UNAVAILABLE,answers.get(outcome).reason());
        assertEquals(8,result.batch().metrics().queryRequests());assertEquals(6,result.batch().metrics().uniqueQueries());
        assertEquals(1,result.batch().metrics().sequencesReplayed());assertEquals(1,result.batch().metrics().operationsReplayed());
        assertSame(dataflow,run.dataflow());assertSame(metrics,dataflow.metrics(),"no solver rerun per query");
        for(Control.OutcomeKey key:List.of(new Control.ExceptionOutcome("tag:😀"),Control.OtherExceptionOutcome.INSTANCE,Control.HaltOutcome.INSTANCE,Control.DivergeOutcome.INSTANCE)) {
            var unavailable=new PointQuery<>(new ProgramPoint(e,ProgramPoint.Kind.AFTER,seq.instructions().getFirst().header().id(),key),object);
            assertEquals(ObservationBatch.PointReason.OUTCOME_UNAVAILABLE,run.observe(List.of(unavailable)).batch().observations().getFirst().reason());
        }
        var reversed=new ArrayList<>(queries);Collections.reverse(reversed);assertEquals(result.batch(),run.observe(reversed).batch());
    }
    @Test void partialSourceIsSeparateFromExactModelAndUnknownWitness() {
        var p=graph(new String[]{"PROGA"},new int[][]{{}},1,false,false);var q=before(p,0,0);
        var full=fact(execute(p),q);var partial=fact(execute(partial(p)),q);
        expected(full,false,"PROGA");expected(partial,false,"PROGA");
        assertFalse(full.sourceUnknownRemainder());assertTrue(partial.sourceUnknownRemainder());assertTrue(partial.effectiveUnknownRemainder());
        var unknown=graph(new String[]{null},new int[][]{{}},1,false,false);
        expected(fact(execute(unknown),before(unknown,0,0)),true);
    }
    @Test void diamondsMissingPathsStrongUpdatesAndOtherCells() {
        var p=graph(new String[]{null,"A","B",null},new int[][]{{1,2},{3},{3},{}},1,false,false);
        expected(fact(execute(p),before(p,3,0)),false,"A","B");
        p=graph(new String[]{null,"A",null,null},new int[][]{{1,2},{3},{3},{}},1,false,false);
        expected(fact(execute(p),before(p,3,0)),true,"A");
        p=graph(new String[]{"A","B"},new int[][]{{1},{}},1,false,false);
        expected(fact(execute(p),before(p,1,0)),false,"B");
        p=graph(new String[]{"A","B","C"},new int[][]{{1},{2},{}},2,true,true);
        var run=execute(p);expected(fact(run,before(p,2,0)),false,"C");expected(fact(run,before(p,2,1)),false,"B");
    }
    @Test void sameCellAliasesAndDisjointnessAreSemanticAdmission() {
        var p=graph(new String[]{"A","B"},new int[][]{{1},{}},2,false,false);var run=execute(p);
        expected(fact(run,before(p,1,0)),false,"B");expected(fact(run,before(p,1,1)),false,"B");
        var noProof=graph(new String[]{"A","B"},new int[][]{{1},{}},2,true,false);
        var refused=PossibleValuesAnalysis.prepare(session(noProof));assertEquals(PossibleValuesAnalysis.Status.UNSUPPORTED,refused.status(),"storage IDs do not prove disjointness");assertEquals(1,refused.unsupportedStorageProfiles());
        var three=graph(new String[]{"A","B","C"},new int[][]{{1},{2},{}},3,true,false);
        var cells=three.storage();var proofs=new ArrayList<Proofs.Premise>();
        for(int i=0;i<2;i++)proofs.add(new Proofs.Premise(new PremiseId(three.id(),"pair"+i),"fixture","pair only",origin(three.id()),new Proofs.DisjointStorage(List.of(cells.get(i).header().id(),cells.get(i+1).header().id()))));
        var fragmented=replace(three,three.units(),three.coverage(),three.uncertainties(),proofs);
        assertEquals(PossibleValuesAnalysis.Status.UNSUPPORTED,PossibleValuesAnalysis.prepare(session(fragmented)).status());
    }
    @Test void directReadIsCopyWhileOtherEffectsAndIndirectStorageRemainUnsupported() {
        var p=graph(new String[]{"A"},new int[][]{{}},1,false,false);var u=p.units().getFirst();var seq=u.sequences().getFirst();var assign=(Operations.Assign)seq.instructions().getFirst();
        var read=new Expressions.Read(operand(assign.header().id(),"read",Operand.Role.VALUE_READ),new Places.ObjectPlace(operand(assign.header().id(),"readplace",Operand.Role.VALUE_READ),u.objects().getFirst().id()));
        var changed=new Sequence(seq.label(),List.of(new Operations.Assign(assign.header(),assign.destination(),read)),seq.terminator(),seq.origin());
        var q=replace(p,List.of(unit(u.id(),u.entries(),List.of(changed),u.objects())),p.coverage(),p.uncertainties(),p.premises());
        var admitted=PossibleValuesAnalysis.prepare(session(q));assertEquals(PossibleValuesAnalysis.Status.ACCEPTED,admitted.status());
        expected(fact(admitted.analysis().orElseThrow().execute(),before(q,0,0)),true);
        var gap=new UncertaintyId(p.id(),"effects");
        var uncertainty=new Evidence.Uncertainty(gap,"UNKNOWN_WRITE",List.of(Evidence.Dimension.EFFECTS,Evidence.Dimension.VALUES),new Scopes.UnitScope(u.id()),"unmodeled write",origin(p.id()));
        var exact=header(u.id(),"metadata").precision().control();
        var open=new Evidence.Claim(new Scopes.UnitScope(u.id()),Evidence.PrecisionStatus.OPEN,List.of(gap));
        var precision=new Evidence.Precision(exact,exact,open,open,exact);
        var mustHeader=new Operations.Header(new OperationId(u.id(),"must"),origin(p.id()),Evidence.CoverageStatus.ABSTRACTED,precision,List.of(gap));
        var mayHeader=new Operations.Header(new OperationId(u.id(),"may"),origin(p.id()),Evidence.CoverageStatus.ABSTRACTED,precision,List.of(gap));
        for(Instruction effect:List.of(
                new Operations.HavocMust(mustHeader,new Places.ObjectPlace(operand(mustHeader.id(),"destination",Operand.Role.VALUE_WRITE),u.objects().getFirst().id()),gap),
                new Operations.HavocMay(mayHeader,new Scopes.ObjectsMemory(List.of(u.objects().getFirst().id())),gap))) {
            var effects=new Sequence(seq.label(),List.of(assign,effect),seq.terminator(),seq.origin());
            var effectPublication=replace(p,List.of(unit(u.id(),u.entries(),List.of(effects),u.objects())),p.coverage(),List.of(uncertainty),p.premises());
            var partial=PossibleValuesAnalysis.prepare(session(effectPublication));
            assertEquals(PossibleValuesAnalysis.Status.ACCEPTED,partial.status());
            var result=fact(partial.analysis().orElseThrow().execute(),before(effectPublication,0,0));
            if(effect instanceof Operations.HavocMust)expected(result,true);else expected(result,true,"A");
        }
        var regionId=new StorageId(p.id(),"region");
        var region=new Memory.Region(new Memory.StorageHeader(regionId,Optional.of(u.id()),Memory.Lifetime.ACTIVATION,Memory.Visibility.PRIVATE,origin(p.id())),Optional.of(java.math.BigInteger.TEN),Optional.empty());
        var copyHeader=header(u.id(),"copy");
        var bound=new Scopes.WithinMemory(new Scopes.StorageMemory(List.of(regionId)));
        var fallback=new Envelopes.Envelope(new Envelopes.MemoryEnvelope(List.of(),bound,List.of(),bound,List.of()),new Control.ControlEnvelope(List.of(Control.ContinueAlternative.INSTANCE),Scopes.NoControl.INSTANCE),new Envelopes.DependencyEnvelope(List.of(),Scopes.NoResources.INSTANCE));
        var copy=new Operations.CopyBytes(copyHeader,
            new Memory.ByteRange(regionId,integer(copyHeader.id(),"dst-offset",1),integer(copyHeader.id(),"dst-size",2)),
            new Memory.ByteRange(regionId,integer(copyHeader.id(),"src-offset",0),integer(copyHeader.id(),"src-size",2)),java.math.BigInteger.TWO,fallback);
        var storage=new ArrayList<Memory.Storage>(p.storage());storage.add(region);
        var copyPublication=new Publication(p.id(),p.airVersion(),new Capabilities.Manifest(List.of(Capabilities.MEMORY_REGIONS),List.of()),p.artifacts(),
            List.of(unit(u.id(),u.entries(),List.of(new Sequence(seq.label(),List.of(assign,copy),seq.terminator(),seq.origin())),u.objects())),storage,p.resources(),p.artifactRelations(),p.origins(),p.coverage(),p.uncertainties(),p.premises());
        assertEquals(PossibleValuesAnalysis.Status.UNSUPPORTED,PossibleValuesAnalysis.prepare(session(copyPublication)).status(),"unmodeled write cannot be identity");
        var original=u.objects().getFirst();var alias=new Memory.ObjectDeclaration(new ObjectId(u.id(),"alias"),Optional.of("WS-PGM"),original.typeRef(),new Memory.AliasBinding(original.id()),original.visibility(),original.origin(),original.coverage(),original.precision());
        q=replace(p,List.of(unit(u.id(),u.entries(),u.sequences(),List.of(original,alias))),p.coverage(),p.uncertainties(),p.premises());
        assertEquals(PossibleValuesAnalysis.Status.UNSUPPORTED,PossibleValuesAnalysis.prepare(session(q)).status());
    }
    private static Expressions.Literal integer(OperationId op,String id,int value) {
        return new Expressions.Literal(operand(op,id,Operand.Role.VALUE_READ),new Values.IntValue(java.math.BigInteger.valueOf(value)));
    }
    @Test void entrySeedsContextsUnreachableAndUnicodeKeepTheirIdentity() {
        var p=graph(new String[]{null,"orphan"},new int[][]{{},{}},1,false,false);var u=p.units().getFirst();var object=u.objects().getFirst().id();
        var entries=new ArrayList<Entries.Entry>();
        for(int i=0;i<2;i++) {
            var entry=entry(u.id(),"e"+i,"s0");var id=new OperandId(new EntryOwner(entry.id()),"seed");
            var literal=new Expressions.Literal(new Operand.Header(id,Operand.Role.VALUE_READ,origin(p.id())),new Values.TextValue(i==0?" A😀é ":"a😀é"));
            var place=new Places.ObjectPlace(new Operand.Header(new OperandId(new EntryOwner(entry.id()),"place"),Operand.Role.VALUE_WRITE,origin(p.id())),object);
            entries.add(new Entries.Entry(entry.id(),entry.initialLabel(),entry.signature(),new Entries.EntryState(List.of(new Entries.InitialCondition(place,new Entries.LiteralInitial(literal),origin(p.id()),List.of())),List.of()),entry.origin()));
        }
        p=replace(p,List.of(unit(u.id(),entries,u.sequences(),u.objects())),p.coverage(),p.uncertainties(),p.premises());var run=execute(p);
        for(int i=0;i<2;i++)expected(fact(run,new PointQuery<>(ProgramPoint.before(entries.get(i).id(),u.sequences().getFirst().terminator().header().id()),object)),false,i==0?" A😀é ":"a😀é");
        var orphan=fact(run,new PointQuery<>(ProgramPoint.before(entries.getFirst().id(),u.sequences().get(1).terminator().header().id()),object));
        assertEquals(ValueFact.Reachability.UNREACHABLE_IN_MODEL,orphan.reachability());assertNull(orphan.candidates());
        var foreign=new PointQuery<>(ProgramPoint.before(new EntryId(u.id(),"missing"),u.sequences().getFirst().terminator().header().id()),object);
        assertEquals(ObservationBatch.PointReason.CONTEXT_NOT_SELECTED,run.observe(List.of(foreign)).batch().observations().getFirst().reason());
    }
}
