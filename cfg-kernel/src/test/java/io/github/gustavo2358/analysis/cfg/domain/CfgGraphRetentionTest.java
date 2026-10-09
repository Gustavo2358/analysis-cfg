package io.github.gustavo2358.analysis.cfg.domain;

import io.github.gustavo2358.air.model.Ids.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static io.github.gustavo2358.analysis.cfg.testing.CfgFirstPublications.*;
import static org.junit.jupiter.api.Assertions.*;

/** Dense projection ordinals provide an index; validation must not duplicate it in maps. */
class CfgGraphRetentionTest {
    @Test void canonicalProjectionRejectsOrdinalNamespaceEntryAndEndpointContradictions() {
        var source=CfgSource.from(minimal());
        var sequence=new CfgNode.SequenceNode(new CfgNodeId(P,0),L,List.of(),new CfgControl.Return(new OperationId(U,"return")));
        var entry=new CfgNode.EntryNode(new CfgNodeId(P,1),E,Optional.of(L));
        var exit=new CfgNode.NormalExit(new CfgNodeId(P,2),P,U,E);
        var nodes=List.<CfgNode>of(sequence,entry,exit);
        var returned=new CfgTransition(sequence.id(),exit.id(),CfgTransition.Kind.RETURN,E);
        assertEquals(nodes,CfgGraph.projected(source,nodes,List.of(returned)).nodes());
        assertThrows(IllegalArgumentException.class,()->CfgGraph.projected(source,List.of(entry,sequence,exit),List.of()));
        var duplicateId=new CfgNode.NormalExit(new CfgNodeId(P,1),P,U,E);
        assertThrows(IllegalArgumentException.class,()->CfgGraph.projected(source,List.of(sequence,entry,duplicateId),List.of()));
        var duplicateEntry=new CfgNode.EntryNode(new CfgNodeId(P,2),E,Optional.of(L));
        assertThrows(IllegalArgumentException.class,()->CfgGraph.projected(source,List.of(sequence,entry,duplicateEntry),List.of()));
        var earlierEntry=new CfgNode.EntryNode(new CfgNodeId(P,2),new EntryId(U,"A"),Optional.of(L));
        assertThrows(IllegalArgumentException.class,()->CfgGraph.projected(source,List.of(sequence,entry,earlierEntry),List.of()));
        var foreign=new PublicationId("foreign");var foreignUnit=new UnitId(foreign,U.localId());
        var foreignNode=new CfgNode.SequenceNode(new CfgNodeId(foreign,0),new LabelId(foreignUnit,L.localId()),List.of(),new CfgControl.Return(new OperationId(foreignUnit,"return")));
        assertThrows(IllegalArgumentException.class,()->CfgGraph.projected(source,List.of(foreignNode,entry,exit),List.of()));
        assertThrows(IllegalArgumentException.class,()->CfgGraph.projected(source,nodes,List.of(
                new CfgTransition(new CfgNodeId(foreign,0),exit.id(),CfgTransition.Kind.RETURN,E))));
        assertThrows(IllegalArgumentException.class,()->CfgGraph.projected(source,nodes,List.of(
                new CfgTransition(sequence.id(),exit.id(),CfgTransition.Kind.RETURN,new EntryId(foreignUnit,E.localId())))));
        for(var bad:List.of(
                new CfgTransition(new CfgNodeId(P,Long.MAX_VALUE),exit.id(),CfgTransition.Kind.RETURN,E),
                new CfgTransition(sequence.id(),entry.id(),CfgTransition.Kind.RETURN,E),
                new CfgTransition(sequence.id(),exit.id(),CfgTransition.Kind.RETURN,new EntryId(U,"missing")))) {
            assertThrows(IllegalArgumentException.class,()->CfgGraph.projected(source,nodes,List.of(bad)));
        }
        assertThrows(IllegalArgumentException.class,()->CfgGraph.projected(source,nodes,List.of(returned,returned)));
        var builder=new CfgTransitionTable.Builder(source);
        builder.add(U,List.of(new CfgTransition(entry.id(),sequence.id(),CfgTransition.Kind.ENTRY,E)),List.of(entry.id()),List.of(returned));
        assertThrows(IllegalArgumentException.class,()->CfgGraph.projected(source,nodes,builder.build()));
    }

    @Test void generalConstructorsKeepArbitraryValidOrdinalsAndEntryOrder() {
        var sequence=new CfgNode.SequenceNode(new CfgNodeId(P,90),L,List.of(),new CfgControl.Return(new OperationId(U,"return")));
        var entry=new CfgNode.EntryNode(new CfgNodeId(P,2),E,Optional.of(L));
        var exit=new CfgNode.NormalExit(new CfgNodeId(P,40),P,U,E);
        var other=new CfgNode.EntryNode(new CfgNodeId(P,3),new EntryId(U,"A"),Optional.of(L));
        var nodes=List.<CfgNode>of(entry,exit,sequence,other);
        var transitions=List.of(new CfgTransition(sequence.id(),exit.id(),CfgTransition.Kind.RETURN,E));
        var publication=minimal();var source=CfgSource.from(publication);
        assertEquals(nodes,new CfgGraph(source,nodes,transitions).nodes());
        assertTrue(new CfgGraph(publication,nodes,transitions).wasProjectedFrom(publication));
        assertThrows(IllegalArgumentException.class,()->CfgGraph.projected(source,nodes,transitions));
    }

    @Test void canonicalProjectionValidatesEndpointsWithoutProgramSizedIdentityMaps() {
        var bean=(com.sun.management.ThreadMXBean)java.lang.management.ManagementFactory.getThreadMXBean();
        assertTrue(bean.isThreadAllocatedMemorySupported());bean.setThreadAllocatedMemoryEnabled(true);
        long thread=Thread.currentThread().threadId();var source=CfgSource.from(minimal());
        for(int count:new int[]{16,256,4096,65536}) {
            var nodes=new ArrayList<CfgNode>();var body=new ArrayList<CfgTransition>();
            var control=new CfgControl.Return(new OperationId(U,"return"));
            var exit=new CfgNodeId(P,count+1);
            for(int i=0;i<count;i++) {
                var id=new CfgNodeId(P,i);
                nodes.add(new CfgNode.SequenceNode(id,new LabelId(U,"ordinary-"+i),List.of(),control));
                body.add(new CfgTransition(id,exit,CfgTransition.Kind.RETURN,E));
            }
            var entryNode=new CfgNode.EntryNode(new CfgNodeId(P,count),E,Optional.of(new LabelId(U,"ordinary-0")));
            nodes.add(entryNode);nodes.add(new CfgNode.NormalExit(exit,P,U,E));
            var binding=new CfgTransition(entryNode.id(),nodes.getFirst().id(),CfgTransition.Kind.ENTRY,E);
            var builder=new CfgTransitionTable.Builder(source);builder.add(U,List.of(binding),List.of(exit),body);
            var table=builder.build();
            for(int warmup=0;warmup<3;warmup++)CfgGraph.projected(source,nodes,table);
            long before=bean.getThreadAllocatedBytes(thread);
            var graph=CfgGraph.projected(source,nodes,table);
            long allocated=bean.getThreadAllocatedBytes(thread)-before;
            assertEquals(count+2,graph.nodes().size());assertSame(table,graph.transitions());
            assertTrue(allocated<=262144L+64L*nodes.size(),"canonical projection duplicated identity index: nodes="+nodes.size()+" bytes="+allocated);
            System.out.println("CFG_CANONICAL_VALIDATION_METRICS nodes="+nodes.size()+" allocatedBytes="+allocated);
        }
    }
}
