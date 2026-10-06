package io.github.gustavo2358.analysis.launcher;

import io.github.gustavo2358.air.json.AirJsonException;
import io.github.gustavo2358.analysis.adapters.*;
import io.github.gustavo2358.analysis.dependencies.*;
import io.github.gustavo2358.analysis.dependencies.source.QualifiedSourceDependencies;
import io.github.gustavo2358.analysis.cfg.adapters.CfgJsonWriter;
import io.github.gustavo2358.analysis.cfg.adapters.CfgJsonException;
import io.github.gustavo2358.analysis.cfg.application.*;
import io.github.gustavo2358.analysis.cfg.extension.SemanticInterpreterRegistry;
import io.github.gustavo2358.analysis.values.StorageAnalysisMode;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Outer file composition: one strict AIR admission, distinct complete products. */
public final class AnalysisPipeline {
    private AnalysisPipeline() { }
    public static void main(String[] args) { System.exit(run(args,System.err)); }
    public static int run(String[] args,PrintStream err) {
        return run(args,err,new DataflowAirReader()::read);
    }
    @FunctionalInterface interface AirRead { DataflowAirReader.Read read(Path path) throws IOException; }
    static int run(String[] args,PrintStream err,AirRead reader) {
        if(args.length<3)return usage(err);
        Path input,cfg,dependencies,source=null;boolean physical=false;
        try {
            input=Path.of(args[0]);cfg=Path.of(args[1]);dependencies=Path.of(args[2]);
            if(args[0].isBlank()||args[1].isBlank()||args[2].isBlank())return usage(err);
            for(int n=3;n<args.length;n++) {
                if(args[n].equals("--source-evidence")&&source==null&&n+1<args.length)source=Path.of(args[++n]);
                else if(args[n].equals("--experimental-physical")&&!physical)physical=true;
                else return usage(err);
            }
            var paths=new ArrayList<>(List.of(input,cfg,dependencies));if(source!=null)paths.add(source);
            for(int a=0;a<paths.size();a++)for(int b=0;b<a;b++)
                if(identity(paths.get(a)).equals(identity(paths.get(b)))
                    || (Files.exists(paths.get(a))&&Files.exists(paths.get(b))&&Files.isSameFile(paths.get(a),paths.get(b))))return usage(err);
        } catch(InvalidPathException failure){return usage(err);}
          catch(IOException failure){err.println("PATH_IO");return 3;}
        DependencyInput admitted;
        try {
            var read=reader.read(input);
            var evidence=Optional.<QualifiedSourceDependencies>empty();
            if(source!=null) {
                QualifiedSourceDependencies value;
                try(var stream=JsonFiles.input(source)){value=new QualifiedSourceJson().decode(stream);}
                if(value.air().size()!=1||!value.air().getFirst().sha256().equals(read.sha256()))
                    throw new IllegalArgumentException("AIR digest mismatch");
                evidence=Optional.of(value);
            }
            if(read.checked().isEmpty())throw new IllegalArgumentException("checked publication required");
            admitted=new DependencyInput(read.publication(),evidence,List.of(),read.checked());
        } catch(AirJsonException failure){err.println("INPUT_CODEC: "+failure.code());return failure.code()==AirJsonException.Code.RESOURCE_LIMIT?7:3;}
          catch(IOException|IllegalArgumentException failure){err.println("PIPELINE_INPUT_INVALID");return 3;}
        // The export result has no owner in the dependency phase after this call returns.
        int exported=export(admitted,cfg,err);if(exported!=0)return exported;
        DependencyResult result;
        try {result=new DependencyAnalysis(physical?StorageAnalysisMode.EXPERIMENTAL_PHYSICAL:StorageAnalysisMode.LOGICAL_ONLY).prepare(admitted);}
        catch(DependencyAnalysis.Failure failure){err.println(failure.getMessage());return switch(failure.kind()){case INVALID_INPUT,INPUT_INCOMPLETE->3;case CFG_UNSUPPORTED->4;case ANALYSIS_UNSUPPORTED->5;case RESOURCE_LIMIT->7;case CONSUMER_FAILURE->8;};}
        try {new DependencyFileWriter().write(result,dependencies);}
        catch(IOException|IllegalArgumentException failure){err.println("DEPENDENCY_OUTPUT_FAILURE");return 6;}
        return 0;
    }
    private static int export(DependencyInput input,Path output,PrintStream err) {
        var result=new CfgBuildCoordinator(SemanticInterpreterRegistry.empty()).buildChecked(input.checked().orElseThrow(),BuildOptions.defaults());
        if(result.status()!=CfgBuildResult.Status.CFG_BUILT){err.println("CFG "+result.status());return 4;}
        try {new CfgJsonWriter().write(result,output);}
        catch(CfgJsonException failure){err.println("CFG_OUTPUT_SERIALIZATION");return 5;}
        catch(IOException failure){err.println("CFG_OUTPUT_IO");return 6;}
        return 0;
    }
    private static Path identity(Path path) throws IOException {
        var absolute=path.toAbsolutePath().normalize();
        if(Files.exists(absolute))return absolute.toRealPath();
        var parent=absolute.getParent();
        return parent!=null&&Files.exists(parent)?parent.toRealPath().resolve(absolute.getFileName()):absolute;
    }
    private static int usage(PrintStream err) {
        err.println("usage: analysis-pipeline <input.air.json> <output.cfg.json> <output.dependencies.json> [--source-evidence <source.json>] [--experimental-physical]");return 2;
    }
}
