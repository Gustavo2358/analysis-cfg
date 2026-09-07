package io.github.gustavo2358.analysis.cfg.application;

import io.github.gustavo2358.air.validation.ValidationOptions;
import io.github.gustavo2358.analysis.cfg.domain.ProjectionPolicy;

import java.util.Objects;

/** Consumer policy that is already enforced before CFG projection starts. */
public record BuildOptions(ValidationOptions validation, ProjectionPolicy projectionPolicy) {
    public BuildOptions {
        Objects.requireNonNull(validation, "validation");
        Objects.requireNonNull(projectionPolicy, "projectionPolicy");
    }

    /** Existing callers also use the default known-subset inventory policy. */
    public BuildOptions(ValidationOptions validation) {
        this(validation, ProjectionPolicy.KNOWN_SUBSET);
    }

    public static BuildOptions defaults() {
        return new BuildOptions(ValidationOptions.defaults());
    }
}
