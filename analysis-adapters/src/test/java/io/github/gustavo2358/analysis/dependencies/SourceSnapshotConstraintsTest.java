package io.github.gustavo2358.analysis.dependencies;

import io.github.gustavo2358.analysis.adapters.FilePageStore;
import io.github.gustavo2358.analysis.adapters.QualifiedSourceJson;
import io.github.gustavo2358.analysis.solver.*;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Local constraints only. No successful semantic admission/certificate from this checker. */
final class SourceSnapshotConstraintsTest {
    @TempDir Path directory;
    private static final String ID="{\"compilationUnitId\":\"synthetic\",\"structuralPath\":[0],\"canonicalProgramName\":\"PROGRAM\"}";
    private static final String SID="{\"unit\":"+ID+",\"handle\":\"s\"}";
    private static final String LOCATION="{\"file\":\"path\\u0000\\ud800😀\\udc00.cbl\",\"startLine\":1,\"startColumn\":99,\"endLine\":1,\"endColumn\":0}";
    private static final String ORIGIN="{\"expanded\":"+LOCATION+",\"original\":"+LOCATION+",\"includeChain\":[],\"exact\":false}";
    private static String root(String occurrence,String nominal) {
        return "{\"schema\":\"qualified-source-dependencies\",\"version\":\"1.6.0\",\"producer\":\"synthetic\",\"source\":{\"schema\":\"cobol-semantic-product\",\"version\":\"2.67.0\",\"sha256\":\""+"0".repeat(64)+"\"},\"air\":[],\"units\":[{\"unit\":"+ID+",\"controlAvailable\":true,\"statements\":[{\"id\":"+SID+",\"provenance\":"+ORIGIN+"}],\"occurrences\":["+occurrence+"],\"targets\":[],\"nodes\":[],\"derivations\":[],\"selections\":[],\"events\":[],\"guards\":[],\"proofs\":[],\"frontiers\":[]"+nominal+"}]}";
    }
    private static String occurrence(String value,int extent) {
        return "{\"id\":"+SID+",\"technology\":\"COBOL\",\"command\":\"CALL\",\"namespace\":\"PROGRAM\",\"nameProfile\":\"COBOL\",\"targetKind\":\"LITERAL\",\"operands\":[{\"id\":{\"statement\":"+SID+",\"handle\":\"operand\"},\"provenance\":"+ORIGIN+"}],\"values\":[{\"logicalDomain\":\"TEXT\",\"value\":\""+value+"\",\"logicalExtent\":"+extent+"}],\"valueRemainder\":false,\"qualifications\":[]}";
    }
    private static AnalysisResources resources(){return new AnalysisResources(new AnalysisResources.Limits(1048576,16384,0,128L*1024*1024,2,Long.MAX_VALUE,0));}
    private static void zero(AnalysisResources resources){for(var pool:AnalysisResources.Pool.values())assertEquals(0,resources.used(pool));}
    private void check(byte[] wire,boolean valid)throws Exception {
        var resources=resources();
        try(var pages=new FilePageStore(directory,4096,4,resources,AnalysisResources.Phase.VALIDATION);
            var storage=new QualifiedSourceJson().decodeSnapshot(new ByteArrayInputStream(wire),pages,resources)) {
            long before=pages.statistics().livePages();
            if(valid)SourceSnapshotConstraints.check(storage,pages,resources);
            else assertThrows(IllegalArgumentException.class,()->SourceSnapshotConstraints.check(storage,pages,resources));
            assertEquals(before,pages.statistics().livePages(),"checker owns only its transient stores");
            assertTrue(storage.root()>0,"a local check borrows storage; it is not an admission owner");
        }zero(resources);
    }
    private void check(String wire,boolean valid)throws Exception {check(wire.getBytes(StandardCharsets.UTF_8),valid);}
    @Test void scalarConstraintsRejectInvalidMetadataEvenWhenNoConsumerDemandsIt()throws Exception {
        String valid=root("","");check(valid,true);
        check(valid.replace("\"producer\":\"synthetic\"","\"producer\":\" \\t\\u2003\""),false);
        check(valid.replace("\"producer\":\"synthetic\"","\"producer\":\"\\u00a0\""),true);
        check(valid.replace("\"version\":\"1.6.0\"","\"version\":\"9.9.9\""),false);
        check(valid.replace("\"structuralPath\":[0]","\"structuralPath\":[]"),false);
        check(valid.replace("\"structuralPath\":[0]","\"structuralPath\":[-1]"),false);
        check(valid.replace("\"startLine\":1","\"startLine\":0"),false);
        check(valid.replace("\"endColumn\":0","\"endColumn\":-1"),false);
        check(valid.replace("\"sha256\":\""+"0".repeat(64)+"\"","\"sha256\":\""+"A".repeat(64)+"\""),false);
    }
    @Test void fullUtf16CodePointExtentIncludesUnpairedSurrogatesAndEmptyLiterals()throws Exception {
        check(root(occurrence("",0),""),true);
        check(root(occurrence("\\ud800X\\udc00😀",4),""),true);
        check(root(occurrence("\\ud800X\\udc00😀",5),""),false);
        check(root(occurrence("A",1),"").replace("\"valueRemainder\":false","\"valueRemainder\":true"),false);
        // The high surrogate is the last character of the fixed 256-character block.
        check(root(occurrence("A".repeat(255)+"😀B",257),""),true);
        check(root(occurrence("A".repeat(255)+"😀B",258),""),false);
        check(root(occurrence("A".repeat(255)+"\\ud800B",257),""),true);
    }
    private static String nominal(String authority,String symbols,String assignments,String tables) {
        return ",\"nominalValues\":{\"facts\":{\"authority\":\""+authority+"\",\"symbols\":"+symbols+",\"assignments\":"+assignments+",\"conditions\":[],\"queries\":[]"+tables+"},\"declarations\":[],\"seeds\":[],\"branches\":[],\"uncertainties\":[]}";
    }
    @Test void conditionalPhysicalPresenceAndExpressionAuthorityAreNotOptionalDefaults()throws Exception {
        String v1="NOMINAL_TEXT_SOURCE_V1",v3="NOMINAL_TEXT_SOURCE_V3",v4="NOMINAL_TEXT_SOURCE_V4";
        String symbols="[{\"node\":\"n\",\"extent\":1}]",model="[{\"node\":\"n\",\"extent\":1,\"modelAssumed\":false}]";
        check(root("",nominal(v1,symbols,"[]","")),true);
        check(root("",nominal(v1,model,"[]","")),false);
        check(root("",nominal(v3,symbols,"[]","")),false);
        check(root("",nominal(v3,model,"[]","")),true);
        check(root("",nominal(v3,model,"[]",",\"tableFields\":[]")),false);
        check(root("",nominal(v4,model,"[]","")),false);
        check(root("",nominal(v4,model,"[]",",\"tableFields\":[]")),true);
        String choice="[{\"statement\":\"s\",\"target\":\"n\",\"source\":{\"kind\":\"CHOICE\",\"value\":\"\",\"arguments\":[{\"kind\":\"LITERAL\",\"value\":\"A\"},{\"kind\":\"LITERAL\",\"value\":\"B\"}]}}]";
        check(root("",nominal(v3,model,choice,"")),false);
        check(root("",nominal(v4,model,choice,",\"tableFields\":[]")),true);
    }
    @Test void recursiveTermsUsePagedWorkAndCheckTheDeepestInvalidPayload()throws Exception {
        for(boolean valid:new boolean[]{true,false}) {
            String term="{\"kind\":\""+(valid?"LITERAL":"READ")+"\",\"value\":\"\"}";
            for(int i=0;i<2048;i++)term="{\"kind\":\"UPPER_ASCII\",\"value\":\"\",\"arguments\":["+term+"]}";
            String assignments="[{\"statement\":\"s\",\"target\":\"n\",\"source\":"+term+"}]";
            check(root("",nominal("NOMINAL_TEXT_SOURCE_V3","[]",assignments,"")),valid);
        }
    }
    private static String units(String first,String second) {
        int at=first.indexOf("\"units\":[")+9,other=second.indexOf("\"units\":[")+9;
        return first.substring(0,at)+first.substring(at,first.length()-2)+","+second.substring(other,second.length()-2)+"]}";
    }
    @Test void sharedCanonicalTermsMustBeCheckedInEveryAuthorityInEitherUnitOrder()throws Exception {
        String choice="[{\"statement\":\"s\",\"target\":\"n\",\"source\":{\"kind\":\"CHOICE\",\"value\":\"\",\"arguments\":[{\"kind\":\"LITERAL\",\"value\":\"A\"},{\"kind\":\"LITERAL\",\"value\":\"B\"}]}}]";
        String v4=root("",nominal("NOMINAL_TEXT_SOURCE_V4","[]",choice,",\"tableFields\":[]"));
        String v3=root("",nominal("NOMINAL_TEXT_SOURCE_V3","[]",choice,""));
        // Deliberate duplicate unit identities: this test is ONLY the local walker,
        // not the separate global unit/reference admission that must reject those.
        check(units(v4,v4),true);
        for(String wire:new String[]{units(v4,v3),units(v3,v4)}) {
            var resources=resources();
            try(var pages=new FilePageStore(directory,4096,4,resources,AnalysisResources.Phase.VALIDATION);
                var storage=new QualifiedSourceJson().decodeSnapshot(new ByteArrayInputStream(wire.getBytes(StandardCharsets.UTF_8)),pages,resources)) {
                long list=storage.field(storage.root(),io.github.gustavo2358.analysis.dependencies.source.SourceShape.ROOT,5);
                long first=storage.element(list,io.github.gustavo2358.analysis.dependencies.source.SourceShape.UNIT_EVIDENCE,0),second=storage.element(list,io.github.gustavo2358.analysis.dependencies.source.SourceShape.UNIT_EVIDENCE,1);
                assertEquals(sharedTerm(storage,first),sharedTerm(storage,second),"same canonical term, different authority");
                long before=pages.statistics().livePages();
                assertThrows(IllegalArgumentException.class,()->SourceSnapshotConstraints.check(storage,pages,resources));
                assertEquals(before,pages.statistics().livePages());assertTrue(storage.root()>0);
            }zero(resources);
        }
    }
    private static long sharedTerm(io.github.gustavo2358.analysis.dependencies.source.SourceSnapshot storage,long unit) {
        var unitType=io.github.gustavo2358.analysis.dependencies.source.SourceShape.UNIT_EVIDENCE;
        var nominalType=io.github.gustavo2358.analysis.dependencies.source.SourceShape.NOMINAL_VALUE_EVIDENCE;
        var factsType=io.github.gustavo2358.analysis.dependencies.source.SourceShape.NOMINAL_VALUES;
        var assignmentType=io.github.gustavo2358.analysis.dependencies.source.SourceShape.ASSIGNMENT;
        long nominal=storage.field(unit,unitType,12),facts=storage.field(nominal,nominalType,0);
        long assignments=storage.field(facts,factsType,2);
        return storage.field(storage.element(assignments,assignmentType,0),assignmentType,2);
    }
    @Test void initializerUniquenessUsesTheCompleteOriginAndValueNotOneField()throws Exception {
        String a="{\"origin\":\"Aa\",\"value\":\"\"}",b="{\"origin\":\"Aa\",\"value\":\"BB\"}",c="{\"origin\":\"BB\",\"value\":\"\"}";
        for(boolean valid:new boolean[]{true,false}) {
            String tables=",\"tableFields\":[{\"node\":\"n\",\"initial\":["+a+","+b+","+c+(valid?"":","+a)+"]}]";
            check(root("",nominal("NOMINAL_TEXT_SOURCE_V4","[]","[]",tables)),valid);
        }
    }
    @Test void existingLiteralResidentContractsPassLocalChecksWithoutReconstruction()throws Exception {
        var json=new ObjectMapper();int accepted=0,attempted=0;
        try(var paths=Files.walk(Path.of("src/test/resources"))) {
            for(var path:paths.filter(p->p.toString().endsWith(".source.json")).sorted().toList()) {
                byte[] wire=Files.readAllBytes(path);ObjectNode tree=(ObjectNode)json.readTree(wire);
                if(!tree.path("schema").asText().equals("qualified-source-dependencies"))continue;
                attempted++;
                try{new QualifiedSourceJson().decode(wire);}catch(IllegalArgumentException invalid){continue;}
                check(wire,true);accepted++;
            }
        }
        assertEquals(53,attempted);assertTrue(accepted>=20);System.out.println("SOURCE_LOCAL_CONSTRAINT_LITERAL_CONTRACTS attempted="+attempted+" accepted="+accepted+" certificate=NOT_ISSUED");
    }
    @Test void deniedCheckerControlLeavesNoAdditionalPagesOrSuccessfulAdmission()throws Exception {
        var resources=resources();
        try(var pages=new FilePageStore(directory,4096,4,resources,AnalysisResources.Phase.VALIDATION);
            var storage=new QualifiedSourceJson().decodeSnapshot(new ByteArrayInputStream(root("","").getBytes(StandardCharsets.UTF_8)),pages,resources)) {
            long before=pages.statistics().livePages();
            try(var pressure=resources.reserve(AnalysisResources.Pool.RESIDENT,resources.limits().heapBytes()-resources.heapUsed()-1023,AnalysisResources.Phase.VALIDATION)) {
                assertTrue(pressure!=null);
                var failure=assertThrows(AnalysisResources.Exhausted.class,()->SourceSnapshotConstraints.check(storage,pages,resources));
                assertEquals(AnalysisResources.Resource.HEAP,failure.resource());assertEquals(AnalysisResources.Phase.VALIDATION,failure.phase());
                assertEquals(before,pages.statistics().livePages());assertTrue(storage.root()>0);
            }
        }zero(resources);
    }
}
