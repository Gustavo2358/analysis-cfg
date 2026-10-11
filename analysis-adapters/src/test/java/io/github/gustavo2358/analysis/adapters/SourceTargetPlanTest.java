package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.analysis.dependencies.*;
import io.github.gustavo2358.analysis.dependencies.source.*;
import io.github.gustavo2358.analysis.solver.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

final class SourceTargetPlanTest {
    @TempDir Path directory;
    private SourceCorrelationSnapshotTest fixtures(){var result=new SourceCorrelationSnapshotTest();result.directory=directory;return result;}
    static DependencyResult result(Publication publication,List<DependencySiteFact> sites){return new DependencyResult(publication.id(),publication.airVersion(),sites,List.of(),Map.of(),publication.coverage().inventory(),publication.origins(),publication.artifacts(),List.of());}
    static DependencySiteFact site(Publication publication,OperationId operation,boolean unreachable,Boolean model,boolean source) {
        var unit=publication.units().stream().filter(u->u.id().equals(operation.unit())).findFirst().orElseThrow();var entry=unit.entries().getFirst().id();
        var sequence=unit.sequences().stream().filter(s->s.terminator().header().id().equals(operation)).findFirst().orElseThrow();var invoke=(Operations.Invoke)sequence.terminator();var origin=invoke.header().origin();
        return new DependencySiteFact(unit.id(),entry,sequence.label(),operation,sequence.instructions().size(),origin,origin,"COBOL","CALL","cobol.program",CallNameInterpreter.PROFILE,DependencySiteFact.TargetKind.COMPUTED,new ObjectId(unit.id(),"object-0"),null,
            unreachable?DependencySiteFact.Reachability.UNREACHABLE_IN_MODEL:DependencySiteFact.Reachability.REACHABLE,unreachable?DependencySiteFact.TargetStatus.UNREACHABLE_IN_MODEL:DependencySiteFact.TargetStatus.OPEN_TARGET,List.of(),List.of(),unreachable?null:model,source,false,!unreachable&&Boolean.TRUE.equals(model),false,List.of(),List.of(),List.of(),List.of());
    }
    private static QualifiedSourceDependencies literal(Publication p,String path)throws Exception {
        var evidence=new QualifiedSourceJson().decode(Files.readAllBytes(Path.of("src/test/resources").resolve(path)));
        return new QualifiedSourceDependencies(evidence.schema(),evidence.version(),evidence.producer(),evidence.source(),List.of(new QualifiedSourceDependencies.AirCorrelation(p.id().localId(),"0".repeat(64))),evidence.units());
    }
    @Test void closedExecutableRefutationsAndOpenQueriesMatchTheExactTargetResolverSelectionLaw()throws Exception {
        var fixture=fixtures();var publication=SourceCorrelationSnapshotTest.program(4);var evidence=fixture.source(publication);var links=SourceCorrelationSnapshotTest.links(publication,evidence);
        var occurrence=new DependencyInput(publication,Optional.of(evidence),links).occurrences().getFirst();
        for(int scenario=0;scenario<6;scenario++) {
            Boolean model=switch(scenario){case 0,1,5->Boolean.FALSE;case 2->null;default->Boolean.TRUE;};
            var sites=new ArrayList<DependencySiteFact>();for(var link:links)sites.add(site(publication,link.operation(),scenario==0,model,scenario>=4));
            boolean expected=scenario>=2&&scenario<=4;assertEquals(expected,TargetResolver.requiresSourceValues(occurrence,sites));
            try(var input=fixture.new NativeCase(publication,evidence);var correlations=SourceCorrelationSnapshot.prepare(input.source,input.program,links,input.pages,input.execution);var qualified=SourceQualifiedSnapshot.prepare(input.source,input.pages,input.execution)) {
                long baseline=input.pages.statistics().livePages();try(var plan=SourceTargetPlan.prepare(correlations,qualified,result(publication,sites),input.pages,input.execution)) {
                    assertEquals(1,plan.count());assertEquals(0,plan.sourceUnit(0));assertEquals(0,plan.sourceOrdinal(0));assertEquals(4,plan.siteCount(0));assertEquals(expected,plan.requiresNominal(0));assertEquals(-1,plan.queryOrdinal(0));assertEquals(0,plan.conditionalQueries());
                    for(int i=0;i<sites.size();i++)assertEquals(occurrence.executableOperations().get(i),plan.site(0,i).operation());
                }assertEquals(baseline,input.pages.statistics().livePages());
            }
        }
    }
    @Test void partialAndMissingCorrelationsKeepEveryStandaloneExecutableOccurrenceInOriginalAirOrder()throws Exception {
        var fixture=fixtures();var publication=SourceCorrelationSnapshotTest.program(4);var evidence=fixture.source(publication);var links=SourceCorrelationSnapshotTest.links(publication,evidence);
        for(int linked:new int[]{0,1,3,4}) {
            var selected=links.subList(0,linked);var expected=new DependencyInput(publication,Optional.of(evidence),selected).occurrences();
            try(var input=fixture.new NativeCase(publication,evidence);var correlations=SourceCorrelationSnapshot.prepare(input.source,input.program,selected,input.pages,input.execution);var qualified=SourceQualifiedSnapshot.prepare(input.source,input.pages,input.execution);var plan=SourceTargetPlan.prepare(correlations,qualified,result(publication,List.of()),input.pages,input.execution)) {
                assertEquals(expected.size(),plan.count());for(int i=0;i<expected.size();i++) {
                    var occurrence=expected.get(i);assertEquals(occurrence.targetKind(),plan.targetKind(i));assertEquals(occurrence.source().isPresent()?0:-1,plan.sourceUnit(i));assertEquals(occurrence.executableOperations().size(),plan.operationCount(i));
                    for(int op=0;op<occurrence.executableOperations().size();op++)assertEquals(occurrence.executableOperations().get(op),plan.operation(i,op));
                    assertEquals(TargetResolver.requiresSourceValues(occurrence,List.of()),plan.requiresNominal(i));
                }
            }
        }
    }
    @Test void sourceControlPossibilityPreservesNominalDemandDespiteClosedExecutableSubset()throws Exception {
        var fixture=fixtures();var publication=SourceCorrelationSnapshotTest.program(1);var evidence=literal(publication,"source-possibility/unknown-copy-kill.source.json");var link=SourceCorrelationSnapshotTest.links(publication,evidence).getFirst();
        var sites=List.of(site(publication,link.operation(),false,false,false));var expected=new DependencyInput(publication,Optional.of(evidence),List.of(link)).occurrences();
        try(var input=fixture.new NativeCase(publication,evidence);var correlations=SourceCorrelationSnapshot.prepare(input.source,input.program,List.of(link),input.pages,input.execution);var qualified=SourceQualifiedSnapshot.prepare(input.source,input.pages,input.execution);var plan=SourceTargetPlan.prepare(correlations,qualified,result(publication,sites),input.pages,input.execution)) {
            assertEquals(2,plan.conditionalQueries());assertEquals(1,plan.conditionalRuns());assertEquals(2,plan.count());
            for(int i=0;i<expected.size();i++){var occurrence=expected.get(i);assertTrue(occurrence.sourceControlRemainder());assertTrue(plan.sourceControlRemainder(i));assertTrue(plan.requiresNominal(i));assertTrue(plan.queryOrdinal(i)>=0);assertTrue(plan.requestsQuery(0,plan.queryOrdinal(i)));assertEquals(TargetResolver.requiresSourceValues(occurrence,i==0?sites:List.of()),plan.requiresNominal(i));}
        }
    }
    @Test void foreignResultContextInvalidQueriesAndOwnerExpiryCannotLeakPlanOrStealParents()throws Exception {
        var fixture=fixtures();var publication=SourceCorrelationSnapshotTest.program(1);var evidence=fixture.source(publication);var links=SourceCorrelationSnapshotTest.links(publication,evidence);
        try(var input=fixture.new NativeCase(publication,evidence);var correlations=SourceCorrelationSnapshot.prepare(input.source,input.program,links,input.pages,input.execution);var qualified=SourceQualifiedSnapshot.prepare(input.source,input.pages,input.execution)) {
            long baseline=input.pages.statistics().livePages();var foreign=new AnalysisResources(input.resources.limits());assertThrows(IllegalArgumentException.class,()->SourceTargetPlan.prepare(correlations,qualified,result(publication,List.of()),input.pages,foreign));
            var other=new DependencyResult(new PublicationId("foreign-target-plan"),publication.airVersion(),List.of(),List.of(),Map.of(),publication.coverage().inventory(),publication.origins(),publication.artifacts(),List.of());
            assertThrows(IllegalArgumentException.class,()->SourceTargetPlan.prepare(correlations,qualified,other,input.pages,input.execution));assertEquals(baseline,input.pages.statistics().livePages());
            try(var plan=SourceTargetPlan.prepare(correlations,qualified,result(publication,List.of()),input.pages,input.execution)){assertThrows(IndexOutOfBoundsException.class,()->plan.site(0,0));assertThrows(IllegalStateException.class,plan::count);}assertEquals(baseline,input.pages.statistics().livePages());
            var plan=SourceTargetPlan.prepare(correlations,qualified,result(publication,List.of()),input.pages,input.execution);input.program.close();assertThrows(IllegalStateException.class,plan::count);assertEquals(0,input.pages.statistics().livePages());for(var pool:AnalysisResources.Pool.values())assertEquals(0,input.execution.used(pool));
        }
    }
    @Test void equalQueryHandlesInDifferentFullUnitsNeverShareDemandAndUnavailableControlCannotRequestValues()throws Exception {
        var fixture=fixtures();var publication=SourceCorrelationSnapshotTest.program(1);var original=literal(publication,"source-possibility/unknown-copy-kill.source.json");var codec=new QualifiedSourceJson();var json=new ObjectMapper();
        for(boolean unavailable:List.of(false,true)) {
            var root=(ObjectNode)json.readTree(codec.encode(original));var units=(ArrayNode)root.path("units");var second=(ObjectNode)units.get(0).deepCopy();units.add(second);
            for(var owner:second.findParents("compilationUnitId"))((ObjectNode)owner).put("compilationUnitId","synthetic-other-unit.cbl");
            if(unavailable) {
                second.put("controlAvailable",false);for(String field:List.of("nodes","derivations","selections","events","guards","proofs","frontiers"))second.set(field,json.createArrayNode());
                for(var occurrence:second.path("occurrences"))((ObjectNode)occurrence).set("qualifications",json.createArrayNode());
            }
            var evidence=codec.decode(json.writeValueAsBytes(root));
            try(var input=fixture.new NativeCase(publication,evidence);var correlations=SourceCorrelationSnapshot.prepare(input.source,input.program,List.of(),input.pages,input.execution);var qualified=SourceQualifiedSnapshot.prepare(input.source,input.pages,input.execution);var plan=SourceTargetPlan.prepare(correlations,qualified,result(publication,List.of()),input.pages,input.execution)) {
                assertEquals(5,plan.count());assertEquals(unavailable?2:4,plan.conditionalQueries());assertEquals(unavailable?1:2,plan.conditionalRuns());
                for(int unit=0;unit<2;unit++)for(int q=0;q<2;q++)assertEquals(unit==0||!unavailable,plan.requestsQuery(unit,q));
                for(int item=0;item<4;item++){assertEquals(item/2,plan.sourceUnit(item));assertEquals(item%2,plan.queryOrdinal(item));assertEquals(item<2||!unavailable,plan.requiresNominal(item));}
                assertEquals(-1,plan.sourceUnit(4));assertFalse(plan.requiresNominal(4));
            }
        }
    }
    @Test void growingSiteIncidenceKeepsFixedResidentPlanControlsAndPreservesEverySelectedSite()throws Exception {
        long fixed=-1;var fixture=fixtures();
        for(int count:new int[]{1,4,16,64,256}) {
            var publication=SourceCorrelationSnapshotTest.program(count);var evidence=fixture.source(publication);var links=SourceCorrelationSnapshotTest.links(publication,evidence);var sites=new ArrayList<DependencySiteFact>();for(var link:links)sites.add(site(publication,link.operation(),false,true,false));
            var expected=new DependencyInput(publication,Optional.of(evidence),links).occurrences().getFirst().executableOperations();
            try(var input=fixture.new NativeCase(publication,evidence);var correlations=SourceCorrelationSnapshot.prepare(input.source,input.program,links,input.pages,input.execution);var qualified=SourceQualifiedSnapshot.prepare(input.source,input.pages,input.execution)) {
                long baseline=input.pages.statistics().livePages(),resident=input.execution.used(AnalysisResources.Pool.RESIDENT);
                try(var plan=SourceTargetPlan.prepare(correlations,qualified,result(publication,sites),input.pages,input.execution)) {
                    long delta=input.execution.used(AnalysisResources.Pool.RESIDENT)-resident;if(fixed<0)fixed=delta;assertEquals(fixed,delta);assertEquals(1,plan.count());assertEquals(count,plan.siteCount(0));assertEquals(count,plan.operationCount(0));assertTrue(plan.requiresNominal(0));
                    for(int i=0;i<count;i++)assertEquals(expected.get(i),plan.site(0,i).operation());assertEquals(0,input.execution.used(AnalysisResources.Pool.SCRATCH));
                }assertEquals(baseline,input.pages.statistics().livePages());
            }
        }
    }
}
