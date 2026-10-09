package io.github.gustavo2358.analysis.solver;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SummaryCollectorTest {
    static final class Node { final int id;long mark;final List<Node> links=new ArrayList<>();Node(int id){this.id=id;} }
    @Test void rootedSccsAndSharedTargetsSurviveWhileUnrootedCyclesAreRetired() {
        var random=new Random(22173);
        for(int round=0;round<60;round++) {
            int n=1+random.nextInt(50);var all=new ArrayList<Node>();for(int i=0;i<n;i++)all.add(new Node(i));
            for(var node:all)for(var target:all)if(random.nextInt(12)==0)node.links.add(target);
            var roots=new ArrayList<Node>();for(var node:all)if(random.nextInt(8)==0)roots.add(node);
            var expected=Collections.newSetFromMap(new IdentityHashMap<Node,Boolean>());var queue=new ArrayDeque<Node>(roots);
            while(!queue.isEmpty()){var node=queue.removeFirst();if(expected.add(node))queue.addAll(node.links);}
            var resources=StateIndexTest.resources(Long.MAX_VALUE);var retired=Collections.newSetFromMap(new IdentityHashMap<Node,Boolean>());
            try(var collector=new SummaryCollector<Node>(resources,AnalysisResources.Phase.DOMAIN,x->x.mark,(x,token)->x.mark=token)) {
                int original=all.size();long count=collector.collect(all,visit->{for(var root:roots)visit.accept(root);},(node,visit)->{for(var next:node.links)visit.accept(next);},retired::add);
                assertEquals(expected,new HashSet<>(all));assertEquals(original-expected.size(),count);assertEquals(count,retired.size());
                assertEquals(expected,all.stream().filter(collector::isMarked).collect(java.util.stream.Collectors.toSet()));
            }
            assertEquals(0,resources.heapUsed());
        }
    }
    @Test void freshCollectorsCannotConfuseOldMarksAndFailedScratchAdmissionPreservesInventory() {
        var all=new ArrayList<Node>();for(int i=0;i<50;i++)all.add(new Node(i));for(int i=1;i<50;i++)all.get(i-1).links.add(all.get(i));
        var resources=StateIndexTest.resources(Long.MAX_VALUE);
        for(int pass=0;pass<2;pass++)try(var collector=new SummaryCollector<Node>(resources,AnalysisResources.Phase.DOMAIN,x->x.mark,(x,token)->x.mark=token)) {
            assertEquals(0,collector.collect(all,visit->visit.accept(all.getFirst()),(node,visit)->{for(var next:node.links)visit.accept(next);},x->{throw new AssertionError("reachable root retired");}));
            assertEquals(50,all.size());
        }
        assertEquals(0,resources.heapUsed());var small=StateIndexTest.resources(500);
        try(var collector=new SummaryCollector<Node>(small,AnalysisResources.Phase.DOMAIN,x->x.mark,(x,token)->x.mark=token)) {
            assertThrows(AnalysisResources.Exhausted.class,()->collector.collect(all,visit->{for(var node:all)visit.accept(node);},(node,visit)->{},x->{throw new AssertionError("failed admission retired a vertex");}));
            assertEquals(50,all.size());
            assertThrows(IllegalStateException.class,()->collector.isMarked(all.getFirst()));
            assertThrows(IllegalStateException.class,()->collector.retain(all,x->{throw new AssertionError("incomplete marks authorized retirement");}));
            assertEquals(50,all.size());
        }
        assertEquals(0,small.heapUsed());
    }
}
