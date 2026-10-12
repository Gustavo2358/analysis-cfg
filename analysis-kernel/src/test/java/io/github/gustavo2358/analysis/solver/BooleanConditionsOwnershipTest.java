package io.github.gustavo2358.analysis.solver;

import java.util.BitSet;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BooleanConditionsOwnershipTest {
    @Test void rootsKeepExactMixedFunctionsAndReleaseIndependentVersions(){
        try(var conditions=new BooleanConditions(64)){
            int a=conditions.variable(0),b=conditions.variable(1);
            conditions.enableOwnership();long ar=conditions.retainRoot(a),br=conditions.retainRoot(b),result=conditions.retainRoot(0);
            conditions.publishCreated();
            for(int key=2;key<258;key++){
                int c=conditions.variable(key),value=conditions.or(conditions.and(a,b),conditions.and(conditions.not(a),c));
                conditions.bindRoot(result,value);conditions.publishCreated();
                for(int mask=0;mask<8;mask++){
                    var bits=new BitSet();if((mask&1)!=0)bits.set(0);if((mask&2)!=0)bits.set(1);if((mask&4)!=0)bits.set(key);
                    assertEquals(((mask&1)!=0&&(mask&2)!=0)||((mask&1)==0&&(mask&4)!=0),conditions.test(value,bits));
                }
                assertTrue(conditions.retainedNodes()<=12,"retention cannot follow number of replaced versions");
            }
            conditions.releaseRoot(result);conditions.releaseRoot(ar);conditions.releaseRoot(br);
            assertEquals(2,conditions.retainedNodes());
        }
    }
    @Test void computedCacheCannotAliasRecycledFunctionIds(){
        try(var conditions=new BooleanConditions(64)){
            conditions.enableOwnership();long a=conditions.retainRoot(conditions.variable(0)),b=conditions.retainRoot(conditions.variable(1));
            int old=conditions.and((int)conditions.rootValue(a),(int)conditions.rootValue(b));
            long result=conditions.retainRoot(old);conditions.publishCreated();
            conditions.releaseRoot(result);conditions.releaseRoot(a);conditions.releaseRoot(b);
            assertEquals(2,conditions.retainedNodes());
            int x=conditions.variable(8),y=conditions.variable(9),value=conditions.and(x,y);
            result=conditions.retainRoot(value);conditions.publishCreated();
            var bits=new BitSet();bits.set(8);bits.set(9);assertTrue(conditions.test(value,bits));
            bits.clear(9);assertFalse(conditions.test(value,bits));conditions.releaseRoot(result);
            assertEquals(2,conditions.retainedNodes());
        }
    }
    @Test void appendOnlyDecisionScopeDoesNotReuseRetiredMeanings(){
        try(var conditions=new BooleanConditions(64)){
            int a=conditions.variable(0),b=conditions.variable(1),c=conditions.variable(2);
            conditions.enableOwnership();long ar=conditions.retainRoot(a),br=conditions.retainRoot(b),cr=conditions.retainRoot(c);
            conditions.publishCreated();int checkpoint=conditions.checkpoint();
            int mixed=conditions.or(conditions.and(a,b),conditions.and(conditions.not(a),c));
            long result=conditions.retainRoot(mixed);conditions.commitAfter(checkpoint,root->root.accept(mixed));
            assertEquals(mixed,conditions.rootValue(result));
            checkpoint=conditions.checkpoint();assertEquals(mixed,conditions.not(conditions.not(mixed)));conditions.discardAfter(checkpoint);
            conditions.releaseRoot(result);conditions.releaseRoot(ar);conditions.releaseRoot(br);conditions.releaseRoot(cr);
            assertEquals(2,conditions.retainedNodes());
        }
    }
}
