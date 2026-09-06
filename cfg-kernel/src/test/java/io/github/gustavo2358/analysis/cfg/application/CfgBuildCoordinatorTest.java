package io.github.gustavo2358.analysis.cfg.application;

import io.github.gustavo2358.air.model.Capabilities;
import io.github.gustavo2358.air.model.Publication;
import io.github.gustavo2358.air.validation.AirValidator;
import io.github.gustavo2358.air.validation.ValidationIssue;
import io.github.gustavo2358.air.validation.ValidationOptions;
import io.github.gustavo2358.analysis.cfg.extension.SemanticInterpreter;
import io.github.gustavo2358.analysis.cfg.extension.SemanticInterpreterRegistry;
import io.github.gustavo2358.analysis.cfg.testing.AirPublications;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CfgBuildCoordinatorTest {
    private static final Capabilities.Capability SYNTHETIC =
            new Capabilities.Capability("test.synthetic-control", "7");

    @Test
    void completeZeroUnitInventoryProducesAnActuallyEmptyCfg() {
        Publication publication = AirPublications.valid();
        BuildOptions options = BuildOptions.defaults();
        BuildCfg buildCfg = new CfgBuildCoordinator(SemanticInterpreterRegistry.empty());

        CfgBuildResult result = buildCfg.build(publication, options);

        assertEquals(CfgBuildResult.Status.CFG_BUILT, result.status());
        assertEquals(publication.id(), result.publicationId());
        assertEquals(publication.airVersion(), result.airVersion());
        assertSame(options, result.options());
        assertEquals(AirValidator.validate(publication), result.preflight());
        assertEquals(List.of(), result.unsupportedCapabilities());
        assertTrue(result.graph().orElseThrow().nodes().isEmpty());
        assertSame(publication, result.graph().orElseThrow().publication());
    }

    @Test
    void registeredSyntheticInterpreterParticipatesWithoutCoordinatorChanges() {
        Publication publication = AirPublications.requiring(SYNTHETIC);
        SemanticInterpreter interpreter = () -> SYNTHETIC;
        BuildCfg buildCfg = new CfgBuildCoordinator(
                SemanticInterpreterRegistry.of(List.of(interpreter)));

        CfgBuildResult result = buildCfg.build(publication, BuildOptions.defaults());

        assertEquals(CfgBuildResult.Status.INCOMPLETE_VALIDATION, result.status());
        assertTrue(result.graph().isEmpty());
        assertEquals(List.of(), result.unsupportedCapabilities());
        assertTrue(result.preflight().issues().stream().anyMatch(
                issue -> issue.kind() == ValidationIssue.Kind.UNSUPPORTED_CAPABILITY));
        assertEquals(AirValidator.validate(publication), result.preflight());
    }

    @Test
    void unregisteredRequiredCapabilityIsExplicitlyUnsupported() {
        Publication publication = AirPublications.requiring(SYNTHETIC);
        BuildCfg buildCfg = new CfgBuildCoordinator(SemanticInterpreterRegistry.empty());

        CfgBuildResult result = buildCfg.build(publication, BuildOptions.defaults());

        assertEquals(CfgBuildResult.Status.UNSUPPORTED_CAPABILITY, result.status());
        assertTrue(result.graph().isEmpty());
        assertEquals(List.of(SYNTHETIC), result.unsupportedCapabilities());
        assertThrows(
                UnsupportedOperationException.class,
                () -> result.unsupportedCapabilities().clear());
        assertEquals(AirValidator.validate(publication), result.preflight());
    }

    @Test
    void invalidIrAndItsDiagnosticsRemainVisible() {
        Publication publication = AirPublications.withCoverageOwner(
                new io.github.gustavo2358.air.model.Ids.PublicationId("foreign"));

        CfgBuildResult result = new CfgBuildCoordinator(SemanticInterpreterRegistry.empty())
                .build(publication, BuildOptions.defaults());

        assertEquals(CfgBuildResult.Status.INVALID_IR, result.status());
        assertTrue(result.graph().isEmpty());
        assertEquals(AirValidator.validate(publication), result.preflight());
        assertTrue(result.preflight().issues().stream().anyMatch(
                issue -> issue.kind() == ValidationIssue.Kind.INVALID_IR));
    }

    @Test
    void validationLimitHasItsOwnResultState() {
        Publication publication = AirPublications.withArtifact();
        BuildOptions options = new BuildOptions(new ValidationOptions(128, 1, 100));

        CfgBuildResult result = new CfgBuildCoordinator(SemanticInterpreterRegistry.empty())
                .build(publication, options);

        assertEquals(CfgBuildResult.Status.VALIDATION_LIMIT, result.status());
        assertTrue(result.graph().isEmpty());
        assertTrue(result.preflight().issues().stream().anyMatch(
                issue -> issue.kind() == ValidationIssue.Kind.VALIDATION_LIMIT));
        assertEquals(
                AirValidator.validate(publication, options.validation()),
                result.preflight());
    }

    @Test
    void semanticObligationIsPreservedWithoutBecomingCfgEvidence() {
        Capabilities.Capability profile = new Capabilities.Capability("AIR-STRUCTURE", "2");
        Publication publication = AirPublications.providing(profile);

        CfgBuildResult result = new CfgBuildCoordinator(SemanticInterpreterRegistry.empty())
                .build(publication, BuildOptions.defaults());

        assertEquals(CfgBuildResult.Status.CFG_BUILT, result.status());
        assertTrue(result.preflight().issues().stream().anyMatch(
                issue -> issue.kind() == ValidationIssue.Kind.SEMANTIC_OBLIGATION));
        assertEquals(AirValidator.validate(publication), result.preflight());
    }
}
