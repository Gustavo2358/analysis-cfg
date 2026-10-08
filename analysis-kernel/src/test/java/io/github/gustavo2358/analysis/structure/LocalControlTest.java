package io.github.gustavo2358.analysis.structure;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.air.model.Unit;
import io.github.gustavo2358.analysis.cfg.application.*;
import io.github.gustavo2358.analysis.cfg.domain.*;
import io.github.gustavo2358.analysis.cfg.extension.SemanticInterpreterRegistry;
import io.github.gustavo2358.analysis.solver.*;
import io.github.gustavo2358.analysis.query.*;
import java.math.BigInteger;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Independent AIR programs, exact execution traces and finite values; no producer normalization. */
class LocalControlTest {
    static final PublicationId P=new PublicationId("local-control-test");
    static final UnitId U=new UnitId(P,"unit");
    static final OriginId O=new OriginId(P,"origin");
    static final CompletionPortId A=new CompletionPortId(U,"a"),B=new CompletionPortId(U,"b");
    static LabelId label(String s){return new LabelId(U,s);}
    static Operations.Header h(String s) {
        var exact=new Evidence.Claim(new Scopes.UnitScope(U),Evidence.PrecisionStatus.EXACT,List.of());
        return new Operations.Header(new OperationId(U,s),O,Evidence.CoverageStatus.MODELED,new Evidence.Precision(exact,exact,exact,exact,exact),List.of());
    }
    static Envelopes.Envelope fallback() {
        return new Envelopes.Envelope(new Envelopes.MemoryEnvelope(List.of(),Scopes.NoMemory.INSTANCE,List.of(),Scopes.NoMemory.INSTANCE,List.of()),
            new Control.ControlEnvelope(List.of(),new Scopes.WithinControl(new Scopes.UnitControl(U,true,true,true,true,true,true))),
            new Envelopes.DependencyEnvelope(List.of(),Scopes.NoResources.INSTANCE));
    }
    static Sequence seq(String name,Terminator t){return new Sequence(label(name),List.of(),t,O);}
    static Sequence call(String name,String body,String resume,CompletionPortId...ports){return seq(name,new Operations.LocalInvoke(h(name),label(body),List.of(ports),label(resume),fallback()));}
    static Sequence resume(String name){return seq(name,new Operations.LocalResume(h(name),fallback()));}
    static Sequence jump(String name,String next){return seq(name,new Operations.Jump(h(name),label(next)));}
    static Sequence ret(String name){return seq(name,new Operations.Return(h(name),List.of()));}
    static Sequence boundary(String name,CompletionPortId port,String next){return seq(name,new Operations.LocalBoundary(h(name),port,label(next),fallback()));}
    static Sequence unwind(String name,BigInteger count,String next){return seq(name,new Operations.LocalUnwind(h(name),count,label(next),fallback()));}
    static Evidence.Coverage coverage(Scopes.FactScope scope){return new Evidence.Coverage(Evidence.InventoryStatus.COMPLETE,scope,List.of(),List.of());}
    static AnalysisSession session(List<Sequence> sequences,String...starts) {
        var entries=new ArrayList<Entries.Entry>();
        for(String start:starts)entries.add(new Entries.Entry(new EntryId(U,start),Optional.of(label(start)),
            new Interactions.Signature(new Interactions.ParameterInventory(List.of(),Interactions.NoRemainder.INSTANCE),new Interactions.ResultInventory(List.of(),Interactions.NoRemainder.INSTANCE),O),new Entries.EntryState(List.of(),List.of()),O));
        var unit=new Unit(U,Optional.empty(),List.of(),List.of(),entries,sequences,List.of(new Entries.CompletionPort(A,O),new Entries.CompletionPort(B,O)),Unit.BodyAvailability.AVAILABLE,Optional.empty(),coverage(new Scopes.UnitScope(U)),O);
        var caps=new ArrayList<Capabilities.Capability>();caps.add(Capabilities.LOCAL_CONTROL);
        if(sequences.stream().anyMatch(s->s.terminator() instanceof Operations.LocalInvoke i&&i.reentryGuard().isPresent()))caps.add(Capabilities.LOCAL_REENTRY_GUARD);
        if(sequences.stream().anyMatch(s->s.terminator() instanceof Operations.LocalInvoke i&&!i.resumeRoutes().isEmpty()||s.terminator() instanceof Operations.LocalResume x&&x.resumeKey().isPresent()))caps.add(Capabilities.LOCAL_RESUME_ROUTES);
        if(sequences.stream().anyMatch(s->s.terminator() instanceof Operations.LocalBoundary x&&x.resumeKey().isPresent())) {
            caps.add(Capabilities.LOCAL_BOUNDARY_ROUTES);
            if(!caps.contains(Capabilities.LOCAL_RESUME_ROUTES))caps.add(Capabilities.LOCAL_RESUME_ROUTES);
        }
        if(sequences.stream().anyMatch(s->s.terminator() instanceof Operations.LocalUnwind x&&x.all()))caps.add(Capabilities.LOCAL_UNWIND_ALL);
        var pub=new Publication(P,SemanticVersion.AIR_2_0_0,new Capabilities.Manifest(caps,List.of()),List.of(),List.of(unit),List.of(),List.of(),List.of(),List.of(new Origins.Unavailable(O,"independent fixture")),coverage(new Scopes.PublicationScope(P)),List.of(),List.of());
        var built=new CfgBuildCoordinator(SemanticInterpreterRegistry.empty()).build(pub,BuildOptions.defaults());
        assertEquals(CfgBuildResult.Status.CFG_BUILT,built.status(),built.toString());
        var admission=AnalysisSession.open(built,pub,ProjectionPolicy.KNOWN_SUBSET,entries);
        assertEquals(AnalysisSession.Status.ACCEPTED,admission.status(),admission.reason());return admission.session().orElseThrow();
    }
    static List<String> trace(ContextView context) {
        var result=new ArrayList<String>();var point=context.initialPoint();var seen=new HashSet<ContextView.Point>();
        while(seen.add(point)) {
            if(point.node().source() instanceof CfgNode.SequenceNode n)result.add(n.label().localId());
            else if(point.node().source() instanceof CfgNode.OutcomeExit n)result.add(((Control.Exceptional)n.outcome()).tag());
            var edges=context.successors(point);if(!edges.advance())break;
            point=edges.target();assertFalse(edges.advance(),"this oracle describes deterministic programs");
        }
        return List.copyOf(result);
    }
    @Test void callersReturnOnlyToTheirOwnContinuation() {
        var s=session(List.of(call("a","body","after-a"),jump("after-a","b"),call("b","body","after-b"),ret("after-b"),resume("body")),"a");
        var c=s.contexts().iterator().next();
        assertEquals(List.of("a","body","after-a","b","body","after-b"),trace(c));
        assertThrows(IllegalArgumentException.class,()->c.successors(c.entryNode()));
    }
    @Test void boundaryMatchesOnlyTopFrameAndEmptyStackUsesDefault() {
        var seqs=List.of(call("a","b","done",A),call("b","boundary-a","resume-outer",B),boundary("boundary-a",A,"boundary-b"),boundary("boundary-b",B,"bad"),boundary("resume-outer",A,"bad"),ret("done"),ret("bad"));
        var s=session(seqs,"a","boundary-a");
        assertEquals(List.of("a","b","boundary-a","boundary-b","resume-outer","done"),trace(s.context(new EntryId(U,"a"))));
        assertEquals(List.of("boundary-a","boundary-b","bad"),trace(s.context(new EntryId(U,"boundary-a"))));
    }
    @Test void selectedBoundaryUsesOnlyMatchingTopFrameRouteAndDefaultsOtherwise() {
        var outer=seq("outer",new Operations.LocalInvoke(h("outer"),label("inner"),List.of(A),label("bad"),fallback(),Optional.empty(),List.of(new Operations.ResumeRoute("state",label("done")))));
        var inner=seq("inner",new Operations.LocalInvoke(h("inner"),label("boundary-a"),List.of(B),label("bad"),fallback(),Optional.empty(),List.of(new Operations.ResumeRoute("state",label("after-inner")))));
        var boundaryA=seq("boundary-a",new Operations.LocalBoundary(h("boundary-a"),A,label("boundary-b"),fallback(),Optional.of("missing")));
        var boundaryB=seq("boundary-b",new Operations.LocalBoundary(h("boundary-b"),B,label("ordinary"),fallback(),Optional.of("state")));
        var finish=seq("after-inner",new Operations.LocalBoundary(h("after-inner"),A,label("ordinary"),fallback(),Optional.of("state")));
        var s=session(List.of(outer,inner,boundaryA,boundaryB,finish,ret("done"),ret("ordinary"),ret("bad")),"outer","boundary-a");
        assertEquals(List.of("outer","inner","boundary-a","boundary-b","after-inner","done"),trace(s.context(new EntryId(U,"outer"))));
        assertEquals(List.of("boundary-a","boundary-b","ordinary"),trace(s.context(new EntryId(U,"boundary-a"))));
        var bad=seq("boundary-b",new Operations.LocalBoundary(h("boundary-b"),B,label("ordinary"),fallback(),Optional.of("missing")));
        var invalid=session(List.of(outer,inner,boundaryA,bad,finish,ret("done"),ret("ordinary"),ret("bad")),"outer");
        assertEquals(List.of("outer","inner","boundary-a","boundary-b","invalid_local_return"),trace(invalid.contexts().iterator().next()));
    }
    @Test void emptyResumeAndExcessiveUnwindAreExceptional() {
        var s=session(List.of(resume("empty"),unwind("huge",BigInteger.ONE.shiftLeft(100),"bad"),ret("bad")),"empty","huge");
        assertEquals(List.of("empty","invalid_local_return"),trace(s.context(new EntryId(U,"empty"))));
        assertEquals(List.of("huge","invalid_local_unwind"),trace(s.context(new EntryId(U,"huge"))));
    }
    @Test void jumpAndZeroUnwindPreserveFrameAndExactUnwindPopsOnlyItsCount() {
        var s=session(List.of(call("a","b","done"),call("b","jump","bad"),jump("jump","zero"),unwind("zero",BigInteger.ZERO,"one"),unwind("one",BigInteger.ONE,"outer"),resume("outer"),ret("done"),ret("bad")),"a");
        assertEquals(List.of("a","b","jump","zero","one","outer","done"),trace(s.contexts().iterator().next()));
    }
    @Test void returnDiscardsPendingFramesAndCannotResumeAnotherEntry() {
        var s=session(List.of(call("a","body","bad"),ret("body"),ret("bad"),resume("other")),"a","other");
        assertEquals(List.of("a","body"),trace(s.context(new EntryId(U,"a"))));
        assertEquals(List.of("other","invalid_local_return"),trace(s.context(new EntryId(U,"other"))));
    }
    @Test void loopReusesCompletedFramesAndRecursiveActivationRefusesWithoutTruncation() {
        var loop=session(List.of(call("a","body","a"),resume("body")),"a");
        assertEquals(List.of("a","body"),trace(loop.contexts().iterator().next()));
        var recursive=session(List.of(call("a","a","done"),ret("done")),"a");
        assertThrows(LocalControlRules.RecursiveActivation.class,()->trace(recursive.contexts().iterator().next()));
    }
    @Test void dataflowKeepsEachCallerRootSeparateInsideSharedBody() {
        var s=session(List.of(call("a","body","after-a"),jump("after-a","b"),call("b","body","after-b"),ret("after-b"),resume("body")),"a");
        var c=s.contexts().iterator().next();
        AnalysisDefinition<Set<String>> d=new AnalysisDefinition<>() {
            public Direction direction(){return Direction.FORWARD;}
            public Set<String> bottom(){return Set.of();}
            public Iterable<Boundary<Set<String>>> boundaries(AnalysisSession ignored){return List.of(new Boundary<>(c,c.entryNode(),Set.of("initial")));}
            public Join<Set<String>> joinInto(Set<String>a,Set<String>b,DomainWork w){var union=new HashSet<>(a);union.addAll(b);return new Join<>(Set.copyOf(union),!union.equals(a));}
            public boolean equivalent(Set<String>a,Set<String>b,DomainWork w){return a.equals(b);}
            public Set<String> transferBlock(AnalysisPoint p,Set<String>state,DomainWork w){
                if(state.isEmpty())return state;
                if(p.node()==s.index().sequence(label("a")))return Set.of("A");
                if(p.node()==s.index().sequence(label("b")))return Set.of("B");
                return state;
            }
            public Set<String> transferEdge(AnalysisPoint p,CfgTransition e,Set<String>state,DomainWork w){return state;}
        };
        var result=DataflowSolver.solve(s,d);
        assertEquals(Set.of("A"),result.in(c,s.index().sequence(label("after-a"))));
        assertEquals(Set.of("B"),result.in(c,s.index().sequence(label("after-b"))));
        assertEquals(Set.of(Set.of("A"),Set.of("B")),new HashSet<>(result.states(c,s.index().sequence(label("body")),true)));
        assertThrows(IllegalArgumentException.class,()->result.in(c,s.index().sequence(label("body"))));
    }
    @Test void observationReplaysEachFrameBeforeJoiningNonDistributiveTransfers() {
        var body=new Sequence(label("body"),List.of(new Operations.Nop(h("combine"))),new Operations.LocalResume(h("body-end"),fallback()),O);
        var s=session(List.of(call("a","body","after-a"),jump("after-a","b"),call("b","body","after-b"),ret("after-b"),body),"a");
        var c=s.contexts().iterator().next();
        java.util.function.BinaryOperator<Set<String>> union=(a,b)->{var x=new HashSet<>(a);x.addAll(b);return Set.copyOf(x);};
        // Finite acyclic fixture: this monotone transfer is intentionally not distributive.
        java.util.function.Function<Set<String>,Set<String>> pairs=a->{var x=new HashSet<String>();for(var l:a)for(var r:a)x.add(l+r);return Set.copyOf(x);};
        AnalysisDefinition<Set<String>> d=new AnalysisDefinition<>() {
            public Direction direction(){return Direction.FORWARD;}
            public Set<String> bottom(){return Set.of();}
            public Iterable<Boundary<Set<String>>> boundaries(AnalysisSession ignored){return List.of(new Boundary<>(c,c.entryNode(),Set.of("initial")));}
            public Join<Set<String>> joinInto(Set<String>a,Set<String>b,DomainWork w){var x=union.apply(a,b);return new Join<>(x,!x.equals(a));}
            public boolean equivalent(Set<String>a,Set<String>b,DomainWork w){return a.equals(b);}
            public Set<String> transferBlock(AnalysisPoint p,Set<String>a,DomainWork w){
                if(a.isEmpty())return a;
                if(p.node()==s.index().sequence(label("a")))return Set.of("A");
                if(p.node()==s.index().sequence(label("b")))return Set.of("B");
                return p.node()==s.index().sequence(label("body"))?pairs.apply(a):a;
            }
            public Set<String> transferEdge(AnalysisPoint p,CfgTransition e,Set<String>a,DomainWork w){return a;}
        };
        var stable=DataflowSolver.solve(s,d);
        var q=new PointQuery<>(ProgramPoint.after(c.entry().id(),new OperationId(U,"combine")),"value");
        var batch=BatchReplayer.materialize(s,stable,Direction.FORWARD,Set.<String>of(),List.of(q),Comparator.naturalOrder(),
            (a,op)->pairs.apply(a),new BatchReplayer.Projection<Set<String>,String,Set<String>>() {
                public boolean supports(PointQuery<String> query){return true;}
                public Set<String> mergeStates(Set<String>a,Set<String>b){return union.apply(a,b);}
                public Set<String> project(PointQuery<String> query,Set<String>a){return a;}
            });
        assertEquals(ObservationBatch.Status.COMPLETE,batch.status());
        assertEquals(Set.of("AA","BB"),batch.observations().getFirst().value(),"no AB or BA from mixing callers before replay");
    }
    @Test void traversalHandleCannotMoveToAnotherActivationEntry() {
        var s=session(List.of(call("a","body","done"),resume("body"),ret("done"),ret("other")),"a","other");
        var a=s.context(new EntryId(U,"a"));var other=s.context(new EntryId(U,"other"));
        assertThrows(IllegalArgumentException.class,()->other.successors(a.initialPoint()));
    }
}
