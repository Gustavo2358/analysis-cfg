package io.github.gustavo2358.analysis.dependencies;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.air.validation.*;
import io.github.gustavo2358.analysis.cfg.domain.CfgControl;
import io.github.gustavo2358.analysis.cfg.domain.CfgProgram;
import io.github.gustavo2358.analysis.cfg.domain.CfgSource;
import io.github.gustavo2358.analysis.cfg.domain.CoreCfgProjection;
import io.github.gustavo2358.analysis.cfg.domain.CfgNode;
import io.github.gustavo2358.analysis.cfg.domain.CfgNodeId;
import io.github.gustavo2358.analysis.cfg.domain.CfgNodeInventory;
import io.github.gustavo2358.analysis.cfg.domain.CfgTransition;
import io.github.gustavo2358.analysis.cfg.domain.CfgTransitionTable;
import io.github.gustavo2358.analysis.cfg.domain.ProjectionPolicy;
import io.github.gustavo2358.analysis.structure.ProgramStore;
import io.github.gustavo2358.analysis.solver.AnalysisResources;
import java.math.BigInteger;
import java.util.*;
import java.util.function.Consumer;
import static io.github.gustavo2358.air.model.AirShape.*;

/**
 * Typed, non-resident program view over one completely admitted AIR snapshot.
 * Consumers see program events and detached evidence rather than wire shapes;
 * the snapshot and its paged identity index remain owned by the caller.
 */
public final class SnapshotProgram implements DependencyProgramStore, CfgProgram.AdmittedSnapshot, ProgramStore.Structural {

    private final SnapshotValidator.CheckedSnapshot admission;
    private final AirSnapshot snapshot;
    private final SnapshotIdentityKeys keys;
    private final SnapshotOrderStorage orderStorage;
    private final SnapshotOccurrenceReader occurrences;
    private final AnalysisResources runtime;
    private final char[] orderLeft=new char[64],orderRight=new char[64];
    private CfgSource source;
    private SnapshotOrderStorage.Tape unitOrder;
    private final EnumMap<AirShape,SnapshotOrderStorage.Tape> metadataOrders=new EnumMap<>(AirShape.class);
    private List<Capabilities.Capability> requiredCapabilities;
    private NativeDeclarations declarationInventory;
    private NativeStorages storageInventory;
    private Set<Capabilities.Capability> namePolicyExtensions;
    private final EnumMap<ProjectionPolicy,SnapshotNodes> nodeStores=new EnumMap<>(ProjectionPolicy.class);
    private boolean closed;

    public SnapshotProgram(SnapshotValidator.CheckedSnapshot checked,SnapshotIdentityKeys.Storage identityStorage) {
        this(checked,identityStorage,SnapshotOrderStorage.resident());
    }
    public SnapshotProgram(SnapshotValidator.CheckedSnapshot checked,SnapshotIdentityKeys.Storage identityStorage,SnapshotOrderStorage orderStorage) {
        this(checked,identityStorage,orderStorage,null);
    }
    /** Explicit common runtime of the production input, analysis and output lifetime. */
    public SnapshotProgram(SnapshotValidator.CheckedSnapshot checked,SnapshotIdentityKeys.Storage identityStorage,SnapshotOrderStorage orderStorage,AnalysisResources runtime) {
        this.runtime=runtime;
        admission=Objects.requireNonNull(checked);snapshot=checked.snapshot();var owned=Objects.requireNonNull(identityStorage);
        occurrences=new SnapshotOccurrenceReader(snapshot,this::borrowedOpen);
        this.orderStorage=Objects.requireNonNull(orderStorage);
        if(checked.result().status()!=ValidationResult.Status.STRUCTURALLY_VALID){owned.close();this.orderStorage.close();throw new IllegalArgumentException("complete snapshot validation required");}
        try{keys=new SnapshotIdentityKeys(snapshot,owned);}
        catch(RuntimeException|Error failure){this.orderStorage.close();throw failure;}
    }

    @Override public SnapshotValidator.CheckedSnapshot admission(){open();admission.snapshot();return admission;}
    @Override public CfgProgram.NodeStore nodes(ProjectionPolicy policy){admission();return nodeStores.computeIfAbsent(Objects.requireNonNull(policy),ignored->new SnapshotNodes()).writer();}
    /** Retire an exported projection without closing the shared AIR/dependency owner. */
    public void releaseCfgProjection(ProjectionPolicy policy){open();var nodes=nodeStores.remove(Objects.requireNonNull(policy));if(nodes!=null)nodes.close();}
    public PublicationId publication(){open();return (PublicationId)id(snapshot.field(snapshot.root(),PUBLICATION,0));}
    @Override public ProgramStore.CoverageView coverage(){borrowedOpen();return new StructuralCoverage(snapshot.field(snapshot.root(),PUBLICATION,9));}
    @Override public PublicationId publicationId(){return publication();}
    @Override public Evidence.InventoryStatus inventory(){borrowedOpen();return inventory(snapshot.field(snapshot.root(),PUBLICATION,9));}
    @Override public CfgSource source(){open();if(source==null)source=cfgSource();return source;}
    @Override public List<Capabilities.Capability> requiredCapabilities(){open();if(requiredCapabilities==null)requiredCapabilities=requiredCapabilityValues();return requiredCapabilities;}
    @Override public Set<Capabilities.Capability> namePolicyExtensions(){open();if(namePolicyExtensions==null)namePolicyExtensions=namePolicyExtensionsFromSnapshot();return namePolicyExtensions;}
    public String textValue(long handle){open();return text(handle);}
    @Override public OperationId operationId(long handle){open();return (OperationId)id(handle);}
    @Override public OriginId originId(long handle){open();return (OriginId)id(handle);}

