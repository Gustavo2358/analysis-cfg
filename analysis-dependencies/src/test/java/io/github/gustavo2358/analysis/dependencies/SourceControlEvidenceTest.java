package io.github.gustavo2358.analysis.dependencies;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static io.github.gustavo2358.analysis.dependencies.source.QualifiedSourceDependencies.*;

class SourceControlEvidenceTest {
    @Test void sharedProgressRemainsActiveThroughProofAndNodePropagation() {
        int count=64;var proofs=new ArrayList<Proof>();
        for(int i=count-1;i>=0;i--)proofs.add(proof("p"+i,i==0,i==0?List.of():List.of("p"+(i-1))));
        // Past the two proof scans and dependency index: stop inside the propagation queue.
        int stopAt=2*count+(count-1)+17;int[] visits={0};
        var stopped=new IllegalStateException("shared owner stopped");
        assertSame(stopped,assertThrows(IllegalStateException.class,()->SourceControlEvidence.hypotheticalProofs(proofs,()->{
            if(++visits[0]==stopAt)throw stopped;
        })));
        assertEquals(stopAt,visits[0]);
        assertEquals(scalar(proofs),SourceControlEvidence.hypotheticalProofs(proofs,()->{}).ids());
        var base=SourceValuesProviderTest.fixture(List.of(),List.of(),List.of());
        var unit=new UnitEvidence(base.unit(),base.controlAvailable(),base.statements(),base.occurrences(),base.targets(),
            base.nodes(),base.derivations(),base.selections(),base.events(),base.guards(),
            List.of(proof("p",true,List.of())),base.frontiers(),base.nominalValues());
        assertEquals(Set.of("n0","n1","n2"),SourceControlEvidence.affected(unit,()->{}));
        visits[0]=0;
        assertSame(stopped,assertThrows(IllegalStateException.class,()->SourceControlEvidence.affected(unit,()->{
            if(++visits[0]==9)throw stopped;
        })));
        assertEquals(9,visits[0]);
    }
    private static Proof proof(String id,boolean seed,List<String> dependencies) {
        return new Proof(id,seed?"CONTROL_POSSIBILITY":"LOCAL_GRAMMAR","independent",SourceValuesProviderTest.ORIGIN,dependencies);
    }
    private static Set<String> scalar(List<Proof> proofs) {
        var result=new HashSet<String>();for(var p:proofs)if(p.kind().equals("CONTROL_POSSIBILITY"))result.add(p.id());
        boolean changed;do {changed=false;for(var p:proofs)for(var dependency:p.dependencies())if(result.contains(dependency)){changed|=result.add(p.id());break;}}while(changed);
        return result;
    }
    @Test void proofClosureMatchesIndependentScalarCyclesCollisionsAndPermutations() {
        var random=new Random(629103);
        for(int round=0;round<160;round++) {
            var proofs=new ArrayList<Proof>();
            proofs.add(proof("Aa",false,List.of("unseeded")));proofs.add(proof("BB",true,List.of()));
            proofs.add(proof("unseeded",false,List.of("Aa")));
            for(int i=0;i<40;i++) {
                var dependencies=new ArrayList<String>();for(int j=0;j<3;j++)if(random.nextBoolean())dependencies.add("p"+random.nextInt(40));
                if(random.nextBoolean())dependencies.add("BB");
                proofs.add(proof("p"+i,random.nextInt(12)==0,dependencies));
            }
            var expected=scalar(proofs);assertFalse(expected.contains("Aa"));
            Collections.shuffle(proofs,random);var actual=SourceControlEvidence.hypotheticalProofs(proofs);
            assertEquals(expected,actual.ids());
            assertTrue(actual.proofVisits()<=2L*proofs.size());
            assertTrue(actual.edgeVisits()<=2L*proofs.stream().mapToLong(p->p.dependencies().size()).sum());
        }
    }
    @Test void reverseOrderedProofChainHasLinearWorkAndNoSeedBuildsNoIndex() {
        int count=30000;var proofs=new ArrayList<Proof>();
        for(int i=count-1;i>=0;i--)proofs.add(proof("p"+i,i==0,i==0?List.of():List.of("p"+(i-1))));
        var closure=SourceControlEvidence.hypotheticalProofs(proofs);assertEquals(count,closure.ids().size());
        assertTrue(closure.proofVisits()<=2L*count);assertTrue(closure.edgeVisits()<=2L*(count-1));
        var unseeded=proofs.stream().map(p->proof(p.id(),false,p.dependencies())).toList();
        var none=SourceControlEvidence.hypotheticalProofs(unseeded);assertEquals(Set.of(),none.ids());assertEquals(0,none.edgeVisits());
    }
}
