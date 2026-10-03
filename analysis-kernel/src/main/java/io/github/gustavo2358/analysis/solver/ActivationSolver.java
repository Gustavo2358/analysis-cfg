package io.github.gustavo2358.analysis.solver;

import io.github.gustavo2358.analysis.cfg.domain.CfgTransition;
import io.github.gustavo2358.analysis.structure.*;
import java.util.*;

/** Input-value tabulation with symbolic ancestor guards and matched caller subscriptions. */
final class ActivationSolver<S> {
    private final AnalysisSession session;
    private final AnalysisDefinition<S> definition;
    private final DomainWork work=new DomainWork();
    private final List<EntryRun> entries=new ArrayList<>();
    private final ArrayDeque<Slot> pending=new ArrayDeque<>();
    private final S bottom;
    private long joins,initializations,attempts,pushes,pops,duplicates,maxSize,transfers,edgeTransfers,edgeJoins,changed,unchanged;
    private int nextPoint;
    private boolean structural;
    private record Need(Object region,int condition) { }
    private final Map<Need,Boolean> feasibleCache=new HashMap<>();
    // Bounded memoization only: eviction recomputes a query, never drops work.
    private final Map<Need,Deferred> deferredCache=new LinkedHashMap<>();
    ActivationSolver(AnalysisSession session,AnalysisDefinition<S> definition) {
        this.session=session;this.definition=definition;bottom=Objects.requireNonNull(definition.bottom());
    }
    DataflowResult<S> solve() {
        execute();
        return result();
    }
    private void execute() {
        var models=structural?List.<ActivationModel>of():structure(session);
        for(var context:session.contexts())entries.add(new EntryRun(context,models.stream().filter(m->m.context()==context).findFirst().orElse(null)));
        var boundaries=new ArrayList<AnalysisDefinition.Boundary<S>>();definition.boundaries(session).forEach(boundaries::add);

        for(var boundary:boundaries) {
            var entry=entries.stream().filter(e->e.context==boundary.context()).findFirst().orElseThrow(()->new IllegalArgumentException("foreign boundary"));
            ActivationControl.Frame frame=null;
            if(!models.isEmpty()) {
                var model=models.stream().filter(m->m.context()==entry.context).findFirst().orElseThrow();
                var location=ActivationBoundaries.require(model,boundary.node());
                frame=location.frame()==null?null:entry.control.frame(model.control().operation(location.frame()));
            }
            entry.boundaries.computeIfAbsent(frame,f->new IdentityHashMap<>()).merge(boundary.node(),boundary.state(),(a,b)->definition.joinInto(a,b,work).state());joins++;
        }
        for(var entry:entries)entry.start();
        while(!pending.isEmpty()) {
            var slot=pending.removeFirst();slot.queued=false;pops++;transfers++;
            process(slot);
        }
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
        var metrics=new SolverMetrics(ins.size(),edgeCount,joins,initializations,attempts,pushes,pops,duplicates,maxSize,
            transfers,work.operations(),ins.size(),changed,unchanged,edgeTransfers,edgeJoins,changed,unchanged,0,0,work.joinEntries(),work.compareEntries());
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
            public boolean equivalent(Boolean a,Boolean b,DomainWork work){return a.equals(b);}
            public Boolean transferBlock(AnalysisPoint point,Boolean state,DomainWork work){return state;}
            public Boolean transferEdge(AnalysisPoint point,CfgTransition edge,Boolean state,DomainWork work){return state;}
        };
        var engine=new ActivationSolver<Boolean>(session,reach);engine.structural=true;engine.execute();engine.checkRecursion();var models=new ArrayList<ActivationModel>();
        for(var entry:engine.entries) {
            var shapes=new IdentityHashMap<ActivationControl.Frame,ActivationModel.Shape>();
            var parents=new IdentityHashMap<ActivationControl.Frame,Map<ActivationControl.Frame,Integer>>();
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
                var links=new IdentityHashMap<ActivationControl.Frame,Integer>();
                for(var link:region.incoming.entrySet())links.merge(link.getKey().region.frame,link.getValue(),entry.bdd::or);
                parents.put(region.frame,links);
            }
            // Guards outside the transitive caller set can never be active. This is a
            // polynomial overapproximation, not enumeration of reachable ancestor sets.
            var environments=new IdentityHashMap<ActivationControl.Frame,Integer>();
            for(var frame:shapes.keySet()) {
                if(frame==null){environments.put(null,entry.empty);continue;}
                var ancestors=Collections.newSetFromMap(new IdentityHashMap<ActivationControl.Frame,Boolean>());
                var pending=new ArrayDeque<ActivationControl.Frame>();pending.add(frame);var possible=new BitSet();
                while(!pending.isEmpty())for(var parent:parents.getOrDefault(pending.removeFirst(),Map.of()).keySet())
                    if(parent!=null&&ancestors.add(parent)){possible.set(parent.variable());pending.addLast(parent);}
                // The current key is shadowed by this frame (always active locally).
                // Its formal ancestor variable is irrelevant, so do not constrain it.
                possible.set(frame.variable());int environment=1;
                for(int v=entry.control.variables()-1;v>=0;v--)if(!possible.get(v))environment=entry.bdd.node(v,environment,0);
                environments.put(frame,environment);
            }
            for(var shape:new ArrayList<>(shapes.values())) {
                var points=new LinkedHashMap<ProgramIndex.Node,ActivationModel.Point>();
                for(var point:shape.points().entrySet())points.put(point.getKey(),new ActivationModel.Point(entry.bdd.and(point.getValue().condition(),environments.get(shape.frame())),point.getValue().moves()));
                shapes.put(shape.frame(),new ActivationModel.Shape(shape.frame(),points));
            }
            models.add(new ActivationModel(entry.context,entry.control,entry.bdd,entry.empty,shapes,parents,environments,List.copyOf(roots),List.copyOf(unwind),depth));
        }
        return models;
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
        final Region region;final int condition;final Witness child;
        Witness(Region region,int condition,Witness child){this.region=region;this.condition=condition;this.child=child;}
    }
    private boolean feasible(Region region,int condition) {return feasible(region,condition,null);}
    private boolean feasible(Region region,int condition,Slot subscriber) {
        if(condition==0)return false;
        var key=new Need(region,condition);var known=feasibleCache.get(key);if(known!=null)return known;
        var deferred=deferredCache.get(key);
        if(deferred!=null&&deferred.valid()){deferred.subscribe(subscriber);return false;}

        // First try a shortest caller path. Evaluating its concrete valuation
        // needs no BDD construction. Cyclic dispatchers usually have a short
        // witness even when saturation of all alternative predicates is costly.
        if(subscriber!=null) {
            var seen=Collections.newSetFromMap(new IdentityHashMap<Region,Boolean>());
            var coarse=new ArrayDeque<Witness>();coarse.add(new Witness(region,1,null));seen.add(region);Witness root=null;
            while(!coarse.isEmpty()) {
                var current=coarse.removeFirst();
                if(current.region.frame==null){root=current;break;}
                for(var link:current.region.incoming.entrySet())if(seen.add(link.getKey().region))
                    coarse.addLast(new Witness(link.getKey().region,link.getValue(),current));
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
        var b=region.entry.bdd;int checkpoint=b.size();
        try {
            var wanted=new IdentityHashMap<Region,Integer>();var waiting=new IdentityHashMap<Region,Integer>();var queue=new ArrayDeque<Region>();
            wanted.put(region,condition);waiting.put(region,condition);queue.add(region);boolean found=false;
            while(!queue.isEmpty()&&!found) {
                var current=queue.removeFirst();int need=waiting.remove(current);
                if(current.frame==null){found=b.and(need,current.entry.empty)!=0;continue;}
                for(var link:current.incoming.entrySet()) {
                    var parent=link.getKey().region;int before=parent.frame==null?need:b.restrict(need,parent.frame.variable(),true);
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
    private final class EntryRun {
        final ContextView context;
        final ActivationControl control;
        final BooleanConditions bdd;
        final Map<ActivationControl.Frame,Integer> environments;
        final List<Region> regions=new ArrayList<>();
        final IdentityHashMap<ActivationControl.Frame,List<Region>> byFrame=new IdentityHashMap<>();
        final Map<ActivationControl.Frame,Map<ProgramIndex.Node,S>> boundaries=new IdentityHashMap<>();
        Region root;int empty;
        EntryRun(ContextView context,ActivationModel model){this.context=context;control=model==null?new ActivationControl(session,context):model.control();bdd=model==null?new BooleanConditions():model.conditions();environments=model==null?new IdentityHashMap<>():model.environments();}
        void start() {
            empty=1;for(int i=control.variables()-1;i>=0;i--)empty=bdd.node(i,empty,0);
            root=new Region(this,null,bottom);regions.add(root);root.accept(empty);
        }
        Region region(ActivationControl.Frame frame,S input,Slot caller,int condition) {
            var candidates=byFrame.computeIfAbsent(frame,f->new ArrayList<>());
            for(var r:candidates)if(definition.equivalent(r.input,input,work))return r;
            if(!structural&&!feasible(caller.region,condition,caller))return null;
            var result=new Region(this,frame,input);candidates.add(result);regions.add(result);return result;
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
        long incomingVersion;
        final Map<ProgramIndex.Node,Integer> recursive=new IdentityHashMap<>();
        int environment;
        Region(EntryRun entry,ActivationControl.Frame frame,S input){this.entry=entry;this.frame=frame;this.input=input;}
        Slot slot(ProgramIndex.Node node){return slots.computeIfAbsent(node,n->new Slot(this,n));}
        void accept(int condition) {
            condition=entry.bdd.and(condition,entry.environments.getOrDefault(frame,1));
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
            anchors=new Partition(region.entry);outputs=new Partition(region.entry);moves=region.entry.control.moves(node,region.frame);initializations++;
        }
    }
    private final class Piece {
        final int condition;final S state;
        Piece(int condition,S state){this.condition=condition;this.state=state;}
    }
    /** Disjoint conditions; joins never mix values from different guard environments. */
    private final class Partition {
        final EntryRun entry;
        List<Piece> pieces=List.of();
        Partition(EntryRun entry){this.entry=entry;}
        boolean add(int condition,S contribution) {
            if(condition==0)return false;
            var b=entry.bdd;var next=new ArrayList<Piece>();int remaining=condition;boolean modified=false;
            for(var piece:pieces) {
                int overlap=b.and(piece.condition,condition);
                if(overlap==0){put(next,piece.condition,piece.state);continue;}
                remaining=b.difference(remaining,piece.condition);
                var joined=definition.joinInto(piece.state,contribution,work);
                if(!joined.changed()){put(next,piece.condition,piece.state);continue;}
                modified=true;put(next,b.difference(piece.condition,condition),piece.state);put(next,overlap,joined.state());
            }
            if(remaining!=0){modified=true;put(next,remaining,contribution);}
            if(modified)pieces=List.copyOf(next);
            return modified;
        }
        private void put(List<Piece> result,int condition,S value) {
            if(condition==0)return;
            for(int i=0;i<result.size();i++)if(definition.equivalent(result.get(i).state,value,work)) {
                result.set(i,new Piece(entry.bdd.or(result.get(i).condition,condition),value));return;
            }
            result.add(new Piece(condition,value));
        }
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
        S contribution=edge(source,transition,value);
        if(structural||definition.equivalent(contribution,value,work)||feasible(target,condition,caller))arrive(target.slot(destination),condition,contribution);
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
                    int active=region.frame!=null&&region.frame.variable()==move.variable()?1:b.variable(move.variable());
                    condition=b.and(condition,move.present()?active:b.not(active));
                }
                if(condition==0)continue;
                switch(move.action()) {
                    case NEXT -> arriveEdge(slot,region,move.destination(),slot.point,move.edge(),condition,output);
                    case CALL -> {
                        S input=edge(slot.point,move.edge(),output);var child=e.region(move.frame(),input,slot,condition);if(child==null)continue;
                        subscribe(slot,child,condition);
                        // A summary is a function of ancestor guards, not their enumerated valuations.
                        child.accept(1);
                        // Snapshot: a self-call may discover an additional summary while delivering one.
                        for(var summary:new ArrayList<>(child.exits.entrySet()))for(var returned:summary.getValue().pieces) {
                            int valid=region.frame==null?returned.condition:b.restrict(returned.condition,region.frame.variable(),true);
                            valid=b.and(condition,valid);if(valid==0)continue;
                            receive(slot,region,summary.getKey(),valid,returned.state);
                        }
                    }
                    case RECURSIVE -> region.recursive.merge(slot.node,condition,b::or);
                    case POP,UNWIND,ROOT -> {
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
            arriveEdge(caller,parent,exit.destination,point,exit.edge,condition,value);return;
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
