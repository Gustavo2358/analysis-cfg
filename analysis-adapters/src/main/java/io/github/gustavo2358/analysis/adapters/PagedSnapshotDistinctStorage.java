package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.air.model.AirSnapshotBuilder;
import io.github.gustavo2358.air.validation.SnapshotDistinctTuples;
import io.github.gustavo2358.analysis.solver.*;
import java.util.Objects;

/** Managed exact list-local four-column tuple set used during snapshot admission. */
public final class PagedSnapshotDistinctStorage implements SnapshotDistinctTuples.Storage {
    private static final AnalysisResources.Phase PHASE=AnalysisResources.Phase.VALIDATION;
    private final AnalysisResources resources;private final AnalysisResources.Reservation resident;
    private CanonicalTupleArena values;private long[] tuple;private boolean closed,failed;
    public PagedSnapshotDistinctStorage(PageStore pages,AnalysisResources resources){Objects.requireNonNull(pages);this.resources=Objects.requireNonNull(resources);resident=resources.reserve(AnalysisResources.Pool.RESIDENT,160,PHASE);try{tuple=new long[4];values=new CanonicalTupleArena(pages,resources,PHASE,4,new int[0]);}catch(RuntimeException|Error failure){closeSuppressed(failure);throw failure;}}
    public synchronized boolean first(long list,long family,long first,long second){open();try{tuple[0]=list;tuple[1]=family;tuple[2]=first;tuple[3]=second;if(values.find(tuple)!=0)return false;values.intern(tuple);return true;}catch(RuntimeException|Error failure){failed=true;throw failure;}}
    public synchronized AirSnapshotBuilder.Lease claim(long bytes){open();try{var lease=resources.reserve(AnalysisResources.Pool.RESIDENT,bytes,PHASE);return lease::close;}catch(RuntimeException|Error failure){failed=true;throw failure;}}
    private void open(){if(closed||failed)throw new IllegalStateException("paged distinct tuple storage is closed or aborted");}
    private void closeSuppressed(Throwable failure){try{close();}catch(RuntimeException|Error cleanup){if(cleanup!=failure)failure.addSuppressed(cleanup);}}
    @Override public synchronized void close(){if(closed)return;closed=true;Throwable failure=null;try{if(values!=null)values.close();}catch(RuntimeException|Error cleanup){failure=cleanup;}values=null;tuple=null;resident.close();if(failure instanceof RuntimeException error)throw error;if(failure instanceof Error error)throw error;}
}
