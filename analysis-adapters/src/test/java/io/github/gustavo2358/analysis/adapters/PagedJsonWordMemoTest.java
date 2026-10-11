package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.air.json.AirJson;
import io.github.gustavo2358.analysis.solver.*;
import java.nio.file.*;

/** Physical storage data law only. Cached primitive words cannot invent admission authority. */
final class PagedJsonWordMemoTest {
    @org.junit.jupiter.api.io.TempDir Path directory;
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    @org.junit.jupiter.api.Test
    void exactPhysicalWordsRemainCoherentAndWorkChecked() {
        var resources=new AnalysisResources(new AnalysisResources.Limits(1048576,65536,0,128L*1024*1024,2,Long.MAX_VALUE,0));
        try {
            try(var backing=new FilePageStore(directory,4096,4,resources,AnalysisResources.Phase.DECODE)) {
                var pages=new ObservedPages(backing);
                try(var storage=new PagedJsonInputStorage(pages,resources)) {
                    var nodes=AirJson.InputStorage.Column.NODES;storage.set(nodes,17,-13);require(storage.get(nodes,17)==-13,"authentic stored signed metadata word");
                    long resident=resources.heapUsed();pages.armed=true;
                    for(int i=0;i<16;i++)require(storage.get(nodes,17)==-13,"stable hot primitive word exact");
                    pages.armed=false;require(resources.heapUsed()==resident,"no query-dependent control growth");
                    require(storage.get(nodes,0)==0,"unwritten cached zero");storage.set(nodes,0,Long.MIN_VALUE);require(storage.get(nodes,0)==Long.MIN_VALUE,"cached zero cannot hide later write");
                    storage.set(nodes,17,-14);require(storage.get(nodes,17)==-14,"cache coherence after overwrite");
                    storage.set(AirJson.InputStorage.Column.CHARACTERS,17,123);storage.set(AirJson.InputStorage.Column.FRAMES,17,456);
                    require(storage.get(nodes,17)==-14,"columns do not alias cache authority");
                    require(storage.get(AirJson.InputStorage.Column.CHARACTERS,17)==123&&storage.get(AirJson.InputStorage.Column.FRAMES,17)==456,"other columns stay exact");
                    pages.armed=true;for(int i=0;i<16;i++)require(storage.get(nodes,17)==-14&&storage.get(AirJson.InputStorage.Column.CHARACTERS,17)==123,"hot metadata and full packed UTF16 words have independent exact keys");pages.armed=false;
                    storage.set(nodes,529,-15);storage.set(nodes,(1L<<40)+17,-16);
                    require(storage.get(nodes,17)==-14&&storage.get(nodes,529)==-15&&storage.get(nodes,(1L<<40)+17)==-16,"full64bit cell identity despite direct-slot collisions");
                    for(int i=0;i<8192;i++)storage.set(nodes,100000L+i,37L*i+7);
                    require(resources.heapUsed()==resident,"fixed cache/control across cold cardinality");
                    for(int i=8191;i>=0;i--)require(storage.get(nodes,100000L+i)==37L*i+7,"whole independent cold word oracle "+i);
                    require(storage.get(nodes,17)==-14&&storage.get(nodes,0)==Long.MIN_VALUE,"eviction does not erase underlying authority");
                }
                require(backing.statistics().livePages()==0,"owned metadata/cache/index pages retired");
                long borrowed=backing.allocate();backing.release(borrowed);
            }
        }finally{for(var pool:AnalysisResources.Pool.values())require(resources.used(pool)==0,"root pool leak "+pool);}
        var pressure=new AnalysisResources(new AnalysisResources.Limits(65536,65536,0,64L*1024*1024,4,1,0));
        try {
            try(var pages=new MemoryPageStore(128,pressure);var storage=new PagedJsonInputStorage(pages,pressure)) {
                require(storage.get(AirJson.InputStorage.Column.NODES,0)==0,"one primitive miss work unit");
                try{storage.get(AirJson.InputStorage.Column.NODES,0);throw new AssertionError("hot metadata must retain work safepoint");}
                catch(AnalysisResources.Exhausted failure){require(failure.resource()==AnalysisResources.Resource.WORK&&failure.phase()==AnalysisResources.Phase.DECODE,"typed hot work denial phase");}
                try{storage.get(AirJson.InputStorage.Column.NODES,0);throw new AssertionError("aborted input storage must reject cache access");}
                catch(IllegalStateException expected){require(expected.getMessage()!=null,"fail closed");}
            }
        }finally{for(var pool:AnalysisResources.Pool.values())require(pressure.used(pool)==0,"work-denial pool leak "+pool);}
        System.out.println("PAGED_JSON_WORD_CACHE_LAW_PASS hotReads=0 coherenceFull64bitColumnsColdOracleAndTypedWorkDenial=true");
    }
    private static final class ObservedPages implements PageStore {
        private final PageStore delegate;private boolean armed;
        ObservedPages(PageStore delegate){this.delegate=delegate;}
        @Override public int pageBytes(){return delegate.pageBytes();}
        @Override public long allocate(){if(armed)throw new AssertionError("metadata lookup must not allocate");return delegate.allocate();}
        @Override public void read(long page,int offset,byte[] target,int at,int count){if(armed)throw new AssertionError("PAGED_JSON_WORD_CACHE_MECHANISM_RED: repeated stable metadata word rereads page storage");delegate.read(page,offset,target,at,count);}
        @Override public void write(long page,int offset,byte[] source,int at,int count){if(armed)throw new AssertionError("metadata lookup must not write");delegate.write(page,offset,source,at,count);}
        @Override public void release(long page){delegate.release(page);}
        @Override public void readForCleanup(long page,int offset,byte[] target,int at,int count){delegate.readForCleanup(page,offset,target,at,count);}
        @Override public void releaseForCleanup(long page){delegate.releaseForCleanup(page);}
        @Override public void flush(){delegate.flush();}
        @Override public Statistics statistics(){return delegate.statistics();}
        @Override public void close(){throw new AssertionError("metadata must not close borrowed backend");}
    }
}
