package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.air.model.AirSnapshotBuilder;
import io.github.gustavo2358.air.validation.SnapshotSignatureIndex;
import io.github.gustavo2358.analysis.solver.*;
import java.util.NoSuchElementException;
import java.util.Objects;

/** Required position facts and two diagnostic orders; all growing state uses borrowed run pages. */
public final class PagedSnapshotSignatureStorage implements SnapshotSignatureIndex.Storage {
    private static final AnalysisResources.Phase PHASE=AnalysisResources.Phase.VALIDATION;
    private static final int WIDTH=9,STATUS=0,ROWS=1,OPEN_COUNT=2,CLOSED_COUNT=3,
        OPEN_HEAD=4,OPEN_TAIL=5,CLOSED_HEAD=6,CLOSED_TAIL=7,NEXT_ORDINAL=8;
    private static final int BAD_WIDTH=5,ROW=0,POSITION=1,ORDINAL=2,OPEN_NEXT=3,CLOSED_NEXT=4;
    private final AnalysisResources resources;
    private final AnalysisResources.Reservation resident;
    private CanonicalTupleArena keys;
    private PagedLongArray metadata,badRows;
    private long[] staged;
    private long issued,next,currentRow,currentPosition,currentOrdinal;
    private int mode;
    private boolean selected,closed,failed;

