package io.github.gustavo2358.analysis.solver;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SignedLiteralSetTest {
    @Test void signedInclusionAndIntersectionMatchIndependentMapsWithoutUnfoldingSharedTrees() {
        var memory=resources();var random=new Random(469503);
        try(var pages=new ResidentPageStore(4096,memory,AnalysisResources.Phase.CONTROL);
            var arena=new CanonicalTupleArena(pages,memory,AnalysisResources.Phase.CONTROL,6,new int[]{2,4,5});
            var sets=new SignedLiteralSet(arena,memory)) {
            for(int round=0;round<600;round++) {
                long a=0,b=0;var left=new TreeMap<Integer,Boolean>();var right=new TreeMap<Integer,Boolean>();
                for(int i=0;i<12;i++) {
                    int key=random.nextInt(64);boolean negative=random.nextBoolean();left.put(key,negative);a=sets.put(a,key,negative);
                    key=random.nextInt(64);negative=random.nextBoolean();right.put(key,negative);b=sets.put(b,key,negative);
                }
                boolean covers=true,intersects=false;
                for(var item:right.entrySet()){boolean same=Objects.equals(left.get(item.getKey()),item.getValue());covers&=same;intersects|=same;}
                assertEquals(covers,sets.includes(a,b));assertEquals(intersects,sets.intersectsSame(a,b));assertEquals(intersects,sets.intersectsSame(b,a));
                assertTrue(sets.includes(a,a));assertFalse(sets.intersectsSame(a,a^1));assertFalse(sets.includes(a,a^1));
                assertTrue(sets.includes(a,0));assertFalse(sets.intersectsSame(a,0));
            }
        }
        assertEquals(0,memory.heapUsed());
    }
    private static AnalysisResources resources(){return new AnalysisResources(new AnalysisResources.Limits(Long.MAX_VALUE,Long.MAX_VALUE,Long.MAX_VALUE,Long.MAX_VALUE,Long.MAX_VALUE,Long.MAX_VALUE,Long.MAX_VALUE));}
    @Test void signedLiteralUpdatesAndComplementsMatchIndependentSortedMaps() {
        var memory=resources();var random=new Random(496101);
        try(var pages=new ResidentPageStore(4096,memory,AnalysisResources.Phase.CONTROL);
            var arena=new CanonicalTupleArena(pages,memory,AnalysisResources.Phase.CONTROL,6,new int[]{2,4,5});
            var sets=new SignedLiteralSet(arena,memory)) {
            long root=0;var expected=new TreeMap<Integer,Boolean>();
            for(int round=0;round<2000;round++) {
                int key=random.nextInt(80);boolean negative=random.nextBoolean();
                if(random.nextInt(3)==0){root=sets.remove(root,key);expected.remove(key);}
                else {root=sets.put(root,key,negative);expected.put(key,negative);}
                assertEquals(expected.size(),sets.size(root));
                if(!expected.isEmpty())assertEquals(expected.firstKey().intValue(),sets.firstKey(root));
                for(int i=0;i<80;i++)assertEquals(expected.containsKey(i)?expected.get(i)?2:1:0,sets.polarity(root,i));
                if(root!=0)for(var e:expected.entrySet())assertEquals(e.getValue()?1:2,sets.polarity(root^1,e.getKey()));
            }
        }
        assertEquals(0,memory.heapUsed());
    }
    @Test void structuralUnionDetectsContradictionsAndSharesEquivalentPermutations() {
        var memory=resources();var random=new Random(97891);
        try(var pages=new ResidentPageStore(4096,memory,AnalysisResources.Phase.CONTROL);
            var arena=new CanonicalTupleArena(pages,memory,AnalysisResources.Phase.CONTROL,6,new int[]{2,4,5});
            var sets=new SignedLiteralSet(arena,memory)) {
            for(int round=0;round<150;round++) {
                long a=0,b=0;var left=new TreeMap<Integer,Boolean>();var right=new TreeMap<Integer,Boolean>();
                for(int i=0;i<16;i++) {
                    int key=random.nextInt(64);boolean negative=random.nextBoolean();
                    a=sets.put(a,key,negative);left.put(key,negative);
                    key=random.nextInt(64);negative=random.nextBoolean();b=sets.put(b,key,negative);right.put(key,negative);
                }
                boolean conflict=false;var expected=new TreeMap<>(left);
                for(var e:right.entrySet()){if(expected.containsKey(e.getKey())&&!expected.get(e.getKey()).equals(e.getValue()))conflict=true;expected.put(e.getKey(),e.getValue());}
                long actual=sets.union(a,b);assertEquals(actual,sets.union(b,a));
                if(conflict)assertEquals(-1,actual);
                else {
                    long reversed=0;for(var e:expected.descendingMap().entrySet())reversed=sets.put(reversed,e.getKey(),e.getValue());
                    assertEquals(reversed,actual,"canonical identity independent of insertion order");
                }
            }
            long root=0;for(int i=0;i<1024;i++)root=sets.put(root,i,i%3==0);
            long before=sets.visits();assertEquals(root,sets.union(root,root));assertEquals(-1,sets.union(root,root^1));
            assertTrue(sets.visits()-before<8,"shared/complemented roots need no complete scan");
        }
        assertEquals(0,memory.heapUsed());
    }
    @Test void polarityCountsAndFirstSignedKeysSurviveComplementAndRemoval() {
        var memory=resources();
        try(var pages=new ResidentPageStore(4096,memory,AnalysisResources.Phase.CONTROL);
            var arena=new CanonicalTupleArena(pages,memory,AnalysisResources.Phase.CONTROL,6,new int[]{2,4,5});
            var sets=new SignedLiteralSet(arena,memory)) {
            long root=0;for(int i=0;i<16;i++)root=sets.put(root,i,(i&1)==0);
            assertEquals(8,sets.positiveCount(root));assertEquals(1,sets.firstPolarity(root,false));assertEquals(0,sets.firstPolarity(root,true));
            assertEquals(8,sets.positiveCount(root^1));assertEquals(0,sets.firstPolarity(root^1,false));
            for(int i=0;i<16;i+=2)root=sets.remove(root,i);
            assertEquals(8,sets.positiveCount(root));assertEquals(-1,sets.firstPolarity(root,true));
            assertEquals(0,sets.positiveCount(root^1));assertEquals(-1,sets.firstPolarity(root^1,false));
        }
        assertEquals(0,memory.heapUsed());
    }
    @Test void absentRestrictionsMatchIndependentLiteralTruthAndCanonicalKeptKeys() {
        var memory=resources();
        try(var pages=new ResidentPageStore(4096,memory,AnalysisResources.Phase.CONTROL);
            var arena=new CanonicalTupleArena(pages,memory,AnalysisResources.Phase.CONTROL,6,new int[]{2,4,5});
            var sets=new SignedLiteralSet(arena,memory)) {
            for(int signs=0;signs<64;signs++) {
                long root=0;for(int key=0;key<6;key++)root=sets.put(root,key,(signs&(1<<key))!=0);
                for(int allowed=0;allowed<64;allowed++)for(boolean union:new boolean[]{true,false}) {
                    long expected=0;boolean absorbed=false;
                    for(int key=0;key<6;key++) {
                        boolean negative=(signs&(1<<key))!=0;
                        if((allowed&(1<<key))!=0)expected=sets.put(expected,key,negative);
                        else if(negative==union)absorbed=true;
                    }
                    int mask=allowed;assertEquals(absorbed?-1:expected,sets.restrict(root,union,key->(mask&(1<<key))!=0));
                }
            }
        }
        assertEquals(0,memory.heapUsed());
    }
    @Test void growingPrefixesSharePayloadAndComplementDoesNotCopyTheLiteralProduct() {
        var memory=resources();
        try(var pages=new ResidentPageStore(4096,memory,AnalysisResources.Phase.CONTROL);
            var arena=new CanonicalTupleArena(pages,memory,AnalysisResources.Phase.CONTROL,6,new int[]{2,4,5});
            var sets=new SignedLiteralSet(arena,memory)) {
            long root=0;int count=8192;
            for(int i=0;i<count;i++) {
                long before=sets.copies();root=sets.put(root,i*257,(i&1)!=0);
                assertTrue(sets.copies()-before<=32,"only the fixed-width key search path may be copied");
                long complemented=root^1;before=sets.copies();
                assertEquals(-1,sets.union(root,complemented));assertEquals(root,sets.union(root,root));
                assertEquals(before,sets.copies(),"complement and identical roots cannot copy payload");
            }
            assertEquals(count,sets.size(root));assertTrue(arena.size()<=33L*count,"payload growth, not just Boolean IDs, is bounded by path sharing");
            long token=arena.retain(root>>>1);arena.collect();assertTrue(arena.size()<=2L*count);
            assertEquals(count,sets.size(root));arena.release(token);arena.collect();assertEquals(0,arena.size());
        }
        assertEquals(0,memory.heapUsed());
    }
}
