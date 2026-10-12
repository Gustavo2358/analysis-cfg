package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.analysis.application.*;
import io.github.gustavo2358.analysis.consumers.PreparedFacts;
import io.github.gustavo2358.analysis.cfg.application.CfgBuildResult;
import io.github.gustavo2358.analysis.cfg.domain.ProjectionPolicy;
import io.github.gustavo2358.analysis.dependencies.SnapshotProgram;
import io.github.gustavo2358.analysis.plan.*;
import io.github.gustavo2358.analysis.query.*;
import io.github.gustavo2358.analysis.solver.AnalysisResources;
import io.github.gustavo2358.analysis.structure.AnalysisSession;
import io.github.gustavo2358.analysis.values.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Real file/cache1 native owner: fund group controls before allocating or replaying. */
final class NativeJointObservationBudgetTest {
    @FunctionalInterface private interface Work {void run(SnapshotProgram program,CfgBuildResult cfg,AnalysisResources resources)throws Exception;}
    private static void nativeInput(Work action)throws Exception {
        var harness=new ManagedExecutableDependencyResultTest();harness.directory=Files.createTempDirectory(Path.of("target"),"native-joint-law-");
        var contract=Class.forName(ManagedExecutableDependencyResultTest.class.getName()+"$Work");
        var work=Proxy.newProxyInstance(contract.getClassLoader(),new Class<?>[]{contract},(proxy,method,args)->{action.run((SnapshotProgram)args[0],(CfgBuildResult)args[1],(AnalysisResources)args[4]);return null;});
        var input=ManagedExecutableDependencyResultTest.class.getDeclaredMethod("nativeInput",int.class,contract);input.setAccessible(true);
        try{input.invoke(harness,1,work);}catch(InvocationTargetException failure){if(failure.getCause() instanceof Exception error)throw error;if(failure.getCause() instanceof Error error)throw error;throw failure;}
    }
    private static AnalysisSession session(SnapshotProgram program,CfgBuildResult cfg) {
        var entry=program.units().getFirst().entries().getFirst().id();
        var admission=AnalysisSession.open(cfg,program,ProjectionPolicy.KNOWN_SUBSET,List.of(entry));
        assertEquals(AnalysisSession.Status.ACCEPTED,admission.status(),admission.reason());return admission.session().orElseThrow();
    }
    @Test void nativePlanningGroupDenialIsAtomicAndNextEpochPreservesCompleteFacts()throws Exception {
        nativeInput((program,cfg,resources)->{
            var session=session(program,cfg);var entry=session.contexts().iterator().next().entryView().id();
            var subject=session.index().objectDeclarations().keySet().iterator().next();
            var key=PossibleValuesProvider.key(entry,PossibleValuesAnalysis.EFFECTS_PROFILE,Set.of(subject));
            var query=new PointQuery<>(ProgramPoint.before(entry,new OperationId(entry.unit(),"invoke")),subject);
            var registrations=new ArrayList<ConsumerRegistration<ValueFact>>();
            for(int at=0;at<4;at++) {
                var batch=PossibleValuesProvider.batch("batch-"+at,key);
                registrations.add(new ConsumerRegistration<>(new ConsumerPlan("consumer-"+at,List.of(key),List.of(batch.id())),
                    List.of(new SiteInterest(Operations.Return.class,entry,site->true)),List.of(new ObservationRequest<>(batch,List.of(query,query))),
                    (site,facts,sink)->{assertEquals(PreparedFacts.LookupStatus.AVAILABLE,facts.lookup(batch,query).status());sink.emit(facts.lookup(batch,query).observation().value());}));
            }
            try(var execution=new PlanningExecution(session,new AnalysisRegistry(List.of(new PossibleValuesProvider())))) {
                var warm=new ConsumerRegistration<ValueFact>(new ConsumerPlan("warm",List.of(key),List.of()),List.of(),List.of(),(site,facts,sink)->{});
                execution.execute("warm",execution.plan(List.of(warm)));
                var denied=execution.plan(registrations);long scratch=resources.used(AnalysisResources.Pool.SCRATCH);
                var held=resources.reserve(AnalysisResources.Pool.SCRATCH,resources.limits().scratchBytes()-scratch,AnalysisResources.Phase.CONTROL);
                try(held) {
                    long before=resources.used(AnalysisResources.Pool.SCRATCH);
                    var failure=assertThrows(AnalysisResources.Exhausted.class,()->execution.execute("denied",denied));
                    assertEquals(AnalysisResources.Resource.SCRATCH,failure.resource());assertEquals(AnalysisResources.Phase.REPLAY,failure.phase());
                    assertEquals(before,resources.used(AnalysisResources.Pool.SCRATCH));assertNotNull(program.admission().snapshot());
                }
                assertEquals(scratch,resources.used(AnalysisResources.Pool.SCRATCH));
                var result=execution.execute("recovered",execution.plan(registrations));
                assertEquals(PreparedAnalysisResult.PreparationStatus.COMPLETE,result.preparationStatus());
                assertEquals(8L,result.metrics().get("observation").get("queryRequests"));assertEquals(4L,result.metrics().get("observation").get("uniqueQueries"));
                assertEquals(1L,result.metrics().get("observation").get("operationsReplayed"));assertEquals(1L,result.metrics().get("observation").get("sequencesReplayed"));
                for(var consumer:result.consumers()) {
                    var fact=consumer.facts().getFirst();assertEquals(List.of(new Values.TextValue("PROGA   ")),fact.candidates());
                    assertEquals(List.of(new OperationId(entry.unit(),"seed")),fact.evidence());assertFalse(fact.effectiveUnknownRemainder());
                }
                assertEquals(scratch,resources.used(AnalysisResources.Pool.SCRATCH),"group and union controls retire while borrowed program remains live");
            }
        });
    }
    @Test void nativeUnionDenialPrecedesReplayAndReturnsEveryLeaseOnSuccess()throws Exception {
        nativeInput((program,cfg,resources)->{
            var session=session(program,cfg);var entry=session.contexts().iterator().next().entryView().id();
            var subject=session.index().objectDeclarations().keySet().iterator().next();
            var query=new PointQuery<>(ProgramPoint.before(entry,new OperationId(entry.unit(),"invoke")),subject);
            var admission=PossibleValuesAnalysis.prepare(session,PossibleValuesAnalysis.EFFECTS_PROFILE,Set.of(subject));
            try(var analysis=admission.analysis().orElseThrow();var execution=analysis.execute()) {
                long scratch=resources.used(AnalysisResources.Pool.SCRATCH);
                var held=resources.reserve(AnalysisResources.Pool.SCRATCH,resources.limits().scratchBytes()-scratch,AnalysisResources.Phase.CONTROL);
                try(held) {
                    var failure=assertThrows(AnalysisResources.Exhausted.class,()->execution.observeBatches(List.of(List.of(query),List.of(query))));
                    assertEquals(AnalysisResources.Resource.SCRATCH,failure.resource());assertEquals(AnalysisResources.Phase.REPLAY,failure.phase());
                    assertEquals(resources.limits().scratchBytes(),resources.used(AnalysisResources.Pool.SCRATCH));
                }
                var results=execution.observeBatches(List.of(List.of(query),List.of(query)));
                assertEquals(results.getFirst().batch().observations(),results.get(1).batch().observations());
                assertEquals(1,results.stream().mapToLong(value->value.batch().metrics().operationsReplayed()).sum());
                assertEquals(scratch,resources.used(AnalysisResources.Pool.SCRATCH));assertNotNull(program.admission().snapshot());
            }
        });
    }
}
