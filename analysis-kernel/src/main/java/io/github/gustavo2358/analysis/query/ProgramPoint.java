package io.github.gustavo2358.analysis.query;

import io.github.gustavo2358.air.model.Control;
import io.github.gustavo2358.air.model.Ids.EntryId;
import io.github.gustavo2358.air.model.Ids.OperationId;
import java.util.Comparator;
import java.util.Objects;

/** Entry is mandatory. Before/after keep program-order meaning in either analysis direction. */
public record ProgramPoint(EntryId entry, Kind kind, OperationId operation, Control.OutcomeKey outcome) {
    public enum Kind { ENTRY, BEFORE, AFTER, OUTCOME }
    public ProgramPoint {
        Objects.requireNonNull(entry);Objects.requireNonNull(kind);
        if(kind==Kind.ENTRY ? operation!=null||outcome!=null : operation==null)throw new IllegalArgumentException("point shape");
        if(kind==Kind.BEFORE&&outcome!=null)throw new IllegalArgumentException("before has no outcome");
    }
    public static ProgramPoint entry(EntryId e) { return new ProgramPoint(e,Kind.ENTRY,null,null); }
    public static ProgramPoint before(EntryId e,OperationId op) { return new ProgramPoint(e,Kind.BEFORE,op,null); }
    /** NORMAL is the completed instruction outcome; terminators are explicitly refused. */
    public static ProgramPoint after(EntryId e,OperationId op) { return new ProgramPoint(e,Kind.AFTER,op,Control.NormalOutcome.INSTANCE); }
    private static String outcomeOrder(Control.OutcomeKey outcome) {
        return switch(outcome) {
            case Control.NormalOutcome ignored -> "0";
            case Control.ExceptionOutcome exception -> "1"+exception.tag();
            case Control.OtherExceptionOutcome ignored -> "2";
            case Control.HaltOutcome ignored -> "3";
            case Control.DivergeOutcome ignored -> "4";
        };
    }
    public static final Comparator<ProgramPoint> ORDER=Comparator
        .comparing((ProgramPoint p)->p.entry.unit().publication().localId())
        .thenComparing(p->p.entry.unit().localId()).thenComparing(p->p.entry.localId())
        .thenComparing(p->p.operation==null?"":p.operation.unit().publication().localId())
        .thenComparing(p->p.operation==null?"":p.operation.unit().localId())
        .thenComparing(p->p.operation==null?"":p.operation.localId())
        .thenComparing(ProgramPoint::kind).thenComparing(ProgramPoint::outcome,Comparator.nullsFirst(Comparator.comparing(ProgramPoint::outcomeOrder)));
}
