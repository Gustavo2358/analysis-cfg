package io.github.gustavo2358.analysis.dependencies;

import java.util.*;
import io.github.gustavo2358.air.model.Interactions;
import io.github.gustavo2358.analysis.solver.*;
import java.util.function.LongConsumer;
import java.util.function.LongUnaryOperator;

/** Shared target interpretation and assembly. Computed queries select executable or conditional source providers. */
public final class TargetResolver {
    private TargetResolver() { }
    /** Fixed semantic state shared by resident, SOURCE and general paged assembly.
     * It holds closed authorities/booleans, never a payload-sized collection. */
    static final class AssemblyState {
        private static final String[] AUTHORITIES={"SOURCE_QUALIFIED","SOURCE_CONTROL_POSSIBLE","EXECUTABLE_FLOW","EXECUTABLE_OCCURRENCE","CONDITIONAL_SOURCE_VALUES"};
        private final boolean qualified;
        private long authorities;
        boolean observed,valueOpen,interpretationOpen;
        AssemblyState(boolean qualified,boolean control){this.qualified=qualified;if(qualified)addAuthority(control?1:0);}
        private void addAuthority(int value){int shift=0;for(long rest=authorities;rest!=0;rest>>>=3){if((rest&7)==value+1)return;shift+=3;}authorities|=((long)value+1)<<shift;}
        void site(DependencySiteFact.Reachability reach,Boolean model,boolean source,boolean interpretation){
            if(reach==DependencySiteFact.Reachability.UNREACHABLE_IN_MODEL)return;
            observed=true;addAuthority(reach==DependencySiteFact.Reachability.REACHABLE?2:3);valueOpen|=!Boolean.FALSE.equals(model)||source;interpretationOpen|=interpretation;
        }
        void conditional(){valueOpen=true;addAuthority(4);}
        String finish(String targetKind,boolean sourceRemainder,long sites){
            String reason=null;
            if(qualified&&!observed&&targetKind.equals("COMPUTED")){valueOpen=true;reason=sites==0?"SOURCE_TARGET_WITHOUT_EXECUTABLE_QUERY":"SOURCE_TARGET_WITHOUT_REACHABLE_VALUE";}
            if(targetKind.equals("UNAVAILABLE")){valueOpen=true;reason="SOURCE_TARGET_UNAVAILABLE";}
            if(qualified&&!observed)valueOpen|=sourceRemainder;return reason;
        }
        long packedAuthorities(){return authorities;}
        int authorityCount(){int count=0;for(long rest=authorities;rest!=0;rest>>>=3)count++;return count;}
        String authority(int ordinal){Objects.checkIndex(ordinal,authorityCount());return AUTHORITIES[(int)((authorities>>>(ordinal*3))&7)-1];}
        List<String> authorityList(){var values=new ArrayList<String>(authorityCount());for(int i=0,n=authorityCount();i<n;i++)values.add(authority(i));return List.copyOf(values);}
    }
    static int compareText(SourceTextDictionary text,long a,long b,char[] left,char[] right,AnalysisResources resources,AnalysisResources.Phase phase){
        if(a==b)return 0;int na=text.characterCount(a),nb=text.characterCount(b),n=Math.min(na,nb);
        for(int at=0;at<n;){resources.work(1,phase);int count=Math.min(left.length,n-at);text.readCharacters(a,at,left,0,count);text.readCharacters(b,at,right,0,count);for(int i=0;i<count;i++){int c=Character.compare(left[i],right[i]);if(c!=0)return c;}at+=count;}return Integer.compare(na,nb);
    }
    /** Exact legacy TreeMap key order, not simple reference-name ordering. */
    static int compareCandidateKey(SourceTextDictionary text,long a,long rawA,long b,long rawB,char[] left,char[] right,AnalysisResources resources,AnalysisResources.Phase phase){
        int c=(Integer.toString(text.characterCount(a))+":").compareTo(Integer.toString(text.characterCount(b))+":");
        if(c==0)c=compareText(text,a,b,left,right,resources,phase);return c==0?compareText(text,rawA,rawB,left,right,resources,phase):c;
    }
    /** A lone candidate keeps duplicate supports; a merged candidate applies
     * full ordered identity union, then the caller's stable wire order. */
    static void orderSupportUnion(boolean merged,long first,LongUnaryOperator next,PagedLongIndex.Order identity,PagedLongIndex.Order wire,LongConsumer append,PageStore pages,AnalysisResources resources,AnalysisResources.Phase phase){
        try(var distinct=new PagedLongIndex(pages,resources,phase,identity);var order=new PagedLongIndex(pages,resources,phase,wire)){
            for(long row=first;row!=0;row=next.applyAsLong(row)){resources.work(1,phase);if(!merged||distinct.intern(row,row)==row)order.intern(row,row);}
            try(var cursor=order.cursor()){while(cursor.advance())append.accept(cursor.value());}
        }
    }
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
        // A closed query over the executable subset cannot refute an additional source possibility.
        if(occurrence.sourceControlRemainder()||sites.isEmpty())return true;
        // A closed executable result (including a refutation) takes priority.
        return sites.stream().anyMatch(s->s.reachability()!=DependencySiteFact.Reachability.UNREACHABLE_IN_MODEL
            &&!Boolean.FALSE.equals(s.modelValueRemainder()));
    }
    public static Resolution resolve(QualifiedDependencyOccurrence occurrence,List<DependencySiteFact> sites) {
        return resolve(occurrence,sites,List.of());
    }
    public static Resolution resolve(QualifiedDependencyOccurrence occurrence,List<DependencySiteFact> sites,List<SourceValuesProvider.Candidate> conditional) {
        return resolve(occurrence,sites,conditional,()->{});
    }
    static Resolution resolve(QualifiedDependencyOccurrence occurrence,List<DependencySiteFact> sites,List<SourceValuesProvider.Candidate> conditional,Runnable progress) {
        Objects.requireNonNull(progress).run();
        var candidates=new TreeMap<String,Candidate>();var reasons=new TreeSet<String>();
        boolean qualified=!occurrence.qualifications().isEmpty();
        var state=new AssemblyState(qualified,occurrence.sourceControlRemainder());
        if(occurrence.sourceControlRemainder())reasons.add("SOURCE_CONTROL_UNAVAILABLE");
        for(var site:sites) {
            progress.run();
            reasons.addAll(site.analysisReasons());
            if(site.reachability()==DependencySiteFact.Reachability.UNREACHABLE_IN_MODEL)continue;
            state.site(site.reachability(),site.modelValueRemainder(),site.sourceValueRemainder(),site.interpretationUnknownRemainder());
            for(var candidate:site.candidates()){progress.run();add(candidates,new Candidate(candidate.referenceName(),candidate.rawValue(),candidate.supports(),qualified?occurrence.qualifications():List.of()));}
        }
        if(qualified&&occurrence.targetKind().equals("LITERAL")) {
            boolean cics=occurrence.technology().equals("CICS");
            boolean supportedProfile=occurrence.nameProfile().equals(cics?CicsNameInterpreter.PROFILE:CallNameInterpreter.PROFILE);
            for(var raw:occurrence.literals()) {
                progress.run();
                if(!supportedProfile){state.interpretationOpen=true;continue;}
                var interpreted=interpret(cics,raw,false,cics?new Interactions.ExtensionName("cics-ts.program","1"):Interactions.ExactName.INSTANCE);
                state.interpretationOpen|=interpreted.unknownRemainder()||!cics;
                if(interpreted.referenceName()!=null)add(candidates,new Candidate(interpreted.referenceName(),raw,List.of(),occurrence.qualifications()));
            }
        }
        if(requiresSourceValues(occurrence,sites)&&!conditional.isEmpty()) {
            boolean cics=occurrence.technology().equals("CICS");
            boolean supportedProfile=occurrence.nameProfile().equals(cics?CicsNameInterpreter.PROFILE:CallNameInterpreter.PROFILE);
            state.conditional();reasons.add("CONDITIONAL_NOMINAL_VALUE_EVIDENCE");
            for(var candidate:conditional) {
                progress.run();
                if(!supportedProfile){state.interpretationOpen=true;continue;}
                var interpreted=interpret(cics,candidate.rawValue(),true,cics?new Interactions.ExtensionName("cics-ts.program","1"):Interactions.ExactName.INSTANCE);
                state.interpretationOpen|=interpreted.unknownRemainder()||!cics;
                if(interpreted.referenceName()!=null)add(candidates,new Candidate(interpreted.referenceName(),candidate.rawValue(),List.of(),occurrence.qualifications(),List.of(candidate.support())));
            }
        }
        String reason=state.finish(occurrence.targetKind(),occurrence.sourceValueRemainder(),sites.size());if(reason!=null)reasons.add(reason);
        return new Resolution(occurrence,state.authorityList(),candidates.values().stream().toList(),sites,state.valueOpen,state.interpretationOpen,List.copyOf(reasons));
    }
    private static void add(Map<String,Candidate> candidates,Candidate value) {
        String key=value.referenceName().length()+":"+value.referenceName()+value.rawValue();
        candidates.merge(key,value,(a,b)->new Candidate(a.referenceName(),a.rawValue(),union(a.executableSupports(),b.executableSupports()),union(a.sourceQualifications(),b.sourceQualifications()),union(a.conditionalSupports(),b.conditionalSupports())));
    }
    private static <T> List<T> union(List<T> a,List<T> b) {var values=new LinkedHashSet<>(a);values.addAll(b);return List.copyOf(values);}
}
