package io.github.gustavo2358.analysis.dependencies;

import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.analysis.plan.AnalysisKey;
import io.github.gustavo2358.analysis.solver.*;
import io.github.gustavo2358.analysis.consumers.FactStorage;
import java.util.*;
import java.util.function.ToIntFunction;

/** Complete general native CALL result owner, not a direct-profile engine.
 * Canonical full-key index, order, edge prefixes and annotations are paged.
 * A cursor holds one funded complete DTO until advance/close. List get remains
 * explicit detached compatibility. FILE, observations, plain qualification and
 * nested proof/candidate DTOs are separate migration debt, not all-store claims.
 * Never owns the checked program or shared page backend. */
public final class ManagedExecutableDependencyResult implements AutoCloseable {
    private SnapshotProgram program;
    private AnalysisResources resources;
    private PageStore pages;
    private AnalysisResources.Reservation control;
    private PagedDependencyFactStorage facts;
    private PagedLongIndex index,operations;
    private PagedLongArray annotations,order;
    private EntryId soughtEntry;
    private OperationId soughtOperation;
    private Cursor cursors;
    private ProgramProjection programProjections;
    private ProgramCursor programCursors;
    private List<QualifiedDependencyOccurrence> occurrences;
    private DependencyResult result;
    private int sites,edges;
    private long projections,leafProjections,partialSites,candidateCount;
    private boolean structural,closed,ready;

