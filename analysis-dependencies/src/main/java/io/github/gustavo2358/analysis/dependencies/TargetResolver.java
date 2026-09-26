package io.github.gustavo2358.analysis.dependencies;

import java.util.*;
import io.github.gustavo2358.air.model.Interactions;

/** Shared target interpretation and assembly. Computed queries select executable or conditional source providers. */
public final class TargetResolver {
    private TargetResolver() { }
    public record Candidate(String referenceName,String rawValue,List<DependencySiteFact.Support> executableSupports,
                            List<String> sourceQualifications,List<SourceValuesProvider.Support> conditionalSupports) {
        public Candidate(String referenceName,String rawValue,List<DependencySiteFact.Support> executableSupports,List<String> sourceQualifications){this(referenceName,rawValue,executableSupports,sourceQualifications,List.of());}
        public Candidate { conditionalSupports=List.copyOf(conditionalSupports); executableSupports=List.copyOf(executableSupports);sourceQualifications=List.copyOf(sourceQualifications); }
    }
    public record Resolution(QualifiedDependencyOccurrence occurrence,List<String> authorities,List<Candidate> candidates,
                             List<DependencySiteFact> executableSites,boolean valueRemainder,boolean interpretationRemainder,
                             List<String> analysisReasons) {
        public Resolution { authorities=List.copyOf(authorities);candidates=List.copyOf(candidates);executableSites=List.copyOf(executableSites);analysisReasons=List.copyOf(analysisReasons); }
    }
    public static CallNameInterpreter.Interpretation interpret(boolean cics,String raw,boolean computed,Interactions.NamePolicy policy) {
        return cics?CicsNameInterpreter.interpret(raw,computed,policy):CallNameInterpreter.interpret(raw,computed,policy);
    }
    public static boolean requiresSourceValues(QualifiedDependencyOccurrence occurrence,List<DependencySiteFact> sites) {
        if(!occurrence.targetKind().equals("COMPUTED")||occurrence.qualifications().isEmpty())return false;
        if(sites.isEmpty())return true;
        // A closed executable result (including a refutation) takes priority.
        return sites.stream().anyMatch(s->s.reachability()!=DependencySiteFact.Reachability.UNREACHABLE_IN_MODEL
            &&!Boolean.FALSE.equals(s.modelValueRemainder()));
    }
    public static Resolution resolve(QualifiedDependencyOccurrence occurrence,List<DependencySiteFact> sites) {
        return resolve(occurrence,sites,List.of());
    }
    public static Resolution resolve(QualifiedDependencyOccurrence occurrence,List<DependencySiteFact> sites,List<SourceValuesProvider.Candidate> conditional) {
        var authorities=new ArrayList<String>();var candidates=new TreeMap<String,Candidate>();var reasons=new TreeSet<String>();
        boolean qualified=!occurrence.qualifications().isEmpty();
        if(qualified)authorities.add("SOURCE_QUALIFIED");
        boolean observed=false,valueOpen=false,interpretationOpen=false;
        for(var site:sites) {
            reasons.addAll(site.analysisReasons());
            if(site.reachability()==DependencySiteFact.Reachability.UNREACHABLE_IN_MODEL)continue;
            String authority=site.reachability()==DependencySiteFact.Reachability.REACHABLE?"EXECUTABLE_FLOW":"EXECUTABLE_OCCURRENCE";
            if(!authorities.contains(authority))authorities.add(authority);
            observed=true;valueOpen|=site.modelValueRemainder()==null||site.modelValueRemainder()||site.sourceValueRemainder();
            interpretationOpen|=site.interpretationUnknownRemainder();
            for(var candidate:site.candidates())add(candidates,new Candidate(candidate.referenceName(),candidate.rawValue(),candidate.supports(),qualified?occurrence.qualifications():List.of()));
        }
        if(qualified&&occurrence.targetKind().equals("LITERAL")) {
            boolean cics=occurrence.technology().equals("CICS");
            boolean supportedProfile=occurrence.nameProfile().equals(cics?CicsNameInterpreter.PROFILE:CallNameInterpreter.PROFILE);
            for(var raw:occurrence.literals()) {
                if(!supportedProfile){interpretationOpen=true;continue;}
                var interpreted=interpret(cics,raw,false,cics?new Interactions.ExtensionName("cics-ts.program","1"):Interactions.ExactName.INSTANCE);
                interpretationOpen|=interpreted.unknownRemainder()||!cics;
                if(interpreted.referenceName()!=null)add(candidates,new Candidate(interpreted.referenceName(),raw,List.of(),occurrence.qualifications()));
            }
        }
        if(requiresSourceValues(occurrence,sites)&&!conditional.isEmpty()) {
            boolean cics=occurrence.technology().equals("CICS");
            boolean supportedProfile=occurrence.nameProfile().equals(cics?CicsNameInterpreter.PROFILE:CallNameInterpreter.PROFILE);
            valueOpen=true;authorities.add("CONDITIONAL_SOURCE_VALUES");reasons.add("CONDITIONAL_NOMINAL_VALUE_EVIDENCE");
            for(var candidate:conditional) {
                if(!supportedProfile){interpretationOpen=true;continue;}
                var interpreted=interpret(cics,candidate.rawValue(),true,cics?new Interactions.ExtensionName("cics-ts.program","1"):Interactions.ExactName.INSTANCE);
                interpretationOpen|=interpreted.unknownRemainder()||!cics;
                if(interpreted.referenceName()!=null)add(candidates,new Candidate(interpreted.referenceName(),candidate.rawValue(),List.of(),occurrence.qualifications(),List.of(candidate.support())));
            }
        }
        if(qualified&&!observed&&occurrence.targetKind().equals("COMPUTED")) {
            valueOpen=true;reasons.add(sites.isEmpty()?"SOURCE_TARGET_WITHOUT_EXECUTABLE_QUERY":"SOURCE_TARGET_WITHOUT_REACHABLE_VALUE");
        }
        if(occurrence.targetKind().equals("UNAVAILABLE")){valueOpen=true;reasons.add("SOURCE_TARGET_UNAVAILABLE");}
        if(qualified&&!observed)valueOpen|=occurrence.sourceValueRemainder();
        return new Resolution(occurrence,authorities,candidates.values().stream().toList(),sites,valueOpen,interpretationOpen,List.copyOf(reasons));
    }
    private static void add(Map<String,Candidate> candidates,Candidate value) {
        String key=value.referenceName().length()+":"+value.referenceName()+value.rawValue();
        candidates.merge(key,value,(a,b)->new Candidate(a.referenceName(),a.rawValue(),union(a.executableSupports(),b.executableSupports()),union(a.sourceQualifications(),b.sourceQualifications()),union(a.conditionalSupports(),b.conditionalSupports())));
    }
    private static <T> List<T> union(List<T> a,List<T> b) {var values=new LinkedHashSet<>(a);values.addAll(b);return List.copyOf(values);}
}
