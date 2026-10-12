package io.github.gustavo2358.analysis.solver;

import io.github.gustavo2358.analysis.adapters.FilePageStore;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class BooleanFunctionIndexStorageTest {
    @TempDir Path directory;
    private static long[] samples(int value) {long[] result=new long[PagedBooleanCircuit.SAMPLE_WORDS];for(int word=0;word<result.length;word++)result[word]=value;return result;}
    @Test void forcedCollisionLinksAliasesBindingAndReusedIdsSurviveOnePageEviction() {
        var memory=new AnalysisResources(new AnalysisResources.Limits(2000000,100000,0,64000000,2,500000000,0));
        var meanings=new int[256];
        try(var resident=new ResidentPageStore(128,memory,AnalysisResources.Phase.CONTROL);
            var spill=new FilePageStore(directory,128,1,memory,AnalysisResources.Phase.CONTROL)) {
            try(var a=new BooleanFunctionIndex(resident,memory,0,(x,y)->meanings[x]==meanings[y]);
                var b=new BooleanFunctionIndex(spill,memory,0,(x,y)->meanings[x]==meanings[y])) {
                for(int round=0;round<3;round++) {
                    for(int id=2;id<34;id++) {
                        meanings[id]=round*100+id;
                        for(var index:new BooleanFunctionIndex[]{a,b}) {
                            index.prepare(id,samples(meanings[id]));assertEquals(-1,index.candidate(id,true));
                            if((id&1)==0)index.insertNative(id,id,0,1,id*7L,2,id*11L);
                            else index.insertMixed(id);
                        }
                    }
                    for(int id=2;id<34;id++) {
                        meanings[100]=meanings[id];
                        for(var index:new BooleanFunctionIndex[]{a,b}) {
                            index.prepare(100,samples(meanings[100]));assertEquals(id,index.candidate(100,false));index.remove(100);
                            if((id&1)!=0)index.bindNative(id,id,0,1,id*7L,2,id*11L);
                            assertEquals(id,index.nativeClass(id,0,1,id*7L,2));
                            for(int word=0;word<PagedBooleanCircuit.SAMPLE_WORDS;word++)assertEquals(meanings[id],index.sample(id,word));
                        }
                    }
                    for(int id=2;id<34;id++){assertEquals(id*11L,a.remove(id));assertEquals(id*11L,b.remove(id));}
                    assertEquals(0,a.size());assertEquals(0,b.size());
                }
                assertTrue(spill.statistics().evictions()>100);
            }
            assertEquals(0,resident.statistics().livePages());assertEquals(0,spill.statistics().livePages());
            long page=spill.allocate();spill.release(page);
        }
        assertEquals(0,memory.heapUsed());assertEquals(0,memory.used(AnalysisResources.Pool.TEMPORARY));
        assertEquals(0,memory.used(AnalysisResources.Pool.OPEN_FILES));
    }
    @Test void samplesNativeAndNominationIndicesHaveConstantResidentCapacity() {
        long capacity=-1,previousTemporary=0;
        for(int count:new int[]{16,64,256}) {
            var memory=new AnalysisResources(new AnalysisResources.Limits(40000,0,0,64000000,1,500000000,0));
            try(var spill=new FilePageStore(directory,128,1,memory,AnalysisResources.Phase.CONTROL)) {
                try(var index=new BooleanFunctionIndex(spill,memory,PagedBooleanCircuit.SAMPLE_WORDS,(a,b)->a==b)) {
                    for(int id=2;id<count+2;id++) {
                        index.prepare(id,samples(id));assertEquals(-1,index.candidate(id,true));index.insertNative(id,id,0,1,id*7L,2,0);
                    }
                    for(int id=2;id<count+2;id++)assertEquals(id,index.nativeClass(id,0,1,id*7L,2));
                    if(capacity<0)capacity=memory.heapPeak();else assertEquals(capacity,memory.heapPeak());
                    long temporary=memory.used(AnalysisResources.Pool.TEMPORARY);
                    assertTrue(temporary>previousTemporary);previousTemporary=temporary;
                    for(int id=2;id<count+2;id++)index.remove(id);assertEquals(0,index.size());
                }
                assertEquals(0,spill.statistics().livePages());
            }
            assertEquals(0,memory.heapUsed());assertEquals(0,memory.used(AnalysisResources.Pool.TEMPORARY));
            assertEquals(0,memory.used(AnalysisResources.Pool.OPEN_FILES));
        }
    }
}
