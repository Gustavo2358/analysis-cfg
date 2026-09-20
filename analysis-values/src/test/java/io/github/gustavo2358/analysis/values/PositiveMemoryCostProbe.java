package io.github.gustavo2358.analysis.values;

import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.analysis.query.*;
import io.github.gustavo2358.analysis.storage.StorageSubject;
import io.github.gustavo2358.analysis.solver.DataflowResult;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import static io.github.gustavo2358.analysis.values.ValuesFixtures.session;
import static io.github.gustavo2358.analysis.values.RegionalValuesTest.U;

/** Manual, synthetic, TEST ONLY: phase timing and read-only inspection of retained
 * interned history. No counters injected into production, no semantic overlays,
 * and no time threshold or iteration/candidate cap in the solver. Run under an
 * external timeout. Counts are records/IDs, never a claim about retained bytes. */
public final class PositiveMemoryCostProbe {
    private PositiveMemoryCostProbe() { }
    public static void main(String[] args) throws Exception {
        if(args.length!=1)throw new IllegalArgumentException("output directory required");
        var output=Path.of(args[0]);Files.createDirectories(output);
        var measurements=new ArrayList<Map<String,Object>>();
        for(int[] scale:List.of(new int[]{4,5},new int[]{16,50},new int[]{32,100})) {
            measure(scale[0],scale[1],-1,output); // One full setup/prepare/solve/replay warmup per scale.
            for(int repeat=0;repeat<3;repeat++) {
                var result=measure(scale[0],scale[1],repeat,output);measurements.add(result);
                System.out.println(json(result));
            }
        }
        var report=new TreeMap<String,Object>();
        report.put("javaVersion",System.getProperty("java.version"));report.put("vm",System.getProperty("java.vm.name"));
        report.put("warmupsPerScale",1);report.put("repeatsPerScale",3);report.put("maxHeapBytes",Runtime.getRuntime().maxMemory());
        report.put("requestedMode",StorageAnalysisMode.EXPERIMENTAL_PHYSICAL.name());
        report.put("sourceProfile","synthetic AIR / text.ebcdic.ibm1047@1");
        report.put("method","fresh preparation and engine per sample; nanoTime wall durations; setup excludes all phases; no GC forcing or memory-byte measurement");
        report.put("historyMethod","all nodes retained in the actual engine interner, not only live roots; edge-reference positions and identity-deduplicated Events arrays reported separately; producer IDs include concrete byte-image labels");
        report.put("samples",measurements);Files.writeString(output.resolve("measurements.json"),json(report)+"\n");
    }
    private static Map<String,Object> measure(int bases,int producers,int repeat,Path output) throws Exception {
        long start=System.nanoTime();var publication=RegionalExplosionFixturesTest.fixture(bases,producers,false);
        if(!publication.premises().isEmpty())throw new AssertionError("fixture must not carry negative separation premises");
        long setup=System.nanoTime()-start;start=System.nanoTime();
        var selected=session(publication);
        var admission=RegionalValuesAnalysis.prepare(selected,StorageAnalysisMode.EXPERIMENTAL_PHYSICAL);
        if(admission.status()!=RegionalValuesAnalysis.Status.ACCEPTED)throw new AssertionError(admission);
        var analysis=admission.analysis().orElseThrow();long prepare=System.nanoTime()-start;
        start=System.nanoTime();var execution=analysis.execute();long solve=System.nanoTime()-start;
        if(execution.dataflow().status()!=DataflowResult.Status.STABLE)throw new AssertionError("solver did not reach fixed point");
        var queries=new ArrayList<PointQuery<StorageSubject>>();
        for(int i=0;i<bases;i++)queries.add(new PointQuery<>(ProgramPoint.before(new EntryId(U,"entry"),new OperationId(U,"return-s0")),
            new StorageSubject.NamedObject(RegionalExplosionFixturesTest.object(i))));
        var engine=field(execution,"engine");var beforeHistory=history(engine);
        var beforeCounters=execution.metrics();
        start=System.nanoTime();var batch=execution.observeStorage(queries);long replay=System.nanoTime()-start;
        if(batch.status()!=ObservationBatch.Status.COMPLETE)throw new AssertionError(batch.status());
        var facts=batch.observations().stream().map(o->o.value()).toList();
        // Batch output orders subjects canonically (lexical IDs), which differs from
        // numeric fixture construction at 10+. Compare complete records keyed by
        // subject; do not normalize any fact, timing, range or provenance field.
        var expected=RegionalStructuralOracleTest.expectedFacts(bases,producers).stream().collect(java.util.stream.Collectors.toMap(StorageValueFact::subject,java.util.function.Function.identity()));
        var observed=facts.stream().collect(java.util.stream.Collectors.toMap(StorageValueFact::subject,java.util.function.Function.identity()));
        if(facts.size()!=bases||!expected.equals(observed))throw new AssertionError("independent complete typed oracle mismatch");
        var preparation=execution.preparationMetrics();var targets=RegionalExplosionFixturesTest.targets(publication);
        if(targets.size()!=producers||RegionalExplosionFixturesTest.unproven(targets)!=0||preparation.get("basesIndexed")!=bases)
            throw new AssertionError("positive topology did not preserve bases and localize targets");
        var delta=new TreeMap<String,Long>();execution.metrics().forEach((key,value)->delta.put(key,value-beforeCounters.getOrDefault(key,0L)));
        var result=new TreeMap<String,Object>();result.put("bases",bases);result.put("producers",producers);result.put("repeat",repeat);
        result.put("mode",field(analysis,"mode").toString());result.put("disjointPremises",0);result.put("targets",targets.size());result.put("unprovenTargets",0);
        result.put("setupNanos",setup);result.put("prepareNanos",prepare);result.put("solveNanos",solve);result.put("replayNanos",replay);
        result.put("preparation",new TreeMap<>(preparation));result.put("solve",new TreeMap<>(execution.solveMetrics()));
        result.put("observation",recordFields(batch.metrics()));result.put("replayEngineDelta",delta);
        result.put("historyAfterSolve",beforeHistory);result.put("historyAfterReplay",history(engine));
        if(repeat>=0) {
            var stem=bases+"-"+producers+"-"+repeat;
            Files.writeString(output.resolve(stem+".facts.typed"),RegionalSemanticSnapshot.encode(facts));
            result.put("factsSha256",RegionalSemanticSnapshot.digest(facts));
        }
        return result;
    }
    private static Map<String,Long> history(Object engine) throws Exception {
        var relations=field(engine,"relations");var interner=(Map<?,?>)field(relations,"interned");
        var eventObjects=Collections.newSetFromMap(new IdentityHashMap<Object,Boolean>());
        var producers=new HashSet<Integer>();long references=0,positions=0,uniquePositions=0,edges=0;
        for(var node:interner.values())for(var edge:(Set<?>)field(node,"edges")) {
            edges++;var label=field(edge,"label");
            if(label.getClass().getSimpleName().equals("Unknown")) {
                var events=field(label,"events");var values=(int[])field(events,"values");
                references++;positions+=values.length;
                if(eventObjects.add(events))uniquePositions+=values.length;
                for(int id:values)producers.add(id);
            } else {
                var value=field(label,"value");
                if(value.getClass().getSimpleName().equals("Bytes"))for(var part:((ByteImage)field(value,"image")).parts()) {
                    if(part.producer()>=0)producers.add(part.producer());
                    producers.addAll(part.capturedOffsets().keySet());producers.addAll(part.coInitial().keySet());
                }
            }
        }
        return Map.of("internedNodes",(long)interner.size(),"internedEdgeReferences",edges,"compactEventsReferences",references,
            "compactEventsHistoricalPositions",positions,"uniqueCompactEventsObjects",(long)eventObjects.size(),
            "uniqueCompactEventsArrayPositions",uniquePositions,"uniqueProducerIdsInHistory",(long)producers.size());
    }
    private static Object field(Object owner,String name) throws Exception {
        var field=owner.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(owner);
    }
    private static Map<String,Object> recordFields(Object record) throws Exception {
        var fields=new TreeMap<String,Object>();for(var component:record.getClass().getRecordComponents())fields.put(component.getName(),component.getAccessor().invoke(record));return fields;
    }
    private static String json(Object value) {
        if(value==null)return "null";
        if(value instanceof String text)return "\""+text.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n")+"\"";
        if(value instanceof Number||value instanceof Boolean)return value.toString();
        if(value instanceof Map<?,?> map)return map.entrySet().stream().map(e->json(e.getKey().toString())+":"+json(e.getValue())).collect(java.util.stream.Collectors.joining(",","{","}"));
        if(value instanceof List<?> list)return list.stream().map(PositiveMemoryCostProbe::json).collect(java.util.stream.Collectors.joining(",","[","]"));
        throw new IllegalArgumentException("unsupported metric type "+value.getClass());
    }
}
