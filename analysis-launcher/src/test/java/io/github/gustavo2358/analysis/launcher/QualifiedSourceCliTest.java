package io.github.gustavo2358.analysis.launcher;

import java.nio.file.*;
import java.util.*;
import java.io.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class QualifiedSourceCliTest {
    @Test void correlatedSourcePublishesAndInvalidInputsPreserveDestination()throws Exception {
        var dir=Files.createTempDirectory("r9-cli-");try {
            var air=dir.resolve("air.json");var source=dir.resolve("source.json");var output=dir.resolve("dependencies.json");
            for(var pair:Map.of(air,"conditional.air.json",source,"conditional.source.json").entrySet())try(var in=getClass().getResourceAsStream("/qualified-source-r9/"+pair.getValue())){Files.write(pair.getKey(),Objects.requireNonNull(in).readAllBytes());}
            var errors=new ByteArrayOutputStream();var err=new PrintStream(errors);var args=new String[]{air.toString(),output.toString(),"--source-evidence",source.toString()};
            assertEquals(0,AnalysisDependencies.run(args,err),errors.toString());var result=Files.readString(output);
            assertTrue(result.contains("\"sourceQualifiedDependencies\""));assertTrue(result.contains("\"version\":\"2.6.0\""));assertTrue(result.contains("CONDPGM"));
            var original=Files.readString(source);Files.writeString(output,"sentinel");
            Files.writeString(source,original.replace("\"version\":\"1.0.0\"","\"version\":\"0.9.0\""));assertEquals(3,AnalysisDependencies.run(args,err));assertEquals("sentinel",Files.readString(output));
            Files.writeString(source,original);Files.writeString(air,Files.readString(air)+" ");assertEquals(3,AnalysisDependencies.run(args,err));assertEquals("sentinel",Files.readString(output));
            assertTrue(errors.toString().contains("AIR digest mismatch"));
        } finally {try(var paths=Files.walk(dir)){for(var p:paths.sorted(Comparator.reverseOrder()).toList())Files.delete(p);}}
    }
}
