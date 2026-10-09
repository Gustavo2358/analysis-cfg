package io.github.gustavo2358.analysis.dependencies;

import java.util.*;
import io.github.gustavo2358.analysis.solver.*;
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
    private record ProofKey(int kind,String owner,String detail) implements Comparable<ProofKey> {
        @Override public int compareTo(ProofKey other) {
            int c=Integer.compare(kind,other.kind);if(c==0)c=owner.compareTo(other.owner);return c==0?detail.compareTo(other.detail):c;
        }
    }
    private final Map<ProofKey,Long> proofIds=new HashMap<>();
    private final List<ProofKey> proofKeys=new ArrayList<>();
    private final List<Evidence> evidence=new ArrayList<>();
    private final IdentityHashMap<NominalValues.Assignment,Long> writeProofs=new IdentityHashMap<>();
    // Resident source input handle index; variable-sized state payloads reside in page columns.
    private final Map<String,Integer> before=new HashMap<>();
    private final Map<String,Long> symbols=new HashMap<>();
    private final Map<String,List<Candidate>> projected=new HashMap<>();
    private final IdentityHashMap<NominalValues.Predicate,Set<String>> predicateReads=new IdentityHashMap<>();
    private SourceValueStore values;
    private SourceExpressions expressions;
    private PagedLongArray stateRoots,stateTokens;
    private PagedWorklist work;
    private String[] nodeIds;
    private AnalysisResources resources;
    private long[] transferRoots,transferResults;
    private String[] transferLocations;
    private byte[] transferModes;
    private long transferEvaluations,transformedCandidates;
    public record StateStatistics(long records,long mapNodeVisits,long pathCopies,long managedHeapPeak,long transferEvaluations,long predicateClassVisits,long transformedCandidates) { }
    private StateStatistics stateStatistics=new StateStatistics(0,0,0,0,0,0,0);
    public StateStatistics stateStatistics(){return stateStatistics;}
    public record DemandStatistics(long nodeVisits,long edgeVisits,long indexedEdges) { }
    private DemandStatistics demandStatistics=new DemandStatistics(0,0,0);
    public DemandStatistics demandStatistics(){return demandStatistics;}
    private long workItems;


    public SourceValuesProvider(UnitEvidence unit,Set<String> requested) {
        this(unit,requested,null,null);
    }
    /** Borrowed paged storage; all temporary analysis owners close before resident candidates return. */
    public SourceValuesProvider(UnitEvidence unit,Set<String> requested,PageStore pages,AnalysisResources limits) {
        if((pages==null)!=(limits==null))throw new IllegalArgumentException("page store and resources must be supplied together");
        this.unit=unit;source=unit.nominalValues().orElseThrow();
        resources=limits==null?new AnalysisResources(new AnalysisResources.Limits(Long.MAX_VALUE,Long.MAX_VALUE,0,0,0,Long.MAX_VALUE,Long.MAX_VALUE)):limits;
        resources.work(0,AnalysisResources.Phase.INDEX);
        if(requested.isEmpty()){controlAffected=Set.of();resources=null;return;}
        for(var query:source.facts().queries()){preparation();if(requested.contains(query.statement()))queries.put(query.statement(),query.node());}
        if(queries.isEmpty()){controlAffected=Set.of();resources=null;return;}
        controlAffected=SourceControlEvidence.affected(unit,this::preparation);
        for(var statement:unit.statements()){preparation();statements.put(statement.id().handle(),statement);}
        var needed=neededNodes(unit,queries.keySet());
        for(var node:unit.nodes())if(needed.contains(node.id())) {
            preparation();
            before.put(node.id(),before.size());nodes.put(node.id(),node);
            if(queries.containsKey(node.location()))observedNodes.computeIfAbsent(node.location(),k->new ArrayList<>()).add(node.id());
        }
        var demandIndex=new NominalDemandIndex(source.facts(),this::preparation);
        var closure=demandIndex.closure(new HashSet<>(queries.values()));
        var demand=closure.symbols();
        demandStatistics=new DemandStatistics(closure.nodeVisits(),closure.edgeVisits(),demandIndex.edgeCount());
        for(var symbol:source.facts().symbols())if(demand.contains(symbol.node())) {
            preparation();
            extents.put(symbol.node(),symbol.extent());if(symbol.modelAssumed())modelSymbols.add(symbol.node());
        }
        for(var a:source.facts().assignments())if(demand.contains(a.target())) {
            preparation();
            assignments.computeIfAbsent(a.statement(),k->new ArrayList<>()).add(a);
            writeProofs.put(a,proof(new ProofKey(2,a.statement(),a.target()),new Evidence("ASSIGNMENT",a.statement(),statements.get(a.statement()).provenance())));
        }
        for(var field:source.facts().tableFields()){preparation();summarySymbols.add(field.node());}
        for(var condition:source.facts().conditions()){preparation();predicates.put(condition.statement(),condition.predicate());}
        for(var branch:source.branches()){preparation();branches.put(branch.derivation(),branch.whenTrue());}
        for(var symbol:extents.keySet()){preparation();symbols.put(symbol,symbols.size()+1L);}
        nodeIds=new String[before.size()];for(var entry:before.entrySet()){preparation();nodeIds[entry.getValue()]=entry.getKey();}
        try(var cache=resources.reserve(AnalysisResources.Pool.RESIDENT,4096,AnalysisResources.Phase.DOMAIN);
            var owned=pages==null?new ResidentPageStore(4096,resources):null;
            var store=new SourceValueStore(pages==null?owned:pages,resources);
            var program=new SourceExpressions(resources);
            var roots=new PagedLongArray(pages==null?owned:pages,before.size(),resources,AnalysisResources.Phase.DOMAIN);
            var tokens=new PagedLongArray(pages==null?owned:pages,before.size(),resources,AnalysisResources.Phase.DOMAIN);
            var pending=new PagedWorklist(pages==null?owned:pages,resources,AnalysisResources.Phase.DOMAIN)) {
            if(cache.amount()!=4096)throw new IllegalStateException("transfer cache reservation mismatch");
            transferRoots=new long[64];transferResults=new long[64];transferLocations=new String[64];transferModes=new byte[64];
            values=store;expressions=program;stateRoots=roots;stateTokens=tokens;work=pending;
            long initial=values.emptyState();
            for(var seed:source.seeds())if(demand.contains(seed.node())&&!modelSymbols.contains(seed.node())) {
                long atom=proof(new ProofKey(0,seed.node(),""),new Evidence("DECLARATION_VALUE",seed.node(),seed.provenance()));
                initial=put(initial,seed.node(),values.literal(TextPredicate.fit(seed.value(),extents.get(seed.node())),0,atom));
            }
            var origins=new HashMap<String,Provenance>();for(var d:source.declarations())origins.put(d.node(),d.provenance());
            var assumed=new HashSet<String>();for(var symbol:source.facts().symbols())if(symbol.modelAssumed())assumed.add(symbol.node());
            for(var field:source.facts().tableFields())if(demand.contains(field.node())&&!modelSymbols.contains(field.node()))for(var seed:field.initial())if(!assumed.contains(seed.origin())) {
                long atom=proof(new ProofKey(1,field.node(),seed.origin()),new Evidence("DECLARATION_VALUE",seed.origin(),origins.get(seed.origin())));
                initial=put(initial,field.node(),values.join(get(initial,field.node()),values.literal(TextPredicate.fit(seed.value(),extents.get(field.node())),SourceValueStore.OPEN|SourceValueStore.TABLE,atom)));
            }
            for(var d:unit.derivations())if(needed.contains(d.destination())) {
                var premises=new HashSet<>(d.source());premises.addAll(d.callerPremise());
                if(premises.isEmpty())join(d.destination(),initial);
                else for(var premise:premises)waiting.computeIfAbsent(premise,k->new ArrayList<>()).add(d);
            }
            long collectAt=4096;
            while(work.size()!=0) {
                resources.work(1,AnalysisResources.Phase.DOMAIN);workItems++;
                String id=nodeIds[(int)work.remove()];
                for(var d:waiting.getOrDefault(id,List.of()))propagate(d);
                // Amortized safepoint: no transient unrooted transfer/evaluator is active here.
                if(values.records()>collectAt) {
                    Arrays.fill(transferRoots,0);Arrays.fill(transferResults,0);Arrays.fill(transferLocations,null);
                    values.collect();collectAt=Math.max(4096,Math.multiplyExact(values.records(),2));
                }
            }
            for(var statement:queries.keySet())projected.put(statement,project(statement));
            stateStatistics=new StateStatistics(values.records(),values.nodeVisits(),values.pathCopies(),resources.heapPeak(),transferEvaluations,values.predicateClassVisits(),transformedCandidates);
        } finally {values=null;expressions=null;stateRoots=null;stateTokens=null;work=null;resources=null;transferRoots=null;transferResults=null;transferLocations=null;transferModes=null;}
    }
    public long workItems(){return workItems;}
    /** Successful construction always completes; exhausted resources throw and publish no partial result. */
    public boolean limited(){return false;}
    public List<Candidate> candidates(String statement) {return projected.getOrDefault(statement,List.of());}
    private List<Candidate> project(String statement) {
        String query=queries.get(statement);long combined=0;
        var observations=observedNodes.getOrDefault(statement,List.of());boolean affected=false;
        for(var id:observations) {
            affected|=controlAffected.contains(id);long state=stateRoots.get(before.get(id));
            if(state!=0){long value=get(state,query);combined=combined==0?value:values.join(combined,value);}
        }
        if(combined==0)return List.of();
        var out=new ArrayList<Candidate>();int flags=values.flags(combined);
        try(var candidates=values.candidates(combined)) {
            while(candidates.advance()) {
                var atoms=new ArrayList<Long>();
                try(var proofs=values.supports(candidates.value())){while(proofs.advance())atoms.add(proofs.key());}
                atoms.sort((a,b)->proofKeys.get((int)(a-1)).compareTo(proofKeys.get((int)(b-1))));
                var distinct=new LinkedHashSet<Evidence>();for(long atom:atoms)distinct.add(evidence.get((int)(atom-1)));
                var supports=List.copyOf(distinct);
                var assumptions=new ArrayList<>(List.of("NOMINAL_DECLARATIONS_PRESERVE_MEANING","NO_UNMODELED_STORAGE_INTERFERENCE"));
                if(affected)assumptions.add("UNKNOWN_CONTROL_CAN_COMPLETE");
                if((flags&SourceValueStore.TABLE)!=0||summarySymbols.contains(query))assumptions.add("TABLE_INDEX_NOT_REFINED");
                if((flags&SourceValueStore.MODEL)!=0)assumptions.add("SYNTHETIC_MODEL_IS_NOT_KILL_PROOF");
                for(var support:supports)if(support.kind().equals("DECLARATION_VALUE")){assumptions.add("DECLARATIVE_INITIAL_VALUES_APPLY");break;}
                out.add(new Candidate(values.text(candidates.key()),new Support(PROFILE,supports,assumptions,source.uncertainties())));
            }
        }
        out.sort(Comparator.comparing(Candidate::rawValue));return List.copyOf(out);
    }
    private long proof(ProofKey key,Evidence fact) {
        Long old=proofIds.get(key);if(old!=null)return old;
        long id=proofKeys.size()+1L;proofKeys.add(key);evidence.add(fact);proofIds.put(key,id);return id;
    }
    private long get(long state,String symbol) {
        Long id=symbols.get(symbol);return id==null?values.unknown(false):values.get(state,id,modelSymbols.contains(symbol));
    }
    private long put(long state,String symbol,long value) {
        long id=symbols.get(symbol);
        return value==values.unknown(modelSymbols.contains(symbol))?values.remove(state,id):values.put(state,id,value);
    }
    private void propagate(Derivation d) {
        if(d.source().isEmpty())return;
        long state=stateRoots.get(before.get(d.source().getFirst()));if(state==0)return;
        for(var premise:d.callerPremise())if(stateRoots.get(before.get(premise))==0)return;
        String location=nodes.get(d.source().getFirst()).location();
        byte mode=(byte)((branches.containsKey(d.id())?(branches.get(d.id())?2:1):0)+(d.selection().isEmpty()?4:0));
        long hash=state*0x9e3779b97f4a7c15L+location.hashCode()*31L+mode;hash^=hash>>>33;
        int slot=(int)hash&63;
        long result;
        if(transferRoots[slot]==state&&location.equals(transferLocations[slot])&&transferModes[slot]==mode)result=transferResults[slot];
        else {
            result=transfer(state,location,mode);
            transferRoots[slot]=state;transferLocations[slot]=location;transferModes[slot]=mode;transferResults[slot]=result;
        }
        if(result!=0)join(d.destination(),result);
    }
    private long transfer(long state,String location,byte mode) {
        int branch=mode&3;
        if(branch!=0){transferEvaluations++;state=filter(state,predicates.get(location),branch==2);if(state==0)return 0;}
        var writes=assignments.getOrDefault(location,List.of());
        if((mode&4)!=0&&!writes.isEmpty()) {
            transferEvaluations++;long changed=state;
            // One immutable predecessor/evaluator for all simultaneous receiver updates.
            try(var evaluator=new Evaluator(state)) {
                for(var a:writes)changed=put(changed,a.target(),assigned(a,state,evaluator));
            }
            state=changed;
        }
        return state;
    }
    private long assigned(NominalValues.Assignment a,long state,Evaluator evaluator) {
        long input=values.addSupport(evaluator.term(a.source()),writeProofs.get(a));int flags=values.flags(input);
        boolean model=modelSymbols.contains(a.target())||(flags&SourceValueStore.MODEL)!=0;
        if(model)flags|=SourceValueStore.OPEN|SourceValueStore.MODEL;
        if(summarySymbols.contains(a.target()))flags|=SourceValueStore.TABLE;
        long assigned=values.emptyValue(flags);
        try(var candidates=values.candidates(input)) {
            while(candidates.advance()) {
                String text=values.text(candidates.key());long support=candidates.value();
                assigned=values.addCandidate(assigned,TextPredicate.fit(text,extents.get(a.target())),support);
                if(model)assigned=values.addCandidate(assigned,text,support);
            }
        }
        return model||summarySymbols.contains(a.target())?values.join(get(state,a.target()),assigned):assigned;
    }
    private final class Evaluator implements AutoCloseable {
        private final long state;
        private final Evaluator parent;
        private final IdentityHashMap<NominalValues.Predicate,Integer> predicates;
        private final AnalysisResources.Reservation scratch;
        private int[] keys,nodes,next;
        private long[] outputs,inputs,accumulated;
        private int used;
        Evaluator(long state){this(state,null);}
        Evaluator(long state,Evaluator parent) {
            this.state=state;this.parent=parent;
            scratch=resources.reserve(AnalysisResources.Pool.SCRATCH,1024,AnalysisResources.Phase.DOMAIN);
            try {
                predicates=new IdentityHashMap<>();keys=new int[8];outputs=new long[8];inputs=new long[8];
                nodes=new int[8];next=new int[8];accumulated=new long[8];
            } catch(RuntimeException|Error failure){scratch.close();throw failure;}
        }
        private int slot(int node) {
            int slot=(node*0x9e3779b9)&(keys.length-1);
            while(keys[slot]!=0&&keys[slot]!=node)slot=(slot+1)&(keys.length-1);return slot;
        }
        private void put(int node,long output,long input) {
            if(used+1>keys.length/2) {
                int capacity=Math.multiplyExact(keys.length,2);scratch.grow(32L*capacity,AnalysisResources.Phase.DOMAIN);
                int[] previousKeys=keys;long[] previousOutputs=outputs,previousInputs=inputs;
                keys=new int[capacity];outputs=new long[capacity];inputs=new long[capacity];
                for(int i=0;i<previousKeys.length;i++)if(previousKeys[i]!=0) {
                    int s=slot(previousKeys[i]);keys[s]=previousKeys[i];outputs[s]=previousOutputs[i];inputs[s]=previousInputs[i];
                }
            }
            int s=slot(node);if(keys[s]==0){keys[s]=node;used++;}outputs[s]=output;inputs[s]=input;
        }
        private long leaf(int node) {
            long input=switch(expressions.kind(node)) {
                case SourceExpressions.READ->get(state,expressions.payload(node));
                case SourceExpressions.LITERAL->values.literal(expressions.payload(node),0,0);
                default->values.unknown(false);
            };
            long output;
            if(parent!=null) {
                int s=parent.slot(node);
                if(parent.keys[s]==node&&parent.inputs[s]==input){output=parent.outputs[s];put(node,output,input);return output;}
            }
            output=expressions.transforms(node)==0?input:transform(expressions.transforms(node),input);
            put(node,output,input);return output;
        }
        long term(NominalValues.Term term) {
            int root=expressions.compile(term),s=slot(root);if(keys[s]!=0)return outputs[s];
            if(expressions.kind(root)!=SourceExpressions.CHOICE)return leaf(root);
            int top=0;nodes[0]=root;next[0]=0;accumulated[0]=0;long result=0;
            while(top>=0) {
                int node=nodes[top];
                if(next[top]<expressions.count(node)) {
                    int child=expressions.argument(node,next[top]);s=slot(child);
                    long value=keys[s]==child?outputs[s]:expressions.kind(child)!=SourceExpressions.CHOICE?leaf(child):0;
                    if(value!=0) {
                        accumulated[top]=accumulated[top]==0?value:values.join(accumulated[top],value);next[top]++;continue;
                    }
                    if(top+1==nodes.length) {
                        int capacity=Math.multiplyExact(nodes.length,2);scratch.grow(24L*capacity,AnalysisResources.Phase.DOMAIN);
                        nodes=Arrays.copyOf(nodes,capacity);next=Arrays.copyOf(next,capacity);accumulated=Arrays.copyOf(accumulated,capacity);
                    }
                    nodes[++top]=child;next[top]=0;accumulated[top]=0;
                } else {
                    result=accumulated[top];put(node,result,0);top--;
                    if(top>=0){accumulated[top]=accumulated[top]==0?result:values.join(accumulated[top],result);next[top]++;}
                }
            }
            return result;
        }
        private long transform(int mask,long input) {
            int flags=values.flags(input);long result=values.emptyValue(flags);
            try(var candidates=values.candidates(input)) {
                while(candidates.advance()) {
                    transformedCandidates++;String text=values.text(candidates.key());
                    int start=0,end=text.length();
                    if((mask&SourceExpressions.LEADING)!=0)while(start<end&&text.charAt(start)==' ')start++;
                    if((mask&SourceExpressions.TRAILING)!=0)while(end>start&&text.charAt(end-1)==' ')end--;
                    String transformed=text.substring(start,end);
                    if((mask&SourceExpressions.UPPER)!=0) {
                        boolean ascii=true;for(int i=0;i<transformed.length();i++)if(transformed.charAt(i)>127){ascii=false;break;}
                        if(!ascii){flags|=SourceValueStore.OPEN;continue;}
                        var builder=new StringBuilder(transformed.length());
                        for(int i=0;i<transformed.length();i++){char c=transformed.charAt(i);builder.append(c>='a'&&c<='z'?(char)(c-'a'+'A'):c);}transformed=builder.toString();
                    }
                    result=values.addCandidate(result,transformed,candidates.value());
                }
            }
            return values.withFlags(result,flags);
        }
        private final class PredicateFrame {final NominalValues.Predicate predicate;int next;PredicateFrame(NominalValues.Predicate p){predicate=p;}}
        int truth(NominalValues.Predicate root) {
            Integer cached=predicates.get(root);if(cached!=null)return cached;
            var todo=new ArrayDeque<PredicateFrame>();scratch.grow(192,AnalysisResources.Phase.DOMAIN);todo.push(new PredicateFrame(root));
            while(!todo.isEmpty()) {
                var frame=todo.peek();var p=frame.predicate;
                if(frame.next<p.children().size()) {
                    var child=p.children().get(frame.next++);if(!predicates.containsKey(child)){scratch.grow(192,AnalysisResources.Phase.DOMAIN);todo.push(new PredicateFrame(child));}continue;
                }
                int result;
                if(p.kind().equals("NOT"))result=TextPredicate.negate(predicates.get(p.children().getFirst()));
                else if(p.kind().equals("AND")||p.kind().equals("OR")) {
                    boolean and=p.kind().equals("AND");result=and?TextPredicate.TRUE:TextPredicate.FALSE;
                    for(var child:p.children())result=TextPredicate.combine(and,result,predicates.get(child));
                } else {
                    long left=term(p.terms().get(0)),right=term(p.terms().get(1));
                    boolean leftFigure=figurative(p.terms().get(0)),rightFigure=figurative(p.terms().get(1));
                    if(rightFigure)result=values.figurativeEquality(left);
                    else if(leftFigure)result=values.figurativeEquality(right);
                    else result=values.equality(left,right);
                }
                predicates.put(p,result);todo.pop();
            }
            return predicates.get(root);
        }
        @Override public void close(){predicates.clear();keys=null;outputs=null;inputs=null;nodes=null;next=null;accumulated=null;scratch.close();}
    }
    private static boolean figurative(NominalValues.Term term){return term.kind().equals("LOW_VALUES")||term.kind().equals("HIGH_VALUES");}
    private long filter(long state,NominalValues.Predicate predicate,boolean whenTrue) {
        if(predicate==null)return state;
        var reads=predicateReads.computeIfAbsent(predicate,SourceValuesProvider::reads);
        for(var symbol:reads)if(summarySymbols.contains(symbol)||(values.flags(get(state,symbol))&SourceValueStore.MODEL)!=0)return state;
        int wanted=whenTrue?TextPredicate.TRUE:TextPredicate.FALSE;
        try(var baseline=new Evaluator(state)) {
            if((baseline.truth(predicate)&wanted)==0)return 0;
            long result=state;
            for(var symbol:reads) {
                if(!symbols.containsKey(symbol))continue;
                long old=get(state,symbol),kept=values.emptyValue(values.flags(old));boolean any=false;
                try(var candidates=values.candidates(old)) {
                    while(candidates.advance()) {
                        long snapshot=put(state,symbol,values.oneCandidate(old,candidates.key(),candidates.value()));
                        boolean allowed=snapshot==state;
                        if(!allowed)try(var evaluator=new Evaluator(snapshot,baseline)){allowed=(evaluator.truth(predicate)&wanted)!=0;}
                        if(allowed){kept=values.addCandidate(kept,values.text(candidates.key()),candidates.value());any=true;}
                    }
                }
                if(!any&&(values.flags(old)&SourceValueStore.OPEN)==0)return 0;
                result=put(result,symbol,kept);
            }
            return result;
        }
    }
    private void join(String node,long incoming) {
        int ordinal=before.get(node);long previous=stateRoots.get(ordinal);
        long joined=previous==0?incoming:values.joinStates(previous,incoming);if(joined==previous)return;
        long token=values.retain(joined),old=stateTokens.get(ordinal);
        stateRoots.set(ordinal,joined);stateTokens.set(ordinal,token);if(old!=0)values.release(old);
        work.add(ordinal);
    }
    /** Incoming ordinal links include every source/caller premise of every alternative. */
    private void preparation(){resources.work(1,AnalysisResources.Phase.INDEX);}
    private Set<String> neededNodes(UnitEvidence unit,Set<String> requested) {
        int n=unit.nodes().size(),d=unit.derivations().size();var ordinal=new HashMap<String,Integer>();
        int[] heads=new int[n],next=new int[d];Arrays.fill(heads,-1);
        var pending=new ArrayDeque<Integer>();var needed=new BitSet(n);
        for(int i=0;i<n;i++){preparation();var node=unit.nodes().get(i);ordinal.put(node.id(),i);if(requested.contains(node.location())){needed.set(i);pending.add(i);}}
        for(int i=0;i<d;i++){preparation();int destination=ordinal.get(unit.derivations().get(i).destination());next[i]=heads[destination];heads[destination]=i;}
        while(!pending.isEmpty())for(int i=heads[pending.removeFirst()];i>=0;i=next[i]) {
            preparation();
            var step=unit.derivations().get(i);
            for(var premise:step.source()){preparation();int id=ordinal.get(premise);if(!needed.get(id)){needed.set(id);pending.add(id);}}
            for(var premise:step.callerPremise()){preparation();int id=ordinal.get(premise);if(!needed.get(id)){needed.set(id);pending.add(id);}}
        }
        var result=new HashSet<String>();for(int i=needed.nextSetBit(0);i>=0;i=needed.nextSetBit(i+1)){preparation();result.add(unit.nodes().get(i).id());}return result;
    }
    private static Set<String> reads(NominalValues.Predicate root) {
        var out=new HashSet<String>();var predicates=new ArrayDeque<NominalValues.Predicate>();predicates.add(root);
        var seenPredicates=new IdentityHashMap<NominalValues.Predicate,Boolean>();
        var seenTerms=new IdentityHashMap<NominalValues.Term,Boolean>();var terms=new ArrayDeque<NominalValues.Term>();
        while(!predicates.isEmpty()) {
            var p=predicates.removeFirst();if(seenPredicates.put(p,Boolean.TRUE)!=null)continue;
            terms.addAll(p.terms());predicates.addAll(p.children());
        }
        while(!terms.isEmpty()) {
            var term=terms.removeFirst();if(seenTerms.put(term,Boolean.TRUE)!=null)continue;
            if(term.kind().equals("READ"))out.add(term.value());terms.addAll(term.arguments());
        }
        return Set.copyOf(out);
    }
}
