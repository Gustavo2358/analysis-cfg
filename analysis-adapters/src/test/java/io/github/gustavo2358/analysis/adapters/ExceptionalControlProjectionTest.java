package io.github.gustavo2358.analysis.adapters;
import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.analysis.cfg.application.*;
import io.github.gustavo2358.analysis.cfg.domain.*;
import io.github.gustavo2358.analysis.cfg.extension.SemanticInterpreterRegistry;
import io.github.gustavo2358.analysis.structure.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ExceptionalControlProjectionTest {
    static Publication model(boolean opaque,Control.InvocationAlternative alternative) {
        var p=EvidenceMonotonicityTest.baseline();var u=p.units().getFirst();var s=u.sequences().getFirst();var i=(Operations.Invoke)s.terminator();
        var gap=new Ids.UncertaintyId(p.id(),"opaque-model-gap");
        var scope=new Scopes.EntityScope(List.of(i.header().id()));
        var h=opaque?new Operations.Header(i.header().id(),i.header().origin(),Evidence.CoverageStatus.ABSTRACTED,i.header().precision(),List.of(gap)):i.header();
        Terminator t=opaque?new Operations.Opaque(h,"uninterpreted-vendor-event",List.of(),List.of(),new Envelopes.Envelope(
            new Envelopes.MemoryEnvelope(List.of(),Scopes.NoMemory.INSTANCE,List.of(),Scopes.NoMemory.INSTANCE,List.of()),
            new Control.ControlEnvelope(List.of(alternative),Scopes.NoControl.INSTANCE),new Envelopes.DependencyEnvelope(List.of(),Scopes.NoResources.INSTANCE))):
            W1dEffectsTest.replace(i,i.effectBound(),new Control.InvocationOutcomes(List.of(alternative),Scopes.NoControl.INSTANCE));
        var sequences=new ArrayList<>(u.sequences());sequences.set(0,new Sequence(s.label(),s.instructions(),t,s.origin()));
        var changed=W1dEffectsTest.sequences(p,sequences,u.entries());
        if(!opaque)return changed;
        return new Publication(changed.id(),changed.airVersion(),changed.capabilities(),changed.artifacts(),changed.units(),changed.storage(),changed.resources(),changed.artifactRelations(),changed.origins(),changed.coverage(),
            List.of(new Evidence.Uncertainty(gap,"MODEL_GAP",List.of(Evidence.Dimension.EFFECTS),scope,"Uninterpreted effect",i.header().origin())),changed.premises());
    }
    @Test void explicitExceptionDoesNotBecomeUnitWideUnknownControl()throws Exception {
        var b=EvidenceMonotonicityTest.baseline();var target=b.units().getFirst().sequences().get(1).label();
        for(boolean opaque:List.of(false,true))for(var alternative:List.<Control.InvocationAlternative>of(
                new Control.Exceptional("vendor-tag",new Control.Handler(target)),new Control.AnyException(new Control.Handler(target)),
                new Control.Exceptional("vendor-tag",Control.Propagate.INSTANCE),new Control.AnyException(Control.Propagate.INSTANCE),Control.HaltAlternative.INSTANCE)) {
            var p=model(opaque,alternative);var options=BuildOptions.defaults();var result=new CfgBuildCoordinator(SemanticInterpreterRegistry.empty()).build(p,options);
            assertEquals(CfgBuildResult.Status.CFG_BUILT,result.status(),()->opaque+" "+alternative+" "+result);
            var session=AnalysisSession.open(result,p,options.projectionPolicy(),p.units().getFirst().entries()).session().orElseThrow();
            assertFalse(session.index().partialControl(p.units().getFirst().id()));
            var context=session.contexts().iterator().next();var cursor=context.successors(session.index().sequence(p.units().getFirst().sequences().getFirst().label()));int count=0;
            while(cursor.advance()){count++;assertNotEquals(CfgTransition.Kind.OPAQUE_UNKNOWN,cursor.transition().kind());assertNotEquals(context.entryNode(),cursor.target());}
            assertEquals(1,count,"one published alternative cannot license any other local destination");
            boolean local=alternative instanceof Control.Exceptional e&&e.destination() instanceof Control.Handler
                || alternative instanceof Control.AnyException a&&a.destination() instanceof Control.Handler;
            assertEquals(local?0:1,result.graph().orElseThrow().nodes().stream().filter(CfgNode.OutcomeExit.class::isInstance).count());
            var graph=result.graph().orElseThrow();
            var edges=graph.transitions().stream().filter(e->e.kind()!=CfgTransition.Kind.EXCEPTION&&e.kind()!=CfgTransition.Kind.CONTROL_EXIT).toList();
            for(boolean removeExit:List.of(false,true)) {
                var nodes=removeExit?graph.nodes().stream().filter(n->!(n instanceof CfgNode.OutcomeExit)).toList():graph.nodes();
                var corrupt=new CfgGraph(p,nodes,edges);
                var fake=new CfgBuildResult(result.status(),result.publicationId(),result.airVersion(),result.options(),result.preflight(),result.unsupportedCapabilities(),result.projectionIssues(),Optional.of(corrupt));
                assertEquals(AnalysisSession.Status.INVALID_INPUT,AnalysisSession.open(fake,p,options.projectionPolicy(),p.units().getFirst().entries()).status(),"missing explicit outcome must be rejected independently");
            }
            var edge=graph.transitions().stream().filter(e->e.kind()==CfgTransition.Kind.EXCEPTION||e.kind()==CfgTransition.Kind.CONTROL_EXIT).findFirst().orElseThrow();
            var wrong=new ArrayList<>(graph.transitions());wrong.remove(edge);wrong.add(new CfgTransition(edge.from(),edge.from(),edge.kind(),edge.activationEntry()));
            assertThrows(IllegalArgumentException.class,()->new CfgGraph(p,graph.nodes(),wrong),"no invented local exception destination");
        }
    }
}
