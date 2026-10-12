package io.github.gustavo2358.analysis.solver;

import io.github.gustavo2358.analysis.adapters.FilePageStore;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class PagedBooleanDecisionsStorageTest {
    @TempDir Path directory;
    private static long literal(int variable,boolean positive){return ((long)variable<<1)|(positive?0:1);}
    @Test void learnedQueriesAndModelsAgreeWhenEveryOtherPageIsEvicted() {
        var memory=new AnalysisResources(new AnalysisResources.Limits(1000000,100000,0,64000000,2,500000000,0));
        try(var resident=new ResidentPageStore(128,memory,AnalysisResources.Phase.CONTROL);
            var spill=new FilePageStore(directory,128,1,memory,AnalysisResources.Phase.CONTROL)) {
            try(var a=new PagedBooleanDecisions(resident,memory);var b=new PagedBooleanDecisions(spill,memory)) {
                for(int key=1;key<=32;key++){assertEquals(key,a.newVariable());assertEquals(key,b.newVariable());}
                for(var decisions:new PagedBooleanDecisions[]{a,b}) {
                    decisions.addClause(literal(1,false),literal(3,false),literal(4,true));
                    decisions.addClause(literal(1,false),literal(3,false),literal(4,false));
                    for(int key=5;key<32;key++)decisions.addClause(literal(key,false),literal(key+1,true));
                }
                assertTrue(a.satisfiable());assertTrue(b.satisfiable());assertTrue(b.learnedClauses()>0);
                for(int bits=0;bits<16;bits++) {
                    long[] assumptions=new long[4];
                    for(int key=1;key<=4;key++)assumptions[key-1]=literal(key,(bits&(1<<(key-1)))!=0);
                    boolean expected=(bits&1)==0||(bits&4)==0;
                    assertEquals(expected,a.satisfiable(assumptions));assertEquals(expected,b.satisfiable(assumptions));
                    if(expected)for(int key=1;key<=32;key++)assertEquals(a.value(key),b.value(key),"model key="+key);
                }
                for(var decisions:new PagedBooleanDecisions[]{a,b}) {
                    assertFalse(decisions.satisfiable(literal(5,true),literal(32,false)));
                    assertTrue(decisions.satisfiable(literal(5,true)));assertTrue(decisions.value(32));
                    decisions.addClause(literal(32,false));assertFalse(decisions.satisfiable(literal(5,true)));
                    assertTrue(decisions.satisfiable());assertFalse(decisions.value(5));
                }
                assertTrue(spill.statistics().evictions()>100);
            }
            assertEquals(0,resident.statistics().livePages());assertEquals(0,spill.statistics().livePages());
            long page=spill.allocate();spill.release(page);
        }
        assertEquals(0,memory.heapUsed());assertEquals(0,memory.used(AnalysisResources.Pool.TEMPORARY));
        assertEquals(0,memory.used(AnalysisResources.Pool.OPEN_FILES));
    }
    @Test void variableCardinalityIncreasesDiskPayloadWithConstantResidentCapacity() {
        long previousTemporary=0,residentCapacity=-1;
        for(int count:new int[]{16,64,256}) {
            var memory=new AnalysisResources(new AnalysisResources.Limits(30000,0,0,64000000,1,500000000,0));
            try(var spill=new FilePageStore(directory,128,1,memory,AnalysisResources.Phase.CONTROL)) {
                try(var decisions=new PagedBooleanDecisions(spill,memory)) {
                    for(int key=1;key<=count;key++)decisions.newVariable();
                    for(int key=1;key<count;key++)decisions.addClause(literal(key,false),literal(key+1,true));
                    assertTrue(decisions.satisfiable(literal(1,true)));assertTrue(decisions.value(count));
                    if(residentCapacity<0)residentCapacity=memory.heapPeak();
                    else assertEquals(residentCapacity,memory.heapPeak(),"all count-dependent tables must spill");
                    long temporary=memory.used(AnalysisResources.Pool.TEMPORARY);
                    assertTrue(temporary>previousTemporary);previousTemporary=temporary;
                }
                assertEquals(0,spill.statistics().livePages());
            }
            assertEquals(0,memory.heapUsed());assertEquals(0,memory.used(AnalysisResources.Pool.TEMPORARY));
            assertEquals(0,memory.used(AnalysisResources.Pool.OPEN_FILES));
        }
    }
    @Test void diskDenialIsOperationalFailureAndNeverASemanticUnsatResult() {
        for(long quota:new long[]{32,192,1024,4096,16384}) {
            var memory=new AnalysisResources(new AnalysisResources.Limits(30000,0,0,quota,1,500000000,0));
            try(var spill=new FilePageStore(directory,128,1,memory,AnalysisResources.Phase.CONTROL)) {
                try(var decisions=new PagedBooleanDecisions(spill,memory)) {
                    var failure=assertThrows(AnalysisResources.Exhausted.class,()->{
                        for(int key=1;key<=512;key++)decisions.newVariable();
                        decisions.satisfiable();
                    });
                    assertEquals(AnalysisResources.Resource.TEMPORARY,failure.resource());
                    assertThrows(IllegalStateException.class,()->decisions.satisfiable());
                    assertThrows(IllegalStateException.class,()->decisions.addClause());
                }
                assertEquals(0,spill.statistics().livePages());
            }
            assertEquals(0,memory.heapUsed());assertEquals(0,memory.used(AnalysisResources.Pool.TEMPORARY));
            assertEquals(0,memory.used(AnalysisResources.Pool.OPEN_FILES));
        }
    }
}
