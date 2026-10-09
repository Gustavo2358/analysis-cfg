package io.github.gustavo2358.analysis.solver;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CallerConnectivityTest {
    @Test void changingLinksMatchesIndependentRootSearchIncludingDisconnectedCycles() {
        int count=24;var graph=new CallerConnectivity();var nodes=new CallerConnectivity.Node[count];
        for(int i=0;i<count;i++)nodes[i]=graph.node(i==0);
        var edges=new int[count][count];var random=new Random(82340);
        for(int iteration=0;iteration<4000;iteration++) {
            int from=random.nextInt(count),to=random.nextInt(count);
            if(random.nextBoolean()){graph.add(nodes[from],nodes[to]);edges[from][to]++;}
            else if(edges[from][to]>0){graph.remove(nodes[from],nodes[to]);edges[from][to]--;}
            var expected=new boolean[count];expected[0]=true;var queue=new ArrayDeque<Integer>();queue.add(0);
            while(!queue.isEmpty()){int parent=queue.remove();for(int child=0;child<count;child++)if(edges[parent][child]>0&&!expected[child]){expected[child]=true;queue.add(child);}}
            for(int i=0;i<count;i++)assertEquals(expected[i],nodes[i].reached(),"iteration="+iteration+" vertex="+i);
        }
    }
    @Test void removingTheLastExternalCallerDisconnectsAnEntireCycleThenReattachesIt() {
        var graph=new CallerConnectivity();var root=graph.node(true);var a=graph.node(false);var b=graph.node(false);
        graph.add(root,a);graph.add(a,b);graph.add(b,a);graph.add(root,a);
        graph.remove(root,a);assertTrue(a.reached());assertTrue(b.reached());
        graph.remove(root,a);assertFalse(a.reached());assertFalse(b.reached());
        graph.add(root,b);assertTrue(a.reached());assertTrue(b.reached());
        graph.remove(a,b);assertTrue(a.reached());assertTrue(b.reached());
    }
}
