package io.github.gustavo2358.analysis.values;

import io.github.gustavo2358.air.model.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static io.github.gustavo2358.analysis.values.ValuesFixtures.*;
import static io.github.gustavo2358.analysis.values.RegionalValuesTest.*;

class RegionalCostMetricsTest {
    @Test void trackedSummariesMatchFullStateTraversalForDisjointAndConnectedGroups() {
        for(boolean connected:List.of(false,true)) {
            var p=RegionalExplosionFixturesTest.fixture(4,5,false);var u=p.units().getFirst();
            var instructions=new ArrayList<>(u.sequences().getFirst().instructions());
            if(connected)for(int i:List.of(0,2))instructions.add(RegionalCompositionTest.copy("connect-"+i,
                p.storage().get(i).header().id(),0,p.storage().get(i+1).header().id(),0,8));
            p=replace(p,List.of(unit(U,u.entries(),List.of(returning(U,"s0",instructions)),u.objects())),p.coverage(),p.uncertainties(),p.premises());
            var selected=session(p);var analysis=RegionalValuesAnalysis.prepare(selected).analysis().orElseThrow();
            var engine=analysis.new Engine();var state=engine.boundaries(selected).iterator().next().state();
            long edges=state.materializedAlternatives(),nodes=state.decisionNodes(),component=state.maxComponentCardinality();
            for(var operation:instructions) {
                state=engine.operation(state,operation);
                edges=Math.max(edges,state.materializedAlternatives());nodes=Math.max(nodes,state.decisionNodes());
                component=Math.max(component,state.maxComponentCardinality());
            }
            var execution=analysis.execute();var metrics=execution.solveMetrics();
            assertEquals(connected?2L:4L,execution.preparationMetrics().get("correlationGroups"));
            assertEquals(edges,metrics.get("maxStateAlternatives"));assertEquals(nodes,metrics.get("maxDecisionNodes"));
            assertEquals(component,metrics.get("maxComponentCardinality"));
        }
    }
}
