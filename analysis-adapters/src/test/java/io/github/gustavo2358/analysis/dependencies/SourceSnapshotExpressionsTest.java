package io.github.gustavo2358.analysis.dependencies;

import io.github.gustavo2358.analysis.adapters.*;
import io.github.gustavo2358.analysis.dependencies.source.*;
import io.github.gustavo2358.analysis.solver.*;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

final class SourceSnapshotExpressionsTest {
    @TempDir Path directory;
    private record Leaf(int kind,int transforms,String payload) { }
    private record Walk(NominalValues.Term term,int mask) { }
    private static AnalysisResources resources(){return new AnalysisResources(new AnalysisResources.Limits(1048576,16384,0,256L*1024*1024,2,Long.MAX_VALUE,0));}
    private static void zero(AnalysisResources resources){for(var pool:AnalysisResources.Pool.values())assertEquals(0,resources.used(pool));}
    private static SourceAdmissionSnapshot admit(byte[] wire,PageStore pages,AnalysisResources resources)throws Exception {
        return SourceAdmissionSnapshot.admit(new QualifiedSourceJson().decodeSnapshot(new ByteArrayInputStream(wire),pages,resources),pages,resources);
    }
    private static byte[] wire(List<NominalValues.Term> terms)throws Exception {
        var writes=new ArrayList<NominalValues.Assignment>();for(int i=0;i<terms.size();i++)writes.add(new NominalValues.Assignment("s"+i,"v0",terms.get(i)));
        return SourceNominalDemandTest.wire(new NominalValues("NOMINAL_TEXT_SOURCE_V4",List.of(new NominalValues.Symbol("v0",8)),writes,List.of(),List.of()),1);
    }
    private static long term(SourceAdmissionSnapshot admission,int ordinal) {
        var source=admission.snapshot();long nominal=source.field(admission.unit(0),SourceShape.UNIT_EVIDENCE,12),facts=source.field(nominal,SourceShape.NOMINAL_VALUE_EVIDENCE,0),writes=source.field(facts,SourceShape.NOMINAL_VALUES,2);
        return source.field(source.element(writes,SourceShape.ASSIGNMENT,ordinal),SourceShape.ASSIGNMENT,2);
    }
    /** Independent flattening of supported unary compositions, not compilation/canonical IDs. */
    private static Set<Leaf> oracle(NominalValues.Term term) {
        var result=new HashSet<Leaf>();var pending=new ArrayDeque<Walk>();pending.add(new Walk(term,0));
        while(!pending.isEmpty()) {
            var item=pending.removeFirst();var value=item.term();int mask=item.mask();
            if(value.kind().equals("CHOICE")){for(var child:value.arguments())pending.add(new Walk(child,mask));continue;}
            if(!value.arguments().isEmpty()) {
                int bit=switch(value.kind()){case "UPPER_ASCII"->1;case "TRIM_LEADING_SPACES"->2;case "TRIM_TRAILING_SPACES"->4;case "TRIM_SPACES"->6;default->throw new AssertionError(value.kind());};
                pending.add(new Walk(value.arguments().getFirst(),mask|bit));continue;
            }
            int kind=switch(value.kind()){case "READ"->1;case "LITERAL","SPACES"->2;default->3;};
            result.add(new Leaf(kind,kind==3?0:mask,value.kind().equals("SPACES")?" ":kind==3?"":value.value()));
        }return result;
    }
    private static Set<Leaf> projection(SourceSnapshotExpressions expressions,SourceAdmissionSnapshot admission,long root) {
        var result=new HashSet<Leaf>();var pending=new ArrayDeque<Long>();var seen=new HashSet<Long>();pending.add(root);
        while(!pending.isEmpty()) {
            long node=pending.removeFirst();if(!seen.add(node))continue;int kind=expressions.kind(node);
            if(kind==4){long prior=0;for(long i=0;i<expressions.argumentCount(node);i++){long child=expressions.argument(node,i);assertTrue(child>prior,"sorted unique canonical arguments");prior=child;pending.add(child);}continue;}
            String payload="";long reference=expressions.payload(node);
            if(reference==-1)payload=" ";else if(reference!=0)try(var text=admission.snapshot().project(reference)){payload=text.value();}
            result.add(new Leaf(kind,expressions.transforms(node),payload));
        }return result;
    }
    private static NominalValues.Term unary(String kind,NominalValues.Term term){return new NominalValues.Term(kind,"",List.of(term));}
    @Test void allEightContextsChoiceDuplicatesUnknownAndUtf16MatchIndependentAndResidentPrograms()throws Exception {
        var base=new NominalValues.Term("CHOICE","",List.of(new NominalValues.Term("READ","v0"),new NominalValues.Term("LITERAL"," a\u0000🚀\ud800 z "),new NominalValues.Term("SPACES",""),new NominalValues.Term("LITERAL"," "),new NominalValues.Term("UNKNOWN",""),new NominalValues.Term("LOW_VALUES",""),new NominalValues.Term("HIGH_VALUES","")));
        var terms=new ArrayList<NominalValues.Term>();
        for(int mask=0;mask<8;mask++) {
            var value=base;if((mask&1)!=0)value=unary("UPPER_ASCII",value);if((mask&2)!=0)value=unary("TRIM_LEADING_SPACES",value);if((mask&4)!=0)value=unary("TRIM_TRAILING_SPACES",value);
            terms.add(value);terms.add(new NominalValues.Term("CHOICE","",List.of(value,value)));
        }
        var resources=resources();byte[] wire=wire(terms);
        try(var pages=new FilePageStore(directory,4096,4,resources,AnalysisResources.Phase.INDEX);var admission=admit(wire,pages,resources);var expressions=SourceSnapshotExpressions.prepare(admission,pages,resources);var resident=new SourceExpressions(resources)) {
            for(int i=0;i<terms.size();i++) {
                var expected=oracle(terms.get(i));long compiled=expressions.compile(term(admission,i));assertEquals(expected,projection(expressions,admission,compiled));
                int root=resident.compile(terms.get(i));var actual=new HashSet<Leaf>();var pending=new ArrayDeque<Integer>();pending.add(root);
                while(!pending.isEmpty()){int node=pending.removeFirst();if(resident.kind(node)==4){for(int j=0;j<resident.count(node);j++)pending.add(resident.argument(node,j));}else actual.add(new Leaf(resident.kind(node),resident.transforms(node),resident.payload(node)));}
                assertEquals(expected,actual);assertEquals(compiled,expressions.compile(term(admission,i)));
                if(i%2==1)assertEquals(expressions.compile(term(admission,i-1)),compiled,"singleton duplicate CHOICE collapses");
            }
            assertTrue(expressions.memoEntries()<=8L*128,"bounded specialization,not paths");
        }zero(resources);
    }
    @Test void deepPhysicalUnaryInputUsesPagedFramesAndFixedControlsWithoutRecursiveResidentAst()throws Exception {
        byte[] small=wire(List.of(new NominalValues.Term("READ","v0")));String original=new String(small,StandardCharsets.UTF_8),leaf="{\"kind\":\"READ\",\"value\":\"v0\"}";assertTrue(original.contains(leaf));long fixed=-1;
        for(int depth:new int[]{1,4,16,64,256,1024,2048}) {
            String nested="{\"kind\":\"UPPER_ASCII\",\"value\":\"\",\"arguments\":[".repeat(depth)+leaf+"]}".repeat(depth);byte[] wire=original.replace(leaf,nested).getBytes(StandardCharsets.UTF_8);var resources=resources();
            try(var pages=new FilePageStore(directory,4096,4,resources,AnalysisResources.Phase.INDEX);var admission=admit(wire,pages,resources)) {
                long heap=resources.heapUsed(),baseline=pages.statistics().livePages();
                try(var expressions=SourceSnapshotExpressions.prepare(admission,pages,resources)) {
                    long delta=resources.heapUsed()-heap;if(fixed<0)fixed=delta;assertEquals(fixed,delta);long root=expressions.compile(term(admission,0));
                    assertEquals(Set.of(new Leaf(1,1,"v0")),projection(expressions,admission,root));assertEquals(depth+1,expressions.memoEntries());assertEquals(1,expressions.nodeCount());
                    assertEquals(root,expressions.compile(term(admission,0)));
                }assertEquals(baseline,pages.statistics().livePages());
            }zero(resources);
        }
    }
    @Test void sharedDagAndWideChoiceHaveLinearStoredIncidenceAndCanonicalPermutation()throws Exception {
        int n=1024;var leaves=new ArrayList<NominalValues.Term>();for(int i=0;i<n;i++)leaves.add(new NominalValues.Term("LITERAL","x"+i));
        var first=new NominalValues.Term("CHOICE","",leaves);Collections.reverse(leaves);var second=new NominalValues.Term("CHOICE","",leaves);
        var shared=new NominalValues.Term("LITERAL","leaf");for(int i=0;i<12;i++)shared=new NominalValues.Term("CHOICE","",List.of(shared,shared));
        var resources=resources();
        try(var pages=new FilePageStore(directory,4096,4,resources,AnalysisResources.Phase.INDEX);var admission=admit(wire(List.of(first,second,shared)),pages,resources);var expressions=SourceSnapshotExpressions.prepare(admission,pages,resources)) {
            long one=expressions.compile(term(admission,0)),two=expressions.compile(term(admission,1));assertEquals(one,two);assertEquals(n,expressions.argumentCount(one));assertEquals(oracle(first),projection(expressions,admission,one));
            long small=expressions.compile(term(admission,2));assertEquals(Set.of(new Leaf(2,0,"leaf")),projection(expressions,admission,small));assertEquals(n+2,expressions.nodeCount());assertEquals(n,expressions.retainedArguments());
        }zero(resources);
    }
    @Test void foreignContextAndInvalidHandleAbortOnlyDerivedProgramWhileParentExpiryIsImmediate()throws Exception {
        var resources=resources();var foreign=resources();
        try(var pages=new FilePageStore(directory,4096,4,resources,AnalysisResources.Phase.INDEX);var other=new FilePageStore(directory,4096,4,resources,AnalysisResources.Phase.INDEX);var admission=admit(wire(List.of(new NominalValues.Term("LITERAL","X"))),pages,resources)) {
            long baseline=pages.statistics().livePages();assertThrows(IllegalArgumentException.class,()->SourceSnapshotExpressions.prepare(admission,other,resources));assertThrows(IllegalArgumentException.class,()->SourceSnapshotExpressions.prepare(admission,pages,foreign));
            try(var expressions=SourceSnapshotExpressions.prepare(admission,pages,resources)){assertThrows(IllegalArgumentException.class,()->expressions.compile(admission.unit(0)));assertThrows(IllegalStateException.class,expressions::nodeCount);}
            assertEquals(baseline,pages.statistics().livePages());var expressions=SourceSnapshotExpressions.prepare(admission,pages,resources);expressions.compile(term(admission,0));admission.snapshot().close();assertEquals(0,pages.statistics().livePages());assertThrows(IllegalStateException.class,expressions::nodeCount);expressions.close();
        }zero(resources);zero(foreign);
    }
    @Test void oddWidePermutationsAndNestedChoiceVectorsKeepExactCanonicalArguments()throws Exception {
        var random=new Random(912741);
        for(int n:new int[]{2,3,5,7,15,31,127}) {
            var leaves=new ArrayList<NominalValues.Term>();for(int i=0;i<n;i++)leaves.add(new NominalValues.Term("LITERAL",i%2==0?"Aa"+i:"BB"+i));
            var first=new NominalValues.Term("CHOICE","",leaves);Collections.shuffle(leaves,random);leaves.add(leaves.getFirst());var second=new NominalValues.Term("CHOICE","",leaves);
            var nested=new NominalValues.Term("CHOICE","",List.of(first,new NominalValues.Term("LITERAL","nested")));var resources=resources();
            try(var pages=new FilePageStore(directory,4096,4,resources,AnalysisResources.Phase.INDEX);var admission=admit(wire(List.of(first,second,nested)),pages,resources);var expressions=SourceSnapshotExpressions.prepare(admission,pages,resources)) {
                long root=expressions.compile(term(admission,0));assertEquals(root,expressions.compile(term(admission,1)));assertEquals(n,expressions.argumentCount(root));assertEquals(oracle(first),projection(expressions,admission,root));
                assertEquals(oracle(nested),projection(expressions,admission,expressions.compile(term(admission,2))));assertEquals(n+2,expressions.retainedArguments());
            }zero(resources);
        }
    }
    @Test void preallocationDenialsAndNativeOwnerCloseNeverLeakOrStealBorrowedStorage()throws Exception {
        var resources=new AnalysisResources(new AnalysisResources.Limits(64L*1024*1024,16L*1024*1024,0,256L*1024*1024,8,Long.MAX_VALUE,0));
        try(var input=new DataflowAirReader().readSnapshot(Path.of("src/test/resources/cp6/dynamic-x8.air.json"),resources);var pages=new FilePageStore(directory,4096,4,resources,AnalysisResources.Phase.INDEX)) {
            var program=new SnapshotProgram(input.checked(),input.newIdentityStorage(),input.newOrderStorage(),resources);
            try {
                var execution=AnalysisResources.executionFor(program);
                try(var admission=admit(wire(List.of(new NominalValues.Term("READ","v0"))),pages,execution)) {
                    long baseline=pages.statistics().livePages();
                    for(int available:new int[]{1023,1024+511,1024+512+3071})try(var pressure=execution.reserve(AnalysisResources.Pool.RESIDENT,resources.limits().heapBytes()-resources.heapUsed()-available,AnalysisResources.Phase.INDEX)) {
                        assertNotNull(pressure);assertThrows(AnalysisResources.Exhausted.class,()->SourceSnapshotExpressions.prepare(admission,pages,execution));assertEquals(baseline,pages.statistics().livePages());assertEquals(1,admission.unitCount());
                    }
                    try(var expressions=SourceSnapshotExpressions.prepare(admission,pages,execution)) {
                        expressions.compile(term(admission,0));program.close();assertEquals(0,pages.statistics().livePages());zero(execution);assertThrows(IllegalStateException.class,expressions::nodeCount);
                    }
                }
            }finally{program.close();}
        }zero(resources);
    }
    @Test void everyPrimitiveCompilationAndQueryFaultPreservesPrimaryAndRetiresDerivedState()throws Exception {
        byte[] wire=wire(List.of(new NominalValues.Term("CHOICE","",List.of(new NominalValues.Term("READ","v0"),new NominalValues.Term("LITERAL","X")))));
        for(int operation=0;operation<4;operation++) {
            var resources=resources();
            try(var pages=new FilePageStore(directory,4096,4,resources,AnalysisResources.Phase.INDEX)) {
                var primary=new PageStore.Failure(PageStore.Reason.IO,"independent SOURCE expression page failure");var port=new FaultPages(pages,primary);
                try(var admission=admit(wire,port,resources)) {
                    long baseline=pages.statistics().livePages();
                    try(var expressions=SourceSnapshotExpressions.prepare(admission,port,resources)) {
                        long term=term(admission,0);
                        if(operation<3){port.arm(operation);assertSame(primary,assertThrows(PageStore.Failure.class,()->expressions.compile(term)));}
                        else {long root=expressions.compile(term);port.arm(1);assertSame(primary,assertThrows(PageStore.Failure.class,()->expressions.argument(root,0)));}
                        assertThrows(IllegalStateException.class,expressions::nodeCount);
                    }
                    try{admission.snapshot();assertEquals(baseline,pages.statistics().livePages());}catch(IllegalStateException expired){assertEquals(0,pages.statistics().livePages());}assertFalse(port.closed);
                }assertEquals(0,pages.statistics().livePages());long borrowed=pages.allocate();pages.release(borrowed);
            }zero(resources);
        }
    }
    private static final class FaultPages implements PageStore {
        private final PageStore delegate;private final Failure primary;private int operation=-1;private boolean closed;
        FaultPages(PageStore delegate,Failure primary){this.delegate=delegate;this.primary=primary;}
        void arm(int operation){this.operation=operation;}
        private void fail(int candidate){if(candidate==operation){operation=-1;throw primary;}}
        @Override public int pageBytes(){return delegate.pageBytes();}
        @Override public long allocate(){fail(0);return delegate.allocate();}
        @Override public void read(long p,int offset,byte[] bytes,int at,int count){fail(1);delegate.read(p,offset,bytes,at,count);}
        @Override public void write(long p,int offset,byte[] bytes,int at,int count){fail(2);delegate.write(p,offset,bytes,at,count);}
        @Override public void release(long p){delegate.release(p);}
        @Override public void readForCleanup(long p,int offset,byte[] bytes,int at,int count){delegate.readForCleanup(p,offset,bytes,at,count);}
        @Override public void releaseForCleanup(long p){delegate.releaseForCleanup(p);}
        @Override public void flush(){delegate.flush();}
        @Override public Statistics statistics(){return delegate.statistics();}
        @Override public void close(){closed=true;delegate.close();}
    }
}
