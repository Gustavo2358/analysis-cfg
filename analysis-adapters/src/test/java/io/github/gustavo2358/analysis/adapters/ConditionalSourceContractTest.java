package io.github.gustavo2358.analysis.adapters;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.gustavo2358.analysis.dependencies.*;
import io.github.gustavo2358.analysis.dependencies.source.*;
import static io.github.gustavo2358.analysis.dependencies.source.QualifiedSourceDependencies.*;

class ConditionalSourceContractTest {
    QualifiedSourceDependencies source(String publication)throws Exception {
        QualifiedSourceDependencies q;
        try(var in=getClass().getResourceAsStream("/qualified-source-r9/computed.source.json")){q=new QualifiedSourceJson().decode(Objects.requireNonNull(in).readAllBytes());}
        var u=q.units().getFirst();var call=u.occurrences().getFirst();var origin=u.statements().stream().filter(s->s.id().equals(call.id())).findFirst().orElseThrow().provenance();
        var facts=new NominalValues("NOMINAL_TEXT_SOURCE_V1",List.of(new NominalValues.Symbol("nominal-item",8)),List.of(),List.of(),List.of(new NominalValues.Query(call.id().handle(),"nominal-item")));
        var value=new NominalValueEvidence(facts,List.of(new NominalValueEvidence.Declaration("nominal-item",origin)),List.of(new NominalValueEvidence.Seed("nominal-item","MAYBE001","DECLARATIVE_POSSIBILITY",origin)),List.of(),List.of(new NominalValueEvidence.Uncertainty("missing","MISSING_COPY",origin)));
        var unit=new UnitEvidence(u.unit(),u.controlAvailable(),u.statements(),u.occurrences(),u.targets(),u.nodes(),u.derivations(),u.selections(),u.events(),u.guards(),u.proofs(),u.frontiers(),Optional.of(value));
        return new QualifiedSourceDependencies(q.schema(),q.version(),q.producer(),q.source(),List.of(new AirCorrelation(publication,"0".repeat(64))),List.of(unit));
    }
    @Test void conditionalFactsRoundTripAndRejectInvalidReferences()throws Exception {
        var q=source("publication");var codec=new QualifiedSourceJson();assertEquals(q,codec.decode(codec.encode(q)));
        var mapper=new ObjectMapper();var wire=(ObjectNode)mapper.readTree(codec.encode(q));
        ((ObjectNode)wire.path("units").get(0).path("nominalValues").path("facts").path("queries").get(0)).put("node","missing-node");
        assertThrows(IllegalArgumentException.class,()->codec.decode(mapper.writeValueAsBytes(wire)));
        var facts=q.units().getFirst().nominalValues().orElseThrow().facts();
        assertThrows(IllegalArgumentException.class,()->new NominalValues(facts.authority(),facts.symbols(),facts.assignments(),facts.conditions(),List.of(new NominalValues.Query("bad","missing-node"))));
    }
    @Test void sourceCandidateReachesUnifiedInventoryWithExplicitAssumptions()throws Exception {
        var p=DependencyPreservationTest.linear(true,false);var q=source(p.id().localId());
        var result=new DependencyAnalysis().prepare(new DependencyInput(p,Optional.of(q),List.of()));
        var resolved=result.programDependencies().stream().filter(o->o.occurrence().source().isPresent()).findFirst().orElseThrow();
        assertEquals(List.of("MAYBE001"),resolved.candidates().stream().map(TargetResolver.Candidate::referenceName).toList());
        assertTrue(resolved.valueRemainder());assertFalse(resolved.candidates().getFirst().conditionalSupports().isEmpty());
        assertTrue(resolved.candidates().getFirst().executableSupports().isEmpty());assertEquals(1L,result.metrics().get("conditionalSourceValueRuns"));
        var out=new java.io.ByteArrayOutputStream();new DependencyJson().write(result,out);assertTrue(out.toString(java.nio.charset.StandardCharsets.UTF_8).contains("NO_UNMODELED_STORAGE_INTERFERENCE"));
        var target=java.nio.file.Path.of("target/conditional-source");java.nio.file.Files.createDirectories(target);java.nio.file.Files.write(target.resolve("dependencies.json"),out.toByteArray());
    }
    @Test void executableValueEvidenceRefinesAndReplacesNominalHypothesis()throws Exception {
        var p=DependencyPreservationTest.linear(true,false);var q=source(p.id().localId());var site=new DependencyAnalysis().prepare(p).sites().getFirst();
        var link=new DependencyInput.StatementCorrelation(q.units().getFirst().occurrences().getFirst().id(),site.operation(),site.sequence(),site.siteOrigin());
        var result=new DependencyAnalysis().prepare(new DependencyInput(p,Optional.of(q),List.of(link)));
        assertEquals(List.of("PROGA"),result.programDependencies().getFirst().candidates().stream().map(TargetResolver.Candidate::referenceName).toList());
        assertEquals(0L,result.metrics().get("conditionalSourceValueRuns"));
    }
    @Test void conditionalUncertaintyDoesNotChangeExecutableScope()throws Exception {
        var p=W1dModelTest.model(1,u->List.of(ResultFixtures.returning(u,"start",List.of())));
        var before=new DependencyAnalysis().prepare(p);assertTrue(before.sites().isEmpty());assertFalse(before.structuralScope());
        var result=new DependencyAnalysis().prepare(new DependencyInput(p,Optional.of(source(p.id().localId())),List.of()));
        assertTrue(result.partial());assertFalse(result.structuralScope());
        assertEquals(List.of("MAYBE001"),result.programDependencies().getFirst().candidates().stream().map(TargetResolver.Candidate::referenceName).toList());
        var out=new java.io.ByteArrayOutputStream();new DependencyJson().write(result,out);
        var wire=new ObjectMapper().readTree(out.toByteArray());assertEquals("KNOWN_GRAPH_ENTRY",wire.path("modelScope").asText());assertEquals("PARTIAL",wire.path("analysisStatus").asText());
        var target=java.nio.file.Path.of("target/conditional-source");java.nio.file.Files.createDirectories(target);java.nio.file.Files.write(target.resolve("empty-executable.dependencies.json"),out.toByteArray());
    }

}
