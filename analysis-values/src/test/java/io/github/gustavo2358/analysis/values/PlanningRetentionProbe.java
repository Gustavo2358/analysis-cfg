package io.github.gustavo2358.analysis.values;

import io.github.gustavo2358.air.model.Publication;
import io.github.gustavo2358.analysis.cfg.application.CfgBuildResult;
import io.github.gustavo2358.analysis.cfg.domain.ProjectionPolicy;
import io.github.gustavo2358.analysis.structure.AnalysisSession;
import io.github.gustavo2358.analysis.application.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.*;

/** External diagnostic JVM only: actual GC reachability before/after execution and detached-result release. */
public final class PlanningRetentionProbe {
    private static Publication publication;
    private static CfgBuildResult cfg;
    private static AnalysisSession session;
    private static PlanningExecution runtime;
    private static ExecutionPlan<PlanningFixtures.TestFact> plan;
    private static PreparedAnalysisResult<PlanningFixtures.TestFact> result;
    private PlanningRetentionProbe() { }
    private static void air() { publication=ValuesFixtures.linear(1,10000,1,1);cfg=ValuesFixtures.build(publication); }
    private static void plan(int n) {
        session=AnalysisSession.open(cfg,publication,ProjectionPolicy.KNOWN_SUBSET,publication.units().getFirst().entries()).session().orElseThrow();
        runtime=new PlanningExecution(session,PlanningFixtures.registry());
        var key=PlanningFixtures.key(publication);var query=PlanningFixtures.query(publication);var batch=PossibleValuesProvider.batch("shared",key);
        var registrations=new ArrayList<ConsumerRegistration<PlanningFixtures.TestFact>>();
        for(int i=0;i<n;i++)registrations.add(PlanningFixtures.queryConsumer("C"+i,key,batch,List.of(query)));
        plan=runtime.plan(registrations);
    }
    private static void execute() { result=runtime.execute("memory",plan); }
    private static void detach() { runtime.close();runtime=null;plan=null;session=null;cfg=null;publication=null; }
    private static void phase(String name,BufferedReader input)throws Exception {
        System.out.println("W4_PHASE "+name);System.out.flush();if(input.readLine()==null)throw new IllegalStateException("diagnostic controller disconnected");
    }
    public static void main(String[] args)throws Exception {
        var input=new BufferedReader(new InputStreamReader(System.in));int n=Integer.parseInt(args[0]);
        air();phase("air_cfg",input);plan(n);phase("planned",input);execute();phase("executed",input);
        if(result.consumers().size()!=n||result.metrics().get("analysis").get("analysisRuns")!=1)throw new AssertionError("real shared execution");
        detach();phase("result_only",input);result=null;phase("released",input);
    }
}
