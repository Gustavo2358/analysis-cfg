package io.github.gustavo2358.analysis.solver;

import io.github.gustavo2358.analysis.cfg.domain.CfgTransition;
import io.github.gustavo2358.analysis.structure.*;
import java.util.*;

/** Dual tabulation: an activation is keyed by values at its possible continuations. */
final class BackwardActivationSolver<S> implements AutoCloseable {
    private final AnalysisSession session;
    private final AnalysisDefinition<S> definition;
    private long callerPathEdgesRead;
    private long callerProofEdgesRead(){long total=callerPathEdgesRead;for(var entry:entries)if(entry.paths!=null)total+=entry.paths.edgesRead();return total;}
    private long certificateEdgesRead(){long count=0;for(var entry:entries)if(entry.paths!=null)count+=entry.paths.edgesRead();return count;}
    private long valuationVisits(){long count=0;for(var entry:entries)if(entry.paths!=null)count+=entry.paths.valuationNodeVisits();return count;}
    private long hintIndexProbes(){long count=0;for(var entry:entries)if(entry.paths!=null)count+=entry.paths.indexProbes();return count;}
    private long conditionPeak(){long peak=0;for(var entry:entries)peak=Math.max(peak,entry.bdd.peakNodes());return peak;}
    private final DomainWork work=new DomainWork();
    private final AnalysisResources indexResources=new AnalysisResources(new AnalysisResources.Limits(Long.MAX_VALUE,Long.MAX_VALUE,Long.MAX_VALUE,Long.MAX_VALUE,Long.MAX_VALUE,Long.MAX_VALUE,Long.MAX_VALUE));
    private final S bottom;
    private final List<EntryRun> entries=new ArrayList<>();
    private final Set<BooleanConditions> ownedConditions=Collections.newSetFromMap(new IdentityHashMap<>());
    private final ArrayDeque<Slot> pending=new ArrayDeque<>();
    private int nextPoint;
    private long summaryCreated,summaryLive,summaryPeak;
    private void summaryCreated(){summaryCreated++;summaryLive++;recordsSinceSweep++;summaryPeak=Math.max(summaryPeak,summaryLive);}
    private long summaryRetired,summaryCollections,recordsSinceSweep,sweepThreshold=64,closedIndexProbes;
    private SummaryCollector<Region> collector;
    private void collectSummaries(boolean force) {
        if(!force&&recordsSinceSweep<sweepThreshold)return;
        if(collector==null)collector=new SummaryCollector<>(indexResources,AnalysisResources.Phase.DOMAIN,r->r.mark,(r,token)->r.mark=token);
        long[] units={entries.size()+pending.size()};
        collector.mark(visit->{for(var entry:entries)visit.accept(entry.root);for(var slot:pending)visit.accept(slot.region);},(region,visit)->{
            units[0]+=1+region.slots.size();indexResources.work(1+region.slots.size(),AnalysisResources.Phase.DOMAIN);
            for(var slot:region.slots.values()) {
                units[0]+=slot.children.size()+slot.watches.size();
                for(var child:slot.children.keySet())visit.accept(child);
                for(var watched:slot.watches)visit.accept(watched);
            }
        });
        feasibleCache.clear();for(var entry:entries)feasibleCache.put(need(entry.root,1),true);deferredCache.clear();closeWitnesses();
        for(var entry:entries) {
            long removed=collector.retain(entry.regions,this::retireSummary);summaryRetired+=removed;summaryLive-=removed;
            var iterator=entry.byFrame.entrySet().iterator();
            while(iterator.hasNext()) {
                var index=iterator.next().getValue();index.retainEntries(collector::isMarked);
                if(index.size()==0){closedIndexProbes+=index.probes();index.close();iterator.remove();}
            }
        }
        summaryCollections++;recordsSinceSweep=0;sweepThreshold=Math.max(64,units[0]);
    }
    private void retireSummary(Region region) {
        for(var slot:region.slots.values()) {
            for(var child:slot.children.keySet()){child.callers.remove(slot);replaceIncoming(slot,child,0);}
            for(var watched:slot.watches)watched.waiters.remove(slot);
            slot.children.clear();slot.watches.clear();slot.in.clear();slot.out.clear();
        }
        if(region.entry.paths!=null){region.entry.paths.retire(region.path);region.certifiedIncoming.clear();}
        for(var slot:region.slots.values())detachRootReads(slot);
        region.slots.clear();region.incoming.clear();region.callers.clear();region.waiters.clear();region.predecessors.clear();
    }
    private long transfers,deliveries,joins,pushes,pops,attempts,duplicates,maxSize,changes,unchanged;
    private long returnSourceReads;
    private record Need(Object region,int condition,long generation) { }
    private Need need(Region region,int condition){return new Need(region,condition,region.entry.bdd.generationOf(condition));}
    private final Map<Need,Boolean> feasibleCache=new HashMap<>();
    // Bounded memoization only: eviction recomputes a query, never drops work.
    private final Map<Need,Deferred> deferredCache=new LinkedHashMap<>();
    BackwardActivationSolver(AnalysisSession session,AnalysisDefinition<S> definition){this.session=session;this.definition=definition;bottom=Objects.requireNonNull(definition.bottom());}
    DataflowResult<S> solve() {
        try(var run=this){return run.execute();}
    }
    @Override public void close(){closeOwners();}
    private void closeOwners() {
        RuntimeException failure=null;
        for(var witness:finalWitnesses.values())failure=ActivationSolver.closeResource(witness,failure);finalWitnesses.clear();
        for(var entry:entries) {
            for(var index:entry.byFrame.values())failure=ActivationSolver.closeResource(index,failure);entry.byFrame.clear();
            failure=ActivationSolver.closeResource(entry.paths,failure);
        }
        failure=ActivationSolver.closeResource(collector,failure);
        for(var conditions:ownedConditions)failure=ActivationSolver.closeResource(conditions,failure);
        ownedConditions.clear();if(failure!=null)throw failure;
    }
    private DataflowResult<S> execute() {
        var models=ActivationSolver.structure(session);
        for(var model:models)ownedConditions.add(model.conditions());
        for(var conditions:ownedConditions)conditions.enableOwnership();
        for(var model:models)model.visitConditions(model.conditions()::retainPermanentRoot);
        for(var model:models)entries.add(new EntryRun(model));
        for(var conditions:ownedConditions)conditions.publishCreated();
        for(var boundary:definition.boundaries(session)) {
            var entry=entries.stream().filter(e->e.model.context()==boundary.context()).findFirst().orElseThrow(()->new IllegalArgumentException("foreign boundary"));
            var location=ActivationBoundaries.require(entry.model,boundary.node());
            entry.boundaries.computeIfAbsent(location.frame(),f->new IdentityHashMap<>()).merge(boundary.node(),boundary.state(),(a,b)->definition.joinInto(a,b,work).state());joins++;
        }
        for(var entry:entries)entry.start();
        while(!pending.isEmpty()){
            var slot=pending.removeFirst();slot.queued=false;pops++;var b=slot.region.entry.bdd;b.beginMutation();
            // Failed executions leave their journal to run-owner destruction;
            // publishing through an aborted manager would replace the cause.
            process(slot);collectSummaries(false);b.endMutation();
        }
        collectSummaries(true);
        var lookup=new IdentityHashMap<ContextView,IdentityHashMap<ProgramIndex.Node,List<AnalysisPoint>>>();
        var ins=new ArrayList<S>();var outs=new ArrayList<S>();long edgeCount=0;
        for(var entry:entries) {
            var nodes=new IdentityHashMap<ProgramIndex.Node,List<AnalysisPoint>>();lookup.put(entry.model.context(),nodes);
            for(var region:entry.regions)for(var slot:region.slots.values()) {
                edgeCount+=slot.shape.moves().size();entry.bdd.beginMutation();
                for(var in:slot.in.pieces)for(var out:slot.out.pieces)if(feasible(region,entry.bdd.and(in.condition,out.condition))) {
                    int id=ins.size();nodes.computeIfAbsent(slot.node,n->new ArrayList<>()).add(new AnalysisPoint(id,entry.model.context(),slot.node));ins.add(in.state);outs.add(out.state);
                }entry.bdd.endMutation();
            }
        }
        long indexProbes=closedIndexProbes;for(var entry:entries)for(var index:entry.byFrame.values())indexProbes+=index.probes();
        var metrics=new SolverMetrics(ins.size(),edgeCount,joins,nextPoint,attempts,pushes,pops,duplicates,maxSize,transfers,work.operations(),ins.size(),changes,unchanged,deliveries,deliveries,changes,unchanged,0,0,work.joinEntries(),work.compareEntries(),summaryCreated,summaryRetired,summaryLive,summaryPeak,summaryCollections,indexProbes,returnSourceReads,callerProofEdgesRead(),conditionPeak(),certificateEdgesRead(),valuationVisits(),hintIndexProbes());
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
                    slot.in.visitConditions(root);
                    slot.out.visitConditions(root);
                }
            }
            for(var key:feasibleCache.keySet())if(owners.contains(key.region))root.accept(key.condition);
            for(var key:deferredCache.keySet())if(owners.contains(key.region))root.accept(key.condition);
        });
    }
    private final class Signature {
        final int depth;
        final long fingerprint;
        final Map<ProgramIndex.Node,S> returns;
        final List<Map<ProgramIndex.Node,S>> ancestors;
        Signature(int depth,Map<ProgramIndex.Node,S> returns,List<Map<ProgramIndex.Node,S>> ancestors) {
            this.depth=depth;this.returns=Map.copyOf(returns);this.ancestors=List.copyOf(ancestors);
            long hash=31L*depth+mapFingerprint(this.returns);
            for(var ancestor:this.ancestors)hash=31*hash+mapFingerprint(ancestor);
            fingerprint=31*hash+this.ancestors.size();
        }
        boolean same(Signature other) {
            if(depth!=other.depth||!sameMap(returns,other.returns)||ancestors.size()!=other.ancestors.size())return false;
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
        final CallerPathCertificates paths;
        final ActivationModel model;final BooleanConditions bdd;
        final List<Region> regions=new ArrayList<>();
        final IdentityHashMap<ActivationControl.Frame,StateIndex<Signature,Region>> byFrame=new IdentityHashMap<>();
        final Map<ActivationControl.Frame,Map<ProgramIndex.Node,S>> boundaries=new IdentityHashMap<>();
        Region root;
        EntryRun(ActivationModel model){this.model=model;bdd=model.conditions();paths=new CallerPathCertificates(bdd,indexResources,null);}
        void start() {
            root=new Region(this,null,new Signature(0,Map.of(),Collections.nCopies(model.maxUnwind(),Map.of())));regions.add(root);summaryCreated();feasibleCache.put(need(root,1),true);root.initialize();
        }
        Region region(ActivationControl.Frame frame,Signature input,Slot caller,int condition) {
            frame=model.control().body(frame);
            var candidates=byFrame.get(frame);
            if(candidates!=null){var known=candidates.get(input);if(known!=null)return known;}
            if(!feasible(caller.region,condition,caller))return null;
            if(candidates==null){candidates=new StateIndex<>(indexResources,AnalysisResources.Phase.DOMAIN,a->a.fingerprint,Signature::same);byFrame.put(frame,candidates);}
            var r=new Region(this,frame,input);candidates.putIfAbsent(input,r);regions.add(r);summaryCreated();r.initialize();return r;
        }
    }
    private final class Region {
        final EntryRun entry;final ActivationControl.Frame frame;final Signature input;
        final Map<ProgramIndex.Node,Slot> slots=new LinkedHashMap<>();
        final Map<ProgramIndex.Node,List<Slot>> predecessors=new IdentityHashMap<>();
        final Set<Slot> callers=Collections.newSetFromMap(new IdentityHashMap<>());
        final Set<Slot> waiters=Collections.newSetFromMap(new IdentityHashMap<>());
        final Map<Slot,Integer> incoming;
        long incomingVersion,mark;
        final CallerPathCertificates.Node path;
        final Map<Slot,CallerPathCertificates.Arc> certifiedIncoming;
        Region(EntryRun entry,ActivationControl.Frame frame,Signature input){this.entry=entry;this.frame=frame;this.input=input;incoming=new ConditionBindings<>(entry.bdd);path=entry.paths==null?null:entry.paths.node(frame==null,gain->{incomingVersion++;if(gain!=0)for(var waiting:waiters)enqueue(waiting);});certifiedIncoming=entry.paths==null?null:new IdentityHashMap<>();}
        void initialize() {
            var shape=entry.model.shapes().get(frame);
            for(var point:shape.points().entrySet())slots.put(point.getKey(),new Slot(this,point.getKey(),point.getValue()));
            for(var slot:slots.values()) {
                for(var move:slot.shape.moves()) {
                    if(move.action()==ActivationControl.Action.NEXT||frame==null&&move.action()==ActivationControl.Action.ROOT)
                        predecessors.computeIfAbsent(move.destination(),n->new ArrayList<>()).add(slot);
                    if(move.action()==ActivationControl.Action.CALL) {
                        var childShape=entry.model.shapes().get(entry.model.control().body(move.frame()));
                        // A symbolic CALL move can be impossible under its source
                        // predicate and thus have no reached child shape. It reads nothing.
                        if(childShape==null)continue;
                        var targets=new LinkedHashSet<>(entry.model.unwindTargets());
                        for(var source:childShape.returnSources()) {
                            returnSourceReads++;targets.add(entry.model.control().returnDestination(source,move.frame()));
                        }
                        for(var target:targets)predecessors.computeIfAbsent(target,n->new ArrayList<>()).add(slot);
                    }
                }
                slot.out.add(slot.shape.condition(),bottom);enqueue(slot);
            }
        }
    }
    private final class Slot {
        final Region region;final ProgramIndex.Node node;final ActivationModel.Point shape;
        final AnalysisPoint point;final Partition in,out;boolean queued;
        final Set<Slot> rootReads=Collections.newSetFromMap(new IdentityHashMap<>());
        final Set<Slot> rootReaders=Collections.newSetFromMap(new IdentityHashMap<>());
        Map<Region,Integer> children;
        final Set<Region> watches=Collections.newSetFromMap(new IdentityHashMap<>());
        Slot(Region region,ProgramIndex.Node node,ActivationModel.Point shape) {recordsSinceSweep++;
            this.region=region;this.node=node;this.shape=shape;children=new ConditionBindings<>(region.entry.bdd);point=new AnalysisPoint(nextPoint++,region.entry.model.context(),node);
            in=new Partition(region.entry);out=new Partition(region.entry);
        }
    }
    private final class Partition extends GuardedStates<S> {
        Partition(EntryRun entry){super(entry.bdd,definition,work);}
    }
    private void enqueue(Slot slot){attempts++;if(slot.queued){duplicates++;return;}slot.queued=true;pending.addLast(slot);pushes++;maxSize=Math.max(maxSize,pending.size());}
    private void contribute(Slot source,int condition,ProgramIndex.Node destination,CfgTransition edge,S state) {
        if(condition==0)return;deliveries++;
        var target=new AnalysisPoint(-1,source.region.entry.model.context(),destination);
        S contribution=Objects.requireNonNull(definition.transferEdge(target,edge,state,work));
        if(definition.equivalent(contribution,state,work)||feasible(source.region,condition,source))source.out.add(condition,contribution);
    }
    private void detachRootReads(Slot source) {
        for(var target:source.rootReads)target.rootReaders.remove(source);source.rootReads.clear();
    }
    /** Successor guard is over an empty word, independently of the source word. */
    private void readRoot(Slot source,ProgramIndex.Node destination,int condition,CfgTransition edge) {
        var target=source.region.entry.root.slots.get(destination);if(target==null)return;
        source.rootReads.add(target);target.rootReaders.add(source);
        for(var piece:target.in.pieces)if(source.region.entry.bdd.atEmpty(piece.condition)!=0)
            contribute(source,condition,destination,edge,piece.state);
    }
    private void read(Slot source,Slot target,int condition,CfgTransition edge) {
        if(target==null)return;
        var b=source.region.entry.bdd;
        for(var piece:target.in.pieces){
            long mark=b.constructionMark();
            contribute(source,b.and(condition,piece.condition),target.node,edge,piece.state);b.publishSince(mark);
        }
    }
    private final class Choice {
        final int condition;final Map<ProgramIndex.Node,S> values;
        Choice(int condition,Map<ProgramIndex.Node,S> values){this.condition=condition;this.values=values;}
    }
    private List<Choice> arguments(Slot caller,Set<ProgramIndex.Node> targets,int condition) {
        var parent=caller.region;
        List<Choice> choices=List.of(new Choice(condition,Map.of()));var b=parent.entry.bdd;
        for(var target:targets) {
            var slot=parent.slots.get(target);var options=slot==null?List.<GuardedStates.Piece<S>>of():slot.in.pieces;var next=new ArrayList<Choice>();
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
        if(!feasible(parent,condition,source))return;
        var needed=new LinkedHashSet<>(e.model.unwindTargets());var returns=new IdentityHashMap<ProgramIndex.Node,ProgramIndex.Node>();
        for(var point:e.model.shapes().get(e.model.control().body(move.frame())).returnSources()) {
            returnSourceReads++;returns.put(point,e.model.control().returnDestination(point,move.frame()));
        }
        needed.addAll(returns.values());
        for(var choice:arguments(source,needed,condition)) {
            long mark=b.constructionMark();
            var returnValues=new IdentityHashMap<ProgramIndex.Node,S>();
            for(var binding:returns.entrySet()) {
                deliveries++;var target=new AnalysisPoint(-1,e.model.context(),binding.getValue());
                returnValues.put(binding.getKey(),Objects.requireNonNull(definition.transferEdge(target,e.model.control().edge(binding.getKey(),binding.getValue()),choice.values.getOrDefault(binding.getValue(),bottom),work)));
            }
            var ancestors=new ArrayList<Map<ProgramIndex.Node,S>>();
            if(e.model.maxUnwind()>0) {
                var first=new IdentityHashMap<ProgramIndex.Node,S>();for(var node:e.model.unwindTargets())first.put(node,choice.values.getOrDefault(node,bottom));ancestors.add(Map.copyOf(first));
                for(int i=1;i<e.model.maxUnwind();i++)ancestors.add(parent.input.ancestors.get(i-1));
            }
            var input=new Signature(Math.min(parent.input.depth+1,e.model.maxUnwind()),returnValues,ancestors);
            var child=e.region(move.frame(),input,source,choice.condition);
            if(child==null){b.publishSince(mark);continue;}subscribe(source,child,choice.condition);
            var entry=child.slots.get(e.model.control().entry(move.frame()));
            for(var value:entry.in.pieces) {
                int pre=b.restrict(value.condition,move.frame().variable(),true);if(parent.frame==null)pre=b.atEmpty(pre);
                contribute(source,b.and(choice.condition,pre),entry.node,move.edge(),value.state);
            }
            b.publishSince(mark);
        }
    }
    private void subscribe(Slot caller,Region child,int condition) {
        var b=child.entry.bdd;caller.children.merge(child,condition,b::or);child.callers.add(caller);
        if(Boolean.TRUE.equals(feasibleCache.get(need(caller.region,condition))))
            feasibleCache.put(need(child,1),true);
        int old=child.incoming.getOrDefault(caller,0),combined=b.or(old,condition);
        if(combined!=old) {
            replaceIncoming(caller,child,combined);
        }
    }
    private void replaceIncoming(Slot caller,Region child,int condition) {
        int old=child.incoming.getOrDefault(caller,0);
        if(old==condition)return;
        if(condition==0)child.incoming.remove(caller);else child.incoming.put(caller,condition);
        if(child.entry.paths!=null) {
            var arc=child.certifiedIncoming.get(caller);
            if(condition==0) {
                child.certifiedIncoming.remove(caller);if(arc!=null&&!arc.removed)child.entry.paths.remove(arc);
            }else if(arc!=null&&!arc.removed)child.entry.paths.update(arc,condition);
            else child.certifiedIncoming.put(caller,child.entry.paths.add(caller.region.path,child.path,child.entry.model.control().frameAt(caller.node).variable(),condition));
        }
        child.incomingVersion++;
        if(condition!=0)for(var waiting:child.waiters)enqueue(waiting);
    }
    private void reconcile(Slot caller,Map<Region,Integer> previous) {
        // Nontrivial predicate witnesses still depend on the exact current links.
        // Individual path certificates repair dependent words; still-valid proofs survive.
        for(var old:previous.entrySet())if(!Objects.equals(caller.children.get(old.getKey()),old.getValue())){feasibleCache.clear();break;}
        for(var child:previous.keySet())if(!caller.children.containsKey(child)){child.callers.remove(caller);replaceIncoming(caller,child,0);}
        for(var link:caller.children.entrySet())replaceIncoming(caller,link.getKey(),link.getValue());
    }
    private void process(Slot slot) {
        detachRootReads(slot);
        var previous=slot.children;slot.children=new ConditionBindings<>(slot.region.entry.bdd);
        for(var watched:slot.watches)watched.waiters.remove(slot);slot.watches.clear();
        transfers++;var region=slot.region;var entry=region.entry;var b=entry.bdd;
        var boundary=entry.boundaries.getOrDefault(region.frame,Map.of()).get(slot.node);
        if(boundary!=null)slot.out.add(slot.shape.condition(),boundary);
        for(var move:slot.shape.moves()) {
            long mark=b.constructionMark();
            int condition=slot.shape.condition();
            if(move.variable()>=0) {
                int active=region.frame==null?0:b.variable(move.variable());
                condition=b.and(condition,move.present()?active:b.not(active));
            }
            if(condition==0){b.publishSince(mark);continue;}
            switch(move.action()) {
                case NEXT -> read(slot,region.slots.get(move.destination()),condition,move.edge());
                case CALL -> call(slot,move,condition);
                case POP -> slot.out.add(condition,region.input.returns.getOrDefault(slot.node,bottom));
                case UNWIND -> {
                    boolean valid=move.count()<=region.input.depth;var destination=valid?move.destination():move.invalid();
                    var edge=valid?move.edge():entry.model.control().edge(slot.node,destination);
                    if(valid)contribute(slot,condition,destination,edge,region.input.ancestors.get(move.count()-1).getOrDefault(destination,bottom));
                    else readRoot(slot,destination,condition,edge);
                }
                case ROOT -> readRoot(slot,move.destination(),condition,move.edge());
                case RECURSIVE -> { /* Refusal is checked only for a feasible caller predicate below. */ }
            }
            b.publishSince(mark);
        }
        boolean changed=false;
        for(var piece:slot.out.pieces) {
            S value=Objects.requireNonNull(definition.transferBlock(slot.point,piece.state,work));
            // Identity propagation cannot generate fresh values around a cycle.
            if(definition.equivalent(value,piece.state,work)||feasible(region,piece.condition,slot))changed|=slot.in.add(piece.condition,value);
        }
        if(changed) {
            changes++;for(var predecessor:region.predecessors.getOrDefault(slot.node,List.of()))enqueue(predecessor);
            if(slot.node==entry.model.control().entry(region.frame))for(var caller:region.callers)enqueue(caller);
            for(var reader:slot.rootReaders)enqueue(reader);
        } else unchanged++;
        reconcile(slot,previous);previous.clear();
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
        final Region region;final int condition,pushedVariable;final Witness child;final boolean requiredSeen;
        Witness(Region region,int condition,Witness child,boolean requiredSeen,int pushedVariable){this.region=region;this.condition=condition;this.child=child;this.requiredSeen=requiredSeen;this.pushedVariable=pushedVariable;}
    }
    private void closeWitnesses(){for(var witness:finalWitnesses.values())witness.close();finalWitnesses.clear();}
    private final Map<EntryRun,CallerWitnesses<Region>> finalWitnesses=new IdentityHashMap<>();
    private CallerWitnesses<Region> finalWitnesses(EntryRun entry) {
        var successors=new IdentityHashMap<Region,List<CallerWitnesses.Edge<Region>>>();
        for(var child:entry.regions)for(var link:child.incoming.entrySet())
            successors.computeIfAbsent(link.getKey().region,r->new ArrayList<>()).add(new CallerWitnesses.Edge<>(child,link.getValue(),entry.model.control().frameAt(link.getKey().node).variable()));
        return new CallerWitnesses<>(entry.bdd,entry.model.control().variables(),entry.root,successors,r->r.frame==null?-1:r.frame.variable(),indexResources,null);
    }
    private boolean feasible(Region region,int condition) {return feasible(region,condition,null);}
    private boolean feasible(Region region,int condition,Slot subscriber) {
        if(condition==0)return false;var key=need(region,condition);
        if(region.entry.paths!=null&&!region.entry.paths.rawReached(region.path)) {
            var disconnected=deferredCache.get(key);
            if(disconnected!=null&&disconnected.valid())disconnected.subscribe(subscriber);
            else defer(key,List.of(region),subscriber);
            return false;
        }
        var known=feasibleCache.get(key);if(known!=null)return known;
        if(region.entry.paths!=null&&region.entry.paths.matches(region.path,condition)){feasibleCache.put(key,true);return true;}
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
            var visited=new HashSet<Need>();visited.add(need(region,required<0?1:0));
            var coarse=new ArrayDeque<Witness>();coarse.add(new Witness(region,1,null,required<0,-1));seen.add(region);Witness root=null;
            while(!coarse.isEmpty()&&root==null) {
                var current=coarse.removeFirst();
                if(current.region.frame==null&&current.requiredSeen){root=current;break;}
                for(var link:current.region.incoming.entrySet()) {
                    callerPathEdgesRead++;
                    var parent=link.getKey().region;boolean requiredSeen=current.requiredSeen||region.entry.model.control().frameAt(link.getKey().node).variable()==required;
                    if(visited.add(need(parent,requiredSeen?1:0))) {
                        seen.add(parent);
                        var witness=new Witness(parent,link.getValue(),current,requiredSeen,region.entry.model.control().frameAt(link.getKey().node).variable());
                        // A root discovered from the current breadth layer is already
                        // a shortest caller witness. Returning it immediately avoids
                        // scanning unrelated peers whose insertion order depends on
                        // the physical order of otherwise equivalent sequences.
                        if(parent.frame==null&&requiredSeen){root=witness;break;}
                        coarse.addLast(witness);
                    }
                }
            }
            if(root==null){defer(key,seen,subscriber);return false;}
            var active=new BitSet();var path=root;boolean valid=true;
            feasibleCache.put(need(path.region,1),true);
            while(path.child!=null) {
                callerPathEdgesRead++;
                if(!region.entry.bdd.test(path.condition,active)){valid=false;break;}
                active.set(path.pushedVariable);
                path=path.child;feasibleCache.put(need(path.region,1),true);
            }
            if(valid&&region.entry.bdd.test(condition,active)){feasibleCache.put(key,true);return true;}
        }
        // Only a successful query restores its semantic scratch scope. A failed
        // query is discarded with the enclosing run and must preserve its cause.
        var b=region.entry.bdd;int checkpoint=b.checkpoint();boolean successful=false;
        try {
            var wanted=new IdentityHashMap<Region,Integer>();var waiting=new IdentityHashMap<Region,Integer>();var queue=new ArrayDeque<Region>();
            wanted.put(region,condition);waiting.put(region,condition);queue.add(region);boolean found=false;
            while(!queue.isEmpty()&&!found) {
                var current=queue.removeFirst();int need=waiting.remove(current);
                if(current.frame==null){found=b.atEmpty(need)!=0;continue;}
                for(var link:current.incoming.entrySet()) {
                    callerPathEdgesRead++;
                    var parent=link.getKey().region;int before=b.restrict(need,region.entry.model.control().frameAt(link.getKey().node).variable(),true);
                    before=b.and(before,link.getValue());if(before==0)continue;
                    int old=wanted.getOrDefault(parent,0),extra=b.difference(before,old);if(extra==0)continue;
                    wanted.put(parent,b.or(old,extra));
                    int pending=waiting.getOrDefault(parent,0);waiting.put(parent,b.or(pending,extra));
                    if(pending==0)queue.addLast(parent);
                }
            }
            if(found||subscriber==null)feasibleCache.put(key,found);
            else defer(key,wanted.keySet(),subscriber);
            successful=true;return found;
        } finally {if(successful)b.discardAfter(checkpoint);}

    }
}
