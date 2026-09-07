package io.github.gustavo2358.analysis.cfg.adapters;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.air.model.Unit;
import java.util.List;
import java.util.Optional;

/** Independent AIR construction for control/coverage equivalence, not a CFG expected-value generator. */
final class MemoryFacts {
    private MemoryFacts() { }
    static Publication goback() {
        var p = new PublicationId("goback-0b-manual");
        var u = new UnitId(p, "unit");
        var origin = new OriginId(p, "memory-origin");
        var gap = new UncertaintyId(p, "memory-gap");
        var label = new LabelId(u, "sequence");
        var coverage = new Evidence.Coverage(Evidence.InventoryStatus.PARTIAL,
                new Scopes.PublicationScope(p), List.of(), List.of(gap));
        var unitCoverage = new Evidence.Coverage(Evidence.InventoryStatus.PARTIAL,
                new Scopes.UnitScope(u), List.of(), List.of(gap));
        var unit = unit(u, origin, List.of(entry(new EntryId(u, "primary-entry"), label, origin)),
                List.of(new Sequence(label, List.of(), new Operations.Return(header(new OperationId(u, "return"), origin), List.of()), origin)), unitCoverage);
        return new Publication(p, SemanticVersion.AIR_2_0_0, new Capabilities.Manifest(List.of(), List.of()),
                List.of(), List.of(unit), List.of(), List.of(), List.of(),
                List.of(new Origins.Unavailable(origin, "independent in-memory facts")), coverage,
                List.of(new Evidence.Uncertainty(gap, "UNKNOWN_REMAINDER", List.of(Evidence.Dimension.CONTROL),
                        new Scopes.PublicationScope(p), "unpublished control inventory", origin)), List.of());
    }
    static Entries.Entry entry(EntryId id, LabelId label, OriginId origin) {
        return new Entries.Entry(id, Optional.of(label), new Interactions.Signature(
                new Interactions.ParameterInventory(List.of(), Interactions.NoRemainder.INSTANCE),
                new Interactions.ResultInventory(List.of(), Interactions.NoRemainder.INSTANCE), origin),
                new Entries.EntryState(List.of(), List.of()), origin);
    }
    static Operations.Header header(OperationId id, OriginId origin) {
        var exact = new Evidence.Claim(new Scopes.UnitScope(id.unit()), Evidence.PrecisionStatus.EXACT, List.of());
        return new Operations.Header(id, origin, Evidence.CoverageStatus.MODELED,
                new Evidence.Precision(exact, exact, exact, exact, exact), List.of());
    }
    static Unit unit(UnitId id, OriginId origin, List<Entries.Entry> entries, List<Sequence> sequences, Evidence.Coverage coverage) {
        return new Unit(id, Optional.empty(), List.of(), List.of(), entries, sequences, List.of(),
                Unit.BodyAvailability.AVAILABLE, Optional.empty(), coverage, origin);
    }
    static Evidence.Coverage complete(Scopes.FactScope scope) {
        return new Evidence.Coverage(Evidence.InventoryStatus.COMPLETE, scope, List.of(), List.of());
    }
    static Publication mixed(String publicationId) {
        var p = new PublicationId(publicationId);
        var u = new UnitId(p, "U");var origin = new OriginId(p, "O");
        var branch = new LabelId(u, "a");var jump = new LabelId(u, "b");var ret = new LabelId(u, "c");
        var halt = new LabelId(u, "d");var abnormal = new LabelId(u, "e");
        var op = new OperationId(u, "branch");
        var predicate = new Expressions.Literal(new Operand.Header(new OperandId(new OperationOwner(op), "predicate"),
                Operand.Role.PREDICATE, origin), new Values.BoolValue(true));
        var sequences = List.of(
                new Sequence(branch, List.of(), new Operations.Branch(header(op, origin), predicate, jump, jump), origin),
                new Sequence(jump, List.of(), new Operations.Jump(header(new OperationId(u, "jump"), origin), ret), origin),
                new Sequence(ret, List.of(), new Operations.Return(header(new OperationId(u, "return"), origin), List.of()), origin),
                new Sequence(halt, List.of(), new Operations.Halt(header(new OperationId(u, "halt"), origin), Operations.HaltKind.NORMAL), origin),
                new Sequence(abnormal, List.of(), new Operations.Halt(header(new OperationId(u, "abnormal"), origin), Operations.HaltKind.ABNORMAL), origin));
        var unit = unit(u, origin, List.of(entry(new EntryId(u, "E"), branch, origin), entry(new EntryId(u, "F"), halt, origin)),
                sequences, complete(new Scopes.UnitScope(u)));
        return new Publication(p, SemanticVersion.AIR_2_0_0, new Capabilities.Manifest(List.of(), List.of()),
                List.of(), List.of(unit), List.of(), List.of(), List.of(), List.of(new Origins.Unavailable(origin, "memory")),
                complete(new Scopes.PublicationScope(p)), List.of(), List.of());
    }
}
