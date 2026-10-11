package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.analysis.dependencies.*;
import io.github.gustavo2358.analysis.query.ProgramPoint;
import io.github.gustavo2358.analysis.solver.*;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.*;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static io.github.gustavo2358.analysis.dependencies.DependencySiteFact.*;

/** Legacy mapper is the independent byte oracle. Caller-owned fixtures and
 * expected bytes precede managed staging; not provider or OUTPUT accounting proof. */
@SuppressWarnings("try")
final class NativeFactWriterOracleTest {
    @TempDir Path directory;
    private static boolean wire(Id id) {return !(id instanceof ArtifactId || id instanceof ArtifactRelationId || id instanceof CompletionPortId);}
    private static List<Support> legal(List<Support> supports) {return supports.stream().filter(s->wire(s.producer())).toList();}
    private static DependencySiteFact fixture(int number)throws Exception {
        var method=PagedDependencyFactStorageTest.class.getDeclaredMethod("fact",int.class);method.setAccessible(true);
        var f=(DependencySiteFact)method.invoke(null,number);
        return copy(f,f.valuePoint(),f.technology(),f.command(),f.namespace(),f.nameProfile(),
            f.rawCandidates().stream().map(c->new RawCandidate(c.rawValue().replace("\uD800",""),legal(c.supports()))).toList(),
            f.candidates().stream().map(c->new Candidate(c.referenceName(),c.rawValue().replace("\uD800",""),legal(c.supports()))).toList(),
            f.evidence().stream().filter(NativeFactWriterOracleTest::wire).toList(),f.provenance(),f.premises(),f.uncertaintyRefs(),f.analysisReasons());
    }
    private static DependencySiteFact copy(DependencySiteFact f,ProgramPoint point,String technology,String command,String namespace,String profile,
            List<RawCandidate> raw,List<Candidate> candidates,List<Id> evidence,List<OriginId> provenance,List<PremiseId> premises,List<UncertaintyId> uncertainties,List<String> reasons) {
        return new DependencySiteFact(f.caller(),f.entry(),f.sequence(),f.operation(),f.offset(),f.siteOrigin(),f.targetOrigin(),technology,command,namespace,profile,f.targetKind(),f.subject(),point,
            f.reachability(),f.targetStatus(),raw,candidates,f.modelValueRemainder(),f.sourceValueRemainder(),f.interpretationUnknownRemainder(),f.effectiveUnknownRemainder(),f.openControlRemainder(),evidence,provenance,premises,uncertainties,f.analysisStatus(),reasons);
    }
    private static byte[] legacy(DependencySiteFact fact)throws Exception {
        var method=DependencyJson.class.getDeclaredMethod("site",DependencySiteFact.class,boolean.class);method.setAccessible(true);
        var fields=(JsonOutput.Fields)method.invoke(null,fact,true);fields.put("coverage","PARTIAL");
        var bytes=new ByteArrayOutputStream();var out=new JsonOutput(bytes);out.value(fields);out.finish();return bytes.toByteArray();
    }
    private void same(DependencySiteFact fact)throws Exception {
        byte[] expected=legacy(fact); // Outside the measured managed staging/encoding scope.
        var resources=new AnalysisResources(new AnalysisResources.Limits(650_000,8_000_000,0,256_000_000,4,Long.MAX_VALUE,Long.MAX_VALUE));
        try(var pages=new FilePageStore(directory,256,1,resources);var storage=new PagedDependencyFactStorage(pages,resources)) {
            storage.append(fact);long resident=resources.used(AnalysisResources.Pool.RESIDENT);
            try(var root=resources.reserve(AnalysisResources.Pool.RESIDENT,65536+4096,AnalysisResources.Phase.ENCODE)) {
                assertEquals(65536+4096,root.amount());var bytes=new ByteArrayOutputStream();var out=new JsonOutput(bytes);
                var writer=new DependencyJson.FactWriter(out,pages,resources);
                try(var scope=storage.openFact(0)){writer.site(scope,"PARTIAL");out.finish();}
                assertArrayEquals(expected,bytes.toByteArray(),"every lexical byte,field,duplicate and ordering must match the unchanged legacy mapper");
                assertEquals(0,resources.used(AnalysisResources.Pool.SCRATCH));
            }
            assertEquals(resident,resources.used(AnalysisResources.Pool.RESIDENT));
        }
        for(var pool:AnalysisResources.Pool.values())assertEquals(0,resources.used(pool));
    }
    @Test void completeWireEveryOutcomeAllLegalIdentityOwnersAndOptionalPointShapesMatchesLegacyBytes()throws Exception {
        for(int i=0;i<6;i++)same(fixture(i));
        var f=fixture(0);
        for(var point:new ProgramPoint[]{ProgramPoint.entry(f.entry()),ProgramPoint.after(f.entry(),f.operation()),null})
            same(copy(f,point,f.technology(),f.command(),f.namespace(),f.nameProfile(),f.rawCandidates(),f.candidates(),f.evidence(),f.provenance(),f.premises(),f.uncertaintyRefs(),List.of("z","Aa","BB","Aa")));
    }
    @Test void reverseCandidatesRawCandidatesStableSupportTiesAndPremiseDuplicatesMatchAcrossFiveCounts()throws Exception {
        var f=fixture(1);var p=f.caller().publication();
        var supports=List.of(new Support(SupportKind.CICS_LITERAL,f.operation(),f.siteOrigin(),List.of(new PremiseId(p,"BB"),new PremiseId(p,"Aa"),new PremiseId(p,"Aa"))),
            new Support(SupportKind.CALL_LITERAL,f.operation(),f.siteOrigin(),List.of()),new Support(SupportKind.VALUE_PRODUCER,f.entry(),f.siteOrigin(),List.of()));
        for(int count:new int[]{64,256,1024,4096,16384}) {
            var candidates=new ArrayList<Candidate>();var raw=new ArrayList<RawCandidate>();
            for(int i=count-1;i>=0;i--){String key="PG"+i;candidates.add(new Candidate(key,"raw"+(i%7),i%97==0?supports:List.of()));raw.add(new RawCandidate("raw"+i,i%97==0?supports:List.of()));}
            same(copy(f,f.valuePoint(),f.technology(),f.command(),f.namespace(),f.nameProfile(),raw,candidates,f.evidence(),f.provenance(),f.premises(),f.uncertaintyRefs(),f.analysisReasons()));
            System.out.println("FACT_WRITER_CANDIDATE_BYTE_ORACLE count="+count);
        }
    }
    @Test void coldScalarTextRawReferenceReasonAndExceptionPayloadMatchAcrossFiveWidths()throws Exception {
        var f=fixture(2);
        for(int width:new int[]{64,256,1024,4096,16384}) {
            String text=("AaBB\u0000\uFFFF\uD83D\uDE42\uD800\uDC00z").repeat(width);
            var point=new ProgramPoint(f.entry(),ProgramPoint.Kind.OUTCOME,f.operation(),new io.github.gustavo2358.air.model.Control.ExceptionOutcome(text));
            same(copy(f,point,text,text+"command",text+"namespace",text+"profile",List.of(new RawCandidate(text,List.of())),List.of(new Candidate(text,text,List.of())),f.evidence(),f.provenance(),f.premises(),f.uncertaintyRefs(),List.of(text+"BB",text+"Aa",text+"Aa")));
            System.out.println("FACT_WRITER_TEXT_BYTE_ORACLE width="+width);
        }
    }
    @Test void coldEvidenceProvenancePremiseUncertaintyAndDistinctReasonDirectoriesMatchAcrossFiveCounts()throws Exception {
        var f=fixture(1);var p=f.caller().publication();
        for(int count:new int[]{64,256,1024,4096,16384}) {
            var evidence=new ArrayList<Id>();var provenance=new ArrayList<OriginId>();var premises=new ArrayList<PremiseId>();var uncertainties=new ArrayList<UncertaintyId>();var reasons=new ArrayList<String>();
            for(int i=count-1;i>=0;i--){String key="id"+(i%101);evidence.add(new OriginId(p,key));provenance.add(new OriginId(p,key));premises.add(new PremiseId(p,key));uncertainties.add(new UncertaintyId(p,key));reasons.add("reason"+(i%101));}
            same(copy(f,f.valuePoint(),f.technology(),f.command(),f.namespace(),f.nameProfile(),List.of(),List.of(),evidence,provenance,premises,uncertainties,reasons));
            System.out.println("FACT_WRITER_DIRECTORY_BYTE_ORACLE count="+count);
        }
    }
    @Test void legacyArtifactEncodingExceptionAndUnsupportedSortAdmissionRemainExact()throws Exception {
        var f=fixture(1);var p=f.caller().publication();var artifact=new ArtifactId(p,"artifact");
        same(copy(f,null,f.technology(),f.command(),f.namespace(),f.nameProfile(),List.of(),List.of(),List.of(artifact),List.of(),List.of(),List.of(),f.analysisReasons()));
        same(copy(f,null,f.technology(),f.command(),f.namespace(),f.nameProfile(),List.of(),List.of(),List.of(artifact,new OriginId(new PublicationId("other"),"origin")),List.of(),List.of(),List.of(),f.analysisReasons()));
        for(var ids:List.of(List.<Id>of(artifact,new OriginId(p,"origin")),List.<Id>of(new ArtifactRelationId(p,"relation")),List.<Id>of(new CompletionPortId(f.caller(),"port")))) {
            var fact=copy(f,null,f.technology(),f.command(),f.namespace(),f.nameProfile(),List.of(),List.of(),ids,List.of(),List.of(),List.of(),f.analysisReasons());
            assertThrows(IllegalArgumentException.class,()->legacy(fact));
            var resources=new AnalysisResources(new AnalysisResources.Limits(650_000,8_000_000,0,256_000_000,4,Long.MAX_VALUE,Long.MAX_VALUE));
            try(var pages=new FilePageStore(directory,256,1,resources);var storage=new PagedDependencyFactStorage(pages,resources)) {
                storage.append(fact);try(var root=resources.reserve(AnalysisResources.Pool.RESIDENT,65536+4096,AnalysisResources.Phase.ENCODE);var scope=storage.openFact(0)) {
                    assertEquals(65536+4096,root.amount());var out=new JsonOutput(new ByteArrayOutputStream());
                    assertThrows(IllegalArgumentException.class,()->new DependencyJson.FactWriter(out,pages,resources).site(scope,"PARTIAL"));
                }assertEquals(0,resources.used(AnalysisResources.Pool.SCRATCH));
            }for(var pool:AnalysisResources.Pool.values())assertEquals(0,resources.used(pool));
        }
    }
    @Test void unpairedSurrogatesInEveryFactTextPortRetainLegacyUnicodeRejection()throws Exception {
        var f=fixture(1);var assertions=new ArrayList<org.junit.jupiter.api.function.Executable>();
        for(String bad:List.of("\uD800","\uDC00","\uD800x","x".repeat(255)+"\uD800x","x".repeat(256)+"\uD800"))for(int field=0;field<5;field++) {
            int selected=field;assertions.add(()->{
                var fact=copy(f,null,selected==0?bad:f.technology(),f.command(),f.namespace(),f.nameProfile(),
                    selected==3?List.of(new RawCandidate(bad,List.of())):List.of(),
                    selected==1?List.of(new Candidate(bad,"raw",List.of())):selected==2?List.of(new Candidate("reference",bad,List.of())):List.of(),
                    List.of(),List.of(),List.of(),List.of(),selected==4?List.of(bad):f.analysisReasons());
                assertThrows(IllegalArgumentException.class,()->legacy(fact));
                var resources=new AnalysisResources(new AnalysisResources.Limits(650_000,8_000_000,0,256_000_000,4,Long.MAX_VALUE,Long.MAX_VALUE));
                try(var pages=new FilePageStore(directory,256,1,resources);var storage=new PagedDependencyFactStorage(pages,resources)) {
                    storage.append(fact);try(var root=resources.reserve(AnalysisResources.Pool.RESIDENT,65536+4096,AnalysisResources.Phase.ENCODE);var scope=storage.openFact(0)) {
                        assertEquals(65536+4096,root.amount());var out=new JsonOutput(new ByteArrayOutputStream());
                        assertThrows(IllegalArgumentException.class,()->new DependencyJson.FactWriter(out,pages,resources).site(scope,"PARTIAL"),"FACT_TEXT_PORT_BROADENED_LEGACY_UNICODE_REJECTION field="+selected+" length="+bad.length());
                    }assertEquals(0,resources.used(AnalysisResources.Pool.SCRATCH));
                }for(var pool:AnalysisResources.Pool.values())assertEquals(0,resources.used(pool));
            });
        }
        assertAll("FACT_TEXT_UNICODE_ADMISSION_CHANGED",assertions);
    }
}
