package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.analysis.dataflow.AnalysisDataflow;
import io.github.gustavo2358.air.model.Publication;
import java.io.*;
import java.nio.file.*;

/** External diagnostic process; phase barriers allow full-GC histograms without product hooks. */
public final class DataflowRetentionProbe {
    private static Object held;
    private DataflowRetentionProbe() { }
    private static void phase(String phase)throws IOException {System.out.println("W5_PHASE "+phase);System.out.flush();if(System.in.read()<0)throw new EOFException("controller absent");}
    private static void prepare(){held=new AnalysisDataflow().prepare((Publication)held,"retention");}
    private static void deliver(Path path) {
        var result=(io.github.gustavo2358.analysis.dataflow.PreparedDataflowResult)held;var attempt=new LocalResultWriter().write(result,path);
        if(attempt.receipt().status()!=DeliveryReceipt.Status.COMPLETE)throw new IllegalStateException("delivery failed");
        held=new Object[]{result,attempt};
    }
    public static void main(String[] args)throws Exception {
        held=ResultFixtures.linear(1,Integer.parseInt(args[0]),Integer.parseInt(args[0]),1);phase("air");
        prepare();phase("prepared");deliver(Path.of(args[1]));phase("delivered");held=null;phase("released");
    }
}
