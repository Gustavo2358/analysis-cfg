package io.github.gustavo2358.analysis.solver;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CallerWitnessesTest {
    @Test void queuedPathsAndSeparateWitnessesSurviveCollectionOfOldLiteralSummaries() {
        int count=256;Object root=new Object(),join=new Object(),impossible=new Object();var b=new BooleanConditions();
        var edges=new IdentityHashMap<Object,List<CallerWitnesses.Edge<Object>>>();var branches=new ArrayList<CallerWitnesses.Edge<Object>>();
        for(int i=0;i<count;i++) {
            Object branch=new Object();branches.add(new CallerWitnesses.Edge<>(branch,1,i));
            edges.put(branch,List.of(new CallerWitnesses.Edge<>(join,1,-1)));
        }
        edges.put(root,branches);edges.put(join,List.of(new CallerWitnesses.Edge<>(impossible,b.and(b.variable(0),b.variable(1)),-1)));
        try(var cache=new CallerWitnesses<>(b,count,root,edges,r->-1)) {
            assertEquals(count,cache.size(join));assertEquals(0,cache.size(impossible));
            for(int i=0;i<count;i++)assertTrue(cache.matches(join,b.variable(i)));
            assertFalse(cache.matches(join,b.and(b.variable(0),b.variable(1))));
            assertTrue(cache.matches(join,b.not(b.variable(0))));
            assertTrue(cache.retiredValuationRecords()>0);assertTrue(cache.collections()>1);
        }
    }
    @Test void queueGrowthDenialReleasesAllPrivatePagesWithoutClosingBorrowedStore() {
        Object root=new Object();var b=new BooleanConditions();var branches=new ArrayList<CallerWitnesses.Edge<Object>>();
        for(int i=0;i<9;i++)branches.add(new CallerWitnesses.Edge<>(new Object(),1,i));
        var resources=new AnalysisResources(new AnalysisResources.Limits(8_000_000,512,0,0,0,1_000_000,0));
        try(var pages=new ResidentPageStore(512,resources)) {
            var failure=assertThrows(AnalysisResources.Exhausted.class,()->new CallerWitnesses<>(b,9,root,Map.of(root,branches),r->-1,resources,pages));
            assertEquals(AnalysisResources.Resource.SCRATCH,failure.resource());assertEquals(512,failure.requested());
            assertEquals(0,resources.used(AnalysisResources.Pool.SCRATCH));assertEquals(0,pages.statistics().livePages());
            long page=pages.allocate();pages.release(page);
        }
        assertEquals(0,resources.heapUsed());
    }
    @Test void sparseDeepPrefixesShareStorageInsteadOfCopyingTheKeyUniverse() {
        int count=64,stride=16384,variables=count*stride+1;
        var b=new BooleanConditions();var nodes=new Object[count+1];Arrays.setAll(nodes,i->new Object());
        var edges=new IdentityHashMap<Object,List<CallerWitnesses.Edge<Object>>>();
        for(int i=0;i<count;i++)edges.put(nodes[i],List.of(new CallerWitnesses.Edge<>(nodes[i+1],1,i*stride)));
        try(var cache=new CallerWitnesses<>(b,variables,nodes[0],edges,r->-1)) {
        for(int i=0;i<=count;i++) {
            assertEquals(1,cache.size(nodes[i]));
            for(int key=0;key<count;key++)assertEquals(key<i,cache.matches(nodes[i],b.variable(key*stride)));
            assertTrue(cache.matches(nodes[i],b.not(b.variable(variables-1))));
        }
        assertTrue(cache.retainedValuationBytes()<500_000,"whole-prefix valuations retained "+cache.retainedValuationBytes());
        }
    }
    @Test void ownersReleaseRetentionsAndQuotaFailureLeavesBorrowedPagesUsable() {
        Object root=new Object(),child=new Object();var b=new BooleanConditions();
        Map<Object,List<CallerWitnesses.Edge<Object>>> edges=Map.of(root,List.of(new CallerWitnesses.Edge<>(child,1,0)));
        for(long scratch:new long[]{0,8192}) {
            var resources=new AnalysisResources(new AnalysisResources.Limits(8_000_000,scratch,0,0,0,1_000_000,0));
            try(var pages=new ResidentPageStore(512,resources)) {
                long before=resources.heapUsed();
                if(scratch==0)assertThrows(AnalysisResources.Exhausted.class,()->new CallerWitnesses<>(b,1,root,edges,r->-1,resources,pages));
                else {
                    var cache=new CallerWitnesses<>(b,1,root,edges,r->-1,resources,pages);
                    assertTrue(cache.matches(child,b.variable(0)));cache.close();cache.close();
                    assertThrows(IllegalStateException.class,()->cache.matches(child,1));
                    // Payload capacity belongs to the borrowed resident store until close.
                    assertEquals(0,pages.statistics().livePages());
                }
                assertEquals(0,resources.used(AnalysisResources.Pool.SCRATCH));
                assertTrue(resources.heapUsed()>=before);long page=pages.allocate();pages.release(page);
            }
            assertEquals(0,resources.heapUsed());
        }
    }
    @Test void separateWitnessesNeverInventACombinedCaller() {
        var b=new BooleanConditions();Object root=new Object(),left=new Object(),right=new Object(),join=new Object();
        var edges=new IdentityHashMap<Object,List<CallerWitnesses.Edge<Object>>>();
        edges.put(root,List.of(new CallerWitnesses.Edge<>(left,1),new CallerWitnesses.Edge<>(right,1)));
        edges.put(left,List.of(new CallerWitnesses.Edge<>(join,1)));edges.put(right,List.of(new CallerWitnesses.Edge<>(join,1)));
        try(var cache=new CallerWitnesses<>(b,2,root,edges,r->r==left?0:r==right?1:-1)) {
        assertTrue(cache.matches(join,b.variable(0)));assertTrue(cache.matches(join,b.variable(1)));
        assertFalse(cache.matches(join,b.and(b.variable(0),b.variable(1))));
        assertFalse(cache.matches(join,b.not(b.or(b.variable(0),b.variable(1)))));
        assertFalse(cache.matches(new Object(),1));
        }
    }
    @Test void cachedValuationsHaveExplicitPathsAndStayBoundedInCyclicGraphs() {
        record Arc(int from,int to,int required,int forbidden) { }
        record State(int node,int active) { }
        int variables=5,count=variables+1;
        for(int seed=0;seed<80;seed++) {
            var b=new BooleanConditions();var random=new Random(seed);var nodes=new Object[count];Arrays.setAll(nodes,i->new Object());
            var keys=new IdentityHashMap<Object,Integer>();for(int i=0;i<count;i++)keys.put(nodes[i],i-1);
            var edges=new IdentityHashMap<Object,List<CallerWitnesses.Edge<Object>>>();var arcs=new ArrayList<Arc>();
            for(int from=0;from<count;from++)for(int to=1;to<count;to++)if(random.nextBoolean()) {
                int required=random.nextInt(1<<variables),forbidden=random.nextInt(1<<variables)&~required;
                if(random.nextBoolean())required=0;
                int condition=1;for(int key=0;key<variables;key++) {
                    if((required&(1<<key))!=0)condition=b.and(condition,b.variable(key));
                    if((forbidden&(1<<key))!=0)condition=b.and(condition,b.not(b.variable(key)));
                }
                arcs.add(new Arc(from,to,required,forbidden));
                edges.computeIfAbsent(nodes[from],n->new ArrayList<>()).add(new CallerWitnesses.Edge<>(nodes[to],condition));
            }
            // Independent finite reachability over concrete bit masks, without BDD operations.
            var reached=new HashSet<State>();var pending=new ArrayDeque<State>();pending.add(new State(0,0));
            while(!pending.isEmpty()) {
                var state=pending.removeFirst();if(!reached.add(state))continue;
                for(var arc:arcs)if(arc.from()==state.node()&&(state.active()&arc.required())==arc.required()&&(state.active()&arc.forbidden())==0)
                    pending.addLast(new State(arc.to(),state.active()|(state.node()==0?0:1<<(state.node()-1))));
            }
            try(var cache=new CallerWitnesses<>(b,variables,nodes[0],edges,keys::get)) {
            for(int node=0;node<count;node++) {
                assertTrue(cache.size(nodes[node])<=2*variables+1);
                for(int mask=0;mask<(1<<variables);mask++) {
                    int condition=1;for(int key=0;key<variables;key++)condition=b.and(condition,(mask&(1<<key))==0?b.not(b.variable(key)):b.variable(key));
                    if(cache.matches(nodes[node],condition))assertTrue(reached.contains(new State(node,mask)),"invented witness seed="+seed);
                }
            }
            }
        }
    }
}
