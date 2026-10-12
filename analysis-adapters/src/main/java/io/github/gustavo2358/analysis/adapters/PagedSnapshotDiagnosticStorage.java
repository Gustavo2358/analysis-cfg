package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.air.model.AirSnapshotBuilder;
import io.github.gustavo2358.air.validation.SnapshotDiagnosticTemplates;
import io.github.gustavo2358.analysis.solver.*;
import java.util.Objects;

/** Immutable exact diagnostic sequence tuples; only LEFT/RIGHT reference this owned catalogue. */
public final class PagedSnapshotDiagnosticStorage implements SnapshotDiagnosticTemplates.Storage {
    private static final AnalysisResources.Phase PHASE=AnalysisResources.Phase.VALIDATION;
    private final AnalysisResources resources;
    private final AnalysisResources.Reservation resident;
    private CanonicalTupleArena tuples;
    private boolean closed,failed;
    public PagedSnapshotDiagnosticStorage(PageStore pages,AnalysisResources resources) {
        Objects.requireNonNull(pages);this.resources=Objects.requireNonNull(resources);
        resident=resources.reserve(AnalysisResources.Pool.RESIDENT,256,PHASE);
        try{tuples=new CanonicalTupleArena(pages,resources,PHASE,14,new int[]{0,1});}
        catch(RuntimeException|Error failure){try{close();}catch(RuntimeException|Error cleanup){if(cleanup!=failure)failure.addSuppressed(cleanup);}throw failure;}
    }
    @Override public synchronized long intern(long[] tuple) {
        open();try{return tuples.intern(tuple);}catch(RuntimeException|Error failure){failed=true;throw failure;}
    }
    @Override public synchronized long word(long handle,SnapshotDiagnosticTemplates.Word field) {
        open();try{return tuples.field(handle,Objects.requireNonNull(field).ordinal());}catch(RuntimeException|Error failure){failed=true;throw failure;}
    }
    @Override public synchronized AirSnapshotBuilder.Lease claim(long bytes) {
        open();try{var lease=resources.reserve(AnalysisResources.Pool.RESIDENT,bytes,PHASE);return lease::close;}
        catch(RuntimeException|Error failure){failed=true;throw failure;}
    }
    private void open(){if(closed||failed)throw new IllegalStateException("paged diagnostic storage is closed or aborted");}
    @Override public synchronized void close() {
        if(closed)return;closed=true;try{if(tuples!=null)tuples.close();}finally{tuples=null;resident.close();}
    }
}
