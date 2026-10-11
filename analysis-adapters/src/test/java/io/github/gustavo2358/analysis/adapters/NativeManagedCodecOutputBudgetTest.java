package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.analysis.dependencies.*;
import io.github.gustavo2358.analysis.cfg.application.*;
import io.github.gustavo2358.analysis.cfg.extension.SemanticInterpreterRegistry;
import io.github.gustavo2358.analysis.solver.AnalysisResources;
import java.io.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Managed codec ports must not require
 * a metering wrapper supplied secretly by only the file adapter. */
final class NativeManagedCodecOutputBudgetTest {
    @TempDir Path directory;
    private SourceCorrelationSnapshotTest fixtures(){var value=new SourceCorrelationSnapshotTest();value.directory=directory;return value;}
    @Test void directGeneralCodecHonorsCommonZeroOutputBeforeAnyBytesAndLeavesBorrowedOwnersAlive()throws Exception {
        var fixture=fixtures();var publication=SourceCorrelationSnapshotTest.program(1);var evidence=fixture.source(publication);
        try(var input=fixture.new NativeCase(publication,evidence)) {
            var cfg=new CfgBuildCoordinator(SemanticInterpreterRegistry.empty()).buildChecked(input.program,input.checked,BuildOptions.defaults());
            assertEquals(CfgBuildResult.Status.CFG_BUILT,cfg.status());
            try(var result=new DependencyAnalysis().prepareManaged(input.program,cfg,input.pages,input.execution)) {
                var bytes=new ByteArrayOutputStream();long scratch=input.execution.used(AnalysisResources.Pool.SCRATCH),resident=input.execution.used(AnalysisResources.Pool.RESIDENT);
                var failure=assertThrows(AnalysisResources.Exhausted.class,()->new DependencyJson().writeSnapshot(result,input.program,input.execution,bytes),"DIRECT_GENERAL_CODEC_OUTPUT_BYPASS_RED");
                assertEquals(AnalysisResources.Resource.OUTPUT,failure.resource());assertEquals(AnalysisResources.Phase.ENCODE,failure.phase());assertEquals(0,bytes.size());assertEquals(0,input.execution.outputUsed());assertEquals(0,input.resources.outputUsed());
                assertEquals(scratch,input.execution.used(AnalysisResources.Pool.SCRATCH));assertEquals(resident,input.execution.used(AnalysisResources.Pool.RESIDENT));assertEquals(1,result.result().sites().size());assertTrue(input.source.unitCount()>0);assertEquals(publication.id(),input.program.publicationId());
            }
        }
    }
    @Test void directSourceCodecHonorsCommonZeroOutputBeforeAnyBytesAndLeavesBorrowedOwnersAlive()throws Exception {
        var fixture=fixtures();var publication=SourceCorrelationSnapshotTest.program(1);var evidence=fixture.source(publication);
        try(var input=fixture.new NativeCase(publication,evidence)) {
            var cfg=new CfgBuildCoordinator(SemanticInterpreterRegistry.empty()).buildChecked(input.program,input.checked,BuildOptions.defaults());
            try(var result=new DependencyAnalysis().prepareLeased(input.program,cfg,input.source,SourceCorrelationSnapshotTest.links(publication,evidence),input.pages,input.execution)) {
                var bytes=new ByteArrayOutputStream();long scratch=input.execution.used(AnalysisResources.Pool.SCRATCH),resident=input.execution.used(AnalysisResources.Pool.RESIDENT);
                var failure=assertThrows(AnalysisResources.Exhausted.class,()->new DependencyJson().writeSnapshot(result,input.program,input.pages,input.execution,bytes),"DIRECT_SOURCE_CODEC_OUTPUT_BYPASS_RED");
                assertEquals(AnalysisResources.Resource.OUTPUT,failure.resource());assertEquals(AnalysisResources.Phase.ENCODE,failure.phase());assertEquals(0,bytes.size());assertEquals(0,input.execution.outputUsed());assertEquals(0,input.resources.outputUsed());
                assertEquals(scratch,input.execution.used(AnalysisResources.Pool.SCRATCH));assertEquals(resident,input.execution.used(AnalysisResources.Pool.RESIDENT));assertEquals(1,result.executable().sites().size());assertTrue(result.programs().count()>0);assertTrue(input.source.unitCount()>0);assertEquals(publication.id(),input.program.publicationId());
            }
        }
    }
    @Test void directAndAtomicGeneralCodecChargeExactlyOneCommonLogicalByteCount()throws Exception {
        for(boolean file:new boolean[]{false,true}) {
            var fixture=fixtures();var publication=SourceCorrelationSnapshotTest.program(1);var evidence=fixture.source(publication);
            try(var input=fixture.new NativeCase(publication,evidence,false,1_000_000)) {
                var cfg=new CfgBuildCoordinator(SemanticInterpreterRegistry.empty()).buildChecked(input.program,input.checked,BuildOptions.defaults());
                try(var result=new DependencyAnalysis().prepareManaged(input.program,cfg,input.pages,input.execution)) {
                    byte[] wire;if(file){Path target=directory.resolve("general.json");new SnapshotDependencyFileWriter().write(result,input.program,target,input.execution);wire=Files.readAllBytes(target);}else{var bytes=new ByteArrayOutputStream();new DependencyJson().writeSnapshot(result,input.program,input.execution,bytes);wire=bytes.toByteArray();}
                    assertEquals(wire.length,input.execution.outputUsed(),"DIRECT_GENERAL_CODEC_OUTPUT_CHARGE_ABSENT_OR_FILE_DOUBLE_RED");assertEquals(wire.length,input.resources.outputUsed());assertEquals(publication.id().localId(),new com.fasterxml.jackson.databind.ObjectMapper().readTree(wire).path("publication").path("localId").asText());assertEquals(1,result.result().sites().size());
                }
            }
        }
    }
    @Test void directAndAtomicSourceCodecChargeExactlyOneCommonLogicalByteCount()throws Exception {
        for(boolean file:new boolean[]{false,true}) {
            var fixture=fixtures();var publication=SourceCorrelationSnapshotTest.program(1);var evidence=fixture.source(publication);
            try(var input=fixture.new NativeCase(publication,evidence,false,1_000_000)) {
                var cfg=new CfgBuildCoordinator(SemanticInterpreterRegistry.empty()).buildChecked(input.program,input.checked,BuildOptions.defaults());
                try(var result=new DependencyAnalysis().prepareLeased(input.program,cfg,input.source,SourceCorrelationSnapshotTest.links(publication,evidence),input.pages,input.execution)) {
                    byte[] wire;if(file){Path target=directory.resolve("source.json");new SnapshotDependencyFileWriter().write(result,target,input.pages,input.execution);wire=Files.readAllBytes(target);}else{var bytes=new ByteArrayOutputStream();new DependencyJson().writeSnapshot(result,input.program,input.pages,input.execution,bytes);wire=bytes.toByteArray();}
                    assertEquals(wire.length,input.execution.outputUsed(),"DIRECT_SOURCE_CODEC_OUTPUT_CHARGE_ABSENT_OR_FILE_DOUBLE_RED");assertEquals(wire.length,input.resources.outputUsed());assertEquals(publication.id().localId(),new com.fasterxml.jackson.databind.ObjectMapper().readTree(wire).path("publication").path("localId").asText());assertEquals(1,result.executable().sites().size());assertTrue(result.programs().count()>0);
                }
            }
        }
    }

