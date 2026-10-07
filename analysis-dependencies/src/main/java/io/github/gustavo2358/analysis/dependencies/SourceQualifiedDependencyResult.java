package io.github.gustavo2358.analysis.dependencies;

import java.util.*;
import io.github.gustavo2358.air.model.Interactions;
import io.github.gustavo2358.analysis.dependencies.source.QualifiedSourceDependencies;
import io.github.gustavo2358.analysis.dependencies.source.QualifiedSourceDependencies.*;

/** Source candidates retain their AND/OR qualification certificate; never AIR sites/edges. */
public final class SourceQualifiedDependencyResult {
    private final QualifiedSourceDependencies evidence;
    private final List<OccurrenceResult> occurrences;
    private final List<NativeFileResult> nativeFiles;
    private final IdentityHashMap<UnitEvidence,Set<String>> affected;
    private record Prepared(List<OccurrenceResult> occurrences,List<NativeFileResult> nativeFiles,
                            IdentityHashMap<UnitEvidence,Set<String>> affected) { }
    public QualifiedSourceDependencies evidence(){return evidence;}
    public List<OccurrenceResult> occurrences(){return occurrences;}
    public List<NativeFileResult> nativeFiles(){return nativeFiles;}
    Set<String> affected(UnitEvidence unit) {
        var result=affected.get(unit);
        if(result==null)throw new IllegalArgumentException("foreign source certificate unit");
        return result;
    }
    private SourceQualifiedDependencyResult(QualifiedSourceDependencies evidence,Prepared prepared) {
        this.evidence=evidence;occurrences=prepared.occurrences();nativeFiles=prepared.nativeFiles();affected=prepared.affected();
    }
    /** Public detached construction verifies all candidates against one fresh certificate. */
    public SourceQualifiedDependencyResult(QualifiedSourceDependencies evidence,List<OccurrenceResult> occurrences) {
        this(Objects.requireNonNull(evidence),prepare(evidence));
        if(!this.occurrences.equals(List.copyOf(occurrences)))throw new IllegalArgumentException("candidate/occurrence/qualification correlation");
    }
    @Override public boolean equals(Object other){return other instanceof SourceQualifiedDependencyResult r&&evidence.equals(r.evidence)&&occurrences.equals(r.occurrences);}
    @Override public int hashCode(){return 31*evidence.hashCode()+occurrences.hashCode();}
    @Override public String toString(){return "SourceQualifiedDependencyResult[evidence="+evidence+", occurrences="+occurrences+"]";}
    private static boolean intersects(List<String> qualifications,Set<String> assumed) {
        for(var qualification:qualifications)if(assumed.contains(qualification))return true;
        return false;
    }

