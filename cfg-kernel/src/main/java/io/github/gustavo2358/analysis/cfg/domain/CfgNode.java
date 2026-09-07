package io.github.gustavo2358.analysis.cfg.domain;

import io.github.gustavo2358.air.model.Entries;
import io.github.gustavo2358.air.model.Ids.EntryId;
import io.github.gustavo2358.air.model.Ids.PublicationId;
import io.github.gustavo2358.air.model.Ids.UnitId;
import io.github.gustavo2358.air.model.Operations;
import io.github.gustavo2358.air.model.Sequence;

import java.util.Objects;

/** Distinct derived node roles with explicit AIR correlation and shared immutable facts. */
public sealed interface CfgNode {
    CfgNodeId id();

    record EntryNode(CfgNodeId id, Entries.Entry source) implements CfgNode {
        public EntryNode {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(source, "source");
            if (!id.publicationId().equals(source.id().publication())) {
                throw new IllegalArgumentException("entry publication differs from CFG node");
            }
        }
    }

    record SequenceNode(CfgNodeId id, Sequence source) implements CfgNode {
        public SequenceNode {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(source, "source");
            if (!id.publicationId().equals(source.label().publication())) {
                throw new IllegalArgumentException("sequence publication differs from CFG node");
            }
        }
    }

    /** Termination per AIR Halt occurrence. Context stays on HALT transitions, not a global exit. */
    record HaltExit(CfgNodeId id, Operations.Halt source) implements CfgNode {
        public HaltExit {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(source, "source");
            if (!id.publicationId().equals(source.header().id().publication())) {
                throw new IllegalArgumentException("halt publication differs from CFG node");
            }
        }
    }

    /** Synthetic normal completion of the activation identified by Entry, with no fabricated origin. */
    record NormalExit(CfgNodeId id, PublicationId publicationId, UnitId unitId, EntryId entryId)
            implements CfgNode {
        public NormalExit {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(publicationId, "publicationId");
            Objects.requireNonNull(unitId, "unitId");
            Objects.requireNonNull(entryId, "entryId");
            if (!id.publicationId().equals(publicationId)
                    || !unitId.publication().equals(publicationId) || !entryId.unit().equals(unitId)) {
                throw new IllegalArgumentException("normal exit namespace mismatch");
            }
        }
    }
}
