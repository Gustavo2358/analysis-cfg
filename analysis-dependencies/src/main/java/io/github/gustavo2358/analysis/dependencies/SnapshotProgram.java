package io.github.gustavo2358.analysis.dependencies;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.air.validation.*;
import io.github.gustavo2358.analysis.cfg.domain.CfgControl;
import io.github.gustavo2358.analysis.cfg.domain.CfgProgram;
import io.github.gustavo2358.analysis.cfg.domain.CfgSource;
import io.github.gustavo2358.analysis.cfg.domain.CoreCfgProjection;
import io.github.gustavo2358.analysis.structure.ProgramStore;
import java.math.BigInteger;
import java.util.*;
import java.util.function.Consumer;
import static io.github.gustavo2358.air.model.AirShape.*;

/**
 * Typed, non-resident program view over one completely admitted AIR snapshot.
 * Consumers see program events and detached evidence rather than wire shapes;
 * the snapshot and its paged identity index remain owned by the caller.
 */
public final class SnapshotProgram implements DependencyProgramStore, CfgProgram {

    private final AirSnapshot snapshot;
    private final SnapshotIdentityKeys keys;
    private final SnapshotOrderStorage orderStorage;
    private final char[] orderLeft=new char[64],orderRight=new char[64];
    private CfgSource source;
    private SnapshotOrderStorage.Tape unitOrder;
    private List<Capabilities.Capability> requiredCapabilities;
    private Set<Capabilities.Capability> namePolicyExtensions;
    private boolean closed;

    public SnapshotProgram(SnapshotValidator.CheckedSnapshot checked,SnapshotIdentityKeys.Storage identityStorage) {
        this(checked,identityStorage,SnapshotOrderStorage.resident());
    }
    public SnapshotProgram(SnapshotValidator.CheckedSnapshot checked,SnapshotIdentityKeys.Storage identityStorage,SnapshotOrderStorage orderStorage) {
        Objects.requireNonNull(checked);snapshot=checked.snapshot();var owned=Objects.requireNonNull(identityStorage);
        this.orderStorage=Objects.requireNonNull(orderStorage);
        if(checked.result().status()!=ValidationResult.Status.STRUCTURALLY_VALID){owned.close();this.orderStorage.close();throw new IllegalArgumentException("complete snapshot validation required");}
        try{keys=new SnapshotIdentityKeys(snapshot,owned);}
        catch(RuntimeException|Error failure){this.orderStorage.close();throw failure;}
    }

    public PublicationId publication(){open();return (PublicationId)id(snapshot.field(snapshot.root(),PUBLICATION,0));}
    public Evidence.InventoryStatus coverage(){open();long coverage=snapshot.field(snapshot.root(),PUBLICATION,9);return Evidence.InventoryStatus.values()[(int)snapshot.scalar(snapshot.field(coverage,EVIDENCE_COVERAGE,0))];}
    @Override public PublicationId publicationId(){return publication();}
    @Override public Evidence.InventoryStatus inventory(){return coverage();}
    @Override public CfgSource source(){open();if(source==null)source=cfgSource();return source;}
    @Override public List<Capabilities.Capability> requiredCapabilities(){open();if(requiredCapabilities==null)requiredCapabilities=capabilities();return requiredCapabilities;}
    @Override public Set<Capabilities.Capability> namePolicyExtensions(){open();if(namePolicyExtensions==null)namePolicyExtensions=namePolicyExtensionsFromSnapshot();return namePolicyExtensions;}
    public String textValue(long handle){open();return text(handle);}
    @Override public OperationId operationId(long handle){open();return (OperationId)id(handle);}
    @Override public OriginId originId(long handle){open();return (OriginId)id(handle);}

    private List<Capabilities.Capability> capabilities() {
        long manifest=snapshot.field(snapshot.root(),PUBLICATION,2),required=snapshot.field(manifest,CAPABILITIES_MANIFEST,0);
        var result=new ArrayList<Capabilities.Capability>();
        try(var rows=snapshot.elements(required,CAPABILITIES_CAPABILITY)) {
            while(rows.advance()) {long row=rows.value();result.add(new Capabilities.Capability(
                    text(snapshot.field(row,CAPABILITIES_CAPABILITY,0)),text(snapshot.field(row,CAPABILITIES_CAPABILITY,1))));}
        }
        return List.copyOf(result);
    }

    private Set<Capabilities.Capability> namePolicyExtensionsFromSnapshot() {
        var result=new HashSet<Capabilities.Capability>();
        try(var units=snapshot.elements(snapshot.field(snapshot.root(),PUBLICATION,4),UNIT)) {
            while(units.advance())try(var sequences=snapshot.elements(snapshot.field(units.value(),UNIT,5),SEQUENCE)) {
                while(sequences.advance()) {
                    long terminator=snapshot.field(sequences.value(),SEQUENCE,2);
                    if(snapshot.shape(terminator)==OPERATIONS_INVOKE)addNamePolicy(result,snapshot.field(terminator,OPERATIONS_INVOKE,2));
                }
            }
        }
        try(var resources=snapshot.elements(snapshot.field(snapshot.root(),PUBLICATION,6),INTERACTIONS_RESOURCE)) {
            while(resources.advance())addNamePolicy(result,snapshot.field(resources.value(),INTERACTIONS_RESOURCE,1));
        }
        return Set.copyOf(result);
    }

