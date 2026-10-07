package io.github.gustavo2358.analysis.solver;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CallerPathCertificatesTest {
    private static AnalysisResources resources(){return new AnalysisResources(new AnalysisResources.Limits(Long.MAX_VALUE,Long.MAX_VALUE,Long.MAX_VALUE,Long.MAX_VALUE,Long.MAX_VALUE,Long.MAX_VALUE,Long.MAX_VALUE));}
    @Test void differentIncomingWordsNeverBecomeOneJointWitness() {
        var b=new BooleanConditions();var memory=resources();
        try(var graph=new CallerPathCertificates(b,memory,null)) {
            var root=graph.node(true);var a=graph.node(false);var end=graph.node(false);
            graph.add(root,a,1,1);graph.add(a,end,2,1);graph.add(root,end,3,1);
            assertTrue(graph.matches(end,b.variable(1)));assertTrue(graph.matches(end,b.variable(2)));assertTrue(graph.matches(end,b.variable(3)));
            assertFalse(graph.matches(end,b.and(b.variable(1),b.variable(3))));
            assertTrue(graph.matches(end,b.and(b.variable(1),b.not(b.variable(3)))));
        }
        assertEquals(0,memory.heapUsed());
    }
    @Test void replacingParallelBindingsChangesTheirActualKeys() {
        var b=new BooleanConditions();
        try(var graph=new CallerPathCertificates(b,resources(),null)) {
            var root=graph.node(true);var child=graph.node(false);
            var first=graph.add(root,child,4,1);var second=graph.add(root,child,5,1);
            assertTrue(graph.matches(child,b.variable(4)));assertTrue(graph.matches(child,b.variable(5)));
            graph.remove(first);assertFalse(graph.matches(child,b.variable(4)));assertTrue(graph.matches(child,b.variable(5)));
            graph.remove(second);assertFalse(graph.matches(child,1));
        }
    }
    @Test void guardedAlternativeIsTestedOnItsOwnParentAndDoesNotRootADisconnectedCycle() {
        var b=new BooleanConditions();
        try(var graph=new CallerPathCertificates(b,resources(),null)) {
            var root=graph.node(true);var child=graph.node(false);var outer=graph.add(root,child,1,1);
            graph.add(child,child,2,b.and(b.variable(1),b.not(b.variable(2))));
            assertTrue(graph.matches(child,b.variable(2)));assertTrue(graph.matches(child,b.not(b.variable(2))));
            assertFalse(graph.matches(child,b.and(b.variable(2),b.not(b.variable(1)))));
            graph.remove(outer);assertFalse(graph.matches(child,1));assertFalse(graph.matches(child,b.variable(2)));
        }
    }
    @Test void repairingAnAncestorUpdatesAllDependentWordsAndBorrowedPagesStayUsable() {
        var b=new BooleanConditions();var memory=resources();
        try(var pages=new ResidentPageStore(4096,memory,AnalysisResources.Phase.CONTROL)) {
            try(var graph=new CallerPathCertificates(b,memory,pages)) {
                var root=graph.node(true);var parent=graph.node(false);var child=graph.node(false);
                var first=graph.add(root,parent,7,1);graph.add(root,parent,8,1);graph.add(parent,child,9,1);
                assertTrue(graph.matches(child,b.variable(7)));assertTrue(graph.matches(child,b.variable(9)));
                graph.remove(first);assertFalse(graph.matches(child,b.variable(7)));assertTrue(graph.matches(child,b.variable(8)));
                graph.retire(child);assertFalse(graph.matches(child,1));
            }
            long page=pages.allocate();pages.release(page);
        }
        assertEquals(0,memory.heapUsed());
    }
    private record Binding(int from,int to,int key,int required,boolean present,CallerPathCertificates.Arc arc) { }
    @Test void everyPositiveHintBelongsToAnIndependentConcreteWordAfterMutations() {
        int count=6,keys=5;var b=new BooleanConditions();var random=new Random(430991);
        try(var graph=new CallerPathCertificates(b,resources(),null)) {
            var nodes=new CallerPathCertificates.Node[count];for(int i=0;i<count;i++)nodes[i]=graph.node(i==0);
            var active=new ArrayList<Binding>();
            for(int iteration=0;iteration<600;iteration++) {
                int action=random.nextInt(3);
                if(active.isEmpty()||action==0) {
                    int from=random.nextInt(count),to=1+random.nextInt(count-1),key=random.nextInt(keys),required=random.nextInt(keys);boolean present=random.nextBoolean();
                    int guard=b.and(b.not(b.variable(key)),present?b.variable(required):b.not(b.variable(required)));
                    var arc=graph.add(nodes[from],nodes[to],key,guard);active.add(new Binding(from,to,key,required,present,arc));
                }else if(action==1){var removed=active.remove(random.nextInt(active.size()));graph.remove(removed.arc());}
                else {
                    int index=random.nextInt(active.size());var old=active.get(index);int required=random.nextInt(keys);boolean present=random.nextBoolean();
                    int guard=b.and(b.not(b.variable(old.key())),present?b.variable(required):b.not(b.variable(required)));
                    graph.update(old.arc(),guard);active.set(index,new Binding(old.from(),old.to(),old.key(),required,present,old.arc()));
                }
                var reached=new boolean[count][1<<keys];reached[0][0]=true;var queue=new ArrayDeque<int[]>();queue.add(new int[]{0,0});
                while(!queue.isEmpty()) {
                    var state=queue.removeFirst();
                    for(var edge:active)if(edge.from()==state[0]&&(state[1]&(1<<edge.key()))==0&&(((state[1]&(1<<edge.required()))!=0)==edge.present())) {
                        int next=state[1]|(1<<edge.key());if(!reached[edge.to()][next]){reached[edge.to()][next]=true;queue.add(new int[]{edge.to(),next});}
                    }
                }
                for(int node=0;node<count;node++)for(int a=0;a<keys;a++)for(int c=0;c<keys;c++) {
                    int condition=b.and(b.variable(a),b.not(b.variable(c)));boolean expected=false;
                    for(int word=0;word<(1<<keys);word++)if(reached[node][word]&&(word&(1<<a))!=0&&(word&(1<<c))==0){expected=true;break;}
                    if(graph.matches(nodes[node],condition))assertTrue(expected,"invented joint word iteration="+iteration+" node="+node+" keys="+a+","+c);
                }
            }
        }
    }
    @Test void sparseDeepCertificatesSharePrimitiveWordsInsteadOfDenseUniverseVectors() {
        var b=new BooleanConditions();var memory=resources();
        try(var graph=new CallerPathCertificates(b,memory,null)) {
            var current=graph.node(true);
            for(int i=0;i<64;i++){var next=graph.node(false);graph.add(current,next,i*16384,1);current=next;}
            assertTrue(graph.matches(current,b.and(b.variable(0),b.variable(63*16384))));
            assertTrue(memory.heapUsed()<500000,"retained certificate bytes="+memory.heapUsed());
        }
        assertEquals(0,memory.heapUsed());
    }

    @Test void guardReplacementPreservesStillValidProofsAndRepairsInvalidatedDescendants() {
        var b=new BooleanConditions();
        try(var graph=new CallerPathCertificates(b,resources(),null)) {
            var root=graph.node(true);var parent=graph.node(false);var first=graph.add(root,parent,4,1);graph.add(root,parent,6,1);
            var end=parent;for(int i=0;i<512;i++){var next=graph.node(false);graph.add(end,next,20+i,1);end=next;}
            long before=graph.edgesRead();graph.update(first,b.not(b.variable(7)));
            assertTrue(graph.edgesRead()-before<=4,"an accepted word must not be rebuilt throughout its subtree");
            assertTrue(graph.matches(end,b.variable(4)));
            graph.update(first,b.variable(7));
            assertFalse(graph.matches(end,b.variable(4)));assertTrue(graph.matches(end,b.variable(6)));
        }
    }

    @Test void disconnectedCallerCyclesHaveAnExactNegativeSupportAndWakeOnReconnection() {
        var b=new BooleanConditions();var aEvents=new ArrayList<Integer>();var cEvents=new ArrayList<Integer>();
        try(var graph=new CallerPathCertificates(b,resources(),null)) {
            var root=graph.node(true);var a=graph.node(false,aEvents::add);var c=graph.node(false,cEvents::add);
            graph.add(a,c,1,1);graph.add(c,a,2,1);
            assertFalse(graph.rawReached(a));assertFalse(graph.rawReached(c));
            var link=graph.add(root,c,3,b.variable(7));
            assertTrue(graph.rawReached(a));assertTrue(graph.rawReached(c));
            assertFalse(graph.matches(a,1),"a root-connected structural superset must not invent a guarded witness");
            assertEquals(List.of(1),aEvents);assertEquals(List.of(1),cEvents);
            graph.update(link,0);assertFalse(graph.rawReached(a));assertFalse(graph.rawReached(c));
            graph.update(link,1);assertTrue(graph.matches(a,1));assertTrue(graph.matches(c,1));
            assertEquals(List.of(1,0,1),aEvents);assertEquals(List.of(1,0,1),cEvents);
        }
    }

    private record RawBinding(int from,int to,CallerPathCertificates.Arc arc) { }
    @Test void rawSupportMatchesIndependentSearchAfterFourThousandBindingMutations() {
        int count=24;var b=new BooleanConditions();var memory=resources();var random=new Random(82340);
        try(var graph=new CallerPathCertificates(b,memory,null)) {
            var nodes=new CallerPathCertificates.Node[count];for(int i=0;i<count;i++)nodes[i]=graph.node(i==0);
            var active=new ArrayList<RawBinding>();
            for(int iteration=0;iteration<4000;iteration++) {
                int action=random.nextInt(3);
                if(active.isEmpty()||action==0) {
                    int from=random.nextInt(count),to=random.nextInt(count);
                    var arc=graph.add(nodes[from],nodes[to],random.nextInt(5),random.nextBoolean()?1:b.variable(9));
                    active.add(new RawBinding(from,to,arc));
                }else if(action==1)graph.remove(active.remove(random.nextInt(active.size())).arc());
                else {var edge=active.get(random.nextInt(active.size()));graph.update(edge.arc(),random.nextBoolean()?0:b.not(b.variable(8)));}
                var expected=new boolean[count];expected[0]=true;var queue=new ArrayDeque<Integer>();queue.add(0);
                while(!queue.isEmpty()) {
                    int parent=queue.remove();
                    for(var edge:active)if(edge.from()==parent&&edge.arc().condition!=0&&!expected[edge.to()]) {
                        expected[edge.to()]=true;queue.add(edge.to());
                    }
                }
                for(int i=0;i<count;i++)assertEquals(expected[i],graph.rawReached(nodes[i]),"iteration="+iteration+" vertex="+i);
            }
        }
        assertEquals(0,memory.heapUsed());
    }

    @Test void allocationFailuresReleaseOwnedRootsAndAllReservedCapacity() {
        int failures=0;
        for(long heap:new long[]{0,128,512,1024,2048,4096,8192,16384,32768,65536}) {
            var memory=new AnalysisResources(new AnalysisResources.Limits(heap,Long.MAX_VALUE,Long.MAX_VALUE,Long.MAX_VALUE,Long.MAX_VALUE,Long.MAX_VALUE,Long.MAX_VALUE));
            try(var graph=new CallerPathCertificates(new BooleanConditions(),memory,null)) {
                var current=graph.node(true);
                for(int i=0;i<256;i++){var child=graph.node(false);graph.add(current,child,i,1);current=child;}
            }catch(AnalysisResources.Exhausted expected){failures++;}
            assertEquals(0,memory.heapUsed(),"quota="+heap);
        }
        assertEquals(10,failures,"every selected quota must exercise interruption and cleanup");
    }

    @Test void disjunctivePredicatesUseAnIndividualIndexedWordWithoutJoiningAlternatives() {
        var b=new BooleanConditions();
        try(var graph=new CallerPathCertificates(b,resources(),null)) {
            var root=graph.node(true);var child=graph.node(false);graph.add(root,child,5,1);
            graph.add(child,child,2,b.not(b.variable(2)));graph.add(child,child,3,b.not(b.variable(3)));
            assertTrue(graph.matches(child,b.or(b.variable(2),b.variable(3))),"either actual individual binding word is sufficient");
            assertFalse(graph.matches(child,b.and(b.variable(2),b.variable(3))),"indexed alternatives must never turn into one joint word");
            assertFalse(graph.matches(child,b.and(b.or(b.variable(2),b.variable(3)),b.not(b.variable(5)))),"the whole predicate must still hold on that word");
        }
    }

    @Test void growingDisjunctionHintsDoNotRewalkThePrimaryWordForEveryAlternative() {
        int count=128;var b=new BooleanConditions();
        try(var graph=new CallerPathCertificates(b,resources(),null)) {
            var root=graph.node(true);var child=graph.node(false);graph.add(root,child,1000000,1);
            for(int key=0;key<count;key++)graph.add(child,child,key,b.not(b.variable(key)));
            long before=graph.valuationNodeVisits();int predicate=0;
            for(int key=0;key<count;key++){predicate=b.or(predicate,b.variable(key));assertTrue(graph.matches(child,predicate));}
            assertTrue(graph.valuationNodeVisits()-before<=32L*count,"primary-prefix valuation visits="+(graph.valuationNodeVisits()-before));
        }
    }

}
