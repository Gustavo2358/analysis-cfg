package io.github.gustavo2358.analysis.cfg.extension;

import io.github.gustavo2358.air.model.Capabilities;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/** Explicit immutable composition of semantic interpreters by capability and version. */
public final class SemanticInterpreterRegistry {
    private static final Comparator<Capabilities.Capability> CAPABILITY_ORDER =
            Comparator.comparing(Capabilities.Capability::name)
                    .thenComparing(Capabilities.Capability::version);
    private static final SemanticInterpreterRegistry EMPTY =
            new SemanticInterpreterRegistry(List.of());

    private final Map<Capabilities.Capability, SemanticInterpreter> interpreters;
    private final List<Capabilities.Capability> capabilities;

    private SemanticInterpreterRegistry(Collection<? extends SemanticInterpreter> values) {
        Map<Capabilities.Capability, SemanticInterpreter> indexed =
                new TreeMap<>(CAPABILITY_ORDER);
        for (SemanticInterpreter value : values) {
            SemanticInterpreter interpreter = Objects.requireNonNull(value, "interpreter");
            Capabilities.Capability capability =
                    Objects.requireNonNull(interpreter.capability(), "interpreter capability");
            if (indexed.putIfAbsent(capability, interpreter) != null) {
                throw new IllegalArgumentException(
                        "duplicate semantic interpreter for capability "
                                + capability.name() + "@" + capability.version());
            }
        }
        interpreters = Collections.unmodifiableMap(indexed);
        capabilities = List.copyOf(indexed.keySet());
    }

    public static SemanticInterpreterRegistry empty() {
        return EMPTY;
    }

    public static SemanticInterpreterRegistry of(
            Collection<? extends SemanticInterpreter> interpreters) {
        Objects.requireNonNull(interpreters, "interpreters");
        return interpreters.isEmpty() ? EMPTY : new SemanticInterpreterRegistry(interpreters);
    }

    public Optional<SemanticInterpreter> find(Capabilities.Capability capability) {
        Objects.requireNonNull(capability, "capability");
        return Optional.ofNullable(interpreters.get(capability));
    }

    public List<Capabilities.Capability> capabilities() {
        return capabilities;
    }
}
