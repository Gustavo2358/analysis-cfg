package io.github.gustavo2358.analysis.cfg.adapters;

import io.github.gustavo2358.air.json.AirJson;
import io.github.gustavo2358.air.model.Publication;
import io.github.gustavo2358.air.validation.ValidationOptions;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/** Bounded physical read followed exclusively by the pinned shared AIR codec. */
public final class AirJsonFileReader {
    private final AirJson.Limits limits;
    private final AirJson codec;

    public AirJsonFileReader() { this(AirJson.Limits.defaults(), ValidationOptions.defaults()); }

    public AirJsonFileReader(AirJson.Limits limits, ValidationOptions validationOptions) {
        this.limits = Objects.requireNonNull(limits, "limits");
        this.codec = new AirJson(limits, validationOptions);
    }

    /** AirJsonException is deliberately propagated unchanged, including code/path/issues. */
    public Publication read(Path path) throws IOException {
        byte[] bytes;
        try (var input = Files.newInputStream(path)) {
            bytes = input.readNBytes(limits.maximumDocumentBytes());
            // Separate extra read avoids max + 1 integer overflow and bounds even a growing file.
            if (input.read() != -1) throw new AirInputLimitException(limits.maximumDocumentBytes());
        }
        return codec.decode(bytes);
    }
}
