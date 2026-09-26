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
    public record Read(Publication publication,long airReads,long airBytesObserved,String sha256) {
        public Read(Publication publication,long airReads,long airBytesObserved){this(publication,airReads,airBytesObserved,"");}
    }
    public Read read(Path path) throws IOException {
        byte[] bytes;
        try(var input=Files.newInputStream(path)) {bytes=input.readAllBytes();}
        return new Read(partialAnalysis?codec.decodeForPartialAnalysis(bytes).publication():codec.decode(bytes),1,bytes.length,sha(bytes));
    }
    private static String sha(byte[] bytes){try{return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));}catch(java.security.NoSuchAlgorithmException ex){throw new IllegalStateException(ex);}}
}