    @Test void compressedAtomicGeneralAndSourceChargeLogicalNotCompressedBytes()throws Exception {
        for(boolean source:new boolean[]{false,true}) {
            var fixture=fixtures();var publication=SourceCorrelationSnapshotTest.program(1);var evidence=fixture.source(publication);
            try(var input=fixture.new NativeCase(publication,evidence,false,1_000_000)) {
                var cfg=new CfgBuildCoordinator(SemanticInterpreterRegistry.empty()).buildChecked(input.program,input.checked,BuildOptions.defaults());
                Path target=directory.resolve(source?"source.json.zst":"general.json.zst");
                if(source)try(var result=new DependencyAnalysis().prepareLeased(input.program,cfg,input.source,SourceCorrelationSnapshotTest.links(publication,evidence),input.pages,input.execution)) {
                    new SnapshotDependencyFileWriter().write(result,target,input.pages,input.execution);assertTrue(result.programs().count()>0);
                }else try(var result=new DependencyAnalysis().prepareManaged(input.program,cfg,input.pages,input.execution)) {
                    new SnapshotDependencyFileWriter().write(result,input.program,target,input.execution);assertEquals(1,result.result().sites().size());
                }
                byte[] wire;try(var stream=JsonFiles.input(target)){wire=stream.readAllBytes();}
                assertTrue(wire.length>Files.size(target));assertEquals(wire.length,input.execution.outputUsed());assertEquals(wire.length,input.resources.outputUsed());
                var document=new com.fasterxml.jackson.databind.ObjectMapper().readTree(wire);assertEquals(publication.id().localId(),document.path("publication").path("localId").asText());assertEquals(1,document.path("sites").size());
            }
        }
    }

