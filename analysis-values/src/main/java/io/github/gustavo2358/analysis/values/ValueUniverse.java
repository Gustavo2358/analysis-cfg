package io.github.gustavo2358.analysis.values;

import io.github.gustavo2358.air.model.Values.TextValue;
import io.github.gustavo2358.air.model.Ids.*;
import java.util.*;

/** Interned immutable logical values and producer evidence; exact Unicode scalars, no normalization. */
final class ValueUniverse {
    private final Map<LogicalText,Integer> ordinals=new HashMap<>();
    private final List<LogicalText> values=new ArrayList<>();
    private final List<Candidates> singletons=new ArrayList<>();
    private record Producer(int candidate,ValueFact.Support support) { }
    private final List<Producer> producers=new ArrayList<>();
    private final Map<Producer,Integer> producerIds=new HashMap<>();
    long poolHits,scalarsHashed;
    Candidates supported(TextValue value,Id evidence,OriginId origin,List<PremiseId> premises,ValuesWork work) {
        return supported(LogicalText.of(value.value()),evidence,origin,premises,work);
    }
    Candidates supported(LogicalText value,Id evidence,OriginId origin,List<PremiseId> premises,ValuesWork work) {
        var singleton=intern(value,work);var producer=new Producer(singleton.at(0),new ValueFact.Support(evidence,origin,premises));
        var ordinal=producerIds.get(producer);
        if(ordinal==null){ordinal=producers.size();Math.incrementExact(ordinal);producers.add(producer);producerIds.put(producer,ordinal);}
        return singleton.supportedBy(ordinal,work);
    }
    /** Remap only supports of the captured input alternative onto the derived value. */
    Candidates derived(Candidates result,LogicalText output,LogicalText input,Candidates captured,ValuesWork work) {
        var ordinal=ordinals.get(input);if(ordinal==null)return result;
        for(int i=0;i<captured.supports.size();i++) {
            var producer=producers.get(captured.supports.at(i));
            if(producer.candidate()==ordinal) {
                var support=producer.support();
                result=result.join(supported(output,support.evidence(),support.origin(),support.premises(),work),work);
            }
        }
        return result;
    }
    LogicalText value(int ordinal){return values.get(ordinal);}
    boolean partial(Candidates candidates){for(int i=0;i<candidates.size();i++)if(!values.get(candidates.at(i)).complete())return true;return false;}
    private static String supportUnit(Id id){return id instanceof OperationId op?op.unit().localId():((OperandId)id).owner().unit().localId();}
    private static int supportKind(Id id){return id instanceof OperationId?0:((OperandId)id).owner() instanceof EntryOwner?1:2;}
    private static String supportOwner(Id id){
        if(id instanceof OperationId)return "";
        return switch(((OperandId)id).owner()) {case EntryOwner e -> e.entry().localId();case OperationOwner o -> o.operation().localId();};
    }
    private static final Comparator<ValueFact.Support> SUPPORT_ORDER=Comparator
        .comparing((ValueFact.Support s)->s.evidence().publication().localId())
        .thenComparing(s->supportUnit(s.evidence())).thenComparingInt(s->supportKind(s.evidence()))
        .thenComparing(s->supportOwner(s.evidence())).thenComparing(s->s.evidence().localId());
    List<ValueFact.CandidateSupport> materializeSupports(Candidates candidates) {
        var selected=new HashMap<Integer,List<ValueFact.Support>>();
        for(int i=0;i<candidates.supports.size();i++) {
            var producer=producers.get(candidates.supports.at(i));
            selected.computeIfAbsent(producer.candidate(),ignored->new ArrayList<>()).add(producer.support());
        }
        var result=new ArrayList<ValueFact.CandidateSupport>();
        for(int i=0;i<candidates.size();i++) {
            int candidate=candidates.at(i);var support=selected.getOrDefault(candidate,List.of()).stream().sorted(SUPPORT_ORDER).toList();
            if(values.get(candidate).complete())result.add(new ValueFact.CandidateSupport(new TextValue(values.get(candidate).text()),support));
        }
        result.sort(Comparator.comparing(s->s.candidate().value()));return List.copyOf(result);
    }
    int producerCount(){return producers.size();}
    Candidates intern(TextValue value,ValuesWork work) {return intern(LogicalText.of(value.value()),work);}
    private Candidates intern(LogicalText value,ValuesWork work) {
        scalarsHashed=Math.addExact(scalarsHashed,value.length());
        var existing=ordinals.get(value);
        if(existing!=null){poolHits=Math.incrementExact(poolHits);return singletons.get(existing);}
        int ordinal=values.size();Math.incrementExact(ordinal);
        var singleton=Candidates.singleton(ordinal,work);
        values.add(value);singletons.add(singleton);ordinals.put(value,ordinal);return singleton;
    }
    List<TextValue> materialize(Candidates candidates) {
        var selected=new ArrayList<TextValue>(candidates.size());
        for(int i=0;i<candidates.size();i++)if(values.get(candidates.at(i)).complete())selected.add(new TextValue(values.get(candidates.at(i)).text()));
        selected.sort(Comparator.comparing(TextValue::value));return List.copyOf(selected);
    }
    int size(){return values.size();}
}
