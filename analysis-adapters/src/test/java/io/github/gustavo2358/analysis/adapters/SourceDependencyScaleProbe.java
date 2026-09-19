package io.github.gustavo2358.analysis.adapters;

import java.nio.file.*;
import java.util.Arrays;
import io.github.gustavo2358.air.json.AirJson;
import io.github.gustavo2358.analysis.dependencies.SourceDependencyAnalysis;

/** Local-only timing of nominal aggregation, separately from decoding/JVM startup. */
public final class SourceDependencyScaleProbe {
    private SourceDependencyScaleProbe() {}
    public static void main(String[] args)throws Exception {
        var publication=new AirJson().decode(Files.readAllBytes(Path.of(args[0])));
        var analysis=new SourceDependencyAnalysis();
        for(int i=0;i<5;i++)analysis.prepare(publication);
        var samples=new long[11];
        for(int i=0;i<samples.length;i++){long start=System.nanoTime();analysis.prepare(publication);samples[i]=System.nanoTime()-start;}
        Arrays.sort(samples);var result=analysis.prepare(publication);
        System.out.println("{\"occurrences\":"+result.occurrences()+",\"unique\":"+result.dependencies().size()+",\"aggregationNanosMedian\":"+samples[5]+"}");
    }
}
