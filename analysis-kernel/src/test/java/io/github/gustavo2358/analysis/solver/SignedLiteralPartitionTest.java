package io.github.gustavo2358.analysis.solver;

import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SignedLiteralPartitionTest {
    private static AnalysisResources memory(){return new AnalysisResources(new AnalysisResources.Limits(64000000,100000,0,0,0,500000000,0));}
    @Test void commonAndResidualSignedSetsMatchIndependentMaps(){
        var memory=memory();var random=new Random(392801);
        try(var pages=new ResidentPageStore(128,memory,AnalysisResources.Phase.CONTROL);
            var arena=new CanonicalTupleArena(pages,memory,AnalysisResources.Phase.CONTROL,6,new int[]{2,4,5});
            var sets=new SignedLiteralSet(arena,memory)){
            for(int round=0;round<400;round++){
                long a=0,b=0;var left=new TreeMap<Integer,Boolean>();var right=new TreeMap<Integer,Boolean>();
                for(int i=0;i<12;i++){
                    int key=i%4==0?Integer.MAX_VALUE:i%4==1?0:random.nextInt(128);boolean negative=random.nextBoolean();left.put(key,negative);a=sets.put(a,key,negative);
                    key=i%4==0?Integer.MAX_VALUE:i%4==1?0:random.nextInt(128);negative=random.nextBoolean();right.put(key,negative);b=sets.put(b,key,negative);
                }
                var common=new TreeMap<Integer,Boolean>();var residual=new TreeMap<Integer,Boolean>();
                for(var entry:left.entrySet())if(Objects.equals(right.get(entry.getKey()),entry.getValue()))common.put(entry.getKey(),entry.getValue());else residual.put(entry.getKey(),entry.getValue());
                assertMap(sets,sets.intersection(a,b),common);assertMap(sets,sets.without(a,b),residual);
                assertEquals(sets.intersection(a,b),sets.intersection(b,a));
                assertEquals(a,sets.without(a,0));assertEquals(0,sets.without(a,a));assertEquals(0,sets.intersection(a,a^1));
            }
        }
        assertEquals(0,memory.heapUsed());
    }
    private static void assertMap(SignedLiteralSet sets,long root,Map<Integer,Boolean> expected){
        assertEquals(expected.size(),sets.size(root));for(var entry:expected.entrySet())assertEquals(entry.getValue()?2:1,sets.polarity(root,entry.getKey()));
    }
}
