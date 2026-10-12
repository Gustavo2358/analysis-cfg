package io.github.gustavo2358.analysis.solver;

import java.util.Arrays;

/** Exact paged condition records and complete-key identity. Tokens/marks/free links
 * are metadata. The caller owns token semantics; this owner borrows its page backend. */
final class BooleanNodeStore implements AutoCloseable {
    private static final int WIDTH=9;
    private final AnalysisResources.Reservation metadata;
    private PagedLongArray rows;
    private PagedLongIndex unique;
    private int[] cacheIds;
    private long[] cacheFields,staged;
    private int span=2,freeHead;
    private boolean closed,failed;
    BooleanNodeStore(PageStore pages,AnalysisResources resources,int cacheSlots) {
        if(cacheSlots<1||Integer.bitCount(cacheSlots)!=1)throw new IllegalArgumentException("node cache must be a power of two");
        int length=Math.multiplyExact(cacheSlots,WIDTH);
        metadata=resources.reserve(AnalysisResources.Pool.RESIDENT,512L+4L*cacheSlots+8L*length,AnalysisResources.Phase.CONTROL);
        try {
            cacheIds=new int[cacheSlots];Arrays.fill(cacheIds,-1);cacheFields=new long[length];staged=new long[5];
            rows=new PagedLongArray(pages,Long.MAX_VALUE,resources,AnalysisResources.Phase.CONTROL);
            unique=new PagedLongIndex(pages,resources,AnalysisResources.Phase.CONTROL,this::compare);
        }catch(RuntimeException|Error failure) {
            var cleanup=ActivationSolver.closeResource(unique,null);cleanup=ActivationSolver.closeResource(rows,cleanup);
            metadata.close();if(cleanup!=null)failure.addSuppressed(cleanup);throw failure;
        }
    }
    private void open(){if(closed||failed)throw new IllegalStateException("condition catalog unavailable");}
    private int slot(int id){return (id*0x9e3779b9)&(cacheIds.length-1);}
    private long field(int id,int column) {
        if(id<2||id>=span)throw new IllegalArgumentException("foreign condition ID");
        int slot=slot(id),base=slot*WIDTH;
        if(cacheIds[slot]!=id) {
            long address=(long)id*WIDTH;
            for(int n=0;n<WIDTH;n++)cacheFields[base+n]=rows.get(address+n);
            cacheIds[slot]=id;
        }
        return cacheFields[base+column];
    }
    private long liveField(int id,int column) {
        open();try {if(field(id,0)==0)throw new IllegalArgumentException("retired condition ID");return field(id,column);}
        catch(AnalysisResources.Exhausted|PageStore.Failure error){failed=true;throw error;}
    }
    int variable(int id){return (int)liveField(id,1);}
    int low(int id){return (int)liveField(id,2);}
    int high(int id){return (int)liveField(id,3);}
    long literals(int id){return liveField(id,4);}
    int junction(int id){return (int)liveField(id,5);}
    long token(int id){return liveField(id,6);}
    long mark(int id){return liveField(id,7);}
    void mark(int id,long value){liveField(id,0);write(id,7,value);}
    boolean live(int id){open();if(id<2||id>=span)return false;try{return field(id,0)!=0;}
        catch(AnalysisResources.Exhausted|PageStore.Failure error){failed=true;throw error;}}
    int size(){open();return span;}
    int retainedNodes(){open();return Math.toIntExact(unique.size()+2);}
    private void write(int id,int column,long value) {
        try {rows.set((long)id*WIDTH+column,value);int slot=slot(id);if(cacheIds[slot]==id)cacheFields[slot*WIDTH+column]=value;}
        catch(AnalysisResources.Exhausted|PageStore.Failure error){failed=true;throw error;}
    }
    private void stage(int variable,int low,int high,long literals,int junction) {
        staged[0]=variable;staged[1]=low;staged[2]=high;staged[3]=literals;staged[4]=junction;
    }
    int find(int variable,int low,int high,long literals,int junction) {
        open();stage(variable,low,high,literals,junction);
        try {long found=unique.find(0);return found==0?-1:Math.toIntExact(found);}
        catch(RuntimeException error){failed=true;throw error;}
    }
    private int compare(long a,long b) {
        if(a==b)return 0;
        for(int n=0;n<5;n++) {
            long left=a==0?staged[n]:field(Math.toIntExact(a),n+1),right=b==0?staged[n]:field(Math.toIntExact(b),n+1);
            int c=Long.compare(left,right);if(c!=0)return c;
        }
        return 0;
    }
    /** Call find first. A fresh record takes its supplied metadata token, even when zero. */
    int create(int variable,int low,int high,long literals,int junction,long token,boolean append) {
        open();if(find(variable,low,high,literals,junction)>=0)throw new IllegalArgumentException("duplicate canonical condition");
        try {
            int id;
            if(!append&&freeHead!=0){id=freeHead;freeHead=(int)field(id,8);}
            else {if(span==Integer.MAX_VALUE)throw new PageStore.Failure(PageStore.Reason.INVALID_HANDLE,"condition ID space exhausted");id=span++;}
            write(id,0,1);write(id,1,variable);write(id,2,low);write(id,3,high);write(id,4,literals);write(id,5,junction);write(id,6,token);write(id,7,0);write(id,8,0);
            if(unique.intern(id,id)!=id)throw new IllegalStateException("condition identity disagreement");return id;
        }catch(RuntimeException|Error error){failed=true;throw error;}
    }
    /** Caller must prove functional equivalence. The same ID now owns a new
     * complete representative key; marks survive, former child references do not.
     * Return the old token for the enclosing owner to release. */
    long replace(int id,int variable,int low,int high,long literals,int junction,long token) {
        open();liveField(id,0);int duplicate=find(variable,low,high,literals,junction);
        if(duplicate>=0&&duplicate!=id)throw new IllegalArgumentException("duplicate replacement representative");
        try {
            long previous=field(id,6);
            if(!unique.remove(id))throw new IllegalStateException("missing replacement identity");
            write(id,1,variable);write(id,2,low);write(id,3,high);write(id,4,literals);write(id,5,junction);write(id,6,token);
            if(unique.intern(id,id)!=id)throw new IllegalStateException("replacement identity disagreement");
            return previous;
        }catch(RuntimeException|Error failure){failed=true;throw failure;}
    }
    void retire(int id) {
        retire(id,true);
    }
    /** A scope retired this row without recycling its meaning. Call exactly
     * once after all scope-local decision caches have closed. */
    void recycleRetired(int id){
        open();if(id<2||id>=span||field(id,0)!=0||field(id,8)!=0)throw new IllegalArgumentException("invalid deferred retirement");
        write(id,8,freeHead);freeHead=id;
    }
    void retire(int id,boolean recycle) {
        open();liveField(id,0);
        try {
            if(!unique.remove(id))throw new IllegalStateException("missing condition identity");
            for(int n=0;n<WIDTH;n++)write(id,n,0);
            if(recycle){write(id,8,freeHead);freeHead=id;}
        }catch(RuntimeException|Error error){failed=true;throw error;}
    }
    /** Preserve old free links while clearing retired tail rows and shrinking the live span. */
    void trim() {
        open();
        try {
            int end=span;while(end>2&&field(end-1,0)==0)end--;
            int nextHead=0;
            for(int id=freeHead;id!=0;) {
                int next=(int)field(id,8);
                if(id<end){write(id,8,nextHead);nextHead=id;}
                id=next;
            }
            for(int id=end;id<span;id++)for(int n=0;n<WIDTH;n++)write(id,n,0);
            freeHead=nextHead;span=end;Arrays.fill(cacheIds,-1);
        }catch(RuntimeException|Error error){failed=true;throw error;}
    }
    /** The caller has not linked any retired tail row into the free list. */
    void trimUnlinkedTail() {
        open();try {
            int end=span;while(end>2&&field(end-1,0)==0)end--;
            if(freeHead>=end)throw new IllegalStateException("tail retirement linked a free ID");
            span=end;Arrays.fill(cacheIds,-1);
        }catch(RuntimeException|Error error){failed=true;throw error;}
    }
    @Override public void close() {
        if(closed)return;closed=true;
        RuntimeException failure=ActivationSolver.closeResource(unique,null);failure=ActivationSolver.closeResource(rows,failure);
        metadata.close();unique=null;rows=null;cacheIds=null;cacheFields=null;staged=null;if(failure!=null)throw failure;
    }
}
