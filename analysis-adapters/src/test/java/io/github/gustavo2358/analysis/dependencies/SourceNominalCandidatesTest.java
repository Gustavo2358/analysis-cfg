package io.github.gustavo2358.analysis.dependencies;

import io.github.gustavo2358.analysis.adapters.QualifiedSourceJson;
import io.github.gustavo2358.analysis.adapters.FilePageStore;
import io.github.gustavo2358.analysis.dependencies.source.*;
import io.github.gustavo2358.analysis.solver.*;
import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static io.github.gustavo2358.analysis.dependencies.source.QualifiedSourceDependencies.*;
import static org.junit.jupiter.api.Assertions.*;

final class SourceNominalCandidatesTest {
    @TempDir Path directory;
    private static AnalysisResources resources(){return new AnalysisResources(new AnalysisResources.Limits(64L*1024*1024,16L*1024*1024,0,256L*1024*1024,8,Long.MAX_VALUE,0));}
    private static void zero(AnalysisResources resources){for(var pool:AnalysisResources.Pool.values())assertEquals(0,resources.used(pool),pool.toString());}
    private static void expire(SourceAdmissionSnapshot admission){admission.close();}
    private static SourceAdmissionSnapshot admit(UnitEvidence unit,PageStore pages,AnalysisResources resources)throws Exception{return SourceAdmissionSnapshot.admit(new QualifiedSourceJson().decodeSnapshot(new ByteArrayInputStream(SourceNominalSnapshotTest.wire(unit)),pages,resources),pages,resources);}
    private static String text(SourceSnapshot source,long ref){try(var p=source.project(ref)){return p.value();}}
    /** Independent small-only port projection; production must use blocks/handles. */
    static List<SourceValuesProvider.Candidate> projection(SourceNominalCandidates result,long query) {
        var source=result.admission().snapshot();var uncertainties=new ArrayList<NominalValueEvidence.Uncertainty>();
        for(long i=0;i<result.uncertaintyCount();i++){long row=result.uncertainty(i);uncertainties.add(new NominalValueEvidence.Uncertainty(text(source,source.field(row,SourceShape.UNCERTAINTY,0)),text(source,source.field(row,SourceShape.UNCERTAINTY,1)),SourceNominalSnapshotTest.provenance(source,source.field(row,SourceShape.UNCERTAINTY,2))));}
        var values=new ArrayList<SourceValuesProvider.Candidate>();
        for(long c=0;c<result.candidateCount(query);c++) {
            char[] raw=new char[Math.toIntExact(result.rawLength(query,c))];result.readRaw(query,c,0,raw,0,raw.length);
            var evidence=new ArrayList<SourceValuesProvider.Evidence>();
            for(long e=0;e<result.evidenceCount(query,c);e++)evidence.add(new SourceValuesProvider.Evidence(result.evidenceKind(query,c,e),text(source,result.evidenceReference(query,c,e)),SourceNominalSnapshotTest.provenance(source,result.evidenceProvenance(query,c,e))));
            var assumptions=new ArrayList<String>();for(int i=0;i<result.assumptionCount(query,c);i++)assumptions.add(result.assumption(query,c,i));
            values.add(new SourceValuesProvider.Candidate(new String(raw),new SourceValuesProvider.Support(SourceValuesProvider.PROFILE,evidence,assumptions,uncertainties)));
        }return values;
    }
    private void compare(UnitEvidence unit,List<String> expected)throws Exception {
        var resident=new SourceValuesProvider(unit,Set.of("s2"));assertEquals(expected,resident.candidates("s2").stream().map(SourceValuesProvider.Candidate::rawValue).toList());var resources=resources();
        try(var pages=new FilePageStore(directory,4096,4,resources,AnalysisResources.Phase.DOMAIN);var admission=admit(unit,pages,resources);var control=SourceControlSnapshot.prepare(admission,pages,resources)) {
            long baseline=pages.statistics().livePages();
            try(var result=SourceNominalCandidates.prepare(admission,0,q->true,control,pages,resources)) {
                assertEquals(1,result.queryCount());assertEquals(1,result.activeQueries());assertEquals(resident.workItems(),result.workItems());
                assertSame(admission,result.admission());assertEquals(0,result.unitOrdinal());
                assertEquals("s2",text(admission.snapshot(),admission.snapshot().field(result.query(0),SourceShape.QUERY,0)));
                assertEquals(resident.candidates("s2"),projection(result,0));
            }assertEquals(baseline,pages.statistics().livePages());assertEquals(1,admission.unitCount());
        }zero(resources);
    }
    @Test void orderedCandidatesFullEvidenceDedupAndAssumptionsMatchManualAndResidentOracles()throws Exception {
        var choice=new NominalValues.Term("CHOICE","",List.of(new NominalValues.Term("LITERAL","ZZZZ0001"),new NominalValues.Term("LITERAL","Aa000001"),new NominalValues.Term("LITERAL","BB000001"),new NominalValues.Term("LITERAL","Aa000001")));
        compare(SourceNominalSnapshotTest.fixture(List.of(new NominalValues.Assignment("s0","P",choice)),List.of(),List.of(),"",8,false),List.of("Aa000001","BB000001","ZZZZ0001"));
        compare(SourceNominalSnapshotTest.fixture(List.of(),List.of(),List.of(),"",8,true),List.of("PROGA001","PROGB001"));
        compare(SourceNominalSnapshotTest.fixture(List.of(new NominalValues.Assignment("s0","P",new NominalValues.Term("LITERAL","PROGA001"))),List.of(),List.of(),"P",1,false),List.of("P","PROGA001"));
    }
    @Test void fullProofKeyOrderDeduplicatesEqualDeclarationEvidenceWithoutLosingIncludeDuplicates()throws Exception {
        var base=SourceNominalSnapshotTest.fixture(List.of(),List.of(),List.of(),"",8,true);var nominal=base.nominalValues().orElseThrow();var facts=nominal.facts();
        var seeds=List.of(new NominalValueEvidence.Seed("Q","PROGA001","DECLARATIVE_POSSIBILITY",nominal.declarations().getLast().provenance()));
        var writes=List.of(new NominalValues.Assignment("s0","P",new NominalValues.Term("READ","Q")),new NominalValues.Assignment("s1","P",new NominalValues.Term("READ","P")));
        var changedFacts=new NominalValues(facts.authority(),facts.symbols(),writes,facts.conditions(),facts.queries(),facts.tableFields());
        var changed=new NominalValueEvidence(changedFacts,nominal.declarations(),seeds,nominal.branches(),nominal.uncertainties());
        var unit=new UnitEvidence(base.unit(),true,base.statements(),base.occurrences(),base.targets(),base.nodes(),base.derivations(),base.selections(),base.events(),base.guards(),base.proofs(),base.frontiers(),Optional.of(changed));
        compare(unit,List.of("PROGA001","PROGB001"));
        var resources=resources();try(var pages=new FilePageStore(directory,4096,4,resources,AnalysisResources.Phase.DOMAIN);var admission=admit(unit,pages,resources);var control=SourceControlSnapshot.prepare(admission,pages,resources);var result=SourceNominalCandidates.prepare(admission,0,q->true,control,pages,resources)) {
            var evidence=projection(result,0).getFirst().support().evidence();assertEquals(3,evidence.size());assertEquals(List.of("Q","s0","s1"),evidence.stream().map(SourceValuesProvider.Evidence::reference).toList());
            for(var e:evidence)assertEquals(2,e.provenance().includeChain().size());
        }zero(resources);
    }
    @Test void emptySelectionAndExpiredOwnersNeverExposePrivateValuesOrCloseBorrowedBackend()throws Exception {
        var unit=SourceNominalSnapshotTest.fixture(List.of(),List.of(),List.of(),"",8,false);var resources=resources();
        try(var pages=new FilePageStore(directory,4096,4,resources,AnalysisResources.Phase.DOMAIN);var admission=admit(unit,pages,resources);var control=SourceControlSnapshot.prepare(admission,pages,resources)) {
            long baseline=pages.statistics().livePages();var result=SourceNominalCandidates.prepare(admission,0,q->false,control,pages,resources);
            assertEquals(0,result.activeQueries());assertEquals(0,result.candidateCount(0));assertEquals(0,result.workItems());result.close();assertEquals(baseline,pages.statistics().livePages());
            var selected=SourceNominalCandidates.prepare(admission,0,q->true,control,pages,resources);expire(admission);assertThrows(IllegalStateException.class,()->selected.candidateCount(0));assertEquals(0,pages.statistics().livePages());long p=pages.allocate();pages.release(p);
        }zero(resources);
    }
    @Test void invalidRangesAndForeignContextAbortOnlyDerivedOwner()throws Exception {
        var unit=SourceNominalSnapshotTest.fixture(List.of(),List.of(),List.of(),"",8,false);var resources=resources();var foreign=resources();
        try(var pages=new FilePageStore(directory,4096,4,resources,AnalysisResources.Phase.DOMAIN);var admission=admit(unit,pages,resources);var control=SourceControlSnapshot.prepare(admission,pages,resources)) {
            long baseline=pages.statistics().livePages();assertThrows(IllegalArgumentException.class,()->SourceNominalCandidates.prepare(admission,0,q->true,control,pages,foreign));assertEquals(baseline,pages.statistics().livePages());
            try(var result=SourceNominalCandidates.prepare(admission,0,q->true,control,pages,resources)){assertThrows(IndexOutOfBoundsException.class,()->result.readRaw(0,0,9,new char[1],0,1));assertThrows(IllegalStateException.class,result::queryCount);}
            assertEquals(1,admission.unitCount());assertEquals(baseline,pages.statistics().livePages());
        }zero(resources);zero(foreign);
    }
    @Test void wideCandidateGeometryAndPhysicalPermutationsKeepFixedResidentDeliveryControls()throws Exception {
        long fixed=-1;
        for(int count:new int[]{1,4,16,64,256})for(boolean reverse:List.of(false,true)) {
            var terms=new ArrayList<NominalValues.Term>();var expected=new ArrayList<String>();
            for(int i=0;i<count;i++){String name=String.format(Locale.ROOT,"P%07d",i);expected.add(name);terms.add(new NominalValues.Term("LITERAL",name));}
            if(reverse)Collections.reverse(terms);terms.add(terms.getFirst());var choice=new NominalValues.Term("CHOICE","",terms);
            var unit=SourceNominalSnapshotTest.fixture(List.of(new NominalValues.Assignment("s0","P",choice)),List.of(),List.of(),"",8,false);var resources=resources();
            try(var pages=new FilePageStore(directory,4096,4,resources,AnalysisResources.Phase.DOMAIN);var admission=admit(unit,pages,resources);var control=SourceControlSnapshot.prepare(admission,pages,resources)) {
                long baseline=pages.statistics().livePages(),resident=resources.used(AnalysisResources.Pool.RESIDENT);
                try(var result=SourceNominalCandidates.prepare(admission,0,q->true,control,pages,resources)) {
                    long delta=resources.used(AnalysisResources.Pool.RESIDENT)-resident;if(fixed<0)fixed=delta;assertEquals(fixed,delta);
                    assertEquals(expected,projection(result,0).stream().map(SourceValuesProvider.Candidate::rawValue).toList());
                    assertEquals(new SourceValuesProvider(unit,Set.of("s2")).candidates("s2"),projection(result,0));assertEquals(0,resources.used(AnalysisResources.Pool.SCRATCH));
                }assertEquals(baseline,pages.statistics().livePages());
            }zero(resources);
        }
    }
    @Test void preallocationAndNestedFundingDenialsNeverPublishOrLeakAndCallbacksKeepPrimary()throws Exception {
        var unit=SourceNominalSnapshotTest.fixture(List.of(),List.of(),List.of(),"",8,false);var resources=resources();
        try(var pages=new FilePageStore(directory,4096,4,resources,AnalysisResources.Phase.DOMAIN);var admission=admit(unit,pages,resources);var control=SourceControlSnapshot.prepare(admission,pages,resources)) {
            long baseline=pages.statistics().livePages();
            for(long available:new long[]{2047,2048+4095,2048+4096+1023}) {
                try(var pressure=resources.reserve(AnalysisResources.Pool.RESIDENT,resources.limits().heapBytes()-resources.heapUsed()-available,AnalysisResources.Phase.SORT)) {
                    assertNotNull(pressure);assertThrows(AnalysisResources.Exhausted.class,()->SourceNominalCandidates.prepare(admission,0,q->true,control,pages,resources));
                }assertEquals(baseline,pages.statistics().livePages());assertEquals(1,admission.unitCount());
            }
            var primary=new IllegalStateException("independent query registration fault");assertSame(primary,assertThrows(IllegalStateException.class,()->SourceNominalCandidates.prepare(admission,0,q->{throw primary;},control,pages,resources)));assertEquals(baseline,pages.statistics().livePages());
            try(var result=SourceNominalCandidates.prepare(admission,0,q->true,control,pages,resources)){assertThrows(IndexOutOfBoundsException.class,()->result.uncertainty(2));}
            assertEquals(1,admission.unitCount());assertEquals(baseline,pages.statistics().livePages());
        }zero(resources);
    }
    @Test void primitiveSortingAndQueryFailuresPreservePrimaryAndRetireOnlyOwnedPages()throws Exception {
        var unit=SourceNominalSnapshotTest.fixture(List.of(),List.of(),List.of(),"",8,false);
        for(int operation:new int[]{0,1,2,3}) {
            var resources=resources();try(var pages=new FilePageStore(directory,4096,4,resources,AnalysisResources.Phase.DOMAIN)) {
                var primary=new PageStore.Failure(PageStore.Reason.IO,"independent ordered delivery fault");var port=new FaultPages(pages,primary);
                try(var admission=admit(unit,port,resources);var control=SourceControlSnapshot.prepare(admission,port,resources)) {
                    if(operation<3){port.operation=operation;assertSame(primary,assertThrows(PageStore.Failure.class,()->SourceNominalCandidates.prepare(admission,0,q->true,control,port,resources)));}
                    else try(var result=SourceNominalCandidates.prepare(admission,0,q->true,control,port,resources)){port.operation=1;assertSame(primary,assertThrows(PageStore.Failure.class,()->result.readRaw(0,0,0,new char[8],0,8)));assertThrows(IllegalStateException.class,result::queryCount);}
                    assertFalse(port.closed);
                }assertEquals(0,pages.statistics().livePages());long p=pages.allocate();pages.release(p);
            }zero(resources);
        }
    }
    private static final class FaultPages implements PageStore {
        private final PageStore delegate;private final Failure primary;private int operation=-1;private boolean closed;
        FaultPages(PageStore delegate,Failure primary){this.delegate=delegate;this.primary=primary;}
        private void fail(int candidate){if(candidate==operation){operation=-1;throw primary;}}
        @Override public int pageBytes(){return delegate.pageBytes();}
        @Override public long allocate(){fail(0);return delegate.allocate();}
        @Override public void read(long page,int offset,byte[] bytes,int at,int count){fail(1);delegate.read(page,offset,bytes,at,count);}
        @Override public void write(long page,int offset,byte[] bytes,int at,int count){fail(2);delegate.write(page,offset,bytes,at,count);}
        @Override public void release(long page){delegate.release(page);}
        @Override public void readForCleanup(long page,int offset,byte[] bytes,int at,int count){delegate.readForCleanup(page,offset,bytes,at,count);}
        @Override public void releaseForCleanup(long page){delegate.releaseForCleanup(page);}
        @Override public void flush(){delegate.flush();}
        @Override public Statistics statistics(){return delegate.statistics();}
        @Override public void close(){closed=true;delegate.close();}
    }
}
