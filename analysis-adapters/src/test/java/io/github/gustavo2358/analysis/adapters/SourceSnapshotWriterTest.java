package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.analysis.dependencies.*;
import io.github.gustavo2358.analysis.dependencies.source.*;
import io.github.gustavo2358.analysis.solver.*;
import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

final class SourceSnapshotWriterTest {
    @TempDir Path directory;
    private static final ObjectMapper JSON=new ObjectMapper();
    private static final QualifiedSourceJson CODEC=new QualifiedSourceJson();
    private static byte[] literal()throws Exception{return Files.readAllBytes(Path.of("src/test/resources/qualified-source-r9/ordinary.source.json"));}
    private static AnalysisResources resources(){return new AnalysisResources(new AnalysisResources.Limits(1048576,16384,0,128L*1024*1024,2,Long.MAX_VALUE,16L*1024*1024));}
    private static void zero(AnalysisResources resources){for(var pool:AnalysisResources.Pool.values())assertEquals(0,resources.used(pool));}
    private static SourceAdmissionSnapshot admit(byte[] wire,PageStore pages,AnalysisResources resources)throws Exception {
        return SourceAdmissionSnapshot.admit(CODEC.decodeSnapshot(new ByteArrayInputStream(wire),pages,resources),pages,resources);
    }
    private static void shapes(SourceSnapshot source,long ref,SourceShape shape,SourceShape element,EnumSet<SourceShape> seen) {
        seen.add(shape);
        if(shape==SourceShape.LIST){for(long i=0,n=source.size(ref,element);i<n;i++)shapes(source,source.element(ref,element,i),element,null,seen);}
        else if(shape.record())for(int i=0;i<shape.fieldCount();i++) {
            long child=source.field(ref,shape,i);var field=shape.field(i);
            if(child!=0)shapes(source,child,field.shape(),field.shape()==SourceShape.LIST?field.element():null,seen);
        }
    }
    private void canonical(byte[] wire,EnumSet<SourceShape> seen)throws Exception {
        var expected=JSON.readTree(CODEC.encode(CODEC.decode(wire)));var resources=resources();
        try(var pages=new FilePageStore(directory,4096,4,resources,AnalysisResources.Phase.ENCODE);
            var admitted=admit(wire,pages,resources)) {
            shapes(admitted.snapshot(),admitted.snapshot().root(),SourceShape.ROOT,null,seen);
            long before=pages.statistics().livePages(),heap=resources.heapUsed();var first=new ByteArrayOutputStream();var second=new ByteArrayOutputStream();
            CODEC.writeSnapshot(admitted,first,pages,resources);CODEC.writeSnapshot(admitted,second,pages,resources);
            assertEquals(expected,JSON.readTree(first.toByteArray()));assertArrayEquals(first.toByteArray(),second.toByteArray());
            assertEquals(before,pages.statistics().livePages());assertEquals(heap,resources.heapUsed());assertTrue(admitted.unitCount()>0);
        }zero(resources);
    }
    private static ObjectNode supplement()throws Exception {
        var root=(ObjectNode)JSON.readTree(literal());root.put("version","1.6.0");var unit=(ObjectNode)root.path("units").get(0);
        var origin=(ObjectNode)unit.path("statements").get(0).path("provenance").deepCopy();
        var include=JSON.createObjectNode().put("includingFile","outer.cbl").put("requestedName","COPY-NAME").put("includedFile","inner.cpy").put("includeLine",Integer.MAX_VALUE);
        origin.set("includeChain",JSON.createArrayNode().add(include).add(include.deepCopy()));
        var facts=JSON.createObjectNode();facts.put("authority","NOMINAL_TEXT_SOURCE_V4");
        facts.set("symbols",JSON.createArrayNode().add(JSON.createObjectNode().put("node","n").put("extent",4).put("modelAssumed",false)));
        facts.set("assignments",JSON.createArrayNode());facts.set("conditions",JSON.createArrayNode());facts.set("queries",JSON.createArrayNode());
        var table=JSON.createObjectNode().put("node","n");table.set("initial",JSON.createArrayNode().add(JSON.createObjectNode().put("origin","n").put("value","seed")));facts.set("tableFields",JSON.createArrayNode().add(table));
        var nominal=JSON.createObjectNode();nominal.set("facts",facts);
        var declaration=JSON.createObjectNode().put("node","n");declaration.set("provenance",origin.deepCopy());nominal.set("declarations",JSON.createArrayNode().add(declaration));
        var seed=JSON.createObjectNode().put("node","n").put("value","seed").put("authority","DECLARATIVE_VALUE");seed.set("provenance",origin.deepCopy());nominal.set("seeds",JSON.createArrayNode().add(seed));
        var uncertainty=JSON.createObjectNode().put("id","independent-missing").put("kind","MISSING_COPY");uncertainty.set("provenance",origin.deepCopy());nominal.set("uncertainties",JSON.createArrayNode().add(uncertainty));nominal.set("branches",JSON.createArrayNode());unit.set("nominalValues",nominal);
        return root;
    }
    @Test void allHistoricalLiteralsAndSupplementKeepCanonicalValuesAndEveryShape()throws Exception {
        int count=0;var seen=EnumSet.noneOf(SourceShape.class);
        try(var paths=Files.walk(Path.of("src/test/resources"))) {
            for(var path:paths.filter(p->p.toString().endsWith(".source.json")).sorted().toList()) {
                byte[] wire=Files.readAllBytes(path);if(!JSON.readTree(wire).path("schema").asText().equals("qualified-source-dependencies"))continue;
                canonical(wire,seen);count++;
            }
        }
        canonical(JSON.writeValueAsBytes(supplement()),seen);assertEquals(53,count);assertEquals(EnumSet.allOf(SourceShape.class),seen);
        System.out.println("SOURCE_MANAGED_CANONICAL_WIRE literals="+count+" supplement=1 shapes="+seen+" CLI=NOT_RUN");
    }
    @Test void sourceUtf16KeepsCrossBlockPairsIsolatedUnitsEscapesAndEmptyDefaults()throws Exception {
        var root=supplement();String producer="p"+"x".repeat(254)+"😀"+"\uD800a\uDC00\u0000\n\t\\\"é";root.put("producer",producer);
        var unit=(ObjectNode)root.path("units").get(0);unit.set("nativeFiles",JSON.createArrayNode());
        for(var node:unit.path("nodes"))((ObjectNode)node.path("support")).set("conditions",JSON.createArrayNode());
        byte[] wire=JSON.writeValueAsBytes(root);canonical(wire,EnumSet.noneOf(SourceShape.class));
        var resources=resources();
        try(var pages=new FilePageStore(directory,4096,4,resources,AnalysisResources.Phase.ENCODE);var admitted=admit(wire,pages,resources)) {
            var output=new ByteArrayOutputStream();CODEC.writeSnapshot(admitted,output,pages,resources);
            assertEquals(producer,JSON.readTree(output.toByteArray()).path("producer").asText());
            String encoded=output.toString(StandardCharsets.UTF_8);assertTrue(encoded.contains("\\ud800"));assertTrue(encoded.contains("\\udc00"));assertFalse(encoded.contains("\"nativeFiles\""));
        }zero(resources);
    }
    @Test void twoThousandNestedTermsUsePagedFramesWithoutARecursiveWriter()throws Exception {
        int depth=2048;var root=supplement();var unit=(ObjectNode)root.path("units").get(0);var facts=(ObjectNode)unit.path("nominalValues").path("facts");
        var assignment=JSON.createObjectNode().put("statement",unit.path("statements").get(0).path("id").path("handle").asText()).put("target","n").put("source","MANAGED_DEEP_TERM");facts.set("assignments",JSON.createArrayNode().add(assignment));
        String term="{\"kind\":\"LITERAL\",\"value\":\"tail\"}";
        for(int i=0;i<depth;i++)term="{\"kind\":\"UPPER_ASCII\",\"value\":\"\",\"arguments\":["+term+"]}";
        byte[] wire=JSON.writeValueAsString(root).replace("\"MANAGED_DEEP_TERM\"",term).getBytes(StandardCharsets.UTF_8);var resources=resources();
        try(var pages=new FilePageStore(directory,4096,4,resources,AnalysisResources.Phase.ENCODE);var admitted=admit(wire,pages,resources)) {
            long before=pages.statistics().livePages();var output=new ByteArrayOutputStream();CODEC.writeSnapshot(admitted,output,pages,resources);
            var factory=JsonFactory.builder().streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(10000).build()).build();int operators=0,tails=0;
            try(var parser=factory.createParser(output.toByteArray())) {
                while(parser.nextToken()!=null)if(parser.currentToken()==JsonToken.VALUE_STRING) {if(parser.getText().equals("UPPER_ASCII"))operators++;if(parser.getText().equals("tail"))tails++;}
            }
            assertEquals(depth,operators);assertEquals(1,tails);assertEquals(before,pages.statistics().livePages());assertTrue(admitted.unitCount()>0);
        }zero(resources);
    }
    @Test void foreignContextAndPreAllocationDenialNeverTouchBorrowedOutput()throws Exception {
        var resources=resources();var foreign=resources();
        try(var pages=new FilePageStore(directory,4096,4,resources,AnalysisResources.Phase.ENCODE);
            var other=new FilePageStore(directory,4096,4,resources,AnalysisResources.Phase.ENCODE);var admitted=admit(literal(),pages,resources)) {
            var output=new ByteArrayOutputStream();long before=pages.statistics().livePages();
            assertThrows(IllegalArgumentException.class,()->CODEC.writeSnapshot(admitted,output,other,resources));
            assertThrows(IllegalArgumentException.class,()->CODEC.writeSnapshot(admitted,output,pages,foreign));
            try(var pressure=resources.reserve(AnalysisResources.Pool.RESIDENT,resources.limits().heapBytes()-resources.heapUsed()-65536-1023,AnalysisResources.Phase.ENCODE)) {
                assertNotNull(pressure);var failure=assertThrows(AnalysisResources.Exhausted.class,()->CODEC.writeSnapshot(admitted,output,pages,resources));
                assertEquals(AnalysisResources.Resource.HEAP,failure.resource());assertEquals(AnalysisResources.Phase.ENCODE,failure.phase());
            }
            assertEquals(0,output.size());assertEquals(before,pages.statistics().livePages());assertTrue(admitted.unitCount()>0);
        }zero(resources);zero(foreign);
    }
    @Test void borrowedOutputFailureKeepsPrimaryAndRetiresOnlyWriterResources()throws Exception {
        var resources=resources();var primary=new IOException("independent source wire output interruption");
        try(var pages=new FilePageStore(directory,4096,4,resources,AnalysisResources.Phase.ENCODE);var admitted=admit(literal(),pages,resources)) {
            long before=pages.statistics().livePages(),heap=resources.heapUsed();
            var output=new OutputStream(){boolean closed;@Override public void write(int value)throws IOException{throw primary;}@Override public void write(byte[] values,int at,int count)throws IOException{throw primary;}@Override public void close(){closed=true;}};
            assertSame(primary,assertThrows(IOException.class,()->CODEC.writeSnapshot(admitted,output,pages,resources)));
            assertFalse(output.closed);assertEquals(before,pages.statistics().livePages());assertEquals(heap,resources.heapUsed());assertTrue(admitted.unitCount()>0);
        }zero(resources);
    }
    @Test void parentExpiryDuringFlushRetiresFramesBeforeQueryAndCannotReturnSuccess()throws Exception {
        var resources=resources();
        try(var pages=new FilePageStore(directory,4096,4,resources,AnalysisResources.Phase.ENCODE);var admitted=admit(literal(),pages,resources)) {
            var output=new OutputStream(){boolean closed,fired;@Override public void write(int value){expire();}@Override public void write(byte[] values,int at,int count){expire();}private void expire(){if(fired)return;fired=true;admitted.snapshot().close();assertEquals(0,pages.statistics().livePages());}@Override public void close(){closed=true;}};
            assertThrows(IllegalStateException.class,()->CODEC.writeSnapshot(admitted,output,pages,resources));assertTrue(output.fired);assertFalse(output.closed);assertEquals(0,pages.statistics().livePages());
            assertThrows(IllegalStateException.class,admitted::snapshot);long borrowed=pages.allocate();pages.release(borrowed);
        }zero(resources);
    }
    @Test void outputQuotaIsEnforcedBeforeBorrowedBytesAndDoesNotExpireAdmission()throws Exception {
        var resources=new AnalysisResources(new AnalysisResources.Limits(1048576,16384,0,128L*1024*1024,2,Long.MAX_VALUE,0));
        try(var pages=new FilePageStore(directory,4096,4,resources,AnalysisResources.Phase.ENCODE);var admitted=admit(literal(),pages,resources)) {
            long before=pages.statistics().livePages(),heap=resources.heapUsed();var output=new ByteArrayOutputStream();
            var failure=assertThrows(AnalysisResources.Exhausted.class,()->CODEC.writeSnapshot(admitted,output,pages,resources));
            assertEquals(AnalysisResources.Resource.OUTPUT,failure.resource());assertEquals(AnalysisResources.Phase.ENCODE,failure.phase());assertEquals(0,output.size());
            assertEquals(before,pages.statistics().livePages());assertEquals(heap,resources.heapUsed());assertTrue(admitted.unitCount()>0);
        }zero(resources);
    }
}