    private void addNamePolicy(Set<Capabilities.Capability> result,long target) {
        AirShape shape=snapshot.shape(target);long policy;
        if(shape==INTERACTIONS_LITERAL_TARGET||shape==INTERACTIONS_COMPUTED_TARGET)policy=snapshot.field(target,shape,3);
        else if(shape==INTERACTIONS_COMPUTED_RESOURCE)policy=snapshot.field(target,shape,3);
        else return;
        if(snapshot.shape(policy)==INTERACTIONS_EXTENSION_NAME)result.add(new Capabilities.Capability(
                text(snapshot.field(policy,INTERACTIONS_EXTENSION_NAME,0)),text(snapshot.field(policy,INTERACTIONS_EXTENSION_NAME,1))));
    }

    private CfgSource cfgSource() {
        long root=snapshot.root(),version=snapshot.field(root,PUBLICATION,1);
        var handles=unitOrder();
        var units=new CfgSource.UnitInventories(Math.toIntExact(handles.size()),ordinal->{
            long unit=handles.handle(ordinal);
            return new CfgSource.UnitInventory((UnitId)id(snapshot.field(unit,UNIT,0)),inventory(snapshot.field(unit,UNIT,9)));
        },()->{open();snapshot.shape(root);});
        return new CfgSource(publication(),new SemanticVersion(integer(snapshot.field(version,SEMANTIC_VERSION,0)),
                integer(snapshot.field(version,SEMANTIC_VERSION,1)),integer(snapshot.field(version,SEMANTIC_VERSION,2))),
                coverage(),units,requiredCapabilities().stream().filter(CoreCfgProjection::supportsControlCapability).distinct().toList());
    }

    private Evidence.InventoryStatus inventory(long coverage) {
        return Evidence.InventoryStatus.values()[(int)snapshot.scalar(snapshot.field(coverage,EVIDENCE_COVERAGE,0))];
    }

    @Override public void units(Consumer<CfgProgram.UnitView> consumer) {
        open();Objects.requireNonNull(consumer);
        var handles=unitOrder();long count=handles.size();
        for(long ordinal=0;ordinal<count;ordinal++)consumer.accept(new SnapshotUnit(handles.handle(ordinal)));
    }
    private SnapshotOrderStorage.Tape unitOrder() {
        open();if(unitOrder!=null)return unitOrder;
        var handles=orderStorage.tape();
        try {
            ordered(snapshot.field(snapshot.root(),PUBLICATION,4),UNIT,(a,b)->compareLocalIds(a,UNIT,0,b,UNIT,0),handles::append);
            unitOrder=handles;return handles;
        } catch(RuntimeException|Error failure) {
            try{handles.close();}catch(RuntimeException|Error cleanup){if(cleanup!=failure)failure.addSuppressed(cleanup);}
            throw failure;
        }
    }

    private final class SnapshotUnit implements CfgProgram.UnitView {
        private final long handle;
        private SnapshotUnit(long handle){this.handle=handle;}
        @Override public UnitId id(){return (UnitId)SnapshotProgram.this.id(snapshot.field(handle,UNIT,0));}
        @Override public Unit.BodyAvailability body(){return Unit.BodyAvailability.values()[(int)snapshot.scalar(snapshot.field(handle,UNIT,7))];}
        @Override public Evidence.InventoryStatus inventory(){return SnapshotProgram.this.inventory(snapshot.field(handle,UNIT,9));}
        @Override public void entries(Consumer<CfgProgram.EntryView> consumer) {
            open();Objects.requireNonNull(consumer);long values=snapshot.field(handle,UNIT,4);
            ordered(values,ENTRIES_ENTRY,(a,b)->compareLocalIds(a,ENTRIES_ENTRY,0,b,ENTRIES_ENTRY,0),entry->{long initial=snapshot.field(entry,ENTRIES_ENTRY,1);consumer.accept(new CfgProgram.EntryView(
                    (EntryId)SnapshotProgram.this.id(snapshot.field(entry,ENTRIES_ENTRY,0)),snapshot.size(initial)==0?Optional.empty():
                    Optional.of((LabelId)SnapshotProgram.this.id(snapshot.element(initial,IDS_LABEL_ID,0)))));});
        }
        @Override public void sequences(Consumer<CfgProgram.SequenceView> consumer) {
            open();Objects.requireNonNull(consumer);long values=snapshot.field(handle,UNIT,5);
            // Complete admission (I-03) proves every instruction belongs to this Unit.
            // Retain that namespace once per Unit scan, not reconstruct it for each query.
            UnitId owner=id();
            ordered(values,SEQUENCE,(a,b)->compareLocalIds(a,SEQUENCE,0,b,SEQUENCE,0),sequence->{
                    long instructions=snapshot.field(sequence,SEQUENCE,1);
                    var operations=new CfgProgram.OperationIds(Math.toIntExact(snapshot.size(instructions)),ordinal->{
                        long instruction=snapshot.element(instructions,INSTRUCTION,ordinal),header=snapshot.field(instruction,snapshot.shape(instruction),0);
                        long operation=snapshot.field(header,OPERATIONS_HEADER,0);
                        return new OperationId(owner,text(snapshot.field(operation,IDS_OPERATION_ID,1)));
                    },()->{SnapshotProgram.this.open();snapshot.shape(instructions);});
                    consumer.accept(new CfgProgram.SequenceView((LabelId)SnapshotProgram.this.id(snapshot.field(sequence,SEQUENCE,0)),
                            operations,control(snapshot.field(sequence,SEQUENCE,2))));
            });
        }
    }

