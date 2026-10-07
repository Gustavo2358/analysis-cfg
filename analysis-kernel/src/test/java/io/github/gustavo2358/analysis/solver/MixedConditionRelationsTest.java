package io.github.gustavo2358.analysis.solver;

import java.util.ArrayList;
import java.util.BitSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import static org.junit.jupiter.api.Assertions.*;

/** Compact, independent mixed relations. No AIR names or solver scheduling are involved. */
class MixedConditionRelationsTest {
    private enum Relation { ANY_PAIR, ALL_EQUAL }
    private static int formula(BooleanConditions b,int count,boolean separated,Relation relation) {
        int result=relation==Relation.ANY_PAIR?0:1;
        for(int i=0;i<count;i++) {
            int a=b.variable(separated?i:2*i),c=b.variable(separated?count+i:2*i+1);
            int term=relation==Relation.ANY_PAIR?b.and(a,c):b.or(b.and(a,c),b.and(b.not(a),b.not(c)));
            result=relation==Relation.ANY_PAIR?b.or(result,term):b.and(result,term);
        }
        return result;
    }
    @Test void mixedRelationsAgreeWithIndependentAllValuationsAndRestrictions() {
        int count=4;
        for(var relation:Relation.values())for(boolean separated:new boolean[]{false,true})try(var b=new BooleanConditions(2)) {
            int root=formula(b,count,separated,relation);
            for(int bits=0;bits<1<<(2*count);bits++) {
                boolean expected=relation==Relation.ALL_EQUAL;
                for(int i=0;i<count;i++) {
                    boolean a=(bits&(1<<(separated?i:2*i)))!=0,c=(bits&(1<<(separated?count+i:2*i+1)))!=0;
                    expected=relation==Relation.ANY_PAIR?expected||(a&&c):expected&&(a==c);
                }
                assertEquals(expected,b.test(root,BitSet.valueOf(new long[]{bits})));
                int restricted=root;for(int key=0;key<2*count;key++)restricted=b.restrict(restricted,key,(bits&(1<<key))!=0);
                assertEquals(expected?1:0,restricted);
            }
        }
    }
    @Test void compactMixedRelationsMustNotExpandIntoEveryBooleanAssignment() {
        var checks=new ArrayList<Executable>();
        for(var relation:Relation.values())for(int count:new int[]{8,10,12})try(var b=new BooleanConditions(8)) {
            int root=formula(b,count,true,relation);b.collect(mark->mark.accept(root));
            int live=b.retainedNodes();long peak=b.peakNodes();
            checks.add(()->assertTrue(live<=64L*count,
                    relation+" pairs="+count+" live="+live+" peak="+peak+" compact input consists of O(pairs) clauses"));
        }
        assertAll(checks);
    }
}
