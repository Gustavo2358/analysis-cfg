package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.analysis.dependencies.SourceValuesProvider;
import io.github.gustavo2358.analysis.dependencies.source.*;
import io.github.gustavo2358.analysis.solver.*;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static io.github.gustavo2358.analysis.dependencies.source.QualifiedSourceDependencies.*;
import static org.junit.jupiter.api.Assertions.*;

final class SourcePagedProviderTest {
    @TempDir Path directory;
    private static UnitEvidence chain(int n) {
        var location=new Location("synthetic.cbl",1,0,1,8);var origin=new Provenance(location,location,List.of(),true);
        var unit=new UnitId("synthetic",List.of(0),"SAMPLE");var support=new Support("ENTRY_UNKNOWN",List.of(),List.of(),"NONE");
        var statements=new ArrayList<Statement>();var nodes=new ArrayList<Node>();var links=new ArrayList<Derivation>();
        var writes=new ArrayList<NominalValues.Assignment>();
        for(int i=0;i<=n;i++) {
            statements.add(new Statement(new StatementId(unit,"s"+i),origin));nodes.add(new Node("n"+i,"ROOT","s"+i,support));
            links.add(new Derivation("d"+i,i==0?List.of():List.of("n"+(i-1)),"n"+i,List.of(),i==0?"PRIMARY_ENTRY":"flow",List.of("p"),List.of()));
            if(i<n)writes.add(new NominalValues.Assignment("s"+i,"P",new NominalValues.Term("READ","P")));
        }
        var statement=new StatementId(unit,"s"+n);
        var occurrence=new Occurrence(statement,"COBOL","CALL","PROGRAM","cobol-zos-dynamic-call-minimal@1","COMPUTED",List.of(new Operand(new OperandId(statement,"o"),origin)),List.of(),true,List.of("n"+n));
        var facts=new NominalValues("NOMINAL_TEXT_SOURCE_V1",List.of(new NominalValues.Symbol("P",8)),writes,List.of(),List.of(new NominalValues.Query("s"+n,"P")));
        var evidence=new NominalValueEvidence(facts,List.of(new NominalValueEvidence.Declaration("P",origin)),List.of(new NominalValueEvidence.Seed("P","SYNPROG","DECLARATIVE_POSSIBILITY",origin)),List.of(),List.of());
        return new UnitEvidence(unit,true,statements,List.of(occurrence),List.of(),nodes,links,List.of(),List.of(),List.of(),List.of(new Proof("p","LOCAL_GRAMMAR","fixture",origin,List.of())),List.of(),Optional.of(evidence));
    }
    @Test void forcedSpillMatchesAllResidentProofsWithSmallManagedWorkingSetAndBorrowedStoreOwnership() {
        int n=256;var unit=chain(n);var expected=new SourceValuesProvider(unit,Set.of("s"+n));
        var resources=new AnalysisResources(new AnalysisResources.Limits(65536,16384,0,64_000_000,2,1_000_000_000,1_000_000));
        try(var pages=new FilePageStore(directory,512,8,resources)) {
            long baseline=resources.heapUsed();var actual=new SourceValuesProvider(unit,Set.of("s"+n),pages,resources);
            assertEquals(expected.candidates("s"+n),actual.candidates("s"+n));
            assertEquals("SYNPROG ",actual.candidates("s"+n).getFirst().rawValue());
            assertEquals(n+1,actual.candidates("s"+n).getFirst().support().evidence().size());
            assertEquals(n+1,actual.workItems());assertFalse(actual.limited());
            assertTrue(resources.heapPeak()<=65536);assertEquals(baseline,resources.heapUsed());
            assertEquals(0,pages.statistics().livePages());assertTrue(pages.statistics().evictions()>0);
            assertTrue(resources.used(AnalysisResources.Pool.TEMPORARY)>65536);
            long page=pages.allocate();pages.release(page); // provider closes its owners, never the borrowed store
        }
        assertEquals(0,resources.heapUsed());assertEquals(0,resources.used(AnalysisResources.Pool.TEMPORARY));
    }
    @Test void absentObservationsAllocateNoPagesAndDeniedCapacityCannotReturnSemanticPartial() {
        var unit=chain(4);var resources=new AnalysisResources(new AnalysisResources.Limits(8192,0,0,64_000_000,2,1_000_000_000,1_000_000));
        try(var pages=new FilePageStore(directory,512,1,resources)) {
            long heap=resources.heapUsed();var empty=new SourceValuesProvider(unit,Set.of(),pages,resources);
            assertEquals(List.of(),empty.candidates("s4"));assertEquals(0,empty.workItems());assertEquals(heap,resources.heapUsed());
            assertEquals(0,pages.statistics().pagesIssued());
            assertThrows(AnalysisResources.Exhausted.class,()->new SourceValuesProvider(unit,Set.of("s4"),pages,resources));
            assertEquals(heap,resources.heapUsed());assertEquals(0,pages.statistics().livePages());
        }
        assertEquals(0,resources.heapUsed());
    }
}
