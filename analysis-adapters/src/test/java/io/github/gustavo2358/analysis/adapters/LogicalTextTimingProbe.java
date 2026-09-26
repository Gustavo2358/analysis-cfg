package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.air.json.AirJson;
import io.github.gustavo2358.analysis.cfg.application.*;
import io.github.gustavo2358.analysis.cfg.domain.ProjectionPolicy;
import io.github.gustavo2358.analysis.cfg.extension.SemanticInterpreterRegistry;
import io.github.gustavo2358.analysis.dependencies.CallDependencyPlan;
import io.github.gustavo2358.analysis.structure.AnalysisSession;
import io.github.gustavo2358.analysis.values.PossibleValuesProvider;
import java.nio.file.*;

/** Observation-only timing; kept outside deterministic dependencies.json. */
public final class LogicalTextTimingProbe {
    public static void main(String[] args)throws Exception {
        var p=new AirJson().decode(Files.readAllBytes(Path.of(args[0])));
        var defaults=BuildOptions.defaults();var options=new BuildOptions(defaults.validation(),ProjectionPolicy.PARTIAL_ANALYSIS);
        var graph=new CfgBuildCoordinator(SemanticInterpreterRegistry.empty()).build(p,options);
        var session=AnalysisSession.open(graph,p,options.projectionPolicy(),p.units().getFirst().entries()).session().orElseThrow();
        var key=CallDependencyPlan.select(session).stream().flatMap(r->r.dependencies().requiredAnalysisKeys().stream())
            .filter(k->k.implementation().equals(PossibleValuesProvider.IMPLEMENTATION)).findFirst().orElseThrow();
        long start=System.nanoTime();var prepared=new PossibleValuesProvider().prepare(session,key);long preparedAt=System.nanoTime();
        if(prepared.refusal()!=null)throw new AssertionError(prepared.refusal());
        var run=prepared.execute();long finished=System.nanoTime();
        if(!run.outcome().status().name().equals("STABLE"))throw new AssertionError(run.outcome());
        var runtime=Runtime.getRuntime();
        System.out.printf("{\"prepareNanos\":%d,\"solveNanos\":%d,\"usedHeapBytesObservation\":%d,\"nodesPopped\":%d}%n",
            preparedAt-start,finished-preparedAt,runtime.totalMemory()-runtime.freeMemory(),run.outcome().metrics().get("nodesPopped"));
    }
}
