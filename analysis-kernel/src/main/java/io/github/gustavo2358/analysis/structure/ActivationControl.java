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
            case LocalControlRules.Boundary b -> active!=null&&active.invoke.ports().contains(b.port())
                ? List.of(move(Action.POP,node,index.node(active.invoke.resume()),null,-1,false,1,null))
                : List.of(move(Action.NEXT,node,index.node(b.defaultDestination()),null,-1,false,0,null));
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
