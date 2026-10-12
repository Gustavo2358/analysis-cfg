package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.analysis.dependencies.*;
import io.github.gustavo2358.analysis.dependencies.source.*;
import io.github.gustavo2358.analysis.solver.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

final class SourceSnapshotInputTest {
    @TempDir Path directory;
    private static final ObjectMapper JSON=new ObjectMapper();
    private static final QualifiedSourceJson CODEC=new QualifiedSourceJson();
    private static String digest(byte[] value)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));}
    private SourceCorrelationSnapshotTest fixtures(){var value=new SourceCorrelationSnapshotTest();value.directory=directory;return value;}
    @Test void typedInputBindsWireDocumentAirAndPublicationWithoutResidentSourceProjection()throws Exception {
        var publication=SourceCorrelationSnapshotTest.program(1);var source=SourceProgramSnapshotTest.source(publication,"source-possibility/unknown-copy-kill.source.json");byte[] wire=CODEC.encode(source);Path path=directory.resolve("source.json");Files.write(path,wire);var fixture=fixtures();
        // This case publishes bytes, unlike the read-only/denial cases below.
        // Declare output capacity explicitly; the native view must charge its root.
        try(var input=fixture.new NativeCase(publication,source,false,1_000_000)) {
            long baseline=input.pages.statistics().livePages();
            try(var admitted=SourceSnapshotInput.read(path,input.program,input.pages,input.execution,"0".repeat(64),Optional.of(digest(wire)),Optional.of(source.source().sha256()))) {
                assertEquals(source.units().size(),admitted.unitCount());var bytes=new ByteArrayOutputStream();CODEC.writeSnapshot(admitted,bytes,input.pages,input.execution);assertEquals(JSON.readTree(wire),JSON.readTree(bytes.toByteArray()));assertEquals(publication.id(),input.program.publicationId());
                assertEquals(bytes.size(),input.execution.outputUsed());assertEquals(bytes.size(),input.resources.outputUsed());
            }assertEquals(baseline,input.pages.statistics().livePages());assertTrue(input.source.unitCount()>0);
        }
    }
    @Test void everyIdentityAndHashContradictionRejectsBeforeAdmissionAndRetiresStagedPages()throws Exception {
        var publication=SourceCorrelationSnapshotTest.program(1);var source=SourceProgramSnapshotTest.source(publication,"source-possibility/unknown-copy-kill.source.json");byte[] wire=CODEC.encode(source);Path path=directory.resolve("source.json");var fixture=fixtures();
        try(var input=fixture.new NativeCase(publication,source)) {
            long baseline=input.pages.statistics().livePages(),resident=input.execution.heapUsed();
            for(int mutation=0;mutation<6;mutation++) {
                var tree=(ObjectNode)JSON.readTree(wire);if(mutation==3)((ObjectNode)tree.path("air").get(0)).put("publication","foreign");if(mutation==4)((ObjectNode)tree.path("air").get(0)).put("sha256","f".repeat(64));if(mutation==5)((ObjectNode)tree.path("units").get(0)).put("controlAvailable",false);
                byte[] changed=JSON.writeValueAsBytes(tree);Files.write(path,changed);String expectedWire=mutation==0?"wrong":digest(changed),expectedDoc=mutation==1?"wrong":source.source().sha256(),expectedAir=mutation==2?"f".repeat(64):"0".repeat(64);
                assertThrows(IllegalArgumentException.class,()->SourceSnapshotInput.read(path,input.program,input.pages,input.execution,expectedAir,Optional.of(expectedWire),Optional.of(expectedDoc)),"mutation="+mutation);assertEquals(baseline,input.pages.statistics().livePages());assertEquals(resident,input.execution.heapUsed());assertTrue(input.source.unitCount()>0);
            }
        }
    }
    @Test void nativeBundleDefersOneSourceAdmissionToTheExactProgramAndExpiresWithItsOwner()throws Exception {
        var fixture=Path.of("../analysis-launcher/src/test/resources/dependency-input-v1/dependency-input.json");var resources=new AnalysisResources(new AnalysisResources.Limits(64L*1024*1024,16L*1024*1024,0,512L*1024*1024,8,Long.MAX_VALUE,32L*1024*1024));
        try(var input=new DependencyInputJson().readSnapshot(fixture,new DataflowAirReader(),resources)) {
            assertTrue(input.hasSource());var read=input.air();SourceAdmissionSnapshot source;
            try(var program=new SnapshotProgram(read.checked(),read.newIdentityStorage(),read.newOrderStorage(),resources)) {
                var execution=AnalysisResources.executionFor(program);source=input.source(program,execution).orElseThrow();long pages=read.borrowedPages().statistics().livePages();assertSame(source,input.source(program,execution).orElseThrow());assertEquals(pages,read.borrowedPages().statistics().livePages());assertTrue(source.unitCount()>0);
                var foreign=new AnalysisResources(resources.limits());assertThrows(IllegalArgumentException.class,()->input.source(program,foreign));for(var pool:AnalysisResources.Pool.values())assertEquals(0,foreign.used(pool));
            }assertThrows(IllegalStateException.class,source::snapshot);
        }for(var pool:AnalysisResources.Pool.values())assertEquals(0,resources.used(pool));
    }
    @Test void nativeDecodeAndValidationExhaustionKeepProductivePhaseAndAuthenticBudgetIdentity()throws Exception {
        var publication=SourceCorrelationSnapshotTest.program(1);var evidence=SourceProgramSnapshotTest.source(publication,"source-possibility/unknown-copy-kill.source.json");var fixture=fixtures();
        try(var input=fixture.new NativeCase(publication,evidence)) {
            var foreign=new AnalysisResources(input.resources.limits());assertThrows(IllegalArgumentException.class,()->foreign.requireExecutionProgram(input.program));input.execution.requireExecutionProgram(input.program);
            try(var pressure=input.resources.reserve(AnalysisResources.Pool.RESIDENT,input.resources.limits().heapBytes()-input.resources.heapUsed(),AnalysisResources.Phase.DOMAIN)) {
                assertNotNull(pressure);for(var phase:List.of(AnalysisResources.Phase.DECODE,AnalysisResources.Phase.VALIDATION)) {
                    var failure=assertThrows(AnalysisResources.Exhausted.class,()->input.execution.reserve(AnalysisResources.Pool.RESIDENT,1,phase));assertEquals(AnalysisResources.Resource.HEAP,failure.resource());assertEquals(phase,failure.phase());
                }
            }assertTrue(input.source.unitCount()>0);assertEquals(publication.id(),input.program.publicationId());
        }
    }
}
