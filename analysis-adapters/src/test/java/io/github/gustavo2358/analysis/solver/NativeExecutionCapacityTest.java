package io.github.gustavo2358.analysis.solver;

import io.github.gustavo2358.analysis.adapters.DataflowAirReader;
import io.github.gustavo2358.analysis.dependencies.SnapshotProgram;
import io.github.gustavo2358.analysis.cfg.application.*;
import io.github.gustavo2358.analysis.cfg.extension.SemanticInterpreterRegistry;
import io.github.gustavo2358.analysis.structure.AnalysisSession;
import io.github.gustavo2358.analysis.application.*;
import io.github.gustavo2358.analysis.plan.*;
import io.github.gustavo2358.analysis.query.*;
import io.github.gustavo2358.analysis.values.*;
import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Actual admitted input and capacity owners; no surrogate publication/store. */
final class NativeExecutionCapacityTest {
    private static final Path INPUT=Path.of("src/test/resources/cp6/dynamic-x8.air.json");
    private static AnalysisResources resources(){return new AnalysisResources(new AnalysisResources.Limits(64L*1024*1024,16L*1024*1024,0,256L*1024*1024,8,Long.MAX_VALUE,Long.MAX_VALUE));}
    private static void released(AnalysisResources resources){for(var pool:AnalysisResources.Pool.values())assertEquals(0,resources.used(pool),pool.toString());}
    /** Stored reference slots only,not decoded projections or measured heap bytes. */
    private static int retainedStates(DataflowResult<?> result) throws ReflectiveOperationException {
        int held=0;
        for(String name:List.of("in","out")){var field=DataflowResult.class.getDeclaredField(name);field.setAccessible(true);for(Object value:(Object[])field.get(result))if(value!=null)held++;}
        return held;
    }

    @Test void nativeExecutionReservationsGrowAndTransferAgainstOneInputLedger() throws Exception {
        var resources=resources();
        try(var input=new DataflowAirReader().readSnapshot(INPUT,resources);
            var program=new SnapshotProgram(input.checked(),input.newIdentityStorage(),input.newOrderStorage(),resources)) {
            var execution=AnalysisResources.executionFor(program);long before=resources.heapUsed();
            var staged=execution.reserve(AnalysisResources.Pool.RESIDENT,23,AnalysisResources.Phase.DOMAIN);
            try(var owner=execution.reserve(AnalysisResources.Pool.RESIDENT,17,AnalysisResources.Phase.CONTROL)) {
                assertEquals(before+40,resources.heapUsed(),"the admitted owner must fund the execution view");
                owner.absorb(staged);staged.close();assertEquals(before+40,resources.heapUsed());assertEquals(40,owner.amount());
                owner.grow(3,AnalysisResources.Phase.CONTROL);assertEquals(before+43,resources.heapUsed());assertEquals(43,owner.amount());
                assertThrows(IllegalStateException.class,()->staged.grow(1,AnalysisResources.Phase.DOMAIN));
            }finally{staged.close();}
            assertEquals(before,resources.heapUsed());assertEquals(0,execution.heapUsed());
            for(var pool:List.of(AnalysisResources.Pool.SCRATCH,AnalysisResources.Pool.TEMPORARY,AnalysisResources.Pool.OPEN_FILES)) {
                long used=resources.used(pool);
                try(var lease=execution.reserve(pool,1,AnalysisResources.Phase.SORT)){assertEquals(1,lease.amount());assertEquals(used+1,resources.used(pool));}
                assertEquals(used,resources.used(pool));
            }
        }
        released(resources);
    }

