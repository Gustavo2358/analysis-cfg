package io.github.gustavo2358.analysis.structure;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class CallerSupportTest {
 @Test void condensedSupportEqualsIndependentScalarReachability() {
  var random=new Random(726019);
  for(int n=0;n<45;n++)for(int trial=0;trial<12;trial++) {
   int[] keys=new int[n];int[][] parents=new int[n][];boolean[][] reaches=new boolean[n][n];
   for(int i=0;i<n;i++) {
    keys[i]=random.nextInt(Math.max(1,n/2));var row=new ArrayList<Integer>();reaches[i][i]=true;
    for(int j=0;j<n;j++)if(random.nextInt(7)==0){row.add(j);reaches[j][i]=true;if(random.nextBoolean())row.add(j);}
    parents[i]=row.stream().mapToInt(Integer::intValue).toArray();
   }
   for(int k=0;k<n;k++)for(int i=0;i<n;i++)for(int j=0;j<n;j++)reaches[i][j]|=reaches[i][k]&&reaches[k][j];
   var actual=CallerSupport.compute(keys,parents);
   for(int i=0;i<n;i++){var expected=new BitSet();for(int j=0;j<n;j++)if(reaches[j][i])expected.set(keys[j]);assertEquals(expected,actual[i],"vertex "+i+" trial "+trial);}
  }
 }
}
