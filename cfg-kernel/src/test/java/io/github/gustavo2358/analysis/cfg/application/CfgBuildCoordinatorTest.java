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

import java.util.*;
import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.validation.ValidationResult;
import static org.junit.jupiter.api.Assertions.*;

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

        assertEquals(CfgBuildResult.Status.UNSUPPORTED_CAPABILITY, result.status());
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
    void resourceLimitHasItsOwnResultState() {
        Publication publication = AirPublications.withArtifact();
        BuildOptions options = new BuildOptions(new ValidationOptions(128, 1, 100));

        CfgBuildResult result = new CfgBuildCoordinator(SemanticInterpreterRegistry.empty())
                .build(publication, options);

        assertEquals(io.github.gustavo2358.air.validation.ValidationResult.Status.INCOMPLETE_VALIDATION, result.preflight().status());
        assertTrue(result.preflight().hasIssues(ValidationIssue.Kind.RESOURCE_LIMIT));
        org.junit.jupiter.api.Assertions.assertFalse(result.preflight().diagnostics().traversalCompleted());
        assertEquals(CfgBuildResult.Status.RESOURCE_LIMIT, result.status());
        assertTrue(result.graph().isEmpty());
        assertTrue(result.preflight().issues().stream().anyMatch(
                issue -> issue.kind() == ValidationIssue.Kind.RESOURCE_LIMIT));
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

    @Test void samePublicationBudgetAndRecoveryDoNotChangeSemantics() {
        var publication=io.github.gustavo2358.analysis.cfg.testing.CfgFirstPublications.minimal();
        var builder=new CfgBuildCoordinator(SemanticInterpreterRegistry.empty());
        var sufficient=builder.build(publication,BuildOptions.defaults());
        assertEquals(ValidationResult.Status.STRUCTURALLY_VALID,sufficient.preflight().status());
        assertEquals(CfgBuildResult.Status.CFG_BUILT,sufficient.status());
        var restricted=builder.build(publication,new BuildOptions(new ValidationOptions(128,1,1)));
        assertEquals(CfgBuildResult.Status.RESOURCE_LIMIT,restricted.status(),"operational resource category");
        assertTrue(restricted.graph().isEmpty(),"resource failure has no CFG");
        var recovered=builder.build(publication,BuildOptions.defaults());
        assertEquals(sufficient,recovered,"no cached resource failure or poisoned state");
        assertSame(publication,recovered.graph().orElseThrow().publication());
    }

    @Test void genericIncompleteWithoutResourceIssueRemainsDistinct() {
        var p=AirPublications.withArtifact();
        var complete=AirValidator.validate(p);
        var incomplete=new ValidationResult(complete.issues(),complete.statistics(),
                new ValidationResult.Diagnostics(complete.diagnostics().counts(),false));
        assertEquals(ValidationResult.Status.INCOMPLETE_VALIDATION,incomplete.status());
        assertFalse(incomplete.hasIssues(ValidationIssue.Kind.RESOURCE_LIMIT));
        var result=new CfgBuildCoordinator(SemanticInterpreterRegistry.empty())
                .buildAfterPreflight(p,BuildOptions.defaults(),incomplete);
        assertEquals(CfgBuildResult.Status.INCOMPLETE_VALIDATION,result.status(),"generic incomplete is not resource exhaustion");
        assertSame(incomplete,result.preflight());
        assertTrue(result.graph().isEmpty());
    }

    @Test void typedPrecedenceUsesTotalCountsIncludingOmittedMessages() {
        var p=AirPublications.requiring(SYNTHETIC);
        var kinds=List.of(ValidationIssue.Kind.INVALID_IR,ValidationIssue.Kind.RESOURCE_LIMIT,
                ValidationIssue.Kind.VALIDATION_LIMIT,ValidationIssue.Kind.UNSUPPORTED_CAPABILITY);
        var expected=List.of(CfgBuildResult.Status.INVALID_IR,CfgBuildResult.Status.RESOURCE_LIMIT,
                CfgBuildResult.Status.VALIDATION_LIMIT,CfgBuildResult.Status.UNSUPPORTED_CAPABILITY);
        for(int first=0;first<kinds.size();first++) {
            var counts=new EnumMap<ValidationIssue.Kind,Long>(ValidationIssue.Kind.class);
            for(int i=first;i<kinds.size();i++)counts.put(kinds.get(i),1L);
            var preflight=new ValidationResult(List.of(),new ValidationResult.Statistics(0,0,0,0),
                    new ValidationResult.Diagnostics(counts,false));
            var result=new CfgBuildCoordinator(SemanticInterpreterRegistry.empty())
                    .buildAfterPreflight(p,BuildOptions.defaults(),preflight);
            assertEquals(expected.get(first),result.status(),"typed precedence including omitted diagnostics");
            assertTrue(result.graph().isEmpty());
        }
    }

    @Test void legacyValidationLimitStillHasItsOwnResultState() {
        var base=io.github.gustavo2358.analysis.cfg.testing.LinearPublications.withData(
                List.of(io.github.gustavo2358.analysis.cfg.testing.CfgFirstPublications.returning(
                        io.github.gustavo2358.analysis.cfg.testing.CfgFirstPublications.L)),false);
        var unit=base.units().getFirst();var object=unit.objects().getFirst();
        var binding=new Memory.AlternativesBinding(List.of(object.storage()),
                new Scopes.WithinMemory(new Scopes.StorageMemory(List.of(base.storage().getFirst().header().id()))));
        var open=new Memory.ObjectDeclaration(object.id(),object.displayName(),object.typeRef(),binding,
                object.visibility(),object.origin(),object.coverage(),object.precision());
        var changed=new Unit(unit.id(),unit.containingUnit(),List.of(open),unit.visibleObjects(),unit.entries(),
                unit.sequences(),unit.completionPorts(),unit.body(),unit.bodyUnavailable(),unit.coverage(),unit.origin());
        var p=new Publication(base.id(),base.airVersion(),base.capabilities(),base.artifacts(),List.of(changed),
                base.storage(),base.resources(),base.artifactRelations(),base.origins(),base.coverage(),base.uncertainties(),base.premises());
        var preflight=AirValidator.validate(p);
        assertTrue(preflight.issues().stream().anyMatch(i->i.kind()==ValidationIssue.Kind.VALIDATION_LIMIT
                && i.rule().equals("ASSOCIATION_DOMAIN_BOUND")),"real specific legacy issue");
        assertEquals(ValidationResult.Status.INCOMPLETE_VALIDATION,preflight.status());
        assertTrue(preflight.diagnostics().traversalCompleted());
        assertFalse(preflight.hasIssues(ValidationIssue.Kind.RESOURCE_LIMIT));
        var result=new CfgBuildCoordinator(SemanticInterpreterRegistry.empty())
                .build(p,BuildOptions.defaults());
        assertEquals(CfgBuildResult.Status.VALIDATION_LIMIT,result.status());
        assertTrue(result.graph().isEmpty());
    }

    @Test void invalidIrWithConfiguredBudgetRemainsInvalid() {
        var p=AirPublications.withCoverageOwner(new io.github.gustavo2358.air.model.Ids.PublicationId("foreign"));
        var result=new CfgBuildCoordinator(SemanticInterpreterRegistry.empty())
                .build(p,new BuildOptions(new ValidationOptions(4,4,1)));
        assertEquals(CfgBuildResult.Status.INVALID_IR,result.status());
        assertTrue(result.preflight().hasIssues(ValidationIssue.Kind.INVALID_IR));
        assertTrue(result.graph().isEmpty());
    }

    @Test void onlyCfgBuiltCanCarryGraphIncludingResourceLimit() {
        var p=io.github.gustavo2358.analysis.cfg.testing.CfgFirstPublications.minimal();
        var built=new CfgBuildCoordinator(SemanticInterpreterRegistry.empty()).build(p,BuildOptions.defaults());
        for(var status:CfgBuildResult.Status.values()) {
            if(status==CfgBuildResult.Status.CFG_BUILT)continue;
            assertThrows(IllegalArgumentException.class,()->new CfgBuildResult(status,p.id(),p.airVersion(),
                    built.options(),built.preflight(),List.of(),List.of(),built.graph()),"failure cannot carry graph: "+status);
        }
    }
}
