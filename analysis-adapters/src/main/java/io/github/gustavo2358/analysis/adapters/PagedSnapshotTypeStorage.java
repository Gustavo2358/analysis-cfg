package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.air.model.AirSnapshotBuilder;
import io.github.gustavo2358.air.validation.SnapshotTypes;
import io.github.gustavo2358.analysis.solver.*;
import java.util.Objects;

/** Managed operand type table and exact label-universe membership memo. */
public final class PagedSnapshotTypeStorage implements SnapshotTypes.Storage {
    private static final AnalysisResources.Phase PHASE=AnalysisResources.Phase.VALIDATION;private static final int FIELDS=2;
    private final AnalysisResources resources;private final AnalysisResources.Reservation resident;private PagedLongIndex index,knownLists;private PagedLongArray rows;private CanonicalTupleArena labels;private long[] pair;private long count;private boolean closed,failed;
    public PagedSnapshotTypeStorage(PageStore pages,AnalysisResources resources){Objects.requireNonNull(pages);this.resources=Objects.requireNonNull(resources);resident=resources.reserve(AnalysisResources.Pool.RESIDENT,256,PHASE);try{index=new PagedLongIndex(pages,resources,PHASE);knownLists=new PagedLongIndex(pages,resources,PHASE);rows=new PagedLongArray(pages,Long.MAX_VALUE,resources,PHASE);labels=new CanonicalTupleArena(pages,resources,PHASE,2,new int[0]);pair=new long[2];}catch(RuntimeException|Error failure){closeSuppressed(failure);throw failure;}}
    public synchronized void put(long key,long kind,long value){open();if(key<=0||kind<0||value<0)throw new IllegalArgumentException("invalid type row");try{if(index.find(key)!=0)throw new IllegalStateException("duplicate operand type");long ordinal=++count;rows.set((ordinal-1)*FIELDS,kind);rows.set((ordinal-1)*FIELDS+1,value);index.intern(key,ordinal);}catch(RuntimeException|Error failure){failed=true;throw failure;}}
    private long read(long key,int field){open();try{long ordinal=index.find(key);return ordinal==0?0:rows.get((ordinal-1)*FIELDS+field);}catch(RuntimeException|Error failure){failed=true;throw failure;}}
    public synchronized long kind(long key){return read(key,0);}public synchronized long value(long key){return read(key,1);}
    public synchronized boolean beginLabels(long list){open();try{if(knownLists.find(list)!=0)return false;knownLists.intern(list,list);return true;}catch(RuntimeException|Error failure){failed=true;throw failure;}}
    public synchronized void addLabel(long list,long key){open();try{pair[0]=list;pair[1]=key;if(labels.find(pair)==0)labels.intern(pair);}catch(RuntimeException|Error failure){failed=true;throw failure;}}
    public synchronized boolean containsLabel(long list,long key){open();try{pair[0]=list;pair[1]=key;return labels.find(pair)!=0;}catch(RuntimeException|Error failure){failed=true;throw failure;}}
    public synchronized AirSnapshotBuilder.Lease claim(long bytes){open();try{var lease=resources.reserve(AnalysisResources.Pool.RESIDENT,bytes,PHASE);return lease::close;}catch(RuntimeException|Error failure){failed=true;throw failure;}}
    private void open(){if(closed||failed)throw new IllegalStateException("paged type storage is closed or aborted");}private void closeSuppressed(Throwable failure){try{close();}catch(RuntimeException|Error cleanup){if(cleanup!=failure)failure.addSuppressed(cleanup);}}
    @Override public synchronized void close(){if(closed)return;closed=true;Throwable failure=null;for(AutoCloseable item:new AutoCloseable[]{index,knownLists,rows,labels})try{if(item!=null)item.close();}catch(Exception cleanup){if(failure==null)failure=cleanup;else if(failure!=cleanup)failure.addSuppressed(cleanup);}index=knownLists=null;rows=null;labels=null;pair=null;count=0;resident.close();if(failure instanceof RuntimeException error)throw error;if(failure instanceof Error error)throw error;if(failure!=null)throw new IllegalStateException(failure);}
}
