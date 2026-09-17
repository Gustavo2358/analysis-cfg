package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.air.json.AirJson;
import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.analysis.dependencies.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** A1–A4/A6 expected facts are manual and independent of SP/lower. */
final class FileDependencyTest {
    private static DependencyResult analyze(String example)throws Exception {
        var manual=ResourceBindingOracle.publication(example);
        var codec=new AirJson();var decoded=codec.decode(codec.encode(manual));assertEquals(manual,decoded);
        var result=new DependencyAnalysis().prepare(decoded);assertFalse(result.analysisReasons().contains("CFG_UNSUPPORTED"),"resource bindings have no control semantics");
        assertTrue(result.fileDependencies().sites().stream().allMatch(site->site.reachability()==FileDependencyResult.Reachability.REACHABLE),"explicit manual normal continuations must be reached");
        var out=new ByteArrayOutputStream();new DependencyJson().write(result,out);
        var dir=Path.of("target/fd-w1");Files.createDirectories(dir);Files.write(dir.resolve(example+".json"),out.toByteArray());
        assertTrue(out.toString(java.nio.charset.StandardCharsets.UTF_8).contains("\"analysisBoundary\":\"COBOL_SOURCE_ONLY\""));
        assertTrue(result.sites().isEmpty());assertTrue(result.edges().isEmpty());return result;
    }
    @Test void declarationWithoutUseIsNotOperationalEdge()throws Exception {
        var files=analyze("A1").fileDependencies();assertEquals(1,files.declarations().size());assertTrue(files.sites().isEmpty());assertTrue(files.edges().isEmpty());
        var d=files.declarations().getFirst();assertEquals("F",d.logicalFile());assertEquals("CLIENTDD",d.name());assertEquals("ASSIGNMENT_NAME",d.sourceKind());assertEquals("cobol.external-file-name",d.namespace());assertEquals(ResourceBindingOracle.unit("U1"),d.owner());assertEquals(1,d.objects().size());
    }
    @Test void writeFromDoesNotReadFileOwningFromRecord()throws Exception {
        var files=analyze("A2").fileDependencies();assertEquals(2,files.declarations().size());assertEquals(1,files.sites().size());assertEquals(1,files.edges().size());
        var s=files.sites().getFirst();assertEquals("write",s.action());assertEquals(List.of("CLIENTDD"),s.candidates().stream().map(FileDependencyResult.Candidate::referenceName).toList());assertEquals(1,s.bindings().size());assertEquals("U1-F",s.bindings().getFirst().declaration().localId());assertEquals("output",s.bindings().getFirst().role());assertFalse(s.unknownRemainder());assertEquals(Evidence.PrecisionStatus.OPEN,s.effects());
    }
    @Test void equalNamesKeepDistinctOwners()throws Exception {
        var files=analyze("A3").fileDependencies();assertEquals(2,files.declarations().size());assertEquals(2,files.declarations().stream().map(FileDependencyResult.Declaration::owner).distinct().count());assertTrue(files.edges().isEmpty());
    }
    @Test void computedCicsIsRetainedWithoutInventedName()throws Exception {
        var files=analyze("A4").fileDependencies();assertTrue(files.declarations().isEmpty());assertEquals(1,files.sites().size());var s=files.sites().getFirst();assertEquals("cics.file",s.namespace());assertTrue(s.candidates().isEmpty());assertTrue(s.unknownRemainder());assertNotNull(s.valuePoint());
    }
    @Test void sortParticipantsKeepRolesAndLocalWork()throws Exception {
        var files=analyze("A6").fileDependencies();assertEquals(4,files.declarations().size());assertEquals(3,files.sites().size());assertEquals(Set.of("INA","INB","OUTC"),files.edges().stream().map(e->e.candidate().referenceName()).collect(java.util.stream.Collectors.toSet()));
        var work=files.declarations().stream().filter(d->d.logicalFile().equals("S")).findFirst().orElseThrow();assertEquals("LOCAL",work.targetKind());assertNull(work.name());assertTrue(files.sites().stream().flatMap(s->s.bindings().stream()).anyMatch(b->b.declaration().equals(work.id())&&b.role().equals("work")));
    }
    @Test void callProjectionAndFileAbsenceRemainSeparate()throws Exception {
        var result=new DependencyAnalysis().prepare(W1dBoundaryTest.input("literal"));assertEquals("PROGA",result.sites().getFirst().candidates().getFirst().referenceName());assertTrue(result.fileDependencies().sites().isEmpty());assertEquals(Evidence.InventoryStatus.UNAVAILABLE,result.fileDependencies().declarationInventory());
    }
    @Test void outputFailurePreservesActualPreviousFile()throws Exception {
        var result=new DependencyAnalysis().prepare(ResourceBindingOracle.publication("A1"));var f=result.fileDependencies();var d=f.declarations().getFirst();
        var invalid=new FileDependencyResult.Declaration(d.id(),d.owner(),"\ud800",d.classification(),d.sourceKind(),d.targetKind(),d.namespace(),d.name(),d.objects(),d.origin());
        var badFiles=new FileDependencyResult(f.declarationInventory(),List.of(invalid),f.sites(),f.edges(),f.metrics());
        var bad=new DependencyResult(result.publication(),result.airVersion(),result.sites(),result.edges(),result.metrics(),result.publicationInventory(),result.origins(),result.artifacts(),result.sourceUncertaintyRefs(),result.analysisReasons(),badFiles);
        var dir=Files.createTempDirectory("fd-w1-output-");var path=dir.resolve("dependencies.json");var writer=new DependencyFileWriter();writer.write(result,path);var old=Files.readAllBytes(path);
        assertThrows(IllegalArgumentException.class,()->writer.write(bad,path));assertArrayEquals(old,Files.readAllBytes(path));
        try(var paths=Files.list(dir)){assertEquals(List.of(path),paths.toList());}
    }

}
