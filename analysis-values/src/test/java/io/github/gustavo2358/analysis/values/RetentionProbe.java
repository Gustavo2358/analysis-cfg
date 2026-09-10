package io.github.gustavo2358.analysis.values;

import io.github.gustavo2358.air.model.Publication;
import io.github.gustavo2358.analysis.cfg.application.CfgBuildResult;
import io.github.gustavo2358.analysis.structure.AnalysisSession;
import io.github.gustavo2358.analysis.cfg.domain.ProjectionPolicy;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;

/** External JVM diagnostic probe: each phase returns before the collector handshake. */
public final class RetentionProbe {
    private static Publication publication;
    private static CfgBuildResult cfg;
    private static AnalysisSession session;
    private static PossibleValuesAnalysis definition;
    private static PossibleValuesAnalysis.Execution execution;
    private static PossibleValuesAnalysis.Execution.Observations observations;
    private RetentionProbe() { }
    private static void prepareAir(int operations) {
        publication=ValuesScaleTest.longSequence(operations,1,false,false);
        cfg=ValuesFixtures.build(publication);
    }
    private static void prepareAnalysis() {
        session=AnalysisSession.open(cfg,publication,ProjectionPolicy.KNOWN_SUBSET,publication.units().getFirst().entries()).session().orElseThrow();
        definition=PossibleValuesAnalysis.prepare(session).analysis().orElseThrow();
    }
    private static void solve(){execution=definition.execute();}
    private static void observeAndReleaseAnalysis() {
        observations=execution.observe(List.of(ValuesTest.before(publication,0,0)));
        ValuesTest.expected(observations.batch().observations().getFirst().value(),false,"v1");
        execution=null;definition=null;session=null;cfg=null;publication=null;
    }
    private static void phase(String name,BufferedReader input) throws Exception {
        System.out.println("W3_PHASE "+name);System.out.flush();
        if(input.readLine()==null)throw new IllegalStateException("diagnostic controller disconnected");
    }
    public static void main(String[] args) throws Exception {
        int operations=Integer.parseInt(args[0]);
        var input=new BufferedReader(new InputStreamReader(System.in));
        prepareAir(operations);phase("air_cfg",input);
        prepareAnalysis();phase("prepared",input);
        solve();phase("solved",input);
        observeAndReleaseAnalysis();phase("batch_only",input);
        observations=null;phase("released",input);
    }
}
