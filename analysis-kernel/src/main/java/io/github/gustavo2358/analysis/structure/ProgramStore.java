package io.github.gustavo2358.analysis.structure;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.PublicationId;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Read-only program boundary shared by structural and semantic analyses.
 * Implementations may be resident or snapshot-backed; callers never require a Publication aggregate.
 */
public interface ProgramStore {
    PublicationId publicationId();
    Evidence.InventoryStatus inventory();
    List<Origins.Artifact> artifacts();
    List<Origins.Origin> origins();

    interface Structural extends ProgramStore {
        SemanticVersion airVersion();
        Capabilities.Manifest capabilities();
        Set<Capabilities.Capability> namePolicyExtensions();
        List<Unit> units();
        List<Memory.Storage> storage();
        Evidence.Coverage coverage();
        List<Evidence.Uncertainty> uncertainties();
        List<Proofs.Premise> premises();
    }

    static Structural resident(Publication publication) {
        return new Resident(Objects.requireNonNull(publication, "publication"));
    }

    /** Explicit resident adapter used while the paged implementation is introduced. */
    final class Resident implements Structural {
        private final Publication publication;
        private final Set<Capabilities.Capability> namePolicyExtensions;
        private Resident(Publication publication) {
            this.publication = publication;
            namePolicyExtensions = NamePolicies.extensions(publication);
        }
        @Override public PublicationId publicationId() { return publication.id(); }
        @Override public Evidence.InventoryStatus inventory() { return publication.coverage().inventory(); }
        @Override public SemanticVersion airVersion() { return publication.airVersion(); }
        @Override public Capabilities.Manifest capabilities() { return publication.capabilities(); }
        @Override public Set<Capabilities.Capability> namePolicyExtensions() { return namePolicyExtensions; }
        @Override public List<Origins.Artifact> artifacts() { return publication.artifacts(); }
        @Override public List<Unit> units() { return publication.units(); }
        @Override public List<Memory.Storage> storage() { return publication.storage(); }
        @Override public List<Origins.Origin> origins() { return publication.origins(); }
        @Override public Evidence.Coverage coverage() { return publication.coverage(); }
        @Override public List<Evidence.Uncertainty> uncertainties() { return publication.uncertainties(); }
        @Override public List<Proofs.Premise> premises() { return publication.premises(); }
    }
}
