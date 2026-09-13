package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.air.json.AirJson;
import io.github.gustavo2358.air.json.AirJsonException;
import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.air.validation.AirValidator;
import io.github.gustavo2358.air.validation.ValidationResult;
import io.github.gustavo2358.analysis.cfg.application.CfgBuildResult;
import io.github.gustavo2358.analysis.dependencies.DependencyAnalysis;
import io.github.gustavo2358.analysis.dependencies.DependencySiteFact;
import io.github.gustavo2358.analysis.values.PossibleValuesAnalysis;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static io.github.gustavo2358.analysis.adapters.ResultFixtures.*;

/** AIR conservative envelopes preserve useful dependency facts at their ProgramPoint. */
final class ConservativeOperationBoundaryTest {
    enum Form { MUST, MAY, OPAQUE_NORMAL, OPAQUE_OPEN }

    static Publication model(Form form) { return model(form,false); }
    static Publication model(Form form,boolean disjoint) {
        var base = transportableOrigin(W1dModelTest.model(disjoint?2:1, u -> {
            var object = new ObjectId(u, "object-0");
            return List.of(MultiCallModelTest.invoke(u, "start", List.of(assign(u, "seed", object, "PROGA   ")),
                    W1dModelTest.call(u, "before", "effect", object, false)),
                jump(u, "effect", "after"),
                MultiCallModelTest.invoke(u, "after", List.of(), W1dModelTest.call(u, "after", "end", object, false)),
                returning(u, "end", List.of()));
        }));
        var unit = base.units().getFirst(); var u = unit.id();
        var id = new OperationId(u, "unknown-effect"); var gap = new UncertaintyId(base.id(), "unknown-effect");
        var scope = new Scopes.EntityScope(List.of(id));
        var exact = new Evidence.Claim(scope, Evidence.PrecisionStatus.EXACT, List.of());
        var open = new Evidence.Claim(scope, Evidence.PrecisionStatus.OPEN, List.of(gap));
        var h = new Operations.Header(id, origin(base.id()), Evidence.CoverageStatus.ABSTRACTED,
            new Evidence.Precision(form == Form.OPAQUE_OPEN ? open : exact, exact, open, open, open), List.of(gap));
        var destination=unit.objects().get(disjoint?1:0).id();
        var memory = new Scopes.ObjectsMemory(List.of(destination));
        var effect = switch (form) {
            case MUST -> new Sequence(new LabelId(u, "effect"), List.of(new Operations.HavocMust(h,
                new Places.ObjectPlace(operand(id, "destination", Operand.Role.VALUE_WRITE), destination), gap)),
                new Operations.Jump(header(u, "effect-next"), new LabelId(u, "after")), origin(base.id()));
            case MAY -> new Sequence(new LabelId(u, "effect"), List.of(new Operations.HavocMay(h, memory, gap)),
                new Operations.Jump(header(u, "effect-next"), new LabelId(u, "after")), origin(base.id()));
            case OPAQUE_NORMAL, OPAQUE_OPEN -> new Sequence(new LabelId(u, "effect"), List.of(),
                new Operations.Opaque(h, "uninterpreted-metadata", List.of(), List.of(), new Envelopes.Envelope(
                    new Envelopes.MemoryEnvelope(List.of(), Scopes.NoMemory.INSTANCE, List.of(), new Scopes.WithinMemory(memory), List.of()),
                    form == Form.OPAQUE_NORMAL
                        ? new Control.ControlEnvelope(List.of(new Control.JumpAlternative(new LabelId(u, "after"))), Scopes.NoControl.INSTANCE)
                        : new Control.ControlEnvelope(List.of(), new Scopes.WithinControl(new Scopes.LabelsControl(List.of(new LabelId(u, "after"), new LabelId(u, "end"))))),
                    new Envelopes.DependencyEnvelope(List.of(), Scopes.AnyResource.INSTANCE))), origin(base.id()));
        };
        var sequences = new ArrayList<>(unit.sequences()); sequences.set(1, effect);
        var uncertainty = new Evidence.Uncertainty(gap, "UNMODELED_REGION",
            List.of(Evidence.Dimension.EFFECTS, Evidence.Dimension.VALUES, Evidence.Dimension.CONTROL, Evidence.Dimension.DEPENDENCIES),
            scope, "Only the declared envelope is known", origin(base.id()));
        return new Publication(base.id(), base.airVersion(), base.capabilities(), base.artifacts(),
            List.of(unit(u, unit.entries(), sequences, unit.objects())), base.storage(), base.resources(), base.artifactRelations(),
            base.origins(), base.coverage(), List.of(uncertainty), base.premises());
    }

