package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.analysis.application.*;
import io.github.gustavo2358.analysis.cfg.application.CfgBuildResult;
import io.github.gustavo2358.analysis.cfg.domain.ProjectionPolicy;
import io.github.gustavo2358.analysis.dependencies.SnapshotProgram;
import io.github.gustavo2358.analysis.plan.*;
import io.github.gustavo2358.analysis.query.*;
import io.github.gustavo2358.analysis.solver.AnalysisResources;
import io.github.gustavo2358.analysis.structure.*;
import io.github.gustavo2358.analysis.values.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Two opted-in runs with unequal keys cannot use a joint observation group. */
final class NativeDisjointJointKeyEligibilityTest {
    @Test void independentJointKeysNeverReserveGroupingCapacity()throws Exception {
        var harness=new ManagedExecutableDependencyResultTest();harness.directory=Files.createTempDirectory(Path.of("target"),"native-disjoint-joint-");
        var contract=Class.forName(ManagedExecutableDependencyResultTest.class.getName()+"$Work");
        var work=Proxy.newProxyInstance(contract.getClassLoader(),new Class<?>[]{contract},(proxy,method,args)->{
            var program=(SnapshotProgram)args[0];var cfg=(CfgBuildResult)args[1];var root=(AnalysisResources)args[4];
            var calls=new AtomicInteger();var budget=program.executionBudget().orElseThrow();
            ProgramStore.ExecutionBudget observed=new ProgramStore.ExecutionBudget() {
                public ProgramStore.CapacityLease reserve(ProgramStore.CapacityPool pool,long amount,ProgramStore.ExecutionPhase phase) {
                    if(pool==ProgramStore.CapacityPool.SCRATCH&&phase==ProgramStore.ExecutionPhase.REPLAY)calls.incrementAndGet();
                    return budget.reserve(pool,amount,phase);
                }
                public void output(long bytes,ProgramStore.ExecutionPhase phase){budget.output(bytes,phase);}
            };
            var store=(ProgramStore.Structural)Proxy.newProxyInstance(ProgramStore.Structural.class.getClassLoader(),new Class<?>[]{ProgramStore.Structural.class},(owner,call,arguments)->{
                if(call.getName().equals("executionBudget"))return Optional.of(observed);
                try{return call.invoke(program,arguments);}catch(InvocationTargetException failure){throw failure.getCause();}
            });
            var entry=program.units().getFirst().entries().getFirst().id();
            var opened=AnalysisSession.open(cfg,store,ProjectionPolicy.KNOWN_SUBSET,List.of(entry));assertEquals(AnalysisSession.Status.ACCEPTED,opened.status());
            var session=opened.session().orElseThrow();var subject=session.index().objectDeclarations().keySet().iterator().next();
            var keys=List.of(PossibleValuesProvider.key(entry,PossibleValuesAnalysis.EFFECTS_PROFILE,Set.of(subject)),PossibleValuesProvider.key(entry,PossibleValuesAnalysis.EFFECTS_PROFILE,Set.of()));assertNotEquals(keys.getFirst(),keys.get(1));
            var registrations=new ArrayList<ConsumerRegistration<ValueFact>>();
            var query=new PointQuery<>(ProgramPoint.before(entry,new OperationId(entry.unit(),"invoke")),subject);
            for(int at=0;at<2;at++) {
                var batch=PossibleValuesProvider.batch("disjoint-"+at,keys.get(at));
                registrations.add(new ConsumerRegistration<>(new ConsumerPlan("disjoint-consumer-"+at,List.of(keys.get(at)),List.of(batch.id())),List.of(),List.of(new ObservationRequest<>(batch,List.of(query))),(site,facts,sink)->{}));
            }
            try(var execution=new PlanningExecution(session,new AnalysisRegistry(List.of(new PossibleValuesProvider())))) {
                var warm=new ConsumerRegistration<ValueFact>(new ConsumerPlan("warm-independent",keys,List.of()),List.of(),List.of(),(site,facts,sink)->{});
                var warmed=execution.execute("warm",execution.plan(List.of(warm)));assertEquals(PreparedAnalysisResult.PreparationStatus.COMPLETE,warmed.preparationStatus());
                var plan=execution.plan(registrations);calls.set(0);long scratch=root.used(AnalysisResources.Pool.SCRATCH);
                var result=execution.execute("independent",plan);assertEquals(PreparedAnalysisResult.PreparationStatus.COMPLETE,result.preparationStatus());assertEquals(2,result.analyses().size());assertEquals(2,result.results().size());
                assertEquals(2L,result.metrics().get("observation").get("observationBatchesExecuted"));assertEquals(2L,result.metrics().get("observation").get("queryRequests"));assertEquals(1L,result.metrics().get("observation").get("queriesAnswered"));assertEquals(1L,result.metrics().get("observation").get("unsupportedQueries"));
                assertEquals(scratch,root.used(AnalysisResources.Pool.SCRATCH));assertNotNull(program.admission().snapshot());
                assertEquals(0,calls.get(),"DISJOINT_JOINT_KEYS_RESERVE_GROUPING_CAPACITY_RED");
            }
            return null;
        });
        var input=ManagedExecutableDependencyResultTest.class.getDeclaredMethod("nativeInput",int.class,contract);input.setAccessible(true);
        try{input.invoke(harness,1,work);}catch(InvocationTargetException failure){if(failure.getCause() instanceof Exception error)throw error;if(failure.getCause() instanceof Error error)throw error;throw failure;}
    }
}
