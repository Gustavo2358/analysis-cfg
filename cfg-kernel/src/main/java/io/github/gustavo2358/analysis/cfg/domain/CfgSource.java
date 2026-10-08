package io.github.gustavo2358.analysis.cfg.domain;

import io.github.gustavo2358.air.model.Capabilities;
import io.github.gustavo2358.air.model.Evidence;
import io.github.gustavo2358.air.model.Ids.PublicationId;
import io.github.gustavo2358.air.model.Ids.UnitId;
import io.github.gustavo2358.air.model.Publication;
import io.github.gustavo2358.air.model.SemanticVersion;
import java.util.List;
import java.util.Objects;

/** Detached source knowledge retained by a CFG product; never owns the full AIR publication. */
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
        units = List.copyOf(units);
        preciseControlCapabilities = List.copyOf(preciseControlCapabilities);
        if (units.stream().anyMatch(unit -> !unit.id().publication().equals(publicationId))
                || units.stream().map(UnitInventory::id).distinct().count() != units.size()) {
            throw new IllegalArgumentException("CFG source has duplicate or foreign Unit knowledge");
        }
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
