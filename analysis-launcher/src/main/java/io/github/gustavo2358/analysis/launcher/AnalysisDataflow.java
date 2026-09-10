package io.github.gustavo2358.analysis.launcher;

import io.github.gustavo2358.air.json.AirJsonException;
import io.github.gustavo2358.analysis.adapters.*;
import io.github.gustavo2358.analysis.dataflow.PreparedDataflowResult;
import io.github.gustavo2358.analysis.application.PreparedAnalysisResult.PreparationStatus;
import java.io.*;
import java.nio.file.*;

/** Separate AIR → dataflow launcher. stdout is exactly one external receipt on delivery attempts. */
public final class AnalysisDataflow {
    private AnalysisDataflow() { }
    public static void main(String[] args) { System.exit(run(args,System.out,System.err)); }
    public static int run(String[] args,PrintStream out,PrintStream err) {
        return run(args,out,err,new DataflowAirReader());
    }
    static int run(String[] args,PrintStream out,PrintStream err,DataflowAirReader reader) {
        if(args.length!=4||!args[2].equals("--result-id")||args[0].isBlank()||args[1].isBlank()||args[3].isBlank()) {
            err.println("usage: analysis-dataflow <air.json> <result.json> --result-id <stable-id>");return 2;
        }
        Path input,output;
        try{input=Path.of(args[0]);output=Path.of(args[1]);}catch(InvalidPathException failure){err.println("USAGE: invalid path");return 2;}
        DataflowAirReader.Read read;
        try {read=reader.read(input);}
        catch(AirJsonException failure) {
            int code=switch(failure.code()){case INPUT_ERROR,VERSION_MISMATCH->3;case INVALID_IR,UNSUPPORTED_CAPABILITY->4;case IMPLEMENTATION_LIMIT,INCOMPLETE_VALIDATION,RESOURCE_LIMIT->7;};
            String category=switch(failure.code()){case INPUT_ERROR->"INPUT_TRANSPORT";case VERSION_MISMATCH->"INPUT_VERSION";case INVALID_IR->"INVALID_INPUT";case UNSUPPORTED_CAPABILITY->"UNSUPPORTED_PROFILE";case IMPLEMENTATION_LIMIT->"EXTERNAL SIZE-CAP DEBT";case INCOMPLETE_VALIDATION->"UPSTREAM_VALIDATION_INCOMPLETE";case RESOURCE_LIMIT->"EXTERNAL_RESOURCE_LIMIT";};
            err.println(category+": "+line(failure.path()));return code;
        }catch(IOException failure){err.println("INPUT_IO: "+line(failure.getMessage()));return 3;}
        PreparedDataflowResult result;
        try {result=new io.github.gustavo2358.analysis.dataflow.AnalysisDataflow().prepare(read.publication(),args[3]);}
        catch(io.github.gustavo2358.analysis.dataflow.AnalysisDataflow.PreparationException failure) {
            err.println(line(failure.getMessage()));return switch(failure.failure()){case INVALID_INPUT,UNSUPPORTED_PROFILE->4;case EXTERNAL_SIZE_CAP_DEBT,EXTERNAL_RESOURCE_LIMIT,INCOMPLETE_VALIDATION->7;};
        }catch(RuntimeException failure){err.println("ANALYSIS_EXECUTION_FAILED: "+failure.getClass().getSimpleName());return 5;}
        var attempt=new LocalResultWriter().write(result,output);
        try {new ReceiptJson().write(attempt.receipt(),out);if(out.checkError())throw new IOException("receipt stdout failed");}
        catch(IOException failure){err.println("RECEIPT_OUTPUT_FAILED");return 6;}
        if(attempt.receipt().status()!=DeliveryReceipt.Status.COMPLETE){err.println("DELIVERY_FAILED");return 6;}
        if(result.result().preparationStatus()!=PreparationStatus.COMPLETE){err.println("PREPARATION_INCOMPLETE");return 5;}
        return 0;
    }
    private static String line(String text){return text==null?"":text.replaceAll("[\\p{Cntrl}]"," ");}
}
