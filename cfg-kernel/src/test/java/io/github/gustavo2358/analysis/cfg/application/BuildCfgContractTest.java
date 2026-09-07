package io.github.gustavo2358.analysis.cfg.application;

import io.github.gustavo2358.air.model.Publication;
import io.github.gustavo2358.air.validation.ValidationOptions;
import io.github.gustavo2358.analysis.cfg.extension.SemanticInterpreterRegistry;
import io.github.gustavo2358.analysis.cfg.testing.CfgFirstPublications;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Set;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BuildCfgContractTest {
    @Test
    void portHasTheExactInMemoryAirJavaSignature() throws NoSuchMethodException {
        Method build = BuildCfg.class.getDeclaredMethod(
                "build", Publication.class, BuildOptions.class);

        assertTrue(BuildCfg.class.isInterface());
        assertTrue(Modifier.isPublic(BuildCfg.class.getModifiers()));
        assertEquals(CfgBuildResult.class, build.getReturnType());
        assertArrayEquals(
                new Class<?>[]{Publication.class, BuildOptions.class},
                build.getParameterTypes());
        assertEquals(1, Arrays.stream(BuildCfg.class.getDeclaredMethods())
                .filter(method -> Modifier.isAbstract(method.getModifiers()))
                .count());
        assertEquals(
                Publication.class.getProtectionDomain().getCodeSource().getLocation(),
                ValidationOptions.class.getProtectionDomain().getCodeSource().getLocation());
    }

    @Test
    void transportCannotEnterThePortSignature() {
        Set<String> signatureTypes = Arrays.stream(BuildCfg.class.getDeclaredMethods())
                .flatMap(method -> java.util.stream.Stream.concat(
                        java.util.stream.Stream.of(method.getReturnType()),
                        Arrays.stream(method.getParameterTypes())))
                .map(Class::getName)
                .collect(java.util.stream.Collectors.toSet());

        assertEquals(Set.of(
                "io.github.gustavo2358.air.model.Publication",
                "io.github.gustavo2358.analysis.cfg.application.BuildOptions",
                "io.github.gustavo2358.analysis.cfg.application.CfgBuildResult"), signatureTypes);
        assertFalse(signatureTypes.stream().anyMatch(type ->
                type.startsWith("java.io.")
                        || type.startsWith("java.nio.")
                        || type.contains("Json")
                        || type.contains("SemanticProduct")));
    }

    @Test
    void optionsAreSmallImmutableConsumerPolicy() {
        BuildOptions options = BuildOptions.defaults();

        assertTrue(BuildOptions.class.isRecord());
        assertTrue(Modifier.isFinal(BuildOptions.class.getModifiers()));
        assertEquals(1, BuildOptions.class.getRecordComponents().length);
        assertEquals(ValidationOptions.defaults(), options.validation());
    }

    @Test
    void resultEnvelopeRequiresARealProductExactlyOnSuccess() {
        assertTrue(CfgBuildResult.class.isRecord());
        assertTrue(Modifier.isFinal(CfgBuildResult.class.getModifiers()));
        Publication publication = CfgFirstPublications.minimal();
        CfgBuildResult result = new CfgBuildCoordinator(SemanticInterpreterRegistry.empty())
                .build(publication, BuildOptions.defaults());
        assertEquals(CfgBuildResult.Status.CFG_BUILT, result.status());
        assertThrows(IllegalArgumentException.class,
                () -> new CfgBuildResult(CfgBuildResult.Status.CFG_BUILT, result.publicationId(),
                        result.airVersion(), result.options(), result.preflight(), List.of(),
                        List.of(), Optional.empty()));
        for (CfgBuildResult.Status failure : CfgBuildResult.Status.values()) {
            if (failure != CfgBuildResult.Status.CFG_BUILT) {
                assertThrows(IllegalArgumentException.class,
                        () -> new CfgBuildResult(failure, result.publicationId(), result.airVersion(),
                                result.options(), result.preflight(), List.of(),
                                List.of(), result.graph()));
            }
        }
    }
}
