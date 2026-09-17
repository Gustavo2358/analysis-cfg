package io.github.gustavo2358.analysis.dependencies;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.analysis.consumers.SiteView;
import io.github.gustavo2358.analysis.query.ProgramPoint;
import io.github.gustavo2358.analysis.values.StorageValueFact;
import java.util.*;
import static io.github.gustavo2358.analysis.dependencies.FileDependencyResult.*;

/** Explicit source selection context carried by the versioned generic AIR contract. */
final class FileSystemContext {
    private FileSystemContext() { }
    static String selection(Operations.Invoke i) {
        if(!(i.contract() instanceof Interactions.KnownContract c)||!c.reference().authority().equals("cics-ts.file-control")||!c.reference().version().equals("1")||i.arguments().size()<2)return null;
        if(!(i.arguments().get(0) instanceof Interactions.ValueArgument a)||!(a.value() instanceof Expressions.Literal l)||!(l.value() instanceof Values.TextValue v))return null;
        if(!(i.arguments().get(1) instanceof Interactions.ValueArgument))return null;
        if(v.value().equals("EXPLICIT"))return "EXPLICIT";
        if(v.value().equals("DEFAULT")&&expression(i) instanceof Expressions.Literal s&&s.value() instanceof Values.TextValue t&&t.value().isEmpty())return "DEFAULT";
        return null;
    }
    static Expression expression(Operations.Invoke i){return i.arguments().size()>1&&i.arguments().get(1) instanceof Interactions.ValueArgument a?a.value():null;}
    static Context context(SiteView site,String namespace,Reachability reach,Optional<StorageValueFact> value) {
        if(!"cics.file".equals(namespace))return null;
        var invoke=(Operations.Invoke)site.operation();var selection=selection(invoke);
        if(selection==null)return new Context("UNAVAILABLE","UNKNOWN",null,List.of(),true,site.origin(),List.of("CICS_CONTEXT_CONTRACT_UNAVAILABLE"));
        if(selection.equals("DEFAULT"))return new Context("DEFAULT","ABSENT",null,List.of(),false,site.origin(),List.of());
        var expression=expression(invoke);boolean literal=expression instanceof Expressions.Literal;
        var origin=expression.header().origin();var candidates=new ArrayList<Candidate>();var reasons=new TreeSet<String>();boolean remainder=true;
        if(expression instanceof Expressions.Literal l&&l.value() instanceof Values.TextValue t){
            var name=FileNamePolicy.cicsName(t.value(),false,4);remainder=name==null;
            if(name!=null&&reach!=Reachability.UNREACHABLE_IN_MODEL)candidates.add(new Candidate(name,t.value(),List.of(new Support("CICS_SYSID_LITERAL",expression.header().id(),origin,List.of()))));
            if(name==null)reasons.add("CICS_SYSID_NAME_POLICY_UNSUPPORTED");
        } else if(value.isPresent()&&reach==Reachability.REACHABLE&&value.get().reachability()==io.github.gustavo2358.analysis.values.ValueFact.Reachability.REACHABLE){
            var fact=value.get();remainder=fact.effectiveUnknownRemainder();
            if(Boolean.TRUE.equals(fact.modelValueRemainder()))reasons.add("CICS_SYSID_MODEL_VALUE_REMAINDER");
            if(fact.sourceUnknownRemainder())reasons.add("CICS_SYSID_SOURCE_VALUE_REMAINDER");
            for(var c:fact.candidateSupports()) {
                String raw=c.candidate().value(),name=FileNamePolicy.cicsName(raw,true,4);
                if(name==null){remainder=true;reasons.add("CICS_SYSID_NAME_POLICY_UNSUPPORTED");continue;}
                candidates.add(new Candidate(name,raw,c.producers().stream().map(p->new Support("VALUE_PRODUCER",p.evidence(),p.origin(),p.premises())).toList()));
            }
            if(candidates.isEmpty())remainder=true;
        } else reasons.add("CICS_SYSID_VALUES_UNAVAILABLE");
        candidates.sort(Comparator.comparing(Candidate::referenceName).thenComparing(Candidate::rawValue));
        return new Context("EXPLICIT",literal?"LITERAL":"COMPUTED",literal?null:ProgramPoint.before(site.entry(),site.operationId()),candidates,remainder,origin,List.copyOf(reasons));
    }
}
