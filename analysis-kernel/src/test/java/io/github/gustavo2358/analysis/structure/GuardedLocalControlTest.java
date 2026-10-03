package io.github.gustavo2358.analysis.structure;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.analysis.solver.*;
import io.github.gustavo2358.analysis.cfg.domain.CfgTransition;
import java.util.*;
import java.math.BigInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Hand-written execution traces; no producer or graph traversal determines expected results. */
class GuardedLocalControlTest extends LocalControlTest {
    static Sequence guarded(String name,String body,String resume,String key,String rejected) {
        return seq(name,new Operations.LocalInvoke(h(name),label(body),List.of(),label(resume),fallback(),
            Optional.of(new Operations.ReentryGuard(key,label(rejected)))));
    }
    @Test void logicalKeyFindsAnOlderFrameAcrossDistinctOperations() {
        var s=session(List.of(guarded("a","b","done","A","bad"),guarded("b","again","resume-a","B","bad"),
            guarded("again","bad","bad","A","reentry"),resume("reentry"),resume("resume-a"),ret("done"),ret("bad")),"a");
        assertEquals(List.of("a","b","again","reentry","resume-a","done"),trace(s.contexts().iterator().next()));
    }
    @Test void returningReleasesGuardAndRetainsEachCallersContinuation() {
        var s=session(List.of(guarded("a","body","b","same","bad"),guarded("b","body","done","same","bad"),
            resume("body"),ret("done"),ret("bad")),"a");
        assertEquals(List.of("a","body","b","body","done"),trace(s.contexts().iterator().next()));
    }
    @Test void unwindReleasesOnlyRemovedKeys() {
        var s=session(List.of(guarded("a","b","done","A","bad"),guarded("b","drop","bad","B","bad"),
            unwind("drop",BigInteger.ONE,"new-b"),guarded("new-b","again-a","after-b","B","bad"),
            guarded("again-a","bad","bad","A","reentry"),resume("reentry"),resume("after-b"),ret("done"),ret("bad")),"a");
        assertEquals(List.of("a","b","drop","new-b","again-a","reentry","after-b","done"),trace(s.contexts().iterator().next()));
    }
    @Test void selfGuardRunsBeforeRecursiveFrameRejection() {
        var s=session(List.of(guarded("a","a","done","A","reentry"),resume("reentry"),ret("done")),"a");
        assertEquals(List.of("a","a","reentry","done"),trace(s.contexts().iterator().next()));
    }
    @Test void dataflowPreservesValuesAcrossGuardedReentryAndMatchedReturns() {
        var s=session(List.of(guarded("a","body","after-a","same","bad"),jump("after-a","b"),
            guarded("b","body","after-b","same","bad"),guarded("body","bad","bad","same","end"),resume("end"),ret("after-b"),ret("bad")),"a");
        var c=s.contexts().iterator().next();
        AnalysisDefinition<Set<String>> definition=new AnalysisDefinition<>() {
            public Direction direction(){return Direction.FORWARD;}
            public Set<String> bottom(){return Set.of();}
            public Iterable<Boundary<Set<String>>> boundaries(AnalysisSession ignored){return List.of(new Boundary<>(c,c.entryNode(),Set.of("initial")));}
            public Join<Set<String>> joinInto(Set<String>a,Set<String>b,DomainWork w){var union=new HashSet<>(a);union.addAll(b);return new Join<>(Set.copyOf(union),!union.equals(a));}
            public boolean equivalent(Set<String>a,Set<String>b,DomainWork w){return a.equals(b);}
            public Set<String> transferBlock(AnalysisPoint point,Set<String> state,DomainWork w){
                if(state.isEmpty())return state;
                if(point.node()==s.index().sequence(label("a")))return Set.of("A");
                if(point.node()==s.index().sequence(label("b")))return Set.of("B");
                return state;
            }
            public Set<String> transferEdge(AnalysisPoint point,CfgTransition edge,Set<String> state,DomainWork w){return state;}
        };
        var result=DataflowSolver.solve(s,definition);
        assertEquals(Set.of("A"),result.in(c,s.index().sequence(label("after-a"))));
        assertEquals(Set.of("B"),result.in(c,s.index().sequence(label("after-b"))));
        assertEquals(Set.of(Set.of("A"),Set.of("B")),new HashSet<>(result.states(c,s.index().sequence(label("end")),true)));
        assertTrue(result.states(c,s.index().sequence(label("bad")),true).isEmpty());
    }
    static Sequence routed(String name,String body,String ordinary,String key,String destination) {
        return seq(name,new Operations.LocalInvoke(h(name),label(body),List.of(),label(ordinary),fallback(),Optional.empty(),
            List.of(new Operations.ResumeRoute(key,label(destination)))));
    }
    static Sequence selected(String name,String key){return seq(name,new Operations.LocalResume(h(name),fallback(),Optional.of(key)));}
    @Test void selectedReturnUsesOnlyItsOwnFrameRoute() {
        var s=session(List.of(routed("a","body","bad","K","b"),routed("b","body","bad","K","done"),
            selected("body","K"),ret("done"),ret("bad")),"a");
        assertEquals(List.of("a","body","b","body","done"),trace(s.contexts().iterator().next()));
    }
    @Test void missingKeyDoesNotSearchBelowTheTopOrUseOrdinaryResume() {
        var s=session(List.of(routed("a","b","bad","K","bad"),routed("b","body","bad","other","bad"),
            selected("body","K"),ret("bad")),"a");
        assertEquals(List.of("a","b","body","invalid_local_return"),trace(s.contexts().iterator().next()));
    }
    @Test void unwindAllDiscardsEveryGuardAndWorksAtEmptyStack() {
        var clear=seq("clear",new Operations.LocalUnwind(h("clear"),BigInteger.ZERO,label("again"),fallback(),true));
        var s=session(List.of(guarded("a","b","bad","A","bad"),guarded("b","clear","bad","B","bad"),clear,
            guarded("again","body","done","A","bad"),resume("body"),ret("done"),ret("bad")),"a","clear");
        assertEquals(List.of("a","b","clear","again","body","done"),trace(s.context(new io.github.gustavo2358.air.model.Ids.EntryId(U,"a"))));
        assertEquals(List.of("clear","again","body","done"),trace(s.context(new io.github.gustavo2358.air.model.Ids.EntryId(U,"clear"))));
    }
    @Test void selectedReturnsPreserveValuesWithoutOrdinaryBypass() {
        var s=session(List.of(routed("a","body","bad","K","b"),routed("b","body","bad","K","after-b"),
            selected("body","K"),ret("after-b"),ret("bad")),"a");
        var c=s.contexts().iterator().next();
        AnalysisDefinition<Set<String>> definition=new AnalysisDefinition<>() {
            public Direction direction(){return Direction.FORWARD;}
            public Set<String> bottom(){return Set.of();}
            public Iterable<Boundary<Set<String>>> boundaries(AnalysisSession ignored){return List.of(new Boundary<>(c,c.entryNode(),Set.of("initial")));}
            public Join<Set<String>> joinInto(Set<String>a,Set<String>b,DomainWork w){var union=new HashSet<>(a);union.addAll(b);return new Join<>(Set.copyOf(union),!union.equals(a));}
            public boolean equivalent(Set<String>a,Set<String>b,DomainWork w){return a.equals(b);}
            public Set<String> transferBlock(AnalysisPoint point,Set<String> state,DomainWork w){
                if(state.isEmpty())return state;
                if(point.node()==s.index().sequence(label("a")))return Set.of("A");
                if(point.node()==s.index().sequence(label("b")))return Set.of("B");
                return state;
            }
            public Set<String> transferEdge(AnalysisPoint point,CfgTransition edge,Set<String> state,DomainWork w){return state;}
        };
        var result=DataflowSolver.solve(s,definition);
        assertEquals(Set.of("A"),result.in(c,s.index().sequence(label("b"))));
        assertEquals(Set.of("B"),result.in(c,s.index().sequence(label("after-b"))));
        assertEquals(Set.of(Set.of("A"),Set.of("B")),new HashSet<>(result.states(c,s.index().sequence(label("body")),true)));
        assertTrue(result.states(c,s.index().sequence(label("bad")),true).isEmpty());
    }
    public static void main(String[] args) {
        var t=new GuardedLocalControlTest();t.selectedReturnsPreserveValuesWithoutOrdinaryBypass();t.selectedReturnUsesOnlyItsOwnFrameRoute();t.missingKeyDoesNotSearchBelowTheTopOrUseOrdinaryResume();t.unwindAllDiscardsEveryGuardAndWorksAtEmptyStack();t.logicalKeyFindsAnOlderFrameAcrossDistinctOperations();
        t.returningReleasesGuardAndRetainsEachCallersContinuation();t.unwindReleasesOnlyRemovedKeys();t.selfGuardRunsBeforeRecursiveFrameRejection();t.dataflowPreservesValuesAcrossGuardedReentryAndMatchedReturns();
        System.out.println("LOCAL_CONTROL_EXTENSIONS=PASS guard/routes/unwind-all traces and two dataflow programs");
    }
}
