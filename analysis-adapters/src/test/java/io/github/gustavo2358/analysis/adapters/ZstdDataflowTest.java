package io.github.gustavo2358.analysis.adapters;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ZstdDataflowTest {
    @TempDir Path directory;
    @Test void airDigestRemainsLogicalAndCorruptionCannotPassAdmission() throws Exception {
        byte[] json;try(var in=getClass().getResourceAsStream("/air/goback.canonical.json")){json=in.readAllBytes();}
        Path input=directory.resolve("air.json.zst");JsonFiles.write(input,input,json);
        var reader=new DataflowAirReader();var read=reader.read(input);
        assertEquals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json)),read.sha256());
        assertEquals(json.length,read.airBytesObserved());
        assertFalse(new DependencyInputJson().isBundle(input));
        byte[] frame=Files.readAllBytes(input);Files.write(input,Arrays.copyOf(frame,frame.length-2));
        assertThrows(java.io.IOException.class,()->reader.read(input));
        Files.write(input,json);assertThrows(java.io.IOException.class,()->reader.read(input));
    }
    @Test void compressedDeliveryHashesPhysicalBytesAndPreservesExistingOnFailure() throws Exception {
        var result=WireTest.fixture();Path output=directory.resolve("result.json.zst");
        var expected=new java.io.ByteArrayOutputStream();new ResultJson().write(result,expected);
        var attempt=new LocalResultWriter().write(result,output);
        assertEquals(DeliveryReceipt.Status.COMPLETE,attempt.receipt().status());
        byte[] frame=Files.readAllBytes(output);
        assertArrayEquals(expected.toByteArray(),JsonFiles.read(output));
        assertEquals(frame.length,attempt.metrics().get("resultBytesWritten"));
        assertEquals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(frame)),attempt.receipt().resultSha256());
        var failed=new LocalResultWriter((r,out)->{out.write(1);throw new java.io.IOException("controlled encoding failure");},new LocalResultWriter.NioFiles()).write(result,output);
        assertEquals(DeliveryReceipt.Status.FAILED,failed.receipt().status());
        assertArrayEquals(frame,Files.readAllBytes(output));
        try(var files=Files.list(directory)){assertEquals(1,files.count());}
    }
}
