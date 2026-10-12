package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.analysis.dependencies.source.*;
import io.github.gustavo2358.analysis.solver.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.EnumSet;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

final class SourceSnapshotJsonTest {
    @TempDir Path directory;
    private static AnalysisResources resources(){return new AnalysisResources(new AnalysisResources.Limits(131072,16384,0,64L*1024*1024,2,Long.MAX_VALUE,0));}
    private static void zero(AnalysisResources r){for(var pool:AnalysisResources.Pool.values())assertEquals(0,r.used(pool));}
    private static final String ID="{\"compilationUnitId\":\"synthetic\",\"structuralPath\":[0,1],\"canonicalProgramName\":\"PROGRAM\"}";
    private static String unit(String statements,String nominal){return "{\"unit\":"+ID+",\"controlAvailable\":true,\"statements\":"+statements+",\"occurrences\":[],\"targets\":[],\"nodes\":[],\"derivations\":[],\"selections\":[],\"events\":[],\"guards\":[],\"proofs\":[],\"frontiers\":[]"+nominal+"}";}
    private static String root(String unit){return "{\"schema\":\"qualified-source-dependencies\",\"version\":\"1.6.0\",\"producer\":\"synthetic\",\"source\":{\"schema\":\"cobol-semantic-product\",\"version\":\"2.67.0\",\"sha256\":\""+"0".repeat(64)+"\"},\"air\":[],\"units\":["+unit+"]}";}
    private static byte[] bytes(String value){return value.getBytes(StandardCharsets.UTF_8);}
    private static String statement(String coordinate){String location="{\"file\":\"path\\u0000\\ud800😀\\udc00.cbl\",\"startLine\":"+coordinate+",\"startColumn\":-0,\"endLine\":2147483647,\"endColumn\":-2147483648}";String include="{\"includingFile\":\"a\",\"requestedName\":\"b\",\"includedFile\":\"c\",\"includeLine\":17}";return "{\"id\":{\"unit\":"+ID+",\"handle\":\"s1\"},\"provenance\":{\"expanded\":"+location+",\"original\":"+location+",\"includeChain\":["+include+","+include+"],\"exact\":false}}";}
    @Test void physicalWireMapsCompleteMetadataAndDuplicateOrdinalsWithoutResidentModel() throws IOException {
        var r=resources();var input=new ByteArrayInputStream(bytes(root(unit("["+statement("1")+","+statement("1")+"]","")))){boolean closed;@Override public void close(){closed=true;}};
        try(var pages=new FilePageStore(directory,512,8,r,AnalysisResources.Phase.INDEX);var snapshot=new QualifiedSourceJson().decodeSnapshot(input,pages,r)) {
            assertFalse(input.closed);
            long u=snapshot.element(snapshot.field(snapshot.root(),SourceShape.ROOT,5),SourceShape.UNIT_EVIDENCE,0);
            assertEquals(0,snapshot.field(u,SourceShape.UNIT_EVIDENCE,12));assertEquals(0,snapshot.field(u,SourceShape.UNIT_EVIDENCE,13));
            long rows=snapshot.field(u,SourceShape.UNIT_EVIDENCE,2);assertEquals(2,snapshot.size(rows,SourceShape.STATEMENT));
            long first=snapshot.element(rows,SourceShape.STATEMENT,0);assertEquals(first,snapshot.element(rows,SourceShape.STATEMENT,1));
            long p=snapshot.field(first,SourceShape.STATEMENT,1),loc=snapshot.field(p,SourceShape.PROVENANCE,0);assertEquals(loc,snapshot.field(p,SourceShape.PROVENANCE,1));
            try(var projection=snapshot.project(snapshot.field(loc,SourceShape.LOCATION,0))){assertEquals("path\u0000\uD800😀\uDC00.cbl",projection.value());}
            assertEquals(1,snapshot.integer(snapshot.field(loc,SourceShape.LOCATION,1)));assertEquals(0,snapshot.integer(snapshot.field(loc,SourceShape.LOCATION,2)));assertEquals(Integer.MAX_VALUE,snapshot.integer(snapshot.field(loc,SourceShape.LOCATION,3)));assertEquals(Integer.MIN_VALUE,snapshot.integer(snapshot.field(loc,SourceShape.LOCATION,4)));assertFalse(snapshot.bool(snapshot.field(p,SourceShape.PROVENANCE,3)));
            long chain=snapshot.field(p,SourceShape.PROVENANCE,2);assertEquals(2,snapshot.size(chain,SourceShape.INCLUDE));assertEquals(snapshot.element(chain,SourceShape.INCLUDE,0),snapshot.element(chain,SourceShape.INCLUDE,1));
        }
        assertFalse(input.closed);zero(r);
    }
    @Test void closedMappingRejectsFractionExponentOverflowWrongShapeAndNull() {
        for(String coordinate:new String[]{"1.0","1e0","2147483648","-2147483649","null","true","\"1\""})reject(root(unit("["+statement(coordinate)+"]","")));
        String valid=root(unit("[]",""));
        reject(valid.replace("\"producer\":\"synthetic\"","\"unexpected\":\"synthetic\""));
        reject(valid.replace("\"producer\":\"synthetic\"","\"producer\":\"synthetic\",\"producer\":\"synthetic\""),io.github.gustavo2358.air.json.AirJsonException.class);
        reject(valid.replace("\"air\":[]","\"air\":null"));
        reject(valid.replace("\"frontiers\":[]", "\"frontiers\":[],\"nativeFiles\":null"));
    }
    private void reject(String wire) {
        reject(wire,IllegalArgumentException.class);
    }
    private void reject(String wire,Class<? extends Throwable> expected) {
        var r=resources();try(var pages=new FilePageStore(directory,512,8,r,AnalysisResources.Phase.INDEX)) {
            assertThrows(expected,()->new QualifiedSourceJson().decodeSnapshot(new ByteArrayInputStream(bytes(wire)),pages,r));
            assertEquals(0,pages.statistics().livePages());long borrowed=pages.allocate();pages.release(borrowed);
        }zero(r);
    }
    @Test void recursiveTermsUsePagedFramesAndRetainEveryLevel() throws IOException {
        int depth=2048;String term="{\"kind\":\"LITERAL\",\"value\":\"tail\"}";
        for(int i=0;i<depth;i++)term="{\"kind\":\"UPPER_ASCII\",\"value\":\"\",\"arguments\":["+term+"]}";
        String nominal=",\"nominalValues\":{\"facts\":{\"authority\":\"NOMINAL_TEXT_SOURCE_V3\",\"symbols\":[],\"assignments\":[{\"statement\":\"s\",\"target\":\"n\",\"source\":"+term+"}],\"conditions\":[],\"queries\":[]},\"declarations\":[],\"seeds\":[],\"branches\":[],\"uncertainties\":[]}";
        var r=resources();try(var pages=new FilePageStore(directory,512,8,r,AnalysisResources.Phase.INDEX);var s=new QualifiedSourceJson().decodeSnapshot(new ByteArrayInputStream(bytes(root(unit("[]",nominal)))),pages,r)) {
            long u=s.element(s.field(s.root(),SourceShape.ROOT,5),SourceShape.UNIT_EVIDENCE,0),n=s.field(u,SourceShape.UNIT_EVIDENCE,12),facts=s.field(n,SourceShape.NOMINAL_VALUE_EVIDENCE,0),assign=s.element(s.field(facts,SourceShape.NOMINAL_VALUES,2),SourceShape.ASSIGNMENT,0),t=s.field(assign,SourceShape.ASSIGNMENT,2);
            for(int i=0;i<depth;i++){assertTrue(s.matches(s.field(t,SourceShape.TERM,0),"UPPER_ASCII"));long arguments=s.field(t,SourceShape.TERM,2);assertEquals(1,s.size(arguments,SourceShape.TERM));t=s.element(arguments,SourceShape.TERM,0);}
            assertTrue(s.matches(s.field(t,SourceShape.TERM,1),"tail"));assertEquals(0,s.field(t,SourceShape.TERM,2));assertTrue(r.heapPeak()<131072);
        }zero(r);
    }
    @Test void existingLiteralWireFixturesKeepEveryFieldAndOccurrence() throws IOException {
        var mapper=new ObjectMapper();var seen=EnumSet.noneOf(SourceShape.class);int checked=0;
        try(var paths=Files.walk(Path.of("src/test/resources"))) {
            for(var path:paths.filter(p->p.toString().endsWith(".source.json")).sorted().toList()) {
                JsonNode expected=mapper.readTree(path.toFile());
                if(!expected.path("schema").asText().equals("qualified-source-dependencies"))continue;
                var r=resources();
                try(var pages=new FilePageStore(directory,4096,4,r,AnalysisResources.Phase.INDEX);
                    var input=Files.newInputStream(path);var s=new QualifiedSourceJson().decodeSnapshot(input,pages,r)) {
                    compare(expected,s,s.root(),SourceShape.ROOT,null,seen);checked++;
                }zero(r);
            }
        }
        // Independent literal supplement for shapes not present in the historical fixtures.
        String statement=statement("1"),marker="\"provenance\":";
        String provenance=statement.substring(statement.indexOf(marker)+marker.length(),statement.length()-1);
        String nominal=",\"nominalValues\":{\"facts\":{\"authority\":\"NOMINAL_TEXT_SOURCE_V4\",\"symbols\":[{\"node\":\"n\",\"extent\":4,\"modelAssumed\":false}],\"assignments\":[],\"conditions\":[],\"queries\":[],\"tableFields\":[{\"node\":\"n\",\"initial\":[{\"origin\":\"n\",\"value\":\"seed\"}]}]},\"declarations\":[{\"node\":\"n\",\"provenance\":"+provenance+"}],\"seeds\":[{\"node\":\"n\",\"value\":\"seed\",\"authority\":\"DECLARATIVE_VALUE\",\"provenance\":"+provenance+"}],\"branches\":[],\"uncertainties\":[{\"id\":\"missing\",\"kind\":\"MISSING_COPY\",\"provenance\":"+provenance+"}]}";
        String supplement=root(unit("["+statement("1")+"]",nominal));var r=resources();
        try(var pages=new FilePageStore(directory,4096,4,r,AnalysisResources.Phase.INDEX);
            var s=new QualifiedSourceJson().decodeSnapshot(new ByteArrayInputStream(bytes(supplement)),pages,r)) {
            compare(mapper.readTree(supplement),s,s.root(),SourceShape.ROOT,null,seen);
        }zero(r);
        assertTrue(checked>=20,"existing independent literal fixtures must be exercised");
        System.out.println("SOURCE_SNAPSHOT_LITERAL_FIXTURES historical="+checked+" supplement=1 shapes="+seen);
        assertEquals(EnumSet.allOf(SourceShape.class),seen,"every existing source shape needs a literal-wire witness");
    }
    private static void compare(JsonNode expected,SourceSnapshot s,long ref,SourceShape type,SourceShape element,EnumSet<SourceShape> seen) {
        assertEquals(type,s.shape(ref));seen.add(type);
        if(type==SourceShape.TEXT){assertTrue(expected.isTextual());try(var p=s.project(ref)){assertEquals(expected.textValue(),p.value());}return;}
        if(type==SourceShape.INTEGER){assertTrue(expected.isIntegralNumber());assertEquals(expected.intValue(),s.integer(ref));return;}
        if(type==SourceShape.BOOLEAN){assertTrue(expected.isBoolean());assertEquals(expected.booleanValue(),s.bool(ref));return;}
        if(type==SourceShape.LIST){assertTrue(expected.isArray());assertEquals(expected.size(),s.size(ref,element));for(int i=0;i<expected.size();i++)compare(expected.get(i),s,s.element(ref,element,i),element,null,seen);return;}
        assertTrue(expected.isObject());int present=0;
        for(int i=0;i<type.fieldCount();i++) {
            var field=type.field(i);var value=expected.get(field.name());long actual=s.field(ref,type,i);
            if(value==null){assertTrue(field.optional());assertEquals(0,actual);}
            else {present++;compare(value,s,actual,field.shape(),field.shape()==SourceShape.LIST?field.element():null,seen);}
        }
        assertEquals(expected.size(),present,"closed field names and counts");
    }
    @Test void interruptedBorrowedInputPreservesFailureAndReclaimsPartialOwners() {
        var r=resources();var primary=new IOException("synthetic input interruption");
        var input=new InputStream(){int calls;boolean closed;@Override public int read()throws IOException{if(calls++==0)return '{';throw primary;}@Override public void close(){closed=true;}};
        try(var pages=new FilePageStore(directory,512,8,r,AnalysisResources.Phase.INDEX)) {
            assertSame(primary,assertThrows(IOException.class,()->new QualifiedSourceJson().decodeSnapshot(input,pages,r)));assertFalse(input.closed);
            assertEquals(0,pages.statistics().livePages());long borrowed=pages.allocate();pages.release(borrowed);
        }zero(r);
    }
    @Test void deniedConstructionAndDecodeBudgetsNeverReturnPartialStorage() {
        for(long heap:new long[]{10000,14272,20000,32000,45000}) {
            var r=new AnalysisResources(new AnalysisResources.Limits(heap,16384,0,64L*1024*1024,2,Long.MAX_VALUE,0));
            try(var pages=new FilePageStore(directory,512,8,r,AnalysisResources.Phase.INDEX)) {
                var failure=assertThrows(AnalysisResources.Exhausted.class,()->new QualifiedSourceJson().decodeSnapshot(new ByteArrayInputStream(bytes(root(unit("[]","")))),pages,r));
                assertEquals(AnalysisResources.Resource.HEAP,failure.resource());assertEquals(0,pages.statistics().livePages());
            }zero(r);
        }
        for(var resource:new AnalysisResources.Resource[]{AnalysisResources.Resource.SCRATCH,AnalysisResources.Resource.WORK}) {
            var r=new AnalysisResources(new AnalysisResources.Limits(131072,resource==AnalysisResources.Resource.SCRATCH?0:16384,0,64L*1024*1024,2,resource==AnalysisResources.Resource.WORK?32:Long.MAX_VALUE,0));
            try(var pages=new FilePageStore(directory,512,8,r,AnalysisResources.Phase.INDEX)) {
                var failure=assertThrows(AnalysisResources.Exhausted.class,()->new QualifiedSourceJson().decodeSnapshot(new ByteArrayInputStream(bytes(root(unit("[]","")))),pages,r));
                assertEquals(resource,failure.resource());assertEquals(0,pages.statistics().livePages());
            }zero(r);
        }
    }
    @Test void eachPagePrimitiveFailureReclaimsOwnersWithoutClosingBorrowedBackend() {
        for(int operation=0;operation<3;operation++) {
            var r=resources();try(var pages=new FilePageStore(directory,512,8,r,AnalysisResources.Phase.INDEX)) {
                var primary=new PageStore.Failure(PageStore.Reason.IO,"synthetic source page interruption");var borrowed=new FaultPages(pages,operation,primary);
                assertSame(primary,assertThrows(PageStore.Failure.class,()->new QualifiedSourceJson().decodeSnapshot(new ByteArrayInputStream(bytes(root(unit("[]","")))),borrowed,r)));
                assertFalse(borrowed.closed);assertEquals(0,pages.statistics().livePages());long page=pages.allocate();pages.release(page);
            }zero(r);
        }
    }
    private static final class FaultPages implements PageStore {
        private final PageStore delegate;private final int operation;private final Failure primary;private boolean fired,closed;
        FaultPages(PageStore delegate,int operation,Failure primary){this.delegate=delegate;this.operation=operation;this.primary=primary;}
        private void fail(int candidate){if(candidate==operation&&!fired){fired=true;throw primary;}}
        @Override public int pageBytes(){return delegate.pageBytes();}
        @Override public long allocate(){fail(0);return delegate.allocate();}
        @Override public void read(long p,int offset,byte[] bytes,int at,int count){fail(1);delegate.read(p,offset,bytes,at,count);}
        @Override public void write(long p,int offset,byte[] bytes,int at,int count){fail(2);delegate.write(p,offset,bytes,at,count);}
        @Override public void release(long p){delegate.release(p);}
        @Override public void readForCleanup(long p,int offset,byte[] bytes,int at,int count){delegate.readForCleanup(p,offset,bytes,at,count);}
        @Override public void releaseForCleanup(long p){delegate.releaseForCleanup(p);}
        @Override public void flush(){delegate.flush();}
        @Override public Statistics statistics(){return delegate.statistics();}
        @Override public void close(){closed=true;delegate.close();}
    }
}
