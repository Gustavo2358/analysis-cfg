package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.analysis.dataflow.PreparedDataflowResult;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;

/** Local publication boundary. Delivery is complete only after atomic finalization succeeds. */
public final class LocalResultWriter {
    @FunctionalInterface public interface Encoder { void encode(PreparedDataflowResult result,OutputStream output) throws IOException; }
    public interface LocalFiles {
        Path temporary(Path destination) throws IOException;
        OutputStream open(Path temporary) throws IOException;
        void finalizeFile(Path temporary,Path destination) throws IOException;
        void cleanup(Path temporary) throws IOException;
    }
    public static final class NioFiles implements LocalFiles {
        public Path temporary(Path destination) throws IOException { return Files.createTempFile(destination.getParent(),".analysis-result-",".tmp"); }
        public OutputStream open(Path temporary) throws IOException { return Files.newOutputStream(temporary); }
        public void finalizeFile(Path temporary,Path destination) throws IOException { Files.move(temporary,destination,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING); }
        public void cleanup(Path temporary) throws IOException { Files.deleteIfExists(temporary); }
    }
    public record Attempt(DeliveryReceipt receipt,Map<String,Long> metrics) { public Attempt { metrics=Map.copyOf(metrics); } }
    private final Encoder encoder;private final LocalFiles files;
    public LocalResultWriter() { this(new ResultJson()::write,new NioFiles()); }
    public LocalResultWriter(Encoder encoder,LocalFiles files) { this.encoder=Objects.requireNonNull(encoder);this.files=Objects.requireNonNull(files); }
    public Attempt write(PreparedDataflowResult result,Path destination) {
        Objects.requireNonNull(result);destination=destination.toAbsolutePath().normalize();
        Path temporary=null;String hash=null;DeliveryReceipt.Reason reason=null;CountingOutput output=null;
        var metrics=new TreeMap<String,Long>();
        for(String name:List.of("resultBytesWritten","resultSha256Computed","deliveryAttempts","deliveryComplete","deliveryFailures","encodingFailures","writeFailures","finalizationFailures","cleanupFailures"))metrics.put(name,0L);
        metrics.put("deliveryAttempts",1L);
        try {
            try {temporary=files.temporary(destination);output=new CountingOutput(files.open(temporary));}
            catch(IOException failure) {reason=DeliveryReceipt.Reason.WRITE_FAILED;}
            if(reason==null) {
                try(var stream=output) {encoder.encode(result,stream);}
                catch(IOException failure) {reason=output.failed?DeliveryReceipt.Reason.WRITE_FAILED:DeliveryReceipt.Reason.ENCODING_FAILED;}
                catch(IllegalArgumentException|IllegalStateException failure) {reason=DeliveryReceipt.Reason.ENCODING_FAILED;}
                if(reason==null) {hash=HexFormat.of().formatHex(output.digest.digest());metrics.put("resultSha256Computed",1L);}
            }
            if(reason==null) {
                try {files.finalizeFile(temporary,destination);temporary=null;}
                catch(IOException failure) {reason=DeliveryReceipt.Reason.FINALIZATION_FAILED;}
            }
        } finally {
            if(output!=null)metrics.put("resultBytesWritten",output.bytes);
            if(temporary!=null)try{files.cleanup(temporary);}catch(IOException failure){metrics.put("cleanupFailures",1L);}
        }
        if(reason==null)metrics.put("deliveryComplete",1L);
        else {metrics.put("deliveryFailures",1L);metrics.put(switch(reason){case ENCODING_FAILED->"encodingFailures";case WRITE_FAILED->"writeFailures";case FINALIZATION_FAILED->"finalizationFailures";},1L);}
        return new Attempt(new DeliveryReceipt(result.result().resultId(),hash,destination.toString(),reason==null?DeliveryReceipt.Status.COMPLETE:DeliveryReceipt.Status.FAILED,reason),metrics);
    }
    private static final class CountingOutput extends OutputStream {
        private final OutputStream out;private final MessageDigest digest;private long bytes;private boolean failed;
        CountingOutput(OutputStream out) { this.out=out;try{digest=MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException failure){throw new IllegalStateException(failure);} }
        @Override public void write(int b)throws IOException {try{out.write(b);}catch(IOException e){failed=true;throw e;}digest.update((byte)b);bytes=Math.incrementExact(bytes);}
        @Override public void write(byte[] b,int off,int len)throws IOException {try{out.write(b,off,len);}catch(IOException e){failed=true;throw e;}digest.update(b,off,len);bytes=Math.addExact(bytes,len);}
        @Override public void flush()throws IOException {try{out.flush();}catch(IOException e){failed=true;throw e;}}
        @Override public void close()throws IOException {try{out.close();}catch(IOException e){failed=true;throw e;}}
    }
}
