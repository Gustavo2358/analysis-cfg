package io.github.gustavo2358.analysis.solver;

import io.github.gustavo2358.analysis.structure.AnalysisSession;
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
        SolverTopology graph = new SolverTopology(session);
        boolean forward = Objects.requireNonNull(definition.direction(), "direction") == Direction.FORWARD;
        DomainWork work = new DomainWork();
        Run<S> run = new Run<>(graph, definition, schedule.apply(graph), forward, work);
        for (var boundary : definition.boundaries(session)) {
            int p = graph.require(boundary.context(), boundary.node()).ordinal;
            run.boundaryJoins = Math.incrementExact(run.boundaryJoins);
            var joined = definition.joinInto(run.anchor(p), boundary.state(), work);
            if (joined.changed()) run.anchors[p] = joined.state();
        }
        // All program-reachable points must publish once, including bottom in an exitless SCC.
        for (int p = 0; p < graph.points.length; p++) {
            run.initializationAttempts = Math.incrementExact(run.initializationAttempts);
            run.enqueueIfAbsent(p);
        }
        int[] heads = forward ? graph.forwardHead : graph.backwardHead;
        int[] next = forward ? graph.forwardNext : graph.backwardNext;
        int[] destinations = forward ? graph.to : graph.from;
        while (run.queue.size() != 0) {
            int p = run.queue.remove();
            run.pops = Math.incrementExact(run.pops);
            run.queued[p] = false;
            run.transfers = Math.incrementExact(run.transfers);
            S candidate = Objects.requireNonNull(definition.transferBlock(graph.points[p], run.anchor(p), work), "block root");
            boolean first = !run.published[p];
            if (!first && definition.equivalent(run.publication(p, false), candidate, work)) {
                run.unchanged = Math.incrementExact(run.unchanged);
                continue;
            }
            run.publications[p] = candidate;
            run.published[p] = true;
            if (first) run.first = Math.incrementExact(run.first);
            else run.changed = Math.incrementExact(run.changed);
            for (int e = heads[p]; e != -1; e = next[e]) {
                int destination = destinations[e];
                run.edgeTransfers = Math.incrementExact(run.edgeTransfers);
                S contribution = Objects.requireNonNull(definition.transferEdge(graph.points[p], graph.edges[e], candidate, work), "edge root");
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
        return new DataflowResult<>(graph, forward ? run.anchors : run.publications,
                forward ? run.publications : run.anchors, run.snapshot());
    }

    private static final class Run<S> {
        final Object[] anchors, publications;
        final boolean[] published, queued;
        final IntWorklist queue;
        final SolverTopology graph;
        final boolean forward;
        final DomainWork work;
        long boundaryJoins, initializationAttempts, attempts, pushes, pops, duplicates, maxSize;
        long transfers, first, changed, unchanged, edgeTransfers, edgeJoins, accumulatorChanged, accumulatorUnchanged;
        long predecessorReads, successorReads;
        Run(SolverTopology graph, AnalysisDefinition<S> definition, IntWorklist queue, boolean forward, DomainWork work) {
            this.graph = graph; this.queue = Objects.requireNonNull(queue); this.forward = forward; this.work = work;
            int size = graph.points.length;
            anchors = new Object[size]; publications = new Object[size];
            published = new boolean[size]; queued = new boolean[size];
            S bottom = Objects.requireNonNull(definition.bottom(), "bottom");
            Arrays.fill(anchors, bottom); Arrays.fill(publications, bottom);
        }
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
            return new SolverMetrics(graph.points.length, graph.edges.length, boundaryJoins, initializationAttempts,
                    attempts, pushes, pops, duplicates, maxSize, transfers, work.operations(), first, changed,
                    unchanged, edgeTransfers, edgeJoins, accumulatorChanged, accumulatorUnchanged,
                    predecessorReads, successorReads, work.joinEntries(), work.compareEntries());
        }
    }
}
