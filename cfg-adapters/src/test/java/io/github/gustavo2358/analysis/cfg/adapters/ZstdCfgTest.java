package io.github.gustavo2358.analysis.cfg.adapters;

import io.github.gustavo2358.air.json.AirJson;
import io.github.gustavo2358.air.validation.ValidationOptions;
import io.github.gustavo2358.analysis.cfg.application.*;
import io.github.gustavo2358.analysis.cfg.extension.SemanticInterpreterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

class ZstdCfgTest {
    @TempDir Path directory;
    @Test void realAirToCfgPreservesCanonicalBytesAndRejectsBrokenFrames() throws Exception {
        byte[] json;try(var in=getClass().getResourceAsStream("/air/goback.canonical.json")){json=in.readAllBytes();}
        Path input=directory.resolve("air.json.zst"),output=directory.resolve("cfg.json.zst");
        JsonFiles.write(input,input,json);
        var publication=new AirJsonFileReader().read(input);
        assertArrayEquals(json,new AirJson().encode(publication));
        var result=new CfgBuildCoordinator(SemanticInterpreterRegistry.empty()).build(publication,BuildOptions.defaults());
        var writer=new CfgJsonWriter();writer.write(result,output);
        assertArrayEquals(writer.encode(result),JsonFiles.read(output));
        assertThrows(AirInputLimitException.class,()->new AirJsonFileReader(new AirJson.Limits(json.length-1,1000),ValidationOptions.defaults()).read(input));
        byte[] frame=Files.readAllBytes(input);Files.write(input,Arrays.copyOf(frame,frame.length-1));
        assertThrows(java.io.IOException.class,()->new AirJsonFileReader().read(input));
        frame[frame.length-1]^=1;Files.write(input,frame);
        assertThrows(java.io.IOException.class,()->new AirJsonFileReader().read(input));
    }
}
