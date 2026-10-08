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
            operations = List.copyOf(operations);
            Objects.requireNonNull(control, "control");
            if (!control.operation().unit().equals(label.unit())) {
                throw new IllegalArgumentException("sequence control belongs to another Unit");
            }
        }
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
