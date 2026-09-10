package io.github.gustavo2358.analysis.solver;

import io.github.gustavo2358.analysis.cfg.domain.CfgTransition;
import io.github.gustavo2358.analysis.structure.*;
import java.util.*;

/** Test-only use of the existing W2 scheduling seam; no engine or production source changes. */
public final class WideScheduleBridge {
    private WideScheduleBridge() { }
    public record Measured<S>(DataflowResult<S> result,long joinDeliveries,long joinTransfers) { }
    public static <S> Measured<S> solve(AnalysisSession session,AnalysisDefinition<S> definition,ProgramIndex.Node join) {
        long[] counts=new long[2];
        AnalysisDefinition<S> counted=new AnalysisDefinition<>() {
            public Direction direction(){return definition.direction();}public S bottom(){return definition.bottom();}
            public Iterable<Boundary<S>> boundaries(AnalysisSession s){return definition.boundaries(s);}
            public Join<S> joinInto(S a,S b,DomainWork w){return definition.joinInto(a,b,w);}
            public boolean equivalent(S a,S b,DomainWork w){return definition.equivalent(a,b,w);}
            public S transferBlock(AnalysisPoint p,S s,DomainWork w){if(p.node()==join)counts[1]++;return definition.transferBlock(p,s,w);}
            public S transferEdge(AnalysisPoint p,CfgTransition e,S s,DomainWork w){if(e.to().equals(join.source().id()))counts[0]++;return definition.transferEdge(p,e,s,w);}
        };
        var result=DataflowSolver.solve(session,counted,graph->new IntWorklist() {
            final PriorityQueue<Integer> queue=new PriorityQueue<>(Comparator.comparingInt(p->graph.points[p].node()==join?-1:p));
            public void add(int p){queue.add(p);}public int remove(){return queue.remove();}public int size(){return queue.size();}
        });
        return new Measured<>(result,counts[0],counts[1]);
    }
}
