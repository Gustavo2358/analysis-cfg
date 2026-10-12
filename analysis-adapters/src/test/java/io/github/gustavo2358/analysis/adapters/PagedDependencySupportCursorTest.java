package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.analysis.dependencies.*;
import io.github.gustavo2358.analysis.solver.*;
import java.lang.reflect.*;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static io.github.gustavo2358.analysis.dependencies.DependencySiteFact.*;

/** Prospective typed cursor law. Draft outside build inputs until FAST/audit/local
 * commit closes. Borrowed input DTO is constructed before storage; this tests
 * reads of the staged closed codec, not bounded productive provider assembly. */
@SuppressWarnings("try") // Reflective prospective leases use AutoCloseable; all expiry/cleanup laws remain explicit.
final class PagedDependencySupportCursorTest {
    @TempDir Path directory;
    private static Object invoke(Object target,String name,Class<?>[] types,Object... args)throws Exception {
        try{return target.getClass().getMethod(name,types).invoke(target,args);}
        catch(InvocationTargetException failure){if(failure.getCause() instanceof RuntimeException error)throw error;if(failure.getCause() instanceof Error error)throw error;throw failure;}
    }
    private static Object get(Object target,String name)throws Exception{return invoke(target,name,new Class<?>[0]);}
    private static AutoCloseable cursor(PagedDependencyFactStorage storage,int support)throws Exception{return (AutoCloseable)invoke(storage,"openSupport",new Class<?>[]{int.class,int.class,int.class},0,0,support);}
    private static AutoCloseable identity(Object owner,String method)throws Exception{return (AutoCloseable)get(owner,method);}
    private static AutoCloseable premise(Object owner,int ordinal)throws Exception{return (AutoCloseable)invoke(owner,"premise",new Class<?>[]{int.class},ordinal);}
    private static String part(Object view,String name)throws Exception {
        var type=Class.forName(PagedDependencyFactStorage.class.getName()+"$IdentityPart");
        Object selected=Arrays.stream(type.getEnumConstants()).filter(v->v.toString().equals(name)).findFirst().orElseThrow();
        long size=((Number)invoke(view,"characterCount",new Class<?>[]{type},selected)).longValue();char[] block=new char[7];var value=new StringBuilder();
        for(long at=0;at<size;){int n=(int)Math.min(block.length,size-at);invoke(view,"readCharacters",new Class<?>[]{type,long.class,char[].class,int.class,int.class},selected,at,block,0,n);value.append(block,0,n);at+=n;}
        return value.toString();
    }
    private static String domain(Id id){return switch(id){case PublicationId ignored->"publication";case UnitId ignored->"unit";case EntryId ignored->"entry";case LabelId ignored->"label";case OperationId ignored->"operation";case OperandId ignored->"operand";case ObjectId ignored->"object";case StorageId ignored->"storage";case ResourceId ignored->"resource";case ArtifactId ignored->"artifact";case ArtifactRelationId ignored->"artifactRelation";case OriginId ignored->"origin";case UncertaintyId ignored->"uncertainty";case PremiseId ignored->"premise";case CompletionPortId ignored->"completionPort";};}
    private static String unit(Id id){return switch(id){case UnitId v->v.localId();case EntryId v->v.unit().localId();case LabelId v->v.unit().localId();case OperationId v->v.unit().localId();case ObjectId v->v.unit().localId();case CompletionPortId v->v.unit().localId();case OperandId v->v.owner().unit().localId();default->"";};}
    private static void same(Object view,Id expected)throws Exception {
        assertEquals(domain(expected),get(view,"domain"));assertEquals(expected.publication().localId(),part(view,"PUBLICATION"));assertEquals(expected.localId(),part(view,"LOCAL"));assertEquals(unit(expected),part(view,"UNIT"));
        if(expected instanceof OperandId operand){String owner=switch(operand.owner()){case OperationOwner v->v.operation().localId();case EntryOwner v->v.entry().localId();};assertEquals(owner,part(view,"OWNER"));assertEquals(operand.owner() instanceof EntryOwner?"ENTRY":"OPERATION",get(view,"ownerKind").toString());}
    }
    private static DependencySiteFact fact(int count,int width) {
        var p=new PublicationId("p\uD83D\uDE42");var u=new UnitId(p,"Aa");var entry=new EntryId(u,"entry");var operation=new OperationId(u,"producer");var origin=new OriginId(p,"origin");
        String suffix="\u03c0\u0000\uFFFF\uD83D\uDE42".repeat(width);var premises=new ArrayList<PremiseId>(count);
        for(int i=0;i<count;i++)premises.add(new PremiseId(p,(i%3==0?"Aa":i%3==1?"BB":"\u03c0")+suffix));
        var support=new Support(SupportKind.VALUE_PRODUCER,operation,origin,premises);
        return new DependencySiteFact(u,entry,new LabelId(u,"sequence"),operation,0,origin,origin,"CALL","CALL","program","call@1",TargetKind.COMPUTED,null,null,
            Reachability.REACHABLE,TargetStatus.RESOLVED_CANDIDATES,List.of(),List.of(new Candidate("PG1","PG1",List.of(support))),
            false,false,false,false,false,List.of(),List.of(),List.of(),List.of());
    }
    private static AnalysisResources resources(){return new AnalysisResources(new AnalysisResources.Limits(650_000,8_000_000,0,256_000_000,4,Long.MAX_VALUE,Long.MAX_VALUE));}
    @Test void oneWideSupportProjectsEveryRepeatedPremiseOrdinalWithConstantFundedScopesAcrossFiveGeometries()throws Exception {
        for(int count:new int[]{64,256,1024,4096,16384}) {
            var expected=fact(count,0);var resources=resources();
            try(var pages=new FilePageStore(directory,256,1,resources);var storage=new PagedDependencyFactStorage(pages,resources)) {
                storage.append(expected);long resident=resources.used(AnalysisResources.Pool.RESIDENT),baseline=resources.used(AnalysisResources.Pool.SCRATCH),reads=pages.statistics().bytesRead();
                try(var scope=cursor(storage,0)) {
                    assertEquals(SupportKind.VALUE_PRODUCER,get(scope,"kind"));assertEquals(count,((Number)get(scope,"premiseCount")).intValue());
                    try(var producer=identity(scope,"producer");var origin=identity(scope,"origin")){same(producer,expected.operation());same(origin,expected.siteOrigin());assertTrue(resources.used(AnalysisResources.Pool.SCRATCH)-baseline<=8192);}
                    long cursorScratch=resources.used(AnalysisResources.Pool.SCRATCH);
                    for(int i=count-1;i>=0;i--){try(var selected=premise(scope,i)){same(selected,expected.candidates().getFirst().supports().getFirst().premises().get(i));assertTrue(resources.used(AnalysisResources.Pool.SCRATCH)-baseline<=8192);}assertEquals(cursorScratch,resources.used(AnalysisResources.Pool.SCRATCH));}
                    assertEquals(resident,resources.used(AnalysisResources.Pool.RESIDENT));assertThrows(IndexOutOfBoundsException.class,()->premise(scope,count));assertEquals(count,((Number)get(scope,"premiseCount")).intValue());
                    System.out.println("PAGED_SUPPORT_PREMISE_CURSOR count="+count+" resident="+resident+" scratch="+cursorScratch+" readDelta="+(pages.statistics().bytesRead()-reads));
                }assertEquals(baseline,resources.used(AnalysisResources.Pool.SCRATCH));
            }for(var pool:AnalysisResources.Pool.values())assertEquals(0,resources.used(pool),pool.toString());
        }
    }
    @Test void everySealedProducerOwnerAndLargeUtf16IdentityUsesBlocksWithoutWholeIdProjection()throws Exception {
        var method=PagedDependencyFactStorageTest.class.getDeclaredMethod("fact",int.class);method.setAccessible(true);var all=(DependencySiteFact)method.invoke(null,0);var resources=resources();
        try(var pages=new FilePageStore(directory,256,1,resources);var storage=new PagedDependencyFactStorage(pages,resources)) {
            storage.append(all);
            for(int s=0;s<all.candidates().getFirst().supports().size();s++)try(var scope=cursor(storage,s);var producer=identity(scope,"producer");var origin=identity(scope,"origin")) {
                var expected=all.candidates().getFirst().supports().get(s);same(producer,expected.producer());same(origin,expected.origin());assertEquals(expected.kind(),get(scope,"kind"));assertEquals(2,((Number)get(scope,"premiseCount")).intValue());
                try(var first=premise(scope,0);var second=premise(scope,1)){same(first,expected.premises().getFirst());same(second,expected.premises().getLast());assertTrue(resources.used(AnalysisResources.Pool.SCRATCH)<=8192);}
            }
        }for(var pool:AnalysisResources.Pool.values())assertEquals(0,resources.used(pool));
        for(int width:new int[]{64,256,1024,4096,16384}) {
            var expected=fact(1,width);var pressure=resources();
            try(var pages=new FilePageStore(directory,256,1,pressure);var storage=new PagedDependencyFactStorage(pages,pressure)) {
                storage.append(expected);try(var scope=cursor(storage,0);var value=premise(scope,0)){same(value,expected.candidates().getFirst().supports().getFirst().premises().getFirst());assertTrue(pressure.used(AnalysisResources.Pool.SCRATCH)<=8192,"ID width must stay cold, not become one giant String");}
            }for(var pool:AnalysisResources.Pool.values())assertEquals(0,pressure.used(pool));
        }
    }
    @Test void parentCursorCloseExpiresIdentityViewsAndDenialPreservesPrimaryAndBorrowedBackend()throws Exception {
        var resources=resources();try(var pages=new FilePageStore(directory,256,1,resources)) {
            long borrowed=pages.allocate(),live=pages.statistics().livePages(),baseline=resources.heapUsed();
            try(var storage=new PagedDependencyFactStorage(pages,resources)) {
                storage.append(fact(256,0));try(var scope=cursor(storage,0);var view=premise(scope,7)) {
                    assertEquals("premise",get(view,"domain"));expire(scope);assertThrows(IllegalStateException.class,()->get(view,"domain"));assertEquals(0,resources.used(AnalysisResources.Pool.SCRATCH));assertEquals(1,storage.size());
                }
                try(var scope=cursor(storage,0);var view=premise(scope,8)) {
                    long remaining=resources.limits().heapBytes()-resources.heapUsed();try(var pressure=resources.reserve(AnalysisResources.Pool.RESIDENT,remaining,AnalysisResources.Phase.CONTROL)) {
                        assertEquals(remaining,pressure.amount());var failure=assertThrows(AnalysisResources.Exhausted.class,()->premise(scope,9));assertEquals(AnalysisResources.Resource.HEAP,failure.resource());assertEquals(AnalysisResources.Phase.REPLAY,failure.phase());
                        assertThrows(IllegalStateException.class,()->get(view,"domain"));assertThrows(IllegalStateException.class,()->get(scope,"premiseCount"));assertThrows(IllegalStateException.class,storage::size);assertEquals(0,resources.used(AnalysisResources.Pool.SCRATCH));assertEquals(live,pages.statistics().livePages());
                    }
                }assertEquals(baseline,resources.heapUsed());
            }byte[] bytes=new byte[8];pages.read(borrowed,0,bytes,0,8);assertArrayEquals(new byte[8],bytes);pages.release(borrowed);
        }for(var pool:AnalysisResources.Pool.values())assertEquals(0,resources.used(pool));
    }
    private static void expire(AutoCloseable scope)throws Exception{scope.close();}

