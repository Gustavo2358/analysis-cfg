package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.air.model.AirSnapshotBuilder;
import io.github.gustavo2358.air.validation.SnapshotContextWalk;
import io.github.gustavo2358.analysis.solver.*;
import java.util.NoSuchElementException;
import java.util.Objects;

/** Spillable exact contextual worklist with consumed frontier release. */
public final class PagedSnapshotContextStorage implements SnapshotContextWalk.Storage {
    private static final AnalysisResources.Phase PHASE=AnalysisResources.Phase.VALIDATION;private static final int FIELDS=5;
    private final AnalysisResources resources;private final AnalysisResources.Reservation resident;
    private CanonicalTupleArena seen;private PagedLongArray queue;private long[] tuple;private long head,tail,node,owner,kind,context,depth;private boolean closed,failed;
    public PagedSnapshotContextStorage(PageStore pages,AnalysisResources resources){Objects.requireNonNull(pages);this.resources=Objects.requireNonNull(resources);resident=resources.reserve(AnalysisResources.Pool.RESIDENT,256,PHASE);try{seen=new CanonicalTupleArena(pages,resources,PHASE,4,new int[0]);queue=new PagedLongArray(pages,Long.MAX_VALUE,resources,PHASE);tuple=new long[4];}catch(RuntimeException|Error failure){closeSuppressed(failure);throw failure;}}
    public synchronized boolean schedule(long node,long owner,long kind,long context,long depth){open();try{tuple[0]=node;tuple[1]=owner;tuple[2]=kind;tuple[3]=context;if(seen.find(tuple)!=0)return false;seen.intern(tuple);long at=tail*FIELDS;queue.set(at,node);queue.set(at+1,owner);queue.set(at+2,kind);queue.set(at+3,context);queue.set(at+4,depth);tail++;return true;}catch(RuntimeException|Error failure){failed=true;throw failure;}}
    public synchronized boolean advance(){open();try{node=owner=kind=context=depth=0;if(head==tail)return false;long at=head*FIELDS;node=queue.get(at);owner=queue.get(at+1);kind=queue.get(at+2);context=queue.get(at+3);depth=queue.get(at+4);for(int i=0;i<FIELDS;i++)queue.set(at+i,0);head++;if(head==tail)head=tail=0;return true;}catch(RuntimeException|Error failure){failed=true;throw failure;}}
    private void current(){open();if(node==0)throw new NoSuchElementException("no current contextual work item");}
    public synchronized long node(){current();return node;}public synchronized long owner(){current();return owner;}public synchronized long kind(){current();return kind;}public synchronized long context(){current();return context;}public synchronized long depth(){current();return depth;}
    public synchronized AirSnapshotBuilder.Lease claim(long bytes){open();try{var lease=resources.reserve(AnalysisResources.Pool.RESIDENT,bytes,PHASE);return lease::close;}catch(RuntimeException|Error failure){failed=true;throw failure;}}
    private void open(){if(closed||failed)throw new IllegalStateException("paged contextual storage is closed or aborted");}private void closeSuppressed(Throwable failure){try{close();}catch(RuntimeException|Error cleanup){if(cleanup!=failure)failure.addSuppressed(cleanup);}}
    @Override public synchronized void close(){if(closed)return;closed=true;Throwable failure=null;try{if(seen!=null)seen.close();}catch(RuntimeException|Error cleanup){failure=cleanup;}try{if(queue!=null)queue.close();}catch(RuntimeException|Error cleanup){if(failure==null)failure=cleanup;else if(failure!=cleanup)failure.addSuppressed(cleanup);}seen=null;queue=null;tuple=null;node=owner=kind=context=depth=head=tail=0;resident.close();if(failure instanceof RuntimeException error)throw error;if(failure instanceof Error error)throw error;}
}
