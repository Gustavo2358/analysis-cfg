package io.github.gustavo2358.analysis.dependencies;

import java.util.*;
import io.github.gustavo2358.air.model.Interactions;
import io.github.gustavo2358.analysis.dependencies.source.QualifiedSourceDependencies;
import io.github.gustavo2358.analysis.dependencies.source.QualifiedSourceDependencies.*;

/** Source candidates retain their AND/OR qualification certificate; never AIR sites/edges. */
public record SourceQualifiedDependencyResult(QualifiedSourceDependencies evidence,List<OccurrenceResult> occurrences) {
    public enum Status { QUALIFIED_POSSIBLE, NOT_QUALIFIED_IN_SOURCE_MODEL, CONTROL_UNAVAILABLE }
    public record Candidate(String referenceName,String rawValue,StatementId occurrence,List<String> qualifications) {
        public Candidate {Objects.requireNonNull(referenceName);Objects.requireNonNull(rawValue);Objects.requireNonNull(occurrence);qualifications=List.copyOf(qualifications);if(qualifications.isEmpty())throw new IllegalArgumentException("candidate needs source authority");}
    }
    public record OccurrenceResult(StatementId occurrence,Status status,List<Candidate> candidates,boolean valueRemainder,boolean interpretationRemainder) {
        public OccurrenceResult {Objects.requireNonNull(occurrence);Objects.requireNonNull(status);candidates=List.copyOf(candidates);}
    }
    public SourceQualifiedDependencyResult {
        Objects.requireNonNull(evidence);occurrences=List.copyOf(occurrences);
        // Public memory construction has the same admission as the wire path.
        if(!occurrences.equals(interpret(evidence)))throw new IllegalArgumentException("candidate/occurrence/qualification correlation");
    }
    public static SourceQualifiedDependencyResult admit(QualifiedSourceDependencies evidence,String publication) {
        if(evidence.air().size()!=1 || !evidence.air().getFirst().publication().equals(publication))throw new IllegalArgumentException("AIR publication mismatch");
        return new SourceQualifiedDependencyResult(evidence,interpret(evidence));
    }
    /** Standalone source admission has no executable AIR identity requirement. */
    public static SourceQualifiedDependencyResult admit(QualifiedSourceDependencies evidence) {return new SourceQualifiedDependencyResult(evidence,interpret(evidence));}
    private static List<OccurrenceResult> interpret(QualifiedSourceDependencies evidence) {
        var results=new ArrayList<OccurrenceResult>();
        for(var u:evidence.units())for(var o:u.occurrences()) {
            var candidates=new ArrayList<Candidate>();boolean open=false;
            var status=!u.controlAvailable()?Status.CONTROL_UNAVAILABLE:o.qualifications().isEmpty()?Status.NOT_QUALIFIED_IN_SOURCE_MODEL:Status.QUALIFIED_POSSIBLE;
            if(status==Status.QUALIFIED_POSSIBLE)for(var value:o.values()) {
                String name=null;
                if(o.technology().equals("COBOL") && o.nameProfile().equals(CallNameInterpreter.PROFILE)) {
                    // R7 supplies the literal, not runtime linker certainty. Preserve
                    // the same open name-policy qualification as executable CALL.
                    var i=CallNameInterpreter.interpret(value.value(),false,Interactions.ExactName.INSTANCE);name=i.referenceName();open=true;
                } else if(o.namespace().equals("PROGRAM") && o.nameProfile().equals(CicsNameInterpreter.PROFILE)) {
                    var i=CicsNameInterpreter.interpret(value.value(),false,new Interactions.ExtensionName("cics-ts.program","1"));name=i.referenceName();open|=i.unknownRemainder();
                } else if(o.namespace().equals("FILE") && o.nameProfile().equals("cics-ts.file@1")) {
                    name=FileNamePolicy.name("cics.file",value.value(),false,new Interactions.ExtensionName("cics-ts.file","1"));open|=name==null;
                } else open=true;
                if(name!=null)candidates.add(new Candidate(name,value.value(),o.id(),o.qualifications()));
            }
            results.add(new OccurrenceResult(o.id(),status,candidates,o.valueRemainder(),open));
        }
        return List.copyOf(results);
    }
}
