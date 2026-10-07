package io.github.gustavo2358.analysis.solver;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BooleanCircuitDecisionsTest {
    private static AnalysisResources resources(){return new AnalysisResources(new AnalysisResources.Limits(8000000,100000,0,0,0,500000000,0));}
    private record Node(int key,long left,long right) { }
    private static final class Graph implements BooleanCircuitView {
        final List<Node> nodes=new ArrayList<>();
        long variable(int key){nodes.add(new Node(key,0,0));return (long)nodes.size()<<1;}
        long and(long a,long b){nodes.add(new Node(-1,a,b));return (long)nodes.size()<<1;}
        long or(long a,long b){return and(a^1,b^1)^1;}
        public long normalize(long root){return root;}
        public int primary(long handle){return nodes.get((int)handle-1).key;}
        public long left(long handle){return nodes.get((int)handle-1).left;}
        public long right(long handle){return nodes.get((int)handle-1).right;}
        boolean test(long root,int bits) {
            if(root<2)return root==1;
            var node=nodes.get((int)(root>>>1)-1);
            boolean result=node.key>=0?(bits&(1<<node.key))!=0:test(node.left,bits)&&test(node.right,bits);
            return result^((root&1)!=0);
        }
    }
    @Test void genericConeDecisionsPreserveRepeatedPrimaryKeysAndIndependentTruthTables() {
        var memory=resources();var graph=new Graph();var random=new Random(3968521);var roots=new ArrayList<Long>();
        for(int key=0;key<6;key++){roots.add(graph.variable(key));roots.add(graph.variable(key));}
        for(int i=0;i<60;i++) {
            long a=roots.get(random.nextInt(roots.size())),b=roots.get(random.nextInt(roots.size()));
            if(random.nextBoolean())a^=1;if(random.nextBoolean())b^=1;
            roots.add(graph.and(a,b));roots.add(graph.or(a,b));
        }
        try(var pages=new ResidentPageStore(4096,memory,AnalysisResources.Phase.CONTROL);
            var decisions=new BooleanCircuitDecisions(pages,memory,graph)) {
            for(int i=0;i<roots.size();i++) {
                long root=roots.get(i);boolean satisfiable=false;
                for(int bits=0;bits<64;bits++)satisfiable|=graph.test(root,bits);
                assertEquals(satisfiable,decisions.satisfiable(root),"SAT root="+i);
                for(int j=0;j<roots.size();j+=13) {
                    long other=roots.get(j);boolean equal=true;
                    for(int bits=0;bits<64;bits++)equal&=graph.test(root,bits)==graph.test(other,bits);
                    assertEquals(equal,decisions.equivalent(root,other),"miter roots="+i+","+j);
                }
            }
            assertTrue(decisions.equivalent(roots.get(0),roots.get(1)));
            assertFalse(decisions.satisfiable(graph.and(roots.get(0),roots.get(1)^1)));
        }
        assertEquals(0,memory.heapUsed());
    }
    @Test void constantsInsideDefinitionsAndAliasesPreserveTheirMeaning() {
        var memory=resources();var graph=new Graph();long x=graph.variable(0),same=graph.variable(0);
        try(var pages=new ResidentPageStore(128,memory,AnalysisResources.Phase.CONTROL);
            var decisions=new BooleanCircuitDecisions(pages,memory,graph)) {
            assertTrue(decisions.equivalent(x,graph.and(1,same)));
            assertTrue(decisions.equivalent(0,graph.and(0,x)));
            assertTrue(decisions.equivalent(1,graph.or(x,x^1)));
            assertTrue(decisions.equivalent(x^1,graph.and(x^1,1)));
            assertFalse(decisions.equivalent(0,1));assertTrue(decisions.satisfiable(1));assertFalse(decisions.satisfiable(0));
        }
        assertEquals(0,memory.heapUsed());
    }
    @Test void unrelatedGraphNodesAreNeverReadAndCyclesAreOperationalFailure() {
        var memory=resources();
        BooleanCircuitView onlyOne=new BooleanCircuitView() {
            public long normalize(long root){return root;}
            public int primary(long handle){assertEquals(1,handle);return 42;}
            public long left(long handle){throw new AssertionError("primary has no children");}
            public long right(long handle){throw new AssertionError("primary has no children");}
        };
        try(var pages=new ResidentPageStore(128,memory,AnalysisResources.Phase.CONTROL)) {
            try(var decisions=new BooleanCircuitDecisions(pages,memory,onlyOne)) {
                assertTrue(decisions.satisfiable(2));assertEquals(1,decisions.lastDecisionVariables());
            }
            BooleanCircuitView cycle=new BooleanCircuitView() {
                public long normalize(long root){return root;}
                public int primary(long handle){return -1;}
                public long left(long handle){return 2;}
                public long right(long handle){return 1;}
            };
            try(var decisions=new BooleanCircuitDecisions(pages,memory,cycle)) {
                var failure=assertThrows(PageStore.Failure.class,()->decisions.satisfiable(2));
                assertEquals(PageStore.Reason.CORRUPT,failure.reason());
                assertThrows(IllegalStateException.class,()->decisions.satisfiable(1));
            }
            assertEquals(0,pages.statistics().livePages());
        }
        assertEquals(0,memory.heapUsed());
    }
}
