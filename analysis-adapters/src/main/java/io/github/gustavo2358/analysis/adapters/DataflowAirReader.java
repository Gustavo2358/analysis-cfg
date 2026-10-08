package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.air.json.AirJson;
import io.github.gustavo2358.air.model.Publication;
import java.io.*;
import java.nio.file.*;

/** Shared pinned codec; no second local pre-read size admission policy. */
public final class DataflowAirReader {
    private final AirJson codec;
    private final boolean partialAnalysis;
    public DataflowAirReader() { this(new AirJson()); }
    /** Optional operational codec configuration remains outside semantic analysis. */
    public DataflowAirReader(AirJson codec) { this(codec,false); }
    private DataflowAirReader(AirJson codec,boolean partialAnalysis) { this.codec=java.util.Objects.requireNonNull(codec);this.partialAnalysis=partialAnalysis; }
    public static DataflowAirReader forPartialAnalysis() { return new DataflowAirReader(new AirJson(),true); }
    public record Read(Publication publication,long airReads,long airBytesObserved,String sha256,
                       java.util.Optional<io.github.gustavo2358.air.validation.AirValidator.CheckedPublication> checked) {
        public Read {
            java.util.Objects.requireNonNull(checked);
            checked.ifPresent(c -> { if (c.publication() != publication) throw new IllegalArgumentException("validation snapshot mismatch"); });
        }
        public Read(Publication publication,long airReads,long airBytesObserved,String sha256) {
            this(publication,airReads,airBytesObserved,sha256,java.util.Optional.empty());
        }
        public Read(Publication publication,long airReads,long airBytesObserved){this(publication,airReads,airBytesObserved,"");}
    }
    /** Default route pages physical input; model admission/Publication remain resident for now. */
    public Read read(Path path) throws IOException {
        var resources=new io.github.gustavo2358.analysis.solver.AnalysisResources(new io.github.gustavo2358.analysis.solver.AnalysisResources.Limits(64L*1024*1024,16L*1024*1024,0,Long.MAX_VALUE,4,Long.MAX_VALUE,0));
        return read(path,resources);
    }
    /** Resources cover physical decode staging, not the returned resident Publication. */
    public Read read(Path path,io.github.gustavo2358.analysis.solver.AnalysisResources resources)throws IOException {
        java.util.Objects.requireNonNull(resources);Path directory=Files.createTempDirectory("analysis-air-decode-");
        Throwable primary=null;
        try {
            var digest=digest();
            try(var pages=new FilePageStore(directory,4096,decodeCachePages(resources),resources,io.github.gustavo2358.analysis.solver.AnalysisResources.Phase.DECODE);
                var input=new CountedInput(JsonFiles.input(path),digest)) {
                var staging=new PagedJsonInputStorage(pages,resources);
                var checked=partialAnalysis?codec.decodeCheckedForPartialAnalysis(input,staging):codec.decodeChecked(input,staging);
                return new Read(checked.publication(),1,input.bytes,java.util.HexFormat.of().formatHex(digest.digest()),java.util.Optional.of(checked));
            }
        }catch(io.github.gustavo2358.analysis.solver.PageStore.Failure failure){var io=new IOException("paged AIR input storage failure: "+failure.reason(),failure);primary=io;throw io;}
        catch(IOException|RuntimeException|Error failure){primary=failure;throw failure;}
        finally{try{Files.deleteIfExists(directory);}catch(IOException cleanup){if(primary!=null)primary.addSuppressed(cleanup);else throw cleanup;}}
    }
    /** Fixed cache chosen from the available staging budget, independently of document size. */
    private static int decodeCachePages(io.github.gustavo2358.analysis.solver.AnalysisResources resources){
        long available=resources.limits().heapBytes()-resources.heapUsed();
        long capacity=Math.max(1,(available/3-512)/4256);int pages=1;
        while(pages<4096&&(long)pages*2<=capacity)pages*=2;
        return pages;
    }
    private static java.security.MessageDigest digest(){try{return java.security.MessageDigest.getInstance("SHA-256");}catch(java.security.NoSuchAlgorithmException failure){throw new IllegalStateException(failure);}}
    private static final class CountedInput extends java.security.DigestInputStream {
        long bytes;
        CountedInput(InputStream input,java.security.MessageDigest digest){super(input,digest);}
        @Override public int read()throws IOException {int value=super.read();if(value>=0)bytes=Math.incrementExact(bytes);return value;}
        @Override public int read(byte[] value,int offset,int length)throws IOException {int count=super.read(value,offset,length);if(count>0)bytes=Math.addExact(bytes,count);return count;}
    }
}
