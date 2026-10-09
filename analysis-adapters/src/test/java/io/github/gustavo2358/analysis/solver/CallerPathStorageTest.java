package io.github.gustavo2358.analysis.solver;

import io.github.gustavo2358.analysis.adapters.FilePageStore;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

final class CallerPathStorageTest {
    @TempDir Path directory;
    @Test void guardedProofRepairAndRetirementHaveEqualResidentAndForcedSpillSemantics() {
        var memory=new AnalysisResources(new AnalysisResources.Limits(500_000,100_000,0,64_000_000,2,100_000_000,1_000_000));
        var b=new BooleanConditions();
        try(var pages=new FilePageStore(directory,128,1,memory,AnalysisResources.Phase.CONTROL)) {
            try(var resident=new CallerPathCertificates(b,memory,null);var spilled=new CallerPathCertificates(b,memory,pages)) {
                var left=new CallerPathCertificates.Node[65];var right=new CallerPathCertificates.Node[65];
                left[0]=resident.node(true);right[0]=spilled.node(true);
                left[1]=resident.node(false);right[1]=spilled.node(false);
                var a=resident.add(left[0],left[1],1,1);var c=spilled.add(right[0],right[1],1,1);
                resident.add(left[0],left[1],2,1);spilled.add(right[0],right[1],2,1);
                for(int i=2;i<left.length;i++) {
                    left[i]=resident.node(false);right[i]=spilled.node(false);
                    resident.add(left[i-1],left[i],i+3,b.not(b.variable(i+3)));
                    spilled.add(right[i-1],right[i],i+3,b.not(b.variable(i+3)));
                }
                compare(b,resident,spilled,left,right);
                resident.update(a,b.variable(99));spilled.update(c,b.variable(99));
                compare(b,resident,spilled,left,right);
                resident.update(a,1);spilled.update(c,1);
                resident.remove(a);spilled.remove(c);compare(b,resident,spilled,left,right);
                resident.retire(left[32]);spilled.retire(right[32]);compare(b,resident,spilled,left,right);
                assertTrue(pages.statistics().evictions()>100,"the one-page backend must really spill proof payloads");
            }
            assertEquals(0,pages.statistics().livePages());
            long page=pages.allocate();pages.release(page);
        }
        assertEquals(0,memory.heapUsed());assertEquals(0,memory.used(AnalysisResources.Pool.TEMPORARY));assertEquals(0,memory.used(AnalysisResources.Pool.OPEN_FILES));
    }
    private static void compare(BooleanConditions b,CallerPathCertificates a,CallerPathCertificates c,CallerPathCertificates.Node[] left,CallerPathCertificates.Node[] right) {
        for(int i=0;i<left.length;i++) {
            assertEquals(a.rawReached(left[i]),c.rawReached(right[i]),"raw node="+i);
            for(int key:new int[]{1,2,31,63,99}) {
                int predicate=b.and(b.variable(key),b.not(b.variable(100)));
                assertEquals(a.matches(left[i],predicate),c.matches(right[i],predicate),"node="+i+" key="+key);
            }
        }
    }
}
