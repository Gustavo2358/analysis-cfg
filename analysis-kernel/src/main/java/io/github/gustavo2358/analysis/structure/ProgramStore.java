package io.github.gustavo2358.analysis.structure;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import java.util.*;
import java.util.function.IntFunction;

/** Read-only program boundary; structural consumers never require AIR aggregates. */
public interface ProgramStore {
    /** Operational safepoints, never a semantic cutoff or a reduced answer.
     * The resident compatibility route has no managed execution owner. */
    enum ExecutionPhase { INDEX, DEMAND, CONTROL, DOMAIN, REPLAY, SORT, ENCODE }
    default void progress(ExecutionPhase phase) { Objects.requireNonNull(phase); }
    PublicationId publicationId();
    Evidence.InventoryStatus inventory();
    List<Origins.Artifact> artifacts();
    List<Origins.Origin> origins();

    interface Structural extends ProgramStore {
        SemanticVersion airVersion();
        Capabilities.Manifest capabilities();
        Set<Capabilities.Capability> namePolicyExtensions();
        List<UnitView> units();
        List<Memory.Storage> storage();
        List<Interactions.Resource> resources();
        CoverageView coverage();
        List<Evidence.Uncertainty> uncertainties();
        List<Proofs.Premise> premises();
        /** Complete immutable declaration catalogue supplied by an admitted owner.
         * Empty selects the explicit resident compatibility inventory. */
        default Optional<DeclarationInventory> declarationInventory(){return Optional.empty();}
        /** Complete storage catalogue; absent selects caller-managed resident compatibility. */
        default Optional<StorageInventory> storageInventory(){return Optional.empty();}
        /** Exact identity lookup. Native implementations can resolve a cold address without
         * retaining every Origin body. The resident compatibility default remains explicit. */
        default Origins.Origin origin(OriginId id) {
            Objects.requireNonNull(id);return origins().stream().filter(value->value.id().equals(id)).findFirst().orElse(null);
        }
    }

    /** Original AIR order plus full nominal lookup, without requiring resident keys.
     * identityAt checks the complete identity, never position alone. */
    interface DeclarationInventory extends Map<ObjectId,Memory.ObjectDeclaration> {
        boolean identityAt(int ordinal,ObjectId identity);
    }
    /** Original AIR order and exact nominal lookup without retaining every storage body. */
    interface StorageInventory extends Map<StorageId,Memory.Storage> {
        boolean identityAt(int ordinal,StorageId identity);
    }

    /** Metadata/body addresses, not an owning Unit containing the complete executable payload. */
    interface UnitView {
        UnitId id();
        Optional<UnitId> containingUnit();
        List<Memory.ObjectDeclaration> objects();
        List<ObjectId> visibleObjects();
        List<Entries.Entry> entries();
        List<SequenceView> sequences();
        List<Entries.CompletionPort> completionPorts();
        Unit.BodyAvailability body();
        Optional<UncertaintyId> bodyUnavailable();
        CoverageView coverage();
        OriginId origin();
    }

    /** Operation access may reconstruct one occurrence. Java object identity is not an AIR ID. */
    interface SequenceView {
        LabelId label();
        List<Instruction> instructions();
        Terminator terminator();
        OriginId origin();
    }

    /** Reading inventory/uncertainties does not materialize every coverage output. */
    interface CoverageView {
        Evidence.InventoryStatus inventory();
        Scopes.FactScope scope();
        List<Evidence.CoverageItem> items();
        List<UncertaintyId> uncertainties();
    }

    /** Frozen ordinal inventory borrowing its owner's storage, without a decoded-value cache. */
    final class BorrowedList<T> extends AbstractList<T> implements RandomAccess {
        private final int count;
        private final IntFunction<T> access;
        private final Runnable owner;
        public BorrowedList(int count,IntFunction<T> access,Runnable owner) {
            if(count<0)throw new IllegalArgumentException("negative borrowed inventory size");
            this.count=count;this.access=Objects.requireNonNull(access);this.owner=Objects.requireNonNull(owner);
            owner.run();
        }
        @Override public int size(){owner.run();return count;}
        @Override public T get(int ordinal){owner.run();Objects.checkIndex(ordinal,count);return Objects.requireNonNull(access.apply(ordinal));}
        @Override public void clear(){throw new UnsupportedOperationException("immutable borrowed inventory");}
    }

