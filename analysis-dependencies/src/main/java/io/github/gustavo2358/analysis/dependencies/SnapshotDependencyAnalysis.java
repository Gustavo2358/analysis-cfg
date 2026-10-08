package io.github.gustavo2358.analysis.dependencies;

import io.github.gustavo2358.air.model.AirSnapshotBuilder;
import io.github.gustavo2358.air.model.Evidence;
import io.github.gustavo2358.air.validation.*;
import java.util.*;

/** Dependency analysis over the admitted snapshot program view. */
public final class SnapshotDependencyAnalysis {
    public interface Storage extends AutoCloseable {
        void add(long objectKey,String rawText,List<SnapshotProgram.Producer> producers);
        void select(long objectKey);boolean advance();String rawText();int producerCount();long producer(int index);long origin(int index);
        AirSnapshotBuilder.Lease claim(long bytes);@Override void close();
    }

    public DirectDependencyResult analyze(SnapshotValidator.CheckedSnapshot checked,
            SnapshotIdentityKeys.Storage identityStorage,Storage storage) {
        Objects.requireNonNull(checked);Objects.requireNonNull(identityStorage);Objects.requireNonNull(storage);
        try(storage;var program=new SnapshotProgram(checked,identityStorage);var lease=storage.claim(512)) {
            Objects.requireNonNull(lease,"storage returned a null analysis lease");
            program.definitions(definition->{try(var materialized=storage.claim(program.materializationBytes(definition))){Objects.requireNonNull(materialized);storage.add(definition.objectKey(),program.materialize(definition),definition.producers());}});
            var sites=new ArrayList<DirectDependencyResult.Site>();
            boolean publicationOpen=program.coverage()!=Evidence.InventoryStatus.COMPLETE;
            program.computedCalls(call->{
                var candidates=new TreeMap<String,LinkedHashMap<DirectDependencyResult.Support,DirectDependencyResult.Support>>();storage.select(call.objectKey());
                while(storage.advance()) {
                    String raw=storage.rawText();var supports=candidates.computeIfAbsent(raw,ignored->new LinkedHashMap<>());
                    for(int i=0;i<storage.producerCount();i++) {var support=new DirectDependencyResult.Support(program.operationId(storage.producer(i)),program.originId(storage.origin(i)),List.of());supports.put(support,support);}
                }
                var materialized=new ArrayList<DirectDependencyResult.Candidate>();
                candidates.forEach((raw,supports)->materialized.add(new DirectDependencyResult.Candidate(raw.stripTrailing(),raw,List.copyOf(supports.values()))));
                sites.add(new DirectDependencyResult.Site(call.caller(),call.entry(),call.sequence(),call.operation(),call.siteOrigin(),call.targetOrigin(),
                    call.coverage(),call.namespace(),call.subject(),materialized,
                    publicationOpen||call.coverage()!=Evidence.CoverageStatus.MODELED||materialized.isEmpty()));
            });
            return new DirectDependencyResult(program.publication(),program.coverage(),program.origins(),program.artifacts(),sites);
        }
    }
}
