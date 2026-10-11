package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.analysis.dependencies.*;
import io.github.gustavo2358.analysis.dependencies.source.*;
import io.github.gustavo2358.analysis.cfg.application.*;
import io.github.gustavo2358.analysis.cfg.extension.SemanticInterpreterRegistry;
import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import java.io.ByteArrayOutputStream;
import java.util.*;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** SOURCE must own the same general native CALL result, not a detached bundle. */
final class ManagedSourceExecutableOwnerTest {
    @TempDir Path directory;
    private static ManagedExecutableDependencyResult owner(ManagedDependencyResult result)throws Exception {
        return (ManagedExecutableDependencyResult)result.getClass().getMethod("executableOwner").invoke(result);
    }
    private static void expire(SourceAdmissionSnapshot source){source.close();}
    private static void expire(SourceTargetPlan plan){plan.close();}
    private static void expire(ManagedExecutableDependencyResult result){result.close();}
    private static Publication literalCalls(int n){
        return W1dModelTest.model(0,u->{var sequences=new ArrayList<Sequence>();
            for(int i=n-1;i>=0;i--){var original=W1dModelTest.call(u,"call-"+i,i==n-1?"end":"s"+(i+1),null,false);
                var target=new Interactions.LiteralTarget("program","cobol.program","PG1",Interactions.ExactName.INSTANCE,original.header().origin());
                var invoke=new Operations.Invoke(original.header(),original.action(),target,original.arguments(),original.results(),original.signature(),original.effectOperands(),original.effectBound(),original.outcomes(),original.contract());
                sequences.add(new Sequence(new LabelId(u,i==0?"start":"s"+i),List.of(),invoke,ResultFixtures.origin(u.publication())));
            }sequences.add(ResultFixtures.returning(u,"end",List.of()));return sequences;});
    }
    private static QualifiedSourceDependencies literalEvidence(QualifiedSourceDependencies original)throws Exception {
        var codec=new QualifiedSourceJson();var json=new com.fasterxml.jackson.databind.ObjectMapper();
        var tree=json.readTree(codec.encode(original));
        for(var unit:tree.path("units"))for(var value:unit.path("occurrences")) {
            var occurrence=(com.fasterxml.jackson.databind.node.ObjectNode)value;
            occurrence.put("targetKind","LITERAL").put("valueRemainder",false);
            occurrence.putArray("values").addObject().put("logicalDomain","TEXT").put("value","PG1").put("logicalExtent",3);
        }
        return codec.decode(json.writeValueAsBytes(tree));
    }
    @Test void planningAndColdSupportSortingNeverDecodeWholeUnrelatedFactBodies()throws Exception {
        var fixture=new SourceCorrelationSnapshotTest();fixture.directory=directory;
        for(int n:new int[]{1,2,4,8,16}) {
            var publication=literalCalls(n);var evidence=literalEvidence(fixture.source(publication));
            try(var input=fixture.new NativeCase(publication,evidence)) {
                var cfg=new CfgBuildCoordinator(SemanticInterpreterRegistry.empty()).buildChecked(input.program,input.checked,BuildOptions.defaults());
                assertEquals(CfgBuildResult.Status.CFG_BUILT,cfg.status());
                try(var result=new DependencyAnalysis().prepareLeased(input.program,cfg,input.source,SourceCorrelationSnapshotTest.links(publication,evidence),input.pages,input.execution)) {
                    var call=owner(result);assertEquals(n,result.executable().sites().size());
                    assertEquals(1,result.programs().candidateCount(0));assertEquals(n,result.programs().supportCount(0,0));
                    assertEquals(n,call.fullFactProjections(),"only one full DTO per resolved source site; headers/support ordering must not decode full bodies n="+n);
                    var wire=new ByteArrayOutputStream();new DependencyJson().writeSnapshot(result,input.program,input.pages,input.execution,wire);
                    assertEquals(4L*n,call.fullFactProjections(),"sites+edges+SOURCE executableSites each visit once; supports use scoped typed leaves n="+n);
                    var json=new com.fasterxml.jackson.databind.ObjectMapper().readTree(wire.toByteArray());
                    assertEquals(n,json.path("sites").size());assertEquals(n,json.path("edges").size());
                    var candidate=json.path("dependencies").path("programs").get(0).path("candidates").get(0);
                    assertEquals("PG1",candidate.path("referenceName").asText());assertEquals("PG1",candidate.path("rawValue").asText());assertEquals(n,candidate.path("supports").size());
                    System.out.println("SOURCE_TYPED_SUPPORT_COST n="+n+" fullFacts="+call.fullFactProjections()+" typedSupports="+call.typedSupportProjections()+" outputBytes="+wire.size()+" resident="+input.execution.used(io.github.gustavo2358.analysis.solver.AnalysisResources.Pool.RESIDENT)+" scratch="+input.execution.used(io.github.gustavo2358.analysis.solver.AnalysisResources.Pool.SCRATCH)+" coldBytesRead="+input.pages.statistics().bytesRead()+" evictions="+input.pages.statistics().evictions());
                }
            }
        }
    }
    @Test void admittedSourceOwnsGeneralCallCursorAndExpiresItWithoutClosingBorrowedAir()throws Exception {
        var publication=SourceCorrelationSnapshotTest.program(4);
        var fixture=new SourceCorrelationSnapshotTest();fixture.directory=directory;
        var evidence=fixture.source(publication);
        try(var input=fixture.new NativeCase(publication,evidence)) {
            var cfg=new CfgBuildCoordinator(SemanticInterpreterRegistry.empty()).buildChecked(input.program,input.checked,BuildOptions.defaults());
            assertEquals(CfgBuildResult.Status.CFG_BUILT,cfg.status());
            try(var result=new DependencyAnalysis().prepareLeased(input.program,cfg,input.source,SourceCorrelationSnapshotTest.links(publication,evidence),input.pages,input.execution)) {
                var call=owner(result);assertSame(call.result(),result.executable());assertEquals(4,call.result().sites().size());
                var sites=call.result().sites();long scratch=input.execution.used(io.github.gustavo2358.analysis.solver.AnalysisResources.Pool.SCRATCH);
                try(var cursor=call.cursor()) {
                    assertTrue(cursor.advance());assertSame(cursor.fact(),cursor.fact());
                    assertTrue(input.execution.used(io.github.gustavo2358.analysis.solver.AnalysisResources.Pool.SCRATCH)>scratch);
                    expire(input.source);
                    assertThrows(IllegalStateException.class,call::result);assertThrows(IllegalStateException.class,sites::size);
                    assertThrows(IllegalStateException.class,cursor::fact);assertThrows(IllegalStateException.class,result::executable);
                    assertEquals(0,input.pages.statistics().livePages());
                    assertEquals(publication.id(),input.program.publicationId());assertTrue(input.checked.result().isStructurallyValid());
                }
            }
        }
    }
    @Test void closingSourcePlanRetiresOnlyItsLiveSiteAndSupportScopesNotBorrowedCallOwner()throws Exception {
        var publication=literalCalls(2);var fixture=new SourceCorrelationSnapshotTest();fixture.directory=directory;
        var evidence=literalEvidence(fixture.source(publication));
        try(var input=fixture.new NativeCase(publication,evidence)) {
            var cfg=new CfgBuildCoordinator(SemanticInterpreterRegistry.empty()).buildChecked(input.program,input.checked,BuildOptions.defaults());
            try(var result=new DependencyAnalysis().prepareLeased(input.program,cfg,input.source,SourceCorrelationSnapshotTest.links(publication,evidence),input.pages,input.execution)) {
                var call=owner(result);var plan=result.programs().plan();long before=input.execution.used(io.github.gustavo2358.analysis.solver.AnalysisResources.Pool.SCRATCH);
                try(var site=plan.projectSite(0,0);var support=plan.projectSupport(0,0,0,0)) {
                    assertEquals("PG1",site.fact().candidates().getFirst().referenceName());assertNotNull(support.support());
                    assertTrue(input.execution.used(io.github.gustavo2358.analysis.solver.AnalysisResources.Pool.SCRATCH)>before);
                    expire(plan);assertThrows(IllegalStateException.class,site::fact);assertThrows(IllegalStateException.class,support::support);
                    assertEquals(before,input.execution.used(io.github.gustavo2358.analysis.solver.AnalysisResources.Pool.SCRATCH),"SOURCE_PLAN_CLOSE_LEFT_LIVE_PROJECTION_FUNDING_RED");
                    assertEquals(2,call.result().sites().size());assertTrue(input.source.unitCount()>0);assertEquals(publication.id(),input.program.publicationId());
                }
            }
        }
    }
    @Test void ownedStatusCannotShortCircuitExpiryThroughAlreadyKnownPartialReasons()throws Exception {
        var publication=SourceCorrelationSnapshotTest.program(1);var fixture=new SourceCorrelationSnapshotTest();fixture.directory=directory;
        var evidence=fixture.source(publication);
        try(var input=fixture.new NativeCase(publication,evidence)) {
            var cfg=new CfgBuildCoordinator(SemanticInterpreterRegistry.empty()).buildChecked(input.program,input.checked,BuildOptions.defaults());
            try(var call=new DependencyAnalysis().prepareManaged(input.program,cfg,input.pages,input.execution)) {
                var base=call.result();
                var partial=new DependencyResult(base.publication(),base.airVersion(),base.sites(),base.edges(),base.metrics(),base.publicationInventory(),base.origins(),base.artifacts(),base.sourceUncertaintyRefs(),List.of("UNPROVED_OPERATION_PRECONDITION"),base.fileDependencies(),base.sourceDependencies(),base.sourceQualifiedDependencies(),base.programDependencies());
                assertTrue(partial.partial());expire(call);
                assertThrows(IllegalStateException.class,partial::partial,"OWNED_PARTIAL_STATUS_BYPASSED_EXPIRED_RESULT_RED");
                assertThrows(IllegalStateException.class,partial::structuralScope);
                assertEquals(publication.id(),input.program.publicationId());assertTrue(input.source.unitCount()>0);
            }
        }
    }
}
