package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.air.model.AirSnapshotBuilder;
import io.github.gustavo2358.air.validation.SnapshotGraphWalk;
import io.github.gustavo2358.analysis.solver.*;
import java.util.Objects;

/** Managed primitive graph frontier and exact source/context memo, borrowing the run PageStore. */
public final class PagedSnapshotGraphStorage implements SnapshotGraphWalk.Storage {
    private static final AnalysisResources.Phase PHASE=AnalysisResources.Phase.VALIDATION;
    private static final int WIDTH=4;
    private final AnalysisResources resources;
    private final AnalysisResources.Reservation resident;
    private PagedLongArray tasks;
    private PagedLongIndex ancestry,completed;
    private CanonicalTupleArena contexts;
    private long[] staged;
    private long size,currentNode,currentDepth;
    private int currentElement;
    private boolean currentExit,closed,failed;

    public PagedSnapshotGraphStorage(PageStore pages,AnalysisResources resources) {
        Objects.requireNonNull(pages);this.resources=Objects.requireNonNull(resources);
        resident=resources.reserve(AnalysisResources.Pool.RESIDENT,256,PHASE);
        try {
            staged=new long[2];tasks=new PagedLongArray(pages,Long.MAX_VALUE,resources,PHASE);
            ancestry=new PagedLongIndex(pages,resources,PHASE);completed=new PagedLongIndex(pages,resources,PHASE);
            // Source handles are literal words, never references into the canonical context arena.
            contexts=new CanonicalTupleArena(pages,resources,PHASE,2,new int[0]);
        } catch(RuntimeException|Error failure) {
            try{close();}catch(RuntimeException|Error cleanup){if(cleanup!=failure)failure.addSuppressed(cleanup);}
            throw failure;
        }
    }
    @Override public synchronized long size(){open();return size;}
    @Override public synchronized void push(long node,int element,long depth,boolean exit) {
        open();positive(node);if(element<0||depth<=0)throw new IllegalArgumentException("positive depth and nonnegative context required");
        try {
            if(size==Long.MAX_VALUE/WIDTH)throw new IllegalStateException("graph frontier address space exhausted");
            long base=size*WIDTH;tasks.set(base,node);tasks.set(base+1,element);tasks.set(base+2,depth);tasks.set(base+3,exit?1:0);size++;
        } catch(RuntimeException|Error failure){failed=true;throw failure;}
    }
    @Override public synchronized void reverse(long from) {
        open();if(from<0||from>size)throw new IndexOutOfBoundsException("graph frontier segment");
        try {
            for(long left=from,right=size-1;left<right;left++,right--)
                for(int column=0;column<WIDTH;column++) {
                    long a=left*WIDTH+column,b=right*WIDTH+column,value=tasks.get(a),other=tasks.get(b);
                    tasks.set(a,other);tasks.set(b,value);
                }
        } catch(RuntimeException|Error failure){failed=true;throw failure;}
    }
    @Override public synchronized boolean advance() {
        open();
        try {
            if(size==0){currentNode=currentDepth=0;currentElement=0;currentExit=false;return false;}
            long base=(size-1)*WIDTH;
            long node=tasks.get(base),element=tasks.get(base+1),depth=tasks.get(base+2),exit=tasks.get(base+3);
            if(node<=0||element<0||element>Integer.MAX_VALUE||depth<=0||exit<0||exit>1)
                throw new IllegalStateException("corrupt graph frontier row");
            for(int column=0;column<WIDTH;column++)tasks.set(base+column,0);
            size--;currentNode=node;currentElement=(int)element;currentDepth=depth;currentExit=exit!=0;return true;
        } catch(RuntimeException|Error failure){failed=true;throw failure;}
    }
    private void current(){open();if(currentNode==0)throw new java.util.NoSuchElementException("no current graph task");}
    @Override public synchronized long node(){current();return currentNode;}
    @Override public synchronized int element(){current();return currentElement;}
    @Override public synchronized long depth(){current();return currentDepth;}
    @Override public synchronized boolean exiting(){current();return currentExit;}
    @Override public synchronized boolean active(long node) {
        open();positive(node);try{return ancestry.find(node)!=0;}catch(RuntimeException|Error failure){failed=true;throw failure;}
    }
    @Override public synchronized void active(long node,boolean value) {
        open();positive(node);
        try {
            if(value)ancestry.intern(node,1);
            else if(!ancestry.remove(node))throw new IllegalStateException("graph ancestry marker missing");
        } catch(RuntimeException|Error failure){failed=true;throw failure;}
    }
    private long context(long node,int element) {
        staged[0]=node;staged[1]=element;return contexts.intern(staged);
    }
    @Override public synchronized boolean completed(long node,int element) {
        open();positive(node);if(element<0)throw new IllegalArgumentException("nonnegative graph context required");
        try{return completed.find(context(node,element))!=0;}catch(RuntimeException|Error failure){failed=true;throw failure;}
    }
    @Override public synchronized void complete(long node,int element) {
        open();positive(node);if(element<0)throw new IllegalArgumentException("nonnegative graph context required");
        try{completed.intern(context(node,element),1);}catch(RuntimeException|Error failure){failed=true;throw failure;}
    }
    @Override public synchronized AirSnapshotBuilder.Lease claim(long bytes) {
        open();
        try{var lease=resources.reserve(AnalysisResources.Pool.RESIDENT,bytes,PHASE);return lease::close;}
        catch(RuntimeException|Error failure){failed=true;throw failure;}
    }
    private static void positive(long node){if(node<=0)throw new IllegalArgumentException("positive source handle required");}
    private void open(){if(closed||failed)throw new IllegalStateException("paged graph storage is closed or aborted");}
    @Override public synchronized void close() {
        if(closed)return;closed=true;Throwable failure=null;
        try{if(tasks!=null)tasks.close();}catch(RuntimeException|Error cleanup){failure=cleanup;}
        try{if(ancestry!=null)ancestry.close();}catch(RuntimeException|Error cleanup){if(failure==null)failure=cleanup;else if(failure!=cleanup)failure.addSuppressed(cleanup);}
        try{if(completed!=null)completed.close();}catch(RuntimeException|Error cleanup){if(failure==null)failure=cleanup;else if(failure!=cleanup)failure.addSuppressed(cleanup);}
        try{if(contexts!=null)contexts.close();}catch(RuntimeException|Error cleanup){if(failure==null)failure=cleanup;else if(failure!=cleanup)failure.addSuppressed(cleanup);}
        tasks=null;ancestry=null;completed=null;contexts=null;staged=null;size=currentNode=currentDepth=0;resident.close();
        if(failure instanceof RuntimeException exception)throw exception;if(failure instanceof Error error)throw error;
    }
}