    @Test void nativeExecutionQuotaFailureDoesNotPartiallyChargeOrFreeAnotherOwner() throws Exception {
        var resources=resources();
        try(var input=new DataflowAirReader().readSnapshot(INPUT,resources);
            var program=new SnapshotProgram(input.checked(),input.newIdentityStorage(),input.newOrderStorage(),resources)) {
            var execution=AnalysisResources.executionFor(program);var foreign=AnalysisResources.executionFor(program);
            var direct=assertThrows(AnalysisResources.Exhausted.class,()->{try(var denied=execution.reserve(AnalysisResources.Pool.DIRECT,1,AnalysisResources.Phase.DOMAIN)){assertEquals(1,denied.amount());} });
            assertEquals(AnalysisResources.Resource.DIRECT,direct.resource());assertEquals(AnalysisResources.Phase.DOMAIN,direct.phase());assertEquals(0,execution.used(AnalysisResources.Pool.DIRECT));
            long before=resources.heapUsed();
            var other=foreign.reserve(AnalysisResources.Pool.RESIDENT,23,AnalysisResources.Phase.DOMAIN);
            try(var owner=execution.reserve(AnalysisResources.Pool.RESIDENT,17,AnalysisResources.Phase.CONTROL)) {
                assertThrows(IllegalArgumentException.class,()->owner.absorb(other));assertThrows(IllegalArgumentException.class,()->owner.absorb(owner));
                assertEquals(before+40,resources.heapUsed());
                long occupied=resources.limits().heapBytes()-resources.heapUsed()-16;
                try(var pressure=resources.reserve(AnalysisResources.Pool.RESIDENT,occupied,AnalysisResources.Phase.CONTROL)) {
                    assertEquals(occupied,pressure.amount());
                    long heap=resources.heapUsed(),peak=execution.heapPeak();
                    var failure=assertThrows(AnalysisResources.Exhausted.class,()->owner.grow(17,AnalysisResources.Phase.CONTROL));
                    assertEquals(AnalysisResources.Resource.HEAP,failure.resource());assertEquals(AnalysisResources.Phase.CONTROL,failure.phase());
                    assertEquals(heap,resources.heapUsed());assertEquals(17,owner.amount());assertEquals(17,execution.heapUsed());assertEquals(peak,execution.heapPeak());
                    assertThrows(IllegalArgumentException.class,()->owner.grow(-1,AnalysisResources.Phase.CONTROL));
                }
                other.close();assertEquals(before+17,resources.heapUsed());
            }finally{other.close();}
            assertEquals(before,resources.heapUsed());assertEquals(0,execution.heapUsed());assertEquals(0,foreign.heapUsed());
        }
        released(resources);
    }

    @Test void nativeReservationOwnerExpiresWithoutBlockingCleanupOrClosingBorrowedAir() throws Exception {
        var resources=resources();
        try(var input=new DataflowAirReader().readSnapshot(INPUT,resources)) {
            long before=resources.heapUsed();var snapshot=input.checked().snapshot();long root=snapshot.root();var shape=snapshot.shape(root);
            var program=new SnapshotProgram(input.checked(),input.newIdentityStorage(),input.newOrderStorage(),resources);
            var execution=AnalysisResources.executionFor(program);
            long programHeap=resources.heapUsed();var lease=execution.reserve(AnalysisResources.Pool.RESIDENT,17,AnalysisResources.Phase.CONTROL);
            try {
                assertEquals(programHeap+17,resources.heapUsed());program.close();
                assertEquals(before,resources.heapUsed(),"closing the program must retire its live execution leases");
                assertEquals(shape,snapshot.shape(root),"execution cleanup cannot close borrowed AIR");
                assertThrows(IllegalStateException.class,()->lease.grow(1,AnalysisResources.Phase.CONTROL));
                assertThrows(IllegalStateException.class,()->execution.reserve(AnalysisResources.Pool.RESIDENT,1,AnalysisResources.Phase.CONTROL));
                lease.close();lease.close();assertEquals(before,resources.heapUsed());assertEquals(0,execution.heapUsed());
            }finally{lease.close();program.close();}
        }
        released(resources);
    }

    @Test void zeroNativeControlCapacityCannotPublishEitherOrdinaryDirection() throws Exception {
        for(var direction:Direction.values()) {
            var resources=resources();
            try(var input=new DataflowAirReader().readSnapshot(INPUT,resources);
                var program=new SnapshotProgram(input.checked(),input.newIdentityStorage(),input.newOrderStorage(),resources)) {
                var options=BuildOptions.defaults();var cfg=new CfgBuildCoordinator(SemanticInterpreterRegistry.empty()).buildChecked(program,input.checked(),options);
                var entry=program.units().getFirst().entries().getFirst();
                var session=AnalysisSession.open(cfg,program,options.projectionPolicy(),List.of(entry.id())).session().orElseThrow();
                assertFalse(session.hasLocalControl(),"this law must not accidentally test only ActivationSolver");
                var context=session.contexts().iterator().next();
                var definition=new AnalysisDefinition<Integer>() {
                    public Direction direction(){return direction;}
                    public Integer bottom(){return 0;}
                    public long stateFingerprint(Integer state){return state;}
                    public Iterable<Boundary<Integer>> boundaries(AnalysisSession ignored){return List.of(new Boundary<>(context,direction==Direction.FORWARD?context.entryNode():context.normalExit(),1));}
                    public Join<Integer> joinInto(Integer a,Integer b,DomainWork work){int joined=a|b;return new Join<>(joined,joined!=a);}
                    public boolean equivalent(Integer a,Integer b,DomainWork work){return a.equals(b);}
                    public Integer transferBlock(AnalysisPoint point,Integer state,DomainWork work){return state;}
                    public Integer transferEdge(AnalysisPoint point,io.github.gustavo2358.analysis.cfg.domain.CfgTransition edge,Integer state,DomainWork work){return state;}
                };
                var stable=DataflowSolver.solve(session,definition);assertEquals(DataflowResult.Status.STABLE,stable.status());
                long before=resources.heapUsed();
                try(var pressure=resources.reserve(AnalysisResources.Pool.RESIDENT,resources.limits().heapBytes()-before,AnalysisResources.Phase.CONTROL)) {
                    assertEquals(resources.limits().heapBytes()-before,pressure.amount());
                    var failure=assertThrows(AnalysisResources.Exhausted.class,()->DataflowSolver.solve(session,definition));
                    assertEquals(AnalysisResources.Resource.HEAP,failure.resource());assertEquals(AnalysisResources.Phase.CONTROL,failure.phase(),"solver capacity must be checked before a later DECODE scratch denial");
                    assertEquals(resources.limits().heapBytes(),resources.heapUsed());assertEquals(DataflowResult.Status.STABLE,stable.status());
                }
                assertEquals(before,resources.heapUsed());
            }
            released(resources);
        }
    }

