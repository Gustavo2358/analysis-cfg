package io.github.gustavo2358.analysis.solver;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CallerWitnessesTest {
    @Test void separateWitnessesNeverInventACombinedCaller() {
        var b=new BooleanConditions();Object root=new Object(),left=new Object(),right=new Object(),join=new Object();
        var edges=new IdentityHashMap<Object,List<CallerWitnesses.Edge<Object>>>();
        edges.put(root,List.of(new CallerWitnesses.Edge<>(left,1),new CallerWitnesses.Edge<>(right,1)));
        edges.put(left,List.of(new CallerWitnesses.Edge<>(join,1)));edges.put(right,List.of(new CallerWitnesses.Edge<>(join,1)));
        var cache=new CallerWitnesses<>(b,2,root,edges,r->r==left?0:r==right?1:-1);
        assertTrue(cache.matches(join,b.variable(0)));assertTrue(cache.matches(join,b.variable(1)));
        assertFalse(cache.matches(join,b.and(b.variable(0),b.variable(1))));
        assertFalse(cache.matches(join,b.not(b.or(b.variable(0),b.variable(1)))));
        assertFalse(cache.matches(new Object(),1));
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
            var cache=new CallerWitnesses<>(b,variables,nodes[0],edges,keys::get);
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
