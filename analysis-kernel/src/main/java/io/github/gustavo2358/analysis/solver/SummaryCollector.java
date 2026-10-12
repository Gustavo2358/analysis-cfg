package io.github.gustavo2358.analysis.solver;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.*;

/** Graph reachability, not state subsumption. Marks use caller-owned primitive fields;
 * tokens are process-unique, never roots. Queue capacity is leased only during marking.
 * Root/link callbacks cannot mutate the candidate inventory during a pass. */
final class SummaryCollector<N> implements AutoCloseable {
    private static long issued;
    private static synchronized long token(){if(issued==Long.MAX_VALUE)throw new IllegalStateException("summary mark space exhausted");return ++issued;}
    private final AnalysisResources resources;
    private final AnalysisResources.Phase phase;
    private final AnalysisResources.Reservation metadata;
    private final ToLongFunction<? super N> read;
    private final ObjLongConsumer<? super N> write;
    private AnalysisResources.Reservation scratch;
    private Object[] queue;
    private long epoch,visits;
    private int size;
    private boolean closed,marking,ready;
    SummaryCollector(AnalysisResources resources,AnalysisResources.Phase phase,ToLongFunction<? super N> read,ObjLongConsumer<? super N> write) {
        this.resources=Objects.requireNonNull(resources);this.phase=Objects.requireNonNull(phase);
        this.read=Objects.requireNonNull(read);this.write=Objects.requireNonNull(write);
        metadata=resources.reserve(AnalysisResources.Pool.RESIDENT,192,phase);
    }
    private void grow(int length) {
        var staged=resources.reserve(AnalysisResources.Pool.SCRATCH,16L+8L*length,phase);
        try {queue=queue==null?new Object[length]:Arrays.copyOf(queue,length);if(scratch!=null)scratch.close();scratch=staged;}
        catch(RuntimeException|Error failure){staged.close();throw failure;}
    }
    private void enqueue(N node) {
        Objects.requireNonNull(node);visits++;resources.work(1,phase);
        if(read.applyAsLong(node)==epoch)return;
        if(size==queue.length)grow(Math.multiplyExact(queue.length,2));
        write.accept(node,epoch);queue[size++]=node;
    }
    @SuppressWarnings("unchecked")
    long mark(Consumer<Consumer<N>> roots,BiConsumer<N,Consumer<N>> links) {
        open();if(marking)throw new IllegalStateException("nested summary collection");marking=true;ready=false;visits=0;size=0;
        try {
            epoch=token();
            grow(8);Consumer<N> visit=this::enqueue;roots.accept(visit);
            for(int head=0;head<size;head++)links.accept((N)queue[head],visit);
            ready=true;return size;
        }finally{queue=null;size=0;if(scratch!=null){scratch.close();scratch=null;}marking=false;}
    }
    long retain(List<N> nodes,Consumer<N> retire) {
        open();if(!ready||marking)throw new IllegalStateException("complete mark pass required");
        int before=nodes.size(),target=0;
        for(int i=0;i<before;i++){var node=nodes.get(i);if(isMarked(node))nodes.set(target++,node);else retire.accept(node);}
        nodes.subList(target,before).clear();return before-target;
    }
    long collect(List<N> nodes,Consumer<Consumer<N>> roots,BiConsumer<N,Consumer<N>> links,Consumer<N> retire){mark(roots,links);return retain(nodes,retire);}
    boolean isMarked(N node){open();if(!ready)throw new IllegalStateException("complete mark pass required");return read.applyAsLong(node)==epoch;}
    long visits(){open();return visits;}
    private void open(){if(closed)throw new IllegalStateException("summary collector closed");}
    @Override public void close(){if(closed)return;closed=true;queue=null;if(scratch!=null)scratch.close();metadata.close();}
}
