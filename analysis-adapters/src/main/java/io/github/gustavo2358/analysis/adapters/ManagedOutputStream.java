package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.analysis.solver.AnalysisResources;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Objects;

/** One logical-byte meter shared by managed codecs and their file adapter.
 * Codecs borrow this stream; only the adapter owning the underlying stream closes it. */
final class ManagedOutputStream extends FilterOutputStream {
    private final AnalysisResources resources;

    private ManagedOutputStream(OutputStream output,AnalysisResources resources) {
        super(output);this.resources=resources;
    }

    static OutputStream metered(OutputStream output,AnalysisResources resources) {
        Objects.requireNonNull(output);Objects.requireNonNull(resources);
        if(output instanceof ManagedOutputStream metered) {
            if(metered.resources!=resources)throw new IllegalArgumentException("foreign managed output resources");
            return metered;
        }
        return new ManagedOutputStream(output,resources);
    }

    @Override public void write(int value)throws IOException {
        resources.output(1,AnalysisResources.Phase.ENCODE);out.write(value);
    }

    @Override public void write(byte[] values,int offset,int length)throws IOException {
        Objects.checkFromIndexSize(offset,length,values.length);
        resources.output(length,AnalysisResources.Phase.ENCODE);out.write(values,offset,length);
    }
}