    @Test void blockReadFaultExpiresAllScopesKeepsPrimaryAndLeavesBorrowedBackendAlive()throws Exception {
        var resources=resources();try(var pages=new FilePageStore(directory,256,1,resources)) {
            long borrowed=pages.allocate(),live=pages.statistics().livePages(),baseline=resources.heapUsed();
            var fault=new SourceCorrelationSnapshotTest.FaultPages(pages);
            try(var storage=new PagedDependencyFactStorage(fault,resources)) {
                storage.append(fact(1,1024));try(var scope=cursor(storage,0);var view=premise(scope,0)) {
                    assertEquals("premise",get(view,"domain"));fault.arm(1);
                    assertSame(fault.primary,assertThrows(PageStore.Failure.class,()->part(view,"LOCAL")));
                    assertThrows(IllegalStateException.class,()->get(view,"domain"));assertThrows(IllegalStateException.class,()->get(scope,"kind"));assertThrows(IllegalStateException.class,storage::size);
                    assertFalse(fault.closed);assertEquals(live,pages.statistics().livePages());assertEquals(baseline,resources.heapUsed());assertEquals(0,resources.used(AnalysisResources.Pool.SCRATCH));
                }
            }byte[] bytes=new byte[8];pages.read(borrowed,0,bytes,0,8);assertArrayEquals(new byte[8],bytes);pages.release(borrowed);
        }for(var pool:AnalysisResources.Pool.values())assertEquals(0,resources.used(pool));
    }

