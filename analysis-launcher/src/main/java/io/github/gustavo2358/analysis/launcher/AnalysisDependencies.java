package io.github.gustavo2358.analysis.launcher;

import io.github.gustavo2358.air.json.AirJsonException;
import io.github.gustavo2358.analysis.adapters.*;
import io.github.gustavo2358.analysis.dependencies.*;
import java.io.*;
import io.github.gustavo2358.analysis.values.StorageAnalysisMode;
import java.nio.file.*;

/** Separate dependency CLI. Failure never publishes a partial destination. */
public final class AnalysisDependencies {
    private AnalysisDependencies(){ }
    public static void main(String[] args){System.exit(run(args,System.err));}
    public static int run(String[] args,PrintStream err) {
        return run(args,err,DataflowAirReader.forPartialAnalysis());
    }
    static int run(String[] args,PrintStream err,DataflowAirReader reader) {
        if(args.length<2||args[0].isBlank()||args[1].isBlank()){err.println("usage: analysis-dependencies <input.air.json> <output.dependencies.json> [--experimental-physical] [--source-evidence <source.json>]");return 2;}
        Path input,output,source=null;boolean physical=false;
        try{
            input=Path.of(args[0]);output=Path.of(args[1]);
            for(int n=2;n<args.length;n++) {
                if(args[n].equals("--experimental-physical")&&!physical)physical=true;
                else if(args[n].equals("--source-evidence")&&source==null&&n+1<args.length)source=Path.of(args[++n]);
                else {err.println("INVALID_OPTIONS");return 2;}
            }
        }catch(InvalidPathException failure){err.println("INVALID_PATH");return 2;}
        DataflowAirReader.Read read;
        try{read=reader.read(input);}
        catch(AirJsonException failure){err.println("INPUT_CODEC: "+failure.code());return failure.code()==AirJsonException.Code.RESOURCE_LIMIT?7:3;}
        catch(IOException failure){err.println("INPUT_IO");return 3;}
        DependencyResult result;
        try{result=new DependencyAnalysis(physical?StorageAnalysisMode.EXPERIMENTAL_PHYSICAL:StorageAnalysisMode.LOGICAL_ONLY).prepare(read.publication());}
        catch(DependencyAnalysis.Failure failure){err.println(failure.getMessage());return switch(failure.kind()){case INVALID_INPUT,INPUT_INCOMPLETE->3;case CFG_UNSUPPORTED->4;case ANALYSIS_UNSUPPORTED->5;case RESOURCE_LIMIT->7;case CONSUMER_FAILURE->8;};}
        if(source!=null)try {
            var evidence=new QualifiedSourceJson().decode(Files.readAllBytes(source));
            if(evidence.air().size()!=1 || !evidence.air().getFirst().sha256().equals(read.sha256()))throw new IllegalArgumentException("AIR digest mismatch");
            result=result.withSourceEvidence(evidence);
        } catch(IOException|IllegalArgumentException failure){err.println("SOURCE_EVIDENCE_INVALID: "+failure.getMessage());return 3;}
        try{new DependencyFileWriter().write(result,output);}
        catch(IOException|IllegalArgumentException failure){err.println("OUTPUT_FAILURE");return 6;}
        return 0;
    }
}
