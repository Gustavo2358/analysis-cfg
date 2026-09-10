package io.github.gustavo2358.analysis.cfg.application;

import io.github.gustavo2358.air.model.Capabilities;
import io.github.gustavo2358.air.model.Publication;
import io.github.gustavo2358.air.validation.ValidationIssue;
import io.github.gustavo2358.air.validation.ValidationResult;
import io.github.gustavo2358.analysis.cfg.domain.CoreCfgProjection;
import io.github.gustavo2358.analysis.cfg.domain.CfgGraph;
import io.github.gustavo2358.analysis.cfg.domain.CfgProjectionIssue;
import io.github.gustavo2358.analysis.cfg.extension.SemanticInterpreterRegistry;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Coordinates preflight, capability negotiation and the core domain projection. */
public final class CfgBuildCoordinator implements BuildCfg {
    private static final Comparator<Capabilities.Capability> CAPABILITY_ORDER =
            Comparator.comparing(Capabilities.Capability::name)
                    .thenComparing(Capabilities.Capability::version);

    private final SemanticInterpreterRegistry interpreters;

    public CfgBuildCoordinator(SemanticInterpreterRegistry interpreters) {
        this.interpreters = Objects.requireNonNull(interpreters, "interpreters");
    }

    @Override
    public CfgBuildResult build(Publication publication, BuildOptions options) {
        Objects.requireNonNull(publication, "publication");
        Objects.requireNonNull(options, "options");

        ValidationResult preflight = CfgPreflight.validate(publication, options.validation());
        return buildAfterPreflight(publication, options, preflight);
    }

    // Package seam for upstream outcomes without a natural Publication fixture.
    // The public boundary always runs the real validator above.
    CfgBuildResult buildAfterPreflight(Publication publication, BuildOptions options,
                                       ValidationResult preflight) {
        List<Capabilities.Capability> unsupported = publication.capabilities().required().stream()
                .filter(capability -> !CoreCfgProjection.supportsControlCapability(capability)
                        && interpreters.find(capability).isEmpty())
                .distinct()
                .sorted(CAPABILITY_ORDER)
                .toList();

        CfgBuildResult.Status status;
        List<CfgProjectionIssue> issues = List.of();
        Optional<CfgGraph> graph = Optional.empty();
        if (has(preflight, ValidationIssue.Kind.INVALID_IR)) {
            status = CfgBuildResult.Status.INVALID_IR;
        } else if (has(preflight, ValidationIssue.Kind.RESOURCE_LIMIT)) {
            status = CfgBuildResult.Status.RESOURCE_LIMIT;
        } else if (has(preflight, ValidationIssue.Kind.VALIDATION_LIMIT)) {
            status = CfgBuildResult.Status.VALIDATION_LIMIT;
        } else if (has(preflight, ValidationIssue.Kind.UNSUPPORTED_CAPABILITY) || !unsupported.isEmpty()) {
            status = CfgBuildResult.Status.UNSUPPORTED_CAPABILITY;
        } else if (preflight.status() == ValidationResult.Status.INCOMPLETE_VALIDATION) {
            status = CfgBuildResult.Status.INCOMPLETE_VALIDATION;
        } else {
            issues = CoreCfgProjection.unsupported(publication, options.projectionPolicy());
            if (issues.isEmpty()) {
                graph = Optional.of(CoreCfgProjection.project(publication));
                status = CfgBuildResult.Status.CFG_BUILT;
            } else {
                status = CfgBuildResult.Status.UNSUPPORTED_INPUT;
            }
        }
        return new CfgBuildResult(status, publication.id(), publication.airVersion(),
                options, preflight, unsupported, issues, graph);
    }

    private static boolean has(ValidationResult result, ValidationIssue.Kind kind) {
        return result.hasIssues(kind);
    }
}
