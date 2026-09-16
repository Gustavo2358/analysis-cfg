package io.github.gustavo2358.analysis.dataflow;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.analysis.query.*;
import io.github.gustavo2358.analysis.rd.DefinitionFact;
import io.github.gustavo2358.analysis.storage.StorageSubject;
import io.github.gustavo2358.analysis.values.StorageValueFact;
import java.math.BigInteger;
import java.util.*;

/** Detached observations and validated reference inventory; no execution or model graph roots. */
public record RegionalAnalysisResult(String resultId,PublicationId publicationId,Inventory inventory,
        List<Observation> observations,Map<String,Map<String,Long>> statistics) {
    public record Observation(PointQuery<StorageSubject> query,ObservationBatch.Observation<StorageSubject,DefinitionFact> rd,
            ObservationBatch.Observation<StorageSubject,StorageValueFact> values,List<ObjectId> explicitObjects) {
        public Observation(PointQuery<StorageSubject> query,ObservationBatch.Observation<StorageSubject,DefinitionFact> rd,
                ObservationBatch.Observation<StorageSubject,StorageValueFact> values) {
            this(query,rd,values,query.subject() instanceof StorageSubject.NamedObject named?List.of(named.object()):List.of());
        }
        public Observation { explicitObjects=List.copyOf(explicitObjects);Objects.requireNonNull(query);Objects.requireNonNull(rd);Objects.requireNonNull(values);if(!query.equals(rd.query())||!query.equals(values.query()))throw new IllegalArgumentException("different observations"); }
    }
    public record Storage(Memory.StorageHeader header,boolean region,Optional<BigInteger> extent,Optional<UncertaintyId> extentUnknown) {
        public Storage { Objects.requireNonNull(header);Objects.requireNonNull(extent);Objects.requireNonNull(extentUnknown);if(region?extent.isPresent()==extentUnknown.isPresent():extent.isPresent()||extentUnknown.isPresent())throw new IllegalArgumentException("storage extent shape"); }
    }
    public record SourceScope(EntryId entry,Evidence.InventoryStatus publicationInventory,Evidence.InventoryStatus unitInventory,
            List<UncertaintyId> publicationUncertainties,List<UncertaintyId> unitUncertainties,List<UncertaintyId> entryUncertainties) {
        public SourceScope { Objects.requireNonNull(entry);Objects.requireNonNull(publicationInventory);Objects.requireNonNull(unitInventory);publicationUncertainties=List.copyOf(publicationUncertainties);unitUncertainties=List.copyOf(unitUncertainties);entryUncertainties=List.copyOf(entryUncertainties); }
    }
    public record Inventory(Set<Id> ids,List<Storage> storages,List<SourceScope> scopes) {
        public Inventory { ids=Set.copyOf(ids);storages=List.copyOf(storages);scopes=List.copyOf(scopes); }
    }
    public RegionalAnalysisResult {
        Objects.requireNonNull(resultId);if(resultId.isBlank())throw new IllegalArgumentException("empty resultId");Objects.requireNonNull(publicationId);Objects.requireNonNull(inventory);observations=List.copyOf(observations);
        var copy=new TreeMap<String,Map<String,Long>>();statistics.forEach((k,v)->copy.put(k,Map.copyOf(v)));statistics=Collections.unmodifiableMap(copy);
    }
}
