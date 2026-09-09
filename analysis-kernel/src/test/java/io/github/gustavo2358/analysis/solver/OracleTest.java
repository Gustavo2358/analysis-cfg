package io.github.gustavo2358.analysis.solver;

import io.github.gustavo2358.analysis.cfg.domain.CfgTransition;
import io.github.gustavo2358.analysis.structure.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static io.github.gustavo2358.analysis.solver.SyntheticAnalyses.*;
import static io.github.gustavo2358.analysis.structure.SolverFixtureBridge.*;

class OracleTest {
    @Test void generatedForwardBackwardCorpusMatchesIndependentOraclesAndSchedules() {
        int comparisons=0,points=0;
        for(int seed=0;seed<80;seed++) {
            Random random=new Random(19073L+seed); int n=1+seed%13;
            int[][] edges=new int[n][]; Op[][] ops=new Op[n][];
            for(int i=0;i<n;i++) {
                int primary=i+1<n?i+1:(seed%3==0?0:-1);
                edges[i]=primary<0?new int[0]:(seed%4==0?new int[]{primary}:new int[]{primary,random.nextInt(n)});
                ops[i]=new Op[random.nextInt(4)];
                for(int j=0;j<ops[i].length;j++) ops[i][j]=Op.values()[random.nextInt(Op.values().length)];
            }
            var p=new Program(edges,new int[]{0,Math.max(0,n/2)},2,ops,true);
            var forward=new Forward(p); var backward=new Backward(p);
            var expectedF=new RecompositionOracle<>(p,forward); var expectedB=new RecompositionOracle<>(p,backward);
            var fifoF=DataflowSolver.solve(p.session,forward); var fifoB=DataflowSolver.solve(p.session,backward);
            expectedF.compare(fifoF); expectedB.compare(fifoB);
            ConcreteOracle.forward(p,fifoF,true); ConcreteOracle.backward(p,fifoB);
            assertEquals(fifoF.metrics(),DataflowSolver.solve(p.session,forward).metrics(),"deterministic forward metrics");
            assertEquals(fifoB.metrics(),DataflowSolver.solve(p.session,backward).metrics(),"deterministic backward metrics");
            for(int schedule=0;schedule<3;schedule++) {
                var actualF=DataflowSolver.solve(p.session,forward,Schedules.ordered(schedule));
                var actualB=DataflowSolver.solve(p.session,backward,Schedules.ordered(schedule));
                expectedF.compare(actualF); expectedB.compare(actualB);
                ConcreteOracle.forward(p,actualF,true); ConcreteOracle.backward(p,actualB);
            }
            comparisons+=8; points+=expectedF.in.size();
        }
        System.out.println("W2_CORPUS {\"graphs\":80,\"directions\":2,\"schedules\":4,\"comparisons\":"+comparisons+",\"contextualPoints\":"+points+",\"concreteChecks\":640,\"seedBase\":19073}");
    }
    @Test void backwardAnchorsAndReverseOperationOrderAreObservable() {
        var p=program(new int[][]{{1},{}},new Op[][]{new Op[0],{Op.USE_Y,Op.SET_X,Op.USE_X}});
        var result=DataflowSolver.solve(p.session,new Backward(p)); var c=p.contexts.getFirst();
        assertEquals(0L,result.out(c,node(p.session,1)),"backward stable OUT anchor");
        assertEquals(2L,result.in(c,node(p.session,1)),"backward reverse block IN");
        assertEquals(2L,result.out(c,node(p.session,0)),"backward propagates IN to predecessor OUT");
        assertArrayEquals(new long[]{2,0,1,0},replay(p,result));
        ConcreteOracle.backward(p,result);
        var simple=program(new int[][]{{}},new Op[][]{{Op.SET_X,Op.USE_X}});
        var r=DataflowSolver.solve(simple.session,new Backward(simple));
        long end=r.out(simple.contexts.getFirst(),node(simple.session,0));
        assertEquals(0,end); assertEquals(1,new Backward(simple).reverse(new Op[]{Op.USE_X},end,new DomainWork()));
        assertEquals(0,r.in(simple.contexts.getFirst(),node(simple.session,0)));
    }
    static long[] replay(Program p,DataflowResult<Long> result) {
        var c=p.contexts.getFirst(); var n=node(p.session,1); Op[] ops=p.block(n);
        long[] states=new long[ops.length+1]; long state=result.out(c,n); states[ops.length]=state;
        for(int i=ops.length-1;i>=0;i--) {
            state=new Backward(p).reverse(new Op[]{ops[i]},state,new DomainWork()); states[i]=state;
        }
        return states;
    }
    @Test void monotoneButWrongSharedTransferIsRejectedByConcreteInclusion() {
        var p=program(new int[][]{{}},new Op[][]{{Op.SET_X}});
        Forward wrong=new Forward(p) {
            @Override Set<Integer> apply(Op[] ops,Set<Integer> a,DomainWork w) {
                return ops.length==0?a:(a.isEmpty()?Set.of():Set.of(2));
            }
        };
        var result=DataflowSolver.solve(p.session,wrong);
        new RecompositionOracle<>(p,wrong).compare(result);
        var failure=assertThrows(AssertionError.class,()->ConcreteOracle.forward(p,result,true));
        assertTrue(failure.getMessage().contains("concrete inclusion"));
    }
    @Test void alwaysTopPassesInclusionButFailsMinimumPrecision() {
        var p=program(new int[][]{{}},new Op[][]{{Op.SET_X}});
        Forward top=new Forward(p) {
            @Override public Set<Integer> transferBlock(AnalysisPoint point,Set<Integer> a,DomainWork w) {
                return a.isEmpty()?a:Set.of(0,1,2,3);
            }
        };
        var result=DataflowSolver.solve(p.session,top);
        ConcreteOracle.forward(p,result,false);
        var failure=assertThrows(AssertionError.class,()->ConcreteOracle.forward(p,result,true));
        assertTrue(failure.getMessage().contains("minimum precision"));
    }
    @Test void validForwardTransferPassesConcreteAndPrecisionOracle() {
        var p=program(new int[][]{{}},new Op[][]{{Op.SET_X}});
        var result=DataflowSolver.solve(p.session,new Forward(p));
        ConcreteOracle.forward(p,result,true);
        assertEquals(Set.of(1),result.out(p.contexts.getFirst(),node(p.session,0)));
    }
    @Test void distinctContextsRemainIsolatedAndUnselectedEntriesCreateNoPoints() {
        var p=new Program(new int[][]{{1},{},{}},new int[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0},2,
                new Op[][]{new Op[0],new Op[0],new Op[0]},false);
        var result=DataflowSolver.solve(p.session,new Forward(p));
        assertEquals(8,result.metrics().analysisPoints());
        for(int c=0;c<2;c++) {
            assertEquals(Set.of(c),result.in(p.contexts.get(c),node(p.session,1)),"context isolation");
            assertFalse(result.contains(p.contexts.get(c),node(p.session,2)),"orphan has no state slot");
        }
        new RecompositionOracle<>(p,new Forward(p)).compare(result);
        ConcreteOracle.forward(p,result,true);
        var backward=new Backward(p) {
            @Override public Iterable<Boundary<Long>> boundaries(AnalysisSession s) {
                return List.of(new Boundary<>(p.contexts.get(0),node(s,1),1L),new Boundary<>(p.contexts.get(1),node(s,1),2L));
            }
        };
        var reverse=DataflowSolver.solve(p.session,backward);
        assertEquals(1L,reverse.out(p.contexts.get(0),node(p.session,0)));
        assertEquals(2L,reverse.out(p.contexts.get(1),node(p.session,0)));
        new RecompositionOracle<>(p,backward).compare(reverse);
    }
    @Test void finiteDomainLawsAndNonIdempotentBlockTransfer() {
        var p=program(new int[][]{{}},new Op[][]{{Op.FLIP_X}}); var f=new Forward(p); var w=new DomainWork();
        List<Set<Integer>> states=new ArrayList<>();
        for(int mask=0;mask<16;mask++) { Set<Integer> s=new TreeSet<>(); for(int v=0;v<4;v++) if((mask&(1<<v))!=0) s.add(v); states.add(s); }
        for(var a:states) for(var b:states) {
            assertEquals(f.joinInto(a,b,w).state(),f.joinInto(b,a,w).state());
            assertEquals(a,f.joinInto(a,a,w).state());
            if(b.containsAll(a)) assertTrue(f.apply(p.blocks[0],b,w).containsAll(f.apply(p.blocks[0],a,w)));
            for(var c:states) assertEquals(f.joinInto(f.joinInto(a,b,w).state(),c,w).state(),f.joinInto(a,f.joinInto(b,c,w).state(),w).state());
        }
        assertEquals(Set.of(1),DataflowSolver.solve(p.session,f).out(p.contexts.getFirst(),node(p.session,0)),"apply block exactly once per evaluation");
    }
}
