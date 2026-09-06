package io.github.gustavo2358.analysis.cfg.extension;

import io.github.gustavo2358.air.model.Capabilities;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SemanticInterpreterRegistryTest {
    private static final Capabilities.Capability SYNTHETIC =
            new Capabilities.Capability("test.synthetic-control", "7");

    @Test
    void syntheticCapabilityIsRegisteredWithoutProductionConstants() {
        SemanticInterpreter interpreter = () -> SYNTHETIC;

        SemanticInterpreterRegistry registry = SemanticInterpreterRegistry.of(List.of(interpreter));

        assertSame(interpreter, registry.find(SYNTHETIC).orElseThrow());
        assertEquals(List.of(SYNTHETIC), registry.capabilities());
        assertThrows(
                UnsupportedOperationException.class,
                () -> registry.capabilities().clear());
    }

    @Test
    void duplicateOrConflictingRegistrationFailsExplicitly() {
        SemanticInterpreter first = () -> SYNTHETIC;
        SemanticInterpreter conflicting = () -> SYNTHETIC;

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> SemanticInterpreterRegistry.of(List.of(first, conflicting)));

        assertEquals(
                "duplicate semantic interpreter for capability test.synthetic-control@7",
                failure.getMessage());
    }

    @Test
    void registrationOrderCreatesNoPriorityAndVersionsRemainDistinct() {
        Capabilities.Capability older =
                new Capabilities.Capability("test.synthetic-control", "6");
        SemanticInterpreter currentInterpreter = () -> SYNTHETIC;
        SemanticInterpreter olderInterpreter = () -> older;

        SemanticInterpreterRegistry firstOrder =
                SemanticInterpreterRegistry.of(List.of(currentInterpreter, olderInterpreter));
        SemanticInterpreterRegistry reverseOrder =
                SemanticInterpreterRegistry.of(List.of(olderInterpreter, currentInterpreter));

        assertEquals(firstOrder.capabilities(), reverseOrder.capabilities());
        assertSame(currentInterpreter, reverseOrder.find(SYNTHETIC).orElseThrow());
        assertSame(olderInterpreter, firstOrder.find(older).orElseThrow());
    }

    @Test
    void missingCapabilityIsNotResolvedAsANop() {
        SemanticInterpreterRegistry registry = SemanticInterpreterRegistry.empty();

        assertFalse(registry.find(SYNTHETIC).isPresent());
        assertEquals(List.of(), registry.capabilities());
    }
}
