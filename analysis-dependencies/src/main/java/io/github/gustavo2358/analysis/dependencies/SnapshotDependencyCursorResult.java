package io.github.gustavo2358.analysis.dependencies;

import io.github.gustavo2358.air.model.Evidence;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.air.model.Origins;
import java.util.*;

/**
 * Leased snapshot dependency result. Sites are ordered in the managed store and materialized one
 * at a time; callers that require a detached object graph must opt in through {@link #materialize()}.
 */
public final class SnapshotDependencyCursorResult implements AutoCloseable {
    public record Metrics(long sites,long candidates) { }
    public record CursorCandidate(String referenceName,String rawValue,List<DirectDependencyResult.Support> supports) {
        public CursorCandidate{Objects.requireNonNull(referenceName);Objects.requireNonNull(rawValue);supports=List.copyOf(supports);}
    }
    public record CursorSite(UnitId caller,EntryId entry,LabelId sequence,OperationId operation,OriginId siteOrigin,
            OriginId targetOrigin,Evidence.CoverageStatus coverage,String namespace,ObjectId subject,
            Iterable<CursorCandidate> candidates,boolean unknownRemainder) { }

    private final PublicationId publication;
    private final Evidence.InventoryStatus coverage;
    private final List<Origins.Origin> origins;
    private final List<Origins.Artifact> artifacts;
    private DependencyProgramStore program;
    private SnapshotDependencyAnalysis.Storage storage;
    private boolean closed;

    SnapshotDependencyCursorResult(PublicationId publication,Evidence.InventoryStatus coverage,
            List<Origins.Origin> origins,List<Origins.Artifact> artifacts,
            DependencyProgramStore program,SnapshotDependencyAnalysis.Storage storage) {
        this.publication=Objects.requireNonNull(publication);this.coverage=Objects.requireNonNull(coverage);
        this.origins=List.copyOf(origins);this.artifacts=List.copyOf(artifacts);
        this.program=Objects.requireNonNull(program);this.storage=Objects.requireNonNull(storage);
    }

    public PublicationId publication(){open();return publication;}
    public Evidence.InventoryStatus coverage(){open();return coverage;}
    public List<Origins.Origin> origins(){open();return origins;}
    public List<Origins.Artifact> artifacts(){open();return artifacts;}

    /** Each iterator owns the store's current site cursor; iterators must not be interleaved. */
    public Iterable<CursorSite> cursorSites() {
        open();
        return ()-> {
            open();storage.selectCalls();
            return new Iterator<>() {
                private boolean prepared,available;
                @Override public boolean hasNext(){open();if(!prepared){available=storage.advanceCall();prepared=true;}return available;}
                @Override public CursorSite next(){if(!hasNext())throw new NoSuchElementException();prepared=false;return currentCursorSite();}
            };
        };
    }

    /** Explicit compatibility materialization, bounded only by one site rather than the whole result. */
    public Iterable<DirectDependencyResult.Site> sites() {return ()->{var source=cursorSites().iterator();return new Iterator<>(){
        public boolean hasNext(){return source.hasNext();}
        public DirectDependencyResult.Site next(){var site=source.next();var candidates=new ArrayList<DirectDependencyResult.Candidate>();for(var candidate:site.candidates())candidates.add(materialize(candidate));
            return new DirectDependencyResult.Site(site.caller(),site.entry(),site.sequence(),site.operation(),site.siteOrigin(),site.targetOrigin(),site.coverage(),site.namespace(),site.subject(),candidates,site.unknownRemainder());}
    };};}

    public boolean partial() {
        open();
        if(coverage!=Evidence.InventoryStatus.COMPLETE)return true;
        for(var site:cursorSites())if(site.unknownRemainder())return true;
        return false;
    }

    public Metrics metrics() {
        open();long candidates=0;
        for(var site:cursorSites())for(var ignored:site.candidates())candidates=Math.addExact(candidates,1);
        return new Metrics(storage.callCount(),candidates);
    }

    public DirectDependencyResult materialize() {
        open();var sites=new ArrayList<DirectDependencyResult.Site>();for(var site:sites())sites.add(site);
        return new DirectDependencyResult(publication,coverage,origins,artifacts,sites);
    }

    private CursorSite currentCursorSite() {
        long object=storage.callObjectKey();var candidates=candidates(object);boolean empty=!candidates.iterator().hasNext();
        var caller=new UnitId(publication,storage.callCaller());
        var entry=new EntryId(caller,storage.callEntry());
        var siteCoverage=storage.callCoverage();
        return new CursorSite(caller,entry,new LabelId(caller,storage.callSequence()),
            new OperationId(caller,storage.callOperation()),new OriginId(publication,storage.callSiteOrigin()),
            new OriginId(publication,storage.callTargetOrigin()),siteCoverage,storage.callNamespace(),
            new ObjectId(caller,storage.callSubject()),candidates,
            coverage!=Evidence.InventoryStatus.COMPLETE||siteCoverage!=Evidence.CoverageStatus.MODELED||empty);
    }

    private Iterable<CursorCandidate> candidates(long object){return ()->new Iterator<>(){
        private Row pending;private boolean prepared,available;
        {storage.select(object);}
        public boolean hasNext(){open();if(!prepared){pending=read();available=pending!=null;prepared=true;}return available;}
        public CursorCandidate next(){if(!hasNext())throw new NoSuchElementException();String raw=pending.raw();var supports=new LinkedHashMap<DirectDependencyResult.Support,DirectDependencyResult.Support>();
            add(supports,pending.supports());pending=null;
            while(true){var row=read();if(row==null){available=false;break;}if(!row.raw().equals(raw)){pending=row;available=true;break;}add(supports,row.supports());}
            prepared=true;return new CursorCandidate(raw.stripTrailing(),raw,List.copyOf(supports.values()));}
    };}
    private Row read(){if(!storage.advance())return null;var supports=new ArrayList<DirectDependencyResult.Support>(storage.producerCount());for(int i=0;i<storage.producerCount();i++)supports.add(new DirectDependencyResult.Support(program.operationId(storage.producer(i)),program.originId(storage.origin(i)),List.of()));return new Row(storage.rawText(),supports);}
    private static void add(Map<DirectDependencyResult.Support,DirectDependencyResult.Support> target,List<DirectDependencyResult.Support> supports){for(var support:supports)target.put(support,support);}
    private static DirectDependencyResult.Candidate materialize(CursorCandidate candidate){return new DirectDependencyResult.Candidate(candidate.referenceName(),candidate.rawValue(),candidate.supports());}
    private record Row(String raw,List<DirectDependencyResult.Support> supports) { }

    private void open(){if(closed)throw new IllegalStateException("snapshot dependency cursor result is closed");}
    @Override public void close() {
        if(closed)return;closed=true;Throwable failure=null;
        try{storage.close();}catch(RuntimeException|Error cleanup){failure=cleanup;}
        try{program.close();}catch(RuntimeException|Error cleanup){if(failure==null)failure=cleanup;else if(cleanup!=failure)failure.addSuppressed(cleanup);}
        storage=null;program=null;
        if(failure instanceof RuntimeException runtime)throw runtime;if(failure instanceof Error error)throw error;
    }
}
