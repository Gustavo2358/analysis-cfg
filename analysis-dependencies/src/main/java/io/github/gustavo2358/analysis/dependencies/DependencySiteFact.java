package io.github.gustavo2358.analysis.dependencies;

import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.analysis.query.ProgramPoint;
import java.util.*;

/** Primary, detached fact. A possible reference is not a proof of runtime linkage. */
public record DependencySiteFact(UnitId caller,EntryId entry,LabelId sequence,OperationId operation,int offset,
        OriginId siteOrigin,OriginId targetOrigin,String technology,String command,String namespace,String nameProfile,TargetKind targetKind,ObjectId subject,ProgramPoint valuePoint,
        Reachability reachability,TargetStatus targetStatus,List<RawCandidate> rawCandidates,List<Candidate> candidates,
        Boolean modelValueRemainder,boolean sourceValueRemainder,boolean interpretationUnknownRemainder,
        boolean effectiveUnknownRemainder,boolean openControlRemainder,List<Id> evidence,List<OriginId> provenance,
        List<PremiseId> premises,List<UncertaintyId> uncertaintyRefs,AnalysisStatus analysisStatus,List<String> analysisReasons) {
    public enum TargetKind { LITERAL, COMPUTED }
    public enum Reachability { REACHABLE, UNREACHABLE_IN_MODEL, UNKNOWN }
    public enum AnalysisStatus { COMPLETE, PARTIAL }
    public enum TargetStatus { RESOLVED_CANDIDATES, OPEN_TARGET, UNREACHABLE_IN_MODEL, UNSUPPORTED_TARGET_EXPRESSION, UNSUPPORTED_INVOCATION_SHAPE, ANALYSIS_INCOMPLETE }
    public enum SupportKind { VALUE_PRODUCER, CALL_LITERAL, CICS_LITERAL }
    public record Support(SupportKind kind,Id producer,OriginId origin,List<PremiseId> premises) {
        public Support {Objects.requireNonNull(kind);Objects.requireNonNull(producer);Objects.requireNonNull(origin);premises=List.copyOf(premises);}
    }
    public record RawCandidate(String rawValue,List<Support> supports) {
        public RawCandidate {Objects.requireNonNull(rawValue);supports=List.copyOf(supports);}
    }
    public record Candidate(String referenceName,String rawValue,List<Support> supports) {
        public Candidate {Objects.requireNonNull(referenceName);Objects.requireNonNull(rawValue);supports=List.copyOf(supports);}
    }
    public DependencySiteFact(UnitId caller,EntryId entry,LabelId sequence,OperationId operation,int offset,
        OriginId siteOrigin,OriginId targetOrigin,String technology,String command,String namespace,String nameProfile,TargetKind targetKind,ObjectId subject,ProgramPoint valuePoint,
        Reachability reachability,TargetStatus targetStatus,List<RawCandidate> rawCandidates,List<Candidate> candidates,
        Boolean modelValueRemainder,boolean sourceValueRemainder,boolean interpretationUnknownRemainder,
        boolean effectiveUnknownRemainder,boolean openControlRemainder,List<Id> evidence,List<OriginId> provenance,
        List<PremiseId> premises,List<UncertaintyId> uncertaintyRefs) {
        this(caller,entry,sequence,operation,offset,siteOrigin,targetOrigin,technology,command,namespace,nameProfile,targetKind,subject,valuePoint,
            reachability,targetStatus,rawCandidates,candidates,modelValueRemainder,sourceValueRemainder,interpretationUnknownRemainder,
            effectiveUnknownRemainder,openControlRemainder,evidence,provenance,premises,uncertaintyRefs,AnalysisStatus.COMPLETE,List.of());
    }
    public DependencySiteFact {
        Objects.requireNonNull(caller);Objects.requireNonNull(entry);Objects.requireNonNull(sequence);Objects.requireNonNull(operation);
        Objects.requireNonNull(siteOrigin);Objects.requireNonNull(targetOrigin);Objects.requireNonNull(targetKind);
        Objects.requireNonNull(technology);Objects.requireNonNull(command);Objects.requireNonNull(namespace);Objects.requireNonNull(nameProfile);
        Objects.requireNonNull(reachability);Objects.requireNonNull(targetStatus);Objects.requireNonNull(analysisStatus);analysisReasons=List.copyOf(analysisReasons);
        if(analysisStatus==AnalysisStatus.COMPLETE?!analysisReasons.isEmpty():analysisReasons.isEmpty()||analysisReasons.stream().anyMatch(String::isBlank))throw new IllegalArgumentException("analysis completion shape");
        if(reachability==Reachability.UNKNOWN&&(analysisStatus!=AnalysisStatus.PARTIAL||!openControlRemainder))throw new IllegalArgumentException("unknown execution requires explicit partiality");
        if(targetStatus==TargetStatus.ANALYSIS_INCOMPLETE&&(analysisStatus!=AnalysisStatus.PARTIAL||!candidates.isEmpty()||!rawCandidates.isEmpty()||!effectiveUnknownRemainder))throw new IllegalArgumentException("incomplete target shape");
        rawCandidates=List.copyOf(rawCandidates);candidates=List.copyOf(candidates);evidence=List.copyOf(evidence);
        provenance=List.copyOf(provenance);premises=List.copyOf(premises);uncertaintyRefs=List.copyOf(uncertaintyRefs);
        if(effectiveUnknownRemainder!=(Boolean.TRUE.equals(modelValueRemainder)||sourceValueRemainder||interpretationUnknownRemainder))throw new IllegalArgumentException("effective remainder");
        if(reachability==Reachability.UNREACHABLE_IN_MODEL&&(!candidates.isEmpty()||targetStatus!=TargetStatus.UNREACHABLE_IN_MODEL||modelValueRemainder!=null))throw new IllegalArgumentException("unreachable shape");
        if(offset<0||!caller.equals(entry.unit())||!caller.equals(sequence.unit())||!caller.equals(operation.unit()))throw new IllegalArgumentException("site identity");
    }
}
