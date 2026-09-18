package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.analysis.dependencies.*;
import io.github.gustavo2358.analysis.values.StorageAnalysisMode;
import java.util.*;
import java.io.ByteArrayOutputStream;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static io.github.gustavo2358.analysis.adapters.ResultFixtures.*;
import static io.github.gustavo2358.analysis.adapters.ValueToCallEvidenceTest.*;

/** Operational default, separate from experimental physical-fragment oracles. */
@org.junit.jupiter.api.Timeout(10)
class LogicalOnlyDependencyTest {
    @Test void nominalCopyPreservesSnapshotCandidateAndPublishesPartialReason() throws Exception {
        var p=fixture(List.of(assign(U,"literal",A,RAW),copy(),assign(U,"later",A,"CHANGED ")),false);
        var result=new DependencyAnalysis().prepare(p);var site=result.sites().getFirst();
        assertEquals(List.of("PROGA"),site.candidates().stream().map(DependencySiteFact.Candidate::referenceName).toList());
        assertTrue(site.modelValueRemainder());assertTrue(site.effectiveUnknownRemainder());
        assertEquals(DependencySiteFact.AnalysisStatus.PARTIAL,site.analysisStatus());
        assertTrue(site.analysisReasons().contains("PHYSICAL_PROPAGATION_DISABLED"));
        assertFalse(site.candidates().getFirst().supports().isEmpty());
        assertEquals(0L,result.metrics().get("physicalGroupsApplied"));assertEquals(0L,result.metrics().get("physicalWritesApplied"));
        var first=new ByteArrayOutputStream();new DependencyJson().write(result,first);
        var second=new ByteArrayOutputStream();new DependencyJson().write(new DependencyAnalysis().prepare(p),second);
        assertArrayEquals(first.toByteArray(),second.toByteArray());assertTrue(first.toString(java.nio.charset.StandardCharsets.UTF_8).contains("logicalOnlyMode"));
    }
    @Test void unresolvedPhysicalSliceDoesNotEnablePhysicalFallback() {
        var p=ConsumerCoverageTest.model("LINK","simple","slice");
        var result=new DependencyAnalysis().prepare(p);var site=result.sites().getFirst();
        assertTrue(site.candidates().isEmpty());assertTrue(site.effectiveUnknownRemainder());
        assertEquals(0L,result.metrics().get("physicalGroupsApplied"));
        var physical=new DependencyAnalysis(StorageAnalysisMode.EXPERIMENTAL_PHYSICAL).prepare(p);
        assertEquals(List.of("PROGA"),physical.sites().getFirst().candidates().stream().map(DependencySiteFact.Candidate::referenceName).toList());
        assertTrue(physical.metrics().get("physicalGroupsApplied")>0);
    }
    @Test void nominalCicsCallAndFileConsumersKeepCandidatesWithOpenCompleteness() {
        for(var family:List.of("LINK","XCTL","native","file"))for(var mode:List.of("simple","multiple","unknown","partial")) {
            var p=ConsumerCoverageTest.model(family,mode,"name");var result=new DependencyAnalysis().prepare(p);
            if(family.equals("LINK")||family.equals("XCTL")) {
                var site=result.sites().getFirst();
                assertEquals(ConsumerCoverageTest.expected(mode),site.candidates().stream().map(DependencySiteFact.Candidate::referenceName).toList());
                assertTrue(site.effectiveUnknownRemainder());
            } else {
                var site=result.fileDependencies().sites().getFirst();
                assertEquals(family.equals("native")?List.of():ConsumerCoverageTest.expected(mode),site.candidates().stream().map(FileDependencyResult.Candidate::referenceName).toList());
                if(family.equals("native"))assertTrue(site.analysisReasons().contains("FILE_VALUES_UNAVAILABLE"));
                assertTrue(site.unknownRemainder());
            }
            assertEquals(0L,result.metrics().get("physicalGroupsApplied"));assertEquals(0L,result.metrics().get("physicalWritesApplied"));
        }
    }
}
