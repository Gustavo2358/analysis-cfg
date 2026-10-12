package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.air.model.AirSnapshotBuilder;
import io.github.gustavo2358.air.validation.SnapshotIdentityKeys;
import io.github.gustavo2358.analysis.solver.AnalysisResources;
import io.github.gustavo2358.analysis.solver.CanonicalTupleArena;
import io.github.gustavo2358.analysis.solver.PageStore;
import io.github.gustavo2358.analysis.solver.PagedLongArray;
import java.util.Objects;

/**
 * Exact official AIR identity-key storage over the shared page runtime. Canonical key payload,
 * complete-tuple ordered lookup and source-handle memo all spill. Only fixed control/staging is
 * resident. The snapshot/index owner closes this port; the run still owns the borrowed PageStore.
 * This is an index backend, not a complete Validator or an input validation certificate.
 */
public final class PagedSnapshotIdentityStorage implements SnapshotIdentityKeys.Storage {
    private static final AnalysisResources.Phase PHASE=AnalysisResources.Phase.VALIDATION;
    private final AnalysisResources resources;
    private final AnalysisResources.Reservation resident;
    private CanonicalTupleArena tuples;
    private PagedLongArray memo;
    private long[] staged,cachedTuples;
    private int nextTuple;
    private boolean closed,failed;

    public PagedSnapshotIdentityStorage(PageStore store,AnalysisResources resources) {
        Objects.requireNonNull(store);this.resources=Objects.requireNonNull(resources);
        // Fixed sixteen complete seven-word keys plus canonical handles and staging/control.
        resident=resources.reserve(AnalysisResources.Pool.RESIDENT,1536,PHASE);
        try {
            staged=new long[7];cachedTuples=new long[128];
            tuples=new CanonicalTupleArena(store,resources,PHASE,7,new int[]{1,2});
            memo=new PagedLongArray(store,Long.MAX_VALUE,resources,PHASE);
        } catch(RuntimeException|Error failure) {
            try{close();}catch(RuntimeException|Error cleanup){if(cleanup!=failure)failure.addSuppressed(cleanup);}
            throw failure;
        }
    }
    @Override public synchronized long known(long sourceHandle) {
        open();positive(sourceHandle);
        try{return memo.get(sourceHandle-1);}catch(RuntimeException|Error failure){failed=true;throw failure;}
    }
    @Override public synchronized void remember(long sourceHandle,long key) {
        open();positive(sourceHandle);positive(key);
        try {
            // Every positive long maps bijectively to [0,Long.MAX_VALUE-1].
            // The radix directory is sparse; neither holes nor key magnitude allocate payload.
            long index=sourceHandle-1,previous=memo.get(index);
            if(previous==0)memo.set(index,key);
            else if(previous!=key)throw new IllegalStateException("immutable snapshot identity memo changed");
        } catch(RuntimeException|Error failure){failed=true;throw failure;}
    }
    @Override public synchronized long intern(long tag,long left,long right,long a,long b,long c,long d) {
        open();
        try {
            // This port never collects its private arena: cached handles remain valid until close.
            // Candidate eviction never retires required tuples. Equality checks all seven words.
            for(int slot=0;slot<128;slot+=8)if(cachedTuples[slot+7]!=0
                &&cachedTuples[slot]==tag&&cachedTuples[slot+1]==left&&cachedTuples[slot+2]==right
                &&cachedTuples[slot+3]==a&&cachedTuples[slot+4]==b&&cachedTuples[slot+5]==c&&cachedTuples[slot+6]==d) {
                resources.work(1,PHASE);return cachedTuples[slot+7];
            }
            staged[0]=tag;staged[1]=left;staged[2]=right;staged[3]=a;staged[4]=b;staged[5]=c;staged[6]=d;
            long key=tuples.intern(staged);int slot=nextTuple*8;
            System.arraycopy(staged,0,cachedTuples,slot,7);cachedTuples[slot+7]=key;
            nextTuple=(nextTuple+1)&15;return key;
        } catch(RuntimeException|Error failure){failed=true;throw failure;}
    }
    @Override public synchronized long word(long key,int column) {
        open();
        try{return tuples.field(key,column);}catch(RuntimeException|Error failure){failed=true;throw failure;}
    }
    @Override public synchronized AirSnapshotBuilder.Lease claim(long bytes) {
        open();
        try {var capacity=resources.reserve(AnalysisResources.Pool.RESIDENT,bytes,PHASE);return capacity::close;}
        catch(RuntimeException|Error failure){failed=true;throw failure;}
    }
    private static void positive(long value){if(value<=0)throw new IllegalArgumentException("positive snapshot handle/key required");}
    private void open(){if(closed||failed)throw new IllegalStateException("paged snapshot identity storage is closed or aborted");}
    @Override public synchronized void close() {
        if(closed)return;closed=true;Throwable failure=null;
        try{if(memo!=null)memo.close();}catch(RuntimeException|Error cleanup){failure=cleanup;}
        try{if(tuples!=null)tuples.close();}catch(RuntimeException|Error cleanup){if(failure==null)failure=cleanup;else if(failure!=cleanup)failure.addSuppressed(cleanup);}
        memo=null;tuples=null;staged=null;cachedTuples=null;resident.close();
        if(failure instanceof RuntimeException exception)throw exception;if(failure instanceof Error error)throw error;
    }
}
