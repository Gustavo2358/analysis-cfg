package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.validation.*;
import io.github.gustavo2358.analysis.cfg.application.*;
import io.github.gustavo2358.analysis.cfg.extension.SemanticInterpreterRegistry;
import io.github.gustavo2358.analysis.dependencies.*;
import io.github.gustavo2358.analysis.solver.*;
import io.github.gustavo2358.analysis.structure.*;
import io.github.gustavo2358.analysis.values.StorageAnalysisMode;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Exact literal provider selection without reconstructing unrelated Invoke payload. */
final class NativeCallPlanTargetTest {
    @TempDir Path directory;
    private static final class GuardedSource implements AirSnapshot.Source {
        private final AirSnapshot.Source delegate;
        private boolean choosing;
        private long payloadReads;
        GuardedSource(AirSnapshot.Source delegate){this.delegate=delegate;}
        public AirShape shape(long handle){return delegate.shape(handle);}
        public long length(long handle){return delegate.length(handle);}
        public long child(long handle,long ordinal){
            if(choosing&&shape(handle)==AirShape.OPERATIONS_INVOKE&&ordinal>=3){payloadReads++;throw new AssertionError("NATIVE_CALL_PLAN_RECONSTRUCTS_UNRELATED_INVOKE_PAYLOAD_RED");}
            return delegate.child(handle,ordinal);
        }
        public long scalar(long handle){return delegate.scalar(handle);}
        public int characters(long handle,long offset,char[] destination,int start,int count){return delegate.characters(handle,offset,destination,start,count);}
        public AirSnapshot.Elements elements(long handle){return delegate.elements(handle);}
        public void close(){delegate.close();}
    }
    @Test void literalProviderChoiceReadsOnlyTypedSelectionMetadataAndCompleteIdentities()throws Exception {
        for(int count:new int[]{4,16,64}){
            var fixture=SnapshotDependencyAnalysisTest.class.getDeclaredMethod("directCalls",int.class);fixture.setAccessible(true);
            var original=(Publication)fixture.invoke(null,count);var units=new ArrayList<Unit>();
            var expected=new HashMap<CallDependencyPlan.SiteKey,CallDependencyPlan.Provider>();
            for(int at=0;at<count;at++){
                var unit=original.units().get(at);var sequence=unit.sequences().getFirst();var call=(Operations.Invoke)sequence.terminator();
                int variant=at%4;String category=variant==2?"file":"program",namespace=variant==3?"other.program":variant==1?"cics.program":"cobol.program";
                var target=new Interactions.LiteralTarget(category,namespace,"PROGA",Interactions.ExactName.INSTANCE,unit.origin());
                var replacement=new Operations.Invoke(call.header(),call.action(),target,call.arguments(),call.results(),call.signature(),call.effectOperands(),call.effectBound(),call.outcomes(),call.contract());
                var sequences=new ArrayList<>(unit.sequences());sequences.set(0,new Sequence(sequence.label(),sequence.instructions(),replacement,sequence.origin()));Collections.reverse(sequences);
                units.add(ResultFixtures.unit(unit.id(),unit.entries(),sequences,unit.objects()));
                if(variant<2)for(var entry:unit.entries())expected.put(new CallDependencyPlan.SiteKey(entry.id(),call.header().id()),CallDependencyPlan.Provider.LITERAL);
            }
            Collections.reverse(units);var publication=ResultFixtures.publication(original.id(),units,original.storage());
            var resources=new AnalysisResources(new AnalysisResources.Limits(64_000_000,16_000_000,0,256_000_000,8,Long.MAX_VALUE,Long.MAX_VALUE));
            try(var pages=new FilePageStore(directory,512,1,resources);var snapshot=AirSnapshot.fromPublication(publication)){
                var field=AirSnapshot.class.getDeclaredField("source");field.setAccessible(true);var observed=new GuardedSource((AirSnapshot.Source)field.get(snapshot));
                try(var checked=SnapshotValidator.check(AirSnapshot.attach(observed,snapshot.root()),ValidationOptions.defaults(),new PagedSnapshotValidationStorage(pages,resources));
                    var program=new SnapshotProgram(checked,new PagedSnapshotIdentityStorage(pages,resources),new PagedSnapshotOrderStorage(pages,resources),resources)){
                    assertTrue(checked.result().isStructurallyValid(),checked.result().toString());
                    var cfg=new CfgBuildCoordinator(SemanticInterpreterRegistry.empty()).buildChecked(program,checked,BuildOptions.defaults());
                    assertEquals(CfgBuildResult.Status.CFG_BUILT,cfg.status());
                    var entries=publication.units().stream().flatMap(unit->unit.entries().stream()).map(Entries.Entry::id).toList();
                    var admission=AnalysisSession.open(cfg,program,cfg.options().projectionPolicy(),entries);assertEquals(AnalysisSession.Status.ACCEPTED,admission.status());
                    observed.choosing=true;
                    var selection=CallDependencyPlan.choose(admission.session().orElseThrow(),"call",false,StorageAnalysisMode.LOGICAL_ONLY);
                    assertEquals(0,observed.payloadReads);assertEquals(expected,selection.providers(),"manual complete Entry/Operation keys,exact category/namespace exclusions");
                    assertEquals(expected.size(),selection.registrations().size());
                    for(var metric:selection.metrics().entrySet())assertEquals(Set.of("targetResolutionRequests","entryCallCandidatesVisited").contains(metric.getKey())?(long)expected.size():0L,metric.getValue());
                    observed.choosing=false;assertNotNull(program.admission().snapshot());
                    System.out.println("NATIVE_CALL_PLAN_TARGET count="+count+" selected="+expected.size()+" payloadReads="+observed.payloadReads);
                }
            }finally{for(var pool:AnalysisResources.Pool.values())assertEquals(0,resources.used(pool),pool.toString());}
        }
    }
}
