package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.air.json.AirJson;
import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.analysis.dependencies.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static io.github.gustavo2358.analysis.adapters.ResultFixtures.*;

/** CICS-W0: public AIR -> JSON -> CFG/index -> real dependency/value queries. */
class CicsInvokeRouteTest {
    static Publication route(boolean normal, boolean localError, boolean remainderLocal) {
        var base = W1dModelTest.model(1, u -> List.of(
            new Sequence(new LabelId(u,"start"),List.of(assign(u,"seed",new ObjectId(u,"object-0"),"BEFORE  ")),
                W1dModelTest.call(u,"interaction","next",null,false),origin(u.publication())),
            new Sequence(new LabelId(u,"next"),List.of(),W1dModelTest.call(u,"downstream","end",new ObjectId(u,"object-0"),false),origin(u.publication())),
            returning(u,"end",List.of())));
        var u=base.units().getFirst(); var first=u.sequences().getFirst(); var i=(Operations.Invoke)first.terminator();
        List<Control.InvocationAlternative> known=new ArrayList<>();
        if(normal)known.add(new Control.Normal(new LabelId(u.id(),"end")));
        Scopes.ControlScope scope=remainderLocal ? new Scopes.LabelsControl(List.of(new LabelId(u.id(),"next")))
            : new Scopes.UnitControl(u.id(),false,false,false,false,false,true);
        if(localError)scope=new Scopes.ControlUnion(List.of(scope,new Scopes.LabelsControl(List.of(new LabelId(u.id(),"next")))));
        var changed=new Operations.Invoke(i.header(),normal ? "call" : "execute",i.target(),i.arguments(),i.results(),i.signature(),i.effectOperands(),i.effectBound(),
            new Control.InvocationOutcomes(known,new Scopes.WithinControl(scope)),i.contract());
        var sequences=new ArrayList<>(u.sequences()); sequences.set(0,new Sequence(first.label(),first.instructions(),changed,first.origin()));
        var result=W1dEffectsTest.sequences(base,sequences,u.entries());
        return new Publication(result.id(),result.airVersion(),result.capabilities(),List.of(new Origins.Artifact(new ArtifactId(result.id(),"fixture"),"CICS route oracle",Optional.empty())),result.units(),result.storage(),result.resources(),result.artifactRelations(),List.of(new Origins.Written(origin(result.id()),new ArtifactId(result.id(),"fixture"),Optional.empty(),List.of(),true)),result.coverage(),result.uncertainties(),result.premises());
    }
    static DependencySiteFact downstream(Publication p) {
        var codec=new AirJson(); var transported=codec.decode(codec.encode(p)); assertEquals(p,transported);
        return new DependencyAnalysis().prepare(transported).sites().stream().filter(s->s.operation().localId().equals("downstream")).findFirst().orElseThrow();
    }
    static void possible(DependencySiteFact fact) {
        assertEquals(DependencySiteFact.Reachability.REACHABLE,fact.reachability());
        assertEquals(List.of("BEFORE"),fact.candidates().stream().map(DependencySiteFact.Candidate::referenceName).toList());
        assertEquals("seed",fact.candidates().getFirst().supports().getFirst().producer().localId());
    }
    @Test void knownNormalDoesNotEraseLocalRemainderFromQueries() { possible(downstream(route(true,false,true))); }
    @Test void emptyKnownWithLocalRemainderRemainsPossible() { possible(downstream(route(false,false,true))); }
    @Test void externalOnlyHasNoLocalContinuationInQueries() {
        var fact=downstream(route(false,false,false));
        assertEquals(DependencySiteFact.Reachability.UNREACHABLE_IN_MODEL,fact.reachability()); assertTrue(fact.candidates().isEmpty());
    }
    @Test void conditionCanContinueWithoutNormalSuccess() { possible(downstream(route(false,true,false))); }
    @Test void extensionPolicySurvivesTransportWithoutCobolSubstitution() {
        var p=route(false,false,false);var u=p.units().getFirst();var s=u.sequences().getFirst();var i=(Operations.Invoke)s.terminator();
        var target=new Interactions.LiteralTarget("program","cics.program","aBc",new Interactions.ExtensionName("cics-ts.program","1"),i.header().origin());
        var changed=new Operations.Invoke(i.header(),"execute",target,i.arguments(),i.results(),i.signature(),i.effectOperands(),i.effectBound(),i.outcomes(),i.contract());
        var seq=new ArrayList<>(u.sequences());seq.set(0,new Sequence(s.label(),s.instructions(),changed,s.origin()));
        p=W1dEffectsTest.sequences(p,seq,u.entries());
        p=new Publication(p.id(),p.airVersion(),new Capabilities.Manifest(List.of(new Capabilities.Capability("cics-ts.program","1")),List.of()),p.artifacts(),p.units(),p.storage(),p.resources(),p.artifactRelations(),p.origins(),p.coverage(),p.uncertainties(),p.premises());var codec=new AirJson();assertEquals(p,codec.decode(codec.encode(p)));
        var fact=new DependencyAnalysis().prepare(codec.decode(codec.encode(p))).sites().stream().filter(f->f.command().equals("XCTL")).findFirst().orElseThrow();
        assertEquals("cics.program",fact.namespace());assertEquals("cics-ts.program@1",fact.nameProfile());
        assertEquals(List.of("aBc"),fact.rawCandidates().stream().map(DependencySiteFact.RawCandidate::rawValue).toList());
        assertTrue(fact.candidates().isEmpty());assertTrue(fact.interpretationUnknownRemainder());
    }
    @Test void platformProfileAcceptsDigitAndPreservesUnsupportedSpelling() {
        var p=new Interactions.ExtensionName("cics-ts.program","1");
        assertEquals("1PGM",CicsNameInterpreter.interpret("1PGM    ",true,p).referenceName());
        assertNull(CallNameInterpreter.interpret("1PGM",false,p).referenceName());
        assertNull(CicsNameInterpreter.interpret("aBc",false,p).referenceName());
        assertNull(CicsNameInterpreter.interpret("SHORT",true,p).referenceName());
        assertNull(CicsNameInterpreter.interpret("ABCDEFGH",true,new Interactions.ExtensionName("cics-ts.program","2")).referenceName());
    }
    @Test void eightCharactersWithoutPhysicalNameAreaRemainUnsupported() {
        var p=route(false,false,true);var u=p.units().getFirst();var s=u.sequences().get(1);var i=(Operations.Invoke)s.terminator();
        var original=(Interactions.ComputedTarget)i.target();
        var target=new Interactions.ComputedTarget("program","cics.program",original.name(),new Interactions.ExtensionName("cics-ts.program","1"),original.origin());
        var changed=new Operations.Invoke(i.header(),"call",target,i.arguments(),i.results(),i.signature(),i.effectOperands(),i.effectBound(),i.outcomes(),i.contract());
        var seq=new ArrayList<>(u.sequences());seq.set(1,new Sequence(s.label(),s.instructions(),changed,s.origin()));
        p=W1dEffectsTest.sequences(p,seq,u.entries());
        p=new Publication(p.id(),p.airVersion(),new Capabilities.Manifest(List.of(new Capabilities.Capability("cics-ts.program","1")),List.of()),p.artifacts(),p.units(),p.storage(),p.resources(),p.artifactRelations(),p.origins(),p.coverage(),p.uncertainties(),p.premises());
        var fact=downstream(p);assertEquals(DependencySiteFact.Reachability.REACHABLE,fact.reachability());
        assertEquals(DependencySiteFact.TargetStatus.UNSUPPORTED_TARGET_EXPRESSION,fact.targetStatus());
        assertTrue(fact.candidates().isEmpty());assertTrue(fact.interpretationUnknownRemainder());
    }

}
