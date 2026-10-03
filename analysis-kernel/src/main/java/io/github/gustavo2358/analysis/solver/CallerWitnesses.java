package io.github.gustavo2358.analysis.solver;

import java.util.*;
import java.util.function.ToIntFunction;

/** Verified caller paths used only to answer positive feasibility queries.
 * A miss requires the exact symbolic search. Build after subscriptions stabilize. */
final class CallerWitnesses<R> {
    record Edge<R>(R child,int condition) { }
    private record Candidate<R>(R region,BitSet active) { }
    private final BooleanConditions conditions;
    private final Map<R,List<BitSet>> witnesses=new IdentityHashMap<>();

    CallerWitnesses(BooleanConditions conditions,int variables,R root,
                    Map<R,List<Edge<R>>> successors,ToIntFunction<R> variable) {
        this.conditions=conditions;
        var present=new IdentityHashMap<R,BitSet>();var absent=new IdentityHashMap<R,BitSet>();
        var pending=new ArrayDeque<Candidate<R>>();pending.add(new Candidate<>(root,new BitSet()));
        while(!pending.isEmpty()) {
            var candidate=pending.removeFirst();var parent=candidate.region();var incoming=candidate.active();
            var newPresent=(BitSet)incoming.clone();var newAbsent=(BitSet)incoming.clone();newAbsent.flip(0,variables);
            if(witnesses.containsKey(parent)) {
                newPresent.andNot(present.get(parent));newAbsent.andNot(absent.get(parent));
                if(newPresent.isEmpty()&&newAbsent.isEmpty())continue;
            }
            // Each retained path adds a previously unwitnessed literal. At most
            // 2*variables+1 paths per region survive, regardless of stack count.
            witnesses.computeIfAbsent(parent,r->new ArrayList<>()).add(incoming);
            present.computeIfAbsent(parent,r->new BitSet()).or(newPresent);
            absent.computeIfAbsent(parent,r->new BitSet()).or(newAbsent);
            var active=(BitSet)incoming.clone();int key=variable.applyAsInt(parent);if(key>=0)active.set(key);
            for(var edge:successors.getOrDefault(parent,List.of()))if(conditions.test(edge.condition(),incoming))
                pending.addLast(new Candidate<>(edge.child(),active));
        }
    }
    boolean matches(R region,int condition) {
        for(var witness:witnesses.getOrDefault(region,List.of()))if(conditions.test(condition,witness))return true;
        return false;
    }
    int size(R region){return witnesses.getOrDefault(region,List.of()).size();}
}
