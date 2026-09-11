package io.github.gustavo2358.analysis.cfg.adapters;

import io.github.gustavo2358.air.json.AirJson;
import io.github.gustavo2358.analysis.cfg.application.*;
import io.github.gustavo2358.analysis.cfg.extension.SemanticInterpreterRegistry;
import io.github.gustavo2358.analysis.cfg.domain.CfgGraph;
import io.github.gustavo2358.analysis.cfg.domain.CfgNode;
import io.github.gustavo2358.air.model.Operations;
import java.util.List;
import java.util.Optional;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class W1dInvokeWireTest {
    @Test void realInvokeRetainsDistinctTerminatorAndTransitionInExistingCfgWire() throws Exception {
        var p=new AirJson().decode(Files.readAllBytes(Path.of("../analysis-adapters/src/test/resources/cp6/dynamic-x8.air.json")));
        var cfg=new CfgBuildCoordinator(SemanticInterpreterRegistry.empty()).build(p,BuildOptions.defaults());
        assertEquals(CfgBuildResult.Status.CFG_BUILT,cfg.status());
        var json=new String(new CfgJsonWriter().encode(cfg),StandardCharsets.UTF_8);
        assertTrue(json.startsWith("{\"schema\":\"analysis-cfg-json\",\"schemaVersion\":\"2.0.0\","));
        assertFalse(json.contains("\"schemaVersion\":\"1.0.0\""));
        assertTrue(json.contains("\"terminator\":{\"kind\":\"INVOKE\""));assertTrue(json.contains("\"kind\":\"INVOKE_NORMAL\""));
        assertFalse(json.contains("PROGA"),"dependency interpretation belongs to a separate product");
    }

    @Test void historicalGoldensRemainExactlyV1() throws Exception {
        for (String name : List.of("goback", "scalar-assign")) {
            var publication = new AirJson().decode(getClass().getResourceAsStream("/air/" + name + ".canonical.json").readAllBytes());
            var result = new CfgBuildCoordinator(SemanticInterpreterRegistry.empty()).build(publication, BuildOptions.defaults());
            byte[] bytes = new CfgJsonWriter().encode(result);
            assertArrayEquals(getClass().getResourceAsStream("/cfg/" + name + ".manual.json").readAllBytes(), bytes);
            assertTrue(new String(bytes, StandardCharsets.UTF_8).contains("\"schemaVersion\":\"1.0.0\""));
        }
    }

    @Test void allLegacyKindsAndMisleadingIdsStillSelectV1() throws Exception {
        var result = new CfgBuildCoordinator(SemanticInterpreterRegistry.empty()).build(
                MemoryFacts.mixed("INVOKE INVOKE_NORMAL PROGA 2.0.0"), BuildOptions.defaults());
        String json = new String(new CfgJsonWriter().encode(result), StandardCharsets.UTF_8);
        assertTrue(json.contains("\"schemaVersion\":\"1.0.0\""));
        for (String token : List.of("JUMP", "BRANCH", "RETURN", "HALT"))
            assertTrue(json.contains("\"terminator\":{\"kind\":\"" + token + "\""));
        assertFalse(json.contains("\"kind\":\"INVOKE_NORMAL\""));
    }

    @Test void invokeNodeRequiresV2EvenWithoutTransitions() throws Exception {
        var full = realInvoke();
        var node = full.graph().orElseThrow().nodes().stream().filter(n -> n instanceof CfgNode.SequenceNode s
                && s.source().terminator() instanceof Operations.Invoke).findFirst().orElseThrow();
        String json = new String(new CfgJsonWriter().encode(withNodes(full, List.of(node))), StandardCharsets.UTF_8);
        assertTrue(json.contains("\"schemaVersion\":\"2.0.0\""));
        assertTrue(json.contains("\"terminator\":{\"kind\":\"INVOKE\""));
        assertTrue(json.endsWith("\"transitions\":[]}"));
    }

    @Test void onlyProjectedDomainSelectsVersionAndWriterHasNoStickyState() throws Exception {
        var full = realInvoke();
        // Original AIR contains Invoke, but this independently constructed CFG product does not.
        var legacyNodes = full.graph().orElseThrow().nodes().stream().filter(n -> n instanceof CfgNode.SequenceNode s
                && s.source().terminator() instanceof Operations.Return).toList();
        assertFalse(legacyNodes.isEmpty());
        var legacy = withNodes(full, legacyNodes);
        var writer = new CfgJsonWriter();
        byte[] before = writer.encode(legacy);
        assertTrue(new String(before, StandardCharsets.UTF_8).contains("\"schemaVersion\":\"1.0.0\""));
        assertTrue(new String(writer.encode(full), StandardCharsets.UTF_8).contains("\"schemaVersion\":\"2.0.0\""));
        assertArrayEquals(before, writer.encode(legacy));
        assertFalse(new String(before, StandardCharsets.UTF_8).contains("PROGA"));
    }

    private static CfgBuildResult realInvoke() throws Exception {
        var publication = new AirJson().decode(Files.readAllBytes(Path.of("../analysis-adapters/src/test/resources/cp6/dynamic-x8.air.json")));
        var result = new CfgBuildCoordinator(SemanticInterpreterRegistry.empty()).build(publication, BuildOptions.defaults());
        assertEquals(CfgBuildResult.Status.CFG_BUILT, result.status());
        return result;
    }

    private static CfgBuildResult withNodes(CfgBuildResult full, List<CfgNode> nodes) {
        return new CfgBuildResult(full.status(), full.publicationId(), full.airVersion(), full.options(), full.preflight(),
                full.unsupportedCapabilities(), full.projectionIssues(),
                Optional.of(new CfgGraph(full.graph().orElseThrow().publication(), nodes, List.of())));
    }
}
