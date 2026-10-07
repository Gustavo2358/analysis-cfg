package io.github.gustavo2358.analysis.solver;

import java.util.HashMap;
import java.util.Random;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BooleanFunctionIndexTest {
    private static AnalysisResources resources(){return new AnalysisResources(new AnalysisResources.Limits(16000000,100000,0,0,0,1000000000,0));}
    private static long[] samples(int truth){long[] values=new long[PagedBooleanCircuit.SAMPLE_WORDS];for(int word=0;word<values.length;word++)values[word]=truth;return values;}
    @Test void certifiedSupportsAvoidAllPrefixScansWithoutSkippingUnknownEquivalentFunctions() {
        var memory=resources();var meanings=new int[1024];
        try(var pages=new ResidentPageStore(128,memory,AnalysisResources.Phase.CONTROL);
            var index=new BooleanFunctionIndex(pages,memory,0,(a,b)->meanings[a]==meanings[b])) {
            for(int id=2;id<258;id++) {
                meanings[id]=id;index.prepare(id,samples(id),id,0);
                assertEquals(-1,index.candidate(id,false));index.insertMixed(id);
            }
            assertEquals(0,index.equivalenceCalls(),"different certified essential supports disprove equality before SAT");
            meanings[300]=91;index.prepare(300,samples(91));assertEquals(91,index.candidate(300,false));index.remove(300);
            meanings[301]=1000;index.prepare(301,samples(1000));index.insertMixed(301);
            meanings[302]=1000;index.prepare(302,samples(1000),567,0);
            assertEquals(301,index.candidate(302,false),"known support still considers every unknown nominee");
            index.certify(301,567,0);assertEquals(301,index.candidate(302,false));index.remove(302);
            for(int id=2;id<258;id++)index.remove(id);index.remove(301);assertEquals(0,index.size());
        }
        assertEquals(0,memory.heapUsed());
    }
    @Test void independentTruthTablesCertifySupportForEveryKnownUnknownRegistrationOrder() {
        var memory=resources();var meanings=new int[1024];var expected=new HashMap<Integer,Integer>();
        try(var pages=new ResidentPageStore(128,memory,AnalysisResources.Phase.CONTROL);
            var index=new BooleanFunctionIndex(pages,memory,0,(a,b)->meanings[a]==meanings[b])) {
            for(int pass=0;pass<3;pass++)for(int truth=1;truth<255;truth++) {
                int id=2+pass*254+truth-1;meanings[id]=truth;int support=0;
                for(int key=0;key<3;key++)for(int bits=0;bits<8;bits++)
                    if(((truth>>>bits)&1)!=((truth>>>(bits^(1<<key)))&1))support|=1<<key;
                index.prepare(id,samples(truth),(pass+truth)%3==0?0:support,0);
                int found=index.candidate(id,truth%5==0);
                // Complete native keys are assumed checked first for native inputs;
                // here all admitted originals are mixed so that path is also covered.
                assertEquals(expected.getOrDefault(truth,-1).intValue(),found);
                if(found<0){index.insertMixed(id);expected.put(truth,id);}else index.remove(id);
            }
            for(var id:expected.values())index.remove(id);assertEquals(0,index.size());
        }
        assertEquals(0,memory.heapUsed());
    }

    @Test void interruptedSupportCertificationAbortsAndReleasesBothNominationPartitions() {
        for(int boundary:new int[]{1,2,4,16,64,256,1024}) {
            var memory=resources();
            try(var backend=new ResidentPageStore(128,memory,AnalysisResources.Phase.CONTROL)) {
                var pages=new InterruptiblePages(backend);var index=new BooleanFunctionIndex(pages,memory,0,(a,b)->a==b);
                for(int id=2;id<66;id++){index.prepare(id,samples(id));index.insertMixed(id);}
                pages.remaining=boundary;
                assertThrows(PageStore.Failure.class,()->{for(int id=2;id<66;id++)index.certify(id,id,0);});
                assertThrows(IllegalStateException.class,()->index.candidate(2,false));pages.remaining=-1;index.close();
                assertEquals(0,backend.statistics().livePages());long page=backend.allocate();backend.release(page);
            }
            assertEquals(0,memory.heapUsed());
        }
    }

    @Test void forcedNominationCollisionsNeverMergeIndependentUnequalFunctions() {
        var memory=resources();var meanings=new int[1024];var expected=new HashMap<Integer,Integer>();var random=new Random(362719);
        try(var pages=new ResidentPageStore(128,memory,AnalysisResources.Phase.CONTROL);
            var index=new BooleanFunctionIndex(pages,memory,0,(a,b)->meanings[a]==meanings[b])) {
            for(int id=2;id<502;id++) {
                int truth=1+random.nextInt(254);meanings[id]=truth;index.prepare(id,samples(truth));
                int found=index.candidate(id,false);
                assertEquals(expected.getOrDefault(truth,-1).intValue(),found);
                if(found<0){index.insertMixed(id);expected.put(truth,id);}else index.remove(id);
            }
            assertEquals(expected.size(),index.size());
            for(var id:expected.values())index.remove(id);assertEquals(0,index.size());
        }
        assertEquals(0,memory.heapUsed());
    }
    @Test void nativeKeysAvoidScanningAllEarlierPurePrefixesAndCanBindMixedClasses() {
        var memory=resources();var meanings=new int[1024];
        try(var pages=new ResidentPageStore(128,memory,AnalysisResources.Phase.CONTROL);
            var index=new BooleanFunctionIndex(pages,memory,0,(a,b)->meanings[a]==meanings[b])) {
            for(int id=2;id<258;id++) {
                meanings[id]=id;index.prepare(id,samples(id));
                assertEquals(-1,index.candidate(id,true),"a new native key considers only mixed candidates");
                index.insertNative(id,id,0,0,id*7L,2,id*11L);
                assertEquals(id,index.nativeClass(id,0,0,id*7L,2));
                assertEquals(-1,index.nativeClass(id,0,1,id*7L,2),"the complete key must be checked");
            }
            assertEquals(0,index.equivalenceCalls(),"pure exact-key prefixes must not produce an all-prefix SAT scan");
            meanings[300]=1000;index.prepare(300,samples(1000));index.insertMixed(300);
            meanings[301]=1000;index.prepare(301,samples(1000));assertEquals(300,index.candidate(301,true));
            index.bindNative(300,91,0,0,1234,1,4321);index.remove(301);
            assertEquals(300,index.nativeClass(91,0,0,1234,1));assertEquals(1,index.nativeKind(300));
            assertEquals(4321,index.remove(300));assertEquals(-1,index.nativeClass(91,0,0,1234,1));
            for(int id=2;id<258;id++)assertEquals(id*11L,index.remove(id));
            assertEquals(0,index.size());
        }
        assertEquals(0,memory.heapUsed());
    }
    @Test void oldestFirstRetirementHasConstantLinkWorkAndReusedIdsHaveNoStaleMeaning() {
        var memory=resources();var meanings=new int[1024];int count=256;
        try(var pages=new ResidentPageStore(128,memory,AnalysisResources.Phase.CONTROL);
            var index=new BooleanFunctionIndex(pages,memory,0,(a,b)->meanings[a]==meanings[b])) {
            for(int id=2;id<count+2;id++){meanings[id]=id;index.prepare(id,samples(id));index.insertMixed(id);}
            long before=index.bucketLinksRead();for(int id=2;id<count+2;id++)index.remove(id);
            assertTrue(index.bucketLinksRead()-before<=8L*count,"retiring tails cannot rescan all earlier collision links");
            assertEquals(0,index.size());
            for(int id=2;id<count+2;id++) {
                meanings[id]=id+1000;index.prepare(id,samples(meanings[id]));assertEquals(-1,index.candidate(id,false));
                index.insertNative(id,id,0,1,0,0,0);
            }
            for(int id=2;id<count+2;id++){assertEquals(id,index.nativeClass(id,0,1,0,0));index.remove(id);}
            assertEquals(0,index.size());
        }
        assertEquals(0,memory.heapUsed());
    }
    @Test void constructorAndGrowthQuotaDenialsReleaseEveryOwnedPage() {
        for(long quota:new long[]{1000,8192,16000,24000,32000,64000}) {
            var memory=new AnalysisResources(new AnalysisResources.Limits(quota,100000,0,0,0,1000000000,0));
            try(var pages=new ResidentPageStore(128,memory,AnalysisResources.Phase.CONTROL)) {
                BooleanFunctionIndex index=null;boolean denied=false;
                try {
                    index=new BooleanFunctionIndex(pages,memory,0,(a,b)->a==b);
                    for(int id=2;id<1002;id++){index.prepare(id,samples(id));index.insertNative(id,id,0,1,id*7L,2,0);}
                }catch(AnalysisResources.Exhausted expected){denied=true;}
                if(index!=null) {
                    var owner=index;assertThrows(IllegalStateException.class,()->owner.nativeClass(1,0,1,0,0));
                    index.close();index.close();
                }
                assertTrue(denied,"quota="+quota);assertEquals(0,pages.statistics().livePages());
            }
            assertEquals(0,memory.heapUsed());
        }
    }
    @Test void interruptedPublicationBindingAndRetirementAbortWithoutDependingOnIntactLinks() {
        for(int operation=0;operation<3;operation++)for(int boundary:new int[]{1,2,4,16,64,256,1024}) {
            var memory=resources();
            try(var backend=new ResidentPageStore(128,memory,AnalysisResources.Phase.CONTROL)) {
                var pages=new InterruptiblePages(backend);var index=new BooleanFunctionIndex(pages,memory,0,(a,b)->a==b);
                if(operation>0)for(int id=2;id<66;id++) {
                    index.prepare(id,samples(id));
                    if(operation==1)index.insertMixed(id);else index.insertNative(id,id,0,1,id*7L,2,0);
                }
                pages.remaining=boundary;
                try {
                    for(int id=2;id<66;id++) {
                        if(operation==0){index.prepare(id,samples(id));index.insertNative(id,id,0,1,id*7L,2,0);}
                        else if(operation==1)index.bindNative(id,id,0,1,id*7L,2,0);
                        else index.remove(id);
                    }
                    fail("fault not reached operation="+operation+" boundary="+boundary);
                }catch(PageStore.Failure expected){assertEquals(PageStore.Reason.IO,expected.reason());}
                assertThrows(IllegalStateException.class,()->index.nativeClass(1,0,1,0,0));
                assertThrows(IllegalStateException.class,()->index.candidate(2,false));
                pages.remaining=-1;index.close();index.close();
                assertEquals(0,backend.statistics().livePages(),"operation="+operation+" boundary="+boundary);
                long page=backend.allocate();backend.release(page);
            }
            assertEquals(0,memory.heapUsed());
        }
    }
    private static final class InterruptiblePages implements PageStore {
        final PageStore delegate;int remaining=-1;
        InterruptiblePages(PageStore delegate){this.delegate=delegate;}
        void step(){if(remaining>0&&--remaining==0)throw new Failure(Reason.IO,"synthetic function-index interruption");}
        public int pageBytes(){return delegate.pageBytes();}
        public long allocate(){step();return delegate.allocate();}
        public void read(long page,int offset,byte[] target,int start,int length){step();delegate.read(page,offset,target,start,length);}
        public void readForCleanup(long page,int offset,byte[] target,int start,int length){step();delegate.readForCleanup(page,offset,target,start,length);}
        public void write(long page,int offset,byte[] source,int start,int length){step();delegate.write(page,offset,source,start,length);}
        public void release(long page){delegate.release(page);}
        public void releaseForCleanup(long page){delegate.releaseForCleanup(page);}
        public void flush(){delegate.flush();}
        public Statistics statistics(){return delegate.statistics();}
        public void close(){delegate.close();}
    }
}
