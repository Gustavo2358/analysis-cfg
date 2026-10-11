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

/** Actual native SOURCE resolution/delivery and fact scope ownership. Leaf
 * success alone does not qualify the productive provider/program/global path. */
@SuppressWarnings("try")
final class NativeSourceFactCursorTest {
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
    @Test void actualSourceResolutionReadsCandidateFieldsWithoutCompleteFactProjection()throws Exception {
        var fixture=new SourceCorrelationSnapshotTest();fixture.directory=directory;var publication=publication();var evidence=evidence(fixture,publication);
        try(var input=fixture.new NativeCase(publication,evidence)) {
            var cfg=new CfgBuildCoordinator(SemanticInterpreterRegistry.empty()).buildChecked(input.program,input.checked,BuildOptions.defaults());
            try(var result=new DependencyAnalysis().prepareLeased(input.program,cfg,input.source,SourceCorrelationSnapshotTest.links(publication,evidence),input.pages,input.execution)) {
                assertEquals(4,result.programs().supportCount(0,0));assertEquals(4,result.executableOwner().result().sites().size());
                assertEquals(0,result.executableOwner().fullFactProjections(),"SOURCE_RESOLUTION_STILL_PROJECTS_COMPLETE_FACT_RED");
                assertEquals(0,result.executableOwner().typedSupportProjections());assertEquals(0,input.execution.used(AnalysisResources.Pool.SCRATCH));
            }
        }
    }
    @Test void actualWholeDependencyDeliveryDoesNotProjectCompleteFactsForSitesEdgesOrExecutableSites()throws Exception {
        var fixture=new SourceCorrelationSnapshotTest();fixture.directory=directory;var publication=publication();var evidence=evidence(fixture,publication);
        try(var input=fixture.new NativeCase(publication,evidence,false,1_000_000)) {
            var cfg=new CfgBuildCoordinator(SemanticInterpreterRegistry.empty()).buildChecked(input.program,input.checked,BuildOptions.defaults());
            try(var result=new DependencyAnalysis().prepareLeased(input.program,cfg,input.source,SourceCorrelationSnapshotTest.links(publication,evidence),input.pages,input.execution)) {
                long before=result.executableOwner().fullFactProjections();var bytes=new ByteArrayOutputStream();new DependencyJson().writeSnapshot(result,input.program,input.pages,input.execution,bytes);
                assertEquals(before,result.executableOwner().fullFactProjections(),"SOURCE_WRITER_STILL_PROJECTS_COMPLETE_FACT_RED");
                assertEquals(bytes.size(),input.execution.outputUsed());assertEquals(bytes.size(),input.resources.outputUsed());
                var product=new com.fasterxml.jackson.databind.ObjectMapper().readTree(bytes.toByteArray());assertEquals(4,product.path("sites").size());assertEquals(4,product.path("edges").size());
                var program=product.path("dependencies").path("programs").get(0);assertEquals(4,program.path("executableSites").size());var candidate=program.path("candidates").get(0);assertEquals("PG1",candidate.path("referenceName").asText());assertEquals("PG1",candidate.path("rawValue").asText());assertEquals(4,candidate.path("supports").size());
                assertEquals(0,input.execution.used(AnalysisResources.Pool.SCRATCH));
            }
        }
    }
    @Test void planOwnsFactCandidateSupportAndIdentityScopesWithoutClosingBorrowedCallOrAir()throws Exception {
        var fixture=new SourceCorrelationSnapshotTest();fixture.directory=directory;var publication=publication();var evidence=evidence(fixture,publication);
        try(var input=fixture.new NativeCase(publication,evidence)) {
            var cfg=new CfgBuildCoordinator(SemanticInterpreterRegistry.empty()).buildChecked(input.program,input.checked,BuildOptions.defaults());
            try(var result=new DependencyAnalysis().prepareLeased(input.program,cfg,input.source,SourceCorrelationSnapshotTest.links(publication,evidence),input.pages,input.execution)) {
                var call=result.executableOwner();var plan=result.programs().plan();long before=input.execution.used(AnalysisResources.Pool.SCRATCH);
                try(var scope=(AutoCloseable)invoke(plan,"openFact",new Class<?>[]{long.class,long.class},0L,0L);
                    var candidate=(AutoCloseable)invoke(scope,"openCandidate",new Class<?>[]{int.class},0);
                    var support=(AutoCloseable)invoke(candidate,"openSupport",new Class<?>[]{int.class},0);
                    var producer=(AutoCloseable)invoke(support,"producer",new Class<?>[0])) {
                    assertEquals("operation",invoke(producer,"domain",new Class<?>[0]));assertTrue(input.execution.used(AnalysisResources.Pool.SCRATCH)>before);plan.close();
                    assertThrows(IllegalStateException.class,()->invoke(scope,"candidateCount",new Class<?>[0]));assertThrows(IllegalStateException.class,()->invoke(candidate,"rawLength",new Class<?>[0]));assertThrows(IllegalStateException.class,()->invoke(producer,"domain",new Class<?>[0]));assertThrows(IllegalStateException.class,()->invoke(support,"kind",new Class<?>[0]));assertEquals(before,input.execution.used(AnalysisResources.Pool.SCRATCH));
                    assertEquals(4,call.result().sites().size());assertEquals(publication.id(),input.program.publicationId());assertTrue(input.source.unitCount()>0);assertTrue(input.checked.result().isStructurallyValid());
                }
            }
        }
    }
    @Test void invalidFactCandidateAndBlockRangesNeverPoisonBorrowedOwnersOrLiveProgramCatalog()throws Exception {
        var fixture=new SourceCorrelationSnapshotTest();fixture.directory=directory;var publication=publication();var evidence=evidence(fixture,publication);
        try(var input=fixture.new NativeCase(publication,evidence)) {
            var cfg=new CfgBuildCoordinator(SemanticInterpreterRegistry.empty()).buildChecked(input.program,input.checked,BuildOptions.defaults());
            try(var result=new DependencyAnalysis().prepareLeased(input.program,cfg,input.source,SourceCorrelationSnapshotTest.links(publication,evidence),input.pages,input.execution)) {
                var call=result.executableOwner();var programs=result.programs();var plan=programs.plan();long count=programs.count();
                assertThrows(IndexOutOfBoundsException.class,()->plan.openFact(plan.count(),0));
                assertThrows(IndexOutOfBoundsException.class,()->plan.openFact(0,plan.siteCount(0)));
                assertThrows(IndexOutOfBoundsException.class,()->call.openFact(4));
                assertEquals(0,input.execution.used(AnalysisResources.Pool.SCRATCH));
                try(var scope=plan.openFact(0,0);var candidate=scope.openCandidate(0)) {
                    assertThrows(IndexOutOfBoundsException.class,()->scope.openCandidate(scope.candidateCount()));
                    assertThrows(IndexOutOfBoundsException.class,()->candidate.openSupport(candidate.supportCount()));
                    char[] block=new char[7];
                    assertThrows(IndexOutOfBoundsException.class,()->candidate.readRaw(-1,block,0,1));
                    assertThrows(IndexOutOfBoundsException.class,()->candidate.readRaw(Long.MAX_VALUE,block,0,1));
                    assertThrows(IndexOutOfBoundsException.class,()->candidate.readRaw(0,block,-1,1));
                    assertThrows(IndexOutOfBoundsException.class,()->candidate.readRaw(0,block,0,8));
                    assertThrows(NullPointerException.class,()->candidate.readRaw(0,null,0,1));
                    candidate.readRaw(candidate.rawLength(),block,0,0);candidate.readRaw(0,block,0,3);assertEquals("PG1",new String(block,0,3));
                    assertEquals(4,call.result().sites().size());assertEquals(count,programs.count());assertTrue(plan.count()>0);
                }assertEquals(0,input.execution.used(AnalysisResources.Pool.SCRATCH));
            }
        }
    }
    @Test void lateCandidateReadFaultImmediatelyExpiresGeneralCallAndPlanWrapperFundingButNotBorrowedInput()throws Exception {
        var fixture=new SourceCorrelationSnapshotTest();fixture.directory=directory;var publication=publication();var evidence=evidence(fixture,publication);
        try(var input=fixture.new NativeCase(publication,evidence,true)) {
            var cfg=new CfgBuildCoordinator(SemanticInterpreterRegistry.empty()).buildChecked(input.program,input.checked,BuildOptions.defaults());
            try(var result=new DependencyAnalysis().prepareLeased(input.program,cfg,input.source,SourceCorrelationSnapshotTest.links(publication,evidence),input.backend,input.execution)) {
                var call=result.executableOwner();var plan=result.programs().plan();long before=input.execution.used(AnalysisResources.Pool.SCRATCH);
                try(var scope=plan.openFact(0,0);var candidate=scope.openCandidate(0);var support=candidate.openSupport(0);var producer=support.producer()) {
                    long[] borrowed=new long[9];try {
                        for(int i=0;i<borrowed.length;i++)borrowed[i]=input.pages.allocate();
                        byte[] bytes=new byte[8];for(long page:borrowed)input.pages.read(page,0,bytes,0,bytes.length);
                        var fault=(SourceCorrelationSnapshotTest.FaultPages)input.backend;fault.arm(1);
                        assertSame(fault.primary,assertThrows(PageStore.Failure.class,()->candidate.readRaw(0,new char[1],0,1)));
                        // Assertions deliberately precede any additional query or explicit close.
                        assertEquals(before,input.execution.used(AnalysisResources.Pool.SCRATCH),"FACT_FAULT_LEFT_PLAN_WRAPPER_FUNDING");
                        assertThrows(IllegalStateException.class,call::result,"FACT_FAULT_LEFT_GENERAL_CALL_LIVE");
                        assertThrows(IllegalStateException.class,scope::candidateCount);assertThrows(IllegalStateException.class,candidate::rawLength);
                        assertThrows(IllegalStateException.class,support::kind);assertThrows(IllegalStateException.class,producer::domain);
                        assertFalse(fault.closed);assertTrue(input.checked.result().isStructurallyValid());assertEquals(publication.id(),input.program.publicationId());assertTrue(input.source.unitCount()>0);
                        for(long page:borrowed){input.pages.read(page,0,bytes,0,bytes.length);assertArrayEquals(new byte[8],bytes);}
                    }finally{for(long page:borrowed)if(page!=0)input.pages.release(page);}
                }
            }
        }
    }
}
