package io.github.gustavo2358.analysis.storage;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.analysis.structure.AnalysisSession;
import java.util.*;

/** Positive proof for replacing current evidence. Unknown and MAY have no permit. */
public final class KillAuthority {
    public enum Execution { REQUIRED, POSSIBLE }
    public static final class Permit {
        private final StorageIndex.Location destination;
        private Permit(StorageIndex.Location destination) { this.destination=destination; }
        public StorageIndex.Location destination() { return destination; }
    }
    private KillAuthority() { }

    /** A singleton must-write, suitable for independent RD locations. */
    public static Optional<Permit> exact(StatementEffects.Write write,StatementEffects.Target target,Execution execution) {
        if(!write.destination().exact()||target.strength()!=StatementEffects.Strength.MUST)return Optional.empty();
        return selected(write,target,execution);
    }
    /** One exact destination in an explicitly enumerated, closed selection. */
    public static Optional<Permit> selected(StatementEffects.Write write,StatementEffects.Target target,Execution execution) {
        if(execution!=Execution.REQUIRED||write.occurrenceStrength()!=StatementEffects.Strength.MUST
                ||write.selection()!=StatementEffects.Selection.SINGLE_DESTINATION
                ||!(write.destination().remainder() instanceof Scopes.NoMemory)
                ||!target.sourceApplicable()||!complete(target.location())
                ||write.destination().candidates().stream().noneMatch(c->c.location().equals(target.location())))return Optional.empty();
        return Optional.of(new Permit(target.location()));
    }
    /** Only exhaustively represented required selections may exclude the unchanged store. */
    public static boolean exhaustive(StatementEffects.Write write,List<StatementEffects.Target> represented,Execution execution) {
        if(write.destination().candidates().isEmpty())return false;
        var proved=new HashSet<StorageIndex.Location>();
        for(var target:represented)selected(write,target,execution).ifPresent(p->proved.add(p.destination()));
        return write.destination().candidates().stream().allMatch(c->proved.contains(c.location()));
    }
    private static boolean complete(StorageIndex.Location location) {
        return location.range().isEmpty()||location.range().filter(r->r.end().isPresent()&&!r.empty()).isPresent();
    }
    /** Scalar admission already proves direct Cell bindings and inter-Cell separation. */
    public static Optional<Permit> exactCell(AnalysisSession session,Operation operation,Memory.Cell destination) {
        Place place=operation instanceof Operations.Assign a?a.destination():operation instanceof Operations.HavocMust h?h.destination():null;
        boolean required=place instanceof Places.ObjectPlace o&&destination.equals(session.index().directCell(o.object()));
        if(operation instanceof Operations.Opaque opaque)required=opaque.envelope().memory().mustOverwrite().stream().anyMatch(id->{
            var object=session.index().referencedObject(id);
            return object!=null&&object.storage() instanceof Memory.CellBinding&&destination.equals(session.index().directCell(object.id()));
        });
        return required?Optional.of(new Permit(new StorageIndex.Location(destination.header(),Optional.empty()))):Optional.empty();
    }
    public static <T> T strongOverwrite(Permit permit,T replacement) {
        Objects.requireNonNull(permit,"strong overwrite requires positive authority");return Objects.requireNonNull(replacement);
    }
    public static <T> Set<T> weakUpdate(Set<T> previous,Set<T> supplied) {
        if(previous.containsAll(supplied))return previous;
        var result=new HashSet<>(previous);result.addAll(supplied);return Set.copyOf(result);
    }
    public static <T> Set<T> widenUnknown(Set<T> previous,T unknownContribution) {
        return weakUpdate(previous,Set.of(unknownContribution));
    }
}
