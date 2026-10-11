package io.github.gustavo2358.analysis.dependencies;

import io.github.gustavo2358.analysis.adapters.FilePageStore;
import io.github.gustavo2358.analysis.adapters.MemoryPageStore;
import io.github.gustavo2358.analysis.solver.*;
import java.lang.reflect.*;
import java.nio.file.*;

/** Immutable whole-word reuse, not hash authority, depth cap or source admission. */
final class SourceRecordCellMemoTest {
    @org.junit.jupiter.api.io.TempDir Path directory;
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static int fingerprint(long tag,long[] fields) {
        long hash=0x9e3779b97f4a7c15L;
        for(int i=0;i<=fields.length;i++){long value=i==0?tag:fields[i-1];value^=value>>>33;value*=0xff51afd7ed558ccdL;value^=value>>>33;hash=Long.rotateLeft(hash,11)^value;}
        return (int)(hash^(hash>>>32));
    }
    private static int compare(Method method,SourceRecordStore rows,long a,long b)throws Exception {
        try{return (int)method.invoke(rows,a,b);}
        catch(InvocationTargetException failure){if(failure.getCause() instanceof Error error)throw error;if(failure.getCause() instanceof Exception cause)throw cause;throw failure;}
    }
    @org.junit.jupiter.api.Test
    void fixedImmutableCellsPreserveFullCollisionAuthorityAndWorkDenial()throws Exception {
        var resources=new AnalysisResources(new AnalysisResources.Limits(1048576,65536,0,128L*1024*1024,2,Long.MAX_VALUE,0));
        try {
            try(var backing=new FilePageStore(directory,4096,4,resources,AnalysisResources.Phase.INDEX)) {
                var pages=new ObservedPages(backing);
                try(var rows=new SourceRecordStore(pages,resources)) {
                    var fields=new long[30];for(int i=0;i<fields.length;i++)fields[i]=(i%3==0)?0:-37L*(i+1);
                    long a=rows.intern(11,fields);require(rows.field(a,0)==11,"exact tag");
                    for(int i=0;i<30;i++)require(rows.field(a,i+1)==fields[i],"whole independent recipe "+i);
                    long resident=resources.heapUsed();pages.armed=true;
                    for(int query=0;query<16;query++){require(rows.field(a,0)==11,"cached complete tag");for(int i=0;i<30;i++)require(rows.field(a,i+1)==fields[i],"cached complete scalar word "+i);}
                    pages.armed=false;require(resources.heapUsed()==resident,"lookup cannot grow controls");
                    var first=new long[30];var second=new long[30];for(int i=0;i<30;i++)first[i]=second[i]=(i+1)*37L;first[29]=39176;second[29]=70428;
                    require(fingerprint(11,first)==-2008668112&&fingerprint(11,second)==-2008668112,"real independent fingerprint collision");
                    long c=rows.intern(11,first),d=rows.intern(11,second);require(c!=d,"full last field distinguishes equal hash");
                    for(int i=0;i<30;i++)require(rows.field(c,i+1)==first[i]&&rows.field(d,i+1)==second[i],"full collision word "+i);
                    var method=SourceRecordStore.class.getDeclaredMethod("compare",long.class,long.class);method.setAccessible(true);
                    long work=resources.workUsed();require(compare(method,rows,c,d)<0,"complete collision ordering");
                    require(resources.workUsed()-work>=62,"equal prefix still inspects tag and all30fields of BOTH immutable rows; cached payload is allowed, hash-only authority is not");
                    require(rows.intern(11,first)==c&&rows.intern(11,second)==d,"canonical collision identity");
                    for(int n=0;n<256;n++){var cold=new long[30];cold[0]=1000L+n;cold[29]=-n;long row=rows.intern(19,cold);for(int i=0;i<30;i++)require(rows.field(row,i+1)==cold[i],"full cold oracle row="+n+" field="+i);}
                    require(resources.heapUsed()==resident,"fixed16row selected-cell memo across evictions/cold data");
                    require(rows.intern(11,first)==c&&rows.intern(11,second)==d,"no key lost on eviction");
                    for(int i=0;i<30;i++)require(rows.field(a,i+1)==fields[i],"old full recipe after cold replacement "+i);
                    rows.seal();require(rows.field(c,30)==39176&&rows.field(d,30)==70428,"seal retains complete last fields");
                }
                require(backing.statistics().livePages()==0,"owned pages retired; borrowed backend remains");
            }
        }finally{for(var pool:AnalysisResources.Pool.values())require(resources.used(pool)==0,"root pool leak "+pool);}
        var pressure=new AnalysisResources(new AnalysisResources.Limits(65536,65536,0,64L*1024*1024,4,100000,0));
        try {
            try(var pages=new MemoryPageStore(128,pressure);var rows=new SourceRecordStore(pages,pressure)) {
                var fields=new long[30];fields[0]=-17;long row=rows.intern(11,fields);require(rows.field(row,1)==-17,"warmed legitimate field");
                long remaining=pressure.limits().workUnits()-pressure.workUsed();require(remaining>=0,"declared setup envelope");pressure.work(remaining,AnalysisResources.Phase.INDEX);
                try{rows.field(row,1);throw new AssertionError("hot selected cell must retain work safepoint");}
                catch(AnalysisResources.Exhausted failure){require(failure.resource()==AnalysisResources.Resource.WORK&&failure.phase()==AnalysisResources.Phase.INDEX,"typed hot metadata WORK phase");}
                try{rows.field(row,1);throw new AssertionError("aborted owner must reject cached fields");}
                catch(IllegalStateException expected){require(expected.getMessage()!=null,"fail closed");}
            }
        }finally{for(var pool:AnalysisResources.Pool.values())require(pressure.used(pool)==0,"work-denial pool leak "+pool);}
        System.out.println("SOURCE_RECORD_CELL_MEMO_LAW_PASS hotPageReads=0 wholeCollisionWordAuthorityColdEvictionsAndTypedWorkDenial=true");
    }
    private static final class ObservedPages implements PageStore {
        private final PageStore delegate;private boolean armed;
        ObservedPages(PageStore delegate){this.delegate=delegate;}
        @Override public int pageBytes(){return delegate.pageBytes();}
        @Override public long allocate(){if(armed)throw new AssertionError("immutable field lookup must not allocate");return delegate.allocate();}
        @Override public void read(long page,int offset,byte[] target,int at,int count){if(armed)throw new AssertionError("SOURCE_RECORD_CELL_MEMO_MECHANISM_RED: repeated checked immutable field rereads recipe page");delegate.read(page,offset,target,at,count);}
        @Override public void write(long page,int offset,byte[] source,int at,int count){if(armed)throw new AssertionError("immutable field lookup must not write");delegate.write(page,offset,source,at,count);}
        @Override public void release(long page){delegate.release(page);}
        @Override public void readForCleanup(long page,int offset,byte[] target,int at,int count){delegate.readForCleanup(page,offset,target,at,count);}
        @Override public void releaseForCleanup(long page){delegate.releaseForCleanup(page);}
        @Override public void flush(){delegate.flush();}
        @Override public Statistics statistics(){return delegate.statistics();}
        @Override public void close(){throw new AssertionError("records must not close borrowed backend");}
    }
}
