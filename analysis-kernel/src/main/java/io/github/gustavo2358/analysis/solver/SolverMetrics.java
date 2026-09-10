package io.github.gustavo2358.analysis.solver;

/** Immutable observations of one completed run, with checked counters and no capacity policy. */
public record SolverMetrics(long analysisPoints, long contextualEdges, long boundaryJoins,
        long initializationAttempts, long worklistAttempts, long worklistPushes, long nodesPopped,
        long duplicatePushesSuppressed, long maxWorklistSize, long nodesTransferred,
        long operationsTransferred, long firstPublications, long publishedStatesChanged,
        long publishedStatesUnchanged, long edgeTransferInvocations, long edgeContributionJoins,
        long accumulatorStatesChanged, long accumulatorStatesUnchanged,
        long predecessorContributionReads, long successorContributionReads,
        long joinEntriesVisited, long stateCompareEntries) { }
