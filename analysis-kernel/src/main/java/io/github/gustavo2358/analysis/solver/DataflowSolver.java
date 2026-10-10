package io.github.gustavo2358.analysis.solver;

import io.github.gustavo2358.analysis.structure.AnalysisSession;
import io.github.gustavo2358.analysis.structure.ProgramStore;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.Function;

/**
 * Monotone root propagation over the selected W1 contexts. Fair scheduling and the definition's
 * convergence laws are required. No edge contribution history, iteration cap or partial result.
 */
public final class DataflowSolver {
    private DataflowSolver() { }
    public static <S> DataflowResult<S> solve(AnalysisSession session, AnalysisDefinition<S> definition) {
        return solve(session, definition, graph -> new IntWorklist.Queue(graph.points.length));
    }

    // Test-only alternate scheduling enters here; no public FIFO policy or semantic option.
    static <S> DataflowResult<S> solve(AnalysisSession session, AnalysisDefinition<S> definition,
                                     Function<SolverTopology, IntWorklist> schedule) {
        Objects.requireNonNull(session, "session"); Objects.requireNonNull(definition, "definition");
        session.index().store().progress(ProgramStore.ExecutionPhase.CONTROL);
        var direction=Objects.requireNonNull(definition.direction(), "direction");
        if(session.hasLocalControl())return direction==Direction.FORWARD
            ?new ActivationSolver<>(session,definition).solve():new BackwardActivationSolver<>(session,definition).solve();
        boolean forward = direction == Direction.FORWARD;
        DomainWork work = new DomainWork();
        try(var graph=new SolverTopology(session);
            var run=new Run<S>(graph,definition,schedule,forward,work)) {
        for (var boundary : definition.boundaries(session)) {
            session.index().store().progress(ProgramStore.ExecutionPhase.CONTROL);
            int p = graph.require(boundary.context(), boundary.node()).ordinal;
            run.boundaryJoins = Math.incrementExact(run.boundaryJoins);
            var joined = definition.joinInto(run.anchor(p), boundary.state(), work);
            if (joined.changed()) run.anchors[p] = joined.state();
        }
        // All program-reachable points must publish once, including bottom in an exitless SCC.
        for (int p = 0; p < graph.points.length; p++) {
            session.index().store().progress(ProgramStore.ExecutionPhase.CONTROL);
            run.initializationAttempts = Math.incrementExact(run.initializationAttempts);
            run.enqueueIfAbsent(p);
        }
        while (run.queue.size() != 0) {
            session.index().store().progress(ProgramStore.ExecutionPhase.CONTROL);
            int p = run.queue.remove();
            run.pops = Math.incrementExact(run.pops);
            run.queued[p] = false;
            run.transfers = Math.incrementExact(run.transfers);
            S candidate = Objects.requireNonNull(definition.transferBlock(graph.points[p], run.anchor(p), work), "block root");
            session.index().store().progress(ProgramStore.ExecutionPhase.DOMAIN);
            boolean first = !run.published[p];
            if (!first && definition.equivalent(run.publication(p, false), candidate, work)) {
                run.unchanged = Math.incrementExact(run.unchanged);
                continue;
            }
            run.publications[p] = candidate;
            run.published[p] = true;
            if (first) run.first = Math.incrementExact(run.first);
            else run.changed = Math.incrementExact(run.changed);
            var point=graph.points[p];
            var edges=forward?point.context().successors(point.node()):point.context().predecessors(point.node());
            while(edges.advance()) {
                session.index().store().progress(ProgramStore.ExecutionPhase.CONTROL);
                var target=graph.find(point.context(),forward?edges.target():edges.source());
                if(target==null)continue; // Backward cannot introduce program-unreachable points.
                int destination=target.ordinal;
                if(first)run.contextualEdges=Math.incrementExact(run.contextualEdges);
                run.edgeTransfers = Math.incrementExact(run.edgeTransfers);
                S contribution = Objects.requireNonNull(definition.transferEdge(point, edges.transition(), candidate, work), "edge root");
                run.edgeJoins = Math.incrementExact(run.edgeJoins);
                var joined = definition.joinInto(run.anchor(destination), contribution, work);
                if (joined.changed()) {
                    run.accumulatorChanged = Math.incrementExact(run.accumulatorChanged);
                    run.anchors[destination] = joined.state();
                    run.enqueueIfAbsent(destination);
                } else {
                    run.accumulatorUnchanged = Math.incrementExact(run.accumulatorUnchanged);
                }
            }
        }
        // Ownership transfer only after the worklist is empty. Failures above expose no result.
        session.index().store().progress(ProgramStore.ExecutionPhase.DOMAIN);
        DataflowResult<S> result=new DataflowResult<>(graph,forward?run.anchors:run.publications,forward?run.publications:run.anchors,run.snapshot(),run.statesCapacity);
        graph.transferRetainedCapacity();run.statesCapacity=null;return result;
        }
    }

