package io.github.gustavo2358.analysis.cfg.application;

import io.github.gustavo2358.air.model.Publication;

/** In-memory input port for CFG construction. */
@FunctionalInterface
public interface BuildCfg {
    CfgBuildResult build(Publication publication, BuildOptions options);
}
