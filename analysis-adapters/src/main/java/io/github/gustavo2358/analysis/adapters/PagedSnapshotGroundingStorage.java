package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.air.model.AirSnapshotBuilder;
import io.github.gustavo2358.air.validation.SnapshotGrounding;
import io.github.gustavo2358.analysis.solver.*;
import java.util.NoSuchElementException;
import java.util.Objects;

/** Managed positive Boolean equations; freeze releases topology/FIFO and retains only exact facts. */
public final class PagedSnapshotGroundingStorage implements SnapshotGrounding.Storage {
    private static final AnalysisResources.Phase PHASE=AnalysisResources.Phase.VALIDATION;
    private static final int EXPANDED=1,TRUE=2,QUEUED=4,WIDTH=2;
    private final AnalysisResources resources;
    private final AnalysisResources.Reservation resident;
    private PagedLongIndex index;
    private PagedLongArray flags,heads,edges,queue;
    private long count,edgeCount,head,tail,currentNode,nextEdge,currentDependent,unqueued;
    private int stage;
    private boolean cursor,closed,failed;

    public PagedSnapshotGroundingStorage(PageStore pages,AnalysisResources resources) {
        Objects.requireNonNull(pages);this.resources=Objects.requireNonNull(resources);
        resident=resources.reserve(AnalysisResources.Pool.RESIDENT,256,PHASE);
        try {
            index=new PagedLongIndex(pages,resources,PHASE);
            flags=new PagedLongArray(pages,Long.MAX_VALUE,resources,PHASE);
            heads=new PagedLongArray(pages,Long.MAX_VALUE,resources,PHASE);
            edges=new PagedLongArray(pages,Long.MAX_VALUE,resources,PHASE);
            queue=new PagedLongArray(pages,Long.MAX_VALUE,resources,PHASE);
        } catch(RuntimeException|Error failure){closeSuppressed(failure);throw failure;}
    }
    private long row(long node,boolean create) {
        positive(node);long ordinal=index.find(node);
        if(ordinal==0) {
            if(!create)throw new IllegalArgumentException("uncollected grounding equation");
            if(count==Long.MAX_VALUE)throw new IllegalStateException("grounding equation address space exhausted");
            ordinal=count+1;
            if(index.intern(node,ordinal)!=ordinal)throw new IllegalStateException("grounding source association changed");
            count=ordinal;
        }
        return ordinal-1;
    }
    private long flags(long row) {
        long value=flags.get(row);
        if(value<0||value>7||((value&QUEUED)!=0&&(value&TRUE)==0))throw new IllegalStateException("corrupt grounding flags");
        return value;
    }
    @Override public synchronized boolean expand(long node) {
        collecting();positive(node);
        try {
            long row=row(node,true),value=flags(row);
            if((value&EXPANDED)!=0)return false;
            flags.set(row,value|EXPANDED);return true;
        } catch(RuntimeException|Error failure){failed=true;throw failure;}
    }
    @Override public synchronized void link(long child,long parent) {
        collecting();positive(child);positive(parent);
        try {
            long childRow=row(child,true);row(parent,true);
            if(edgeCount==Long.MAX_VALUE/WIDTH)throw new IllegalStateException("grounding edge address space exhausted");
            long offset=edgeCount*WIDTH,previous=heads.get(childRow),ordinal=edgeCount+1;
            edges.set(offset,parent);edges.set(offset+1,previous);heads.set(childRow,ordinal);edgeCount=ordinal;
        } catch(RuntimeException|Error failure){failed=true;throw failure;}
    }
    @Override public synchronized boolean prove(long node) {
        mutable();positive(node);
        try {
            long row=row(node,false),value=flags(row);
            if((value&EXPANDED)==0)throw new IllegalStateException("unexpanded grounding equation");
            if((value&TRUE)!=0)return false;
            long pending=Math.incrementExact(unqueued);flags.set(row,value|TRUE);unqueued=pending;return true;
        } catch(RuntimeException|Error failure){failed=true;throw failure;}
    }
    @Override public synchronized void enqueue(long node) {
        mutable();positive(node);
        try {
            long row=row(node,false),value=flags(row);
            if((value&(EXPANDED|TRUE))!=(EXPANDED|TRUE)||(value&QUEUED)!=0)throw new IllegalStateException("grounding FIFO requires one proven change");
            if(tail==Long.MAX_VALUE)throw new IllegalStateException("grounding FIFO address space exhausted");
            if(unqueued==0)throw new IllegalStateException("grounding change publication count disagreement");
            queue.set(tail,node);tail++;flags.set(row,value|QUEUED);unqueued--;
        } catch(RuntimeException|Error failure){failed=true;throw failure;}
    }
    @Override public synchronized void start() {
        collecting();
        try {
            if(unqueued!=0)throw new IllegalStateException("grounding seed was not queued");
            for(long row=0;row<count;row++)if((flags(row)&EXPANDED)==0)throw new IllegalStateException("linked grounding equation was not expanded");
            stage=1;
        } catch(RuntimeException|Error failure){failed=true;throw failure;}
    }
    @Override public synchronized boolean advance() {
        propagating();
        try {
            if(cursor&&nextEdge!=0)throw new IllegalStateException("grounding dependent cursor not drained");
            cursor=false;currentDependent=0;currentNode=0;
            if(head==tail)return false;
            long node=queue.get(head);
            if(node<=0)throw new IllegalStateException("corrupt grounding FIFO row");
            queue.set(head,0);head++;if(head==tail)head=tail=0;currentNode=node;return true;
        } catch(RuntimeException|Error failure){failed=true;throw failure;}
    }
    @Override public synchronized long node(){open();if(currentNode==0)throw new NoSuchElementException("no current grounding FIFO row");return currentNode;}
    @Override public synchronized void dependents(long node) {
        propagating();positive(node);
        try {
            if(cursor&&nextEdge!=0)throw new IllegalStateException("grounding dependent cursor not drained");
            long row=row(node,false);nextEdge=heads.get(row);heads.set(row,0);cursor=true;currentDependent=0;
        } catch(RuntimeException|Error failure){failed=true;throw failure;}
    }
    @Override public synchronized boolean advanceDependent() {
        propagating();if(!cursor)throw new IllegalStateException("no selected grounding dependent cursor");
        try {
            currentDependent=0;if(nextEdge==0)return false;
            if(nextEdge<0||nextEdge>edgeCount)throw new IllegalStateException("corrupt grounding edge address");
            long offset=(nextEdge-1)*WIDTH,parent=edges.get(offset),next=edges.get(offset+1);
            if(parent<=0||next<0||next>=nextEdge)throw new IllegalStateException("corrupt grounding reverse occurrence");
            edges.set(offset,0);edges.set(offset+1,0);nextEdge=next;currentDependent=parent;return true;
        } catch(RuntimeException|Error failure){failed=true;throw failure;}
    }
    @Override public synchronized long dependent(){open();if(currentDependent==0)throw new NoSuchElementException("no current grounding dependent");return currentDependent;}
    @Override public synchronized void freeze() {
        propagating();
        try {
            if(unqueued!=0||head!=tail||(cursor&&nextEdge!=0))throw new IllegalStateException("grounding relation not saturated");
            closeTransient();stage=2;currentNode=currentDependent=nextEdge=0;cursor=false;
        } catch(RuntimeException|Error failure){failed=true;throw failure;}
    }
    @Override public synchronized boolean grounded(long node) {
        open();if(stage!=2)throw new IllegalStateException("grounding facts are not frozen");positive(node);
        try {
            long value=flags(row(node,false));
            if((value&EXPANDED)==0)throw new IllegalStateException("unexpanded frozen grounding fact");
            return (value&TRUE)!=0;
        } catch(RuntimeException|Error failure){failed=true;throw failure;}
    }
    @Override public synchronized AirSnapshotBuilder.Lease claim(long bytes) {
        collecting();try{var lease=resources.reserve(AnalysisResources.Pool.RESIDENT,bytes,PHASE);return lease::close;}
        catch(RuntimeException|Error failure){failed=true;throw failure;}
    }
    private static void positive(long node){if(node<=0)throw new IllegalArgumentException("positive immutable source node required");}
    private void open(){if(closed||failed)throw new IllegalStateException("paged grounding storage is closed or aborted");}
    private void collecting(){open();if(stage!=0)throw new IllegalStateException("grounding collection is sealed");}
    private void mutable(){open();if(stage==2)throw new IllegalStateException("grounding facts are frozen");}
    private void propagating(){open();if(stage!=1)throw new IllegalStateException("grounding propagation is unavailable");}
    private void closeSuppressed(Throwable failure){try{close();}catch(RuntimeException|Error cleanup){if(cleanup!=failure)failure.addSuppressed(cleanup);}}
    private void closeTransient() {
        Throwable failure=null;
        try{if(heads!=null)heads.close();}catch(RuntimeException|Error cleanup){failure=cleanup;}
        try{if(edges!=null)edges.close();}catch(RuntimeException|Error cleanup){if(failure==null)failure=cleanup;else if(failure!=cleanup)failure.addSuppressed(cleanup);}
        try{if(queue!=null)queue.close();}catch(RuntimeException|Error cleanup){if(failure==null)failure=cleanup;else if(failure!=cleanup)failure.addSuppressed(cleanup);}
        heads=null;edges=null;queue=null;head=tail=0;
        if(failure instanceof RuntimeException error)throw error;if(failure instanceof Error error)throw error;
    }
    @Override public synchronized void close() {
        if(closed)return;closed=true;Throwable failure=null;
        try{if(index!=null)index.close();}catch(RuntimeException|Error cleanup){failure=cleanup;}
        try{if(flags!=null)flags.close();}catch(RuntimeException|Error cleanup){if(failure==null)failure=cleanup;else if(failure!=cleanup)failure.addSuppressed(cleanup);}
        try{closeTransient();}catch(RuntimeException|Error cleanup){if(failure==null)failure=cleanup;else if(failure!=cleanup)failure.addSuppressed(cleanup);}
        index=null;flags=null;count=edgeCount=currentNode=nextEdge=currentDependent=unqueued=0;resident.close();
        if(failure instanceof RuntimeException error)throw error;if(failure instanceof Error error)throw error;
    }
}
