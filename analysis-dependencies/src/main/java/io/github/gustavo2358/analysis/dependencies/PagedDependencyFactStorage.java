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
 * Project funds one complete fact under a preflight scratch envelope
 * (1024 +64 per encoded cell), held until its scoped projection closes. Get is
 * explicit detached compatibility and releases that envelope on return. Callers
 * retaining detached facts are not managed cursor qualification. A complete fact
 * can also project a complete typed support leaf through paged inline directories.
 * SupportCursor reads indexed premises and full identities in caller-owned text
 * blocks with fixed funded carriers. Complete fact/program projections remain
 * compatibility; their cardinality is not bounded by this leaf read API.
 * Owner expiry/read/write/denial abort retires only these tapes and controls. */
public final class PagedDependencyFactStorage implements FactStorage<DependencySiteFact> {
    private static final AnalysisResources.Phase PHASE=AnalysisResources.Phase.DEMAND;
    // First eight columns retain their original meaning. The remaining fixed
    // field anchors are paged, not resident objects or separate field tapes.
    private static final int STRIDE=28;
    private final AnalysisResources resources;
    private AnalysisResources.Reservation control;
    private PagedLongArray offsets,payload;
    private long cells;
    private int count;
    private boolean closed;
    private Transaction active;
    private Projection projections;
    private SupportProjection supportProjections;
    private SupportCursor supportCursors;
    private ReadScope readScopes;
    private Runnable ownerClose;
    synchronized void onClose(Runnable cleanup){Objects.requireNonNull(cleanup);if(ownerClose!=null)throw new IllegalStateException("fact storage owner already registered");if(closed)cleanup.run();else ownerClose=cleanup;}
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
        appendFact(fact,0);
    }
    /** Optional owner-local scalar metadata; never a published identity or DTO. */
    synchronized void append(DependencySiteFact fact,long metadata) {
        if(active!=null)throw new IllegalStateException("use the exclusive current fact transaction");
        appendFact(fact,metadata);
    }
    synchronized long ownerMetadata(int ordinal){open();Objects.checkIndex(ordinal,count);try{return offsets.get(ordinal*(long)STRIDE+27);}catch(RuntimeException|Error failure){abort(failure);throw failure;}}
    private void appendFact(DependencySiteFact fact,long metadata) {
        Objects.requireNonNull(fact);open();
        try {
            int next=Math.incrementExact(count);long begin=cells,row=count*(long)STRIDE;
            offsets.set(row+8,cells);id(fact.caller());offsets.set(row+9,cells);id(fact.entry());
            offsets.set(row+10,cells);id(fact.sequence());offsets.set(row+11,cells);id(fact.operation());long keyEnd=cells;
            offsets.set(row+12,cells);word(fact.offset());offsets.set(row+13,cells);id(fact.siteOrigin());offsets.set(row+14,cells);id(fact.targetOrigin());
            offsets.set(row+15,cells);text(fact.technology());offsets.set(row+16,cells);text(fact.command());
            offsets.set(row+17,cells);text(fact.namespace());offsets.set(row+18,cells);text(fact.nameProfile());
            long kind=cells;word(fact.targetKind().ordinal());offsets.set(row+19,cells);id(fact.subject());offsets.set(row+20,cells);point(fact.valuePoint());
            long reach=cells;word(fact.reachability().ordinal());word(fact.targetStatus().ordinal());
            offsets.set(row+21,cells);indexed(fact.rawCandidates().size(),i->{var raw=fact.rawCandidates().get(i);text(raw.rawValue());supports(raw.supports());});
            long candidates=cells;indexed(fact.candidates().size(),i->{var candidate=fact.candidates().get(i);text(candidate.referenceName());text(candidate.rawValue());supports(candidate.supports());});
            long model=cells;word(fact.modelValueRemainder()==null?0:fact.modelValueRemainder()?2:1);
            bool(fact.sourceValueRemainder());bool(fact.interpretationUnknownRemainder());bool(fact.effectiveUnknownRemainder());bool(fact.openControlRemainder());
            offsets.set(row+22,cells);ids(fact.evidence());offsets.set(row+23,cells);ids(fact.provenance());
            offsets.set(row+24,cells);ids(fact.premises());offsets.set(row+25,cells);ids(fact.uncertaintyRefs());
            long analysis=cells;word(fact.analysisStatus().ordinal());offsets.set(row+26,cells);indexed(fact.analysisReasons().size(),i->text(fact.analysisReasons().get(i)));
            offsets.set(row,begin);offsets.set(row+1,cells);offsets.set(row+2,keyEnd);
            offsets.set(row+3,kind);offsets.set(row+4,reach);offsets.set(row+5,candidates);offsets.set(row+6,analysis);offsets.set(row+7,model);offsets.set(row+27,metadata);count=next;
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
        return transaction(null);
    }
    /** Callback borrows the producer's existing typed fact only during append.
     * Its scalar value shares the same row/tail destruction as every other column. */
    synchronized FactStorage<DependencySiteFact> transaction(java.util.function.ToLongFunction<DependencySiteFact> metadata) {
        open();
        try{control.grow(256,PHASE);var next=new Transaction(count,metadata);active=next;return next;}
        catch(RuntimeException|Error failure){abort(failure);throw failure;}
    }
    /** Transfer the committed ranges to this explicit result owner before the
     * PlanningExecution closes its transaction views. No failed tail is revived. */
    synchronized void seal(){open();active=null;}
    record Header(UnitId caller,EntryId entry,OperationId operation,TargetKind targetKind,
                  Reachability reachability,int candidates,AnalysisStatus analysisStatus,Boolean modelValueRemainder) { }
    /** Only identity prefix and fixed typed columns, never candidates/proofs/body.
     * Callback lifetime keeps both the projected complete key and carrier funded.
     * The fixed field-directory cells are paged, not a resident side inventory. */
    synchronized <T> T withHeader(int ordinal,java.util.function.Function<Header,T> use) {
        open();Objects.checkIndex(ordinal,count);Objects.requireNonNull(use);
        try {
            long row=ordinal*(long)STRIDE,first=offsets.get(row),keyEnd=offsets.get(row+2);
            try(var scratch=resources.reserve(AnalysisResources.Pool.SCRATCH,Math.addExact(1024,Math.multiplyExact(Math.subtractExact(keyEnd,first),64)),AnalysisResources.Phase.SORT)) {
                if(scratch.amount()==0)throw new IllegalStateException("unfunded fact key projection");
                var reader=new Reader(first,keyEnd);var caller=reader.id(UnitId.class);var entry=reader.id(EntryId.class);
                reader.id(LabelId.class);var operation=reader.id(OperationId.class);
                if(reader.at!=keyEnd)throw new IllegalStateException("fact key extent mismatch");
                var header=new Header(caller,entry,operation,reader.tag(TargetKind.values(),Math.toIntExact(payload.get(offsets.get(row+3)))),
                    reader.tag(Reachability.values(),Math.toIntExact(payload.get(offsets.get(row+4)))),Math.toIntExact(payload.get(offsets.get(row+5))),
                    reader.tag(AnalysisStatus.values(),Math.toIntExact(payload.get(offsets.get(row+6)))),
                    remainder(payload.get(offsets.get(row+7))));
                if(header.candidates()<0)throw new IllegalStateException("negative candidate count");
                return use.apply(header);
            }
        }catch(RuntimeException|Error failure){abort(failure);throw failure;}
    }
    private static Boolean remainder(long value){return switch(Math.toIntExact(value)){case 0->null;case 1->Boolean.FALSE;case 2->Boolean.TRUE;default->throw new IllegalStateException("invalid nullable remainder");};}
    private final class Transaction implements FactStorage<DependencySiteFact> {
        private final int first;
        private java.util.function.ToLongFunction<DependencySiteFact> metadata;
        private int size;
        private boolean retired;
        Transaction(int first,java.util.function.ToLongFunction<DependencySiteFact> metadata){this.first=first;this.metadata=metadata;}
        private void available(){open();if(retired)throw new IllegalStateException("fact transaction expired");}
        @Override public void append(DependencySiteFact fact){synchronized(PagedDependencyFactStorage.this){available();if(active!=this)throw new IllegalStateException("fact transaction already sealed");Objects.requireNonNull(fact);try{appendFact(fact,metadata==null?0:metadata.applyAsLong(fact));size=Math.incrementExact(size);}catch(RuntimeException|Error failure){abort(failure);throw failure;}}}
        @Override public int size(){synchronized(PagedDependencyFactStorage.this){available();return size;}}
        @Override public DependencySiteFact get(int ordinal){synchronized(PagedDependencyFactStorage.this){available();Objects.checkIndex(ordinal,size);return PagedDependencyFactStorage.this.get(Math.addExact(first,ordinal));}}
        @Override public void close(){synchronized(PagedDependencyFactStorage.this){if(retired)return;retired=true;metadata=null;if(!closed&&active==this)try{truncate(first);}finally{active=null;}}}
    }
    private void truncate(int first) {
        try {
            // Expire the failed range before its cells are cleared/reused. Do
            // not retire committed-prefix scopes or detached caller-owned DTOs.
            long firstRow=first*(long)STRIDE;
            for(var scope=readScopes;scope!=null;){var next=scope.next;if(scope instanceof FactCursor fact&&fact.row>=firstRow)scope.close();scope=next;}
            for(var scope=supportCursors;scope!=null;){var next=scope.next;if(scope.factRow>=firstRow)scope.close();scope=next;}
            for(var scope=supportProjections;scope!=null;){var next=scope.next;if(scope.ordinal>=first)scope.close();scope=next;}
            for(var scope=projections;scope!=null;){var next=scope.next;if(scope.ordinal>=first)scope.close();scope=next;}
            long tail=first==count?cells:offsets.get(first*(long)STRIDE);
            for(int i=count-1;i>=first;i--)for(int column=STRIDE-1;column>=0;column--)offsets.set(i*(long)STRIDE+column,0);
            for(long i=cells;i>tail;)payload.set(--i,0);
            count=first;cells=tail;
        }catch(RuntimeException|Error failure){abort(failure);throw failure;}
    }
    private void word(long value){payload.set(cells,value);cells=Math.incrementExact(cells);}
    private void bool(boolean value){word(value?1:0);}
    private void text(String value){word(value.length());for(int i=0;i<value.length();i++)word(value.charAt(i));}
    private void ids(List<? extends Id> values){indexed(values.size(),i->id(values.get(i)));}
    /** Count/end plus two extent cells per element, all in the existing paged
     * payload tape. Direct leaf access does not scan preceding candidate/proofs.
     * No additional fixed tape control or resident per-element directory. */
    private void indexed(int size,java.util.function.IntConsumer append){
        word(size);long endCell=cells;word(0);long directory=cells;
        for(long i=0,n=2L*size;i<n;i++)word(0);
        for(int i=0;i<size;i++){long first=cells;append.accept(i);payload.set(directory+2L*i,first);payload.set(directory+2L*i+1,cells);}
        payload.set(endCell,cells);
    }
    private void supports(List<Support> values){indexed(values.size(),i->{var value=values.get(i);word(value.kind().ordinal());id(value.producer());id(value.origin());indexed(value.premises().size(),p->id(value.premises().get(p)));});}
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
        try(var projection=project(ordinal)){return projection.fact();}
    }
    /** Fund before decoding; the caller must close this borrowed projection.
     * Invalid ordinals do not poison storage. Operational failure expires all
     * live projections and retires only this storage's derived pages. */
    public synchronized Projection project(int ordinal) {
        open();Objects.checkIndex(ordinal,count);
        AnalysisResources.Reservation scratch=null;
        try {
            long first=offsets.get(ordinal*(long)STRIDE),end=offsets.get(ordinal*(long)STRIDE+1),length=Math.subtractExact(end,first);
            if(first<0||end>cells||length<=0)throw new IllegalStateException("invalid fact payload range");
            scratch=resources.reserve(AnalysisResources.Pool.SCRATCH,Math.addExact(1024,Math.multiplyExact(length,64)),AnalysisResources.Phase.REPLAY);
            if(scratch.amount()==0)throw new IllegalStateException("unfunded fact projection");
            var reader=new Reader(first,end);var fact=reader.fact();
            if(reader.at!=end)throw new IllegalStateException("fact payload trailing fields");
            var projection=new Projection(fact,scratch,ordinal);
            projection.next=projections;if(projections!=null)projections.previous=projection;projections=projection;
            scratch=null;projection.reservation.onOwnerClose(projection::close);
            // Native expiry may run cleanup during callback registration.
            projection.available();return projection;
        }catch(RuntimeException|Error failure){
            abort(failure);
            if(scratch!=null)try{scratch.close();}catch(RuntimeException|Error cleanup){if(cleanup!=failure)failure.addSuppressed(cleanup);}
            throw failure;
        }
    }
    /** Intrusive live-lease chain avoids an unbudgeted cardinality-sized directory.
     * The preflight envelope also funds this carrier and its links. Closing drops
     * this owner's DTO reference; an explicitly copied Java record cannot expire. */
    public final class Projection implements AutoCloseable {
        private final int ordinal;
        private DependencySiteFact fact;
        private AnalysisResources.Reservation reservation;
        private Projection previous,next;
        private boolean retired;
        private Projection(DependencySiteFact fact,AnalysisResources.Reservation reservation,int ordinal){this.fact=fact;this.reservation=reservation;this.ordinal=ordinal;}
        private void available(){if(retired)throw new IllegalStateException("dependency fact projection expired");open();}
        public DependencySiteFact fact(){synchronized(PagedDependencyFactStorage.this){available();return fact;}}
        long funding(){synchronized(PagedDependencyFactStorage.this){available();return reservation.amount();}}
        @Override public void close(){synchronized(PagedDependencyFactStorage.this){
            if(retired)return;retired=true;
            if(previous==null)projections=next;else previous.next=next;
            if(next!=null)next.previous=previous;
            previous=null;next=null;fact=null;var owned=reservation;reservation=null;
            if(owned!=null)owned.close();
        }}
    }
    /** O(1) directory probes plus bytes of the selected complete typed support.
     * Fund only that leaf before decoding, and keep it funded until close.
     * Fixed root control covers the bounded metadata probes before reservation. */
    public synchronized SupportProjection projectSupport(int ordinal,int candidate,int support){
        open();Objects.checkIndex(ordinal,count);AnalysisResources.Reservation scratch=null;
        try {
            long row=ordinal*(long)STRIDE;var reader=new Reader(offsets.get(row+5),offsets.get(row+1));
            var candidateRange=reader.select(candidate);reader=new Reader(candidateRange.first(),candidateRange.end());reader.skipText();reader.skipText();
            var range=reader.select(support);
            scratch=resources.reserve(AnalysisResources.Pool.SCRATCH,Math.addExact(1024,Math.multiplyExact(Math.subtractExact(range.end(),range.first()),64)),AnalysisResources.Phase.REPLAY);
            if(scratch.amount()==0)throw new IllegalStateException("unfunded support projection");
            reader=new Reader(range.first(),range.end());var value=reader.support();if(reader.at!=range.end())throw new IllegalStateException("support trailing fields");
            var projection=new SupportProjection(value,scratch,ordinal);projection.next=supportProjections;if(supportProjections!=null)supportProjections.previous=projection;supportProjections=projection;
            scratch=null;projection.reservation.onOwnerClose(projection::close);projection.available();return projection;
        }catch(InvalidOrdinal invalid){throw new IndexOutOfBoundsException(invalid.getMessage());}
        catch(RuntimeException|Error failure){abort(failure);if(scratch!=null)try{scratch.close();}catch(RuntimeException|Error cleanup){if(cleanup!=failure)failure.addSuppressed(cleanup);}throw failure;}
    }
    private static final class InvalidOrdinal extends RuntimeException {
        private static final long serialVersionUID=1L;
        InvalidOrdinal(){super("dependency nested ordinal");}
    }
    /** One complete fact's read metadata, not its payload. Reserve a constant
     * envelope before construction; lists, text and typed IDs remain on tape. */
    public synchronized FactCursor openFact(int ordinal) {
        open();Objects.checkIndex(ordinal,count);AnalysisResources.Reservation scratch=null;
        try {
            scratch=resources.reserve(AnalysisResources.Pool.SCRATCH,1024,AnalysisResources.Phase.REPLAY);
            var scope=new FactCursor(ordinal,scratch);scratch=null;install(scope);return scope;
        }catch(RuntimeException|Error failure){abort(failure);if(scratch!=null)try{scratch.close();}catch(RuntimeException|Error cleanup){if(cleanup!=failure)failure.addSuppressed(cleanup);}throw failure;}
    }
    /** Intrusive live scopes,not a cardinality-sized child registry. Every
     * constructed scope has its own fixed reservation and native expiry hook. */
    private abstract class ReadScope implements AutoCloseable {
        private final ReadScope parent;
        private ReadScope previous,next,children;
        private AnalysisResources.Reservation reservation;
        private Runnable retirement;
        private boolean retired;
        ReadScope(ReadScope parent,AnalysisResources.Reservation reservation){this.parent=parent;this.reservation=reservation;}
        final void available(){if(retired)throw new IllegalStateException("dependency field scope expired");if(parent==null)open();else parent.available();}
        final void onRetirement(Runnable cleanup){Objects.requireNonNull(cleanup);if(retirement!=null)throw new IllegalStateException("field scope parent already registered");if(retired)cleanup.run();else retirement=cleanup;}
        void closeFields(){ }
        @Override public void close(){synchronized(PagedDependencyFactStorage.this){
            if(retired)return;retired=true;Throwable failure=null;
            while(children!=null)try{children.close();}catch(RuntimeException|Error cleanup){if(failure==null)failure=cleanup;else if(failure!=cleanup)failure.addSuppressed(cleanup);}
            try{closeFields();}catch(RuntimeException|Error cleanup){if(failure==null)failure=cleanup;else if(failure!=cleanup)failure.addSuppressed(cleanup);}
            if(previous==null){if(parent==null)readScopes=next;else parent.children=next;}else previous.next=next;
            if(next!=null)next.previous=previous;previous=null;next=null;
            var owned=reservation;reservation=null;try{if(owned!=null)owned.close();}catch(RuntimeException|Error cleanup){if(failure==null)failure=cleanup;else if(failure!=cleanup)failure.addSuppressed(cleanup);}
            var expire=retirement;retirement=null;try{if(expire!=null)expire.run();}catch(RuntimeException|Error cleanup){if(failure==null)failure=cleanup;else if(failure!=cleanup)failure.addSuppressed(cleanup);}
            rethrowCleanup(failure);
        }}
    }
    private void install(ReadScope scope){
        var first=scope.parent==null?readScopes:scope.parent.children;scope.next=first;if(first!=null)first.previous=scope;
        if(scope.parent==null)readScopes=scope;else scope.parent.children=scope;
        scope.reservation.onOwnerClose(scope::close);scope.available();
    }
    private TextView textView(ReadScope parent,long first,long end,Directory directory,int ordinal) {
        AnalysisResources.Reservation scratch=null;
        try {
            scratch=resources.reserve(AnalysisResources.Pool.SCRATCH,256,AnalysisResources.Phase.REPLAY);
            if(directory!=null){var range=new Reader(directory.body(),directory.end()).range(directory,ordinal);first=range.first();end=range.end();}
            var reader=new Reader(first,end);long length=reader.length(),body=reader.at;
            if(Math.addExact(body,length)!=end)throw new IllegalStateException("text field trailing cells");
            var view=new TextView(parent,body,length,scratch);scratch=null;install(view);return view;
        }catch(RuntimeException|Error failure){abort(failure);if(scratch!=null)try{scratch.close();}catch(RuntimeException|Error cleanup){if(cleanup!=failure)failure.addSuppressed(cleanup);}throw failure;}
    }
    private void readText(long first,long length,long offset,char[] destination,int target,int count) {
        Objects.checkFromIndexSize(target,count,Objects.requireNonNull(destination).length);
        if(offset<0||offset>length||count>length-offset)throw new IndexOutOfBoundsException("fact field text extent");
        try{long at=Math.addExact(first,offset);for(int i=0;i<count;i++){long character=payload.get(Math.addExact(at,i));if(character<0||character>Character.MAX_VALUE)throw new IllegalStateException("invalid UTF16 cell");destination[target+i]=(char)character;}}
        catch(RuntimeException|Error failure){abort(failure);throw failure;}
    }
    public final class TextView extends ReadScope {
        private final long first,length;
        private final String constant;
        private TextView(ReadScope parent,long first,long length,AnalysisResources.Reservation reservation){super(parent,reservation);this.first=first;this.length=length;constant=null;}
        private TextView(ReadScope parent,String constant,AnalysisResources.Reservation reservation){super(parent,reservation);this.constant=constant;first=0;length=constant.length();}
        public long characterCount(){synchronized(PagedDependencyFactStorage.this){available();return length;}}
        public void readCharacters(long offset,char[] destination,int target,int count){synchronized(PagedDependencyFactStorage.this){
            available();if(constant==null){readText(first,length,offset,destination,target,count);return;}
            Objects.checkFromIndexSize(target,count,Objects.requireNonNull(destination).length);
            if(offset<0||offset>length||count>length-offset)throw new IndexOutOfBoundsException("fact annotation text extent");
            try{resources.work(count,AnalysisResources.Phase.REPLAY);constant.getChars(Math.toIntExact(offset),Math.toIntExact(offset)+count,destination,target);}
            catch(RuntimeException|Error failure){abort(failure);throw failure;}
        }}
        @Override public void close(){super.close();}
    }
    /** Only the three fixed product annotation literals,never arbitrary payload.
     * Same256-byte funded text scope/lifetime; no resident list or fact rewrite. */
    private TextView annotationText(ReadScope parent,int bit) {
        AnalysisResources.Reservation scratch=null;
        try {
            scratch=resources.reserve(AnalysisResources.Pool.SCRATCH,256,AnalysisResources.Phase.REPLAY);
            String constant=switch(bit){case 1->"PHYSICAL_PROPAGATION_DISABLED";case 2->"PARTIAL_CONTROL_PROJECTION";case 4->"UNPROVED_OPERATION_PRECONDITION";default->throw new IllegalStateException("invalid fact annotation");};
            var view=new TextView(parent,constant,scratch);scratch=null;install(view);return view;
        }catch(RuntimeException|Error failure){abort(failure);if(scratch!=null)try{scratch.close();}catch(RuntimeException|Error cleanup){if(cleanup!=failure)failure.addSuppressed(cleanup);}throw failure;}
    }
    public final class FactCursor extends ReadScope {
        private final long row,end;
        private final Directory candidates,raw,evidence,provenance,premises,uncertainties,reasons;
        private final IdentityOwner identityOwner;
        private int annotationFlags;
        FactCursor annotations(int flags){synchronized(PagedDependencyFactStorage.this){available();annotationFlags=flags&7;return this;}}
        void onClose(Runnable cleanup){synchronized(PagedDependencyFactStorage.this){onRetirement(cleanup);}}
        private FactCursor(int ordinal,AnalysisResources.Reservation reservation){
            super(null,reservation);row=ordinal*(long)STRIDE;end=offsets.get(row+1);identityOwner=new IdentityOwner(this::available);
            candidates=directory(5);raw=directory(21);evidence=directory(22);provenance=directory(23);premises=directory(24);uncertainties=directory(25);reasons=directory(26);
        }
        private long anchor(int column){return offsets.get(row+column);}
        private Directory directory(int column){return new Reader(anchor(column),end).directory();}
        private long value(int column,int delta){available();try{return payload.get(Math.addExact(anchor(column),delta));}catch(RuntimeException|Error failure){abort(failure);throw failure;}}
        private <T> T tag(int column,int delta,T[] values){int ordinal=Math.toIntExact(value(column,delta));if(ordinal<0||ordinal>=values.length){var failure=new IllegalStateException("invalid fact column tag");abort(failure);throw failure;}return values[ordinal];}
        private boolean bool(int delta){long value=value(7,delta);if(value!=0&&value!=1){var failure=new IllegalStateException("invalid fact column boolean");abort(failure);throw failure;}return value!=0;}
        private IdentityView identity(int column,int finish,int expected,boolean nullable){
            available();try{long first=anchor(column),last=anchor(finish);if(nullable&&payload.get(first)==0){if(last!=first+1)throw new IllegalStateException("null identity extent");return null;}return identityOwner.view(first,last,expected,null,0);}
            catch(RuntimeException|Error failure){abort(failure);throw failure;}
        }
        private TextView text(int column,int finish){available();try{return textView(this,anchor(column),anchor(finish),null,0);}catch(RuntimeException|Error failure){abort(failure);throw failure;}}
        private IdentityView item(Directory directory,int ordinal,int expected){available();Objects.checkIndex(ordinal,directory.size());return identityOwner.view(0,0,expected,directory,ordinal);}
        public IdentityView caller(){synchronized(PagedDependencyFactStorage.this){return identity(8,9,2,false);}}
        public IdentityView entry(){synchronized(PagedDependencyFactStorage.this){return identity(9,10,3,false);}}
        public IdentityView sequence(){synchronized(PagedDependencyFactStorage.this){return identity(10,11,4,false);}}
        public IdentityView operation(){synchronized(PagedDependencyFactStorage.this){return identity(11,2,5,false);}}
        public int offset(){synchronized(PagedDependencyFactStorage.this){return Math.toIntExact(value(12,0));}}
        public IdentityView siteOrigin(){synchronized(PagedDependencyFactStorage.this){return identity(13,14,12,false);}}
        public IdentityView targetOrigin(){synchronized(PagedDependencyFactStorage.this){return identity(14,15,12,false);}}
        public TextView technology(){synchronized(PagedDependencyFactStorage.this){return text(15,16);}}
        public TextView command(){synchronized(PagedDependencyFactStorage.this){return text(16,17);}}
        public TextView namespace(){synchronized(PagedDependencyFactStorage.this){return text(17,18);}}
        public TextView nameProfile(){synchronized(PagedDependencyFactStorage.this){return text(18,3);}}
        public TargetKind targetKind(){synchronized(PagedDependencyFactStorage.this){return tag(3,0,TargetKind.values());}}
        public IdentityView subject(){synchronized(PagedDependencyFactStorage.this){return identity(19,20,7,true);}}
        public PointView valuePoint(){synchronized(PagedDependencyFactStorage.this){
            available();AnalysisResources.Reservation scratch=null;
            try{long first=anchor(20),last=anchor(4);if(payload.get(first)==0){if(last!=first+1)throw new IllegalStateException("null point extent");return null;}
                scratch=resources.reserve(AnalysisResources.Pool.SCRATCH,512,AnalysisResources.Phase.REPLAY);
                var point=new PointView(this,new Reader(first,last),scratch);scratch=null;install(point);return point;
            }catch(RuntimeException|Error failure){abort(failure);if(scratch!=null)try{scratch.close();}catch(RuntimeException|Error cleanup){if(cleanup!=failure)failure.addSuppressed(cleanup);}throw failure;}
        }}
        public Reachability reachability(){synchronized(PagedDependencyFactStorage.this){return tag(4,0,Reachability.values());}}
        public TargetStatus targetStatus(){synchronized(PagedDependencyFactStorage.this){return tag(4,1,TargetStatus.values());}}
        public Boolean modelValueRemainder(){synchronized(PagedDependencyFactStorage.this){return remainder(value(7,0));}}
        public boolean sourceValueRemainder(){synchronized(PagedDependencyFactStorage.this){return bool(1);}}
        public boolean interpretationUnknownRemainder(){synchronized(PagedDependencyFactStorage.this){return bool(2);}}
        public boolean effectiveUnknownRemainder(){synchronized(PagedDependencyFactStorage.this){return bool(3);}}
        public boolean openControlRemainder(){synchronized(PagedDependencyFactStorage.this){return bool(4);}}
        public AnalysisStatus analysisStatus(){synchronized(PagedDependencyFactStorage.this){var stored=tag(6,0,AnalysisStatus.values());return annotationFlags==0?stored:AnalysisStatus.PARTIAL;}}
        public int candidateCount(){synchronized(PagedDependencyFactStorage.this){available();return candidates.size();}}
        public int rawCandidateCount(){synchronized(PagedDependencyFactStorage.this){available();return raw.size();}}
        public CandidateCursor openCandidate(int ordinal){synchronized(PagedDependencyFactStorage.this){return candidate(candidates,ordinal,false);}}
        public CandidateCursor openRawCandidate(int ordinal){synchronized(PagedDependencyFactStorage.this){return candidate(raw,ordinal,true);}}
        private CandidateCursor candidate(Directory directory,int ordinal,boolean rawOnly){
            available();Objects.checkIndex(ordinal,directory.size());AnalysisResources.Reservation scratch=null;
            try{scratch=resources.reserve(AnalysisResources.Pool.SCRATCH,512,AnalysisResources.Phase.REPLAY);
                var range=new Reader(directory.body(),directory.end()).range(directory,ordinal);var cursor=new CandidateCursor(this,new Reader(range.first(),range.end()),rawOnly,scratch);scratch=null;install(cursor);return cursor;
            }catch(RuntimeException|Error failure){abort(failure);if(scratch!=null)try{scratch.close();}catch(RuntimeException|Error cleanup){if(cleanup!=failure)failure.addSuppressed(cleanup);}throw failure;}
        }
        public int evidenceCount(){synchronized(PagedDependencyFactStorage.this){available();return evidence.size();}}
        public IdentityView evidence(int ordinal){synchronized(PagedDependencyFactStorage.this){return item(evidence,ordinal,0);}}
        public int provenanceCount(){synchronized(PagedDependencyFactStorage.this){available();return provenance.size();}}
        public IdentityView provenance(int ordinal){synchronized(PagedDependencyFactStorage.this){return item(provenance,ordinal,12);}}
        public int premiseCount(){synchronized(PagedDependencyFactStorage.this){available();return premises.size();}}
        public IdentityView premise(int ordinal){synchronized(PagedDependencyFactStorage.this){return item(premises,ordinal,14);}}
        public int uncertaintyCount(){synchronized(PagedDependencyFactStorage.this){available();return uncertainties.size();}}
        public IdentityView uncertainty(int ordinal){synchronized(PagedDependencyFactStorage.this){return item(uncertainties,ordinal,13);}}
        public int reasonCount(){synchronized(PagedDependencyFactStorage.this){available();try{return Math.addExact(reasons.size(),Integer.bitCount(annotationFlags));}catch(RuntimeException|Error failure){abort(failure);throw failure;}}}
        /** Stored ordinals plus fixed owner annotations. Consumers retain the
         * existing sorted/distinct union contract without materializing it here. */
        public TextView reason(int ordinal){synchronized(PagedDependencyFactStorage.this){
            available();Objects.checkIndex(ordinal,reasonCount());if(ordinal<reasons.size())return textView(this,0,0,reasons,ordinal);
            int selected=ordinal-reasons.size();for(int bit=1;bit<=4;bit<<=1)if((annotationFlags&bit)!=0&&selected--==0)return annotationText(this,bit);
            throw new IllegalStateException("fact annotation ordinal missing");
        }}
        @Override void closeFields(){identityOwner.close();}
        @Override public void close(){super.close();}
    }
    public final class CandidateCursor extends ReadScope {
        private final long factRow;
        private final long referenceFirst,referenceLength,rawFirst,rawLength;
        private final Directory supports;
        private SupportCursor ownedSupports;
        private CandidateCursor(FactCursor parent,Reader reader,boolean rawOnly,AnalysisResources.Reservation reservation){
            super(parent,reservation);factRow=parent.row;
            if(rawOnly){referenceLength=0;referenceFirst=0;}else{referenceLength=reader.length();referenceFirst=reader.at;reader.at=Math.addExact(reader.at,referenceLength);}
            rawLength=reader.length();rawFirst=reader.at;reader.at=Math.addExact(reader.at,rawLength);supports=reader.directory();
            if(supports.end()!=reader.end)throw new IllegalStateException("candidate scope trailing cells");
        }
        public long referenceLength(){synchronized(PagedDependencyFactStorage.this){available();return referenceLength;}}
        public long rawLength(){synchronized(PagedDependencyFactStorage.this){available();return rawLength;}}
        public void readReference(long offset,char[] destination,int target,int count){synchronized(PagedDependencyFactStorage.this){available();readText(referenceFirst,referenceLength,offset,destination,target,count);}}
        public void readRaw(long offset,char[] destination,int target,int count){synchronized(PagedDependencyFactStorage.this){available();readText(rawFirst,rawLength,offset,destination,target,count);}}
        public int supportCount(){synchronized(PagedDependencyFactStorage.this){available();return supports.size();}}
        public SupportCursor openSupport(int ordinal){synchronized(PagedDependencyFactStorage.this){
            available();Objects.checkIndex(ordinal,supports.size());AnalysisResources.Reservation scratch=null;
            try {
                scratch=resources.reserve(AnalysisResources.Pool.SCRATCH,512,AnalysisResources.Phase.REPLAY);
                var range=new Reader(supports.body(),supports.end()).range(supports,ordinal);
                var cursor=new SupportCursor(new Reader(range.first(),range.end()),scratch,factRow);cursor.next=supportCursors;
                if(supportCursors!=null)supportCursors.previous=cursor;supportCursors=cursor;scratch=null;
                cursor.readNext=ownedSupports;if(ownedSupports!=null)ownedSupports.readPrevious=cursor;ownedSupports=cursor;
                cursor.onClose(()->unlink(cursor));cursor.reservation.onOwnerClose(cursor::close);cursor.available();return cursor;
            }catch(RuntimeException|Error failure){abort(failure);if(scratch!=null)try{scratch.close();}catch(RuntimeException|Error cleanup){if(cleanup!=failure)failure.addSuppressed(cleanup);}throw failure;}
        }}
        private void unlink(SupportCursor cursor){if(cursor.readPrevious==null)ownedSupports=cursor.readNext;else cursor.readPrevious.readNext=cursor.readNext;if(cursor.readNext!=null)cursor.readNext.readPrevious=cursor.readPrevious;cursor.readPrevious=null;cursor.readNext=null;}
        @Override void closeFields(){Throwable failure=null;while(ownedSupports!=null)try{ownedSupports.close();}catch(RuntimeException|Error cleanup){if(failure==null)failure=cleanup;else if(failure!=cleanup)failure.addSuppressed(cleanup);}rethrowCleanup(failure);}
        @Override public void close(){super.close();}
    }
    public enum PointOutcome { NONE, NORMAL, EXCEPTION, OTHER_EXCEPTION, HALT, DIVERGE }
    public final class PointView extends ReadScope {
        private final ProgramPoint.Kind kind;
        private final PointOutcome outcome;
        private final long entryFirst,entryEnd,operationFirst,operationEnd,tagFirst,tagEnd;
        private final boolean nullOperation;
        private final IdentityOwner identityOwner;
        private PointView(FactCursor parent,Reader reader,AnalysisResources.Reservation reservation){
            super(parent,reservation);identityOwner=new IdentityOwner(this::available);kind=reader.tag(ProgramPoint.Kind.values(),reader.integer()-1);
            entryFirst=reader.at;reader.skipId();entryEnd=reader.at;operationFirst=reader.at;
            nullOperation=payload.get(reader.at)==0;if(nullOperation)reader.word();else reader.skipId();operationEnd=reader.at;
            outcome=reader.tag(PointOutcome.values());tagFirst=reader.at;if(outcome==PointOutcome.EXCEPTION)reader.skipText();tagEnd=reader.at;
            if(reader.at!=reader.end)throw new IllegalStateException("point scope trailing cells");
        }
        public ProgramPoint.Kind kind(){synchronized(PagedDependencyFactStorage.this){available();return kind;}}
        public PointOutcome outcomeKind(){synchronized(PagedDependencyFactStorage.this){available();return outcome;}}
        public IdentityView entry(){synchronized(PagedDependencyFactStorage.this){available();return identityOwner.view(entryFirst,entryEnd,3,null,0);}}
        public IdentityView operation(){synchronized(PagedDependencyFactStorage.this){available();return nullOperation?null:identityOwner.view(operationFirst,operationEnd,5,null,0);}}
        public TextView exceptionTag(){synchronized(PagedDependencyFactStorage.this){available();return outcome==PointOutcome.EXCEPTION?textView(this,tagFirst,tagEnd,null,0):null;}}
        @Override void closeFields(){identityOwner.close();}
        @Override public void close(){super.close();}
    }
    /** Fixed 512-byte scratch envelope per support: carrier/links/reservation/
     * callback and bounded Reader/Range/Directory probes, not premise cardinality.
     * The private directory uses the existing payload tape, no extra cache/store.
     * Opening/reading/operational denial aborts owned tapes; invalid ordinals do not. */
    public synchronized SupportCursor openSupport(int ordinal,int candidate,int support) {
        open();Objects.checkIndex(ordinal,count);AnalysisResources.Reservation scratch=null;
        try {
            scratch=resources.reserve(AnalysisResources.Pool.SCRATCH,512,AnalysisResources.Phase.REPLAY);
            long row=ordinal*(long)STRIDE;var reader=new Reader(offsets.get(row+5),offsets.get(row+1));
            var range=reader.select(candidate);reader=new Reader(range.first(),range.end());reader.skipText();reader.skipText();
            range=reader.select(support);reader=new Reader(range.first(),range.end());
            var cursor=new SupportCursor(reader,scratch,row);cursor.next=supportCursors;
            if(supportCursors!=null)supportCursors.previous=cursor;supportCursors=cursor;scratch=null;
            cursor.reservation.onOwnerClose(cursor::close);cursor.available();return cursor;
        }catch(InvalidOrdinal invalid){if(scratch!=null)scratch.close();throw new IndexOutOfBoundsException(invalid.getMessage());}
        catch(RuntimeException|Error failure){abort(failure);if(scratch!=null)try{scratch.close();}catch(RuntimeException|Error cleanup){if(cleanup!=failure)failure.addSuppressed(cleanup);}throw failure;}
    }
    public enum IdentityPart { PUBLICATION, UNIT, OWNER, LOCAL }
    public enum IdentityOwnerKind { NONE, ENTRY, OPERATION }
    /** Established full typed identity order,using caller-funded fixed blocks.
     * Unit itself has no wire unit key. Operand owner kind precedes owner local.
     * Do not compare hashes or join owner/text components into a resident key. */
    static int compareIdentity(IdentityView a,IdentityView b,char[] left,char[] right) {
        if(left.length==0||right.length==0)throw new IllegalArgumentException("identity comparison needs nonempty blocks");
        int c=compareIdentityPart(a,b,IdentityPart.PUBLICATION,left,right,false);
        if(c==0)c=a.domain().compareTo(b.domain());
        if(c==0)c=compareIdentityPart(a,b,IdentityPart.UNIT,left,right,true);
        if(c==0&&a.ownerKind()!=IdentityOwnerKind.NONE&&b.ownerKind()!=IdentityOwnerKind.NONE){
            c=a.ownerKind().compareTo(b.ownerKind());if(c==0)c=compareIdentityPart(a,b,IdentityPart.OWNER,left,right,false);
        }
        return c==0?compareIdentityPart(a,b,IdentityPart.LOCAL,left,right,false):c;
    }
    private static int compareIdentityPart(IdentityView a,IdentityView b,IdentityPart part,char[] left,char[] right,boolean wireUnit) {
        long x=wireUnit&&a.domain().equals("unit")?0:a.characterCount(part),y=wireUnit&&b.domain().equals("unit")?0:b.characterCount(part);
        for(long at=0,n=Math.min(x,y);at<n;){int count=(int)Math.min(Math.min(left.length,right.length),n-at);
            a.readCharacters(part,at,left,0,count);b.readCharacters(part,at,right,0,count);
            for(int i=0;i<count;i++){int c=Character.compare(left[i],right[i]);if(c!=0)return c;}at+=count;
        }return Long.compare(x,y);
    }
    public final class SupportCursor implements AutoCloseable {
        private final long factRow;
        private final SupportKind kind;
        private final long producerFirst,producerEnd,originFirst,originEnd;
        private final Directory premises;
        private AnalysisResources.Reservation reservation;
        private SupportCursor previous,next;
        private final IdentityOwner identityOwner;
        private SupportCursor readPrevious,readNext;
        private Runnable retirement;
        private boolean retired;
        private SupportCursor(Reader reader,AnalysisResources.Reservation reservation,long factRow) {
            this.reservation=reservation;this.factRow=factRow;identityOwner=new IdentityOwner(this::available);kind=reader.tag(SupportKind.values());
            producerFirst=reader.at;reader.skipId();producerEnd=reader.at;
            originFirst=reader.at;reader.skipId();originEnd=reader.at;
            premises=reader.directory();if(premises.end()!=reader.end)throw new IllegalStateException("support cursor trailing fields");
        }
        private void available(){if(retired)throw new IllegalStateException("dependency support cursor expired");open();}
        void onClose(Runnable cleanup){synchronized(PagedDependencyFactStorage.this){Objects.requireNonNull(cleanup);if(retirement!=null)throw new IllegalStateException("support cursor parent already registered");if(retired)cleanup.run();else retirement=cleanup;}}
        public SupportKind kind(){synchronized(PagedDependencyFactStorage.this){available();return kind;}}
        public int premiseCount(){synchronized(PagedDependencyFactStorage.this){available();return premises.size();}}
        public IdentityView producer(){synchronized(PagedDependencyFactStorage.this){available();return identity(producerFirst,producerEnd,0);}}
        public IdentityView origin(){synchronized(PagedDependencyFactStorage.this){available();return identity(originFirst,originEnd,12);}}
        public IdentityView premise(int ordinal){synchronized(PagedDependencyFactStorage.this){
            available();Objects.checkIndex(ordinal,premises.size());
            // Reserve before the directory/read metadata and view are constructed.
            return identity(ordinal);
        }}
        private IdentityView identity(int ordinal){return identity(0,0,14,ordinal);}
        private IdentityView identity(long first,long end,int expected){return identity(first,end,expected,-1);}
        private IdentityView identity(long first,long end,int expected,int ordinal) {
            AnalysisResources.Reservation scratch=null;
            try {
                scratch=resources.reserve(AnalysisResources.Pool.SCRATCH,512,AnalysisResources.Phase.REPLAY);
                if(ordinal>=0){var range=new Reader(premises.body(),premises.end()).range(premises,ordinal);first=range.first();end=range.end();}
                var view=new IdentityView(identityOwner,new Reader(first,end),expected,scratch);view.next=identityOwner.identities;
                if(identityOwner.identities!=null)identityOwner.identities.previous=view;identityOwner.identities=view;scratch=null;
                view.reservation.onOwnerClose(view::close);view.available();return view;
            }catch(RuntimeException|Error failure){abort(failure);if(scratch!=null)try{scratch.close();}catch(RuntimeException|Error cleanup){if(cleanup!=failure)failure.addSuppressed(cleanup);}throw failure;}
        }
        @Override public void close(){synchronized(PagedDependencyFactStorage.this){
            if(retired)return;retired=true;Throwable failure=null;
            try{identityOwner.close();}catch(RuntimeException|Error cleanup){failure=cleanup;}
            if(previous==null)supportCursors=next;else previous.next=next;if(next!=null)next.previous=previous;previous=null;next=null;
            var owned=reservation;reservation=null;try{if(owned!=null)owned.close();}catch(RuntimeException|Error cleanup){if(failure==null)failure=cleanup;else if(cleanup!=failure)failure.addSuppressed(cleanup);}
            var expire=retirement;retirement=null;try{if(expire!=null)expire.run();}catch(RuntimeException|Error cleanup){if(failure==null)failure=cleanup;else if(cleanup!=failure)failure.addSuppressed(cleanup);}
            if(failure instanceof RuntimeException runtime)throw runtime;if(failure instanceof Error error)throw error;
        }}
    }
    /** Constant funded holder lets non-support field scopes own identity views
     * without constructing a fake Support or a complete typed ID. */
    private final class IdentityOwner {
        private final Runnable alive;
        private IdentityView identities;
        IdentityOwner(Runnable alive){this.alive=alive;}
        void available(){alive.run();}
        IdentityView view(long first,long end,int expected,Directory directory,int ordinal) {
            AnalysisResources.Reservation scratch=null;
            try {
                scratch=resources.reserve(AnalysisResources.Pool.SCRATCH,512,AnalysisResources.Phase.REPLAY);
                if(directory!=null){var range=new Reader(directory.body(),directory.end()).range(directory,ordinal);first=range.first();end=range.end();}
                var view=new IdentityView(this,new Reader(first,end),expected,scratch);view.next=identities;
                if(identities!=null)identities.previous=view;identities=view;scratch=null;
                view.reservation.onOwnerClose(view::close);view.available();return view;
            }catch(RuntimeException|Error failure){abort(failure);if(scratch!=null)try{scratch.close();}catch(RuntimeException|Error cleanup){if(cleanup!=failure)failure.addSuppressed(cleanup);}throw failure;}
        }
        void close(){Throwable failure=null;while(identities!=null)try{identities.close();}catch(RuntimeException|Error cleanup){if(failure==null)failure=cleanup;else if(failure!=cleanup)failure.addSuppressed(cleanup);}rethrowCleanup(failure);}
    }
    /** Fixed 512 bytes: primitive text extents, carrier/links, reservation/callback
     * and one bounded Reader. No String, typed Id, char array or premise list is
     * decoded here. At most four sealed identity levels; text payload stays cold.
     * Views borrow their parent and expire with it; caller-owned blocks are not
     * retained. Character offsets are UTF16 code units, including split pairs. */
    public final class IdentityView implements AutoCloseable {
        private final IdentityOwner parent;
        private final int tag;
        private IdentityOwnerKind ownerKind=IdentityOwnerKind.NONE;
        private long publicationAt,publicationLength,unitAt,unitLength,ownerAt,ownerLength,localAt,localLength;
        private AnalysisResources.Reservation reservation;
        private IdentityView previous,next;
        private boolean retired;
        private IdentityView(IdentityOwner parent,Reader reader,int expected,AnalysisResources.Reservation reservation) {
            this.parent=parent;this.reservation=reservation;tag=reader.integer();
            if(expected!=0&&tag!=expected)throw new IllegalStateException("unexpected cursor identity type");
            switch(tag) {
                case 1->{capture(reader,IdentityPart.PUBLICATION);localAt=publicationAt;localLength=publicationLength;}
                case 2->{publication(reader);capture(reader,IdentityPart.UNIT);localAt=unitAt;localLength=unitLength;}
                case 3,4,5,7,15->{unit(reader);capture(reader,IdentityPart.LOCAL);}
                case 6->{int owner=reader.integer();int expectedOwner=switch(owner){case 1->{ownerKind=IdentityOwnerKind.OPERATION;yield 5;}case 2->{ownerKind=IdentityOwnerKind.ENTRY;yield 3;}default->throw new IllegalStateException("invalid operand owner");};requireTag(reader,expectedOwner);unit(reader);capture(reader,IdentityPart.OWNER);capture(reader,IdentityPart.LOCAL);}
                case 8,9,10,11,12,13,14->{publication(reader);capture(reader,IdentityPart.LOCAL);}
                default->throw new IllegalStateException("invalid cursor identity tag");
            }
            if(reader.at!=reader.end)throw new IllegalStateException("identity cursor trailing fields");
        }
        private void requireTag(Reader reader,int expected){if(reader.integer()!=expected)throw new IllegalStateException("invalid nested identity tag");}
        private void publication(Reader reader){requireTag(reader,1);capture(reader,IdentityPart.PUBLICATION);}
        private void unit(Reader reader){requireTag(reader,2);publication(reader);capture(reader,IdentityPart.UNIT);}
        private void capture(Reader reader,IdentityPart part){long length=reader.length(),first=reader.at;reader.at=Math.addExact(first,length);switch(part){
            case PUBLICATION->{publicationAt=first;publicationLength=length;}case UNIT->{unitAt=first;unitLength=length;}
            case OWNER->{ownerAt=first;ownerLength=length;}case LOCAL->{localAt=first;localLength=length;}
        }}
        private void available(){if(retired)throw new IllegalStateException("dependency identity view expired");parent.available();}
        public String domain(){synchronized(PagedDependencyFactStorage.this){available();return switch(tag){case 1->"publication";case 2->"unit";case 3->"entry";case 4->"label";case 5->"operation";case 6->"operand";case 7->"object";case 8->"storage";case 9->"resource";case 10->"artifact";case 11->"artifactRelation";case 12->"origin";case 13->"uncertainty";case 14->"premise";case 15->"completionPort";default->throw new IllegalStateException("invalid identity tag");};}}
        public IdentityOwnerKind ownerKind(){synchronized(PagedDependencyFactStorage.this){available();return ownerKind;}}
        public int compareFullIdentity(IdentityView other,char[] left,char[] right){synchronized(PagedDependencyFactStorage.this){available();Objects.requireNonNull(other).available();return compareIdentity(this,other,left,right);}}
        public long characterCount(IdentityPart part){synchronized(PagedDependencyFactStorage.this){available();return length(Objects.requireNonNull(part));}}
        private long length(IdentityPart part){return switch(part){case PUBLICATION->publicationLength;case UNIT->unitLength;case OWNER->ownerLength;case LOCAL->localLength;};}
        private long first(IdentityPart part){return switch(part){case PUBLICATION->publicationAt;case UNIT->unitAt;case OWNER->ownerAt;case LOCAL->localAt;};}
        public void readCharacters(IdentityPart part,long offset,char[] destination,int target,int count){synchronized(PagedDependencyFactStorage.this){
            available();Objects.requireNonNull(part);Objects.checkFromIndexSize(target,count,Objects.requireNonNull(destination).length);
            long length=length(part);if(offset<0||offset>length||count>length-offset)throw new IndexOutOfBoundsException("identity text block extent");
            try{long first=Math.addExact(first(part),offset);for(int i=0;i<count;i++){long character=payload.get(Math.addExact(first,i));if(character<0||character>Character.MAX_VALUE)throw new IllegalStateException("invalid UTF16 cell");destination[target+i]=(char)character;}}
            catch(RuntimeException|Error failure){abort(failure);throw failure;}
        }}
        @Override public void close(){synchronized(PagedDependencyFactStorage.this){
            if(retired)return;retired=true;if(previous==null)parent.identities=next;else previous.next=next;if(next!=null)next.previous=previous;previous=null;next=null;
            var owned=reservation;reservation=null;if(owned!=null)owned.close();
        }}
    }
    public final class SupportProjection implements AutoCloseable {
        private final int ordinal;
        private Support support;private AnalysisResources.Reservation reservation;private SupportProjection previous,next;private boolean retired;
        private SupportProjection(Support support,AnalysisResources.Reservation reservation,int ordinal){this.support=support;this.reservation=reservation;this.ordinal=ordinal;}
        private void available(){if(retired)throw new IllegalStateException("dependency support projection expired");open();}
        public Support support(){synchronized(PagedDependencyFactStorage.this){available();return support;}}
        @Override public void close(){synchronized(PagedDependencyFactStorage.this){
            if(retired)return;retired=true;if(previous==null)supportProjections=next;else previous.next=next;if(next!=null)next.previous=previous;
            previous=null;next=null;support=null;var owned=reservation;reservation=null;if(owned!=null)owned.close();
        }}
    }
    private record Range(long first,long end) { }
    private record Directory(int size,long at,long body,long end) { }
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
        void skipText(){int size=length();at=Math.addExact(at,size);}
        void skipId(){switch(integer()){
            case 1->skipText();case 2->{requireIdentityTag(1);skipText();skipText();}
            case 3,4,5,7,15->{requireIdentityTag(2);requireIdentityTag(1);skipText();skipText();skipText();}
            case 6->{int owner=integer();requireIdentityTag(switch(owner){case 1->5;case 2->3;default->throw new IllegalStateException("invalid operand owner");});requireIdentityTag(2);requireIdentityTag(1);skipText();skipText();skipText();skipText();}
            case 8,9,10,11,12,13,14->{requireIdentityTag(1);skipText();skipText();}
            default->throw new IllegalStateException("invalid nonnull cursor identity tag");
        }}
        void requireIdentityTag(int expected){if(integer()!=expected)throw new IllegalStateException("invalid nested cursor identity tag");}
        Directory directory(){int size=length();long finish=word(),body=Math.addExact(at,2L*size);if(body>finish||finish>end||size==0&&body!=finish)throw new IllegalStateException("invalid nested fact directory");return new Directory(size,at,body,finish);}
        Range range(Directory directory,int ordinal){long first=payload.get(directory.at()+2L*ordinal),last=payload.get(directory.at()+2L*ordinal+1);if(first<directory.body()||last<=first||last>directory.end())throw new IllegalStateException("invalid nested fact extent");return new Range(first,last);}
        Range select(int ordinal){var directory=directory();if(ordinal<0||ordinal>=directory.size())throw new InvalidOrdinal();return range(directory,ordinal);}
        <T> List<T> indexed(java.util.function.Function<Reader,T> decode){
            var directory=directory();var values=new ArrayList<T>(directory.size());long expected=directory.body();
            for(int i=0;i<directory.size();i++){var range=range(directory,i);if(range.first()!=expected)throw new IllegalStateException("noncontiguous nested payload");var reader=new Reader(range.first(),range.end());values.add(decode.apply(reader));if(reader.at!=range.end())throw new IllegalStateException("nested trailing fields");expected=range.end();}
            if(expected!=directory.end())throw new IllegalStateException("nested extent mismatch");at=directory.end();return values;
        }
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
        <T extends Id> List<T> ids(Class<T> type){return indexed(reader->reader.id(type));}
        Support support(){return new Support(tag(SupportKind.values()),id(Id.class),id(OriginId.class),indexed(reader->reader.id(PremiseId.class)));}
        List<Support> supports(){return indexed(Reader::support);}
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
            var raw=indexed(reader->new RawCandidate(reader.text(),reader.supports()));
            var candidates=indexed(reader->new Candidate(reader.text(),reader.text(),reader.supports()));
            Boolean model=switch(integer()){case 0->null;case 1->Boolean.FALSE;case 2->Boolean.TRUE;default->throw new IllegalStateException("invalid nullable remainder");};
            boolean source=bool(),interpretation=bool(),effective=bool(),controlOpen=bool();
            var evidence=ids(Id.class);var origins=ids(OriginId.class);var premises=ids(PremiseId.class);var uncertainties=ids(UncertaintyId.class);var analysis=tag(AnalysisStatus.values());
            var reasons=indexed(Reader::text);
            return new DependencySiteFact(caller,entry,sequence,operation,offset,siteOrigin,targetOrigin,technology,command,namespace,profile,kind,subject,point,
                reach,status,raw,candidates,model,source,interpretation,effective,controlOpen,evidence,origins,premises,uncertainties,analysis,reasons);
        }
    }
    private void abort(Throwable primary){try{close();}catch(RuntimeException|Error cleanup){if(cleanup!=primary)primary.addSuppressed(cleanup);}}
    private static void rethrowCleanup(Throwable failure){if(failure instanceof RuntimeException runtime)throw runtime;if(failure instanceof Error error)throw error;}
    @Override public synchronized void close() {
        if(closed)return;closed=true;Throwable failure=null;
        while(readScopes!=null)try{readScopes.close();}
            catch(RuntimeException|Error cleanup){if(failure==null)failure=cleanup;else if(cleanup!=failure)failure.addSuppressed(cleanup);}
        while(supportCursors!=null)try{supportCursors.close();}
            catch(RuntimeException|Error cleanup){if(failure==null)failure=cleanup;else if(cleanup!=failure)failure.addSuppressed(cleanup);}
        while(supportProjections!=null)try{supportProjections.close();}
            catch(RuntimeException|Error cleanup){if(failure==null)failure=cleanup;else if(cleanup!=failure)failure.addSuppressed(cleanup);}
        while(projections!=null)try{projections.close();}
            catch(RuntimeException|Error cleanup){if(failure==null)failure=cleanup;else if(cleanup!=failure)failure.addSuppressed(cleanup);}
        for(var owned:new AutoCloseable[]{payload,offsets,control})if(owned!=null)try{owned.close();}
            catch(Exception|Error cleanup){if(failure==null)failure=cleanup;else if(cleanup!=failure)failure.addSuppressed(cleanup);}
        payload=null;offsets=null;control=null;active=null;count=0;cells=0;
        var expire=ownerClose;ownerClose=null;if(expire!=null)try{expire.run();}
            catch(RuntimeException|Error cleanup){if(failure==null)failure=cleanup;else if(cleanup!=failure)failure.addSuppressed(cleanup);}
        if(failure instanceof RuntimeException runtime)throw runtime;if(failure instanceof Error error)throw error;if(failure!=null)throw new IllegalStateException("fact storage cleanup",failure);
    }
}
