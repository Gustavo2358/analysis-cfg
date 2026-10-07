package io.github.gustavo2358.analysis.solver;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.LongConsumer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PagedDagOwnershipTest {
    private static AnalysisResources memory(){return new AnalysisResources(new AnalysisResources.Limits(64000000,100000,0,0,0,500000000,0));}
    private static final class Graph implements PagedDagOwnership.Graph {
        final Map<Long,long[]> edges=new HashMap<>();final Set<Long> alive=new HashSet<>();
        void put(long id,long... children){assertTrue(alive.add(id));edges.put(id,children);}
        public void children(long node,LongConsumer accept){assertTrue(alive.contains(node));for(long child:edges.get(node))accept.accept(child);}
        public void retire(long node){assertTrue(alive.remove(node),"node cannot retire twice="+node);}
        Set<Long> reachable(long... roots){
            var result=new HashSet<Long>();var pending=new ArrayDeque<Long>();for(long root:roots)if(root>=2)pending.add(root);
            while(!pending.isEmpty()){long id=pending.removeFirst();if(!result.add(id))continue;for(long child:edges.get(id))if(child>=2)pending.add(child);}
            return result;
        }
    }
    @Test void duplicateEdgesAndRootReplacementMatchIndependentReachability(){
        var memory=memory();var graph=new Graph();
        try(var pages=new ResidentPageStore(128,memory,AnalysisResources.Phase.CONTROL)){
            try(var ownership=new PagedDagOwnership(pages,memory,AnalysisResources.Phase.CONTROL,graph)){
                graph.put(2);ownership.created(2);graph.put(3,2,2);ownership.created(3);
                graph.put(4,2);ownership.created(4);graph.put(5,3,4);ownership.created(5);
                long root=ownership.root(5);ownership.commitCreated();assertEquals(graph.reachable(5),graph.alive);
                ownership.bind(root,3);assertEquals(graph.reachable(3),graph.alive);
                ownership.bind(root,2);assertEquals(graph.reachable(2),graph.alive);
                ownership.closeRoot(root);ownership.closeRoot(root);assertTrue(graph.alive.isEmpty());
                assertThrows(IllegalStateException.class,()->ownership.bind(root,1));
            }
            assertEquals(0,pages.statistics().livePages());
        }
        assertEquals(0,memory.heapUsed());
    }
    @Test void replacedIndependentVersionsRetireImmediatelyWithoutScanningHistory(){
        var memory=memory();var graph=new Graph();
        try(var pages=new ResidentPageStore(128,memory,AnalysisResources.Phase.CONTROL);
            var ownership=new PagedDagOwnership(pages,memory,AnalysisResources.Phase.CONTROL,graph)){
            long root=ownership.root(0);long workBefore=memory.workUsed();
            for(long id=2;id<2050;id++){
                graph.put(id);ownership.created(id);ownership.bind(root,id);ownership.commitCreated();
                assertEquals(Set.of(id),graph.alive,"current version="+id);assertEquals(id,ownership.value(root));
            }
            ownership.closeRoot(root);assertTrue(graph.alive.isEmpty());
            assertTrue(memory.workUsed()-workBefore<=4096L*2048,"independent versions must not rescan accumulated history");
        }
        assertEquals(0,memory.heapUsed());
    }
    @Test void sharedDagRootsAndUpdatesMatchAnIndependentTraversalOracle(){
        var memory=memory();var graph=new Graph();var random=new Random(893714);int count=256;
        try(var pages=new ResidentPageStore(128,memory,AnalysisResources.Phase.CONTROL);
            var ownership=new PagedDagOwnership(pages,memory,AnalysisResources.Phase.CONTROL,graph)){
            for(long id=2;id<count+2;id++){
                if(id<5)graph.put(id);else graph.put(id,2+random.nextInt((int)id-2),2+random.nextInt((int)id-2));
                ownership.created(id);
            }
            long[] tokens=new long[12],roots=new long[12];
            for(int i=0;i<tokens.length;i++){roots[i]=2+random.nextInt(count);tokens[i]=ownership.root(roots[i]);}
            ownership.commitCreated();assertEquals(graph.reachable(roots),graph.alive);
            for(int step=0;step<100;step++){
                int which=random.nextInt(tokens.length);long replacement=step%3==0?0:roots[random.nextInt(roots.length)];
                ownership.bind(tokens[which],replacement);roots[which]=replacement;
                assertEquals(graph.reachable(roots),graph.alive,"update="+step);
            }
            for(long token:tokens)ownership.closeRoot(token);assertTrue(graph.alive.isEmpty());
        }
        assertEquals(0,memory.heapUsed());
    }
    @Test void retiredIdsHaveNewGenerationsAndOldConstructionRecordsCannotReleaseThem(){
        var memory=memory();var graph=new Graph();
        try(var pages=new ResidentPageStore(128,memory,AnalysisResources.Phase.CONTROL);
            var ownership=new PagedDagOwnership(pages,memory,AnalysisResources.Phase.CONTROL,graph)){
            graph.put(2);ownership.created(2);long old=ownership.generation(2);ownership.abandonCreated(2);
            assertTrue(graph.alive.isEmpty());assertEquals(0,ownership.generation(2));
            graph.put(2);ownership.created(2);assertNotEquals(old,ownership.generation(2));
            long root=ownership.root(2);ownership.commitCreated();assertEquals(Set.of(2L),graph.alive);
            ownership.closeRoot(root);assertTrue(graph.alive.isEmpty());
        }
        assertEquals(0,memory.heapUsed());
    }
    @Test void nestedConstructionPublicationReleasesOnlyItsOwnUnpublishedNodes(){
        var memory=memory();var graph=new Graph();
        try(var pages=new ResidentPageStore(128,memory,AnalysisResources.Phase.CONTROL);
            var ownership=new PagedDagOwnership(pages,memory,AnalysisResources.Phase.CONTROL,graph)){
            graph.put(2);ownership.created(2);long checkpoint=ownership.constructionSize();
            graph.put(3,2);ownership.created(3);graph.put(4);ownership.created(4);
            long root=ownership.root(3);ownership.commitCreatedSince(checkpoint);
            assertEquals(Set.of(2L,3L),graph.alive);assertEquals(checkpoint,ownership.constructionSize());
            graph.put(5,3);ownership.created(5);ownership.bind(root,5);ownership.commitCreated();
            assertEquals(graph.reachable(5),graph.alive);ownership.closeRoot(root);assertTrue(graph.alive.isEmpty());
        }
        assertEquals(0,memory.heapUsed());
    }
    @Test void rootTokensRejectForeignOwnersWithoutAffectingEitherGraph(){
        var memory=memory();var a=new Graph();var b=new Graph();
        try(var pages=new ResidentPageStore(128,memory,AnalysisResources.Phase.CONTROL);
            var first=new PagedDagOwnership(pages,memory,AnalysisResources.Phase.CONTROL,a);
            var second=new PagedDagOwnership(pages,memory,AnalysisResources.Phase.CONTROL,b)){
            a.put(2);first.created(2);b.put(2);second.created(2);
            long left=first.root(2),right=second.root(2);assertNotEquals(left,right);
            first.commitCreated();second.commitCreated();
            assertThrows(IllegalArgumentException.class,()->first.bind(right,0));
            assertThrows(IllegalArgumentException.class,()->first.closeRoot(right));
            assertThrows(IllegalArgumentException.class,()->second.value(left));
            assertEquals(2,first.value(left));assertEquals(2,second.value(right));
            first.closeRoot(left);second.closeRoot(right);assertTrue(a.alive.isEmpty());assertTrue(b.alive.isEmpty());
        }
        assertEquals(0,memory.heapUsed());
    }
    @Test void retirementCallbackFailureAbortsRootReadsAndFutureBindings(){
        var memory=memory();PagedDagOwnership.Graph graph=new PagedDagOwnership.Graph(){
            public void children(long node,LongConsumer accept){}
            public void retire(long node){throw new IllegalStateException("synthetic retirement interruption");}
        };
        try(var pages=new ResidentPageStore(128,memory,AnalysisResources.Phase.CONTROL)){
            try(var ownership=new PagedDagOwnership(pages,memory,AnalysisResources.Phase.CONTROL,graph)){
                ownership.created(2);long root=ownership.root(2);ownership.commitCreated();
                assertThrows(IllegalStateException.class,()->ownership.bind(root,0));
                assertThrows(IllegalStateException.class,()->ownership.value(root));
                assertThrows(IllegalStateException.class,()->ownership.root(1));
            }
            assertEquals(0,pages.statistics().livePages());
        }
        assertEquals(0,memory.heapUsed());
    }
    @Test void deepRetirementIsIterativeAndTwoPassImportDoesNotAssumeNumericTopologicalIds(){
        var memory=memory();var graph=new Graph();int count=20000;
        try(var pages=new ResidentPageStore(128,memory,AnalysisResources.Phase.CONTROL)){
            try(var ownership=new PagedDagOwnership(pages,memory,AnalysisResources.Phase.CONTROL,graph)){
                for(long id=2;id<count+2;id++)graph.put(id,id==count+1?1:id+1);
                for(long id=2;id<count+2;id++)ownership.declare(id);
                for(long id=2;id<count+2;id++)ownership.linkDeclared(id);
                long root=ownership.root(2);ownership.commitCreated();assertEquals(count,graph.alive.size());
                ownership.closeRoot(root);assertTrue(graph.alive.isEmpty());
            }
            assertEquals(0,pages.statistics().livePages());
        }
        assertEquals(0,memory.heapUsed());
    }
}
