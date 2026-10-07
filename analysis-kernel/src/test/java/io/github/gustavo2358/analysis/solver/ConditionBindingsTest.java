package io.github.gustavo2358.analysis.solver;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ConditionBindingsTest {
    @Test void mapUpdatesAcquireNewRootsBeforeReleasingOldOnes(){
        try(var conditions=new BooleanConditions(64)){
            conditions.enableOwnership();var bindings=new ConditionBindings<Object>(conditions);Object a=new Object(),b=new Object();
            bindings.put(a,conditions.variable(0));bindings.put(b,conditions.variable(1));conditions.publishCreated();
            int merged=conditions.and(bindings.get(a),bindings.get(b));bindings.put(a,merged);bindings.remove(b);conditions.publishCreated();
            assertEquals(merged,bindings.get(a));assertEquals(1,bindings.size());
            assertThrows(UnsupportedOperationException.class,()->bindings.entrySet().iterator().next().setValue(1));
            bindings.clear();assertEquals(2,conditions.retainedNodes());
        }
    }
    @Test void explicitBorrowKeepsPreviousMapVersionAliveAcrossMutation(){
        try(var conditions=new BooleanConditions(64)){
            conditions.enableOwnership();var bindings=new ConditionBindings<Object>(conditions);Object key=new Object();
            int old=conditions.variable(0);bindings.put(key,old);conditions.publishCreated();
            long borrowed=conditions.retainRoot(old);conditions.beginMutation();bindings.put(key,conditions.variable(1));
            assertEquals(0,conditions.atEmpty(old));assertTrue(conditions.test(old,java.util.BitSet.valueOf(new long[]{1})));
            conditions.endMutation();assertEquals(old,conditions.rootValue(borrowed));conditions.releaseRoot(borrowed);assertEquals(3,conditions.retainedNodes());bindings.clear();assertEquals(2,conditions.retainedNodes());
        }
    }
}