    private void ordered(long values,AirShape shape,SnapshotOrderStorage.Order order,java.util.function.LongConsumer consumer) {
        try(var index=orderStorage.open(order)) {
            try(var rows=snapshot.elements(values,shape)){while(rows.advance())index.add(rows.value());}
            try(var cursor=index.cursor()){while(cursor.advance())consumer.accept(cursor.handle());}
        }
    }
    private int compareLocalIds(long first,AirShape firstShape,int firstId,long second,AirShape secondShape,int secondId) {
        long firstValue=snapshot.field(first,firstShape,firstId),secondValue=snapshot.field(second,secondShape,secondId);
        return compareText(snapshot.field(firstValue,snapshot.shape(firstValue),1),snapshot.field(secondValue,snapshot.shape(secondValue),1));
    }
    private synchronized int compareText(long first,long second) {
        long firstLength=snapshot.characterCount(first),secondLength=snapshot.characterCount(second),limit=Math.min(firstLength,secondLength),offset=0;
        while(offset<limit) {
            int width=(int)Math.min(orderLeft.length,limit-offset);
            int left=snapshot.readCharacters(first,offset,orderLeft,0,width),right=snapshot.readCharacters(second,offset,orderRight,0,width);
            if(left<=0||right<=0||left!=right)throw new IllegalStateException("snapshot local-ID comparison made invalid progress");
            for(int i=0;i<left;i++)if(orderLeft[i]!=orderRight[i])return Character.compare(orderLeft[i],orderRight[i]);
            offset+=left;
        }
        return Long.compare(firstLength,secondLength);
    }

    private CfgControl control(long terminator) {
        AirShape shape=snapshot.shape(terminator);long header=snapshot.field(terminator,shape,0);
        OperationId operation=(OperationId)id(snapshot.field(header,OPERATIONS_HEADER,0));
        return switch(shape) {
            case OPERATIONS_JUMP -> new CfgControl.Jump(operation,(LabelId)id(snapshot.field(terminator,shape,1)));
            case OPERATIONS_BRANCH -> new CfgControl.Branch(operation,(LabelId)id(snapshot.field(terminator,shape,2)),(LabelId)id(snapshot.field(terminator,shape,3)));
            case OPERATIONS_RETURN -> new CfgControl.Return(operation);
            case OPERATIONS_HALT -> new CfgControl.Halt(operation,Operations.HaltKind.values()[(int)snapshot.scalar(snapshot.field(terminator,shape,1))]);
            case OPERATIONS_INVOKE -> {long outcomes=snapshot.field(terminator,shape,8);yield new CfgControl.Invoke(operation,invocationAlternatives(snapshot.field(outcomes,CONTROL_INVOCATION_OUTCOMES,0)),controlBound(snapshot.field(outcomes,CONTROL_INVOCATION_OUTCOMES,1)));}
            case OPERATIONS_OPAQUE -> {long envelope=snapshot.field(terminator,shape,4),control=snapshot.field(envelope,ENVELOPES_ENVELOPE,1);yield new CfgControl.Opaque(operation,controlAlternatives(snapshot.field(control,CONTROL_CONTROL_ENVELOPE,0)),controlBound(snapshot.field(control,CONTROL_CONTROL_ENVELOPE,1)));}
            case OPERATIONS_LOCAL_INVOKE -> new CfgControl.LocalInvoke(operation,(LabelId)id(snapshot.field(terminator,shape,1)),completionPorts(snapshot.field(terminator,shape,2)),
                    (LabelId)id(snapshot.field(terminator,shape,3)),reentryGuard(snapshot.field(terminator,shape,5)),resumeRoutes(snapshot.field(terminator,shape,6)));
            case OPERATIONS_LOCAL_BOUNDARY -> new CfgControl.LocalBoundary(operation,(CompletionPortId)id(snapshot.field(terminator,shape,1)),
                    (LabelId)id(snapshot.field(terminator,shape,2)),optionalText(snapshot.field(terminator,shape,4)));
            case OPERATIONS_LOCAL_RESUME -> new CfgControl.LocalResume(operation,optionalText(snapshot.field(terminator,shape,2)));
            case OPERATIONS_LOCAL_UNWIND -> new CfgControl.LocalUnwind(operation,integer(snapshot.field(terminator,shape,1)),
                    (LabelId)id(snapshot.field(terminator,shape,2)),snapshot.scalar(snapshot.field(terminator,shape,4))!=0);
            default -> new CfgControl.Unsupported(operation);
        };
    }