    ManagedExecutableDependencyResult(SnapshotProgram program,PageStore pages,AnalysisResources resources){
        this.program=Objects.requireNonNull(program);this.resources=Objects.requireNonNull(resources);this.pages=Objects.requireNonNull(pages);
        resources.requireExecutionProgram(program);program.admission();
        control=resources.reserve(AnalysisResources.Pool.RESIDENT,2048,AnalysisResources.Phase.DEMAND);
        try {
            facts=new PagedDependencyFactStorage(pages,resources);
            annotations=new PagedLongArray(pages,Long.MAX_VALUE,resources,AnalysisResources.Phase.DEMAND);
            order=new PagedLongArray(pages,Long.MAX_VALUE,resources,AnalysisResources.Phase.SORT);
            index=new PagedLongIndex(pages,resources,AnalysisResources.Phase.SORT,this::compare);
            operations=new PagedLongIndex(pages,resources,AnalysisResources.Phase.SORT,this::compareOperation);
            facts.onClose(this::close);
            control.onOwnerClose(this::close);
        }catch(RuntimeException|Error failure){abort(failure);throw failure;}
    }
    private void live(){
        if(closed)throw new IllegalStateException("managed executable dependency result expired");
        try{program.admission();resources.work(1,AnalysisResources.Phase.DEMAND);}
        catch(RuntimeException|Error failure){abort(failure);throw failure;}
    }
    private void available(){live();if(!ready)throw new IllegalStateException("managed executable dependency result incomplete");}
    FactStorage<DependencySiteFact> transaction(){live();return facts.transaction();}
    private static int compare(PagedDependencyFactStorage.Header a,PagedDependencyFactStorage.Header b){return compare(a.entry(),a.operation(),b);}
    private static int compare(EntryId entry,OperationId operation,PagedDependencyFactStorage.Header b){
        int value=AnalysisKey.ENTRY_ORDER.compare(entry,b.entry());
        if(value==0)value=operation.unit().publication().localId().compareTo(b.operation().unit().publication().localId());
        if(value==0)value=operation.unit().localId().compareTo(b.operation().unit().localId());
        if(value==0)value=operation.localId().compareTo(b.operation().localId());return value;
    }
    private int compare(long a,long b){
        if(a==b)return 0;
        if(a==0)return facts.withHeader(Math.toIntExact(b-1),h->compare(soughtEntry,soughtOperation,h));
        if(b==0)return -facts.withHeader(Math.toIntExact(a-1),h->compare(soughtEntry,soughtOperation,h));
        return facts.withHeader(Math.toIntExact(a-1),left->facts.withHeader(Math.toIntExact(b-1),right->compare(left,right)));
    }
    private static int operationOrder(OperationId a,OperationId b){
        int value=a.unit().publication().localId().compareTo(b.unit().publication().localId());
        if(value==0)value=a.unit().localId().compareTo(b.unit().localId());
        if(value==0)value=a.localId().compareTo(b.localId());return value;
    }
    private int compareOperation(long a,long b){
        if(a==b)return 0;
        // The transient zero key is below every Entry for the sought complete
        // Operation identity. It is never inserted or confused with an Id hash.
        if(a==0)return facts.withHeader(Math.toIntExact(b-1),h->{int value=operationOrder(soughtOperation,h.operation());return value==0?-1:value;});
        if(b==0)return -compareOperation(0,a);
        return facts.withHeader(Math.toIntExact(a-1),left->facts.withHeader(Math.toIntExact(b-1),right->{
            int value=operationOrder(left.operation(),right.operation());return value==0?AnalysisKey.ENTRY_ORDER.compare(left.entry(),right.entry()):value;
        }));
    }
    private void include(int ordinal){
        long key=ordinal+1L,prior=index.intern(key,key);
        // Preserve the existing last-fact-wins contract for a complete SiteKey.
        if(prior!=key){index.remove(key);if(index.intern(key,key)!=key)throw new IllegalStateException("fact replacement failed");}
        prior=operations.intern(key,key);
        if(prior!=key){operations.remove(key);if(operations.intern(key,key)!=key)throw new IllegalStateException("operation fact replacement failed");}
    }
    void indexPrepared(){live();try{facts.seal();for(int i=0,n=facts.size();i<n;i++)include(i);}
        catch(RuntimeException|Error failure){abort(failure);throw failure;}}
    boolean contains(EntryId entry,OperationId operation){
        live();soughtEntry=Objects.requireNonNull(entry);soughtOperation=Objects.requireNonNull(operation);
        try{return index.find(0)!=0;}
        catch(RuntimeException|Error failure){abort(failure);throw failure;}
        finally{soughtEntry=null;soughtOperation=null;}
    }
    void appendPartial(DependencySiteFact fact){live();try{int ordinal=facts.size();facts.append(fact);include(ordinal);}
        catch(RuntimeException|Error failure){abort(failure);throw failure;}}
    void finishIndex(ToIntFunction<PagedDependencyFactStorage.Header> flags){
        live();try(var rows=index.cursor()){
            int siteCount=0,edgeCount=0;
            while(rows.advance()){
                int ordinal=Math.toIntExact(rows.value()-1),site=siteCount++;
                final int previousEdges=edgeCount;
                int added=facts.withHeader(ordinal,h->{
                    int annotation=flags.applyAsInt(h);annotations.set(ordinal,annotation);
                    if(annotation!=0||h.analysisStatus()==DependencySiteFact.AnalysisStatus.PARTIAL)partialSites++;
                    structural|=h.reachability()==DependencySiteFact.Reachability.UNKNOWN;
                    candidateCount=Math.addExact(candidateCount,h.candidates());
                    return h.reachability()==DependencySiteFact.Reachability.UNREACHABLE_IN_MODEL?0:h.candidates();
                });
                edgeCount=Math.addExact(previousEdges,added);order.set(site*2L,ordinal);order.set(site*2L+1,edgeCount);
            }
            sites=siteCount;edges=edgeCount;
        }catch(RuntimeException|Error failure){abort(failure);throw failure;}
    }
    long partialSites(){live();return partialSites;}
    private DependencySiteFact annotated(int ordinal,DependencySiteFact fact){
        long flags=annotations.get(ordinal);
        if((flags&1)!=0)fact=fact.withPartialAnalysis("PHYSICAL_PROPAGATION_DISABLED");
        if((flags&2)!=0)fact=fact.withPartialAnalysis("PARTIAL_CONTROL_PROJECTION");
        if((flags&4)!=0)fact=fact.withPartialAnalysis("UNPROVED_OPERATION_PRECONDITION");return fact;
    }
    private DependencySiteFact detached(int site){
        available();Objects.checkIndex(site,sites);
        try(var projection=facts.project(Math.toIntExact(order.get(site*2L)))){
            projections=Math.incrementExact(projections);int ordinal=Math.toIntExact(order.get(site*2L));return annotated(ordinal,projection.fact());
        }catch(RuntimeException|Error failure){abort(failure);throw failure;}
    }
    private DependencyResult.Edge edge(int edge){
        available();Objects.checkIndex(edge,edges);int low=0,high=sites;
        try {
        while(low<high){int middle=(low+high)>>>1;if(order.get(middle*2L+1)<=edge)low=middle+1;else high=middle;}
        int candidate=Math.toIntExact(edge-(low==0?0:order.get((low-1)*2L+1)));var fact=detached(low);
        return new DependencyResult.Edge(fact.caller(),fact.entry(),fact.operation(),fact.candidates().get(candidate),fact.effectiveUnknownRemainder());
        }catch(RuntimeException|Error failure){abort(failure);throw failure;}
    }
    public long candidateCount(){available();return candidateCount;}
    private int factOrdinal(int site){available();Objects.checkIndex(site,sites);return Math.toIntExact(order.get(site*2L));}
    int operationOrdinal(int site,io.github.gustavo2358.analysis.structure.ProgramStore.OperationDirectory directory){
        try{return facts.withHeader(factOrdinal(site),h->directory.ordinal(h.operation()));}
        catch(RuntimeException|Error failure){abort(failure);throw failure;}
    }
    boolean requiresNominal(int site){
        try{return facts.withHeader(factOrdinal(site),h->h.reachability()!=DependencySiteFact.Reachability.UNREACHABLE_IN_MODEL&&!Boolean.FALSE.equals(h.modelValueRemainder()));}
        catch(RuntimeException|Error failure){abort(failure);throw failure;}
    }
    public void requireContext(SnapshotProgram source,AnalysisResources execution){available();if(program!=source||resources!=execution)throw new IllegalArgumentException("foreign managed executable result context");execution.requireExecutionProgram(source);}
    /** One complete program projection, including all matching Entries. It is
     * funded until close; nested programs/proofs still need finer-grained cursors
     * for a single program whose necessary result exceeds the resident budget. */
    public ProgramProjection projectProgram(int ordinal){
        available();Objects.checkIndex(ordinal,occurrences.size());ProgramProjection projection=null;AnalysisResources.Reservation capacity=null;
        try {
            capacity=resources.reserve(AnalysisResources.Pool.SCRATCH,1024,AnalysisResources.Phase.REPLAY);
            projection=new ProgramProjection(capacity);projection.next=programProjections;if(programProjections!=null)programProjections.previous=projection;programProjections=projection;capacity=null;
            projection.capacity.onOwnerClose(projection::close);projection.open();
            var occurrence=occurrences.get(ordinal);
                for(var operation:occurrence.executableOperations()){
                    soughtOperation=operation;
                    try(var rows=operations.cursor(0)){
                        soughtOperation=null;
                        while(rows.advance()){
                            int factOrdinal=Math.toIntExact(rows.value()-1);
                            if(!facts.withHeader(factOrdinal,h->h.operation().equals(operation)))break;
                            projection.capacity.grow(128,AnalysisResources.Phase.REPLAY);
                            var lease=facts.project(factOrdinal);projection.leases.add(lease);projections=Math.incrementExact(projections);
                            // Additional complete envelope bounds resolution's
                            // candidate/support assembly beside the original DTO.
                            projection.capacity.grow(lease.funding(),AnalysisResources.Phase.REPLAY);
                            projection.selected.add(annotated(factOrdinal,lease.fact()));
                        }
                    }finally{soughtOperation=null;}
                }
            projection.resolution=TargetResolver.resolve(occurrence,projection.selected,List.of(),()->program.progress(io.github.gustavo2358.analysis.structure.ProgramStore.ExecutionPhase.DOMAIN));projection.open();return projection;
        }catch(RuntimeException|Error failure){abort(failure);if(capacity!=null)try{capacity.close();}catch(RuntimeException|Error cleanup){if(cleanup!=failure)failure.addSuppressed(cleanup);}throw failure;}
    }
    /** General PROGRAM assembly over real fact ordinals. The original complete
     * projection remains explicit compatibility; occurrence/header DTOs remain
     * separate debt,not disguised as a bounded provider or SOURCE bridge. */
    public ProgramCursor openProgram(int ordinal){
        available();Objects.checkIndex(ordinal,occurrences.size());AnalysisResources.Reservation capacity=null;
        try{
            capacity=resources.reserve(AnalysisResources.Pool.RESIDENT,4096,AnalysisResources.Phase.SORT);
            var cursor=new ProgramCursor(occurrences.get(ordinal),capacity);capacity=null;cursor.capacity.onOwnerClose(cursor::close);cursor.initialize();cursor.open();return cursor;
        }catch(RuntimeException|Error failure){abort(failure);if(capacity!=null)try{capacity.close();}catch(RuntimeException|Error cleanup){if(cleanup!=failure)failure.addSuppressed(cleanup);}throw failure;}
    }
    public final class ProgramCursor implements AutoCloseable {
        private static final int CANDIDATE=8,SUPPORT=4;
        private static final AnalysisResources.Phase PHASE=AnalysisResources.Phase.SORT;
        private QualifiedDependencyOccurrence occurrence;
        private AnalysisResources.Reservation capacity;
        private ProgramCursor previous,next;
        private SourceTextDictionary text;
        private PagedLongArray siteRows,candidates,candidateOrder,supports,supportOrder,reasons;
        private TargetResolver.AssemblyState state;
        private char[] left,right;
        private TextProbe probe;
        private SiteScope siteScopes;
        private SupportScope supportScopes;
        private long sitesCount,candidateRows,candidatesCount,supportRows,supportsCount,reasonsCount;
        private boolean retired,complete;
        private ProgramCursor(QualifiedDependencyOccurrence occurrence,AnalysisResources.Reservation capacity){
            this.occurrence=occurrence;this.capacity=capacity;next=programCursors;if(programCursors!=null)programCursors.previous=this;programCursors=this;
        }
        private void live(){if(retired)throw new IllegalStateException("general program cursor expired");available();}
        private void open(){live();if(!complete)throw new IllegalStateException("general program cursor incomplete");}
        private PagedLongArray array(){return new PagedLongArray(pages,Long.MAX_VALUE,resources,PHASE);}
        private PagedDependencyFactStorage.FactCursor fact(int ordinal){int flags=(int)(annotations.get(ordinal)&7);return facts.openFact(ordinal).annotations(flags);}
        private int compareText(long a,long b){return TargetResolver.compareText(text,a,b,left,right,resources,PHASE);}
        private int compareCandidate(long a,long b){if(a==b)return 0;return TargetResolver.compareCandidateKey(text,candidates.get(a*CANDIDATE),candidates.get(a*CANDIDATE+1),candidates.get(b*CANDIDATE),candidates.get(b*CANDIDATE+1),left,right,resources,PHASE);}
        private long addCandidate(PagedLongIndex order,long reference,long raw){
            long row=candidateRows=Math.incrementExact(candidateRows),at=Math.multiplyExact(row,CANDIDATE);candidates.set(at,reference);candidates.set(at+1,raw);long found=order.intern(row,row);long count=found*CANDIDATE+4;candidates.set(count,Math.incrementExact(candidates.get(count)));return found;
        }
        private void addSupport(long candidate,int fact,int value,int support){
            long row=supportRows=Math.incrementExact(supportRows),at=Math.multiplyExact(row,SUPPORT);supports.set(at,fact);supports.set(at+1,value);supports.set(at+2,support);long base=candidate*CANDIDATE,last=candidates.get(base+3);
            if(last==0)candidates.set(base+2,row);else supports.set(last*SUPPORT+3,row);candidates.set(base+3,row);
        }
        private long intern(PagedDependencyFactStorage.TextView value){try{probe.text(value);return text.intern(probe);}finally{probe.clear();}}
        private void initialize(){
            live();left=new char[128];right=new char[128];probe=new TextProbe();state=new TargetResolver.AssemblyState(!occurrence.qualifications().isEmpty(),occurrence.sourceControlRemainder());
            text=new SourceTextDictionary(pages,resources,PHASE);siteRows=array();candidates=array();candidateOrder=array();supports=array();supportOrder=array();reasons=array();
            try(var order=new PagedLongIndex(pages,resources,PHASE,this::compareCandidate);var reasonOrder=new PagedLongIndex(pages,resources,PHASE,this::compareText)){
                if(occurrence.sourceControlRemainder()){long id=text.intern("SOURCE_CONTROL_UNAVAILABLE");reasonOrder.intern(id,id);}
                for(var operation:occurrence.executableOperations()){
                    soughtOperation=operation;
                    try(var rows=operations.cursor(0)){
                        soughtOperation=null;
                        while(rows.advance()){
                            int ordinal=Math.toIntExact(rows.value()-1);if(!facts.withHeader(ordinal,h->h.operation().equals(operation)))break;
                            siteRows.set(sitesCount,ordinal);sitesCount=Math.incrementExact(sitesCount);accept(ordinal,order,reasonOrder);
                        }
                    }finally{soughtOperation=null;}
                }
                literalCandidates(order);
                String reason=state.finish(occurrence.targetKind(),occurrence.sourceValueRemainder(),sitesCount);if(reason!=null){long id=text.intern(reason);reasonOrder.intern(id,id);}
                try(var rows=order.cursor()){while(rows.advance()){long row=rows.value();candidateOrder.set(candidatesCount++,row);orderSupports(row);}}
                try(var rows=reasonOrder.cursor()){while(rows.advance())reasons.set(reasonsCount++,rows.value());}
            }probe.clear();live();complete=true;
        }
        private void accept(int ordinal,PagedLongIndex order,PagedLongIndex reasonOrder){
            try(var site=fact(ordinal)){
                for(int i=0,n=site.reasonCount();i<n;i++)try(var reason=site.reason(i)){long id=intern(reason);reasonOrder.intern(id,id);}
                if(site.reachability()==DependencySiteFact.Reachability.UNREACHABLE_IN_MODEL)return;
                state.site(site.reachability(),site.modelValueRemainder(),site.sourceValueRemainder(),site.interpretationUnknownRemainder());
                for(int c=0,n=site.candidateCount();c<n;c++)try(var value=site.openCandidate(c)){
                    long row;try{probe.candidate(value,false);long reference=text.intern(probe);probe.candidate(value,true);row=addCandidate(order,reference,text.intern(probe));}finally{probe.clear();}
                    for(int s=0,count=value.supportCount();s<count;s++)addSupport(row,ordinal,c,s);
                }
            }
        }
        private void literalCandidates(PagedLongIndex order){
            if(occurrence.qualifications().isEmpty()||!occurrence.targetKind().equals("LITERAL"))return;
            boolean cics=occurrence.technology().equals("CICS"),supported=occurrence.nameProfile().equals(cics?CicsNameInterpreter.PROFILE:CallNameInterpreter.PROFILE);
            for(var raw:occurrence.literals()){
                resources.work(1,PHASE);if(!supported){state.interpretationOpen=true;continue;}
                var value=TargetResolver.interpret(cics,raw,false,cics?new io.github.gustavo2358.air.model.Interactions.ExtensionName("cics-ts.program","1"):io.github.gustavo2358.air.model.Interactions.ExactName.INSTANCE);
                state.interpretationOpen|=value.unknownRemainder()||!cics;if(value.referenceName()!=null)addCandidate(order,text.intern(value.referenceName()),text.intern(raw));
            }
        }
        private PagedDependencyFactStorage.SupportCursor support(long row){long at=row*SUPPORT;return facts.openSupport(Math.toIntExact(supports.get(at)),Math.toIntExact(supports.get(at+1)),Math.toIntExact(supports.get(at+2)));}
        private int compareSupport(long a,long b,boolean identity){
            if(a==b)return 0;try(var x=support(a);var y=support(b)){
                int c=identity?x.kind().compareTo(y.kind()):0;
                if(c==0)try(var first=x.producer();var second=y.producer()){c=PagedDependencyFactStorage.compareIdentity(first,second,left,right);}
                if(c==0)try(var first=x.origin();var second=y.origin()){c=PagedDependencyFactStorage.compareIdentity(first,second,left,right);}
                if(!identity)return c==0?Long.compare(a,b):c;
                for(int i=0,n=Math.min(x.premiseCount(),y.premiseCount());c==0&&i<n;i++)try(var first=x.premise(i);var second=y.premise(i)){c=PagedDependencyFactStorage.compareIdentity(first,second,left,right);}
                return c==0?Integer.compare(x.premiseCount(),y.premiseCount()):c;
            }
        }
        private void orderSupports(long row){long at=row*CANDIDATE;candidates.set(at+5,supportsCount);
            TargetResolver.orderSupportUnion(candidates.get(at+4)>1,candidates.get(at+2),value->supports.get(value*SUPPORT+3),(a,b)->compareSupport(a,b,true),(a,b)->compareSupport(a,b,false),value->supportOrder.set(supportsCount++,value),pages,resources,PHASE);
            candidates.set(at+6,supportsCount-candidates.get(at+5));
        }
        private final class TextProbe implements CharSequence {
            private PagedDependencyFactStorage.CandidateCursor candidate;
            private PagedDependencyFactStorage.TextView value;
            private boolean raw;
            private int length,start=-1,count;
            private final char[] block=new char[128];
            void candidate(PagedDependencyFactStorage.CandidateCursor candidate,boolean raw){clear();this.candidate=candidate;this.raw=raw;length=Math.toIntExact(raw?candidate.rawLength():candidate.referenceLength());}
            void text(PagedDependencyFactStorage.TextView value){clear();this.value=value;length=Math.toIntExact(value.characterCount());}
            void clear(){candidate=null;value=null;length=0;start=-1;count=0;raw=false;}
            @Override public int length(){return length;}
            @Override public char charAt(int index){Objects.checkIndex(index,length);if(start<0||index<start||index>=start+count){start=index;count=Math.min(block.length,length-start);if(value!=null)value.readCharacters(start,block,0,count);else if(raw)candidate.readRaw(start,block,0,count);else candidate.readReference(start,block,0,count);}return block[index-start];}
            @Override public CharSequence subSequence(int start,int end){throw new UnsupportedOperationException("no complete general program text projection");}
        }
        private <T> T read(java.util.function.Supplier<T> action){open();try{return action.get();}catch(IndexOutOfBoundsException invalid){throw invalid;}catch(RuntimeException|Error failure){abort(failure);throw failure;}}
        private void destination(char[] destination){if(destination==null){open();throw new NullPointerException("general program text destination");}}
        private long candidate(long ordinal){open();if(ordinal<0||ordinal>=candidatesCount)throw new IndexOutOfBoundsException("general candidate ordinal");return candidateOrder.get(ordinal)*CANDIDATE;}
        private long reason(long ordinal){open();if(ordinal<0||ordinal>=reasonsCount)throw new IndexOutOfBoundsException("general program reason ordinal");return reasons.get(ordinal);}
        public QualifiedDependencyOccurrence occurrence(){open();return occurrence;}
        public long siteCount(){open();return sitesCount;}
        public long candidateCount(){open();return candidatesCount;}
        public long referenceLength(long ordinal){return read(()->text.characterCount(candidates.get(candidate(ordinal))));}
        public long rawLength(long ordinal){return read(()->text.characterCount(candidates.get(candidate(ordinal)+1)));}
        public void readReference(long ordinal,long offset,char[] destination,int at,int count){destination(destination);read(()->{text.readCharacters(candidates.get(candidate(ordinal)),offset,destination,at,count);return 0;});}
        public void readRaw(long ordinal,long offset,char[] destination,int at,int count){destination(destination);read(()->{text.readCharacters(candidates.get(candidate(ordinal)+1),offset,destination,at,count);return 0;});}
        public long supportCount(long ordinal){return read(()->candidates.get(candidate(ordinal)+6));}
        public long reasonCount(){open();return reasonsCount;}
        public long reasonLength(long ordinal){return read(()->text.characterCount(reason(ordinal)));}
        public void readReason(long ordinal,long offset,char[] destination,int at,int count){destination(destination);read(()->{text.readCharacters(reason(ordinal),offset,destination,at,count);return 0;});}
        public int authorityCount(){open();return state.authorityCount();}
        public String authority(int ordinal){open();return state.authority(ordinal);}
        public boolean valueRemainder(){open();return state.valueOpen;}
        public boolean interpretationRemainder(){open();return state.interpretationOpen;}
        public SiteScope openSite(long ordinal){
            open();if(ordinal<0||ordinal>=sitesCount)throw new IndexOutOfBoundsException("general program site ordinal");AnalysisResources.Reservation funding=null;PagedDependencyFactStorage.FactCursor raw=null;
            try{funding=resources.reserve(AnalysisResources.Pool.SCRATCH,256,AnalysisResources.Phase.REPLAY);raw=fact(Math.toIntExact(siteRows.get(ordinal)));var scope=new SiteScope(raw,funding);raw=null;funding=null;scope.raw.onClose(scope::close);scope.funding.onOwnerClose(scope::close);scope.live();return scope;}
            catch(RuntimeException|Error failure){abort(failure);cleanup(raw,failure);cleanup(funding,failure);throw failure;}
        }
        public SupportScope openSupport(long candidate,long ordinal){
            open();AnalysisResources.Reservation funding=null;PagedDependencyFactStorage.SupportCursor raw=null;
            try{long at=candidate(candidate);if(ordinal<0||ordinal>=candidates.get(at+6))throw new IndexOutOfBoundsException("general program support ordinal");long row=supportOrder.get(candidates.get(at+5)+ordinal);
                funding=resources.reserve(AnalysisResources.Pool.SCRATCH,256,AnalysisResources.Phase.REPLAY);raw=support(row);var scope=new SupportScope(raw,funding);raw=null;funding=null;scope.raw.onClose(scope::close);scope.funding.onOwnerClose(scope::close);scope.live();return scope;}
            catch(IndexOutOfBoundsException invalid){cleanup(raw,invalid);cleanup(funding,invalid);throw invalid;}
            catch(RuntimeException|Error failure){abort(failure);cleanup(raw,failure);cleanup(funding,failure);throw failure;}
        }
        private void cleanup(AutoCloseable owned,Throwable primary){if(owned!=null)try{owned.close();}catch(Exception|Error failure){if(failure!=primary)primary.addSuppressed(failure);}}
        private void retire(AutoCloseable cursor,AnalysisResources.Reservation funding){
            Throwable failure=null;try{cursor.close();}catch(Exception|Error error){failure=error;}
            try{funding.close();}catch(RuntimeException|Error error){if(failure==null)failure=error;else if(error!=failure)failure.addSuppressed(error);}
            if(failure instanceof RuntimeException error)throw error;if(failure instanceof Error error)throw error;if(failure!=null)throw new IllegalStateException("general scope cleanup failed",failure);
        }
        public final class SiteScope implements AutoCloseable {
            private PagedDependencyFactStorage.FactCursor raw;
            private AnalysisResources.Reservation funding;
            private SiteScope previous,next;private boolean retired;
            private SiteScope(PagedDependencyFactStorage.FactCursor raw,AnalysisResources.Reservation funding){this.raw=raw;this.funding=funding;next=siteScopes;if(siteScopes!=null)siteScopes.previous=this;siteScopes=this;}
            private void live(){if(retired)throw new IllegalStateException("general program site scope expired");open();}
            public int candidateCount(){live();return raw.candidateCount();}
            public PagedDependencyFactStorage.CandidateCursor openCandidate(int ordinal){live();return raw.openCandidate(ordinal);}
            public PagedDependencyFactStorage.IdentityView entry(){live();return raw.entry();}
            public PagedDependencyFactStorage.IdentityView operation(){live();return raw.operation();}
            public DependencySiteFact.Reachability reachability(){live();return raw.reachability();}
            public PagedDependencyFactStorage.PointView valuePoint(){live();return raw.valuePoint();}
            public int premiseCount(){live();return raw.premiseCount();}
            public PagedDependencyFactStorage.IdentityView premise(int ordinal){live();return raw.premise(ordinal);}
            public int provenanceCount(){live();return raw.provenanceCount();}
            public PagedDependencyFactStorage.IdentityView provenance(int ordinal){live();return raw.provenance(ordinal);}
            @Override public void close(){if(retired)return;retired=true;if(previous==null)siteScopes=next;else previous.next=next;if(next!=null)next.previous=previous;previous=null;next=null;var cursor=raw;raw=null;var reservation=funding;funding=null;retire(cursor,reservation);}
        }
        public final class SupportScope implements AutoCloseable {
            private PagedDependencyFactStorage.SupportCursor raw;
            private AnalysisResources.Reservation funding;
            private SupportScope previous,next;private boolean retired;
            private SupportScope(PagedDependencyFactStorage.SupportCursor raw,AnalysisResources.Reservation funding){this.raw=raw;this.funding=funding;next=supportScopes;if(supportScopes!=null)supportScopes.previous=this;supportScopes=this;}
            private void live(){if(retired)throw new IllegalStateException("general program support scope expired");open();}
            public DependencySiteFact.SupportKind kind(){live();return raw.kind();}
            public PagedDependencyFactStorage.IdentityView producer(){live();return raw.producer();}
            public PagedDependencyFactStorage.IdentityView origin(){live();return raw.origin();}
            public int premiseCount(){live();return raw.premiseCount();}
            public PagedDependencyFactStorage.IdentityView premise(int ordinal){live();return raw.premise(ordinal);}
            @Override public void close(){if(retired)return;retired=true;if(previous==null)supportScopes=next;else previous.next=next;if(next!=null)next.previous=previous;previous=null;next=null;var cursor=raw;raw=null;var reservation=funding;funding=null;retire(cursor,reservation);}
        }
        @Override public void close(){
            if(retired)return;retired=true;complete=false;if(previous==null)programCursors=next;else previous.next=next;if(next!=null)next.previous=previous;previous=null;next=null;
            Throwable failure=null;
            while(siteScopes!=null)try{siteScopes.close();}catch(RuntimeException|Error cleanup){if(failure==null)failure=cleanup;else if(failure!=cleanup)failure.addSuppressed(cleanup);}
            while(supportScopes!=null)try{supportScopes.close();}catch(RuntimeException|Error cleanup){if(failure==null)failure=cleanup;else if(failure!=cleanup)failure.addSuppressed(cleanup);}
            if(probe!=null)probe.clear();
            for(var owned:new AutoCloseable[]{reasons,supportOrder,supports,candidateOrder,candidates,siteRows,text,capacity})if(owned!=null)try{owned.close();}catch(Exception|Error cleanup){if(failure==null)failure=cleanup;else if(failure!=cleanup)failure.addSuppressed(cleanup);}
            reasons=null;supportOrder=null;supports=null;candidateOrder=null;candidates=null;siteRows=null;text=null;capacity=null;probe=null;left=null;right=null;state=null;occurrence=null;
            if(failure instanceof RuntimeException error)throw error;if(failure instanceof Error error)throw error;
        }
    }
    /** SOURCE supplies its own paged program inventory and metric enrichment. */
    DependencyResult bindBase(DependencyResult base){live();occurrences=List.of();result=Objects.requireNonNull(base);ready=true;return result;}
    DependencyResult bind(DependencyResult base,DependencyInput.Qualification qualification){
        live();occurrences=qualification.occurrences();ready=true;
        try {
            var counts=new TreeMap<>(base.metrics());
            counts.put("sourceQualifiedResolvedByExistingQuery",0L);counts.put("qualifiedComputedOccurrences",qualification.occurrences().stream().filter(o->o.targetKind().equals("COMPUTED")&&(!o.qualifications().isEmpty()||!o.executableOperations().isEmpty())).count());
            counts.put("conditionalSourceValueRuns",0L);counts.put("conditionalSourceValueQueries",0L);counts.put("conditionalSourceWorkItems",0L);counts.put("conditionalSourceResourceLimits",0L);counts.put("targetResolutionRequests",(long)qualification.occurrences().size());
            var programs=DependencyResult.borrowedResult(qualification.occurrences().size(),i->{try(var projection=projectProgram(i)){return projection.resolution();}},this::available,()->false,()->false);
            result=new DependencyResult(base.publication(),base.airVersion(),siteView(),edgeView(),counts,base.publicationInventory(),base.origins(),base.artifacts(),base.sourceUncertaintyRefs(),base.analysisReasons(),base.fileDependencies(),base.sourceDependencies(),base.sourceQualifiedDependencies(),programs);return result;
        }catch(RuntimeException|Error failure){abort(failure);throw failure;}
    }
    List<DependencySiteFact> siteView(){return DependencyResult.borrowedResult(sites,this::detached,this::live,()->partialSites!=0,()->structural);}
    List<DependencyResult.Edge> edgeView(){return DependencyResult.borrowedResult(edges,this::edge,this::live,()->false,()->false);}
    public DependencyResult result(){available();return result;}
    public long fullFactProjections(){available();return projections;}
    public long typedSupportProjections(){available();return leafProjections;}
    /** Borrowed backend for caller-owned transient read-order tapes; never close
     * it with this result. Its native resources remain the same checked context. */
    public PageStore borrowedPages(){available();return pages;}
    public io.github.gustavo2358.air.model.Evidence.CoverageStatus siteCoverage(int site){
        available();Objects.checkIndex(site,sites);
        try{return facts.withHeader(factOrdinal(site),h->program.invokeCoverage(h.operation()).orElseThrow(()->new IllegalStateException("missing original site coverage")));}
        catch(RuntimeException|Error failure){abort(failure);throw failure;}
    }
    /** Field scopes read the canonical site without a complete fact projection.
     * The caller owns this leaf lifetime, never the borrowed AIR/backend. */
    public PagedDependencyFactStorage.FactCursor openFact(int site){
        available();Objects.checkIndex(site,sites);
        try{int ordinal=factOrdinal(site);int flags=(int)(annotations.get(ordinal)&7);return facts.openFact(ordinal).annotations(flags);}
        catch(IndexOutOfBoundsException invalid){throw invalid;}
        catch(RuntimeException|Error failure){abort(failure);throw failure;}
    }
    public PagedDependencyFactStorage.SupportProjection projectSupport(int site,int candidate,int support){
        available();Objects.checkIndex(site,sites);
        try{var projection=facts.projectSupport(factOrdinal(site),candidate,support);leafProjections=Math.incrementExact(leafProjections);return projection;}
        catch(IndexOutOfBoundsException invalid){throw invalid;}
        catch(RuntimeException|Error failure){abort(failure);throw failure;}
    }
    /** Bounded borrowed support/identity scopes. Facts owns their native expiry;
     * this port deliberately does not increment complete DTO projection counts. */
    public PagedDependencyFactStorage.SupportCursor openSupport(int site,int candidate,int support){
        available();Objects.checkIndex(site,sites);
        try{return facts.openSupport(factOrdinal(site),candidate,support);}
        catch(IndexOutOfBoundsException invalid){throw invalid;}
        catch(RuntimeException|Error failure){abort(failure);throw failure;}
    }
    /** Scoped random site access for SOURCE incidence; no detached DTO handoff. */
    public Cursor projectSite(int site){
        available();Objects.checkIndex(site,sites);var cursor=cursor();
        try{cursor.position=site-1;if(!cursor.advance())throw new IllegalStateException("missing indexed site");return cursor;}
        catch(RuntimeException|Error failure){abort(failure);throw failure;}
    }
    public Cursor cursor(){available();try{
        var capacity=resources.reserve(AnalysisResources.Pool.SCRATCH,512,AnalysisResources.Phase.REPLAY);
        Cursor cursor=null;
        try{cursor=new Cursor(capacity);cursor.next=cursors;if(cursors!=null)cursors.previous=cursor;cursors=cursor;capacity.onOwnerClose(cursor::close);cursor.open();return cursor;}
        catch(RuntimeException|Error failure){try{if(cursor!=null)cursor.close();else capacity.close();}catch(RuntimeException|Error cleanup){if(cleanup!=failure)failure.addSuppressed(cleanup);}throw failure;}
    }catch(RuntimeException|Error failure){abort(failure);throw failure;}}
    public final class ProgramProjection implements AutoCloseable {
        private AnalysisResources.Reservation capacity;
        private ArrayList<PagedDependencyFactStorage.Projection> leases=new ArrayList<>();
        private ArrayList<DependencySiteFact> selected=new ArrayList<>();
        private TargetResolver.Resolution resolution;
        private ProgramProjection previous,next;
        private boolean retired;
        private ProgramProjection(AnalysisResources.Reservation capacity){this.capacity=capacity;}
        private void open(){if(retired)throw new IllegalStateException("program projection expired");available();}
        public TargetResolver.Resolution resolution(){open();if(resolution==null)throw new IllegalStateException("program projection incomplete");return resolution;}
        @Override public void close(){
            if(retired)return;retired=true;if(previous==null)programProjections=next;else previous.next=next;if(next!=null)next.previous=previous;
            previous=null;next=null;resolution=null;selected=null;var owned=leases;leases=null;var reservation=capacity;capacity=null;Throwable failure=null;
            for(var lease:owned)try{lease.close();}catch(RuntimeException|Error cleanup){if(failure==null)failure=cleanup;else if(failure!=cleanup)failure.addSuppressed(cleanup);}
            try{reservation.close();}catch(RuntimeException|Error cleanup){if(failure==null)failure=cleanup;else if(failure!=cleanup)failure.addSuppressed(cleanup);}
            if(failure instanceof RuntimeException error)throw error;if(failure instanceof Error error)throw error;
        }
    }
    public final class Cursor implements AutoCloseable {
        private AnalysisResources.Reservation capacity;
        private PagedDependencyFactStorage.Projection projection;
        private DependencySiteFact fact;
        private Cursor previous,next;
        private int position=-1;
        private boolean retired;
        private Cursor(AnalysisResources.Reservation capacity){this.capacity=capacity;}
        private void open(){if(retired)throw new IllegalStateException("dependency cursor expired");available();}
        private void release(){fact=null;var owned=projection;projection=null;if(owned!=null)owned.close();}
        public boolean advance(){open();try{
            release();if(position>=sites)return false;if(++position>=sites)return false;
            int ordinal=Math.toIntExact(order.get(position*2L));projection=facts.project(ordinal);
            projections=Math.incrementExact(projections);fact=annotated(ordinal,projection.fact());return true;
        }catch(RuntimeException|Error failure){abort(failure);throw failure;}}
        public DependencySiteFact fact(){open();if(fact==null)throw new IllegalStateException("cursor has no current fact");return fact;}
        @Override public void close(){
            if(retired)return;retired=true;if(previous==null)cursors=next;else previous.next=next;if(next!=null)next.previous=previous;previous=null;next=null;
            try{release();}finally{var owned=capacity;capacity=null;if(owned!=null)owned.close();}
        }
    }
    private void abort(Throwable primary){try{close();}catch(RuntimeException|Error cleanup){if(cleanup!=primary)primary.addSuppressed(cleanup);}}
    @Override public void close(){
        if(closed)return;closed=true;ready=false;Throwable failure=null;
        while(programCursors!=null)try{programCursors.close();}catch(RuntimeException|Error cleanup){if(failure==null)failure=cleanup;else if(failure!=cleanup)failure.addSuppressed(cleanup);}
        while(programProjections!=null)try{programProjections.close();}catch(RuntimeException|Error cleanup){if(failure==null)failure=cleanup;else if(failure!=cleanup)failure.addSuppressed(cleanup);}
        while(cursors!=null)try{cursors.close();}catch(RuntimeException|Error cleanup){if(failure==null)failure=cleanup;else if(failure!=cleanup)failure.addSuppressed(cleanup);}
        for(var owned:new AutoCloseable[]{order,annotations,operations,index,facts,control})if(owned!=null)try{owned.close();}
            catch(Exception|Error cleanup){if(failure==null)failure=cleanup;else if(failure!=cleanup)failure.addSuppressed(cleanup);}
        order=null;annotations=null;operations=null;index=null;facts=null;control=null;program=null;result=null;resources=null;pages=null;occurrences=null;soughtEntry=null;soughtOperation=null;
        if(failure instanceof RuntimeException error)throw error;if(failure instanceof Error error)throw error;if(failure!=null)throw new IllegalStateException("managed executable cleanup",failure);
    }
}
