package io.github.gustavo2358.analysis.solver;

import io.github.gustavo2358.analysis.cfg.domain.CfgTransition;
import io.github.gustavo2358.analysis.structure.*;
import java.util.*;
import static io.github.gustavo2358.analysis.structure.SolverFixtureBridge.*;

/** Tiny finite test language. Nothing interprets AIR operations or values. */
final class SyntheticAnalyses {
    enum Op { SET_X, CLEAR_X, SET_Y, USE_X, USE_Y, FLIP_X }
    static final class Program {
        final AnalysisSession session;
        final int[][] successors;
        final Op[][] blocks;
        final Map<ProgramIndex.Node, Integer> numbers = new IdentityHashMap<>();
        final List<ContextView> contexts;
        final boolean edgeEffects;
        Program(int[][] successors, int[] roots, int selected, Op[][] blocks, boolean edgeEffects) {
            this.successors = successors; this.blocks = blocks; this.edgeEffects = edgeEffects;
            session = graph(successors, roots, selected); contexts = List.copyOf(session.contexts());
            for (int n = 0; n < successors.length; n++) numbers.put(node(session, n), n);
        }
        Op[] block(ProgramIndex.Node n) { Integer i = numbers.get(n); return i == null ? new Op[0] : blocks[i]; }
        boolean decorated(CfgTransition e) { return edgeEffects && e.kind() == CfgTransition.Kind.BRANCH_TRUE; }
        List<ProgramIndex.Node> reachable(ContextView c) {
            List<ProgramIndex.Node> result = new ArrayList<>(); Set<ProgramIndex.Node> seen = Collections.newSetFromMap(new IdentityHashMap<>());
            result.add(c.entryNode()); seen.add(c.entryNode());
            for (int i=0;i<result.size();i++) {
                var edges=c.successors(result.get(i));
                while(edges.advance()) if(seen.add(edges.target())) result.add(edges.target());
            }
            return result;
        }
    }
    static class Forward implements AnalysisDefinition<Set<Integer>> {
        final Program program;
        Forward(Program program) { this.program=program; }
        public Direction direction() { return Direction.FORWARD; }
        public Set<Integer> bottom() { return Set.of(); }
        public Iterable<Boundary<Set<Integer>>> boundaries(AnalysisSession s) {
            List<Boundary<Set<Integer>>> result=new ArrayList<>();
            for(int c=0;c<program.contexts.size();c++) {
                var context=program.contexts.get(c); result.add(new Boundary<>(context,context.entryNode(),Set.of(c%4)));
            }
            return result;
        }
        public Join<Set<Integer>> joinInto(Set<Integer> a,Set<Integer> b,DomainWork w) {
            Set<Integer> next=new TreeSet<>(a);
            for(int v:b) { w.joinEntryVisited(); next.add(v); }
            return new Join<>(Collections.unmodifiableSet(next), !equivalent(a,next,w));
        }
        public boolean equivalent(Set<Integer> a,Set<Integer> b,DomainWork w) {
            if(a.size()!=b.size()) return false;
            for(int v:a) { w.stateCompareEntry(); if(!b.contains(v)) return false; }
            return true;
        }
        Set<Integer> apply(Op[] ops,Set<Integer> state,DomainWork work) {
            for(Op op:ops) {
                work.operationTransferred(); Set<Integer> next=new TreeSet<>();
                for(int v:state) next.add(switch(op) {
                    case SET_X -> v|1;
                    case CLEAR_X -> v&2;
                    case SET_Y -> v|2;
                    case FLIP_X -> v^1;
                    case USE_X,USE_Y -> v;
                });
                state=Collections.unmodifiableSet(next);
            }
            return state;
        }
        public Set<Integer> transferBlock(AnalysisPoint p,Set<Integer> a,DomainWork w) { return apply(program.block(p.node()),a,w); }
        public Set<Integer> transferEdge(AnalysisPoint p,CfgTransition e,Set<Integer> a,DomainWork w) {
            return program.decorated(e) ? apply(new Op[]{Op.SET_Y,Op.USE_X},a,w) : a;
        }
    }
    static class Backward implements AnalysisDefinition<Long> {
        final Program program;
        Backward(Program program) { this.program=program; }
        public Direction direction() { return Direction.BACKWARD; }
        public Long bottom() { return 0L; }
        public Iterable<Boundary<Long>> boundaries(AnalysisSession s) { return List.of(); }
        public Join<Long> joinInto(Long a,Long b,DomainWork w) { w.joinEntryVisited(); long v=a|b; return new Join<>(v,v!=a); }
        public boolean equivalent(Long a,Long b,DomainWork w) { w.stateCompareEntry(); return a.longValue()==b.longValue(); }
        long reverse(Op[] ops,long state,DomainWork work) {
            for(int i=ops.length-1;i>=0;i--) {
                work.operationTransferred();
                state=switch(ops[i]) {
                    case SET_X,CLEAR_X -> state&~1L;
                    case SET_Y -> state&~2L;
                    case USE_X,FLIP_X -> state|1L;
                    case USE_Y -> state|2L;
                };
            }
            return state;
        }
        public Long transferBlock(AnalysisPoint p,Long a,DomainWork w) { return reverse(program.block(p.node()),a,w); }
        public Long transferEdge(AnalysisPoint p,CfgTransition e,Long a,DomainWork w) {
            return program.decorated(e) ? reverse(new Op[]{Op.SET_Y,Op.USE_X},a,w) : a;
        }
    }
    static Program program(int[][] edges, Op[][] ops) { return new Program(edges,new int[]{0},1,ops,true); }
}
