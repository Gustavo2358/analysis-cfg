package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.air.validation.*;
import io.github.gustavo2358.analysis.cfg.application.*;
import io.github.gustavo2358.analysis.cfg.extension.SemanticInterpreterRegistry;
import io.github.gustavo2358.analysis.dependencies.*;
import io.github.gustavo2358.analysis.solver.*;
import io.github.gustavo2358.analysis.structure.*;
import io.github.gustavo2358.analysis.values.StorageAnalysisMode;
import io.github.gustavo2358.analysis.values.PossibleValuesAnalysis;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static io.github.gustavo2358.analysis.adapters.ResultFixtures.*;

/** Prospective order-control capacity laws; not whole-plan residency or allstore. */
final class NativeScalarAdmissionLifecycleTest {
    private enum Fault { NONE, RESERVE, GROW, EXPIRE, QUOTA, VIEW_RESERVE, VIEW_QUOTA, VIEW_EXPIRE }
    private static final class InjectedFailure extends RuntimeException {
        private static final long serialVersionUID=1L;
    }
    private static final class Budget implements ProgramStore.ExecutionBudget {
        final SnapshotProgram program;
        final AnalysisResources resources;
        final ProgramStore.ExecutionBudget delegate;
        final InjectedFailure marker=new InjectedFailure();
        Fault fault=Fault.NONE;
        int reserves,grows,live,viewReserves;
        Budget(SnapshotProgram program,AnalysisResources resources){this.program=program;this.resources=resources;delegate=program.executionBudget().orElseThrow();}
        public ProgramStore.CapacityLease reserve(ProgramStore.CapacityPool pool,long amount,ProgramStore.ExecutionPhase phase) {
            boolean order=pool==ProgramStore.CapacityPool.SCRATCH&&phase==ProgramStore.ExecutionPhase.DEMAND&&amount==160;
            boolean view=pool==ProgramStore.CapacityPool.SCRATCH&&phase==ProgramStore.ExecutionPhase.DEMAND&&amount==512;
            if(view) {
                viewReserves++;
                if(fault==Fault.VIEW_RESERVE)throw marker;
                if(fault==Fault.VIEW_EXPIRE)program.close();
                if(fault==Fault.VIEW_QUOTA) {
                    try(var held=resources.reserve(AnalysisResources.Pool.SCRATCH,resources.limits().scratchBytes()-resources.used(AnalysisResources.Pool.SCRATCH),AnalysisResources.Phase.CONTROL)) {
                        assertTrue(held.amount()>0);return delegate.reserve(pool,amount,phase);
                    }
                }
            }
            if(order){reserves++;if(fault==Fault.RESERVE)throw marker;}
            if(order&&fault==Fault.QUOTA) {
                try(var held=resources.reserve(AnalysisResources.Pool.SCRATCH,resources.limits().scratchBytes()-resources.used(AnalysisResources.Pool.SCRATCH),AnalysisResources.Phase.CONTROL)) {
                    assertTrue(held.amount()>0);return delegate.reserve(pool,amount,phase);
                }
            }
            var lease=delegate.reserve(pool,amount,phase);
            if(!order)return lease;
            live++;
            return new ProgramStore.CapacityLease() {
                private boolean closed;
                public void grow(long bytes,ProgramStore.ExecutionPhase stage) {
                    assertEquals(32,bytes);assertEquals(ProgramStore.ExecutionPhase.DEMAND,stage);grows++;
                    if(fault==Fault.GROW)throw marker;
                    if(fault==Fault.EXPIRE)program.close();
                    lease.grow(bytes,stage);
                }
                public void absorb(ProgramStore.CapacityLease staged){lease.absorb(staged);}
                public void onOwnerClose(Runnable cleanup){lease.onOwnerClose(cleanup);}
                public void close(){if(!closed){closed=true;live--;lease.close();}}
            };
        }
        public void output(long bytes,ProgramStore.ExecutionPhase phase){delegate.output(bytes,phase);}
    }
    @FunctionalInterface private interface Work {void run(AnalysisSession session,SnapshotProgram program,AnalysisResources resources,Budget budget,Map<CallDependencyPlan.SiteKey,CallDependencyPlan.Provider> expected)throws Exception;}
    private static void nativeModel(boolean computed,Work work)throws Exception {
        var publication=W1dModelTest.model(3,u->{
            var seeds=new ArrayList<Instruction>();
            for(int at=0;at<3;at++)seeds.add(assign(u,"seed-"+at,new ObjectId(u,"object-"+at),"PROGA   "));
            var sequences=new ArrayList<Sequence>();
            for(int at=0;at<6;at++)sequences.add(new Sequence(new LabelId(u,at==0?"start":"site-"+at),at==0?seeds:List.of(),W1dModelTest.call(u,"call-"+at,at==5?"end":"site-"+(at+1),computed?new ObjectId(u,"object-"+(at%3)):null,false),origin(u.publication())));
            sequences.add(returning(u,"end",List.of()));return sequences;
        });
        var expected=new HashMap<CallDependencyPlan.SiteKey,CallDependencyPlan.Provider>();var unit=publication.units().getFirst();var entry=unit.entries().getFirst().id();
        for(int at=0;at<6;at++)expected.put(new CallDependencyPlan.SiteKey(entry,new OperationId(unit.id(),"call-"+at)),computed?CallDependencyPlan.Provider.SCALAR:CallDependencyPlan.Provider.LITERAL);
        var temporary=Files.createTempDirectory(Path.of("target"),"native-scalar-admission-");
        for(boolean file:new boolean[]{false,true}) {
            var resources=new AnalysisResources(new AnalysisResources.Limits(64L*1024*1024,16L*1024*1024,0,256L*1024*1024,8,Long.MAX_VALUE,Long.MAX_VALUE));
            try(PageStore pages=file?new FilePageStore(temporary,512,1,resources):new MemoryPageStore(512,resources);
                    var snapshot=AirSnapshot.fromPublication(publication);
                    var checked=SnapshotValidator.check(snapshot,ValidationOptions.defaults(),new PagedSnapshotValidationStorage(pages,resources));
                    var program=new SnapshotProgram(checked,new PagedSnapshotIdentityStorage(pages,resources),new PagedSnapshotOrderStorage(pages,resources),resources)) {
                assertTrue(checked.result().isStructurallyValid());
                var cfg=new CfgBuildCoordinator(SemanticInterpreterRegistry.empty()).buildChecked(program,checked,BuildOptions.defaults());assertEquals(CfgBuildResult.Status.CFG_BUILT,cfg.status());
                var budget=new Budget(program,resources);
                var store=(ProgramStore.Structural)Proxy.newProxyInstance(ProgramStore.Structural.class.getClassLoader(),new Class<?>[]{ProgramStore.Structural.class},(proxy,method,args)->{
                    if(method.getName().equals("executionBudget"))return Optional.of(budget);
                    try{return method.invoke(program,args);}catch(InvocationTargetException failure){throw failure.getCause();}
                });
                var opened=AnalysisSession.open(cfg,store,cfg.options().projectionPolicy(),List.of(entry));assertEquals(AnalysisSession.Status.ACCEPTED,opened.status());
                work.run(opened.session().orElseThrow(),program,resources,budget,expected);
                assertEquals(0,budget.live,"new order controls retired even after failure/owner expiry");
            } finally {for(var pool:AnalysisResources.Pool.values())assertEquals(0,resources.used(pool),pool.toString());}
        }
    }
    private static CallDependencyPlan.Selection choose(AnalysisSession session){return CallDependencyPlan.choose(session,"capacity",false,StorageAnalysisMode.LOGICAL_ONLY);}
    @Test void repeatedSubjectsFundOneOrderControlBeforeGrowthAndRetireIt()throws Exception {
        nativeModel(true,(session,program,resources,budget,expected)->{
            long before=resources.used(AnalysisResources.Pool.SCRATCH);var selection=choose(session);
            assertEquals(expected,selection.providers());assertEquals(3L,selection.metrics().get("scalarAdmissionAttempts"));assertEquals(3L,selection.metrics().get("scalarAdmissionAccepted"));assertEquals(0L,selection.metrics().get("scalarAdmissionRefused"));
            assertEquals(1,budget.reserves,"SCALAR_ADMISSION_ORDER_CONTROL_UNFUNDED_RED");assertEquals(2,budget.grows,"duplicates must not allocate another order link");assertEquals(before,resources.used(AnalysisResources.Pool.SCRATCH));assertNotNull(program.admission().snapshot());
        });
    }
    @Test void literalOnlyPlanNeverReservesScalarOrderControls()throws Exception {
        nativeModel(false,(session,program,resources,budget,expected)->{
            var selection=choose(session);assertEquals(expected,selection.providers());assertEquals(0L,selection.metrics().get("scalarAdmissionAttempts"));assertEquals(0,budget.reserves);assertEquals(0,budget.grows);assertNotNull(program.admission().snapshot());
        });
    }
    @Test void reserveAndGrowthFailuresAreOperationalAtomicAndAllowAFreshAttempt()throws Exception {
        for(var fault:List.of(Fault.RESERVE,Fault.GROW))nativeModel(true,(session,program,resources,budget,expected)->{
            long before=resources.used(AnalysisResources.Pool.SCRATCH);budget.fault=fault;
            assertSame(budget.marker,assertThrows(InjectedFailure.class,()->choose(session)));assertEquals(0,budget.live);assertEquals(before,resources.used(AnalysisResources.Pool.SCRATCH));assertNotNull(program.admission().snapshot());
            budget.fault=Fault.NONE;var selection=choose(session);assertEquals(expected,selection.providers());assertEquals(3L,selection.metrics().get("scalarAdmissionAccepted"));assertEquals(before,resources.used(AnalysisResources.Pool.SCRATCH));
        });
    }
    @Test void expiredOwnerCannotPublishChoicesAndStillRetiresOrderCapacity()throws Exception {
        nativeModel(true,(session,program,resources,budget,expected)->{
            budget.fault=Fault.EXPIRE;assertThrows(IllegalStateException.class,()->choose(session));assertEquals(1,budget.reserves);assertEquals(1,budget.grows);assertEquals(0,budget.live);assertThrows(IllegalStateException.class,()->program.admission().snapshot());
        });
    }
    @Test void actualScratchDenialBeforeOrderInsertionPreservesTheLiveOwner()throws Exception {
        nativeModel(true,(session,program,resources,budget,expected)->{
            long before=resources.used(AnalysisResources.Pool.SCRATCH);budget.fault=Fault.QUOTA;
            var failure=assertThrows(AnalysisResources.Exhausted.class,()->choose(session));assertEquals(AnalysisResources.Resource.SCRATCH,failure.resource());assertEquals(AnalysisResources.Phase.DEMAND,failure.phase());
            assertEquals(0,budget.live);assertEquals(before,resources.used(AnalysisResources.Pool.SCRATCH));assertNotNull(program.admission().snapshot());
            budget.fault=Fault.NONE;assertEquals(expected,choose(session).providers());assertEquals(before,resources.used(AnalysisResources.Pool.SCRATCH));
        });
    }
    @Test void candidateViewReservationFailureIsOperationalAndAllowsFreshSelection()throws Exception {
        nativeModel(true,(session,program,resources,budget,expected)->{
            long before=resources.used(AnalysisResources.Pool.SCRATCH);budget.fault=Fault.VIEW_RESERVE;
            assertSame(budget.marker,assertThrows(InjectedFailure.class,()->choose(session)));
            assertEquals(1,budget.viewReserves);assertEquals(0,budget.live);assertEquals(before,resources.used(AnalysisResources.Pool.SCRATCH));
            assertNotNull(program.admission().snapshot());budget.fault=Fault.NONE;
            assertEquals(expected,choose(session).providers());assertEquals(2,budget.viewReserves);assertEquals(before,resources.used(AnalysisResources.Pool.SCRATCH));
        });
    }
    @Test void candidateViewActualScratchDenialRetiresOrderLinksWithoutPublishing()throws Exception {
        nativeModel(true,(session,program,resources,budget,expected)->{
            long before=resources.used(AnalysisResources.Pool.SCRATCH);budget.fault=Fault.VIEW_QUOTA;
            var failure=assertThrows(AnalysisResources.Exhausted.class,()->choose(session));
            assertEquals(AnalysisResources.Resource.SCRATCH,failure.resource());assertEquals(AnalysisResources.Phase.DEMAND,failure.phase());
            assertEquals(1,budget.viewReserves);assertEquals(0,budget.live);assertEquals(before,resources.used(AnalysisResources.Pool.SCRATCH));
            assertNotNull(program.admission().snapshot());budget.fault=Fault.NONE;assertEquals(expected,choose(session).providers());
        });
    }
    @Test void expiryBeforeCandidateViewAllocationCannotPublishChoices()throws Exception {
        nativeModel(true,(session,program,resources,budget,expected)->{
            budget.fault=Fault.VIEW_EXPIRE;assertThrows(IllegalStateException.class,()->choose(session));
            assertEquals(1,budget.viewReserves);assertEquals(0,budget.live);
            assertThrows(IllegalStateException.class,()->program.admission().snapshot());
        });
    }
    @Test void decisionProfilesRetireLocallyAndUnknownProfileExpiresWithNativeOwner()throws Exception {
        nativeModel(true,(session,program,resources,budget,expected)->{
            var subject=session.index().objectDeclarations().keySet().iterator().next();
            var effects=PossibleValuesAnalysis.admissionDecisions(session,PossibleValuesAnalysis.EFFECTS_PROFILE);
            var direct=PossibleValuesAnalysis.admissionDecisions(session,PossibleValuesAnalysis.PROFILE);
            var unknown=PossibleValuesAnalysis.admissionDecisions(session,"unsupported-profile");
            try {
                assertEquals(PossibleValuesAnalysis.Status.ACCEPTED,effects.decide(subject).status());
                assertEquals("UNSUPPORTED_EFFECT_PROFILE",direct.decide(subject).reason());
                var refusal=unknown.decide(subject);assertEquals(PossibleValuesAnalysis.Status.UNSUPPORTED,refusal.status());
                assertEquals("UNSUPPORTED_EFFECT_PROFILE",refusal.reason());assertEquals(0,refusal.unsupportedStorageProfiles());assertEquals(1,refusal.unsupportedEffectProfiles());
                effects.close();effects.close();assertThrows(IllegalStateException.class,()->effects.decide(subject));
                assertEquals("UNSUPPORTED_EFFECT_PROFILE",direct.decide(subject).reason());assertNotNull(program.admission().snapshot());
                program.close();
                assertThrows(IllegalStateException.class,()->direct.decide(subject));
                assertThrows(IllegalStateException.class,()->unknown.decide(subject));
            } finally {effects.close();direct.close();unknown.close();}
        });
    }
}
