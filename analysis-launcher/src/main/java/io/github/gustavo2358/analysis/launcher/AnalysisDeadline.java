package io.github.gustavo2358.analysis.launcher;

import io.github.gustavo2358.analysis.solver.AnalysisResources;
import java.time.Duration;

/** Outer operational configuration; never a semantic cutoff or a capacity increase. */
final class AnalysisDeadline {
    private AnalysisDeadline() { }
    static Duration parse(String[] arguments,int positional,boolean pipeline) {
        if(arguments.length<positional)throw new IllegalArgumentException("missing paths");
        Duration deadline=Duration.ofMinutes(8);boolean configured=false,source=false,physical=false;
        for(int n=positional;n<arguments.length;n++) {
            if(arguments[n].equals("--deadline-seconds")&&!configured&&n+1<arguments.length) {
                deadline=seconds(arguments[++n]);configured=true;
            } else if(pipeline&&arguments[n].equals("--source-evidence")&&!source&&n+1<arguments.length) {
                n++;source=true; // Its value is a path, even when it resembles an option.
            } else if(pipeline&&arguments[n].equals("--experimental-physical")&&!physical) {
                physical=true;
            } else throw new IllegalArgumentException("invalid option");
        }
        return deadline;
    }
    private static Duration seconds(String text) {
        if(text.isEmpty())throw new IllegalArgumentException("empty deadline");
        long value=0,maximum=Long.MAX_VALUE/1_000_000_000L;
        for(int n=0;n<text.length();n++) {
            char digit=text.charAt(n);
            if(digit<'0'||digit>'9'||value>(maximum-(digit-'0'))/10)
                throw new IllegalArgumentException("deadline must be positive integral seconds within the monotonic clock range");
            value=value*10+(digit-'0');
        }
        if(value==0)throw new IllegalArgumentException("zero deadline");
        return Duration.ofSeconds(value);
    }
    static AnalysisResources resources(Duration deadline) {
        return AnalysisResources.withDeadline(new AnalysisResources.Limits(
                64L*1024*1024,16L*1024*1024,0,16L*1024*1024*1024,8,Long.MAX_VALUE,4L*1024*1024*1024),deadline);
    }
}
