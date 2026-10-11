package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
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

final class SourceProgramSnapshotTest {
    @TempDir Path directory;
    private static final ObjectMapper JSON=new ObjectMapper();
    private static final QualifiedSourceJson CODEC=new QualifiedSourceJson();
    private SourceCorrelationSnapshotTest fixtures(){var fixture=new SourceCorrelationSnapshotTest();fixture.directory=directory;return fixture;}
    static QualifiedSourceDependencies source(Publication p,String path)throws Exception {
        var q=CODEC.decode(Files.readAllBytes(Path.of("src/test/resources").resolve(path)));
        return new QualifiedSourceDependencies(q.schema(),q.version(),q.producer(),q.source(),List.of(new QualifiedSourceDependencies.AirCorrelation(p.id().localId(),"0".repeat(64))),q.units());
    }
    /** Small-only independent assembly through the preexisting public resolver and formatter. */
    private static JsonNode expected(Publication publication,QualifiedSourceDependencies evidence,List<DependencyInput.StatementCorrelation> links,List<DependencySiteFact> sites)throws Exception {
        var input=new DependencyInput(publication,Optional.of(evidence),links);var resolved=new ArrayList<TargetResolver.Resolution>();
        for(var occurrence:input.occurrences()) {
            var selected=occurrence.executableOperations().stream().flatMap(op->sites.stream().filter(s->s.operation().equals(op))).toList();var conditional=List.<SourceValuesProvider.Candidate>of();
            if(occurrence.source().isPresent()&&TargetResolver.requiresSourceValues(occurrence,selected)) {
                var id=occurrence.source().orElseThrow();var unit=evidence.units().stream().filter(u->u.unit().equals(id.unit())).findFirst().orElseThrow();
                if(unit.nominalValues().isPresent()&&unit.nominalValues().orElseThrow().facts().queries().stream().anyMatch(q->q.statement().equals(id.handle())))conditional=new SourceValuesProvider(unit,Set.of(id.handle())).candidates(id.handle());
            }resolved.add(TargetResolver.resolve(occurrence,selected,conditional));
        }
        var bytes=new ByteArrayOutputStream();var output=new JsonOutput(bytes);output.value(resolved.stream().map(DependencyJson::program));output.finish();return JSON.readTree(bytes.toByteArray());
    }
    private static JsonNode emitted(SourceProgramSnapshot result,PageStore pages,AnalysisResources resources)throws Exception {
        var bytes=new ByteArrayOutputStream();var output=new JsonOutput(bytes);
        try(var writer=SourceSnapshotWriter.open(result.admission(),output,pages,resources)){output.value(writer.programs(result));output.finish();writer.verify();}return JSON.readTree(bytes.toByteArray());
    }
    private void compare(Publication publication,QualifiedSourceDependencies evidence,List<DependencyInput.StatementCorrelation> links,List<DependencySiteFact> sites)throws Exception {
        var oracle=expected(publication,evidence,links,sites);var fixture=fixtures();
        try(var input=fixture.new NativeCase(publication,evidence);var correlations=SourceCorrelationSnapshot.prepare(input.source,input.program,links,input.pages,input.execution);var qualified=SourceQualifiedSnapshot.prepare(input.source,input.pages,input.execution);var plan=SourceTargetPlan.prepare(correlations,qualified,SourceTargetPlanTest.result(publication,sites),input.pages,input.execution)) {
            long baseline=input.pages.statistics().livePages();try(var result=SourceProgramSnapshot.prepare(plan,qualified,input.pages,input.execution)) {
                assertEquals(oracle,emitted(result,input.pages,input.execution));assertEquals(plan.count(),result.count());assertEquals(plan.conditionalRuns(),result.conditionalRuns());assertEquals(plan.conditionalQueries(),result.conditionalQueries());
            }assertEquals(baseline,input.pages.statistics().livePages());assertTrue(input.source.unitCount()>0);
        }
    }
    @Test void allFourNominalLiteralsPreserveCompleteProgramResolutionAndStandaloneExecutableInventory()throws Exception {
        var publication=SourceCorrelationSnapshotTest.program(2);
        for(String path:List.of("source-possibility/unknown-branch-kill.source.json","source-possibility/unknown-copy-kill.source.json","source-possibility/unknown-branch-query.source.json","perform-reentry/reentry-values.source.json")) {
            var evidence=source(publication,path);var links=SourceCorrelationSnapshotTest.links(publication,evidence);var sites=new ArrayList<DependencySiteFact>();for(var link:links)sites.add(SourceTargetPlanTest.site(publication,link.operation(),false,true,false));
            compare(publication,evidence,List.of(),sites);compare(publication,evidence,links,sites);
        }
    }
    private static DependencySiteFact supported(DependencySiteFact site,List<DependencySiteFact.Candidate> candidates) {
        return new DependencySiteFact(site.caller(),site.entry(),site.sequence(),site.operation(),site.offset(),site.siteOrigin(),site.targetOrigin(),site.technology(),site.command(),site.namespace(),site.nameProfile(),site.targetKind(),site.subject(),site.valuePoint(),site.reachability(),DependencySiteFact.TargetStatus.RESOLVED_CANDIDATES,List.of(),candidates,site.modelValueRemainder(),site.sourceValueRemainder(),site.interpretationUnknownRemainder(),site.effectiveUnknownRemainder(),site.openControlRemainder(),site.evidence(),site.provenance(),site.premises(),site.uncertaintyRefs(),site.analysisStatus(),site.analysisReasons());
    }
    @Test void executableAndConditionalCandidatesMergeExactSupportsAndRetainPremisesWithStableTies()throws Exception {
        var publication=SourceCorrelationSnapshotTest.program(2);var evidence=source(publication,"source-possibility/unknown-copy-kill.source.json");var links=SourceCorrelationSnapshotTest.links(publication,evidence);var sites=new ArrayList<DependencySiteFact>();
        for(var link:links) {
            var first=new DependencySiteFact.Support(DependencySiteFact.SupportKind.VALUE_PRODUCER,link.operation(),link.origin(),List.of(new PremiseId(publication.id(),"Aa"),new PremiseId(publication.id(),"BB")));
            var second=new DependencySiteFact.Support(DependencySiteFact.SupportKind.CALL_LITERAL,link.operation(),link.origin(),List.of(new PremiseId(publication.id(),"BB"),new PremiseId(publication.id(),"Aa")));
            sites.add(supported(SourceTargetPlanTest.site(publication,link.operation(),false,true,false),List.of(new DependencySiteFact.Candidate("SECOND","SECOND  ",List.of(first,first,second)),new DependencySiteFact.Candidate("ZZZZ","ZZZZ    ",List.of(first)))));
        }
        compare(publication,evidence,links,sites);var oracle=expected(publication,evidence,links,sites);assertEquals("ZZZZ    ",oracle.get(0).path("candidates").get(0).path("rawValue").asText());var candidate=oracle.get(0).path("candidates").get(1);assertEquals("SECOND  ",candidate.path("rawValue").asText());assertEquals(4,candidate.path("supports").size());assertEquals(1,candidate.path("conditionalSupports").size());
    }
    @Test void literalAndConditionalProfilesPreserveKnownPlusRemainderWithoutInventingNames()throws Exception {
        var publication=SourceCorrelationSnapshotTest.program(1);
        compare(publication,source(publication,"qualified-source-r9/ordinary.source.json"),List.of(),List.of());
        var original=source(publication,"source-possibility/unknown-copy-kill.source.json");
        for(String profile:List.of(CicsNameInterpreter.PROFILE,"unsupported-program-profile@1")) {
            var root=(ObjectNode)JSON.readTree(CODEC.encode(original));for(var unit:root.path("units"))for(var occurrence:unit.path("occurrences")){((ObjectNode)occurrence).put("technology","CICS").put("command","LINK").put("nameProfile",profile);}
            var evidence=CODEC.decode(JSON.writeValueAsBytes(root));compare(publication,evidence,List.of(),List.of());var oracle=expected(publication,evidence,List.of(),List.of());
            assertTrue(oracle.get(0).path("valueRemainder").asBoolean());assertEquals(profile.equals(CicsNameInterpreter.PROFILE)?1:0,oracle.get(0).path("candidates").size());assertEquals(!profile.equals(CicsNameInterpreter.PROFILE),oracle.get(0).path("interpretationRemainder").asBoolean());
        }
    }
    @Test void foreignPreparedSourceAndOwnerExpiryCannotPublishOrLeakDerivedProgramResults()throws Exception {
        var publication=SourceCorrelationSnapshotTest.program(1);var evidence=source(publication,"source-possibility/unknown-copy-kill.source.json");var fixture=fixtures();
        try(var input=fixture.new NativeCase(publication,evidence);var correlations=SourceCorrelationSnapshot.prepare(input.source,input.program,List.of(),input.pages,input.execution);var qualified=SourceQualifiedSnapshot.prepare(input.source,input.pages,input.execution);var plan=SourceTargetPlan.prepare(correlations,qualified,SourceTargetPlanTest.result(publication,List.of()),input.pages,input.execution);var other=fixture.new NativeCase(publication,evidence);var foreign=SourceQualifiedSnapshot.prepare(other.source,other.pages,other.execution)) {
            long baseline=input.pages.statistics().livePages();assertThrows(IllegalArgumentException.class,()->SourceProgramSnapshot.prepare(plan,foreign,input.pages,input.execution));assertEquals(baseline,input.pages.statistics().livePages());
            try(var result=SourceProgramSnapshot.prepare(plan,qualified,input.pages,input.execution)){assertThrows(IndexOutOfBoundsException.class,()->result.candidateCount(-1));assertThrows(IllegalStateException.class,result::count);}assertEquals(baseline,input.pages.statistics().livePages());
            var result=SourceProgramSnapshot.prepare(plan,qualified,input.pages,input.execution);input.program.close();assertThrows(IllegalStateException.class,result::count);assertEquals(0,input.pages.statistics().livePages());for(var pool:AnalysisResources.Pool.values())assertEquals(0,input.execution.used(pool));
        }
    }
    private static void renameUnit(JsonNode node,String name){if(node instanceof ObjectNode object&&object.has("compilationUnitId"))object.put("compilationUnitId",name);for(var child:node)renameUnit(child,name);}
    @Test void completeSourceInventoryAndCandidateSupportWidthsSpillWithFixedResidentControls()throws Exception {
        long fixed=-1;var fixture=fixtures();
        for(int count:new int[]{1,4,16,64,256}) {
            var publication=SourceCorrelationSnapshotTest.program(1);var original=fixture.source(publication);var root=(ObjectNode)JSON.readTree(CODEC.encode(original));var units=(ArrayNode)root.path("units");var unit=units.get(0).deepCopy();units.removeAll();
            for(int i=0;i<count;i++){var copy=unit.deepCopy();renameUnit(copy,"synthetic-unit-"+i);units.add(copy);}var source=CODEC.decode(JSON.writeValueAsBytes(root));
            var operation=SourceCorrelationSnapshotTest.links(publication,original).getFirst().operation();var origin=publication.units().getFirst().sequences().getFirst().terminator().header().origin();var support=new DependencySiteFact.Support(DependencySiteFact.SupportKind.VALUE_PRODUCER,operation,origin,List.of(new PremiseId(publication.id(),"BB"),new PremiseId(publication.id(),"Aa")));
            var values=new ArrayList<DependencySiteFact.Candidate>();for(int i=count-1;i>=0;i--)values.add(new DependencySiteFact.Candidate(i%2==0?"A"+i:"ABCDEFGHIJ"+i,"raw-"+i,List.of(support,support)));var sites=List.of(supported(SourceTargetPlanTest.site(publication,operation,false,false,false),values));var oracle=expected(publication,source,List.of(),sites);
            try(var input=fixture.new NativeCase(publication,source);var correlations=SourceCorrelationSnapshot.prepare(input.source,input.program,List.of(),input.pages,input.execution);var qualified=SourceQualifiedSnapshot.prepare(input.source,input.pages,input.execution);var plan=SourceTargetPlan.prepare(correlations,qualified,SourceTargetPlanTest.result(publication,sites),input.pages,input.execution)) {
                long baseline=input.pages.statistics().livePages(),resident=input.execution.heapUsed();try(var result=SourceProgramSnapshot.prepare(plan,qualified,input.pages,input.execution)) {
                    long delta=input.execution.heapUsed()-resident;if(fixed<0)fixed=delta;assertEquals(fixed,delta);assertEquals(count+1,result.count());assertEquals(count,result.candidateCount(count));assertEquals(2,result.supportCount(count,0),"single contribution preserves original repeated supports");assertEquals(oracle,emitted(result,input.pages,input.execution));assertEquals(0,input.execution.used(AnalysisResources.Pool.SCRATCH));assertTrue(input.pages.statistics().evictions()>0);
                }assertEquals(baseline,input.pages.statistics().livePages());assertEquals(resident,input.execution.heapUsed());
            }
        }
    }
    private static void expire(SourceProgramSnapshot result){result.close();}
    @Test void finalFlushExpiryAndRootQuotaCannotCertifyOrConsumeBorrowedProgramFacts()throws Exception {
        var publication=SourceCorrelationSnapshotTest.program(1);var evidence=source(publication,"source-possibility/unknown-copy-kill.source.json");var fixture=fixtures();
        try(var input=fixture.new NativeCase(publication,evidence);var correlations=SourceCorrelationSnapshot.prepare(input.source,input.program,List.of(),input.pages,input.execution);var qualified=SourceQualifiedSnapshot.prepare(input.source,input.pages,input.execution);var plan=SourceTargetPlan.prepare(correlations,qualified,SourceTargetPlanTest.result(publication,List.of()),input.pages,input.execution);var result=SourceProgramSnapshot.prepare(plan,qualified,input.pages,input.execution)) {
            long baseline=input.pages.statistics().livePages(),resident=input.execution.heapUsed();var primary=new IOException("independent root publication quota");var failed=new OutputStream(){@Override public void write(int value)throws IOException{throw primary;}@Override public void write(byte[] data,int at,int count)throws IOException{throw primary;}};
            assertSame(primary,assertThrows(IOException.class,()->{var output=new JsonOutput(failed);try(var writer=SourceSnapshotWriter.open(input.source,output,input.pages,input.execution)){output.value(writer.programs(result));output.finish();writer.verify();}}));assertEquals(baseline,input.pages.statistics().livePages());assertEquals(resident,input.execution.heapUsed());assertTrue(result.count()>0);
            var retire=new OutputStream(){boolean fired;@Override public void write(int value){retire();}@Override public void write(byte[] value,int at,int count){retire();}private void retire(){if(!fired){fired=true;expire(result);}}};
            assertThrows(IllegalStateException.class,()->{var output=new JsonOutput(retire);try(var writer=SourceSnapshotWriter.open(input.source,output,input.pages,input.execution)){output.value(writer.programs(result));output.finish();writer.verify();}});assertTrue(retire.fired);assertTrue(input.source.unitCount()>0);assertTrue(plan.count()>0);
        }
    }
    @Test void fundingDenialAndEveryPrimitiveFaultRetainPrimaryAndBorrowedBackend()throws Exception {
        var publication=SourceCorrelationSnapshotTest.program(1);var evidence=source(publication,"source-possibility/unknown-copy-kill.source.json");var fixture=fixtures();
        try(var input=fixture.new NativeCase(publication,evidence);var correlations=SourceCorrelationSnapshot.prepare(input.source,input.program,List.of(),input.pages,input.execution);var qualified=SourceQualifiedSnapshot.prepare(input.source,input.pages,input.execution);var plan=SourceTargetPlan.prepare(correlations,qualified,SourceTargetPlanTest.result(publication,List.of()),input.pages,input.execution)) {
            long baseline=input.pages.statistics().livePages(),resident=input.execution.heapUsed();
            for(long available:new long[]{4095,4096+383,4096+384+511})try(var pressure=input.resources.reserve(AnalysisResources.Pool.RESIDENT,input.resources.limits().heapBytes()-input.resources.heapUsed()-available,AnalysisResources.Phase.SORT)) {
                assertNotNull(pressure);assertThrows(AnalysisResources.Exhausted.class,()->SourceProgramSnapshot.prepare(plan,qualified,input.pages,input.execution));assertEquals(baseline,input.pages.statistics().livePages());assertTrue(plan.count()>0);
            }assertEquals(resident,input.execution.heapUsed());
        }
        for(int fault=0;fault<4;fault++)try(var input=fixture.new NativeCase(publication,evidence,true);var correlations=SourceCorrelationSnapshot.prepare(input.source,input.program,List.of(),input.backend,input.execution);var qualified=SourceQualifiedSnapshot.prepare(input.source,input.backend,input.execution)) {
            long beforePlan=input.pages.statistics().livePages();try(var plan=SourceTargetPlan.prepare(correlations,qualified,SourceTargetPlanTest.result(publication,List.of()),input.backend,input.execution)) {
            var port=(SourceCorrelationSnapshotTest.FaultPages)input.backend;long baseline=input.pages.statistics().livePages();
            if(fault<3){port.arm(fault);assertSame(port.primary,assertThrows(PageStore.Failure.class,()->SourceProgramSnapshot.prepare(plan,qualified,port,input.execution)));}
            else try(var result=SourceProgramSnapshot.prepare(plan,qualified,port,input.execution)){port.arm(1);assertSame(port.primary,assertThrows(PageStore.Failure.class,()->result.candidateCount(0)));assertThrows(IllegalStateException.class,result::count);}
            try{input.source.snapshot();try{plan.count();assertEquals(baseline,input.pages.statistics().livePages());}catch(IllegalStateException expiredPlan){assertEquals(beforePlan,input.pages.statistics().livePages(),"failed borrowed plan retires only its own pages");}}catch(IllegalStateException expired){assertEquals(0,input.pages.statistics().livePages());}
            assertFalse(port.closed);assertEquals(publication.id(),input.program.publicationId());long borrowed=input.pages.allocate();input.pages.release(borrowed);
            }
        }
    }
}
