package io.github.gustavo2358.analysis.structure;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.analysis.solver.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static io.github.gustavo2358.analysis.structure.LocalControlTest.*;
import static org.junit.jupiter.api.Assertions.*;

class ConditionalFeasibilityTest {
    @Test void sharedRecursiveDispatchDoesNotSearchAllCallersForEachGuard() {
        for(var direction:Direction.values())for(int count:new int[]{4,8,16,32,64,128,256,512}) {
            var owner=dispatch(count,-1);var context=owner.contexts().iterator().next();
            var definition=identity(owner,direction);var actual=DataflowSolver.solve(owner,definition);
            assertEquals(1,actual.in(context,context.entryNode()));
            if(count==4) {
                var expected=ExplicitActivationOracle.solve(owner,definition);
                for(var node:owner.index().nodes)for(boolean in:new boolean[]{true,false}) {
                    var values=new HashSet<Integer>();for(var point:expected)if(point.node()==node)values.add(in?point.in():point.out());
                    assertEquals(values,new HashSet<>(actual.states(context,node,in)),direction+" "+node.source().id()+" IN="+in);
                }
            }
            System.out.println("CONDITIONAL_PREFIX direction="+direction+" count="+count+" proofEdges="+actual.metrics().callerPathEdgesRead()+" certificateEdges="+actual.metrics().callerCertificateEdgesRead()+" valuationVisits="+actual.metrics().callerValuationNodesVisited()+" indexProbes="+actual.metrics().callerHintIndexProbes());
            assertTrue(actual.metrics().callerPathEdgesRead()<=64L*count,"direction="+direction+" count="+count+" proof edge reads="+actual.metrics().callerPathEdgesRead()+" certificateEdges="+actual.metrics().callerCertificateEdgesRead()+" valuationVisits="+actual.metrics().callerValuationNodesVisited()+" indexProbes="+actual.metrics().callerHintIndexProbes());
        }
    }
    @Test void dispatchSequencePermutationsKeepReachabilityAndLinearCertificates(){
        for(int seed=0;seed<8;seed++)for(int count:new int[]{4,128}){
            var owner=dispatch(count,seed);var context=owner.contexts().iterator().next();
            var definition=identity(owner,Direction.BACKWARD);var actual=DataflowSolver.solve(owner,definition);
            assertEquals(1,actual.in(context,context.entryNode()));assertEquals(1,actual.in(context,context.normalExit()));
            if(count==4){
                var expected=ExplicitActivationOracle.solve(owner,definition);
                for(var node:owner.index().nodes)for(boolean in:new boolean[]{true,false}){
                    var values=new HashSet<Integer>();for(var point:expected)if(point.node()==node)values.add(in?point.in():point.out());
                    assertEquals(values,new HashSet<>(actual.states(context,node,in)),"seed="+seed+" node="+node.source().id()+" IN="+in);
                }
            }
            System.out.println("DISPATCH_PERMUTATION seed="+seed+" count="+count+" proofEdges="+actual.metrics().callerPathEdgesRead());
            assertTrue(actual.metrics().callerPathEdgesRead()<=64L*count,"permutation seed="+seed+" count="+count);
        }
    }
    private static AnalysisSession dispatch(int count,int seed){
        var sequences=new ArrayList<Sequence>();
        sequences.add(guarded("main","body","done","main"));
        sequences.add(StructuralFixtures.branch(U,"body","dispatch-0","finish"));sequences.add(resume("finish"));
        for(int i=0;i<count;i++) {
            sequences.add(guarded("call-"+i,"body","after-"+i,"key-"+i));
            sequences.add(jump("after-"+i,"dispatch-0"));
            sequences.add(i+1==count?jump("dispatch-"+i,"call-"+i):StructuralFixtures.branch(U,"dispatch-"+i,"call-"+i,"dispatch-"+(i+1)));
        }
        sequences.add(ret("done"));if(seed>=0)Collections.shuffle(sequences,new Random(seed));
        return session(sequences,"main");
    }
    private static Sequence guarded(String name,String body,String next,String key) {
        return seq(name,new Operations.LocalInvoke(h(name),label(body),List.of(),label(next),fallback(),Optional.of(new Operations.ReentryGuard(key,label("done"))),List.of()));
    }
    private static AnalysisDefinition<Integer> identity(AnalysisSession owner,Direction direction) {
        return new AnalysisDefinition<>() {
            public Direction direction(){return direction;}
            public Integer bottom(){return 0;}
            public long stateFingerprint(Integer value){return value;}
            public Iterable<Boundary<Integer>> boundaries(AnalysisSession ignored) {var context=owner.contexts().iterator().next();return List.of(new Boundary<>(context,direction==Direction.FORWARD?context.entryNode():context.normalExit(),1));}
            public Join<Integer> joinInto(Integer a,Integer b,DomainWork work){int joined=a|b;return new Join<>(joined,joined!=a);}
            public boolean equivalent(Integer a,Integer b,DomainWork work){return a.equals(b);}
            public Integer transferBlock(AnalysisPoint point,Integer value,DomainWork work){return value;}
            public Integer transferEdge(AnalysisPoint point,io.github.gustavo2358.analysis.cfg.domain.CfgTransition edge,Integer value,DomainWork work){return value;}
        };
    }
}
