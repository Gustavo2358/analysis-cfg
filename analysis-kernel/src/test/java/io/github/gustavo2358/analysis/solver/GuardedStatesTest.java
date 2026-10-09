package io.github.gustavo2358.analysis.solver;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GuardedStatesTest {
    @Test void failedConstructionDoesNotMaskBudgetFailureDuringScratchRollback() {
        long[] now={0};var resources=new AnalysisResources(new AnalysisResources.Limits(Long.MAX_VALUE,Long.MAX_VALUE,0,0,0,Long.MAX_VALUE,Long.MAX_VALUE),5,()->now[0]);
        try(var b=new BooleanConditions(2,resources)) {
            int x=b.variable(0),y=b.variable(1);var states=new GuardedStates<Integer>(b,definition(),new DomainWork());
            assertTrue(states.add(x,1));now[0]=5;
            var failure=assertThrows(AnalysisResources.Exhausted.class,()->states.add(y,2));
            assertEquals(AnalysisResources.Resource.TIME,failure.resource());assertEquals(5,failure.used());
        }
        assertEquals(0,resources.heapUsed());
    }
    private static AnalysisDefinition<Integer> definition(){return new AnalysisDefinition<>() {
        public Direction direction(){return Direction.FORWARD;}
        public Integer bottom(){return 0;}
        public Iterable<Boundary<Integer>> boundaries(io.github.gustavo2358.analysis.structure.AnalysisSession session){return List.of();}
        public Join<Integer> joinInto(Integer a,Integer b,DomainWork work){int c=a|b;return new Join<>(c,c!=a);}
        public boolean equivalent(Integer a,Integer b,DomainWork work){return a.equals(b);}
        public Integer transferBlock(AnalysisPoint point,Integer state,DomainWork work){return state;}
        public Integer transferEdge(AnalysisPoint point,io.github.gustavo2358.analysis.cfg.domain.CfgTransition edge,Integer state,DomainWork work){return state;}
    };}
    @Test void pointwiseJoinsAndDisjointValueClassesMatchIndependentTables() {
        try(var b=new BooleanConditions(2)) {
            var states=new GuardedStates<Integer>(b,definition(),new DomainWork());
            int[] expected=new int[64];boolean[] reached=new boolean[64];var random=new Random(531408);
            for(int step=0;step<200;step++) {
                int a=random.nextInt(6),c=random.nextInt(6);int condition=b.or(b.variable(a),b.not(b.variable(c))),value=random.nextInt(16);
                boolean changed=false;
                for(int bits=0;bits<64;bits++)if((bits&(1<<a))!=0||(bits&(1<<c))==0) {
                    int next=expected[bits]|value;changed|=!reached[bits]||next!=expected[bits];reached[bits]=true;expected[bits]=next;
                }
                assertEquals(changed,states.add(condition,value));
                for(int bits=0;bits<64;bits++) {
                    int matches=0,actual=0;var word=BitSet.valueOf(new long[]{bits});
                    for(var piece:states.pieces)if(b.test(piece.condition,word)){matches++;actual=piece.state;}
                    assertEquals(reached[bits]?1:0,matches);assertEquals(expected[bits],actual);
                }
                for(int i=0;i<states.pieces.size();i++)for(int j=0;j<i;j++)assertNotEquals(states.pieces.get(i).state,states.pieces.get(j).state);
            }
        }
    }
    @Test void equalValueUnionDoesNotConstructDisjointTransientDifferences() {
        try(var b=new BooleanConditions()) {
            var states=new GuardedStates<Integer>(b,definition(),new DomainWork());
            for(int key=0;key<128;key++)assertTrue(states.add(b.variable(key),1));
            assertEquals(1,states.pieces.size());assertTrue(b.peakNodes()<=4L*128,"avoidable differences="+b.peakNodes());
            assertFalse(states.add(b.variable(64),1));
        }
    }
    @Test void initializedValueClassesJoinWithoutCreatingOldAndNewGuardProducts() {
        try(var b=new BooleanConditions()) {
            var states=new GuardedStates<Integer>(b,definition(),new DomainWork());states.add(1,0);
            for(int key=0;key<128;key++)assertTrue(states.add(b.variable(key),1));
            assertEquals(2,states.pieces.size());
            for(int key=-1;key<128;key++) {
                var word=new BitSet();if(key>=0)word.set(key);int matches=0;
                for(var piece:states.pieces)if(b.test(piece.condition,word)){matches++;assertEquals(key<0?0:1,piece.state);}
                assertEquals(1,matches);
            }
            assertTrue(b.peakNodes()<=8L*128,"unused pointwise intersection product="+b.peakNodes());
        }
    }
    @Test void managedValueClassesOwnAllPublishedGuardsAndReleaseOldVersions(){
        try(var b=new BooleanConditions(64)){
            b.enableOwnership();var states=new GuardedStates<Integer>(b,definition(),new DomainWork());states.add(1,0);b.publishCreated();
            for(int key=0;key<256;key++){
                b.beginMutation();
                try{assertTrue(states.add(b.variable(key),1));}finally{b.endMutation();}
                assertEquals(2,states.pieces.size());assertTrue(b.retainedNodes()<=8,"only published guards survive="+b.retainedNodes());
                var word=new BitSet();word.set(key);
                for(var piece:states.pieces)if(b.test(piece.condition,word))assertEquals(1,piece.state);
            }
            states.clear();assertEquals(2,b.retainedNodes());
        }
    }

}
