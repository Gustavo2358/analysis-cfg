package io.github.gustavo2358.analysis.solver;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BooleanCircuitDecisionsTest {
    @Test void distinctGraphAliasesReuseDefinitionsAndCountOnlyNewVariables(){
        var memory=resources();var graph=new Graph();long a=graph.variable(0),b=graph.variable(1),root=graph.and(a,b);
        try(var pages=new ResidentPageStore(128,memory,AnalysisResources.Phase.CONTROL);
            var decisions=new BooleanCircuitDecisions(pages,memory,graph)){
            decisions.beginScope();assertTrue(decisions.satisfiable(root));assertEquals(3,decisions.lastDecisionVariables());
            for(int alias=0;alias<32;alias++){
                assertTrue(decisions.equivalent(root,graph.and(b,a)));
                assertEquals(0,decisions.lastDecisionVariables(),"aliases must not report phantom variable allocations");
            }
            decisions.endScope();assertEquals(0,pages.statistics().livePages());
        }
        assertEquals(0,memory.heapUsed());
    }
    @Test void formulaDefinitionsCannotMultiplyIndependentBranchingDimension() throws Exception {
        // The circuit has N independent atoms. Derived gate values are uniquely
        // determined by those atoms, regardless of the number of graph records.
        for(int count:new int[]{32,128,512}){
            var memory=resources();var graph=new Graph();long root=1;
            for(int key=0;key<count;key++)root=graph.and(root,graph.variable(key));
            try(var pages=new ResidentPageStore(4096,memory,AnalysisResources.Phase.CONTROL);
                var decisions=new BooleanCircuitDecisions(pages,memory,graph)){
                decisions.beginScope();assertTrue(decisions.satisfiable(root));
                Object formula=field(field(decisions,"retained"),"decisions");
                assertEquals(count,field(formula,"heapSize"),"only original atoms are independent search choices N="+count);
                decisions.endScope();assertEquals(0,pages.statistics().livePages());
            }
            assertEquals(0,memory.heapUsed());
        }
    }
    private static Object field(Object owner,String name) throws ReflectiveOperationException {
        var field=owner.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(owner);
    }
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
    @Test void scopedIncrementalQueriesEncodeSharedFaninsOnceInsteadOfPerPrefix() {
        var memory=resources();var graph=new Graph();long[] reads={0};
        BooleanCircuitView counted=new BooleanCircuitView(){
            public long normalize(long root){return root;}
            public int primary(long handle){reads[0]++;return graph.primary(handle);}
            public long left(long handle){reads[0]++;return graph.left(handle);}
            public long right(long handle){reads[0]++;return graph.right(handle);}
        };
        try(var pages=new ResidentPageStore(4096,memory,AnalysisResources.Phase.CONTROL)) {
            try(var decisions=new BooleanCircuitDecisions(pages,memory,counted)) {
                decisions.beginScope();long root=1;int count=512;
                for(int key=0;key<count;key++) {
                    root=graph.and(root,graph.variable(key));
                    assertTrue(decisions.equivalent(root,graph.or(root,0)));
                }
                assertTrue(reads[0]<=32L*count,"shared definition reads="+reads[0]);
                decisions.endScope();assertEquals(0,pages.statistics().livePages());
                assertTrue(decisions.equivalent(root,root));
            }
            assertEquals(0,pages.statistics().livePages());
        }
        assertEquals(0,memory.heapUsed());
    }
    @Test void scopedLearningAndFormulaExtensionPreserveIndependentAssignmentOracles() {
        var memory=resources();var graph=new Graph();var roots=new ArrayList<Long>();
        for(int key=0;key<4;key++)roots.add(graph.variable(key));
        var random=new Random(842719);
        try(var pages=new ResidentPageStore(4096,memory,AnalysisResources.Phase.CONTROL);
            var decisions=new BooleanCircuitDecisions(pages,memory,graph)) {
            decisions.beginScope();assertThrows(IllegalStateException.class,decisions::beginScope);
            for(int step=0;step<16;step++) {
                long a=roots.get(random.nextInt(roots.size())),b=roots.get(random.nextInt(roots.size()));
                long root=random.nextBoolean()?graph.and(a,b):graph.or(a,b);
                if(random.nextBoolean())root^=1;roots.add(root);
                for(int bits=0;bits<16;bits++) {
                    long cube=1;
                    for(int key=0;key<4;key++){long literal=graph.variable(key);cube=graph.and(cube,literal^((bits&(1<<key))==0?1:0));}
                    assertEquals(graph.test(root,bits),decisions.satisfiable(graph.and(root,cube)),"step="+step+" bits="+bits);
                }
            }
            decisions.endScope();assertEquals(0,pages.statistics().livePages());
            decisions.beginScope();assertTrue(decisions.equivalent(roots.get(0),graph.variable(0)));decisions.endScope();
        }
        assertEquals(0,memory.heapUsed());
    }
    @Test void scopeEndDropsAllDefinitionsBeforeBorrowedHandlesAreReused() {
        var memory=resources();var graph=new Graph();
        try(var pages=new ResidentPageStore(128,memory,AnalysisResources.Phase.CONTROL);
            var decisions=new BooleanCircuitDecisions(pages,memory,graph)) {
            long x=graph.variable(0),y=graph.variable(1),old=graph.and(x,y);
            decisions.beginScope();assertFalse(decisions.equivalent(old,x));decisions.endScope();
            assertEquals(0,pages.statistics().livePages());
            graph.nodes.clear();x=graph.variable(2);y=graph.variable(2);long fresh=graph.and(x,y);
            assertEquals(old,fresh,"must reuse the same borrowed graph handle");
            decisions.beginScope();assertTrue(decisions.equivalent(fresh,x));decisions.endScope();
            assertEquals(0,pages.statistics().livePages());
        }
        assertEquals(0,memory.heapUsed());
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
