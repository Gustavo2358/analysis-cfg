package io.github.gustavo2358.analysis.consumers;

import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.air.model.Operation;
import java.util.Objects;

/** One immutable AIR occurrence in a selected activation context. STRUCTURAL never asserts reachability. */
public record SiteView(EntryId entry, LabelId sequence, int offset, Operation operation) {
    public SiteView {
        Objects.requireNonNull(entry); Objects.requireNonNull(sequence); Objects.requireNonNull(operation);
        if (offset < 0 || !sequence.unit().equals(entry.unit()) || !operation.header().id().unit().equals(entry.unit()))
            throw new IllegalArgumentException("site owner/offset mismatch");
    }
    public OperationId operationId() { return operation.header().id(); }
    public OriginId origin() { return operation.header().origin(); }
    public Presence presence() { return Presence.STRUCTURAL; }
    public enum Presence { STRUCTURAL }
}
