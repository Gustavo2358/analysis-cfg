package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.air.model.Control;
import io.github.gustavo2358.analysis.dependencies.*;
import io.github.gustavo2358.analysis.solver.*;
import java.lang.reflect.*;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static io.github.gustavo2358.analysis.dependencies.DependencySiteFact.*;

/** Scoped complete fact fields and operational ownership laws. Fixtures/expected
 * DTOs precede storage;not bounded provider or integrated product proof. */
@SuppressWarnings("try")
final class PagedDependencyFactCursorTest {
    @TempDir Path directory;
    private static Object invoke(Object target,String name,Class<?>[] types,Object... args)throws Exception {
        try{return target.getClass().getMethod(name,types).invoke(target,args);}
        catch(InvocationTargetException failure){if(failure.getCause() instanceof RuntimeException error)throw error;if(failure.getCause() instanceof Error error)throw error;throw failure;}
    }
    private static Object get(Object target,String name)throws Exception{return invoke(target,name,new Class<?>[0]);}
    private static AutoCloseable fact(PagedDependencyFactStorage storage)throws Exception{return (AutoCloseable)invoke(storage,"openFact",new Class<?>[]{int.class},0);}
    private static AutoCloseable candidate(Object scope,int ordinal)throws Exception{return (AutoCloseable)invoke(scope,"openCandidate",new Class<?>[]{int.class},ordinal);}
    private static String text(Object scope,boolean raw)throws Exception {
        String length=raw?"rawLength":"referenceLength",read=raw?"readRaw":"readReference";long size=((Number)get(scope,length)).longValue();char[] block=new char[7];var value=new StringBuilder();
        for(long at=0;at<size;){int count=(int)Math.min(block.length,size-at);invoke(scope,read,new Class<?>[]{long.class,char[].class,int.class,int.class},at,block,0,count);value.append(block,0,count);at+=count;}return value.toString();
    }
    private static DependencySiteFact fixture(int count,int width) {
        var p=new PublicationId("p\uD83D\uDE42");var u=new UnitId(p,"Aa");var entry=new EntryId(u,"entry");var operation=new OperationId(u,"producer");var origin=new OriginId(p,"origin");
        String suffix="\u03c0\u0000\uFFFF\uD83D\uDE42".repeat(width);var values=new ArrayList<Candidate>(count);
        for(int i=0;i<count;i++)values.add(new Candidate("PG"+i, (i%3==0?"Aa":i%3==1?"BB":"\u03c0")+suffix+"@"+i,
            List.of(new Support(SupportKind.VALUE_PRODUCER,operation,origin,List.of(new PremiseId(p,"Aa"),new PremiseId(p,"BB"),new PremiseId(p,"Aa"))))));
        return new DependencySiteFact(u,entry,new LabelId(u,"sequence"),operation,0,origin,origin,"CALL","CALL","program","call@1",TargetKind.COMPUTED,null,null,
            Reachability.REACHABLE,TargetStatus.RESOLVED_CANDIDATES,List.of(),values,false,false,false,false,false,List.of(),List.of(),List.of(),List.of());
    }
    private static AnalysisResources resources(){return new AnalysisResources(new AnalysisResources.Limits(650_000,8_000_000,0,256_000_000,4,Long.MAX_VALUE,Long.MAX_VALUE));}
    @Test void oneFactReadsEveryCandidateAndSupportOrdinalAcrossFiveCountsWithConstantFundedScopes()throws Exception {
        for(int count:new int[]{64,256,1024,4096,16384}) {
            var expected=fixture(count,0);var resources=resources();
            try(var pages=new FilePageStore(directory,256,1,resources);var storage=new PagedDependencyFactStorage(pages,resources)) {
                storage.append(expected);long resident=resources.used(AnalysisResources.Pool.RESIDENT),baseline=resources.used(AnalysisResources.Pool.SCRATCH);
                try(var scope=fact(storage)) {
                    assertEquals(count,((Number)get(scope,"candidateCount")).intValue());assertEquals(expected.targetKind(),get(scope,"targetKind"));assertEquals(expected.reachability(),get(scope,"reachability"));assertEquals(expected.targetStatus(),get(scope,"targetStatus"));assertEquals(expected.analysisStatus(),get(scope,"analysisStatus"));
                    long parent=resources.used(AnalysisResources.Pool.SCRATCH);
                    for(int i=count-1;i>=0;i--)try(var row=candidate(scope,i)) {
                        var want=expected.candidates().get(i);assertEquals(want.referenceName(),text(row,false));assertEquals(want.rawValue(),text(row,true));assertEquals(1,((Number)get(row,"supportCount")).intValue());
                        try(var support=(AutoCloseable)invoke(row,"openSupport",new Class<?>[]{int.class},0)){assertEquals(want.supports().getFirst().kind(),get(support,"kind"));assertEquals(3,((Number)get(support,"premiseCount")).intValue());assertTrue(resources.used(AnalysisResources.Pool.SCRATCH)-baseline<=8192);}
                    }
                    assertEquals(parent,resources.used(AnalysisResources.Pool.SCRATCH));assertEquals(resident,resources.used(AnalysisResources.Pool.RESIDENT));
                    assertThrows(IndexOutOfBoundsException.class,()->candidate(scope,count));assertEquals(count,((Number)get(scope,"candidateCount")).intValue());
                    System.out.println("PAGED_FACT_CANDIDATE_CURSOR count="+count+" resident="+resident+" scratch="+parent);
                }assertEquals(baseline,resources.used(AnalysisResources.Pool.SCRATCH));
            }for(var pool:AnalysisResources.Pool.values())assertEquals(0,resources.used(pool));
        }
    }
    @Test void oneCandidateKeepsItsLargeRawUtf16PayloadColdAcrossFiveWidthsWithoutWholeFactProjection()throws Exception {
        for(int width:new int[]{64,256,1024,4096,16384}) {
            var expected=fixture(1,width);var resources=resources();
            try(var pages=new FilePageStore(directory,256,1,resources);var storage=new PagedDependencyFactStorage(pages,resources)) {
                storage.append(expected);long resident=resources.used(AnalysisResources.Pool.RESIDENT);
                try(var scope=fact(storage);var row=candidate(scope,0)){assertEquals(expected.candidates().getFirst().rawValue(),text(row,true));assertEquals(expected.candidates().getFirst().referenceName(),text(row,false));assertTrue(resources.used(AnalysisResources.Pool.SCRATCH)<=8192);assertEquals(resident,resources.used(AnalysisResources.Pool.RESIDENT));}
            }for(var pool:AnalysisResources.Pool.values())assertEquals(0,resources.used(pool));
        }
    }
    @Test void factCloseExpiresCandidateAndSupportChildrenButPreservesStorageAndBorrowedBackend()throws Exception {
        var resources=resources();try(var pages=new FilePageStore(directory,256,1,resources)) {
            long borrowed=pages.allocate();try(var storage=new PagedDependencyFactStorage(pages,resources)) {
                storage.append(fixture(256,0));try(var scope=fact(storage);var row=candidate(scope,7);var support=(AutoCloseable)invoke(row,"openSupport",new Class<?>[]{int.class},0)) {
                    assertEquals(1,((Number)get(row,"supportCount")).intValue());expire(scope);assertThrows(IllegalStateException.class,()->get(row,"rawLength"));assertThrows(IllegalStateException.class,()->get(support,"kind"));assertEquals(0,resources.used(AnalysisResources.Pool.SCRATCH));assertEquals(1,storage.size());
                }
            }byte[] value=new byte[8];pages.read(borrowed,0,value,0,8);assertArrayEquals(new byte[8],value);pages.release(borrowed);
        }for(var pool:AnalysisResources.Pool.values())assertEquals(0,resources.used(pool));
    }
    @Test void discardedTransactionTailExpiresEveryScopedReadButKeepsCommittedPrefixAndNeverRevivesOldHandles()throws Exception {
        var resources=resources();try(var pages=new FilePageStore(directory,256,1,resources);var storage=new PagedDependencyFactStorage(pages,resources)) {
            var first=storage.transaction();first.append(fixture(1,0));var failed=storage.transaction();failed.append(fixture(16,0));
            try(var prefix=(AutoCloseable)invoke(storage,"openFact",new Class<?>[]{int.class},0)) {
                long baseline=resources.used(AnalysisResources.Pool.SCRATCH);
                try(var tail=(AutoCloseable)invoke(storage,"openFact",new Class<?>[]{int.class},1);var row=candidate(tail,7);var child=indexedChild(row,"openSupport",0);
                    var complete=storage.project(1);var leaf=storage.projectSupport(1,7,0);var direct=storage.openSupport(1,7,0);var producer=direct.producer()) {
                    failed.close();
                    assertAll("DISCARDED_TAIL_LEFT_SCOPED_READS_LIVE_RED",
                        ()->assertThrows(IllegalStateException.class,()->get(tail,"candidateCount"),"TAIL_FACT_CURSOR_LIVE"),
                        ()->assertThrows(IllegalStateException.class,()->get(row,"rawLength"),"TAIL_CANDIDATE_CURSOR_LIVE"),
                        ()->assertThrows(IllegalStateException.class,()->get(child,"kind"),"TAIL_CHILD_SUPPORT_LIVE"),
                        ()->assertThrows(IllegalStateException.class,complete::fact,"TAIL_COMPLETE_PROJECTION_LIVE"),
                        ()->assertThrows(IllegalStateException.class,leaf::support,"TAIL_SUPPORT_PROJECTION_LIVE"),
                        ()->assertThrows(IllegalStateException.class,direct::kind,"TAIL_DIRECT_SUPPORT_LIVE"),
                        ()->assertThrows(IllegalStateException.class,producer::domain,"TAIL_DIRECT_IDENTITY_LIVE"));
                    assertEquals(1,storage.size());assertEquals(1,((Number)get(prefix,"candidateCount")).intValue());assertEquals(baseline,resources.used(AnalysisResources.Pool.SCRATCH));
                    var replacement=storage.transaction();replacement.append(fixture(2,0));assertEquals(2,storage.size());assertThrows(IllegalStateException.class,()->get(tail,"candidateCount"));assertThrows(IllegalStateException.class,direct::kind);replacement.close();first.close();
                    assertEquals(1,storage.size());assertEquals(1,((Number)get(prefix,"candidateCount")).intValue());
                }
            }
        }for(var pool:AnalysisResources.Pool.values())assertEquals(0,resources.used(pool));
    }
    private static void expire(AutoCloseable scope)throws Exception{scope.close();}
    private static AutoCloseable child(Object owner,String method)throws Exception{return (AutoCloseable)get(owner,method);}
    private static AutoCloseable indexedChild(Object owner,String method,int ordinal)throws Exception{return (AutoCloseable)invoke(owner,method,new Class<?>[]{int.class},ordinal);}
    private static String fieldText(Object view)throws Exception {
        long size=((Number)get(view,"characterCount")).longValue();char[] block=new char[7];var value=new StringBuilder();
        for(long at=0;at<size;){int n=(int)Math.min(block.length,size-at);invoke(view,"readCharacters",new Class<?>[]{long.class,char[].class,int.class,int.class},at,block,0,n);value.append(block,0,n);at+=n;}return value.toString();
    }
    private static void sameIdentity(Object view,Id expected)throws Exception {
        if(expected==null){assertNull(view);return;}
        assertNotNull(view);var oracle=PagedDependencySupportCursorTest.class.getDeclaredMethod("same",Object.class,Id.class);oracle.setAccessible(true);oracle.invoke(null,view,expected);
    }
    private static void idField(Object scope,String method,Id expected)throws Exception{try(var view=child(scope,method)){sameIdentity(view,expected);}}
    private static void textField(Object scope,String method,String expected)throws Exception{try(var view=child(scope,method)){assertEquals(expected,fieldText(view));}}
    private static void idList(Object scope,String count,String method,List<? extends Id> expected)throws Exception {
        assertEquals(expected.size(),((Number)get(scope,count)).intValue());
        for(int i=expected.size()-1;i>=0;i--)try(var view=indexedChild(scope,method,i)){sameIdentity(view,expected.get(i));}
        assertThrows(IndexOutOfBoundsException.class,()->indexedChild(scope,method,expected.size()));
    }
    private static void supports(Object scope,List<Support> expected)throws Exception {
        assertEquals(expected.size(),((Number)get(scope,"supportCount")).intValue());
        for(int s=expected.size()-1;s>=0;s--)try(var view=indexedChild(scope,"openSupport",s)) {
            var want=expected.get(s);assertEquals(want.kind(),get(view,"kind"));idField(view,"producer",want.producer());idField(view,"origin",want.origin());
            idList(view,"premiseCount","premise",want.premises());
        }
    }
    private static void complete(Object scope,DependencySiteFact want)throws Exception {
        idField(scope,"caller",want.caller());idField(scope,"entry",want.entry());idField(scope,"sequence",want.sequence());idField(scope,"operation",want.operation());assertEquals(want.offset(),((Number)get(scope,"offset")).intValue());
        idField(scope,"siteOrigin",want.siteOrigin());idField(scope,"targetOrigin",want.targetOrigin());idField(scope,"subject",want.subject());
        textField(scope,"technology",want.technology());textField(scope,"command",want.command());textField(scope,"namespace",want.namespace());textField(scope,"nameProfile",want.nameProfile());
        assertEquals(want.targetKind(),get(scope,"targetKind"));assertEquals(want.reachability(),get(scope,"reachability"));assertEquals(want.targetStatus(),get(scope,"targetStatus"));assertEquals(want.analysisStatus(),get(scope,"analysisStatus"));
        assertEquals(want.modelValueRemainder(),get(scope,"modelValueRemainder"));assertEquals(want.sourceValueRemainder(),get(scope,"sourceValueRemainder"));assertEquals(want.interpretationUnknownRemainder(),get(scope,"interpretationUnknownRemainder"));assertEquals(want.effectiveUnknownRemainder(),get(scope,"effectiveUnknownRemainder"));assertEquals(want.openControlRemainder(),get(scope,"openControlRemainder"));
        try(var point=child(scope,"valuePoint")) {
            if(want.valuePoint()==null)assertNull(point);
            else {
                assertEquals(want.valuePoint().kind(),get(point,"kind"));idField(point,"entry",want.valuePoint().entry());idField(point,"operation",want.valuePoint().operation());
                String kind=want.valuePoint().outcome()==null?"NONE":switch(want.valuePoint().outcome()){case Control.NormalOutcome ignored->"NORMAL";case Control.ExceptionOutcome ignored->"EXCEPTION";case Control.OtherExceptionOutcome ignored->"OTHER_EXCEPTION";case Control.HaltOutcome ignored->"HALT";case Control.DivergeOutcome ignored->"DIVERGE";};
                assertEquals(kind,get(point,"outcomeKind").toString());try(var tag=child(point,"exceptionTag")){if(want.valuePoint().outcome() instanceof Control.ExceptionOutcome error)assertEquals(error.tag(),fieldText(tag));else assertNull(tag);}
            }
        }
        assertEquals(want.rawCandidates().size(),((Number)get(scope,"rawCandidateCount")).intValue());
        for(int i=want.rawCandidates().size()-1;i>=0;i--)try(var row=indexedChild(scope,"openRawCandidate",i)){assertEquals(want.rawCandidates().get(i).rawValue(),text(row,true));supports(row,want.rawCandidates().get(i).supports());}
        assertEquals(want.candidates().size(),((Number)get(scope,"candidateCount")).intValue());
        for(int i=want.candidates().size()-1;i>=0;i--)try(var row=candidate(scope,i)){var selected=want.candidates().get(i);assertEquals(selected.referenceName(),text(row,false));assertEquals(selected.rawValue(),text(row,true));supports(row,selected.supports());}
        idList(scope,"evidenceCount","evidence",want.evidence());idList(scope,"provenanceCount","provenance",want.provenance());idList(scope,"premiseCount","premise",want.premises());idList(scope,"uncertaintyCount","uncertainty",want.uncertaintyRefs());
        assertEquals(want.analysisReasons().size(),((Number)get(scope,"reasonCount")).intValue());for(int i=want.analysisReasons().size()-1;i>=0;i--)try(var reason=indexedChild(scope,"reason",i)){assertEquals(want.analysisReasons().get(i),fieldText(reason));}
    }
    @Test void allTwentyNineFieldsSealedEvidenceDuplicateOrdinalsUtf16AndEveryOutcomeMatchHandmadeCompleteOracle()throws Exception {
        var oracle=PagedDependencyFactStorageTest.class.getDeclaredMethod("fact",int.class);oracle.setAccessible(true);
        for(int number=0;number<6;number++) {
            var expected=(DependencySiteFact)oracle.invoke(null,number);var resources=resources();
            try(var pages=new FilePageStore(directory,256,1,resources);var storage=new PagedDependencyFactStorage(pages,resources)) {
                storage.append(expected);assertEquals(expected,storage.get(0));long resident=resources.used(AnalysisResources.Pool.RESIDENT);
                try(var scope=fact(storage)){complete(scope,expected);assertEquals(resident,resources.used(AnalysisResources.Pool.RESIDENT));assertTrue(resources.used(AnalysisResources.Pool.SCRATCH)<=8192);}
                assertEquals(0,resources.used(AnalysisResources.Pool.SCRATCH));
            }for(var pool:AnalysisResources.Pool.values())assertEquals(0,resources.used(pool));
        }
        var expected=fixture(2,0);var resources=resources();try(var pages=new FilePageStore(directory,256,1,resources);var storage=new PagedDependencyFactStorage(pages,resources)) {
            storage.append(expected);try(var scope=fact(storage)){complete(scope,expected);}
        }for(var pool:AnalysisResources.Pool.values())assertEquals(0,resources.used(pool));
    }
    @Test void factCandidatePressureAndInvalidTextRangesHaveDistinctNonDestructiveVersusAbortingOwnership()throws Exception {
        var resources=resources();try(var pages=new FilePageStore(directory,256,1,resources)) {
            long borrowed=pages.allocate(),live=pages.statistics().livePages(),baseline=resources.heapUsed();
            try(var storage=new PagedDependencyFactStorage(pages,resources)) {
                storage.append(fixture(256,0));try(var scope=fact(storage);var row=candidate(scope,9);var support=indexedChild(row,"openSupport",0)) {
                    assertThrows(IndexOutOfBoundsException.class,()->invoke(row,"readRaw",new Class<?>[]{long.class,char[].class,int.class,int.class},-1L,new char[7],0,1));assertEquals(256,((Number)get(scope,"candidateCount")).intValue());
                    long remaining=resources.limits().heapBytes()-resources.heapUsed();try(var pressure=resources.reserve(AnalysisResources.Pool.RESIDENT,remaining,AnalysisResources.Phase.CONTROL)) {
                        assertEquals(remaining,pressure.amount());var failure=assertThrows(AnalysisResources.Exhausted.class,()->candidate(scope,10));assertEquals(AnalysisResources.Resource.HEAP,failure.resource());assertEquals(AnalysisResources.Phase.REPLAY,failure.phase());
                        assertThrows(IllegalStateException.class,()->get(row,"rawLength"));assertThrows(IllegalStateException.class,()->get(support,"kind"));assertThrows(IllegalStateException.class,storage::size);assertEquals(0,resources.used(AnalysisResources.Pool.SCRATCH));assertEquals(live,pages.statistics().livePages());
                    }
                }assertEquals(baseline,resources.heapUsed());
            }byte[] value=new byte[8];pages.read(borrowed,0,value,0,8);assertArrayEquals(new byte[8],value);pages.release(borrowed);
        }for(var pool:AnalysisResources.Pool.values())assertEquals(0,resources.used(pool));
    }
    @Test void candidateBlockFaultKeepsPrimaryExpiresAllParentsAndChildrenWithoutClosingBorrowedBackend()throws Exception {
        var resources=resources();try(var pages=new FilePageStore(directory,256,1,resources)) {
            long borrowed=pages.allocate(),live=pages.statistics().livePages(),baseline=resources.heapUsed();var fault=new SourceCorrelationSnapshotTest.FaultPages(pages);
            try(var storage=new PagedDependencyFactStorage(fault,resources)) {
                storage.append(fixture(1,1024));try(var scope=fact(storage);var row=candidate(scope,0);var support=indexedChild(row,"openSupport",0)) {
                    fault.arm(1);assertSame(fault.primary,assertThrows(PageStore.Failure.class,()->text(row,true)));assertThrows(IllegalStateException.class,()->get(row,"rawLength"));assertThrows(IllegalStateException.class,()->get(scope,"candidateCount"));assertThrows(IllegalStateException.class,()->get(support,"kind"));assertThrows(IllegalStateException.class,storage::size);
                    assertFalse(fault.closed);assertEquals(live,pages.statistics().livePages());assertEquals(baseline,resources.heapUsed());assertEquals(0,resources.used(AnalysisResources.Pool.SCRATCH));
                }
            }byte[] value=new byte[8];pages.read(borrowed,0,value,0,8);assertArrayEquals(new byte[8],value);pages.release(borrowed);
        }for(var pool:AnalysisResources.Pool.values())assertEquals(0,resources.used(pool));
    }
    @Test void nativeExpiryRetiresFactCandidateAndSupportButPreservesBorrowedCheckedAirAndBackend()throws Exception {
        var fixture=new SourceCorrelationSnapshotTest();fixture.directory=directory;var publication=SourceCorrelationSnapshotTest.program(1);var evidence=fixture.source(publication);
        try(var input=fixture.new NativeCase(publication,evidence)) {
            long borrowed=input.pages.allocate();try(var storage=new PagedDependencyFactStorage(input.pages,input.execution)) {
                storage.append(fixture(256,0));try(var scope=fact(storage);var row=candidate(scope,9);var support=indexedChild(row,"openSupport",0)) {
                    expire(input.program);assertThrows(IllegalStateException.class,()->get(scope,"candidateCount"));assertThrows(IllegalStateException.class,()->get(row,"rawLength"));assertThrows(IllegalStateException.class,()->get(support,"kind"));assertThrows(IllegalStateException.class,storage::size);
                    for(var pool:AnalysisResources.Pool.values())assertEquals(0,input.execution.used(pool));assertEquals(1,input.pages.statistics().livePages());assertTrue(input.checked.result().isStructurallyValid());
                }
            }byte[] value=new byte[8];input.pages.read(borrowed,0,value,0,8);assertArrayEquals(new byte[8],value);input.pages.release(borrowed);
        }
    }
    @Test void scratchDenialBeforeFieldCarrierEscapesExpiresEveryOwnedScopeWithoutClosingBorrowedBackend()throws Exception {
        var resources=new AnalysisResources(new AnalysisResources.Limits(650_000,4096,0,256_000_000,4,Long.MAX_VALUE,Long.MAX_VALUE));
        try(var pages=new FilePageStore(directory,256,1,resources)) {
            long borrowed=pages.allocate(),live=pages.statistics().livePages(),baseline=resources.heapUsed();
            try(var storage=new PagedDependencyFactStorage(pages,resources)) {
                storage.append(fixture(1,0));try(var scope=storage.openFact(0);var row=scope.openCandidate(0);var support=row.openSupport(0);var identity=support.producer()) {
                    long remainder=resources.limits().scratchBytes()-resources.used(AnalysisResources.Pool.SCRATCH);
                    try(var pressure=resources.reserve(AnalysisResources.Pool.SCRATCH,remainder,AnalysisResources.Phase.CONTROL)) {
                        assertEquals(remainder,pressure.amount());var failure=assertThrows(AnalysisResources.Exhausted.class,scope::technology);
                        assertEquals(AnalysisResources.Resource.SCRATCH,failure.resource());assertEquals(AnalysisResources.Phase.REPLAY,failure.phase());
                        assertEquals(remainder,resources.used(AnalysisResources.Pool.SCRATCH));assertEquals(baseline+remainder,resources.heapUsed());assertEquals(live,pages.statistics().livePages());
                        assertThrows(IllegalStateException.class,scope::candidateCount);assertThrows(IllegalStateException.class,row::rawLength);assertThrows(IllegalStateException.class,support::kind);assertThrows(IllegalStateException.class,identity::domain);assertThrows(IllegalStateException.class,storage::size);
                    }
                }assertEquals(baseline,resources.heapUsed());assertEquals(0,resources.used(AnalysisResources.Pool.SCRATCH));
            }byte[] bytes=new byte[8];pages.read(borrowed,0,bytes,0,8);assertArrayEquals(new byte[8],bytes);pages.release(borrowed);
        }for(var pool:AnalysisResources.Pool.values())assertEquals(0,resources.used(pool));
    }
    @Test void workDenialFromLiveCandidateImmediatelyRetiresParentsChildrenAndPayloadWithoutExtraQueryOrClose()throws Exception {
        var resources=new AnalysisResources(new AnalysisResources.Limits(650_000,8_000_000,0,256_000_000,4,10_000_000,Long.MAX_VALUE));
        try(var pages=new FilePageStore(directory,256,1,resources)) {
            long borrowed=pages.allocate(),live=pages.statistics().livePages(),baseline=resources.heapUsed();
            try(var storage=new PagedDependencyFactStorage(pages,resources)) {
                storage.append(fixture(1,0));try(var scope=storage.openFact(0);var row=scope.openCandidate(0);var support=row.openSupport(0);var identity=support.producer()) {
                    resources.work(resources.limits().workUnits()-resources.workUsed(),AnalysisResources.Phase.CONTROL);
                    var failure=assertThrows(AnalysisResources.Exhausted.class,row::rawLength);assertEquals(AnalysisResources.Resource.WORK,failure.resource());assertEquals(AnalysisResources.Phase.DEMAND,failure.phase());
                    assertEquals(0,resources.used(AnalysisResources.Pool.SCRATCH));assertEquals(baseline,resources.heapUsed());assertEquals(live,pages.statistics().livePages());
                    assertThrows(IllegalStateException.class,scope::candidateCount);assertThrows(IllegalStateException.class,support::kind);assertThrows(IllegalStateException.class,identity::domain);assertThrows(IllegalStateException.class,storage::size);
                }
            }
            // Normal backend reads also consume the deliberately exhausted shared
            // WORK pool. The cleanup port proves the borrowed handle remains live
            // without resetting quota or relabeling an ordinary read as successful.
            byte[] bytes=new byte[8];pages.readForCleanup(borrowed,0,bytes,0,8);assertArrayEquals(new byte[8],bytes);pages.releaseForCleanup(borrowed);
        }for(var pool:AnalysisResources.Pool.values())assertEquals(0,resources.used(pool));
    }
}