    public enum Status { QUALIFIED_POSSIBLE, POSSIBLE_UNDER_UNKNOWN_CONTROL, NOT_QUALIFIED_IN_SOURCE_MODEL, CONTROL_UNAVAILABLE }
    public record Candidate(String referenceName,String rawValue,StatementId occurrence,List<String> qualifications) {
        public Candidate {Objects.requireNonNull(referenceName);Objects.requireNonNull(rawValue);Objects.requireNonNull(occurrence);qualifications=List.copyOf(qualifications);if(qualifications.isEmpty())throw new IllegalArgumentException("candidate needs source authority");}
    }
    public record OccurrenceResult(StatementId occurrence,Status status,List<Candidate> candidates,boolean valueRemainder,boolean interpretationRemainder) {
        public OccurrenceResult {Objects.requireNonNull(occurrence);Objects.requireNonNull(status);candidates=List.copyOf(candidates);}
    }
    public record NativeFileResult(NativeFileUse source,Status status,List<Candidate> candidates,boolean remainder) {
        public NativeFileResult {Objects.requireNonNull(source);Objects.requireNonNull(status);candidates=List.copyOf(candidates);}
    }
    public static SourceQualifiedDependencyResult admit(QualifiedSourceDependencies evidence,String publication) {
        if(evidence.air().size()!=1 || !evidence.air().getFirst().publication().equals(publication))throw new IllegalArgumentException("AIR publication mismatch");
        return new SourceQualifiedDependencyResult(evidence,prepare(Objects.requireNonNull(evidence)));
    }
    /** Standalone source admission has no executable AIR identity requirement. */
    public static SourceQualifiedDependencyResult admit(QualifiedSourceDependencies evidence) {return new SourceQualifiedDependencyResult(evidence,prepare(Objects.requireNonNull(evidence)));}
    private static List<String> texts(List<Value> values) {
        var result=new ArrayList<String>(values.size());for(var value:values)result.add(value.value());return result;
    }
    private static Prepared prepare(QualifiedSourceDependencies evidence) {
        var results=new ArrayList<OccurrenceResult>();var files=new ArrayList<NativeFileResult>();
        var affected=new IdentityHashMap<UnitEvidence,Set<String>>();
        for(var u:evidence.units()) {var assumed=SourceControlEvidence.affected(u);affected.put(u,assumed);
            for(var f:u.nativeFiles()) {
                var status=!u.controlAvailable()?Status.CONTROL_UNAVAILABLE:f.qualifications().isEmpty()?Status.NOT_QUALIFIED_IN_SOURCE_MODEL:
                    intersects(f.qualifications(),assumed)?Status.POSSIBLE_UNDER_UNKNOWN_CONTROL:Status.QUALIFIED_POSSIBLE;
                var candidates=new ArrayList<Candidate>();boolean remainder=!f.local()&&f.names().isEmpty()||!f.gaps().isEmpty()||status==Status.POSSIBLE_UNDER_UNKNOWN_CONTROL;
                if(!f.qualifications().isEmpty())for(var value:f.names()) {
                    var name=FileNamePolicy.name("cobol.external-file-name",value.rawValue(),false,Interactions.ExactName.INSTANCE);
                    if(name==null)remainder=true;else candidates.add(new Candidate(name,value.rawValue(),f.statement(),f.qualifications()));
                }
                files.add(new NativeFileResult(f,status,candidates,remainder));
            }
            for(var o:u.occurrences()) {
            var candidates=new ArrayList<Candidate>();boolean open=false;
            var status=!u.controlAvailable()?Status.CONTROL_UNAVAILABLE:o.qualifications().isEmpty()?Status.NOT_QUALIFIED_IN_SOURCE_MODEL:intersects(o.qualifications(),assumed)?Status.POSSIBLE_UNDER_UNKNOWN_CONTROL:Status.QUALIFIED_POSSIBLE;
            if(o.namespace().equals("PROGRAM")) {
                var occurrence=new QualifiedDependencyOccurrence(Optional.of(o.id()),o.id().unit().canonicalProgramName(),o.technology(),o.nameProfile(),o.targetKind(),texts(o.values()),u.controlAvailable()?o.qualifications():List.of(),List.of(),o.valueRemainder(),status==Status.POSSIBLE_UNDER_UNKNOWN_CONTROL);
                var resolved=TargetResolver.resolve(occurrence,List.of());open=resolved.interpretationRemainder();
                for(var candidate:resolved.candidates())candidates.add(new Candidate(candidate.referenceName(),candidate.rawValue(),o.id(),candidate.sourceQualifications()));
            } else if(status==Status.QUALIFIED_POSSIBLE||status==Status.POSSIBLE_UNDER_UNKNOWN_CONTROL)for(var value:o.values()) {
                String name=null;
                if(o.namespace().equals("FILE") && o.nameProfile().equals("cics-ts.file@1")) {
                    name=FileNamePolicy.name("cics.file",value.value(),false,new Interactions.ExtensionName("cics-ts.file","1"));open|=name==null;
                } else open=true;
                if(name!=null)candidates.add(new Candidate(name,value.value(),o.id(),o.qualifications()));
            }
            results.add(new OccurrenceResult(o.id(),status,candidates,o.valueRemainder(),open));
        }
        }
        return new Prepared(List.copyOf(results),List.copyOf(files),affected);
    }
}
