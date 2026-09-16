package io.github.gustavo2358.analysis.values;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.analysis.query.ProgramPoint;
import io.github.gustavo2358.analysis.rd.DefinitionEvent;
import io.github.gustavo2358.analysis.storage.*;
import java.util.*;

/** Typed total ordering for detached products; no hash/inventory order or record rendering. */
final class StorageValueOrder {
    private StorageValueOrder() { }
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
    static final Comparator<Id> ID=Comparator.comparing(StorageValueOrder::idParts,list(Comparator.naturalOrder()));
    private static final Comparator<StorageRange> RANGE=Comparator.comparing(StorageRange::start).thenComparing(StorageRange::end,optional(Comparator.naturalOrder()));
    private static final Comparator<StorageIndex.ContextualLocation> LOCATION=Comparator
        .comparing((StorageIndex.ContextualLocation l)->l.location().base().id(),ID)
        .thenComparing(l->l.location().range(),optional(RANGE)).thenComparing(StorageIndex.ContextualLocation::activation,optional(ID));
    static final Comparator<RegionalValueFact.Interpretation> INTERPRETATION=Comparator.comparing(RegionalValueFact.Interpretation::location,LOCATION)
        .thenComparing(RegionalValueFact.Interpretation::codec,optional(StorageSubject.CODEC_ORDER));
    private static List<String> outcome(Control.OutcomeKey outcome) {
        return switch(outcome) {
            case Control.NormalOutcome ignored -> List.of("normal");
            case Control.ExceptionOutcome e -> List.of("exception",e.tag());
            case Control.OtherExceptionOutcome ignored -> List.of("other-exception");
            case Control.HaltOutcome ignored -> List.of("halt");
            case Control.DivergeOutcome ignored -> List.of("diverge");
        };
    }
    private static final Comparator<DefinitionEvent> DEFINITION=Comparator.comparing(DefinitionEvent::entry,ID)
        .thenComparing(DefinitionEvent::operation,optional(ID)).thenComparing(DefinitionEvent::destination,optional(ID))
        .thenComparingInt(DefinitionEvent::slot).thenComparing(DefinitionEvent::outcome,optional(Comparator.comparing(StorageValueOrder::outcome,list(Comparator.naturalOrder()))))
        .thenComparing(DefinitionEvent::storage,optional(ID)).thenComparing(DefinitionEvent::logicalObject,optional(ID)).thenComparing(DefinitionEvent::kind).thenComparing(DefinitionEvent::unknown)
        .thenComparing(DefinitionEvent::origin,ID).thenComparing(DefinitionEvent::premises,list(ID)).thenComparing(DefinitionEvent::uncertainties,list(ID))
        .thenComparing(DefinitionEvent::reasons,list(Comparator.naturalOrder()));
    static final Comparator<StorageValueFact.Capture> CAPTURE=Comparator.comparing(StorageValueFact.Capture::definition,DEFINITION)
        .thenComparing(StorageValueFact.Capture::before,ProgramPoint.ORDER).thenComparing(StorageValueFact.Capture::sourceRange,LOCATION)
        .thenComparing(StorageValueFact.Capture::destinationRange,LOCATION).thenComparing(StorageValueFact.Capture::sourceContribution,LOCATION)
        .thenComparing(StorageValueFact.Capture::destinationContribution,LOCATION);
    static final Comparator<StorageValueFact.SourceGap> GAP=Comparator.comparing(StorageValueFact.SourceGap::affectedLocation,LOCATION)
        .thenComparing(StorageValueFact.SourceGap::origin,ID).thenComparing(StorageValueFact.SourceGap::uncertainties,list(ID));
    private static final Comparator<StorageValueFact.Producer> PRODUCER=Comparator.comparing(StorageValueFact.Producer::definition,DEFINITION)
        .thenComparing(StorageValueFact.Producer::contributedRange,LOCATION);
    private static final Comparator<ValueFact.Support> SUPPORT=Comparator.comparing(ValueFact.Support::evidence,ID).thenComparing(ValueFact.Support::origin,ID).thenComparing(ValueFact.Support::premises,list(ID));
    private static final Comparator<StorageValueFact.LogicalCapture> LOGICAL_CAPTURE=Comparator.comparing(StorageValueFact.LogicalCapture::object,ID).thenComparing(StorageValueFact.LogicalCapture::before,ProgramPoint.ORDER).thenComparing(StorageValueFact.LogicalCapture::producers,list(SUPPORT));
    static final Comparator<StorageValueFact.Fragment> FRAGMENT=Comparator.comparing(StorageValueFact.Fragment::location,LOCATION)
        .thenComparing(StorageValueFact.Fragment::kind).thenComparing(StorageValueFact.Fragment::bytes,optional(Comparator.comparing(Values.BytesValue::octets,list(Comparator.naturalOrder()))))
        .thenComparing(StorageValueFact.Fragment::producer,optional(PRODUCER)).thenComparing(StorageValueFact.Fragment::unknownWriter,optional(DEFINITION))
        .thenComparing(StorageValueFact.Fragment::captures,list(CAPTURE)).thenComparing(StorageValueFact.Fragment::sourceGaps,list(GAP))
        .thenComparing(StorageValueFact.Fragment::modelReasons,list(Comparator.naturalOrder())).thenComparing(StorageValueFact.Fragment::logicalCapture,optional(LOGICAL_CAPTURE));
    static final Comparator<StorageValueFact.Alternative> ALTERNATIVE=Comparator.comparing(StorageValueFact.Alternative::interpretation,INTERPRETATION)
        .thenComparing(StorageValueFact.Alternative::candidate,optional(Comparator.comparing(Values.TextValue::value)))
        .thenComparing(StorageValueFact.Alternative::fragments,list(FRAGMENT));
}
