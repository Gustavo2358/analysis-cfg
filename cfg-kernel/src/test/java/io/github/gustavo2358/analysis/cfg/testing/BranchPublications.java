package io.github.gustavo2358.analysis.cfg.testing;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.air.model.Unit;

import java.util.List;

import static io.github.gustavo2358.analysis.cfg.testing.CfgFirstPublications.*;
import static io.github.gustavo2358.analysis.cfg.testing.LinearPublications.*;

/** AIR facts only, with explicit labels and operand owners. Never constructs expected CFG facts. */
public final class BranchPublications {
    public static final LabelId YES = new LabelId(U, "yes");
    public static final LabelId NO = new LabelId(U, "no");
    public static final LabelId JOIN = new LabelId(U, "join");
    public static final UncertaintyId TYPE_GAP = new UncertaintyId(P, "type-gap");

    private BranchPublications() { }

    public static OperationId branchId(LabelId source) {
        return new OperationId(source.unit(), "branch-" + source.localId());
    }

    public static Expressions.Literal bool(LabelId source, boolean value) {
        return new Expressions.Literal(operand(branchId(source), "predicate", Operand.Role.PREDICATE),
                new Values.BoolValue(value));
    }

    public static Expressions.Unknown unknown(LabelId source, Types.TypeRef type) {
        OperationId op = branchId(source);
        var read = new Expressions.Read(operand(op, "dependency", Operand.Role.VALUE_READ),
                new Places.ObjectPlace(operand(op, "read-place", Operand.Role.VALUE_READ), OBJECT));
        return new Expressions.Unknown(operand(op, "predicate", Operand.Role.PREDICATE), type,
                List.of(read), Scopes.NoMemory.INSTANCE, GAP);
    }

    public static Sequence branch(LabelId from, Expression predicate, LabelId yes, LabelId no) {
        return new Sequence(from, List.of(), new Operations.Branch(header(branchId(from)), predicate, yes, no), ORIGIN);
    }

    public static Publication plain(List<Sequence> sequences) {
        return publication(List.of(unit(U, List.of(entry(E, L)), sequences)));
    }

    public static Publication diamond(Expression predicate) {
        return withData(List.of(branch(L, predicate, YES, NO),
                jump(YES, JOIN, List.of(assign("opY", "Y"))),
                jump(NO, JOIN, List.of(assign("opN", "N"))), returning(JOIN)), false);
    }

    public static Publication withTypeGap(Publication base) {
        var gaps = new java.util.ArrayList<>(base.uncertainties());
        gaps.add(new Evidence.Uncertainty(TYPE_GAP, "TYPE_UNKNOWN", List.of(Evidence.Dimension.VALUES),
                new Scopes.UnitScope(U), "concrete domain unavailable", ORIGIN));
        return new Publication(base.id(), base.airVersion(), base.capabilities(), base.artifacts(), base.units(),
                base.storage(), base.resources(), base.artifactRelations(), base.origins(), base.coverage(), gaps, base.premises());
    }

    public static Publication replaceUnits(Publication base, List<Unit> units) {
        return new Publication(base.id(), base.airVersion(), base.capabilities(), base.artifacts(), units,
                base.storage(), base.resources(), base.artifactRelations(), base.origins(), base.coverage(),
                base.uncertainties(), base.premises());
    }

    public static Unit replaceSequences(Unit u, List<Sequence> sequences) {
        return new Unit(u.id(), u.containingUnit(), u.objects(), u.visibleObjects(), u.entries(), sequences,
                u.completionPorts(), u.body(), u.bodyUnavailable(), u.coverage(), u.origin());
    }
}
