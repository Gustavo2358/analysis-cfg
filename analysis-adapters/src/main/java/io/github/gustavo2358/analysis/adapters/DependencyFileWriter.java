package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.analysis.dependencies.DependencyResult;
import java.io.IOException;
import java.nio.file.*;

/** Publish only a complete encoded file, by same-directory atomic replacement. */
public final class DependencyFileWriter {
    public void write(DependencyResult result,Path destination) throws IOException {
        Path target=destination.toAbsolutePath();Path temporary=Files.createTempFile(target.getParent(),".dependencies-",".tmp");
        try {
            try(var stream=Files.newOutputStream(temporary)){new DependencyJson().write(result,stream);}
            Files.move(temporary,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        } finally {Files.deleteIfExists(temporary);}
    }
}
