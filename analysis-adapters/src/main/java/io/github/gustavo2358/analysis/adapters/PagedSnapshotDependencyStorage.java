package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.air.model.AirSnapshotBuilder;
import io.github.gustavo2358.analysis.dependencies.SnapshotDependencyAnalysis;
import io.github.gustavo2358.analysis.solver.*;
import java.util.NoSuchElementException;
import java.util.Objects;

/** Managed object-to-definition posting relation for the snapshot-native dependency slice. */
public final class PagedSnapshotDependencyStorage implements SnapshotDependencyAnalysis.Storage {
    private static final AnalysisResources.Phase PHASE=AnalysisResources.Phase.DOMAIN;private static final int FIELDS=4;
    private final AnalysisResources resources;private final AnalysisResources.Reservation resident;private PagedLongIndex objects;private PagedLongArray heads,rows;private long objectCount,count,cursor,raw,producer,origin;private boolean closed,failed;
    public PagedSnapshotDependencyStorage(PageStore pages,AnalysisResources resources){Objects.requireNonNull(pages);this.resources=Objects.requireNonNull(resources);resident=resources.reserve(AnalysisResources.Pool.RESIDENT,192,PHASE);try{objects=new PagedLongIndex(pages,resources,PHASE);heads=new PagedLongArray(pages,Long.MAX_VALUE,resources,PHASE);rows=new PagedLongArray(pages,Long.MAX_VALUE,resources,PHASE);}catch(RuntimeException|Error failure){closeSuppressed(failure);throw failure;}}
    public synchronized void add(long object,long text,long producer,long origin){open();try{long objectOrdinal=objects.find(object);if(objectOrdinal==0){if(objectCount==Long.MAX_VALUE)throw new IllegalStateException("dependency object cardinality exceeds signed 64-bit range");objectOrdinal=++objectCount;objects.intern(object,objectOrdinal);}if(count==Long.MAX_VALUE/FIELDS)throw new IllegalStateException("dependency definition cardinality exceeds paged row space");long previous=heads.get(objectOrdinal-1),ordinal=++count,at=(ordinal-1)*FIELDS;rows.set(at,text);rows.set(at+1,producer);rows.set(at+2,origin);rows.set(at+3,previous);heads.set(objectOrdinal-1,ordinal);}catch(RuntimeException|Error failure){failed=true;throw failure;}}
    public synchronized void select(long object){open();try{long objectOrdinal=objects.find(object);cursor=objectOrdinal==0?0:heads.get(objectOrdinal-1);raw=producer=origin=0;}catch(RuntimeException|Error failure){failed=true;throw failure;}}
    public synchronized boolean advance(){open();try{raw=producer=origin=0;if(cursor==0)return false;long at=(cursor-1)*FIELDS;raw=rows.get(at);producer=rows.get(at+1);origin=rows.get(at+2);cursor=rows.get(at+3);return true;}catch(RuntimeException|Error failure){failed=true;throw failure;}}
    private void current(){open();if(raw==0)throw new NoSuchElementException("no current definition");}public synchronized long rawText(){current();return raw;}public synchronized long producer(){current();return producer;}public synchronized long origin(){current();return origin;}
    public synchronized AirSnapshotBuilder.Lease claim(long bytes){open();try{var lease=resources.reserve(AnalysisResources.Pool.RESIDENT,bytes,PHASE);return lease::close;}catch(RuntimeException|Error failure){failed=true;throw failure;}}
    private void open(){if(closed||failed)throw new IllegalStateException("paged dependency definitions are closed or aborted");}private void closeSuppressed(Throwable failure){try{close();}catch(RuntimeException|Error cleanup){if(cleanup!=failure)failure.addSuppressed(cleanup);}}
    @Override public synchronized void close(){if(closed)return;closed=true;Throwable failure=null;try{if(objects!=null)objects.close();}catch(RuntimeException|Error cleanup){failure=cleanup;}try{if(heads!=null)heads.close();}catch(RuntimeException|Error cleanup){if(failure==null)failure=cleanup;else if(failure!=cleanup)failure.addSuppressed(cleanup);}try{if(rows!=null)rows.close();}catch(RuntimeException|Error cleanup){if(failure==null)failure=cleanup;else if(failure!=cleanup)failure.addSuppressed(cleanup);}objects=null;heads=null;rows=null;objectCount=count=cursor=raw=producer=origin=0;resident.close();if(failure instanceof RuntimeException error)throw error;if(failure instanceof Error error)throw error;}
}
