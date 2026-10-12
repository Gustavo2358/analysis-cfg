package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.analysis.dependencies.DependencySiteFact;
import io.github.gustavo2358.analysis.dependencies.SnapshotProgram;
import io.github.gustavo2358.analysis.consumers.FactStorage;
import io.github.gustavo2358.analysis.query.ProgramPoint;
import io.github.gustavo2358.analysis.solver.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static io.github.gustavo2358.analysis.dependencies.DependencySiteFact.*;

/** Closed fact codec oracle: expected full records/typed identities are made by hand,
 * before the storage exists. Not an AIR parser, semantic solver or clock benchmark. */
final class PagedDependencyFactStorageTest {
    private static FactStorage<DependencySiteFact> storage(PageStore pages,AnalysisResources resources)throws Exception {
        try {
            var type=Class.forName("io.github.gustavo2358.analysis.dependencies.PagedDependencyFactStorage");
            @SuppressWarnings("unchecked") var result=(FactStorage<DependencySiteFact>)type.getConstructor(PageStore.class,AnalysisResources.class).newInstance(pages,resources);
            return result;
        }catch(ClassNotFoundException absent){throw new AssertionError("COMPLETE_GENERAL_TYPED_FACT_CODEC_ABSENT_RED",absent);}
        catch(InvocationTargetException failure){if(failure.getCause() instanceof RuntimeException runtime)throw runtime;if(failure.getCause() instanceof Error error)throw error;throw failure;}
    }
    // A success envelope for one explicitly detached complete fact, NOT a quota
    // increase in production: each identity component is <=10 code units (<=50
    // cells at sealed depth4), other text <=32,17 evidence/support identities,
    // two copies of
    // the17 supports, each with two premise occurrences. That closed inventory
    // fits10000 cells, hence1024+64*10000 projection + the fixed8512 store/cache
    // controls fit650000. The earlier100000 envelope was insufficient for this
    // declared projection cost; its raw failure is preserved. Cardinality does
    // not enter this resident bound. Denial still consumes all available heap.
    private static final int MAX_ONE_FACT_CELLS=10_000;
    private static AnalysisResources resources(){return new AnalysisResources(new AnalysisResources.Limits(650_000,8_000_000,0,256_000_000,4,Long.MAX_VALUE,Long.MAX_VALUE));}
    private static void released(AnalysisResources resources){for(var pool:AnalysisResources.Pool.values())assertEquals(0,resources.used(pool),pool.toString());}
    private static DependencySiteFact fact(int number) {
        var p=new PublicationId("p/\uD83D\uDE42");var u=new UnitId(p,number%2==0?"Aa":"BB");var foreign=new UnitId(new PublicationId("foreign"),u.localId());
        var entry=new EntryId(u,"entry"+number);var operation=new OperationId(u,"op"+number);var origin=new OriginId(p,"origin"+number);var premise=new PremiseId(p,"premise");
        List<Id> ids=List.of(p,u,entry,new LabelId(u,"label"),operation,
            new OperandId(new OperationOwner(operation),"same"),new OperandId(new EntryOwner(entry),"same"),
            new ObjectId(u,"same"),new StorageId(p,"same"),new ResourceId(p,"same"),new ArtifactId(p,"same"),
            new ArtifactRelationId(p,"same"),origin,new UncertaintyId(p,"same"),premise,new CompletionPortId(u,"same"),new OperationId(foreign,operation.localId()));
        var supports=new ArrayList<Support>();int ordinal=0;
        for(var id:ids)supports.add(new Support(SupportKind.values()[ordinal++%3],id,origin,List.of(premise,premise)));
        String text="raw \u0000\uFFFF\uD83D\uDE42 \uD800 end "+number;
        var raw=List.of(new RawCandidate(text,supports),new RawCandidate(text,List.of()));
        var candidates=List.of(new Candidate("Aa",text,supports),new Candidate("BB",text,List.of()));
        Control.OutcomeKey outcome=switch(number%6){case 0->null;case 1->Control.NormalOutcome.INSTANCE;case 2->new Control.ExceptionOutcome("fault\u0000");case 3->Control.OtherExceptionOutcome.INSTANCE;case 4->Control.HaltOutcome.INSTANCE;default->Control.DivergeOutcome.INSTANCE;};
        var point=new ProgramPoint(entry,number%6==0?ProgramPoint.Kind.BEFORE:ProgramPoint.Kind.OUTCOME,operation,outcome);
        Boolean model=number%3==0?null:number%3==1?Boolean.FALSE:Boolean.TRUE;
        return new DependencySiteFact(u,entry,new LabelId(u,"sequence"),operation,number,origin,new OriginId(p,"target"),
            "CICS","XCTL","cics.program","profile@1",TargetKind.COMPUTED,new ObjectId(u,"subject"),point,
            Reachability.REACHABLE,TargetStatus.RESOLVED_CANDIDATES,raw,candidates,model,true,true,true,true,
            ids,List.of(origin,origin),List.of(premise,premise),List.of(new UncertaintyId(p,"uncertainty")),
            AnalysisStatus.PARTIAL,List.of("known+unknown", "case="+number));
    }
    @Test void completeFactsAllTypedIdsUtf16DuplicatesAndOutcomesSpillAcrossFiveGeometries()throws Exception {
        var directory=Files.createTempDirectory(Path.of("target"),"typed-facts-");
        for(int count:new int[]{1,4,16,64,256}) {
            var resources=resources();
            try(var pages=new FilePageStore(directory,256,2,resources);var stored=storage(pages,resources)) {
                long fixed=resources.used(AnalysisResources.Pool.RESIDENT);
                for(int i=0;i<count;i++){stored.append(fact(i));assertEquals(fixed,resources.used(AnalysisResources.Pool.RESIDENT),"no resident per-fact directory or payload");}
                var cells=stored.getClass().getDeclaredField("cells");cells.setAccessible(true);
                assertTrue(cells.getLong(stored)<=MAX_ONE_FACT_CELLS*(long)count,"closed fixture projection cost must fit its declared envelope");
                assertEquals(count,stored.size());
                for(int i=count-1;i>=0;i--){assertEquals(fact(i),stored.get(i),"every field, duplicate ordinal, identity owner and raw UTF16 must survive");assertEquals(0,resources.used(AnalysisResources.Pool.SCRATCH));}
                assertThrows(IndexOutOfBoundsException.class,()->stored.get(count));
                if(count==256){assertTrue(resources.used(AnalysisResources.Pool.TEMPORARY)>fixed*8);assertTrue(pages.statistics().evictions()>0);}
            }
            released(resources);
        }
    }
    @Test void projectionDenialAbortsOnlyOwnedFactPagesBeforeAnyDetachedFactEscapes()throws Exception {
        var directory=Files.createTempDirectory(Path.of("target"),"fact-denial-");var resources=resources();
        try(var pages=new FilePageStore(directory,256,2,resources)) {
            long borrowed=pages.allocate();long live=pages.statistics().livePages();long before=resources.heapUsed();
            var stored=storage(pages,resources);
            try {
                stored.append(fact(2));assertEquals(1,stored.size());long occupied=resources.limits().heapBytes()-resources.heapUsed();
                try(var pressure=resources.reserve(AnalysisResources.Pool.RESIDENT,occupied,AnalysisResources.Phase.CONTROL)) {
                    assertEquals(occupied,pressure.amount());
                    var failure=assertThrows(AnalysisResources.Exhausted.class,()->stored.get(0));
                    assertEquals(AnalysisResources.Resource.HEAP,failure.resource());assertEquals(AnalysisResources.Phase.REPLAY,failure.phase());
                    assertThrows(IllegalStateException.class,stored::size);assertEquals(live,pages.statistics().livePages());
                }
                assertEquals(before,resources.heapUsed());byte[] word=new byte[8];pages.read(borrowed,0,word,0,8);assertArrayEquals(new byte[8],word);
            }finally{stored.close();pages.release(borrowed);}
        }
        released(resources);
    }
    private static final class ReadFault implements PageStore {
        final PageStore backend;final RuntimeException primary=new IllegalStateException("cold fact read failure");boolean armed;
        ReadFault(PageStore backend){this.backend=backend;}
        public int pageBytes(){return backend.pageBytes();}public long allocate(){return backend.allocate();}
        public void read(long page,int offset,byte[] target,int at,int length){if(armed)throw primary;backend.read(page,offset,target,at,length);}
        public void write(long page,int offset,byte[] source,int at,int length){backend.write(page,offset,source,at,length);}
        public void release(long page){backend.release(page);}public void readForCleanup(long page,int offset,byte[] target,int at,int length){backend.readForCleanup(page,offset,target,at,length);}
        public void releaseForCleanup(long page){backend.releaseForCleanup(page);}public void flush(){backend.flush();}public Statistics statistics(){return backend.statistics();}
        public void close(){throw new AssertionError("borrowed backend must not close");}
    }
    @Test void arbitraryReadFailureKeepsPrimaryRetiresBothTapesAndLeavesBorrowedBackendAlive()throws Exception {
        var directory=Files.createTempDirectory(Path.of("target"),"fact-fault-");var resources=resources();
        try(var pages=new FilePageStore(directory,256,2,resources)) {
            long before=resources.heapUsed(),live=pages.statistics().livePages();var fault=new ReadFault(pages);var stored=storage(fault,resources);
            try {
                stored.append(fact(1));fault.armed=true;
                assertSame(fault.primary,assertThrows(IllegalStateException.class,()->stored.get(0)));
                assertEquals(before,resources.heapUsed());assertEquals(live,pages.statistics().livePages());assertThrows(IllegalStateException.class,stored::size);
                long borrowed=pages.allocate();pages.release(borrowed);
            }finally{stored.close();}
        }
        released(resources);
    }
    @Test void sharedStorageTransactionsKeepDistinctRangesAndDiscardOnlyTheActiveTail()throws Exception {
        var directory=Files.createTempDirectory(Path.of("target"),"fact-ranges-");var resources=resources();
        try(var pages=new FilePageStore(directory,256,2,resources);var stored=storage(pages,resources)) {
            Method transaction;
            try{transaction=stored.getClass().getMethod("transaction");}
            catch(NoSuchMethodException absent){throw new AssertionError("SHARED_TYPED_FACT_TRANSACTION_PORT_ABSENT_RED",absent);}
            @SuppressWarnings("unchecked") var first=(FactStorage<DependencySiteFact>)transaction.invoke(stored);
            first.append(fact(0));assertEquals(1,first.size());
            @SuppressWarnings("unchecked") var failed=(FactStorage<DependencySiteFact>)transaction.invoke(stored);
            failed.append(fact(1));failed.append(fact(2));assertEquals(3,stored.size());failed.close();
            assertEquals(1,stored.size(),"discard cannot keep failed tail facts");assertEquals(fact(0),first.get(0));
            @SuppressWarnings("unchecked") var last=(FactStorage<DependencySiteFact>)transaction.invoke(stored);
            last.append(fact(3));assertEquals(fact(3),last.get(0));assertEquals(fact(0),first.get(0));
            assertThrows(IllegalStateException.class,()->first.append(fact(4)),"earlier committed ranges cannot grow");
            first.close();assertEquals(fact(3),last.get(0),"closing an earlier range cannot truncate later facts");
            assertThrows(IllegalStateException.class,first::size);last.close();assertEquals(1,stored.size());
        }
        released(resources);
    }
    @Test void workDenialImmediatelyRetiresOwnedPayloadWithoutWaitingForAnotherQueryOrClose()throws Exception {
        var directory=Files.createTempDirectory(Path.of("target"),"fact-work-");
        var resources=new AnalysisResources(new AnalysisResources.Limits(650_000,8_000_000,0,256_000_000,4,10_000_000,Long.MAX_VALUE));
        try(var pages=new FilePageStore(directory,256,2,resources)) {
            long before=resources.heapUsed(),live=pages.statistics().livePages();var stored=storage(pages,resources);
            try {
                stored.append(fact(0));resources.work(resources.limits().workUnits()-resources.workUsed(),AnalysisResources.Phase.CONTROL);
                var failure=assertThrows(AnalysisResources.Exhausted.class,stored::size);
                assertEquals(AnalysisResources.Resource.WORK,failure.resource());assertEquals(AnalysisResources.Phase.DEMAND,failure.phase());
                assertEquals(before,resources.heapUsed(),"FACT_WORK_DENIAL_LEFT_OWNED_PAGES_RED");
                assertEquals(live,pages.statistics().livePages());assertThrows(IllegalStateException.class,stored::size);
            }finally{stored.close();}
        }
        released(resources);
    }
    @Test void nativeProgramExpiryDetachesEveryFactPageWithoutClosingBorrowedAdmittedInput()throws Exception {
        var resources=new AnalysisResources(new AnalysisResources.Limits(64L*1024*1024,16L*1024*1024,0,256L*1024*1024,8,Long.MAX_VALUE,Long.MAX_VALUE));
        try(var input=new DataflowAirReader().readSnapshot(Path.of("src/test/resources/cp6/dynamic-x8.air.json"),resources)) {
            long before=resources.heapUsed(),live=input.borrowedPages().statistics().livePages();
            var program=new SnapshotProgram(input.checked(),input.newIdentityStorage(),input.newOrderStorage(),resources);
            var stored=storage(input.borrowedPages(),AnalysisResources.executionFor(program));
            try {
                stored.append(fact(1));program.close();
                assertEquals(before,resources.heapUsed());assertEquals(live,input.borrowedPages().statistics().livePages());
                assertThrows(IllegalStateException.class,stored::size);input.checked().snapshot().root();
            }finally{stored.close();program.close();}
        }
        released(resources);
    }
    @Test void allCompletionReachabilityTargetKindsAndOptionalPointShapesRetainExactClosedFields()throws Exception {
        var directory=Files.createTempDirectory(Path.of("target"),"fact-shapes-");var resources=resources();var f=fact(0);
        var expected=new ArrayList<DependencySiteFact>();
        for(var status:TargetStatus.values()) {
            boolean unreachable=status==TargetStatus.UNREACHABLE_IN_MODEL,incomplete=status==TargetStatus.ANALYSIS_INCOMPLETE;
            var reach=unreachable?Reachability.UNREACHABLE_IN_MODEL:incomplete?Reachability.UNKNOWN:Reachability.REACHABLE;
            var point=status.ordinal()%2==0?ProgramPoint.entry(f.entry()):ProgramPoint.after(f.entry(),f.operation());
            expected.add(new DependencySiteFact(f.caller(),f.entry(),f.sequence(),f.operation(),0,f.siteOrigin(),f.targetOrigin(),"COBOL","CALL","program","profile@1",
                TargetKind.COMPUTED,null,point,reach,status,unreachable||incomplete?List.of():f.rawCandidates(),unreachable||incomplete?List.of():f.candidates(),
                unreachable?null:Boolean.TRUE,false,false,!unreachable,true,List.of(),List.of(),List.of(),List.of(),AnalysisStatus.PARTIAL,List.of("known-shape")));
        }
        expected.add(new DependencySiteFact(f.caller(),f.entry(),f.sequence(),f.operation(),0,f.siteOrigin(),f.targetOrigin(),"COBOL","CALL","program","profile@1",
            TargetKind.LITERAL,null,null,Reachability.REACHABLE,TargetStatus.RESOLVED_CANDIDATES,f.rawCandidates(),f.candidates(),
            Boolean.FALSE,false,false,false,false,List.of(),List.of(),List.of(),List.of(),AnalysisStatus.COMPLETE,List.of()));
        try(var pages=new FilePageStore(directory,256,2,resources);var stored=storage(pages,resources)) {
            for(var fact:expected)stored.append(fact);
            for(int i=0;i<expected.size();i++)assertEquals(expected.get(i),stored.get(i));
        }
        released(resources);
    }
}
