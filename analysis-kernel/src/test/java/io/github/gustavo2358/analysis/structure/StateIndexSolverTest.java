package io.github.gustavo2358.analysis.structure;

import io.github.gustavo2358.analysis.cfg.domain.CfgTransition;
import io.github.gustavo2358.analysis.solver.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StateIndexSolverTest {
    @Test void registeredAndCollidingFingerprintsPreserveGeneratedExactStacksInBothDirections() {
        for(var direction:Direction.values())for(int seed=1;seed<=40;seed++) {
            var owner=ActivationOracleTest.generated(seed);var base=ActivationOracleTest.definition(owner,seed,direction);
            var expected=ExplicitActivationOracle.solve(owner,base);
            for(boolean collide:new boolean[]{false,true}) {
                AnalysisDefinition<Integer> indexed=new AnalysisDefinition<>() {
                    public Direction direction(){return base.direction();}public Integer bottom(){return base.bottom();}
                    public Iterable<Boundary<Integer>> boundaries(AnalysisSession session){return base.boundaries(session);}
                    public Join<Integer> joinInto(Integer a,Integer b,DomainWork work){return base.joinInto(a,b,work);}
                    public boolean equivalent(Integer a,Integer b,DomainWork work){return base.equivalent(a,b,work);}
                    public long stateFingerprint(Integer state){return collide?state%3:state.longValue();}
                    public Integer transferBlock(AnalysisPoint point,Integer state,DomainWork work){return base.transferBlock(point,state,work);}
                    public Integer transferEdge(AnalysisPoint point,CfgTransition edge,Integer state,DomainWork work){return base.transferEdge(point,edge,state,work);}
                };
                var actual=DataflowSolver.solve(owner,indexed);
                for(var context:owner.contexts()) {
                    var nodes=new HashSet<ProgramIndex.Node>();for(var state:expected)if(state.context()==context)nodes.add(state.node());
                    for(var node:nodes)for(boolean atIn:new boolean[]{true,false}) {
                        var values=new HashSet<Integer>();for(var state:expected)if(state.context()==context&&state.node()==node)values.add(atIn?state.in():state.out());
                        assertEquals(values,new HashSet<>(actual.states(context,node,atIn)),"seed="+seed+" direction="+direction+" collision="+collide);
                    }
                    for(var sequence:owner.index().unit(context.entry().id().unit()).sequences()) {
                        var node=owner.index().sequence(sequence.label());assertEquals(nodes.contains(node),actual.contains(context,node));
                    }
                }
            }
        }
    }
}
