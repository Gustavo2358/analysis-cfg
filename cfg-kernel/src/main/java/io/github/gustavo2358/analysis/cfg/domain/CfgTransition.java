package io.github.gustavo2358.analysis.cfg.domain;

import io.github.gustavo2358.air.model.Ids.EntryId;

import java.util.Objects;

/**
 * ENTRY establishes activationEntry; RETURN applies only to that activation Entry.
 * These are scoped control rules, not unconditionally composable unlabelled edges.
 */
public record CfgTransition(CfgNodeId from, CfgNodeId to, Kind kind, EntryId activationEntry) {
    public CfgTransition {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(activationEntry, "activationEntry");
        if (!from.publicationId().equals(to.publicationId())
                || !from.publicationId().equals(activationEntry.publication())) {
            throw new IllegalArgumentException("transition namespace mismatch");
        }
    }

    public enum Kind { ENTRY, RETURN }
}
