package io.github.gustavo2358.analysis.solver;

/** Borrowed exact acyclic AND/complement view. Zero/one are constants; other
 * nonnegative roots encode a positive node handle shifted once and complement bit.
 * Normalization resolves exact aliases, never an approximation. A primary key has
 * the same Boolean meaning at every occurrence even if node handles differ.
 * The view must retain every queried node throughout the decision operation;
 * explicit decision scopes extend that lifetime and forbid handle reuse. */
interface BooleanCircuitView {
    long normalize(long root);
    /** Nonnegative primary key, or -1 for a two-input AND definition. */
    int primary(long handle);
    long left(long handle);
    long right(long handle);
}
