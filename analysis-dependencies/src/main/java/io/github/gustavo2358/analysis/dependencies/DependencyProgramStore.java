package io.github.gustavo2358.analysis.dependencies;

import io.github.gustavo2358.air.model.Evidence;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.air.model.Origins;
import io.github.gustavo2358.analysis.structure.ProgramStore;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/** Streaming dependency events over an admitted program store. */
public interface DependencyProgramStore extends ProgramStore, AutoCloseable {
    @FunctionalInterface interface MetadataHandleConsumer {void accept(long handle,String localId);}
    sealed interface OriginView {
        OriginId id();
        record Unavailable(OriginId id,String reason) implements OriginView {public Unavailable{Objects.requireNonNull(id);Objects.requireNonNull(reason);}}
        record Derived(OriginId id,String rule) implements OriginView {public Derived{Objects.requireNonNull(id);Objects.requireNonNull(rule);}}
        record Contractual(OriginId id,String authority,String version) implements OriginView {public Contractual{Objects.requireNonNull(id);Objects.requireNonNull(authority);Objects.requireNonNull(version);}}
        record Written(OriginId id,ArtifactId artifact,Optional<Origins.Location> location,boolean exact) implements OriginView {public Written{Objects.requireNonNull(id);Objects.requireNonNull(artifact);Objects.requireNonNull(location);}}
    }
    record Producer(long operation,long origin) { }
    record Definition(long objectKey,long firstText,long secondText,int fitLength,String pad,List<Producer> producers) {
        public Definition {if(firstText<=0||secondText<0||fitLength<0)throw new IllegalArgumentException("invalid definition text recipe");Objects.requireNonNull(pad);producers=List.copyOf(producers);if(producers.isEmpty())throw new IllegalArgumentException("definition requires a producer");}
        public boolean direct(){return secondText==0;}
    }
    record ComputedCall(long objectKey,UnitId caller,EntryId entry,LabelId sequence,OperationId operation,
            OriginId siteOrigin,OriginId targetOrigin,Evidence.CoverageStatus coverage,String namespace,ObjectId subject) {
        public ComputedCall {Objects.requireNonNull(caller);Objects.requireNonNull(entry);Objects.requireNonNull(sequence);Objects.requireNonNull(operation);Objects.requireNonNull(siteOrigin);Objects.requireNonNull(targetOrigin);Objects.requireNonNull(coverage);Objects.requireNonNull(namespace);Objects.requireNonNull(subject);}
    }

    void definitions(Consumer<Definition> consumer);
    void computedCalls(Consumer<ComputedCall> consumer);
    void artifactHandles(MetadataHandleConsumer consumer);
    void originHandles(MetadataHandleConsumer consumer);
    void originInputHandles(long originHandle,MetadataHandleConsumer consumer);
    Origins.Artifact materializeArtifact(long handle);
    Origins.Origin materializeOrigin(long handle);
    OriginView originView(long handle);
    OriginId materializeOriginInput(long handle);
    Iterable<Origins.IncludeFrame> cursorOriginIncludes(long originHandle);
    long materializationBytes(Definition definition);
    String materialize(Definition definition);
    OperationId operationId(long handle);
    OriginId originId(long handle);
    @Override void close();
}
