package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.air.json.AirJson;
import io.github.gustavo2358.analysis.dataflow.AnalysisDataflow;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

final class WireTest {
    static io.github.gustavo2358.analysis.dataflow.PreparedDataflowResult fixture() throws IOException {
        try(var input=WireTest.class.getResourceAsStream("/air/scalar-assign.canonical.json")) {
            return new AnalysisDataflow().prepare(new AirJson().decode(input.readAllBytes()),"review-result");
        }
    }
    @Test void realValueSupportAndSourceRemainderReachWire() throws IOException {
        var r=fixture();var output=new ByteArrayOutputStream();new ResultJson().write(r,output);
        String json=output.toString(StandardCharsets.UTF_8);
        assertTrue(json.contains("\"schema\":\"prepared-analysis-result\""));
        assertTrue(json.contains("\"version\":\"1.1.0\""));
        assertTrue(json.contains("\"candidateSupports\":[{\"candidate\":\"PROGA\",\"producers\":[{\"evidence\":{\"domain\":\"operation\",\"localId\":\"set-program\""),"candidate retains real producer association");
        assertTrue(json.contains("\"sourceUnknownRemainder\":true"),"source stays open");
        assertTrue(json.contains("\"modelValueRemainder\":false"));
        assertTrue(json.contains("set-program"));
        assertFalse(json.contains("deliveryStatus"));
        var again=new ByteArrayOutputStream();new ResultJson().write(fixture(),again);
        assertArrayEquals(output.toByteArray(),again.toByteArray(),"deterministic full payload");
    }
}
