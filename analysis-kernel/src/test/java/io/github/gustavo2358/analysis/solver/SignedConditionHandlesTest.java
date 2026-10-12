package io.github.gustavo2358.analysis.solver;

import java.util.BitSet;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SignedConditionHandlesTest {
    @Test void positiveAndNegativeRootsShareOneRequiredFunctionRecord(){
        try(var b=new BooleanConditions(64)){
            b.enableOwnership();long[] roots=new long[256];
            for(int key=0;key<128;key++){
                int positive=b.variable(key),negative=b.not(positive);
                roots[key*2]=b.retainRoot(positive);roots[key*2+1]=b.retainRoot(negative);
            }
            b.publishCreated();assertEquals(130,b.retainedNodes(),"each complement pair needs one function record plus constants");
            for(int key=0;key<128;key++){
                int positive=(int)b.rootValue(roots[key*2]),negative=(int)b.rootValue(roots[key*2+1]);var bits=new BitSet();bits.set(key);
                assertTrue(b.test(positive,bits));assertFalse(b.test(negative,bits));
                b.releaseRoot(roots[key*2]);assertTrue(b.test(negative,new BitSet()));b.releaseRoot(roots[key*2+1]);
            }
            assertEquals(2,b.retainedNodes());
        }
    }
    @Test void mixedComplementSharesStorageWhileBindingsPreserveBothPolarities(){
        try(var b=new BooleanConditions(64)){
            int a=b.variable(0),c=b.variable(1),d=b.variable(2);
            b.enableOwnership();b.retainPermanentRoot(a);b.retainPermanentRoot(c);b.retainPermanentRoot(d);b.publishCreated();
            int value=b.or(b.and(a,c),b.and(b.not(a),d));long root=b.retainRoot(value);b.publishCreated();int live=b.retainedNodes();
            int negative=b.not(value);long negativeRoot=b.retainRoot(negative);b.publishCreated();
            assertEquals(live,b.retainedNodes(),"complement cannot allocate another expression record");
            assertEquals(b.generationOf(value),b.generationOf(negative));
            assertEquals(value,b.not(negative));b.bindRoot(root,negative);assertEquals(negative,b.rootValue(root));
            for(int mask=0;mask<8;mask++){
                boolean expected=(mask&1)!=0?(mask&2)!=0:(mask&4)!=0;
                assertEquals(!expected,b.test((int)b.rootValue(root),BitSet.valueOf(new long[]{mask})));
            }
            b.bindRoot(root,value);b.releaseRoot(root);assertEquals(negative,b.rootValue(negativeRoot));
            for(int mask=0;mask<8;mask++)assertEquals(!((mask&1)!=0?(mask&2)!=0:(mask&4)!=0),b.test(negative,BitSet.valueOf(new long[]{mask})));
            b.releaseRoot(negativeRoot);assertEquals(5,b.retainedNodes());
        }
    }
    @Test void nativePromotionDuringRestrictionKeepsSignedRootsAndDecisionMeanings(){
        for(boolean discard:new boolean[]{false,true})try(var b=new BooleanConditions(64)){
            int p=b.variable(0),q=b.variable(1),r=b.variable(2);
            b.enableOwnership();b.retainPermanentRoot(p);b.retainPermanentRoot(q);b.retainPermanentRoot(r);b.publishCreated();
            int value=b.and(b.and(p,b.or(q,r)),b.or(q,b.not(r)));
            long positive=b.retainRoot(value),negative=b.retainRoot(b.not(value));b.publishCreated();
            long generation=b.generationOf(value);int checkpoint=b.checkpoint();
            assertEquals(value,b.restrict(value,2,false));
            assertEquals(b.not(value),b.restrict(b.not(value),2,true));
            if(discard)b.discardAfter(checkpoint);else b.commitAfter(checkpoint,mark->{mark.accept(value);mark.accept(b.not(value));});
            assertEquals(generation,b.generationOf(value));
            for(int mask=0;mask<8;mask++){
                boolean expected=(mask&3)==3;var bits=BitSet.valueOf(new long[]{mask});
                assertEquals(expected,b.test((int)b.rootValue(positive),bits));assertEquals(!expected,b.test((int)b.rootValue(negative),bits));
            }
            b.releaseRoot(positive);b.releaseRoot(negative);assertEquals(5,b.retainedNodes());
        }
    }

}
