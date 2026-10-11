package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.analysis.dependencies.*;
import io.github.gustavo2358.analysis.solver.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Authored before implementation; full wire/identity/lifetime assertions. */
final class ManifestCorrelationSnapshotTest {
    @TempDir Path directory;
    private static final ObjectMapper JSON=new ObjectMapper();
    private static final Path FIXTURE=Path.of("../analysis-launcher/src/test/resources/dependency-input-v1/dependency-input.json");
    private static AnalysisResources resources(){return new AnalysisResources(new AnalysisResources.Limits(64L*1024*1024,16L*1024*1024,0,512L*1024*1024,8,Long.MAX_VALUE,32L*1024*1024));}
    private static void zero(AnalysisResources resources){for(var pool:AnalysisResources.Pool.values())assertEquals(0,resources.used(pool),pool.toString());}
    private static void closeInput(DependencyInputJson.NativeInput input){input.close();}
    private ObjectNode manifest()throws Exception {
        var root=(ObjectNode)JSON.readTree(Files.readAllBytes(FIXTURE));
        for(String name:List.of("air","qualifiedSource")){var value=(ObjectNode)root.path(name);value.put("path",FIXTURE.toAbsolutePath().getParent().resolve(value.path("path").textValue()).toString());}
        return root;
    }
    private Path write(JsonNode root)throws Exception{Path file=directory.resolve("input-"+UUID.randomUUID()+".json");Files.write(file,JSON.writeValueAsBytes(root));return file;}
    private static ObjectNode at(ObjectNode root,String pointer){return (ObjectNode)root.at(pointer);}
    private static String chars(StatementCorrelations port,long row,StatementCorrelations.Field field){
        long count=port.characterCount(row,field);assertTrue(count<=Integer.MAX_VALUE);char[] value=new char[(int)count];
        for(int offset=0;offset<value.length;){int n=Math.min(17,value.length-offset);port.readCharacters(row,field,offset,value,offset,n);offset+=n;}
        return new String(value);
    }
    @Test void everyTypedIdentityPathOrdinalAndCanonicalMappingMatchesIndependentResidentInput()throws Exception {
        var root=manifest();var path=write(root);var expected=new DependencyInputJson().read(path,new DataflowAirReader());var link=expected.correlations().getFirst();var resources=resources();
        try(var input=new DependencyInputJson().readSnapshot(path,new DataflowAirReader(),resources)){
            var read=input.air();try(var program=new SnapshotProgram(read.checked(),read.newIdentityStorage(),read.newOrderStorage(),resources)){
                var execution=AnalysisResources.executionFor(program);var port=input.correlations(program,execution);assertSame(program,port.program());assertEquals(1,port.size());
                var fields=StatementCorrelations.Field.values();var texts=List.of(link.source().unit().compilationUnitId(),link.source().unit().canonicalProgramName(),link.source().handle(),link.operation().unit().publication().localId(),link.operation().unit().localId(),link.operation().localId(),link.label().unit().publication().localId(),link.label().unit().localId(),link.label().localId(),link.origin().publication().localId(),link.origin().localId());
                assertEquals(texts.size(),fields.length);for(int i=0;i<fields.length;i++)assertEquals(texts.get(i),chars(port,0,fields[i]));
                assertEquals(link.source().unit().structuralPath().size(),port.structuralPathCount(0));for(int i=0;i<port.structuralPathCount(0);i++)assertEquals(link.source().unit().structuralPath().get(i).intValue(),port.structuralPath(0,i));
                var source=input.source(program,execution).orElseThrow();try(var prepared=SourceCorrelationSnapshot.prepare(source,program,port,read.borrowedPages(),execution)){
                    assertEquals(expected.occurrences().getFirst().executableOperations().size(),prepared.operationCount(0,0));for(int i=0;i<prepared.operationCount(0,0);i++)assertEquals(expected.occurrences().getFirst().executableOperations().get(i),prepared.operation(0,0,i));
                }
            }
        }zero(resources);
    }
    @Test void allFullIdentityFieldContradictionsAndExactDuplicateRowsRejectWithoutPartialMappings()throws Exception {
        var pointers=List.of("/source/unit/compilationUnitId","/source/unit/canonicalProgramName","/source/handle","/operation/publication","/operation/unit","/operation/localId","/label/publication","/label/unit","/label/localId","/origin/publication","/origin/localId","/source/unit/structuralPath");
        for(int mutation=0;mutation<=pointers.size();mutation++){
            var root=manifest();String publication=expectedPublication(root);var rows=(ArrayNode)root.path("correlations");if(mutation==pointers.size())rows.add(rows.get(0).deepCopy());
            else {String pointer=pointers.get(mutation);int split=pointer.lastIndexOf('/');var parent=(ObjectNode)rows.get(0).at(pointer.substring(0,split));String key=pointer.substring(split+1);if(key.equals("structuralPath"))parent.putArray(key).add(0).add(1);else parent.put(key,"synthetic-foreign-full-identity");}
            var resources=resources();try(var input=new DependencyInputJson().readSnapshot(write(root),new DataflowAirReader(),resources)){
                var read=input.air();try(var program=new SnapshotProgram(read.checked(),read.newIdentityStorage(),read.newOrderStorage(),resources)){
                    var execution=AnalysisResources.executionFor(program);var port=input.correlations(program,execution);var source=input.source(program,execution).orElseThrow();
                    // The shared backend also holds the borrowed native owner.
                    // Its lazy operation directory is legitimate retained AIR
                    // state, not failed correlation preparation. Warm that
                    // owner before requiring exact derived-page retirement.
                    program.operationDirectory().orElseThrow();long before=read.borrowedPages().statistics().livePages();
                    assertThrows(IllegalArgumentException.class,()->SourceCorrelationSnapshot.prepare(source,program,port,read.borrowedPages(),execution));assertEquals(before,read.borrowedPages().statistics().livePages());assertTrue(source.unitCount()>0);assertEquals(publication,program.publicationId().localId());
                }
            }zero(resources);
        }
    }
    private static String expectedPublication(ObjectNode root){return root.path("correlations").get(0).path("operation").path("publication").asText();}
    @Test void closedObjectFieldsRequiredKindsDomainsAndExactIntegralPathRejectBeforeNativeFactsEscape()throws Exception {
        var pointers=List.of("","/air","/qualifiedSource","/correlations/0","/correlations/0/source","/correlations/0/source/unit","/correlations/0/operation","/correlations/0/label","/correlations/0/origin");
        for(String pointer:pointers)for(int mutation=0;mutation<3;mutation++){
            var root=manifest();var object=at(root,pointer);if(mutation==0)object.put("synthetic-extra-field",true);else {String key=object.fieldNames().next();if(mutation==1)object.remove(key);else object.putNull(key);}
            var resources=resources();Path path=write(root);assertThrows(IllegalArgumentException.class,()->{try(var input=new DependencyInputJson().readSnapshot(path,new DataflowAirReader(),resources)){input.air();fail("invalid closed wire admitted");}});zero(resources);
        }
        for(String domain:List.of("operation","label","origin")){
            var root=manifest();at(root,"/correlations/0/"+domain).put("domain","synthetic-wrong-domain");var resources=resources();Path path=write(root);assertThrows(IllegalArgumentException.class,()->{try(var input=new DependencyInputJson().readSnapshot(path,new DataflowAirReader(),resources)){input.air();fail("wrong identity domain");}});zero(resources);
        }
        for(JsonNode path:List.of(JSON.readTree("[]"),JSON.readTree("[-1]"),JSON.readTree("[2147483648]"),JSON.readTree("[0.0]"),JSON.readTree("[null]"),JSON.readTree("[true]"))){
            var root=manifest();at(root,"/correlations/0/source/unit").set("structuralPath",path);var resources=resources();Path wire=write(root);assertThrows(IllegalArgumentException.class,()->{try(var input=new DependencyInputJson().readSnapshot(wire,new DataflowAirReader(),resources)){input.air();fail("invalid path admitted");}});zero(resources);
        }
    }
    @Test void correlationPortRejectsForeignProgramAndResourcesAndExpiresWithBothOwners()throws Exception {
        Path path=write(manifest());var resources=resources();var other=resources();StatementCorrelations expired;
        try(var input=new DependencyInputJson().readSnapshot(path,new DataflowAirReader(),resources);var foreign=new DependencyInputJson().readSnapshot(path,new DataflowAirReader(),other)){
            var read=input.air();var foreignRead=foreign.air();try(var program=new SnapshotProgram(read.checked(),read.newIdentityStorage(),read.newOrderStorage(),resources);var otherProgram=new SnapshotProgram(foreignRead.checked(),foreignRead.newIdentityStorage(),foreignRead.newOrderStorage(),other)){
                var execution=AnalysisResources.executionFor(program);var foreignExecution=AnalysisResources.executionFor(otherProgram);assertThrows(IllegalArgumentException.class,()->input.correlations(otherProgram,foreignExecution));assertThrows(IllegalArgumentException.class,()->input.correlations(program,foreignExecution));
                expired=input.correlations(program,execution);assertSame(expired,input.correlations(program,execution));assertEquals(1,expired.size());
            }assertThrows(IllegalStateException.class,expired::size);assertThrows(IllegalStateException.class,expired::program);
        }zero(resources);zero(other);
        var second=resources();StatementCorrelations closed;
        try(var input=new DependencyInputJson().readSnapshot(path,new DataflowAirReader(),second)){
            var read=input.air();try(var program=new SnapshotProgram(read.checked(),read.newIdentityStorage(),read.newOrderStorage(),second)){
                closed=input.correlations(program,AnalysisResources.executionFor(program));closeInput(input);assertThrows(IllegalStateException.class,closed::size);
            }
        }zero(second);
    }
    @Test void pagedManifestRetainsEveryDuplicateOrdinalWithFixedResidentControlsBeforeCorrelationAdmission()throws Exception {
        long fixed=-1;
        for(int n:new int[]{1,4,16,64,256,1024}){
            var root=manifest();var original=root.path("correlations").get(0).deepCopy();var rows=root.putArray("correlations");for(int i=0;i<n;i++)rows.add(original.deepCopy());var resources=resources();
            try(var input=new DependencyInputJson().readSnapshot(write(root),new DataflowAirReader(),resources)){
                var read=input.air();try(var program=new SnapshotProgram(read.checked(),read.newIdentityStorage(),read.newOrderStorage(),resources)){
                    var port=input.correlations(program,AnalysisResources.executionFor(program));assertEquals(n,port.size());assertEquals(chars(port,0,StatementCorrelations.Field.SOURCE_HANDLE),chars(port,n-1,StatementCorrelations.Field.SOURCE_HANDLE));
                    if(fixed<0)fixed=resources.heapUsed();assertEquals(fixed,resources.heapUsed(),"resident control must not grow with correlation count");
                }
            }zero(resources);
        }
    }
    @Test void fieldPermutationLongFullUtf16AndEveryOrderedPathCoordinateSurviveWithoutIdentityProjection()throws Exception {
        var root=manifest();String raw="synthetic:"+"AaBB\0\uD83D\uDE00".repeat(1024);var unit=at(root,"/correlations/0/source/unit");unit.put("compilationUnitId",raw);var coordinates=unit.putArray("structuralPath");for(int i=0;i<4096;i++)coordinates.add(i%31);at(root,"/correlations/0/source").put("handle","ISOLATED_MARKER");
        var reversed=JSON.createObjectNode();var names=new ArrayList<String>();root.fieldNames().forEachRemaining(names::add);Collections.reverse(names);for(String name:names)reversed.set(name,root.get(name));
        String wire=JSON.writeValueAsString(reversed).replace("\"ISOLATED_MARKER\"","\"left\\ud800middle\\udcffright\"");Path path=directory.resolve("permuted-utf16.json");Files.writeString(path,wire);var resources=resources();
        try(var input=new DependencyInputJson().readSnapshot(path,new DataflowAirReader(),resources)){
            var read=input.air();try(var program=new SnapshotProgram(read.checked(),read.newIdentityStorage(),read.newOrderStorage(),resources)){
                var port=input.correlations(program,AnalysisResources.executionFor(program));assertEquals(raw,chars(port,0,StatementCorrelations.Field.SOURCE_COMPILATION_UNIT));assertEquals("left\uD800middle\uDCFFright",chars(port,0,StatementCorrelations.Field.SOURCE_HANDLE));assertEquals(4096,port.structuralPathCount(0));for(int i=0;i<4096;i++)assertEquals(i%31,port.structuralPath(0,i));
            }
        }zero(resources);
    }
    @Test void decodedDuplicatePropertyNamesRejectAndManifestPreallocationDenialRetainsPrimaryPhaseAndPools()throws Exception {
        var root=manifest();String rest=JSON.writeValueAsString(root);Path path=directory.resolve("duplicate-schema.json");Files.writeString(path,"{\"schema\":\"dependency-input\",\"\\u0073chema\":\"dependency-input\","+rest.substring(1));var resources=resources();
        var error=assertThrows(io.github.gustavo2358.air.json.AirJsonException.class,()->{try(var input=new DependencyInputJson().readSnapshot(path,new DataflowAirReader(),resources)){input.air();fail("decoded duplicate escaped field");}});assertEquals(io.github.gustavo2358.air.json.AirJsonException.Code.INPUT_ERROR,error.code());zero(resources);
        Path valid=write(manifest());try(var pressure=resources.reserve(AnalysisResources.Pool.RESIDENT,resources.limits().heapBytes()-255,AnalysisResources.Phase.INDEX)){
            assertNotNull(pressure);var denied=assertThrows(AnalysisResources.Exhausted.class,()->new DependencyInputJson().readSnapshot(valid,new DataflowAirReader(),resources));assertEquals(AnalysisResources.Resource.HEAP,denied.resource());assertEquals(AnalysisResources.Phase.DECODE,denied.phase());assertEquals(resources.limits().heapBytes()-255,resources.heapUsed());
        }zero(resources);
    }
    @Test void failedSharedAirAdmissionBorrowsManifestBackendAndPreservesExactPrimaryBeforeOwnerCleanup()throws Exception {
        var resources=resources();Path path=write(manifest());var primary=new java.io.IOException("synthetic borrowed input failure");
        try(var manifest=DependencyManifestSnapshot.read(path,resources)){
            var pages=manifest.pages();long before=pages.statistics().livePages();var reader=new DataflowAirReader();
            assertSame(primary,assertThrows(java.io.IOException.class,()->reader.readSnapshot(manifest.airPath(),resources,manifest.directory(),ignored->{throw primary;},pages)));
            assertSame(pages,manifest.pages());assertEquals(before,pages.statistics().livePages());assertNotNull(manifest.airSha());long borrowed=pages.allocate();pages.release(borrowed);
        }zero(resources);
    }
}
