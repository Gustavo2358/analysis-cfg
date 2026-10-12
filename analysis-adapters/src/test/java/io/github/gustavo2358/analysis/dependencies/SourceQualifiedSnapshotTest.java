package io.github.gustavo2358.analysis.dependencies;

import io.github.gustavo2358.analysis.adapters.QualifiedSourceJson;
import io.github.gustavo2358.analysis.adapters.FilePageStore;
import io.github.gustavo2358.analysis.dependencies.source.*;
import io.github.gustavo2358.analysis.solver.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.io.ByteArrayInputStream;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

final class SourceQualifiedSnapshotTest {
    @TempDir Path directory;
    private static final ObjectMapper JSON=new ObjectMapper();
    private static AnalysisResources resources(){return new AnalysisResources(new AnalysisResources.Limits(1048576,16384,0,256L*1024*1024,2,Long.MAX_VALUE,0));}
    private static void zero(AnalysisResources resources){for(var pool:AnalysisResources.Pool.values())assertEquals(0,resources.used(pool));}
    private static byte[] literal()throws Exception{return Files.readAllBytes(Path.of("src/test/resources/source-possibility/unknown-native.source.json"));}
    private static SourceAdmissionSnapshot admit(byte[] wire,PageStore pages,AnalysisResources resources)throws Exception{return SourceAdmissionSnapshot.admit(new QualifiedSourceJson().decodeSnapshot(new ByteArrayInputStream(wire),pages,resources),pages,resources);}
    private static String text(SourceSnapshot source,long ref,long length) {
        assertTrue(length<=Integer.MAX_VALUE);char[] chars=new char[Math.toIntExact(length)];source.readCharacters(ref,0,chars,0,chars.length);return new String(chars);
    }
    private static void candidates(SourceAdmissionSnapshot admission,SourceQualifiedSnapshot result,long u,long i,SourceQualifiedSnapshot.Kind kind,List<SourceQualifiedDependencyResult.Candidate> expected) {
        var source=admission.snapshot();assertEquals(expected.size(),result.candidateCount(u,i,kind));
        for(int j=0;j<expected.size();j++) {
            long raw=result.rawValue(u,i,kind,j);var candidate=expected.get(j);
            assertEquals(candidate.rawValue(),text(source,raw,source.characterCount(raw)));
            assertEquals(candidate.referenceName(),text(source,raw,result.referenceLength(u,i,kind,j)));
        }
    }
    private static void compare(byte[] wire,SourceAdmissionSnapshot admission,SourceQualifiedSnapshot result)throws Exception {
        var resident=SourceQualifiedDependencyResult.admit(new QualifiedSourceJson().decode(wire));int occurrences=0,files=0;
        var model=resident.evidence();assertEquals(model.units().size(),result.unitCount());
        for(int u=0;u<model.units().size();u++) {
            assertEquals(model.units().get(u).occurrences().size(),result.count(u,SourceQualifiedSnapshot.Kind.OCCURRENCE));
            assertEquals(model.units().get(u).nativeFiles().size(),result.count(u,SourceQualifiedSnapshot.Kind.NATIVE_FILE));
            for(int i=0;i<model.units().get(u).occurrences().size();i++) {
                var expected=resident.occurrences().get(occurrences++);var kind=SourceQualifiedSnapshot.Kind.OCCURRENCE;
                assertEquals(expected.status(),result.status(u,i,kind));assertEquals(expected.valueRemainder(),result.valueRemainder(u,i,kind));assertEquals(expected.interpretationRemainder(),result.remainder(u,i,kind));
                candidates(admission,result,u,i,kind,expected.candidates());
                assertEquals(admission.snapshot().element(admission.snapshot().field(admission.unit(u),SourceShape.UNIT_EVIDENCE,3),SourceShape.OCCURRENCE,i),result.reference(u,i,kind));
            }
            for(int i=0;i<model.units().get(u).nativeFiles().size();i++) {
                var expected=resident.nativeFiles().get(files++);var kind=SourceQualifiedSnapshot.Kind.NATIVE_FILE;
                assertEquals(expected.status(),result.status(u,i,kind));assertEquals(expected.remainder(),result.remainder(u,i,kind));
                candidates(admission,result,u,i,kind,expected.candidates());
            }
        }
    }
    @Test void all53HistoricalLiteralsMatchCompleteResidentCandidatesAndRemainders()throws Exception {
        int count=0;
        try(var paths=Files.walk(Path.of("src/test/resources"))) {
            for(var path:paths.filter(p->p.toString().endsWith(".source.json")).sorted().toList()) {
                byte[] wire=Files.readAllBytes(path);if(!JSON.readTree(wire).path("schema").asText().equals("qualified-source-dependencies"))continue;
                var resources=resources();
                try(var pages=new FilePageStore(directory,4096,4,resources,AnalysisResources.Phase.INDEX);var admission=admit(wire,pages,resources)) {
                    long before=pages.statistics().livePages();try(var result=SourceQualifiedSnapshot.prepare(admission,pages,resources)){compare(wire,admission,result);}
                    assertEquals(before,pages.statistics().livePages());assertTrue(admission.unitCount()>0);
                }zero(resources);count++;
            }
        }assertEquals(53,count);
    }
    @Test void nativeRawUtf16IsExactOrderedAndDuplicatePreservingWithoutCallPolicy()throws Exception {
        var root=(ObjectNode)JSON.readTree(literal());var unit=(ObjectNode)root.path("units").get(0);var template=(ObjectNode)unit.path("nativeFiles").get(0).deepCopy();var uses=JSON.createArrayNode();
        int ordinal=0;for(var value:List.of("x".repeat(16384)+"\uD800", "Aa", "BB", "Aa", "padded   ","\0")){var use=template.deepCopy();use.put("ordinal",20-ordinal++);((ObjectNode)use.path("names").get(0)).put("rawValue",value);uses.add(use);}unit.set("nativeFiles",uses);
        byte[] wire=JSON.writeValueAsBytes(root);var resources=resources();
        try(var pages=new FilePageStore(directory,4096,4,resources,AnalysisResources.Phase.INDEX);var admission=admit(wire,pages,resources);var result=SourceQualifiedSnapshot.prepare(admission,pages,resources)) {
            compare(wire,admission,result);assertEquals(6,result.count(0,SourceQualifiedSnapshot.Kind.NATIVE_FILE));assertEquals(1,result.candidateCount(0,0,SourceQualifiedSnapshot.Kind.NATIVE_FILE));assertTrue(result.remainder(0,0,SourceQualifiedSnapshot.Kind.NATIVE_FILE));
        }zero(resources);
    }
    @Test void programProfilesAndLongCicsPaddingMatchIndependentNames()throws Exception {
        byte[] original;
        try(var paths=Files.walk(Path.of("src/test/resources"))) {
            original=paths.filter(p->p.toString().endsWith(".source.json")).sorted().map(p->{try{return Files.readAllBytes(p);}catch(Exception failure){throw new IllegalStateException(failure);}})
                .filter(w->{try{var unit=JSON.readTree(w).path("units").get(0);var o=unit.path("occurrences");return unit.path("controlAvailable").asBoolean()&&o.size()>0&&o.get(0).path("qualifications").size()>0&&o.get(0).path("namespace").asText().equals("PROGRAM")&&o.get(0).path("targetKind").asText().equals("LITERAL");}catch(Exception failure){return false;}}).findFirst().orElseThrow();
        }
        for(boolean cics:new boolean[]{false,true})for(String raw:new String[]{"BB","A","Aa","A   ","A"+" ".repeat(8192),"_CALL","9"}) {
            var root=(ObjectNode)JSON.readTree(original);var occurrence=(ObjectNode)root.path("units").get(0).path("occurrences").get(0);occurrence.put("technology",cics?"CICS":"COBOL");occurrence.put("command",cics?"LINK":"CALL");occurrence.put("nameProfile",cics?CicsNameInterpreter.PROFILE:CallNameInterpreter.PROFILE);
            occurrence.set("values",JSON.createArrayNode().add(JSON.createObjectNode().put("logicalDomain","TEXT").put("value",raw).put("logicalExtent",raw.length())));
            byte[] wire=JSON.writeValueAsBytes(root);var resources=resources();
            try(var pages=new FilePageStore(directory,4096,4,resources,AnalysisResources.Phase.INDEX);var admission=admit(wire,pages,resources);var result=SourceQualifiedSnapshot.prepare(admission,pages,resources)) {
                compare(wire,admission,result);var actual=new ArrayList<String>();
                for(long i=0;i<result.candidateCount(0,0,SourceQualifiedSnapshot.Kind.OCCURRENCE);i++){long handle=result.rawValue(0,0,SourceQualifiedSnapshot.Kind.OCCURRENCE,i);actual.add(text(admission.snapshot(),handle,result.referenceLength(0,0,SourceQualifiedSnapshot.Kind.OCCURRENCE,i)));}
                String expected=cics?(raw.equals("Aa")||raw.equals("_CALL")?null:raw.stripTrailing()):(Set.of("BB","A","_CALL").contains(raw)?raw:null);
                assertEquals(expected==null?List.of():List.of(expected),actual);assertEquals(!cics||expected==null,result.remainder(0,0,SourceQualifiedSnapshot.Kind.OCCURRENCE));
            }zero(resources);
        }
    }
    @Test void duplicateNativeCandidatesSpillWithFixedResidentControls()throws Exception {
        long fixed=-1;
        for(int count:new int[]{1,4,16,64,256,1024}) {
            var root=(ObjectNode)JSON.readTree(literal());var unit=(ObjectNode)root.path("units").get(0);var template=(ObjectNode)unit.path("nativeFiles").get(0).deepCopy();var uses=JSON.createArrayNode();for(int i=0;i<count;i++){var use=template.deepCopy();use.put("ordinal",i);uses.add(use);}unit.set("nativeFiles",uses);
            byte[] wire=JSON.writeValueAsBytes(root);var resources=resources();
            try(var pages=new FilePageStore(directory,4096,4,resources,AnalysisResources.Phase.INDEX);var admission=admit(wire,pages,resources)) {
                long before=resources.heapUsed();try(var result=SourceQualifiedSnapshot.prepare(admission,pages,resources)){compare(wire,admission,result);long actual=resources.heapUsed()-before;if(fixed<0)fixed=actual;assertEquals(fixed,actual);assertEquals(count,result.count(0,SourceQualifiedSnapshot.Kind.NATIVE_FILE));}
            }zero(resources);
        }
    }
    @Test void parentExpiryImmediatelyRetiresCandidatePagesWhileExplicitCloseBorrows()throws Exception {
        var resources=resources();
        try(var pages=new FilePageStore(directory,4096,4,resources,AnalysisResources.Phase.INDEX);var admission=admit(literal(),pages,resources)) {
            long before=pages.statistics().livePages();var first=SourceQualifiedSnapshot.prepare(admission,pages,resources);first.close();assertEquals(before,pages.statistics().livePages());assertTrue(admission.unitCount()>0);
            var second=SourceQualifiedSnapshot.prepare(admission,pages,resources);admission.snapshot().close();assertEquals(0,pages.statistics().livePages());assertThrows(IllegalStateException.class,second::unitCount);second.close();
            long borrowed=pages.allocate();pages.release(borrowed);
        }zero(resources);
    }
    @Test void exactContextAndPreallocationDenialNeverStealAdmittedSource()throws Exception {
        var resources=resources();var foreign=resources();
        try(var pages=new FilePageStore(directory,4096,4,resources,AnalysisResources.Phase.INDEX);var other=new FilePageStore(directory,4096,4,resources,AnalysisResources.Phase.INDEX);var admission=admit(literal(),pages,resources)) {
            long before=pages.statistics().livePages();assertThrows(IllegalArgumentException.class,()->SourceQualifiedSnapshot.prepare(admission,other,resources));assertThrows(IllegalArgumentException.class,()->SourceQualifiedSnapshot.prepare(admission,pages,foreign));
            try(var pressure=resources.reserve(AnalysisResources.Pool.RESIDENT,resources.limits().heapBytes()-resources.heapUsed()-2047,AnalysisResources.Phase.INDEX)) {assertNotNull(pressure);assertThrows(AnalysisResources.Exhausted.class,()->SourceQualifiedSnapshot.prepare(admission,pages,resources));}
            assertEquals(before,pages.statistics().livePages());assertTrue(admission.unitCount()>0);
        }zero(resources);zero(foreign);
    }
    @Test void invalidCandidateOrdinalPoisonsOnlyDerivedResult()throws Exception {
        var resources=resources();
        try(var pages=new FilePageStore(directory,4096,4,resources,AnalysisResources.Phase.INDEX);var admission=admit(literal(),pages,resources);var result=SourceQualifiedSnapshot.prepare(admission,pages,resources)) {
            long before=pages.statistics().livePages();assertThrows(IndexOutOfBoundsException.class,()->result.rawValue(0,0,SourceQualifiedSnapshot.Kind.NATIVE_FILE,Long.MAX_VALUE));assertThrows(IllegalStateException.class,result::unitCount);assertTrue(admission.unitCount()>0);assertTrue(pages.statistics().livePages()<before);
        }zero(resources);
    }
    @Test void primitiveFailuresKeepPrimaryAndRetireCandidateOwnership()throws Exception {
        for(int operation=0;operation<4;operation++) {
            var resources=resources();
            try(var pages=new FilePageStore(directory,4096,4,resources,AnalysisResources.Phase.INDEX)) {
                var primary=new PageStore.Failure(PageStore.Reason.IO,"independent candidate primitive failure");var port=new FaultPages(pages,primary);
                try(var admission=admit(literal(),port,resources)) {
                    long before=pages.statistics().livePages();
                    if(operation<3){port.arm(operation);assertSame(primary,assertThrows(PageStore.Failure.class,()->SourceQualifiedSnapshot.prepare(admission,port,resources)));}
                    else try(var result=SourceQualifiedSnapshot.prepare(admission,port,resources)) {
                        port.arm(1);assertSame(primary,assertThrows(PageStore.Failure.class,()->result.rawValue(0,0,SourceQualifiedSnapshot.Kind.NATIVE_FILE,0)));assertThrows(IllegalStateException.class,result::unitCount);
                    }
                    try{admission.snapshot();assertEquals(before,pages.statistics().livePages());}catch(IllegalStateException expired){assertEquals(0,pages.statistics().livePages());}
                    assertFalse(port.closed);
                }assertEquals(0,pages.statistics().livePages());long borrowed=pages.allocate();pages.release(borrowed);
            }zero(resources);
        }
    }
    @Test void nativeCommonOwnerExpiryRetiresCandidatesBeforeAnyFurtherQuery()throws Exception {
        var resources=new AnalysisResources(new AnalysisResources.Limits(64L*1024*1024,16L*1024*1024,0,256L*1024*1024,8,Long.MAX_VALUE,0));
        try(var input=new io.github.gustavo2358.analysis.adapters.DataflowAirReader().readSnapshot(Path.of("src/test/resources/cp6/dynamic-x8.air.json"),resources);
            var pages=new FilePageStore(directory,4096,4,resources,AnalysisResources.Phase.INDEX)) {
            var program=new SnapshotProgram(input.checked(),input.newIdentityStorage(),input.newOrderStorage(),resources);
            try {
                var execution=AnalysisResources.executionFor(program);
                try(var admission=admit(literal(),pages,execution);var result=SourceQualifiedSnapshot.prepare(admission,pages,execution)) {
                    assertTrue(result.count(0,SourceQualifiedSnapshot.Kind.NATIVE_FILE)>0);program.close();assertEquals(0,pages.statistics().livePages());zero(execution);
                    assertThrows(IllegalStateException.class,result::unitCount);assertThrows(IllegalStateException.class,admission::snapshot);
                }
            }finally{program.close();}
        }zero(resources);
    }
    private static final class FaultPages implements PageStore {
        private final PageStore delegate;private final Failure primary;private int operation=-1;private boolean closed;
        FaultPages(PageStore delegate,Failure primary){this.delegate=delegate;this.primary=primary;}
        void arm(int value){operation=value;}
        private void fail(int candidate){if(candidate==operation){operation=-1;throw primary;}}
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
