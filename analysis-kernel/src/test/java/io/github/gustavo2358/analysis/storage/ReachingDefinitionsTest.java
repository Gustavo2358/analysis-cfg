package io.github.gustavo2358.analysis.storage;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.analysis.query.*;
import io.github.gustavo2358.analysis.rd.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static io.github.gustavo2358.analysis.storage.StorageFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class ReachingDefinitionsTest {
    @Test void invocationTargetIsObservedBeforeNormalEffectsAndOutcomeReplay() {
        var text=Types.known(Types.Builtin.TEXT);var codec=new Memory.ExtensionCodec("text.ebcdic.ibm1047","1",text);
        var targetObject=new Memory.ObjectDeclaration(object("target"),Optional.empty(),text,new Memory.ViewBinding(base("r"),java.math.BigInteger.ZERO,java.math.BigInteger.valueOf(8),codec),Memory.Visibility.PRIVATE,O,Evidence.CoverageStatus.MODELED,header("m").precision());
        var targetPlace=new Places.ObjectPlace(operand("invoke","target-place",Operand.Role.VALUE_READ),object("target"));
        var target=new Expressions.Read(operand("invoke","target",Operand.Role.CALL_TARGET),targetPlace);
        var effectPlace=place("invoke","left");
        var normalEffects=new Interactions.ForeignEffects(Scopes.NoMemory.INSTANCE,new Scopes.WithinMemory(new Scopes.ObjectsMemory(List.of(object("left")))),List.of(effectPlace.header().id()));
        var defaultEffects=new Interactions.ForeignEffects(Scopes.NoMemory.INSTANCE,new Scopes.WithinMemory(new Scopes.AllMemory(P,true)),List.of());
        var invocation=new Operations.Invoke(header("invoke"),"call",new Interactions.ComputedTarget("program","test",target,Interactions.ExactName.INSTANCE,O),List.of(),List.of(),new Interactions.ExternalSignature(entry("e","s").signature()),List.of(effectPlace),new Interactions.EffectBound(defaultEffects,List.of(new Interactions.OutcomeEffects(Control.NormalOutcome.INSTANCE,normalEffects))),new Control.InvocationOutcomes(List.of(new Control.Normal(new LabelId(U,"done"))),Scopes.NoControl.INSTANCE),new Interactions.KnownContract(new Interactions.ContractRef("manual","1",List.of(O))));
        var p=publication(List.of(region("r",8L,Memory.Lifetime.ACTIVATION)),List.of(view("all","r",0,8),view("left","r",0,4),view("right","r",4,4),targetObject),List.of(new Sequence(new LabelId(U,"s"),List.of(assign("d1","all",193,194,195,196,197,198,199,200)),invocation,O),sequence("done",List.of())),List.of());
        p=new Publication(p.id(),p.airVersion(),new Capabilities.Manifest(List.of(Capabilities.MEMORY_REGIONS,Capabilities.IBM1047),List.of(Capabilities.MEMORY_REGIONS,Capabilities.IBM1047)),p.artifacts(),p.units(),p.storage(),p.resources(),p.artifactRelations(),p.origins(),p.coverage(),p.uncertainties(),p.premises());
        var effects=new StatementEffects(new StorageIndex(session(p)));assertEquals(StatementEffects.ReadKind.TARGET,effects.statement(invocation.header().id()).reads().getFirst().kind());assertTrue(effects.statement(invocation.header().id()).writes().isEmpty());
        var run=new ReachingDefinitions(effects).execute();assertEquals(Set.of("d1:0..8"),contributions(fact(run,before("invoke","target"))));
        var after=fact(run,before("return-done","all"));assertEquals(Set.of("invoke:0..4","d1:4..8"),contributions(after));assertTrue(after.unknownRemainder());
        assertFalse(fact(run,before("return-done","right")).unknownRemainder());
        var outcome=new PointQuery<>(new ProgramPoint(new EntryId(U,"main"),ProgramPoint.Kind.OUTCOME,invocation.header().id(),Control.NormalOutcome.INSTANCE),object("all"));
        assertEquals(after.definitions(),fact(run,outcome).definitions());
        var requests=List.of(outcome,new PointQuery<>(outcome.point(),object("right")),outcome);
        var batch=run.observe(requests);assertEquals(2,batch.observations().size());assertEquals(2,batch.metrics().operationsReplayed());
    }
    @Test void entryDefinitionsAreSeededOnceAndStaySeparateAcrossContexts() {
        var cell=new Memory.Cell(new Memory.StorageHeader(base("r"),Optional.of(U),Memory.Lifetime.ACTIVATION,Memory.Visibility.PRIVATE,O),BYTES);
        var p=publication(List.of(cell),List.of(declaration("all",new Memory.CellBinding(base("r")))),List.of(sequence("s",List.of(assign("d1","all",87,88,89,90)))),List.of());
        p=withEntries(p,List.of(seeded("one","s","all",65,66,67,68,69,70,71,72),seeded("two","s","all",49,50,51,52,53,54,55,56)));
        var run=new ReachingDefinitions(new StatementEffects(new StorageIndex(session(p)))).execute();
        for(String id:List.of("one","two")) {
            var q=new PointQuery<>(ProgramPoint.before(new EntryId(U,id),new OperationId(U,"d1")),object("all"));var answer=fact(run,q);
            assertEquals(1,answer.definitions().size());assertEquals(DefinitionEvent.Kind.INITIAL_CONDITION,answer.definitions().getFirst().definition().kind());assertFalse(answer.unknownRemainder());
            assertTrue(answer.definitions().getFirst().contributedRanges().getFirst().location().range().isEmpty());
            assertTrue(answer.definitions().stream().allMatch(c->c.definition().entry().equals(q.point().entry())));
            assertEquals(ReachingDefinitions.PROFILE,run.key(q.point().entry()).profile());
        }
    }
    @Test void regionalLiteralInitializerRemainsExplicitValidatorLimit() {
        var p=publication(List.of(region("r",8L,Memory.Lifetime.ACTIVATION)),List.of(view("all","r",0,8)),List.of(sequence("s",List.of())),List.of());
        p=withEntries(p,List.of(seeded("one","s","all",65,66,67,68,69,70,71,72)));
        var result=new io.github.gustavo2358.analysis.cfg.application.CfgBuildCoordinator(io.github.gustavo2358.analysis.cfg.extension.SemanticInterpreterRegistry.empty()).build(p,io.github.gustavo2358.analysis.cfg.application.BuildOptions.defaults());
        assertEquals(io.github.gustavo2358.analysis.cfg.application.CfgBuildResult.Status.VALIDATION_LIMIT,result.status());assertTrue(result.graph().isEmpty());
    }
    static ReachingDefinitions.Execution execution(List<Instruction> instructions) {
        var p=publication(List.of(region("r",8L,Memory.Lifetime.ACTIVATION)),List.of(view("all","r",0,8),view("left","r",0,4),view("right","r",4,4),declaration("alias",new Memory.AliasBinding(object("all")))),List.of(sequence("s",instructions)),List.of());
        return new ReachingDefinitions(new StatementEffects(new StorageIndex(session(p)))).execute();
    }
    static PointQuery<ObjectId> before(String operation,String subject) { return new PointQuery<>(ProgramPoint.before(new EntryId(U,"main"),new OperationId(U,operation)),object(subject)); }
    static PointQuery<ObjectId> after(String operation,String subject) { return new PointQuery<>(ProgramPoint.after(new EntryId(U,"main"),new OperationId(U,operation)),object(subject)); }
    static DefinitionFact fact(ReachingDefinitions.Execution execution,PointQuery<ObjectId> query) {
        var batch=execution.observe(List.of(query));assertEquals(ObservationBatch.Status.COMPLETE,batch.status());
        var answer=batch.observations().getFirst();assertEquals(ObservationBatch.QueryStatus.VALUE,answer.status());return answer.value();
    }
    static Set<String> contributions(DefinitionFact fact) {
        var result=new TreeSet<String>();for(var c:fact.definitions())for(var location:c.contributedRanges()) {
            var event=c.definition();var name=event.operation().map(OperationId::localId).orElse("ENTRY");
            var range=location.location().range().orElseThrow();result.add(name+":"+range.start()+".."+range.end().map(Object::toString).orElse("unknown"));
        }
        return result;
    }
    @Test void essentialPartialKillHasIndependentManualContributedRanges() {
        var run=execution(List.of(assign("d1","all",65,66,67,68,69,70,71,72),assign("d2","left",87,88,89,90)));
        var answer=fact(run,after("d2","all"));
        assertEquals(Set.of("d2:0..4","d1:4..8"),contributions(answer));assertFalse(answer.unknownRemainder());
        assertEquals(answer.definitions(),fact(run,after("d2","alias")).definitions());
        assertEquals(Set.of("d1:0..8"),contributions(fact(run,before("d2","all"))));
        assertTrue(answer.definitions().stream().allMatch(c->c.definition().origin().equals(O)&&c.definition().entry().equals(new EntryId(U,"main"))));
        System.out.println("ST_RD_MANUAL "+answer);
    }
    @Test void mandatoryUnknownPartialKillsOnlyItsRangeAndMayRetainsOldDefinition() {
        for(boolean must:List.of(true,false)) {
            Instruction effect=must?new Operations.HavocMust(header("effect"),place("effect","left"),UNKNOWN):new Operations.HavocMay(header("effect"),new Scopes.ObjectsMemory(List.of(object("left"))),UNKNOWN);
            var run=execution(List.of(assign("d1","all",65,66,67,68,69,70,71,72),effect));
            var answer=fact(run,after("effect","all"));assertTrue(answer.unknownRemainder());
            assertEquals(must?Set.of("effect:0..4","d1:4..8"):Set.of("effect:0..4","d1:0..8"),contributions(answer));
            assertFalse(fact(run,after("effect","right")).unknownRemainder());
        }
    }
    @Test void reachedUnknownAndEmptyBatchRemainExplicitAndReplayIsShared() {
        var run=execution(List.of(assign("d1","left",65,66,67,68)));
        var prior=fact(run,before("d1","all"));assertTrue(prior.unknownRemainder());assertEquals(Set.of("ENTRY:0..8"),contributions(prior));
        assertEquals(DefinitionFact.Reachability.REACHABLE,prior.reachability());
        var query=after("d1","all");var batch=run.observe(List.of(query,query,after("d1","left")));
        assertEquals(2,batch.observations().size());assertEquals(1,batch.metrics().operationsReplayed());
        var empty=run.observe(List.of());assertEquals(ObservationBatch.Status.COMPLETE,empty.status());assertTrue(empty.observations().isEmpty());
        var unsupported=run.observe(List.of(after("return-s","all")));assertEquals(ObservationBatch.QueryStatus.UNSUPPORTED_POINT,unsupported.observations().getFirst().status());
    }
}
