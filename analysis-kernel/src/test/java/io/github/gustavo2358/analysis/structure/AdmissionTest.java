package io.github.gustavo2358.analysis.structure;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.analysis.cfg.domain.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static io.github.gustavo2358.analysis.structure.StructuralFixtures.*;

class AdmissionTest {
    @Test void realBuildIsAccepted() {
        var p=linear(3,2,2,2);
        assertEquals(AnalysisSession.Status.ACCEPTED,AnalysisSession.open(build(p),p,ProjectionPolicy.KNOWN_SUBSET,p.units().getFirst().entries()).status());
    }
    @Test void missingBranchEdgeIsInvalid() {
        var p0=linear(1,0,0,1); var u=p0.units().getFirst();
        var p=publication(p0.id(),List.of(unit(u.id(),u.entries(),List.of(branch(u.id(),"seq-0","tail","tail"),returning(u.id(),"tail",List.of())),List.of())),List.of());
        var b=build(p); var g=b.graph().orElseThrow();
        var cut=new CfgGraph(p,g.nodes(),g.transitions().stream().filter(t->t.kind()!=CfgTransition.Kind.BRANCH_FALSE).toList());
        assertEquals(AnalysisSession.Status.INVALID_INPUT,AnalysisSession.open(withGraph(b,cut),p,ProjectionPolicy.KNOWN_SUBSET,u.entries()).status());
    }
    @Test void missingOrphanIsInvalid() {
        var p0=linear(1,0,0,1); var u=p0.units().getFirst();
        var p=publication(p0.id(),List.of(unit(u.id(),u.entries(),List.of(returning(u.id(),"seq-0",List.of()),returning(u.id(),"orphan",List.of())),List.of())),List.of());
        var b=build(p); var g=b.graph().orElseThrow();
        var orphan=g.nodes().stream().filter(n->n instanceof CfgNode.SequenceNode s && s.source().label().localId().equals("orphan")).findFirst().orElseThrow();
        var cut=new CfgGraph(p,g.nodes().stream().filter(n->n!=orphan).toList(),g.transitions().stream().filter(t->!t.from().equals(orphan.id())).toList());
        assertEquals(AnalysisSession.Status.INVALID_INPUT,AnalysisSession.open(withGraph(b,cut),p,ProjectionPolicy.KNOWN_SUBSET,u.entries()).status());
    }
    @Test void equalLookingReplacementSequenceIsInvalid() {
        var p=linear(1,2,1,1); var b=build(p); var g=b.graph().orElseThrow();
        var nodes=g.nodes().stream().map(n->{ if(n instanceof CfgNode.SequenceNode s) { var q=s.source(); return (CfgNode)new CfgNode.SequenceNode(s.id(),new Sequence(q.label(),q.instructions(),q.terminator(),q.origin())); } return n; }).toList();
        assertEquals(AnalysisSession.Status.INVALID_INPUT,AnalysisSession.open(withGraph(b,new CfgGraph(p,nodes,g.transitions())),p,ProjectionPolicy.KNOWN_SUBSET,p.units().getFirst().entries()).status());
    }
    @Test void equalLookingForeignSnapshotIsInvalid() {
        var p=linear(1,1,1,1); var other=publication(p.id(),p.units(),p.storage());
        assertEquals(p,other);
        assertEquals(AnalysisSession.Status.INVALID_INPUT,AnalysisSession.open(build(p),other,ProjectionPolicy.KNOWN_SUBSET,other.units().getFirst().entries()).status());
    }
    @Test void equalLookingForeignEntrySelectionIsInvalid() {
        var p=linear(1,1,1,1); var other=linear(1,1,1,1);
        assertEquals(AnalysisSession.Status.INVALID_INPUT,AnalysisSession.open(build(p),p,ProjectionPolicy.KNOWN_SUBSET,other.units().getFirst().entries()).status());
    }

