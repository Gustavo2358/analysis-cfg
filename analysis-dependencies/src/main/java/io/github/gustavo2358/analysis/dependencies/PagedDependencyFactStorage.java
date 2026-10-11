package io.github.gustavo2358.analysis.dependencies;

import io.github.gustavo2358.air.model.Control;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.analysis.consumers.FactStorage;
import io.github.gustavo2358.analysis.query.ProgramPoint;
import io.github.gustavo2358.analysis.solver.*;
import java.util.*;
import static io.github.gustavo2358.analysis.dependencies.DependencySiteFact.*;

/** Complete closed DependencySiteFact codec in two primitive paged tapes, no JSON
 * bridge, reflection, unbounded resident directory, hash-only or joined identity.
 * This is disposable internal storage, not a wire or admission certificate.
 * Each append preserves every field and list ordinal, including duplicates.
 * Payload costs O(full UTF16 bytes + typed fields + list occurrences); repeated
 * payloads are not interned in this initial codec. Radix-directory costs belong
 * to PagedLongArray. Fixed control is 1024 + two3072-byte tape controls; the
 * shared backend separately funds its cache and external payload/directories.
 * Get explicitly projects one detached fact under a preflight scratch envelope
 * (1024 +64 per encoded cell), released on return. Callers that retain decoded
 * facts are resident compatibility consumers, not managed cursor qualification.
 * Owner expiry/read/write/denial abort retires only these tapes and controls. */
