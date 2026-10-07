package io.github.gustavo2358.analysis.solver;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SignedLiteralSupportTest {
    private static AnalysisResources resources(){return new AnalysisResources(new AnalysisResources.Limits(16000000,100000,0,0,0,1000000000,0));}
    @Test void unsignedSupportIsCanonicalAcrossAllPolaritiesAndRetiresWithItsOwner() {
        var memory=resources();
        try(var pages=new ResidentPageStore(128,memory,AnalysisResources.Phase.CONTROL);
            var arena=new CanonicalTupleArena(pages,memory,AnalysisResources.Phase.CONTROL,63,new int[]{2,4,5,62});
            var sets=new SignedLiteralSet(arena,memory)) {
            long expected=0;for(int key=0;key<6;key++)expected=sets.put(expected,key,false);
            for(int signs=0;signs<64;signs++) {
                long signed=0;for(int key=5;key>=0;key--)signed=sets.put(signed,key,(signs&(1<<key))!=0);
                assertEquals(expected,sets.unsigned(signed));assertEquals(expected,sets.unsigned(signed^1));
                for(int key=0;key<6;key++)assertEquals(1,sets.polarity(sets.unsigned(signed),key));
                long token=arena.retain(signed>>>1);arena.collect();assertEquals(expected,sets.unsigned(signed));
                assertTrue(arena.size()<=3*6,"only signed tree and unsigned skeleton survive");arena.release(token);
            }
            arena.collect();assertEquals(0,arena.size());
        }
        assertEquals(0,memory.heapUsed());
    }
    @Test void aGrowingMixedSetStoresUnsignedSupportWithBoundedCopiedPathWork() {
        var memory=resources();int count=1024;
        try(var pages=new ResidentPageStore(128,memory,AnalysisResources.Phase.CONTROL);
            var arena=new CanonicalTupleArena(pages,memory,AnalysisResources.Phase.CONTROL,63,new int[]{2,4,5,62});
            var sets=new SignedLiteralSet(arena,memory)) {
            long root=0,positive=0,token=0;
            for(int key=0;key<count;key++) {
                root=sets.put(root,key,(key&1)!=0);positive=sets.put(positive,key,false);
                assertEquals(positive,sets.unsigned(root));
                long next=arena.retain(root>>>1);if(token!=0)arena.release(token);token=next;
                if((key&127)==0)arena.collect();
            }
            assertTrue(sets.copies()<=3L*33*count,"a support skeleton must not rescan the entire prefix");
            arena.collect();assertTrue(arena.size()<=3L*count);arena.release(token);
        }
        assertEquals(0,memory.heapUsed());
    }
}
