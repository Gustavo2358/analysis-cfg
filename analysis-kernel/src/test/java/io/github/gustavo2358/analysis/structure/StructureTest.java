package io.github.gustavo2358.analysis.structure;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.analysis.cfg.domain.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static io.github.gustavo2358.analysis.structure.StructuralFixtures.*;

class StructureTest {
    static AnalysisSession session(Publication p) {
        var admission=AnalysisSession.open(build(p),p,ProjectionPolicy.KNOWN_SUBSET,p.units().stream().flatMap(u->u.entries().stream()).toList());
        assertEquals(AnalysisSession.Status.ACCEPTED,admission.status(),admission.reason());
        return admission.session().orElseThrow();
    }
    static List<CfgTransition.Kind> kinds(ContextView.EdgeCursor cursor) {
        List<CfgTransition.Kind> result=new ArrayList<>();
        while(cursor.advance()) result.add(cursor.transition().kind());
        assertEquals(result.size(),cursor.edgesVisited());
        return result;
    }
    @Test void offsetsBucketsAndPayloadAreCanonical() {
        var p=linear(1,3,2,1); var u=p.units().getFirst(); var q=u.sequences().getFirst();
        var s=session(p); var idx=s.index();
        assertSame(p,idx.publication()); assertSame(u,idx.unit(u.id()));
        var node=idx.sequence(q.label()); assertSame(q,((CfgNode.SequenceNode)node.source()).source());
        for(int offset=0;offset<4;offset++) {
            Operation op=offset==3?q.terminator():q.instructions().get(offset);
            var site=idx.site(op.header().id());
            assertSame(site,idx.site(op.header().id())); assertSame(op,site.operation());
            assertSame(q,site.sequence()); assertSame(u,site.owner());
            assertEquals(offset,site.offset()); assertEquals(offset==3,site.isTerminator());
        }
        assertEquals(3,idx.sites(Operations.Assign.class).size());
        assertEquals(1,idx.sites(Operations.Return.class).size());
        assertTrue(idx.sites(Operations.Halt.class).isEmpty());
        assertThrows(UnsupportedOperationException.class,()->idx.sites(Operations.Assign.class).clear());
        for(var object:u.objects()) { assertSame(object,idx.object(object.id())); assertSame(p.storage().getFirst(),idx.directCell(object.id())); }
        var place=(Places.ObjectPlace)((Operations.Assign)q.instructions().getFirst()).destination();
        assertSame(u.objects().getFirst(),idx.referencedObject(place.header().id()));
    }
    @Test void multipleEntriesKeepEntryReturnAndContextSeparate() {
        var p=linear(2,0,0,2); var u=p.units().getFirst(); var s=session(p); var idx=s.index();
        for(var e:u.entries()) {
            var view=s.context(e.id()); assertSame(e,view.entry());
            assertEquals(List.of(CfgTransition.Kind.ENTRY),kinds(view.successors(view.entryNode())));
            var tail=idx.sequence(new LabelId(u.id(),"seq-1")); var c=view.successors(tail);
            assertTrue(c.advance()); assertSame(view.normalExit(),c.target()); assertEquals(e.id(),c.transition().activationEntry()); assertFalse(c.advance());
            assertEquals(List.of(CfgTransition.Kind.RETURN),kinds(view.predecessors(view.normalExit())));
            for(var other:u.entries()) if(other!=e) assertTrue(kinds(view.successors(s.context(other.id()).entryNode())).isEmpty());
        }
    }
    @Test void sameTargetBranchKeepsTwoOutcomesAndBackwardEdges() {
        var base=linear(1,0,0,1); var u=base.units().getFirst();
        var p=publication(base.id(),List.of(unit(u.id(),u.entries(),List.of(branch(u.id(),"seq-0","tail","tail"),returning(u.id(),"tail",List.of())),List.of())),List.of());
        var s=session(p); var v=s.context(u.entries().getFirst().id());
        assertEquals(List.of(CfgTransition.Kind.BRANCH_TRUE,CfgTransition.Kind.BRANCH_FALSE),kinds(v.successors(s.index().sequence(new LabelId(u.id(),"seq-0")))));
        assertEquals(List.of(CfgTransition.Kind.BRANCH_TRUE,CfgTransition.Kind.BRANCH_FALSE),kinds(v.predecessors(s.index().sequence(new LabelId(u.id(),"tail")))));
    }
    @Test void orphanIsIndexedWithoutInventedReachability() {
        var base=linear(1,0,0,2); var u=base.units().getFirst();
        var orphan=returning(u.id(),"orphan",List.of());
        var p=publication(base.id(),List.of(unit(u.id(),u.entries(),List.of(u.sequences().getFirst(),orphan),List.of())),List.of());
        var s=session(p); var node=s.index().sequence(orphan.label()); assertNotNull(node);
        for(var e:u.entries()) {
            var v=s.context(e.id()); assertTrue(kinds(v.predecessors(node)).isEmpty());
            // Structural RETURN still exists. The test-only graph walk, not production, proves no entry path.
            assertEquals(List.of(CfgTransition.Kind.RETURN),kinds(v.successors(node)));
            Set<ProgramIndex.Node> reached=new HashSet<>(); ArrayDeque<ProgramIndex.Node> pending=new ArrayDeque<>(); pending.add(v.entryNode());
            while(!pending.isEmpty()) { var n=pending.removeFirst(); if(!reached.add(n)) continue; var c=v.successors(n); while(c.advance()) pending.add(c.target()); }
            assertFalse(reached.contains(node));
        }
    }
    @Test void sparseNodeIdsRoundTripWithoutDensePublicAssumption() {
        var p=linear(3,0,0,2); var b=build(p); var g=b.graph().orElseThrow();
        Map<CfgNodeId,CfgNodeId> ids=new HashMap<>(); long next=Long.MAX_VALUE;
        for(var n:g.nodes()) { ids.put(n.id(),new CfgNodeId(p.id(),next)); next-=1000003; }
        List<CfgNode> nodes=g.nodes().stream().map(n->switch(n) {
            case CfgNode.SequenceNode q -> (CfgNode)new CfgNode.SequenceNode(ids.get(n.id()),q.source());
            case CfgNode.EntryNode e -> new CfgNode.EntryNode(ids.get(n.id()),e.source());
            case CfgNode.NormalExit e -> new CfgNode.NormalExit(ids.get(n.id()),e.publicationId(),e.unitId(),e.entryId());
            case CfgNode.HaltExit h -> new CfgNode.HaltExit(ids.get(n.id()),h.source());
        }).toList();
        var graph=new CfgGraph(p,nodes,g.transitions().stream().map(e->new CfgTransition(ids.get(e.from()),ids.get(e.to()),e.kind(),e.activationEntry())).toList());
        var s=AnalysisSession.open(withGraph(b,graph),p,ProjectionPolicy.KNOWN_SUBSET,p.units().getFirst().entries()).session().orElseThrow();
        for(var n:nodes) assertSame(n,s.index().node(n.id()).source());
        assertEquals(7,s.index().metrics().nodesIndexed());
        assertEquals(7,s.index().nodes.length); // Internal assertion: retention proportional to V, not largest external ordinal.
    }
    @Test void fullOwnersAndDisplayRenamingDoNotCollide() {
        var base=linear(1,1,1,1); var a=base.units().getFirst(); var p=base.id(); var other=new UnitId(p,"other");
        var id=new ObjectId(other,"object-0"); var original=a.objects().getFirst();
        var object=new Memory.ObjectDeclaration(id,original.displayName(),original.typeRef(),original.storage(),original.visibility(),original.origin(),original.coverage(),original.precision());
        var b=unit(other,List.of(entry(other,"entry-0","seq-0")),List.of(returning(other,"seq-0",List.of(assign(other,"instruction-0",id)))),List.of(object));
        var publication=publication(p,List.of(a,b),base.storage()); var s=session(publication);
        assertNotSame(s.index().object(original.id()),s.index().object(id));
        assertSame(object,s.index().object(id));
        assertNull(s.index().object(new ObjectId(new UnitId(new PublicationId("foreign"),other.localId()),id.localId())));
        assertNotSame(s.index().site(new OperationId(a.id(),"instruction-0")),s.index().site(new OperationId(other,"instruction-0")));
        var renamed=new Memory.ObjectDeclaration(id,Optional.of("entirely renamed"),object.typeRef(),object.storage(),object.visibility(),object.origin(),object.coverage(),object.precision());
        var renamedB=unit(other,b.entries(),b.sequences(),List.of(renamed));
        var s2=session(publication(p,List.of(a,renamedB),base.storage()));
        assertEquals(s.index().metrics(),s2.index().metrics()); assertSame(renamed,s2.index().object(id));
    }
    @Test void physicalOrderDoesNotDefineControlAndSameSnapshotIsDeterministic() {
        var p=linear(4,2,2,2); var u=p.units().getFirst();
        var first=session(p); var second=session(p); assertEquals(first.index().metrics(),second.index().metrics());
        var reordered=publication(p.id(),List.of(unit(u.id(),u.entries().reversed(),u.sequences().reversed(),u.objects().reversed())),p.storage());
        var third=session(reordered);
        for(var e:u.entries()) for(var q:u.sequences()) {
            var a=first.context(e.id()).successors(first.index().sequence(q.label()));
            var b=third.context(e.id()).successors(third.index().sequence(q.label()));
            assertEquals(kinds(a),kinds(b));
        }
        assertEquals(first.index().metrics(),third.index().metrics());
    }
    @Test void emptySelectionCreatesNoContextsAndForeignHandleIsRejected() {
        var p=linear(2,1,1,20); var build=build(p);
        var empty=AnalysisSession.open(build,p,ProjectionPolicy.KNOWN_SUBSET,List.of()).session().orElseThrow();
        assertTrue(empty.contexts().isEmpty());
        var s=session(p); var v=s.context(p.units().getFirst().entries().getFirst().id());
        assertThrows(IllegalArgumentException.class,()->v.successors(empty.index().sequence(p.units().getFirst().sequences().getFirst().label())));
    }
    @Test void cycleAndSelfLoopKeepLiteralControl() {
        var b=linear(1,0,0,1); var u=b.units().getFirst();
        var p=publication(b.id(),List.of(unit(u.id(),u.entries(),List.of(branch(u.id(),"seq-0","seq-0","tail"),jump(u.id(),"tail","seq-0")),List.of())),List.of());
        var s=session(p); var c=s.context(u.entries().getFirst().id()).successors(s.index().sequence(new LabelId(u.id(),"seq-0")));
        assertTrue(c.advance()); assertSame(c.source(),c.target()); assertTrue(c.advance()); assertFalse(c.advance());
    }
}
