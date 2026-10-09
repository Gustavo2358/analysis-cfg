package io.github.gustavo2358.analysis.structure;

import io.github.gustavo2358.analysis.cfg.domain.CfgTransition;
import io.github.gustavo2358.analysis.solver.*;
import io.github.gustavo2358.air.model.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static io.github.gustavo2358.analysis.structure.LocalControlTest.*;

class FormalBodiesTest {
    @Test void impossibleCallsHaveNoReadInterfaceAndKeepTheirGuardRejection() {
        var owner=session(List.of(GuardedLocalControlTest.guarded("outer","probe","done","key","bad"),
            GuardedLocalControlTest.guarded("probe","dead-first","bad","key","latent"),
            GuardedLocalControlTest.guarded("latent","dead-second","bad","key","finish"),resume("finish"),resume("dead-first"),resume("dead-second"),ret("done"),ret("bad")),"outer");
        var context=owner.contexts().iterator().next();
        for(var direction:Direction.values()) {
            var definition=identity(owner,direction,context.entryNode(),false);var expected=ExplicitActivationOracle.solve(owner,definition);var actual=DataflowSolver.solve(owner,definition);
            for(var node:owner.index().nodes)for(boolean in:new boolean[]{true,false}) {
                var values=new HashSet<Integer>();for(var state:expected)if(state.node()==node)values.add(in?state.in():state.out());
                assertEquals(values,new HashSet<>(actual.states(context,node,in)),direction+" "+node.source().id()+" IN="+in);
            }
        }
    }
    @Test void backwardWakeupsFollowReadContinuationsInsteadOfEveryCallInTheRegion() {
        for(int n:new int[]{32,64,128,256,512}) {
            int m=32;var sequences=new ArrayList<Sequence>();
            for(int i=0;i<n;i++)sequences.add(call("call-"+i,"body-0",i+1<n?"call-"+(i+1):"done"));
            for(int i=0;i<m;i++)sequences.add(i+1<m?jump("body-"+i,"body-"+(i+1)):resume("body-"+i));sequences.add(ret("done"));
            var owner=session(sequences,"call-0");var context=owner.contexts().iterator().next();
            var result=DataflowSolver.solve(owner,identity(owner,Direction.BACKWARD,context.entryNode(),false));
            assertEquals(1,result.in(context,context.entryNode()));
            assertTrue(result.metrics().nodesTransferred()<=16L*(n+m),"global call wakeups="+result.metrics().nodesTransferred()+" callers="+n);
            assertTrue(result.metrics().formalReturnSourceReads()<=16L*(n+m),"repeated full-shape pop discovery="+result.metrics().formalReturnSourceReads());
        }
    }
    @Test void identicalArgumentsShareBodyEquationsButKeepEveryMatchedContinuation() {
        int n=128,m=128;var sequences=new ArrayList<io.github.gustavo2358.air.model.Sequence>();
        for(int i=0;i<n;i++)sequences.add(call("call-"+i,"body-0",i+1<n?"call-"+(i+1):"done",i%2==0?A:B));
        for(int i=0;i<m;i++)sequences.add(i+1<m?jump("body-"+i,"body-"+(i+1)):resume("body-"+i));
        sequences.add(ret("done"));var owner=session(sequences,"call-0");var context=owner.contexts().iterator().next();
        for(var direction:Direction.values()) {
            AnalysisDefinition<Integer> definition=new AnalysisDefinition<>() {
                public Direction direction(){return direction;}public Integer bottom(){return 0;}
                public Iterable<Boundary<Integer>> boundaries(AnalysisSession ignored){return List.of(new Boundary<>(context,direction==Direction.FORWARD?context.entryNode():context.normalExit(),1));}
                public Join<Integer> joinInto(Integer a,Integer b,DomainWork work){return new Join<>(a|b,(a|b)!=a);}
                public boolean equivalent(Integer a,Integer b,DomainWork work){return a.equals(b);}
                public Integer transferBlock(AnalysisPoint point,Integer state,DomainWork work){return state;}
                public Integer transferEdge(AnalysisPoint point,CfgTransition edge,Integer state,DomainWork work){return state;}
            };
            var result=DataflowSolver.solve(owner,definition);
            for(var sequence:sequences)assertEquals(Set.of(1),new HashSet<>(result.states(context,owner.index().sequence(sequence.label()),true)),direction+" "+sequence.label());
            assertTrue(result.metrics().analysisPoints()<=n+m+8,"body instantiated per frame: "+result.metrics().analysisPoints());
        }
    }
    @Test void routeDestinationsStayInBindingsAndOnlyObservedTopPortsSplitBodies() {
        int n=96,m=64;var sequences=new ArrayList<Sequence>();
        for(int i=0;i<n;i++) {
            String next=i+1<n?"call-"+(i+1):"done";
            sequences.add(seq("call-"+i,new Operations.LocalInvoke(h("call-"+i),label("body-0"),List.of(i%2==0?A:B),label("bad"),fallback(),Optional.empty(),
                List.of(new Operations.ResumeRoute("route",label(next)),new Operations.ResumeRoute("unused-"+i,label("bad"))))));
        }
        for(int i=0;i<m-1;i++)sequences.add(jump("body-"+i,"body-"+(i+1)));
        sequences.add(seq("body-"+(m-1),new Operations.LocalBoundary(h("end-a"),A,label("end-b"),fallback(),Optional.of("route"))));
        sequences.add(seq("end-b",new Operations.LocalBoundary(h("end-b"),B,label("bad"),fallback(),Optional.of("route"))));sequences.add(ret("done"));sequences.add(ret("bad"));
        var owner=session(sequences,"call-0");var context=owner.contexts().iterator().next();
        for(var direction:Direction.values()) {
            var result=DataflowSolver.solve(owner,identity(owner,direction,context.entryNode(),false));
            for(var sequence:sequences)if(!sequence.label().equals(label("bad")))assertEquals(Set.of(1),new HashSet<>(result.states(context,owner.index().sequence(sequence.label()),true)),direction+" "+sequence.label());
            assertFalse(result.contains(context,owner.index().sequence(label("bad"))));
            assertTrue(result.metrics().analysisPoints()<=n+2*m+12,"port interface expanded by concrete continuation: "+result.metrics().analysisPoints());
        }
    }
    @Test void edgeSensitiveBackwardArgumentsAndNonDistributiveBlocksMatchEveryConcreteStack() {
        var owner=session(List.of(StructuralFixtures.branch(U,"choose","left","right"),call("left","body","after-left"),call("right","body","after-right"),
            jump("after-left","done"),jump("after-right","done"),resume("body"),ret("done")),"choose");
        var context=owner.contexts().iterator().next();var body=owner.index().sequence(label("body"));
        for(var direction:Direction.values()) {
            AnalysisDefinition<Integer> definition=new AnalysisDefinition<>() {
                public Direction direction(){return direction;}public Integer bottom(){return 0;}
                public Iterable<Boundary<Integer>> boundaries(AnalysisSession ignored){return List.of(new Boundary<>(context,direction==Direction.FORWARD?context.entryNode():context.normalExit(),1));}
                public Join<Integer> joinInto(Integer a,Integer b,DomainWork work){return new Join<>(a|b,(a|b)!=a);}
                public boolean equivalent(Integer a,Integer b,DomainWork work){return a.equals(b);}
                public Integer transferBlock(AnalysisPoint point,Integer state,DomainWork work){return point.node()==body&&(state&24)==24?state|4:state;}
                public Integer transferEdge(AnalysisPoint point,CfgTransition edge,Integer state,DomainWork work){
                    if(state==0||!edge.from().equals(body.source().id()))return state;
                    if(edge.to().equals(owner.index().sequence(label("after-left")).source().id()))return state|8;
                    if(edge.to().equals(owner.index().sequence(label("after-right")).source().id()))return state|16;return state;
                }
            };
            var expected=ExplicitActivationOracle.solve(owner,definition);var actual=DataflowSolver.solve(owner,definition);
            var nodes=Collections.newSetFromMap(new IdentityHashMap<ProgramIndex.Node,Boolean>());for(var state:expected)nodes.add(state.node());
            for(var node:nodes)for(boolean in:new boolean[]{true,false}) {
                var values=new HashSet<Integer>();for(var state:expected)if(state.node()==node)values.add(in?state.in():state.out());
                assertEquals(values,new HashSet<>(actual.states(context,node,in)),direction+" "+node.source().id()+" IN="+in);
            }
        }
    }
    @Test void distinctSymbolsStillMakeBodyBoundariesAmbiguousButDeadBindingsDoNot() {
        var multiple=session(List.of(StructuralFixtures.branch(U,"choose","left","right"),call("left","body","done"),call("right","body","done"),resume("body"),ret("done")),"choose");
        for(var direction:Direction.values())assertThrows(IllegalArgumentException.class,()->DataflowSolver.solve(multiple,identity(multiple,direction,multiple.index().sequence(label("body")),true)));
        var unique=session(List.of(call("left","body","done"),call("dead","body","bad"),resume("body"),ret("done"),ret("bad")),"left");
        for(var direction:Direction.values()) {
            var result=DataflowSolver.solve(unique,identity(unique,direction,unique.index().sequence(label("body")),true));var context=unique.contexts().iterator().next();
            assertEquals(1,result.in(context,unique.index().sequence(label("body"))));assertFalse(result.contains(context,unique.index().sequence(label("dead"))));
        }
    }
    @Test void unwindLandingsObserveTheRemainingTopInterfaceOutsideItsNormalEntryWalk() {
        var owner=session(List.of(StructuralFixtures.branch(U,"choose","left","right"),call("left","parent","done",A),call("right","parent","done",B),
            call("parent","leaf","normal-return"),resume("normal-return"),unwind("leaf",java.math.BigInteger.ONE,"land-a"),boundary("land-a",A,"land-b"),
            boundary("land-b",B,"bad"),ret("done"),ret("bad")),"choose");
        var context=owner.contexts().iterator().next();
        for(var direction:Direction.values()) {
            var definition=identity(owner,direction,context.entryNode(),false);var expected=ExplicitActivationOracle.solve(owner,definition);var actual=DataflowSolver.solve(owner,definition);
            for(var node:owner.index().nodes)for(boolean in:new boolean[]{true,false}) {
                var values=new HashSet<Integer>();for(var state:expected)if(state.node()==node)values.add(in?state.in():state.out());
                assertEquals(values,new HashSet<>(actual.states(context,node,in)),direction+" "+node.source().id()+" IN="+in);
            }
        }
    }
    private static AnalysisDefinition<Integer> identity(AnalysisSession owner,Direction direction,ProgramIndex.Node boundary,boolean internal) {
        var context=owner.contexts().iterator().next();return new AnalysisDefinition<>() {
            public Direction direction(){return direction;}public Integer bottom(){return 0;}
            public Iterable<Boundary<Integer>> boundaries(AnalysisSession ignored){return List.of(new Boundary<>(context,internal?boundary:direction==Direction.FORWARD?context.entryNode():context.normalExit(),1));}
            public Join<Integer> joinInto(Integer a,Integer b,DomainWork work){return new Join<>(a|b,(a|b)!=a);}
            public boolean equivalent(Integer a,Integer b,DomainWork work){return a.equals(b);}
            public Integer transferBlock(AnalysisPoint point,Integer state,DomainWork work){return state;}
            public Integer transferEdge(AnalysisPoint point,CfgTransition edge,Integer state,DomainWork work){return state;}
        };
    }
}