    @Test void directManagedWritePreservesPrimaryIOExceptionAndNeverClosesBorrowedStream()throws Exception {
        for(boolean source:new boolean[]{false,true}) {
            var fixture=fixtures();var publication=SourceCorrelationSnapshotTest.program(1);var evidence=fixture.source(publication);
            try(var input=fixture.new NativeCase(publication,evidence,false,1_000_000)) {
                var cfg=new CfgBuildCoordinator(SemanticInterpreterRegistry.empty()).buildChecked(input.program,input.checked,BuildOptions.defaults());
                var primary=new IOException("synthetic borrowed stream failure");boolean[] closed={false};
                var stream=new OutputStream(){
                    @Override public void write(int value)throws IOException{throw primary;}
                    @Override public void write(byte[] bytes,int offset,int length)throws IOException{assertTrue(length>0);throw primary;}
                    @Override public void close(){closed[0]=true;}
                };
                if(source)try(var result=new DependencyAnalysis().prepareLeased(input.program,cfg,input.source,SourceCorrelationSnapshotTest.links(publication,evidence),input.pages,input.execution)) {
                    long scratch=input.execution.used(AnalysisResources.Pool.SCRATCH),resident=input.execution.used(AnalysisResources.Pool.RESIDENT);
                    assertSame(primary,assertThrows(IOException.class,()->new DependencyJson().writeSnapshot(result,input.program,input.pages,input.execution,stream)));
                    assertEquals(scratch,input.execution.used(AnalysisResources.Pool.SCRATCH));assertEquals(resident,input.execution.used(AnalysisResources.Pool.RESIDENT));assertTrue(result.programs().count()>0);
                }else try(var result=new DependencyAnalysis().prepareManaged(input.program,cfg,input.pages,input.execution)) {
                    long scratch=input.execution.used(AnalysisResources.Pool.SCRATCH),resident=input.execution.used(AnalysisResources.Pool.RESIDENT);
                    assertSame(primary,assertThrows(IOException.class,()->new DependencyJson().writeSnapshot(result,input.program,input.execution,stream)));
                    assertEquals(scratch,input.execution.used(AnalysisResources.Pool.SCRATCH));assertEquals(resident,input.execution.used(AnalysisResources.Pool.RESIDENT));assertEquals(1,result.result().sites().size());
                }
                assertFalse(closed[0]);assertTrue(input.execution.outputUsed()>0,"fund the attempted bytes before IO; do not refund failed IO");assertEquals(input.execution.outputUsed(),input.resources.outputUsed());
                assertTrue(input.source.unitCount()>0);assertEquals(publication.id(),input.program.publicationId());
            }
        }
    }

    @Test void sharedMeterUsesExactResourceIdentityAndRejectsInvalidRangesBeforeCharging()throws Exception {
        var limits=new AnalysisResources.Limits(1024,1024,0,0,0,100,100);
        var resources=new AnalysisResources(limits);var foreign=new AnalysisResources(limits);var bytes=new ByteArrayOutputStream();
        var stream=ManagedOutputStream.metered(bytes,resources);assertSame(stream,ManagedOutputStream.metered(stream,resources));
        assertThrows(IllegalArgumentException.class,()->ManagedOutputStream.metered(stream,foreign));
        assertThrows(IndexOutOfBoundsException.class,()->stream.write(new byte[3],2,2));assertEquals(0,resources.outputUsed());assertEquals(0,bytes.size());
        stream.write(new byte[]{1,2,3});stream.write(4);assertEquals(4,resources.outputUsed());assertEquals(0,foreign.outputUsed());assertArrayEquals(new byte[]{1,2,3,4},bytes.toByteArray());
    }
}
