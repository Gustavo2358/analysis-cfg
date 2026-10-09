package io.github.gustavo2358.analysis.dependencies;

import io.github.gustavo2358.air.model.AirSnapshot;

/** Compatibility name; occurrence construction belongs to the official AIR access API. */
final class SnapshotOccurrenceReader {
    private final io.github.gustavo2358.air.model.SnapshotOccurrenceReader reader;
    SnapshotOccurrenceReader(AirSnapshot snapshot,Runnable owner) {
        reader=new io.github.gustavo2358.air.model.SnapshotOccurrenceReader(snapshot,owner);
    }
    <T> T read(long handle,Class<T> expected){return reader.read(handle,expected);}
    Object read(long handle){return reader.read(handle);}
}
