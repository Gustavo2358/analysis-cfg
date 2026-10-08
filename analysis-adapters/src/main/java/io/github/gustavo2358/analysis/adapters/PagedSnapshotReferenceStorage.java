package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.air.model.AirSnapshotBuilder;
import io.github.gustavo2358.air.validation.SnapshotReferenceLists;
import io.github.gustavo2358.analysis.solver.*;
import java.util.Objects;

/** Managed exact source/recipe memo; a finished zero root is distinct from missing/unfinished. */
public final class PagedSnapshotReferenceStorage implements SnapshotReferenceLists.Storage {
    private static final AnalysisResources.Phase PHASE=AnalysisResources.Phase.VALIDATION;
    private static final int WIDTH=2,STATUS=0,ROOT=1;
    private final AnalysisResources resources;
    private final AnalysisResources.Reservation resident;
    private CanonicalTupleArena keys;
    private PagedLongArray metadata;
    private long[] staged;
    private boolean closed,failed;

    public PagedSnapshotReferenceStorage(PageStore pages,AnalysisResources resources) {
        Objects.requireNonNull(pages);this.resources=Objects.requireNonNull(resources);
        resident=resources.reserve(AnalysisResources.Pool.RESIDENT,256,PHASE);
        try {
            staged=new long[2];
            // Both source/recipe words are literals. Memo roots also belong to the borrowed
            // diagnostic catalogue and must never be interpreted as keys-owned tuple edges.
            keys=new CanonicalTupleArena(pages,resources,PHASE,2,new int[0]);
            metadata=new PagedLongArray(pages,Long.MAX_VALUE,resources,PHASE);
        } catch(RuntimeException|Error failure){closeSuppressed(failure);throw failure;}
    }
    private void key(long source,int kind){if(source<=0||kind<0)throw new IllegalArgumentException("positive reference source and nonnegative recipe required");staged[0]=source;staged[1]=kind;}
    private static long base(long table){if(table<=0||table>Long.MAX_VALUE/WIDTH)throw new IllegalArgumentException("reference memo table address capacity");return (table-1)*WIDTH;}
    private long table(long table,boolean complete){long offset=base(table);if(metadata.get(offset+STATUS)!=(complete?2:1))throw new IllegalStateException("reference memo table is unknown/unfinished/finished");return offset;}
    @Override public synchronized long find(long source,int kind) {
        open();try{key(source,kind);long table=keys.find(staged);if(table!=0)table(table,true);return table;}
        catch(RuntimeException|Error failure){failed=true;throw failure;}
    }
    @Override public synchronized long begin(long source,int kind) {
        open();try{key(source,kind);if(keys.find(staged)!=0)throw new IllegalStateException("reference memo table already exists");long table=keys.intern(staged);metadata.set(base(table)+STATUS,1);return table;}
        catch(RuntimeException|Error failure){failed=true;throw failure;}
    }
    @Override public synchronized void finish(long handle,long root) {
        open();try{if(root<0)throw new IllegalArgumentException("nonnegative borrowed template root required");long offset=table(handle,false);metadata.set(offset+ROOT,root);metadata.set(offset+STATUS,2);}
        catch(RuntimeException|Error failure){failed=true;throw failure;}
    }
    @Override public synchronized long root(long handle) {
        open();try{long root=metadata.get(table(handle,true)+ROOT);if(root<0)throw new IllegalStateException("negative reference template root");return root;}
        catch(RuntimeException|Error failure){failed=true;throw failure;}
    }
    @Override public synchronized AirSnapshotBuilder.Lease claim(long bytes) {
        open();try{var lease=resources.reserve(AnalysisResources.Pool.RESIDENT,bytes,PHASE);return lease::close;}
        catch(RuntimeException|Error failure){failed=true;throw failure;}
    }
    private void open(){if(closed||failed)throw new IllegalStateException("paged reference memo is closed or aborted");}
    private void closeSuppressed(Throwable failure){try{close();}catch(RuntimeException|Error cleanup){if(cleanup!=failure)failure.addSuppressed(cleanup);}}
    @Override public synchronized void close() {
        if(closed)return;closed=true;Throwable failure=null;
        try{if(keys!=null)keys.close();}catch(RuntimeException|Error cleanup){failure=cleanup;}
        try{if(metadata!=null)metadata.close();}catch(RuntimeException|Error cleanup){if(failure==null)failure=cleanup;else if(failure!=cleanup)failure.addSuppressed(cleanup);}
        keys=null;metadata=null;staged=null;resident.close();
        if(failure instanceof RuntimeException error)throw error;if(failure instanceof Error error)throw error;
    }
}
