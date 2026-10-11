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
    private AnalysisResources.Reservation control;
    private PagedDependencyFactStorage facts;
    private PagedLongIndex index,operations;
    private PagedLongArray annotations,order;
    private EntryId soughtEntry;
    private OperationId soughtOperation;
    private Cursor cursors;
    private ProgramProjection programProjections;
    private List<QualifiedDependencyOccurrence> occurrences;
    private DependencyResult result;
    private int sites,edges;
    private long projections,leafProjections,partialSites,candidateCount;
    private boolean structural,closed,ready;

    ManagedExecutableDependencyResult(SnapshotProgram program,PageStore pages,AnalysisResources resources){
        this.program=Objects.requireNonNull(program);this.resources=Objects.requireNonNull(resources);
        resources.requireExecutionProgram(program);program.admission();
        control=resources.reserve(AnalysisResources.Pool.RESIDENT,2048,AnalysisResources.Phase.DEMAND);
        try {
            facts=new PagedDependencyFactStorage(pages,resources);
            annotations=new PagedLongArray(pages,Long.MAX_VALUE,resources,AnalysisResources.Phase.DEMAND);
            order=new PagedLongArray(pages,Long.MAX_VALUE,resources,AnalysisResources.Phase.SORT);
            index=new PagedLongIndex(pages,resources,AnalysisResources.Phase.SORT,this::compare);
            operations=new PagedLongIndex(pages,resources,AnalysisResources.Phase.SORT,this::compareOperation);
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
    public PagedDependencyFactStorage.SupportProjection projectSupport(int site,int candidate,int support){
        available();Objects.checkIndex(site,sites);
        try{var projection=facts.projectSupport(factOrdinal(site),candidate,support);leafProjections=Math.incrementExact(leafProjections);return projection;}
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
        while(programProjections!=null)try{programProjections.close();}catch(RuntimeException|Error cleanup){if(failure==null)failure=cleanup;else if(failure!=cleanup)failure.addSuppressed(cleanup);}
        while(cursors!=null)try{cursors.close();}catch(RuntimeException|Error cleanup){if(failure==null)failure=cleanup;else if(failure!=cleanup)failure.addSuppressed(cleanup);}
        for(var owned:new AutoCloseable[]{order,annotations,operations,index,facts,control})if(owned!=null)try{owned.close();}
            catch(Exception|Error cleanup){if(failure==null)failure=cleanup;else if(failure!=cleanup)failure.addSuppressed(cleanup);}
        order=null;annotations=null;operations=null;index=null;facts=null;control=null;program=null;result=null;resources=null;occurrences=null;soughtEntry=null;soughtOperation=null;
        if(failure instanceof RuntimeException error)throw error;if(failure instanceof Error error)throw error;if(failure!=null)throw new IllegalStateException("managed executable cleanup",failure);
    }
}
