package io.github.gustavo2358.analysis.adapters;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

final class DeliveryTest {
    @TempDir Path directory;
    @Test void resourceExhaustionIsNotACompletedOrSemanticDeliveryOutcome()throws Exception {
        var result=WireTest.fixture();var path=directory.resolve("resource.json");Files.writeString(path,"old");
        var error=new OutOfMemoryError("controlled test signal; no memory allocation pressure");
        var writer=new LocalResultWriter((r,out)->{throw error;},new LocalResultWriter.NioFiles());
        assertSame(error,assertThrows(OutOfMemoryError.class,()->writer.write(result,path)));assertEquals("old",Files.readString(path));
    }
    @Test void completeReceiptBindsExactFinalBytesAndReplacesExistingAtomically() throws Exception {
        var result=WireTest.fixture();var path=directory.resolve("result.json");Files.writeString(path,"old");
        var attempt=new LocalResultWriter().write(result,path);var receipt=attempt.receipt();
        assertEquals(DeliveryReceipt.Status.COMPLETE,receipt.status(),"delivery complete after finalization");
        assertEquals(result.result().resultId(),receipt.resultId());
        assertEquals(path.toAbsolutePath().toString(),receipt.destination());
        assertEquals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))),receipt.resultSha256(),"hash exact final bytes");
        assertEquals(Files.size(path),attempt.metrics().get("resultBytesWritten"));
        assertEquals(1L,attempt.metrics().get("deliveryComplete"));
    }
    @Test void controlledFailuresPreservePreparedResultAndExistingDestination() throws Exception {
        var result=WireTest.fixture();var original=result.result();
        for(var reason:DeliveryReceipt.Reason.values()) {
            var path=directory.resolve(reason+".json");Files.writeString(path,"old");var nio=new LocalResultWriter.NioFiles();
            var files=new LocalResultWriter.LocalFiles() {
                public Path temporary(Path p)throws IOException{return nio.temporary(p);}
                public OutputStream open(Path p)throws IOException{
                    if(reason!=DeliveryReceipt.Reason.WRITE_FAILED)return nio.open(p);
                    return new FilterOutputStream(nio.open(p)) { @Override public void write(byte[] b,int o,int n)throws IOException {out.write(b,o,Math.min(7,n));throw new IOException("controlled write");} };
                }
                public void finalizeFile(Path a,Path b)throws IOException{if(reason==DeliveryReceipt.Reason.FINALIZATION_FAILED)throw new IOException("controlled finalization");nio.finalizeFile(a,b);}
                public void cleanup(Path p)throws IOException{nio.cleanup(p);}
            };
            LocalResultWriter.Encoder encoder=reason==DeliveryReceipt.Reason.ENCODING_FAILED?(r,out)->{out.write(1);throw new IllegalArgumentException("controlled encoding");}:new ResultJson()::write;
            var receipt=new LocalResultWriter(encoder,files).write(result,path).receipt();
            assertEquals(DeliveryReceipt.Status.FAILED,receipt.status(),"writer failure never COMPLETE");assertEquals(reason,receipt.reason());
            if(reason!=DeliveryReceipt.Reason.FINALIZATION_FAILED)assertNull(receipt.resultSha256(),"prefix is not result hash");else assertNotNull(receipt.resultSha256());
            assertEquals("old",Files.readString(path),"no partial final file");assertSame(original,result.result());
            try(var paths=Files.list(directory)){assertFalse(paths.anyMatch(p->p.getFileName().toString().endsWith(".tmp")),"temporary cleaned");}
        }
    }
}
