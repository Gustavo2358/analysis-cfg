package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.analysis.dependencies.SnapshotOrderStorage;
import io.github.gustavo2358.analysis.solver.AnalysisResources;
import io.github.gustavo2358.analysis.solver.PageStore;
import io.github.gustavo2358.analysis.solver.PagedLongIndex;
import io.github.gustavo2358.analysis.solver.PagedLongArray;
import java.util.Objects;
import io.github.gustavo2358.analysis.structure.ProgramStore;

/** Spillable canonical-order indexes over the snapshot session's borrowed pages. */
public final class PagedSnapshotOrderStorage implements SnapshotOrderStorage {
    private static final AnalysisResources.Phase PHASE=AnalysisResources.Phase.INDEX;
    private final PageStore pages;
    private final AnalysisResources resources;
    private final AnalysisResources.Reservation resident;
    private int owners;
    private boolean closed,failed;

    public PagedSnapshotOrderStorage(PageStore pages,AnalysisResources resources) {
        this.pages=Objects.requireNonNull(pages);this.resources=Objects.requireNonNull(resources);
        resident=resources.reserve(AnalysisResources.Pool.RESIDENT,512,PHASE);
    }
    @Override public synchronized Index open(Order order) {
        available();Objects.requireNonNull(order);
        try {var index=new PagedLongIndex(pages,resources,PHASE,order::compare);owners++;return new PagedIndex(index);}
        catch(RuntimeException|Error failure){failed=true;throw failure;}
    }
    @Override public synchronized Tape tape() {
        available();
        try{var values=new PagedLongArray(pages,Long.MAX_VALUE,resources,PHASE);owners++;return new PagedTape(values);}
        catch(RuntimeException|Error failure){failed=true;throw failure;}
    }
    @Override public synchronized ProgramStore.OrdinalColumn column(long length){
        available();
        try{var values=new PagedLongArray(pages,length,resources,PHASE);owners++;return new PagedColumn(values);}
        catch(RuntimeException|Error failure){failed=true;throw failure;}
    }
    private final class PagedColumn implements ProgramStore.OrdinalColumn {
        private PagedLongArray values;private boolean columnFailed;
        PagedColumn(PagedLongArray values){this.values=values;}
        private void open(){available();if(values==null||columnFailed)throw new IllegalStateException("snapshot ordinal column is closed or aborted");}
        @Override public synchronized long get(long ordinal){
            open();try{return values.get(ordinal);}catch(RuntimeException|Error failure){columnFailed=true;PagedSnapshotOrderStorage.this.failed=true;throw failure;}
        }
        @Override public synchronized void set(long ordinal,long value){
            open();try{values.set(ordinal,value);}catch(RuntimeException|Error failure){columnFailed=true;PagedSnapshotOrderStorage.this.failed=true;throw failure;}
        }
        @Override public synchronized void close(){
            if(values==null)return;try{values.close();}finally{values=null;synchronized(PagedSnapshotOrderStorage.this){owners--;}}
        }
    }
    private void available(){if(closed||failed)throw new IllegalStateException("paged snapshot order storage is closed or aborted");}
    @Override public synchronized void close() {
        if(closed)return;if(owners!=0)throw new IllegalStateException("snapshot order indexes remain open");
        closed=true;resident.close();
    }
    private final class PagedTape implements Tape {
        private PagedLongArray values;private long count;private boolean tapeFailed;
        private PagedTape(PagedLongArray values){this.values=values;}
        private void open(){available();if(values==null||tapeFailed)throw new IllegalStateException("snapshot order tape is closed or aborted");}
        @Override public synchronized void append(long handle) {
            open();if(handle<=0)throw new IllegalArgumentException("positive snapshot handle required");
            try{values.set(count,handle);count=Math.addExact(count,1);}
            catch(RuntimeException|Error failure){tapeFailed=true;PagedSnapshotOrderStorage.this.failed=true;throw failure;}
        }
        @Override public synchronized long size(){open();return count;}
        @Override public synchronized long handle(long ordinal) {
            open();Objects.checkIndex(ordinal,count);
            try{return values.get(ordinal);}
            catch(RuntimeException|Error failure){tapeFailed=true;PagedSnapshotOrderStorage.this.failed=true;throw failure;}
        }
        @Override public synchronized void close() {
            if(values==null)return;
            try{values.close();}
            finally{values=null;synchronized(PagedSnapshotOrderStorage.this){owners--;}}
        }
    }
    private final class PagedIndex implements Index {
        private PagedLongIndex index;private boolean indexFailed;
        private PagedIndex(PagedLongIndex index){this.index=index;}
        @Override public synchronized void add(long handle) {
            open();
            try{if(index.intern(handle,handle)!=handle)throw new IllegalArgumentException("duplicate canonical snapshot local ID");}
            catch(RuntimeException|Error failure){indexFailed=true;PagedSnapshotOrderStorage.this.failed=true;throw failure;}
        }
        @Override public synchronized Cursor cursor() {
            open();try{return new OrderedCursor(index.cursor());}
            catch(RuntimeException|Error failure){indexFailed=true;PagedSnapshotOrderStorage.this.failed=true;throw failure;}
        }
        private void open(){if(index==null||indexFailed)throw new IllegalStateException("paged snapshot order index is closed or aborted");}
        @Override public synchronized void close() {
            if(index==null)return;Throwable failure=null;try{index.close();}catch(RuntimeException|Error cleanup){failure=cleanup;}
            index=null;synchronized(PagedSnapshotOrderStorage.this){owners--;}
            if(failure instanceof RuntimeException exception)throw exception;if(failure instanceof Error error)throw error;
        }
        private final class OrderedCursor implements Cursor {
            private PagedLongIndex.Cursor cursor;
            private OrderedCursor(PagedLongIndex.Cursor cursor){this.cursor=cursor;}
            @Override public boolean advance(){return cursor!=null&&cursor.advance();}
            @Override public long handle(){if(cursor==null)throw new IllegalStateException("snapshot order cursor is closed");return cursor.value();}
            @Override public void close(){if(cursor==null)return;cursor.close();cursor=null;}
        }
    }
}
