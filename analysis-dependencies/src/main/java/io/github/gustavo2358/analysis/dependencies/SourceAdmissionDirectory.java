package io.github.gustavo2358.analysis.dependencies;

import io.github.gustavo2358.analysis.solver.*;
import java.util.Objects;

/**
 * Internal paged admission directory, NOT a certificate. Keys are complete
 * (unit, namespace, first, second) tuples. The admission owner must supply
 * canonical handles from one immutable SOURCE owner, never hashes or joined
 * string identities. Two mutable state words do not participate in identity.
 */
final class SourceAdmissionDirectory implements AutoCloseable {
    private static final AnalysisResources.Phase PHASE=AnalysisResources.Phase.VALIDATION;
    private static final int WIDTH=8;
    private AnalysisResources.Reservation control;
    private PagedLongArray rows;
    private PagedLongIndex index;
    private long size;
    private boolean closed;

    SourceAdmissionDirectory(PageStore pages,AnalysisResources resources) {
        Objects.requireNonNull(pages);Objects.requireNonNull(resources);
        control=resources.reserve(AnalysisResources.Pool.RESIDENT,512,PHASE);
        try {
            rows=new PagedLongArray(pages,Long.MAX_VALUE,resources,PHASE);
            index=new PagedLongIndex(pages,resources,PHASE,this::compare);
            control.onOwnerClose(this::close);
        }catch(RuntimeException|Error failure){cleanup(failure);throw failure;}
    }
    private int compare(long first,long second) {
        long a=Math.multiplyExact(first,WIDTH),b=Math.multiplyExact(second,WIDTH);
        for(int i=0;i<4;i++){int order=Long.compare(rows.get(a+i),rows.get(b+i));if(order!=0)return order;}
        return 0;
    }
    private static void key(long unit,int namespace) {
        if(unit<0||namespace<=0)throw new IllegalArgumentException("admission key scope");
    }
    private void keyAt(long row,long unit,int namespace,long first,long second) {
        long at=Math.multiplyExact(row,WIDTH);
        rows.set(at,unit);rows.set(at+1,namespace);rows.set(at+2,first);rows.set(at+3,second);
    }
    /** Prior row on exact duplicate, otherwise a new stable positive row ordinal. */
    synchronized long intern(long unit,int namespace,long first,long second,long reference,long ordinal) {
        open();key(unit,namespace);
        if(reference<=0||ordinal<0)throw new IllegalArgumentException("admission row payload");
        try {
            long row=Math.incrementExact(size),at=Math.multiplyExact(row,WIDTH);
            keyAt(row,unit,namespace,first,second);
            rows.set(at+4,reference);rows.set(at+5,ordinal);rows.set(at+6,0);rows.set(at+7,0);
            long canonical=index.intern(row,row);
            if(canonical==row)size=row;
            return canonical;
        }catch(RuntimeException|Error failure){cleanup(failure);throw failure;}
    }
    /** The dedicated row-zero probe is never inserted or used as a stable key. */
    synchronized long find(long unit,int namespace,long first,long second) {
        open();key(unit,namespace);
        try{keyAt(0,unit,namespace,first,second);return index.find(0);}
        catch(RuntimeException|Error failure){cleanup(failure);throw failure;}
    }
    synchronized long size(){open();return size;}
    synchronized long reference(long row){return get(row,4);}
    synchronized long ordinal(long row){return get(row,5);}
    synchronized long state(long row,int slot){return get(row,6+stateSlot(slot));}
    synchronized void state(long row,int slot,long value) {
        open();row(row);int column=6+stateSlot(slot);
        try{rows.set(Math.addExact(Math.multiplyExact(row,WIDTH),column),value);}
        catch(RuntimeException|Error failure){cleanup(failure);throw failure;}
    }
    private long get(long row,int column) {
        open();row(row);
        try{return rows.get(Math.addExact(Math.multiplyExact(row,WIDTH),column));}
        catch(RuntimeException|Error failure){cleanup(failure);throw failure;}
    }
    private void row(long row){if(row<=0||row>size)throw new IllegalArgumentException("admission row ordinal");}
    private static int stateSlot(int slot){if(slot<0||slot>1)throw new IllegalArgumentException("admission state slot");return slot;}
    private void open(){if(closed)throw new IllegalStateException("admission directory expired or aborted");}
    private void cleanup(Throwable failure){try{close();}catch(RuntimeException|Error cleanup){if(cleanup!=failure)failure.addSuppressed(cleanup);}}
    @Override public synchronized void close() {
        if(closed)return;closed=true;size=0;Throwable failure=null;
        for(AutoCloseable owner:new AutoCloseable[]{index,rows,control})if(owner!=null)try{owner.close();}catch(Exception|Error cleanup){if(failure==null)failure=cleanup;else if(failure!=cleanup)failure.addSuppressed(cleanup);}
        index=null;rows=null;control=null;
        if(failure instanceof RuntimeException error)throw error;if(failure instanceof Error error)throw error;if(failure!=null)throw new IllegalStateException("admission directory cleanup",failure);
    }
}
