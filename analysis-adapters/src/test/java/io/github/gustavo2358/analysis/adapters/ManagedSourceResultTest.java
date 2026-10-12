package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.analysis.dependencies.*;
import io.github.gustavo2358.analysis.solver.*;
import io.github.gustavo2358.analysis.cfg.application.*;
import io.github.gustavo2358.analysis.cfg.extension.SemanticInterpreterRegistry;
import com.fasterxml.jackson.databind.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** The actual general native application and complete v3 product, not a helper-only projection. */
final class ManagedSourceResultTest {
    @TempDir Path directory;
    private static final ObjectMapper JSON=new ObjectMapper();
    private SourceCorrelationSnapshotTest fixtures(){var value=new SourceCorrelationSnapshotTest();value.directory=directory;return value;}
    private static CfgBuildResult cfg(SourceCorrelationSnapshotTest.NativeCase input){return new CfgBuildCoordinator(SemanticInterpreterRegistry.empty()).buildChecked(input.program,input.checked,BuildOptions.defaults());}
    @Test void completeNativeApplicationPreservesEveryLegacyProductFieldAcrossSixSourceFamilies()throws Exception {
        var publication=SourceCorrelationSnapshotTest.program(2);var fixture=fixtures();
        for(String path:List.of("source-possibility/unknown-branch-kill.source.json","source-possibility/unknown-copy-kill.source.json","source-possibility/unknown-branch-query.source.json","perform-reentry/reentry-values.source.json","qualified-source-r9/ordinary.source.json","source-possibility/unknown-native.source.json")) {
            var evidence=SourceProgramSnapshotTest.source(publication,path);var links=path.contains("ordinary")||path.contains("native")?List.<DependencyInput.StatementCorrelation>of():SourceCorrelationSnapshotTest.links(publication,evidence);
            // Metered publication needs a nonzero common output budget. Keep
            // every semantic oracle unchanged; read-only/denial fixtures stay zero.
            try(var input=fixture.new NativeCase(publication,evidence,false,1_000_000)) {
                var built=cfg(input);assertEquals(CfgBuildResult.Status.CFG_BUILT,built.status());var analysis=new DependencyAnalysis();var old=analysis.prepareLeased(input.program,built,evidence,links,input.pages,input.execution);var oracle=new ByteArrayOutputStream();new DependencyJson().writeSnapshot(old,input.program,oracle);
                long baseline=input.pages.statistics().livePages();try(var result=analysis.prepareLeased(input.program,built,input.source,links,input.pages,input.execution)) {
                    var wire=new ByteArrayOutputStream();new DependencyJson().writeSnapshot(result,input.program,input.pages,input.execution,wire);assertEquals(JSON.readTree(oracle.toByteArray()),JSON.readTree(wire.toByteArray()),path);
                    assertSame(input.source,result.admission());assertSame(input.program,result.program());assertTrue(result.programs().count()>0);
                    Path output=directory.resolve("complete-managed.json");new SnapshotDependencyFileWriter().write(result,output,input.pages,input.execution);assertEquals(JSON.readTree(wire.toByteArray()),JSON.readTree(Files.readAllBytes(output)));
                }assertEquals(baseline,input.pages.statistics().livePages());assertTrue(input.source.unitCount()>0);assertEquals(publication.id(),input.program.publicationId());
            }
        }
    }
    private static void expire(ManagedDependencyResult result){result.close();}
    @Test void atomicPublicationPreservesDestinationOnOwnerExpiryAndForeignSourceFailsBeforeDerivation()throws Exception {
        var publication=SourceCorrelationSnapshotTest.program(1);var fixture=fixtures();var evidence=SourceProgramSnapshotTest.source(publication,"source-possibility/unknown-copy-kill.source.json");
        try(var input=fixture.new NativeCase(publication,evidence);var other=fixture.new NativeCase(publication,evidence)) {
            var built=cfg(input);var analysis=new DependencyAnalysis();long baseline=input.pages.statistics().livePages();
            assertThrows(IllegalArgumentException.class,()->analysis.prepareLeased(input.program,built,other.source,List.of(),input.pages,input.execution));assertEquals(baseline,input.pages.statistics().livePages());
            try(var result=analysis.prepareLeased(input.program,built,input.source,List.of(),input.pages,input.execution)) {
                Path output=directory.resolve("existing.json");Files.writeString(output,"existing destination");expire(result);assertThrows(IllegalStateException.class,()->new SnapshotDependencyFileWriter().write(result,output,input.pages,input.execution));assertEquals("existing destination",Files.readString(output));
                try(var files=Files.list(directory)){assertFalse(files.anyMatch(p->p.getFileName().toString().startsWith(".dependencies-")));}
            }assertEquals(baseline,input.pages.statistics().livePages());assertTrue(input.source.unitCount()>0);assertEquals(publication.id(),input.program.publicationId());
        }
    }
}