    private List<Control.InvocationAlternative> invocationAlternatives(long list) {
        var result=new ArrayList<Control.InvocationAlternative>();
        try(var rows=snapshot.elements(list,CONTROL_INVOCATION_ALTERNATIVE)){while(rows.advance())result.add((Control.InvocationAlternative)controlAlternative(rows.value()));}
        return List.copyOf(result);
    }
    private List<Control.ControlAlternative> controlAlternatives(long list) {
        var result=new ArrayList<Control.ControlAlternative>();
        try(var rows=snapshot.elements(list,CONTROL_CONTROL_ALTERNATIVE)){while(rows.advance())result.add(controlAlternative(rows.value()));}
        return List.copyOf(result);
    }
    private Control.ControlAlternative controlAlternative(long alternative) {
        return switch(snapshot.shape(alternative)) {
            case CONTROL_NORMAL -> new Control.Normal((LabelId)id(snapshot.field(alternative,CONTROL_NORMAL,0)));
            case CONTROL_JUMP_ALTERNATIVE -> new Control.JumpAlternative((LabelId)id(snapshot.field(alternative,CONTROL_JUMP_ALTERNATIVE,0)));
            case CONTROL_EXCEPTIONAL -> new Control.Exceptional(text(snapshot.field(alternative,CONTROL_EXCEPTIONAL,0)),exceptionDestination(snapshot.field(alternative,CONTROL_EXCEPTIONAL,1)));
            case CONTROL_ANY_EXCEPTION -> new Control.AnyException(exceptionDestination(snapshot.field(alternative,CONTROL_ANY_EXCEPTION,0)));
            case CONTROL_HALT_ALTERNATIVE -> Control.HaltAlternative.INSTANCE;
            case CONTROL_DIVERGE -> Control.Diverge.INSTANCE;
            case CONTROL_RETURN_ALTERNATIVE -> Control.ReturnAlternative.INSTANCE;
            case CONTROL_CONTINUE_ALTERNATIVE -> Control.ContinueAlternative.INSTANCE;
            default -> throw new IllegalStateException("unsupported admitted control alternative "+snapshot.shape(alternative));
        };
    }
    private Control.ExceptionDestination exceptionDestination(long destination) {
        return switch(snapshot.shape(destination)) {
            case CONTROL_HANDLER -> new Control.Handler((LabelId)id(snapshot.field(destination,CONTROL_HANDLER,0)));
            case CONTROL_PROPAGATE -> Control.Propagate.INSTANCE;
            default -> throw new IllegalStateException("unsupported admitted exception destination "+snapshot.shape(destination));
        };
    }
    private Scopes.ControlBound controlBound(long bound) {
        return switch(snapshot.shape(bound)) {
            case SCOPES_NO_CONTROL -> Scopes.NoControl.INSTANCE;
            case SCOPES_WITHIN_CONTROL -> new Scopes.WithinControl(controlScope(snapshot.field(bound,SCOPES_WITHIN_CONTROL,0)));
            default -> throw new IllegalStateException("unsupported admitted control bound "+snapshot.shape(bound));
        };
    }
    private Scopes.ControlScope controlScope(long scope) {
        return switch(snapshot.shape(scope)) {
            case SCOPES_ALL_CONTROL -> new Scopes.AllControl((PublicationId)id(snapshot.field(scope,SCOPES_ALL_CONTROL,0)));
            case SCOPES_LABELS_CONTROL -> new Scopes.LabelsControl(labelIds(snapshot.field(scope,SCOPES_LABELS_CONTROL,0)));
            case SCOPES_UNIT_CONTROL -> new Scopes.UnitControl((UnitId)id(snapshot.field(scope,SCOPES_UNIT_CONTROL,0)),bool(scope,1),bool(scope,2),bool(scope,3),bool(scope,4),bool(scope,5),bool(scope,6));
            case SCOPES_CONTROL_UNION -> {var members=new ArrayList<Scopes.ControlScope>();try(var rows=snapshot.elements(snapshot.field(scope,SCOPES_CONTROL_UNION,0),SCOPES_CONTROL_SCOPE)){while(rows.advance())members.add(controlScope(rows.value()));}yield new Scopes.ControlUnion(members);}
            default -> throw new IllegalStateException("unsupported admitted control scope "+snapshot.shape(scope));
        };
    }
    private boolean bool(long record,int slot){return snapshot.scalar(snapshot.field(record,snapshot.shape(record),slot))!=0;}
    private List<LabelId> labelIds(long list){var result=new ArrayList<LabelId>();try(var rows=snapshot.elements(list,IDS_LABEL_ID)){while(rows.advance())result.add((LabelId)id(rows.value()));}return List.copyOf(result);}
    private List<CompletionPortId> completionPorts(long list){var result=new ArrayList<CompletionPortId>();try(var rows=snapshot.elements(list,IDS_COMPLETION_PORT_ID)){while(rows.advance())result.add((CompletionPortId)id(rows.value()));}return List.copyOf(result);}
    private Optional<CfgControl.ReentryGuard> reentryGuard(long optional){if(snapshot.size(optional)==0)return Optional.empty();long value=snapshot.element(optional,OPERATIONS_REENTRY_GUARD,0);return Optional.of(new CfgControl.ReentryGuard(text(snapshot.field(value,OPERATIONS_REENTRY_GUARD,0)),(LabelId)id(snapshot.field(value,OPERATIONS_REENTRY_GUARD,1))));}
    private List<CfgControl.ResumeRoute> resumeRoutes(long list){var result=new ArrayList<CfgControl.ResumeRoute>();try(var rows=snapshot.elements(list,OPERATIONS_RESUME_ROUTE)){while(rows.advance()){long value=rows.value();result.add(new CfgControl.ResumeRoute(text(snapshot.field(value,OPERATIONS_RESUME_ROUTE,0)),(LabelId)id(snapshot.field(value,OPERATIONS_RESUME_ROUTE,1))));}}return List.copyOf(result);}
    private Optional<String> optionalText(long optional){return snapshot.size(optional)==0?Optional.empty():Optional.of(text(snapshot.element(optional,TEXT,0)));}

    @Override public void definitions(Consumer<Definition> consumer) {
        open();Objects.requireNonNull(consumer);
        try(var units=snapshot.elements(snapshot.field(snapshot.root(),PUBLICATION,4),UNIT)) {
            while(units.advance())try(var sequences=snapshot.elements(snapshot.field(units.value(),UNIT,5),SEQUENCE)) {
                while(sequences.advance())try(var operations=snapshot.elements(snapshot.field(sequences.value(),SEQUENCE,1),INSTRUCTION)) {
                    while(operations.advance()) {
                        long operation=operations.value();if(snapshot.shape(operation)!=OPERATIONS_ASSIGN)continue;
                        long destination=snapshot.field(operation,OPERATIONS_ASSIGN,1),value=snapshot.field(operation,OPERATIONS_ASSIGN,2);
                        if(snapshot.shape(destination)!=PLACES_OBJECT_PLACE||snapshot.shape(value)!=EXPRESSIONS_LITERAL)continue;
                        long literal=snapshot.field(value,EXPRESSIONS_LITERAL,1);if(snapshot.shape(literal)!=VALUES_TEXT_VALUE)continue;
                        long header=snapshot.field(operation,OPERATIONS_ASSIGN,0);
                        consumer.accept(new Definition(keys.key(snapshot.field(destination,PLACES_OBJECT_PLACE,1)),
                            snapshot.field(literal,VALUES_TEXT_VALUE,0),0,0,"",List.of(new Producer(snapshot.field(header,OPERATIONS_HEADER,0),
                            snapshot.field(header,OPERATIONS_HEADER,1)))));
                    }
                }
            }
        }
        correlatedDefinitions(consumer);
    }

