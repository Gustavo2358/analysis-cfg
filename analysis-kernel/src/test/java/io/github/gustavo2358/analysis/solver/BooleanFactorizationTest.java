package io.github.gustavo2358.analysis.solver;

import java.util.BitSet;
import java.util.Random;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BooleanFactorizationTest {
    @Test void commonNativeFactorsAndTheirDualDoNotRetainExpandedPrefixes(){
        var bounds=new java.util.ArrayList<org.junit.jupiter.api.function.Executable>();
        for(boolean union:new boolean[]{false,true})for(int count:new int[]{4,16,64,128}){
            try(var b=new BooleanConditions(64)){
                int common=union?b.and(b.variable(0),b.not(b.variable(1))):b.or(b.variable(0),b.not(b.variable(1)));
                int root=union?0:1;
                for(int key=2;key<count+2;key++){
                    int literal=key%2==0?b.variable(key):b.not(b.variable(key));
                    int term=union?b.and(common,literal):b.or(common,literal);
                    root=union?b.or(root,term):b.and(root,term);
                }
                int result=root;b.collect(mark->mark.accept(result));
                int live=b.retainedNodes();bounds.add(()->assertTrue(live<=8,"factored native families need no expanded Boolean prefix union="+union+" N="+count+" live="+live));
                var random=new Random(85931);
                for(int sample=0;sample<64;sample++){
                    var bits=new BitSet();for(int key=0;key<count+2;key++)if(random.nextBoolean())bits.set(key);
                    boolean shared=union?bits.get(0)&&!bits.get(1):bits.get(0)||!bits.get(1),remainder=!union;
                    for(int key=2;key<count+2;key++){boolean literal=bits.get(key)==(key%2==0);remainder=union?remainder||literal:remainder&&literal;}
                    assertEquals(union?shared&&remainder:shared||remainder,b.test(result,bits));
                }
            }
        }
        assertAll(bounds);
    }
    @Test void partialSignedFactorsPreserveEveryFourKeyAssignment(){
        for(boolean union:new boolean[]{false,true})for(int polarity=0;polarity<16;polarity++){
            try(var b=new BooleanConditions(64)){
                int[] literal=new int[4];for(int key=0;key<4;key++)literal[key]=(polarity&(1<<key))==0?b.variable(key):b.not(b.variable(key));
                int left=union?b.and(literal[0],b.and(literal[1],literal[2])):b.or(literal[0],b.or(literal[1],literal[2]));
                int right=union?b.and(literal[0],b.and(literal[1],literal[3])):b.or(literal[0],b.or(literal[1],literal[3]));
                int root=union?b.or(left,right):b.and(left,right);
                for(int mask=0;mask<16;mask++){
                    boolean a=((mask^polarity)&1)!=0,c=((mask^polarity)&2)!=0,x=((mask^polarity)&4)!=0,y=((mask^polarity)&8)!=0;
                    assertEquals(union?(a&&c&&(x||y)):(a||c||(x&&y)),b.test(root,BitSet.valueOf(new long[]{mask})));
                }
            }
        }
    }
}
