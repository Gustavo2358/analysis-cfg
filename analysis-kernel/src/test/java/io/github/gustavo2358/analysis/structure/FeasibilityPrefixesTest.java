package io.github.gustavo2358.analysis.structure;

import io.github.gustavo2358.analysis.solver.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static io.github.gustavo2358.analysis.structure.LocalControlTest.*;
import static org.junit.jupiter.api.Assertions.*;

class FeasibilityPrefixesTest {
    @Test void nestedCallsDoNotRewalkEveryCallerPrefixToCheckTheNextGuard() {
        for(var direction:Direction.values())for(int count:new int[]{32,64,128,256,512}) {
            var sequences=new ArrayList<io.github.gustavo2358.air.model.Sequence>();
            for(int i=0;i<count;i++) {
                sequences.add(call("call-"+i,i+1<count?"call-"+(i+1):"leaf",i==0?"done":"return-"+i));
                if(i>0)sequences.add(resume("return-"+i));
            }
            sequences.add(resume("leaf"));sequences.add(ret("done"));
            var session=session(sequences,"call-0");
            var definition=identity(session,direction);
            var result=DataflowSolver.solve(session,definition);
            for(var node:session.index().nodes)if(node.source() instanceof io.github.gustavo2358.analysis.cfg.domain.CfgNode.SequenceNode)assertTrue(result.contains(session.contexts().iterator().next(),node));
            if(count==32)assertSameStates(session,definition,result);
            System.out.println("CALLER_PREFIX direction="+direction+" count="+count+" proofEdges="+result.metrics().callerPathEdgesRead());
            assertTrue(result.metrics().callerPathEdgesRead()<=32L*count,"direction="+direction+" count="+count+" caller prefix reads="+result.metrics().callerPathEdgesRead());
        }
    }
    @Test void ordinaryDiamondsDoNotAccumulateImpossibleReentryPredicates() {
        for(int count:new int[]{4,8,12,16}) {
            var sequences=new ArrayList<io.github.gustavo2358.air.model.Sequence>();
            for(int i=0;i<count;i++) {
                sequences.add(StructuralFixtures.branch(U,"level-"+i,"left-"+i,"right-"+i));
                sequences.add(call("left-"+i,"level-"+(i+1),"join-"+i));
                sequences.add(call("right-"+i,"level-"+(i+1),"join-"+i));
                sequences.add(i==0?jump("join-"+i,"done"):resume("join-"+i));
            }
            sequences.add(resume("level-"+count));sequences.add(ret("done"));var owner=session(sequences,"level-0");
            var context=owner.contexts().iterator().next();
            for(var direction:Direction.values()) {
                var definition=identity(owner,direction);var actual=DataflowSolver.solve(owner,definition);
                assertEquals(1,actual.in(context,context.entryNode()));
                if(count<=8)assertSameStates(owner,definition,actual);
                assertTrue(actual.metrics().peakBooleanNodes()<=32L*count,"impossible transient guard nodes="+actual.metrics().peakBooleanNodes()+" count="+count+" direction="+direction);
            }
        }
    }
    private static void assertSameStates(AnalysisSession owner,AnalysisDefinition<Integer> definition,DataflowResult<Integer> actual) {
        var context=owner.contexts().iterator().next();var expected=ExplicitActivationOracle.solve(owner,definition);
        for(var node:owner.index().nodes)for(boolean in:new boolean[]{true,false}) {
            var values=new HashSet<Integer>();for(var point:expected)if(point.node()==node)values.add(in?point.in():point.out());
            assertEquals(values,new HashSet<>(actual.states(context,node,in)),definition.direction()+" "+node.source().id()+" IN="+in);
        }
    }
    private static AnalysisDefinition<Integer> identity(AnalysisSession session,Direction direction) {
        return new AnalysisDefinition<>() {
                public Direction direction(){return direction;}
                public Integer bottom(){return 0;}
                public long stateFingerprint(Integer state){return state;}
                public Iterable<Boundary<Integer>> boundaries(AnalysisSession ignored) {
                    var context=session.contexts().iterator().next();
                    return List.of(new Boundary<>(context,direction==Direction.FORWARD?context.entryNode():context.normalExit(),1));
                }
                public Join<Integer> joinInto(Integer left,Integer right,DomainWork work){int joined=left|right;return new Join<>(joined,joined!=left);}
                public boolean equivalent(Integer left,Integer right,DomainWork work){return left.equals(right);}
                public Integer transferBlock(AnalysisPoint point,Integer state,DomainWork work){return state;}
                public Integer transferEdge(AnalysisPoint point,io.github.gustavo2358.analysis.cfg.domain.CfgTransition edge,Integer state,DomainWork work){return state;}
            };
    }

}
