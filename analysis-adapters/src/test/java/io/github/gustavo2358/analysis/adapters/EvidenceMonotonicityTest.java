package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.analysis.dependencies.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static io.github.gustavo2358.analysis.adapters.ResultFixtures.*;

/** Metamorphic obligations are defined by semantic evidence, never source keyword spelling. */
class EvidenceMonotonicityTest {
    static Publication baseline() {
        return W1dModelTest.model(1,u->List.of(
            MultiCallModelTest.invoke(u,"start",List.of(assign(u,"source-support",new ObjectId(u,"object-0"),"PROGA")),W1dModelTest.call(u,"computed","end",new ObjectId(u,"object-0"),false)),
            returning(u,"end",List.of())));
    }
    static Publication changed(Publication p,Operations.Invoke invoke) {
        var u=p.units().getFirst();var sequences=new ArrayList<>(u.sequences());var s=sequences.getFirst();
        sequences.set(0,new Sequence(s.label(),s.instructions(),invoke,s.origin()));
        return W1dEffectsTest.sequences(p,sequences,u.entries());
    }
    static void supported(Publication p) {
        var result=new DependencyAnalysis().prepare(p);var fact=result.sites().stream().filter(s->s.operation().localId().equals("computed")).findFirst().orElseThrow();
        assertEquals(List.of("PROGA"),fact.candidates().stream().map(DependencySiteFact.Candidate::referenceName).toList());
        assertTrue(fact.candidates().getFirst().supports().stream().anyMatch(s->s.producer().localId().equals("source-support")));
    }
    @Test void addingUnknownEffectCannotEraseIndependentBeforeValue() {
        var p=baseline();supported(p);var invoke=(Operations.Invoke)p.units().getFirst().sequences().getFirst().terminator();
        var effects=new Interactions.ForeignEffects(Scopes.NoMemory.INSTANCE,new Scopes.WithinMemory(new Scopes.VisibleMemory(invoke.header().id().unit(),true)),List.of());
        for(var bound:List.of(new Interactions.EffectBound(effects,List.of()),new Interactions.EffectBound(effects,List.of(new Interactions.OutcomeEffects(Control.NormalOutcome.INSTANCE,effects)))))
            supported(changed(p,W1dEffectsTest.replace(invoke,bound,invoke.outcomes())));
    }
    @Test void addingUnmodeledOutcomeCannotEraseBeforeSupport() {
        var p=baseline();supported(p);var invoke=(Operations.Invoke)p.units().getFirst().sequences().getFirst().terminator();
        supported(changed(p,W1dEffectsTest.replace(invoke,invoke.effectBound(),new Control.InvocationOutcomes(List.of(invoke.outcomes().known().getFirst(),Control.Diverge.INSTANCE),Scopes.NoControl.INSTANCE))));
    }
    @Test void addingUnknownDeclarationCannotEraseIndependentAssignedTarget() {
        var p=baseline();supported(p);var u=p.units().getFirst();var gap=new UncertaintyId(p.id(),"generic-unknown-storage");
        var declaration=new Memory.ObjectDeclaration(new ObjectId(u.id(),"arbitrary-partial-declaration"),Optional.empty(),Types.known(Types.Builtin.TEXT),new Memory.UnknownBinding(new Scopes.AllMemory(p.id(),true),gap),Memory.Visibility.UNKNOWN,origin(p.id()),Evidence.CoverageStatus.ABSTRACTED,header(u.id(),"meta").precision());
        var objects=new ArrayList<>(u.objects());objects.add(declaration);
        var changed=new Publication(p.id(),p.airVersion(),p.capabilities(),p.artifacts(),List.of(unit(u.id(),u.entries(),u.sequences(),objects)),p.storage(),p.resources(),p.artifactRelations(),p.origins(),p.coverage(),
            List.of(new Evidence.Uncertainty(gap,"EP_UNKNOWN",List.of(Evidence.Dimension.STORAGE),new Scopes.UnitScope(u.id()),"unproved storage",origin(p.id()))),p.premises());
        supported(changed);
    }
    @Test void unrelatedUnavailableUnitCannotEraseIndependentComputedAndLiteralSites() {
        var p=baseline();var good=p.units().getFirst();var all=new ArrayList<>(good.sequences());
        all.set(1,MultiCallModelTest.invoke(good.id(),"end",List.of(),MultiCallModelTest.literal(good.id(),"direct","DIRECT","done")));
        all.add(returning(good.id(),"done",List.of()));p=W1dEffectsTest.sequences(p,all,good.entries());supported(p);
        var id=new UnitId(p.id(),"unavailable-other-unit");var gap=new UncertaintyId(p.id(),"missing-input");var template=entry(id,"external-entry","no-body");
        var e=new Entries.Entry(template.id(),Optional.empty(),template.signature(),template.state(),template.origin());
        var coverage=new Evidence.Coverage(Evidence.InventoryStatus.PARTIAL,new Scopes.UnitScope(id),List.of(),List.of(gap));
        var unavailable=new Unit(id,Optional.empty(),List.of(),List.of(),List.of(e),List.of(),List.of(),Unit.BodyAvailability.UNAVAILABLE,Optional.of(gap),coverage,origin(p.id()));
        var units=new ArrayList<>(p.units());units.add(unavailable);
        var changed=new Publication(p.id(),p.airVersion(),p.capabilities(),p.artifacts(),units,p.storage(),p.resources(),p.artifactRelations(),p.origins(),p.coverage(),
            List.of(new Evidence.Uncertainty(gap,"EP_INPUT_GAP",List.of(Evidence.Dimension.CONTROL),new Scopes.UnitScope(id),"missing unrelated input",origin(p.id()))),p.premises());
        supported(changed);var result=new DependencyAnalysis().prepare(changed);
        assertTrue(result.edges().stream().anyMatch(edge->edge.candidate().referenceName().equals("DIRECT")));
        assertTrue(result.partial());
    }

}
