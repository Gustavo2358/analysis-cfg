package io.github.gustavo2358.analysis.dataflow;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.analysis.application.PreparedAnalysisResult;
import io.github.gustavo2358.analysis.structure.IndexMetrics;
import java.util.*;

/** W5 detached transport context around the unchanged W4 prepared result; no delivery state. */
public record PreparedDataflowResult(PreparedAnalysisResult<ObservedValueFact> result,
        Map<EntryId,SourceScope> sourceScopes,Map<ObjectId,StorageId> subjectCells,Map<String,Long> compositionMetrics) {
    public record SourceScope(Evidence.InventoryStatus publicationInventory,Evidence.InventoryStatus unitInventory,
                              List<UncertaintyId> entryUncertaintyRefs) {
        public SourceScope { entryUncertaintyRefs=List.copyOf(entryUncertaintyRefs); }
    }
    public PreparedDataflowResult {
        Objects.requireNonNull(result);sourceScopes=Map.copyOf(sourceScopes);subjectCells=Map.copyOf(subjectCells);compositionMetrics=Map.copyOf(compositionMetrics);
    }
    static PreparedDataflowResult capture(PreparedAnalysisResult<ObservedValueFact> result,Publication publication,
                                          IndexMetrics index,Map<String,Long> selection) {
        var scopes=new HashMap<EntryId,SourceScope>();var cells=new HashMap<ObjectId,StorageId>();
        var required=new HashSet<EntryId>();result.analyses().forEach(a->required.add(a.key().entry()));
        var subjects=new HashSet<ObjectId>();
        for(var batch:result.results())for(var o:batch.observations())subjects.add((ObjectId)o.query().subject());
        for(var unit:publication.units()) {
            for(var entry:unit.entries())if(required.contains(entry.id()))scopes.put(entry.id(),new SourceScope(publication.coverage().inventory(),unit.coverage().inventory(),entry.state().uncertainties()));
            for(var object:unit.objects())if(subjects.contains(object.id())&&object.storage() instanceof Memory.CellBinding binding)cells.put(object.id(),binding.storage());
        }
        var metrics=new TreeMap<>(selection);metrics.put("compositionRuns",1L);metrics.put("cfgBuilds",1L);
        metrics.put("nodesIndexed",index.nodesIndexed());metrics.put("edgesIndexed",index.edgesIndexed());metrics.put("operationsIndexed",index.operationsIndexed());
        metrics.put("objectsIndexed",index.objectsIndexed());metrics.put("locationsIndexed",index.locationsIndexed());metrics.put("referencesResolved",index.referencesResolved());metrics.put("structuralVisits",index.structuralVisits());
        return new PreparedDataflowResult(result,scopes,cells,metrics);
    }
}
