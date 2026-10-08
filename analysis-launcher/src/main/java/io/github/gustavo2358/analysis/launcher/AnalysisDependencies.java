package io.github.gustavo2358.analysis.launcher;

import io.github.gustavo2358.air.json.AirJsonException;
import io.github.gustavo2358.air.validation.ValidationResult;
import io.github.gustavo2358.analysis.adapters.*;
import io.github.gustavo2358.analysis.dependencies.SnapshotDependencyAnalysis;
import io.github.gustavo2358.analysis.solver.AnalysisResources;
import java.io.*;
import java.nio.file.*;
import java.time.Duration;

/** AIR JSON -> typed/validated snapshot -> direct dependency result. */
public final class AnalysisDependencies {
    private AnalysisDependencies(){ }
    public static void main(String[] args){System.exit(run(args,System.err));}
    public static int run(String[] args,PrintStream err){return run(args,err,new DataflowAirReader());}
    static int run(String[] args,PrintStream err,DataflowAirReader reader) {
        if(args.length!=2||args[0].isBlank()||args[1].isBlank()){err.println("usage: analysis-dependencies <input.air.json> <output.dependencies.json>");return 2;}
        Path input,output;try{input=Path.of(args[0]);output=Path.of(args[1]);}catch(InvalidPathException failure){err.println("INVALID_PATH");return 2;}
        var resources=AnalysisResources.withDeadline(new AnalysisResources.Limits(64L*1024*1024,16L*1024*1024,0,16L*1024*1024*1024,8,Long.MAX_VALUE,4L*1024*1024*1024),Duration.ofMinutes(8));
        try(var read=reader.readSnapshot(input,resources)) {
            var validation=read.checked().result();
            if(validation.status()!=ValidationResult.Status.STRUCTURALLY_VALID){err.println("INPUT_VALIDATION: "+validation.status());return validation.status()==ValidationResult.Status.INVALID_IR?3:7;}
            try(var result=new SnapshotDependencyAnalysis().open(read.checked(),read.newIdentityStorage(),read.newDependencyStorage())) {
                try{new SnapshotDependencyFileWriter().write(result,output,resources);}catch(IOException|IllegalArgumentException failure){err.println("OUTPUT_FAILURE: "+failure.getMessage());return 6;}
            }
            return 0;
        }catch(AnalysisResources.Exhausted failure){err.println((failure.resource()==AnalysisResources.Resource.TIME?"ANALYSIS_TIME_LIMIT: ":"ANALYSIS_RESOURCE_LIMIT: ")+failure.getMessage());return 7;}
        catch(AirJsonException failure){err.println("INPUT_CODEC: "+failure.code());return failure.code()==AirJsonException.Code.RESOURCE_LIMIT?7:3;}
        catch(IOException|IllegalArgumentException failure){err.println("DEPENDENCY_INPUT_INVALID: "+failure.getMessage());return 3;}
        catch(RuntimeException failure){err.println("ANALYSIS_EXECUTION_FAILED: "+failure.getClass().getSimpleName());return 5;}
    }
}