    @Test void nativeProgramExpiryRetiresCursorAndIdentityFundingWithoutClosingBorrowedCheckedAirOrBackend()throws Exception {
        var fixture=new SourceCorrelationSnapshotTest();fixture.directory=directory;
        var publication=SourceCorrelationSnapshotTest.program(1);var evidence=fixture.source(publication);
        try(var input=fixture.new NativeCase(publication,evidence)) {
            long borrowed=input.pages.allocate();
            try(var storage=new PagedDependencyFactStorage(input.pages,input.execution)) {
                storage.append(fact(256,0));try(var scope=cursor(storage,0);var view=premise(scope,9)) {
                    assertEquals("premise",get(view,"domain"));expire(input.program);
                    assertThrows(IllegalStateException.class,()->get(view,"domain"));assertThrows(IllegalStateException.class,()->get(scope,"kind"));assertThrows(IllegalStateException.class,storage::size);
                    for(var pool:AnalysisResources.Pool.values())assertEquals(0,input.execution.used(pool));
                    assertEquals(1,input.pages.statistics().livePages());assertTrue(input.checked.result().isStructurallyValid());
                }
            }byte[] bytes=new byte[8];input.pages.read(borrowed,0,bytes,0,8);assertArrayEquals(new byte[8],bytes);input.pages.release(borrowed);
        }
    }
}
