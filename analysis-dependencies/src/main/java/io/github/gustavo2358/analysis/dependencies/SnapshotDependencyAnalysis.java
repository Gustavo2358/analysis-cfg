package io.github.gustavo2358.analysis.dependencies;

import io.github.gustavo2358.air.model.AirSnapshotBuilder;
import io.github.gustavo2358.air.model.Evidence;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.air.validation.*;
import java.util.*;

/** Dependency analysis over the admitted snapshot program view. */
public final class SnapshotDependencyAnalysis {
    public interface Storage extends AutoCloseable {
        long add(long objectKey,String rawText);
        void addSupport(long candidate,OperationId producer,OriginId origin);
        void select(long objectKey);boolean advance();long candidate();String rawText();
        void selectSupports(long candidate);void selectOrderedSupports(long candidate);boolean advanceSupport();OperationId supportProducer();OriginId supportOrigin();
        void addCall(DependencyProgramStore.ComputedCall call);
        void selectCalls();boolean advanceCall();long callObjectKey();String callCaller();String callEntry();String callSequence();
        String callOperation();String callSiteOrigin();String callTargetOrigin();Evidence.CoverageStatus callCoverage();
        String callNamespace();String callSubject();long callCandidateCount();long callCount();
        long candidateCount();long unknownRemainderCount();
        void addArtifact(long handle,String localId);void selectArtifacts();boolean advanceArtifact();long artifactHandle();
        void addOrigin(long handle,String localId);void selectOrigins();boolean advanceOrigin();long originHandle();
        void addOriginInput(long originHandle,long inputHandle,String localId);void selectOriginInputs(long originHandle);boolean advanceOriginInput();long originInputHandle();
        AirSnapshotBuilder.Lease claim(long bytes);@Override void close();
    }

    public DirectDependencyResult analyze(SnapshotValidator.CheckedSnapshot checked,
            SnapshotIdentityKeys.Storage identityStorage,Storage storage) {
        try(var result=open(checked,identityStorage,storage)){return result.materialize();}
    }

    public SnapshotDependencyCursorResult open(SnapshotValidator.CheckedSnapshot checked,
            SnapshotIdentityKeys.Storage identityStorage,Storage storage) {
        Objects.requireNonNull(checked);Objects.requireNonNull(identityStorage);Objects.requireNonNull(storage);
        DependencyProgramStore program;
        try { program=new SnapshotProgram(checked,identityStorage); }
        catch (RuntimeException failure) {
            storage.close();
            throw failure;
        }
        return open(program,storage);
    }

    public DirectDependencyResult analyze(DependencyProgramStore program,Storage storage) {
        try(var result=open(program,storage)){return result.materialize();}
    }

    public SnapshotDependencyCursorResult open(DependencyProgramStore program,Storage storage) {
        Objects.requireNonNull(program);Objects.requireNonNull(storage);
        try(var lease=storage.claim(512)) {
            Objects.requireNonNull(lease,"storage returned a null analysis lease");
            program.definitions(definition->{try(var materialized=storage.claim(program.materializationBytes(definition))){Objects.requireNonNull(materialized);long candidate=storage.add(definition.objectKey(),program.materialize(definition));
                for(var producer:definition.producers())storage.addSupport(candidate,program.operationId(producer.operation()),program.originId(producer.origin()));}});
            program.computedCalls(storage::addCall);
            program.originHandles((handle,localId)->{storage.addOrigin(handle,localId);program.originInputHandles(handle,(input,inputId)->storage.addOriginInput(handle,input,inputId));});
            program.artifactHandles(storage::addArtifact);
            return new SnapshotDependencyCursorResult(program.publicationId(),program.inventory(),program,storage);
        } catch(RuntimeException|Error failure) {
            try{storage.close();}catch(RuntimeException|Error cleanup){if(cleanup!=failure)failure.addSuppressed(cleanup);}
            try{program.close();}catch(RuntimeException|Error cleanup){if(cleanup!=failure)failure.addSuppressed(cleanup);}
            throw failure;
        }
    }
}
