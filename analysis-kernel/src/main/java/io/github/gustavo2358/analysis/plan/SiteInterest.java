package io.github.gustavo2358.analysis.plan;

import io.github.gustavo2358.air.model.Ids.EntryId;
import io.github.gustavo2358.air.model.Operation;
import io.github.gustavo2358.analysis.consumers.SiteView;
import io.github.gustavo2358.analysis.query.PointQuery;
import java.util.*;
import java.util.function.*;

/** Pure static selectors operate only on a site from a W1 AIR-kind bucket. No whole-program callback. */
public record SiteInterest(Class<? extends Operation> kind, EntryId entry, Predicate<SiteView> filter,
                           List<SiteQuery<?,?>> queries) {
    public SiteInterest { Objects.requireNonNull(kind); Objects.requireNonNull(entry); Objects.requireNonNull(filter); queries = List.copyOf(queries); }
    public SiteInterest(Class<? extends Operation> kind, EntryId entry, Predicate<SiteView> filter) { this(kind,entry,filter,List.of()); }
    /** Pure declaration evaluated in planning, once per matching interest, never during consume. */
    public record SiteQuery<T,V>(ObservationBatchId<T,V> batch, Function<SiteView,PointQuery<T>> query) {
        public SiteQuery { Objects.requireNonNull(batch); Objects.requireNonNull(query); }
        public ObservationRequest<T,V> at(SiteView site) { return new ObservationRequest<>(batch,List.of(query.apply(site))); }
    }
}
