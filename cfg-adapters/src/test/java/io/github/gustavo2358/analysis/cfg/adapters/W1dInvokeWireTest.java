package io.github.gustavo2358.analysis.cfg.adapters;

import io.github.gustavo2358.air.json.AirJson;
import io.github.gustavo2358.analysis.cfg.application.*;
import io.github.gustavo2358.analysis.cfg.extension.SemanticInterpreterRegistry;
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
        assertTrue(json.contains("\"terminator\":{\"kind\":\"INVOKE\""));assertTrue(json.contains("\"kind\":\"INVOKE_NORMAL\""));
        assertFalse(json.contains("PROGA"),"dependency interpretation belongs to a separate product");
    }
}
