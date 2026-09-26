package io.github.gustavo2358.analysis.adapters;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.analysis.dependencies.*;
import io.github.gustavo2358.analysis.dependencies.source.QualifiedSourceDependencies;

class UnifiedTargetResolutionTest {
    private QualifiedSourceDependencies source(String publication)throws Exception {
        try(var in=getClass().getResourceAsStream("/qualified-source-r9/computed.source.json")) {
            var q=new QualifiedSourceJson().decode(Objects.requireNonNull(in).readAllBytes());
            return new QualifiedSourceDependencies(q.schema(),q.version(),q.producer(),q.source(),List.of(new QualifiedSourceDependencies.AirCorrelation(publication,"0".repeat(64))),q.units());
        }
    }
    @Test void twoAuthoritiesReuseOneQueryAndOneOccurrence()throws Exception {
        var p=DependencyPreservationTest.linear(true,false);var q=source(p.id().localId());
        var before=new DependencyAnalysis().prepare(p);var site=before.sites().getFirst();
        var link=new DependencyInput.StatementCorrelation(q.units().getFirst().occurrences().getFirst().id(),site.operation(),site.sequence(),site.siteOrigin());
        var input=new DependencyInput(p,Optional.of(q),List.of(link));
        var result=new DependencyAnalysis().prepare(input);
        assertEquals(1,result.programDependencies().size());var occurrence=result.programDependencies().getFirst();
        assertEquals(List.of("PROGA"),occurrence.candidates().stream().map(TargetResolver.Candidate::referenceName).toList());
        assertEquals(Set.of("EXECUTABLE_FLOW","SOURCE_QUALIFIED"),Set.copyOf(occurrence.authorities()));
        assertEquals(1L,result.metrics().get("sourceQualifiedResolvedByExistingQuery"));
        assertEquals(before.metrics().get("possibleValuesRuns"),result.metrics().get("possibleValuesRuns"));
        assertEquals(before.metrics().get("planning.uniqueQueries"),result.metrics().get("planning.uniqueQueries"));
        assertFalse(occurrence.candidates().getFirst().executableSupports().isEmpty());
        assertFalse(occurrence.candidates().getFirst().sourceQualifications().isEmpty());
    }
    @Test void sourceOnlyComputedStaysOpenAndDoesNotRunAnotherSolver()throws Exception {
        var p=DependencyPreservationTest.linear(true,false);var q=source(p.id().localId());
        var result=new DependencyAnalysis().prepare(new DependencyInput(p,Optional.of(q),List.of()));
        var source=result.programDependencies().stream().filter(o->o.occurrence().source().isPresent()).findFirst().orElseThrow();
        assertTrue(source.candidates().isEmpty());assertTrue(source.valueRemainder());
        assertTrue(source.occurrence().executableOperations().isEmpty());
        assertEquals(1L,result.metrics().get("possibleValuesRuns"));
    }
    @Test void rejectForeignAndDuplicatedCorrelations()throws Exception {
        var p=DependencyPreservationTest.linear(true,false);var q=source(p.id().localId());var site=new DependencyAnalysis().prepare(p).sites().getFirst();
        var link=new DependencyInput.StatementCorrelation(q.units().getFirst().occurrences().getFirst().id(),site.operation(),site.sequence(),site.siteOrigin());
        assertThrows(IllegalArgumentException.class,()->new DependencyInput(p,Optional.of(q),List.of(link,link)));
        var foreign=new DependencyInput.StatementCorrelation(link.source(),new OperationId(site.caller(),"missing"),link.label(),link.origin());
        assertThrows(IllegalArgumentException.class,()->new DependencyInput(p,Optional.of(q),List.of(foreign)));
        var origin=new DependencyInput.StatementCorrelation(link.source(),link.operation(),link.label(),new OriginId(p.id(),"missing"));
        assertThrows(IllegalArgumentException.class,()->new DependencyInput(p,Optional.of(q),List.of(origin)));
    }
}