    @Test void nativeOrdinaryResultOwnsCapacityUntilCloseAndCannotOutliveItsProgram() throws Exception {
        var resources=resources();
        try(var input=new DataflowAirReader().readSnapshot(INPUT,resources)) {
            var program=new SnapshotProgram(input.checked(),input.newIdentityStorage(),input.newOrderStorage(),resources);
            try {
                var options=BuildOptions.defaults();var cfg=new CfgBuildCoordinator(SemanticInterpreterRegistry.empty()).buildChecked(program,input.checked(),options);
                var entry=program.units().getFirst().entries().getFirst();
                var session=AnalysisSession.open(cfg,program,options.projectionPolicy(),List.of(entry.id())).session().orElseThrow();
                var context=session.contexts().iterator().next();
                var definition=new AnalysisDefinition<Integer>() {
                    public Direction direction(){return Direction.FORWARD;}
                    public Integer bottom(){return 0;}
                    public long stateFingerprint(Integer state){return state;}
                    public Iterable<Boundary<Integer>> boundaries(AnalysisSession ignored){return List.of(new Boundary<>(context,context.entryNode(),1));}
                    public Join<Integer> joinInto(Integer a,Integer b,DomainWork work){int joined=a|b;return new Join<>(joined,joined!=a);}
                    public boolean equivalent(Integer a,Integer b,DomainWork work){return a.equals(b);}
                    public Integer transferBlock(AnalysisPoint point,Integer state,DomainWork work){return state;}
                    public Integer transferEdge(AnalysisPoint point,io.github.gustavo2358.analysis.cfg.domain.CfgTransition edge,Integer state,DomainWork work){return state;}
                };
                long before=resources.heapUsed();var first=DataflowSolver.solve(session,definition);
                assertTrue(resources.heapUsed()>=before+64+16*first.metrics().analysisPoints(),"both stable state-reference arrays must remain funded after solve returns");
                assertEquals(1,first.in(context,context.entryNode()));
                assertInstanceOf(AutoCloseable.class,first).close();
                assertEquals(before,resources.heapUsed());assertThrows(IllegalStateException.class,()->first.states(context,context.entryNode(),true));
                var expired=DataflowSolver.solve(session,definition);program.close();
                assertThrows(IllegalStateException.class,()->expired.in(context,context.entryNode()));
                assertInstanceOf(AutoCloseable.class,expired).close();assertEquals(DataflowResult.Status.STABLE,expired.status());
                assertEquals(0,retainedStates(expired));assertEquals(0,retainedStates(first));
            }finally{program.close();}
        }
        released(resources);
    }

