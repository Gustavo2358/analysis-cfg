package io.github.gustavo2358.analysis.cfg.application;

import io.github.gustavo2358.air.validation.ValidationOptions;

import java.util.Objects;

/** Consumer policy that is already enforced before CFG projection starts. */
public record BuildOptions(ValidationOptions validation) {
    public BuildOptions {
        Objects.requireNonNull(validation, "validation");
    }

    public static BuildOptions defaults() {
        return new BuildOptions(ValidationOptions.defaults());
    }
}
