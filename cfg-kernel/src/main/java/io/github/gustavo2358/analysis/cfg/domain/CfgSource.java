package io.github.gustavo2358.analysis.cfg.domain;

import io.github.gustavo2358.air.model.Capabilities;
import io.github.gustavo2358.air.model.Evidence;
import io.github.gustavo2358.air.model.Ids.PublicationId;
import io.github.gustavo2358.air.model.Ids.UnitId;
import io.github.gustavo2358.air.model.Publication;
import io.github.gustavo2358.air.model.SemanticVersion;
import java.util.List;
import java.util.Objects;
import java.util.function.IntFunction;

/** Source knowledge retained by a CFG product; snapshot-backed inventories borrow their owner. */
public record CfgSource(
        PublicationId publicationId,
        SemanticVersion airVersion,
        Evidence.InventoryStatus publicationInventory,
        List<UnitInventory> units,
        List<Capabilities.Capability> preciseControlCapabilities) {

    public CfgSource {
        Objects.requireNonNull(publicationId, "publicationId");
        Objects.requireNonNull(airVersion, "airVersion");
        Objects.requireNonNull(publicationInventory, "publicationInventory");
        Objects.requireNonNull(units,"units");
        units = units instanceof UnitInventories ? units : List.copyOf(units);
        preciseControlCapabilities = List.copyOf(preciseControlCapabilities);
        if(units instanceof UnitInventories) {
            String previous=null;
            for(var unit:units) {
                String local=unit.id().localId();
                if(!unit.id().publication().equals(publicationId)||previous!=null&&previous.compareTo(local)>=0)
                    throw new IllegalArgumentException("CFG source has noncanonical, duplicate or foreign Unit knowledge");
                previous=local;
            }
        } else {
            if (units.stream().anyMatch(unit -> !unit.id().publication().equals(publicationId))
                    || units.stream().map(UnitInventory::id).distinct().count() != units.size()) {
                throw new IllegalArgumentException("CFG source has duplicate or foreign Unit knowledge");
            }
        }
    }

    /** Immutable canonical inventory; stable ordinal access expires with the checked owner. */
    public static final class UnitInventories extends java.util.AbstractList<UnitInventory> implements java.util.RandomAccess {
        private final int count;
        private final IntFunction<UnitInventory> access;
        private final Runnable owner;
        public UnitInventories(int count,IntFunction<UnitInventory> access,Runnable owner) {
            if(count<0)throw new IllegalArgumentException("negative Unit inventory size");
            this.count=count;this.access=Objects.requireNonNull(access);this.owner=Objects.requireNonNull(owner);owner.run();
        }
        @Override public int size(){owner.run();return count;}
        @Override public UnitInventory get(int index){owner.run();Objects.checkIndex(index,count);return Objects.requireNonNull(access.apply(index));}
        @Override public void clear(){throw new UnsupportedOperationException("immutable Unit inventory");}
    }

    public static CfgSource from(Publication publication) {
        Objects.requireNonNull(publication, "publication");
        return new CfgSource(
                publication.id(),
                publication.airVersion(),
                publication.coverage().inventory(),
                publication.units().stream()
                        .map(unit -> new UnitInventory(unit.id(), unit.coverage().inventory()))
                        .toList(),
                publication.capabilities().required().stream()
                        .filter(CoreCfgProjection::supportsControlCapability)
                        .distinct()
                        .toList());
    }

    public record UnitInventory(UnitId id, Evidence.InventoryStatus inventory) {
        public UnitInventory {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(inventory, "inventory");
        }
    }
}
