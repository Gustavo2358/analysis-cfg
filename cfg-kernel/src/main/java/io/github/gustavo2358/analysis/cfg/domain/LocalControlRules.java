package io.github.gustavo2358.analysis.cfg.domain;

import io.github.gustavo2358.air.model.Control;
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
import java.util.Optional;

/** AIR 05.7 rules over shared CFG nodes. Dynamic returns never become ordinary graph edges. */
public final class LocalControlRules {
    private LocalControlRules() { }
    public sealed interface Rule permits Invoke, Boundary, Resume, Unwind {
        CfgNodeId source(); OperationId operation();
    }
    public record ReentryGuard(String activationKey,CfgNodeId destination) {
        public ReentryGuard { Objects.requireNonNull(activationKey);Objects.requireNonNull(destination); }
    }
    public record Invoke(CfgNodeId source,OperationId operation,CfgNodeId entry,List<CompletionPortId> ports,CfgNodeId resume,Optional<ReentryGuard> reentryGuard,Map<String,CfgNodeId> resumeRoutes) implements Rule {
        public Invoke { ports=List.copyOf(ports);reentryGuard=Objects.requireNonNull(reentryGuard);resumeRoutes=Map.copyOf(resumeRoutes); }
        public Invoke(CfgNodeId source,OperationId operation,CfgNodeId entry,List<CompletionPortId> ports,CfgNodeId resume,Optional<ReentryGuard> reentryGuard) {
            this(source,operation,entry,ports,resume,reentryGuard,Map.of());
        }
        public Invoke(CfgNodeId source,OperationId operation,CfgNodeId entry,List<CompletionPortId> ports,CfgNodeId resume) {
            this(source,operation,entry,ports,resume,Optional.empty(),Map.of());
        }
    }
    public record Boundary(CfgNodeId source,OperationId operation,CompletionPortId port,CfgNodeId defaultDestination,Optional<String> resumeKey,CfgNodeId invalidExit) implements Rule {
        public Boundary {resumeKey=Objects.requireNonNull(resumeKey);if(resumeKey.isPresent())Objects.requireNonNull(invalidExit);}
        public Boundary(CfgNodeId source,OperationId operation,CompletionPortId port,CfgNodeId defaultDestination) {this(source,operation,port,defaultDestination,Optional.empty(),null);}
    }
    public record Resume(CfgNodeId source,OperationId operation,CfgNodeId invalidExit,Optional<String> resumeKey) implements Rule {
        public Resume {resumeKey=Objects.requireNonNull(resumeKey);}
        public Resume(CfgNodeId source,OperationId operation,CfgNodeId invalidExit){this(source,operation,invalidExit,Optional.empty());}
    }
    public record Unwind(CfgNodeId source,OperationId operation,BigInteger count,CfgNodeId destination,CfgNodeId invalidExit,boolean all) implements Rule {
        public Unwind(CfgNodeId source,OperationId operation,BigInteger count,CfgNodeId destination,CfgNodeId invalidExit){this(source,operation,count,destination,invalidExit,false);}
    }
    public static boolean local(CfgControl control) {
        return CfgControl.local(control);
    }
    public static boolean local(io.github.gustavo2358.air.model.Terminator term) {
        return local(CfgControl.from(term));
    }
    public static Control.Exceptional invalid(CfgControl control) {
        return control instanceof CfgControl.LocalResume || control instanceof CfgControl.LocalBoundary b&&b.resumeKey().isPresent() ? new Control.Exceptional("invalid_local_return",Control.Propagate.INSTANCE)
            : control instanceof CfgControl.LocalUnwind ? new Control.Exceptional("invalid_local_unwind",Control.Propagate.INSTANCE) : null;
    }
    public static Control.Exceptional invalid(io.github.gustavo2358.air.model.Terminator term) {
        return invalid(CfgControl.from(term));
    }
    // Constructed only from the graph's inventoried AIR nodes and typed label identities.
    static Map<CfgNodeId,Rule> project(List<CfgNode> nodes) {
        var labels=new HashMap<LabelId,CfgNodeId>(); var invalid=new HashMap<OperationId,CfgNodeId>();
        for(var n:nodes) {
            if(n instanceof CfgNode.SequenceNode s) labels.put(s.label(),s.id());
            if(n instanceof CfgNode.OutcomeExit e) invalid.put(e.operation(),e.id());
        }
        var result=new LinkedHashMap<CfgNodeId,Rule>();
        for(var n:nodes) if(n instanceof CfgNode.SequenceNode s) {
            var control=s.control(); var id=control.operation();
            Rule rule=switch(control) {
                case CfgControl.LocalInvoke i -> new Invoke(s.id(),id,required(labels.get(i.entry())),i.completionPorts(),required(labels.get(i.resume())),
                    i.reentryGuard().map(g->new ReentryGuard(g.activationKey(),required(labels.get(g.destination())))),resumeRoutes(i,labels));
                case CfgControl.LocalBoundary b -> new Boundary(s.id(),id,b.port(),required(labels.get(b.defaultDestination())),b.resumeKey(),b.resumeKey().isPresent()?required(invalid.get(id)):null);
                case CfgControl.LocalResume r -> new Resume(s.id(),id,required(invalid.get(id)),r.resumeKey());
                case CfgControl.LocalUnwind u -> new Unwind(s.id(),id,u.count(),required(labels.get(u.destination())),required(invalid.get(id)),u.all());
                default -> null;
            };
            if(rule!=null)result.put(n.id(),rule);
        }
        return Collections.unmodifiableMap(result);
    }
    private static Map<String,CfgNodeId> resumeRoutes(CfgControl.LocalInvoke invoke,Map<LabelId,CfgNodeId> labels) {
        var routes=new LinkedHashMap<String,CfgNodeId>();
        for(var route:invoke.resumeRoutes())routes.put(route.key(),required(labels.get(route.destination())));
        return routes;
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
        private boolean guarded(Invoke call) {
            if(call.reentryGuard().isEmpty())return false;
            var key=call.reentryGuard().orElseThrow().activationKey();
            for(var cursor=this;cursor.top!=null;cursor=cursor.parent) {
                var frame=cursor.top;
                if(frame.operation().unit().equals(call.operation().unit())
                        &&frame.reentryGuard().isPresent()&&frame.reentryGuard().orElseThrow().activationKey().equals(key))return true;
            }
            return false;
        }
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
            case Invoke i -> stack.guarded(i) ? new Step(i.reentryGuard().orElseThrow().destination(),stack) : new Step(i.entry(),stack.push(i));
            case Boundary b -> {
                if(stack.top==null||!stack.top.ports().contains(b.port()))yield new Step(b.defaultDestination(),stack);
                var destination=b.resumeKey().isEmpty()?stack.top.resume():stack.top.resumeRoutes().get(b.resumeKey().orElseThrow());
                yield destination==null?new Step(b.invalidExit(),Stack.EMPTY):new Step(destination,stack.parent);
            }
            case Resume r -> {
                if(stack.top==null)yield new Step(r.invalidExit(),Stack.EMPTY);
                var destination=r.resumeKey().isEmpty()?stack.top.resume():stack.top.resumeRoutes().get(r.resumeKey().orElseThrow());
                yield destination==null?new Step(r.invalidExit(),Stack.EMPTY):new Step(destination,stack.parent);
            }
            case Unwind u -> {
                if(u.all())yield new Step(u.destination(),Stack.EMPTY);
                if(u.count().compareTo(BigInteger.valueOf(stack.depth))>0)yield new Step(u.invalidExit(),Stack.EMPTY);
                var remaining=stack;for(int i=u.count().intValueExact();i>0;i--)remaining=remaining.parent;
                yield new Step(u.destination(),remaining);
            }
        };
    }
}
