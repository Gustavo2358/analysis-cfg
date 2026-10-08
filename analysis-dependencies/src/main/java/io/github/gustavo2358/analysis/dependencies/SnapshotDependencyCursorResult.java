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
    public record CursorCandidate(String referenceName,String rawValue,Iterable<DirectDependencyResult.Support> supports,
            Iterable<DirectDependencyResult.Support> orderedSupports) {
        public CursorCandidate{Objects.requireNonNull(referenceName);Objects.requireNonNull(rawValue);Objects.requireNonNull(supports);Objects.requireNonNull(orderedSupports);}
    }
    public record CursorSite(UnitId caller,EntryId entry,LabelId sequence,OperationId operation,OriginId siteOrigin,
            OriginId targetOrigin,Evidence.CoverageStatus coverage,String namespace,ObjectId subject,
            Iterable<CursorCandidate> candidates,boolean hasCandidates,boolean unknownRemainder) { }

    private final PublicationId publication;
    private final Evidence.InventoryStatus coverage;
    private DependencyProgramStore program;
    private SnapshotDependencyAnalysis.Storage storage;
    private boolean closed;

    SnapshotDependencyCursorResult(PublicationId publication,Evidence.InventoryStatus coverage,
            DependencyProgramStore program,SnapshotDependencyAnalysis.Storage storage) {
        this.publication=Objects.requireNonNull(publication);this.coverage=Objects.requireNonNull(coverage);
        this.program=Objects.requireNonNull(program);this.storage=Objects.requireNonNull(storage);
    }

    public PublicationId publication(){open();return publication;}
    public Evidence.InventoryStatus coverage(){open();return coverage;}
    public Iterable<Origins.Origin> cursorOrigins(){open();return ()->{open();storage.selectOrigins();return new Iterator<>(){
        private boolean prepared,available;public boolean hasNext(){open();if(!prepared){available=storage.advanceOrigin();prepared=true;}return available;}
        public Origins.Origin next(){if(!hasNext())throw new NoSuchElementException();prepared=false;return program.materializeOrigin(storage.originHandle());}
    };};}
    public Iterable<Origins.Artifact> cursorArtifacts(){open();return ()->{open();storage.selectArtifacts();return new Iterator<>(){
        private boolean prepared,available;public boolean hasNext(){open();if(!prepared){available=storage.advanceArtifact();prepared=true;}return available;}
        public Origins.Artifact next(){if(!hasNext())throw new NoSuchElementException();prepared=false;return program.materializeArtifact(storage.artifactHandle());}
    };};}

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
        return coverage!=Evidence.InventoryStatus.COMPLETE||storage.unknownRemainderCount()!=0;
    }

    public Metrics metrics() {
        open();return new Metrics(storage.callCount(),storage.candidateCount());
    }

    public DirectDependencyResult materialize() {
        open();var sites=new ArrayList<DirectDependencyResult.Site>();for(var site:sites())sites.add(site);
        var origins=new ArrayList<Origins.Origin>();for(var origin:cursorOrigins())origins.add(origin);
        var artifacts=new ArrayList<Origins.Artifact>();for(var artifact:cursorArtifacts())artifacts.add(artifact);
        return new DirectDependencyResult(publication,coverage,origins,artifacts,sites);
    }

    private CursorSite currentCursorSite() {
        long object=storage.callObjectKey(),candidateCount=storage.callCandidateCount();var candidates=candidates(object);
        var caller=new UnitId(publication,storage.callCaller());
        var entry=new EntryId(caller,storage.callEntry());
        var siteCoverage=storage.callCoverage();
        return new CursorSite(caller,entry,new LabelId(caller,storage.callSequence()),
            new OperationId(caller,storage.callOperation()),new OriginId(publication,storage.callSiteOrigin()),
            new OriginId(publication,storage.callTargetOrigin()),siteCoverage,storage.callNamespace(),
            new ObjectId(caller,storage.callSubject()),candidates,candidateCount!=0,
            coverage!=Evidence.InventoryStatus.COMPLETE||siteCoverage!=Evidence.CoverageStatus.MODELED||candidateCount==0);
    }

    private Iterable<CursorCandidate> candidates(long object){return ()->new Iterator<>(){
        private boolean prepared,available;
        {storage.select(object);}
        public boolean hasNext(){open();if(!prepared){available=storage.advance();prepared=true;}return available;}
        public CursorCandidate next(){if(!hasNext())throw new NoSuchElementException();prepared=false;long candidate=storage.candidate();String raw=storage.rawText();return new CursorCandidate(raw.stripTrailing(),raw,supports(candidate,false),supports(candidate,true));}
    };}
    private Iterable<DirectDependencyResult.Support> supports(long candidate,boolean ordered){return ()->new Iterator<>(){
        private boolean prepared,available;
        {if(ordered)storage.selectOrderedSupports(candidate);else storage.selectSupports(candidate);}
        public boolean hasNext(){open();if(!prepared){available=storage.advanceSupport();prepared=true;}return available;}
        public DirectDependencyResult.Support next(){if(!hasNext())throw new NoSuchElementException();prepared=false;return new DirectDependencyResult.Support(storage.supportProducer(),storage.supportOrigin(),List.of());}
    };}
    private static DirectDependencyResult.Candidate materialize(CursorCandidate candidate){var supports=new ArrayList<DirectDependencyResult.Support>();for(var support:candidate.supports())supports.add(support);return new DirectDependencyResult.Candidate(candidate.referenceName(),candidate.rawValue(),supports);}

    private void open(){if(closed)throw new IllegalStateException("snapshot dependency cursor result is closed");}
    @Override public void close() {
        if(closed)return;closed=true;Throwable failure=null;
        try{storage.close();}catch(RuntimeException|Error cleanup){failure=cleanup;}
        try{program.close();}catch(RuntimeException|Error cleanup){if(failure==null)failure=cleanup;else if(cleanup!=failure)failure.addSuppressed(cleanup);}
        storage=null;program=null;
        if(failure instanceof RuntimeException runtime)throw runtime;if(failure instanceof Error error)throw error;
    }
}
