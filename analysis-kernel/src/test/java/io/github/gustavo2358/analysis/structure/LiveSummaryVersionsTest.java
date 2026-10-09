package io.github.gustavo2358.analysis.structure;

import io.github.gustavo2358.analysis.cfg.domain.CfgTransition;
import io.github.gustavo2358.analysis.solver.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static io.github.gustavo2358.analysis.structure.LocalControlTest.*;

class LiveSummaryVersionsTest {
    @Test void collectionsDuringConvergencePreserveEveryExactStackInAndOut() {
        int n=257;var owner=session(List.of(call("call","body","choose"),StructuralFixtures.branch(U,"choose","call","done"),resume("body"),ret("done")),"call");
        var grow=owner.index().sequence(label("body"));var context=owner.contexts().iterator().next();
        for(var direction:Direction.values()) {
            AnalysisDefinition<Integer> definition=new AnalysisDefinition<>() {
                public Direction direction(){return direction;}public Integer bottom(){return 0;}
                public Iterable<Boundary<Integer>> boundaries(AnalysisSession ignored){return List.of(new Boundary<>(context,direction==Direction.FORWARD?context.entryNode():context.normalExit(),1));}
                public Join<Integer> joinInto(Integer a,Integer b,DomainWork work){return new Join<>(Math.max(a,b),b>a);}
                public boolean equivalent(Integer a,Integer b,DomainWork work){return a.equals(b);}
                public Integer transferBlock(AnalysisPoint point,Integer state,DomainWork work){return point.node()==grow&&state>0?Math.min(n,state+1):state;}
                public Integer transferEdge(AnalysisPoint point,CfgTransition edge,Integer state,DomainWork work){return state;}
            };
            var expected=ExplicitActivationOracle.solve(owner,definition);var actual=DataflowSolver.solve(owner,definition);
            assertTrue(actual.metrics().summaryCollections()>1,"collector must run before convergence");
            assertTrue(actual.metrics().summaryVersionsRetired()>n/2);
            var nodes=Collections.newSetFromMap(new IdentityHashMap<ProgramIndex.Node,Boolean>());
            for(var state:expected)nodes.add(state.node());
            for(var node:nodes)for(boolean in:new boolean[]{true,false}) {
                var values=new HashSet<Integer>();for(var state:expected)if(state.node()==node)values.add(in?state.in():state.out());
                assertEquals(values,new HashSet<>(actual.states(context,node,in)),direction+" "+node.source().id()+" IN="+in);
            }
        }
    }
    @Test void oneCallerRetainsLiveVersionsInsteadOfEveryHistoricalInputInBothDirections() {
        int n=4096;var owner=session(List.of(call("call","body","choose"),StructuralFixtures.branch(U,"choose","call","done"),resume("body"),ret("done")),"call");
        var grow=owner.index().sequence(label("body"));var context=owner.contexts().iterator().next();
        for(var direction:Direction.values()) {
            long[] comparisons={0};AnalysisDefinition<Integer> definition=new AnalysisDefinition<>() {
                public Direction direction(){return direction;}public Integer bottom(){return 0;}
                public Iterable<Boundary<Integer>> boundaries(AnalysisSession ignored){return List.of(new Boundary<>(context,direction==Direction.FORWARD?context.entryNode():context.normalExit(),1));}
                public Join<Integer> joinInto(Integer a,Integer b,DomainWork work){return new Join<>(Math.max(a,b),b>a);}
                public boolean equivalent(Integer a,Integer b,DomainWork work){comparisons[0]++;return a.equals(b);}
                // Deliberately keep the opaque compatibility fingerprint: liveness must be general.
                public Integer transferBlock(AnalysisPoint point,Integer state,DomainWork work){return point.node()==grow&&state>0?Math.min(n,state+1):state;}
                public Integer transferEdge(AnalysisPoint point,CfgTransition edge,Integer state,DomainWork work){return state;}
            };
            var result=DataflowSolver.solve(owner,definition);var target=direction==Direction.FORWARD?owner.index().sequence(label("done")):context.entryNode();
            assertEquals(n,result.states(context,target,true).stream().mapToInt(Integer::intValue).max().orElseThrow());
            assertTrue(result.metrics().summaryVersionsCreated()>=n,"all input changes are processed");
            assertTrue(result.metrics().summaryVersionsRetired()>n/2,"historical versions remain roots");
            assertTrue(result.metrics().liveSummaryVersions()<=8,"final versions="+result.metrics().liveSummaryVersions());
            assertTrue(result.metrics().peakLiveSummaryVersions()<=128,"peak versions="+result.metrics().peakLiveSummaryVersions());
            assertTrue(comparisons[0]<200L*n,"pairwise comparisons="+comparisons[0]);
        }
    }
    @Test void aCallerThatKeepsItsOldInputSurvivesAnotherCallersVersionChurn() {
        int n=1024;var owner=session(List.of(StructuralFixtures.branch(U,"choose","left","right"),call("left","body","after-left"),
            StructuralFixtures.branch(U,"after-left","choose","done"),call("right","body","after-right"),jump("after-right","done"),resume("body"),ret("done")),"choose");
        var context=owner.contexts().iterator().next();var body=owner.index().sequence(label("body"));
        for(var direction:Direction.values()) {
            var reset=owner.index().sequence(label(direction==Direction.FORWARD?"right":"after-right"));
            AnalysisDefinition<Integer> definition=new AnalysisDefinition<>() {
                public Direction direction(){return direction;}public Integer bottom(){return 0;}
                public Iterable<Boundary<Integer>> boundaries(AnalysisSession ignored){return List.of(new Boundary<>(context,direction==Direction.FORWARD?context.entryNode():context.normalExit(),1));}
                public Join<Integer> joinInto(Integer a,Integer b,DomainWork work){return new Join<>(Math.max(a,b),b>a);}
                public boolean equivalent(Integer a,Integer b,DomainWork work){return a.equals(b);}
                public Integer transferBlock(AnalysisPoint point,Integer state,DomainWork work){return point.node()==reset?1:point.node()==body&&state>0?Math.min(n,state+1):state;}
                public Integer transferEdge(AnalysisPoint point,CfgTransition edge,Integer state,DomainWork work){return state;}
            };
            var result=DataflowSolver.solve(owner,definition);var target=direction==Direction.FORWARD?owner.index().sequence(label("done")):context.entryNode();
            assertEquals(n,result.states(context,target,true).stream().mapToInt(Integer::intValue).max().orElseThrow());
            var stable=owner.index().sequence(label(direction==Direction.FORWARD?"after-right":"right"));
            assertEquals(Set.of(2),new HashSet<>(result.states(context,stable,true)));
            assertEquals(Set.of(1,n),new HashSet<>(result.states(context,body,direction==Direction.FORWARD)));
            assertTrue(result.metrics().summaryVersionsRetired()>n/2);assertTrue(result.metrics().liveSummaryVersions()<=8);
            assertTrue(result.metrics().peakLiveSummaryVersions()<=128,"peak="+result.metrics().peakLiveSummaryVersions());
        }
    }
    @Test void nestedSharedInputsAndDeferredWaitersDoNotRootEveryExpiredParent() {
        int n=1024;var owner=session(List.of(StructuralFixtures.branch(U,"choose","parent","grow"),call("parent","reset","after-parent"),jump("reset","inner"),
            call("inner","leaf","after-inner"),resume("after-inner"),resume("leaf"),jump("after-parent","done"),
            StructuralFixtures.branch(U,"grow","choose","done"),ret("done")),"choose");
        var context=owner.contexts().iterator().next();var grow=owner.index().sequence(label("grow"));var reset=owner.index().sequence(label("reset"));var leaf=owner.index().sequence(label("leaf"));
        AnalysisDefinition<Integer> definition=new AnalysisDefinition<>() {
            public Direction direction(){return Direction.FORWARD;}public Integer bottom(){return 0;}
            public Iterable<Boundary<Integer>> boundaries(AnalysisSession ignored){return List.of(new Boundary<>(context,context.entryNode(),1));}
            public Join<Integer> joinInto(Integer a,Integer b,DomainWork work){return new Join<>(Math.max(a,b),b>a);}
            public boolean equivalent(Integer a,Integer b,DomainWork work){return a.equals(b);}
            public Integer transferBlock(AnalysisPoint point,Integer state,DomainWork work){return point.node()==grow&&state>0?Math.min(n,state+1):point.node()==reset?1:point.node()==leaf&&state>0?2:state;}
            public Integer transferEdge(AnalysisPoint point,CfgTransition edge,Integer state,DomainWork work){return state;}
        };
        var result=DataflowSolver.solve(owner,definition);
        assertEquals(n,result.in(context,owner.index().sequence(label("done"))));
        assertEquals(Set.of(2),new HashSet<>(result.states(context,owner.index().sequence(label("after-parent")),true)));
        assertTrue(result.metrics().summaryVersionsCreated()>n/2);
        assertTrue(result.metrics().summaryVersionsRetired()>n/2,"waiters retain expired parents");
        assertTrue(result.metrics().peakLiveSummaryVersions()<=256,"peak="+result.metrics().peakLiveSummaryVersions());
        assertTrue(result.metrics().liveSummaryVersions()<=16,"final="+result.metrics().liveSummaryVersions());
    }
}
