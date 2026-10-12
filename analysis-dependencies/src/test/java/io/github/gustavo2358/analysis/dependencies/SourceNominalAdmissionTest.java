package io.github.gustavo2358.analysis.dependencies;

import io.github.gustavo2358.analysis.dependencies.source.NominalValues;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import io.github.gustavo2358.analysis.dependencies.source.NominalValueEvidence;
import static io.github.gustavo2358.analysis.dependencies.source.QualifiedSourceDependencies.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class SourceNominalAdmissionTest {
    private static NominalValues.Term read(String symbol){return new NominalValues.Term("READ",symbol);}
    private static NominalValues.Term unary(NominalValues.Term term,int depth) {
        for(int i=0;i<depth;i++)term=new NominalValues.Term("TRIM_SPACES","",List.of(term));return term;
    }
    private static NominalValues facts(String authority,NominalValues.Term term,List<NominalValues.Condition> conditions) {
        return new NominalValues(authority,List.of(new NominalValues.Symbol("P",8)),List.of(new NominalValues.Assignment("write","P",term)),conditions,List.of(new NominalValues.Query("query","P")));
    }
    @Test void deepTermAndPredicateAdmissionUsesNoJavaCallStack() {
        var term=unary(read("P"),12000);
        var predicate=new NominalValues.Predicate("EQ",List.of(read("P"),new NominalValues.Term("LITERAL","A")),List.of());
        for(int i=0;i<12000;i++)predicate=new NominalValues.Predicate("NOT",List.of(),List.of(predicate));
        var condition=new NominalValues.Condition("branch",predicate);
        assertDoesNotThrow(()->facts("NOMINAL_TEXT_SOURCE_V3",term,List.of(condition)));
    }
    @Test void sharedTermAndPredicateDagsAreAdmittedWithoutEnumeratingTheirPaths() {
        NominalValues.Term term=read("P");
        for(int i=0;i<30;i++)term=new NominalValues.Term("CHOICE","",List.of(term,term));
        var predicate=new NominalValues.Predicate("EQ",List.of(term,term),List.of());
        for(int i=0;i<30;i++)predicate=new NominalValues.Predicate("AND",List.of(),List.of(predicate,predicate));
        var root=term;var condition=new NominalValues.Condition("branch",predicate);
        var admitted=assertDoesNotThrow(()->facts("NOMINAL_TEXT_SOURCE_V4",root,List.of(condition)));
        assertSame(root,admitted.assignments().getFirst().source());assertSame(condition,admitted.conditions().getFirst());
    }
    @Test void deepAndSharedInvalidLeavesRetainTheirSpecificAdmissionFailures() {
        var invalid=unary(read("MISSING"),12000);
        assertEquals("nominal read reference",assertThrows(IllegalArgumentException.class,()->facts("NOMINAL_TEXT_SOURCE_V3",invalid,List.of())).getMessage());
        var choice=new NominalValues.Term("CHOICE","",List.of(read("P"),read("P")));
        var nested=unary(choice,12000);
        assertEquals("choice requires V4",assertThrows(IllegalArgumentException.class,()->facts("NOMINAL_TEXT_SOURCE_V3",nested,List.of())).getMessage());
        NominalValues.Term shared=read("MISSING");
        for(int i=0;i<30;i++)shared=new NominalValues.Term("CHOICE","",List.of(shared,shared));
        var root=shared;
        assertEquals("nominal read reference",assertThrows(IllegalArgumentException.class,()->facts("NOMINAL_TEXT_SOURCE_V4",root,List.of())).getMessage());
    }
    @Test void extendedExpressionsStillRequireTheirPublishedAuthority() {
        var extended=unary(read("P"),12000);
        for(var authority:List.of("NOMINAL_TEXT_SOURCE_V1","NOMINAL_TEXT_SOURCE_V2"))
            assertEquals("expression requires V3",assertThrows(IllegalArgumentException.class,()->facts(authority,extended,List.of())).getMessage());
    }
    @Test void constructionProgressStopsInsideDeepAdmissionWithoutBecomingModelState() {
        var term=unary(read("P"),12000);
        var symbols=List.of(new NominalValues.Symbol("P",8));
        var assignments=List.of(new NominalValues.Assignment("write","P",term));
        var queries=List.of(new NominalValues.Query("query","P"));
        int[] steps={0};var stopped=new IllegalStateException("construction budget");
        assertSame(stopped,assertThrows(IllegalStateException.class,()->new NominalValues(
            "NOMINAL_TEXT_SOURCE_V3",symbols,assignments,List.of(),queries,List.of(),
            ()->{if(++steps[0]==100)throw stopped;})));
        assertEquals(100,steps[0]);
        int[] completed={0};var admitted=new NominalValues("NOMINAL_TEXT_SOURCE_V3",symbols,assignments,
            List.of(),queries,List.of(),()->completed[0]++);
        assertTrue(completed[0]>12000);assertSame(term,admitted.assignments().getFirst().source());
        int before=completed[0];admitted.symbols().getFirst();admitted.assignments().getFirst();
        admitted.validate(Set.of("P"),Set.of("write","query"));assertEquals(before,completed[0]);
        var ordinary=facts("NOMINAL_TEXT_SOURCE_V3",read("P"),List.of());
        var checked=new NominalValues(ordinary.authority(),ordinary.symbols(),ordinary.assignments(),
            ordinary.conditions(),ordinary.queries(),ordinary.tableFields(),()->completed[0]++);
        assertEquals(ordinary,checked);assertEquals(ordinary.hashCode(),checked.hashCode());
        assertEquals(ordinary.toString(),checked.toString());
    }
    private static UnitEvidence readmit(UnitEvidence base,Runnable progress) {
        return new UnitEvidence(base.unit(),base.controlAvailable(),base.statements(),base.occurrences(),
            base.targets(),base.nodes(),base.derivations(),base.selections(),base.events(),base.guards(),
            base.proofs(),base.frontiers(),base.nominalValues(),base.nativeFiles(),progress);
    }
    @Test void unitAndDocumentAdmissionShareProgressAndRetainOnlyImmutableEvidence() {
        var base=SourceValuesProviderTest.fixture(List.of(),List.of(),List.of());
        var nominal=base.nominalValues().orElseThrow();int[] steps={0};
        var checkedNominal=new NominalValueEvidence(nominal.facts(),nominal.declarations(),nominal.seeds(),
            nominal.branches(),nominal.uncertainties(),()->steps[0]++);
        assertEquals(nominal,checkedNominal);assertTrue(steps[0]>5);
        var admitted=readmit(base,()->steps[0]++);assertEquals(base,admitted);
        int total=steps[0];int stopAt=total/2;steps[0]=0;
        var stopped=new IllegalStateException("unit construction budget");
        assertSame(stopped,assertThrows(IllegalStateException.class,()->readmit(base,()->{
            if(++steps[0]==stopAt)throw stopped;
        })));assertEquals(stopAt,steps[0]);
        var document=new Document("cobol-semantic-product","1.0.0","0".repeat(64));
        var air=List.of(new AirCorrelation("publication","1".repeat(64)));
        for(var version:List.of("1.0.0","1.1.0","1.2.0","1.3.0","1.4.0","1.5.0","1.6.0")) {
            var ordinary=new io.github.gustavo2358.analysis.dependencies.source.QualifiedSourceDependencies(
                "qualified-source-dependencies",version,"synthetic",document,air,List.of(base));
            var checked=new io.github.gustavo2358.analysis.dependencies.source.QualifiedSourceDependencies(
                ordinary.schema(),version,ordinary.producer(),document,air,List.of(admitted),()->steps[0]++);
            int completed=steps[0];assertEquals(ordinary,checked);assertEquals(ordinary.hashCode(),checked.hashCode());
            assertEquals(ordinary.toString(),checked.toString());assertEquals(completed,steps[0]);
        }
        int completed=steps[0];assertEquals(base.hashCode(),admitted.hashCode());assertEquals(base.toString(),admitted.toString());
        assertEquals(completed,steps[0]);assertThrows(UnsupportedOperationException.class,()->admitted.nodes().clear());
    }
    private static UnitEvidence executable(NominalValues.Term term,NominalValues.Predicate predicate) {
        var base=SourceValuesProviderTest.fixture(List.of(),List.of(),List.of());var old=base.nominalValues().orElseThrow();
        var conditions=predicate==null?List.<NominalValues.Condition>of():List.of(new NominalValues.Condition("s1",predicate));
        var branches=predicate==null?List.<NominalValueEvidence.Branch>of():List.of(new NominalValueEvidence.Branch("d2",true));
        var facts=new NominalValues("NOMINAL_TEXT_SOURCE_V4",old.facts().symbols(),List.of(new NominalValues.Assignment("s0","P",term)),conditions,old.facts().queries());
        var evidence=new NominalValueEvidence(facts,old.declarations(),old.seeds(),branches,old.uncertainties());
        return new UnitEvidence(base.unit(),base.controlAvailable(),base.statements(),base.occurrences(),base.targets(),base.nodes(),base.derivations(),base.selections(),base.events(),base.guards(),base.proofs(),base.frontiers(),Optional.of(evidence));
    }
    @Test void sharedChoiceAndPredicateDagsReachProductionCandidatesWithOneCompleteAssignmentProof() {
        NominalValues.Term term=new NominalValues.Term("LITERAL","REAL0001");
        for(int i=0;i<30;i++)term=new NominalValues.Term("CHOICE","",List.of(term,term));
        var predicate=new NominalValues.Predicate("EQ",List.of(read("P"),new NominalValues.Term("LITERAL","REAL0001")),List.of());
        for(int i=0;i<30;i++)predicate=new NominalValues.Predicate("AND",List.of(),List.of(predicate,predicate));
        var provider=new SourceValuesProvider(executable(term,predicate),Set.of("s2"));
        var candidates=provider.candidates("s2");assertEquals(1,candidates.size());assertEquals("REAL0001",candidates.getFirst().rawValue());
        assertEquals(List.of(new SourceValuesProvider.Evidence("ASSIGNMENT","s0",SourceValuesProviderTest.ORIGIN)),candidates.getFirst().support().evidence());
        assertFalse(provider.limited());
    }
    @Test void deepProductionTransformsAndPredicateTruthPreserveTheirSelectedBranch() {
        var term=unary(new NominalValues.Term("LITERAL","REAL0001"),12000);
        var predicate=new NominalValues.Predicate("EQ",List.of(read("P"),new NominalValues.Term("LITERAL","REAL0001")),List.of());
        for(int i=0;i<12000;i++)predicate=new NominalValues.Predicate("NOT",List.of(),List.of(predicate));
        assertEquals("REAL0001",new SourceValuesProvider(executable(term,predicate),Set.of("s2")).candidates("s2").getFirst().rawValue());
        var opposite=new NominalValues.Predicate("NOT",List.of(),List.of(predicate));
        assertTrue(new SourceValuesProvider(executable(term,opposite),Set.of("s2")).candidates("s2").isEmpty());
    }

}
