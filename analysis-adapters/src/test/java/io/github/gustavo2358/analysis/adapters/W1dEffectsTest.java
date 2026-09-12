package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.analysis.plan.*;
import io.github.gustavo2358.analysis.query.*;
import io.github.gustavo2358.analysis.solver.Direction;
import io.github.gustavo2358.analysis.values.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static io.github.gustavo2358.analysis.adapters.W1dBoundaryTest.*;

final class W1dEffectsTest {
    static final String PROFILE="scalar-text-effects@1";
    static Operations.Invoke invoke(Publication p) {
        return (Operations.Invoke)p.units().getFirst().sequences().stream().filter(s->s.terminator() instanceof Operations.Invoke).findFirst().orElseThrow().terminator();
    }
    static AnalysisKey key(Publication p) {
        return new AnalysisKey("PossibleValues","1",PROFILE,Direction.FORWARD,"FINITE_PROGRAM_TEXT_VALUES",Map.of(),p.units().getFirst().entries().getFirst().id());
    }
    static ObjectId subject(Publication p) {return ((Places.ObjectPlace)((Expressions.Read)((Interactions.ComputedTarget)invoke(p).target()).name()).place()).object();}
    static PointQuery<ObjectId> before(Publication p,OperationId operation) {return new PointQuery<>(ProgramPoint.before(key(p).entry(),operation),subject(p));}
    static ValueFact value(Publication p,OperationId operation) {
        var provider=new PossibleValuesProvider();assertTrue(provider.supports(key(p)),"new generic effects profile must be explicitly recognized");
        var prepared=provider.prepare(session(p),key(p));assertNull(prepared.refusal(),()->String.valueOf(prepared.refusal()));
        var run=prepared.execute();assertEquals(AnalysisOutcome.Status.STABLE,run.outcome().status());
        var observation=run.observe(List.of(before(p,operation))).batch().observations().getFirst();
        assertEquals(ObservationBatch.QueryStatus.VALUE,observation.status());return observation.value();
    }
    static Operations.Invoke replace(Operations.Invoke i,Interactions.EffectBound effects,Control.InvocationOutcomes outcomes) {
        return new Operations.Invoke(i.header(),i.action(),i.target(),i.arguments(),i.results(),i.signature(),i.effectOperands(),effects,outcomes,i.contract());
    }
    static Publication sequences(Publication p,List<Sequence> sequences,List<Entries.Entry> entries) {
        var u=p.units().getFirst();var unit=new io.github.gustavo2358.air.model.Unit(u.id(),u.containingUnit(),u.objects(),u.visibleObjects(),entries,sequences,u.completionPorts(),u.body(),u.bodyUnavailable(),u.coverage(),u.origin());
        return new Publication(p.id(),p.airVersion(),p.capabilities(),p.artifacts(),List.of(unit),p.storage(),p.resources(),p.artifactRelations(),p.origins(),p.coverage(),p.uncertainties(),p.premises());
    }
    static Publication effect(Publication p,Interactions.EffectBound bound) {
        return sequences(p,p.units().getFirst().sequences().stream().map(s->s.terminator() instanceof Operations.Invoke i?new Sequence(s.label(),s.instructions(),replace(i,bound,i.outcomes()),s.origin()):s).toList(),p.units().getFirst().entries());
    }
    static Publication loop(Publication p) {
        var u=p.units().getFirst();var call=u.sequences().stream().filter(s->s.terminator() instanceof Operations.Invoke).findFirst().orElseThrow();var i=invoke(p);
        var entry=u.entries().getFirst();var prefix=new LabelId(u.id(),"prefix");
        var header=new Operations.Header(new OperationId(u.id(),"jump-to-call"),i.header().origin(),i.header().coverage(),i.header().precision(),i.header().uncertainties());
        var start=new Sequence(prefix,call.instructions(),new Operations.Jump(header,call.label()),call.origin());
        var self=new Sequence(call.label(),List.of(),replace(i,i.effectBound(),new Control.InvocationOutcomes(List.of(new Control.Normal(call.label())),i.outcomes().remainder())),call.origin());
        var e=new Entries.Entry(entry.id(),Optional.of(prefix),entry.signature(),entry.state(),entry.origin());
        var all=new ArrayList<Sequence>();all.add(self);all.add(start);u.sequences().stream().filter(s->s!=call).forEach(all::add);
        return sequences(p,all,List.of(e));
    }
    @Test void beforeRealInvokeIsClosedPaddedValueWithAssignSupport() throws Exception {
        var p=input("dynamic-x8");var fact=value(p,invoke(p).header().id());
        assertEquals(List.of(new Values.TextValue("PROGA   ")),fact.candidates());assertFalse(fact.modelValueRemainder());assertTrue(fact.sourceUnknownRemainder());
        var assign=p.units().getFirst().sequences().stream().flatMap(s->s.instructions().stream()).filter(Operations.Assign.class::isInstance).findFirst().orElseThrow();
        assertEquals(assign.header().id(),fact.candidateSupports().getFirst().producers().getFirst().evidence());
        assertEquals(assign.header().origin(),fact.candidateSupports().getFirst().producers().getFirst().origin());
    }
    @Test void mayWritePreservesCandidateAndOpensNormalContinuation() throws Exception {
        var p=input("dynamic-x8");var after=p.units().getFirst().sequences().stream().filter(s->s.terminator() instanceof Operations.Return).findFirst().orElseThrow().terminator();
        var fact=value(p,after.header().id());assertEquals(List.of(new Values.TextValue("PROGA   ")),fact.candidates());assertTrue(fact.modelValueRemainder());
    }
    @Test void loopBeforeInvokeContainsPreviousEffectAtFixpoint() throws Exception {
        var p=loop(input("dynamic-x8"));var fact=value(p,invoke(p).header().id());
        assertEquals(List.of(new Values.TextValue("PROGA   ")),fact.candidates());assertTrue(fact.modelValueRemainder());assertEquals(1,fact.candidateSupports().size());
    }
    @Test void explicitNoMemoryDoesNotOpenLoopValue() throws Exception {
        var p=loop(input("dynamic-x8"));var bound=new Interactions.EffectBound(new Interactions.ForeignEffects(Scopes.NoMemory.INSTANCE,Scopes.NoMemory.INSTANCE,List.of()),List.of());
        p=effect(p,bound);var fact=value(p,invoke(p).header().id());assertFalse(fact.modelValueRemainder());assertEquals(List.of(new Values.TextValue("PROGA   ")),fact.candidates());
    }
    @Test void profilesAreDistinctAndUnknownProfileIsRejected() throws Exception {
        var p=input("dynamic-x8");assertNotEquals(PossibleValuesProvider.key(key(p).entry()),key(p));
        var k=key(p);assertFalse(new PossibleValuesProvider().supports(new AnalysisKey(k.implementation(),k.version(),"missing@1",k.direction(),k.precisionPolicy(),k.options(),k.entry())));
    }
    @Test void perOutcomeEffectsAreExplicitlyUnsupported() throws Exception {
        var p=input("dynamic-x8");var f=invoke(p).effectBound().otherwise();p=effect(p,new Interactions.EffectBound(f,List.of(new Interactions.OutcomeEffects(Control.NormalOutcome.INSTANCE,f))));
        var provider=new PossibleValuesProvider();assertTrue(provider.supports(key(p)));var prepared=provider.prepare(session(p),key(p));assertNotNull(prepared.refusal());assertEquals("UNSUPPORTED_EFFECT_PROFILE",prepared.refusal().reason());
    }
    @Test void narrowerMemoryScopeIsRefusedRatherThanSilentlyIgnored() throws Exception {
        var p=input("dynamic-x8");var f=invoke(p).effectBound().otherwise();
        p=effect(p,new Interactions.EffectBound(new Interactions.ForeignEffects(f.reads(),new Scopes.WithinMemory(new Scopes.ObjectsMemory(List.of(subject(p)))),List.of()),List.of()));
        assertEquals("UNSUPPORTED_EFFECT_PROFILE",new PossibleValuesProvider().prepare(session(p),key(p)).refusal().reason());
    }
    @Test void mustOverwriteOutsideSliceHasExplicitRefusal() throws Exception {
        var p=input("dynamic-x8");var i=invoke(p);var f=i.effectBound().otherwise();
        var place=new Places.ObjectPlace(new Operand.Header(new OperandId(new OperationOwner(i.header().id()),"effect-write"),Operand.Role.VALUE_WRITE,i.header().origin()),subject(p));
        var next=new Operations.Invoke(i.header(),i.action(),i.target(),i.arguments(),i.results(),i.signature(),List.of(place),new Interactions.EffectBound(new Interactions.ForeignEffects(f.reads(),f.writes(),List.of(place.header().id())),List.of()),i.outcomes(),i.contract());
        p=sequences(p,p.units().getFirst().sequences().stream().map(s->s.terminator()==i?new Sequence(s.label(),s.instructions(),next,s.origin()):s).toList(),p.units().getFirst().entries());
        assertEquals("UNSUPPORTED_EFFECT_PROFILE",new PossibleValuesProvider().prepare(session(p),key(p)).refusal().reason());
    }
}
