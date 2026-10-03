package io.github.gustavo2358.analysis.structure;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.analysis.cfg.domain.CfgTransition;
import io.github.gustavo2358.analysis.solver.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static io.github.gustavo2358.analysis.structure.LocalControlTest.*;
import static io.github.gustavo2358.analysis.structure.GuardedLocalControlTest.guarded;

class ActivationScaleTest {
    static AnalysisSession dispatcher(int n) {
        var sequences=new ArrayList<Sequence>();
        sequences.add(guarded("main","body","done","main","rejected"));
        sequences.add(StructuralFixtures.branch(U,"body","dispatch-0","finish"));
        sequences.add(resume("finish"));sequences.add(ret("done"));sequences.add(ret("rejected"));
        for(int i=0;i<n;i++) {
            sequences.add(guarded("call-"+i,"body","after-"+i,"key-"+i,"rejected"));
            sequences.add(jump("after-"+i,"dispatch-0"));
            sequences.add(i==n-1?jump("dispatch-"+i,"call-"+i):StructuralFixtures.branch(U,"dispatch-"+i,"call-"+i,"dispatch-"+(i+1)));
        }
        return session(sequences,"main");
    }
    static AnalysisDefinition<Boolean> reach(AnalysisSession session) {return reach(session,Direction.FORWARD);}
    static AnalysisDefinition<Boolean> reach(AnalysisSession session,Direction direction) {
        return new AnalysisDefinition<>() {
            public Direction direction(){return direction;}
            public Boolean bottom(){return false;}
            public Iterable<Boundary<Boolean>> boundaries(AnalysisSession ignored){return session.contexts().stream().map(c->new Boundary<>(c,direction==Direction.FORWARD?c.entryNode():c.normalExit(),true)).toList();}
            public Join<Boolean> joinInto(Boolean a,Boolean b,DomainWork work){return new Join<>(a||b,!a&&b);}
            public boolean equivalent(Boolean a,Boolean b,DomainWork work){return a.equals(b);}
            public Boolean transferBlock(AnalysisPoint point,Boolean value,DomainWork work){return value;}
            public Boolean transferEdge(AnalysisPoint point,CfgTransition edge,Boolean value,DomainWork work){return value;}
        };
    }
    @Test void localControlRejectsMissingAnalysisDirection() {
        var session=dispatcher(1);
        AnalysisDefinition<Boolean> invalid=new AnalysisDefinition<>() {
            public Direction direction(){return null;}
            public Boolean bottom(){return false;}
            public Iterable<Boundary<Boolean>> boundaries(AnalysisSession ignored){return List.of();}
            public Join<Boolean> joinInto(Boolean a,Boolean b,DomainWork w){return new Join<>(a||b,!a&&b);}
            public boolean equivalent(Boolean a,Boolean b,DomainWork w){return a.equals(b);}
            public Boolean transferBlock(AnalysisPoint p,Boolean a,DomainWork w){return a;}
            public Boolean transferEdge(AnalysisPoint p,CfgTransition edge,Boolean a,DomainWork w){return a;}
        };
        assertEquals("direction",assertThrows(NullPointerException.class,()->DataflowSolver.solve(session,invalid)).getMessage());
    }
    @Test void cyclicDispatchDoesNotEnumeratePermutations() {
        int n=7;var session=dispatcher(n);var result=DataflowSolver.solve(session,reach(session));var context=session.contexts().iterator().next();
        assertEquals(Boolean.TRUE,result.in(context,session.index().sequence(label("done"))));
        assertTrue(result.states(context,session.index().sequence(label("rejected")),true).contains(true));
        assertTrue(result.metrics().analysisPoints()<1000,"tabulated points="+result.metrics().analysisPoints());
    }
    @Test void impossibleAncestorGuardsCannotMultiplyValueSummaries() {
        int n=10;var sequences=new ArrayList<Sequence>();
        sequences.add(guarded("main","call-0","done","main","done"));sequences.add(ret("done"));sequences.add(resume("end"));
        for(int i=0;i<n;i++) {
            String next=i==n-1?"end":"call-"+(i+1);
            sequences.add(guarded("call-"+i,"write-"+i,next,"key-"+i,next));sequences.add(resume("write-"+i));
        }
        var session=session(sequences,"main");var context=session.contexts().iterator().next();
        AnalysisDefinition<Integer> values=new AnalysisDefinition<>() {
            public Direction direction(){return Direction.FORWARD;}
            public Integer bottom(){return 0;}
            public Iterable<Boundary<Integer>> boundaries(AnalysisSession ignored){return List.of();}
            public Join<Integer> joinInto(Integer a,Integer b,DomainWork w){return new Join<>(a|b,(a|b)!=a);}
            public boolean equivalent(Integer a,Integer b,DomainWork w){return a.equals(b);}
            public Integer transferBlock(AnalysisPoint p,Integer a,DomainWork w){
                if(p.node().source() instanceof io.github.gustavo2358.analysis.cfg.domain.CfgNode.SequenceNode node&&node.source().label().localId().startsWith("write-"))
                    return a|1<<Integer.parseInt(node.source().label().localId().substring(6));
                return a;
            }
            public Integer transferEdge(AnalysisPoint p,CfgTransition edge,Integer a,DomainWork w){return a;}
        };
        var result=DataflowSolver.solve(session,values);
        assertEquals((1<<n)-1,result.in(context,session.index().sequence(label("done"))));
        assertTrue(result.metrics().nodesTransferred()<1000,"transfers="+result.metrics().nodesTransferred());
    }
    @Test void infeasibleGuardedCyclesCannotEnumerateChangingInputs() {
        var session=session(List.of(StructuralFixtures.branch(U,"start","a","b"),
            guarded("a","body-a","done","a","done"),guarded("b","body-b","done","b","done"),
            jump("body-a","b"),jump("body-b","a"),ret("done")),"start");
        var context=session.contexts().iterator().next();
        AnalysisDefinition<Integer> values=new AnalysisDefinition<>() {
            public Direction direction(){return Direction.FORWARD;}
            public Integer bottom(){return 0;}
            public Iterable<Boundary<Integer>> boundaries(AnalysisSession ignored){return List.of();}
            public Join<Integer> joinInto(Integer a,Integer b,DomainWork w){return new Join<>(Math.max(a,b),b>a);}
            public boolean equivalent(Integer a,Integer b,DomainWork w){return a.equals(b);}
            public Integer transferBlock(AnalysisPoint p,Integer a,DomainWork w){
                return p.node()==session.index().sequence(label("body-a"))||p.node()==session.index().sequence(label("body-b"))?Math.min(4096,a+1):a;
            }
            public Integer transferEdge(AnalysisPoint p,CfgTransition edge,Integer a,DomainWork w){return a;}
        };
        var expected=ExplicitActivationOracle.solve(session,values);
        assertEquals(List.of(2),expected.stream().filter(x->x.node()==context.normalExit()).map(ExplicitActivationOracle.State::in).toList());
        var result=DataflowSolver.solve(session,values);assertEquals(2,result.in(context,context.normalExit()));
        assertTrue(result.metrics().nodesTransferred()<100,"transfers="+result.metrics().nodesTransferred());
    }
    @Test void infeasibleOrdinaryCyclesDoNotTransferValues() {infeasibleOrdinaryCycle(false);}
    @Test void infeasibleOrdinaryEdgeCyclesDoNotTransferValues() {infeasibleOrdinaryCycle(true);}
    private void infeasibleOrdinaryCycle(boolean edgeGrowth) {
        var session=session(List.of(StructuralFixtures.branch(U,"start","a","b"),
            guarded("a","prefix-a","done","a","done"),guarded("b","prefix-b","done","b","done"),
            jump("prefix-a","f"),jump("prefix-b","f"),guarded("f","test-a","done","f","done"),
            guarded("test-a","done","done","a","spin"),jump("spin","spin"),ret("done")),"start");
        var context=session.contexts().iterator().next();
        AnalysisDefinition<Integer> values=new AnalysisDefinition<>() {
            public Direction direction(){return Direction.FORWARD;}
            public Integer bottom(){return 0;}
            public Iterable<Boundary<Integer>> boundaries(AnalysisSession ignored){return List.of();}
            public Join<Integer> joinInto(Integer a,Integer b,DomainWork w){return new Join<>(Math.max(a,b),b>a);}
            public boolean equivalent(Integer a,Integer b,DomainWork w){return a.equals(b);}
            public Integer transferBlock(AnalysisPoint p,Integer a,DomainWork w){
                if(p.node()==session.index().sequence(label("prefix-b")))return Math.max(1,a);
                if(!edgeGrowth&&p.node()==session.index().sequence(label("spin"))&&a>0)return Math.min(4096,a+1);
                return a;
            }
            public Integer transferEdge(AnalysisPoint p,CfgTransition edge,Integer a,DomainWork w){
                return edgeGrowth&&p.node()==session.index().sequence(label("spin"))&&a>0?Math.min(4096,a+1):a;
            }
        };
        var expected=ExplicitActivationOracle.solve(session,values);
        assertEquals(List.of(1),expected.stream().filter(x->x.node()==context.normalExit()).map(ExplicitActivationOracle.State::in).toList());
        var result=DataflowSolver.solve(session,values);assertEquals(1,result.in(context,context.normalExit()));
        assertEquals(Set.of(0),new HashSet<>(result.states(context,session.index().sequence(label("spin")),true)));
        assertTrue(result.metrics().nodesTransferred()<100,"transfers="+result.metrics().nodesTransferred());
    }
    @Test void backwardDispatchUsesContinuationValuesWithoutEnumeratingStacks() {
        var session=dispatcher(9);var result=DataflowSolver.solve(session,reach(session,Direction.BACKWARD));
        var context=session.contexts().iterator().next();
        assertTrue(result.states(context,context.entryNode(),true).contains(true));
        assertTrue(result.metrics().analysisPoints()<6000,"tabulated points="+result.metrics().analysisPoints());
    }
    @Test void hundredWayDispatchFinishesFinalQueriesInBothDirections() {
        org.junit.jupiter.api.Assertions.assertTimeout(java.time.Duration.ofSeconds(30),()->{
            for(var direction:Direction.values()) {
                var session=dispatcher(100);var result=DataflowSolver.solve(session,reach(session,direction));
                var context=session.contexts().iterator().next();
                assertTrue(result.states(context,context.entryNode(),true).contains(true));
                assertTrue(result.contains(context,session.index().sequence(label("done"))));
                assertTrue(result.contains(context,session.index().sequence(label("rejected"))));
                assertTrue(result.metrics().analysisPoints()<100000);
            }
        });
    }
    @Test void acyclicCallDiamondsDoNotEnumerateCallStrings() {
        int n=14;var sequences=new ArrayList<Sequence>();
        for(int i=0;i<n;i++) {
            sequences.add(StructuralFixtures.branch(U,"level-"+i,"left-"+i,"right-"+i));
            sequences.add(call("left-"+i,"level-"+(i+1),"end-"+i));
            sequences.add(call("right-"+i,"level-"+(i+1),"end-"+i));
            sequences.add(i==0?ret("end-"+i):resume("end-"+i));
        }
        sequences.add(resume("level-"+n));
        var session=session(sequences,"level-0");var result=DataflowSolver.solve(session,reach(session));
        assertTrue(result.metrics().analysisPoints()<1000,"tabulated points="+result.metrics().analysisPoints());
    }
    public static void main(String[] args) {
        var t=new ActivationScaleTest();if(args.length==0||args[0].equals("cycle"))t.cyclicDispatchDoesNotEnumeratePermutations();
        if(args.length==0||args[0].equals("diamond"))t.acyclicCallDiamondsDoNotEnumerateCallStrings();
        System.out.println("ACTIVATION_SCALE=PASS");
    }
}
