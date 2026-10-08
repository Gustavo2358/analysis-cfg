package io.github.gustavo2358.analysis.cfg.application;

import io.github.gustavo2358.air.model.Capabilities;
import io.github.gustavo2358.air.model.Publication;
import io.github.gustavo2358.air.validation.ValidationIssue;
import io.github.gustavo2358.air.validation.ValidationResult;
import io.github.gustavo2358.analysis.cfg.domain.CoreCfgProjection;
import io.github.gustavo2358.analysis.cfg.domain.CfgGraph;
import io.github.gustavo2358.analysis.cfg.domain.CfgProgram;
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
        return buildAfterPreflight(CfgProgram.resident(publication), options, preflight);
    }

    /** Reuse only a validator-owned run with identical options; changed budgets run preflight again. */
    @Override
    public CfgBuildResult buildChecked(io.github.gustavo2358.air.validation.AirValidator.CheckedPublication checked,
                                BuildOptions options) {
        Objects.requireNonNull(checked, "checked");
        Objects.requireNonNull(options, "options");
        return checked.options().equals(options.validation())
                ? buildAfterPreflight(CfgProgram.resident(checked.publication()), options, checked.result())
                : build(checked.publication(), options);
    }

    /** Builds from the exact admitted snapshot port; snapshot options cannot be changed post-admission. */
    public CfgBuildResult buildChecked(CfgProgram program,
            io.github.gustavo2358.air.validation.SnapshotValidator.CheckedSnapshot checked,
            BuildOptions options) {
        Objects.requireNonNull(program, "program");
        Objects.requireNonNull(checked, "checked");
        Objects.requireNonNull(options, "options");
        if (!checked.options().equals(options.validation())) {
            throw new IllegalArgumentException("snapshot validation options differ from CFG build options");
        }
        return buildAfterPreflight(program, options, checked.result());
    }

    // Package seam for upstream outcomes without a natural Publication fixture.
    CfgBuildResult buildAfterPreflight(Publication publication, BuildOptions options,
                                       ValidationResult preflight) {
        return buildAfterPreflight(CfgProgram.resident(publication), options, preflight);
    }

    CfgBuildResult buildAfterPreflight(CfgProgram program, BuildOptions options,
                                       ValidationResult preflight) {
        var namePolicies = program.namePolicyExtensions();
        List<Capabilities.Capability> unsupported = program.requiredCapabilities().stream()
                .filter(capability -> !CoreCfgProjection.supportsControlCapability(capability)
                        && !namePolicies.contains(capability) && interpreters.find(capability).isEmpty())
                .distinct()
                .sorted(CAPABILITY_ORDER)
                .toList();

        boolean partialPreconditions=options.projectionPolicy()==io.github.gustavo2358.analysis.cfg.domain.ProjectionPolicy.PARTIAL_ANALYSIS
            && preflight.unprovedOperationPreconditions().isPresent();
        CfgBuildResult.Status status;
        List<CfgProjectionIssue> issues = List.of();
        Optional<CfgGraph> graph = Optional.empty();
        if (has(preflight, ValidationIssue.Kind.INVALID_IR)) {
            status = CfgBuildResult.Status.INVALID_IR;
        } else if (has(preflight, ValidationIssue.Kind.RESOURCE_LIMIT)) {
            status = CfgBuildResult.Status.RESOURCE_LIMIT;
        } else if (has(preflight, ValidationIssue.Kind.VALIDATION_LIMIT) && !partialPreconditions) {
            status = CfgBuildResult.Status.VALIDATION_LIMIT;
        } else if (has(preflight, ValidationIssue.Kind.UNSUPPORTED_CAPABILITY) || !unsupported.isEmpty()) {
            status = CfgBuildResult.Status.UNSUPPORTED_CAPABILITY;
        } else if (preflight.status() == ValidationResult.Status.INCOMPLETE_VALIDATION && !partialPreconditions) {
            status = CfgBuildResult.Status.INCOMPLETE_VALIDATION;
        } else {
            issues = CoreCfgProjection.unsupported(program, options.projectionPolicy());
            if (issues.isEmpty()) {
                graph = Optional.of(CoreCfgProjection.project(program, options.projectionPolicy()));
                status = CfgBuildResult.Status.CFG_BUILT;
            } else {
                status = CfgBuildResult.Status.UNSUPPORTED_INPUT;
            }
        }
        return new CfgBuildResult(status, program.source().publicationId(), program.source().airVersion(),
                options, preflight, unsupported, issues, graph);
    }

    private static boolean has(ValidationResult result, ValidationIssue.Kind kind) {
        return result.hasIssues(kind);
    }
}
