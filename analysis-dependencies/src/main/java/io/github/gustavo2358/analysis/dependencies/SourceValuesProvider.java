package io.github.gustavo2358.analysis.dependencies;

import java.util.*;
import io.github.gustavo2358.analysis.dependencies.source.*;
import io.github.gustavo2358.analysis.values.TextPredicate;
import static io.github.gustavo2358.analysis.dependencies.source.QualifiedSourceDependencies.*;

/** Conditional nominal values over the supplied source/state graph, never AIR/CFG synthesis. */
public final class SourceValuesProvider {
    public static final String PROFILE="nominal-source-text@1";
    public record Evidence(String kind,String reference,Provenance provenance) { }
    public record Support(String provider,List<Evidence> evidence,List<String> assumptions,List<NominalValueEvidence.Uncertainty> uncertainties) {
        public Support {evidence=List.copyOf(evidence);assumptions=List.copyOf(assumptions);uncertainties=List.copyOf(uncertainties);}
    }
    public record Candidate(String rawValue,Support support) { }
    private record Values(Map<String,Set<String>> candidates,boolean open) {
        static final Values UNKNOWN=new Values(Map.of(),true);
        Values {var copy=new TreeMap<String,Set<String>>();candidates.forEach((k,v)->copy.put(k,Set.copyOf(v)));candidates=Collections.unmodifiableMap(copy);}
        Values join(Values other){var out=new TreeMap<>(candidates);other.candidates.forEach((k,v)->out.merge(k,v,SourceValuesProvider::union));return new Values(out,open||other.open);}
    }
    private final UnitEvidence unit;
    private final NominalValueEvidence source;
    private final Map<String,Node> nodes=new HashMap<>();
    private final Map<String,Statement> statements=new HashMap<>();
    private final Map<String,List<Derivation>> waiting=new HashMap<>();
    private final Map<String,List<NominalValues.Assignment>> assignments=new HashMap<>();
    private final Map<String,NominalValues.Predicate> predicates=new HashMap<>();
    private final Map<String,Boolean> branches=new HashMap<>();
    private final Map<String,Integer> extents=new TreeMap<>();
    private final Map<String,String> queries=new HashMap<>();
    private final Map<String,Evidence> evidence=new HashMap<>();
    private final Map<String,Map<String,Values>> before=new HashMap<>();
    private final ArrayDeque<String> work=new ArrayDeque<>();
    private final Set<String> queued=new HashSet<>();
    private long workItems;
    private boolean limited;

