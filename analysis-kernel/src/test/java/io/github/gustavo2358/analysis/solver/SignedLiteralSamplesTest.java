package io.github.gustavo2358.analysis.solver;

import java.util.HashMap;
import java.util.Random;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SignedLiteralSamplesTest {
    private static AnalysisResources resources(){return new AnalysisResources(new AnalysisResources.Limits(32000000,100000,0,0,0,1000000000,0));}
    @Test void sampledConjunctionDisjunctionAndComplementedSignsMatchIndependentLiteralValuations() {
        var memory=resources();var expected=new HashMap<Integer,Boolean>();var random=new Random(348961);
        try(var pages=new ResidentPageStore(4096,memory,AnalysisResources.Phase.CONTROL);
            var arena=new CanonicalTupleArena(pages,memory,AnalysisResources.Phase.CONTROL,6+2*PagedBooleanCircuit.SAMPLE_WORDS,new int[]{2,4,5});
            var literals=new SignedLiteralSet(arena,memory)) {
            long root=0;
            for(int step=0;step<160;step++) {
                int key=random.nextInt(64);boolean negative=random.nextBoolean();
                if(random.nextInt(4)==0){root=literals.remove(root,key);expected.remove(key);}
                else {root=literals.put(root,key,negative);expected.put(key,negative);}
                for(int word=0;word<PagedBooleanCircuit.SAMPLE_WORDS;word++) {
                    long conjunction=0,disjunction=0,inverted=0;
                    for(int bit=0;bit<64;bit++) {
                        boolean all=true,some=false,allOpposite=true;
                        for(var entry:expected.entrySet()) {
                            boolean value=((PagedBooleanCircuit.primarySample(entry.getKey(),word)>>>bit)&1)!=0;
                            boolean signed=value!=entry.getValue();all&=signed;some|=signed;allOpposite&=!signed;
                        }
                        if(all)conjunction|=1L<<bit;if(some)disjunction|=1L<<bit;if(allOpposite)inverted|=1L<<bit;
                    }
                    assertEquals(conjunction,literals.sample(root,false,word),"AND step="+step+" word="+word);
                    assertEquals(disjunction,literals.sample(root,true,word),"OR step="+step+" word="+word);
                    assertEquals(inverted,literals.sample(root==0?0:root^1,false,word),"literal parity must swap the conjunction pair");
                }
            }
        }
        assertEquals(0,memory.heapUsed());
    }
    @Test void prefixMetadataUsesCopiedPathsAndRetiresWithItsOwningLiteralRecords() {
        var memory=resources();int count=1024;
        try(var pages=new ResidentPageStore(4096,memory,AnalysisResources.Phase.CONTROL);
            var arena=new CanonicalTupleArena(pages,memory,AnalysisResources.Phase.CONTROL,6+2*PagedBooleanCircuit.SAMPLE_WORDS,new int[]{2,4,5});
            var literals=new SignedLiteralSet(arena,memory)) {
            long root=0;for(int key=0;key<count;key++)root=literals.put(root,key,(key&1)!=0);
            assertTrue(literals.sampleWordsCalculated()<=33L*count*2*PagedBooleanCircuit.SAMPLE_WORDS,"metadata must follow bounded key paths, never scan every prefix");
            long before=literals.sampleWordsCalculated();
            for(int word=0;word<PagedBooleanCircuit.SAMPLE_WORDS;word++)literals.sample(root,false,word);
            assertEquals(before,literals.sampleWordsCalculated());
            long stale=literals.put(0,Integer.MAX_VALUE,false),token=arena.retain(root>>>1);
            assertTrue(arena.collect()>0);assertTrue(arena.size()<=2L*count);
            assertThrows(IllegalArgumentException.class,()->literals.sample(stale,false,0));
            assertEquals(before+2L*PagedBooleanCircuit.SAMPLE_WORDS,literals.sampleWordsCalculated());
            arena.release(token);arena.collect();assertEquals(0,arena.size());
        }
        assertEquals(0,memory.heapUsed());
    }
    @Test void sixFieldClientsKeepTheirOriginalSchemaAndMeaning() {
        var memory=resources();
        try(var pages=new ResidentPageStore(128,memory,AnalysisResources.Phase.CONTROL);
            var arena=new CanonicalTupleArena(pages,memory,AnalysisResources.Phase.CONTROL,6,new int[]{2,4,5});
            var literals=new SignedLiteralSet(arena,memory)) {
            long root=literals.put(literals.put(0,12,false),97,true);
            assertEquals(2,literals.size(root));assertEquals(1,literals.polarity(root,12));assertEquals(2,literals.polarity(root,97));
            assertThrows(IllegalStateException.class,()->literals.sample(root,false,0));
            assertEquals(0,literals.sampleWordsCalculated());
        }
        assertEquals(0,memory.heapUsed());
    }
}
