package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.air.validation.*;
import io.github.gustavo2358.analysis.solver.AnalysisResources;
import io.github.gustavo2358.analysis.solver.PageStore;
import java.util.Objects;

/** Fresh managed ports for one official snapshot validation run. */
public final class PagedSnapshotValidationStorage implements SnapshotValidator.Storage {
    private final PageStore pages;
    private final AnalysisResources resources;
    public PagedSnapshotValidationStorage(PageStore pages,AnalysisResources resources){this.pages=Objects.requireNonNull(pages);this.resources=Objects.requireNonNull(resources);}
    public SnapshotIdentityKeys.Storage identities(){return new PagedSnapshotIdentityStorage(pages,resources);}
    public SnapshotDeclarations.Storage declarations(){return new PagedSnapshotDeclarationsStorage(pages,resources);}
    public SnapshotLocalConstraints.Storage localConstraints(){return new PagedSnapshotLocalStorage(pages,resources);}
    public SnapshotGraphWalk.Storage graphWalk(){return new PagedSnapshotGraphStorage(pages,resources);}
    public SnapshotNominalCycles.Storage nominalCycles(){return new PagedSnapshotCycleStorage(pages,resources);}
    public SnapshotGrounding.Storage grounding(){return new PagedSnapshotGroundingStorage(pages,resources);}
    public SnapshotVisibleObjects.Storage visibleObjects(){return new PagedSnapshotVisibleStorage(pages,resources);}
    public SnapshotSignatureIndex.Storage signatures(){return new PagedSnapshotSignatureStorage(pages,resources);}
    public SnapshotDiagnosticTemplates.Storage diagnostics(){return new PagedSnapshotDiagnosticStorage(pages,resources);}
    public SnapshotReferenceLists.Storage references(){return new PagedSnapshotReferenceStorage(pages,resources);}
    public SnapshotLocalLabels.Storage localLabels(){return new PagedSnapshotLabelStorage(pages,resources);}
    public SnapshotAnnotationTemplates.Storage annotations(){return new PagedSnapshotAnnotationStorage(pages,resources);}
    public SnapshotCapabilities.Storage capabilities(){return new PagedSnapshotCapabilityStorage(pages,resources);}
    public SnapshotDistinctTuples.Storage distinctTuples(){return new PagedSnapshotDistinctStorage(pages,resources);}
    public SnapshotContextWalk.Storage contextWalk(){return new PagedSnapshotContextStorage(pages,resources);}
    public SnapshotTypes.Storage types(){return new PagedSnapshotTypeStorage(pages,resources);}
}