    public SourceValuesProvider(UnitEvidence unit,Set<String> requested) {
        this.unit=unit;source=unit.nominalValues().orElseThrow();
        unit.nodes().forEach(n->nodes.put(n.id(),n));unit.statements().forEach(s->statements.put(s.id().handle(),s));
        source.facts().queries().stream().filter(q->requested.contains(q.statement())).forEach(q->queries.put(q.statement(),q.node()));
        var demand=new HashSet<>(queries.values());boolean changed;
        do {
            changed=false;
            for(var a:source.facts().assignments())if(demand.contains(a.target())&&a.source().kind().equals("READ"))changed|=demand.add(a.source().value());
            for(var c:source.facts().conditions()){var reads=reads(c.predicate());if(reads.stream().anyMatch(demand::contains))changed|=demand.addAll(reads);}
        }while(changed);
        source.facts().symbols().stream().filter(s->demand.contains(s.node())).forEach(s->extents.put(s.node(),s.extent()));
        for(var a:source.facts().assignments())if(demand.contains(a.target())) {
            assignments.computeIfAbsent(a.statement(),k->new ArrayList<>()).add(a);
            evidence.put(writeKey(a),new Evidence("ASSIGNMENT",a.statement(),statements.get(a.statement()).provenance()));
        }
        source.facts().conditions().forEach(c->predicates.put(c.statement(),c.predicate()));
        source.branches().forEach(b->branches.put(b.derivation(),b.whenTrue()));
        var initial=new TreeMap<String,Values>();extents.keySet().forEach(k->initial.put(k,Values.UNKNOWN));
        for(var seed:source.seeds())if(demand.contains(seed.node())) {
            String key="seed/"+seed.node();evidence.put(key,new Evidence("DECLARATION_VALUE",seed.node(),seed.provenance()));
            initial.put(seed.node(),new Values(Map.of(TextPredicate.fit(seed.value(),extents.get(seed.node())),Set.of(key)),false));
        }
        for(var d:unit.derivations()) {
            var premises=new TreeSet<>(d.source());premises.addAll(d.callerPremise());
            if(premises.isEmpty())join(d.destination(),initial);
            else for(var premise:premises)waiting.computeIfAbsent(premise,k->new ArrayList<>()).add(d);
        }
        // No path enumeration. Each finite value/support fact only grows at a join.
        // The explicit bound limits resources, never licenses an empty/complete answer.
        while(!work.isEmpty()) {
            if(++workItems>1_000_000){limited=true;break;}
            String id=work.removeFirst();queued.remove(id);
            for(var d:waiting.getOrDefault(id,List.of()))propagate(d);
        }
    }
    public long workItems(){return workItems;}
    public boolean limited(){return limited;}
    public List<Candidate> candidates(String statement) {
        String query=queries.get(statement);if(query==null)return List.of();
        Values values=null;
        for(var n:unit.nodes())if(n.location().equals(statement)&&before.containsKey(n.id())) {
            var v=before.get(n.id()).getOrDefault(query,Values.UNKNOWN);values=values==null?v:values.join(v);
        }
        if(values==null)return List.of();
        var out=new ArrayList<Candidate>();
        for(var value:values.candidates().entrySet()) {
            var supports=value.getValue().stream().sorted().map(evidence::get).filter(Objects::nonNull).distinct().toList();
            var assumptions=new ArrayList<>(List.of("NOMINAL_DECLARATIONS_PRESERVE_MEANING","NO_UNMODELED_STORAGE_INTERFERENCE"));
            if(supports.stream().anyMatch(e->e.kind().equals("DECLARATION_VALUE")))assumptions.add("DECLARATIVE_INITIAL_VALUES_APPLY");
            out.add(new Candidate(value.getKey(),new Support(PROFILE,supports,assumptions,source.uncertainties())));
        }
        return List.copyOf(out);
    }
    private void propagate(Derivation d) {
        if(d.source().isEmpty()||!before.containsKey(d.source().getFirst())||d.callerPremise().stream().anyMatch(p->!before.containsKey(p)))return;
        String location=nodes.get(d.source().getFirst()).location();var state=before.get(d.source().getFirst());
        if(branches.containsKey(d.id())){state=filter(state,predicates.get(location),branches.get(d.id()));if(state==null)return;}
        if(d.selection().isEmpty()) {
            var changed=new TreeMap<>(state);
            // All origins read the predecessor snapshot before any receiver update.
            for(var a:assignments.getOrDefault(location,List.of()))changed.put(a.target(),assigned(a,state));
            state=changed;
        }
        join(d.destination(),state);
    }
    private Values assigned(NominalValues.Assignment a,Map<String,Values> state) {
        var input=term(a.source(),state);var out=new TreeMap<String,Set<String>>();
        for(var value:input.candidates().entrySet())out.merge(TextPredicate.fit(value.getKey(),extents.get(a.target())),union(value.getValue(),Set.of(writeKey(a))),SourceValuesProvider::union);
        return new Values(out,input.open());
    }
    private static String writeKey(NominalValues.Assignment a){return "write/"+a.statement()+"/"+a.target();}
    private Values term(NominalValues.Term t,Map<String,Values> state) {
        return switch(t.kind()) {
            case "READ"->state.getOrDefault(t.value(),Values.UNKNOWN);
            case "LITERAL"->new Values(Map.of(t.value(),Set.of()),false);
            case "SPACES"->new Values(Map.of(" ",Set.of()),false);
            default->Values.UNKNOWN; // LOW/HIGH retain unknown collating sequence.
        };
    }
    private Map<String,Values> filter(Map<String,Values> state,NominalValues.Predicate p,boolean whenTrue) {
        if(p==null)return state;int wanted=whenTrue?TextPredicate.TRUE:TextPredicate.FALSE;
        if((truth(p,state)&wanted)==0)return null;
        var result=new TreeMap<>(state);
        for(var symbol:reads(p)) {
            var old=state.get(symbol);if(old==null)continue;var kept=new TreeMap<String,Set<String>>();
            for(var value:old.candidates().entrySet()) {
                var snapshot=new TreeMap<>(state);snapshot.put(symbol,new Values(Map.of(value.getKey(),value.getValue()),false));
                if((truth(p,snapshot)&wanted)!=0)kept.put(value.getKey(),value.getValue());
            }
            if(kept.isEmpty()&&!old.open())return null;
            result.put(symbol,new Values(kept,old.open()));
        }
        return result;
    }
    private int truth(NominalValues.Predicate p,Map<String,Values> state) {
        if(p.kind().equals("NOT"))return TextPredicate.negate(truth(p.children().getFirst(),state));
        if(p.kind().equals("AND")||p.kind().equals("OR")) {
            boolean and=p.kind().equals("AND");int result=and?TextPredicate.TRUE:TextPredicate.FALSE;
            for(var child:p.children())result=TextPredicate.combine(and,result,truth(child,state));return result;
        }
        var left=term(p.terms().get(0),state);var right=term(p.terms().get(1),state);
        if(Set.of("LOW_VALUES","HIGH_VALUES").contains(p.terms().get(1).kind()))return TextPredicate.sourceFigurativeEquality(left.candidates().keySet(),left.open());
        if(Set.of("LOW_VALUES","HIGH_VALUES").contains(p.terms().get(0).kind()))return TextPredicate.sourceFigurativeEquality(right.candidates().keySet(),right.open());
        return TextPredicate.sourceEquality(left.candidates().keySet(),left.open(),right.candidates().keySet(),right.open());
    }
    private void join(String node,Map<String,Values> incoming) {
        var previous=before.get(node);var joined=new TreeMap<>(incoming);
        if(previous!=null)previous.forEach((k,v)->joined.merge(k,v,Values::join));
        if(!joined.equals(previous)){before.put(node,Collections.unmodifiableMap(joined));if(queued.add(node))work.addLast(node);}
    }
    private static Set<String> reads(NominalValues.Predicate p) {
        var out=new HashSet<String>();var todo=new ArrayDeque<NominalValues.Predicate>();todo.add(p);
        while(!todo.isEmpty()){var next=todo.removeFirst();for(var t:next.terms())if(t.kind().equals("READ"))out.add(t.value());todo.addAll(next.children());}return out;
    }
    private static <T> Set<T> union(Set<T> a,Set<T> b){var out=new HashSet<>(a);out.addAll(b);return Set.copyOf(out);}
}
