package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.analysis.dataflow.AnalysisDataflow;
import io.github.gustavo2358.analysis.application.PreparedAnalysisResult.PreparationStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

final class WideResultTest {
    @TempDir Path directory;
    @Test void scaleKeepsAllQueriesFactsAndDetachedResult()throws Exception {
        for(int n:new int[]{1000,2000,4000}) {
            long start=System.nanoTime();var p=ResultFixtures.linear(1,n,n,1);var r=new AnalysisDataflow().prepare(p,"scale");
            assertEquals(PreparationStatus.COMPLETE,r.result().preparationStatus(),"size does not change preparation");
            assertEquals(n,r.result().results().getFirst().observations().size(),"all queries materialized");
            assertEquals(n,r.result().consumers().getFirst().facts().size(),"all facts retained");
            assertEquals((long)n,r.compositionMetrics().get("defaultPlanAssignVisits"),"visits follow writes");
            long cardinality=0,producers=0;
            for(var observation:r.result().results().getFirst().observations()) {
                var fact=(io.github.gustavo2358.analysis.values.ValueFact)observation.value();
                assertEquals(List.of(new Values.TextValue("value-"+(n-1))),fact.candidates(),"all aliases see the final write");
                assertEquals(1,fact.candidateSupports().size());var support=fact.candidateSupports().getFirst();assertEquals(fact.candidates().getFirst(),support.candidate());
                assertEquals(List.of(new Ids.OperationId(p.units().getFirst().id(),"instruction-"+(n-1))),support.producers().stream().map(v->v.evidence()).toList(),"all candidates keep final Assign producer");
                cardinality=Math.max(cardinality,fact.candidates().size());producers+=support.producers().size();
            }
            var attempt=new LocalResultWriter().write(r,directory.resolve("result-"+n+".json"));
            assertEquals(DeliveryReceipt.Status.COMPLETE,attempt.receipt().status(),"size does not change delivery");
            long retained=retention(r);long elapsed=System.nanoTime()-start;
            var metrics=new TreeMap<String,Object>();metrics.putAll(r.compositionMetrics());metrics.putAll(attempt.metrics());
            metrics.put("N",n);metrics.put("elapsedNanos",elapsed);metrics.put("retainedLogicalObjects",retained);
            metrics.put("analysisRuns",r.result().metrics().get("analysis").get("analysisRuns"));metrics.put("factsEmitted",r.result().metrics().get("consumer").get("factsEmitted"));
            metrics.putAll(r.result().metrics().get("observation"));
            metrics.put("candidateCardinality",cardinality);metrics.put("producerOccurrences",producers);
            metrics.put("consumerFailures",r.result().metrics().get("consumer").get("consumerFailures"));
            metrics.put("publicationFailures",attempt.metrics().get("deliveryFailures"));
            var out=new JsonOutput(System.out);System.out.print("W5_METRICS ");out.value(metrics);out.finish();
        }
    }
    @Test void resultBeyondLegacy64MiBHasNoOutputCapacityPolicy()throws Exception {
        var p=ResultFixtures.linear(1,1,1,1);var u=p.units().getFirst();var s=u.sequences().getFirst();
        String literal="x".repeat(36*1024*1024);
        var changed=new Sequence(s.label(),List.of(ResultFixtures.assign(u.id(),"instruction-0",u.objects().getFirst().id(),literal)),s.terminator(),s.origin());
        var unit=new Unit(u.id(),u.containingUnit(),u.objects(),u.visibleObjects(),u.entries(),List.of(changed),u.completionPorts(),u.body(),u.bodyUnavailable(),u.coverage(),u.origin());
        var r=new AnalysisDataflow().prepare(ResultFixtures.publication(p.id(),List.of(unit),p.storage()),"large-text");
        var receipt=new LocalResultWriter().write(r,directory.resolve("large.json"));
        assertEquals(DeliveryReceipt.Status.COMPLETE,receipt.receipt().status(),"result output has no size cap");
        assertTrue(receipt.metrics().get("resultBytesWritten")>64L*1024*1024);
        System.out.println("W5_LARGE_BYTES "+receipt.metrics().get("resultBytesWritten"));
    }
    static long retention(Object root)throws Exception {
        var seen=Collections.newSetFromMap(new IdentityHashMap<Object,Boolean>());var todo=new ArrayDeque<Object>();todo.add(root);
        while(!todo.isEmpty()) {
            var value=todo.removeFirst();if(!seen.add(value))continue;
            String name=value.getClass().getSimpleName();
            assertFalse(Set.of("AnalysisSession","ProgramIndex","DataflowResult","PossibleValuesState","ValueUniverse","PlanningExecution","ExecutionPlan","Publication","TextProfile").contains(name),"result retains execution: "+name);
            if(value instanceof Map<?,?> map){for(var e:map.entrySet()){todo.add(e.getKey());if(e.getValue()!=null)todo.add(e.getValue());}}
            else if(value instanceof Iterable<?> list){for(var v:list)if(v!=null)todo.add(v);}
            else if(value.getClass().isArray()&&!value.getClass().getComponentType().isPrimitive()){for(var v:(Object[])value)if(v!=null)todo.add(v);}
            else if(!value.getClass().isEnum()&&value.getClass().getName().startsWith("io.github.gustavo2358.")) {
                for(var f:value.getClass().getDeclaredFields())if(!java.lang.reflect.Modifier.isStatic(f.getModifiers())){f.setAccessible(true);var v=f.get(value);if(v!=null)todo.add(v);}
            }
        }
        return seen.size();
    }
}
