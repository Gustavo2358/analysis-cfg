package io.github.gustavo2358.analysis.values;

import io.github.gustavo2358.air.model.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static io.github.gustavo2358.analysis.values.ValuesFixtures.*;
import static io.github.gustavo2358.analysis.values.RegionalValuesTest.*;

/** Repeated shape changes deliberately exercise the production concrete fallback. */
class RegionalFallbackStressTest {
    static Publication fixture(int rounds) {
        var p=RegionalExplosionFixturesTest.fixture(4,1,false);var unit=p.units().getFirst();
        var instructions=new ArrayList<Instruction>();
        for(int i=0;i<rounds;i++) {
            instructions.add(assign(U,"stress-write-"+i,RegionalExplosionFixturesTest.object(0),"ABCDEFGH"));
            instructions.add(RegionalCompositionTest.copy("stress-partial-"+i,p.storage().get(0).header().id(),1,p.storage().get(1).header().id(),3,4));
        }
        return replace(p,List.of(unit(U,unit.entries(),List.of(returning(U,"s0",instructions)),unit.objects())),p.coverage(),p.uncertainties(),p.premises());
    }
    @Test void repeatedPartialCopiesPreserveFullReplayAndActuallyUseFallback() throws Exception {
        var result=ExactProvenanceFixtureBridge.check(fixture(8),4);
        assertEquals(result.before(),result.after());
        assertTrue(result.solveMetrics().get("concreteFallbacks")>=8);
        assertTrue(result.after().stream().flatMap(f->f.alternatives().stream()).flatMap(a->a.fragments().stream()).anyMatch(f->!f.captures().isEmpty()));
        System.out.println("W33_FALLBACK_STRESS "+new TreeMap<>(result.solveMetrics()));
    }
}
