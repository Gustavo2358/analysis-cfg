package io.github.gustavo2358.analysis.cfg.domain;

import io.github.gustavo2358.air.model.Entries;
import io.github.gustavo2358.air.model.Ids.EntryId;
import io.github.gustavo2358.air.model.Ids.LabelId;
import io.github.gustavo2358.air.model.Ids.OperationId;
import io.github.gustavo2358.air.model.Ids.PublicationId;
import io.github.gustavo2358.air.model.Ids.UnitId;
import io.github.gustavo2358.air.model.Operations;
import io.github.gustavo2358.air.model.Sequence;

import java.util.Optional;
import java.util.List;
import java.util.Objects;

/** Distinct derived node roles with detached AIR identities and compact control facts. */
public sealed interface CfgNode {
    CfgNodeId id();

    record EntryNode(CfgNodeId id, EntryId entry, Optional<LabelId> initialLabel) implements CfgNode {
        public EntryNode {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(entry, "entry");
            initialLabel = Objects.requireNonNull(initialLabel, "initialLabel");
            if (!id.publicationId().equals(entry.publication())
                    || initialLabel.isPresent() && !initialLabel.orElseThrow().unit().equals(entry.unit())) {
                throw new IllegalArgumentException("entry publication differs from CFG node");
            }
        }
        public EntryNode(CfgNodeId id, Entries.Entry source) {
            this(id, Objects.requireNonNull(source, "source").id(), source.initialLabel());
        }
    }

    record SequenceNode(CfgNodeId id, LabelId label, List<OperationId> operations,
            CfgControl control)
            implements CfgNode {
        public SequenceNode {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(label, "label");
            operations = CfgProgram.immutableOperations(operations);
            Objects.requireNonNull(control, "control");
            if (!id.publicationId().equals(label.publication())
                    || !control.operation().unit().equals(label.unit())) {
                throw new IllegalArgumentException("sequence publication differs from CFG node");
            }
        }
        public SequenceNode(CfgNodeId id, Sequence source) {
            this(id, Objects.requireNonNull(source, "source").label(),
                    source.instructions().stream().map(operation -> operation.header().id()).toList(),
                    CfgControl.from(source.terminator()));
        }
        public SequenceNode(CfgNodeId id, LabelId label,
                io.github.gustavo2358.air.model.Terminator terminator) {
            this(id, label, List.of(), CfgControl.from(terminator));
        }
    }

    /** Termination per AIR Halt occurrence. Context stays on HALT transitions, not a global exit. */
    record HaltExit(CfgNodeId id, OperationId operation, Operations.HaltKind haltKind) implements CfgNode {
        public HaltExit {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(operation, "operation");
            Objects.requireNonNull(haltKind, "haltKind");
            if (!id.publicationId().equals(operation.publication())) {
                throw new IllegalArgumentException("halt publication differs from CFG node");
            }
        }
        public HaltExit(CfgNodeId id, Operations.Halt source) {
            this(id, Objects.requireNonNull(source, "source").header().id(), source.haltKind());
        }
    }

    /** A published outside outcome, owned by its exact AIR occurrence. */
    record OutcomeExit(CfgNodeId id, OperationId operation,
            io.github.gustavo2358.air.model.Control.InvocationAlternative outcome) implements CfgNode {
        public OutcomeExit {
            Objects.requireNonNull(id);Objects.requireNonNull(operation);Objects.requireNonNull(outcome);
            if(!id.publicationId().equals(operation.publication())||!CoreCfgProjection.outside(outcome))
                throw new IllegalArgumentException("outside outcome requires a matching AIR namespace");
        }
        public OutcomeExit(CfgNodeId id, CfgControl source,
                io.github.gustavo2358.air.model.Control.InvocationAlternative outcome) {
            this(id, Objects.requireNonNull(source, "source").operation(), outcome);
            if(!CfgControl.alternatives(source).contains(outcome))
                throw new IllegalArgumentException("outside outcome requires its actual AIR occurrence");
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
