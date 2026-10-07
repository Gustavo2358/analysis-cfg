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