    private record Literal(long object,long raw,long producer,long origin) { }
    private record Arm(List<Literal> values,long join) { }
    private void correlatedDefinitions(Consumer<Definition> consumer) {
        long unit=snapshot.element(snapshot.field(snapshot.root(),PUBLICATION,4),UNIT,0),entry=firstEntry(unit),initials=snapshot.field(entry,ENTRIES_ENTRY,1);
        if(snapshot.size(initials)!=1)return;
        long sequences=snapshot.field(unit,UNIT,5),head=sequence(sequences,keys.key(snapshot.element(initials,IDS_LABEL_ID,0)));
        if(head==0)return;long branch=snapshot.field(head,SEQUENCE,2);if(snapshot.shape(branch)!=OPERATIONS_BRANCH)return;
        var left=arm(sequence(sequences,keys.key(snapshot.field(branch,OPERATIONS_BRANCH,2))));
        var right=arm(sequence(sequences,keys.key(snapshot.field(branch,OPERATIONS_BRANCH,3))));
        if(left==null||right==null||left.join()!=right.join())throw new IllegalStateException("admitted correlated diamond is malformed");
        long join=sequence(sequences,left.join()),instructions=snapshot.field(join,SEQUENCE,1),assignment=snapshot.element(instructions,INSTRUCTION,0);
        long destination=snapshot.field(assignment,OPERATIONS_ASSIGN,1),fit=snapshot.field(assignment,OPERATIONS_ASSIGN,2),binary=snapshot.field(fit,EXPRESSIONS_FIT_TEXT,1);
        long first=readObject(snapshot.field(binary,EXPRESSIONS_BINARY,2)),second=readObject(snapshot.field(binary,EXPRESSIONS_BINARY,3));
        int length=new BigInteger(text(snapshot.field(fit,EXPRESSIONS_FIT_TEXT,2))).intValueExact();String pad=text(snapshot.field(fit,EXPRESSIONS_FIT_TEXT,3));
        long header=snapshot.field(assignment,OPERATIONS_ASSIGN,0),object=keys.key(snapshot.field(destination,PLACES_OBJECT_PLACE,1));
        for(var alternative:List.of(left,right)) {
            var a=literal(alternative,first);var b=literal(alternative,second);
            var producers=List.of(new Producer(a.producer(),a.origin()),new Producer(b.producer(),b.origin()),
                new Producer(snapshot.field(header,OPERATIONS_HEADER,0),snapshot.field(header,OPERATIONS_HEADER,1)));
            consumer.accept(new Definition(object,a.raw(),b.raw(),length,pad,producers));
        }
    }
    private Arm arm(long sequence) {
        if(sequence==0)return null;long instructions=snapshot.field(sequence,SEQUENCE,1);var values=new ArrayList<Literal>();
        try(var rows=snapshot.elements(instructions,INSTRUCTION)){while(rows.advance()){
            long assignment=rows.value(),destination=snapshot.field(assignment,OPERATIONS_ASSIGN,1),expression=snapshot.field(assignment,OPERATIONS_ASSIGN,2),literal=snapshot.field(expression,EXPRESSIONS_LITERAL,1),header=snapshot.field(assignment,OPERATIONS_ASSIGN,0);
            values.add(new Literal(keys.key(snapshot.field(destination,PLACES_OBJECT_PLACE,1)),snapshot.field(literal,VALUES_TEXT_VALUE,0),snapshot.field(header,OPERATIONS_HEADER,0),snapshot.field(header,OPERATIONS_HEADER,1)));
        }}
        long jump=snapshot.field(sequence,SEQUENCE,2);return new Arm(List.copyOf(values),keys.key(snapshot.field(jump,OPERATIONS_JUMP,1)));
    }
    private Literal literal(Arm arm,long object){return arm.values().stream().filter(value->value.object()==object).findFirst().orElseThrow(()->new IllegalStateException("admitted correlated source is missing"));}
    private long readObject(long expression){return keys.key(snapshot.field(snapshot.field(expression,EXPRESSIONS_READ,1),PLACES_OBJECT_PLACE,1));}
    private long sequence(long sequences,long label){try(var rows=snapshot.elements(sequences,SEQUENCE)){while(rows.advance()){long sequence=rows.value();if(keys.key(snapshot.field(sequence,SEQUENCE,0))==label)return sequence;}}return 0;}
    @Override public String materialize(Definition definition) {open();return definition.direct()?text(definition.firstText()):fitConcat(definition.firstText(),definition.secondText(),definition.fitLength(),definition.pad());}
    @Override public TextCursor cursorText(Definition definition) {
        open();Objects.requireNonNull(definition);
        if(definition.direct()) {
            long source=definition.firstText(),length=snapshot.characterCount(source);
            return new TextCursor(){private long offset;public int read(char[] target,int start,int maximum){Objects.checkFromIndexSize(start,maximum,target.length);if(maximum==0)return 0;if(offset==length)return -1;int count=snapshot.readCharacters(source,offset,target,start,(int)Math.min(maximum,length-offset));if(count<=0)throw new IllegalStateException("snapshot text cursor made no progress");offset+=count;return count;}};
        }
        return new FittedTextCursor(definition);
    }
    private final class FittedTextCursor implements TextCursor {
        private final Definition definition;private final long firstLength,secondLength;private final char[] scalar=new char[2];
        private long sourceOffset;private boolean second;private int points;private char pending;
        private FittedTextCursor(Definition definition){this.definition=definition;firstLength=snapshot.characterCount(definition.firstText());secondLength=snapshot.characterCount(definition.secondText());definition.pad().codePointAt(0);}
        @Override public int read(char[] target,int start,int maximum) {
            open();Objects.checkFromIndexSize(start,maximum,target.length);if(maximum==0)return 0;int written=0;
            if(pending!=0){target[start+written++]=pending;pending=0;}
            while(written<maximum&&points<definition.fitLength()) {
                int value=nextScalar();if(value<0)value=definition.pad().codePointAt(0);int characters=Character.charCount(value);
                target[start+written++]=characters==1?(char)value:Character.highSurrogate(value);if(characters==2){char low=Character.lowSurrogate(value);if(written<maximum)target[start+written++]=low;else pending=low;}points++;
            }
            return written==0?-1:written;
        }
        private int nextScalar() {
            while(true) {
                long source=second?definition.secondText():definition.firstText(),length=second?secondLength:firstLength;
                if(sourceOffset>=length){if(second)return -1;second=true;sourceOffset=0;continue;}
                int count=snapshot.readCharacters(source,sourceOffset,scalar,0,(int)Math.min(2,length-sourceOffset));if(count<=0)throw new IllegalStateException("snapshot fitted text cursor made no progress");
                int value=Character.codePointAt(scalar,0,count),characters=Character.charCount(value);sourceOffset+=characters;return value;
            }
        }
    }
    private String fitConcat(long first,long second,int length,String pad) {
        if(length<0)throw new IllegalArgumentException("negative text fit length");var result=new StringBuilder();
        int count=appendPrefix(result,first,length);count+=appendPrefix(result,second,length-count);
        int padding=pad.codePointAt(0);for(int i=count;i<length;i++)result.appendCodePoint(padding);return result.toString();
    }
    private int appendPrefix(StringBuilder target,long source,int limit) {
        long length=snapshot.characterCount(source),offset=0;int appended=0;char[] block=new char[1024];
        while(offset<length&&appended<limit) {
            int count=snapshot.readCharacters(source,offset,block,0,(int)Math.min(block.length,length-offset)),at=0;
            while(at<count&&appended<limit) {
                if(Character.isHighSurrogate(block[at])&&at+1==count&&offset+count<length)break;
                int scalar=Character.codePointAt(block,at,count);target.appendCodePoint(scalar);at+=Character.charCount(scalar);appended++;
            }
            if(at==0) {count=snapshot.readCharacters(source,offset,block,0,2);int scalar=Character.codePointAt(block,0,count);target.appendCodePoint(scalar);at=Character.charCount(scalar);appended++;}
            offset+=at;
        }
        return appended;
    }

