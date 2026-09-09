package io.github.gustavo2358.analysis.values;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.analysis.cfg.domain.*;
import io.github.gustavo2358.analysis.query.*;
import io.github.gustavo2358.analysis.solver.*;
import io.github.gustavo2358.analysis.structure.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static io.github.gustavo2358.analysis.values.ValuesFixtures.*;

class ReplayTest {
    private static final class Backward implements AnalysisDefinition<Long> {
        private final IdentityHashMap<Operation,Integer> codes;
        Backward(IdentityHashMap<Operation,Integer> codes){this.codes=codes;}
        long operation(long state,Operation operation){return switch(codes.getOrDefault(operation,0)){case 1->state|2L;case 2->state&~1L;case 3->state|1L;default->state;};}
        public Direction direction(){return Direction.BACKWARD;}public Long bottom(){return 0L;}
        public Iterable<Boundary<Long>> boundaries(AnalysisSession session){return List.of();}
        public Join<Long> joinInto(Long a,Long b,DomainWork work){return new Join<>(a|b,(a|b)!=a);}
        public boolean equivalent(Long a,Long b,DomainWork work){return a.equals(b);}
        public Long transferEdge(AnalysisPoint point,CfgTransition edge,Long state,DomainWork work){return state;}
        public Long transferBlock(AnalysisPoint point,Long anchor,DomainWork work) {
            long state=anchor;
            if(point.node().source() instanceof CfgNode.SequenceNode node) {
                state=operation(state,node.source().terminator());
                for(int i=node.source().instructions().size()-1;i>=0;i--)state=operation(state,node.source().instructions().get(i));
            }
            return state;
        }
    }
    @Test void backwardReplayUsesStableOutAndReverseSuffixOnce() {
        for(int start:new int[]{0,1}) {
            var p=graph(new String[]{null},new int[][]{{}},0,false,false);var u=p.units().getFirst();var operations=new ArrayList<Instruction>();
            var codes=new IdentityHashMap<Operation,Integer>();
            for(int i=start;i<3;i++){var op=new Operations.Nop(header(u.id(),"op"+i));operations.add(op);codes.put(op,i+1);}
            var seq=returning(u.id(),"s0",operations);
            p=replace(p,List.of(unit(u.id(),u.entries(),List.of(seq),u.objects())),p.coverage(),p.uncertainties(),p.premises());
            var session=session(p);var def=new Backward(codes);var result=DataflowSolver.solve(session,def);var context=session.contexts().iterator().next();var node=session.index().sequence(seq.label());
            assertEquals(start==0?2L:0L,result.in(context,node));assertEquals(0L,result.out(context,node));
            var queries=new ArrayList<PointQuery<Integer>>();var expected=new HashMap<PointQuery<Integer>,Long>();
            long[] before={2,0,1},after={0,1,0};
            for(int i=0;i<operations.size();i++) {
                var b=new PointQuery<>(ProgramPoint.before(context.entry().id(),operations.get(i).header().id()),0);
                var a=new PointQuery<>(ProgramPoint.after(context.entry().id(),operations.get(i).header().id()),0);
                queries.add(b);queries.add(a);queries.add(b);expected.put(b,before[i+start]);expected.put(a,after[i+start]);
            }
            var batch=BatchReplayer.materialize(session,result,Direction.BACKWARD,0L,queries,Comparator.naturalOrder(),def::operation,new BatchReplayer.Projection<Long,Integer,Long>() {
                public boolean supports(PointQuery<Integer> q){return true;}public Long project(PointQuery<Integer> q,Long state){return state;}
            });
            assertEquals(ObservationBatch.Status.COMPLETE,batch.status());
            for(var answer:batch.observations())assertEquals(expected.get(answer.query()),answer.value(),"backward point anchor/order");
            assertEquals(operations.size()+1,batch.metrics().operationsReplayed(),"suffix union includes terminator");
            assertEquals(1,batch.metrics().sequencesReplayed());assertEquals(2*operations.size(),batch.metrics().uniqueQueries());
        }
    }
    @Test void foreignStableRunCannotMasqueradeAsUnreachableInAnotherSession() {
        var p=graph(new String[]{"A"},new int[][]{{}},1,false,false);
        var first=session(p);var second=session(p);
        var run=PossibleValuesAnalysis.prepare(first).analysis().orElseThrow().execute();
        var q=ValuesTest.before(p,0,0);
        assertThrows(IllegalArgumentException.class,()->BatchReplayer.materialize(second,run.dataflow(),Direction.FORWARD,PossibleValuesState.unreachable(),List.of(q),Comparator.comparing(ObjectId::localId),
            (state,op)->state,new BatchReplayer.Projection<PossibleValuesState,ObjectId,String>() {
                public boolean supports(PointQuery<ObjectId> query){return true;}public String project(PointQuery<ObjectId> query,PossibleValuesState state){return "unknown";}
            }),"foreign stable result must be rejected before replay");
    }
    @Test void controlledObservationFailureIsAtomicAndPreservesStableRun() {
        var p=graph(new String[]{"A"},new int[][]{{}},1,false,false);var session=session(p);var run=PossibleValuesAnalysis.prepare(session).analysis().orElseThrow().execute();
        var q=ValuesTest.before(p,0,0);var result=run.dataflow();
        var failed=BatchReplayer.materialize(session,result,Direction.FORWARD,PossibleValuesState.unreachable(),List.of(q,q),Comparator.comparing(ObjectId::localId),
            (state,op)->{throw new ObservationBatch.ObservationException("controlled test replay failure");},new BatchReplayer.Projection<PossibleValuesState,ObjectId,String>() {
                public boolean supports(PointQuery<ObjectId> query){return true;}public String project(PointQuery<ObjectId> query,PossibleValuesState state){return "selected";}
            });
        assertEquals(ObservationBatch.Status.FAILED,failed.status(),"failed observation is not complete");
        assertEquals("OBSERVATION_ERROR",failed.reason());assertTrue(failed.observations().isEmpty());
        assertEquals(1,failed.metrics().queriesNotMaterialized());assertEquals(1,failed.metrics().observationFailures());assertEquals(1,failed.metrics().operationsReplayed());
        assertEquals(DataflowResult.Status.STABLE,result.status());assertSame(result,run.dataflow());
        ValuesTest.expected(ValuesTest.fact(run,q),false,"A");
    }
}
