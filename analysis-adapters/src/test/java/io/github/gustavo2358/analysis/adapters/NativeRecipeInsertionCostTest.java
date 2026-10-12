package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.air.validation.SnapshotReferenceLists;
import io.github.gustavo2358.air.validation.SnapshotAnnotationTemplates;
import io.github.gustavo2358.analysis.solver.*;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Prospective isolated insertion law, not admission/CLI/time-cause qualification. */
final class NativeRecipeInsertionCostTest {
    @TempDir Path directory;
    private static final AnalysisResources.Phase PHASE=AnalysisResources.Phase.VALIDATION;
    private static AnalysisResources resources(){return new AnalysisResources(new AnalysisResources.Limits(32_000_000,32_000_000,0,256_000_000,2,2_000_000_000,1_000_000));}
    private PageStore pages(boolean file,AnalysisResources ledger){return file?new FilePageStore(directory,128,1,ledger):new MemoryPageStore(128,ledger);}
    private static SnapshotReferenceLists.Storage port(boolean annotation,PageStore pages,AnalysisResources ledger){return annotation?new PagedSnapshotAnnotationStorage(pages,ledger):new PagedSnapshotReferenceStorage(pages,ledger);}
    private static void zero(AnalysisResources ledger){assertEquals(0,ledger.heapUsed());for(var pool:AnalysisResources.Pool.values())assertEquals(0,ledger.used(pool),pool.name());}

