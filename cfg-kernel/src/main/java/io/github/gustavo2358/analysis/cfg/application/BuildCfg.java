package io.github.gustavo2358.analysis.cfg.application;

import io.github.gustavo2358.air.model.Publication;

/** In-memory input port for CFG construction. */
@FunctionalInterface
public interface BuildCfg {
    CfgBuildResult build(Publication publication, BuildOptions options);
    /** Existing implementations remain valid; coordinators may reuse the validator-owned run. */
    default CfgBuildResult buildChecked(io.github.gustavo2358.air.validation.AirValidator.CheckedPublication checked, BuildOptions options) {
        return build(checked.publication(), options);
    }
}
