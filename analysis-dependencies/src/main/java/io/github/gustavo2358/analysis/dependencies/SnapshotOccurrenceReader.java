package io.github.gustavo2358.analysis.dependencies;

import io.github.gustavo2358.air.model.*;
import java.math.BigInteger;
import java.util.*;

/**
 * One requested typed occurrence, not a Publication/Unit/Sequence decoder. Explicit constructors
 * follow the pinned official AirShape field ordinals. No reflection, wire parser, semantic
 * inference or decoded-identity cache. Aggregate program bodies remain borrowed views.
 * Per-occurrence payload allocation is caller-managed; this alone is not a whole-analysis heap bound.
 */
final class SnapshotOccurrenceReader {
    private final AirSnapshot snapshot;
    private final Runnable owner;
    SnapshotOccurrenceReader(AirSnapshot snapshot,Runnable owner) {
        this.snapshot=Objects.requireNonNull(snapshot);this.owner=Objects.requireNonNull(owner);
    }
    <T> T read(long handle,Class<T> expected){return expected.cast(read(handle));}
    Object read(long handle) {
        owner.run();
        var pending=new ArrayDeque<Frame>();pending.push(new Frame(handle,null));
        try {
        while(true) {
            var frame=pending.peek();Object result;
            switch(frame.shape.form()) {
                case TEXT -> result=text(frame.handle);
                case INTEGER -> result=new BigInteger(text(frame.handle));
                case BOOLEAN -> result=snapshot.scalar(frame.handle)!=0;
                case SMALL_INTEGER -> result=Math.toIntExact(snapshot.scalar(frame.handle));
                case ENUM -> result=construct(frame.shape,null,snapshot.scalar(frame.handle));
                case LIST,OPTIONAL -> {
                    if(frame.cursor==null)frame.cursor=snapshot.elements(frame.handle,Objects.requireNonNull(frame.element));
                    if(frame.cursor.advance()){pending.push(new Frame(frame.cursor.value(),null));continue;}
                    frame.cursor.close();frame.cursor=null;
                    result=frame.shape==AirShape.LIST?List.copyOf(frame.values):
                        frame.values.isEmpty()?Optional.empty():Optional.of(frame.values.getFirst());
                }
                case RECORD -> {
                    if(frame.at<frame.shape.fieldCount()) {
                        var slot=frame.shape.field(frame.at++);
                        pending.push(new Frame(snapshot.field(frame.handle,frame.shape,slot.index()),slot.element()));
                        continue;
                    }
                    result=construct(frame.shape,frame.values.toArray(),0);
                }
                default -> throw new IllegalStateException("concrete occurrence required");
            }
            pending.pop();
            if(pending.isEmpty())return result;
            pending.peek().values.add(result);
        }
        } finally {
            for(var frame:pending)if(frame.cursor!=null)frame.cursor.close();
        }
    }
    private final class Frame {
        final long handle;final AirShape shape,element;
        final ArrayList<Object> values=new ArrayList<>();
        int at;AirSnapshot.Cursor cursor;
        Frame(long handle,AirShape element) {
            this.handle=handle;this.shape=snapshot.shape(handle);this.element=element;
            if(shape==AirShape.PUBLICATION||shape==AirShape.UNIT||shape==AirShape.SEQUENCE||shape==AirShape.EVIDENCE_COVERAGE)
                throw new IllegalArgumentException("aggregate must remain a borrowed program view: "+shape);
        }
    }
    private String text(long handle) {
        int length=Math.toIntExact(snapshot.characterCount(handle));char[] value=new char[length];
        for(int at=0;at<length;) {
            int count=snapshot.readCharacters(handle,at,value,at,Math.min(1024,length-at));
            if(count<=0)throw new IllegalStateException("snapshot text made no progress");
            at+=count;
        }
        return new String(value);
    }
    @SuppressWarnings("unchecked")
    private static Object construct(AirShape shape,Object[] a,long scalar) {
        return switch(shape) {
            case ARTIFACTS_EXTERNAL_ARTIFACT -> new Artifacts.ExternalArtifact((Interactions.LiteralTarget)a[0]);
            case ARTIFACTS_INTERNAL_ARTIFACT -> new Artifacts.InternalArtifact((Ids.ArtifactId)a[0]);
            case ARTIFACTS_RELATION -> new Artifacts.Relation((Ids.ArtifactRelationId)a[0],(Ids.ArtifactId)a[1],(Artifacts.RelationTarget)a[2],(java.lang.String)a[3],(Ids.OriginId)a[4],(Evidence.CoverageStatus)a[5]);
            case CAPABILITIES_CAPABILITY -> new Capabilities.Capability((java.lang.String)a[0],(java.lang.String)a[1]);
            case CAPABILITIES_MANIFEST -> new Capabilities.Manifest((java.util.List<Capabilities.Capability>)a[0],(java.util.List<Capabilities.Capability>)a[1]);
            case CONTROL_AFTER -> new Control.After((Ids.OperationId)a[0],(Control.OutcomeKey)a[1]);
            case CONTROL_ANY_EXCEPTION -> new Control.AnyException((Control.ExceptionDestination)a[0]);
            case CONTROL_BEFORE -> new Control.Before((Ids.OperationId)a[0]);
            case CONTROL_CONTINUE_ALTERNATIVE -> Control.ContinueAlternative.values()[Math.toIntExact(scalar)];
            case CONTROL_CONTROL_ENVELOPE -> new Control.ControlEnvelope((java.util.List<Control.ControlAlternative>)a[0],(Scopes.ControlBound)a[1]);
            case CONTROL_DIVERGE -> Control.Diverge.values()[Math.toIntExact(scalar)];
            case CONTROL_DIVERGE_OUTCOME -> Control.DivergeOutcome.values()[Math.toIntExact(scalar)];
            case CONTROL_ENTRY_POINT -> new Control.EntryPoint((Ids.EntryId)a[0]);
            case CONTROL_EXCEPTION_OUTCOME -> new Control.ExceptionOutcome((java.lang.String)a[0]);
            case CONTROL_EXCEPTIONAL -> new Control.Exceptional((java.lang.String)a[0],(Control.ExceptionDestination)a[1]);
            case CONTROL_EXIT_POINT -> new Control.ExitPoint((Ids.UnitId)a[0],(Control.OutcomeKey)a[1]);
            case CONTROL_HALT_ALTERNATIVE -> Control.HaltAlternative.values()[Math.toIntExact(scalar)];
            case CONTROL_HALT_OUTCOME -> Control.HaltOutcome.values()[Math.toIntExact(scalar)];
            case CONTROL_HANDLER -> new Control.Handler((Ids.LabelId)a[0]);
            case CONTROL_INVOCATION_OUTCOMES -> new Control.InvocationOutcomes((java.util.List<Control.InvocationAlternative>)a[0],(Scopes.ControlBound)a[1]);
            case CONTROL_JUMP_ALTERNATIVE -> new Control.JumpAlternative((Ids.LabelId)a[0]);
            case CONTROL_NORMAL -> new Control.Normal((Ids.LabelId)a[0]);
            case CONTROL_NORMAL_OUTCOME -> Control.NormalOutcome.values()[Math.toIntExact(scalar)];
            case CONTROL_OTHER_EXCEPTION_OUTCOME -> Control.OtherExceptionOutcome.values()[Math.toIntExact(scalar)];
            case CONTROL_PROPAGATE -> Control.Propagate.values()[Math.toIntExact(scalar)];
            case CONTROL_RETURN_ALTERNATIVE -> Control.ReturnAlternative.values()[Math.toIntExact(scalar)];
            case DECIMAL_TEXT_KIND -> DecimalText.Kind.values()[Math.toIntExact(scalar)];
            case DECIMAL_TEXT_PART -> new DecimalText.Part((DecimalText.Kind)a[0],(java.math.BigInteger)a[1],(java.lang.String)a[2],(java.lang.String)a[3]);
            case ENTRIES_COMPLETION_PORT -> new Entries.CompletionPort((Ids.CompletionPortId)a[0],(Ids.OriginId)a[1]);
            case ENTRIES_ENTRY -> new Entries.Entry((Ids.EntryId)a[0],(java.util.Optional<Ids.LabelId>)a[1],(Interactions.Signature)a[2],(Entries.EntryState)a[3],(Ids.OriginId)a[4]);
            case ENTRIES_ENTRY_STATE -> new Entries.EntryState((java.util.List<Entries.InitialCondition>)a[0],(java.util.List<Ids.UncertaintyId>)a[1]);
            case ENTRIES_EXTERNAL_UNKNOWN -> new Entries.ExternalUnknown((Ids.UncertaintyId)a[0]);
            case ENTRIES_INITIAL_CONDITION -> new Entries.InitialCondition((Place)a[0],(Entries.InitialValue)a[1],(Ids.OriginId)a[2],(java.util.List<Ids.PremiseId>)a[3]);
            case ENTRIES_LITERAL_INITIAL -> new Entries.LiteralInitial((Expressions.Literal)a[0]);
            case ENTRIES_PARAMETER_INITIAL -> new Entries.ParameterInitial((java.math.BigInteger)a[0]);
            case ENTRIES_POSSIBLE_LITERALS -> new Entries.PossibleLiterals((java.util.List<Expressions.Literal>)a[0],(Ids.UncertaintyId)a[1]);
            case ENTRIES_PRESERVE -> Entries.Preserve.values()[Math.toIntExact(scalar)];
            case ENTRIES_UNINITIALIZED -> new Entries.Uninitialized((Ids.UncertaintyId)a[0]);
            case ENVELOPES_DEPENDENCY_ENVELOPE -> new Envelopes.DependencyEnvelope((java.util.List<Envelopes.ResourceUse>)a[0],(Scopes.DependencyBound)a[1]);
            case ENVELOPES_ENVELOPE -> new Envelopes.Envelope((Envelopes.MemoryEnvelope)a[0],(Control.ControlEnvelope)a[1],(Envelopes.DependencyEnvelope)a[2]);
            case ENVELOPES_MEMORY_ENVELOPE -> new Envelopes.MemoryEnvelope((java.util.List<Ids.OperandId>)a[0],(Scopes.MemoryBound)a[1],(java.util.List<Ids.OperandId>)a[2],(Scopes.MemoryBound)a[3],(java.util.List<Ids.OperandId>)a[4]);
            case ENVELOPES_RESOURCE_USE -> new Envelopes.ResourceUse((java.lang.String)a[0],(Interactions.ResourceDescription)a[1],(Control.ProgramPoint)a[2],(Ids.OriginId)a[3]);
            case EVIDENCE_CLAIM -> new Evidence.Claim((Scopes.FactScope)a[0],(Evidence.PrecisionStatus)a[1],(java.util.List<Ids.UncertaintyId>)a[2]);
            case EVIDENCE_COVERAGE_ITEM -> new Evidence.CoverageItem((java.lang.String)a[0],(Ids.OriginId)a[1],(Evidence.CoverageStatus)a[2],(java.util.List<Ids.Id>)a[3],(java.util.List<Ids.UncertaintyId>)a[4],(java.util.Optional<Evidence.Elimination>)a[5]);
            case EVIDENCE_COVERAGE_STATUS -> Evidence.CoverageStatus.values()[Math.toIntExact(scalar)];
            case EVIDENCE_DIMENSION -> Evidence.Dimension.values()[Math.toIntExact(scalar)];
            case EVIDENCE_ELIMINATION -> new Evidence.Elimination((java.lang.String)a[0],(Ids.OriginId)a[1]);
            case EVIDENCE_INVENTORY_STATUS -> Evidence.InventoryStatus.values()[Math.toIntExact(scalar)];
            case EVIDENCE_PRECISION -> new Evidence.Precision((Evidence.Claim)a[0],(Evidence.Claim)a[1],(Evidence.Claim)a[2],(Evidence.Claim)a[3],(Evidence.Claim)a[4]);
            case EVIDENCE_PRECISION_STATUS -> Evidence.PrecisionStatus.values()[Math.toIntExact(scalar)];
            case EVIDENCE_UNCERTAINTY -> new Evidence.Uncertainty((Ids.UncertaintyId)a[0],(java.lang.String)a[1],(java.util.List<Evidence.Dimension>)a[2],(Scopes.FactScope)a[3],(java.lang.String)a[4],(Ids.OriginId)a[5]);
            case EXPRESSIONS_BINARY -> new Expressions.Binary((Operand.Header)a[0],(Expressions.BinaryOperator)a[1],(Expression)a[2],(Expression)a[3]);
            case EXPRESSIONS_BINARY_OPERATOR -> Expressions.BinaryOperator.values()[Math.toIntExact(scalar)];
            case EXPRESSIONS_FILL_TEXT -> new Expressions.FillText((Operand.Header)a[0],(Expression)a[1],(java.math.BigInteger)a[2]);
            case EXPRESSIONS_FIT_DECIMAL -> new Expressions.FitDecimal((Operand.Header)a[0],(Expression)a[1],(java.math.BigInteger)a[2],(java.math.BigInteger)a[3],(Boolean)a[4]);
            case EXPRESSIONS_FIT_TEXT -> new Expressions.FitText((Operand.Header)a[0],(Expression)a[1],(java.math.BigInteger)a[2],(java.lang.String)a[3]);
            case EXPRESSIONS_FORMAT_DECIMAL -> new Expressions.FormatDecimal((Operand.Header)a[0],(Expression)a[1],(java.util.List<DecimalText.Part>)a[2]);
            case EXPRESSIONS_INTEGER_DIGITS -> new Expressions.IntegerDigits((Operand.Header)a[0],(Expression)a[1],(java.math.BigInteger)a[2]);
            case EXPRESSIONS_LITERAL -> new Expressions.Literal((Operand.Header)a[0],(Values.LiteralValue)a[1]);
            case EXPRESSIONS_PARSE_INTEGER -> new Expressions.ParseInteger((Operand.Header)a[0],(Expression)a[1],(Expression)a[2]);
            case EXPRESSIONS_QUANTIZE -> new Expressions.Quantize((Operand.Header)a[0],(Expression)a[1],(java.math.BigInteger)a[2],(Expressions.Rounding)a[3]);
            case EXPRESSIONS_READ -> new Expressions.Read((Operand.Header)a[0],(Place)a[1]);
            case EXPRESSIONS_ROUNDING -> Expressions.Rounding.values()[Math.toIntExact(scalar)];
            case EXPRESSIONS_SLICE_TEXT -> new Expressions.SliceText((Operand.Header)a[0],(Expression)a[1],(Expression)a[2],(Expression)a[3]);
            case EXPRESSIONS_TRIM_RIGHT -> new Expressions.TrimRight((Operand.Header)a[0],(Expression)a[1],(java.lang.String)a[2]);
            case EXPRESSIONS_UNARY -> new Expressions.Unary((Operand.Header)a[0],(Expressions.UnaryOperator)a[1],(Expression)a[2]);
            case EXPRESSIONS_UNARY_OPERATOR -> Expressions.UnaryOperator.values()[Math.toIntExact(scalar)];
            case EXPRESSIONS_UNKNOWN -> new Expressions.Unknown((Operand.Header)a[0],(Types.TypeRef)a[1],(java.util.List<Expression>)a[2],(Scopes.MemoryBound)a[3],(Ids.UncertaintyId)a[4]);
            case EXPRESSIONS_WRAP_INTEGER -> new Expressions.WrapInteger((Operand.Header)a[0],(Expression)a[1],(java.math.BigInteger)a[2],(Boolean)a[3]);
            case IDS_ARTIFACT_ID -> new Ids.ArtifactId((Ids.PublicationId)a[0],(java.lang.String)a[1]);
            case IDS_ARTIFACT_RELATION_ID -> new Ids.ArtifactRelationId((Ids.PublicationId)a[0],(java.lang.String)a[1]);
            case IDS_COMPLETION_PORT_ID -> new Ids.CompletionPortId((Ids.UnitId)a[0],(java.lang.String)a[1]);
            case IDS_ENTRY_ID -> new Ids.EntryId((Ids.UnitId)a[0],(java.lang.String)a[1]);
            case IDS_ENTRY_OWNER -> new Ids.EntryOwner((Ids.EntryId)a[0]);
            case IDS_LABEL_ID -> new Ids.LabelId((Ids.UnitId)a[0],(java.lang.String)a[1]);
            case IDS_OBJECT_ID -> new Ids.ObjectId((Ids.UnitId)a[0],(java.lang.String)a[1]);
            case IDS_OPERAND_ID -> new Ids.OperandId((Ids.OperandOwner)a[0],(java.lang.String)a[1]);
            case IDS_OPERATION_ID -> new Ids.OperationId((Ids.UnitId)a[0],(java.lang.String)a[1]);
            case IDS_OPERATION_OWNER -> new Ids.OperationOwner((Ids.OperationId)a[0]);
            case IDS_ORIGIN_ID -> new Ids.OriginId((Ids.PublicationId)a[0],(java.lang.String)a[1]);
            case IDS_PREMISE_ID -> new Ids.PremiseId((Ids.PublicationId)a[0],(java.lang.String)a[1]);
            case IDS_PUBLICATION_ID -> new Ids.PublicationId((java.lang.String)a[0]);
            case IDS_RESOURCE_ID -> new Ids.ResourceId((Ids.PublicationId)a[0],(java.lang.String)a[1]);
            case IDS_STORAGE_ID -> new Ids.StorageId((Ids.PublicationId)a[0],(java.lang.String)a[1]);
            case IDS_UNCERTAINTY_ID -> new Ids.UncertaintyId((Ids.PublicationId)a[0],(java.lang.String)a[1]);
            case IDS_UNIT_ID -> new Ids.UnitId((Ids.PublicationId)a[0],(java.lang.String)a[1]);
            case INTERACTIONS_COMPUTED_RESOURCE -> new Interactions.ComputedResource((java.lang.String)a[0],(java.lang.String)a[1],(Ids.OperandId)a[2],(Interactions.NamePolicy)a[3],(Ids.OriginId)a[4]);
            case INTERACTIONS_COMPUTED_TARGET -> new Interactions.ComputedTarget((java.lang.String)a[0],(java.lang.String)a[1],(Expression)a[2],(Interactions.NamePolicy)a[3],(Ids.OriginId)a[4]);
            case INTERACTIONS_CONTRACT_REF -> new Interactions.ContractRef((java.lang.String)a[0],(java.lang.String)a[1],(java.util.List<Ids.OriginId>)a[2]);
            case INTERACTIONS_COPY_ARGUMENT -> new Interactions.CopyArgument((Expression)a[0]);
            case INTERACTIONS_EFFECT_BOUND -> new Interactions.EffectBound((Interactions.ForeignEffects)a[0],(java.util.List<Interactions.OutcomeEffects>)a[1]);
            case INTERACTIONS_ENTRY_SIGNATURE -> new Interactions.EntrySignature((Ids.EntryId)a[0]);
            case INTERACTIONS_EXACT_NAME -> Interactions.ExactName.values()[Math.toIntExact(scalar)];
            case INTERACTIONS_EXTENSION_NAME -> new Interactions.ExtensionName((java.lang.String)a[0],(java.lang.String)a[1]);
            case INTERACTIONS_EXTERNAL_BINDING -> Interactions.ExternalBinding.values()[Math.toIntExact(scalar)];
            case INTERACTIONS_EXTERNAL_SIGNATURE -> new Interactions.ExternalSignature((Interactions.Signature)a[0]);
            case INTERACTIONS_FOREIGN_EFFECTS -> new Interactions.ForeignEffects((Scopes.MemoryBound)a[0],(Scopes.MemoryBound)a[1],(java.util.List<Ids.OperandId>)a[2]);
            case INTERACTIONS_INTERNAL_TARGET -> new Interactions.InternalTarget((Ids.EntryId)a[0]);
            case INTERACTIONS_KNOWN_CONTRACT -> new Interactions.KnownContract((Interactions.ContractRef)a[0]);
            case INTERACTIONS_KNOWN_MODE -> new Interactions.KnownMode((Interactions.PassingMode)a[0]);
            case INTERACTIONS_LITERAL_TARGET -> new Interactions.LiteralTarget((java.lang.String)a[0],(java.lang.String)a[1],(java.lang.String)a[2],(Interactions.NamePolicy)a[3],(Ids.OriginId)a[4]);
            case INTERACTIONS_LOCAL_RESOURCE -> new Interactions.LocalResource((java.lang.String)a[0]);
            case INTERACTIONS_NO_REMAINDER -> Interactions.NoRemainder.values()[Math.toIntExact(scalar)];
            case INTERACTIONS_OBJECT_BINDING -> new Interactions.ObjectBinding((Ids.ObjectId)a[0]);
            case INTERACTIONS_OUTCOME_EFFECTS -> new Interactions.OutcomeEffects((Control.OutcomeKey)a[0],(Interactions.ForeignEffects)a[1]);
            case INTERACTIONS_PARAMETER -> new Interactions.Parameter((java.math.BigInteger)a[0],(Interactions.ModeKnowledge)a[1],(Types.TypeRef)a[2],(Interactions.ParameterBinding)a[3],(Ids.OriginId)a[4]);
            case INTERACTIONS_PARAMETER_INVENTORY -> new Interactions.ParameterInventory((java.util.List<Interactions.Parameter>)a[0],(Interactions.UnknownBound)a[1]);
            case INTERACTIONS_PASSING_MODE -> Interactions.PassingMode.values()[Math.toIntExact(scalar)];
            case INTERACTIONS_REFERENCE_ARGUMENT -> new Interactions.ReferenceArgument((Place)a[0]);
            case INTERACTIONS_RESOURCE -> new Interactions.Resource((Ids.ResourceId)a[0],(Interactions.ResourceDescription)a[1],(Ids.OriginId)a[2],(java.util.Optional<Interactions.ResourceDeclaration>)a[3]);
            case INTERACTIONS_RESOURCE_DECLARATION -> new Interactions.ResourceDeclaration((Ids.UnitId)a[0],(java.lang.String)a[1],(java.lang.String)a[2],(java.lang.String)a[3],(java.util.List<Interactions.ResourceObject>)a[4],(java.util.List<Interactions.ResourceUse>)a[5]);
            case INTERACTIONS_RESOURCE_OBJECT -> new Interactions.ResourceObject((Ids.ObjectId)a[0],(java.lang.String)a[1]);
            case INTERACTIONS_RESOURCE_USE -> new Interactions.ResourceUse((Ids.OperationId)a[0],(java.lang.String)a[1],(Ids.OriginId)a[2]);
            case INTERACTIONS_RESULT_INVENTORY -> new Interactions.ResultInventory((java.util.List<Interactions.ResultSlot>)a[0],(Interactions.UnknownBound)a[1]);
            case INTERACTIONS_RESULT_SLOT -> new Interactions.ResultSlot((java.math.BigInteger)a[0],(Types.TypeRef)a[1],(Ids.OriginId)a[2]);
            case INTERACTIONS_SIGNATURE -> new Interactions.Signature((Interactions.ParameterInventory)a[0],(Interactions.ResultInventory)a[1],(Ids.OriginId)a[2]);
            case INTERACTIONS_UNKNOWN_CONTRACT -> new Interactions.UnknownContract((Ids.UncertaintyId)a[0]);
            case INTERACTIONS_UNKNOWN_MODE -> new Interactions.UnknownMode((Ids.UncertaintyId)a[0]);
            case INTERACTIONS_UNKNOWN_NAME -> new Interactions.UnknownName((Ids.UncertaintyId)a[0]);
            case INTERACTIONS_UNKNOWN_PARAMETER_BINDING -> new Interactions.UnknownParameterBinding((Ids.UncertaintyId)a[0]);
            case INTERACTIONS_UNKNOWN_REMAINDER -> new Interactions.UnknownRemainder((Ids.UncertaintyId)a[0]);
            case INTERACTIONS_UNKNOWN_RESOURCE -> new Interactions.UnknownResource((java.lang.String)a[0],(java.lang.String)a[1],(Ids.UncertaintyId)a[2]);
            case INTERACTIONS_VALUE_ARGUMENT -> new Interactions.ValueArgument((Expression)a[0]);
            case MEMORY_ALIAS_BINDING -> new Memory.AliasBinding((Ids.ObjectId)a[0]);
            case MEMORY_ALTERNATIVES_BINDING -> new Memory.AlternativesBinding((java.util.List<Memory.Binding>)a[0],(Scopes.MemoryBound)a[1]);
            case MEMORY_ASCII_TEXT -> Memory.AsciiText.values()[Math.toIntExact(scalar)];
            case MEMORY_BINARY_CODEC -> new Memory.BinaryCodec((Boolean)a[0],(java.math.BigInteger)a[1],(Memory.ByteOrder)a[2]);
            case MEMORY_BYTE_ORDER -> Memory.ByteOrder.values()[Math.toIntExact(scalar)];
            case MEMORY_BYTE_RANGE -> new Memory.ByteRange((Ids.StorageId)a[0],(Expression)a[1],(Expression)a[2]);
            case MEMORY_CELL -> new Memory.Cell((Memory.StorageHeader)a[0],(Types.TypeRef)a[1]);
            case MEMORY_CELL_BINDING -> new Memory.CellBinding((Ids.StorageId)a[0]);
            case MEMORY_EXTENSION_CODEC -> new Memory.ExtensionCodec((java.lang.String)a[0],(java.lang.String)a[1],(Types.TypeRef)a[2]);
            case MEMORY_IDENTITY_BYTES -> Memory.IdentityBytes.values()[Math.toIntExact(scalar)];
            case MEMORY_LIFETIME -> Memory.Lifetime.values()[Math.toIntExact(scalar)];
            case MEMORY_OBJECT_DECLARATION -> new Memory.ObjectDeclaration((Ids.ObjectId)a[0],(java.util.Optional<java.lang.String>)a[1],(Types.TypeRef)a[2],(Memory.Binding)a[3],(Memory.Visibility)a[4],(Ids.OriginId)a[5],(Evidence.CoverageStatus)a[6],(Evidence.Precision)a[7]);
            case MEMORY_REGION -> new Memory.Region((Memory.StorageHeader)a[0],(java.util.Optional<java.math.BigInteger>)a[1],(java.util.Optional<Ids.UncertaintyId>)a[2]);
            case MEMORY_STORAGE_HEADER -> new Memory.StorageHeader((Ids.StorageId)a[0],(java.util.Optional<Ids.UnitId>)a[1],(Memory.Lifetime)a[2],(Memory.Visibility)a[3],(Ids.OriginId)a[4]);
            case MEMORY_UNKNOWN_BINDING -> new Memory.UnknownBinding((Scopes.MemoryScope)a[0],(Ids.UncertaintyId)a[1]);
            case MEMORY_UNKNOWN_CODEC -> new Memory.UnknownCodec((Types.TypeRef)a[0],(Ids.UncertaintyId)a[1]);
            case MEMORY_VIEW_BINDING -> new Memory.ViewBinding((Ids.StorageId)a[0],(java.math.BigInteger)a[1],(java.math.BigInteger)a[2],(Memory.Codec)a[3]);
            case MEMORY_VISIBILITY -> Memory.Visibility.values()[Math.toIntExact(scalar)];
            case OPERAND_HEADER -> new Operand.Header((Ids.OperandId)a[0],(Operand.Role)a[1],(Ids.OriginId)a[2]);
            case OPERAND_ROLE -> Operand.Role.values()[Math.toIntExact(scalar)];
            case OPERATIONS_ASSIGN -> new Operations.Assign((Operations.Header)a[0],(Place)a[1],(Expression)a[2]);
            case OPERATIONS_BRANCH -> new Operations.Branch((Operations.Header)a[0],(Expression)a[1],(Ids.LabelId)a[2],(Ids.LabelId)a[3]);
            case OPERATIONS_CASE -> new Operations.Case((Values.LiteralValue)a[0],(Ids.LabelId)a[1]);
            case OPERATIONS_COPY_BYTES -> new Operations.CopyBytes((Operations.Header)a[0],(Memory.ByteRange)a[1],(Memory.ByteRange)a[2],(java.math.BigInteger)a[3],(Envelopes.Envelope)a[4]);
            case OPERATIONS_DISPATCH -> new Operations.Dispatch((Operations.Header)a[0],(Expression)a[1],(java.util.List<Operations.Case>)a[2],(Ids.LabelId)a[3]);
            case OPERATIONS_HALT -> new Operations.Halt((Operations.Header)a[0],(Operations.HaltKind)a[1]);
            case OPERATIONS_HALT_KIND -> Operations.HaltKind.values()[Math.toIntExact(scalar)];
            case OPERATIONS_HAVOC_MAY -> new Operations.HavocMay((Operations.Header)a[0],(Scopes.MemoryScope)a[1],(Ids.UncertaintyId)a[2]);
            case OPERATIONS_HAVOC_MUST -> new Operations.HavocMust((Operations.Header)a[0],(Place)a[1],(Ids.UncertaintyId)a[2]);
            case OPERATIONS_HEADER -> new Operations.Header((Ids.OperationId)a[0],(Ids.OriginId)a[1],(Evidence.CoverageStatus)a[2],(Evidence.Precision)a[3],(java.util.List<Ids.UncertaintyId>)a[4]);
            case OPERATIONS_INDIRECT_JUMP -> new Operations.IndirectJump((Operations.Header)a[0],(Expression)a[1],(Types.LabelType)a[2],(Envelopes.Envelope)a[3]);
            case OPERATIONS_INVOKE -> new Operations.Invoke((Operations.Header)a[0],(java.lang.String)a[1],(Interactions.Target)a[2],(java.util.List<Interactions.Argument>)a[3],(java.util.List<Place>)a[4],(Interactions.InvocationSignature)a[5],(java.util.List<Place>)a[6],(Interactions.EffectBound)a[7],(Control.InvocationOutcomes)a[8],(Interactions.ContractKnowledge)a[9]);
            case OPERATIONS_JUMP -> new Operations.Jump((Operations.Header)a[0],(Ids.LabelId)a[1]);
            case OPERATIONS_LOCAL_BOUNDARY -> new Operations.LocalBoundary((Operations.Header)a[0],(Ids.CompletionPortId)a[1],(Ids.LabelId)a[2],(Envelopes.Envelope)a[3],(java.util.Optional<java.lang.String>)a[4]);
            case OPERATIONS_LOCAL_INVOKE -> new Operations.LocalInvoke((Operations.Header)a[0],(Ids.LabelId)a[1],(java.util.List<Ids.CompletionPortId>)a[2],(Ids.LabelId)a[3],(Envelopes.Envelope)a[4],(java.util.Optional<Operations.ReentryGuard>)a[5],(java.util.List<Operations.ResumeRoute>)a[6]);
            case OPERATIONS_LOCAL_RESUME -> new Operations.LocalResume((Operations.Header)a[0],(Envelopes.Envelope)a[1],(java.util.Optional<java.lang.String>)a[2]);
            case OPERATIONS_LOCAL_UNWIND -> new Operations.LocalUnwind((Operations.Header)a[0],(java.math.BigInteger)a[1],(Ids.LabelId)a[2],(Envelopes.Envelope)a[3],(Boolean)a[4]);
            case OPERATIONS_NOP -> new Operations.Nop((Operations.Header)a[0]);
            case OPERATIONS_OPAQUE -> new Operations.Opaque((Operations.Header)a[0],(java.lang.String)a[1],(java.util.List<Operand>)a[2],(java.util.List<Ids.OperandId>)a[3],(Envelopes.Envelope)a[4]);
            case OPERATIONS_RAISE -> new Operations.Raise((Operations.Header)a[0],(java.lang.String)a[1],(java.util.List<Expression>)a[2]);
            case OPERATIONS_REENTRY_GUARD -> new Operations.ReentryGuard((java.lang.String)a[0],(Ids.LabelId)a[1]);
            case OPERATIONS_RESUME_ROUTE -> new Operations.ResumeRoute((java.lang.String)a[0],(Ids.LabelId)a[1]);
            case OPERATIONS_RETURN -> new Operations.Return((Operations.Header)a[0],(java.util.List<Expression>)a[1]);
            case ORIGINS_ARTIFACT -> new Origins.Artifact((Ids.ArtifactId)a[0],(java.lang.String)a[1],(java.util.Optional<java.lang.String>)a[2]);
            case ORIGINS_COLUMN_UNIT -> Origins.ColumnUnit.values()[Math.toIntExact(scalar)];
            case ORIGINS_CONTRACTUAL -> new Origins.Contractual((Ids.OriginId)a[0],(java.lang.String)a[1],(java.lang.String)a[2]);
            case ORIGINS_DERIVED -> new Origins.Derived((Ids.OriginId)a[0],(java.util.List<Ids.OriginId>)a[1],(java.lang.String)a[2]);
            case ORIGINS_INCLUDE_FRAME -> new Origins.IncludeFrame((Ids.ArtifactId)a[0],(Ids.ArtifactId)a[1],(java.lang.String)a[2],(java.util.Optional<Origins.Location>)a[3]);
            case ORIGINS_LINE_COLUMNS -> new Origins.LineColumns((Origins.Span)a[0]);
            case ORIGINS_OFFSETS -> new Origins.Offsets((java.math.BigInteger)a[0],(java.math.BigInteger)a[1],(java.lang.String)a[2],(Boolean)a[3]);
            case ORIGINS_POSITION -> new Origins.Position((java.math.BigInteger)a[0],(java.math.BigInteger)a[1]);
            case ORIGINS_SPAN -> new Origins.Span((Origins.Position)a[0],(Origins.Position)a[1],(java.math.BigInteger)a[2],(java.math.BigInteger)a[3],(Origins.ColumnUnit)a[4],(Boolean)a[5]);
            case ORIGINS_UNAVAILABLE -> new Origins.Unavailable((Ids.OriginId)a[0],(java.lang.String)a[1]);
            case ORIGINS_WRITTEN -> new Origins.Written((Ids.OriginId)a[0],(Ids.ArtifactId)a[1],(java.util.Optional<Origins.Location>)a[2],(java.util.List<Origins.IncludeFrame>)a[3],(Boolean)a[4]);
            case PLACES_CHOICE -> new Places.Choice((Operand.Header)a[0],(java.util.List<Place>)a[1],(Scopes.MemoryBound)a[2],(Types.TypeRef)a[3]);
            case PLACES_OBJECT_PLACE -> new Places.ObjectPlace((Operand.Header)a[0],(Ids.ObjectId)a[1]);
            case PLACES_REGION_SLICE -> new Places.RegionSlice((Operand.Header)a[0],(Ids.StorageId)a[1],(Expression)a[2],(Expression)a[3],(Memory.Codec)a[4],(Types.TypeRef)a[5]);
            case PROOFS_CALL_PARAMETER_DOMAIN -> new Proofs.CallParameterDomain((Ids.OperationId)a[0],(Ids.EntryId)a[1],(java.math.BigInteger)a[2]);
            case PROOFS_CALL_RESULT_DOMAIN -> new Proofs.CallResultDomain((Ids.OperationId)a[0],(Ids.EntryId)a[1],(java.math.BigInteger)a[2]);
            case PROOFS_CELL_DOMAIN -> new Proofs.CellDomain((Ids.StorageId)a[0]);
            case PROOFS_DISJOINT_STORAGE -> new Proofs.DisjointStorage((java.util.List<Ids.StorageId>)a[0]);
            case PROOFS_ENTRY_DOMAIN -> new Proofs.EntryDomain((Ids.EntryId)a[0]);
            case PROOFS_EXTERNAL_PARAMETER_DOMAIN -> new Proofs.ExternalParameterDomain((Ids.OperationId)a[0],(java.math.BigInteger)a[1]);
            case PROOFS_EXTERNAL_RESULT_DOMAIN -> new Proofs.ExternalResultDomain((Ids.OperationId)a[0],(java.math.BigInteger)a[1]);
            case PROOFS_INTERSECTION -> new Proofs.Intersection((Proofs.DomainProofScope)a[0],(Proofs.DomainProofScope)a[1]);
            case PROOFS_INVOCATION_DOMAIN -> new Proofs.InvocationDomain((Ids.OperationId)a[0]);
            case PROOFS_OBJECT_DOMAIN -> new Proofs.ObjectDomain((Ids.ObjectId)a[0]);
            case PROOFS_OPERAND_DOMAIN -> new Proofs.OperandDomain((Ids.OperandId)a[0]);
            case PROOFS_OPERATION_DOMAIN -> new Proofs.OperationDomain((Ids.OperationId)a[0]);
            case PROOFS_PARAMETER_DOMAIN -> new Proofs.ParameterDomain((Ids.EntryId)a[0],(java.math.BigInteger)a[1]);
            case PROOFS_PREMISE -> new Proofs.Premise((Ids.PremiseId)a[0],(java.lang.String)a[1],(java.lang.String)a[2],(Ids.OriginId)a[3],(Proofs.Assertion)a[4]);
            case PROOFS_PUBLICATION_DOMAIN -> Proofs.PublicationDomain.values()[Math.toIntExact(scalar)];
            case PROOFS_RESULT_DOMAIN -> new Proofs.ResultDomain((Ids.EntryId)a[0],(java.math.BigInteger)a[1]);
            case PROOFS_SAME_DOMAIN -> new Proofs.SameDomain((Proofs.DomainSubject)a[0],(Proofs.DomainSubject)a[1],(Proofs.DomainProofScope)a[2]);
            case PROOFS_UNIT_DOMAIN -> new Proofs.UnitDomain((Ids.UnitId)a[0]);
            case SCOPES_ALL_CONTROL -> new Scopes.AllControl((Ids.PublicationId)a[0]);
            case SCOPES_ALL_MEMORY -> new Scopes.AllMemory((Ids.PublicationId)a[0],(Boolean)a[1]);
            case SCOPES_ANY_RESOURCE -> Scopes.AnyResource.values()[Math.toIntExact(scalar)];
            case SCOPES_CONTROL_UNION -> new Scopes.ControlUnion((java.util.List<Scopes.ControlScope>)a[0]);
            case SCOPES_ENTITY_SCOPE -> new Scopes.EntityScope((java.util.List<Ids.Id>)a[0]);
            case SCOPES_LABELS_CONTROL -> new Scopes.LabelsControl((java.util.List<Ids.LabelId>)a[0]);
            case SCOPES_MEMORY_UNION -> new Scopes.MemoryUnion((java.util.List<Scopes.MemoryScope>)a[0]);
            case SCOPES_NO_CONTROL -> Scopes.NoControl.values()[Math.toIntExact(scalar)];
            case SCOPES_NO_MEMORY -> Scopes.NoMemory.values()[Math.toIntExact(scalar)];
            case SCOPES_NO_RESOURCES -> Scopes.NoResources.values()[Math.toIntExact(scalar)];
            case SCOPES_OBJECTS_MEMORY -> new Scopes.ObjectsMemory((java.util.List<Ids.ObjectId>)a[0]);
            case SCOPES_PUBLICATION_SCOPE -> new Scopes.PublicationScope((Ids.PublicationId)a[0]);
            case SCOPES_RESOURCE_CATEGORIES -> new Scopes.ResourceCategories((java.util.List<java.lang.String>)a[0]);
            case SCOPES_STORAGE_MEMORY -> new Scopes.StorageMemory((java.util.List<Ids.StorageId>)a[0]);
            case SCOPES_UNIT_CONTROL -> new Scopes.UnitControl((Ids.UnitId)a[0],(Boolean)a[1],(Boolean)a[2],(Boolean)a[3],(Boolean)a[4],(Boolean)a[5],(Boolean)a[6]);
            case SCOPES_UNIT_SCOPE -> new Scopes.UnitScope((Ids.UnitId)a[0]);
            case SCOPES_VISIBLE_MEMORY -> new Scopes.VisibleMemory((Ids.UnitId)a[0],(Boolean)a[1]);
            case SCOPES_WITHIN_CONTROL -> new Scopes.WithinControl((Scopes.ControlScope)a[0]);
            case SCOPES_WITHIN_MEMORY -> new Scopes.WithinMemory((Scopes.MemoryScope)a[0]);
            case SEMANTIC_VERSION -> new SemanticVersion((java.math.BigInteger)a[0],(java.math.BigInteger)a[1],(java.math.BigInteger)a[2]);
            case TYPES_BUILTIN -> Types.Builtin.values()[Math.toIntExact(scalar)];
            case TYPES_EXTENSION_TYPE -> new Types.ExtensionType((java.lang.String)a[0],(java.lang.String)a[1]);
            case TYPES_KNOWN -> new Types.Known((Types.Type)a[0]);
            case TYPES_LABEL_TYPE -> new Types.LabelType((Ids.UnitId)a[0],(java.util.List<Ids.LabelId>)a[1]);
            case TYPES_UNKNOWN_TYPE -> new Types.UnknownType((Ids.UncertaintyId)a[0]);
            case UNIT_BODY_AVAILABILITY -> Unit.BodyAvailability.values()[Math.toIntExact(scalar)];
            case VALUES_BOOL_VALUE -> new Values.BoolValue((Boolean)a[0]);
            case VALUES_BYTES_VALUE -> new Values.BytesValue((java.util.List<java.lang.Integer>)a[0]);
            case VALUES_DECIMAL_VALUE -> new Values.DecimalValue((java.math.BigInteger)a[0],(java.math.BigInteger)a[1]);
            case VALUES_INT_VALUE -> new Values.IntValue((java.math.BigInteger)a[0]);
            case VALUES_LABEL_VALUE -> new Values.LabelValue((Ids.LabelId)a[0],(Types.LabelType)a[1]);
            case VALUES_TEXT_VALUE -> new Values.TextValue((java.lang.String)a[0]);
            default -> throw new IllegalArgumentException("not a materializable AIR occurrence: "+shape);
        };
    }
}
