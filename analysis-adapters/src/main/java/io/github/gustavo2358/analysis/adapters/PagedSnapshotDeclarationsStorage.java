package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.air.model.AirSnapshotBuilder;
import io.github.gustavo2358.air.validation.SnapshotDeclarations;
import io.github.gustavo2358.analysis.solver.*;
import java.util.NoSuchElementException;
import java.util.Objects;

/**
 * Official AIR declaration rows and occurrence FIFO over the shared managed page runtime.
 * Required catalogue/index/frontier state spills; consumed FIFO rows release their pages.
 * No source-handle deduplication, Java model records, row arrays or resident maps are retained.
 * The snapshot index owns this port; the run still owns the borrowed PageStore.
 */
public final class PagedSnapshotDeclarationsStorage implements SnapshotDeclarations.Storage {
    private static final AnalysisResources.Phase PHASE=AnalysisResources.Phase.VALIDATION;
    private static final int FACTS=5,QUEUE_FIELDS=3;
    private final AnalysisResources resources;
    private final AnalysisResources.Reservation resident;
    private PagedLongIndex index;
    private PagedLongArray rows,frontier;
    private long count,head,tail,currentNode,currentOwner,currentDepth;
    private boolean closed,failed,frozen;

    public PagedSnapshotDeclarationsStorage(PageStore pages,AnalysisResources resources) {
        Objects.requireNonNull(pages);this.resources=Objects.requireNonNull(resources);
        resident=resources.reserve(AnalysisResources.Pool.RESIDENT,256,PHASE);
        try {
            index=new PagedLongIndex(pages,resources,PHASE);
            rows=new PagedLongArray(pages,Long.MAX_VALUE,resources,PHASE);
            frontier=new PagedLongArray(pages,Long.MAX_VALUE,resources,PHASE);
        } catch(RuntimeException|Error failure){closeSuppressed(failure);throw failure;}
    }
    @Override public synchronized boolean define(long key,long node,long identity,long unit,long sequence,long owner) {
        mutable();positive(key);positive(node);positive(identity);
        if(unit<0||sequence<0||owner<0)throw new IllegalArgumentException("nonnegative AIR association handles required");
        try {
            if(index.find(key)!=0)return false;
            if(count==Long.MAX_VALUE/FACTS)throw new IllegalStateException("AIR declaration address space exhausted");
            long offset=count*FACTS;
            rows.set(offset,node);rows.set(offset+1,identity);rows.set(offset+2,unit);
            rows.set(offset+3,sequence);rows.set(offset+4,owner);
            long ordinal=count+1;
            if(index.intern(key,ordinal)!=ordinal)throw new IllegalStateException("AIR declaration key changed during definition");
            count++;return true;
        } catch(RuntimeException|Error failure){failed=true;throw failure;}
    }
    @Override public synchronized long fact(long key,SnapshotDeclarations.Fact field) {
        open();positive(key);Objects.requireNonNull(field);
        try {
            long ordinal=index.find(key);return ordinal==0?0:rows.get((ordinal-1)*FACTS+field.ordinal());
        } catch(RuntimeException|Error failure){failed=true;throw failure;}
    }
    @Override public synchronized long declaration(long ordinal,SnapshotDeclarations.Fact field) {
        open();Objects.requireNonNull(field);
        if(ordinal<0||ordinal>=count)throw new IndexOutOfBoundsException("AIR declaration ordinal "+ordinal);
        try{return rows.get(ordinal*FACTS+field.ordinal());}
        catch(RuntimeException|Error failure){failed=true;throw failure;}
    }
    @Override public synchronized void enqueue(long node,long owner,long depth) {
        mutable();positive(node);positive(owner);
        if(depth<0)throw new IllegalArgumentException("nonnegative AIR operand depth required");
        try {
            if(tail==Long.MAX_VALUE/QUEUE_FIELDS)throw new IllegalStateException("AIR occurrence frontier address space exhausted");
            long offset=tail*QUEUE_FIELDS;
            frontier.set(offset,node);frontier.set(offset+1,owner);frontier.set(offset+2,depth);tail++;
        } catch(RuntimeException|Error failure){failed=true;throw failure;}
    }
    @Override public synchronized boolean advance() {
        mutable();
        try {
            currentNode=currentOwner=currentDepth=0;
            if(head==tail)return false;
            long offset=head*QUEUE_FIELDS;
            long node=frontier.get(offset),owner=frontier.get(offset+1),depth=frontier.get(offset+2);
            if(node<=0||owner<=0||depth<0)throw new IllegalStateException("incomplete AIR occurrence frontier row");
            frontier.set(offset,0);frontier.set(offset+1,0);frontier.set(offset+2,0);
            head++;if(head==tail)head=tail=0; // Addresses/payload follow live frontier, not historical visits.
            currentNode=node;currentOwner=owner;currentDepth=depth;return true;
        } catch(RuntimeException|Error failure){failed=true;throw failure;}
    }
    @Override public synchronized long node(){current();return currentNode;}
    @Override public synchronized long owner(){current();return currentOwner;}
    @Override public synchronized long depth(){current();return currentDepth;}
    @Override public synchronized void freeze() {
        mutable();if(head!=tail)throw new IllegalStateException("AIR occurrence frontier not drained");
        currentNode=currentOwner=currentDepth=0;frozen=true;
    }
    @Override public synchronized AirSnapshotBuilder.Lease claim(long bytes) {
        mutable();
        try {var capacity=resources.reserve(AnalysisResources.Pool.RESIDENT,bytes,PHASE);return capacity::close;}
        catch(RuntimeException|Error failure){failed=true;throw failure;}
    }
    private static void positive(long value){if(value<=0)throw new IllegalArgumentException("positive AIR handle/key required");}
    private void current(){open();if(currentNode==0)throw new NoSuchElementException("no current AIR frontier row");}
    private void open(){if(closed||failed)throw new IllegalStateException("paged AIR declaration storage is closed or aborted");}
    private void mutable(){open();if(frozen)throw new IllegalStateException("paged AIR declaration storage is frozen");}
    private void closeSuppressed(Throwable failure){try{close();}catch(RuntimeException|Error cleanup){if(cleanup!=failure)failure.addSuppressed(cleanup);}}
    @Override public synchronized void close() {
        if(closed)return;closed=true;Throwable failure=null;
        try{if(index!=null)index.close();}catch(RuntimeException|Error cleanup){failure=cleanup;}
        try{if(rows!=null)rows.close();}catch(RuntimeException|Error cleanup){if(failure==null)failure=cleanup;else if(failure!=cleanup)failure.addSuppressed(cleanup);}
        try{if(frontier!=null)frontier.close();}catch(RuntimeException|Error cleanup){if(failure==null)failure=cleanup;else if(failure!=cleanup)failure.addSuppressed(cleanup);}
        index=null;rows=null;frontier=null;currentNode=currentOwner=currentDepth=count=head=tail=0;resident.close();
        if(failure instanceof RuntimeException error)throw error;if(failure instanceof Error error)throw error;
    }
}
