package io.github.gustavo2358.analysis.cfg.application;

import io.github.gustavo2358.air.model.Capabilities;
import io.github.gustavo2358.air.model.Ids.PublicationId;
import io.github.gustavo2358.air.model.Publication;
import io.github.gustavo2358.air.model.Scopes;
import io.github.gustavo2358.air.validation.AirValidator;
import io.github.gustavo2358.air.validation.ValidationIssue;
import io.github.gustavo2358.air.validation.ValidationResult;
import io.github.gustavo2358.analysis.cfg.testing.AirPublications;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CfgPreflightTest {
    @Test
    void realAirJavaPublicationCrossesTheBoundary() throws NoSuchMethodException {
        Publication publication = AirPublications.valid();

        assertEquals(
                Publication.class,
                CfgPreflight.class.getDeclaredMethod("validate", Publication.class).getParameterTypes()[0]);
        assertEquals("io.github.gustavo2358.air.model.Publication", publication.getClass().getName());
        assertTrue(Publication.class.getProtectionDomain().getCodeSource().getLocation()
                .toExternalForm().endsWith("/air-java-0.1.0-SNAPSHOT.jar"));
        assertEquals(
                Publication.class.getProtectionDomain().getCodeSource().getLocation(),
                AirValidator.class.getProtectionDomain().getCodeSource().getLocation());
        assertEquals(ValidationResult.Status.STRUCTURALLY_VALID, CfgPreflight.validate(publication).status());
        assertEquals(AirValidator.validate(publication), CfgPreflight.validate(publication));
    }

    @Test
    void invalidIrIsNeitherRepairedNorReinterpreted() {
        PublicationId foreign = new PublicationId("foreign-publication");
        Publication publication = AirPublications.withCoverageOwner(foreign);

        ValidationResult result = CfgPreflight.validate(publication);

        assertEquals(AirValidator.validate(publication), result);
        assertEquals(ValidationResult.Status.INVALID_IR, result.status());
        assertTrue(result.issues().stream().anyMatch(
                issue -> issue.kind() == ValidationIssue.Kind.INVALID_IR));
        assertEquals(foreign, ((Scopes.PublicationScope) publication.coverage().scope()).publication());
    }

    @Test
    void unsupportedCapabilityRemainsIncompleteValidation() {
        Capabilities.Capability unsupported =
                new Capabilities.Capability("vendor.unsupported-control", "1");
        Publication publication = AirPublications.requiring(unsupported);

        ValidationResult result = CfgPreflight.validate(publication);

        assertEquals(AirValidator.validate(publication), result);
        assertEquals(ValidationResult.Status.INCOMPLETE_VALIDATION, result.status());
        assertTrue(result.issues().stream().anyMatch(
                issue -> issue.kind() == ValidationIssue.Kind.UNSUPPORTED_CAPABILITY));
    }

    @Test
    void semanticObligationRemainsVisibleRatherThanBecomingProof() {
        Capabilities.Capability profileClaim =
                new Capabilities.Capability("AIR-STRUCTURE", "2");
        Publication publication = AirPublications.providing(profileClaim);

        ValidationResult result = CfgPreflight.validate(publication);

        assertEquals(AirValidator.validate(publication), result);
        assertEquals(ValidationResult.Status.STRUCTURALLY_VALID, result.status());
        assertTrue(result.issues().stream().anyMatch(
                issue -> issue.kind() == ValidationIssue.Kind.SEMANTIC_OBLIGATION));
    }
}
