package io.github.gustavo2358.analysis.solver;

import java.util.ArrayList;
import java.util.Random;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PagedBooleanCircuitTest {
    private static AnalysisResources resources(){return new AnalysisResources(new AnalysisResources.Limits(32000000,100000,0,0,0,2000000000L,0));}
    private record Expression(int kind,int key,Expression left,Expression right) {
        boolean test(int bits){return switch(kind){case 0->(bits&(1<<key))!=0;case 1->!left.test(bits);case 2->left.test(bits)&&right.test(bits);default->left.test(bits)||right.test(bits);};}
    }
    private static long compile(PagedBooleanCircuit b,Expression e) {
        return switch(e.kind){case 0->b.variable(e.key);case 1->b.not(compile(b,e.left));case 2->b.and(compile(b,e.left),compile(b,e.right));default->b.or(compile(b,e.left),compile(b,e.right));};
    }
    @Test void constructionValuationsRestrictionsSatAndEqualityAgreeWithIndependentExpressions() {
        var random=new Random(170263);var expressions=new ArrayList<Expression>();var roots=new ArrayList<Long>();
        var memory=resources();
        try(var pages=new ResidentPageStore(4096,memory,AnalysisResources.Phase.CONTROL);
            var b=new PagedBooleanCircuit(pages,memory)) {
            for(int key=0;key<5;key++)expressions.add(new Expression(0,key,null,null));
            for(int i=0;i<50;i++) {
                int kind=1+random.nextInt(3);
                expressions.add(new Expression(kind,0,expressions.get(random.nextInt(expressions.size())),expressions.get(random.nextInt(expressions.size()))));
            }
            for(var e:expressions)roots.add(compile(b,e));
            for(int i=0;i<expressions.size();i++) {
                var e=expressions.get(i);long root=roots.get(i);boolean any=false;
                for(int bits=0;bits<32;bits++) {
                    int assignment=bits;boolean expected=e.test(bits);any|=expected;
                    assertEquals(expected,b.test(root,key->(assignment&(1<<key))!=0));
                    long restricted=b.restrict(root,key->(assignment&(1<<key))!=0?1:0);
                    assertEquals(expected?1:0,restricted,"all primary inputs fixed");
                    for(int key=0;key<5;key++) {
                        long partial=b.restrict(root,key,true);int fixed=bits|(1<<key);
                        assertEquals(e.test(fixed),b.test(partial,k->(assignment&(1<<k))!=0));
                    }
                }
                assertEquals(any,b.satisfiable(root),"SAT expression="+i);
                for(int j=0;j<expressions.size();j+=7) {
                    boolean equal=true;for(int bits=0;bits<32;bits++)equal&=e.test(bits)==expressions.get(j).test(bits);
                    assertEquals(equal,b.equivalent(root,roots.get(j)),"miter expressions="+i+","+j);
                }
            }
        }
        assertEquals(0,memory.heapUsed());
    }
    @Test void exactFunctionalLawsAreSeparateFromRawCircuitIdentity() {
        var memory=resources();
        try(var pages=new ResidentPageStore(4096,memory,AnalysisResources.Phase.CONTROL);
            var b=new PagedBooleanCircuit(pages,memory)) {
            long x=b.variable(7),y=b.variable(83),z=b.variable(Integer.MAX_VALUE);
            long left=b.and(x,b.or(y,z)),right=b.or(b.and(x,y),b.and(x,z));
            assertNotEquals(left,right,"structural identity is deliberately not functional identity");
            assertTrue(b.equivalent(left,right));assertFalse(b.equivalent(left,right^1));
            assertTrue(b.equivalent(b.not(b.and(x,y)),b.or(b.not(x),b.not(y))));
            assertFalse(b.equivalent(x,y));assertFalse(b.equivalent(x,z));
            assertEquals(x,b.not(b.not(x)));assertEquals(0,b.and(x,b.not(x)));
            assertEquals(1,b.or(x,b.not(x)));assertEquals(x,b.and(x,b.or(x,y)));
            assertEquals(b.and(x,y),b.and(y,x));
            assertFalse(b.satisfiable(b.and(left,b.not(right))));
            assertTrue(b.satisfiable(b.and(left,b.not(x))^1));
        }
        assertEquals(0,memory.heapUsed());
    }
    @Test void mixedRelationsRetainLinearSharedSyntaxAfterRootedCollection() {
        for(boolean equality:new boolean[]{false,true})for(int count:new int[]{8,32,128,512}) {
            var memory=resources();
            try(var pages=new ResidentPageStore(4096,memory,AnalysisResources.Phase.CONTROL);
                var b=new PagedBooleanCircuit(pages,memory)) {
                long root=equality?1:0;
                for(int i=0;i<count;i++) {
                    long a=b.variable(i),c=b.variable(count+i);
                    long term=equality?b.or(b.and(a,c),b.and(b.not(a),b.not(c))):b.and(a,c);
                    root=equality?b.and(root,term):b.or(root,term);
                }
                long token=b.retain(root);b.collect();
                assertTrue(b.retainedNodes()<=8L*count,"relation="+equality+" count="+count);
                assertTrue(b.satisfiable(root));
                b.release(token);b.collect();assertEquals(0,b.retainedNodes());
            }
            assertEquals(0,memory.heapUsed());
        }
    }
    @Test void decisionQueriesEncodeOnlyTheExactRequestedFaninCones() {
        var memory=resources();
        try(var pages=new ResidentPageStore(4096,memory,AnalysisResources.Phase.CONTROL);
            var b=new PagedBooleanCircuit(pages,memory)) {
            for(int key=10;key<1010;key++)b.and(b.variable(key),b.variable(key+2000));
            long x=b.variable(1),y=b.variable(2);
            assertFalse(b.equivalent(x,y));assertEquals(2,b.lastDecisionVariables());
            assertTrue(b.satisfiable(x));assertEquals(1,b.lastDecisionVariables());
            assertTrue(b.satisfiable(1));assertEquals(0,b.lastDecisionVariables());
        }
        assertEquals(0,memory.heapUsed());
    }
    @Test void deepPostorderSubstitutionAndEvaluationNeverUseJavaCallStack() {
        var memory=resources();int count=4096;
        try(var pages=new ResidentPageStore(4096,memory,AnalysisResources.Phase.CONTROL);
            var b=new PagedBooleanCircuit(pages,memory)) {
            long root=1;for(int key=0;key<count;key++)root=b.and(root,b.variable(key));
            assertTrue(b.test(root,key->true));assertFalse(b.test(root,key->key!=count-1));
            assertEquals(1,b.restrict(root,key->1));assertEquals(0,b.restrict(root,key->key==count-1?0:1));
            assertTrue(b.satisfiable(root));assertFalse(b.satisfiable(b.and(root,b.not(b.variable(count-1)))));
        }
        assertEquals(0,memory.heapUsed());
    }
    @Test void rootedSharedChildrenSurviveCollectionAndStaleHandlesAreRejected() {
        var memory=resources();
        try(var pages=new ResidentPageStore(128,memory,AnalysisResources.Phase.CONTROL)) {
            try(var b=new PagedBooleanCircuit(pages,memory)) {
                long x=b.variable(1),y=b.variable(2),root=b.and(x,y);long token=b.retain(root);
                long discarded=b.variable(3);b.collect();assertEquals(3,b.retainedNodes());
                assertTrue(b.test(root,key->true));assertThrows(IllegalArgumentException.class,()->b.test(discarded,key->true));
                b.release(token);b.collect();assertEquals(0,b.retainedNodes());
                assertThrows(IllegalArgumentException.class,()->b.satisfiable(root));
                assertTrue(b.variable(1)>x,"collected handles cannot be reused for different CNF meanings");
            }
            assertEquals(0,pages.statistics().livePages());long page=pages.allocate();pages.release(page);
        }
        assertEquals(0,memory.heapUsed());
    }
    @Test void quotaDenialAbortsEvenTerminalQueriesAndReleasesBorrowedPages() {
        for(long quota:new long[]{512,4096,8192,16000,26000,40000,100000}) {
            var memory=new AnalysisResources(new AnalysisResources.Limits(quota,100000,0,0,0,2000000000L,0));
            try(var pages=new ResidentPageStore(128,memory,AnalysisResources.Phase.CONTROL)) {
                PagedBooleanCircuit b=null;boolean denied=false;
                try {
                    b=new PagedBooleanCircuit(pages,memory);
                    long root=1;for(int key=0;key<4096;key++)root=b.and(root,b.variable(key));
                }catch(AnalysisResources.Exhausted expected){denied=true;}
                if(b!=null) {
                    var owner=b;
                    assertThrows(IllegalStateException.class,()->owner.satisfiable(1));
                    assertThrows(IllegalStateException.class,()->owner.equivalent(0,0));
                    assertThrows(IllegalStateException.class,()->owner.not(0));
                    b.close();b.close();
                }
                assertTrue(denied,"quota="+quota);assertEquals(0,pages.statistics().livePages());
            }
            assertEquals(0,memory.heapUsed());
        }
    }
    @Test void interruptedTraversalDecisionsAndCollectionReleaseQueryAndCircuitOwners() {
        for(int operation=0;operation<5;operation++)for(int boundary:new int[]{1,2,4,16,64,256,1024}) {
            var memory=resources();
            try(var backend=new ResidentPageStore(128,memory,AnalysisResources.Phase.CONTROL)) {
                var pages=new InterruptiblePages(backend);var b=new PagedBooleanCircuit(pages,memory);
                long root=1;for(int key=0;key<32;key++)root=b.and(root,b.variable(key));
                long retained=b.retain(root);long sibling=b.variable(40);pages.remaining=boundary;
                try {
                    if(operation==0)b.test(root,key->true);
                    else if(operation==1)b.restrict(root,key->key%2==0?1:-1);
                    else if(operation==2)b.satisfiable(root);
                    else if(operation==3)b.equivalent(root,sibling);
                    else b.collect();
                    fail("injection not reached operation="+operation+" boundary="+boundary);
                }catch(PageStore.Failure expected){assertEquals(PageStore.Reason.IO,expected.reason());}
                assertThrows(IllegalStateException.class,()->b.satisfiable(1));
                assertThrows(IllegalStateException.class,()->b.release(retained));
                pages.remaining=-1;b.close();b.close();
                assertEquals(0,backend.statistics().livePages(),"operation="+operation+" boundary="+boundary);
                long page=backend.allocate();backend.release(page);
            }
            assertEquals(0,memory.heapUsed());
        }
    }
    private static final class InterruptiblePages implements PageStore {
        final PageStore delegate;int remaining=-1;
        InterruptiblePages(PageStore delegate){this.delegate=delegate;}
        void step(){if(remaining>0&&--remaining==0)throw new Failure(Reason.IO,"synthetic circuit interruption");}
        public int pageBytes(){return delegate.pageBytes();}
        public long allocate(){step();return delegate.allocate();}
        public void read(long page,int offset,byte[] target,int start,int length){step();delegate.read(page,offset,target,start,length);}
        public void readForCleanup(long page,int offset,byte[] target,int start,int length){step();delegate.readForCleanup(page,offset,target,start,length);}
        public void write(long page,int offset,byte[] source,int start,int length){step();delegate.write(page,offset,source,start,length);}
        public void release(long page){delegate.release(page);}
        public void releaseForCleanup(long page){delegate.releaseForCleanup(page);}
        public void flush(){delegate.flush();}
        public Statistics statistics(){return delegate.statistics();}
        public void close(){delegate.close();}
    }
    @Test void storedSimulationWordsAreActualValuationsAndRespectFunctionalLaws() {
        var memory=resources();
        try(var pages=new ResidentPageStore(4096,memory,AnalysisResources.Phase.CONTROL);
            var b=new PagedBooleanCircuit(pages,memory)) {
            long x=b.variable(17),y=b.variable(91),z=b.variable(Integer.MAX_VALUE);
            long left=b.and(x,b.or(y,z)),right=b.or(b.and(x,y),b.and(x,z));
            for(int word=0;word<PagedBooleanCircuit.SAMPLE_WORDS;word++) {
                int channel=word;long expected=0;
                for(int bit=0;bit<64;bit++) {
                    int position=bit;
                    if(b.test(left,key->((PagedBooleanCircuit.primarySample(key,channel)>>>position)&1)!=0))expected|=1L<<bit;
                }
                assertEquals(expected,b.sample(left,word));assertEquals(expected,b.sample(right,word));
                assertEquals(~expected,b.sample(left^1,word));
                assertEquals(0,b.sample(0,word));assertEquals(-1L,b.sample(1,word));
            }
            // Identical samples are deliberately not a proof. An unsampled primary
            // valuation can distinguish functions; every merge still needs an exact query.
            assertTrue(b.equivalent(left,right));assertFalse(b.equivalent(x,y));
        }
        assertEquals(0,memory.heapUsed());
    }
    @Test void simulationConstructionProcessesEachNewSharedNodeOnceInsteadOfItsWholeCone() {
        var memory=resources();int count=4096;
        try(var pages=new ResidentPageStore(4096,memory,AnalysisResources.Phase.CONTROL);
            var b=new PagedBooleanCircuit(pages,memory)) {
            long root=1;for(int key=0;key<count;key++)root=b.and(root,b.variable(key));
            assertEquals((2L*count-1)*PagedBooleanCircuit.SAMPLE_WORDS,b.sampleWordsCalculated());
            long before=b.sampleWordsCalculated();
            assertEquals(root,b.and(root,root));assertEquals(root,b.and(root,1));
            assertEquals(before,b.sampleWordsCalculated());
            for(int word=0;word<PagedBooleanCircuit.SAMPLE_WORDS;word++)b.sample(root,word);
            assertEquals(before,b.sampleWordsCalculated(),"reading a sample cannot reevaluate a cone");
        }
        assertEquals(0,memory.heapUsed());
    }
}
