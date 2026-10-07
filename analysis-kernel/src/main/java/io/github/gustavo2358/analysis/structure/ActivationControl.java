package io.github.gustavo2358.analysis.structure;

import io.github.gustavo2358.analysis.cfg.domain.*;
import java.math.BigInteger;
import java.util.*;

/** Typed activation operations for tabulation. No concrete stack or source-language inference. */
public final class ActivationControl {
    public enum Action { NEXT, CALL, POP, UNWIND, ROOT, RECURSIVE }
    public record Move(Action action,ProgramIndex.Node destination,Frame frame,int variable,boolean present,int count,ProgramIndex.Node invalid,CfgTransition edge) { }
    public static final class Frame {
        private final LocalControlRules.Invoke invoke;
        private final int variable;
        private Frame body;
        private Frame(LocalControlRules.Invoke invoke,int variable){this.invoke=invoke;this.variable=variable;}
        public int variable(){return variable;}
    }
    private record Guard(Object unit,String key) { }
    private final ProgramIndex index;
    private final ContextView context;
    private final Map<CfgNodeId,Frame> frames=new HashMap<>();
    private final Map<Object,Integer> variables=new LinkedHashMap<>();
    private record Interface(Set<Object> ports,Set<String> routes) { }
    private record BodyKey(ProgramIndex.Node entry,Set<Object> ports,Set<String> routes) { }
    private final Map<ProgramIndex.Node,Interface> interfaces=new IdentityHashMap<>();
    private final Map<BodyKey,Frame> bodies=new HashMap<>();
    private final List<ProgramIndex.Node> unwindStarts=new ArrayList<>();
    private Interface unwindInterface;
    public ActivationControl(AnalysisSession session,ContextView context) {
        this.index=session.index();this.context=context;
        for(var rule:index.localRules.values())if(rule instanceof LocalControlRules.Invoke invoke&&invoke.operation().unit().equals(context.entry().id().unit())) {
            Object key=invoke.reentryGuard().<Object>map(g->new Guard(invoke.operation().unit(),g.activationKey())).orElse(invoke.operation());
            frames.put(invoke.source(),new Frame(invoke,variables.computeIfAbsent(key,k->variables.size())));
        }
        for(var rule:index.localRules.values())if(rule instanceof LocalControlRules.Unwind unwind&&unwind.operation().unit().equals(context.entry().id().unit())
                &&!unwind.all()&&unwind.count().signum()>0&&unwind.count().compareTo(BigInteger.valueOf(frames.size()))<=0)unwindStarts.add(index.node(unwind.destination()));
    }
    public Object operation(Frame frame){return frame.invoke.source();}
    public Frame frame(Object operation){return frames.get(operation);}
    public Frame frameAt(ProgramIndex.Node node){return frames.get(node.source().id());}
    /** Body equivalence projects only top ports/route availability that the body can
     * inspect. Concrete activation keys and return destinations stay in push bindings.
     * Discovery follows explicit parent continuations; nested bodies have their own interface. */
    public Frame body(Frame frame) {
        if(frame==null)return null;if(frame.body!=null)return frame.body;
        var entry=index.node(frame.invoke.entry());var observed=interfaces.computeIfAbsent(entry,this::interfaceAt);
        var ports=new HashSet<Object>();for(var port:frame.invoke.ports())if(observed.ports.contains(port))ports.add(port);
        var routes=new HashSet<String>();for(var route:frame.invoke.resumeRoutes().keySet())if(observed.routes.contains(route))routes.add(route);
        var key=new BodyKey(entry,Set.copyOf(ports),Set.copyOf(routes));
        var canonical=bodies.putIfAbsent(key,frame);frame.body=canonical==null?frame:canonical;return frame.body;
    }
    private Interface interfaceAt(ProgramIndex.Node entry) {
        var own=discoverInterface(List.of(entry));if(unwindStarts.isEmpty())return own;
        // A positive unwind can enter an ancestor at an arbitrary typed label,
        // outside its entry/continuation walk. Share this conservative suffix scan.
        if(unwindInterface==null)unwindInterface=discoverInterface(unwindStarts);
        var ports=new HashSet<>(own.ports);ports.addAll(unwindInterface.ports);
        var routes=new HashSet<>(own.routes);routes.addAll(unwindInterface.routes);return new Interface(Set.copyOf(ports),Set.copyOf(routes));
    }
    private Interface discoverInterface(List<ProgramIndex.Node> starts) {
        var ports=new HashSet<Object>();var routes=new HashSet<String>();
        var seen=Collections.newSetFromMap(new IdentityHashMap<ProgramIndex.Node,Boolean>());var pending=new ArrayDeque<ProgramIndex.Node>(starts);
        while(!pending.isEmpty()) {
            var node=pending.removeFirst();if(!seen.add(node))continue;
            var rule=index.localRules.get(node.source().id());
            if(rule==null){var cursor=context.ordinarySuccessors(node);while(cursor.advance())pending.addLast(cursor.target());continue;}
            switch(rule) {
                case LocalControlRules.Invoke invoke -> {
                    pending.addLast(index.node(invoke.resume()));for(var destination:invoke.resumeRoutes().values())pending.addLast(index.node(destination));
                    if(invoke.reentryGuard().isPresent())pending.addLast(index.node(invoke.reentryGuard().orElseThrow().destination()));
                }
                case LocalControlRules.Boundary boundary -> {ports.add(boundary.port());boundary.resumeKey().ifPresent(routes::add);pending.addLast(index.node(boundary.defaultDestination()));}
                case LocalControlRules.Resume resume -> resume.resumeKey().ifPresent(routes::add);
                case LocalControlRules.Unwind unwind -> {if(!unwind.all()&&unwind.count().signum()==0)pending.addLast(index.node(unwind.destination()));}
            }
        }
        return new Interface(Set.copyOf(ports),Set.copyOf(routes));
    }
    /** Resolve a formal pop event using its actual matched invocation. */
    public ProgramIndex.Node returnDestination(ProgramIndex.Node source,Frame binding) {
        Objects.requireNonNull(binding);var rule=index.localRules.get(source.source().id());
        Optional<String> route=switch(rule){case LocalControlRules.Resume resume->resume.resumeKey();case LocalControlRules.Boundary boundary->boundary.resumeKey();default->throw new IllegalArgumentException("not a pop event");};
        var target=route.isEmpty()?binding.invoke.resume():binding.invoke.resumeRoutes().get(route.orElseThrow());
        if(target==null)throw new IllegalArgumentException("formal pop requires an available route");return index.node(target);
    }
    public int variables(){return variables.size();}
    /** A structural superset, computed without constructing Boolean guards.
     * Continuations are scheduling bounds here, not executable bypass edges. */
    public Map<Frame,BitSet> possibleAncestors() {
        var parents=new IdentityHashMap<Frame,Set<Frame>>();
        var remaining=new LinkedHashSet<ProgramIndex.Node>();var roots=new LinkedHashSet<ProgramIndex.Node>();
        for(var rule:index.localRules.values())if(rule instanceof LocalControlRules.Unwind unwind
                &&unwind.operation().unit().equals(context.entry().id().unit())) {
            if(unwind.all())roots.add(index.node(unwind.destination()));
            else if(unwind.count().signum()>0&&unwind.count().compareTo(BigInteger.valueOf(frames.size()))<=0)
                remaining.add(index.node(unwind.destination()));
        }
        var tops=new ArrayList<>(frames.values());tops.add(null);
        for(var top:tops) {
            var visited=Collections.newSetFromMap(new IdentityHashMap<ProgramIndex.Node,Boolean>());
            var pending=new ArrayDeque<ProgramIndex.Node>();pending.add(entry(top));pending.addAll(remaining);
            if(top==null)pending.addAll(roots);
            while(!pending.isEmpty()) {
                var node=pending.removeFirst();if(!visited.add(node))continue;
                for(var move:moves(node,top)) {
                    if(move.action()==Action.NEXT)pending.addLast(move.destination());
                    else if(move.action()==Action.CALL) {
                        parents.computeIfAbsent(move.frame(),f->Collections.newSetFromMap(new IdentityHashMap<>())).add(top);
                        var invoke=move.frame().invoke;pending.addLast(index.node(invoke.resume()));
                        for(var destination:invoke.resumeRoutes().values())pending.addLast(index.node(destination));
                    }
                }
            }
        }
        var ordered=new ArrayList<>(frames.values());var ordinals=new IdentityHashMap<Frame,Integer>();
        for(int i=0;i<ordered.size();i++)ordinals.put(ordered.get(i),i);
        int[] keys=new int[ordered.size()];int[][] incoming=new int[ordered.size()][];
        for(int i=0;i<ordered.size();i++) {
            var frame=ordered.get(i);keys[i]=frame.variable();
            incoming[i]=parents.getOrDefault(frame,Set.of()).stream().filter(Objects::nonNull).mapToInt(ordinals::get).toArray();
        }
        var closure=CallerSupport.compute(keys,incoming);
        var possible=new IdentityHashMap<Frame,BitSet>();possible.put(null,new BitSet());
        for(int i=0;i<ordered.size();i++)possible.put(ordered.get(i),closure[i]);
        return possible;
    }
    public boolean isLocal(){return !index.localRules.isEmpty();}
    public ProgramIndex.Node entry(Frame frame){return frame==null?context.entryNode():index.node(frame.invoke.entry());}
    public boolean rootDestination(ProgramIndex.Node node){return !(node.source() instanceof CfgNode.SequenceNode);}
    public void recursive(ProgramIndex.Node node){throw new LocalControlRules.RecursiveActivation(index.localRules.get(node.source().id()).operation());}
    public List<Move> moves(ProgramIndex.Node node,Frame active) {
        var rule=index.localRules.get(node.source().id());
        if(rule==null) {
            var list=new ArrayList<Move>();var cursor=context.ordinarySuccessors(node);
            while(cursor.advance())list.add(new Move(rootDestination(cursor.target())?Action.ROOT:Action.NEXT,cursor.target(),null,-1,false,0,null,cursor.transition()));
            return list;
        }
        return switch(rule) {
            case LocalControlRules.Invoke i -> {
                var f=frames.get(i.source());var failure=i.reentryGuard().isPresent()?Action.NEXT:Action.RECURSIVE;
                var failNode=i.reentryGuard().isPresent()?index.node(i.reentryGuard().orElseThrow().destination()):node;
                yield List.of(move(Action.CALL,node,index.node(i.entry()),f,f.variable,false,0,null),move(failure,node,failNode,null,f.variable,true,0,null));
            }
            case LocalControlRules.Boundary b -> {
                if(active==null||!active.invoke.ports().contains(b.port()))
                    yield List.of(move(Action.NEXT,node,index.node(b.defaultDestination()),null,-1,false,0,null));
                var target=b.resumeKey().isEmpty()?active.invoke.resume():active.invoke.resumeRoutes().get(b.resumeKey().orElseThrow());
                yield List.of(move(target==null?Action.ROOT:Action.POP,node,index.node(target==null?b.invalidExit():target),null,-1,false,1,null));
            }
            case LocalControlRules.Resume r -> {
                var target=active==null?null:r.resumeKey().isEmpty()?active.invoke.resume():active.invoke.resumeRoutes().get(r.resumeKey().orElseThrow());
                yield List.of(move(target==null?Action.ROOT:Action.POP,node,index.node(target==null?r.invalidExit():target),null,-1,false,1,null));
            }
            case LocalControlRules.Unwind u -> {
                var dest=index.node(u.destination());var invalid=index.node(u.invalidExit());
                if(u.all())yield List.of(move(Action.ROOT,node,dest,null,-1,false,0,invalid));
                if(u.count().compareTo(BigInteger.valueOf(frames.size()))>0)yield List.of(move(Action.ROOT,node,invalid,null,-1,false,0,invalid));
                int n=u.count().intValueExact();
                yield List.of(move(n==0?Action.NEXT:Action.UNWIND,node,dest,null,-1,false,n,invalid));
            }
        };
    }
    private Move move(Action action,ProgramIndex.Node from,ProgramIndex.Node to,Frame frame,int variable,boolean present,int count,ProgramIndex.Node invalid) {
        return new Move(action,to,frame,variable,present,count,invalid,edge(from,to));
    }
    public CfgTransition edge(ProgramIndex.Node from,ProgramIndex.Node to){return new CfgTransition(from.source().id(),to.source().id(),CfgTransition.Kind.LOCAL,context.entry().id());}
}
