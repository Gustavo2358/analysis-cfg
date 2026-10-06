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
    private record Values(Map<String,Set<String>> candidates,boolean open,boolean modelAssumed,boolean tableAssumed) {
        Values(Map<String,Set<String>> candidates,boolean open,boolean modelAssumed){this(candidates,open,modelAssumed,false);}
        Values(Map<String,Set<String>> candidates,boolean open){this(candidates,open,false);}
        static final Values MODEL_UNKNOWN=new Values(Map.of(),true,true);
        static final Values UNKNOWN=new Values(Map.of(),true);
        Values {var copy=new TreeMap<String,Set<String>>();candidates.forEach((k,v)->copy.put(k,Set.copyOf(v)));candidates=Collections.unmodifiableMap(copy);}
        Values join(Values other){var out=new TreeMap<>(candidates);other.candidates.forEach((k,v)->out.merge(k,v,SourceValuesProvider::union));return new Values(out,open||other.open,modelAssumed||other.modelAssumed,tableAssumed||other.tableAssumed);}
    }
    private static final class State extends AbstractMap<String,Values> {
        private final Map<String,Values> owned;
        State(Map<String,Values> source){owned=Collections.unmodifiableMap(new TreeMap<>(source));}
        @Override public Set<Entry<String,Values>> entrySet(){return owned.entrySet();}
        @Override public Values get(Object key){return owned.get(key);}
    }
    private static Map<String,Values> freeze(Map<String,Values> source){return source instanceof State?source:new State(source);}
    private final Map<String,List<String>> observedNodes=new HashMap<>();
    private final Set<String> controlAffected;
    private final UnitEvidence unit;
    private final NominalValueEvidence source;
    private final Map<String,Node> nodes=new HashMap<>();
    private final Map<String,Statement> statements=new HashMap<>();
    private final Map<String,List<Derivation>> waiting=new HashMap<>();
    private final Map<String,List<NominalValues.Assignment>> assignments=new HashMap<>();
    private final Map<String,NominalValues.Predicate> predicates=new HashMap<>();
    private final Map<String,Boolean> branches=new HashMap<>();
    private final Map<String,Integer> extents=new TreeMap<>();
    private final Set<String> modelSymbols=new HashSet<>();
    private final Set<String> summarySymbols=new HashSet<>();
    private final Map<String,String> queries=new HashMap<>();
    private final Map<String,Evidence> evidence=new HashMap<>();
    private final Map<String,Map<String,Values>> before=new HashMap<>();
    private final ArrayDeque<String> work=new ArrayDeque<>();
    private final Set<String> queued=new HashSet<>();
    private long workItems;
    private boolean limited;

    public SourceValuesProvider(UnitEvidence unit,Set<String> requested) {
        this.unit=unit;source=unit.nominalValues().orElseThrow();controlAffected=SourceControlEvidence.affected(unit);
        unit.statements().forEach(s->statements.put(s.id().handle(),s));
        source.facts().queries().stream().filter(q->requested.contains(q.statement())).forEach(q->queries.put(q.statement(),q.node()));
        if(queries.isEmpty())return;
        var needed=neededNodes(unit,queries.keySet());
        for(var node:unit.nodes())if(needed.contains(node.id())) {
            nodes.put(node.id(),node);
            if(queries.containsKey(node.location()))observedNodes.computeIfAbsent(node.location(),k->new ArrayList<>()).add(node.id());
        }
        var demand=new HashSet<>(queries.values());boolean changed;
        do {
            changed=false;
            for(var a:source.facts().assignments())if(demand.contains(a.target()))changed|=demand.addAll(reads(a.source()));
            for(var c:source.facts().conditions()){var reads=reads(c.predicate());if(reads.stream().anyMatch(demand::contains))changed|=demand.addAll(reads);}
        }while(changed);
        source.facts().symbols().stream().filter(s->demand.contains(s.node())).forEach(s->{
            extents.put(s.node(),s.extent());if(s.modelAssumed())modelSymbols.add(s.node());
        });
        for(var a:source.facts().assignments())if(demand.contains(a.target())) {
            assignments.computeIfAbsent(a.statement(),k->new ArrayList<>()).add(a);
            evidence.put(writeKey(a),new Evidence("ASSIGNMENT",a.statement(),statements.get(a.statement()).provenance()));
        }
        source.facts().tableFields().forEach(f->summarySymbols.add(f.node()));
        source.facts().conditions().forEach(c->predicates.put(c.statement(),c.predicate()));
        source.branches().forEach(b->branches.put(b.derivation(),b.whenTrue()));
        var initial=new TreeMap<String,Values>();extents.keySet().forEach(k->initial.put(k,modelSymbols.contains(k)?Values.MODEL_UNKNOWN:Values.UNKNOWN));
        for(var seed:source.seeds())if(demand.contains(seed.node())&&!modelSymbols.contains(seed.node())) {
            String key="seed/"+seed.node();evidence.put(key,new Evidence("DECLARATION_VALUE",seed.node(),seed.provenance()));
            initial.put(seed.node(),new Values(Map.of(TextPredicate.fit(seed.value(),extents.get(seed.node())),Set.of(key)),false));
        }
        var origins=new HashMap<String,Provenance>();source.declarations().forEach(d->origins.put(d.node(),d.provenance()));
        var assumed=new HashSet<String>();source.facts().symbols().stream().filter(NominalValues.Symbol::modelAssumed).forEach(s->assumed.add(s.node()));
        for(var field:source.facts().tableFields())if(demand.contains(field.node())&&!modelSymbols.contains(field.node()))for(var seed:field.initial())if(!assumed.contains(seed.origin())) {
            String key="table-seed/"+field.node()+"/"+seed.origin();evidence.put(key,new Evidence("DECLARATION_VALUE",seed.origin(),origins.get(seed.origin())));
            initial.merge(field.node(),new Values(Map.of(TextPredicate.fit(seed.value(),extents.get(field.node())),Set.of(key)),true,false,true),Values::join);
        }
        var seedState=freeze(initial);
        for(var d:unit.derivations())if(needed.contains(d.destination())) {
            var premises=new TreeSet<>(d.source());premises.addAll(d.callerPremise());
            if(premises.isEmpty())join(d.destination(),seedState);
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
        for(var id:observedNodes.getOrDefault(statement,List.of()))if(before.containsKey(id)) {
            var v=before.get(id).getOrDefault(query,Values.UNKNOWN);values=values==null?v:values.join(v);
        }
        if(values==null)return List.of();
        var out=new ArrayList<Candidate>();
        for(var value:values.candidates().entrySet()) {
            var supports=value.getValue().stream().sorted().map(evidence::get).filter(Objects::nonNull).distinct().toList();
            var assumptions=new ArrayList<>(List.of("NOMINAL_DECLARATIONS_PRESERVE_MEANING","NO_UNMODELED_STORAGE_INTERFERENCE"));
            if(observedNodes.getOrDefault(statement,List.of()).stream().anyMatch(controlAffected::contains))assumptions.add("UNKNOWN_CONTROL_CAN_COMPLETE");
            if(values.tableAssumed()||summarySymbols.contains(query))assumptions.add("TABLE_INDEX_NOT_REFINED");
            if(values.modelAssumed())assumptions.add("SYNTHETIC_MODEL_IS_NOT_KILL_PROOF");
            if(supports.stream().anyMatch(e->e.kind().equals("DECLARATION_VALUE")))assumptions.add("DECLARATIVE_INITIAL_VALUES_APPLY");
            out.add(new Candidate(value.getKey(),new Support(PROFILE,supports,assumptions,source.uncertainties())));
        }
        return List.copyOf(out);
    }
    private void propagate(Derivation d) {
        if(d.source().isEmpty()||!before.containsKey(d.source().getFirst())||d.callerPremise().stream().anyMatch(p->!before.containsKey(p)))return;
        String location=nodes.get(d.source().getFirst()).location();var state=before.get(d.source().getFirst());
        if(branches.containsKey(d.id())){state=filter(state,predicates.get(location),branches.get(d.id()));if(state==null)return;}
        if(d.selection().isEmpty()&&!assignments.getOrDefault(location,List.of()).isEmpty()) {
            var changed=new TreeMap<>(state);
            // All origins read the predecessor snapshot before any receiver update.
            for(var a:assignments.getOrDefault(location,List.of()))changed.put(a.target(),assigned(a,state));
            state=freeze(changed);
        }
        join(d.destination(),state);
    }
    private Values assigned(NominalValues.Assignment a,Map<String,Values> state) {
        var input=term(a.source(),state);var out=new TreeMap<String,Set<String>>();
        boolean model=modelSymbols.contains(a.target())||input.modelAssumed();
        for(var value:input.candidates().entrySet()) {
            var support=union(value.getValue(),Set.of(writeKey(a)));
            out.merge(TextPredicate.fit(value.getKey(),extents.get(a.target())),support,SourceValuesProvider::union);
            // A model's width cannot disprove a name explicitly observed in the program.
            if(model)out.merge(value.getKey(),support,SourceValuesProvider::union);
        }
        var assigned=new Values(out,input.open()||model,model,input.tableAssumed()||summarySymbols.contains(a.target()));
        // Model assumptions supply possibilities, never a strong-update/kill proof.
        return model||summarySymbols.contains(a.target())?state.getOrDefault(a.target(),Values.UNKNOWN).join(assigned):assigned;
    }
    private static String writeKey(NominalValues.Assignment a){return "write/"+a.statement()+"/"+a.target();}
    private Values term(NominalValues.Term t,Map<String,Values> state) {
        if(t.kind().equals("CHOICE")){Values result=null;for(var arg:t.arguments()){var value=term(arg,state);result=result==null?value:result.join(value);}return result;}
        if(t.extended()) {
            var input=term(t.arguments().getFirst(),state);var values=new TreeMap<String,Set<String>>();boolean open=input.open();
            for(var value:input.candidates().entrySet()) {
                String text=value.getKey(),result;
                if(t.kind().equals("UPPER_ASCII")) {
                    if(text.codePoints().anyMatch(c->c>127)){open=true;continue;}
                    var transformed=new StringBuilder(text.length());
                    for(int i=0;i<text.length();i++){char c=text.charAt(i);transformed.append(c>='a'&&c<='z'?(char)(c-'a'+'A'):c);}result=transformed.toString();
                } else {
                    int start=0,end=text.length();
                    if(!t.kind().equals("TRIM_TRAILING_SPACES"))while(start<end&&text.charAt(start)==' ')start++;
                    if(!t.kind().equals("TRIM_LEADING_SPACES"))while(end>start&&text.charAt(end-1)==' ')end--;
                    result=text.substring(start,end);
                }
                values.merge(result,value.getValue(),SourceValuesProvider::union);
            }
            return new Values(values,open,input.modelAssumed(),input.tableAssumed());
        }
        return switch(t.kind()) {
            case "READ"->state.getOrDefault(t.value(),Values.UNKNOWN);
            case "LITERAL"->new Values(Map.of(t.value(),Set.of()),false);
            case "SPACES"->new Values(Map.of(" ",Set.of()),false);
            default->Values.UNKNOWN; // LOW/HIGH retain unknown collating sequence.
        };
    }
    private Map<String,Values> filter(Map<String,Values> state,NominalValues.Predicate p,boolean whenTrue) {
        if(p==null||reads(p).stream().anyMatch(summarySymbols::contains)||reads(p).stream().anyMatch(s->state.getOrDefault(s,Values.UNKNOWN).modelAssumed()))return state;int wanted=whenTrue?TextPredicate.TRUE:TextPredicate.FALSE;
        if((truth(p,state)&wanted)==0)return null;
        var result=new TreeMap<>(state);
        for(var symbol:reads(p)) {
            var old=state.get(symbol);if(old==null)continue;var kept=new TreeMap<String,Set<String>>();
            for(var value:old.candidates().entrySet()) {
                var snapshot=new TreeMap<>(state);snapshot.put(symbol,new Values(Map.of(value.getKey(),value.getValue()),false));
                if((truth(p,snapshot)&wanted)!=0)kept.put(value.getKey(),value.getValue());
            }
            if(kept.isEmpty()&&!old.open())return null;
            result.put(symbol,new Values(kept,old.open(),old.modelAssumed(),old.tableAssumed()));
        }
        return freeze(result);
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
        var previous=before.get(node);if(incoming.equals(previous))return;
        Map<String,Values> joined=incoming;
        if(previous!=null){var merge=new TreeMap<>(incoming);previous.forEach((k,v)->merge.merge(k,v,Values::join));joined=merge;}
        if(!joined.equals(previous)){before.put(node,freeze(joined));if(queued.add(node))work.addLast(node);}
    }
    /** Incoming ordinal links include every source/caller premise of every alternative. */
    private static Set<String> neededNodes(UnitEvidence unit,Set<String> requested) {
        int n=unit.nodes().size(),d=unit.derivations().size();var ordinal=new HashMap<String,Integer>();
        int[] heads=new int[n],next=new int[d];Arrays.fill(heads,-1);
        var pending=new ArrayDeque<Integer>();var needed=new BitSet(n);
        for(int i=0;i<n;i++){var node=unit.nodes().get(i);ordinal.put(node.id(),i);if(requested.contains(node.location())){needed.set(i);pending.add(i);}}
        for(int i=0;i<d;i++){int destination=ordinal.get(unit.derivations().get(i).destination());next[i]=heads[destination];heads[destination]=i;}
        while(!pending.isEmpty())for(int i=heads[pending.removeFirst()];i>=0;i=next[i]) {
            var step=unit.derivations().get(i);
            for(var premise:step.source()){int id=ordinal.get(premise);if(!needed.get(id)){needed.set(id);pending.add(id);}}
            for(var premise:step.callerPremise()){int id=ordinal.get(premise);if(!needed.get(id)){needed.set(id);pending.add(id);}}
        }
        var result=new HashSet<String>();for(int i=needed.nextSetBit(0);i>=0;i=needed.nextSetBit(i+1))result.add(unit.nodes().get(i).id());return result;
    }
    private static Set<String> reads(NominalValues.Predicate p) {
        var out=new HashSet<String>();var todo=new ArrayDeque<NominalValues.Predicate>();todo.add(p);
        while(!todo.isEmpty()){var next=todo.removeFirst();for(var t:next.terms())out.addAll(reads(t));todo.addAll(next.children());}return out;
    }
    private static Set<String> reads(NominalValues.Term root) {
        var out=new HashSet<String>();var todo=new ArrayDeque<NominalValues.Term>();todo.add(root);
        while(!todo.isEmpty()){var t=todo.removeFirst();if(t.kind().equals("READ"))out.add(t.value());todo.addAll(t.arguments());}return out;
    }
    private static <T> Set<T> union(Set<T> a,Set<T> b){var out=new HashSet<>(a);out.addAll(b);return Set.copyOf(out);}
}
