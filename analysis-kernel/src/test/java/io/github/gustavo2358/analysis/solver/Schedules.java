package io.github.gustavo2358.analysis.solver;

import java.util.*;
import java.util.function.Function;

final class Schedules {
    static Function<SolverTopology,IntWorklist> ordered(int kind) {
        return graph -> {
            List<Integer> order=new ArrayList<>();
            for(int p=0;p<graph.points.length;p++) order.add(p);
            if(kind==1) Collections.reverse(order);
            if(kind==2) Collections.shuffle(order,new Random(72319));
            int[] ranks=new int[order.size()]; for(int r=0;r<order.size();r++) ranks[order.get(r)]=r;
            return priority(ranks);
        };
    }
    static IntWorklist priority(int[] ranks) {
        return new IntWorklist() {
            final PriorityQueue<Integer> queue=new PriorityQueue<>(Comparator.comparingInt(p->ranks[p]));
            public void add(int p) { queue.add(p); }
            public int remove() { return queue.remove(); }
            public int size() { return queue.size(); }
        };
    }
}
