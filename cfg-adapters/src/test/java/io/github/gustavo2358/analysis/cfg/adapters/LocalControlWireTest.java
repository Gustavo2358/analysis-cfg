package io.github.gustavo2358.analysis.cfg.adapters;

import io.github.gustavo2358.air.json.AirJson;
import io.github.gustavo2358.air.model.*;
import java.util.*;
import io.github.gustavo2358.analysis.cfg.application.*;
import io.github.gustavo2358.analysis.cfg.extension.SemanticInterpreterRegistry;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class LocalControlWireTest {
    @Test void guardedRulesRequireV6AndKeepGuardIdentity() throws Exception {
        var codec=new AirJson();var base=codec.decode(getClass().getResourceAsStream("/air/local-control.canonical.json").readAllBytes());
        var unit=base.units().getFirst();var sequences=new ArrayList<>(unit.sequences());var first=sequences.getFirst();
        var call=(Operations.LocalInvoke)first.terminator();
        sequences.set(0,new Sequence(first.label(),first.instructions(),new Operations.LocalInvoke(call.header(),call.entry(),call.completionPorts(),call.resume(),call.fallback(),
            Optional.of(new Operations.ReentryGuard("same-binding/α",call.resume()))),first.origin()));
        var changed=new Unit(unit.id(),unit.containingUnit(),unit.objects(),unit.visibleObjects(),unit.entries(),sequences,unit.completionPorts(),unit.body(),unit.bodyUnavailable(),unit.coverage(),unit.origin());
        var caps=List.of(Capabilities.LOCAL_CONTROL,Capabilities.LOCAL_REENTRY_GUARD);
        var p=new Publication(base.id(),base.airVersion(),new Capabilities.Manifest(caps,caps),base.artifacts(),List.of(changed),base.storage(),base.resources(),base.artifactRelations(),base.origins(),base.coverage(),base.uncertainties(),base.premises());
        assertEquals(p,codec.decode(codec.encode(p)));
        var result=new CfgBuildCoordinator(SemanticInterpreterRegistry.empty()).build(p,BuildOptions.defaults());
        assertEquals(CfgBuildResult.Status.CFG_BUILT,result.status());
        var bytes=new CfgJsonWriter().encode(result);var json=new String(bytes,StandardCharsets.UTF_8);
        assertTrue(json.contains("\"schemaVersion\":\"6.0.0\""));
        assertTrue(json.contains("\"activationKey\":\"same-binding/α\""));
        assertArrayEquals(bytes,new CfgJsonWriter().encode(result));
        Files.createDirectories(Path.of("target/local-control-wire"));Files.write(Path.of("target/local-control-wire/guarded-cfg.json"),bytes);
    }
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
