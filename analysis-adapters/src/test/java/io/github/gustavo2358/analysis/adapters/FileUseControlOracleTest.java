package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.air.json.AirJson;
import io.github.gustavo2358.analysis.dependencies.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static io.github.gustavo2358.analysis.adapters.ResultFixtures.*;
import static io.github.gustavo2358.analysis.adapters.FileIoOutcomeOracleTest.*;

/** Independent O4/control composition: error body is conditional and shared;
 * status already written, bounded return never introduces a memory write. */
final class FileUseControlOracleTest {
    static Publication withUse(Outcome selected) {
        var p=manual(selected,false);var old=p.units().getFirst();var sequences=new ArrayList<Sequence>();
        for(var q:old.sequences()) {
            if(q.label().localId().equals("ERROR"))q=new Sequence(q.label(),q.instructions(),new Operations.Jump(header(U,"error-enters-use"),new LabelId(U,"use-body")),O);
            sequences.add(q);
        }
        sequences.add(new Sequence(new LabelId(U,"use-body"),List.of(),W1dModelTest.call(U,"use-call","use-status",object("X"),false),O));
        sequences.add(new Sequence(new LabelId(U,"use-status"),List.of(),W1dModelTest.call(U,"use-status-call","use-return",object("STATUS"),false),O));
        var op=new OperationId(U,"use-return");var gap=new UncertaintyId(P,"return-context");var scope=new Scopes.EntityScope(List.of(op));
        var exact=new Evidence.Claim(scope,Evidence.PrecisionStatus.EXACT,List.of());var open=new Evidence.Claim(scope,Evidence.PrecisionStatus.OPEN,List.of(gap));
        var h=new Operations.Header(op,O,Evidence.CoverageStatus.ABSTRACTED,new Evidence.Precision(open,exact,exact,exact,exact),List.of(gap));
        var envelope=new Envelopes.Envelope(new Envelopes.MemoryEnvelope(List.of(),Scopes.NoMemory.INSTANCE,List.of(),Scopes.NoMemory.INSTANCE,List.of()),
            new Control.ControlEnvelope(List.of(new Control.JumpAlternative(new LabelId(U,"probe-B"))),new Scopes.WithinControl(new Scopes.UnitControl(U,false,false,true,false,false,false))),
            new Envelopes.DependencyEnvelope(List.of(),Scopes.NoResources.INSTANCE));
        sequences.add(new Sequence(new LabelId(U,"use-return"),List.of(),new Operations.Opaque(h,"manual-use-return",List.of(),List.of(),envelope),O));
        var gaps=new ArrayList<>(p.uncertainties());gaps.add(new Evidence.Uncertainty(gap,"LOCAL_RETURN_CONTEXT_NOT_PROVEN",List.of(Evidence.Dimension.CONTROL),scope,"return abstraction, not a context matching proof",O));
        return new Publication(p.id(),p.airVersion(),p.capabilities(),p.artifacts(),List.of(unit(U,old.entries(),sequences,old.objects())),p.storage(),p.resources(),p.artifactRelations(),p.origins(),p.coverage(),gaps,p.premises());
    }
    @Test void errorInvokesUseAfterStatusAndReturnsWithoutGlobalMemoryKill()throws Exception {
        var p=withUse(Outcome.ERROR);var codec=new AirJson();var bytes=codec.encode(p);assertEquals(p,codec.decode(bytes));
        var r=new DependencyAnalysis().prepare(p);
        assertFalse(r.analysisReasons().contains("CFG_UNSUPPORTED"));
        assertEquals(DependencySiteFact.Reachability.REACHABLE,site(r,"use-call").reachability());
        assertEquals(List.of("SAFE0001"),names(site(r,"use-call")));assertEquals(List.of("90"),values(site(r,"use-status-call")));
        assertEquals(List.of("SAFE0001"),names(site(r,"probe-X")));
        assertTrue(site(r,"probe-X").candidates().getFirst().supports().stream().anyMatch(s->s.producer().localId().equals("seed-X")));
        assertEquals(1,r.fileDependencies().sites().size());
        Files.createDirectories(Path.of("target/fd-w4/manual"));Files.write(Path.of("target/fd-w4/manual/use-error.air.json"),bytes);
    }
    @Test void successDoesNotExecuteUseAsAProgramPrefix() {
        var r=new DependencyAnalysis().prepare(withUse(Outcome.SUCCESS));
        assertEquals(DependencySiteFact.Reachability.UNREACHABLE_IN_MODEL,site(r,"use-call").reachability());
        assertEquals(DependencySiteFact.Reachability.UNREACHABLE_IN_MODEL,site(r,"use-status-call").reachability());
        assertEquals(List.of("SAFE0001"),names(site(r,"probe-X")));
    }
}
