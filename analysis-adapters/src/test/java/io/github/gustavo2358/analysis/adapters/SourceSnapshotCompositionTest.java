package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.analysis.dependencies.*;
import io.github.gustavo2358.analysis.dependencies.source.*;
import io.github.gustavo2358.analysis.solver.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

final class SourceSnapshotCompositionTest {
    @TempDir Path directory;
    private static final ObjectMapper JSON=new ObjectMapper();
    private static final QualifiedSourceJson CODEC=new QualifiedSourceJson();
    private static byte[] literal()throws Exception{return Files.readAllBytes(Path.of("src/test/resources/source-possibility/unknown-native.source.json"));}
    private static AnalysisResources resources(){return resources(32L*1024*1024);}
    private static AnalysisResources resources(long output){return new AnalysisResources(new AnalysisResources.Limits(1048576,16384,0,256L*1024*1024,4,Long.MAX_VALUE,output));}
    private static void zero(AnalysisResources resources){for(var pool:AnalysisResources.Pool.values())assertEquals(0,resources.used(pool));}
    private static void expire(SourceQualifiedSnapshot result){result.close();}
    private static SourceAdmissionSnapshot admit(byte[] wire,PageStore pages,AnalysisResources resources)throws Exception{return SourceAdmissionSnapshot.admit(CODEC.decodeSnapshot(new ByteArrayInputStream(wire),pages,resources),pages,resources);}
    /** Literal resident oracle, independent of the paged emitter's field recipes. */
    private static JsonNode expected(byte[] wire)throws Exception {
        var resident=SourceQualifiedDependencyResult.admit(CODEC.decode(wire));var root=JSON.createObjectNode();
        var qualified=root.putObject("sourceQualifiedDependencies");qualified.put("analysisBoundary","NON_EXECUTABLE_SOURCE");qualified.set("evidence",JSON.readTree(CODEC.encode(resident.evidence())));
        var occurrences=qualified.putArray("occurrences");
        for(var item:resident.occurrences()) {
            var out=occurrences.addObject();out.set("occurrence",JSON.valueToTree(QualifiedSourceJson.value(item.occurrence())));out.put("status",item.status().name());out.put("valueRemainder",item.valueRemainder());out.put("interpretationRemainder",item.interpretationRemainder());
            var candidates=out.putArray("candidates");for(var candidate:item.candidates()) {
                var c=candidates.addObject();c.put("referenceName",candidate.referenceName());c.put("rawValue",candidate.rawValue());c.set("occurrence",JSON.valueToTree(QualifiedSourceJson.value(candidate.occurrence())));c.set("qualifications",JSON.valueToTree(candidate.qualifications()));
            }
        }
        var files=root.putObject("dependencies").putArray("files");
        for(var item:resident.nativeFiles()) {
            var out=files.addObject();out.put("analysisBoundary","NON_EXECUTABLE_SOURCE");out.set("source",JSON.valueToTree(QualifiedSourceJson.value(item.source())));out.put("status",item.status().name());out.put("remainder",item.remainder());
            var candidates=out.putArray("candidates");for(var candidate:item.candidates()) {
                var c=candidates.addObject();c.put("referenceName",candidate.referenceName());c.put("rawValue",candidate.rawValue());c.set("qualifications",JSON.valueToTree(candidate.qualifications()));
            }
        }
        return root;
    }
    private static final class Meter extends OutputStream {
        private final OutputStream borrowed;private final AnalysisResources resources;long bytes;boolean closed;
        Meter(OutputStream borrowed,AnalysisResources resources){this.borrowed=borrowed;this.resources=resources;}
        @Override public void write(int value)throws IOException{resources.output(1,AnalysisResources.Phase.ENCODE);borrowed.write(value);bytes++;}
        @Override public void write(byte[] values,int at,int count)throws IOException{resources.output(count,AnalysisResources.Phase.ENCODE);borrowed.write(values,at,count);bytes+=count;}
        @Override public void flush()throws IOException{borrowed.flush();}
        @Override public void close(){closed=true;}
    }
    private static void composed(SourceAdmissionSnapshot admission,SourceQualifiedSnapshot result,PageStore pages,AnalysisResources resources,JsonOutput output)throws Exception {
        try(var writer=SourceSnapshotWriter.open(admission,output,pages,resources)) {
            output.value(new JsonOutput.Fields("sourceQualifiedDependencies",writer.qualified(result),"dependencies",new JsonOutput.Fields("files",writer.nativeFiles(result))));
            output.finish();writer.verify();
        }
    }
    @Test void all53LiteralsComposeCompleteEvidenceOccurrencesAndFilesInOneRootBuffer()throws Exception {
        int count=0;
        try(var paths=Files.walk(Path.of("src/test/resources"))) {
            for(var path:paths.filter(p->p.toString().endsWith(".source.json")).sorted().toList()) {
                byte[] wire=Files.readAllBytes(path);if(!JSON.readTree(wire).path("schema").asText().equals("qualified-source-dependencies"))continue;
                var resources=resources();
                try(var pages=new FilePageStore(directory,4096,4,resources,AnalysisResources.Phase.ENCODE);var admission=admit(wire,pages,resources);var result=SourceQualifiedSnapshot.prepare(admission,pages,resources);
                    var buffer=resources.reserve(AnalysisResources.Pool.RESIDENT,65536,AnalysisResources.Phase.ENCODE)) {
                    assertNotNull(buffer);long before=pages.statistics().livePages(),heap=resources.heapUsed();var bytes=new ByteArrayOutputStream();var meter=new Meter(bytes,resources);
                    composed(admission,result,pages,resources,new JsonOutput(meter));assertEquals(expected(wire),JSON.readTree(bytes.toByteArray()));assertEquals(bytes.size(),meter.bytes);assertFalse(meter.closed);
                    assertEquals(before,pages.statistics().livePages());assertEquals(heap,resources.heapUsed());assertTrue(result.unitCount()>0);bytes.write(' ');
                }zero(resources);count++;
            }
        }assertEquals(53,count);
    }
    @Test void sharedEmitterNeverChargesTheExistingRootMeterTwice()throws Exception {
        byte[] wire=literal();var expected=expected(wire);long size=JSON.writeValueAsBytes(expected).length+1L;var resources=resources(size);
        try(var pages=new FilePageStore(directory,4096,4,resources,AnalysisResources.Phase.ENCODE);var admission=admit(wire,pages,resources);var result=SourceQualifiedSnapshot.prepare(admission,pages,resources)) {
            var bytes=new ByteArrayOutputStream();var meter=new Meter(bytes,resources);composed(admission,result,pages,resources,new JsonOutput(meter));assertEquals(size,meter.bytes);assertEquals(expected,JSON.readTree(bytes.toByteArray()));
        }zero(resources);
    }
    @Test void sharedRootQuotaDenialPreservesBorrowedSourceAndPrimaryFailure()throws Exception {
        var resources=resources(0);
        try(var pages=new FilePageStore(directory,4096,4,resources,AnalysisResources.Phase.ENCODE);var admission=admit(literal(),pages,resources);var result=SourceQualifiedSnapshot.prepare(admission,pages,resources)) {
            long before=pages.statistics().livePages(),heap=resources.heapUsed();var bytes=new ByteArrayOutputStream();var meter=new Meter(bytes,resources);
            var failure=assertThrows(AnalysisResources.Exhausted.class,()->composed(admission,result,pages,resources,new JsonOutput(meter)));
            assertEquals(AnalysisResources.Resource.OUTPUT,failure.resource());assertEquals(0,bytes.size());assertFalse(meter.closed);assertEquals(before,pages.statistics().livePages());assertEquals(heap,resources.heapUsed());assertTrue(result.unitCount()>0);
        }zero(resources);
    }
    @Test void qualifiedResultCannotBeCombinedWithAnotherLegitimateAdmission()throws Exception {
        var resources=resources();
        try(var pages=new FilePageStore(directory,4096,4,resources,AnalysisResources.Phase.ENCODE);var admission=admit(literal(),pages,resources);var foreign=admit(literal(),pages,resources);var result=SourceQualifiedSnapshot.prepare(foreign,pages,resources)) {
            var bytes=new ByteArrayOutputStream();var output=new JsonOutput(bytes);long before=pages.statistics().livePages();
            try(var writer=SourceSnapshotWriter.open(admission,output,pages,resources)) {
                assertThrows(IllegalArgumentException.class,()->writer.qualified(result));assertThrows(IllegalArgumentException.class,()->writer.nativeFiles(result));
            }assertEquals(0,bytes.size());assertEquals(before,pages.statistics().livePages());assertTrue(admission.unitCount()>0);assertTrue(result.unitCount()>0);
        }zero(resources);
    }
    @Test void resultExpiryDuringFinalRootFlushCannotCertifySuccess()throws Exception {
        var resources=resources();
        try(var pages=new FilePageStore(directory,4096,4,resources,AnalysisResources.Phase.ENCODE);var admission=admit(literal(),pages,resources);var result=SourceQualifiedSnapshot.prepare(admission,pages,resources)) {
            var output=new OutputStream(){boolean fired;@Override public void write(int value){retire();}@Override public void write(byte[] bytes,int at,int count){retire();}private void retire(){if(!fired){fired=true;expire(result);}}};
            assertThrows(IllegalStateException.class,()->composed(admission,result,pages,resources,new JsonOutput(output)));assertTrue(output.fired);assertTrue(admission.unitCount()>0);assertThrows(IllegalStateException.class,result::unitCount);
        }zero(resources);
    }
    @Test void nativeUtf16AndProgramTrimmedPrefixesNeverMaterializeResidentNames()throws Exception {
        var root=(ObjectNode)JSON.readTree(literal());((ObjectNode)root.path("units").get(0).path("nativeFiles").get(0).path("names").get(0)).put("rawValue","x".repeat(16383)+"\uD800\u0000\uDC00😀");
        byte[] wire=JSON.writeValueAsBytes(root);var resources=resources();
        try(var pages=new FilePageStore(directory,4096,4,resources,AnalysisResources.Phase.ENCODE);var admission=admit(wire,pages,resources);var result=SourceQualifiedSnapshot.prepare(admission,pages,resources)) {
            var bytes=new ByteArrayOutputStream();composed(admission,result,pages,resources,new JsonOutput(new Meter(bytes,resources)));assertEquals(expected(wire),JSON.readTree(bytes.toByteArray()));
        }zero(resources);
        byte[] original=Files.readAllBytes(Path.of("src/test/resources/qualified-source-r9/ordinary.source.json"));var source=(ObjectNode)JSON.readTree(original);boolean seen=false;
        for(var unit:source.path("units"))for(var o:unit.path("occurrences"))if(o.path("namespace").asText().equals("PROGRAM")&&o.path("targetKind").asText().equals("LITERAL")&&o.path("qualifications").size()>0&&unit.path("controlAvailable").asBoolean()) {
            var occurrence=(ObjectNode)o;occurrence.put("technology","CICS").put("command","LINK").put("nameProfile",CicsNameInterpreter.PROFILE);String raw="CALL"+" ".repeat(8192);((ObjectNode)occurrence.path("values").get(0)).put("value",raw).put("logicalExtent",raw.length());seen=true;
        }
        assertTrue(seen);wire=JSON.writeValueAsBytes(source);resources=resources();
        try(var pages=new FilePageStore(directory,4096,4,resources,AnalysisResources.Phase.ENCODE);var admission=admit(wire,pages,resources);var result=SourceQualifiedSnapshot.prepare(admission,pages,resources)) {
            var bytes=new ByteArrayOutputStream();composed(admission,result,pages,resources,new JsonOutput(new Meter(bytes,resources)));var emitted=JSON.readTree(bytes.toByteArray());assertEquals(expected(wire),emitted);assertEquals("CALL",emitted.path("sourceQualifiedDependencies").path("occurrences").get(0).path("candidates").get(0).path("referenceName").asText());
        }zero(resources);
    }
}
