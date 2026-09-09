package io.github.gustavo2358.analysis.solver;

import io.github.gustavo2358.analysis.structure.*;
import java.util.*;

/** Independent round solver: re-reads all neighbors on every visit, intentionally slow/test-only. */
final class RecompositionOracle<S> {
    record Key(ContextView context, ProgramIndex.Node node) { }
    final Map<Key,S> in=new LinkedHashMap<>(), out=new LinkedHashMap<>();
    long predecessorReads, successorReads, visits;
    RecompositionOracle(SyntheticAnalyses.Program program,AnalysisDefinition<S> definition) {
        List<Key> points=new ArrayList<>();
        for(var context:program.contexts) for(var node:program.reachable(context)) points.add(new Key(context,node));
        for(var p:points) { in.put(p,definition.bottom()); out.put(p,definition.bottom()); }
        DomainWork work=new DomainWork();
        boolean changed;
        do {
            changed=false;
            for(var p:points) {
                visits++;
                S value=definition.bottom();
                for(var boundary:definition.boundaries(program.session))
                    if(boundary.context()==p.context && boundary.node()==p.node) value=definition.joinInto(value,boundary.state(),work).state();
                boolean forward=definition.direction()==Direction.FORWARD;
                var cursor=forward?p.context.predecessors(p.node):p.context.successors(p.node);
                while(cursor.advance()) {
                    var neighbor=new Key(p.context,forward?cursor.source():cursor.target());
                    if(!in.containsKey(neighbor)) continue;
                    S input;
                    if(forward) { predecessorReads++; input=out.get(neighbor); }
                    else { successorReads++; input=in.get(neighbor); }
                    value=definition.joinInto(value,definition.transferEdge(new AnalysisPoint(-1,neighbor.context,neighbor.node),cursor.transition(),input,work),work).state();
                }
                S transformed=definition.transferBlock(new AnalysisPoint(-1,p.context,p.node),value,work);
                S before=forward?value:transformed, after=forward?transformed:value;
                if(!definition.equivalent(before,in.get(p),work)||!definition.equivalent(after,out.get(p),work)) changed=true;
                in.put(p,before); out.put(p,after);
            }
        } while(changed);
    }
    void compare(DataflowResult<S> actual) {
        for(var p:in.keySet()) {
            org.junit.jupiter.api.Assertions.assertEquals(in.get(p),actual.in(p.context,p.node),"recomposition IN");
            org.junit.jupiter.api.Assertions.assertEquals(out.get(p),actual.out(p.context,p.node),"recomposition OUT");
        }
    }
}
