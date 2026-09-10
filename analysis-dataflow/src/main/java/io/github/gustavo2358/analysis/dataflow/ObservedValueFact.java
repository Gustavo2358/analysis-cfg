package io.github.gustavo2358.analysis.dataflow;

import io.github.gustavo2358.air.model.Ids.LabelId;
import io.github.gustavo2358.air.model.Ids.ObjectId;
import io.github.gustavo2358.analysis.query.PointQuery;
import java.util.Objects;

/** Detached generic fact referring to the complete observation in its named batch. */
public record ObservedValueFact(LabelId sequence, String observationBatchId, PointQuery<ObjectId> query) {
    public ObservedValueFact { Objects.requireNonNull(sequence); Objects.requireNonNull(observationBatchId); Objects.requireNonNull(query); }
}
