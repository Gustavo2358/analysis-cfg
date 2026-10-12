package io.github.gustavo2358.analysis.solver;

import io.github.gustavo2358.analysis.adapters.FilePageStore;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class SignedLiteralSamplesStorageTest {
    @TempDir Path directory;
    @Test void bothConjunctionSamplePolaritiesFollowTheOwningArenaThroughEvictionAndCollection() {
        var memory=new AnalysisResources(new AnalysisResources.Limits(2000000,100000,0,64000000,2,500000000,0));
        try(var resident=new ResidentPageStore(128,memory,AnalysisResources.Phase.CONTROL);
            var spill=new FilePageStore(directory,128,1,memory,AnalysisResources.Phase.CONTROL);
            var aa=new CanonicalTupleArena(resident,memory,AnalysisResources.Phase.CONTROL,6+2*PagedBooleanCircuit.SAMPLE_WORDS,new int[]{2,4,5});
            var ab=new CanonicalTupleArena(spill,memory,AnalysisResources.Phase.CONTROL,6+2*PagedBooleanCircuit.SAMPLE_WORDS,new int[]{2,4,5});
            var a=new SignedLiteralSet(aa,memory);var b=new SignedLiteralSet(ab,memory)) {
            long x=0,y=0;
            for(int key=0;key<64;key++){x=a.put(x,key,(key%3)==0);y=b.put(y,key,(key%3)==0);}
            for(int word=0;word<PagedBooleanCircuit.SAMPLE_WORDS;word++) {
                assertEquals(a.sample(x,false,word),b.sample(y,false,word));
                assertEquals(a.sample(x,true,word),b.sample(y,true,word));
                assertEquals(a.sample(x^1,false,word),b.sample(y^1,false,word));
            }
            long ta=aa.retain(x>>>1),tb=ab.retain(y>>>1);aa.collect();ab.collect();
            assertTrue(ab.size()<=128);assertEquals(aa.size(),ab.size());
            assertTrue(spill.statistics().evictions()>100);
            aa.release(ta);ab.release(tb);aa.collect();ab.collect();assertEquals(0,ab.size());
        }
        assertEquals(0,memory.heapUsed());assertEquals(0,memory.used(AnalysisResources.Pool.TEMPORARY));
        assertEquals(0,memory.used(AnalysisResources.Pool.OPEN_FILES));
    }
}