    /** Independent primitive trace: one exact intern and paged status, not the owner algorithm. */
    private static final class Primitive implements AutoCloseable {
        private final AnalysisResources.Reservation control;
        private final CanonicalTupleArena keys,members;
        private final PagedLongArray metadata;
        Primitive(boolean annotation,PageStore pages,AnalysisResources ledger){
            control=ledger.reserve(AnalysisResources.Pool.RESIDENT,256,PHASE);
            keys=new CanonicalTupleArena(pages,ledger,PHASE,2,new int[0]);
            members=annotation?new CanonicalTupleArena(pages,ledger,PHASE,2,new int[0]):null;
            metadata=new PagedLongArray(pages,Long.MAX_VALUE,ledger,PHASE);
        }
        long find(long source,int kind){return keys.find(source,kind);}
        long begin(long source,int kind){long table=keys.intern(source,kind);long base=(table-1)*2;assertEquals(0,metadata.get(base));metadata.set(base,1);return table;}
        void finish(long table,long root){long base=(table-1)*2;assertEquals(1,metadata.get(base));metadata.set(base+1,root);metadata.set(base,2);}
        boolean first(long table,long text){long before=members.size();members.intern(table,text);return members.size()!=before;}
        @Override public void close(){keys.close();if(members!=null)members.close();metadata.close();control.close();}
    }
    private static long[] metrics(PageStore pages,AnalysisResources ledger){var s=pages.statistics();return new long[]{ledger.workUsed(),s.livePages(),s.bytesRead(),s.bytesWritten()};}
    private static long[] delta(long[] before,long[] after){for(int n=0;n<after.length;n++)after[n]-=before[n];return after;}
    private long[] measure(boolean file,int mode,int count,boolean actual){
        var ledger=resources();long[] result;
        try(var pages=pages(file,ledger)){
            if(actual)try(var memo=port(mode!=0,pages,ledger)){
                if(mode==2)for(int n=0;n<7;n++)memo.begin(1+n,0);
                var before=metrics(pages,ledger);
                for(int n=count-1;n>=0;n--){long source=Long.MAX_VALUE-n;int kind=(n&1)==0?0:Integer.MAX_VALUE;
                    if(mode==2){var member=(SnapshotAnnotationTemplates.Storage)memo;assertTrue(member.first(1+n%7,source));assertFalse(member.first(1+n%7,source));}
                    else{assertEquals(0,memo.find(source,kind));long table=memo.begin(source,kind);assertEquals(count-n,table);memo.finish(table,(n&1)==0?0:Long.MAX_VALUE);}
                }
                result=delta(before,metrics(pages,ledger));
                if(mode!=2)for(int n=count-1;n>=0;n--){long source=Long.MAX_VALUE-n;int kind=(n&1)==0?0:Integer.MAX_VALUE;long table=memo.find(source,kind);assertEquals(count-n,table);assertEquals((n&1)==0?0:Long.MAX_VALUE,memo.root(table));}
            }else try(var primitive=new Primitive(mode!=0,pages,ledger)){
                if(mode==2)for(int n=0;n<7;n++)primitive.begin(1+n,0);
                var before=metrics(pages,ledger);
                for(int n=count-1;n>=0;n--){long source=Long.MAX_VALUE-n;int kind=(n&1)==0?0:Integer.MAX_VALUE;
                    if(mode==2){assertTrue(primitive.first(1+n%7,source));assertFalse(primitive.first(1+n%7,source));}
                    else{assertEquals(0,primitive.find(source,kind));long table=primitive.begin(source,kind);assertEquals(count-n,table);primitive.finish(table,(n&1)==0?0:Long.MAX_VALUE);}
                }
                result=delta(before,metrics(pages,ledger));
            }
            assertEquals(0,pages.statistics().livePages());
        }
        zero(ledger);return result;
    }
    @Test void insertionsUseOneCanonicalInternAndMembershipUsesExactMonotonicCardinality(){
        var differences=new ArrayList<String>();
        for(int mode=0;mode<3;mode++)for(boolean file:new boolean[]{false,true})for(int count:new int[]{64,128,256,512,1024}){
            long[] actual=measure(file,mode,count,true),baseline=measure(file,mode,count,false);
            System.out.println("NATIVE_RECIPE_COST mode="+mode+" file="+file+" count="+count+" actual="+Arrays.toString(actual)+" primitive="+Arrays.toString(baseline));
            if(!Arrays.equals(baseline,actual))differences.add("mode="+mode+" file="+file+" count="+count+" actual="+Arrays.toString(actual)+" primitive="+Arrays.toString(baseline));
        }
        assertTrue(differences.isEmpty(),"NATIVE_RECIPE_DUPLICATED_LOOKUP_RED "+differences);
    }
    @Test void completeKeysZeroRootsReadonlyMissesPoisonedReuseAndBorrowedPagesStayExact(){
        for(boolean annotation:new boolean[]{false,true})for(boolean file:new boolean[]{false,true}){
            var ledger=resources();
            try(var pages=pages(file,ledger)){
                long borrowed=pages.allocate();pages.write(borrowed,0,new byte[]{41},0,1);
                try(var memo=port(annotation,pages,ledger)){
                    assertEquals(0,memo.find(Long.MAX_VALUE,0));long empty=memo.begin(Long.MAX_VALUE,0);memo.finish(empty,0);
                    long full=memo.begin(Long.MAX_VALUE,Integer.MAX_VALUE);memo.finish(full,Long.MAX_VALUE);
                    long other=memo.begin(1,0);memo.finish(other,9);
                    assertNotEquals(empty,full);assertNotEquals(full,other);
                    assertEquals(empty,memo.find(Long.MAX_VALUE,0));assertEquals(full,memo.find(Long.MAX_VALUE,Integer.MAX_VALUE));
                    assertEquals(0,memo.root(empty));assertEquals(Long.MAX_VALUE,memo.root(full));assertEquals(9,memo.root(other));
                    long live=pages.statistics().livePages(),heap=ledger.heapUsed(),temporary=ledger.used(AnalysisResources.Pool.TEMPORARY);
                    for(int n=2;n<1026;n++)assertEquals(0,memo.find(n,n));
                    assertEquals(live,pages.statistics().livePages());assertEquals(heap,ledger.heapUsed());assertEquals(temporary,ledger.used(AnalysisResources.Pool.TEMPORARY));
                    if(annotation){var member=(SnapshotAnnotationTemplates.Storage)memo;assertTrue(member.first(empty,Long.MAX_VALUE));assertFalse(member.first(empty,Long.MAX_VALUE));assertTrue(member.first(full,Long.MAX_VALUE));assertFalse(member.first(full,Long.MAX_VALUE));}
                    long beforeClaim=ledger.heapUsed();
                    try(var claim=memo.claim(17)){assertNotNull(claim);assertEquals(beforeClaim+17,ledger.heapUsed());}
                }
                assertEquals(1,pages.statistics().livePages());var bytes=new byte[1];pages.read(borrowed,0,bytes,0,1);assertEquals(41,bytes[0]);pages.release(borrowed);
                for(int fault=0;fault<7;fault++){
                    final int at=fault;
                    try(var memo=port(annotation,pages,ledger)){
                        long table=memo.begin(Long.MAX_VALUE,0);
                        Class<? extends RuntimeException> expectedFailure=at<=3?IllegalStateException.class:IllegalArgumentException.class;
                        assertThrows(expectedFailure,()->{switch(at){
                            case 0->memo.find(Long.MAX_VALUE,0);case 1->memo.begin(Long.MAX_VALUE,0);case 2->memo.root(table);
                            case 3->{memo.finish(table,0);memo.finish(table,0);}case 4->memo.finish(table,-1);
                            case 5->memo.find(0,0);default->memo.find(1,-1);
                        }});
                        assertThrows(IllegalStateException.class,()->memo.find(1,0));
                    }
                    assertEquals(0,pages.statistics().livePages());
                }
            }
            zero(ledger);
        }
    }
}
