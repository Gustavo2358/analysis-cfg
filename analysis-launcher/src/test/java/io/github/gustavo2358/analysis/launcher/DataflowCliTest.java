package io.github.gustavo2358.analysis.launcher;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

final class DataflowCliTest {
    @TempDir Path dir;
    @Test void externalCodecCapIsOperationalDebtWithoutLocalReadAdmission()throws Exception {
        Path input=dir.resolve("large.air.json"),output=dir.resolve("result.json");
        try(var out=Files.newOutputStream(input);var stream=getClass().getResourceAsStream("/air/scalar-assign.canonical.json")) {
            stream.transferTo(out);var padding=new byte[1024*1024];java.util.Arrays.fill(padding,(byte)' ');
            for(int i=0;i<17;i++)out.write(padding);
        }
        var stdout=new ByteArrayOutputStream();var stderr=new ByteArrayOutputStream();
        assertEquals(7,AnalysisDataflow.run(new String[]{input.toString(),output.toString(),"--result-id","large-input"},new PrintStream(stdout),new PrintStream(stderr)));
        assertTrue(stderr.toString().contains("EXTERNAL SIZE-CAP DEBT"));assertFalse(stderr.toString().contains("UNSUPPORTED"));assertEquals(0,stdout.size());assertFalse(Files.exists(output));
    }
    @Test void fileRouteMatchesInMemoryAndReceiptIsSeparate()throws Exception {
        Path input=dir.resolve("input.air.json"),output=dir.resolve("result.json");
        try(var stream=getClass().getResourceAsStream("/air/scalar-assign.canonical.json")){Files.copy(stream,input);}
        var stdout=new ByteArrayOutputStream();var stderr=new ByteArrayOutputStream();
        assertEquals(0,AnalysisDataflow.run(new String[]{input.toString(),output.toString(),"--result-id","test-id"},new PrintStream(stdout),new PrintStream(stderr)));
        assertEquals("",stderr.toString());assertTrue(stdout.toString().contains("analysis-delivery-receipt"));
        var read=new io.github.gustavo2358.analysis.adapters.DataflowAirReader().read(input);
        var memory=new io.github.gustavo2358.analysis.dataflow.AnalysisDataflow().prepare(read.publication(),"test-id");
        var bytes=new ByteArrayOutputStream();new io.github.gustavo2358.analysis.adapters.ResultJson().write(memory,bytes);
        assertArrayEquals(bytes.toByteArray(),Files.readAllBytes(output),"memory/file exact bytes");
        assertEquals(1,read.airReads());assertEquals(Files.size(input),read.airBytesObserved());
    }
    @Test void usageMalformedMissingAndOutputFailuresAreDistinct()throws Exception {
        var out=new PrintStream(new ByteArrayOutputStream());var err=new PrintStream(new ByteArrayOutputStream());
        assertEquals(2,AnalysisDataflow.run(new String[]{},out,err));
        var input=dir.resolve("bad.json");Files.writeString(input,"{");
        assertEquals(3,AnalysisDataflow.run(new String[]{input.toString(),dir.resolve("out").toString(),"--result-id","id"},out,err));
        assertEquals(3,AnalysisDataflow.run(new String[]{dir.resolve("absent").toString(),dir.resolve("out").toString(),"--result-id","id"},out,err));
        try(var stream=getClass().getResourceAsStream("/air/scalar-assign.canonical.json")){Files.copy(stream,input,StandardCopyOption.REPLACE_EXISTING);}
        assertEquals(6,AnalysisDataflow.run(new String[]{input.toString(),dir.resolve("absent/out").toString(),"--result-id","id"},out,err));
    }
}
