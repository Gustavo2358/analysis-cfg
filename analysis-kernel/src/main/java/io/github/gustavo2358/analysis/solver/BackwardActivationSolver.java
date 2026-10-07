package io.github.gustavo2358.analysis.solver;

import io.github.gustavo2358.analysis.cfg.domain.CfgTransition;
import io.github.gustavo2358.analysis.structure.*;
import java.util.*;

/** Dual tabulation: an activation is keyed by values at its possible continuations. */
final class BackwardActivationSolver<S> {
    private final AnalysisSession session;
    private final AnalysisDefinition<S> definition;
    private final DomainWork work=new DomainWork();
    private final AnalysisResources indexResources=new AnalysisResources(new AnalysisResources.Limits(Long.MAX_VALUE,Long.MAX_VALUE,Long.MAX_VALUE,Long.MAX_VALUE,Long.MAX_VALUE,Long.MAX_VALUE,Long.MAX_VALUE));
    private final S bottom;
    private final List<EntryRun> entries=new ArrayList<>();
    private final ArrayDeque<Slot> pending=new ArrayDeque<>();
    private int nextPoint;
    private long transfers,deliveries,joins,pushes,pops,attempts,duplicates,maxSize,changes,unchanged;
    private record Need(Object region,int condition) { }
    private final Map<Need,Boolean> feasibleCache=new HashMap<>();
    // Bounded memoization only: eviction recomputes a query, never drops work.
    private final Map<Need,Deferred> deferredCache=new LinkedHashMap<>();
    BackwardActivationSolver(AnalysisSession session,AnalysisDefinition<S> definition){this.session=session;this.definition=definition;bottom=Objects.requireNonNull(definition.bottom());}
    DataflowResult<S> solve() {
        try {return execute();}finally{for(var entry:entries){for(var index:entry.byFrame.values())index.close();entry.byFrame.clear();}}
    }
    private DataflowResult<S> execute() {
        for(var model:ActivationSolver.structure(session))entries.add(new EntryRun(model));
        for(var boundary:definition.boundaries(session)) {
            var entry=entries.stream().filter(e->e.model.context()==boundary.context()).findFirst().orElseThrow(()->new IllegalArgumentException("foreign boundary"));
            var location=ActivationBoundaries.require(entry.model,boundary.node());
            entry.boundaries.computeIfAbsent(location.frame(),f->new IdentityHashMap<>()).merge(boundary.node(),boundary.state(),(a,b)->definition.joinInto(a,b,work).state());joins++;
        }
        for(var entry:entries)entry.start();
        while(!pending.isEmpty()){var slot=pending.removeFirst();slot.queued=false;pops++;process(slot);collectConditions(slot.region.entry);}
        var lookup=new IdentityHashMap<ContextView,IdentityHashMap<ProgramIndex.Node,List<AnalysisPoint>>>();
        var ins=new ArrayList<S>();var outs=new ArrayList<S>();long edgeCount=0;
        for(var entry:entries) {
            var nodes=new IdentityHashMap<ProgramIndex.Node,List<AnalysisPoint>>();lookup.put(entry.model.context(),nodes);
            for(var region:entry.regions)for(var slot:region.slots.values()) {
                edgeCount+=slot.shape.moves().size();
                for(var in:slot.in.pieces)for(var out:slot.out.pieces)if(feasible(region,entry.bdd.and(in.condition,out.condition))) {
                    int id=ins.size();nodes.computeIfAbsent(slot.node,n->new ArrayList<>()).add(new AnalysisPoint(id,entry.model.context(),slot.node));ins.add(in.state);outs.add(out.state);
                }
            }
        }
        var metrics=new SolverMetrics(ins.size(),edgeCount,joins,nextPoint,attempts,pushes,pops,duplicates,maxSize,transfers,work.operations(),ins.size(),changes,unchanged,deliveries,deliveries,changes,unchanged,0,0,work.joinEntries(),work.compareEntries());
        return new DataflowResult<>(lookup,ins.toArray(),outs.toArray(),metrics);
    }
    private void collectConditions(EntryRun entry) {
        if(!entry.bdd.collectionDue())return;
        entry.bdd.collect(root->{
            entry.model.visitConditions(root);
            var owners=Collections.newSetFromMap(new IdentityHashMap<Object,Boolean>());
            for(var region:entry.regions) {
                owners.add(region);region.incoming.values().forEach(root::accept);
                for(var slot:region.slots.values()) {
                    slot.children.values().forEach(root::accept);
                    for(var piece:slot.in.pieces)root.accept(piece.condition);
                    for(var piece:slot.out.pieces)root.accept(piece.condition);
                }
            }
            for(var key:feasibleCache.keySet())if(owners.contains(key.region))root.accept(key.condition);
            for(var key:deferredCache.keySet())if(owners.contains(key.region))root.accept(key.condition);
        });
    }
    private final class Signature {
        final int depth;
        final long fingerprint;
        final Map<ProgramIndex.Node,S> returns,roots;
        final List<Map<ProgramIndex.Node,S>> ancestors;
        Signature(int depth,Map<ProgramIndex.Node,S> returns,Map<ProgramIndex.Node,S> roots,List<Map<ProgramIndex.Node,S>> ancestors) {
            this.depth=depth;this.returns=Map.copyOf(returns);this.roots=Map.copyOf(roots);this.ancestors=List.copyOf(ancestors);
            long hash=31L*(31L*depth+mapFingerprint(this.returns))+mapFingerprint(this.roots);
            for(var ancestor:this.ancestors)hash=31*hash+mapFingerprint(ancestor);
            fingerprint=31*hash+this.ancestors.size();
        }
        boolean same(Signature other) {
            if(depth!=other.depth||!sameMap(returns,other.returns)||!sameMap(roots,other.roots)||ancestors.size()!=other.ancestors.size())return false;
            for(int i=0;i<ancestors.size();i++)if(!sameMap(ancestors.get(i),other.ancestors.get(i)))return false;return true;
        }
    }
    private long mapFingerprint(Map<ProgramIndex.Node,S> states) {
        long result=0,empty=definition.stateFingerprint(bottom);
        for(var entry:states.entrySet()) {
            long node=System.identityHashCode(entry.getKey());
            result+=StateIndex.mix(node^definition.stateFingerprint(entry.getValue()))-StateIndex.mix(node^empty);
        }
        return result;
    }
    private boolean sameMap(Map<ProgramIndex.Node,S> a,Map<ProgramIndex.Node,S> b) {
        var keys=new HashSet<>(a.keySet());keys.addAll(b.keySet());
        for(var key:keys)if(!definition.equivalent(a.getOrDefault(key,bottom),b.getOrDefault(key,bottom),work))return false;return true;
    }
    private final class EntryRun {
        final ActivationModel model;final BooleanConditions bdd;
        final List<Region> regions=new ArrayList<>();
        final IdentityHashMap<ActivationControl.Frame,StateIndex<Signature,Region>> byFrame=new IdentityHashMap<>();
        final Map<ActivationControl.Frame,Map<ProgramIndex.Node,S>> boundaries=new IdentityHashMap<>();
        final Set<Slot> calls=Collections.newSetFromMap(new IdentityHashMap<>());
        Region root;
        EntryRun(ActivationModel model){this.model=model;bdd=model.conditions();}
        void start() {
            root=new Region(this,null,new Signature(0,Map.of(),Map.of(),Collections.nCopies(model.maxUnwind(),Map.of())));regions.add(root);root.initialize();
        }
        Region region(ActivationControl.Frame frame,Signature input,Slot caller,int condition) {
            var candidates=byFrame.get(frame);
            if(candidates!=null){var known=candidates.get(input);if(known!=null)return known;}
            if(!feasible(caller.region,condition,caller))return null;
            if(candidates==null){candidates=new StateIndex<>(indexResources,AnalysisResources.Phase.DOMAIN,a->a.fingerprint,Signature::same);byFrame.put(frame,candidates);}
            var r=new Region(this,frame,input);candidates.putIfAbsent(input,r);regions.add(r);r.initialize();return r;
        }
        Map<ProgramIndex.Node,S> roots() {
            var values=new IdentityHashMap<ProgramIndex.Node,S>();
            for(var node:model.rootTargets()) {
                var slot=root.slots.get(node);S state=bottom;
                if(slot!=null)for(var piece:slot.in.pieces)state=definition.joinInto(state,piece.state,work).state();
                values.put(node,state);
            }
            return values;
        }
    }
    private final class Region {
        final EntryRun entry;final ActivationControl.Frame frame;final Signature input;
        final Map<ProgramIndex.Node,Slot> slots=new LinkedHashMap<>();
        final Map<ProgramIndex.Node,List<Slot>> predecessors=new IdentityHashMap<>();
        final Set<Slot> callers=Collections.newSetFromMap(new IdentityHashMap<>());
        final Set<Slot> waiters=Collections.newSetFromMap(new IdentityHashMap<>());
        final Map<Slot,Integer> incoming=new IdentityHashMap<>();
        long incomingVersion;
        final List<Slot> calls=new ArrayList<>();
        Region(EntryRun entry,ActivationControl.Frame frame,Signature input){this.entry=entry;this.frame=frame;this.input=input;}
        void initialize() {
            var shape=entry.model.shapes().get(frame);
            for(var point:shape.points().entrySet())slots.put(point.getKey(),new Slot(this,point.getKey(),point.getValue()));
            for(var slot:slots.values()) {
                for(var move:slot.shape.moves())if(move.action()==ActivationControl.Action.NEXT||frame==null&&move.action()==ActivationControl.Action.ROOT)
                    predecessors.computeIfAbsent(move.destination(),n->new ArrayList<>()).add(slot);
                if(slot.shape.moves().stream().anyMatch(m->m.action()==ActivationControl.Action.CALL)){calls.add(slot);entry.calls.add(slot);}
                slot.out.add(slot.shape.condition(),bottom);enqueue(slot);
            }
        }
    }
    private final class Slot {
        final Region region;final ProgramIndex.Node node;final ActivationModel.Point shape;
        final AnalysisPoint point;final Partition in,out;boolean queued;
        Map<Region,Integer> children=new IdentityHashMap<>();
        final Set<Region> watches=Collections.newSetFromMap(new IdentityHashMap<>());
        Slot(Region region,ProgramIndex.Node node,ActivationModel.Point shape) {
            this.region=region;this.node=node;this.shape=shape;point=new AnalysisPoint(nextPoint++,region.entry.model.context(),node);
            in=new Partition(region.entry);out=new Partition(region.entry);
        }
    }
    private final class Piece {
        final int condition;final S state;Piece(int condition,S state){this.condition=condition;this.state=state;}
    }
    private final class Partition {
        final EntryRun entry;List<Piece> pieces=List.of();Partition(EntryRun entry){this.entry=entry;}
        boolean add(int condition,S state) {
            if(condition==0)return false;var next=new ArrayList<Piece>();int remaining=condition;boolean modified=false;var b=entry.bdd;
            for(var piece:pieces) {
                int overlap=b.and(piece.condition,condition);
                if(overlap==0){put(next,piece.condition,piece.state);continue;}
                remaining=b.difference(remaining,piece.condition);var joined=definition.joinInto(piece.state,state,work);
                if(!joined.changed()){put(next,piece.condition,piece.state);continue;}
                modified=true;put(next,b.difference(piece.condition,condition),piece.state);put(next,overlap,joined.state());
            }
            if(remaining!=0){modified=true;put(next,remaining,state);}
            if(modified)pieces=List.copyOf(next);return modified;
        }
        void put(List<Piece> result,int condition,S value) {
            if(condition==0)return;
            for(int i=0;i<result.size();i++)if(definition.equivalent(result.get(i).state,value,work)) {result.set(i,new Piece(entry.bdd.or(result.get(i).condition,condition),value));return;}
            result.add(new Piece(condition,value));
        }
    }
    private void enqueue(Slot slot){attempts++;if(slot.queued){duplicates++;return;}slot.queued=true;pending.addLast(slot);pushes++;maxSize=Math.max(maxSize,pending.size());}
    private void contribute(Slot source,int condition,ProgramIndex.Node destination,CfgTransition edge,S state) {
        if(condition==0)return;deliveries++;
        var target=new AnalysisPoint(-1,source.region.entry.model.context(),destination);
        S contribution=Objects.requireNonNull(definition.transferEdge(target,edge,state,work));
        if(definition.equivalent(contribution,state,work)||feasible(source.region,condition,source))source.out.add(condition,contribution);
    }
    private void read(Slot source,Slot target,int condition,CfgTransition edge) {
        if(target==null)return;
        for(var piece:target.in.pieces)contribute(source,source.region.entry.bdd.and(condition,piece.condition),target.node,edge,piece.state);
    }
    private final class Choice {
        final int condition;final Map<ProgramIndex.Node,S> values;
        Choice(int condition,Map<ProgramIndex.Node,S> values){this.condition=condition;this.values=values;}
    }
    private List<Choice> arguments(Slot caller,Set<ProgramIndex.Node> targets,int condition) {
        var parent=caller.region;
        List<Choice> choices=List.of(new Choice(condition,Map.of()));var b=parent.entry.bdd;
        for(var target:targets) {
            var slot=parent.slots.get(target);var options=slot==null?List.<Piece>of():slot.in.pieces;var next=new ArrayList<Choice>();
            for(var choice:choices) {
                int remainder=choice.condition;
                for(var value:options) {
                    int overlap=b.and(choice.condition,value.condition);remainder=b.difference(remainder,value.condition);
                    if(overlap==0||targets.size()>1&&!feasible(parent,overlap,caller))continue;var values=new IdentityHashMap<>(choice.values);values.put(target,value.state);next.add(new Choice(overlap,values));
                }
                if(remainder!=0&&(targets.size()<=1||feasible(parent,remainder,caller))){var values=new IdentityHashMap<>(choice.values);values.put(target,bottom);next.add(new Choice(remainder,values));}
            }
            choices=next;
        }
        return choices;
    }
    private void call(Slot source,ActivationControl.Move move,int condition) {
        var parent=source.region;var e=parent.entry;var b=e.bdd;
        var needed=new LinkedHashSet<>(e.model.unwindTargets());var returns=new LinkedHashSet<ProgramIndex.Node>();
        for(var point:e.model.shapes().get(move.frame()).points().values())for(var exit:point.moves())if(exit.action()==ActivationControl.Action.POP)returns.add(exit.destination());
        needed.addAll(returns);
        for(var choice:arguments(source,needed,condition)) {
            var returnValues=new IdentityHashMap<ProgramIndex.Node,S>();for(var node:returns)returnValues.put(node,choice.values.getOrDefault(node,bottom));
            var ancestors=new ArrayList<Map<ProgramIndex.Node,S>>();
            if(e.model.maxUnwind()>0) {
                var first=new IdentityHashMap<ProgramIndex.Node,S>();for(var node:e.model.unwindTargets())first.put(node,choice.values.getOrDefault(node,bottom));ancestors.add(Map.copyOf(first));
                for(int i=1;i<e.model.maxUnwind();i++)ancestors.add(parent.input.ancestors.get(i-1));
            }
            var input=new Signature(Math.min(parent.input.depth+1,e.model.maxUnwind()),returnValues,e.roots(),ancestors);
            var child=e.region(move.frame(),input,source,choice.condition);if(child==null)continue;subscribe(source,child,choice.condition);
            var entry=child.slots.get(e.model.control().entry(move.frame()));
            for(var value:entry.in.pieces) {
                int pre=parent.frame==null?b.atEmpty(value.condition):b.restrict(value.condition,parent.frame.variable(),true);
                contribute(source,b.and(choice.condition,pre),entry.node,move.edge(),value.state);
            }
        }
    }
    private void subscribe(Slot caller,Region child,int condition) {
        var b=child.entry.bdd;caller.children.merge(child,condition,b::or);child.callers.add(caller);
        int old=child.incoming.getOrDefault(caller,0),combined=b.or(old,condition);
        if(combined!=old) {
            child.incoming.put(caller,combined);child.incomingVersion++;
            for(var waiting:child.waiters)enqueue(waiting);
        }
    }
    private void reconcile(Slot caller,Map<Region,Integer> previous) {
        // Positive witnesses survive added links, but not removal/replacement.
        for(var old:previous.entrySet())if(!Objects.equals(caller.children.get(old.getKey()),old.getValue())){feasibleCache.clear();break;}
        for(var child:previous.keySet())if(!caller.children.containsKey(child)){child.callers.remove(caller);child.incoming.remove(caller);}
        caller.children.forEach((child,condition)->child.incoming.put(caller,condition));
    }
    private void process(Slot slot) {
        var previous=slot.children;slot.children=new IdentityHashMap<>();
        for(var watched:slot.watches)watched.waiters.remove(slot);slot.watches.clear();
        transfers++;var region=slot.region;var entry=region.entry;var b=entry.bdd;
        var boundary=entry.boundaries.getOrDefault(region.frame,Map.of()).get(slot.node);
        if(boundary!=null)slot.out.add(slot.shape.condition(),boundary);
        for(var move:slot.shape.moves()) {
            int condition=slot.shape.condition();
            if(move.variable()>=0) {
                int active=region.frame==null?0:region.frame.variable()==move.variable()?1:b.variable(move.variable());
                condition=b.and(condition,move.present()?active:b.not(active));
            }
            if(condition==0)continue;
            switch(move.action()) {
                case NEXT -> read(slot,region.slots.get(move.destination()),condition,move.edge());
                case CALL -> call(slot,move,condition);
                case POP -> contribute(slot,condition,move.destination(),move.edge(),region.input.returns.getOrDefault(move.destination(),bottom));
                case UNWIND -> {
                    boolean valid=move.count()<=region.input.depth;var destination=valid?move.destination():move.invalid();
                    S value=valid?region.input.ancestors.get(move.count()-1).getOrDefault(destination,bottom):region.frame==null?bottom:region.input.roots.getOrDefault(destination,bottom);
                    var edge=valid?move.edge():entry.model.control().edge(slot.node,destination);
                    if(region.frame==null)read(slot,entry.root.slots.get(destination),condition,edge);else contribute(slot,condition,destination,edge,value);
                }
                case ROOT -> {
                    if(region.frame==null)read(slot,entry.root.slots.get(move.destination()),condition,move.edge());
                    else contribute(slot,condition,move.destination(),move.edge(),region.input.roots.getOrDefault(move.destination(),bottom));
                }
                case RECURSIVE -> { /* Refusal is checked only for a feasible caller predicate below. */ }
            }
        }
        boolean changed=false;
        for(var piece:slot.out.pieces) {
            S value=Objects.requireNonNull(definition.transferBlock(slot.point,piece.state,work));
            // Identity propagation cannot generate fresh values around a cycle.
            if(definition.equivalent(value,piece.state,work)||feasible(region,piece.condition,slot))changed|=slot.in.add(piece.condition,value);
        }
        if(changed) {
            changes++;for(var predecessor:region.predecessors.getOrDefault(slot.node,List.of()))enqueue(predecessor);
            for(var call:region.calls)enqueue(call);
            if(slot.node==entry.model.control().entry(region.frame))for(var caller:region.callers)enqueue(caller);
            if(region.frame==null&&entry.model.rootTargets().contains(slot.node))for(var call:entry.calls)enqueue(call);
        } else unchanged++;
        reconcile(slot,previous);
    }
    private final class Deferred {
        final Map<Region,Long> versions=new IdentityHashMap<>();
        Deferred(Collection<Region> watched){for(var region:watched)versions.put(region,region.incomingVersion);}
        boolean valid(){for(var v:versions.entrySet())if(v.getKey().incomingVersion!=v.getValue())return false;return true;}
        void subscribe(Slot subscriber){if(subscriber!=null)for(var region:versions.keySet()){region.waiters.add(subscriber);subscriber.watches.add(region);}}
    }
    private void defer(Need key,Collection<Region> watched,Slot subscriber) {
        var deferred=new Deferred(watched);deferred.subscribe(subscriber);deferredCache.put(key,deferred);
        if(deferredCache.size()>1024)deferredCache.remove(deferredCache.keySet().iterator().next());
    }
    private final class Witness {
        final Region region;final int condition;final Witness child;final boolean requiredSeen;
        Witness(Region region,int condition,Witness child,boolean requiredSeen){this.region=region;this.condition=condition;this.child=child;this.requiredSeen=requiredSeen;}
    }
    private final Map<EntryRun,CallerWitnesses<Region>> finalWitnesses=new IdentityHashMap<>();
    private CallerWitnesses<Region> finalWitnesses(EntryRun entry) {
        var successors=new IdentityHashMap<Region,List<CallerWitnesses.Edge<Region>>>();
        for(var child:entry.regions)for(var link:child.incoming.entrySet())
            successors.computeIfAbsent(link.getKey().region,r->new ArrayList<>()).add(new CallerWitnesses.Edge<>(child,link.getValue()));
        return new CallerWitnesses<>(entry.bdd,entry.model.control().variables(),entry.root,successors,r->r.frame==null?-1:r.frame.variable());
    }
    private boolean feasible(Region region,int condition) {return feasible(region,condition,null);}
    private boolean feasible(Region region,int condition,Slot subscriber) {
        if(condition==0)return false;var key=new Need(region,condition);var known=feasibleCache.get(key);if(known!=null)return known;
        if(subscriber==null&&finalWitnesses.computeIfAbsent(region.entry,this::finalWitnesses).matches(region,condition))
            {feasibleCache.put(key,true);return true;}
        var deferred=deferredCache.get(key);
        if(deferred!=null&&deferred.valid()){deferred.subscribe(subscriber);return false;}

        // First try a shortest caller path. Evaluating its concrete valuation
        // needs no BDD construction. Cyclic dispatchers usually have a short
        // witness even when saturation of all alternative predicates is costly.
        {
            var seen=Collections.newSetFromMap(new IdentityHashMap<Region,Boolean>());
            int required=region.entry.bdd.requiredPresent(condition);
            var visited=new HashSet<Need>();visited.add(new Need(region,required<0?1:0));
            var coarse=new ArrayDeque<Witness>();coarse.add(new Witness(region,1,null,required<0));seen.add(region);Witness root=null;
            while(!coarse.isEmpty()) {
                var current=coarse.removeFirst();
                if(current.region.frame==null&&current.requiredSeen){root=current;break;}
                for(var link:current.region.incoming.entrySet()) {
                    var parent=link.getKey().region;boolean requiredSeen=current.requiredSeen||parent.frame!=null&&parent.frame.variable()==required;
                    if(visited.add(new Need(parent,requiredSeen?1:0))) {
                        seen.add(parent);coarse.addLast(new Witness(parent,link.getValue(),current,requiredSeen));
                    }
                }
            }
            if(root==null){defer(key,seen,subscriber);return false;}
            var active=new BitSet();var path=root;boolean valid=true;
            while(path.child!=null) {
                if(!region.entry.bdd.test(path.condition,active)){valid=false;break;}
                if(path.region.frame!=null)active.set(path.region.frame.variable());
                path=path.child;
            }
            if(valid&&region.entry.bdd.test(condition,active)){feasibleCache.put(key,true);return true;}
        }
        var b=region.entry.bdd;int checkpoint=b.checkpoint();
        try {
            var wanted=new IdentityHashMap<Region,Integer>();var waiting=new IdentityHashMap<Region,Integer>();var queue=new ArrayDeque<Region>();
            wanted.put(region,condition);waiting.put(region,condition);queue.add(region);boolean found=false;
            while(!queue.isEmpty()&&!found) {
                var current=queue.removeFirst();int need=waiting.remove(current);
                if(current.frame==null){found=b.atEmpty(need)!=0;continue;}
                for(var link:current.incoming.entrySet()) {
                    var parent=link.getKey().region;int before=parent.frame==null?b.atEmpty(need):b.restrict(need,parent.frame.variable(),true);
                    before=b.and(before,link.getValue());if(before==0)continue;
                    int old=wanted.getOrDefault(parent,0),extra=b.difference(before,old);if(extra==0)continue;
                    wanted.put(parent,b.or(old,extra));
                    int pending=waiting.getOrDefault(parent,0);waiting.put(parent,b.or(pending,extra));
                    if(pending==0)queue.addLast(parent);
                }
            }
            if(found||subscriber==null)feasibleCache.put(key,found);
            else defer(key,wanted.keySet(),subscriber);
            return found;
        } finally {b.discardAfter(checkpoint);}

    }
}
