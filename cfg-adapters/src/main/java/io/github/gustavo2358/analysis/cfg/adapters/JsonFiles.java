package io.github.gustavo2358.analysis.cfg.adapters;

import com.github.luben.zstd.ZstdInputStream;
import com.github.luben.zstd.ZstdOutputStream;
import java.io.*;
import java.nio.file.*;

/** Physical JSON transport only. JSON codecs and semantic identities stay unchanged. */
public final class JsonFiles {
    private JsonFiles() { }
    public static boolean compressed(Path path) { return path.getFileName().toString().endsWith(".zst"); }
    /** Accept legacy JSON and standard Zstandard frames, including renamed frames. */
    public static InputStream input(Path path) throws IOException {
        var raw = new BufferedInputStream(Files.newInputStream(path));
        try {
            raw.mark(4);
            byte[] magic = raw.readNBytes(4);
            raw.reset();
            boolean frame = magic.length == 4 && magic[0] == 0x28 && magic[1] == (byte)0xb5
                && magic[2] == 0x2f && magic[3] == (byte)0xfd;
            if (compressed(path) && !frame) throw new IOException("Expected Zstandard frame: " + path);
            return frame ? new ZstdInputStream(raw) : raw;
        } catch (IOException | RuntimeException | Error failure) {
            try { raw.close(); } catch (IOException cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }
    /** The final destination selects the format even when writing an atomic staging file. */
    public static OutputStream output(OutputStream raw, Path destination) throws IOException {
        if (!compressed(destination)) return raw;
        try { return new ZstdOutputStream(raw, 3).setChecksum(true); }
        catch (IOException | RuntimeException | Error failure) {
            try { raw.close(); } catch (IOException cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }
    public static byte[] read(Path path) throws IOException {
        try (var in = input(path)) { return in.readAllBytes(); }
    }
    public static void write(Path temporary, Path destination, byte[] json) throws IOException {
        try (var out = output(Files.newOutputStream(temporary), destination)) { out.write(json); }
    }
}