    @Test void nativePlanningClosesScalarAndRegionalStateWithoutInvalidatingDeliveredFacts() throws Exception {
        planningLifetime(new PossibleValuesProvider(),false);
        planningLifetime(new RegionalValuesProvider(),true);
    }
    @Test void nativeOwnerCleanupRetiresEveryLeaseEvenWhenCallbacksFail() throws Exception {
        var resources=resources();
        try(var input=new DataflowAirReader().readSnapshot(INPUT,resources)) {
            long before=resources.heapUsed();var snapshot=input.checked().snapshot();long root=snapshot.root();var shape=snapshot.shape(root);
            var program=new SnapshotProgram(input.checked(),input.newIdentityStorage(),input.newOrderStorage(),resources);
            var execution=AnalysisResources.executionFor(program);
            var first=execution.reserve(AnalysisResources.Pool.RESIDENT,17,AnalysisResources.Phase.CONTROL);
            var second=execution.reserve(AnalysisResources.Pool.RESIDENT,23,AnalysisResources.Phase.DOMAIN);
            var firstFailure=new IllegalStateException("first owner callback");var secondFailure=new AssertionError("second owner callback");
            first.onOwnerClose(()->{throw firstFailure;});second.onOwnerClose(()->{throw secondFailure;});
            try {
                var failure=assertThrows(AssertionError.class,program::close);
                assertSame(secondFailure,failure);assertArrayEquals(new Throwable[]{firstFailure},failure.getSuppressed());
                assertEquals(before,resources.heapUsed(),"all common leases and program storage are retired despite callback failure");
                assertEquals(shape,snapshot.shape(root),"cleanup cannot close borrowed AIR");
                assertThrows(IllegalStateException.class,()->first.grow(1,AnalysisResources.Phase.CONTROL));
                assertThrows(IllegalStateException.class,()->second.grow(1,AnalysisResources.Phase.DOMAIN));
                first.close();second.close();program.close();assertEquals(before,resources.heapUsed());assertEquals(0,execution.heapUsed());
            }finally{first.close();second.close();program.close();}
        }
        released(resources);
    }
    private static <V extends TextValueFact> void planningLifetime(AnalysisProvider<ObjectId,V> provider,boolean regional) throws Exception {
        var resources=resources();V detached;
        Id producer;OriginId origin;List<OriginId> expectedOrigins;
        try(var input=new DataflowAirReader().readSnapshot(INPUT,resources);
            var program=new SnapshotProgram(input.checked(),input.newIdentityStorage(),input.newOrderStorage(),resources)) {
            var options=BuildOptions.defaults();var cfg=new CfgBuildCoordinator(SemanticInterpreterRegistry.empty()).buildChecked(program,input.checked(),options);
            var unit=program.units().getFirst();var entry=unit.entries().getFirst();
            var session=AnalysisSession.open(cfg,program,options.projectionPolicy(),List.of(entry.id())).session().orElseThrow();
            var invoke=unit.sequences().stream().map(s->s.terminator()).filter(Operations.Invoke.class::isInstance).map(Operations.Invoke.class::cast).findFirst().orElseThrow();
            var assign=unit.sequences().stream().flatMap(s->s.instructions().stream()).filter(Operations.Assign.class::isInstance).findFirst().orElseThrow();
            producer=assign.header().id();origin=assign.header().origin();
            var subject=((Places.ObjectPlace)((Expressions.Read)((Interactions.ComputedTarget)invoke.target()).name()).place()).object();
            var declarationOrigin=unit.objects().stream().filter(o->o.id().equals(subject)).findFirst().orElseThrow().origin();
            expectedOrigins=regional?List.of(origin,declarationOrigin):List.of(origin);
            var key=regional?RegionalValuesProvider.key(entry.id(),StorageAnalysisMode.EXPERIMENTAL_PHYSICAL):PossibleValuesProvider.key(entry.id(),PossibleValuesAnalysis.EFFECTS_PROFILE);
            var batch=new ObservationBatchId<>("lifetime",key,provider.projection(),ObjectId.class,provider.factType());
            var query=new PointQuery<>(ProgramPoint.before(entry.id(),invoke.header().id()),subject);
            var registration=new ConsumerRegistration<V>(new ConsumerPlan("sink",List.of(key),List.of(batch.id())),
                List.of(new SiteInterest(Operations.Invoke.class,entry.id(),site->true)),List.of(new ObservationRequest<>(batch,List.of(query))),
                (site,facts,sink)->sink.emit(facts.lookup(batch,query).observation().value()));
            var runtime=new PlanningExecution(session,new AnalysisRegistry(List.of(provider)));
            try {
                var result=runtime.execute("native-lifetime",runtime.plan(List.of(registration)));
                assertEquals(PreparedAnalysisResult.PreparationStatus.COMPLETE,result.preparationStatus());
                assertEquals(1,result.consumers().getFirst().facts().size());detached=result.consumers().getFirst().facts().getFirst();
                long held=resources.heapUsed();long points=result.analyses().getFirst().metrics().get("analysisPoints");
                runtime.close();
                assertTrue(held-resources.heapUsed()>=64+16*points,"planning close releases the two real stable reference arrays from the common ledger");
                long closed=resources.heapUsed();runtime.close();assertEquals(closed,resources.heapUsed());
            }finally{runtime.close();}
        }
        released(resources);
        assertEquals(ValueFact.Reachability.REACHABLE,detached.reachability());
        assertEquals(List.of(new Values.TextValue("PROGA   ")),detached.candidates());
        assertEquals(List.of(producer),detached.evidence());assertEquals(expectedOrigins,detached.provenance());
        assertEquals(producer,detached.candidateSupports().getFirst().producers().getFirst().evidence());
        assertFalse(detached.modelValueRemainder());assertTrue(detached.sourceUnknownRemainder());assertFalse(detached.effectiveUnknownRemainder());
    }
}
