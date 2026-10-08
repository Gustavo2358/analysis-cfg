package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.analysis.dependencies.DirectDependencyResult;
import io.github.gustavo2358.analysis.solver.AnalysisResources;
import java.io.*;
import java.nio.file.*;
import java.util.Objects;

/** Atomic bounded publication of one complete snapshot-native dependency result. */
public final class SnapshotDependencyFileWriter {
    public void write(DirectDependencyResult result,Path destination,AnalysisResources resources)throws IOException {
        Objects.requireNonNull(resources);Path target=destination.toAbsolutePath();Path temporary=Files.createTempFile(target.getParent(),".dependencies-",".tmp");
        try {
            try(var raw=JsonFiles.output(Files.newOutputStream(temporary),destination);var stream=new MeteredOutput(raw,resources)){new SnapshotDependencyJson().write(result,stream);}
            Files.move(temporary,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        }finally{Files.deleteIfExists(temporary);}
    }
    private static final class MeteredOutput extends FilterOutputStream {
        private final AnalysisResources resources;private MeteredOutput(OutputStream output,AnalysisResources resources){super(output);this.resources=resources;}
        @Override public void write(int value)throws IOException{resources.output(1,AnalysisResources.Phase.ENCODE);out.write(value);}
        @Override public void write(byte[] value,int offset,int length)throws IOException{resources.output(length,AnalysisResources.Phase.ENCODE);out.write(value,offset,length);}
    }
}
