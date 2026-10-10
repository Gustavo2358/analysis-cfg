package io.github.gustavo2358.analysis.values;

import io.github.gustavo2358.air.model.Values.TextValue;
import io.github.gustavo2358.air.model.Ids.*;
import java.util.*;

/** Interned immutable logical values and producer evidence; exact Unicode scalars, no normalization. */
final class ValueUniverse implements AutoCloseable {
    private final LogicalTextArena texts;
    private final io.github.gustavo2358.analysis.structure.ProgramStore.CapacityLease capacity;
    private int owners=1;
    private boolean closed;
    ValueUniverse(io.github.gustavo2358.analysis.structure.ProgramStore.Structural program) {
        var budget=program.executionBudget();
        capacity=budget.isEmpty()?null:budget.orElseThrow().reserve(io.github.gustavo2358.analysis.structure.ProgramStore.CapacityPool.RESIDENT,512,io.github.gustavo2358.analysis.structure.ProgramStore.ExecutionPhase.DOMAIN);
        LogicalTextArena arena=null;
        try {
            arena=new LogicalTextArena(program);
            var observer=new java.lang.ref.WeakReference<>(this);
            arena.onOwnerClose(()->{var universe=observer.get();if(universe!=null)universe.close();});
            texts=arena;
        } catch(RuntimeException|Error failure) {
            if(arena!=null)try{arena.close();}catch(RuntimeException|Error cleanup){if(cleanup!=failure)failure.addSuppressed(cleanup);}
            if(capacity!=null)try{capacity.close();}catch(RuntimeException|Error cleanup){if(cleanup!=failure)failure.addSuppressed(cleanup);}throw failure;
        }
    }
    void available(){if(closed)throw new IllegalStateException("scalar value owner is closed");texts.available();}
    boolean closed(){return closed;}
    void retain(){available();owners=Math.incrementExact(owners);}
    void release(){if(!closed&&--owners==0)close();}
    LogicalText literal(String value){available();return texts.of(value);}
    private void grow(long bytes){available();if(capacity!=null)capacity.grow(bytes,io.github.gustavo2358.analysis.structure.ProgramStore.ExecutionPhase.DOMAIN);}
    private Map<LogicalText,Integer> ordinals=new HashMap<>();
    private List<LogicalText> values=new ArrayList<>();
    private List<Candidates> singletons=new ArrayList<>();
    private record Producer(int candidate,ValueFact.Support support) { }
    private List<Producer> producers=new ArrayList<>();
    private Map<Producer,Integer> producerIds=new HashMap<>();
    long poolHits,runsHashed;
    Candidates supported(TextValue value,Id evidence,OriginId origin,List<PremiseId> premises,ValuesWork work) {
        return supported(literal(value.value()),evidence,origin,premises,work);
    }
    Candidates supported(LogicalText value,Id evidence,OriginId origin,List<PremiseId> premises,ValuesWork work) {
        var singleton=intern(value,work);var producer=new Producer(singleton.at(0),new ValueFact.Support(evidence,origin,premises));
        var ordinal=producerIds.get(producer);
        if(ordinal==null){ordinal=producers.size();Math.incrementExact(ordinal);grow(Math.addExact(256,Math.multiplyExact(16L,premises.size())));producers.add(producer);producerIds.put(producer,ordinal);}
        return singleton.supportedBy(ordinal,work);
    }
    /** Remap only supports of the captured input alternative onto the derived value. */
    Candidates derived(Candidates result,LogicalText output,LogicalText input,Candidates captured,ValuesWork work) {
        available();
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
    LogicalText value(int ordinal){available();return values.get(ordinal);}
    boolean partial(Candidates candidates){available();for(int i=0;i<candidates.size();i++)if(!values.get(candidates.at(i)).complete())return true;return false;}
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
        available();
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
    int producerCount(){return closed?0:producers.size();}
    Candidates intern(TextValue value,ValuesWork work) {return intern(literal(value.value()),work);}
    private Candidates intern(LogicalText value,ValuesWork work) {
        available();
        runsHashed=Math.addExact(runsHashed,value.runCount());
        var existing=ordinals.get(value);
        if(existing!=null){poolHits=Math.incrementExact(poolHits);return singletons.get(existing);}
        int ordinal=values.size();Math.incrementExact(ordinal);
        // Canonical map entry/key, two growable reference slots and singleton
        // control/ordinal array. Text payload/header capacity belongs to texts.
        grow(160);var singleton=Candidates.singleton(ordinal,work);
        values.add(value);singletons.add(singleton);ordinals.put(value,ordinal);return singleton;
    }
    List<TextValue> materialize(Candidates candidates) {
        available();
        var selected=new ArrayList<TextValue>(candidates.size());
        for(int i=0;i<candidates.size();i++)if(values.get(candidates.at(i)).complete())selected.add(new TextValue(values.get(candidates.at(i)).text()));
        selected.sort(Comparator.comparing(TextValue::value));return List.copyOf(selected);
    }
    int size(){return closed?0:values.size();}
    @Override public void close() {
        if(closed)return;closed=true;owners=0;
        // Expiry may precede public-handle closure: discard stored semantic
        // roots before returning their capacity, without pinning the owner.
        // Drop the containers too: clear() would retain their backing capacity
        // through a still-held expired preparation, after releasing its lease.
        ordinals=null;values=null;singletons=null;producers=null;producerIds=null;
        Throwable failure=null;try{texts.close();}catch(RuntimeException|Error error){failure=error;}
        if(capacity!=null)try{capacity.close();}catch(RuntimeException|Error error){if(failure==null)failure=error;else if(failure!=error)failure.addSuppressed(error);}
        if(failure instanceof RuntimeException error)throw error;if(failure instanceof Error error)throw error;
    }
}
