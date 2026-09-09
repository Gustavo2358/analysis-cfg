package io.github.gustavo2358.analysis.solver;

import io.github.gustavo2358.analysis.cfg.domain.CfgTransition;
import io.github.gustavo2358.analysis.structure.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static io.github.gustavo2358.analysis.solver.SyntheticAnalyses.*;
import static io.github.gustavo2358.analysis.structure.SolverFixtureBridge.*;

class SolverPropertiesTest {
    @Test void firstBottomPublicationAndExitlessSccAreProcessed() {
        var p=program(new int[][]{{0}},new Op[][]{new Op[0]}); var c=p.contexts.getFirst();
        var definition=new Backward(p) {
            @Override public Long transferBlock(AnalysisPoint point,Long a,DomainWork w) { return 0L; }
            @Override public Long transferEdge(AnalysisPoint point,CfgTransition e,Long a,DomainWork w) { return 1L; }
        };
        var result=DataflowSolver.solve(p.session,definition);
        assertEquals(1L,result.out(c,node(p.session,0)),"first bottom publication must deliver nonidentity edge");
        assertEquals(0L,result.in(c,node(p.session,0)));
        assertEquals(2,result.metrics().firstPublications());
        assertEquals(2,result.metrics().edgeContributionJoins());
        new RecompositionOracle<>(p,definition).compare(result);
    }
    @Test void selfLoopReenqueuesAfterMembershipIsCleared() {
        var p=program(new int[][]{{0}},new Op[][]{new Op[0]}); var c=p.contexts.getFirst();
        var definition=rank(p,3);
        var result=DataflowSolver.solve(p.session,definition);
        assertEquals(3L,result.in(c,node(p.session,0)),"self loop reaches finite height");
        assertEquals(3L,result.out(c,node(p.session,0)));
        new RecompositionOracle<>(p,definition).compare(result);
    }
    static AnalysisDefinition<Long> rank(Program p,long height) {
        return new Backward(p) {
            @Override public Direction direction() { return Direction.FORWARD; }
            @Override public Join<Long> joinInto(Long a,Long b,DomainWork w) { w.joinEntryVisited(); return new Join<>(Math.max(a,b),b>a); }
            @Override public Long transferBlock(AnalysisPoint point,Long a,DomainWork w) { return point.node()==node(p.session,0)?Math.min(height,a+1):a; }
            @Override public Long transferEdge(AnalysisPoint point,CfgTransition e,Long a,DomainWork w) { return a; }
        };
    }
    @Test void multipleBoundariesJoinOnceOnCorrectSideInBothDirections() {
        var p=program(new int[][]{{0,1},{}},new Op[][]{new Op[0],new Op[0]}); var c=p.contexts.getFirst();
        for(Direction direction:Direction.values()) {
            var definition=new Backward(p) {
                @Override public Direction direction() { return direction; }
                @Override public Iterable<Boundary<Long>> boundaries(AnalysisSession s) {
                    return List.of(new Boundary<>(c,node(s,0),1L),new Boundary<>(c,node(s,0),2L),new Boundary<>(c,node(s,1),4L));
                }
                @Override public Long transferBlock(AnalysisPoint point,Long a,DomainWork w) { return a&~4L; }
            };
            var result=DataflowSolver.solve(p.session,definition);
            assertEquals(3,result.metrics().boundaryJoins(),"boundaries joined once, never per pop");
            assertEquals(4L,(direction==Direction.FORWARD?result.in(c,node(p.session,1)):result.out(c,node(p.session,1)))&4L,"boundary anchor side");
            assertEquals(0L,(direction==Direction.FORWARD?result.out(c,node(p.session,1)):result.in(c,node(p.session,1)))&4L,"block kills boundary bit");
            new RecompositionOracle<>(p,definition).compare(result);
        }
    }
    @Test void coalescedArrivalsSuppressDuplicateEnqueueAndSubsumedJoins() {
        var p=program(new int[][]{{1,2},{3},{3},{}},new Op[][]{new Op[0],{Op.SET_X},{Op.SET_Y},new Op[0]});
        var result=DataflowSolver.solve(p.session,new Forward(p)); var m=result.metrics();
        assertEquals(6,m.worklistPushes(),"coalesced diamond enqueues each point once");
        assertTrue(m.duplicatePushesSuppressed()>0);
        assertEquals(m.analysisPoints()+m.accumulatorStatesChanged(),m.worklistAttempts(),"unchanged join never enqueues");
        assertEquals(m.worklistPushes(),m.nodesPopped());
        assertEquals(m.nodesPopped(),m.nodesTransferred());
        assertEquals(m.edgeContributionJoins(),m.edgeTransferInvocations());
        assertTrue(m.maxWorklistSize()<=m.analysisPoints());
        var parallel=new Program(new int[][]{{1,1},{}},new int[]{0},1,new Op[][]{new Op[0],new Op[0]},false);
        var repeated=DataflowSolver.solve(parallel.session,new Forward(parallel)).metrics();
        assertTrue(repeated.accumulatorStatesUnchanged()>0,"parallel equal edges exercise subsumed contribution");
        assertEquals(repeated.initializationAttempts()+repeated.accumulatorStatesChanged(),repeated.worklistAttempts(),"unchanged join never enqueues");
    }
    @Test void equivalentFreshRootsStopPropagationAndKeepPublishedRoot() {
        var p=program(new int[][]{{1,2},{3},{3},{}},new Op[][]{new Op[0],new Op[0],new Op[0],new Op[0]});
        var f=new Forward(p) {
            @Override public Set<Integer> transferBlock(AnalysisPoint point,Set<Integer> a,DomainWork w) {
                return Collections.unmodifiableSet(new TreeSet<>(Set.of(0)));
            }
        };
        var result=DataflowSolver.solve(p.session,f,Schedules.ordered(1)); var m=result.metrics();
        assertTrue(m.publishedStatesUnchanged()>0,"fixture must reevaluate equivalent allocated roots");
        assertEquals(m.analysisPoints(),m.firstPublications());
        assertEquals(0,m.publishedStatesChanged(),"semantic equality, not pointer equality");
        assertEquals(m.contextualEdges(),m.edgeContributionJoins(),"unchanged publications do not propagate");
    }
    @Test void failuresExposeNoStableResultAndForeignBoundaryIsRejected() {
        var p=program(new int[][]{{}},new Op[][]{new Op[0]}); var c=p.contexts.getFirst();
        var sentinel=new IllegalStateException("deliberate transfer failure");
        var bad=new Backward(p) { @Override public Long transferBlock(AnalysisPoint point,Long a,DomainWork w) { throw sentinel; } };
        assertSame(sentinel,assertThrows(IllegalStateException.class,()->DataflowSolver.solve(p.session,bad)));
        var other=program(new int[][]{{}},new Op[][]{new Op[0]});
        var foreign=new Backward(p) {
            @Override public Iterable<Boundary<Long>> boundaries(AnalysisSession s) { return List.of(new Boundary<>(c,node(other.session,0),1L)); }
        };
        assertThrows(IllegalArgumentException.class,()->DataflowSolver.solve(p.session,foreign));
        var result=DataflowSolver.solve(p.session,new Forward(p));
        assertThrows(IllegalArgumentException.class,()->result.in(c,node(other.session,0)));
        assertThrows(UnsupportedOperationException.class,()->result.in(c,node(p.session,0)).add(3));
    }
    @Test void emptySelectionIsStableWithoutPhantomStates() {
        var p=new Program(new int[][]{{}},new int[]{0},0,new Op[][]{new Op[0]},false);
        var result=DataflowSolver.solve(p.session,new Forward(p));
        assertEquals(DataflowResult.Status.STABLE,result.status());
        assertEquals(0,result.metrics().analysisPoints()); assertEquals(0,result.metrics().worklistPushes());
    }
}
