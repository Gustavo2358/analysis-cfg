package io.github.gustavo2358.analysis.application;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.UnitId;
import io.github.gustavo2358.analysis.consumers.SiteView;
import io.github.gustavo2358.analysis.plan.*;
import io.github.gustavo2358.analysis.query.*;
import io.github.gustavo2358.analysis.structure.AnalysisSession;
import io.github.gustavo2358.analysis.structure.ProgramStore;
import io.github.gustavo2358.analysis.structure.ProgramIndex;
import java.util.*;

/** Compiles each AIR-kind bucket once, then tests only interests registered for that bucket and owner. */
final class SitePlanner {
    private SitePlanner() { }
    private record Binding(String consumer, SiteInterest interest,int ordinal) { }
    static final Comparator<SiteView> SITE_ORDER = Comparator.comparing(SiteView::entry,AnalysisKey.ENTRY_ORDER)
        .thenComparing(s -> s.operationId().unit().publication().localId()).thenComparing(s -> s.operationId().unit().localId())
        .thenComparing(s -> s.operationId().localId());
    static <F> ExecutionPlan<F> compile(Object owner, long epoch, AnalysisSession session, AnalysisRegistry registry,
                                        List<ConsumerRegistration<F>> registrations,java.util.function.Consumer<AutoCloseable> own,Runnable lifetime) {
        var consumers = new TreeMap<String,ConsumerRegistration<F>>();
        var byKind = new TreeMap<Class<? extends Operation>,Map<UnitId,List<Binding>>>(Comparator.comparing(Class::getName));
        var sites = new TreeMap<String,TreeSet<SiteView>>();
        var nativeSites=new TreeMap<String,NativeSites>();
        var store=session.index().store();var budget=store.executionBudget();
        var directory=store.operationDirectory();var storage=store.storageInventory();
        boolean nativeSelection=budget.isPresent()&&directory.isPresent()&&storage.isPresent();
        var requests = new TreeMap<String,List<ObservationRequest<?,?>>>();
        var batches = new TreeMap<String,PendingBatch>();
        var keys = new TreeSet<AnalysisKey>(AnalysisKey.ORDER);
        var counts = new Counts("planningEpochs","candidateSites","structuralVisits","siteMatches","filterEvaluations",
            "planningCallbacks","queryRequests","uniqueQueries","observationBatchesPlanned","analysisRequests");
        counts.add("planningEpochs",1);
        for (var registration : registrations) {
            var dependency = registration.dependencies(); String id = dependency.consumerId();
            if (consumers.putIfAbsent(id,registration) != null) throw new IllegalArgumentException("duplicate consumer ID");
            if(nativeSelection){var selected=new NativeSites(session,registration,directory.orElseThrow(),storage.orElseThrow(),budget.orElseThrow(),lifetime);own.accept(selected);nativeSites.put(id,selected);}
            else sites.put(id,new TreeSet<>(SITE_ORDER));
            requests.put(id,new ArrayList<>(registration.requests()));
            for (var key : dependency.requiredAnalysisKeys()) {
                requireContext(session,key.entry()); registry.require(key); keys.add(key); counts.add("analysisRequests",1);
            }
            for (int ordinal=0;ordinal<registration.interests().size();ordinal++) {
                var interest=registration.interests().get(ordinal);
                requireContext(session,interest.entry());
                // A declaration binds the batch even when its bucket/filter produces no sites.
                for (var query : interest.queries()) bindBatch(batches,registry,dependency,query.batch());
                byKind.computeIfAbsent(interest.kind(),ignored -> new HashMap<>())
                    .computeIfAbsent(interest.entry().unit(),ignored -> new ArrayList<>()).add(new Binding(id,interest,ordinal));
            }
        }
        for (var bucket : byKind.entrySet()) {
            for (var candidate : session.index().sites(bucket.getKey())) {
                counts.add("structuralVisits",1); counts.add("candidateSites",1);
                var bindings = bucket.getValue().getOrDefault(candidate.owner().id(),List.of());
                for (var binding : bindings) {
                    var interest = binding.interest();
                    var site = new SiteView(interest.entry(),candidate.sequence().label(),candidate.offset(),candidate.operation());
                    counts.add("filterEvaluations",1);
                    if (!interest.filter().test(site)) continue;
                    if(nativeSelection)nativeSites.get(binding.consumer()).append(binding.ordinal(),site.operationId());
                    else if (sites.get(binding.consumer()).add(site)) counts.add("siteMatches",1);
                    for (var query : interest.queries()) {
                        counts.add("planningCallbacks",1); requests.get(binding.consumer()).add(query.at(site));
                    }
                }
            }
        }
        var localRequests = new TreeMap<String,Map<String,Set<PointQuery<?>>>>();
        for (var consumer : consumers.values()) {
            var dependencies = consumer.dependencies(); var requested = new TreeMap<String,Set<PointQuery<?>>>();
            for (var request : requests.get(dependencies.consumerId())) {
                var batch = request.batch();
                var pending = bindBatch(batches,registry,dependencies,batch);
                var local = requested.computeIfAbsent(batch.id(),ignored -> new HashSet<>());
                for (var query : request.queries()) {
                    if (!query.point().entry().equals(batch.analysisKey().entry()) || !batch.subjectType().isInstance(query.subject()))
                        throw new IllegalArgumentException("query/key Entry or subject binding mismatch");
                    pending.raw = Math.incrementExact(pending.raw); counts.add("queryRequests",1);
                    pending.queries.add(query); local.add(query);
                }
            }
            localRequests.put(dependencies.consumerId(),requested);
        }
        // Required IDs must resolve, including analysis-only and structural consumers with empty lists.
        for (var consumer : consumers.values()) for (String batchId : consumer.dependencies().requiredObservationBatchIds()) {
            var batch = batches.get(batchId);
            if (batch == null || !consumer.dependencies().requiredAnalysisKeys().contains(batch.id.analysisKey()))
                throw new IllegalArgumentException("missing or mismatched consumer batch dependency");
        }
        var frozen = new TreeMap<String,ExecutionPlan.BatchPlan>();
        for (var batch : batches.values()) {
            var ordered = new ArrayList<PointQuery<?>>(batch.queries);
            ordered.sort(queryOrder(registry.require(batch.id.analysisKey())));
            frozen.put(batch.id.id(),new ExecutionPlan.BatchPlan(batch.id,ordered,batch.raw));
            counts.add("uniqueQueries",ordered.size()); counts.add("observationBatchesPlanned",1);
        }
        var selected = new TreeMap<String,List<SiteView>>(); sites.forEach((id,values) -> selected.put(id,List.copyOf(values)));
        for(var entry:nativeSites.entrySet()){entry.getValue().finish();counts.add("siteMatches",entry.getValue().size());selected.put(entry.getKey(),entry.getValue());}
        return new ExecutionPlan<>(owner,epoch,List.copyOf(consumers.values()),selected,List.copyOf(keys),frozen,localRequests,counts.snapshot());
    }
    /** Frozen exact selection relation, not a decoded Operation catalogue.
     * Rows borrow registration Entry IDs and canonical admitted operation codes.
     * Existing typed external-order columns preserve SITE_ORDER and first-match
     * deduplication. One complete SiteView is explicit read compatibility; query
     * and observation payloads remain separate migration debt. */
    static final class NativeSites extends AbstractList<SiteView> implements RandomAccess,AutoCloseable {
        private AnalysisSession session;
        private ConsumerRegistration<?> registration;
        private ProgramStore.OperationDirectory directory;
        private ProgramStore.StorageInventory storage;
        private ProgramStore.CapacityLease control;
        private ProgramStore.OrdinalColumn rows,selection;
        private Runnable lifetime;
        private int matched,selected;
        private boolean frozen,closed;
        NativeSites(AnalysisSession session,ConsumerRegistration<?> registration,ProgramStore.OperationDirectory directory,
                ProgramStore.StorageInventory storage,ProgramStore.ExecutionBudget budget,Runnable lifetime){
            this.session=session;this.registration=registration;this.directory=directory;this.storage=storage;this.lifetime=lifetime;
            control=budget.reserve(ProgramStore.CapacityPool.RESIDENT,512,ProgramStore.ExecutionPhase.DEMAND);
            try{rows=storage.column(Long.MAX_VALUE);control.onOwnerClose(this::close);}
            catch(RuntimeException|Error failure){abort(failure);throw failure;}
        }
        private void available(){
            if(closed)throw new IllegalStateException("native planned site selection expired");
            try{lifetime.run();session.index().store().progress(ProgramStore.ExecutionPhase.DEMAND);}
            catch(RuntimeException|Error failure){abort(failure);throw failure;}
        }
        void append(int interest,io.github.gustavo2358.air.model.Ids.OperationId operation){
            available();if(frozen)throw new IllegalStateException("frozen native selection");
            try{int code=directory.ordinal(operation);if(code<0)throw new IllegalArgumentException("foreign selected operation");
                long at=matched*2L;rows.set(at,interest);rows.set(at+1,code);matched=Math.incrementExact(matched);
            }catch(RuntimeException|Error failure){abort(failure);throw failure;}
        }
        private io.github.gustavo2358.air.model.Ids.EntryId entry(long row){return registration.interests().get(Math.toIntExact(rows.get(row*2L))).entry();}
        private int compare(long first,long second){
            session.index().store().progress(ProgramStore.ExecutionPhase.SORT);
            int value=AnalysisKey.ENTRY_ORDER.compare(entry(first),entry(second));
            if(value!=0)return value;
            // SiteView's original ownership check proves equal Entries imply the
            // same full publication/unit. Retain complete local text,not a hash.
            return directory.identity(Math.toIntExact(rows.get(first*2L+1))).localId()
                .compareTo(directory.identity(Math.toIntExact(rows.get(second*2L+1))).localId());
        }
        void finish(){
            available();if(frozen)throw new IllegalStateException("native selection already frozen");
            if(matched==0){frozen=true;return;}
            try(var ordered=storage.order(matched,this::compare)){
                selection=storage.column(matched);long previous=-1;
                for(int at=0;at<matched;at++){long row=ordered.get(at);if(previous<0||compare(previous,row)!=0){selection.set(selected++,row);previous=row;}}
                frozen=true;
            }catch(RuntimeException|Error failure){abort(failure);throw failure;}
        }
        @Override public int size(){available();if(!frozen)throw new IllegalStateException("native selection incomplete");return selected;}
        @Override public SiteView get(int ordinal){
            Objects.checkIndex(ordinal,size());
            try{long row=selection.get(ordinal);var entry=entry(row);var identity=directory.identity(Math.toIntExact(rows.get(row*2L+1)));
                ProgramIndex.Site site=Objects.requireNonNull(session.index().site(identity),"changed admitted operation catalogue");
                return new SiteView(entry,site.sequence().label(),site.offset(),site.operation());
            }catch(RuntimeException|Error failure){abort(failure);throw failure;}
        }
        private void abort(Throwable primary){try{close();}catch(RuntimeException|Error cleanup){if(cleanup!=primary)primary.addSuppressed(cleanup);}}
        @Override public void close(){
            if(closed)return;closed=true;Throwable failure=null;
            for(var owned:new AutoCloseable[]{selection,rows,control})if(owned!=null)try{owned.close();}
                catch(Exception|Error cleanup){if(failure==null)failure=cleanup;else if(cleanup!=failure)failure.addSuppressed(cleanup);}
            selection=null;rows=null;control=null;session=null;registration=null;directory=null;storage=null;lifetime=null;
            if(failure instanceof RuntimeException error)throw error;if(failure instanceof Error error)throw error;
            if(failure!=null)throw new IllegalStateException("native site selection cleanup",failure);
        }
    }
    private static PendingBatch bindBatch(Map<String,PendingBatch> batches, AnalysisRegistry registry,
                                          ConsumerPlan dependencies, ObservationBatchId<?,?> batch) {
        registry.require(batch);
        if (!dependencies.requiredAnalysisKeys().contains(batch.analysisKey()) || !dependencies.requiredObservationBatchIds().contains(batch.id()))
            throw new IllegalArgumentException("request missing explicit consumer dependency");
        var pending = batches.computeIfAbsent(batch.id(),ignored -> new PendingBatch(batch));
        if (!pending.id.equals(batch)) throw new IllegalArgumentException("incompatible batch ID binding");
        return pending;
    }
    private static void requireContext(AnalysisSession session, io.github.gustavo2358.air.model.Ids.EntryId entry) {
        if (session.context(entry) == null) throw new IllegalArgumentException("Entry not selected in this session");
    }
    private static <T,V> Comparator<PointQuery<?>> queryOrder(AnalysisProvider<T,V> provider) {
        return Comparator.comparing(PointQuery<?>::point,ProgramPoint.ORDER)
            .thenComparing(q -> provider.subjectType().cast(q.subject()),provider.subjectOrder());
    }
    private static final class PendingBatch {
        final ObservationBatchId<?,?> id;
        final Set<PointQuery<?>> queries = new HashSet<>();
        long raw;
        PendingBatch(ObservationBatchId<?,?> id) { this.id = id; }
    }
}