    private List<Capabilities.Capability> requiredCapabilityValues() {
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
                inventory(),units,requiredCapabilities().stream().filter(CoreCfgProjection::supportsControlCapability).distinct().toList());
    }

    private Evidence.InventoryStatus inventory(long coverage) {
        return Evidence.InventoryStatus.values()[(int)snapshot.scalar(snapshot.field(coverage,EVIDENCE_COVERAGE,0))];
    }

    private void borrowedOpen(){open();admission.snapshot();}
    @Override public void progress(ProgramStore.ExecutionPhase phase) {
        borrowedOpen();Objects.requireNonNull(phase);
        // Older explicitly assembled native stores still check the borrowed
        // backing owner's deadline. Production supplies its common runtime so
        // the actual productive phase is reported, without a physical read.
        if(runtime==null){snapshot.shape(snapshot.root());return;}
        runtime.work(1,switch(phase) {
            case INDEX->AnalysisResources.Phase.INDEX;case DEMAND->AnalysisResources.Phase.DEMAND;
            case CONTROL->AnalysisResources.Phase.CONTROL;case DOMAIN->AnalysisResources.Phase.DOMAIN;
            case REPLAY->AnalysisResources.Phase.REPLAY;case SORT->AnalysisResources.Phase.SORT;
            case ENCODE->AnalysisResources.Phase.ENCODE;
        });
    }
    private long field(long handle,int ordinal){borrowedOpen();return snapshot.field(handle,snapshot.shape(handle),ordinal);}
    private <T> T occurrence(long handle,Class<T> type){return occurrences.read(handle,type);}
    private <T> List<T> borrowed(long list,AirShape type,Class<T> result){
        borrowedOpen();return new ProgramStore.BorrowedList<>(Math.toIntExact(snapshot.size(list)),
            ordinal->occurrence(snapshot.element(list,type,ordinal),result),this::borrowedOpen);
    }
    @Override public SemanticVersion airVersion(){return occurrence(field(snapshot.root(),1),SemanticVersion.class);}
    @Override public Capabilities.Manifest capabilities(){return occurrence(field(snapshot.root(),2),Capabilities.Manifest.class);}
    @Override public List<Memory.Storage> storage(){return borrowed(field(snapshot.root(),5),MEMORY_STORAGE,Memory.Storage.class);}
    @Override public List<Interactions.Resource> resources(){return borrowed(field(snapshot.root(),6),INTERACTIONS_RESOURCE,Interactions.Resource.class);}
    @Override public List<Evidence.Uncertainty> uncertainties(){return borrowed(field(snapshot.root(),10),EVIDENCE_UNCERTAINTY,Evidence.Uncertainty.class);}
    @Override public List<Proofs.Premise> premises(){return borrowed(field(snapshot.root(),11),PROOFS_PREMISE,Proofs.Premise.class);}
    @Override public Optional<ProgramStore.DeclarationInventory> declarationInventory(){
        borrowedOpen();if(declarationInventory==null)declarationInventory=new NativeDeclarations();
        return Optional.of(declarationInventory);
    }
    /** Both ordinal and nominal directories use the existing page owner. No typed
     * identity/address rows survive a cold read; collection projections are explicit. */
    private final class NativeDeclarations extends AbstractMap<ObjectId,Memory.ObjectDeclaration>
            implements ProgramStore.DeclarationInventory,AutoCloseable {
        private SnapshotOrderStorage.Tape sourceOrder,lookupOrder;
        private boolean ended;
        NativeDeclarations(){
            try {
                sourceOrder=orderStorage.tape();
                try(var index=orderStorage.open((a,b)->Long.compare(objectKey(a),objectKey(b)))) {
                    long units=field(snapshot.root(),4);
                    for(long u=0;u<snapshot.size(units);u++) {
                        long objects=field(snapshot.element(units,UNIT,u),2);
                        for(long o=0;o<snapshot.size(objects);o++) {
                            long object=snapshot.element(objects,MEMORY_OBJECT_DECLARATION,o);
                            index.add(object);sourceOrder.append(object);
                        }
                    }
                    lookupOrder=orderStorage.tape();
                    try(var rows=index.cursor()){
                        long previous=0;
                        while(rows.advance()){
                            long handle=rows.handle(),key=objectKey(handle);
                            if(key<=previous)throw new IllegalArgumentException("duplicate canonical Object identity");
                            lookupOrder.append(handle);previous=key;
                        }
                    }
                    if(lookupOrder.size()!=sourceOrder.size())throw new IllegalArgumentException("duplicate Object occurrence");
                }
            } catch(RuntimeException|Error failure){
                try{close();}catch(RuntimeException|Error cleanup){failure.addSuppressed(cleanup);}throw failure;
            }
        }
        private void available(){borrowedOpen();if(ended)throw new IllegalStateException("native declaration inventory is closed");}
        private long objectKey(long source){return keys.key(field(source,0));}
        private ObjectId identity(long source){return occurrence(field(source,0),ObjectId.class);}
        @Override public int size(){available();return Math.toIntExact(sourceOrder.size());}
        @Override public boolean identityAt(int ordinal,ObjectId identity){
            available();Objects.requireNonNull(identity);
            return ordinal>=0&&ordinal<sourceOrder.size()
                &&keys.key(identity)==objectKey(sourceOrder.handle(ordinal));
        }
        private long source(Object key){
            available();if(!(key instanceof ObjectId identity))return 0;
            long wanted=keys.key(identity),low=0,high=lookupOrder.size();
            while(low<high){long middle=low+(high-low)/2,handle=lookupOrder.handle(middle),found=objectKey(handle);
                if(found<wanted)low=middle+1;else if(found>wanted)high=middle;else return handle;}
            return 0;
        }
        @Override public boolean containsKey(Object key){return source(key)!=0;}
        @Override public Memory.ObjectDeclaration get(Object key){
            long source=source(key);if(source==0)return null;
            var value=occurrence(source,Memory.ObjectDeclaration.class);
            if(!value.id().equals(key))throw new IllegalStateException("changed indexed Object identity");return value;
        }
        @Override public Set<ObjectId> keySet(){
            available();return Collections.unmodifiableSet(new AbstractSet<>() {
                @Override public int size(){return NativeDeclarations.this.size();}
                @Override public Iterator<ObjectId> iterator(){available();return new Iterator<>() {
                    private long ordinal;
                    @Override public boolean hasNext(){available();return ordinal<sourceOrder.size();}
                    @Override public ObjectId next(){if(!hasNext())throw new NoSuchElementException();return identity(sourceOrder.handle(ordinal++));}
                };}
            });
        }
        @Override public Set<Entry<ObjectId,Memory.ObjectDeclaration>> entrySet(){
            available();return Collections.unmodifiableSet(new AbstractSet<>() {
                @Override public int size(){return NativeDeclarations.this.size();}
                @Override public Iterator<Entry<ObjectId,Memory.ObjectDeclaration>> iterator(){available();return new Iterator<>() {
                    private long ordinal;
                    @Override public boolean hasNext(){available();return ordinal<sourceOrder.size();}
                    @Override public Entry<ObjectId,Memory.ObjectDeclaration> next(){
                        if(!hasNext())throw new NoSuchElementException();
                        var value=occurrence(sourceOrder.handle(ordinal++),Memory.ObjectDeclaration.class);
                        return new SimpleImmutableEntry<>(value.id(),value);
                    }
                };}
            });
        }
        @Override public void close(){
            if(ended)return;ended=true;Throwable failure=null;
            if(sourceOrder!=null)try{sourceOrder.close();}catch(RuntimeException|Error cleanup){failure=cleanup;}
            if(lookupOrder!=null)try{lookupOrder.close();}catch(RuntimeException|Error cleanup){if(failure==null)failure=cleanup;else if(failure!=cleanup)failure.addSuppressed(cleanup);}
            sourceOrder=lookupOrder=null;
            if(failure instanceof RuntimeException exception)throw exception;if(failure instanceof Error error)throw error;
        }
    }
    @Override public Optional<ProgramStore.StorageInventory> storageInventory(){
        borrowedOpen();if(storageInventory==null)storageInventory=new NativeStorages();return Optional.of(storageInventory);
    }
    /** Source addresses, exact canonical keys and sorted ordinals are all paged.
     * Sort/lookup compare primitive keys, not repeated cold full-text projections. */
    private final class NativeStorages extends AbstractMap<StorageId,Memory.Storage>
            implements ProgramStore.StorageInventory,AutoCloseable {
        private SnapshotOrderStorage.Tape sourceOrder,sourceKeys,lookupOrder;
        private boolean ended;
        NativeStorages(){
            try {
                sourceOrder=orderStorage.tape();sourceKeys=orderStorage.tape();
                try(var index=orderStorage.open((a,b)->Long.compare(sourceKeys.handle(a-1),sourceKeys.handle(b-1)))) {
                    long values=field(snapshot.root(),5);
                    for(long at=0;at<snapshot.size(values);at++){
                        long value=snapshot.element(values,MEMORY_STORAGE,at);
                        sourceOrder.append(value);sourceKeys.append(keys.key(field(field(value,0),0)));index.add(at+1);
                    }
                    lookupOrder=orderStorage.tape();
                    try(var rows=index.cursor()){
                        long previous=0;
                        while(rows.advance()){
                            long ordinal=rows.handle(),key=sourceKeys.handle(ordinal-1);
                            if(key<=previous)throw new IllegalArgumentException("duplicate canonical Storage identity");
                            lookupOrder.append(ordinal);previous=key;
                        }
                    }
                    if(sourceKeys.size()!=sourceOrder.size()||lookupOrder.size()!=sourceOrder.size())
                        throw new IllegalArgumentException("duplicate Storage occurrence");
                }
            }catch(RuntimeException|Error failure){try{close();}catch(RuntimeException|Error cleanup){if(cleanup!=failure)failure.addSuppressed(cleanup);}throw failure;}
        }
        private void available(){borrowedOpen();if(ended)throw new IllegalStateException("native storage inventory is closed");}
        @Override public int size(){available();return Math.toIntExact(sourceOrder.size());}
        @Override public boolean identityAt(int ordinal,StorageId identity){
            available();Objects.requireNonNull(identity);
            return ordinal>=0&&ordinal<sourceOrder.size()&&keys.key(identity)==sourceKeys.handle(ordinal);
        }
        private long source(Object key){
            available();if(!(key instanceof StorageId identity))return 0;
            long wanted=keys.key(identity),low=0,high=lookupOrder.size();
            while(low<high){
                long middle=low+(high-low)/2,ordinal=lookupOrder.handle(middle)-1,found=sourceKeys.handle(ordinal);
                if(found<wanted)low=middle+1;else if(found>wanted)high=middle;else return sourceOrder.handle(ordinal);
            }
            return 0;
        }
        @Override public boolean containsKey(Object key){return source(key)!=0;}
        @Override public Memory.Storage get(Object key){
            long source=source(key);if(source==0)return null;var value=occurrence(source,Memory.Storage.class);
            if(!value.header().id().equals(key))throw new IllegalStateException("changed indexed Storage identity");return value;
        }
        @Override public Set<Entry<StorageId,Memory.Storage>> entrySet(){
            available();return Collections.unmodifiableSet(new AbstractSet<>() {
                @Override public int size(){return NativeStorages.this.size();}
                @Override public Iterator<Entry<StorageId,Memory.Storage>> iterator(){available();return new Iterator<>() {
                    private long ordinal;
                    @Override public boolean hasNext(){available();return ordinal<sourceOrder.size();}
                    @Override public Entry<StorageId,Memory.Storage> next(){
                        if(!hasNext())throw new NoSuchElementException();
                        var value=occurrence(sourceOrder.handle(ordinal++),Memory.Storage.class);
                        return new SimpleImmutableEntry<>(value.header().id(),value);
                    }
                };}
            });
        }
        @Override public void close(){
            if(ended)return;ended=true;Throwable failure=null;
            for(var tape:new SnapshotOrderStorage.Tape[]{sourceOrder,sourceKeys,lookupOrder})if(tape!=null)
                try{tape.close();}catch(RuntimeException|Error cleanup){if(failure==null)failure=cleanup;else if(failure!=cleanup)failure.addSuppressed(cleanup);}
            sourceOrder=sourceKeys=lookupOrder=null;
            if(failure instanceof RuntimeException exception)throw exception;if(failure instanceof Error error)throw error;
        }
    }
    @Override public List<ProgramStore.UnitView> units(){
        long list=field(snapshot.root(),4);
        return new ProgramStore.BorrowedList<>(Math.toIntExact(snapshot.size(list)),
            ordinal->new StructuralUnit(snapshot.element(list,UNIT,ordinal)),this::borrowedOpen);
    }
    private final class StructuralUnit implements ProgramStore.UnitView {
        private final long handle;
        StructuralUnit(long handle){this.handle=handle;}
        @Override public UnitId id(){return occurrence(field(handle,0),UnitId.class);}
        @Override public Optional<UnitId> containingUnit(){return optional(field(handle,1),IDS_UNIT_ID,UnitId.class);}
        @Override public List<Memory.ObjectDeclaration> objects(){return borrowed(field(handle,2),MEMORY_OBJECT_DECLARATION,Memory.ObjectDeclaration.class);}
        @Override public List<ObjectId> visibleObjects(){return borrowed(field(handle,3),IDS_OBJECT_ID,ObjectId.class);}
        @Override public List<Entries.Entry> entries(){return borrowed(field(handle,4),ENTRIES_ENTRY,Entries.Entry.class);}
        @Override public List<ProgramStore.SequenceView> sequences(){
            long list=field(handle,5);
            return new ProgramStore.BorrowedList<>(Math.toIntExact(snapshot.size(list)),
                ordinal->new StructuralSequence(snapshot.element(list,SEQUENCE,ordinal)),()->SnapshotProgram.this.borrowedOpen());
        }
        @Override public List<Entries.CompletionPort> completionPorts(){return borrowed(field(handle,6),ENTRIES_COMPLETION_PORT,Entries.CompletionPort.class);}
        @Override public Unit.BodyAvailability body(){return occurrence(field(handle,7),Unit.BodyAvailability.class);}
        @Override public Optional<UncertaintyId> bodyUnavailable(){return optional(field(handle,8),IDS_UNCERTAINTY_ID,UncertaintyId.class);}
        @Override public ProgramStore.CoverageView coverage(){return new StructuralCoverage(field(handle,9));}
        @Override public OriginId origin(){return occurrence(field(handle,10),OriginId.class);}
    }
    private final class StructuralSequence implements ProgramStore.SequenceView {
        private final long handle;
        StructuralSequence(long handle){this.handle=handle;}
        @Override public LabelId label(){return occurrence(field(handle,0),LabelId.class);}
        @Override public List<Instruction> instructions(){return borrowed(field(handle,1),INSTRUCTION,Instruction.class);}
        @Override public Terminator terminator(){return occurrence(field(handle,2),Terminator.class);}
        @Override public OriginId origin(){return occurrence(field(handle,3),OriginId.class);}
    }
    private final class StructuralCoverage implements ProgramStore.CoverageView {
        private final long handle;
        StructuralCoverage(long handle){this.handle=handle;}
        @Override public Evidence.InventoryStatus inventory(){return occurrence(field(handle,0),Evidence.InventoryStatus.class);}
        @Override public Scopes.FactScope scope(){return occurrence(field(handle,1),Scopes.FactScope.class);}
        @Override public List<Evidence.CoverageItem> items(){return borrowed(field(handle,2),EVIDENCE_COVERAGE_ITEM,Evidence.CoverageItem.class);}
        @Override public List<UncertaintyId> uncertainties(){return borrowed(field(handle,3),IDS_UNCERTAINTY_ID,UncertaintyId.class);}
    }
    private <T> Optional<T> optional(long list,AirShape type,Class<T> result){
        borrowedOpen();return snapshot.size(list)==0?Optional.empty():Optional.of(occurrence(snapshot.element(list,type,0),result));
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
                    Optional.of((LabelId)SnapshotProgram.this.id(snapshot.element(initial,IDS_LABEL_ID,0))),entry));});
        }
        @Override public void sequences(Consumer<CfgProgram.SequenceView> consumer) {
            open();Objects.requireNonNull(consumer);long values=snapshot.field(handle,UNIT,5);
            // Complete admission (I-03) proves every instruction belongs to this Unit.
            // Retain that namespace once per Unit scan, not reconstruct it for each query.
            UnitId owner=id();
            ordered(values,SEQUENCE,(a,b)->compareLocalIds(a,SEQUENCE,0,b,SEQUENCE,0),sequence->{
                    consumer.accept(sequenceView(sequence,owner));
            });
        }
    }

    private CfgProgram.SequenceView sequenceView(long sequence,UnitId owner) {
        long instructions=snapshot.field(sequence,SEQUENCE,1);
        var operations=new CfgProgram.OperationIds(Math.toIntExact(snapshot.size(instructions)),ordinal->{
            long instruction=snapshot.element(instructions,INSTRUCTION,ordinal),header=snapshot.field(instruction,snapshot.shape(instruction),0);
            return new OperationId(owner,text(snapshot.field(snapshot.field(header,OPERATIONS_HEADER,0),IDS_OPERATION_ID,1)));
        },()->{SnapshotProgram.this.open();snapshot.shape(instructions);});
        return new CfgProgram.SequenceView((LabelId)id(snapshot.field(sequence,SEQUENCE,0)),operations,control(snapshot.field(sequence,SEQUENCE,2)),sequence);
    }

    private enum NodeKind { SEQUENCE,ENTRY,NORMAL_EXIT,HALT_EXIT,OUTCOME_EXIT }
    /** Seven native tapes per policy; no per-node/row callback registry or resident group array. */
    private final class SnapshotNodes implements AutoCloseable {
        private final SnapshotOrderStorage.Tape[] tapes=new SnapshotOrderStorage.Tape[7];
        private CfgNodeInventory view;
        private UnitRouting activeRouting;
        private boolean ended,failed;
        private SnapshotNodes() {
            try{for(int i=0;i<tapes.length;i++)tapes[i]=orderStorage.tape();}
            catch(RuntimeException|Error failure){try{close();}catch(RuntimeException|Error cleanup){if(cleanup!=failure)failure.addSuppressed(cleanup);}throw failure;}
        }
        private void available(){admission();if(ended||failed)throw new IllegalStateException("snapshot CFG descriptors are closed or aborted");tapes[0].size();}
        private int count(){available();return Math.toIntExact(tapes[0].size()/3);}
        private NodeKind kind(CfgNode node) {
            if(node instanceof CfgNode.SequenceNode)return NodeKind.SEQUENCE;
            if(node instanceof CfgNode.EntryNode)return NodeKind.ENTRY;
            if(node instanceof CfgNode.NormalExit)return NodeKind.NORMAL_EXIT;
            if(node instanceof CfgNode.HaltExit)return NodeKind.HALT_EXIT;
            if(node instanceof CfgNode.OutcomeExit)return NodeKind.OUTCOME_EXIT;
            throw new IllegalArgumentException("unknown CFG node role");
        }
        private CfgNode read(int ordinal) {
            Objects.checkIndex(ordinal,count());long base=3L*ordinal;
            var role=NodeKind.values()[Math.toIntExact(tapes[0].handle(base)-1)];
            long handle=tapes[0].handle(base+1);int variant=Math.toIntExact(tapes[0].handle(base+2)-1);
            var nodeId=new CfgNodeId(publication(),ordinal);
            if(role==NodeKind.ENTRY||role==NodeKind.NORMAL_EXIT) {
                var entry=(EntryId)id(snapshot.field(handle,ENTRIES_ENTRY,0));
                if(role==NodeKind.NORMAL_EXIT)return new CfgNode.NormalExit(nodeId,entry.publication(),entry.unit(),entry);
                long initial=snapshot.field(handle,ENTRIES_ENTRY,1);
                return new CfgNode.EntryNode(nodeId,entry,snapshot.size(initial)==0?Optional.empty():Optional.of((LabelId)id(snapshot.element(initial,IDS_LABEL_ID,0))));
            }
            if(role==NodeKind.SEQUENCE) {
                var label=(LabelId)id(snapshot.field(handle,SEQUENCE,0));var sequence=sequenceView(handle,label.unit());
                return new CfgNode.SequenceNode(nodeId,sequence.label(),sequence.operations(),sequence.control());
            }
            var control=control(snapshot.field(handle,SEQUENCE,2));
            if(role==NodeKind.HALT_EXIT){var halt=(CfgControl.Halt)control;return new CfgNode.HaltExit(nodeId,halt.operation(),halt.haltKind());}
            var outside=CfgControl.alternatives(control).stream().filter(CoreCfgProjection::outside).distinct().toList();
            return new CfgNode.OutcomeExit(nodeId,control,(Control.InvocationAlternative)outside.get(variant));
        }
        private CfgProgram.NodeStore writer() {
            available();
            return new CfgProgram.NodeStore() {
                private int at;private boolean sealed;
                private void active(){available();if(sealed)throw new IllegalStateException("sealed CFG descriptor writer");}
                @Override public CfgTransitionTable.Storage transitions(){active();return transitionWriter();}
                @Override public CfgProgram.Routing routing(UnitId unit,int first,int end){active();if(end>at)throw new IllegalArgumentException("unwritten Unit sequence range");if(activeRouting!=null)throw new IllegalStateException("Unit routing remains open");activeRouting=new UnitRouting(unit,first,end);return activeRouting;}
                @Override public int size(){active();return at;}
                @Override public CfgNode get(int ordinal){active();Objects.checkIndex(ordinal,at);return read(ordinal);}
                @Override public void append(CfgNode node,long handle,int variant) {
                    active();Objects.requireNonNull(node);
                    if(handle<=0||variant<0||node.id().ordinal()!=at||!node.id().publicationId().equals(publication()))throw new IllegalArgumentException("invalid CFG descriptor");
                    long kind=kind(node).ordinal()+1L;
                    try {
                        if(view!=null) {
                            Objects.checkIndex(at,view.size());long base=3L*at;
                            if(tapes[0].handle(base)!=kind||tapes[0].handle(base+1)!=handle||tapes[0].handle(base+2)!=variant+1L)
                                throw new IllegalArgumentException("repeated projection changed its immutable descriptor");
                        } else {
                            tapes[0].append(kind);tapes[0].append(handle);tapes[0].append(variant+1L);
                            int role=node instanceof CfgNode.EntryNode?1:node instanceof CfgNode.NormalExit?2:node instanceof CfgNode.HaltExit?3:0;
                            if(role!=0)tapes[role].append(at+1L);
                        }
                        at=Math.addExact(at,1);
                    } catch(RuntimeException|Error failure){failed=true;throw failure;}
                }
                @Override public List<CfgNode> seal() {
                    active();if(at!=count())throw new IllegalArgumentException("incomplete repeated CFG projection");
                    if(view==null)view=new CfgNodeInventory(at,SnapshotNodes.this::read,SnapshotNodes.this::available,
                            Math.toIntExact(tapes[1].size()),i->Math.toIntExact(tapes[1].handle(i)-1),
                            Math.toIntExact(tapes[2].size()),i->Math.toIntExact(tapes[2].handle(i)-1),
                            Math.toIntExact(tapes[3].size()),i->Math.toIntExact(tapes[3].handle(i)-1));
                    sealed=true;return view;
                }
            };
        }
        /** Canonical sequence ordinals only; no retained labels, operations or typed node map. */
        private final class UnitRouting implements CfgProgram.Routing {
            private final UnitId unit;
            private final int first,end;
            private final SnapshotOrderStorage.Tape ordinals;
            private boolean closed;
            private UnitRouting(UnitId unit,int first,int end) {
                this.unit=Objects.requireNonNull(unit);this.first=first;this.end=end;
                if(first<0||end<first||end>count())throw new IllegalArgumentException("invalid Unit sequence range");
                ordinals=orderStorage.tape();
                try {
                    long previous=0;
                    for(int at=first;at<end;at++) {
                        long base=3L*at,role=tapes[0].handle(base),handle=tapes[0].handle(base+1);
                        if(role==NodeKind.SEQUENCE.ordinal()+1L) {
                            var label=(LabelId)id(snapshot.field(handle,SEQUENCE,0));
                            if(!label.unit().equals(unit)||previous!=0&&compareLocalIds(previous,SEQUENCE,0,handle,SEQUENCE,0)>=0)
                                throw new IllegalArgumentException("noncanonical Unit sequence index");
                            ordinals.append(at+1L);previous=handle;
                        } else if(previous==0||handle!=previous||role!=NodeKind.HALT_EXIT.ordinal()+1L&&role!=NodeKind.OUTCOME_EXIT.ordinal()+1L)
                            throw new IllegalArgumentException("invalid derived Unit sequence role");
                    }
                } catch(RuntimeException|Error failure){failed=true;try{ordinals.close();}catch(RuntimeException|Error cleanup){if(cleanup!=failure)failure.addSuppressed(cleanup);}throw failure;}
            }
            private void open(){available();if(closed)throw new IllegalStateException("closed Unit routing");ordinals.size();}
            @Override public CfgNodeId sequence(LabelId label) {
                open();Objects.requireNonNull(label);if(!label.unit().equals(unit))throw new IllegalArgumentException("foreign Unit label");
                long low=0,high=ordinals.size();
                while(low<high) {
                    long middle=(low+high)>>>1;int at=Math.toIntExact(ordinals.handle(middle)-1);
                    int order=compareLabel(tapes[0].handle(3L*at+1),label.localId());
                    if(order<0)low=middle+1;else high=middle;
                }
                if(low==ordinals.size())throw new IllegalArgumentException("missing CFG label");
                int at=Math.toIntExact(ordinals.handle(low)-1);
                if(compareLabel(tapes[0].handle(3L*at+1),label.localId())!=0)throw new IllegalArgumentException("missing CFG label");
                return nodeId(at);
            }
            private int compareLabel(long sequence,String query) {
                synchronized(SnapshotProgram.this) {
                long label=snapshot.field(sequence,SEQUENCE,0),text=snapshot.field(label,IDS_LABEL_ID,1);
                long length=snapshot.characterCount(text),limit=Math.min(length,query.length()),offset=0;
                while(offset<limit) {
                    int count=snapshot.readCharacters(text,offset,orderLeft,0,(int)Math.min(orderLeft.length,limit-offset));
                    if(count<=0)throw new IllegalStateException("Unit label comparison made no progress");
                    for(int i=0;i<count;i++){int order=Character.compare(orderLeft[i],query.charAt(Math.toIntExact(offset+i)));if(order!=0)return order;}
                    offset+=count;
                }
                return Long.compare(length,query.length());
                }
            }
            private int ordinal(CfgNode.SequenceNode sequence) {
                open();int at=nodeOrdinal(sequence.id());
                if(at<first||at>=end||tapes[0].handle(3L*at)!=NodeKind.SEQUENCE.ordinal()+1L||!sequence.label().unit().equals(unit)
                        ||compareLabel(tapes[0].handle(3L*at+1),sequence.label().localId())!=0)
                    throw new IllegalArgumentException("foreign Unit sequence");
                return at;
            }
            @Override public CfgNodeId halt(CfgNode.SequenceNode sequence) {
                int at=ordinal(sequence);
                for(int i=at+1;i<end;i++) {
                    long role=tapes[0].handle(3L*i);
                    if(role==NodeKind.SEQUENCE.ordinal()+1L)break;
                    if(role==NodeKind.HALT_EXIT.ordinal()+1L)return nodeId(i);
                }
                throw new IllegalArgumentException("missing CFG Halt exit");
            }
            @Override public void outside(CfgNode.SequenceNode sequence,Consumer<CfgNodeId> consumer) {
                int at=ordinal(sequence);Objects.requireNonNull(consumer);
                for(int i=at+1;i<end;i++) {
                    long role=tapes[0].handle(3L*i);
                    if(role==NodeKind.SEQUENCE.ordinal()+1L)break;
                    if(role==NodeKind.OUTCOME_EXIT.ordinal()+1L)consumer.accept(nodeId(i));
                }
            }
            @Override public void close(){if(closed)return;closed=true;if(activeRouting==this)activeRouting=null;ordinals.close();}
        }
        private EntryId entryId(int ordinal) {
            Objects.checkIndex(ordinal,count());long base=3L*ordinal;
            if(tapes[0].handle(base)!=NodeKind.ENTRY.ordinal()+1L)throw new IllegalArgumentException("activation requires an Entry node");
            return (EntryId)id(snapshot.field(tapes[0].handle(base+1),ENTRIES_ENTRY,0));
        }
        private CfgNodeId nodeId(int ordinal){Objects.checkIndex(ordinal,count());return new CfgNodeId(source().publicationId(),ordinal);}
        private int nodeOrdinal(CfgNodeId node) {
            if(!node.publicationId().equals(source().publicationId()))throw new IllegalArgumentException("foreign CFG transition node");
            return Objects.checkIndex(Math.toIntExact(node.ordinal()),count());
        }
        private int physicalCount(){available();return Math.toIntExact(tapes[4].size()/4);}
        private int groupCount(){available();return Math.toIntExact(tapes[5].size()/6);}
        private int groupValue(int group,int field){Objects.checkIndex(group,groupCount());return Math.toIntExact(tapes[5].handle(6L*group+field)-1);}
        private CfgTransition transition(int ordinal) {
            Objects.checkIndex(ordinal,physicalCount());long base=4L*ordinal;
            return new CfgTransition(nodeId(Math.toIntExact(tapes[4].handle(base)-1)),nodeId(Math.toIntExact(tapes[4].handle(base+1)-1)),
                    CfgTransition.Kind.values()[Math.toIntExact(tapes[4].handle(base+2)-1)],entryId(Math.toIntExact(tapes[4].handle(base+3)-1)));
        }
        private int compareTransitions(long first,long second) {
            long a=4*(first-1),b=4*(second-1);
            for(int field=0;field<4;field++){int order=Long.compare(tapes[4].handle(a+field),tapes[4].handle(b+field));if(order!=0)return order;}
            return 0;
        }
        private CfgTransitionTable.Storage transitionWriter() {
            available();boolean repeated=view!=null;
            return new CfgTransitionTable.Storage() {
                private int atGroup,atRow,atExit,logical;
                private boolean sealed,groupOpen;
                private void active(){available();if(sealed)throw new IllegalStateException("sealed CFG transition writer");}
                private void readable(){available();if(!sealed)throw new IllegalStateException("unsealed CFG transitions");}
                private void word(int tape,long ordinal,long value) {
                    if(repeated){if(tapes[tape].handle(ordinal)!=value)throw new IllegalArgumentException("repeated projection changed its immutable flow descriptor");}
                    else {if(tapes[tape].size()!=ordinal)throw new IllegalStateException("noncontiguous CFG flow descriptor");tapes[tape].append(value);}
                }
                private void row(CfgTransition row,int activation) {
                    if(!row.activationEntry().equals(entryId(activation)))throw new IllegalArgumentException("CFG representative Entry mismatch");
                    int from=nodeOrdinal(row.from()),to=nodeOrdinal(row.to());long base=4L*atRow;
                    word(4,base,from+1L);word(4,base+1,to+1L);word(4,base+2,row.kind().ordinal()+1L);word(4,base+3,activation+1L);
                    atRow=Math.addExact(atRow,1);
                }
                @Override public CfgTransitionTable.GroupWriter begin(UnitId unit) {
                    active();Objects.requireNonNull(unit);if(groupOpen)throw new IllegalStateException("unfinished CFG group");groupOpen=true;
                    return new CfgTransitionTable.GroupWriter() {
                        private final int start=atRow,exitStart=atExit;
                        private int entries,body,representative=-1;
                        private boolean ended,bodyStarted;
                        private void writable(){active();if(ended)throw new IllegalStateException("ended CFG group");}
                        @Override public void binding(CfgTransition binding,CfgNodeId exit) {
                            writable();
                            try {
                                if(bodyStarted)throw new IllegalStateException("Entry after body");
                                int activation=nodeOrdinal(binding.from());
                                if(binding.kind()!=CfgTransition.Kind.ENTRY||!entryId(activation).unit().equals(unit))throw new IllegalArgumentException("factored CFG Unit/Entry mismatch");
                                row(binding,activation);word(6,atExit,nodeOrdinal(exit)+1L);atExit=Math.addExact(atExit,1);
                                if(representative<0)representative=activation;entries=Math.addExact(entries,1);
                            } catch(RuntimeException|Error failure){failed=true;throw failure;}
                        }
                        @Override public void body(CfgTransition transition) {
                            writable();
                            try{if(entries==0)throw new IllegalStateException("body without Entry");bodyStarted=true;row(transition,representative);body=Math.addExact(body,1);}
                            catch(RuntimeException|Error failure){failed=true;throw failure;}
                        }
                        @Override public void end() {
                            writable();
                            try {
                                if(entries!=0) {
                                    logical=Math.toIntExact(Math.addExact((long)logical,Math.multiplyExact((long)entries,Math.addExact(1L,body))));
                                    long base=6L*atGroup;
                                    word(5,base,start+1L);word(5,base+1,entries+1L);word(5,base+2,body+1L);
                                    word(5,base+3,logical+1L);word(5,base+4,atRow+1L);word(5,base+5,exitStart+1L);atGroup=Math.addExact(atGroup,1);
                                }
                                ended=true;groupOpen=false;
                            } catch(RuntimeException|Error failure){failed=true;throw failure;}
                        }
                    };
                }
                @Override public void add(UnitId unit,List<CfgTransition> entries,List<CfgNodeId> exits,List<CfgTransition> body) {
                    active();Objects.requireNonNull(unit);
                    if(entries.isEmpty()||entries.size()!=exits.size())throw new IllegalArgumentException("entry/normal-exit bindings");
                    try {
                        var group=begin(unit);for(int e=0;e<entries.size();e++)group.binding(entries.get(e),exits.get(e));
                        for(var transition:body)group.body(transition);group.end();
                    } catch(RuntimeException|Error failure){failed=true;throw failure;}
                }
                @Override public void seal() {
                    active();if(groupOpen||atGroup!=groupCount()||atRow!=physicalCount()||atExit!=tapes[6].size()){failed=true;throw new IllegalArgumentException("incomplete repeated CFG flow projection");}
                    sealed=true;
                }
                @Override public int groups(){readable();return groupCount();}
                @Override public UnitId unit(int group){return entry(group,0).activationEntry().unit();}
                @Override public int entries(int group){readable();return groupValue(group,1);}
                @Override public CfgTransition entry(int group,int entry){readable();Objects.checkIndex(entry,entries(group));return transition(groupValue(group,0)+entry);}
                @Override public CfgNodeId normalExit(int group,int entry){readable();Objects.checkIndex(entry,entries(group));return nodeId(Math.toIntExact(tapes[6].handle(groupValue(group,5)+entry)-1));}
                @Override public int bodySize(int group){readable();return groupValue(group,2);}
                @Override public CfgTransition body(int group,int row){readable();Objects.checkIndex(row,bodySize(group));return transition(groupValue(group,0)+entries(group)+row);}
                @Override public int logicalEnd(int group){readable();return groupValue(group,3);}
                @Override public int storedSize(){readable();return physicalCount();}
                @Override public CfgTransition stored(int row){readable();return transition(row);}
                @Override public void validateUnique() {
                    readable();
                    // Ordered exact primitive tuples: no resident HashSet or hash-collision assumption.
                    try(var index=orderStorage.open(SnapshotNodes.this::compareTransitions)) {
                        for(int row=0;row<physicalCount();row++) {
                            try{index.add(row+1L);}
                            catch(IllegalArgumentException duplicate){throw new IllegalArgumentException("duplicate CFG transition",duplicate);}
                        }
                    } catch(RuntimeException|Error failure){failed=true;throw failure;}
                }
            };
        }
        @Override public void close() {
            if(ended)return;ended=true;Throwable failure=null;
            if(activeRouting!=null)try{activeRouting.close();}catch(RuntimeException|Error cleanup){failure=cleanup;}
            for(var tape:tapes)if(tape!=null)try{tape.close();}catch(RuntimeException|Error cleanup){if(failure==null)failure=cleanup;else if(failure!=cleanup)failure.addSuppressed(cleanup);}
            if(failure instanceof RuntimeException exception)throw exception;if(failure instanceof Error error)throw error;
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
                long unit=units.value();
                try(var sequences=snapshot.elements(snapshot.field(unit,UNIT,5),SEQUENCE)) {
                    while(sequences.advance()) {
                        long sequence=sequences.value(),operation=snapshot.field(sequence,SEQUENCE,2);
                        if(snapshot.shape(operation)!=OPERATIONS_INVOKE)continue;
                        long target=snapshot.field(operation,OPERATIONS_INVOKE,2);if(snapshot.shape(target)!=INTERACTIONS_COMPUTED_TARGET)continue;
                        long read=snapshot.field(target,INTERACTIONS_COMPUTED_TARGET,2),place=snapshot.field(read,EXPRESSIONS_READ,1),object=snapshot.field(place,PLACES_OBJECT_PLACE,1),header=snapshot.field(operation,OPERATIONS_INVOKE,0);
                        // Admission proves every direct Entry shares this initial label and has
                        // an empty seed/signature. Evaluate body facts once; enumerate only the
                        // required output contexts. The correlated profile still has one Entry.
                        long objectKey=keys.key(object);var caller=(UnitId)id(snapshot.field(unit,UNIT,0));
                        var label=(LabelId)id(snapshot.field(sequence,SEQUENCE,0));var operationId=(OperationId)id(snapshot.field(header,OPERATIONS_HEADER,0));
                        var siteOrigin=(OriginId)id(snapshot.field(header,OPERATIONS_HEADER,1));var targetOrigin=(OriginId)id(snapshot.field(target,INTERACTIONS_COMPUTED_TARGET,4));
                        var coverage=Evidence.CoverageStatus.values()[(int)snapshot.scalar(snapshot.field(header,OPERATIONS_HEADER,2))];
                        var namespace=text(snapshot.field(target,INTERACTIONS_COMPUTED_TARGET,1));var subject=(ObjectId)id(object);
                        try(var entries=snapshot.elements(snapshot.field(unit,UNIT,4),ENTRIES_ENTRY)) {
                            while(entries.advance())consumer.accept(new ComputedCall(objectKey,caller,(EntryId)id(snapshot.field(entries.value(),ENTRIES_ENTRY,0)),
                                    label,operationId,siteOrigin,targetOrigin,coverage,namespace,subject));
                        }
                    }
                }
            }
        }
    }

    public List<Origins.Artifact> artifacts() {return borrowed(field(snapshot.root(),3),ORIGINS_ARTIFACT,Origins.Artifact.class);}
    /** Canonical metadata addresses share the checked program's paged lifetime. */
    public List<Origins.Artifact> orderedArtifacts(){return metadata(3,ORIGINS_ARTIFACT,Origins.Artifact.class);}
    public List<Origins.Origin> orderedOrigins(){return metadata(8,ORIGINS_ORIGIN,Origins.Origin.class);}
    public List<UncertaintyId> orderedUncertaintyRefs(){
        var tape=metadataOrder(10,EVIDENCE_UNCERTAINTY);
        return DependencyResult.borrowedMetadata(tape.size(),ordinal->{long row=tape.handle(ordinal);
            return occurrence(snapshot.field(row,snapshot.shape(row),0),UncertaintyId.class);},this::borrowedOpen);
    }
    private <T> List<T> metadata(int field,AirShape shape,Class<T> type){
        var tape=metadataOrder(field,shape);
        return DependencyResult.borrowedMetadata(tape.size(),ordinal->occurrence(tape.handle(ordinal),type),this::borrowedOpen);
    }
    private SnapshotOrderStorage.Tape metadataOrder(int field,AirShape shape){
        borrowedOpen();var known=metadataOrders.get(shape);if(known!=null)return known;
        progress(ProgramStore.ExecutionPhase.INDEX);
        var tape=orderStorage.tape();
        try {
            ordered(snapshot.field(snapshot.root(),PUBLICATION,field),shape,
                (a,b)->compareLocalIds(a,snapshot.shape(a),0,b,snapshot.shape(b),0),tape::append);
            metadataOrders.put(shape,tape);return tape;
        }catch(RuntimeException|Error failure){try{tape.close();}catch(RuntimeException|Error cleanup){if(cleanup!=failure)failure.addSuppressed(cleanup);}throw failure;}
    }
    @Override public Origins.Origin origin(OriginId id){
        borrowedOpen();Objects.requireNonNull(id);if(!id.publication().equals(publicationId()))return null;
        var tape=metadataOrder(8,ORIGINS_ORIGIN);long low=0,high=tape.size()-1;
        while(low<=high){progress(ProgramStore.ExecutionPhase.INDEX);long middle=low+((high-low)>>>1),row=tape.handle(middle);
            long identity=snapshot.field(row,snapshot.shape(row),0);
            int order=compareTextToString(snapshot.field(identity,IDS_ORIGIN_ID,1),id.localId());
            if(order==0)return materializeOrigin(row);if(order<0)low=middle+1;else high=middle-1;
        }
        return null;
    }
    private int compareTextToString(long text,String value){
        long length=snapshot.characterCount(text),limit=Math.min(length,value.length()),offset=0;
        char[] block=new char[64];
        while(offset<limit){int width=(int)Math.min(block.length,limit-offset),read=snapshot.readCharacters(text,offset,block,0,width);
            if(read<=0)throw new IllegalStateException("metadata identity comparison made no progress");
            for(int i=0;i<read;i++){int order=Character.compare(block[i],value.charAt(Math.toIntExact(offset+i)));if(order!=0)return order;}offset+=read;
        }
        return Long.compare(length,value.length());
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

    public List<Origins.Origin> origins() {return borrowed(field(snapshot.root(),8),ORIGINS_ORIGIN,Origins.Origin.class);}

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
        try{if(declarationInventory!=null)declarationInventory.close();}catch(RuntimeException|Error cleanup){failure=cleanup;}
        try{if(storageInventory!=null)storageInventory.close();}catch(RuntimeException|Error cleanup){if(failure==null)failure=cleanup;else if(failure!=cleanup)failure.addSuppressed(cleanup);}
        for(var nodes:nodeStores.values())try{nodes.close();}catch(RuntimeException|Error cleanup){if(failure==null)failure=cleanup;else if(failure!=cleanup)failure.addSuppressed(cleanup);}
        try{if(unitOrder!=null)unitOrder.close();}catch(RuntimeException|Error cleanup){if(failure==null)failure=cleanup;else if(failure!=cleanup)failure.addSuppressed(cleanup);}
        for(var tape:metadataOrders.values())try{tape.close();}catch(RuntimeException|Error cleanup){if(failure==null)failure=cleanup;else if(failure!=cleanup)failure.addSuppressed(cleanup);}
        metadataOrders.clear();
        try{keys.close();}catch(RuntimeException|Error cleanup){if(failure==null)failure=cleanup;else if(failure!=cleanup)failure.addSuppressed(cleanup);}
        try{orderStorage.close();}catch(RuntimeException|Error cleanup){if(failure==null)failure=cleanup;else if(failure!=cleanup)failure.addSuppressed(cleanup);}
        if(failure instanceof RuntimeException exception)throw exception;if(failure instanceof Error error)throw error;}
}
