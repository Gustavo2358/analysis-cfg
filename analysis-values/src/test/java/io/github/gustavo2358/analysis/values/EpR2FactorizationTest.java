package io.github.gustavo2358.analysis.values;

import io.github.gustavo2358.analysis.storage.SegmentMap;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static io.github.gustavo2358.analysis.values.EpR2Fixtures.*;

class EpR2FactorizationTest {
    /** Structural oracle: count materialized alternatives, not elapsed time. */
    static long alternatives(RegionalValuesAnalysis.State state) throws ReflectiveOperationException {
        var field=state.getClass().getDeclaredField("bindings");field.setAccessible(true);
        var map=(SegmentMap<?>)field.get(state);long[] count={0};
        map.forEach((key,value)->count[0]+=((Set<?>)value).size());return count[0];
    }
    @Test void connectedEntryFactorsGrowBySum() throws ReflectiveOperationException {
        for(int p:List.of(1,2,4,7)) {
            var publication=fixture(8,p,0,0,7,false,true,"diamonds",false);
            var analysis=EpR2EntryTest.analysis(publication);var engine=analysis.new Engine();
            var sessionField=analysis.getClass().getDeclaredField("session");sessionField.setAccessible(true);
            var session=(io.github.gustavo2358.analysis.structure.AnalysisSession)sessionField.get(analysis);
            var boundary=engine.boundaries(session).iterator().next();
            assertTrue(alternatives(boundary.state())<=8L*(p+1),"G must store component alternatives, not complete worlds: P="+p);
        }
    }
    @Test void independentSlicesWithinOneRegionAreAlsoFactored() throws ReflectiveOperationException {
        var publication=fixture(1,7,0,0,0,false,true,"control",false);
        var analysis=EpR2EntryTest.analysis(publication);var engine=analysis.new Engine();
        var sessionField=analysis.getClass().getDeclaredField("session");sessionField.setAccessible(true);
        var session=(io.github.gustavo2358.analysis.structure.AnalysisSession)sessionField.get(analysis);
        assertTrue(alternatives(engine.boundaries(session).iterator().next().state())<=14,"seven independent slices need fourteen alternatives");
    }
}