    private static Publication transportableOrigin(Publication p) {
        var artifact = new ArtifactId(p.id(), "fixture");
        return new Publication(p.id(), p.airVersion(), p.capabilities(),
            List.of(new Origins.Artifact(artifact, "ConservativeOperationBoundaryTest.java", Optional.empty())),
            p.units(), p.storage(), p.resources(), p.artifactRelations(),
            List.of(new Origins.Written(origin(p.id()), artifact, Optional.empty(), List.of(), false)),
            p.coverage(), p.uncertainties(), p.premises());
    }

    private static void verify(Form form) {
        var p=model(form);var validation=AirValidator.validate(p);
        assertEquals(ValidationResult.Status.STRUCTURALLY_VALID,validation.status(),validation.toString());
        var codec=new AirJson();var bytes=codec.encode(p);
        assertArrayEquals(bytes,codec.encode(codec.decode(bytes)));
        var cfg=W1dBoundaryTest.build(p);assertEquals(CfgBuildResult.Status.CFG_BUILT,cfg.status(),cfg.toString());
        var result=new DependencyAnalysis().prepare(p);
        var before=site(result,"before");var after=site(result,"after");
        assertEquals(List.of("PROGA"),names(before));assertFalse(before.modelValueRemainder());
        assertFalse(before.sourceValueRemainder());assertFalse(before.openControlRemainder());
        assertEquals(DependencySiteFact.Reachability.REACHABLE,after.reachability());
        assertEquals(form==Form.MUST?List.of():List.of("PROGA"),names(after));
        assertTrue(after.modelValueRemainder());assertFalse(after.sourceValueRemainder());
        assertEquals(form==Form.OPAQUE_OPEN,after.openControlRemainder());
        var seq=new ArrayList<>(p.units().getFirst().sequences());Collections.reverse(seq);
        var reversed=W1dEffectsTest.sequences(p,seq,p.units().getFirst().entries());
        var again=new DependencyAnalysis().prepare(reversed);
        assertEquals(result.sites(),again.sites());assertEquals(result.edges(),again.edges());
    }
    private static DependencySiteFact site(io.github.gustavo2358.analysis.dependencies.DependencyResult r,String id) {
        return r.sites().stream().filter(s->s.operation().localId().equals(id)).findFirst().orElseThrow();
    }
    private static List<String> names(DependencySiteFact s) {return s.candidates().stream().map(DependencySiteFact.Candidate::referenceName).toList();}
    @Test void mandatoryWriteKillsOnlyTheOverwrittenValue() {verify(Form.MUST);}
    @Test void possibleWriteKeepsKnownCandidateAndOpensRemainder() {verify(Form.MAY);}
    @Test void opaqueAppliesDeclaredMemoryAndKnownContinuation() {verify(Form.OPAQUE_NORMAL);}
    @Test void boundedOpenControlReachesLaterSitesWithoutChangingEarlierFacts() {verify(Form.OPAQUE_OPEN);}

    @Test void provedDisjointWriteScopeDoesNotOpenUnrelatedTarget() {
        for(var form:List.of(Form.MUST,Form.MAY,Form.OPAQUE_NORMAL)) {
            var p=model(form,true);var r=new DependencyAnalysis().prepare(p);
            assertEquals(List.of("PROGA"),names(site(r,"before")));assertEquals(List.of("PROGA"),names(site(r,"after")));
            assertFalse(site(r,"after").modelValueRemainder());assertFalse(site(r,"after").sourceValueRemainder());
            var bytes=new AirJson().encode(p);assertArrayEquals(bytes,new AirJson().encode(new AirJson().decode(bytes)));
        }
    }

