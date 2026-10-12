package io.github.gustavo2358.analysis.values;

import io.github.gustavo2358.analysis.structure.ProgramStore;
import java.util.AbstractList;
import java.util.List;
import java.util.Objects;

/** Execution-owned canonical runs. Native payload borrows the admitted paged column,
 * never a resident Run catalogue. Caller-managed programs keep resident compatibility. */
final class LogicalTextArena implements AutoCloseable {
    private static final ProgramStore.ExecutionPhase PHASE=ProgramStore.ExecutionPhase.DOMAIN;
    private final ProgramStore.Structural program;
    private final Rows rows;
    private boolean closed;
    LogicalTextArena(ProgramStore.Structural program) {
        this.program=Objects.requireNonNull(program);
        var budget=program.executionBudget();
        if(budget.isEmpty()){rows=null;return;}
        var retained=budget.orElseThrow().reserve(ProgramStore.CapacityPool.RESIDENT,256,PHASE);
        ProgramStore.CapacityLease scratch=null;ProgramStore.OrdinalColumn column=null;
        try {
            scratch=budget.orElseThrow().reserve(ProgramStore.CapacityPool.SCRATCH,128,PHASE);
            // A native program must provide native columns. Never silently fall back
            // to an array whose cardinality is the input's cardinality.
            column=program.storageInventory().orElseThrow().column(Long.MAX_VALUE);
            rows=new Rows(column,retained,scratch);
            retained.onOwnerClose(rows::close); // no Engine, semantic roots or arena capture
        } catch(RuntimeException|Error failure) {
            cleanup(failure,column,scratch,retained);throw failure;
        }
    }
    private static void cleanup(Throwable primary,AutoCloseable... resources) {
        for(var resource:resources)if(resource!=null)try{resource.close();}
        catch(Exception|Error failure){if(failure!=primary)primary.addSuppressed(failure);}
    }
    private static final class Rows implements AutoCloseable {
        private ProgramStore.OrdinalColumn column;
        private ProgramStore.CapacityLease retained,scratch;
        private Runnable ownerObserver;
        private long next;
        Rows(ProgramStore.OrdinalColumn column,ProgramStore.CapacityLease retained,ProgramStore.CapacityLease scratch){this.column=column;this.retained=retained;this.scratch=scratch;}
        void available(){if(column==null)throw new IllegalStateException("logical text owner is closed");}
        @Override public void close() {
            var owned=column;var live=retained;var work=scratch;var observer=ownerObserver;
            column=null;retained=null;scratch=null;ownerObserver=null;
            Throwable failure=null;
            // The observer must be weak with respect to semantic owners. Detach
            // their actual roots before returning capacity, including on expiry.
            if(observer!=null)try{observer.run();}catch(RuntimeException|Error error){failure=error;}
            for(var resource:new AutoCloseable[]{owned,work,live})if(resource!=null)try{resource.close();}
            catch(Exception|Error error){if(failure==null)failure=error;else if(failure!=error)failure.addSuppressed(error);}
            if(failure instanceof RuntimeException error)throw error;if(failure instanceof Error error)throw error;
            if(failure!=null)throw new IllegalStateException("logical text cleanup failed",failure);
        }
    }
    LogicalText of(String value) {
        Objects.requireNonNull(value);available();
        if(rows==null)return LogicalText.of(value);
        var builder=builder();
        for(int at=0;at<value.length();) {
            int scalar=value.codePointAt(at);at+=Character.charCount(scalar);builder.append(scalar,1);
        }
        return builder.finish();
    }
    void available(){if(closed)throw new IllegalStateException("logical text arena is closed");if(rows!=null)rows.available();}
    long rowsWritten(){available();return rows==null?0:rows.next;}
    void onOwnerClose(Runnable observer){available();if(rows!=null){if(rows.ownerObserver!=null)throw new IllegalStateException("text owner observer already installed");rows.ownerObserver=Objects.requireNonNull(observer);}}
    Builder builder(){available();if(rows==null)throw new IllegalStateException("resident compatibility has no native builder");program.progress(PHASE);return new Builder();}
    final class Builder {
        private final long start=rows.next;
        private int scalar,length,count,total,hash=1;
        private boolean complete=true,finished;
        void append(int value,int extent) {
            if(finished)throw new IllegalStateException("finished text builder");
            if(extent<0)throw new IllegalArgumentException("negative run extent");if(extent==0)return;
            total=Math.addExact(total,extent);
            if(length!=0&&scalar==value){length=Math.addExact(length,extent);return;}
            flush();scalar=value;length=extent;
        }
        void append(LogicalText text){for(var run:text.runs())append(run.scalar(),run.length());}
        private void flush() {
            if(length==0)return;rows.available();
            int nextCount=Math.incrementExact(count);long nextRow=Math.incrementExact(rows.next);
            if((count&255)==0)program.progress(PHASE);
            rows.column.set(rows.next,((long)scalar<<32)|(length&0xffffffffL));
            rows.next=nextRow;count=nextCount;hash=31*hash+31*scalar+length;complete&=scalar>=0;length=0;
        }
        LogicalText finish() {
            if(finished)throw new IllegalStateException("finished text builder");flush();
            // Fixed text/header/view capacity is paid before publication; payload
            // and all directory/cache capacity belong to the native column owner.
            rows.retained.grow(96,PHASE);finished=true;
            return LogicalText.stored(new Runs(start,count),total,LogicalTextArena.this,hash,complete);
        }
    }
    private final class Runs extends AbstractList<LogicalText.Run> {
        private final long start;private final int count;
        Runs(long start,int count){this.start=start;this.count=count;}
        @Override public int size(){available();return count;}
        @Override public LogicalText.Run get(int ordinal){available();Objects.checkIndex(ordinal,count);long value=rows.column.get(start+ordinal);return new LogicalText.Run((int)(value>>32),(int)value);}
    }
    @Override public void close(){if(closed)return;closed=true;if(rows!=null)rows.close();}
}
