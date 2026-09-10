package io.github.gustavo2358.analysis.plan;

import io.github.gustavo2358.analysis.query.PointQuery;
import java.util.*;

/** Requests are immutable planning input. Duplicate occurrences remain countable until the planner unions them. */
public record ObservationRequest<T,V>(ObservationBatchId<T,V> batch, List<PointQuery<T>> queries) {
    public ObservationRequest { Objects.requireNonNull(batch); queries = List.copyOf(queries); }
}
