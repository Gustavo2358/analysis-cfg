package io.github.gustavo2358.analysis.cfg.extension;

import io.github.gustavo2358.air.model.Capabilities;

/**
 * Composition identity for one versioned AIR semantic interpreter.
 *
 * <p>This foundation checkpoint only negotiates the interpreter's presence. A
 * later authorized semantic slice may add a typed interpretation operation when
 * its CFG control alternatives exist.
 */
@FunctionalInterface
public interface SemanticInterpreter {
    Capabilities.Capability capability();
}