public final class PagedDependencyFactStorage implements FactStorage<DependencySiteFact> {
    private static final AnalysisResources.Phase PHASE=AnalysisResources.Phase.DEMAND;
    private final AnalysisResources resources;
    private AnalysisResources.Reservation control;
    private PagedLongArray offsets,payload;
    private long cells;
    private int count;
    private boolean closed;
    private Transaction active;
    public PagedDependencyFactStorage(PageStore pages,AnalysisResources resources) {
        Objects.requireNonNull(pages);this.resources=Objects.requireNonNull(resources);
        control=resources.reserve(AnalysisResources.Pool.RESIDENT,1024,PHASE);
        try {
            offsets=new PagedLongArray(pages,Long.MAX_VALUE,resources,PHASE);
            payload=new PagedLongArray(pages,Long.MAX_VALUE,resources,PHASE);
            control.onOwnerClose(this::close);
        }catch(RuntimeException|Error failure){abort(failure);throw failure;}
    }
    private void open(){
        if(closed)throw new IllegalStateException("dependency fact storage closed or aborted");
        try{resources.work(1,PHASE);}catch(RuntimeException|Error failure){abort(failure);throw failure;}
    }
    @Override public synchronized int size(){open();return count;}
    @Override public synchronized void append(DependencySiteFact fact) {
        if(active!=null)throw new IllegalStateException("use the exclusive current fact transaction");
        appendFact(fact);
    }
    private void appendFact(DependencySiteFact fact) {
        Objects.requireNonNull(fact);open();
        try {
            int next=Math.incrementExact(count);long begin=cells;
            id(fact.caller());id(fact.entry());id(fact.sequence());id(fact.operation());word(fact.offset());
            id(fact.siteOrigin());id(fact.targetOrigin());text(fact.technology());text(fact.command());text(fact.namespace());text(fact.nameProfile());
            word(fact.targetKind().ordinal());id(fact.subject());point(fact.valuePoint());
            word(fact.reachability().ordinal());word(fact.targetStatus().ordinal());
            word(fact.rawCandidates().size());for(var raw:fact.rawCandidates()){text(raw.rawValue());supports(raw.supports());}
            word(fact.candidates().size());for(var candidate:fact.candidates()){text(candidate.referenceName());text(candidate.rawValue());supports(candidate.supports());}
            word(fact.modelValueRemainder()==null?0:fact.modelValueRemainder()?2:1);
            bool(fact.sourceValueRemainder());bool(fact.interpretationUnknownRemainder());bool(fact.effectiveUnknownRemainder());bool(fact.openControlRemainder());
            ids(fact.evidence());ids(fact.provenance());ids(fact.premises());ids(fact.uncertaintyRefs());
            word(fact.analysisStatus().ordinal());word(fact.analysisReasons().size());for(var reason:fact.analysisReasons())text(reason);
            offsets.set(count*2L,begin);offsets.set(count*2L+1,cells);count=next;
        }catch(RuntimeException|Error failure){abort(failure);throw failure;}
    }
    /** Sequential consumer ranges share these same two tapes. No fixed tape
     * control multiplied by entries/providers. Fund256 bytes per live transaction
     * view and commit/reference bookkeeping before constructing that view. The
     * envelope grows with semantic consumers, not fact/text/support cardinality.
     * Opening the next range seals the previous range against further append.
     * Closing the active failed tail physically clears its payload/directory;
     * closing an earlier range cannot alter another committed range. */
    public synchronized FactStorage<DependencySiteFact> transaction() {
        open();
        try{control.grow(256,PHASE);var next=new Transaction(count);active=next;return next;}
        catch(RuntimeException|Error failure){abort(failure);throw failure;}
    }
    private final class Transaction implements FactStorage<DependencySiteFact> {
        private final int first;
        private int size;
        private boolean retired;
        Transaction(int first){this.first=first;}
        private void available(){open();if(retired)throw new IllegalStateException("fact transaction expired");}
        @Override public void append(DependencySiteFact fact){synchronized(PagedDependencyFactStorage.this){available();if(active!=this)throw new IllegalStateException("fact transaction already sealed");appendFact(fact);size=Math.incrementExact(size);}}
        @Override public int size(){synchronized(PagedDependencyFactStorage.this){available();return size;}}
        @Override public DependencySiteFact get(int ordinal){synchronized(PagedDependencyFactStorage.this){available();Objects.checkIndex(ordinal,size);return PagedDependencyFactStorage.this.get(Math.addExact(first,ordinal));}}
        @Override public void close(){synchronized(PagedDependencyFactStorage.this){if(retired)return;retired=true;if(!closed&&active==this)try{truncate(first);}finally{active=null;}}}
    }
    private void truncate(int first) {
        try {
            long tail=first==count?cells:offsets.get(first*2L);
            for(int i=count-1;i>=first;i--){offsets.set(i*2L+1,0);offsets.set(i*2L,0);}
            for(long i=cells;i>tail;)payload.set(--i,0);
            count=first;cells=tail;
        }catch(RuntimeException|Error failure){abort(failure);throw failure;}
    }
    private void word(long value){payload.set(cells,value);cells=Math.incrementExact(cells);}
    private void bool(boolean value){word(value?1:0);}
    private void text(String value){word(value.length());for(int i=0;i<value.length();i++)word(value.charAt(i));}
    private void ids(List<? extends Id> values){word(values.size());for(var value:values)id(value);}
    private void supports(List<Support> values){word(values.size());for(var value:values){word(value.kind().ordinal());id(value.producer());id(value.origin());ids(value.premises());}}
    /** Explicit tags describe all sealed AIR identities, including both Operand owners. */
    private void id(Id value) {
        if(value==null){word(0);return;}
        switch(value) {
            case PublicationId v->{word(1);text(v.localId());}
            case UnitId v->{word(2);id(v.publication());text(v.localId());}
            case EntryId v->{word(3);id(v.unit());text(v.localId());}
            case LabelId v->{word(4);id(v.unit());text(v.localId());}
            case OperationId v->{word(5);id(v.unit());text(v.localId());}
            case OperandId v->{word(6);switch(v.owner()){case OperationOwner o->{word(1);id(o.operation());}case EntryOwner o->{word(2);id(o.entry());}}text(v.localId());}
            case ObjectId v->{word(7);id(v.unit());text(v.localId());}
            case StorageId v->{word(8);id(v.publication());text(v.localId());}
            case ResourceId v->{word(9);id(v.publication());text(v.localId());}
            case ArtifactId v->{word(10);id(v.publication());text(v.localId());}
            case ArtifactRelationId v->{word(11);id(v.publication());text(v.localId());}
            case OriginId v->{word(12);id(v.publication());text(v.localId());}
            case UncertaintyId v->{word(13);id(v.publication());text(v.localId());}
            case PremiseId v->{word(14);id(v.publication());text(v.localId());}
            case CompletionPortId v->{word(15);id(v.unit());text(v.localId());}
        }
    }
    private void point(ProgramPoint point) {
        if(point==null){word(0);return;}
        word(point.kind().ordinal()+1);id(point.entry());id(point.operation());
        if(point.outcome()==null){word(0);return;}
        switch(point.outcome()) {
            case Control.NormalOutcome ignored->word(1);
            case Control.ExceptionOutcome value->{word(2);text(value.tag());}
            case Control.OtherExceptionOutcome ignored->word(3);
            case Control.HaltOutcome ignored->word(4);
            case Control.DivergeOutcome ignored->word(5);
        }
    }
    @Override public synchronized DependencySiteFact get(int ordinal) {
        open();Objects.checkIndex(ordinal,count);
        try {
            long first=offsets.get(ordinal*2L),end=offsets.get(ordinal*2L+1),length=Math.subtractExact(end,first);
            if(first<0||end>cells||length<=0)throw new IllegalStateException("invalid fact payload range");
            try(var scratch=resources.reserve(AnalysisResources.Pool.SCRATCH,Math.addExact(1024,Math.multiplyExact(length,64)),AnalysisResources.Phase.REPLAY)) {
                if(scratch.amount()==0)throw new IllegalStateException("unfunded fact projection");
                var reader=new Reader(first,end);var fact=reader.fact();if(reader.at!=end)throw new IllegalStateException("fact payload trailing fields");return fact;
            }
        }catch(RuntimeException|Error failure){abort(failure);throw failure;}
    }
    private final class Reader {
        private long at;private final long end;
        Reader(long first,long end){at=first;this.end=end;}
        long word(){if(at>=end)throw new IllegalStateException("truncated fact payload");return payload.get(at++);}
        int integer(){return Math.toIntExact(word());}
        int length(){int value=integer();if(value<0||value>end-at)throw new IllegalStateException("invalid fact list/text extent");return value;}
        boolean bool(){return switch(integer()){case 0->false;case 1->true;default->throw new IllegalStateException("invalid fact boolean");};}
        <T> T tag(T[] values){return tag(values,integer());}
        <T> T tag(T[] values,int tag){if(tag<0||tag>=values.length)throw new IllegalStateException("invalid fact tag");return values[tag];}
        String text(){int size=length();char[] value=new char[size];for(int i=0;i<size;i++){long character=word();if(character<0||character>Character.MAX_VALUE)throw new IllegalStateException("invalid UTF16 cell");value[i]=(char)character;}return new String(value);}
        Id id() {
            return switch(integer()) {
                case 0->null;
                case 1->new PublicationId(text());case 2->new UnitId(id(PublicationId.class),text());
                case 3->new EntryId(id(UnitId.class),text());case 4->new LabelId(id(UnitId.class),text());case 5->new OperationId(id(UnitId.class),text());
                case 6->{OperandOwner owner=switch(integer()){case 1->new OperationOwner(id(OperationId.class));case 2->new EntryOwner(id(EntryId.class));default->throw new IllegalStateException("invalid operand owner");};yield new OperandId(owner,text());}
                case 7->new ObjectId(id(UnitId.class),text());case 8->new StorageId(id(PublicationId.class),text());
                case 9->new ResourceId(id(PublicationId.class),text());case 10->new ArtifactId(id(PublicationId.class),text());
                case 11->new ArtifactRelationId(id(PublicationId.class),text());case 12->new OriginId(id(PublicationId.class),text());
                case 13->new UncertaintyId(id(PublicationId.class),text());case 14->new PremiseId(id(PublicationId.class),text());
                case 15->new CompletionPortId(id(UnitId.class),text());default->throw new IllegalStateException("invalid identity tag");
            };
        }
        <T extends Id> T id(Class<T> type){return type.cast(Objects.requireNonNull(id()));}
        <T extends Id> List<T> ids(Class<T> type){int size=length();var values=new ArrayList<T>(size);for(int i=0;i<size;i++)values.add(id(type));return values;}
        List<Support> supports(){int size=length();var values=new ArrayList<Support>(size);for(int i=0;i<size;i++)values.add(new Support(tag(SupportKind.values()),id(Id.class),id(OriginId.class),ids(PremiseId.class)));return values;}
        ProgramPoint point() {
            int kind=integer();if(kind==0)return null;
            var entry=id(EntryId.class);var operation=(OperationId)id();
            Control.OutcomeKey outcome=switch(integer()){case 0->null;case 1->Control.NormalOutcome.INSTANCE;case 2->new Control.ExceptionOutcome(text());case 3->Control.OtherExceptionOutcome.INSTANCE;case 4->Control.HaltOutcome.INSTANCE;case 5->Control.DivergeOutcome.INSTANCE;default->throw new IllegalStateException("invalid point outcome");};
            return new ProgramPoint(entry,tag(ProgramPoint.Kind.values(),kind-1),operation,outcome);
        }
        DependencySiteFact fact() {
            var caller=id(UnitId.class);var entry=id(EntryId.class);var sequence=id(LabelId.class);var operation=id(OperationId.class);int offset=integer();
            var siteOrigin=id(OriginId.class);var targetOrigin=id(OriginId.class);var technology=text();var command=text();var namespace=text();var profile=text();
            var kind=tag(TargetKind.values());var subject=(ObjectId)id();var point=point();var reach=tag(Reachability.values());var status=tag(TargetStatus.values());
            int rawCount=length();var raw=new ArrayList<RawCandidate>(rawCount);for(int i=0;i<rawCount;i++)raw.add(new RawCandidate(text(),supports()));
            int candidateCount=length();var candidates=new ArrayList<Candidate>(candidateCount);for(int i=0;i<candidateCount;i++)candidates.add(new Candidate(text(),text(),supports()));
            Boolean model=switch(integer()){case 0->null;case 1->Boolean.FALSE;case 2->Boolean.TRUE;default->throw new IllegalStateException("invalid nullable remainder");};
            boolean source=bool(),interpretation=bool(),effective=bool(),controlOpen=bool();
            var evidence=ids(Id.class);var origins=ids(OriginId.class);var premises=ids(PremiseId.class);var uncertainties=ids(UncertaintyId.class);var analysis=tag(AnalysisStatus.values());
            int reasonCount=length();var reasons=new ArrayList<String>(reasonCount);for(int i=0;i<reasonCount;i++)reasons.add(text());
            return new DependencySiteFact(caller,entry,sequence,operation,offset,siteOrigin,targetOrigin,technology,command,namespace,profile,kind,subject,point,
                reach,status,raw,candidates,model,source,interpretation,effective,controlOpen,evidence,origins,premises,uncertainties,analysis,reasons);
        }
    }
    private void abort(Throwable primary){try{close();}catch(RuntimeException|Error cleanup){if(cleanup!=primary)primary.addSuppressed(cleanup);}}
    @Override public synchronized void close() {
        if(closed)return;closed=true;Throwable failure=null;
        for(var owned:new AutoCloseable[]{payload,offsets,control})if(owned!=null)try{owned.close();}
            catch(Exception|Error cleanup){if(failure==null)failure=cleanup;else if(cleanup!=failure)failure.addSuppressed(cleanup);}
        payload=null;offsets=null;control=null;active=null;count=0;cells=0;
        if(failure instanceof RuntimeException runtime)throw runtime;if(failure instanceof Error error)throw error;if(failure!=null)throw new IllegalStateException("fact storage cleanup",failure);
    }
}
