package io.github.gustavo2358.analysis.cfg.application;

import io.github.gustavo2358.air.model.Capabilities;
import io.github.gustavo2358.air.model.Publication;
import io.github.gustavo2358.air.validation.ValidationIssue;
import io.github.gustavo2358.air.validation.ValidationResult;
import io.github.gustavo2358.analysis.cfg.extension.SemanticInterpreterRegistry;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Coordinates preflight and declared semantic support, stopping before CFG projection. */
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
        List<Capabilities.Capability> unsupported = publication.capabilities().required().stream()
                .filter(capability -> interpreters.find(capability).isEmpty())
                .distinct()
                .sorted(CAPABILITY_ORDER)
                .toList();

        return new CfgBuildResult(
                status(preflight, unsupported),
                publication.id(),
                publication.airVersion(),
                options,
                preflight,
                unsupported);
    }

    private static CfgBuildResult.Status status(
            ValidationResult preflight,
            List<Capabilities.Capability> unsupported) {
        if (has(preflight, ValidationIssue.Kind.INVALID_IR)) {
            return CfgBuildResult.Status.INVALID_IR;
        }
        if (has(preflight, ValidationIssue.Kind.VALIDATION_LIMIT)) {
            return CfgBuildResult.Status.VALIDATION_LIMIT;
        }
        if (!unsupported.isEmpty()) {
            return CfgBuildResult.Status.UNSUPPORTED_CAPABILITY;
        }
        if (preflight.status() == ValidationResult.Status.INCOMPLETE_VALIDATION) {
            return CfgBuildResult.Status.INCOMPLETE_VALIDATION;
        }
        return CfgBuildResult.Status.READY_FOR_CFG_PROJECTION;
    }

    private static boolean has(ValidationResult result, ValidationIssue.Kind kind) {
        return result.issues().stream().anyMatch(issue -> issue.kind() == kind);
    }
}
