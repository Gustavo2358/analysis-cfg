package io.github.gustavo2358.analysis.cfg.domain;

import io.github.gustavo2358.air.model.Capabilities;
import io.github.gustavo2358.air.model.Evidence;
import io.github.gustavo2358.air.model.Ids.EntryId;
import io.github.gustavo2358.air.model.Ids.LabelId;
import io.github.gustavo2358.air.model.Ids.OperationId;
import io.github.gustavo2358.air.model.Ids.UnitId;
import io.github.gustavo2358.air.model.NamePolicies;
import io.github.gustavo2358.air.model.Publication;
import io.github.gustavo2358.air.model.Unit;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.IntFunction;

/**
 * Read-only control program consumed by the one core CFG projection. Implementations may be
 * Publication-backed or snapshot-backed; a scan never transfers ownership of the underlying AIR.
 * Unit, Entry and Sequence scans are repeatable and use canonical local-ID order.
 */
public interface CfgProgram {
    CfgSource source();
    List<Capabilities.Capability> requiredCapabilities();
    Set<Capabilities.Capability> namePolicyExtensions();
    void units(Consumer<UnitView> consumer);

    /**
     * Typed adapter boundary for an admitted snapshot, not a claim based on PublicationId.
     * All views must come from this exact validator-owned input and expire with it. The adapter
     * remains responsible for faithfully mapping AIR facts, as with every program port.
     * Returning the witness must check both the adapter and admission owner's lifetime.
     */
    interface AdmittedSnapshot extends CfgProgram {
        io.github.gustavo2358.air.validation.SnapshotValidator.CheckedSnapshot admission();
    }

    interface UnitView {
        UnitId id();
        Unit.BodyAvailability body();
        Evidence.InventoryStatus inventory();
        void entries(Consumer<EntryView> consumer);
        void sequences(Consumer<SequenceView> consumer);
    }

    record EntryView(EntryId id, java.util.Optional<LabelId> initialLabel) {
        public EntryView {
            Objects.requireNonNull(id, "id");
            initialLabel = Objects.requireNonNull(initialLabel, "initialLabel");
        }
    }

    record SequenceView(LabelId label, List<OperationId> operations, CfgControl control) {
        public SequenceView {
            Objects.requireNonNull(label, "label");
            operations = immutableOperations(operations);
            Objects.requireNonNull(control, "control");
            if (!control.operation().unit().equals(label.unit())) {
                throw new IllegalArgumentException("sequence control belongs to another Unit");
            }
        }
    }

    /**
     * Immutable operation inventory backed by caller-owned program storage. The access function
     * must return the same identity for each ordinal for the lifetime checked by {@code owner}.
     * No identity is read or cached at construction. Borrowed inventories expire with their owner;
     * consumers needing a detached list must explicitly materialize it while that owner is open.
     */
    final class OperationIds extends java.util.AbstractList<OperationId> implements java.util.RandomAccess {
        private final int count;
        private final IntFunction<OperationId> access;
        private final Runnable owner;

        public OperationIds(int count, IntFunction<OperationId> access, Runnable owner) {
            if(count<0)throw new IllegalArgumentException("negative operation inventory size");
            this.count=count;this.access=Objects.requireNonNull(access);this.owner=Objects.requireNonNull(owner);
            owner.run();
        }
        @Override public int size(){owner.run();return count;}
        @Override public OperationId get(int index){owner.run();Objects.checkIndex(index,count);return Objects.requireNonNull(access.apply(index));}
        @Override public void clear(){throw new UnsupportedOperationException("immutable operation inventory");}
    }

    static List<OperationId> immutableOperations(List<OperationId> operations) {
        Objects.requireNonNull(operations,"operations");
        return operations instanceof OperationIds ? operations : List.copyOf(operations);
    }

    static CfgProgram resident(Publication publication) {
        return new Resident(Objects.requireNonNull(publication, "publication"));
    }

    /** Compatibility adapter. It intentionally retains its caller-owned Publication. */
    final class Resident implements CfgProgram {
        private final Publication publication;
        private final CfgSource source;
        private final Set<Capabilities.Capability> namePolicies;

        private Resident(Publication publication) {
            this.publication = publication;
            source = CfgSource.from(publication);
            namePolicies = NamePolicies.extensions(publication);
        }

        Publication publication() { return publication; }
        @Override public CfgSource source() { return source; }
        @Override public List<Capabilities.Capability> requiredCapabilities() {
            return publication.capabilities().required();
        }
        @Override public Set<Capabilities.Capability> namePolicyExtensions() { return namePolicies; }
        @Override public void units(Consumer<UnitView> consumer) {
            Objects.requireNonNull(consumer, "consumer");
            publication.units().stream().sorted(Comparator.comparing(unit -> unit.id().localId()))
                    .forEach(unit -> consumer.accept(new ResidentUnit(unit)));
        }
    }

    final class ResidentUnit implements UnitView {
        private final Unit unit;
        private ResidentUnit(Unit unit) { this.unit = unit; }
        @Override public UnitId id() { return unit.id(); }
        @Override public Unit.BodyAvailability body() { return unit.body(); }
        @Override public Evidence.InventoryStatus inventory() { return unit.coverage().inventory(); }
        @Override public void entries(Consumer<EntryView> consumer) {
            Objects.requireNonNull(consumer, "consumer");
            unit.entries().stream().sorted(Comparator.comparing(entry -> entry.id().localId()))
                    .map(entry -> new EntryView(entry.id(), entry.initialLabel()))
                    .forEach(consumer);
        }
        @Override public void sequences(Consumer<SequenceView> consumer) {
            Objects.requireNonNull(consumer, "consumer");
            unit.sequences().stream().sorted(Comparator.comparing(sequence -> sequence.label().localId()))
                    .map(sequence -> new SequenceView(sequence.label(),
                            sequence.instructions().stream().map(operation -> operation.header().id()).toList(),
                            CfgControl.from(sequence.terminator())))
                    .forEach(consumer);
        }
    }
}
