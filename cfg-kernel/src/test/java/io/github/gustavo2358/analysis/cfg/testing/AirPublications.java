package io.github.gustavo2358.analysis.cfg.testing;

import io.github.gustavo2358.air.model.Capabilities;
import io.github.gustavo2358.air.model.Evidence;
import io.github.gustavo2358.air.model.Ids.PublicationId;
import io.github.gustavo2358.air.model.Publication;
import io.github.gustavo2358.air.model.Scopes;
import io.github.gustavo2358.air.model.SemanticVersion;

import java.util.List;

/** Test-only construction of small publications through the shared AIR API. */
public final class AirPublications {
    private AirPublications() {
    }

    public static Publication valid() {
        PublicationId id = new PublicationId("cfg-boundary");
        return publication(id, id, new Capabilities.Manifest(List.of(), List.of()));
    }

    public static Publication withCoverageOwner(PublicationId coverageOwner) {
        PublicationId id = new PublicationId("cfg-boundary");
        return publication(id, coverageOwner, new Capabilities.Manifest(List.of(), List.of()));
    }

    public static Publication requiring(Capabilities.Capability capability) {
        PublicationId id = new PublicationId("cfg-boundary");
        return publication(id, id, new Capabilities.Manifest(List.of(capability), List.of()));
    }

    public static Publication providing(Capabilities.Capability capability) {
        PublicationId id = new PublicationId("cfg-boundary");
        return publication(id, id, new Capabilities.Manifest(List.of(), List.of(capability)));
    }

    private static Publication publication(
            PublicationId id,
            PublicationId coverageOwner,
            Capabilities.Manifest capabilities) {
        return new Publication(
                id,
                SemanticVersion.AIR_2_0_0,
                capabilities,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                new Evidence.Coverage(
                        Evidence.InventoryStatus.COMPLETE,
                        new Scopes.PublicationScope(coverageOwner),
                        List.of(),
                        List.of()),
                List.of(),
                List.of());
    }
}
