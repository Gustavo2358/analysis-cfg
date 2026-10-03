package io.github.gustavo2358.analysis.solver;

import io.github.gustavo2358.analysis.cfg.domain.CfgTransition;
import io.github.gustavo2358.analysis.structure.*;
import java.util.*;

/** Small-test oracle: concrete stacks and full synchronous recomposition, never production tabulation. */
public final class ExplicitActivationOracle {
    private record Edge(int from,int to,CfgTransition transition) { }
    public record State<S>(ContextView context,ProgramIndex.Node node,S in,S out) { }
    public static <S> List<State<S>> solve(AnalysisSession session,AnalysisDefinition<S> definition) {
        var points=new ArrayList<AnalysisPoint>();var edges=new ArrayList<Edge>();
        for(var context:session.contexts()) {
            var ids=new HashMap<ContextView.Point,Integer>();int start=points.size();
            ids.put(context.initialPoint(),start);points.add(new AnalysisPoint(start,context,context.initialPoint()));
            for(int i=start;i<points.size();i++) {
                var cursor=context.successors(points.get(i).traversal());
                while(cursor.advance()) {
                    Integer target=ids.get(cursor.target());
                    if(target==null){target=points.size();ids.put(cursor.target(),target);points.add(new AnalysisPoint(target,context,cursor.target()));}
                    edges.add(new Edge(i,target,cursor.transition()));
                }
            }
        }
        var initial=new ArrayList<S>(Collections.nCopies(points.size(),definition.bottom()));var work=new DomainWork();
        for(var boundary:definition.boundaries(session)) {
            var found=points.stream().filter(p->p.context()==boundary.context()&&p.node()==boundary.node()).toList();
            if(found.size()!=1)throw new IllegalArgumentException("oracle boundary must be unique");
            int i=found.getFirst().ordinal;initial.set(i,definition.joinInto(initial.get(i),boundary.state(),work).state());
        }
        var anchors=new ArrayList<>(initial);var published=new ArrayList<S>(Collections.nCopies(points.size(),definition.bottom()));
        boolean forward=definition.direction()==Direction.FORWARD;boolean changed=true;int rounds=0;
        while(changed) {
            if(rounds++>10000)throw new AssertionError("small oracle did not converge");
            var next=new ArrayList<>(initial);
            for(var edge:edges) {
                int source=forward?edge.from:edge.to,target=forward?edge.to:edge.from;
                var contribution=definition.transferEdge(points.get(source),edge.transition,published.get(source),work);
                next.set(target,definition.joinInto(next.get(target),contribution,work).state());
            }
            var output=new ArrayList<S>();changed=false;
            for(int i=0;i<points.size();i++) {
                var value=definition.transferBlock(points.get(i),next.get(i),work);output.add(value);
                changed|=!definition.equivalent(anchors.get(i),next.get(i),work)||!definition.equivalent(published.get(i),value,work);
            }
            anchors=next;published=output;
        }
        var result=new ArrayList<State<S>>();
        for(int i=0;i<points.size();i++)result.add(new State<>(points.get(i).context(),points.get(i).node(),forward?anchors.get(i):published.get(i),forward?published.get(i):anchors.get(i)));
        return result;
    }
}