    @Override public void computedCalls(Consumer<ComputedCall> consumer) {
        open();Objects.requireNonNull(consumer);
        try(var units=snapshot.elements(snapshot.field(snapshot.root(),PUBLICATION,4),UNIT)) {
            while(units.advance()) {
                long unit=units.value(),entry=firstEntry(unit);
                try(var sequences=snapshot.elements(snapshot.field(unit,UNIT,5),SEQUENCE)) {
                    while(sequences.advance()) {
                        long sequence=sequences.value(),operation=snapshot.field(sequence,SEQUENCE,2);
                        if(snapshot.shape(operation)!=OPERATIONS_INVOKE)continue;
                        long target=snapshot.field(operation,OPERATIONS_INVOKE,2);if(snapshot.shape(target)!=INTERACTIONS_COMPUTED_TARGET)continue;
                        long read=snapshot.field(target,INTERACTIONS_COMPUTED_TARGET,2),place=snapshot.field(read,EXPRESSIONS_READ,1),object=snapshot.field(place,PLACES_OBJECT_PLACE,1),header=snapshot.field(operation,OPERATIONS_INVOKE,0);
                        consumer.accept(new ComputedCall(keys.key(object),(UnitId)id(snapshot.field(unit,UNIT,0)),
                            (EntryId)id(snapshot.field(entry,ENTRIES_ENTRY,0)),(LabelId)id(snapshot.field(sequence,SEQUENCE,0)),
                            (OperationId)id(snapshot.field(header,OPERATIONS_HEADER,0)),(OriginId)id(snapshot.field(header,OPERATIONS_HEADER,1)),
                            (OriginId)id(snapshot.field(target,INTERACTIONS_COMPUTED_TARGET,4)),
                            Evidence.CoverageStatus.values()[(int)snapshot.scalar(snapshot.field(header,OPERATIONS_HEADER,2))],
                            text(snapshot.field(target,INTERACTIONS_COMPUTED_TARGET,1)),(ObjectId)id(object)));
                    }
                }
            }
        }
    }

    public List<Origins.Artifact> artifacts() {
        open();var result=new ArrayList<Origins.Artifact>();artifactHandles((handle,ignored)->result.add(materializeArtifact(handle)));return List.copyOf(result);
    }

    @Override public void artifactHandles(MetadataHandleConsumer consumer) {
        open();Objects.requireNonNull(consumer);
        try(var rows=snapshot.elements(snapshot.field(snapshot.root(),PUBLICATION,3),ORIGINS_ARTIFACT)) {
            while(rows.advance()){long row=rows.value();consumer.accept(row,localId(snapshot.field(row,ORIGINS_ARTIFACT,0)));}
        }
    }

    @Override public Origins.Artifact materializeArtifact(long row) {
        open();long digest=snapshot.field(row,ORIGINS_ARTIFACT,2);return new Origins.Artifact(
            (ArtifactId)id(snapshot.field(row,ORIGINS_ARTIFACT,0)),text(snapshot.field(row,ORIGINS_ARTIFACT,1)),
            snapshot.size(digest)==0?Optional.empty():Optional.of(text(snapshot.element(digest,TEXT,0))));
    }

