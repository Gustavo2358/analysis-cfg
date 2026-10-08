package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.air.json.AirJson;
import io.github.gustavo2358.air.model.AirSnapshot;
import io.github.gustavo2358.air.model.Publication;
import io.github.gustavo2358.air.validation.*;
import io.github.gustavo2358.analysis.solver.*;
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
    /** Owned direct input session: checked typed facts, page store and temporary files share a lifetime. */
    public static final class SnapshotRead implements AutoCloseable {
        private SnapshotValidator.CheckedSnapshot checked;private PageStore pages;private final AnalysisResources resources;private Path directory;
        private final long airReads,airBytesObserved;private final String sha256;
        private SnapshotRead(SnapshotValidator.CheckedSnapshot checked,PageStore pages,AnalysisResources resources,Path directory,long bytes,String sha256){this.checked=checked;this.pages=pages;this.resources=resources;this.directory=directory;this.airReads=1;this.airBytesObserved=bytes;this.sha256=sha256;}
        public synchronized SnapshotValidator.CheckedSnapshot checked(){open();return checked;}
        public synchronized SnapshotIdentityKeys.Storage newIdentityStorage(){open();return new PagedSnapshotIdentityStorage(pages,resources);}
        public synchronized io.github.gustavo2358.analysis.dependencies.SnapshotDependencyAnalysis.Storage newDependencyStorage(){open();return new PagedSnapshotDependencyStorage(pages,resources);}
        public AnalysisResources resources(){return resources;}public long airReads(){return airReads;}public long airBytesObserved(){return airBytesObserved;}public String sha256(){return sha256;}
        private void open(){if(checked==null)throw new IllegalStateException("snapshot read is closed");}
        @Override public synchronized void close(){if(checked==null)return;Throwable failure=null;try{checked.close();}catch(RuntimeException|Error cleanup){failure=cleanup;}checked=null;try{if(pages!=null)pages.close();}catch(RuntimeException|Error cleanup){if(failure==null)failure=cleanup;else if(failure!=cleanup)failure.addSuppressed(cleanup);}pages=null;try{if(directory!=null)Files.deleteIfExists(directory);}catch(IOException cleanup){if(failure==null)failure=cleanup;else failure.addSuppressed(cleanup);}directory=null;if(failure instanceof RuntimeException error)throw error;if(failure instanceof Error error)throw error;if(failure!=null)throw new IllegalStateException("snapshot input cleanup",failure);}
    }
    /** New production path: JSON -> typed paged AIR -> one central admission certificate. */
    public SnapshotRead readSnapshot(Path path,AnalysisResources resources)throws IOException {
        java.util.Objects.requireNonNull(path);java.util.Objects.requireNonNull(resources);Path directory=Files.createTempDirectory("analysis-air-snapshot-");FilePageStore pages=null;AirSnapshot snapshot=null;SnapshotValidator.CheckedSnapshot checked=null;Throwable primary=null;
        try {
            pages=new FilePageStore(directory,4096,decodeCachePages(resources),resources,AnalysisResources.Phase.DECODE);var digest=digest();
            try(var input=new CountedInput(JsonFiles.input(path),digest)) {
                snapshot=codec.decodeSnapshot(input,new PagedJsonInputStorage(pages,resources),new PagedAirStorage(pages,resources,AnalysisResources.Phase.DECODE));
                checked=SnapshotValidator.check(snapshot,ValidationOptions.defaults(),new PagedSnapshotValidationStorage(pages,resources));snapshot=null;
                return new SnapshotRead(checked,pages,resources,directory,input.bytes,java.util.HexFormat.of().formatHex(digest.digest()));
            }
        }catch(PageStore.Failure failure){var io=new IOException("paged AIR snapshot storage failure: "+failure.reason(),failure);primary=io;throw io;}
        catch(IOException|RuntimeException|Error failure){primary=failure;throw failure;}
        finally {if(checked==null){try{if(snapshot!=null)snapshot.close();}catch(RuntimeException|Error cleanup){if(primary!=null)primary.addSuppressed(cleanup);else throw cleanup;}try{if(pages!=null)pages.close();}catch(RuntimeException|Error cleanup){if(primary!=null)primary.addSuppressed(cleanup);else throw cleanup;}try{Files.deleteIfExists(directory);}catch(IOException cleanup){if(primary!=null)primary.addSuppressed(cleanup);else throw cleanup;}}}
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