    private static final class Run<S> implements AutoCloseable {
        final Object[] anchors, publications;
        final boolean[] published, queued;
        final IntWorklist queue;
        final SolverTopology graph;
        final boolean forward;
        final DomainWork work;
        private AnalysisResources.Reservation statesCapacity,workingCapacity;
        long boundaryJoins, initializationAttempts, attempts, pushes, pops, duplicates, maxSize;
        long transfers, first, changed, unchanged, edgeTransfers, edgeJoins, accumulatorChanged, accumulatorUnchanged;
        long predecessorReads, successorReads,contextualEdges;
        Run(SolverTopology graph, AnalysisDefinition<S> definition, Function<SolverTopology,IntWorklist> schedule, boolean forward, DomainWork work) {
            this.graph = graph;this.forward = forward; this.work = work;
            int size = graph.points.length;
            statesCapacity=graph.resources().reserve(AnalysisResources.Pool.RESIDENT,64L+16L*size,AnalysisResources.Phase.DOMAIN);
            try {
            workingCapacity=graph.resources().reserve(AnalysisResources.Pool.SCRATCH,512L+2L*size+4L*Math.max(1,size),AnalysisResources.Phase.CONTROL);
            queue=Objects.requireNonNull(schedule.apply(graph));
            anchors = new Object[size]; publications = new Object[size];
            published = new boolean[size]; queued = new boolean[size];
            S bottom = Objects.requireNonNull(definition.bottom(), "bottom");
            Arrays.fill(anchors, bottom); Arrays.fill(publications, bottom);
            }catch(RuntimeException|Error failure){try{close();}catch(RuntimeException|Error cleanup){if(cleanup!=failure)failure.addSuppressed(cleanup);}throw failure;}
        }
        @Override public void close(){var working=workingCapacity;var states=statesCapacity;workingCapacity=null;statesCapacity=null;AnalysisResources.closeCapacities(working,states);}
        @SuppressWarnings("unchecked") S anchor(int point) { return (S) anchors[point]; }
        /** Reads publication slots, labelling recomposition separately from comparison at the source. */
        @SuppressWarnings("unchecked") S publication(int point, boolean contributionRead) {
            if (contributionRead) {
                if (forward) predecessorReads = Math.incrementExact(predecessorReads);
                else successorReads = Math.incrementExact(successorReads);
            }
            return (S) publications[point];
        }
        void enqueueIfAbsent(int point) {
            attempts = Math.incrementExact(attempts);
            if (queued[point]) { duplicates = Math.incrementExact(duplicates); return; }
            queue.add(point); queued[point] = true;
            pushes = Math.incrementExact(pushes); maxSize = Math.max(maxSize, queue.size());
        }
        SolverMetrics snapshot() {
            return new SolverMetrics(graph.points.length, contextualEdges, boundaryJoins, initializationAttempts,
                    attempts, pushes, pops, duplicates, maxSize, transfers, work.operations(), first, changed,
                    unchanged, edgeTransfers, edgeJoins, accumulatorChanged, accumulatorUnchanged,
                    predecessorReads, successorReads, work.joinEntries(), work.compareEntries());
        }
    }
}
