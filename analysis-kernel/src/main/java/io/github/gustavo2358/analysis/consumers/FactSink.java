package io.github.gustavo2358.analysis.consumers;

/** Consumer-local staging only. The runtime commits the complete immutable bundle after all callbacks succeed.
 * Facts must be immutable detached values; retaining a sink does not permit emission after the callback epoch. */
@FunctionalInterface
public interface FactSink<F> { void emit(F fact); }
