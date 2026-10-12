package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.analysis.application.*;
import io.github.gustavo2358.analysis.cfg.application.CfgBuildResult;
import io.github.gustavo2358.analysis.dependencies.SnapshotProgram;
import io.github.gustavo2358.analysis.plan.*;
import io.github.gustavo2358.analysis.structure.*;
import io.github.gustavo2358.analysis.solver.*;
import java.lang.reflect.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Fully delegated read observation, not a complete-provider/root heap witness. */
final class NativeSitePlanOwnershipTest {
    private static final class ObservedSource implements AirSnapshot.Source {
        private final AirSnapshot.Source source;
        private long invokePayloadReads;
        ObservedSource(AirSnapshot.Source source){this.source=source;}
        @Override public AirShape shape(long handle){return source.shape(handle);}
        @Override public long length(long handle){return source.length(handle);}
        @Override public long child(long handle,long ordinal){if(shape(handle)==AirShape.OPERATIONS_INVOKE&&ordinal>=3)invokePayloadReads++;return source.child(handle,ordinal);}
        @Override public long scalar(long handle){return source.scalar(handle);}
        @Override public int characters(long handle,long offset,char[] destination,int start,int count){return source.characters(handle,offset,destination,start,count);}
        @Override public AirSnapshot.Elements elements(long handle){return source.elements(handle);}
        @Override public void close(){source.close();}
    }
    @Test void nativePlanRetainsPagedSelectionNotCompleteColdOperationsAndExpiresWithExecution()throws Exception {
        var fixture=new ManagedExecutableDependencyResultTest();
        fixture.directory=java.nio.file.Files.createTempDirectory(java.nio.file.Path.of("target"),"native-site-plan-");
        var contract=Class.forName(ManagedExecutableDependencyResultTest.class.getName()+"$Work");long[] fixed={-1};
        for(int count:new int[]{4,16,64}){
            var callback=Proxy.newProxyInstance(contract.getClassLoader(),new Class<?>[]{contract},(proxy,method,args)->{
                var program=(SnapshotProgram)args[0];var cfg=(CfgBuildResult)args[1];var resources=(AnalysisResources)args[3];var root=(AnalysisResources)args[4];var pages=(PageStore)args[2];
                var admission=program.admission();var snapshot=admission.snapshot();
                var field=AirSnapshot.class.getDeclaredField("source");field.setAccessible(true);var observer=new ObservedSource((AirSnapshot.Source)field.get(snapshot));field.set(snapshot,observer);
                var entries=program.units().stream().flatMap(unit->unit.entryViews().stream()).map(ProgramStore.EntryView::id).sorted(AnalysisKey.ENTRY_ORDER).toList();
                var opened=AnalysisSession.open(cfg,program,cfg.options().projectionPolicy(),entries);assertEquals(AnalysisSession.Status.ACCEPTED,opened.status());var session=opened.session().orElseThrow();
                program.operationDirectory().orElseThrow().size();program.storageInventory().orElseThrow().size();
                var expected=new ArrayList<io.github.gustavo2358.analysis.consumers.SiteView>();
                for(var entry:entries){var site=session.index().sites(Operations.Invoke.class,entry.unit()).getFirst();expected.add(new io.github.gustavo2358.analysis.consumers.SiteView(entry,site.sequence().label(),site.offset(),site.operation()));}
                var interests=new ArrayList<SiteInterest>();
                for(var entry:entries)interests.add(new SiteInterest(Operations.Invoke.class,entry,site->site.operationId().localId().equals("invoke"),List.of()));
                Collections.reverse(interests);interests.add(interests.getFirst());
                var registration=new ConsumerRegistration<String>(new ConsumerPlan("cold-plan",List.of(),List.of()),interests,List.of(),(site,facts,sink)->sink.emit(site.operationId().localId()));
                long before=root.heapUsed(),live=pages.statistics().livePages();List<io.github.gustavo2358.analysis.consumers.SiteView> selected;
                try(var execution=new PlanningExecution(session,new AnalysisRegistry(List.of()))){
                    var plan=execution.plan(List.of(registration));selected=plan.sites().get("cold-plan");assertEquals(count,selected.size());assertEquals((long)count,plan.metrics().get("siteMatches"));assertEquals(count+1L,plan.metrics().get("filterEvaluations"));
                    long delta=root.heapUsed()-before;if(fixed[0]<0)fixed[0]=delta;assertEquals(fixed[0],delta,"funded native selection control is cardinality independent");
                    observer.invokePayloadReads=0;
                    for(int i=count-1;i>=0;i--)assertEquals(expected.get(i),selected.get(i),"complete record fields in canonical Entry/Operation order despite physical registration permutation and duplicate interest");
                    assertTrue(observer.invokePayloadReads>0,"PLANNING_RETAINS_COLD_COMPLETE_BODY_RED: each returned site must borrow and reconstruct its original operation,not retain every decoded body");
                    assertThrows(UnsupportedOperationException.class,selected::clear);assertThrows(IndexOutOfBoundsException.class,()->selected.get(-1));assertEquals(expected.getFirst(),selected.getFirst());
                    var result=execution.execute("cold-sites",plan);assertEquals(PreparedAnalysisResult.PreparationStatus.COMPLETE,result.preparationStatus());assertEquals(count,result.consumers().getFirst().facts().size());
                    System.out.println("NATIVE_SITE_PLAN count="+count+" fixed="+delta+" payloadReads="+observer.invokePayloadReads);
                }
                assertThrows(IllegalStateException.class,selected::size);assertEquals(before,root.heapUsed());assertEquals(live,pages.statistics().livePages());assertNotNull(program.admission().snapshot());assertTrue(root.heapUsed()>0,"borrowed input belongs to root,not the now-empty execution fork");
                return null;
            });
            var input=ManagedExecutableDependencyResultTest.class.getDeclaredMethod("nativeInput",int.class,contract);input.setAccessible(true);
            try{input.invoke(fixture,count,callback);}catch(InvocationTargetException failure){if(failure.getCause() instanceof Exception error)throw error;if(failure.getCause() instanceof Error error)throw error;throw failure;}
        }
    }
}
