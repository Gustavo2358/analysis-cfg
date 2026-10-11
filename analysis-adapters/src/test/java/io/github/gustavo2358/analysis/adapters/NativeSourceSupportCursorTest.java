package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.air.model.Publication;
import io.github.gustavo2358.analysis.dependencies.*;
import io.github.gustavo2358.analysis.dependencies.source.QualifiedSourceDependencies;
import io.github.gustavo2358.analysis.cfg.application.*;
import io.github.gustavo2358.analysis.cfg.extension.SemanticInterpreterRegistry;
import io.github.gustavo2358.analysis.solver.AnalysisResources;
import io.github.gustavo2358.analysis.solver.PageStore;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.*;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Actual native SOURCE consumers,not the standalone leaf success law. */
@SuppressWarnings("try")
final class NativeSourceSupportCursorTest {
    @TempDir Path directory;
    private static Object invoke(Object target,String name,Class<?>[] types,Object... args)throws Exception {
        try{return target.getClass().getMethod(name,types).invoke(target,args);}
        catch(InvocationTargetException failure){if(failure.getCause() instanceof RuntimeException error)throw error;if(failure.getCause() instanceof Error error)throw error;throw failure;}
    }
    private static Publication publication()throws Exception {
        var method=ManagedSourceExecutableOwnerTest.class.getDeclaredMethod("literalCalls",int.class);method.setAccessible(true);return (Publication)method.invoke(null,4);
    }
    private static QualifiedSourceDependencies evidence(SourceCorrelationSnapshotTest fixture,Publication publication)throws Exception {
        var method=ManagedSourceExecutableOwnerTest.class.getDeclaredMethod("literalEvidence",QualifiedSourceDependencies.class);method.setAccessible(true);return (QualifiedSourceDependencies)method.invoke(null,fixture.source(publication));
    }
    @Test void nativeSourcePreparationSortsFullSupportsWithoutCompleteLeafProjection()throws Exception {
        var fixture=new SourceCorrelationSnapshotTest();fixture.directory=directory;var publication=publication();var evidence=evidence(fixture,publication);
        try(var input=fixture.new NativeCase(publication,evidence)) {
            var cfg=new CfgBuildCoordinator(SemanticInterpreterRegistry.empty()).buildChecked(input.program,input.checked,BuildOptions.defaults());
            try(var result=new DependencyAnalysis().prepareLeased(input.program,cfg,input.source,SourceCorrelationSnapshotTest.links(publication,evidence),input.pages,input.execution)) {
                assertEquals(4,result.programs().supportCount(0,0));assertEquals(4,result.executableOwner().result().sites().size());
                assertEquals(0,result.executableOwner().typedSupportProjections(),"SOURCE_SUPPORT_SORT_STILL_MATERIALIZES_COMPLETE_LEAF_RED");
            }
        }
    }
    @Test void nativeSourceWireReadsPremisesWithoutCompleteLeafProjectionAndPreservesKnownCandidates()throws Exception {
        var fixture=new SourceCorrelationSnapshotTest();fixture.directory=directory;var publication=publication();var evidence=evidence(fixture,publication);
        try(var input=fixture.new NativeCase(publication,evidence,false,1_000_000)) {
            var cfg=new CfgBuildCoordinator(SemanticInterpreterRegistry.empty()).buildChecked(input.program,input.checked,BuildOptions.defaults());
            try(var result=new DependencyAnalysis().prepareLeased(input.program,cfg,input.source,SourceCorrelationSnapshotTest.links(publication,evidence),input.pages,input.execution)) {
                long before=result.executableOwner().typedSupportProjections();var bytes=new ByteArrayOutputStream();
                new DependencyJson().writeSnapshot(result,input.program,input.pages,input.execution,bytes);
                assertEquals(before,result.executableOwner().typedSupportProjections(),"SOURCE_SUPPORT_WIRE_STILL_MATERIALIZES_COMPLETE_LEAF_RED");
                assertEquals(bytes.size(),input.execution.outputUsed());assertEquals(bytes.size(),input.resources.outputUsed());
                var product=new com.fasterxml.jackson.databind.ObjectMapper().readTree(bytes.toByteArray());
                assertEquals(4,product.path("sites").size());assertEquals(4,product.path("edges").size());
                var candidate=product.path("dependencies").path("programs").get(0).path("candidates").get(0);
                assertEquals("PG1",candidate.path("referenceName").asText());assertEquals("PG1",candidate.path("rawValue").asText());assertEquals(4,candidate.path("supports").size());
            }
        }
    }
    @Test void planOwnedSupportCursorExpiresItsIdentityViewsWithoutClosingBorrowedGeneralCall()throws Exception {
        var fixture=new SourceCorrelationSnapshotTest();fixture.directory=directory;var publication=publication();var evidence=evidence(fixture,publication);
        try(var input=fixture.new NativeCase(publication,evidence)) {
            var cfg=new CfgBuildCoordinator(SemanticInterpreterRegistry.empty()).buildChecked(input.program,input.checked,BuildOptions.defaults());
            try(var result=new DependencyAnalysis().prepareLeased(input.program,cfg,input.source,SourceCorrelationSnapshotTest.links(publication,evidence),input.pages,input.execution)) {
                var call=result.executableOwner();var plan=result.programs().plan();long before=input.execution.used(AnalysisResources.Pool.SCRATCH);
                try(var support=(AutoCloseable)invoke(plan,"openSupport",new Class<?>[]{long.class,long.class,int.class,int.class},0L,0L,0,0);
                    var producer=(AutoCloseable)invoke(support,"producer",new Class<?>[0])) {
                    assertEquals("operation",invoke(producer,"domain",new Class<?>[0]));assertTrue(input.execution.used(AnalysisResources.Pool.SCRATCH)>before);
                    plan.close();assertThrows(IllegalStateException.class,()->invoke(producer,"domain",new Class<?>[0]));
                    assertThrows(IllegalStateException.class,()->invoke(support,"kind",new Class<?>[0]));assertEquals(before,input.execution.used(AnalysisResources.Pool.SCRATCH));
                    assertEquals(4,call.result().sites().size());assertEquals(publication.id(),input.program.publicationId());assertTrue(input.source.unitCount()>0);assertTrue(input.checked.result().isStructurallyValid());
                }
            }
        }
    }
    @Test void lateIdentityReadFaultImmediatelyRetiresPlanWrapperFundingAndGeneralCallNotBorrowedInput()throws Exception {
        readFault(false);
    }
    @Test void lateIdentityReadFaultImmediatelyExpiresGeneralCallResultWithoutWaitingForAnotherReadOrClose()throws Exception {
        readFault(true);
    }
    @Test void invalidSourceSupportAndTextRangesDoNotAbortLiveBorrowedOwnersOrProgramCatalog()throws Exception {
        var fixture=new SourceCorrelationSnapshotTest();fixture.directory=directory;var publication=publication();var evidence=evidence(fixture,publication);
        try(var input=fixture.new NativeCase(publication,evidence)) {
            var cfg=new CfgBuildCoordinator(SemanticInterpreterRegistry.empty()).buildChecked(input.program,input.checked,BuildOptions.defaults());
            try(var result=new DependencyAnalysis().prepareLeased(input.program,cfg,input.source,SourceCorrelationSnapshotTest.links(publication,evidence),input.pages,input.execution)) {
                var call=result.executableOwner();var programs=result.programs();var plan=programs.plan();long count=programs.count();
                try(var support=plan.openSupport(0,0,0,0);var producer=support.producer()){
                    char[] bytes=new char[7];var part=PagedDependencyFactStorage.IdentityPart.LOCAL;long length=producer.characterCount(part);
                    assertThrows(IndexOutOfBoundsException.class,()->producer.readCharacters(part,-1,bytes,0,1));
                    assertThrows(IndexOutOfBoundsException.class,()->producer.readCharacters(part,Long.MAX_VALUE,bytes,0,1));
                    assertThrows(IndexOutOfBoundsException.class,()->producer.readCharacters(part,0,bytes,-1,1));
                    assertThrows(IndexOutOfBoundsException.class,()->producer.readCharacters(part,0,bytes,0,8));
                    assertThrows(NullPointerException.class,()->producer.readCharacters(null,0,bytes,0,1));
                    producer.readCharacters(part,length,bytes,0,0);assertEquals("operation",producer.domain());
                    assertThrows(IndexOutOfBoundsException.class,()->support.premise(support.premiseCount()));
                    assertThrows(IndexOutOfBoundsException.class,()->plan.openSupport(plan.count(),0,0,0));
                    assertThrows(IndexOutOfBoundsException.class,()->plan.openSupport(0,plan.siteCount(0),0,0));
                    assertThrows(IndexOutOfBoundsException.class,()->plan.openSupport(0,0,1,0));
                    assertThrows(IndexOutOfBoundsException.class,()->call.openSupport(0,1,0));
                    assertEquals(4,call.result().sites().size());assertEquals("operation",producer.domain());assertTrue(plan.count()>0);
                    assertThrows(IndexOutOfBoundsException.class,()->programs.openSupport(0,0,programs.supportCount(0,0)));
                    assertEquals(count,assertDoesNotThrow(programs::count,"SOURCE_INVALID_SUPPORT_ORDINAL_ABORTED_LIVE_PROGRAM_RED"));
                }
            }
        }
    }
    private void readFault(boolean ownerFirst)throws Exception {
        var fixture=new SourceCorrelationSnapshotTest();fixture.directory=directory;var publication=publication();var evidence=evidence(fixture,publication);
        try(var input=fixture.new NativeCase(publication,evidence,true)) {
            var cfg=new CfgBuildCoordinator(SemanticInterpreterRegistry.empty()).buildChecked(input.program,input.checked,BuildOptions.defaults());
            try(var result=new DependencyAnalysis().prepareLeased(input.program,cfg,input.source,SourceCorrelationSnapshotTest.links(publication,evidence),input.backend,input.execution)) {
                var call=result.executableOwner();var plan=result.programs().plan();long before=input.execution.used(AnalysisResources.Pool.SCRATCH);
                try(var support=(AutoCloseable)invoke(plan,"openSupport",new Class<?>[]{long.class,long.class,int.class,int.class},0L,0L,0,0);
                    var producer=(AutoCloseable)invoke(support,"producer",new Class<?>[0])) {
                    long[] borrowed=new long[9];try {
                        for(int i=0;i<borrowed.length;i++)borrowed[i]=input.pages.allocate();
                        byte[] bytes=new byte[8];for(long page:borrowed)input.pages.read(page,0,bytes,0,bytes.length);
                        var fault=(SourceCorrelationSnapshotTest.FaultPages)input.backend;fault.arm(1);
                        var part=Class.forName(PagedDependencyFactStorage.class.getName()+"$IdentityPart");Object local=java.util.Arrays.stream(part.getEnumConstants()).filter(v->v.toString().equals("LOCAL")).findFirst().orElseThrow();
                        assertSame(fault.primary,assertThrows(PageStore.Failure.class,()->invoke(producer,"readCharacters",new Class<?>[]{part,long.class,char[].class,int.class,int.class},local,0L,new char[1],0,1)));
                        if(ownerFirst)assertThrows(IllegalStateException.class,call::result,"SOURCE_SUPPORT_FAULT_LEFT_GENERAL_OWNER_LIVE_RED");
                        assertEquals(before,input.execution.used(AnalysisResources.Pool.SCRATCH),"SOURCE_SUPPORT_FAULT_LEFT_PLAN_WRAPPER_FUNDING_RED");
                        assertThrows(IllegalStateException.class,call::result,"SOURCE_SUPPORT_FAULT_LEFT_GENERAL_OWNER_LIVE_RED");
                        assertThrows(IllegalStateException.class,()->invoke(producer,"domain",new Class<?>[0]));assertThrows(IllegalStateException.class,()->invoke(support,"kind",new Class<?>[0]));
                        assertFalse(fault.closed);assertTrue(input.checked.result().isStructurallyValid());assertEquals(publication.id(),input.program.publicationId());assertTrue(input.source.unitCount()>0);
                        for(long page:borrowed){input.pages.read(page,0,bytes,0,bytes.length);assertArrayEquals(new byte[8],bytes);}
                    }finally{for(long page:borrowed)if(page!=0)input.pages.release(page);}
                }
            }
        }
    }
}
