package io.github.gustavo2358.analysis.solver;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PagedBooleanDecisionsTest {
    private static AnalysisResources resources(){return new AnalysisResources(new AnalysisResources.Limits(16000000,100000,0,0,0,2000000000L,0));}
    private static long literal(int variable,boolean positive){return ((long)variable<<1)|(positive?0:1);}
    private static boolean satisfied(long[] clause,int bits) {
        for(long p:clause)if(((bits&(1<<((int)(p>>>1)-1)))!=0)==((p&1)==0))return true;
        return false;
    }
    private static boolean oracle(List<long[]> clauses,int variables,long[] assumptions) {
        for(int bits=0;bits<1<<variables;bits++) {
            boolean accepted=true;
            for(long p:assumptions)if(!satisfied(new long[]{p},bits)){accepted=false;break;}
            if(accepted)for(long[] clause:clauses)if(!satisfied(clause,bits)){accepted=false;break;}
            if(accepted)return true;
        }
        return false;
    }
    @Test void incrementalClausesQueriesAndModelsAgreeWithIndependentExhaustiveCnf() {
        var random=new Random(768429);
        for(int sample=0;sample<160;sample++) {
            var memory=resources();var clauses=new ArrayList<long[]>();int count=1+random.nextInt(8);
            try(var pages=new ResidentPageStore(128,memory,AnalysisResources.Phase.CONTROL);
                var decisions=new PagedBooleanDecisions(pages,memory)) {
                for(int key=1;key<=count;key++)assertEquals(key,decisions.newVariable());
                for(int stage=0;stage<4;stage++) {
                    for(int added=0;added<3;added++) {
                        long[] clause=new long[1+random.nextInt(4)];
                        for(int i=0;i<clause.length;i++)clause[i]=literal(1+random.nextInt(count),random.nextBoolean());
                        clauses.add(clause);decisions.addClause(clause);
                    }
                    for(int query=0;query<6;query++) {
                        long[] assumptions=new long[random.nextInt(4)];
                        for(int i=0;i<assumptions.length;i++)assumptions[i]=literal(1+random.nextInt(count),random.nextBoolean());
                        boolean expected=oracle(clauses,count,assumptions);
                        assertEquals(expected,decisions.satisfiable(assumptions),"sample="+sample+" stage="+stage+" query="+query);
                        if(expected) {
                            int bits=0;for(int key=1;key<=count;key++)if(decisions.value(key))bits|=1<<(key-1);
                            for(long[] clause:clauses)assertTrue(satisfied(clause,bits),"returned model must satisfy every original clause");
                            for(long p:assumptions)assertTrue(satisfied(new long[]{p},bits));
                        }else assertThrows(IllegalStateException.class,()->decisions.value(1));
                    }
                }
            }
            assertEquals(0,memory.heapUsed(),"sample="+sample);
        }
    }
    @Test void contradictoryAssumptionsNeverBecomePermanentUnsatOrLeakIntoNextQuery() {
        var memory=resources();
        try(var pages=new ResidentPageStore(128,memory,AnalysisResources.Phase.CONTROL)) {
            try(var decisions=new PagedBooleanDecisions(pages,memory)) {
                int x=decisions.newVariable(),y=decisions.newVariable();
                decisions.addClause(literal(x,true),literal(y,true));
                assertFalse(decisions.satisfiable(literal(x,false),literal(y,false)));
                assertTrue(decisions.satisfiable(literal(x,true)));
                assertFalse(decisions.satisfiable(literal(x,false),literal(x,true)));
                assertTrue(decisions.satisfiable(literal(x,false)));assertTrue(decisions.value(y));
                decisions.addClause(literal(y,false));
                assertTrue(decisions.satisfiable());assertTrue(decisions.value(x));assertFalse(decisions.value(y));
                decisions.addClause(literal(x,false));assertFalse(decisions.satisfiable());
                assertFalse(decisions.satisfiable(literal(y,true)));
            }
            assertEquals(0,pages.statistics().livePages());long page=pages.allocate();pages.release(page);
        }
        assertEquals(0,memory.heapUsed());
    }
    @Test void emptyTautologicalDuplicateAndUnitClausesRetainTheirExactMeaning() {
        var memory=resources();
        try(var pages=new ResidentPageStore(128,memory,AnalysisResources.Phase.CONTROL)) {
            try(var decisions=new PagedBooleanDecisions(pages,memory)) {
                int x=decisions.newVariable();decisions.addClause(literal(x,true),literal(x,false));
                assertTrue(decisions.satisfiable(literal(x,false)));
                decisions.addClause(literal(x,true),literal(x,true));
                assertTrue(decisions.satisfiable());assertTrue(decisions.value(x));
                assertFalse(decisions.satisfiable(literal(x,false)));assertTrue(decisions.satisfiable());
                decisions.addClause();assertFalse(decisions.satisfiable());
            }
            assertEquals(0,pages.statistics().livePages());
        }
        assertEquals(0,memory.heapUsed());
    }
    @Test void deepImplicationPropagationVisitsAffectedWatchesInsteadOfRescanningFormula() {
        var memory=resources();int count=4096;
        try(var pages=new ResidentPageStore(4096,memory,AnalysisResources.Phase.CONTROL);
            var decisions=new PagedBooleanDecisions(pages,memory)) {
            for(int key=1;key<=count;key++)decisions.newVariable();
            for(int key=1;key<count;key++)decisions.addClause(literal(key,false),literal(key+1,true));
            long before=decisions.watchedClausesVisited();
            assertFalse(decisions.satisfiable(literal(1,true),literal(count,false)));
            assertTrue(decisions.satisfiable(literal(1,true)));assertTrue(decisions.value(count));
            assertTrue(decisions.watchedClausesVisited()-before<=16L*count,"a linear implication chain cannot perform a clause-by-assignment product");
        }
        assertEquals(0,memory.heapUsed());
    }
    @Test void firstUipLearningSkipsIrrelevantDecisionAndPreservesEveryLaterAssumption() {
        var memory=resources();var clauses=new ArrayList<long[]>();
        clauses.add(new long[]{literal(1,false),literal(3,false),literal(4,true)});
        clauses.add(new long[]{literal(1,false),literal(3,false),literal(4,false)});
        try(var pages=new ResidentPageStore(128,memory,AnalysisResources.Phase.CONTROL);
            var decisions=new PagedBooleanDecisions(pages,memory)) {
            for(int key=1;key<=4;key++)decisions.newVariable();
            for(long[] clause:clauses)decisions.addClause(clause);
            // Default decisions x,y,z=true force opposite implications on a. The
            // entailed clause !x|!z backjumps over y, which is absent from the CNF.
            assertTrue(decisions.satisfiable());assertTrue(decisions.learnedClauses()>0);
            for(int bits=0;bits<16;bits++) {
                long[] assumptions=new long[4];
                for(int key=1;key<=4;key++)assumptions[key-1]=literal(key,(bits&(1<<(key-1)))!=0);
                assertEquals(oracle(clauses,4,assumptions),decisions.satisfiable(assumptions),"valuation="+bits);
                assertTrue(decisions.satisfiable(),"a query conflict must not poison the permanent formula");
                int model=0;for(int key=1;key<=4;key++)if(decisions.value(key))model|=1<<(key-1);
                for(long[] clause:clauses)assertTrue(satisfied(clause,model));
            }
        }
        assertEquals(0,memory.heapUsed());
    }
    @Test void pigeonholeContradictionsRequireSearchAndPreserveIncrementalQueries() {
        for(int holes=2;holes<=4;holes++) {
            var memory=resources();int pigeons=holes+1;
            try(var pages=new ResidentPageStore(4096,memory,AnalysisResources.Phase.CONTROL);
                var decisions=new PagedBooleanDecisions(pages,memory)) {
                for(int key=1;key<=holes*pigeons;key++)decisions.newVariable();
                for(int pigeon=0;pigeon<pigeons;pigeon++) {
                    long[] someHole=new long[holes];
                    for(int hole=0;hole<holes;hole++)someHole[hole]=literal(pigeon*holes+hole+1,true);
                    decisions.addClause(someHole);
                }
                for(int hole=0;hole<holes;hole++)for(int a=0;a<pigeons;a++)for(int b=a+1;b<pigeons;b++)
                    decisions.addClause(literal(a*holes+hole+1,false),literal(b*holes+hole+1,false));
                // Independent pigeonhole principle: holes cannot hold holes+1 distinct pigeons.
                assertFalse(decisions.satisfiable());
                assertFalse(decisions.satisfiable(literal(1,true)));
                assertFalse(decisions.satisfiable(literal(1,false)));
                assertThrows(IllegalStateException.class,()->decisions.value(1));
            }
            assertEquals(0,memory.heapUsed());
        }
    }
    @Test void denseNonunitFormulasAndLearnedClausesAgreeWithIndependentEnumeration() {
        var random=new Random(9816751);long learned=0;
        for(int sample=0;sample<80;sample++) {
            var memory=resources();var clauses=new ArrayList<long[]>();int count=9+random.nextInt(4);
            try(var pages=new ResidentPageStore(4096,memory,AnalysisResources.Phase.CONTROL);
                var decisions=new PagedBooleanDecisions(pages,memory)) {
                for(int key=1;key<=count;key++)decisions.newVariable();
                for(int stage=0;stage<3;stage++) {
                    for(int added=0;added<count*2;added++) {
                        long[] clause=new long[3];
                        for(int i=0;i<clause.length;i++)clause[i]=literal(1+random.nextInt(count),random.nextBoolean());
                        clauses.add(clause);decisions.addClause(clause);
                    }
                    for(int query=0;query<10;query++) {
                        long[] assumptions=new long[random.nextInt(4)];
                        for(int i=0;i<assumptions.length;i++)assumptions[i]=literal(1+random.nextInt(count),random.nextBoolean());
                        boolean expected=oracle(clauses,count,assumptions);
                        assertEquals(expected,decisions.satisfiable(assumptions),"sample="+sample+" stage="+stage+" query="+query);
                        if(expected) {
                            int bits=0;for(int key=1;key<=count;key++)if(decisions.value(key))bits|=1<<(key-1);
                            for(long[] clause:clauses)assertTrue(satisfied(clause,bits));
                            for(long p:assumptions)assertTrue(satisfied(new long[]{p},bits));
                        }
                    }
                }
                learned+=decisions.learnedClauses();
            }
            assertEquals(0,memory.heapUsed());
        }
        assertTrue(learned>100,"the independent oracle must exercise actual reusable conflict learning");
    }
    @Test void constructorAndGrowthDenialsAbortWithoutLeakingBorrowedPages() {
        for(long quota:new long[]{512,4096,8192,16000,26000,40000,100000}) {
            var memory=new AnalysisResources(new AnalysisResources.Limits(quota,100000,0,0,0,2000000000L,0));
            try(var pages=new ResidentPageStore(128,memory,AnalysisResources.Phase.CONTROL)) {
                PagedBooleanDecisions decisions=null;boolean denied=false;
                try {
                    decisions=new PagedBooleanDecisions(pages,memory);
                    for(int key=1;key<=4096;key++)decisions.newVariable();
                }catch(AnalysisResources.Exhausted expected){denied=true;}
                if(decisions!=null) {
                    var owner=decisions;
                    assertThrows(IllegalStateException.class,()->owner.satisfiable());
                    assertThrows(IllegalStateException.class,()->owner.addClause());
                    decisions.close();decisions.close();
                }
                assertTrue(denied,"actual constructor or variable growth must exhaust quota="+quota);
                assertEquals(0,pages.statistics().livePages(),"quota="+quota);
            }
            assertEquals(0,memory.heapUsed(),"quota="+quota);
        }
    }
    @Test void storageInterruptionsDuringInsertionSearchAndModelCopyAbortAndReleaseAllPages() {
        for(boolean searching:new boolean[]{false,true})for(int boundary:new int[]{1,2,3,4,8,16,32,64,128,256,512,1024}) {
            var memory=resources();
            try(var backend=new ResidentPageStore(128,memory,AnalysisResources.Phase.CONTROL)) {
                var pages=new InterruptiblePages(backend);var decisions=new PagedBooleanDecisions(pages,memory);
                for(int key=1;key<=32;key++)decisions.newVariable();
                if(searching)for(int key=1;key<32;key++)decisions.addClause(literal(key,false),literal(key+1,true));
                pages.remaining=boundary;
                try {
                    if(searching)decisions.satisfiable(literal(1,true));
                    else for(int key=1;key<32;key++)decisions.addClause(literal(key,false),literal(key+1,true));
                    fail("fault not reached: searching="+searching+" boundary="+boundary);
                }catch(PageStore.Failure expected){assertEquals(PageStore.Reason.IO,expected.reason());}
                assertThrows(IllegalStateException.class,()->decisions.satisfiable());
                assertThrows(IllegalStateException.class,()->decisions.value(1));
                assertThrows(IllegalStateException.class,()->decisions.newVariable());
                pages.remaining=-1;decisions.close();decisions.close();
                assertEquals(0,backend.statistics().livePages(),"searching="+searching+" boundary="+boundary);
                long page=backend.allocate();backend.release(page);
            }
            assertEquals(0,memory.heapUsed());
        }
    }
    private static final class InterruptiblePages implements PageStore {
        final PageStore delegate;int remaining=-1;
        InterruptiblePages(PageStore delegate){this.delegate=delegate;}
        void step(){if(remaining>0&&--remaining==0)throw new Failure(Reason.IO,"synthetic decision interruption");}
        public int pageBytes(){return delegate.pageBytes();}
        public long allocate(){step();return delegate.allocate();}
        public void read(long page,int offset,byte[] target,int start,int length){step();delegate.read(page,offset,target,start,length);}
        public void write(long page,int offset,byte[] source,int start,int length){step();delegate.write(page,offset,source,start,length);}
        public void release(long page){delegate.release(page);}
        public void flush(){delegate.flush();}
        public Statistics statistics(){return delegate.statistics();}
        public void close(){delegate.close();}
    }
}
