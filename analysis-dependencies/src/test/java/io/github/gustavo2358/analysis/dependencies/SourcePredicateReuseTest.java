package io.github.gustavo2358.analysis.dependencies;

import io.github.gustavo2358.analysis.dependencies.source.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static io.github.gustavo2358.analysis.dependencies.source.QualifiedSourceDependencies.*;
import static org.junit.jupiter.api.Assertions.*;

final class SourcePredicateReuseTest {
    private static UnitEvidence fixture(int n,boolean transformed,boolean mixed) {
        var base=SourceValuesProviderTest.fixture(List.of(),List.of(),List.of());var old=base.nominalValues().orElseThrow();
        var left=new ArrayList<NominalValues.Term>();var right=new ArrayList<NominalValues.Term>();
        for(int i=0;i<n;i++) {
            left.add(new NominalValues.Term("LITERAL","NAME"+i));right.add(new NominalValues.Term("LITERAL",transformed?"name"+i:"NAME"+i));
        }
        var p=new NominalValues.Term("CHOICE","",left);var q=new NominalValues.Term("CHOICE","",right);
        NominalValues.Term predicateLeft=new NominalValues.Term("READ","P"),predicateRight=new NominalValues.Term("READ","Q");
        if(mixed)predicateRight=new NominalValues.Term("CHOICE","",List.of(predicateLeft,predicateRight));
        if(transformed)predicateRight=new NominalValues.Term("UPPER_ASCII","",List.of(predicateRight));
        var condition=new NominalValues.Condition("s1",new NominalValues.Predicate("EQ",List.of(predicateLeft,predicateRight),List.of()));
        var facts=new NominalValues("NOMINAL_TEXT_SOURCE_V4",List.of(new NominalValues.Symbol("P",32),new NominalValues.Symbol("Q",32)),List.of(new NominalValues.Assignment("s0","P",p),new NominalValues.Assignment("s0","Q",q)),List.of(condition),old.facts().queries());
        var evidence=new NominalValueEvidence(facts,old.declarations(),old.seeds(),List.of(new NominalValueEvidence.Branch("d2",true)),old.uncertainties());
        return new UnitEvidence(base.unit(),base.controlAvailable(),base.statements(),base.occurrences(),base.targets(),base.nodes(),base.derivations(),base.selections(),base.events(),base.guards(),base.proofs(),base.frontiers(),Optional.of(evidence));
    }
    private static void verify(int n,boolean transformed,boolean mixed) {
        var provider=new SourceValuesProvider(fixture(n,transformed,mixed),Set.of("s2"));
        var candidates=provider.candidates("s2");assertEquals(n,candidates.size());
        var expected=new TreeSet<String>();for(int i=0;i<n;i++)expected.add("NAME"+i+" ".repeat(32-("NAME"+i).length()));
        assertEquals(expected,new TreeSet<>(candidates.stream().map(SourceValuesProvider.Candidate::rawValue).toList()));
        for(var candidate:candidates)assertEquals(List.of(new SourceValuesProvider.Evidence("ASSIGNMENT","s0",SourceValuesProviderTest.ORIGIN)),candidate.support().evidence());
        var cost=provider.stateStatistics();
        assertTrue(cost.predicateClassVisits()<=4L*n,"refinement rescanned complete candidate classes: "+cost);
        assertTrue(cost.transformedCandidates()<=8L*n,"unaffected choices were transformed for every refinement: "+cost);
        assertFalse(provider.limited());
    }
    @Test void ordinaryEqualityRefinementUsesSharedPrimitiveClassFacts(){verify(128,false,false);}
    @Test void unchangedTransformOperandIsPreparedOnceAcrossEveryCandidate(){verify(128,true,false);}
    @Test void transformsDistributeOverMixedChoicesWithoutRepeatingTheirUnchangedSide(){verify(128,true,true);}
}
