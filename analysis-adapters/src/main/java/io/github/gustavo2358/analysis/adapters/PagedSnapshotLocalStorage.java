package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.air.model.AirSnapshotBuilder;
import io.github.gustavo2358.air.validation.SnapshotLocalConstraints;
import io.github.gustavo2358.analysis.solver.*;
import java.util.Objects;

/** Exact required local facts/memberships over a borrowed run PageStore; misses retain no rows. */
public final class PagedSnapshotLocalStorage implements SnapshotLocalConstraints.Storage {
    private static final AnalysisResources.Phase PHASE=AnalysisResources.Phase.VALIDATION;
    private final AnalysisResources resources;
    private final AnalysisResources.Reservation resident;
    private CanonicalTupleArena rows;
    private long[] staged;
    private boolean closed,failed;
    public PagedSnapshotLocalStorage(PageStore pages,AnalysisResources resources) {
        Objects.requireNonNull(pages);this.resources=Objects.requireNonNull(resources);
        resident=resources.reserve(AnalysisResources.Pool.RESIDENT,256,PHASE);
        try {
            staged=new long[3];
            // Both snapshot handles and borrowed identity keys are literal words in this arena.
            rows=new CanonicalTupleArena(pages,resources,PHASE,3,new int[0]);
        } catch(RuntimeException|Error failure){try{close();}catch(RuntimeException|Error cleanup){if(cleanup!=failure)failure.addSuppressed(cleanup);}throw failure;}
    }
    private void fact(long source,int kind){positive(source);if(kind<0||kind>2)throw new IllegalArgumentException("unknown local collection predicate kind");staged[0]=0;staged[1]=source;staged[2]=kind;}
    private void member(long collection,long key){positive(collection);positive(key);staged[0]=1;staged[1]=collection;staged[2]=key;}
    @Override public synchronized boolean known(long source,int kind) {
        open();fact(source,kind);try{return rows.find(staged)!=0;}catch(RuntimeException|Error failure){failed=true;throw failure;}
    }
    @Override public synchronized void remember(long source,int kind) {
        open();fact(source,kind);try{rows.intern(staged);}catch(RuntimeException|Error failure){failed=true;throw failure;}
    }
    @Override public synchronized boolean addLabel(long collection,long key) {
        open();member(collection,key);
        try{if(rows.find(staged)!=0)return false;rows.intern(staged);return true;}
        catch(RuntimeException|Error failure){failed=true;throw failure;}
    }
    @Override public synchronized boolean containsLabel(long collection,long key) {
        open();member(collection,key);try{return rows.find(staged)!=0;}catch(RuntimeException|Error failure){failed=true;throw failure;}
    }
    @Override public synchronized AirSnapshotBuilder.Lease claim(long bytes) {
        open();try{var lease=resources.reserve(AnalysisResources.Pool.RESIDENT,bytes,PHASE);return lease::close;}
        catch(RuntimeException|Error failure){failed=true;throw failure;}
    }
    private static void positive(long value){if(value<=0)throw new IllegalArgumentException("positive source/key handle required");}
    private void open(){if(closed||failed)throw new IllegalStateException("paged local constraints storage is closed or aborted");}
    @Override public synchronized void close() {
        if(closed)return;closed=true;
        try{if(rows!=null)rows.close();}finally{rows=null;staged=null;resident.close();}
    }
}
