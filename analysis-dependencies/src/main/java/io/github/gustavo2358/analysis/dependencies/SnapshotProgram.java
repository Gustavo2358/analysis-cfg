package io.github.gustavo2358.analysis.dependencies;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.air.validation.*;
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
public final class SnapshotProgram implements DependencyProgramStore {

    private final AirSnapshot snapshot;
    private final SnapshotIdentityKeys keys;
    private boolean closed;

    public SnapshotProgram(SnapshotValidator.CheckedSnapshot checked,SnapshotIdentityKeys.Storage identityStorage) {
        Objects.requireNonNull(checked);snapshot=checked.snapshot();var owned=Objects.requireNonNull(identityStorage);
        if(checked.result().status()!=ValidationResult.Status.STRUCTURALLY_VALID){owned.close();throw new IllegalArgumentException("complete snapshot validation required");}
        keys=new SnapshotIdentityKeys(snapshot,owned);
    }

    public PublicationId publication(){open();return (PublicationId)id(snapshot.field(snapshot.root(),PUBLICATION,0));}
    public Evidence.InventoryStatus coverage(){open();long coverage=snapshot.field(snapshot.root(),PUBLICATION,9);return Evidence.InventoryStatus.values()[(int)snapshot.scalar(snapshot.field(coverage,EVIDENCE_COVERAGE,0))];}
    @Override public PublicationId publicationId(){return publication();}
    @Override public Evidence.InventoryStatus inventory(){return coverage();}
    public String textValue(long handle){open();return text(handle);}
    @Override public OperationId operationId(long handle){open();return (OperationId)id(handle);}
    @Override public OriginId originId(long handle){open();return (OriginId)id(handle);}

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
    @Override public long materializationBytes(Definition definition) {open();return definition.direct()?Math.multiplyExact(snapshot.characterCount(definition.firstText()),Character.BYTES):Math.multiplyExact((long)definition.fitLength(),2L*Character.BYTES);}
    @Override public String materialize(Definition definition) {open();return definition.direct()?text(definition.firstText()):fitConcat(definition.firstText(),definition.secondText(),definition.fitLength(),definition.pad());}
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
        open();var result=new ArrayList<Origins.Artifact>();
        try(var rows=snapshot.elements(snapshot.field(snapshot.root(),PUBLICATION,3),ORIGINS_ARTIFACT)) {
            while(rows.advance()) {long row=rows.value(),digest=snapshot.field(row,ORIGINS_ARTIFACT,2);result.add(new Origins.Artifact(
                (ArtifactId)id(snapshot.field(row,ORIGINS_ARTIFACT,0)),text(snapshot.field(row,ORIGINS_ARTIFACT,1)),
                snapshot.size(digest)==0?Optional.empty():Optional.of(text(snapshot.element(digest,TEXT,0)))));}
        }
        return List.copyOf(result);
    }

    public List<Origins.Origin> origins() {
        open();var result=new ArrayList<Origins.Origin>();
        try(var rows=snapshot.elements(snapshot.field(snapshot.root(),PUBLICATION,8),ORIGINS_ORIGIN)) {
            while(rows.advance()) {long row=rows.value();result.add(switch(snapshot.shape(row)) {
                case ORIGINS_UNAVAILABLE -> new Origins.Unavailable((OriginId)id(snapshot.field(row,ORIGINS_UNAVAILABLE,0)),text(snapshot.field(row,ORIGINS_UNAVAILABLE,1)));
                case ORIGINS_CONTRACTUAL -> new Origins.Contractual((OriginId)id(snapshot.field(row,ORIGINS_CONTRACTUAL,0)),text(snapshot.field(row,ORIGINS_CONTRACTUAL,1)),text(snapshot.field(row,ORIGINS_CONTRACTUAL,2)));
                case ORIGINS_DERIVED -> new Origins.Derived((OriginId)id(snapshot.field(row,ORIGINS_DERIVED,0)),originIds(snapshot.field(row,ORIGINS_DERIVED,1)),text(snapshot.field(row,ORIGINS_DERIVED,2)));
                case ORIGINS_WRITTEN -> written(row);
                default -> throw new IllegalStateException("unsupported origin shape "+snapshot.shape(row));
            });}
        }
        return List.copyOf(result);
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
    private BigInteger integer(long source){return new BigInteger(text(source));}
    private long firstEntry(long unit){long entries=snapshot.field(unit,UNIT,4);if(snapshot.size(entries)==0)throw new IllegalStateException("direct dependency profile requires an entry");return snapshot.element(entries,ENTRIES_ENTRY,0);}
    private Id id(long source){AirShape shape=snapshot.shape(source);return switch(shape){case IDS_PUBLICATION_ID->new PublicationId(text(snapshot.field(source,shape,0)));case IDS_UNIT_ID->new UnitId((PublicationId)id(snapshot.field(source,shape,0)),text(snapshot.field(source,shape,1)));case IDS_ENTRY_ID->new EntryId((UnitId)id(snapshot.field(source,shape,0)),text(snapshot.field(source,shape,1)));case IDS_LABEL_ID->new LabelId((UnitId)id(snapshot.field(source,shape,0)),text(snapshot.field(source,shape,1)));case IDS_OPERATION_ID->new OperationId((UnitId)id(snapshot.field(source,shape,0)),text(snapshot.field(source,shape,1)));case IDS_OBJECT_ID->new ObjectId((UnitId)id(snapshot.field(source,shape,0)),text(snapshot.field(source,shape,1)));case IDS_ORIGIN_ID->new OriginId((PublicationId)id(snapshot.field(source,shape,0)),text(snapshot.field(source,shape,1)));case IDS_ARTIFACT_ID->new ArtifactId((PublicationId)id(snapshot.field(source,shape,0)),text(snapshot.field(source,shape,1)));default->throw new IllegalArgumentException("unsupported program id "+shape);};}
    private String text(long source){long length=snapshot.characterCount(source);if(length>Integer.MAX_VALUE)throw new IllegalStateException("program text too large");char[] result=new char[(int)length],block=new char[Math.min(1024,Math.max(1,result.length))];long offset=0;while(offset<length){int count=snapshot.readCharacters(source,offset,block,0,(int)Math.min(block.length,length-offset));System.arraycopy(block,0,result,(int)offset,count);offset+=count;}return new String(result);}
    private void open(){if(closed)throw new IllegalStateException("snapshot program is closed");}
    @Override public void close(){if(closed)return;closed=true;keys.close();}
}