    static Structural resident(Publication publication) {return new Resident(Objects.requireNonNull(publication));}
    static UnitView residentUnit(Unit unit) {return new ResidentUnit(Objects.requireNonNull(unit));}
    static SequenceView residentSequence(Sequence sequence) {return new ResidentSequence(Objects.requireNonNull(sequence));}
    static CoverageView residentCoverage(Evidence.Coverage coverage) {return new ResidentCoverage(Objects.requireNonNull(coverage));}

    /** Explicit compatibility route. Its Publication and AIR payload are caller-managed. */
    final class Resident implements Structural {
        private final Publication publication;
        private final Set<Capabilities.Capability> namePolicyExtensions;
        private final List<UnitView> units;
        private Map<OriginId,Origins.Origin> originIndex;
        private Resident(Publication publication) {
            this.publication=publication;namePolicyExtensions=NamePolicies.extensions(publication);
            units=publication.units().stream().map(ProgramStore::residentUnit).toList();
        }
        @Override public PublicationId publicationId(){return publication.id();}
        @Override public Evidence.InventoryStatus inventory(){return publication.coverage().inventory();}
        @Override public SemanticVersion airVersion(){return publication.airVersion();}
        @Override public Capabilities.Manifest capabilities(){return publication.capabilities();}
        @Override public Set<Capabilities.Capability> namePolicyExtensions(){return namePolicyExtensions;}
        @Override public List<Origins.Artifact> artifacts(){return publication.artifacts();}
        @Override public List<UnitView> units(){return units;}
        @Override public List<Memory.Storage> storage(){return publication.storage();}
        @Override public List<Interactions.Resource> resources(){return publication.resources();}
        @Override public List<Origins.Origin> origins(){return publication.origins();}
        @Override public Origins.Origin origin(OriginId id){
            Objects.requireNonNull(id);
            if(originIndex==null){originIndex=new HashMap<>();for(var value:publication.origins())originIndex.put(value.id(),value);}
            return originIndex.get(id);
        }
        @Override public CoverageView coverage(){return residentCoverage(publication.coverage());}
        @Override public List<Evidence.Uncertainty> uncertainties(){return publication.uncertainties();}
        @Override public List<Proofs.Premise> premises(){return publication.premises();}
    }

    record ResidentUnit(Unit source) implements UnitView {
        public ResidentUnit{Objects.requireNonNull(source);}
        @Override public UnitId id(){return source.id();}
        @Override public Optional<UnitId> containingUnit(){return source.containingUnit();}
        @Override public List<Memory.ObjectDeclaration> objects(){return source.objects();}
        @Override public List<ObjectId> visibleObjects(){return source.visibleObjects();}
        @Override public List<Entries.Entry> entries(){return source.entries();}
        @Override public List<SequenceView> sequences(){return source.sequences().stream().map(ProgramStore::residentSequence).toList();}
        @Override public List<Entries.CompletionPort> completionPorts(){return source.completionPorts();}
        @Override public Unit.BodyAvailability body(){return source.body();}
        @Override public Optional<UncertaintyId> bodyUnavailable(){return source.bodyUnavailable();}
        @Override public CoverageView coverage(){return residentCoverage(source.coverage());}
        @Override public OriginId origin(){return source.origin();}
    }
    record ResidentSequence(Sequence source) implements SequenceView {
        public ResidentSequence{Objects.requireNonNull(source);}
        @Override public LabelId label(){return source.label();}
        @Override public List<Instruction> instructions(){return source.instructions();}
        @Override public Terminator terminator(){return source.terminator();}
        @Override public OriginId origin(){return source.origin();}
    }
    record ResidentCoverage(Evidence.Coverage source) implements CoverageView {
        public ResidentCoverage{Objects.requireNonNull(source);}
        @Override public Evidence.InventoryStatus inventory(){return source.inventory();}
        @Override public Scopes.FactScope scope(){return source.scope();}
        @Override public List<Evidence.CoverageItem> items(){return source.items();}
        @Override public List<UncertaintyId> uncertainties(){return source.uncertainties();}
    }
}
