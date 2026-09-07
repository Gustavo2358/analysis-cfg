package io.github.gustavo2358.analysis.cfg.testing;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.air.model.Unit;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;

import static io.github.gustavo2358.analysis.cfg.testing.CfgFirstPublications.*;

/** AIR inputs only. No CFG builder, expected control, or textual successor inference. */
public final class LinearPublications {
    public static final LabelId TAIL = new LabelId(U, "tail");
    public static final ObjectId OBJECT = new ObjectId(U, "object");
    public static final StorageId CELL = new StorageId(P, "cell");
    public static final StorageId REGION = new StorageId(P, "region");
    public static final UncertaintyId GAP = new UncertaintyId(P, "value-gap");

    private LinearPublications() { }

    public static Sequence jump(LabelId from, LabelId to, List<Instruction> instructions) {
        return new Sequence(from, instructions,
                new Operations.Jump(header(new OperationId(from.unit(), "jump-" + from.localId())), to), ORIGIN);
    }

    public static Sequence halt(LabelId label, Operations.HaltKind kind) {
        return new Sequence(label, List.of(),
                new Operations.Halt(header(new OperationId(label.unit(), "halt-" + label.localId())), kind), ORIGIN);
    }

    public static Operations.Assign assign(String id, String value) {
        OperationId op = new OperationId(U, id);
        return new Operations.Assign(header(op), place(op, "destination"),
                new Expressions.Literal(operand(op, "value", Operand.Role.VALUE_READ), new Values.TextValue(value)));
    }

    public static Place place(OperationId op, String id) {
        return new Places.ObjectPlace(operand(op, id, Operand.Role.VALUE_WRITE), OBJECT);
    }

    public static Operand.Header operand(OperationId op, String id, Operand.Role role) {
        return new Operand.Header(new OperandId(new OperationOwner(op), id), role, ORIGIN);
    }

    public static Expression integer(OperationId op, String id, long value) {
        return new Expressions.Literal(operand(op, id, Operand.Role.VALUE_READ),
                new Values.IntValue(BigInteger.valueOf(value)));
    }

    public static Operations.CopyBytes copyBytes() {
        OperationId op = new OperationId(U, "copy");
        var memory = new Envelopes.MemoryEnvelope(List.of(), new Scopes.WithinMemory(
                new Scopes.StorageMemory(List.of(REGION))), List.of(), new Scopes.WithinMemory(
                new Scopes.StorageMemory(List.of(REGION))), List.of());
        var fallback = new Envelopes.Envelope(memory, new Control.ControlEnvelope(
                List.of(Control.ContinueAlternative.INSTANCE), Scopes.NoControl.INSTANCE),
                new Envelopes.DependencyEnvelope(List.of(), Scopes.NoResources.INSTANCE));
        return new Operations.CopyBytes(header(op),
                new Memory.ByteRange(REGION, integer(op, "dst-offset", 1), integer(op, "dst-extent", 2)),
                new Memory.ByteRange(REGION, integer(op, "src-offset", 0), integer(op, "src-extent", 2)),
                BigInteger.TWO, fallback);
    }

    public static List<Instruction> allInstructions() {
        OperationId must = new OperationId(U, "havoc-must");
        OperationId may = new OperationId(U, "havoc-may");
        Evidence.Claim exact = header(must).precision().control();
        Evidence.Claim open = new Evidence.Claim(new Scopes.UnitScope(U), Evidence.PrecisionStatus.OPEN, List.of(GAP));
        var precision = new Evidence.Precision(exact, exact, open, open, exact);
        return List.of(assign("z-first", "A"), assign("a-second", "B"),
                new Operations.HavocMust(new Operations.Header(must, ORIGIN, Evidence.CoverageStatus.ABSTRACTED,
                        precision, List.of(GAP)), place(must, "destination"), GAP),
                new Operations.HavocMay(new Operations.Header(may, ORIGIN, Evidence.CoverageStatus.ABSTRACTED,
                        precision, List.of(GAP)), new Scopes.ObjectsMemory(List.of(OBJECT)), GAP),
                new Operations.Nop(header(new OperationId(U, "nop"))), copyBytes());
    }

    public static Publication withData(List<Sequence> sequences, boolean regions) {
        Unit base = unit(U, List.of(entry(E, L)), sequences);
        var object = new Memory.ObjectDeclaration(OBJECT, Optional.of("display only"),
                new Types.Known(Types.Builtin.TEXT), new Memory.CellBinding(CELL), Memory.Visibility.PRIVATE,
                ORIGIN, Evidence.CoverageStatus.MODELED, header(new OperationId(U, "metadata")).precision());
        Unit unit = new Unit(base.id(), base.containingUnit(), List.of(object), base.visibleObjects(),
                base.entries(), base.sequences(), base.completionPorts(), base.body(), base.bodyUnavailable(),
                base.coverage(), base.origin());
        Publication original = publication(List.of(unit));
        var cell = new Memory.Cell(new Memory.StorageHeader(CELL, Optional.of(U), Memory.Lifetime.ACTIVATION,
                Memory.Visibility.PRIVATE, ORIGIN), new Types.Known(Types.Builtin.TEXT));
        var region = new Memory.Region(new Memory.StorageHeader(REGION, Optional.of(U), Memory.Lifetime.ACTIVATION,
                Memory.Visibility.PRIVATE, ORIGIN), Optional.of(BigInteger.valueOf(8)), Optional.empty());
        return new Publication(P, original.airVersion(), new Capabilities.Manifest(
                regions ? List.of(Capabilities.MEMORY_REGIONS) : List.of(), List.of()), original.artifacts(),
                original.units(), regions ? List.of(cell, region) : List.of(cell), original.resources(),
                original.artifactRelations(), original.origins(), original.coverage(),
                List.of(new Evidence.Uncertainty(GAP, "EXTERNAL_VALUE", List.of(Evidence.Dimension.VALUES,
                        Evidence.Dimension.EFFECTS), new Scopes.UnitScope(U), "value and effects open", ORIGIN)),
                original.premises());
    }

    public static Publication requiring(Publication base, List<Capabilities.Capability> required) {
        return new Publication(base.id(), base.airVersion(), new Capabilities.Manifest(required, List.of()),
                base.artifacts(), base.units(), base.storage(), base.resources(), base.artifactRelations(),
                base.origins(), base.coverage(), base.uncertainties(), base.premises());
    }
}
