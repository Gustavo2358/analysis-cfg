package io.github.gustavo2358.analysis.solver;

import java.util.BitSet;
import java.util.Random;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BooleanFactorizationTest {
    @Test void nestedDifferencesDoNotObscureCommonFactors(){
        for(boolean dual:new boolean[]{false,true})for(int count:new int[]{4,16,64,128})for(int seed=0;seed<4;seed++){
            try(var b=new BooleanConditions(64)){
                var random=new Random(seed);var order=new java.util.ArrayList<Integer>();
                boolean[] negative=new boolean[count+1];int[] values=new int[count+1];
                for(int key=0;key<=count;key++){
                    negative[key]=seed!=0&&random.nextBoolean();values[key]=b.variable(key)^(negative[key]?1:0);
                    if(key>0)order.add(key);
                }
                if(seed!=0)java.util.Collections.shuffle(order,random);
                int root=dual?0:1;
                for(int key:order){
                    int clause=dual?b.and(values[0],values[key]):b.or(values[0],values[key]);
                    root=dual?b.or(root,b.not(b.or(root,b.not(clause))))
                        :b.and(root,b.not(b.and(root,b.not(clause))));
                }
                int result=root;
                // Exhaust all small inputs; large inputs also visit the rare
                // all-residuals-true valuation and every single-key perturbation.
                int exhaustive=count==4?1<<(count+1):0;
                for(int sample=0;sample<exhaustive+2*(count+2)+64;sample++){
                    var bits=new BitSet();
                    for(int key=0;key<=count;key++){
                        boolean value=sample<exhaustive?(sample&(1<<key))!=0
                            :sample<exhaustive+count+2?key!=sample-exhaustive-1
                            :sample<exhaustive+2*(count+2)?key==sample-exhaustive-count-3:random.nextBoolean();
                        if(value!=negative[key])bits.set(key);
                    }
                    boolean rest=!dual;
                    for(int key=1;key<=count;key++){
                        boolean literal=bits.get(key)!=negative[key];rest=dual?rest||literal:rest&&literal;
                    }
                    boolean common=bits.get(0)!=negative[0];
                    assertEquals(dual?common&&rest:common||rest,b.test(root,bits));
                }
                b.collect(mark->mark.accept(result));
                assertTrue(b.equivalenceComparisons()<=4L*count,"avoidable equivalences count="+count+" dual="+dual+" seed="+seed+" calls="+b.equivalenceComparisons());
                assertTrue(b.retainedNodes()<=8,"factored result count="+count+" dual="+dual+" seed="+seed+" live="+b.retainedNodes());
            }
        }
    }

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
    @Test void nativePremisesPreserveAllAssignmentsInMixedSignedJunctions(){
        for(boolean union:new boolean[]{false,true})for(int polarity=0;polarity<16;polarity++){
            try(var b=new BooleanConditions(64)){
                int[] literals=new int[4];
                for(int key=0;key<4;key++)literals[key]=b.variable(key)^((polarity>>>key)&1);
                int premise=union?b.or(literals[0],literals[1]):b.and(literals[0],literals[1]);
                int known=union?b.and(literals[0]^1,literals[2]):b.or(literals[0]^1,literals[2]);
                int rest=union?b.and(literals[2],literals[3]):b.or(literals[2],literals[3]);
                int mixed=union?b.and(known,rest):b.or(known,rest);
                int result=union?b.or(premise,mixed):b.and(premise,mixed);
                for(int mask=0;mask<16;mask++){
                    int assignment=mask^polarity;
                    boolean a=(assignment&1)!=0,c=(assignment&2)!=0,x=(assignment&4)!=0,y=(assignment&8)!=0;
                    boolean expected=union?(a||c)||((!a&&x)&&(x&&y)):(a&&c)&&((!a||x)||(x||y));
                    assertEquals(expected,b.test(result,BitSet.valueOf(new long[]{mask})),"union="+union+" polarity="+polarity+" assignment="+mask);
                }
            }
        }
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