    public List<Origins.Origin> origins() {
        open();var result=new ArrayList<Origins.Origin>();originHandles((handle,ignored)->result.add(materializeOrigin(handle)));return List.copyOf(result);
    }

    @Override public void originHandles(MetadataHandleConsumer consumer) {
        open();Objects.requireNonNull(consumer);
        try(var rows=snapshot.elements(snapshot.field(snapshot.root(),PUBLICATION,8),ORIGINS_ORIGIN)) {
            while(rows.advance()){long row=rows.value(),shapeId=snapshot.field(row,snapshot.shape(row),0);consumer.accept(row,localId(shapeId));}
        }
    }

    @Override public void originInputHandles(long row,MetadataHandleConsumer consumer) {
        open();Objects.requireNonNull(consumer);if(snapshot.shape(row)!=ORIGINS_DERIVED)return;
        try(var inputs=snapshot.elements(snapshot.field(row,ORIGINS_DERIVED,1),IDS_ORIGIN_ID)) {
            while(inputs.advance()){long input=inputs.value();consumer.accept(input,localId(input));}
        }
    }

    @Override public OriginView originView(long row) {
        open();return switch(snapshot.shape(row)) {
            case ORIGINS_UNAVAILABLE -> new OriginView.Unavailable((OriginId)id(snapshot.field(row,ORIGINS_UNAVAILABLE,0)),text(snapshot.field(row,ORIGINS_UNAVAILABLE,1)));
            case ORIGINS_CONTRACTUAL -> new OriginView.Contractual((OriginId)id(snapshot.field(row,ORIGINS_CONTRACTUAL,0)),text(snapshot.field(row,ORIGINS_CONTRACTUAL,1)),text(snapshot.field(row,ORIGINS_CONTRACTUAL,2)));
            case ORIGINS_DERIVED -> new OriginView.Derived((OriginId)id(snapshot.field(row,ORIGINS_DERIVED,0)),text(snapshot.field(row,ORIGINS_DERIVED,2)));
            case ORIGINS_WRITTEN -> {long at=snapshot.field(row,ORIGINS_WRITTEN,2);yield new OriginView.Written((OriginId)id(snapshot.field(row,ORIGINS_WRITTEN,0)),(ArtifactId)id(snapshot.field(row,ORIGINS_WRITTEN,1)),snapshot.size(at)==0?Optional.empty():Optional.of(location(snapshot.element(at,ORIGINS_LOCATION,0))),snapshot.scalar(snapshot.field(row,ORIGINS_WRITTEN,4))!=0);}
            default -> throw new IllegalStateException("unsupported origin shape "+snapshot.shape(row));
        };
    }

    @Override public OriginId materializeOriginInput(long handle){open();return (OriginId)id(handle);}

    @Override public Iterable<Origins.IncludeFrame> cursorOriginIncludes(long row) {
        open();if(snapshot.shape(row)!=ORIGINS_WRITTEN)return List.of();long includes=snapshot.field(row,ORIGINS_WRITTEN,3);
        return ()->{open();var values=snapshot.elements(includes,ORIGINS_INCLUDE_FRAME);return new Iterator<>(){
            private boolean prepared,available,closed;public boolean hasNext(){open();if(closed)return false;if(!prepared){available=values.advance();prepared=true;if(!available){values.close();closed=true;}}return available;}
            public Origins.IncludeFrame next(){if(!hasNext())throw new NoSuchElementException();prepared=false;long value=values.value(),site=snapshot.field(value,ORIGINS_INCLUDE_FRAME,3);return new Origins.IncludeFrame(
                (ArtifactId)id(snapshot.field(value,ORIGINS_INCLUDE_FRAME,0)),(ArtifactId)id(snapshot.field(value,ORIGINS_INCLUDE_FRAME,1)),
                text(snapshot.field(value,ORIGINS_INCLUDE_FRAME,2)),snapshot.size(site)==0?Optional.empty():Optional.of(location(snapshot.element(site,ORIGINS_LOCATION,0))));}
        };};
    }

    @Override public Origins.Origin materializeOrigin(long row) {
        open();return switch(snapshot.shape(row)) {
            case ORIGINS_UNAVAILABLE -> new Origins.Unavailable((OriginId)id(snapshot.field(row,ORIGINS_UNAVAILABLE,0)),text(snapshot.field(row,ORIGINS_UNAVAILABLE,1)));
            case ORIGINS_CONTRACTUAL -> new Origins.Contractual((OriginId)id(snapshot.field(row,ORIGINS_CONTRACTUAL,0)),text(snapshot.field(row,ORIGINS_CONTRACTUAL,1)),text(snapshot.field(row,ORIGINS_CONTRACTUAL,2)));
            case ORIGINS_DERIVED -> new Origins.Derived((OriginId)id(snapshot.field(row,ORIGINS_DERIVED,0)),originIds(snapshot.field(row,ORIGINS_DERIVED,1)),text(snapshot.field(row,ORIGINS_DERIVED,2)));
            case ORIGINS_WRITTEN -> written(row);
            default -> throw new IllegalStateException("unsupported origin shape "+snapshot.shape(row));
        };
    }

