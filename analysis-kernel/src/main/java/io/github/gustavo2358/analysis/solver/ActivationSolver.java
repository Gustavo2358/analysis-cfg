package io.github.gustavo2358.analysis.solver;

import io.github.gustavo2358.analysis.cfg.domain.CfgTransition;
import io.github.gustavo2358.analysis.structure.*;
import java.util.*;

/** Input-value tabulation with symbolic ancestor guards and matched caller subscriptions. */
final class ActivationSolver<S> implements AutoCloseable {
    private final AnalysisSession session;
    private final AnalysisDefinition<S> definition;
    private long callerPathEdgesRead;
    private long callerProofEdgesRead(){long total=callerPathEdgesRead;for(var entry:entries)if(entry.paths!=null)total+=entry.paths.edgesRead();return total;}
    private long certificateEdgesRead(){long count=0;for(var entry:entries)if(entry.paths!=null)count+=entry.paths.edgesRead();return count;}
    private long valuationVisits(){long count=0;for(var entry:entries)if(entry.paths!=null)count+=entry.paths.valuationNodeVisits();return count;}
    private long hintIndexProbes(){long count=0;for(var entry:entries)if(entry.paths!=null)count+=entry.paths.indexProbes();return count;}
    private long conditionPeak(){long peak=0;for(var entry:entries)peak=Math.max(peak,entry.bdd.peakNodes());return peak;}
    private final DomainWork work=new DomainWork();
    // Resident compatibility route; the index accepts managed resources at its port.
    private final AnalysisResources indexResources=new AnalysisResources(new AnalysisResources.Limits(Long.MAX_VALUE,Long.MAX_VALUE,Long.MAX_VALUE,Long.MAX_VALUE,Long.MAX_VALUE,Long.MAX_VALUE,Long.MAX_VALUE));
    private final List<EntryRun> entries=new ArrayList<>();
    private final Set<BooleanConditions> ownedConditions=Collections.newSetFromMap(new IdentityHashMap<>());
    private final ArrayDeque<Slot> pending=new ArrayDeque<>();
    private final S bottom;
    private long joins,initializations,attempts,pushes,pops,duplicates,maxSize,transfers,edgeTransfers,edgeJoins,changed,unchanged;
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
        feasibleCache.clear();for(var entry:entries)feasibleCache.put(new Need(entry.root,1),true);deferredCache.clear();closeWitnesses();
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
            slot.children.clear();slot.watches.clear();slot.anchors.pieces=List.of();slot.outputs.pieces=List.of();
        }
        if(region.entry.paths!=null){region.entry.paths.retire(region.path);region.certifiedIncoming.clear();}
        region.slots.clear();region.incoming.clear();region.callers.clear();region.waiters.clear();region.exits.clear();region.recursive.clear();region.environment=0;
    }
    private boolean structural;
    private record Need(Object region,int condition) { }
    private final Map<Need,Boolean> feasibleCache=new HashMap<>();
    // Bounded memoization only: eviction recomputes a query, never drops work.
    private final Map<Need,Deferred> deferredCache=new LinkedHashMap<>();
    ActivationSolver(AnalysisSession session,AnalysisDefinition<S> definition) {
        this.session=session;this.definition=definition;bottom=Objects.requireNonNull(definition.bottom());
    }
    DataflowResult<S> solve() {
        try(var run=this){run.execute();return run.result();}
    }
    @Override public void close(){closeIndexes(true);}
    private void closeIndexes(boolean closeConditions) {
        RuntimeException failure=null;
        for(var witness:finalWitnesses.values())failure=closeResource(witness,failure);finalWitnesses.clear();
        for(var entry:entries) {
            for(var index:entry.byFrame.values())failure=closeResource(index,failure);entry.byFrame.clear();
            failure=closeResource(entry.preliminary,failure);failure=closeResource(entry.paths,failure);
        }
        failure=closeResource(collector,failure);
        if(closeConditions)for(var conditions:ownedConditions)failure=closeResource(conditions,failure);
        ownedConditions.clear();if(failure!=null)throw failure;
    }
    static RuntimeException closeResource(AutoCloseable resource,RuntimeException failure) {
        if(resource==null)return failure;
        try{resource.close();}catch(Exception error) {
            RuntimeException next=error instanceof RuntimeException runtime?runtime:new IllegalStateException(error);
            if(failure==null)return next;failure.addSuppressed(next);
        }
        return failure;
    }
    private void execute() {
        var models=structural?List.<ActivationModel>of():structure(session);
        for(var model:models)ownedConditions.add(model.conditions());
        for(var context:session.contexts())entries.add(new EntryRun(context,models.stream().filter(m->m.context()==context).findFirst().orElse(null)));
        var boundaries=new ArrayList<AnalysisDefinition.Boundary<S>>();definition.boundaries(session).forEach(boundaries::add);

        for(var boundary:boundaries) {
            var entry=entries.stream().filter(e->e.context==boundary.context()).findFirst().orElseThrow(()->new IllegalArgumentException("foreign boundary"));
            ActivationControl.Frame frame=null;
            if(!models.isEmpty()) {
                var model=models.stream().filter(m->m.context()==entry.context).findFirst().orElseThrow();
                var location=ActivationBoundaries.require(model,boundary.node());
                frame=location.frame();
            }
            entry.boundaries.computeIfAbsent(frame,f->new IdentityHashMap<>()).merge(boundary.node(),boundary.state(),(a,b)->definition.joinInto(a,b,work).state());joins++;
        }
        for(var entry:entries)entry.start();
        while(!pending.isEmpty()) {
            var slot=pending.removeFirst();slot.queued=false;pops++;transfers++;
            process(slot);
            if(!structural)collectSummaries(false);
            collectConditions(slot.region.entry);
        }
        if(!structural)collectSummaries(true);
    }
    private void collectConditions(EntryRun entry) {
        if(!entry.bdd.collectionDue())return;
        entry.bdd.collect(root->{
            if(entry.model!=null)entry.model.visitConditions(root);
            var owners=Collections.newSetFromMap(new IdentityHashMap<Object,Boolean>());
            for(var region:entry.regions) {
                owners.add(region);root.accept(region.environment);
                region.incoming.values().forEach(root::accept);region.recursive.values().forEach(root::accept);
                for(var partition:region.exits.values())for(var piece:partition.pieces)root.accept(piece.condition);
                for(var slot:region.slots.values()) {
                    slot.children.values().forEach(root::accept);
                    slot.anchors.visitConditions(root);
                    slot.outputs.visitConditions(root);
                }
            }
            for(var key:feasibleCache.keySet())if(owners.contains(key.region))root.accept(key.condition);
            for(var key:deferredCache.keySet())if(owners.contains(key.region))root.accept(key.condition);
        });
    }
    private DataflowResult<S> result() {
        var lookup=new IdentityHashMap<ContextView,IdentityHashMap<ProgramIndex.Node,List<AnalysisPoint>>>();
        var ins=new ArrayList<S>();var outs=new ArrayList<S>();long edgeCount=0;
        for(var entry:entries) {
            var nodes=new IdentityHashMap<ProgramIndex.Node,List<AnalysisPoint>>();lookup.put(entry.context,nodes);
            for(var region:entry.regions)for(var slot:region.slots.values()) {
                for(var anchor:slot.anchors.pieces) {
                    // The output is joined pointwise; keep roots separate until observation replay.
                    for(var output:slot.outputs.pieces)if(feasible(region,entry.bdd.and(anchor.condition,output.condition))) {
                        var list=nodes.computeIfAbsent(slot.node,n->new ArrayList<>());
                        var point=new AnalysisPoint(ins.size(),entry.context,slot.node);list.add(point);ins.add(anchor.state);outs.add(output.state);
                    }
                }
                edgeCount+=slot.moves.size();
            }
        }
        checkRecursion();
        long indexProbes=closedIndexProbes;for(var entry:entries)for(var index:entry.byFrame.values())indexProbes+=index.probes();
        var metrics=new SolverMetrics(ins.size(),edgeCount,joins,initializations,attempts,pushes,pops,duplicates,maxSize,
            transfers,work.operations(),ins.size(),changed,unchanged,edgeTransfers,edgeJoins,changed,unchanged,0,0,work.joinEntries(),work.compareEntries(),summaryCreated,summaryRetired,summaryLive,summaryPeak,summaryCollections,indexProbes,0,callerProofEdgesRead(),conditionPeak(),certificateEdgesRead(),valuationVisits(),hintIndexProbes());
        return new DataflowResult<>(lookup,ins.toArray(),outs.toArray(),metrics);
    }
    private void checkRecursion() {
        for(var entry:entries)for(var region:entry.regions)for(var error:region.recursive.entrySet())
            if(feasible(region,error.getValue()))entry.control.recursive(error.getKey());
    }
    static List<ActivationModel> structure(AnalysisSession session) {
        AnalysisDefinition<Boolean> reach=new AnalysisDefinition<>() {
            public Direction direction(){return Direction.FORWARD;}
            public Boolean bottom(){return false;}
            public Iterable<Boundary<Boolean>> boundaries(AnalysisSession ignored){return session.contexts().stream().map(c->new Boundary<>(c,c.entryNode(),true)).toList();}
            public Join<Boolean> joinInto(Boolean a,Boolean b,DomainWork work){return new Join<>(a||b,!a&&b);}
            public long stateFingerprint(Boolean state){return state?1:0;}
            public boolean equivalent(Boolean a,Boolean b,DomainWork work){return a.equals(b);}
            public Boolean transferBlock(AnalysisPoint point,Boolean state,DomainWork work){return state;}
            public Boolean transferEdge(AnalysisPoint point,CfgTransition edge,Boolean state,DomainWork work){return state;}
        };
        var engine=new ActivationSolver<Boolean>(session,reach);engine.structural=true;boolean transferred=false;
        try {engine.execute();engine.checkRecursion();var models=new ArrayList<ActivationModel>();
        for(var entry:engine.entries) {
            var shapes=new IdentityHashMap<ActivationControl.Frame,ActivationModel.Shape>();
            var parents=new IdentityHashMap<ActivationControl.Frame,List<ActivationModel.Push>>();
            var roots=new LinkedHashSet<ProgramIndex.Node>();var unwind=new LinkedHashSet<ProgramIndex.Node>();int depth=0;
            for(var region:entry.regions) {
                var points=new LinkedHashMap<ProgramIndex.Node,ActivationModel.Point>();
                for(var slot:region.slots.values()) {
                    int condition=0;for(var piece:slot.anchors.pieces)condition=entry.bdd.or(condition,piece.condition);
                    points.put(slot.node,new ActivationModel.Point(condition,slot.moves));
                    for(var move:slot.moves) {
                        if(move.action()==ActivationControl.Action.ROOT)roots.add(move.destination());
                        if(move.action()==ActivationControl.Action.UNWIND){roots.add(move.invalid());unwind.add(move.destination());depth=Math.max(depth,move.count());}
                    }
                }
                shapes.put(region.frame,new ActivationModel.Shape(region.frame,points));
                var links=new ArrayList<ActivationModel.Push>();
                for(var link:region.incoming.entrySet())links.add(new ActivationModel.Push(link.getKey().region.frame,entry.control.frameAt(link.getKey().node),link.getValue()));
                parents.put(region.frame,List.copyOf(links));
            }
            refineShapes(entry.bdd,shapes,parents);
            models.add(new ActivationModel(entry.context,entry.control,entry.bdd,shapes,parents,List.copyOf(roots),List.copyOf(unwind),depth));
        }
        transferred=true;return models;
        }finally{engine.closeIndexes(!transferred);}
    }
    /** Reduce impossible tested guards using a shared persistent SCC support relation. */
    private static void refineShapes(BooleanConditions b,Map<ActivationControl.Frame,ActivationModel.Shape> shapes,
            Map<ActivationControl.Frame,List<ActivationModel.Push>> parents) {
        var frames=new ArrayList<ActivationControl.Frame>();var ordinals=new IdentityHashMap<ActivationControl.Frame,Integer>();
        for(var frame:shapes.keySet())if(frame!=null){ordinals.put(frame,frames.size());frames.add(frame);}
        if(frames.isEmpty())return;
        int bindings=0;for(var links:parents.values())bindings+=links.size();
        int[] variables=new int[frames.size()+bindings];Arrays.fill(variables,-1);int[][] incoming=new int[variables.length][];int binding=frames.size();
        for(int i=0;i<frames.size();i++) {
            var links=parents.getOrDefault(frames.get(i),List.of());incoming[i]=new int[links.size()];
            for(int slot=0;slot<links.size();slot++) {
                var link=links.get(slot);incoming[i][slot]=binding;variables[binding]=link.symbol().variable();
                incoming[binding++]=link.parent()==null?new int[0]:new int[]{ordinals.get(link.parent())};
            }
        }
        // Explicit resident compatibility backend; managed session injection is a later wave.
        var resources=new AnalysisResources(new AnalysisResources.Limits(Long.MAX_VALUE,Long.MAX_VALUE,Long.MAX_VALUE,Long.MAX_VALUE,Long.MAX_VALUE,Long.MAX_VALUE,Long.MAX_VALUE));
        try(var store=new ResidentPageStore(4096,resources,AnalysisResources.Phase.CONTROL);
                var support=new PersistentGraphClosure(store,resources,AnalysisResources.Phase.CONTROL,variables,incoming)) {
            var memos=new HashMap<Long,Map<Integer,Integer>>();
            for(int i=0;i<frames.size();i++) {
                final int ordinal=i;var frame=frames.get(i);var shape=shapes.get(frame);
                var memo=memos.computeIfAbsent(support.root(i),root->new HashMap<>());
                java.util.function.IntPredicate allowed=key->support.contains(ordinal,key);
                var points=new LinkedHashMap<ProgramIndex.Node,ActivationModel.Point>();
                for(var point:shape.points().entrySet()) {
                    int condition=b.restrictAbsent(point.getValue().condition(),allowed,memo);if(condition==0)continue;
                    var moves=new ArrayList<ActivationControl.Move>();
                    for(var move:point.getValue().moves()) {
                        if(move.variable()<0||allowed.test(move.variable()))moves.add(move);
                        else if(!move.present())moves.add(new ActivationControl.Move(move.action(),move.destination(),move.frame(),-1,false,move.count(),move.invalid(),move.edge()));
                    }
                    points.put(point.getKey(),new ActivationModel.Point(condition,List.copyOf(moves)));
                }
                shapes.put(frame,new ActivationModel.Shape(frame,points));
                var links=parents.get(frame);var refined=new ArrayList<ActivationModel.Push>();
                for(var link:links) {
                    int condition=link.parent()==null?b.atEmpty(link.condition()):link.condition();
                    if(condition!=0)refined.add(new ActivationModel.Push(link.parent(),link.symbol(),condition));
                }
                parents.put(frame,List.copyOf(refined));
            }
        }
    }
    /** Backward reachability of a guard predicate through caller subscriptions, without stack strings. */
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
            successors.computeIfAbsent(link.getKey().region,r->new ArrayList<>()).add(new CallerWitnesses.Edge<>(child,link.getValue(),entry.control.frameAt(link.getKey().node).variable()));
        return new CallerWitnesses<>(entry.bdd,entry.control.variables(),entry.root,successors,r->r.frame==null?-1:r.frame.variable(),indexResources,null);
    }
    private boolean feasible(Region region,int condition) {return feasible(region,condition,null);}
    private boolean feasible(Region region,int condition,Slot subscriber) {
        if(condition==0)return false;
        var key=new Need(region,condition);
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
            var visited=new HashSet<Need>();visited.add(new Need(region,required<0?1:0));
            var coarse=new ArrayDeque<Witness>();coarse.add(new Witness(region,1,null,required<0,-1));seen.add(region);Witness root=null;
            while(!coarse.isEmpty()) {
                var current=coarse.removeFirst();
                if(current.region.frame==null&&current.requiredSeen){root=current;break;}
                for(var link:current.region.incoming.entrySet()) {
                    callerPathEdgesRead++;
                    var parent=link.getKey().region;boolean requiredSeen=current.requiredSeen||region.entry.control.frameAt(link.getKey().node).variable()==required;
                    if(visited.add(new Need(parent,requiredSeen?1:0))) {
                        seen.add(parent);coarse.addLast(new Witness(parent,link.getValue(),current,requiredSeen,region.entry.control.frameAt(link.getKey().node).variable()));
                    }
                }
            }
            if(root==null){defer(key,seen,subscriber);return false;}
            var active=new BitSet();var path=root;boolean valid=true;
            feasibleCache.put(new Need(path.region,1),true);
            while(path.child!=null) {
                callerPathEdgesRead++;
                if(!region.entry.bdd.test(path.condition,active)){valid=false;break;}
                active.set(path.pushedVariable);
                path=path.child;feasibleCache.put(new Need(path.region,1),true);
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
                    var parent=link.getKey().region;int before=b.restrict(need,region.entry.control.frameAt(link.getKey().node).variable(),true);
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
    private final class EntryRun {
        final CallerPathCertificates paths;
        final ContextView context;
        final ActivationModel model;
        final ActivationControl control;
        final BooleanConditions bdd;
        final List<Region> regions=new ArrayList<>();
        final IdentityHashMap<ActivationControl.Frame,StateIndex<S,Region>> byFrame=new IdentityHashMap<>();
        final Map<ActivationControl.Frame,Map<ProgramIndex.Node,S>> boundaries=new IdentityHashMap<>();
        Region root;
        PreliminaryCallerSupport preliminary;
        EntryRun(ContextView context,ActivationModel model){
            this.context=context;this.model=model;control=model==null?new ActivationControl(session,context):model.control();
            bdd=model==null?new BooleanConditions():model.conditions();ownedConditions.add(bdd);paths=new CallerPathCertificates(bdd,indexResources,null);
        }
        void start() {
            if(structural)preliminary=new PreliminaryCallerSupport(control,indexResources);
            root=new Region(this,null,bottom);regions.add(root);summaryCreated();feasibleCache.put(new Need(root,1),true);root.accept(1);
        }
        Region region(ActivationControl.Frame frame,S input,Slot caller,int condition) {
            frame=control.body(frame);
            var candidates=byFrame.get(frame);
            if(candidates!=null){var known=candidates.get(input);if(known!=null)return known;}
            if(!structural&&!feasible(caller.region,condition,caller))return null;
            if(candidates==null){candidates=new StateIndex<>(indexResources,structural?AnalysisResources.Phase.CONTROL:AnalysisResources.Phase.DOMAIN,definition::stateFingerprint,(a,b)->definition.equivalent(a,b,work));byFrame.put(frame,candidates);}
            var result=new Region(this,frame,input);candidates.putIfAbsent(input,result);regions.add(result);summaryCreated();return result;
        }
    }
    private final class Region {
        final EntryRun entry;
        final ActivationControl.Frame frame;
        final S input;
        final Map<ProgramIndex.Node,Slot> slots=new LinkedHashMap<>();
        final Map<ExitKey,Partition> exits=new LinkedHashMap<>();
        final Set<Slot> callers=Collections.newSetFromMap(new IdentityHashMap<>());
        final Set<Slot> waiters=Collections.newSetFromMap(new IdentityHashMap<>());
        final Map<Slot,Integer> incoming=new IdentityHashMap<>();
        long incomingVersion,mark;
        final CallerPathCertificates.Node path;
        final Map<Slot,CallerPathCertificates.Arc> certifiedIncoming;
        final Map<ProgramIndex.Node,Integer> recursive=new IdentityHashMap<>();
        int environment;
        Region(EntryRun entry,ActivationControl.Frame frame,S input){this.entry=entry;this.frame=frame;this.input=input;path=entry.paths==null?null:entry.paths.node(frame==null,gain->{incomingVersion++;if(gain!=0)for(var waiting:waiters)enqueue(waiting);});certifiedIncoming=entry.paths==null?null:new IdentityHashMap<>();}
        Slot slot(ProgramIndex.Node node){return slots.computeIfAbsent(node,n->new Slot(this,n));}
        void accept(int condition) {
            int extra=entry.bdd.difference(condition,environment);if(extra==0)return;
            environment=entry.bdd.or(environment,condition);arrive(slot(entry.control.entry(frame)),extra,input);
        }
        void emit(ExitKey key,int condition,S value) {
            var values=exits.computeIfAbsent(key,k->new Partition(entry));
            if(values.add(condition,value))for(var caller:callers)enqueue(caller);
        }
    }
    private record ExitKey(ProgramIndex.Node source,ProgramIndex.Node destination,ActivationControl.Action action,int count,ProgramIndex.Node invalid,CfgTransition edge) { }
    private final class Slot {
        final Region region;
        final ProgramIndex.Node node;
        final AnalysisPoint point;
        final Partition anchors,outputs;
        final List<ActivationControl.Move> moves;
        Map<Region,Integer> children=new IdentityHashMap<>();
        final Set<Region> watches=Collections.newSetFromMap(new IdentityHashMap<>());
        boolean queued;
        Slot(Region region,ProgramIndex.Node node) {
            this.region=region;this.node=node;point=new AnalysisPoint(nextPoint++,region.entry.context,node);
            anchors=new Partition(region.entry);outputs=new Partition(region.entry);moves=region.entry.model==null?region.entry.control.moves(node,region.frame):region.entry.model.shapes().get(region.frame).points().get(node).moves();initializations++;recordsSinceSweep++;
        }
    }
    private final class Partition extends GuardedStates<S> {
        Partition(EntryRun entry){super(entry.bdd,definition,work);}
    }
    private void enqueue(Slot slot) {
        attempts++;if(slot.queued){duplicates++;return;}slot.queued=true;pending.addLast(slot);pushes++;maxSize=Math.max(maxSize,pending.size());
    }
    private void arrive(Slot slot,int condition,S value) {
        edgeJoins++;
        if(slot.anchors.add(condition,value)){changed++;enqueue(slot);}else unchanged++;
    }
    private S edge(AnalysisPoint source,CfgTransition transition,S value) {
        edgeTransfers++;return Objects.requireNonNull(definition.transferEdge(source,transition,value,work));
    }
    private void arriveEdge(Slot caller,Region target,ProgramIndex.Node destination,AnalysisPoint source,CfgTransition transition,int condition,S value) {
        if(target.entry.model!=null) {
            var shape=target.entry.model.shapes().get(target.frame);var point=shape.points().get(destination);
            if(point==null)return;
            condition=target.entry.bdd.and(condition,point.condition());if(condition==0)return;
        }
        S contribution=edge(source,transition,value);
        if(structural||definition.equivalent(contribution,value,work)||feasible(target,condition,caller))arrive(target.slot(destination),condition,contribution);
    }
    private void subscribe(Slot caller,Region child,int condition) {
        var b=child.entry.bdd;caller.children.merge(child,condition,b::or);child.callers.add(caller);
        if(Boolean.TRUE.equals(feasibleCache.get(new Need(caller.region,condition))))
            feasibleCache.put(new Need(child,1),true);
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
            else child.certifiedIncoming.put(caller,child.entry.paths.add(caller.region.path,child.path,child.entry.control.frameAt(caller.node).variable(),condition));
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
        // A subscription represents the current input, not every intermediate input
        // ever seen at this caller. Old summaries may stay cached but cannot become
        // additional observable contexts after their last caller has moved on.
        var previous=slot.children;slot.children=new IdentityHashMap<>();
        for(var watched:slot.watches)watched.waiters.remove(slot);slot.watches.clear();
        var region=slot.region;var e=region.entry;var b=e.bdd;
        var boundary=e.boundaries.getOrDefault(region.frame,Map.of()).get(slot.node);
        if(boundary!=null)for(var piece:List.copyOf(slot.anchors.pieces))slot.anchors.add(piece.condition,boundary);
        for(var piece:slot.anchors.pieces) {
            S output=Objects.requireNonNull(definition.transferBlock(slot.point,piece.state,work));
            // Identity propagation cannot generate fresh values around a cycle.
            if(!structural&&!definition.equivalent(output,piece.state,work)&&!feasible(region,piece.condition,slot))continue;
            slot.outputs.add(piece.condition,output);
            for(var move:slot.moves) {
                int condition=piece.condition;
                if(move.variable()>=0) {
                    int active=region.frame==null||structural&&!e.preliminary.mayContain(region.frame,move.variable())?0:b.variable(move.variable());
                    condition=b.and(condition,move.present()?active:b.not(active));
                }
                if(condition==0)continue;
                switch(move.action()) {
                    case NEXT -> arriveEdge(slot,region,move.destination(),slot.point,move.edge(),condition,output);
                    case CALL -> {
                        if(!structural&&!feasible(region,condition,slot))continue;
                        S input=edge(slot.point,move.edge(),output);var child=e.region(move.frame(),input,slot,condition);if(child==null)continue;
                        subscribe(slot,child,condition);
                        // A summary is a function of ancestor guards, not their enumerated valuations.
                        child.accept(1);
                        // Snapshot: a self-call may discover an additional summary while delivering one.
                        for(var summary:new ArrayList<>(child.exits.entrySet()))for(var returned:summary.getValue().pieces) {
                            int valid=b.restrict(returned.condition,move.frame().variable(),true);if(region.frame==null)valid=b.atEmpty(valid);
                            valid=b.and(condition,valid);if(valid==0)continue;
                            receive(slot,region,summary.getKey(),valid,returned.state);
                        }
                    }
                    case RECURSIVE -> region.recursive.merge(slot.node,condition,b::or);
                    case ROOT -> {
                        // The action discards the entire local word. Only its existence
                        // is observable at the empty-root destination, never its prefixes.
                        if(feasible(region,condition,slot)) {
                            var key=new ExitKey(slot.node,move.destination(),move.action(),move.count(),move.invalid(),move.edge());
                            finishAtRoot(slot,e.root,key,1,output);
                        }
                    }
                    case POP,UNWIND -> {
                        var key=new ExitKey(slot.node,move.destination(),move.action(),move.count(),move.invalid(),move.edge());
                        if(region.frame==null)finishAtRoot(slot,region,key,condition,output);
                        else region.emit(key,condition,output);
                    }
                }
            }
        }
        reconcile(slot,previous);
    }
    /** Apply the original edge once, when its actual destination and remaining frame are known. */
    private void receive(Slot caller,Region parent,ExitKey exit,int condition,S value) {
        if(exit.action==ActivationControl.Action.POP||exit.action==ActivationControl.Action.UNWIND&&exit.count==1) {
            var point=new AnalysisPoint(-1,parent.entry.context,exit.source);
            var destination=exit.action==ActivationControl.Action.POP?parent.entry.control.returnDestination(exit.source,parent.entry.control.frameAt(caller.node)):exit.destination;
            arriveEdge(caller,parent,destination,point,parent.entry.control.edge(exit.source,destination),condition,value);return;
        }
        var next=exit.action==ActivationControl.Action.UNWIND
            ? new ExitKey(exit.source,exit.destination,exit.action,exit.count-1,exit.invalid,exit.edge):exit;
        if(parent.frame==null)finishAtRoot(caller,parent,next,condition,value);
        else parent.emit(next,condition,value);
    }
    private void finishAtRoot(Slot caller,Region root,ExitKey exit,int condition,S value) {
        var target=exit.action==ActivationControl.Action.UNWIND?exit.invalid:exit.destination;
        var edge=target==exit.destination?exit.edge:root.entry.control.edge(exit.source,target);
        var point=new AnalysisPoint(-1,root.entry.context,exit.source);
        arriveEdge(caller,root,target,point,edge,condition,value);
    }
}
