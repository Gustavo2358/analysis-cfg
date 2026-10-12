package io.github.gustavo2358.analysis.launcher;

import io.github.gustavo2358.air.json.AirJsonException;
import io.github.gustavo2358.air.validation.ValidationResult;
import io.github.gustavo2358.analysis.adapters.*;
import io.github.gustavo2358.analysis.dependencies.SnapshotProgram;
import io.github.gustavo2358.analysis.dependencies.DependencyAnalysis;
import io.github.gustavo2358.analysis.cfg.application.*;
import io.github.gustavo2358.analysis.cfg.extension.SemanticInterpreterRegistry;
import io.github.gustavo2358.analysis.solver.AnalysisResources;
import java.io.*;
import java.nio.file.*;

/** AIR JSON -> typed/validated snapshot -> shared general dependency analysis/result. */
public final class AnalysisDependencies {
    private AnalysisDependencies(){ }
    public static void main(String[] args){System.exit(run(args,System.err));}
    public static int run(String[] args,PrintStream err){return run(args,err,new DataflowAirReader());}
    static int run(String[] args,PrintStream err,DataflowAirReader reader) {
        try {return run(args,err,reader,AnalysisDeadline.resources(AnalysisDeadline.parse(args,2,false)));}
        catch(IllegalArgumentException failure){return usage(err);}
    }
    /** Package seam for deterministic exhaustion of the same production owner. */
    static int run(String[] args,PrintStream err,DataflowAirReader reader,AnalysisResources resources) {
        try{AnalysisDeadline.parse(args,2,false);}catch(IllegalArgumentException failure){return usage(err);}
        if(args[0].isBlank()||args[1].isBlank())return usage(err);
        Path input,output;try{input=Path.of(args[0]);output=Path.of(args[1]);}catch(InvalidPathException failure){err.println("INVALID_PATH");return 2;}
        try(var admitted=new DependencyInputJson().readSnapshot(input,reader,resources)) {
            var read=admitted.air();
            var validation=read.checked().result();
            if(validation.status()!=ValidationResult.Status.STRUCTURALLY_VALID){err.println("INPUT_VALIDATION: "+validation.status());return validation.status()==ValidationResult.Status.INVALID_IR?3:7;}
            try(var program=new SnapshotProgram(read.checked(),read.newIdentityStorage(),read.newOrderStorage(),resources)) {
                var cfg=new CfgBuildCoordinator(SemanticInterpreterRegistry.empty()).buildChecked(program,read.checked(),BuildOptions.defaults());
                var analysis=new DependencyAnalysis();
                var execution=AnalysisResources.executionFor(program);
                var source=admitted.source(program,execution);
                if(source.isPresent()) {
                    try(var result=analysis.prepareLeased(program,cfg,source.orElseThrow(),admitted.correlations(program,execution),read.borrowedPages(),execution)) {
                        try{new SnapshotDependencyFileWriter().write(result,output,read.borrowedPages(),execution);}catch(IOException|IllegalArgumentException failure){err.println("OUTPUT_FAILURE: "+failure.getMessage());return 6;}
                    }
                }else {
                    try(var result=analysis.prepareManaged(program,cfg,read.borrowedPages(),execution)) {
                        try{new SnapshotDependencyFileWriter().write(result,program,output,execution);}catch(IOException|IllegalArgumentException failure){err.println("OUTPUT_FAILURE: "+failure.getMessage());return 6;}
                    }
                }
            }
            return 0;
        }catch(AnalysisResources.Exhausted failure){err.println((failure.resource()==AnalysisResources.Resource.TIME?"ANALYSIS_TIME_LIMIT: ":"ANALYSIS_RESOURCE_LIMIT: ")+failure.getMessage());return 7;}
        catch(DependencyAnalysis.Failure failure){err.println(failure.getMessage());return switch(failure.kind()){case INVALID_INPUT->3;case INPUT_INCOMPLETE,RESOURCE_LIMIT->7;case CFG_UNSUPPORTED->4;case ANALYSIS_UNSUPPORTED->5;case CONSUMER_FAILURE->8;};}
        catch(AirJsonException failure){err.println("INPUT_CODEC: "+failure.code());return failure.code()==AirJsonException.Code.RESOURCE_LIMIT?7:3;}
        catch(IOException|IllegalArgumentException failure){err.println("DEPENDENCY_INPUT_INVALID: "+failure.getMessage());return 3;}
        catch(RuntimeException failure){err.println("ANALYSIS_EXECUTION_FAILED: "+failure.getClass().getSimpleName());return 5;}
    }
    private static int usage(PrintStream err) {
        err.println("usage: analysis-dependencies <input.air.json> <output.dependencies.json> [--deadline-seconds <positive-integer>]");return 2;
    }
}
