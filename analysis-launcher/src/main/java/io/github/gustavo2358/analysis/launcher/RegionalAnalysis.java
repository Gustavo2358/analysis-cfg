package io.github.gustavo2358.analysis.launcher;

import io.github.gustavo2358.air.json.AirJsonException;
import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.analysis.adapters.*;
import io.github.gustavo2358.analysis.dataflow.RegionalAnalysisResult;
import io.github.gustavo2358.analysis.query.*;
import io.github.gustavo2358.analysis.storage.*;
import java.io.*;
import io.github.gustavo2358.analysis.values.StorageAnalysisMode;
import java.math.BigInteger;
import java.nio.file.*;
import java.util.*;

/** Explicit file query; input admission and atomic output do not change analysis semantics. */
public final class RegionalAnalysis {
    private RegionalAnalysis() { }
    public static void main(String[] args){System.exit(run(args,System.err));}
    public static int run(String[] args,PrintStream err) {
        boolean physical=(args.length==13||args.length==16)&&args[args.length-1].equals("--experimental-physical");
        if(physical)args=Arrays.copyOf(args,args.length-1);
        Path input,destination;StorageRange range=null;Memory.Codec codec=null;
        try {
            if((args.length!=12&&args.length!=15)||!args[2].equals("--result-id")||!args[4].equals("--unit")||!args[6].equals("--entry"))throw new IllegalArgumentException();
            for(var arg:args)if(arg.isBlank())throw new IllegalArgumentException();
            if(!Set.of("--before","--after","--outcome-normal","--at-entry").contains(args[8])||args[8].equals("--at-entry")&&!args[9].equals("-"))throw new IllegalArgumentException();
            if(args.length==12&&!args[10].equals("--object")||args.length==15&&!args[10].equals("--range"))throw new IllegalArgumentException();
            if(args.length==15) {
                var start=decimal(args[12]);var end=args[13].equals("open")?Optional.<BigInteger>empty():Optional.of(decimal(args[13]));range=new StorageRange(start,end);
                codec=switch(args[14]){case "ascii"->Memory.AsciiText.INSTANCE;case "ibm1047"->new Memory.ExtensionCodec("text.ebcdic.ibm1047","1",Types.known(Types.Builtin.TEXT));case "identity"->Memory.IdentityBytes.INSTANCE;default->throw new IllegalArgumentException();};
            }
            input=Path.of(args[0]).toAbsolutePath().normalize();destination=Path.of(args[1]).toAbsolutePath().normalize();if(input.equals(destination))throw new IllegalArgumentException();
        } catch(IllegalArgumentException failure) {
            err.println("usage: regional-analysis <air.json> <result.json> --result-id <id> --unit <unit> --entry <entry> <--before|--after|--outcome-normal|--at-entry> <operation|-> <--object <object>|--range <storage> <start> <end|open> <ascii|ibm1047|identity>> [--experimental-physical]");return 2;
        }
        Publication publication;
        try {publication=DataflowAirReader.forPartialAnalysis().read(input).publication();}
        catch(AirJsonException failure){err.println("AIR_INPUT: "+failure.code());return switch(failure.code()){case INPUT_ERROR,VERSION_MISMATCH->3;case INVALID_IR,UNSUPPORTED_CAPABILITY->4;case IMPLEMENTATION_LIMIT,INCOMPLETE_VALIDATION,RESOURCE_LIMIT->7;};}
        catch(IOException failure){err.println("INPUT_IO");return 3;}
        RegionalAnalysisResult result;
        try {
            var unit=new UnitId(publication.id(),args[5]);var entry=new EntryId(unit,args[7]);var operation=new OperationId(unit,args[9]);
            var point=switch(args[8]){case "--before"->ProgramPoint.before(entry,operation);case "--after"->ProgramPoint.after(entry,operation);case "--outcome-normal"->new ProgramPoint(entry,ProgramPoint.Kind.OUTCOME,operation,Control.NormalOutcome.INSTANCE);default->ProgramPoint.entry(entry);};
            StorageSubject subject=args.length==12?new StorageSubject.NamedObject(new ObjectId(unit,args[11])):new StorageSubject.PhysicalRange(new StorageId(publication.id(),args[11]),range,codec);
            result=new io.github.gustavo2358.analysis.dataflow.RegionalAnalysis(physical?StorageAnalysisMode.EXPERIMENTAL_PHYSICAL:StorageAnalysisMode.LOGICAL_ONLY).preparePartial(publication,args[3],List.of(new PointQuery<>(point,subject)));
        } catch(io.github.gustavo2358.analysis.dataflow.AnalysisDataflow.PreparationException failure) {
            err.println("PREPARATION: "+failure.failure());return switch(failure.failure()){case INVALID_INPUT,UNSUPPORTED_PROFILE->4;case EXTERNAL_SIZE_CAP_DEBT,EXTERNAL_RESOURCE_LIMIT,INCOMPLETE_VALIDATION->7;};
        } catch(RuntimeException failure){err.println("ANALYSIS_EXECUTION_FAILED: "+failure.getClass().getSimpleName());return 5;}
        Path temporary=null;
        try {
            temporary=Files.createTempFile(destination.getParent(),".regional-result-",".tmp");
            try(var out=Files.newOutputStream(temporary)){new RegionalResultJson().write(result,out);}
            Files.move(temporary,destination,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);temporary=null;return 0;
        } catch(IOException|IllegalArgumentException|IllegalStateException failure){err.println("RESULT_OUTPUT_FAILED");return 6;}
        finally {if(temporary!=null)try{Files.deleteIfExists(temporary);}catch(IOException failure){err.println("TEMPORARY_CLEANUP_FAILED");}}
    }
    private static BigInteger decimal(String value){if(!value.matches("0|[1-9][0-9]*"))throw new IllegalArgumentException();return new BigInteger(value);}
}
