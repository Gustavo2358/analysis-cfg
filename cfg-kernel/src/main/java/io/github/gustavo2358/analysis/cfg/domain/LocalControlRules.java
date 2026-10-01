package io.github.gustavo2358.analysis.cfg.domain;

import io.github.gustavo2358.air.model.Control;
import io.github.gustavo2358.air.model.Operations;
import io.github.gustavo2358.air.model.Terminator;
import io.github.gustavo2358.air.model.Ids.OperationId;
import io.github.gustavo2358.air.model.Ids.LabelId;
import io.github.gustavo2358.air.model.Ids.CompletionPortId;
import java.math.BigInteger;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** AIR 05.7 rules over shared CFG nodes. Dynamic returns never become ordinary graph edges. */
public final class LocalControlRules {
    private LocalControlRules() { }
    public sealed interface Rule permits Invoke, Boundary, Resume, Unwind {
        CfgNodeId source(); OperationId operation();
    }
    public record Invoke(CfgNodeId source,OperationId operation,CfgNodeId entry,List<CompletionPortId> ports,CfgNodeId resume) implements Rule {
        public Invoke { ports=List.copyOf(ports); }
    }
    public record Boundary(CfgNodeId source,OperationId operation,CompletionPortId port,CfgNodeId defaultDestination) implements Rule { }
    public record Resume(CfgNodeId source,OperationId operation,CfgNodeId invalidExit) implements Rule { }
    public record Unwind(CfgNodeId source,OperationId operation,BigInteger count,CfgNodeId destination,CfgNodeId invalidExit) implements Rule { }
    public static boolean local(Terminator term) {
        return term instanceof Operations.LocalInvoke || term instanceof Operations.LocalBoundary
            || term instanceof Operations.LocalResume || term instanceof Operations.LocalUnwind;
    }
    public static Control.Exceptional invalid(Terminator term) {
        return term instanceof Operations.LocalResume ? new Control.Exceptional("invalid_local_return",Control.Propagate.INSTANCE)
            : term instanceof Operations.LocalUnwind ? new Control.Exceptional("invalid_local_unwind",Control.Propagate.INSTANCE) : null;
    }
    // Constructed only from the graph's inventoried AIR nodes and typed label identities.
    static Map<CfgNodeId,Rule> project(List<CfgNode> nodes) {
        var labels=new HashMap<LabelId,CfgNodeId>(); var invalid=new HashMap<OperationId,CfgNodeId>();
        for(var n:nodes) {
            if(n instanceof CfgNode.SequenceNode s) labels.put(s.source().label(),s.id());
            if(n instanceof CfgNode.OutcomeExit e && local(e.source())) invalid.put(e.source().header().id(),e.id());
        }
        var result=new LinkedHashMap<CfgNodeId,Rule>();
        for(var n:nodes) if(n instanceof CfgNode.SequenceNode s) {
            var t=s.source().terminator(); var id=t.header().id();
            Rule rule=switch(t) {
                case Operations.LocalInvoke i -> new Invoke(s.id(),id,required(labels.get(i.entry())),i.completionPorts(),required(labels.get(i.resume())));
                case Operations.LocalBoundary b -> new Boundary(s.id(),id,b.port(),required(labels.get(b.defaultDestination())));
                case Operations.LocalResume ignored -> new Resume(s.id(),id,required(invalid.get(id)));
                case Operations.LocalUnwind u -> new Unwind(s.id(),id,u.count(),required(labels.get(u.destination())),required(invalid.get(id)));
                default -> null;
            };
            if(rule!=null)result.put(n.id(),rule);
        }
        return Collections.unmodifiableMap(result);
    }
    private static <T> T required(T value) {
        if(value==null)throw new IllegalArgumentException("local control requires its inventoried destinations");
        return value;
    }
    /** Persistent stack; iterative equality/hash avoids Java stack overflow on deep COBOL calls. */
    public static final class Stack {
        public static final Stack EMPTY=new Stack();
        private final Invoke top;
        private final Stack parent;
        private final int depth,hash;
        private Stack(){top=null;parent=null;depth=0;hash=1;}
        private Stack(Invoke top,Stack parent){this.top=top;this.parent=parent;depth=Math.incrementExact(parent.depth);hash=31*parent.hash+top.hashCode();}
        public int depth(){return depth;}
        private Stack push(Invoke call) {
            for(var cursor=this;cursor.top!=null;cursor=cursor.parent)
                if(cursor.top.operation().equals(call.operation()))throw new RecursiveActivation(call.operation());
            return new Stack(call,this);
        }
        @Override public int hashCode(){return hash;}
        @Override public boolean equals(Object other) {
            if(this==other)return true;
            if(!(other instanceof Stack s)||hash!=s.hash||depth!=s.depth)return false;
            var a=this;var b=s;
            while(a!=b&&a.top!=null){if(!a.top.equals(b.top))return false;a=a.parent;b=b.parent;}
            return true;
        }
    }
    /** Finite-context analysis cannot silently truncate recursive local activations. */
    public static final class RecursiveActivation extends IllegalArgumentException {
        private static final long serialVersionUID=1L;
        public RecursiveActivation(OperationId operation){super("recursive local activation is outside the finite-context execution domain: "+operation);}
    }
    public record Step(CfgNodeId destination,Stack stack) { }
    public static Step step(Rule rule,Stack stack) {
        Objects.requireNonNull(rule);Objects.requireNonNull(stack);
        return switch(rule) {
            case Invoke i -> new Step(i.entry(),stack.push(i));
            case Boundary b -> stack.top!=null&&stack.top.ports().contains(b.port())
                ? new Step(stack.top.resume(),stack.parent):new Step(b.defaultDestination(),stack);
            case Resume r -> stack.top==null?new Step(r.invalidExit(),Stack.EMPTY):new Step(stack.top.resume(),stack.parent);
            case Unwind u -> {
                if(u.count().compareTo(BigInteger.valueOf(stack.depth))>0)yield new Step(u.invalidExit(),Stack.EMPTY);
                var remaining=stack;for(int i=u.count().intValueExact();i>0;i--)remaining=remaining.parent;
                yield new Step(u.destination(),remaining);
            }
        };
    }
}