    private Origins.Written written(long row) {
        long at=snapshot.field(row,ORIGINS_WRITTEN,2),includes=snapshot.field(row,ORIGINS_WRITTEN,3);var frames=new ArrayList<Origins.IncludeFrame>();
        try(var values=snapshot.elements(includes,ORIGINS_INCLUDE_FRAME)) {
            while(values.advance()) {long value=values.value(),site=snapshot.field(value,ORIGINS_INCLUDE_FRAME,3);frames.add(new Origins.IncludeFrame(
                (ArtifactId)id(snapshot.field(value,ORIGINS_INCLUDE_FRAME,0)),(ArtifactId)id(snapshot.field(value,ORIGINS_INCLUDE_FRAME,1)),
                text(snapshot.field(value,ORIGINS_INCLUDE_FRAME,2)),snapshot.size(site)==0?Optional.empty():Optional.of(location(snapshot.element(site,ORIGINS_LOCATION,0)))));}
        }
        return new Origins.Written((OriginId)id(snapshot.field(row,ORIGINS_WRITTEN,0)),(ArtifactId)id(snapshot.field(row,ORIGINS_WRITTEN,1)),
            snapshot.size(at)==0?Optional.empty():Optional.of(location(snapshot.element(at,ORIGINS_LOCATION,0))),frames,snapshot.scalar(snapshot.field(row,ORIGINS_WRITTEN,4))!=0);
    }

    private Origins.Location location(long row) {
        if(snapshot.shape(row)==ORIGINS_OFFSETS)return new Origins.Offsets(integer(snapshot.field(row,ORIGINS_OFFSETS,0)),integer(snapshot.field(row,ORIGINS_OFFSETS,1)),text(snapshot.field(row,ORIGINS_OFFSETS,2)),snapshot.scalar(snapshot.field(row,ORIGINS_OFFSETS,3))!=0);
        if(snapshot.shape(row)!=ORIGINS_LINE_COLUMNS)throw new IllegalStateException("unsupported origin location");
        long span=snapshot.field(row,ORIGINS_LINE_COLUMNS,0),start=snapshot.field(span,ORIGINS_SPAN,0),end=snapshot.field(span,ORIGINS_SPAN,1);
        return new Origins.LineColumns(new Origins.Span(new Origins.Position(integer(snapshot.field(start,ORIGINS_POSITION,0)),integer(snapshot.field(start,ORIGINS_POSITION,1))),
            new Origins.Position(integer(snapshot.field(end,ORIGINS_POSITION,0)),integer(snapshot.field(end,ORIGINS_POSITION,1))),
            integer(snapshot.field(span,ORIGINS_SPAN,2)),integer(snapshot.field(span,ORIGINS_SPAN,3)),Origins.ColumnUnit.values()[(int)snapshot.scalar(snapshot.field(span,ORIGINS_SPAN,4))],snapshot.scalar(snapshot.field(span,ORIGINS_SPAN,5))!=0));
    }

    private List<OriginId> originIds(long list){var result=new ArrayList<OriginId>();try(var rows=snapshot.elements(list,IDS_ORIGIN_ID)){while(rows.advance())result.add((OriginId)id(rows.value()));}return List.copyOf(result);}
    private String localId(long id){return text(snapshot.field(id,snapshot.shape(id),1));}
    private BigInteger integer(long source){return new BigInteger(text(source));}
    private long firstEntry(long unit){long entries=snapshot.field(unit,UNIT,4);if(snapshot.size(entries)==0)throw new IllegalStateException("direct dependency profile requires an entry");return snapshot.element(entries,ENTRIES_ENTRY,0);}
    private Id id(long source){AirShape shape=snapshot.shape(source);return switch(shape){case IDS_PUBLICATION_ID->new PublicationId(text(snapshot.field(source,shape,0)));case IDS_UNIT_ID->new UnitId((PublicationId)id(snapshot.field(source,shape,0)),text(snapshot.field(source,shape,1)));case IDS_ENTRY_ID->new EntryId((UnitId)id(snapshot.field(source,shape,0)),text(snapshot.field(source,shape,1)));case IDS_LABEL_ID->new LabelId((UnitId)id(snapshot.field(source,shape,0)),text(snapshot.field(source,shape,1)));case IDS_OPERATION_ID->new OperationId((UnitId)id(snapshot.field(source,shape,0)),text(snapshot.field(source,shape,1)));case IDS_COMPLETION_PORT_ID->new CompletionPortId((UnitId)id(snapshot.field(source,shape,0)),text(snapshot.field(source,shape,1)));case IDS_OBJECT_ID->new ObjectId((UnitId)id(snapshot.field(source,shape,0)),text(snapshot.field(source,shape,1)));case IDS_ORIGIN_ID->new OriginId((PublicationId)id(snapshot.field(source,shape,0)),text(snapshot.field(source,shape,1)));case IDS_ARTIFACT_ID->new ArtifactId((PublicationId)id(snapshot.field(source,shape,0)),text(snapshot.field(source,shape,1)));default->throw new IllegalArgumentException("unsupported program id "+shape);};}
    private String text(long source){long length=snapshot.characterCount(source);if(length>Integer.MAX_VALUE)throw new IllegalStateException("program text too large");char[] result=new char[(int)length],block=new char[Math.min(1024,Math.max(1,result.length))];long offset=0;while(offset<length){int count=snapshot.readCharacters(source,offset,block,0,(int)Math.min(block.length,length-offset));System.arraycopy(block,0,result,(int)offset,count);offset+=count;}return new String(result);}
    private void open(){if(closed)throw new IllegalStateException("snapshot program is closed");}
    @Override public void close(){if(closed)return;closed=true;Throwable failure=null;
        try{if(unitOrder!=null)unitOrder.close();}catch(RuntimeException|Error cleanup){failure=cleanup;}
        try{keys.close();}catch(RuntimeException|Error cleanup){if(failure==null)failure=cleanup;else if(failure!=cleanup)failure.addSuppressed(cleanup);}
        try{orderStorage.close();}catch(RuntimeException|Error cleanup){if(failure==null)failure=cleanup;else if(failure!=cleanup)failure.addSuppressed(cleanup);}
        if(failure instanceof RuntimeException exception)throw exception;if(failure instanceof Error error)throw error;}
}
