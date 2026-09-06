package io.github.gustavo2358.analysis.cfg.application;

import io.github.gustavo2358.air.model.Publication;
import io.github.gustavo2358.air.validation.AirValidator;
import io.github.gustavo2358.air.validation.ValidationOptions;
import io.github.gustavo2358.air.validation.ValidationResult;

/**
 * Structural preflight for the future CFG use case.
 *
 * <p>The complete upstream validation result crosses this boundary unchanged so
 * callers cannot lose unsupported capabilities, limits, or semantic obligations.
 */
public final class CfgPreflight {
    private CfgPreflight() {
    }

    public static ValidationResult validate(Publication publication) {
        return AirValidator.validate(publication);
    }

    public static ValidationResult validate(
            Publication publication,
            ValidationOptions options) {
        return AirValidator.validate(publication, options);
    }

}
