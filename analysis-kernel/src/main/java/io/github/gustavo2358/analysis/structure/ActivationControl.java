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
        private Frame(LocalControlRules.Invoke invoke,int variable){this.invoke=invoke;this.variable=variable;}
        public int variable(){return variable;}
    }
    private record Guard(Object unit,String key) { }
    private final ProgramIndex index;
    private final ContextView context;
    private final Map<CfgNodeId,Frame> frames=new HashMap<>();
    private final Map<Object,Integer> variables=new LinkedHashMap<>();
    public ActivationControl(AnalysisSession session,ContextView context) {
        this.index=session.index();this.context=context;
        for(var rule:index.localRules.values())if(rule instanceof LocalControlRules.Invoke invoke&&invoke.operation().unit().equals(context.entry().id().unit())) {
            Object key=invoke.reentryGuard().<Object>map(g->new Guard(invoke.operation().unit(),g.activationKey())).orElse(invoke.operation());
            frames.put(invoke.source(),new Frame(invoke,variables.computeIfAbsent(key,k->variables.size())));
        }
    }
    public Object operation(Frame frame){return frame.invoke.source();}
    public Frame frame(Object operation){return frames.get(operation);}
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
