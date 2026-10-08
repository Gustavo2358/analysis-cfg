package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.air.model.AirSnapshotBuilder;
import io.github.gustavo2358.air.validation.SnapshotAnnotationTemplates;
import io.github.gustavo2358.analysis.solver.*;
import java.util.Objects;

/** Managed annotation recipe memo plus list-local text membership. */
public final class PagedSnapshotAnnotationStorage implements SnapshotAnnotationTemplates.Storage {
    private static final AnalysisResources.Phase PHASE=AnalysisResources.Phase.VALIDATION;
    private static final int WIDTH=2,STATUS=0,ROOT=1;
    private final AnalysisResources resources;
    private final AnalysisResources.Reservation resident;
    private CanonicalTupleArena recipes,members;
    private PagedLongArray metadata;
    private long[] staged;
    private boolean closed,failed;

    public PagedSnapshotAnnotationStorage(PageStore pages,AnalysisResources resources) {
        Objects.requireNonNull(pages);this.resources=Objects.requireNonNull(resources);
        resident=resources.reserve(AnalysisResources.Pool.RESIDENT,256,PHASE);
        try{
            staged=new long[2];recipes=new CanonicalTupleArena(pages,resources,PHASE,2,new int[0]);
            members=new CanonicalTupleArena(pages,resources,PHASE,2,new int[0]);
            metadata=new PagedLongArray(pages,Long.MAX_VALUE,resources,PHASE);
        }catch(RuntimeException|Error failure){closeSuppressed(failure);throw failure;}
    }
    private void key(long source,int kind){if(source<=0||kind<0)throw new IllegalArgumentException("positive annotation source and nonnegative recipe required");staged[0]=source;staged[1]=kind;}
    private static long base(long table){if(table<=0||table>Long.MAX_VALUE/WIDTH)throw new IllegalArgumentException("annotation table address capacity");return (table-1)*WIDTH;}
    private long table(long handle,boolean complete){long offset=base(handle);if(metadata.get(offset+STATUS)!=(complete?2:1))throw new IllegalStateException("annotation table is unknown/unfinished/finished");return offset;}
    @Override public synchronized long find(long source,int kind){open();try{key(source,kind);long handle=recipes.find(staged);if(handle!=0)table(handle,true);return handle;}catch(RuntimeException|Error failure){failed=true;throw failure;}}
    @Override public synchronized long begin(long source,int kind){open();try{key(source,kind);if(recipes.find(staged)!=0)throw new IllegalStateException("annotation table already exists");long handle=recipes.intern(staged);metadata.set(base(handle)+STATUS,1);return handle;}catch(RuntimeException|Error failure){failed=true;throw failure;}}
    @Override public synchronized void finish(long handle,long root){open();try{if(root<0)throw new IllegalArgumentException("nonnegative template root required");long offset=table(handle,false);metadata.set(offset+ROOT,root);metadata.set(offset+STATUS,2);}catch(RuntimeException|Error failure){failed=true;throw failure;}}
    @Override public synchronized long root(long handle){open();try{return metadata.get(table(handle,true)+ROOT);}catch(RuntimeException|Error failure){failed=true;throw failure;}}
    @Override public synchronized boolean first(long table,long textKey){open();try{if(table<=0||textKey<=0)throw new IllegalArgumentException("positive annotation item keys required");staged[0]=table;staged[1]=textKey;if(members.find(staged)!=0)return false;members.intern(staged);return true;}catch(RuntimeException|Error failure){failed=true;throw failure;}}
    @Override public synchronized AirSnapshotBuilder.Lease claim(long bytes){open();try{var lease=resources.reserve(AnalysisResources.Pool.RESIDENT,bytes,PHASE);return lease::close;}catch(RuntimeException|Error failure){failed=true;throw failure;}}
    private void open(){if(closed||failed)throw new IllegalStateException("paged annotation storage is closed or aborted");}
    private void closeSuppressed(Throwable failure){try{close();}catch(RuntimeException|Error cleanup){if(cleanup!=failure)failure.addSuppressed(cleanup);}}
    @Override public synchronized void close(){
        if(closed)return;closed=true;Throwable failure=null;
        try{if(recipes!=null)recipes.close();}catch(RuntimeException|Error cleanup){failure=cleanup;}
        try{if(members!=null)members.close();}catch(RuntimeException|Error cleanup){if(failure==null)failure=cleanup;else if(failure!=cleanup)failure.addSuppressed(cleanup);}
        try{if(metadata!=null)metadata.close();}catch(RuntimeException|Error cleanup){if(failure==null)failure=cleanup;else if(failure!=cleanup)failure.addSuppressed(cleanup);}
        recipes=members=null;metadata=null;staged=null;resident.close();
        if(failure instanceof RuntimeException error)throw error;if(failure instanceof Error error)throw error;
    }
}
