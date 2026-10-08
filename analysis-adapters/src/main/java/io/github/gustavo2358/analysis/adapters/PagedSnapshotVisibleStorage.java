package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.air.model.AirSnapshotBuilder;
import io.github.gustavo2358.air.validation.SnapshotVisibleObjects;
import io.github.gustavo2358.analysis.solver.*;
import java.util.Objects;

/** Exact managed (UnitId,ObjectId) visibility pairs over the shared page runtime. */
public final class PagedSnapshotVisibleStorage implements SnapshotVisibleObjects.Storage {
    private static final AnalysisResources.Phase PHASE=AnalysisResources.Phase.VALIDATION;
    private final AnalysisResources resources;
    private final AnalysisResources.Reservation resident;
    private CanonicalTupleArena pairs,grounding;
    private long[] staged;
    private boolean closed,failed;

    public PagedSnapshotVisibleStorage(PageStore pages,AnalysisResources resources) {
        Objects.requireNonNull(pages);this.resources=Objects.requireNonNull(resources);
        resident=resources.reserve(AnalysisResources.Pool.RESIDENT,128,PHASE);
        try{staged=new long[2];pairs=new CanonicalTupleArena(pages,resources,PHASE,2,new int[0]);grounding=new CanonicalTupleArena(pages,resources,PHASE,1,new int[0]);}
        catch(RuntimeException|Error failure){closeSuppressed(failure);throw failure;}
    }
    private void key(long unit,long object){if(unit<=0||object<=0)throw new IllegalArgumentException("positive visibility keys required");staged[0]=unit;staged[1]=object;}
    @Override public synchronized boolean add(long unit,long object) {
        open();try{key(unit,object);if(pairs.find(staged)!=0)return false;pairs.intern(staged);return true;}
        catch(RuntimeException|Error failure){failed=true;throw failure;}
    }
    @Override public synchronized boolean contains(long unit,long object) {
        open();try{key(unit,object);return pairs.find(staged)!=0;}
        catch(RuntimeException|Error failure){failed=true;throw failure;}
    }
    @Override public synchronized boolean firstGrounding(long object) {
        open();try{if(object<=0)throw new IllegalArgumentException("positive object key required");long[] key={object};if(grounding.find(key)!=0)return false;grounding.intern(key);return true;}
        catch(RuntimeException|Error failure){failed=true;throw failure;}
    }
    @Override public synchronized AirSnapshotBuilder.Lease claim(long bytes) {
        open();try{var lease=resources.reserve(AnalysisResources.Pool.RESIDENT,bytes,PHASE);return lease::close;}
        catch(RuntimeException|Error failure){failed=true;throw failure;}
    }
    private void open(){if(closed||failed)throw new IllegalStateException("paged visibility storage is closed or aborted");}
    private void closeSuppressed(Throwable failure){try{close();}catch(RuntimeException|Error cleanup){if(cleanup!=failure)failure.addSuppressed(cleanup);}}
    @Override public synchronized void close(){if(closed)return;closed=true;Throwable failure=null;try{if(pairs!=null)pairs.close();}catch(RuntimeException|Error cleanup){failure=cleanup;}try{if(grounding!=null)grounding.close();}catch(RuntimeException|Error cleanup){if(failure==null)failure=cleanup;else if(failure!=cleanup)failure.addSuppressed(cleanup);}pairs=grounding=null;staged=null;resident.close();if(failure instanceof RuntimeException error)throw error;if(failure instanceof Error error)throw error;}
}
