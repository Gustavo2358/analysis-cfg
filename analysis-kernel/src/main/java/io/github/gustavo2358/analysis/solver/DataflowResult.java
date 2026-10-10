package io.github.gustavo2358.analysis.solver;

import io.github.gustavo2358.analysis.structure.ContextView;
import io.github.gustavo2358.analysis.structure.ProgramIndex;
import io.github.gustavo2358.analysis.structure.ProgramStore;
import java.util.*;
import java.util.function.Consumer;
import java.lang.ref.WeakReference;

/** Constructed only after convergence. Roots obey the definition's isolation contract. */
public final class DataflowResult<S> implements AutoCloseable {
    private final IdentityHashMap<ContextView, IdentityHashMap<ProgramIndex.Node, List<AnalysisPoint>>> lookup;
    private final Object[] in, out;
    private final SolverMetrics metrics;
    private ProgramStore program;
    private AnalysisResources.Reservation retainedCapacity,statesCapacity;
    private boolean closed;
    DataflowResult(SolverTopology topology, Object[] in, Object[] out, SolverMetrics metrics) {
        this(topology,in,out,metrics,null);topology.transferRetainedCapacity();
    }
    DataflowResult(SolverTopology topology,Object[] in,Object[] out,SolverMetrics metrics,AnalysisResources.Reservation statesCapacity) {
        lookup=topology.lookup;this.in=in;this.out=out;this.metrics=metrics;
        program=topology.program.executionBudget().isPresent()?topology.program:null;
        retainedCapacity=topology.retainedCapacity();this.statesCapacity=statesCapacity;
        registerExpiry(this,statesCapacity,retainedCapacity);
    }
    private DataflowResult(IdentityHashMap<ContextView,IdentityHashMap<ProgramIndex.Node,List<AnalysisPoint>>> lookup,Object[] in,Object[] out,SolverMetrics metrics,
            ProgramStore owner,AnalysisResources.Reservation retained,AnalysisResources.Reservation states) {
        this.lookup=lookup;this.in=in;this.out=out;this.metrics=metrics;
        program=owner.executionBudget().isPresent()?owner:null;retainedCapacity=retained;statesCapacity=states;
        registerExpiry(this,states,retained);
    }
    private static void registerExpiry(DataflowResult<?> result,AnalysisResources.Reservation states,AnalysisResources.Reservation retained) {
        var observer=new WeakReference<>(result);
        var lease=states!=null?states:retained;
        if(lease!=null)lease.onOwnerClose(()->{
            var live=observer.get();
            if(live!=null)live.close();else AnalysisResources.closeCapacities(states,retained);
        });
        // The owner observes expiry without pinning semantic roots. No capacity is
        // reclaimed by GC: both leases stay charged until explicit/owner cleanup.
    }
    /** Fund publication separately from solve history. Feasible contextual roots stay separate. */
    static final class Builder<S> implements AutoCloseable {
        private final ProgramStore program;
        private final AnalysisResources resources;
        private IdentityHashMap<ContextView,IdentityHashMap<ProgramIndex.Node,List<AnalysisPoint>>> lookup;
        private List<S> ins,outs;
        private AnalysisResources.Reservation retained,working,states;
        Builder(ProgramStore program,AnalysisResources resources) {
            this.program=Objects.requireNonNull(program);this.resources=Objects.requireNonNull(resources);
            retained=resources.reserve(AnalysisResources.Pool.RESIDENT,256,AnalysisResources.Phase.DOMAIN);
            try {
                working=resources.reserve(AnalysisResources.Pool.SCRATCH,256,AnalysisResources.Phase.DOMAIN);
                lookup=new IdentityHashMap<>();ins=new ArrayList<>();outs=new ArrayList<>();
            }catch(RuntimeException|Error failure){cleanup(failure);throw failure;}
        }
        void context(ContextView context) {
            if(lookup.containsKey(context))return;
            retained.grow(256,AnalysisResources.Phase.DOMAIN);
            lookup.put(context,new IdentityHashMap<>());
        }
        void add(ContextView context,ProgramIndex.Node node,S in,S out) {
            Objects.requireNonNull(in);Objects.requireNonNull(out);int ordinal=ins.size();Math.incrementExact(ordinal);
            context(context);retained.grow(512,AnalysisResources.Phase.DOMAIN);working.grow(64,AnalysisResources.Phase.DOMAIN);
            lookup.get(context).computeIfAbsent(node,ignored->new ArrayList<>()).add(new AnalysisPoint(ordinal,context,node));
            ins.add(in);outs.add(out);
        }
        int size(){return ins.size();}
        DataflowResult<S> finish(SolverMetrics metrics) {
            states=resources.reserve(AnalysisResources.Pool.RESIDENT,64L+16L*size(),AnalysisResources.Phase.DOMAIN);
            var result=new DataflowResult<S>(lookup,ins.toArray(),outs.toArray(),metrics,program,retained,states);
            lookup=null;retained=null;states=null;return result;
        }
        private void cleanup(Throwable failure){try{close();}catch(RuntimeException|Error error){if(error!=failure)failure.addSuppressed(error);}}
        @Override public void close() {
            if(lookup!=null){lookup.clear();lookup=null;}if(ins!=null)ins.clear();if(outs!=null)outs.clear();
            var scratch=working;var live=retained;var arrays=states;working=null;retained=null;states=null;
            Throwable failure=null;
            try{AnalysisResources.closeCapacities(scratch,live);}catch(RuntimeException|Error error){failure=error;}
            try{if(arrays!=null)arrays.close();}catch(RuntimeException|Error error){if(failure==null)failure=error;else if(failure!=error)failure.addSuppressed(error);}
            if(failure instanceof RuntimeException error)throw error;if(failure instanceof Error error)throw error;
        }
    }
    public enum Status { STABLE }
    public Status status() { return Status.STABLE; }
    public SolverMetrics metrics() { return metrics; }
    /** False for an unselected context, foreign node or structurally unreachable point. */
    public boolean contains(ContextView context, ProgramIndex.Node node) { open();var nodes=lookup.get(context); return nodes!=null&&nodes.containsKey(node); }
    @SuppressWarnings("unchecked")
    public S in(ContextView context, ProgramIndex.Node node) { return (S) in[require(context, node).ordinal]; }
    @SuppressWarnings("unchecked")
    public S out(ContextView context, ProgramIndex.Node node) { return (S) out[require(context, node).ordinal]; }
    /** Separate roots; joining before replay could invent cross-context expression values. */
    @SuppressWarnings("unchecked")
    public List<S> states(ContextView context,ProgramIndex.Node node,boolean atIn) {
        open();
        var nodes=lookup.get(context);var points=nodes==null?null:nodes.get(node);
        if(points==null)return List.of();
        var roots=new ArrayList<S>(points.size());
        for(var point:points)roots.add((S)(atIn?in:out)[point.ordinal]);
        return List.copyOf(roots);
    }
    /**
     * Visits every root retained by the stable result. Domains with their own
     * canonical stores use this boundary to retire superseded solve history
     * without guessing which roots a later replay can still observe.
     */
    @SuppressWarnings("unchecked")
    public void forEachRetainedState(Consumer<? super S> consumer) {
        open();
        Objects.requireNonNull(consumer,"consumer");
        var seen=Collections.newSetFromMap(new IdentityHashMap<Object,Boolean>());
        for(var states:List.of(in,out))for(var state:states)if(seen.add(state))consumer.accept((S)state);
    }
    private AnalysisPoint find(ContextView context, ProgramIndex.Node node) {
        var nodes = lookup.get(context);var points=nodes==null?null:nodes.get(node);
        if(points==null)return null;
        if(points.size()!=1)throw new IllegalArgumentException("multiple local contexts: replay each root before joining");
        return points.getFirst();
    }
    private AnalysisPoint require(ContextView context, ProgramIndex.Node node) {
        open();
        var p = find(context, node);
        if (p == null) throw new IllegalArgumentException("point is outside the selected reachable contexts");
        return p;
    }
    private void open(){if(closed)throw new IllegalStateException("dataflow result is closed");if(program!=null)program.progress(ProgramStore.ExecutionPhase.DOMAIN);}
    /** Stable status/metrics are detached history; state roots expire with this lease. */
    @Override public void close(){if(closed)return;closed=true;Arrays.fill(in,null);Arrays.fill(out,null);lookup.clear();
        var states=statesCapacity;var retained=retainedCapacity;statesCapacity=null;retainedCapacity=null;program=null;
        AnalysisResources.closeCapacities(states,retained);}
}
