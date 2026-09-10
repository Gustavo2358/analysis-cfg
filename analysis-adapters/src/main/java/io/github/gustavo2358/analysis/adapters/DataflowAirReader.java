package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.air.json.AirJson;
import io.github.gustavo2358.air.model.Publication;
import java.io.*;
import java.nio.file.*;

/** Shared pinned codec; no second local pre-read size admission policy. */
public final class DataflowAirReader {
    public record Read(Publication publication,long airReads,long airBytesObserved) { }
    public Read read(Path path) throws IOException {
        byte[] bytes;
        try(var input=Files.newInputStream(path)) {bytes=input.readAllBytes();}
        // The pinned codec still owns its external capacity debt. Validation remains enabled.
        return new Read(new AirJson().decode(bytes),1,bytes.length);
    }
}
