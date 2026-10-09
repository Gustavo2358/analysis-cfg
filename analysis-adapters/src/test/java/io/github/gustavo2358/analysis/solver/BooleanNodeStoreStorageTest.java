package io.github.gustavo2358.analysis.solver;

import io.github.gustavo2358.analysis.adapters.FilePageStore;
import java.nio.file.Path;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class BooleanNodeStoreStorageTest {
    @TempDir Path directory;
    @Test void exactIdentityRetirementAndReuseAgreeAcrossResidentAndOnePageBackends() {
        var memory=new AnalysisResources(new AnalysisResources.Limits(1000000,100000,0,64000000,2,500000000,0));
        try(var resident=new ResidentPageStore(128,memory,AnalysisResources.Phase.CONTROL);
            var spill=new FilePageStore(directory,128,1,memory,AnalysisResources.Phase.CONTROL)) {
            try(var a=new BooleanNodeStore(resident,memory,1);var b=new BooleanNodeStore(spill,memory,1)) {
                var random=new Random(180134);
                for(int round=0;round<3;round++) {
                    int[] ids=new int[64];
                    for(int key=0;key<ids.length;key++) {
                        int low=random.nextInt(100),high=random.nextInt(100);long literal=random.nextInt(100)+2;
                        int id=a.create(key,low,high,literal,1,key+1,false);
                        assertEquals(id,b.create(key,low,high,literal,1,key+1,false));ids[key]=id;
                        a.mark(id,round+1);b.mark(id,round+1);
                    }
                    for(int key=ids.length-1;key>=0;key--) {
                        int id=ids[key];assertEquals(key,a.variable(id));assertEquals(key,b.variable(id));
                        assertEquals(a.low(id),b.low(id));assertEquals(a.high(id),b.high(id));
                        assertEquals(a.literals(id),b.literals(id));assertEquals(a.junction(id),b.junction(id));
                        assertEquals(id,a.find(key,a.low(id),a.high(id),a.literals(id),1));
                        assertEquals(id,b.find(key,a.low(id),a.high(id),a.literals(id),1));
                        assertEquals(round+1,b.mark(id));assertEquals(key+1,b.token(id));
                    }
                    for(int id:ids){a.retire(id);b.retire(id);}
                    a.trim();b.trim();assertEquals(2,a.size());assertEquals(2,b.size());
                    assertEquals(2,a.retainedNodes());assertEquals(2,b.retainedNodes());
                }
                assertTrue(spill.statistics().evictions()>100,"must actually evict catalog payload and index pages");
            }
            assertEquals(0,resident.statistics().livePages());assertEquals(0,spill.statistics().livePages());
            long page=spill.allocate();spill.release(page);
        }
        assertEquals(0,memory.heapUsed());assertEquals(0,memory.used(AnalysisResources.Pool.TEMPORARY));
        assertEquals(0,memory.used(AnalysisResources.Pool.OPEN_FILES));
    }
}
