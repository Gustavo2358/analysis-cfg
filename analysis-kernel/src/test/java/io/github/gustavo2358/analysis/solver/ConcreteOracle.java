package io.github.gustavo2358.analysis.solver;

import io.github.gustavo2358.analysis.cfg.domain.CfgTransition;
import io.github.gustavo2358.analysis.structure.*;
import java.util.*;
import static io.github.gustavo2358.analysis.solver.SyntheticAnalyses.*;
import static org.junit.jupiter.api.Assertions.*;

/** Finite machine exploration and use-before-definition search; never calls abstract callbacks. */
final class ConcreteOracle {
    record Location(ContextView context,ProgramIndex.Node node) { }
    record Machine(Location point,int value) { }
    record Search(ProgramIndex.Node node,int offset) { }
    private static int execute(Op op,int concrete) {
        // Explicit concrete transition tables, independent of the abstract bit operations.
        int[] table=switch(op) {
            case SET_X -> new int[]{1,1,3,3};
            case CLEAR_X -> new int[]{0,0,2,2};
            case SET_Y -> new int[]{2,3,2,3};
            case FLIP_X -> new int[]{1,0,3,2};
            case USE_X,USE_Y -> new int[]{0,1,2,3};
        };
        return table[concrete];
    }
    static void forward(Program program,DataflowResult<Set<Integer>> result,boolean precise) {
        Set<Machine> seen=new HashSet<>(); ArrayDeque<Machine> pending=new ArrayDeque<>();
        Map<Location,Set<Integer>> before=new HashMap<>(),after=new HashMap<>();
        for(int c=0;c<program.contexts.size();c++) {
            var context=program.contexts.get(c); pending.add(new Machine(new Location(context,context.entryNode()),c%4));
        }
        while(!pending.isEmpty()) {
            Machine m=pending.remove(); if(!seen.add(m)) continue;
            before.computeIfAbsent(m.point,k->new TreeSet<>()).add(m.value);
            int value=m.value;
            for(Op op:program.block(m.point.node)) value=execute(op,value);
            after.computeIfAbsent(m.point,k->new TreeSet<>()).add(value);
            var edges=m.point.context.successors(m.point.node);
            while(edges.advance()) {
                int target=value;
                if(program.decorated(edges.transition())) target=new int[]{2,3,2,3}[target];
                pending.add(new Machine(new Location(m.point.context,edges.target()),target));
            }
        }
        for(var c:program.contexts) for(var n:program.reachable(c)) {
            Location p=new Location(c,n); var expectedIn=before.getOrDefault(p,Set.of()); var expectedOut=after.getOrDefault(p,Set.of());
            assertTrue(result.in(c,n).containsAll(expectedIn),"concrete inclusion IN");
            assertTrue(result.out(c,n).containsAll(expectedOut),"concrete inclusion OUT");
            if(precise) { assertEquals(expectedIn,result.in(c,n),"minimum precision IN"); assertEquals(expectedOut,result.out(c,n),"minimum precision OUT"); }
        }
    }
    static void backward(Program program,DataflowResult<Long> result) {
        for(var c:program.contexts) for(var n:program.reachable(c)) {
            long before=0,after=0;
            for(int variable=0;variable<2;variable++) {
                if(useBeforeDefinition(program,c,n,0,variable)) before|=1L<<variable;
                if(useBeforeDefinition(program,c,n,program.block(n).length,variable)) after|=1L<<variable;
            }
            assertEquals(before,result.in(c,n),"concrete backward IN");
            assertEquals(after,result.out(c,n),"concrete backward OUT");
        }
    }
    private static boolean useBeforeDefinition(Program p,ContextView c,ProgramIndex.Node start,int offset,int variable) {
        Set<Search> visited=new HashSet<>(); ArrayDeque<Search> paths=new ArrayDeque<>(); paths.add(new Search(start,offset));
        while(!paths.isEmpty()) {
            Search s=paths.remove(); if(!visited.add(s)) continue;
            Op[] ops=p.block(s.node); boolean killed=false;
            for(int i=s.offset;i<ops.length;i++) {
                Op op=ops[i];
                if(variable==0 && (op==Op.USE_X||op==Op.FLIP_X) || variable==1 && op==Op.USE_Y) return true;
                if(variable==0 && (op==Op.SET_X||op==Op.CLEAR_X) || variable==1 && op==Op.SET_Y) { killed=true; break; }
            }
            if(killed) continue;
            var edges=c.successors(s.node);
            while(edges.advance()) {
                if(p.decorated(edges.transition())) {
                    if(variable==0) return true; // Edge executes SET_Y, then USE_X.
                    continue; // SET_Y kills the old Y before any subsequent use.
                }
                paths.add(new Search(edges.target(),0));
            }
        }
        return false;
    }
}