    public PagedSnapshotSignatureStorage(PageStore pages,AnalysisResources resources) {
        Objects.requireNonNull(pages);this.resources=Objects.requireNonNull(resources);
        resident=resources.reserve(AnalysisResources.Pool.RESIDENT,384,PHASE);
        try {
            staged=new long[3];
            // tag0: (LIST source,kind); tag1: (table handle,borrowed atom key), all literal words.
            keys=new CanonicalTupleArena(pages,resources,PHASE,3,new int[0]);
            metadata=new PagedLongArray(pages,Long.MAX_VALUE,resources,PHASE);
            badRows=new PagedLongArray(pages,Long.MAX_VALUE,resources,PHASE);
        } catch(RuntimeException|Error failure){closeSuppressed(failure);throw failure;}
    }
    private void listKey(long list,int kind){positive(list);if(kind<0||kind>1)throw new IllegalArgumentException("signature row kind required");staged[0]=0;staged[1]=list;staged[2]=kind;}
    private void memberKey(long table,long atom){positive(atom);staged[0]=1;staged[1]=table;staged[2]=atom;}
    private static long base(long table){positive(table);if(table>Long.MAX_VALUE/WIDTH)throw new IllegalArgumentException("signature table address exceeds capacity");return (table-1)*WIDTH;}
    private long table(long table,boolean complete) {
        long base=base(table),status=metadata.get(base+STATUS);
        if(status!=(complete?2:1))throw new IllegalStateException(complete?"signature table is unfinished/unknown":"signature table is finished/unknown");
        return base;
    }
    @Override public synchronized long find(long list,int kind) {
        open();listKey(list,kind);
        try {long handle=keys.find(staged);if(handle!=0)table(handle,true);return handle;}
        catch(RuntimeException|Error failure){failed=true;throw failure;}
    }
    @Override public synchronized long begin(long list,int kind) {
        open();listKey(list,kind);
        try {
            if(keys.find(staged)!=0)throw new IllegalStateException("signature table already exists");
            long handle=keys.intern(staged),base=base(handle);metadata.set(base+STATUS,1);return handle;
        } catch(RuntimeException|Error failure){failed=true;throw failure;}
    }
    @Override public synchronized void member(long handle,long atom) {
        open();positive(atom);
        try{table(handle,false);memberKey(handle,atom);keys.intern(staged);}
        catch(RuntimeException|Error failure){failed=true;throw failure;}
    }
    @Override public synchronized void issue(long handle,long row,long position,long ordinal,boolean unordered,boolean noncontiguous) {
        open();positive(row);positive(position);
        if(ordinal<0||ordinal==Long.MAX_VALUE||!(unordered||noncontiguous))throw new IllegalArgumentException("actual bad signature row required");
        try {
            long base=table(handle,false);
            if(ordinal<metadata.get(base+NEXT_ORDINAL))throw new IllegalStateException("signature issues must follow unique row order");
            if(issued==Long.MAX_VALUE/BAD_WIDTH)throw new IllegalStateException("signature issue address space exhausted");
            long id=issued+1,offset=issued*BAD_WIDTH;
            badRows.set(offset+ROW,row);badRows.set(offset+POSITION,position);badRows.set(offset+ORDINAL,ordinal);
            if(unordered)append(base,OPEN_HEAD,OPEN_TAIL,OPEN_COUNT,OPEN_NEXT,id);
            append(base,CLOSED_HEAD,CLOSED_TAIL,CLOSED_COUNT,CLOSED_NEXT,id);
            metadata.set(base+NEXT_ORDINAL,ordinal+1);issued=id;
        } catch(RuntimeException|Error failure){failed=true;throw failure;}
    }
    private void append(long base,int head,int tail,int count,int link,long id) {
        long previous=metadata.get(base+tail),total=Math.incrementExact(metadata.get(base+count));
        if(previous==0)metadata.set(base+head,id);
        else{if(previous<0||previous>=id)throw new IllegalStateException("corrupt signature issue tail");badRows.set((previous-1)*BAD_WIDTH+link,id);}
        metadata.set(base+tail,id);metadata.set(base+count,total);
    }
    @Override public synchronized void finish(long handle,long rows) {
        open();if(rows<0)throw new IllegalArgumentException("nonnegative signature row count required");
        try {
            long base=table(handle,false);
            if(metadata.get(base+NEXT_ORDINAL)>rows||metadata.get(base+CLOSED_COUNT)>rows)throw new IllegalStateException("signature issues exceed completed row count");
            metadata.set(base+ROWS,rows);metadata.set(base+STATUS,2);
        } catch(RuntimeException|Error failure){failed=true;throw failure;}
    }
    @Override public synchronized long rows(long handle) {
        open();try{return metadata.get(table(handle,true)+ROWS);}catch(RuntimeException|Error failure){failed=true;throw failure;}
    }
    @Override public synchronized long issues(long handle,boolean closedMode) {
        open();try{return metadata.get(table(handle,true)+(closedMode?CLOSED_COUNT:OPEN_COUNT));}catch(RuntimeException|Error failure){failed=true;throw failure;}
    }
    @Override public synchronized boolean contains(long handle,long atom) {
        open();positive(atom);
        try{table(handle,true);memberKey(handle,atom);return keys.find(staged)!=0;}
        catch(RuntimeException|Error failure){failed=true;throw failure;}
    }
    @Override public synchronized void select(long handle,boolean closedMode) {
        open();
        try {long base=table(handle,true);next=metadata.get(base+(closedMode?CLOSED_HEAD:OPEN_HEAD));mode=closedMode?CLOSED_NEXT:OPEN_NEXT;selected=true;currentRow=currentPosition=currentOrdinal=0;}
        catch(RuntimeException|Error failure){failed=true;throw failure;}
    }
    @Override public synchronized boolean advanceIssue() {
        open();if(!selected)throw new IllegalStateException("no selected signature issue sequence");
        try {
            currentRow=currentPosition=currentOrdinal=0;if(next==0)return false;
            if(next<0||next>issued)throw new IllegalStateException("corrupt signature issue address");
            long id=next,base=(id-1)*BAD_WIDTH,following=badRows.get(base+mode);
            long row=badRows.get(base+ROW),position=badRows.get(base+POSITION),ordinal=badRows.get(base+ORDINAL);
            if(row<=0||position<=0||ordinal<0||following<0||(following!=0&&(following<=id||following>issued)))throw new IllegalStateException("corrupt signature issue row/order");
            next=following;currentRow=row;currentPosition=position;currentOrdinal=ordinal;return true;
        } catch(RuntimeException|Error failure){failed=true;throw failure;}
    }
    @Override public synchronized long row(){current();return currentRow;}
    @Override public synchronized long position(){current();return currentPosition;}
    @Override public synchronized long ordinal(){current();return currentOrdinal;}
    private void current(){open();if(currentRow==0)throw new NoSuchElementException("no current signature issue");}
    @Override public synchronized AirSnapshotBuilder.Lease claim(long bytes) {
        open();try{var lease=resources.reserve(AnalysisResources.Pool.RESIDENT,bytes,PHASE);return lease::close;}
        catch(RuntimeException|Error failure){failed=true;throw failure;}
    }
    private static void positive(long value){if(value<=0)throw new IllegalArgumentException("positive signature source/key required");}
    private void open(){if(closed||failed)throw new IllegalStateException("paged signature storage is closed or aborted");}
    private void closeSuppressed(Throwable failure){try{close();}catch(RuntimeException|Error cleanup){if(cleanup!=failure)failure.addSuppressed(cleanup);}}
    @Override public synchronized void close() {
        if(closed)return;closed=true;Throwable failure=null;
        try{if(keys!=null)keys.close();}catch(RuntimeException|Error cleanup){failure=cleanup;}
        try{if(metadata!=null)metadata.close();}catch(RuntimeException|Error cleanup){if(failure==null)failure=cleanup;else if(failure!=cleanup)failure.addSuppressed(cleanup);}
        try{if(badRows!=null)badRows.close();}catch(RuntimeException|Error cleanup){if(failure==null)failure=cleanup;else if(failure!=cleanup)failure.addSuppressed(cleanup);}
        keys=null;metadata=null;badRows=null;staged=null;issued=next=currentRow=currentPosition=currentOrdinal=0;resident.close();
        if(failure instanceof RuntimeException error)throw error;if(failure instanceof Error error)throw error;
    }
}
