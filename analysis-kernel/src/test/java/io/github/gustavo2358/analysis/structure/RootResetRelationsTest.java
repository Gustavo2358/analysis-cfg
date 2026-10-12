package io.github.gustavo2358.analysis.structure;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.analysis.solver.*;
import io.github.gustavo2358.analysis.cfg.domain.CfgTransition;
import java.math.BigInteger;
import java.util.*;
import org.junit.jupiter.api.Test;
import static io.github.gustavo2358.analysis.structure.LocalControlTest.*;
import static org.junit.jupiter.api.Assertions.*;

class RootResetRelationsTest {
    @Test void sharedGuardedRootResetsDoNotAccumulateCallerPrefixPredicates() {
        var bounds=new ArrayList<org.junit.jupiter.api.function.Executable>();
        for(var direction:Direction.values())for(boolean all:new boolean[]{false,true})for(int count:new int[]{4,8,64,128,256,512}) {
            var owner=dispatcher(count,all,false);var definition=definition(owner,direction,false);var actual=DataflowSolver.solve(owner,definition);
            if(count<=4)assertOracle(owner,definition,actual);
            System.out.println("ROOT_RESET direction="+direction+" all="+all+" count="+count+" booleanPeak="+actual.metrics().peakBooleanNodes());
            bounds.add(()->assertTrue(actual.metrics().peakBooleanNodes()<=16L*count,"reset prefix expansion direction="+direction+" all="+all+" count="+count+" peak="+actual.metrics().peakBooleanNodes()));
        }
        assertAll(bounds);
    }
    @Test void resetProjectionPreservesEveryContextAndNonDistributiveEdgeSensitiveMemory() {
        for(var direction:Direction.values())for(boolean all:new boolean[]{false,true}) {
            var owner=dispatcher(3,all,true);var definition=definition(owner,direction,true);var actual=DataflowSolver.solve(owner,definition);
            assertOracle(owner,definition,actual);
        }
    }
    private static AnalysisSession dispatcher(int count,boolean all,boolean multipleEntries) {
        var sequences=new ArrayList<Sequence>();sequences.add(guarded("main","body","done","main"));
        sequences.add(StructuralFixtures.branch(U,"body","dispatch-0","finish"));sequences.add(resume("finish"));
        for(int i=0;i<count;i++) {
            sequences.add(guarded("call-"+i,"body","after-"+i,"key-"+i));sequences.add(jump("after-"+i,"dispatch-0"));
            sequences.add(i+1==count?jump("dispatch-"+i,"call-"+i):StructuralFixtures.branch(U,"dispatch-"+i,"call-"+i,"dispatch-"+(i+1)));
        }
        sequences.add(all?seq("reset",new Operations.LocalUnwind(h("reset"),BigInteger.ZERO,label("done"),fallback(),true)):ret("reset"));
        sequences.add(ret("done"));sequences.add(guarded("secondary","body","done","secondary"));
        return multipleEntries?session(sequences,"main","secondary"):session(sequences,"main");
    }
    private static Sequence guarded(String name,String body,String next,String key) {
        return seq(name,new Operations.LocalInvoke(h(name),label(body),List.of(),label(next),fallback(),Optional.of(new Operations.ReentryGuard(key,label("reset"))),List.of()));
    }
    private static void assertOracle(AnalysisSession owner,AnalysisDefinition<Integer> definition,DataflowResult<Integer> actual) {
        var expected=ExplicitActivationOracle.solve(owner,definition);
        for(var context:owner.contexts())for(var node:owner.index().nodes)for(boolean in:new boolean[]{true,false}) {
            var values=new HashSet<Integer>();for(var point:expected)if(point.context()==context&&point.node()==node)values.add(in?point.in():point.out());
            assertEquals(values,new HashSet<>(actual.states(context,node,in)),definition.direction()+" entry="+context.entry().id()+" node="+node.source().id()+" IN="+in);
        }
    }
    private static AnalysisDefinition<Integer> definition(AnalysisSession owner,Direction direction,boolean sensitive) {
        return new AnalysisDefinition<>() {
            public Direction direction(){return direction;}
            public Integer bottom(){return 0;}
            public long stateFingerprint(Integer value){return value;}
            public Iterable<Boundary<Integer>> boundaries(AnalysisSession ignored) {
                var values=new ArrayList<Boundary<Integer>>();int seed=1;
                for(var context:owner.contexts()){values.add(new Boundary<>(context,direction==Direction.FORWARD?context.entryNode():context.normalExit(),seed));seed=2;}
                return values;
            }
            public Join<Integer> joinInto(Integer a,Integer b,DomainWork work){int joined=a|b;return new Join<>(joined,joined!=a);}
            public boolean equivalent(Integer a,Integer b,DomainWork work){return a.equals(b);}
            public Integer transferBlock(AnalysisPoint point,Integer value,DomainWork work){return sensitive&&(value&3)==3?value|16:value;}
            public Integer transferEdge(AnalysisPoint point,CfgTransition edge,Integer value,DomainWork work){return !sensitive||value==0?value:value|(1<<((int)edge.from().ordinal()%4));}
        };
    }
}
