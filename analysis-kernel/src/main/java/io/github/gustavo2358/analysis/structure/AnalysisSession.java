package io.github.gustavo2358.analysis.structure;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.EntryId;
import io.github.gustavo2358.analysis.cfg.application.CfgBuildResult;
import io.github.gustavo2358.analysis.cfg.domain.ProjectionPolicy;
import java.util.*;

/** Owns one immutable structural index and only explicitly selected contextual views. No analysis runs. */
public final class AnalysisSession {
    private final ProgramIndex index;
    private final Map<EntryId, ContextView> contexts;
    private AnalysisSession(ProgramIndex index, Collection<Entries.Entry> selected) {
        this.index = index;
        Map<EntryId, ContextView> views = new LinkedHashMap<>();
        for (Entries.Entry entry : selected) views.computeIfAbsent(entry.id(), ignored -> new ContextView(index, entry));
        contexts = Collections.unmodifiableMap(views);
    }
    public enum Status { ACCEPTED, INVALID_INPUT, UNSUPPORTED }
    public record Admission(Status status, String reason, Optional<AnalysisSession> session) {
        public Admission {
            Objects.requireNonNull(status, "status"); Objects.requireNonNull(reason, "reason");
            Objects.requireNonNull(session, "session");
            if ((status == Status.ACCEPTED) != session.isPresent()) throw new IllegalArgumentException("admission/session mismatch");
        }
    }
    public ProgramIndex index() { return index; }
    /** Null means this Entry was not selected. Selection order is retained; duplicate selections share a view. */
    public ContextView context(EntryId entry) { return contexts.get(entry); }
    public Collection<ContextView> contexts() { return contexts.values(); }

    /**
     * A narrower explicit context selection sharing this already admitted index. No AIR/CFG traversal,
     * validation or projection is repeated. Only Entries selected by this owner can be selected again.
     * The new view has its own contextual handles; it does not mutate this session or any stable run.
     */
    public AnalysisSession selectEntries(Collection<EntryId> selectedEntries) {
        var selected = new ArrayList<Entries.Entry>();
        for (var id : selectedEntries) {
            var context = contexts.get(Objects.requireNonNull(id));
            if (context == null) throw new IllegalArgumentException("Entry not selected by owning session");
            selected.add(context.entry());
        }
        return new AnalysisSession(index, selected);
    }


    /**
     * Correlates a successful core BuildCfg result with the expected snapshot and policy.
     * Structural checks are linear in inventoried entities/references/nodes/edges, with expected O(1)
     * hash lookups. It neither reruns validation/projection nor certifies producer truth or source coverage.
     * Legacy incomplete/limited builds are execution precondition failures, never size-based admission.
     */
    public static Admission open(CfgBuildResult result, Publication snapshot, ProjectionPolicy policy,
                                 Collection<Entries.Entry> selectedEntries) {
        if (result == null || snapshot == null || policy == null || selectedEntries == null)
            return new Admission(Status.INVALID_INPUT, "null structural input", Optional.empty());
        if (result.status() == CfgBuildResult.Status.VALIDATION_LIMIT
                || result.status() == CfgBuildResult.Status.INCOMPLETE_VALIDATION)
            throw new IllegalStateException("a successful complete CFG build is required; upstream validation did not complete");
        try {
            var builder = new IndexBuilder(result, snapshot, policy);
            ProgramIndex index = builder.build();
            List<Entries.Entry> selected = new ArrayList<>();
            for (Entries.Entry entry : selectedEntries) {
                IndexBuilder.valid(entry != null && index.entry(entry.id()) == entry, "foreign selected Entry");
                selected.add(entry);
            }
            return new Admission(Status.ACCEPTED, "core structural inventory admitted", Optional.of(new AnalysisSession(index, selected)));
        } catch (IndexBuilder.Rejection rejection) {
            return new Admission(rejection.status, rejection.getMessage(), Optional.empty());
        }
    }
}