    @Test void opaqueKnownOperandWritesHonorMandatoryAndPossibleEffects() {
        for(boolean mandatory:List.of(false,true))for(boolean disjoint:List.of(false,true)) {
            var p=model(Form.OPAQUE_NORMAL,disjoint);var unit=p.units().getFirst();
            var sequences=new ArrayList<>(unit.sequences());var seq=sequences.get(1);var opaque=(Operations.Opaque)seq.terminator();
            var place=new Places.ObjectPlace(operand(opaque.header().id(),"known-destination",Operand.Role.VALUE_WRITE),unit.objects().get(disjoint?1:0).id());
            var writes=List.of(place.header().id());
            var memory=new Envelopes.MemoryEnvelope(List.of(),Scopes.NoMemory.INSTANCE,writes,Scopes.NoMemory.INSTANCE,mandatory?writes:List.of());
            sequences.set(1,new Sequence(seq.label(),List.of(),new Operations.Opaque(opaque.header(),"display cannot select effect",List.of(place),List.of(),
                new Envelopes.Envelope(memory,opaque.envelope().control(),opaque.envelope().dependencies())),seq.origin()));
            var changed=W1dEffectsTest.sequences(p,sequences,unit.entries());
            assertEquals(ValidationResult.Status.STRUCTURALLY_VALID,AirValidator.validate(changed).status());
            var bytes=new AirJson().encode(changed);assertArrayEquals(bytes,new AirJson().encode(new AirJson().decode(bytes)));
            var r=new DependencyAnalysis().prepare(changed);
            assertEquals(List.of("PROGA"),names(site(r,"before")));assertFalse(site(r,"before").modelValueRemainder());
            assertEquals(mandatory&&!disjoint?List.of():List.of("PROGA"),names(site(r,"after")));
            assertEquals(!disjoint,site(r,"after").modelValueRemainder());
        }
    }

    @Test void boundedControlHasMatchingForwardAndBackwardCursors() {
        var p=model(Form.OPAQUE_OPEN);var session=W1dBoundaryTest.session(p);var context=session.contexts().iterator().next();
        var nodes=W1dBoundaryTest.build(p).graph().orElseThrow().nodes();
        var forward=new HashSet<String>();var backward=new HashSet<String>();
        for(var raw:nodes) {
            var node=session.index().node(raw.id());
            for(boolean direction:List.of(true,false)) {
                var cursor=direction?context.successors(node):context.predecessors(node);
                while(cursor.advance())if(cursor.transition().kind()==io.github.gustavo2358.analysis.cfg.domain.CfgTransition.Kind.OPAQUE_UNKNOWN) {
                    var source=(io.github.gustavo2358.analysis.cfg.domain.CfgNode.SequenceNode)cursor.source().source();
                    var target=(io.github.gustavo2358.analysis.cfg.domain.CfgNode.SequenceNode)cursor.target().source();
                    (direction?forward:backward).add(source.source().label().localId()+"->"+target.source().label().localId());
                }
            }
        }
        assertEquals(Set.of("effect->after","effect->end"),forward);assertEquals(forward,backward);
    }

    @Test void preciseControlBeforeAddingRegionProvesBothCallTargets() {
        var p = transportableOrigin(W1dModelTest.model(1, u -> List.of(
            MultiCallModelTest.invoke(u, "start", List.of(assign(u, "seed", new ObjectId(u, "object-0"), "PROGA   ")),
                W1dModelTest.call(u, "before", "after", new ObjectId(u, "object-0"), false)),
            MultiCallModelTest.invoke(u, "after", List.of(), W1dModelTest.call(u, "after", "end", new ObjectId(u, "object-0"), false)),
            returning(u, "end", List.of()))));
        var bytes = new AirJson().encode(p);
        assertArrayEquals(bytes, new AirJson().encode(new AirJson().decode(bytes)));
        var result = new DependencyAnalysis().prepare(p);
        assertEquals(2, result.sites().size());
        for (var site : result.sites()) {
            assertEquals(DependencySiteFact.Reachability.REACHABLE, site.reachability());
            assertEquals(List.of("PROGA"), site.candidates().stream().map(DependencySiteFact.Candidate::referenceName).toList());
            assertFalse(site.effectiveUnknownRemainder());
        }
    }
}
