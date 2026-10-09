package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.air.model.AirSnapshotBuilder;
import io.github.gustavo2358.air.validation.SnapshotNominalCycles;
import io.github.gustavo2358.analysis.solver.*;
import java.util.NoSuchElementException;
import java.util.Objects;

/** Exact degree/reverse-occurrence relation and FIFO, borrowing the run page store. */
public final class PagedSnapshotCycleStorage implements SnapshotNominalCycles.Storage {
    private static final AnalysisResources.Phase PHASE=AnalysisResources.Phase.VALIDATION;
    private static final int WIDTH=2;
    private final AnalysisResources resources;
    private final AnalysisResources.Reservation resident;
    private PagedLongIndex index;
    private PagedLongArray rows,edges,queue;
    private long count,edgeCount,head,tail,currentNode,nextEdge,currentChild;
    private boolean scheduled,cursor,closed,failed;

    public PagedSnapshotCycleStorage(PageStore pages,AnalysisResources resources) {
        Objects.requireNonNull(pages);this.resources=Objects.requireNonNull(resources);
        resident=resources.reserve(AnalysisResources.Pool.RESIDENT,256,PHASE);
        try {
            index=new PagedLongIndex(pages,resources,PHASE);
            rows=new PagedLongArray(pages,Long.MAX_VALUE,resources,PHASE);
            edges=new PagedLongArray(pages,Long.MAX_VALUE,resources,PHASE);
            queue=new PagedLongArray(pages,Long.MAX_VALUE,resources,PHASE);
        } catch(RuntimeException|Error failure){closeSuppressed(failure);throw failure;}
    }
    @Override public synchronized void define(long key) {
        building();positive(key);
        try {
            if(index.find(key)!=0)throw new IllegalStateException("duplicate nominal cycle key");
            if(count==Long.MAX_VALUE/WIDTH)throw new IllegalStateException("nominal node address space exhausted");
            long ordinal=count+1;
            if(index.intern(key,ordinal)!=ordinal)throw new IllegalStateException("nominal key association changed");
            count=ordinal; // Missing degree/head cells are exactly zero; no empty payload allocation.
        } catch(RuntimeException|Error failure){failed=true;throw failure;}
    }
    private long row(long key) {
        positive(key);long ordinal=index.find(key);
        if(ordinal==0)throw new IllegalStateException("missing nominal cycle key");
        return (ordinal-1)*WIDTH;
    }
    @Override public synchronized void link(long parent,long child) {
        building();positive(parent);positive(child);
        try {
            long parentRow=row(parent),childRow=row(child),degree=rows.get(childRow);
            if(degree<0)throw new IllegalStateException("corrupt nominal degree");
            long increased=Math.incrementExact(degree);
            if(edgeCount==Long.MAX_VALUE/WIDTH)throw new IllegalStateException("nominal edge address space exhausted");
            long offset=edgeCount*WIDTH,previous=rows.get(parentRow+1),ordinal=edgeCount+1;
            edges.set(offset,child);edges.set(offset+1,previous);
            rows.set(parentRow+1,ordinal);rows.set(childRow,increased);edgeCount=ordinal;
        } catch(RuntimeException|Error failure){failed=true;throw failure;}
    }
    @Override public synchronized long degree(long key) {
        open();positive(key);
        try {long value=rows.get(row(key));if(value<0)throw new IllegalStateException("corrupt nominal degree");return value;}
        catch(RuntimeException|Error failure){failed=true;throw failure;}
    }
    @Override public synchronized long decrement(long key) {
        open();positive(key);
        try {
            long offset=row(key),value=rows.get(offset);
            if(value<=0)throw new IllegalStateException("nominal degree underflow");
            rows.set(offset,--value);return value;
        } catch(RuntimeException|Error failure){failed=true;throw failure;}
    }
    @Override public synchronized void enqueue(long key) {
        open();positive(key);
        try {
            if(rows.get(row(key))!=0)throw new IllegalStateException("nominal FIFO requires zero degree");
            scheduled=true;
            if(tail==Long.MAX_VALUE)throw new IllegalStateException("nominal FIFO address space exhausted");
            queue.set(tail,key);tail++;
        } catch(RuntimeException|Error failure){failed=true;throw failure;}
    }
    @Override public synchronized boolean advance() {
        open();
        try {
            if(cursor&&nextEdge!=0)throw new IllegalStateException("nominal reverse cursor not drained");
            scheduled=true;currentNode=0;
            if(head==tail)return false;
            long value=queue.get(head);
            if(value<=0)throw new IllegalStateException("corrupt nominal FIFO row");
            queue.set(head,0);head++;if(head==tail)head=tail=0;
            currentNode=value;return true;
        } catch(RuntimeException|Error failure){failed=true;throw failure;}
    }
    @Override public synchronized long node() {open();if(currentNode==0)throw new NoSuchElementException("no current nominal FIFO row");return currentNode;}
    @Override public synchronized void children(long key) {
        open();positive(key);
        try {
            if(cursor&&nextEdge!=0)throw new IllegalStateException("nominal reverse cursor not drained");
            long offset=row(key);nextEdge=rows.get(offset+1);rows.set(offset+1,0);
            cursor=true;currentChild=0;
        } catch(RuntimeException|Error failure){failed=true;throw failure;}
    }
    @Override public synchronized boolean advanceChild() {
        open();if(!cursor)throw new IllegalStateException("no selected nominal reverse cursor");
        try {
            currentChild=0;if(nextEdge==0)return false;
            if(nextEdge<0||nextEdge>edgeCount)throw new IllegalStateException("corrupt nominal edge address");
            long offset=(nextEdge-1)*WIDTH,child=edges.get(offset),next=edges.get(offset+1);
            if(child<=0||next<0||next>=nextEdge)throw new IllegalStateException("corrupt nominal reverse occurrence");
            edges.set(offset,0);edges.set(offset+1,0);
            nextEdge=next;currentChild=child;return true;
        } catch(RuntimeException|Error failure){failed=true;throw failure;}
    }
    @Override public synchronized long child(){open();if(currentChild==0)throw new NoSuchElementException("no current nominal reverse occurrence");return currentChild;}
    @Override public synchronized AirSnapshotBuilder.Lease claim(long bytes) {
        open();
        try{var lease=resources.reserve(AnalysisResources.Pool.RESIDENT,bytes,PHASE);return lease::close;}
        catch(RuntimeException|Error failure){failed=true;throw failure;}
    }
    private static void positive(long key){if(key<=0)throw new IllegalArgumentException("positive complete identity key required");}
    private void open(){if(closed||failed)throw new IllegalStateException("paged nominal cycle storage is closed or aborted");}
    private void building(){open();if(scheduled)throw new IllegalStateException("nominal relation construction is sealed");}
    private void closeSuppressed(Throwable failure){try{close();}catch(RuntimeException|Error cleanup){if(cleanup!=failure)failure.addSuppressed(cleanup);}}
    @Override public synchronized void close() {
        if(closed)return;closed=true;Throwable failure=null;
        try{if(index!=null)index.close();}catch(RuntimeException|Error cleanup){failure=cleanup;}
        try{if(rows!=null)rows.close();}catch(RuntimeException|Error cleanup){if(failure==null)failure=cleanup;else if(failure!=cleanup)failure.addSuppressed(cleanup);}
        try{if(edges!=null)edges.close();}catch(RuntimeException|Error cleanup){if(failure==null)failure=cleanup;else if(failure!=cleanup)failure.addSuppressed(cleanup);}
        try{if(queue!=null)queue.close();}catch(RuntimeException|Error cleanup){if(failure==null)failure=cleanup;else if(failure!=cleanup)failure.addSuppressed(cleanup);}
        index=null;rows=null;edges=null;queue=null;count=edgeCount=head=tail=currentNode=nextEdge=currentChild=0;cursor=false;resident.close();
        if(failure instanceof RuntimeException error)throw error;if(failure instanceof Error error)throw error;
    }
}
