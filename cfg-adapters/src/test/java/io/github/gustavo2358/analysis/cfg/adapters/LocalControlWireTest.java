package io.github.gustavo2358.analysis.cfg.adapters;

import io.github.gustavo2358.air.json.AirJson;
import io.github.gustavo2358.analysis.cfg.application.*;
import io.github.gustavo2358.analysis.cfg.extension.SemanticInterpreterRegistry;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class LocalControlWireTest {
    @Test void symbolicRulesRequireV5AndKeepOperationIdentity() throws Exception {
        var p=new AirJson().decode(getClass().getResourceAsStream("/air/local-control.canonical.json").readAllBytes());
        var result=new CfgBuildCoordinator(SemanticInterpreterRegistry.empty()).build(p,BuildOptions.defaults());
        assertEquals(CfgBuildResult.Status.CFG_BUILT,result.status());
        var bytes=new CfgJsonWriter().encode(result);
        var json=new String(bytes,StandardCharsets.UTF_8);
        assertTrue(json.contains("\"schemaVersion\":\"5.0.0\""));
        assertTrue(json.contains("\"localControl\":["));
        for(String kind:new String[]{"LOCAL_INVOKE","LOCAL_BOUNDARY","LOCAL_RESUME","LOCAL_UNWIND"})
            assertTrue(json.contains("\"kind\":\""+kind+"\""),kind);
        assertTrue(json.contains("\"count\":\"1267650600228229401496703205377\""));
        assertEquals(6,result.graph().orElseThrow().localRules().size());
        var ruleSources=result.graph().orElseThrow().localRules().keySet();
        assertTrue(result.graph().orElseThrow().transitions().stream().noneMatch(t->ruleSources.contains(t.from())));
        Files.createDirectories(Path.of("target/local-control-wire"));
        Files.write(Path.of("target/local-control-wire/cfg.json"),bytes);
        assertArrayEquals(bytes,new CfgJsonWriter().encode(result));
    }
}
