package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.air.model.Publication;
import io.github.gustavo2358.analysis.cfg.application.*;
import io.github.gustavo2358.analysis.cfg.extension.SemanticInterpreterRegistry;
import io.github.gustavo2358.analysis.dependencies.*;
import io.github.gustavo2358.analysis.solver.*;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Native owner metadata must augment, not replace, stored fact completeness. */
@SuppressWarnings("try")
final class NativeFactAnnotationTest {
    @TempDir Path directory;
    private static byte[] legacy(DependencySiteFact fact)throws Exception {
        var method=DependencyJson.class.getDeclaredMethod("site",DependencySiteFact.class,boolean.class);method.setAccessible(true);
        var fields=(JsonOutput.Fields)method.invoke(null,fact,true);fields.put("coverage","PARTIAL");
        var bytes=new ByteArrayOutputStream();var out=new JsonOutput(bytes);out.value(fields);out.finish();return bytes.toByteArray();
    }
    @Test void eachFundedNativeAnnotationCombinationPreservesDetachedFactStatusReasonsAndWholeSiteBytes()throws Exception {
        var fixture=new SourceCorrelationSnapshotTest();fixture.directory=directory;var publication=SourceCorrelationSnapshotTest.program(1);var evidence=fixture.source(publication);
        try(var input=fixture.new NativeCase(publication,evidence)) {
            var cfg=new CfgBuildCoordinator(SemanticInterpreterRegistry.empty()).buildChecked(input.program,input.checked,BuildOptions.defaults());
            try(var owner=new DependencyAnalysis().prepareManaged(input.program,cfg,input.pages,input.execution)) {
                // Synthetic owner-metadata vectors isolate the existing overlay
                // contract. They do not claim to prove the rules that set flags.
                var field=ManagedExecutableDependencyResult.class.getDeclaredField("annotations");field.setAccessible(true);var annotations=(PagedLongArray)field.get(owner);
                var assertions=new ArrayList<org.junit.jupiter.api.function.Executable>();
                for(int mask=0;mask<8;mask++){int flags=mask;assertions.add(()->{
                    annotations.set(0,flags);DependencySiteFact want;try(var projection=owner.projectSite(0)){want=projection.fact();}
                    assertEquals(flags==0?DependencySiteFact.AnalysisStatus.COMPLETE:DependencySiteFact.AnalysisStatus.PARTIAL,want.analysisStatus());
                    List<String> reasons=new ArrayList<>();if((flags&1)!=0)reasons.add("PHYSICAL_PROPAGATION_DISABLED");if((flags&2)!=0)reasons.add("PARTIAL_CONTROL_PROJECTION");if((flags&4)!=0)reasons.add("UNPROVED_OPERATION_PRECONDITION");reasons.sort(String::compareTo);assertEquals(reasons,want.analysisReasons());
                    byte[] expected=legacy(want);long before=owner.fullFactProjections();
                    try(var root=input.execution.reserve(AnalysisResources.Pool.RESIDENT,65536+4096,AnalysisResources.Phase.ENCODE);var fact=owner.openFact(0)) {
                        assertEquals(65536+4096,root.amount());var bytes=new ByteArrayOutputStream();var out=new JsonOutput(bytes);new DependencyJson.FactWriter(out,input.pages,input.execution).site(fact,"PARTIAL");out.finish();
                        assertArrayEquals(expected,bytes.toByteArray(),"NATIVE_FACT_ANNOTATION_OVERLAY_LOST mask="+flags);
                    }assertEquals(before,owner.fullFactProjections());assertEquals(0,input.execution.used(AnalysisResources.Pool.SCRATCH));
                });}
                assertAll("NATIVE_ANNOTATION_SEVEN_OVERLAYS",assertions);
            }
        }
    }
    @Test void actualPhysicalDisabledSourceProductRetainsKnownCandidateAndPerSiteReason()throws Exception {
        Publication publication=ValueToCallEvidenceTest.fixture(List.of(ResultFixtures.assign(ValueToCallEvidenceTest.U,"literal",ValueToCallEvidenceTest.A,ValueToCallEvidenceTest.RAW),ValueToCallEvidenceTest.copy(),ResultFixtures.assign(ValueToCallEvidenceTest.U,"later",ValueToCallEvidenceTest.A,"CHANGED ")),false);
        var fixture=new SourceCorrelationSnapshotTest();fixture.directory=directory;var evidence=fixture.source(publication);var links=SourceCorrelationSnapshotTest.links(publication,evidence);
        try(var input=fixture.new NativeCase(publication,evidence,false,1_000_000)) {
            var cfg=new CfgBuildCoordinator(SemanticInterpreterRegistry.empty()).buildChecked(input.program,input.checked,BuildOptions.defaults());
            var analysis=new DependencyAnalysis();var old=analysis.prepareLeased(input.program,cfg,evidence,links,input.pages,input.execution);
            var expected=old.sites().getFirst();assertEquals(List.of("PROGA"),expected.candidates().stream().map(DependencySiteFact.Candidate::referenceName).toList());assertTrue(expected.analysisReasons().contains("PHYSICAL_PROPAGATION_DISABLED"));
            var oracle=new ByteArrayOutputStream();new DependencyJson().writeSnapshot(old,input.program,oracle);
            try(var managed=analysis.prepareLeased(input.program,cfg,input.source,links,input.pages,input.execution)) {
                var bytes=new ByteArrayOutputStream();new DependencyJson().writeSnapshot(managed,input.program,input.pages,input.execution,bytes);
                var json=new com.fasterxml.jackson.databind.ObjectMapper();assertEquals(json.readTree(oracle.toByteArray()),json.readTree(bytes.toByteArray()),"PRODUCTIVE_SOURCE_FACT_ANNOTATION_LOST");
            }
        }
    }
}
