package io.github.gustavo2358.analysis.query;

import java.util.Objects;

/** Complete logical query within one stable analysis execution. */
public record PointQuery<T>(ProgramPoint point,T subject) {
    public PointQuery { Objects.requireNonNull(point);Objects.requireNonNull(subject); }
}
