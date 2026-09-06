package io.github.gustavo2358.analysis.cfg.domain;

import io.github.gustavo2358.air.model.Ids.PublicationId;

import java.util.Objects;

/** CFG-owned ordinal within this publication's projection; never an AIR entity ID. */
public record CfgNodeId(PublicationId publicationId, long ordinal) {
    public CfgNodeId {
        Objects.requireNonNull(publicationId, "publicationId");
        if (ordinal < 0) {
            throw new IllegalArgumentException("negative CFG node ordinal");
        }
    }
}
