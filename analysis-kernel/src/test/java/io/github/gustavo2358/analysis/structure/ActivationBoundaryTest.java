package io.github.gustavo2358.analysis.structure;

import io.github.gustavo2358.analysis.cfg.domain.*;
import io.github.gustavo2358.analysis.solver.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static io.github.gustavo2358.analysis.structure.LocalControlTest.*;

class ActivationBoundaryTest {
    static AnalysisDefinition<Integer> definition(AnalysisSession s,Direction direction,String name) {
        var c=s.contexts().iterator().next();
        return new AnalysisDefinition<>() {
            public Direction direction(){return direction;}
            public Integer bottom(){return 0;}
            public Iterable<Boundary<Integer>> boundaries(AnalysisSession ignored){return List.of(new Boundary<>(c,s.index().sequence(label(name)),1),new Boundary<>(c,s.index().sequence(label(name)),2));}
            public Join<Integer> joinInto(Integer a,Integer b,DomainWork w){return new Join<>(a|b,(a|b)!=a);}
            public boolean equivalent(Integer a,Integer b,DomainWork w){return a.equals(b);}
            public Integer transferBlock(AnalysisPoint p,Integer a,DomainWork w){
                return a==3?7:a;
            }
            public Integer transferEdge(AnalysisPoint p,CfgTransition e,Integer a,DomainWork w){return a;}
        };
    }
    static void compare(AnalysisSession s,AnalysisDefinition<Integer> d) {
        var expected=ExplicitActivationOracle.solve(s,d);var actual=DataflowSolver.solve(s,d);
        for(var c:s.contexts())for(var node:s.index().unit(c.entry().id().unit()).sequences())for(boolean atIn:new boolean[]{true,false}) {
            var n=s.index().sequence(node.label());
            var states=expected.stream().filter(x->x.context()==c&&x.node()==n).map(x->atIn?x.in():x.out()).toList();
            assertEquals(new HashSet<>(states),new HashSet<>(actual.states(c,n,atIn)),node.label().toString()+" "+d.direction()+" "+atIn);
        }
    }
    @Test void arbitraryUniqueBoundariesMatchConcreteStacksInBothDirections() {
        var s=session(List.of(call("a","b","done",A),call("b","body","outer",B),boundary("body",B,"bad"),boundary("outer",A,"bad"),ret("done"),ret("bad")),"a");
        for(var direction:Direction.values())for(var node:List.of("a","b","body","outer","done"))compare(s,definition(s,direction,node));
    }
    @Test void differentStacksWithIdenticalGuardSetsStillRejectAmbiguousBoundary() {
        var s=session(List.of(StructuralFixtures.branch(U,"start","a","b"),call("a","common","done"),call("b","common","done"),call("common","body","return"),resume("body"),resume("return"),ret("done")),"start");
        for(var direction:Direction.values()) {
            assertThrows(IllegalArgumentException.class,()->ExplicitActivationOracle.solve(s,definition(s,direction,"body")));
            assertThrows(IllegalArgumentException.class,()->DataflowSolver.solve(s,definition(s,direction,"body")));
        }
    }
    @Test void aSingleConcreteContextDoesNotExposeObsoleteInputVersions() {
        var s=session(List.of(call("a","body","again"),StructuralFixtures.branch(U,"again","a","done"),resume("body"),ret("done")),"a");
        var c=s.contexts().iterator().next();
        AnalysisDefinition<Integer> d=new AnalysisDefinition<>() {
            public Direction direction(){return Direction.FORWARD;}
            public Integer bottom(){return 0;}
            public Iterable<Boundary<Integer>> boundaries(AnalysisSession ignored){return List.of(new Boundary<>(c,c.entryNode(),1));}
            public Join<Integer> joinInto(Integer a,Integer b,DomainWork w){return new Join<>(a|b,(a|b)!=a);}
            public boolean equivalent(Integer a,Integer b,DomainWork w){return a.equals(b);}
            public Integer transferBlock(AnalysisPoint p,Integer a,DomainWork w){return p.node()==s.index().sequence(label("body"))?a|2:a;}
            public Integer transferEdge(AnalysisPoint p,CfgTransition e,Integer a,DomainWork w){return a;}
        };
        var expected=ExplicitActivationOracle.solve(s,d);assertEquals(List.of(3),expected.stream().filter(x->x.node()==s.index().sequence(label("body"))).map(ExplicitActivationOracle.State::in).toList());
        var result=DataflowSolver.solve(s,d);assertEquals(3,result.in(c,s.index().sequence(label("body"))));
    }
    @Test void unguardedRecursionIsRefusedInBothDirections() {
        var s=session(List.of(call("a","a","done"),ret("done")),"a");
        for(var direction:Direction.values())assertThrows(LocalControlRules.RecursiveActivation.class,()->DataflowSolver.solve(s,definition(s,direction,"a")));
    }
}
