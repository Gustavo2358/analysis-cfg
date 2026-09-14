package io.github.gustavo2358.analysis.rd;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.analysis.storage.StatementEffects;
import io.github.gustavo2358.analysis.storage.StorageIndex;
import java.util.*;

/** Finite producer event, independent of the number of paths or solver visits. */
public record DefinitionEvent(EntryId entry,Optional<OperationId> operation,Optional<OperandId> destination,
                             int slot,Optional<Control.OutcomeKey> outcome,StorageId storage,
                             Kind kind,boolean unknown,OriginId origin,List<PremiseId> premises,List<UncertaintyId> uncertainties,List<String> reasons) {
    public enum Kind { ENTRY_UNKNOWN, INITIAL_CONDITION, ENTRY_PRESERVE, ENTRY_UNINITIALIZED, ENTRY_PARAMETER, ENTRY_EXTERNAL, ASSIGN, COPY, UNKNOWN_WRITE }
    public DefinitionEvent { Objects.requireNonNull(entry);Objects.requireNonNull(operation);Objects.requireNonNull(destination);Objects.requireNonNull(outcome);Objects.requireNonNull(storage);Objects.requireNonNull(kind);Objects.requireNonNull(origin);premises=ordered(premises);uncertainties=ordered(uncertainties);reasons=reasons.stream().distinct().sorted().toList(); }
    /** Shared event materialization for RD and value provenance; no consumer reconstruction. */
    public static DefinitionEvent write(EntryId entry,Operation operation,StatementEffects.Write write,StatementEffects.Target target,Optional<Control.OutcomeKey> outcome) {
        var source=write.source();var unknown=source instanceof StatementEffects.UnknownSource||!target.sourceApplicable();
        if(source instanceof StatementEffects.ExpressionSource expression&&!(expression.value() instanceof Expressions.Literal))unknown=true;
        var kind=source instanceof StatementEffects.CapturedBytes?Kind.COPY:source instanceof StatementEffects.ExpressionSource?Kind.ASSIGN:Kind.UNKNOWN_WRITE;
        var uncertainty=new LinkedHashSet<>(operation.header().uncertainties());uncertainty.addAll(write.destination().uncertainties());
        if(operation instanceof Operations.HavocMust h)uncertainty.add(h.reason());
        if(operation instanceof Operations.HavocMay h)uncertainty.add(h.reason());
        var reasons=new LinkedHashSet<>(target.reasons());if(source instanceof StatementEffects.UnknownSource u)reasons.add(u.reason());
        return new DefinitionEvent(entry,Optional.of(operation.header().id()),write.occurrence(),write.slot(),outcome,target.location().base().id(),kind,unknown,operation.header().origin(),target.premises(),List.copyOf(uncertainty),List.copyOf(reasons));
    }
    public static DefinitionEvent initial(EntryId entry,Entries.InitialCondition condition,int slot,StatementEffects.Target target,StorageIndex.Resolution resolution) {
        var value=condition.value();var kind=value instanceof Entries.LiteralInitial?Kind.INITIAL_CONDITION:value instanceof Entries.Preserve?Kind.ENTRY_PRESERVE
            :value instanceof Entries.ParameterInitial?Kind.ENTRY_PARAMETER:value instanceof Entries.ExternalUnknown?Kind.ENTRY_EXTERNAL:Kind.ENTRY_UNINITIALIZED;
        var uncertainty=new LinkedHashSet<UncertaintyId>();if(value instanceof Entries.ExternalUnknown u)uncertainty.add(u.reason());if(value instanceof Entries.Uninitialized u)uncertainty.add(u.reason());uncertainty.addAll(resolution.uncertainties());
        var premises=new LinkedHashSet<>(condition.premises());premises.addAll(target.premises());
        return new DefinitionEvent(entry,Optional.empty(),Optional.of(condition.place().header().id()),slot,Optional.empty(),target.location().base().id(),kind,!(value instanceof Entries.LiteralInitial)||!target.sourceApplicable(),condition.origin(),List.copyOf(premises),List.copyOf(uncertainty),target.reasons());
    }
    private static <T extends Id> List<T> ordered(List<T> ids){return ids.stream().distinct().sorted(Comparator.comparing((T id)->id.publication().localId()).thenComparing(Id::localId)).toList();}

    // Complete typed tie-breaker: equal operation/slot/storage is not equal evidence.
    private static <T> Comparator<Optional<T>> optional(Comparator<? super T> order) {
        return (a,b)->a.isEmpty()?(b.isEmpty()?0:-1):b.isEmpty()?1:order.compare(a.get(),b.get());
    }
    private static <T> Comparator<List<T>> list(Comparator<? super T> order) {
        return (a,b)->{for(int i=0;i<Math.min(a.size(),b.size());i++){int c=order.compare(a.get(i),b.get(i));if(c!=0)return c;}return Integer.compare(a.size(),b.size());};
    }
    private static List<String> idParts(Id id) {
        var result=new ArrayList<String>();result.add(id.publication().localId());
        switch(id) {
            case PublicationId ignored -> result.add("publication");
            case UnitId ignored -> result.add("unit");
            case StorageId ignored -> result.add("storage");
            case ResourceId ignored -> result.add("resource");
            case ArtifactId ignored -> result.add("artifact");
            case ArtifactRelationId ignored -> result.add("artifact-relation");
            case OriginId ignored -> result.add("origin");
            case UncertaintyId ignored -> result.add("uncertainty");
            case PremiseId ignored -> result.add("premise");
            case EntryId i -> result.addAll(List.of("entry",i.unit().localId()));
            case LabelId i -> result.addAll(List.of("label",i.unit().localId()));
            case OperationId i -> result.addAll(List.of("operation",i.unit().localId()));
            case ObjectId i -> result.addAll(List.of("object",i.unit().localId()));
            case CompletionPortId i -> result.addAll(List.of("completion-port",i.unit().localId()));
            case OperandId i -> {
                result.addAll(List.of("operand",i.owner().unit().localId()));
                switch(i.owner()) {
                    case OperationOwner o -> result.addAll(List.of("operation",o.operation().localId()));
                    case EntryOwner o -> result.addAll(List.of("entry",o.entry().localId()));
                }
            }
        }
        result.add(id.localId());return result;
    }
    private static final Comparator<Id> ID=Comparator.comparing(DefinitionEvent::idParts,list(Comparator.naturalOrder()));
    private static List<String> outcome(Control.OutcomeKey outcome) {
        return switch(outcome) {
            case Control.NormalOutcome ignored -> List.of("normal");
            case Control.ExceptionOutcome e -> List.of("exception",e.tag());
            case Control.OtherExceptionOutcome ignored -> List.of("other-exception");
            case Control.HaltOutcome ignored -> List.of("halt");
            case Control.DivergeOutcome ignored -> List.of("diverge");
        };
    }
    static final Comparator<DefinitionEvent> ORDER=Comparator.comparing(DefinitionEvent::entry,ID)
        .thenComparing(DefinitionEvent::operation,optional(ID)).thenComparing(DefinitionEvent::destination,optional(ID))
        .thenComparingInt(DefinitionEvent::slot).thenComparing(DefinitionEvent::outcome,optional(Comparator.comparing(DefinitionEvent::outcome,list(Comparator.naturalOrder()))))
        .thenComparing(DefinitionEvent::storage,ID).thenComparing(DefinitionEvent::kind).thenComparing(DefinitionEvent::unknown)
        .thenComparing(DefinitionEvent::origin,ID).thenComparing(DefinitionEvent::premises,list(ID)).thenComparing(DefinitionEvent::uncertainties,list(ID))
        .thenComparing(DefinitionEvent::reasons,list(Comparator.naturalOrder()));
}
