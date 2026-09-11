package io.github.gustavo2358.analysis.dependencies;

import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.analysis.query.ProgramPoint;
import java.util.*;

/** Primary, detached fact. A possible reference is not a proof of runtime linkage. */
public record DependencySiteFact(UnitId caller,EntryId entry,LabelId sequence,OperationId operation,int offset,
        OriginId siteOrigin,OriginId targetOrigin,TargetKind targetKind,ObjectId subject,ProgramPoint valuePoint,
        Reachability reachability,TargetStatus targetStatus,List<RawCandidate> rawCandidates,List<Candidate> candidates,
        Boolean modelValueRemainder,boolean sourceValueRemainder,boolean interpretationUnknownRemainder,
        boolean effectiveUnknownRemainder,boolean openControlRemainder,List<Id> evidence,List<OriginId> provenance,
        List<PremiseId> premises,List<UncertaintyId> uncertaintyRefs) {
    public enum TargetKind { LITERAL, COMPUTED }
    public enum Reachability { REACHABLE, UNREACHABLE_IN_MODEL }
    public enum TargetStatus { RESOLVED_CANDIDATES, OPEN_TARGET, UNREACHABLE_IN_MODEL, UNSUPPORTED_TARGET_EXPRESSION, UNSUPPORTED_INVOCATION_SHAPE }
    public enum SupportKind { VALUE_PRODUCER, CALL_LITERAL }
    public record Support(SupportKind kind,Id producer,OriginId origin,List<PremiseId> premises) {
        public Support {Objects.requireNonNull(kind);Objects.requireNonNull(producer);Objects.requireNonNull(origin);premises=List.copyOf(premises);}
    }
    public record RawCandidate(String rawValue,List<Support> supports) {
        public RawCandidate {Objects.requireNonNull(rawValue);supports=List.copyOf(supports);}
    }
    public record Candidate(String referenceName,String rawValue,List<Support> supports) {
        public Candidate {Objects.requireNonNull(referenceName);Objects.requireNonNull(rawValue);supports=List.copyOf(supports);}
    }
    public DependencySiteFact {
        Objects.requireNonNull(caller);Objects.requireNonNull(entry);Objects.requireNonNull(sequence);Objects.requireNonNull(operation);
        Objects.requireNonNull(siteOrigin);Objects.requireNonNull(targetOrigin);Objects.requireNonNull(targetKind);
        Objects.requireNonNull(reachability);Objects.requireNonNull(targetStatus);
        rawCandidates=List.copyOf(rawCandidates);candidates=List.copyOf(candidates);evidence=List.copyOf(evidence);
        provenance=List.copyOf(provenance);premises=List.copyOf(premises);uncertaintyRefs=List.copyOf(uncertaintyRefs);
        if(effectiveUnknownRemainder!=(Boolean.TRUE.equals(modelValueRemainder)||sourceValueRemainder||interpretationUnknownRemainder))throw new IllegalArgumentException("effective remainder");
        if(reachability==Reachability.UNREACHABLE_IN_MODEL&&(!candidates.isEmpty()||targetStatus!=TargetStatus.UNREACHABLE_IN_MODEL||modelValueRemainder!=null))throw new IllegalArgumentException("unreachable shape");
        if(offset<0||!caller.equals(entry.unit())||!caller.equals(sequence.unit())||!caller.equals(operation.unit()))throw new IllegalArgumentException("site identity");
    }
}
