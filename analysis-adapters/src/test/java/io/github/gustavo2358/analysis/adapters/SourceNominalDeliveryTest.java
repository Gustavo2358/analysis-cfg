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

final class SourceNominalDeliveryTest {
    @TempDir Path directory;
    private static final ObjectMapper JSON=new ObjectMapper();
    private static final QualifiedSourceJson CODEC=new QualifiedSourceJson();
    private static final String[] LITERALS={"source-possibility/unknown-branch-kill.source.json","source-possibility/unknown-copy-kill.source.json","source-possibility/unknown-branch-query.source.json","perform-reentry/reentry-values.source.json"};
    private static byte[] literal(int ordinal)throws Exception{return Files.readAllBytes(Path.of("src/test/resources").resolve(LITERALS[ordinal]));}
    private static AnalysisResources resources(long output){return new AnalysisResources(new AnalysisResources.Limits(1048576,16384,0,256L*1024*1024,4,Long.MAX_VALUE,output));}
    private static void zero(AnalysisResources resources){for(var pool:AnalysisResources.Pool.values())assertEquals(0,resources.used(pool));}
    private static void expire(SourceNominalCandidates result){result.close();}
    private static SourceAdmissionSnapshot admit(byte[] wire,PageStore pages,AnalysisResources resources)throws Exception{return SourceAdmissionSnapshot.admit(CODEC.decodeSnapshot(new ByteArrayInputStream(wire),pages,resources),pages,resources);}
    private static ArrayNode expected(QualifiedSourceDependencies.UnitEvidence unit,String statement) {
        var out=JSON.createArrayNode();var provider=new SourceValuesProvider(unit,Set.of(statement));
        for(var candidate:provider.candidates(statement)) {
            var row=out.addObject();row.put("rawValue",candidate.rawValue());var support=row.putArray("conditionalSupports").addObject();var s=candidate.support();
            support.put("provider",s.provider());support.put("analysisBoundary","NON_EXECUTABLE_SOURCE");support.set("assumptions",JSON.valueToTree(s.assumptions()));
            var evidence=support.putArray("evidence");for(var e:s.evidence()){var value=evidence.addObject();value.put("kind",e.kind());value.put("reference",e.reference());value.set("provenance",JSON.valueToTree(QualifiedSourceJson.value(e.provenance())));}
            var uncertainties=support.putArray("uncertainties");for(var u:s.uncertainties()){var value=uncertainties.addObject();value.put("id",u.id());value.put("kind",u.kind());value.set("provenance",JSON.valueToTree(QualifiedSourceJson.value(u.provenance())));}
        }return out;
    }
    private static void emitted(SourceNominalCandidates result,long query,JsonOutput output,PageStore pages,AnalysisResources resources)throws Exception {
        try(var writer=SourceSnapshotWriter.open(result.admission(),output,pages,resources)) {
            output.symbol('[');
            for(long c=0,n=result.candidateCount(query);c<n;c++) {
                if(c!=0)output.symbol(',');long candidate=c;
                output.value(new JsonOutput.Fields("rawValue",writer.nominalRaw(result,query,candidate),"conditionalSupports",List.of(writer.conditionalSupport(result,query,candidate))));
            }
            output.symbol(']');output.finish();writer.verify();
        }
    }
    @Test void allFourNominalLiteralsEmitEveryQueryCandidateSupportAssumptionAndUncertainty()throws Exception {
        int files=0,queries=0;
        for(int i=0;i<LITERALS.length;i++) {
            byte[] wire=literal(i);var resident=CODEC.decode(wire);var resources=resources(0);
            try(var pages=new FilePageStore(directory,4096,4,resources,AnalysisResources.Phase.ENCODE);var admission=admit(wire,pages,resources);var control=SourceControlSnapshot.prepare(admission,pages,resources)) {
                for(int u=0;u<resident.units().size();u++) {
                    var unit=resident.units().get(u);if(unit.nominalValues().isEmpty())continue;
                    try(var values=SourceNominalCandidates.prepare(admission,u,q->true,control,pages,resources)) {
                        for(int q=0;q<unit.nominalValues().orElseThrow().facts().queries().size();q++) {
                            var bytes=new ByteArrayOutputStream();emitted(values,q,new JsonOutput(bytes),pages,resources);assertEquals(expected(unit,unit.nominalValues().orElseThrow().facts().queries().get(q).statement()),JSON.readTree(bytes.toByteArray()),LITERALS[i]+"/query"+q);queries++;
                        }
                    }
                }
            }zero(resources);files++;
        }assertEquals(4,files);assertTrue(queries>=4);
    }
    @Test void largeUtf16CandidateStreamsWithoutWholeStringProjectionAndKeepsExactRawAndSupport()throws Exception {
        var tree=(ObjectNode)JSON.readTree(literal(1));var facts=(ObjectNode)tree.path("units").get(0).path("nominalValues").path("facts");
        String raw="ab🚀\u0000".repeat(8192)+"\uD800x\uDC00";var write=(ObjectNode)facts.path("assignments").get(0);((ObjectNode)write.path("source")).put("value",raw);
        boolean changed=false;for(var symbol:facts.path("symbols")){((ObjectNode)symbol).put("extent",raw.codePointCount(0,raw.length()));changed=true;}assertTrue(changed);
        byte[] wire=JSON.writeValueAsBytes(tree);var resident=CODEC.decode(wire);var resources=resources(0);
        try(var pages=new FilePageStore(directory,4096,4,resources,AnalysisResources.Phase.ENCODE);var admission=admit(wire,pages,resources);var control=SourceControlSnapshot.prepare(admission,pages,resources);var result=SourceNominalCandidates.prepare(admission,0,q->true,control,pages,resources)) {
            var unit=resident.units().getFirst();boolean seen=false;
            for(int q=0;q<unit.nominalValues().orElseThrow().facts().queries().size();q++) {
                var oracle=expected(unit,unit.nominalValues().orElseThrow().facts().queries().get(q).statement());var bytes=new ByteArrayOutputStream();emitted(result,q,new JsonOutput(bytes),pages,resources);assertEquals(oracle,JSON.readTree(bytes.toByteArray()));
                for(var candidate:oracle)seen|=candidate.path("rawValue").asText().equals(raw);
            }assertTrue(seen,"whole UTF16 candidate must reach at least one original query");
        }zero(resources);
    }
    @Test void foreignAdmissionAndLastFlushExpiryCannotCertifyBorrowedNominalResults()throws Exception {
        var resources=resources(0);byte[] wire=literal(1);
        try(var pages=new FilePageStore(directory,4096,4,resources,AnalysisResources.Phase.ENCODE);var admission=admit(wire,pages,resources);var foreign=admit(wire,pages,resources);var control=SourceControlSnapshot.prepare(admission,pages,resources);var result=SourceNominalCandidates.prepare(admission,0,q->true,control,pages,resources)) {
            long baseline=pages.statistics().livePages();try(var writer=SourceSnapshotWriter.open(foreign,new JsonOutput(new ByteArrayOutputStream()),pages,resources)){assertThrows(IllegalArgumentException.class,()->writer.conditionalSupport(result,0,0));}assertEquals(baseline,pages.statistics().livePages());
            var sink=new OutputStream(){boolean fired;@Override public void write(int value){retire();}@Override public void write(byte[] value,int at,int count){retire();}private void retire(){if(!fired){fired=true;expire(result);}}};
            assertThrows(IllegalStateException.class,()->emitted(result,0,new JsonOutput(sink),pages,resources));assertTrue(sink.fired);assertTrue(admission.unitCount()>0);assertTrue(foreign.unitCount()>0);
        }zero(resources);
    }
    @Test void rootOutputMeterIsBorrowedOnceAndQuotaFailurePreservesPreparedCandidates()throws Exception {
        var resources=resources(1);byte[] wire=literal(1);
        try(var pages=new FilePageStore(directory,4096,4,resources,AnalysisResources.Phase.ENCODE);var admission=admit(wire,pages,resources);var control=SourceControlSnapshot.prepare(admission,pages,resources);var result=SourceNominalCandidates.prepare(admission,0,q->true,control,pages,resources)) {
            long baseline=pages.statistics().livePages(),count=result.candidateCount(0);var bytes=new ByteArrayOutputStream();var meter=new OutputStream(){@Override public void write(int value)throws IOException{resources.output(1,AnalysisResources.Phase.ENCODE);bytes.write(value);}@Override public void write(byte[] value,int at,int count)throws IOException{resources.output(count,AnalysisResources.Phase.ENCODE);bytes.write(value,at,count);}};
            var failure=assertThrows(AnalysisResources.Exhausted.class,()->emitted(result,0,new JsonOutput(meter),pages,resources));assertEquals(AnalysisResources.Resource.OUTPUT,failure.resource());assertEquals(0,bytes.size());assertEquals(baseline,pages.statistics().livePages());assertEquals(count,result.candidateCount(0));assertTrue(count>0);
        }zero(resources);
    }
}
