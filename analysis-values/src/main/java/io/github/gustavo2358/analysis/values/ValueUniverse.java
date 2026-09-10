package io.github.gustavo2358.analysis.values;

import io.github.gustavo2358.air.model.Values.TextValue;
import io.github.gustavo2358.air.model.Ids.*;
import java.util.*;

/** Preparation-only interning; equality is exact Unicode scalar text, without normalization. */
final class ValueUniverse {
    private final Map<TextKey,Integer> ordinals=new HashMap<>();
    private final List<TextValue> values=new ArrayList<>();
    private final List<Candidates> singletons=new ArrayList<>();
    private record Producer(int candidate,ValueFact.Support support) { }
    private final List<Producer> producers=new ArrayList<>();
    long poolHits,scalarsHashed;
    Candidates supported(TextValue value,Id evidence,OriginId origin,List<PremiseId> premises,ValuesWork work) {
        var singleton=intern(value,work);int ordinal=producers.size();Math.incrementExact(ordinal);
        producers.add(new Producer(singleton.at(0),new ValueFact.Support(evidence,origin,premises)));
        return singleton.supportedBy(ordinal,work);
    }
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
            result.add(new ValueFact.CandidateSupport(values.get(candidate),support));
        }
        result.sort(Comparator.comparing(s->s.candidate().value()));return List.copyOf(result);
    }
    int producerCount(){return producers.size();}
    private record TextKey(String text,int hash) {
        @Override public int hashCode(){return hash;}
        @Override public boolean equals(Object other){return other instanceof TextKey k&&text.equals(k.text);}
    }
    Candidates intern(TextValue value,ValuesWork work) {
        int hash=0;String text=value.value();
        for(int offset=0;offset<text.length();) {
            int scalar=text.codePointAt(offset);offset+=Character.charCount(scalar);
            hash=31*hash+scalar; // Hash mixing is intentionally modular; counts/ordinals are checked.
            scalarsHashed=Math.incrementExact(scalarsHashed);
        }
        var key=new TextKey(text,hash);var existing=ordinals.get(key);
        if(existing!=null){poolHits=Math.incrementExact(poolHits);return singletons.get(existing);}
        int ordinal=values.size();Math.incrementExact(ordinal);
        var singleton=Candidates.singleton(ordinal,work);
        values.add(value);singletons.add(singleton);ordinals.put(key,ordinal);return singleton;
    }
    List<TextValue> materialize(Candidates candidates) {
        var selected=new ArrayList<TextValue>(candidates.size());
        for(int i=0;i<candidates.size();i++)selected.add(values.get(candidates.at(i)));
        selected.sort(Comparator.comparing(TextValue::value));return List.copyOf(selected);
    }
    int size(){return values.size();}
}
