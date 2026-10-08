package io.github.gustavo2358.analysis.dependencies;

import io.github.gustavo2358.air.model.AirSnapshotBuilder;
import io.github.gustavo2358.air.model.Evidence;
import io.github.gustavo2358.air.validation.*;
import java.util.*;

/** Dependency analysis over the admitted snapshot program view. */
public final class SnapshotDependencyAnalysis {
    public interface Storage extends AutoCloseable {
        void add(long objectKey,long rawText,long producer,long origin);
        void select(long objectKey);boolean advance();long rawText();long producer();long origin();
        AirSnapshotBuilder.Lease claim(long bytes);@Override void close();
    }

    public DirectDependencyResult analyze(SnapshotValidator.CheckedSnapshot checked,
            SnapshotIdentityKeys.Storage identityStorage,Storage storage) {
        Objects.requireNonNull(checked);Objects.requireNonNull(identityStorage);Objects.requireNonNull(storage);
        try(storage;var program=new SnapshotProgram(checked,identityStorage);var lease=storage.claim(512)) {
            Objects.requireNonNull(lease,"storage returned a null analysis lease");
            program.definitions(definition->storage.add(definition.objectKey(),definition.rawText(),definition.producer(),definition.origin()));
            var sites=new ArrayList<DirectDependencyResult.Site>();
            boolean publicationOpen=program.coverage()!=Evidence.InventoryStatus.COMPLETE;
            program.computedCalls(call->{
                var candidates=new ArrayList<DirectDependencyResult.Candidate>();storage.select(call.objectKey());
                while(storage.advance()) {
                    String raw=program.textValue(storage.rawText()),name=raw.stripTrailing();
                    var support=new DirectDependencyResult.Support(program.operationId(storage.producer()),program.originId(storage.origin()),List.of());
                    candidates.add(new DirectDependencyResult.Candidate(name,raw,List.of(support)));
                }
                sites.add(new DirectDependencyResult.Site(call.caller(),call.entry(),call.sequence(),call.operation(),call.siteOrigin(),call.targetOrigin(),
                    call.coverage(),call.namespace(),call.subject(),candidates,
                    publicationOpen||call.coverage()!=Evidence.CoverageStatus.MODELED||candidates.isEmpty()));
            });
            return new DirectDependencyResult(program.publication(),program.coverage(),program.origins(),program.artifacts(),sites);
        }
    }
}
