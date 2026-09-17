package io.github.gustavo2358.analysis.values;

import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.analysis.query.*;
import io.github.gustavo2358.analysis.storage.*;
import java.lang.management.ManagementFactory;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import jdk.jfr.Configuration;
import jdk.jfr.Recording;
import static io.github.gustavo2358.analysis.values.ValuesFixtures.*;
import static io.github.gustavo2358.analysis.values.RegionalValuesTest.*;

/** Manual W2 diagnostic, not a timed CI test. Reuses W1 synthetic AIR and real solver. */
public final class RegionalCostProbe {
    private RegionalCostProbe() { }
    public static void main(String[] args) throws Exception {
        int regions=Integer.parseInt(args[0]),producers=Integer.parseInt(args[1]);
        int warmups=Integer.parseInt(args[2]),repeats=Integer.parseInt(args[3]);
        Path output=Path.of(args[4]);Files.createDirectories(output);
        var p=RegionalExplosionFixturesTest.fixture(regions,producers,false);
        var selected=session(p);var admission=RegionalValuesAnalysis.prepare(selected);
        if(admission.status()!=RegionalValuesAnalysis.Status.ACCEPTED)throw new AssertionError(admission);
        var analysis=admission.analysis().orElseThrow();
        var targets=RegionalExplosionFixturesTest.targets(p);
        for(int i=0;i<warmups;i++)analysis.execute();
        resetCounters();
        try(var recording=new Recording(Configuration.getConfiguration("profile"))) {
            recording.enable("jdk.ExecutionSample").withPeriod(Duration.ofMillis(2));
            boolean profile=args.length>5&&args[5].equals("jfr");
            if(profile)recording.start();
            var bean=ManagementFactory.getThreadMXBean();
            long cpu=bean.getCurrentThreadCpuTime(),start=System.nanoTime();
            RegionalValuesAnalysis.Execution execution=null;
            for(int i=0;i<repeats;i++)execution=analysis.execute();
            long nanos=System.nanoTime()-start,cpuNanos=bean.getCurrentThreadCpuTime()-cpu;
            if(profile){recording.stop();recording.dump(output.resolve("solve.jfr"));}
            if(execution==null)throw new IllegalArgumentException("positive repeats required");
            System.out.printf(Locale.ROOT,"W2_COST regions=%d producers=%d repeats=%d wallNs=%d threadCpuNs=%d targets=%d unproven=%d solve=%s%n",
                regions,producers,repeats,nanos,cpuNanos,targets.size(),RegionalExplosionFixturesTest.unproven(targets),new TreeMap<>(execution.solveMetrics()));
            printCounters();
            var facts=new StringBuilder();
            var typedFacts=new ArrayList<StorageValueFact>();
            for(int i=0;i<regions;i++) {
                var query=new PointQuery<StorageSubject>(ProgramPoint.before(new EntryId(U,"entry"),new OperationId(U,"return-s0")),
                    new StorageSubject.NamedObject(RegionalExplosionFixturesTest.object(i)));
                var batch=execution.observeStorage(List.of(query));
                if(batch.status()!=ObservationBatch.Status.COMPLETE)throw new AssertionError(batch.status());
                var fact=batch.observations().getFirst().value();
                typedFacts.add(fact);facts.append(fact).append('\n');
            }
            // Keep the W2 text diagnostic; W3 freezes every typed field independently of toString.
            Files.writeString(output.resolve("facts.txt"),facts);
            Files.writeString(output.resolve("facts.typed"),RegionalSemanticSnapshot.encode(typedFacts));
            Files.writeString(output.resolve("targets.typed"),RegionalSemanticSnapshot.encode(targets));
            System.out.println("W3_FACTS_SHA256 "+RegionalSemanticSnapshot.digest(typedFacts));
            System.out.println("W3_TARGETS_SHA256 "+RegionalSemanticSnapshot.digest(targets));
            Files.writeString(output.resolve("metrics.txt"),new TreeMap<>(execution.solveMetrics()).toString()+"\n");
        }
    }
    // Optional counters exist only in separately compiled diagnostic overlays, never production.
    private static List<Class<?>> counterClasses(){return List.of(FactorizedAlternatives.class,ByteImage.class,RegionalValuesAnalysis.class);}
    private static void resetCounters() throws IllegalAccessException {
        for(var type:counterClasses())for(var field:type.getDeclaredFields())if(field.getName().startsWith("w2")) {
            field.setAccessible(true);if(field.getType()==long.class)field.setLong(null,0);
            else if(field.get(null) instanceof Map<?,?> map)map.clear();
        }
    }
    private static void printCounters() throws IllegalAccessException {
        var counters=new TreeMap<String,Object>();
        for(var type:counterClasses())for(var field:type.getDeclaredFields())if(field.getName().startsWith("w2")) {
            field.setAccessible(true);Object value=field.get(null);
            if(value instanceof Map<?,?> map)value=map.size();
            counters.put(type.getSimpleName()+"."+field.getName(),value);
        }
        System.out.println("W2_WORK "+counters);
    }
}
