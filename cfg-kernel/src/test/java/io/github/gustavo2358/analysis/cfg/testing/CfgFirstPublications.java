package io.github.gustavo2358.analysis.cfg.testing;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.air.model.Unit;

import java.util.List;
import java.util.Optional;

/** In-memory AIR facts only; no CFG expected values or inferred entry labels. */
public final class CfgFirstPublications {
    public static final PublicationId P = new PublicationId("P");
    public static final UnitId U = new UnitId(P, "U");
    public static final EntryId E = new EntryId(U, "E");
    public static final LabelId L = new LabelId(U, "L");
    public static final OriginId ORIGIN = new OriginId(P, "origin");

    private CfgFirstPublications() { }

    public static Publication minimal() {
        return publication(List.of(unit(U, List.of(entry(E, L)), List.of(returning(L)))));
    }

    public static Publication publication(List<Unit> units) {
        return new Publication(P, SemanticVersion.AIR_2_0_0,
                new Capabilities.Manifest(List.of(), List.of()), List.of(), units,
                List.of(), List.of(), List.of(),
                List.of(new Origins.Unavailable(ORIGIN, "in-memory CFG-FIRST fixture")),
                coverage(new Scopes.PublicationScope(P)), List.of(), List.of());
    }

    public static Unit unit(UnitId id, List<Entries.Entry> entries, List<Sequence> sequences) {
        return new Unit(id, Optional.empty(), List.of(), List.of(), entries, sequences,
                List.of(), Unit.BodyAvailability.AVAILABLE, Optional.empty(),
                coverage(new Scopes.UnitScope(id)), ORIGIN);
    }

    public static Entries.Entry entry(EntryId id, LabelId initialLabel) {
        return new Entries.Entry(id, Optional.of(initialLabel),
                new Interactions.Signature(
                        new Interactions.ParameterInventory(List.of(), Interactions.NoRemainder.INSTANCE),
                        new Interactions.ResultInventory(List.of(), Interactions.NoRemainder.INSTANCE), ORIGIN),
                new Entries.EntryState(List.of(), List.of()), ORIGIN);
    }

    public static Sequence returning(LabelId label) {
        return new Sequence(label, List.of(),
                new Operations.Return(header(new OperationId(label.unit(), "return-" + label.localId())),
                        List.of()), ORIGIN);
    }

    public static Operations.Header header(OperationId id) {
        Evidence.Claim exact = new Evidence.Claim(new Scopes.UnitScope(id.unit()),
                Evidence.PrecisionStatus.EXACT, List.of());
        return new Operations.Header(id, ORIGIN, Evidence.CoverageStatus.MODELED,
                new Evidence.Precision(exact, exact, exact, exact, exact), List.of());
    }

    public static Evidence.Coverage coverage(Scopes.FactScope scope) {
        return new Evidence.Coverage(Evidence.InventoryStatus.COMPLETE, scope, List.of(), List.of());
    }
}
