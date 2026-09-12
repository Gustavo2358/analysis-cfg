package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.air.json.AirJson;
import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.analysis.cfg.application.*;
import io.github.gustavo2358.analysis.cfg.domain.*;
import io.github.gustavo2358.analysis.cfg.extension.SemanticInterpreterRegistry;
import io.github.gustavo2358.analysis.structure.*;
import io.github.gustavo2358.analysis.values.*;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Independent assertions over exact real W1A → W1C bytes, captured before CFG implementation. */
final class W1dBoundaryTest {
    static Publication input(String name) throws IOException {
        try(var in=W1dBoundaryTest.class.getResourceAsStream("/cp6/"+name+".air.json")) {
            assertNotNull(in); return new AirJson().decode(in.readAllBytes());
        }
    }
    static CfgBuildResult build(Publication p) {
        return new CfgBuildCoordinator(SemanticInterpreterRegistry.empty()).build(p,BuildOptions.defaults());
    }
    static AnalysisSession session(Publication p) {
        var cfg=build(p); assertEquals(CfgBuildResult.Status.CFG_BUILT,cfg.status(),cfg.projectionIssues().toString());
        var opened=AnalysisSession.open(cfg,p,ProjectionPolicy.KNOWN_SUBSET,p.units().getFirst().entries());
        assertEquals(AnalysisSession.Status.ACCEPTED,opened.status(),opened.reason());return opened.session().orElseThrow();
    }
    @Test void realDynamicInvokeHasTypedLocalNormalAndOriginalAir() throws Exception {
        var p=input("dynamic-x8");var cfg=build(p);
        assertEquals(CfgBuildResult.Status.CFG_BUILT,cfg.status());
        var graph=cfg.graph().orElseThrow();
        var source=p.units().getFirst().sequences().stream().filter(s->s.terminator() instanceof Operations.Invoke).findFirst().orElseThrow();
        var invoke=(Operations.Invoke)source.terminator();
        var node=graph.nodes().stream().filter(n->n instanceof CfgNode.SequenceNode s&&s.source()==source).findFirst().orElseThrow();
        var edge=graph.transitions().stream().filter(t->t.from().equals(node.id())).toList();
        assertEquals(1,edge.size());assertEquals("INVOKE_NORMAL",edge.getFirst().kind().name());
        assertEquals(p.units().getFirst().entries().getFirst().id(),edge.getFirst().activationEntry());
        var normal=(Control.Normal)invoke.outcomes().known().getFirst();
        var destination=graph.nodes().stream().filter(n->n.id().equals(edge.getFirst().to())).findFirst().orElseThrow();
        assertEquals(normal.label(),((CfgNode.SequenceNode)destination).source().label());
        assertEquals(4,graph.nodes().size());assertEquals(3,graph.transitions().size());
        assertInstanceOf(Scopes.WithinControl.class,invoke.outcomes().remainder());
        var index=session(p).index();var sites=index.sites(Operations.Invoke.class);
        assertEquals(1,sites.size());assertSame(invoke,sites.getFirst().operation());
        assertEquals(1,sites.getFirst().offset());assertEquals(source.label(),sites.getFirst().sequence().label());
    }
    @Test void oldValuesProfileStillRefusesRealInvoke() throws Exception {
        var admission=PossibleValuesAnalysis.prepare(session(input("dynamic-x8")));
        assertEquals(PossibleValuesAnalysis.Status.UNSUPPORTED,admission.status());
        assertEquals("UNSUPPORTED_EFFECT_PROFILE",admission.reason());
    }
    @Test void literalRetainsLiteralWithoutFakeObjectOrAssign() throws Exception {
        var p=input("literal");session(p);
        assertTrue(p.units().getFirst().objects().isEmpty());
        var invoke=(Operations.Invoke)p.units().getFirst().sequences().stream().filter(s->s.terminator() instanceof Operations.Invoke).findFirst().orElseThrow().terminator();
        assertEquals("PROGA",((Interactions.LiteralTarget)invoke.target()).name());
        assertTrue(p.units().getFirst().sequences().stream().allMatch(s->s.instructions().isEmpty()));
    }
}