    /** Corruption injector is test-only. It bypasses CfgGraph constructor guards to test admission itself. */
    static void corrupt(CfgGraph graph, String field, Object replacement) throws Exception {
        var f=CfgGraph.class.getDeclaredField(field); f.setAccessible(true); f.set(graph,replacement);
    }
    @Test void duplicateAndWrongContextEdgesAreInvalid() throws Exception {
        for(int mode=0;mode<5;mode++) {
            var p=linear(2,0,0,2); var b=build(p); var g=b.graph().orElseThrow(); var edges=new ArrayList<>(g.transitions());
            var last=edges.getLast();
            if(mode==0) edges.add(last);
            if(mode==1) edges.set(edges.size()-1,new CfgTransition(last.from(),last.to(),last.kind(),p.units().getFirst().entries().getFirst().id()));
            if(mode==2) edges.set(edges.size()-1,new CfgTransition(last.to(),last.from(),last.kind(),last.activationEntry()));
            if(mode==3) edges.set(edges.size()-1,new CfgTransition(last.from(),new CfgNodeId(p.id(),987654321),last.kind(),last.activationEntry()));
            if(mode==4) edges.set(edges.size()-1,edges.getFirst());
            corrupt(g,"transitions",List.copyOf(edges));
            assertEquals(AnalysisSession.Status.INVALID_INPUT,AnalysisSession.open(b,p,ProjectionPolicy.KNOWN_SUBSET,p.units().getFirst().entries()).status(),"corruption "+mode);
        }
    }
    @Test void missingEntryExitHaltAndReplacedHaltAreInvalid() throws Exception {
        var p0=linear(1,0,0,1); var u=p0.units().getFirst();
        var halt=new Operations.Halt(header(u.id(),"halt"),Operations.HaltKind.NORMAL);
        var p=publication(p0.id(),List.of(unit(u.id(),u.entries(),List.of(new Sequence(u.sequences().getFirst().label(),List.of(),halt,origin(p0.id()))),List.of())),List.of());
        for(int mode=0;mode<4;mode++) {
            var b=build(p); var g=b.graph().orElseThrow(); var nodes=new ArrayList<>(g.nodes());
            if(mode==0) nodes.removeIf(CfgNode.EntryNode.class::isInstance);
            if(mode==1) nodes.removeIf(CfgNode.NormalExit.class::isInstance);
            if(mode==2) nodes.removeIf(CfgNode.HaltExit.class::isInstance);
            if(mode==3) nodes.replaceAll(n->n instanceof CfgNode.HaltExit h ? new CfgNode.HaltExit(h.id(),new Operations.Halt(halt.header(),halt.haltKind())) : n);
            corrupt(g,"nodes",List.copyOf(nodes));
            assertEquals(AnalysisSession.Status.INVALID_INPUT,AnalysisSession.open(b,p,ProjectionPolicy.KNOWN_SUBSET,u.entries()).status());
        }
    }
    @Test void foreignEntryNodeAndDuplicateSequenceRoleAreInvalid() throws Exception {
        for(boolean foreign:List.of(true,false)) {
            var p=linear(1,0,0,1); var b=build(p); var g=b.graph().orElseThrow(); var nodes=new ArrayList<>(g.nodes());
            if(foreign) nodes.replaceAll(n->n instanceof CfgNode.EntryNode e ? new CfgNode.EntryNode(e.id(),entry(e.source().id().unit(),e.source().id().localId(),"seq-0")) : n);
            else { var q=p.units().getFirst().sequences().getFirst(); nodes.add(new CfgNode.SequenceNode(new CfgNodeId(p.id(),900),q)); }
            corrupt(g,"nodes",List.copyOf(nodes));
            assertEquals(AnalysisSession.Status.INVALID_INPUT,AnalysisSession.open(b,p,ProjectionPolicy.KNOWN_SUBSET,p.units().getFirst().entries()).status());
        }
    }
    @Test void unsupportedProfileAndPolicyMismatchHaveDistinctTaxonomy() {
        var p=linear(1,0,0,1); var b=build(p);
        assertEquals(AnalysisSession.Status.INVALID_INPUT,AnalysisSession.open(b,p,ProjectionPolicy.STRICT,p.units().getFirst().entries()).status());
        var u=p.units().getFirst(); var q=new Sequence(u.sequences().getFirst().label(),List.of(),new Operations.Raise(header(u.id(),"raise"),"tag",List.of()),origin(p.id()));
        var unsupported=publication(p.id(),List.of(unit(u.id(),u.entries(),List.of(q),List.of())),List.of());
        // Manual result can claim CFG_BUILT, but membership alone cannot certify the core profile.
        var g=new CfgGraph(unsupported,List.of(),List.of());
        var manual=new io.github.gustavo2358.analysis.cfg.application.CfgBuildResult(b.status(),unsupported.id(),unsupported.airVersion(),b.options(),b.preflight(),List.of(),List.of(),Optional.of(g));
        assertEquals(AnalysisSession.Status.UNSUPPORTED,AnalysisSession.open(manual,unsupported,ProjectionPolicy.KNOWN_SUBSET,u.entries()).status());
    }
    @Test void incompleteUpstreamValidationDoesNotBecomeSemanticSizeOutcome() {
        var p=linear(1,0,0,1); var b=build(p);
        var limited=new io.github.gustavo2358.analysis.cfg.application.CfgBuildResult(io.github.gustavo2358.analysis.cfg.application.CfgBuildResult.Status.VALIDATION_LIMIT,b.publicationId(),b.airVersion(),b.options(),b.preflight(),List.of(),List.of(),Optional.empty());
        assertThrows(IllegalStateException.class,()->AnalysisSession.open(limited,p,ProjectionPolicy.KNOWN_SUBSET,List.of()));
        assertEquals(Set.of("ACCEPTED","INVALID_INPUT","UNSUPPORTED"),new HashSet<>(Arrays.stream(AnalysisSession.Status.values()).map(Enum::name).toList()));
    }
}
