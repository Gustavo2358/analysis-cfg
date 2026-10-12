package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.air.model.AirSnapshotBuilder;
import io.github.gustavo2358.air.validation.SnapshotLocalLabels;
import io.github.gustavo2358.analysis.solver.*;
import java.util.Objects;

/** One shared managed row/posting payload and directory for all local-label lists and Units. */
public final class PagedSnapshotLabelStorage implements SnapshotLocalLabels.Storage {
    private static final AnalysisResources.Phase PHASE=AnalysisResources.Phase.VALIDATION;
    private static final int WIDTH=8,STATUS=0,ROW_BASE=1,ROWS=2,MISSING_BASE=3,MISSING_COUNT=4,HEAD=5,TAIL=6;
    // A group shares the sparse metadata column; its canonical key has a distinct literal tag.
    private static final int COUNT=1,POST_BASE=2,FILL=3,NEXT=4;
    private final AnalysisResources resources;
    private final AnalysisResources.Reservation resident;
    private CanonicalTupleArena keys;
    private PagedLongArray metadata,payload;
    private long[] staged;
    private long active,nextWord;
    private boolean closed,failed;

    public PagedSnapshotLabelStorage(PageStore pages,AnalysisResources resources) {
        Objects.requireNonNull(pages);this.resources=Objects.requireNonNull(resources);
        resident=resources.reserve(AnalysisResources.Pool.RESIDENT,384,PHASE);
        try {
            staged=new long[3];keys=new CanonicalTupleArena(pages,resources,PHASE,3,new int[0]);
            metadata=new PagedLongArray(pages,Long.MAX_VALUE,resources,PHASE);payload=new PagedLongArray(pages,Long.MAX_VALUE,resources,PHASE);
        } catch(RuntimeException|Error failure){closeSuppressed(failure);throw failure;}
    }
    private void key(int tag,long first,long second){staged[0]=tag;staged[1]=first;staged[2]=second;}
    private static long base(long handle){positive(handle);if(handle>Long.MAX_VALUE/WIDTH)throw new IllegalArgumentException("label metadata address capacity");return (handle-1)*WIDTH;}
    private long table(long handle,boolean complete){long offset=base(handle);if(metadata.get(offset+STATUS)!=(complete?2:1)||!complete&&active!=handle)throw new IllegalStateException("label table is unknown/unfinished/frozen");return offset;}
    @Override public synchronized long find(long list) {
        open();try{positive(list);key(0,list,0);long handle=keys.find(staged);if(handle!=0)table(handle,true);return handle;}
        catch(RuntimeException|Error failure){failed=true;throw failure;}
    }
    @Override public synchronized long begin(long list) {
        open();try{positive(list);if(active!=0)throw new IllegalStateException("another label table is unfinished");key(0,list,0);if(keys.find(staged)!=0)throw new IllegalStateException("label table already exists");long handle=keys.intern(staged),offset=base(handle);metadata.set(offset+STATUS,1);metadata.set(offset+ROW_BASE,nextWord);active=handle;return handle;}
        catch(RuntimeException|Error failure){failed=true;throw failure;}
    }
    @Override public synchronized void row(long handle,long label,long unit,boolean missing) {
        open();
        try {
            positive(label);positive(unit);long offset=table(handle,false),rows=Math.incrementExact(metadata.get(offset+ROWS));
            long end=Math.addExact(nextWord,3);key(1,handle,unit);long group=keys.find(staged);
            if(group==0) {
                group=keys.intern(staged);long groupBase=base(group);metadata.set(groupBase+STATUS,3);
                long tail=metadata.get(offset+TAIL);if(tail==0)metadata.set(offset+HEAD,group);else metadata.set(base(tail)+NEXT,group);
                metadata.set(offset+TAIL,group);
            }
            long groupBase=base(group);if(metadata.get(groupBase+STATUS)!=3)throw new IllegalStateException("invalid label Unit group");
            metadata.set(groupBase+COUNT,Math.incrementExact(metadata.get(groupBase+COUNT)));
            payload.set(nextWord,label);payload.set(nextWord+1,group);payload.set(nextWord+2,missing?1:0);
            metadata.set(offset+ROWS,rows);if(missing)metadata.set(offset+MISSING_COUNT,Math.incrementExact(metadata.get(offset+MISSING_COUNT)));nextWord=end;
        } catch(RuntimeException|Error failure){failed=true;throw failure;}
    }
    @Override public synchronized void finish(long handle) {
        open();
        try {
            long offset=table(handle,false),rows=metadata.get(offset+ROWS),missing=metadata.get(offset+MISSING_COUNT),rowBase=metadata.get(offset+ROW_BASE);
            long missingBase=nextWord,end=Math.addExact(nextWord,missing),groups=0,matched=0;
            metadata.set(offset+MISSING_BASE,missingBase);
            for(long group=metadata.get(offset+HEAD);group!=0;group=metadata.get(base(group)+NEXT)) {
                if(++groups>rows)throw new IllegalStateException("cyclic label Unit group order");long groupBase=base(group),count=metadata.get(groupBase+COUNT);
                if(metadata.get(groupBase+STATUS)!=3||count<=0)throw new IllegalStateException("invalid label Unit count");
                matched=Math.addExact(matched,count);metadata.set(groupBase+POST_BASE,end);end=Math.addExact(end,count);
            }
            if(matched!=rows||missing>rows)throw new IllegalStateException("label posting counts disagree with rows");
            long missingFilled=0;
            for(long n=0;n<rows;n++) {
                long row=rowBase+3*n,groupBase=base(payload.get(row+1)),used=metadata.get(groupBase+FILL),count=metadata.get(groupBase+COUNT);
                if(used>=count)throw new IllegalStateException("label posting span overflow");payload.set(metadata.get(groupBase+POST_BASE)+used,n+1);metadata.set(groupBase+FILL,used+1);
                long bit=payload.get(row+2);if(bit!=0&&bit!=1)throw new IllegalStateException("invalid missing-label flag");
                if(bit!=0){if(missingFilled>=missing)throw new IllegalStateException("missing label span overflow");payload.set(missingBase+missingFilled++,n+1);}
            }
            if(missingFilled!=missing)throw new IllegalStateException("missing-label count disagrees with rows");
            for(long group=metadata.get(offset+HEAD);group!=0;group=metadata.get(base(group)+NEXT)) {long groupBase=base(group);if(metadata.get(groupBase+FILL)!=metadata.get(groupBase+COUNT))throw new IllegalStateException("unfinished label posting span");}
            nextWord=end;metadata.set(offset+STATUS,2);active=0;
        } catch(RuntimeException|Error failure){failed=true;throw failure;}
    }
    @Override public synchronized long rows(long handle){open();try{return metadata.get(table(handle,true)+ROWS);}catch(RuntimeException|Error failure){failed=true;throw failure;}}
    @Override public synchronized long missing(long handle){open();try{return metadata.get(table(handle,true)+MISSING_COUNT);}catch(RuntimeException|Error failure){failed=true;throw failure;}}
    private long group(long handle,long unit){positive(unit);key(1,handle,unit);long group=keys.find(staged);if(group!=0&&metadata.get(base(group)+STATUS)!=3)throw new IllegalStateException("unknown label Unit group");return group;}
    @Override public synchronized long matching(long handle,long unit) {
        open();try{table(handle,true);long group=group(handle,unit);return group==0?0:metadata.get(base(group)+COUNT);}
        catch(RuntimeException|Error failure){failed=true;throw failure;}
    }
    @Override public synchronized long label(long handle,long ordinal) {
        open();try{long offset=table(handle,true);range(ordinal,metadata.get(offset+ROWS));long label=payload.get(metadata.get(offset+ROW_BASE)+3*ordinal);positive(label);return label;}
        catch(RuntimeException|Error failure){failed=true;throw failure;}
    }
    @Override public synchronized long missingAt(long handle,long ordinal) {
        open();try{long offset=table(handle,true);range(ordinal,metadata.get(offset+MISSING_COUNT));return posted(metadata.get(offset+MISSING_BASE)+ordinal,metadata.get(offset+ROWS));}
        catch(RuntimeException|Error failure){failed=true;throw failure;}
    }
    /** Select complement j using nondecreasing gaps m[i]-i, with one binary search. */
    @Override public synchronized long foreignAt(long handle,long unit,long ordinal) {
        open();
        try {
            long offset=table(handle,true),rows=metadata.get(offset+ROWS),group=group(handle,unit),count=group==0?0:metadata.get(base(group)+COUNT);
            range(ordinal,rows-count);if(count==0)return ordinal;
            long posting=metadata.get(base(group)+POST_BASE),low=0,high=count;
            while(low<high){long mid=low+(high-low)/2,row=posted(posting+mid,rows);if(row-mid<=ordinal)low=mid+1;else high=mid;}
            long row=Math.addExact(ordinal,low);range(row,rows);return row;
        } catch(RuntimeException|Error failure){failed=true;throw failure;}
    }
    private long posted(long address,long rows){long word=payload.get(address);if(word<=0||word>rows)throw new IllegalStateException("invalid label posting ordinal");return word-1;}
    private static void range(long ordinal,long size){if(ordinal<0||ordinal>=size)throw new IndexOutOfBoundsException("label relation ordinal");}
    private static void positive(long word){if(word<=0)throw new IllegalArgumentException("positive label source/key required");}
    @Override public synchronized AirSnapshotBuilder.Lease claim(long bytes) {
        open();try{var lease=resources.reserve(AnalysisResources.Pool.RESIDENT,bytes,PHASE);return lease::close;}
        catch(RuntimeException|Error failure){failed=true;throw failure;}
    }
    private void open(){if(closed||failed)throw new IllegalStateException("paged label relation is closed or aborted");}
    private void closeSuppressed(Throwable failure){try{close();}catch(RuntimeException|Error cleanup){if(cleanup!=failure)failure.addSuppressed(cleanup);}}
    @Override public synchronized void close() {
        if(closed)return;closed=true;Throwable failure=null;
        try{if(keys!=null)keys.close();}catch(RuntimeException|Error cleanup){failure=cleanup;}
        try{if(metadata!=null)metadata.close();}catch(RuntimeException|Error cleanup){if(failure==null)failure=cleanup;else if(failure!=cleanup)failure.addSuppressed(cleanup);}
        try{if(payload!=null)payload.close();}catch(RuntimeException|Error cleanup){if(failure==null)failure=cleanup;else if(failure!=cleanup)failure.addSuppressed(cleanup);}
        keys=null;metadata=null;payload=null;staged=null;active=nextWord=0;resident.close();
        if(failure instanceof RuntimeException error)throw error;if(failure instanceof Error error)throw error;
    }
}
