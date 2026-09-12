package io.github.gustavo2358.analysis.launcher;

import io.github.gustavo2358.air.json.AirJsonException;
import io.github.gustavo2358.analysis.adapters.*;
import io.github.gustavo2358.analysis.dependencies.*;
import java.io.*;
import java.nio.file.*;

/** Separate dependency CLI. Failure never publishes a partial destination. */
public final class AnalysisDependencies {
    private AnalysisDependencies(){ }
    public static void main(String[] args){System.exit(run(args,System.err));}
    public static int run(String[] args,PrintStream err) {
        return run(args,err,new DataflowAirReader());
    }
    static int run(String[] args,PrintStream err,DataflowAirReader reader) {
        if(args.length!=2||args[0].isBlank()||args[1].isBlank()){err.println("usage: analysis-dependencies <input.air.json> <output.dependencies.json>");return 2;}
        Path input,output;
        try{input=Path.of(args[0]);output=Path.of(args[1]);}catch(InvalidPathException failure){err.println("INVALID_PATH");return 2;}
        DataflowAirReader.Read read;
        try{read=reader.read(input);}
        catch(AirJsonException failure){err.println("INPUT_CODEC: "+failure.code());return failure.code()==AirJsonException.Code.RESOURCE_LIMIT?7:3;}
        catch(IOException failure){err.println("INPUT_IO");return 3;}
        DependencyResult result;
        try{result=new DependencyAnalysis().prepare(read.publication());}
        catch(DependencyAnalysis.Failure failure){err.println(failure.getMessage());return switch(failure.kind()){case INVALID_INPUT,INPUT_INCOMPLETE->3;case CFG_UNSUPPORTED->4;case ANALYSIS_UNSUPPORTED->5;case RESOURCE_LIMIT->7;case CONSUMER_FAILURE->8;};}
        try{new DependencyFileWriter().write(result,output);}
        catch(IOException|IllegalArgumentException failure){err.println("OUTPUT_FAILURE");return 6;}
        return 0;
    }
}
