package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.analysis.dependencies.DirectDependencyResult;
import io.github.gustavo2358.analysis.dependencies.SnapshotDependencyCursorResult;
import io.github.gustavo2358.analysis.dependencies.DependencyResult;
import io.github.gustavo2358.analysis.dependencies.ManagedDependencyResult;
import io.github.gustavo2358.analysis.solver.PageStore;
import io.github.gustavo2358.analysis.structure.ProgramStore;
import io.github.gustavo2358.analysis.solver.AnalysisResources;
import java.io.*;
import java.nio.file.*;
import java.util.Objects;

/** Atomic bounded publication of one complete snapshot-native dependency result. */
public final class SnapshotDependencyFileWriter {
    public void write(io.github.gustavo2358.analysis.dependencies.ManagedExecutableDependencyResult result,
            io.github.gustavo2358.analysis.dependencies.SnapshotProgram source,Path destination,AnalysisResources resources)throws IOException {
        Objects.requireNonNull(result);result.requireContext(source,resources);
        write(destination,resources,stream->new DependencyJson().writeSnapshot(result,source,resources,stream),()->result.requireContext(source,resources));
    }
    public void write(ManagedDependencyResult result,Path destination,PageStore pages,AnalysisResources resources)throws IOException {
        Objects.requireNonNull(result);result.admission().snapshot(pages,resources);
        write(destination,resources,stream->new DependencyJson().writeSnapshot(result,result.program(),pages,resources,stream),()->{result.executable();result.programs().count();});
    }
    public void write(DependencyResult result,ProgramStore.Structural source,Path destination,AnalysisResources resources)throws IOException {
        Objects.requireNonNull(result);Objects.requireNonNull(source);write(destination,resources,stream->new DependencyJson().writeSnapshot(result,source,stream));
    }
    public void write(SnapshotDependencyCursorResult result,Path destination,AnalysisResources resources)throws IOException {
        Objects.requireNonNull(result);write(destination,resources,stream->new SnapshotDependencyJson().write(result,stream));
    }
    public void write(DirectDependencyResult result,Path destination,AnalysisResources resources)throws IOException {
        Objects.requireNonNull(result);write(destination,resources,stream->new SnapshotDependencyJson().write(result,stream));
    }
    private void write(Path destination,AnalysisResources resources,Encoder encoder)throws IOException {
        write(destination,resources,encoder,()->{});
    }
    private void write(Path destination,AnalysisResources resources,Encoder encoder,Runnable verify)throws IOException {
        Objects.requireNonNull(resources);Path target=destination.toAbsolutePath();Path temporary=Files.createTempFile(target.getParent(),".dependencies-",".tmp");
        try {
            try(var raw=JsonFiles.output(Files.newOutputStream(temporary),destination);var stream=ManagedOutputStream.metered(raw,resources)){encoder.write(stream);}
            verify.run();
            Files.move(temporary,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        }finally{Files.deleteIfExists(temporary);}
    }
    @FunctionalInterface private interface Encoder{void write(OutputStream stream)throws IOException;}
}
