package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.validation.*;
import io.github.gustavo2358.analysis.dependencies.*;
import io.github.gustavo2358.analysis.solver.*;
import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Fail the actual borrowed accessor, independently of cache-induced read order. */
final class SourceProgramBorrowedFaultOwnershipTest {
    @TempDir Path directory;
    private enum Owner { QUALIFIED,PLAN,RESULT,SOURCE,PLAN_REQUEST }
    private static final Map<String,Owner> READ_OWNERS=Map.of(
        SourceQualifiedSnapshot.class.getName(),Owner.QUALIFIED,
        SourceTargetPlan.class.getName(),Owner.PLAN,
        SourceProgramSnapshot.class.getName(),Owner.RESULT,
        "io.github.gustavo2358.analysis.dependencies.SourceSnapshotBuilder$Snapshot",Owner.SOURCE);

    @Test void eachActualFailedOwnerReleasesItsExactPagesWithoutStealingSurvivingOwners()throws Exception {
        for(var owner:Owner.values()) {
            var publication=SourceCorrelationSnapshotTest.program(1);
            var evidence=SourceProgramSnapshotTest.source(publication,"source-possibility/unknown-copy-kill.source.json");
            var resources=new AnalysisResources(new AnalysisResources.Limits(64L*1024*1024,16L*1024*1024,0,512L*1024*1024,8,Long.MAX_VALUE,0));
            try(var airPages=new FilePageStore(directory,4096,4,resources,AnalysisResources.Phase.INDEX);
                var sourcePages=new FilePageStore(directory,4096,4,resources,AnalysisResources.Phase.INDEX)) {
                SnapshotValidator.CheckedSnapshot checked;
                try(var original=AirSnapshot.fromPublication(publication);
                    var builder=new AirSnapshotBuilder(new PagedAirStorage(airPages,resources,AnalysisResources.Phase.DECODE))) {
                    long root=PagedAirStorageTest.copy(original,original.root(),null,builder);
                    checked=SnapshotValidator.check(builder.finish(root),ValidationOptions.defaults(),new PagedSnapshotValidationStorage(airPages,resources));
                    assertTrue(checked.result().isStructurallyValid(),checked.result().toString());
                }
                var pages=new TrackedPages(sourcePages);
                try(checked;var program=new SnapshotProgram(checked,new PagedSnapshotIdentityStorage(airPages,resources),new PagedSnapshotOrderStorage(airPages,resources),resources)) {
                    var execution=AnalysisResources.executionFor(program);var codec=new QualifiedSourceJson();
                    try(var admission=SourceAdmissionSnapshot.admit(codec.decodeSnapshot(new ByteArrayInputStream(codec.encode(evidence)),pages,execution),pages,execution);
                        var correlations=SourceCorrelationSnapshot.prepare(admission,program,List.of(),pages,execution)) {
                        var beforeQualified=pages.handles();
                        try(var qualified=SourceQualifiedSnapshot.prepare(admission,pages,execution)) {
                            var qualifiedPages=difference(pages.handles(),beforeQualified);
                            try(var plan=SourceTargetPlan.prepare(correlations,qualified,SourceTargetPlanTest.result(publication,List.of()),pages,execution)) {
                                var planPages=difference(pages.handles(),union(beforeQualified,qualifiedPages));
                                var baseline=pages.handles();long count=plan.count(),units=admission.unitCount(),queries=plan.queryCount(0);
                                if(owner==Owner.RESULT) {
                                    try(var result=SourceProgramSnapshot.prepare(plan,qualified,pages,execution)) {
                                        var resultPages=difference(pages.handles(),baseline);assertFalse(resultPages.isEmpty());
                                        pages.arm(owner);assertSame(pages.primary,assertThrows(PageStore.Failure.class,()->result.candidateCount(0)));
                                        assertThrows(IllegalStateException.class,result::count);
                                    }
                                    assertEquals(baseline,pages.handles(),"only derived result pages retire");
                                } else {
                                    pages.arm(owner);assertSame(pages.primary,assertThrows(PageStore.Failure.class,()->SourceProgramSnapshot.prepare(plan,qualified,pages,execution)));
                                    var expected=switch(owner){case QUALIFIED->difference(baseline,qualifiedPages);case PLAN,PLAN_REQUEST->difference(baseline,planPages);case SOURCE->Set.<Long>of();default->throw new AssertionError();};
                                    assertEquals(expected,pages.handles(),"exact handles, not a relaxed live-page count");
                                }
                                assertEquals(1,pages.fired,"the selected real owner's primitive must fail exactly once");
                                if(owner==Owner.SOURCE) {
                                    assertThrows(IllegalStateException.class,admission::snapshot);assertThrows(IllegalStateException.class,plan::count);assertThrows(IllegalStateException.class,qualified::admission);
                                } else {
                                    assertEquals(units,admission.unitCount());assertEquals(0,correlations.operationCount(0,0));
                                    if(owner==Owner.PLAN||owner==Owner.PLAN_REQUEST)assertThrows(IllegalStateException.class,plan::count);
                                    else {assertEquals(count,plan.count());assertEquals(queries,plan.queryCount(0));assertTrue(plan.sourceUnit(0)>=0);}
                                    if(owner==Owner.QUALIFIED)assertThrows(IllegalStateException.class,qualified::admission);
                                    else {assertEquals(units,qualified.unitCount());assertTrue(qualified.count(0,SourceQualifiedSnapshot.Kind.OCCURRENCE)>0);}
                                }
                                assertFalse(pages.closed);assertEquals(publication.id(),program.publicationId());
                                var surviving=pages.handles();long borrowed=pages.allocate();pages.release(borrowed);assertEquals(surviving,pages.handles());
                            }
                        }
                    }
                    assertTrue(pages.handles().isEmpty());
                }
            }
            for(var pool:AnalysisResources.Pool.values())assertEquals(0,resources.used(pool));
        }
    }
    private static Set<Long> difference(Set<Long> left,Set<Long> right){var result=new HashSet<>(left);result.removeAll(right);return Set.copyOf(result);}
    private static Set<Long> union(Set<Long> left,Set<Long> right){var result=new HashSet<>(left);result.addAll(right);return Set.copyOf(result);}
    private static final class TrackedPages implements PageStore {
        private final PageStore delegate;
        private final Set<Long> live=new HashSet<>();
        final Failure primary=new Failure(Reason.IO,"synthetic selected borrowed-owner read failure");
        private Owner armed;int fired;boolean closed;
        TrackedPages(PageStore delegate){this.delegate=delegate;}
        void arm(Owner owner){assertNull(armed);armed=owner;}
        Set<Long> handles(){assertEquals(delegate.statistics().livePages(),live.size());return Set.copyOf(live);}
        @Override public int pageBytes(){return delegate.pageBytes();}
        @Override public long allocate(){long page=delegate.allocate();assertTrue(live.add(page));return page;}
        @Override public void read(long page,int offset,byte[] out,int at,int count) {
            if(armed!=null) {
                var owner=StackWalker.getInstance().walk(frames->frames.map(StackWalker.StackFrame::getClassName).filter(READ_OWNERS::containsKey).findFirst().map(READ_OWNERS::get).orElse(null));
                boolean requestWord=armed==Owner.PLAN_REQUEST&&StackWalker.getInstance().walk(frames->{
                    var methods=frames.filter(frame->frame.getClassName().equals(SourceTargetPlan.class.getName())).map(StackWalker.StackFrame::getMethodName).toList();
                    return methods.contains("requestsAnyQuery")&&!methods.contains("queryCount");
                });
                if(owner==armed||requestWord){armed=null;fired++;throw primary;}
            }
            delegate.read(page,offset,out,at,count);
        }
        @Override public void write(long page,int offset,byte[] in,int at,int count){delegate.write(page,offset,in,at,count);}
        @Override public void release(long page){delegate.release(page);assertTrue(live.remove(page));}
        @Override public void readForCleanup(long page,int offset,byte[] out,int at,int count){delegate.readForCleanup(page,offset,out,at,count);}
        @Override public void releaseForCleanup(long page){delegate.releaseForCleanup(page);assertTrue(live.remove(page));}
        @Override public void flush(){delegate.flush();}
        @Override public Statistics statistics(){return delegate.statistics();}
        @Override public void close(){closed=true;delegate.close();}
    }
}
