package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.analysis.dependencies.*;
import io.github.gustavo2358.analysis.dependencies.source.*;
import io.github.gustavo2358.analysis.solver.*;
import java.io.*;
import java.nio.file.Path;
import java.security.*;
import java.util.*;

/** One typed decode of the exact stream whose digest is checked, followed by
 * complete SOURCE admission. Never prehash/reopen or reconstruct a SOURCE DTO.
 * The returned admission owns only staged SOURCE pages; all inputs are borrowed. */
public final class SourceSnapshotInput {
    private SourceSnapshotInput(){ }
    @SuppressWarnings("try") // File/controller leases govern the decode scope.
    public static SourceAdmissionSnapshot read(Path path,SnapshotProgram program,PageStore pages,AnalysisResources resources,
            String airSha256,Optional<String> wireSha256,Optional<String> documentSha256)throws IOException {
        Objects.requireNonNull(path);Objects.requireNonNull(program);Objects.requireNonNull(pages);Objects.requireNonNull(resources);
        Objects.requireNonNull(airSha256);Objects.requireNonNull(wireSha256);Objects.requireNonNull(documentSha256);program.admission();resources.requireExecutionProgram(program);
        SourceSnapshot snapshot=null;
        try {
            MessageDigest digest=sha256();
            try(var control=resources.reserve(AnalysisResources.Pool.RESIDENT,1024,AnalysisResources.Phase.DECODE);
                var file=resources.reserve(AnalysisResources.Pool.OPEN_FILES,1,AnalysisResources.Phase.DECODE);
                var input=new DigestInputStream(JsonFiles.input(path),digest)) {
                snapshot=new QualifiedSourceJson().decodeSnapshot(input,pages,resources);
            }
            if(wireSha256.isPresent()&&!wireSha256.orElseThrow().equals(HexFormat.of().formatHex(digest.digest())))throw new IllegalArgumentException("source evidence digest mismatch");
            long root=snapshot.root(),air=snapshot.field(root,SourceShape.ROOT,4);
            if(snapshot.size(air,SourceShape.AIR_CORRELATION)!=1)throw new IllegalArgumentException("source/AIR snapshot mismatch");
            long correlation=snapshot.element(air,SourceShape.AIR_CORRELATION,0);
            if(!snapshot.matches(snapshot.field(correlation,SourceShape.AIR_CORRELATION,0),program.publicationId().localId())
                ||!snapshot.matches(snapshot.field(correlation,SourceShape.AIR_CORRELATION,1),airSha256))throw new IllegalArgumentException("source/AIR snapshot mismatch");
            if(documentSha256.isPresent()&&!snapshot.matches(snapshot.field(snapshot.field(root,SourceShape.ROOT,3),SourceShape.DOCUMENT,2),documentSha256.orElseThrow()))throw new IllegalArgumentException("source document digest mismatch");
            program.admission();var transfer=snapshot;snapshot=null;return SourceAdmissionSnapshot.admit(transfer,pages,resources);
        }catch(IOException|RuntimeException|Error failure){if(snapshot!=null)try{snapshot.close();}catch(RuntimeException|Error cleanup){if(failure!=cleanup)failure.addSuppressed(cleanup);}throw failure;}
    }
    private static MessageDigest sha256(){try{return MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException failure){throw new IllegalStateException(failure);}}
}
