package io.github.gustavo2358.analysis.structure;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.analysis.cfg.domain.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static io.github.gustavo2358.analysis.structure.StructuralFixtures.*;

final class SharedTopologyTest {
    private static List<CfgTransition> read(ContextView context,ProgramIndex.Node node,boolean forward) {
        var cursor=forward?context.successors(node):context.predecessors(node);var result=new ArrayList<CfgTransition>();
        while(cursor.advance()){result.add(cursor.transition());assertEquals(cursor.transition().from(),cursor.source().source().id());assertEquals(cursor.transition().to(),cursor.target().source().id());}
        return result;
    }
    @Test void projectionAndIndexStoreEntryBindingsWithoutCopyingEveryBody() {
        int sequences=256,entries=128;var p=linear(sequences,0,0,entries);var built=build(p);var graph=built.graph().orElseThrow();
        assertEquals(entries*(sequences+1),graph.transitions().size());
        assertEquals(entries+sequences,graph.storedTransitionCount(),"body edges were expanded per Entry");
        var admitted=AnalysisSession.open(built,p,ProjectionPolicy.KNOWN_SUBSET,p.units().getFirst().entries());
        assertEquals(AnalysisSession.Status.ACCEPTED,admitted.status(),admitted.reason());
        var index=admitted.session().orElseThrow().index();
        assertEquals((long)entries+sequences,index.metrics().visitsByCollection().get("cfg.transitions"));
        assertEquals((long)entries+sequences,index.metrics().visitsByCollection().get("adjacency.transitions"));
        assertEquals((long)entries*(sequences+1),index.metrics().edgesIndexed());
    }
    @Test void denseAndFactoredGraphsHaveSameEveryEntryForwardBackwardAndListHash() {
        var p=linear(12,0,0,6);var built=build(p);var graph=built.graph().orElseThrow();
        var denseRows=new ArrayList<>(graph.transitions());var dense=new CfgGraph(p,graph.nodes(),denseRows);
        assertEquals(denseRows,graph.transitions());assertEquals(graph.transitions(),denseRows);
        assertEquals(denseRows.hashCode(),graph.transitions().hashCode());assertEquals(dense,graph);assertEquals(dense.hashCode(),graph.hashCode());
        var factored=AnalysisSession.open(built,p,ProjectionPolicy.KNOWN_SUBSET,p.units().getFirst().entries()).session().orElseThrow();
        var expanded=AnalysisSession.open(withGraph(built,dense),p,ProjectionPolicy.KNOWN_SUBSET,p.units().getFirst().entries()).session().orElseThrow();
        for(var context:factored.contexts())for(var node:factored.index().nodes) {
            var other=expanded.context(context.entry().id());var otherNode=expanded.index().node(node.source().id());
            for(boolean forward:List.of(false,true))assertEquals(read(other,otherNode,forward),read(context,node,forward));
            if(node.source() instanceof CfgNode.NormalExit exit&&exit.entryId().equals(context.entry().id())) {
                var incoming=read(context,node,false);assertEquals(1,incoming.size());assertEquals(CfgTransition.Kind.RETURN,incoming.getFirst().kind());
            }
        }
        var invalid=new ArrayList<>(denseRows);invalid.removeLast();
        var rejected=AnalysisSession.open(withGraph(built,new CfgGraph(p,graph.nodes(),invalid)),p,ProjectionPolicy.KNOWN_SUBSET,p.units().getFirst().entries());
        assertEquals(AnalysisSession.Status.INVALID_INPUT,rejected.status());
    }
    @Test void unitAndEntryExitBindingsStaySeparateWithEqualTargetBranchesAndDifferentRoots() {
        var id=new io.github.gustavo2358.air.model.Ids.PublicationId("shared-units");var units=new ArrayList<Unit>();
        for(String name:List.of("A","B")) {
            var u=new io.github.gustavo2358.air.model.Ids.UnitId(id,name);
            units.add(unit(u,List.of(entry(u,"entry-1","start"),entry(u,"entry-2","a")),List.of(branch(u,"start","a","a"),returning(u,"a",List.of()),returning(u,"b",List.of())),List.of()));
        }
        var p=publication(id,units,List.of());var built=build(p);var selected=new ArrayList<Entries.Entry>();for(var u:units)selected.addAll(u.entries());
        var session=AnalysisSession.open(built,p,ProjectionPolicy.KNOWN_SUBSET,selected).session().orElseThrow();
        for(var context:session.contexts())for(var node:session.index().nodes) {
            var outgoing=read(context,node,true);var incoming=read(context,node,false);
            if(!node.owner().id().equals(context.entry().id().unit())){assertTrue(outgoing.isEmpty());assertTrue(incoming.isEmpty());continue;}
            if(node.source() instanceof CfgNode.EntryNode entryNode)assertEquals(entryNode.source().id().equals(context.entry().id())?1:0,outgoing.size());
            if(node.source() instanceof CfgNode.SequenceNode sequence) {
                if(sequence.source().terminator() instanceof Operations.Return) {
                    assertEquals(1,outgoing.size());assertEquals(context.normalExit().source().id(),outgoing.getFirst().to());
                } else {
                    assertEquals(2,outgoing.size());assertEquals(outgoing.getFirst().to(),outgoing.getLast().to());
                    assertEquals(List.of(CfgTransition.Kind.BRANCH_TRUE,CfgTransition.Kind.BRANCH_FALSE),outgoing.stream().map(CfgTransition::kind).toList());
                }
            }
            if(node.source() instanceof CfgNode.NormalExit exit)assertEquals(exit.entryId().equals(context.entry().id())?2:0,incoming.size());
        }
    }
    @Test void retentionAuditCountsPhysicalRowsAndStillDetectsAnExtraRetainedTransition()throws Exception {
        var p=linear(16,0,0,8);var graph=build(p).graph().orElseThrow();var baseline=RetentionWalk.walk(graph);
        assertEquals(graph.storedTransitionCount(),baseline.stream().filter(CfgTransition.class::isInstance).count());
        var row=graph.transitions().getFirst();var copy=new CfgTransition(row.from(),row.to(),row.kind(),row.activationEntry());
        assertEquals(1L,RetentionWalk.additional(List.of(graph,copy),baseline).get(CfgTransition.class.getName()));
    }
}
