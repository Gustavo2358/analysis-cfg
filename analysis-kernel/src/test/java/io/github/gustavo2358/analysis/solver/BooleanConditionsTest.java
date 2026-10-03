package io.github.gustavo2358.analysis.solver;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BooleanConditionsTest {
    @Test void operationsMatchIndependentTruthTablesAndCanonicalize() {
        var b=new BooleanConditions();int x=b.variable(0),y=b.variable(1),z=b.variable(2);
        int f=b.or(b.and(x,y),b.and(b.not(x),z));
        for(int bits=0;bits<8;bits++) {
            int value=f;for(int i=0;i<3;i++)value=b.restrict(value,i,(bits&(1<<i))!=0);
            boolean expected=(bits&1)!=0?(bits&2)!=0:(bits&4)!=0;
            assertEquals(expected?1:0,value);
            assertEquals(expected,b.test(f,java.util.BitSet.valueOf(new long[]{bits})));
        }
        assertEquals(f,b.or(b.and(z,b.not(x)),b.and(y,x)));
        assertEquals(0,b.and(f,b.not(f)));assertEquals(1,b.or(f,b.not(f)));
        assertEquals(y,b.restrict(f,0,true));assertEquals(z,b.restrict(f,0,false));
        assertEquals(b.and(x,b.or(y,z)),b.setPresent(f,0));
    }
    @Test void computedTableCollisionsPreserveEveryTruthAssignment() {
        var random=new java.util.Random(4117);var b=new BooleanConditions(2);
        var forms=new java.util.ArrayList<Integer>();var truths=new java.util.ArrayList<Long>();
        for(int v=0;v<6;v++){forms.add(b.variable(v));long mask=0;for(int bits=0;bits<64;bits++)if((bits&(1<<v))!=0)mask|=1L<<bits;truths.add(mask);}
        for(int i=0;i<300;i++) {
            int a=random.nextInt(forms.size()),c=random.nextInt(forms.size());boolean union=random.nextBoolean();
            forms.add(union?b.or(forms.get(a),forms.get(c)):b.and(forms.get(a),forms.get(c)));
            truths.add(union?truths.get(a)|truths.get(c):truths.get(a)&truths.get(c));
            int index=forms.size()-1;
            if(random.nextBoolean()){forms.set(index,b.not(forms.get(index)));truths.set(index,~truths.get(index));}
            for(int bits=0;bits<64;bits++){int value=forms.get(index);for(int v=0;v<6;v++)value=b.restrict(value,v,(bits&(1<<v))!=0);assertEquals((truths.get(index)>>>bits)&1,value);assertEquals(value==1,b.test(forms.get(index),java.util.BitSet.valueOf(new long[]{bits})));}
        }
    }
    @Test void scratchQueriesReleaseNodesWithoutReusingStaleComputedEntries() {
        var b=new BooleanConditions(8);var random=new java.util.Random(739);
        var base=new java.util.ArrayList<Integer>();var masks=new java.util.ArrayList<Long>();
        for(int v=0;v<6;v++){base.add(b.variable(v));long mask=0;for(int bits=0;bits<64;bits++)if((bits&(1<<v))!=0)mask|=1L<<bits;masks.add(mask);}
        int checkpoint=b.size();
        for(int round=0;round<30;round++) {
            var forms=new java.util.ArrayList<>(base);var truths=new java.util.ArrayList<>(masks);
            for(int i=0;i<40;i++) {
                int a=random.nextInt(forms.size()),c=random.nextInt(forms.size());boolean union=random.nextBoolean();
                int f=union?b.or(forms.get(a),forms.get(c)):b.and(forms.get(a),forms.get(c));
                long truth=union?truths.get(a)|truths.get(c):truths.get(a)&truths.get(c);
                if(random.nextBoolean()){f=b.not(f);truth=~truth;}
                forms.add(f);truths.add(truth);
                for(int bits=0;bits<64;bits++)assertEquals(((truth>>>bits)&1)!=0,b.test(f,java.util.BitSet.valueOf(new long[]{bits})));
            }
            b.discardAfter(checkpoint);assertEquals(checkpoint,b.size());
        }
    }
    @Test void requiredPresentIsNecessaryForEverySatisfyingAssignment() {
        var b=new BooleanConditions();int x=b.variable(0),y=b.variable(1),z=b.variable(2);
        assertEquals(1,b.requiredPresent(b.and(b.not(x),y)));
        assertEquals(-1,b.requiredPresent(b.or(x,y)));assertEquals(-1,b.requiredPresent(b.not(x)));
        for(int table=0;table<256;table++) {
            int formula=0;
            for(int bits=0;bits<8;bits++)if((table&(1<<bits))!=0) {
                int term=1;for(int key=0;key<3;key++)term=b.and(term,(bits&(1<<key))==0?b.not(b.variable(key)):b.variable(key));
                formula=b.or(formula,term);
            }
            int required=b.requiredPresent(formula);
            if(required>=0)for(int bits=0;bits<8;bits++)if((table&(1<<bits))!=0)assertTrue((bits&(1<<required))!=0);
        }
    }
    @Test void deepGuardConditionsDoNotUseTheJavaCallStack() {
        var b=new BooleanConditions();int all=1;
        for(int i=19999;i>=0;i--)all=b.node(i,0,all);
        int complement=b.not(all);
        assertEquals(1,b.or(all,complement));assertEquals(0,b.and(all,complement));
        assertEquals(0,b.restrict(all,19999,false));
    }
}
