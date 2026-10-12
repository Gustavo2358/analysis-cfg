package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.air.json.AirJson;
import io.github.gustavo2358.analysis.dependencies.source.*;
import io.github.gustavo2358.analysis.solver.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.lang.reflect.*;
import java.nio.file.*;

/** A fixed hot frame prefix is a storage layout, never a maximum nesting capability.
 * Observe an authentic closed ROOT push and preserve the paged tail address space.
 */
final class SourceBindingFrameHeadTest {
    @org.junit.jupiter.api.io.TempDir Path directory;
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static Object invoke(Method method,Object owner,Object... args)throws Exception {
        try{return method.invoke(owner,args);}
        catch(InvocationTargetException failure){if(failure.getCause() instanceof Error error)throw error;if(failure.getCause() instanceof Exception cause)throw cause;throw failure;}
    }
    @org.junit.jupiter.api.Test
    void fixedFramePrefixKeepsAuthenticRootAndOriginalPagedTailExact()throws Exception {
        var codec=new QualifiedSourceJson();byte[] wire=Files.readAllBytes(Path.of("src/test/resources/qualified-source-r9/ordinary.source.json"));
        var expected=new ObjectMapper().readTree(codec.encode(codec.decode(wire)));
        var resources=new AnalysisResources(new AnalysisResources.Limits(1048576,16384,0,128L*1024*1024,2,Long.MAX_VALUE,16L*1024*1024));
        try {
            try(var backing=new FilePageStore(directory,4096,4,resources,AnalysisResources.Phase.INDEX)) {
                var pages=new ObservedFramePages(backing);
                var constructor=SourceSnapshotJson.class.getDeclaredConstructor(PageStore.class,AnalysisResources.class);constructor.setAccessible(true);
                try(var mapping=constructor.newInstance(pages,resources);
                    var physical=new AirJson().stageInput(new ByteArrayInputStream(wire),new PagedJsonInputStorage(pages,resources),AirJson.PhysicalProfile.UTF16_JSON)) {
                    var push=SourceSnapshotJson.class.getDeclaredMethod("push",AirJson.StagedInput.class,long.class,SourceShape.class,SourceShape.class,int.class);push.setAccessible(true);
                    pages.armed=true;invoke(push,mapping,physical,physical.root(),SourceShape.ROOT,null,0);pages.armed=false;
                    var get=SourceSnapshotJson.class.getDeclaredMethod("frameGet",long.class);get.setAccessible(true);
                    var set=SourceSnapshotJson.class.getDeclaredMethod("frameSet",long.class,long.class);set.setAccessible(true);
                    require((long)invoke(get,mapping,0L)==physical.root(),"authentic root token stored in fixed head");
                    require((long)invoke(get,mapping,1L)==SourceShape.ROOT.tag(),"authentic root shape");
                    long resident=resources.heapUsed();
                    for(long cell:new long[]{39,40,479,480,511,512,513,1023,1024,81919,81920,1048575}) {
                        long value=Long.MIN_VALUE+cell;invoke(set,mapping,cell,value);require((long)invoke(get,mapping,cell)==value,"head/tail exact signed cell "+cell);
                        invoke(set,mapping,cell,0L);require((long)invoke(get,mapping,cell)==0,"reused frame clears exactly "+cell);
                    }
                    require(resources.heapUsed()==resident,"fixed control does not grow with depth/address");
                }
                require(backing.statistics().livePages()==0,"physical and mapping prefix/tail owners retired");
                try(var source=codec.decodeSnapshot(new ByteArrayInputStream(wire),pages,resources);
                    var admission=io.github.gustavo2358.analysis.dependencies.SourceAdmissionSnapshot.admit(source,pages,resources)) {
                    var output=new ByteArrayOutputStream();codec.writeSnapshot(admission,output,pages,resources);
                    require(expected.equals(new ObjectMapper().readTree(output.toByteArray())),"whole canonical SOURCE independent legacy oracle");
                }
                require(backing.statistics().livePages()==0,"whole productive composition retires owned pages");
                System.out.println("SOURCE_BINDING_FRAME_HEAD_LAW_PASS authenticRootFrameWritePageCalls=0 pagedTailAddressPreserved=true wholeCanonicalOracle=true");
            }
        }finally{for(var pool:AnalysisResources.Pool.values())require(resources.used(pool)==0,"root pool leak "+pool);}
    }
    private static final class ObservedFramePages implements PageStore {
        private final PageStore delegate;private boolean armed;
        ObservedFramePages(PageStore delegate){this.delegate=delegate;}
        private void check(){if(armed&&StackWalker.getInstance().walk(frames->frames.anyMatch(frame->frame.getClassName().equals("io.github.gustavo2358.analysis.solver.PagedLongArray")&&frame.getMethodName().equals("set"))))throw new AssertionError("SOURCE_BINDING_FRAME_HEAD_MECHANISM_RED: authentic shallow ROOT frame write accesses page storage");}
        @Override public int pageBytes(){return delegate.pageBytes();}
        @Override public long allocate(){check();return delegate.allocate();}
        @Override public void read(long page,int offset,byte[] target,int at,int count){check();delegate.read(page,offset,target,at,count);}
        @Override public void write(long page,int offset,byte[] source,int at,int count){check();delegate.write(page,offset,source,at,count);}
        @Override public void release(long page){delegate.release(page);}
        @Override public void readForCleanup(long page,int offset,byte[] target,int at,int count){delegate.readForCleanup(page,offset,target,at,count);}
        @Override public void releaseForCleanup(long page){delegate.releaseForCleanup(page);}
        @Override public void flush(){delegate.flush();}
        @Override public Statistics statistics(){return delegate.statistics();}
        @Override public void close(){throw new AssertionError("mapper must not close borrowed backend");}
    }
}
