package io.github.gustavo2358.analysis.dependencies;

import io.github.gustavo2358.analysis.dependencies.source.*;
import io.github.gustavo2358.analysis.solver.*;
import java.util.Objects;

/**
 * Internal paged nominal expression program borrowing admitted immutable SOURCE.
 * ASCII upper/leading/trailing SPACE trim commute, are idempotent and distribute
 * over CHOICE. At most eight contexts per complete syntax handle are specialized.
 * No resident syntax/String inventory, recursive walker, argument array or stack
 * grows with input. Compilation frames, memo, canonical nodes and vectors spill.
 * Payload is a borrowed SOURCE text handle, except -1 for the exact single SPACE;
 * zero belongs only to UNKNOWN/CHOICE. No value or executable fact is inferred.
 */
final class SourceSnapshotExpressions extends SourceAdmissionSnapshot.Dependent {
    static final int READ=1,LITERAL=2,UNKNOWN=3,CHOICE=4,UPPER=1,LEADING=2,TRAILING=4;
    private static final int WIDTH=5,MEMO=10;
    private static final AnalysisResources.Phase PHASE=AnalysisResources.Phase.DOMAIN;
    private volatile SourceAdmissionSnapshot admission;
    private SourceSnapshot source;
    private PageStore pages;
    private AnalysisResources resources;
    private AnalysisResources.Reservation control;
    private SourceAdmissionDirectory directory;
    private PagedLongArray rows,arguments;
    private PagedLongIndex choices;
    private long count,argumentCount,memoEntries;
    private volatile boolean closed,ready;

    private SourceSnapshotExpressions(SourceAdmissionSnapshot admission,AnalysisResources.Reservation capacity){this.admission=admission;control=capacity;}
    static SourceSnapshotExpressions prepare(SourceAdmissionSnapshot admission,PageStore pages,AnalysisResources resources) {
        Objects.requireNonNull(admission);Objects.requireNonNull(pages);Objects.requireNonNull(resources);admission.requireContext(pages,resources);
        var capacity=resources.reserve(AnalysisResources.Pool.RESIDENT,1024,PHASE);SourceSnapshotExpressions result=null;
        try {
            result=new SourceSnapshotExpressions(admission,capacity);var registration=capacity;capacity=null;registration.onOwnerClose(result::close);
            result.initialize(pages,resources);admission.attach(result,pages,resources);result.open();return result;
        }catch(RuntimeException|Error failure) {
            if(result!=null)result.abort(failure);else if(capacity!=null)try{capacity.close();}catch(RuntimeException|Error cleanup){suppress(failure,cleanup);}
            throw failure;
        }
    }
    private void initialize(PageStore pages,AnalysisResources resources) {
        if(closed)throw new IllegalStateException("SOURCE expression program expired before initialization");
        this.pages=pages;this.resources=resources;source=parent().snapshot();directory=new SourceAdmissionDirectory(pages,resources);
        rows=new PagedLongArray(pages,Long.MAX_VALUE,resources,PHASE);arguments=new PagedLongArray(pages,Long.MAX_VALUE,resources,PHASE);
        choices=new PagedLongIndex(pages,resources,PHASE,this::compareChoice);parent().snapshot();
        if(closed)throw new IllegalStateException("SOURCE expression program expired before completion");ready=true;
    }
    private SourceAdmissionSnapshot parent(){var value=admission;if(value==null)throw new IllegalStateException("SOURCE expression program expired");return value;}
    private SourceSnapshot view(){var value=source;if(value==null)throw new IllegalStateException("SOURCE expression program expired");return value;}
    private void open(){if(closed||!ready)throw new IllegalStateException("SOURCE expression program expired or incomplete");parent().snapshot();}
    private long column(long node,int column){return rows.get(Math.addExact(Math.multiplyExact(node,WIDTH),column));}
    private void provisional(long node,int kind,int mask,long payload,long start,long children) {
        long at=Math.multiplyExact(node,WIDTH);rows.set(at,kind);rows.set(at+1,mask);rows.set(at+2,payload);rows.set(at+3,start);rows.set(at+4,children);
    }
    private int compareChoice(long left,long right) {
        long a=column(left,4),b=column(right,4);int order=Long.compare(a,b);if(order!=0)return order;
        long sa=column(left,3),sb=column(right,3);
        for(long i=0;i<a;i++){order=Long.compare(arguments.get(sa+i),arguments.get(sb+i));if(order!=0)return order;}return 0;
    }
    private long field(long row,int ordinal){return view().field(row,SourceShape.TERM,ordinal);}
    private boolean kind(long term,String expected){return view().matches(field(term,0),expected);}
    private long arity(long term){long list=field(term,2);return list==0?0:view().size(list,SourceShape.TERM);}
    private long child(long term,long ordinal){return view().element(field(term,2),SourceShape.TERM,ordinal);}
    private int mask(long term) {
        if(kind(term,"UPPER_ASCII"))return UPPER;if(kind(term,"TRIM_LEADING_SPACES"))return LEADING;
        if(kind(term,"TRIM_TRAILING_SPACES"))return TRAILING;if(kind(term,"TRIM_SPACES"))return LEADING|TRAILING;
        throw new IllegalStateException("admitted SOURCE unary expression operator missing");
    }
    private long remember(long term,int mask,long result) {
        long before=directory.size(),row=directory.intern(0,MEMO,term,mask,result,0);
        if(directory.size()!=before)memoEntries=Math.incrementExact(memoEntries);
        if(directory.reference(row)!=result)throw new IllegalStateException("SOURCE compilation memo disagreement");return result;
    }
    /** Returns zero only for a nonleaf syntax context not compiled yet. */
    private long known(long term,int mask) {
        long memo=directory.find(0,MEMO,term,mask);if(memo!=0)return directory.reference(memo);
        if(arity(term)!=0)return 0;
        int kind=kind(term,"READ")?READ:kind(term,"LITERAL")||kind(term,"SPACES")?LITERAL:UNKNOWN;
        long payload=kind==UNKNOWN?0:kind(term,"SPACES")?-1:field(term,1);
        if(kind==LITERAL&&payload>0&&view().matches(payload," "))payload=-1;
        int context=kind==UNKNOWN?0:mask;long leaf=directory.find(0,kind,payload,context),result;
        if(leaf!=0)result=directory.reference(leaf);
        else {
            result=Math.incrementExact(count);provisional(result,kind,context,payload,0,0);
            long row=directory.intern(0,kind,payload,context,result,0);
            if(directory.reference(row)!=result)throw new IllegalStateException("SOURCE expression leaf canonical disagreement");count=result;
        }return remember(term,mask,result);
    }
    private static long push(PagedLongArray frames,long depth,long term,int mask,long vector,long children) {
        long at=Math.multiplyExact(depth,WIDTH);frames.set(at,term);frames.set(at+1,mask);frames.set(at+2,0);frames.set(at+3,vector);frames.set(at+4,children);
        return Math.addExact(vector,children);
    }
    long compile(long term) {
        open();try {
            if(view().shape(term)!=SourceShape.TERM)throw new IllegalArgumentException("SOURCE expression term shape");
            long existing=known(term,0);if(existing!=0)return existing;
            try(var frames=new PagedLongArray(pages,Long.MAX_VALUE,resources,PHASE);var vectors=new PagedLongArray(pages,Long.MAX_VALUE,resources,PHASE)) {
                long top=0,nextVector=push(frames,0,term,0,0,arity(term)),result=0;
                while(top>=0) {
                    long at=top*WIDTH,current=frames.get(at),next=frames.get(at+2),start=frames.get(at+3),children=frames.get(at+4);int context=Math.toIntExact(frames.get(at+1));boolean choice=kind(current,"CHOICE");
                    if(next<children) {
                        long subterm=child(current,next);int childContext=choice?context:context|mask(current);long node=known(subterm,childContext);
                        if(node==0){top=Math.incrementExact(top);nextVector=push(frames,top,subterm,childContext,nextVector,arity(subterm));continue;}
                        vectors.set(start+next,node);frames.set(at+2,next+1);continue;
                    }
                    result=choice?choice(vectors,start,children):vectors.get(start);remember(current,context,result);top--;
                    if(top>=0){long parent=top*WIDTH,index=frames.get(parent+2);vectors.set(frames.get(parent+3)+index,result);frames.set(parent+2,index+1);}
                }
                parent().snapshot();return result;
            }
        }catch(RuntimeException|Error failure){abort(failure);throw failure;}
    }
    private static void swap(PagedLongArray vector,long first,long second){long value=vector.get(first);vector.set(first,vector.get(second));vector.set(second,value);}
    private static void sift(PagedLongArray vector,long start,long size,long root) {
        while(root<size/2) {
            long child=Math.addExact(Math.multiplyExact(root,2),1);
            if(child+1<size&&vector.get(start+child)<vector.get(start+child+1))child++;
            if(vector.get(start+root)>=vector.get(start+child))return;swap(vector,start+root,start+child);root=child;
        }
    }
    private long choice(PagedLongArray vector,long start,long size) {
        for(long parent=size/2;parent>0;parent--)sift(vector,start,size,parent-1);
        for(long end=size;end>1;){swap(vector,start,start+--end);sift(vector,start,end,0);}
        long unique=0,previous=0;
        for(long i=0;i<size;i++){long value=vector.get(start+i);if(value!=previous){vector.set(start+unique++,value);previous=value;}}
        if(unique==1)return vector.get(start);
        if(unique<2)throw new IllegalStateException("admitted CHOICE without arguments");
        long provisional=Math.incrementExact(count);
        for(long i=0;i<unique;i++)arguments.set(Math.addExact(argumentCount,i),vector.get(start+i));
        provisional(provisional,CHOICE,0,0,argumentCount,unique);long result=choices.intern(provisional,provisional);
        if(result==provisional){count=provisional;argumentCount=Math.addExact(argumentCount,unique);}return result;
    }
    private long value(long node,int field){if(node<=0||node>count)throw new IllegalArgumentException("compiled SOURCE expression node");return column(node,field);}
    int kind(long node){open();try{return Math.toIntExact(value(node,0));}catch(RuntimeException|Error failure){abort(failure);throw failure;}}
    int transforms(long node){open();try{return Math.toIntExact(value(node,1));}catch(RuntimeException|Error failure){abort(failure);throw failure;}}
    long payload(long node){open();try{return value(node,2);}catch(RuntimeException|Error failure){abort(failure);throw failure;}}
    long argumentCount(long node){open();try{return value(node,4);}catch(RuntimeException|Error failure){abort(failure);throw failure;}}
    long argument(long node,long ordinal){open();try{long count=value(node,4);if(ordinal<0||ordinal>=count)throw new IndexOutOfBoundsException("compiled SOURCE expression argument");return arguments.get(value(node,3)+ordinal);}catch(RuntimeException|Error failure){abort(failure);throw failure;}}
    long nodeCount(){open();return count;}
    long retainedArguments(){open();return argumentCount;}
    long memoEntries(){open();return memoEntries;}
    private static void suppress(Throwable primary,Throwable cleanup){if(primary!=cleanup)primary.addSuppressed(cleanup);}
    private void abort(Throwable primary){try{close();}catch(RuntimeException|Error cleanup){suppress(primary,cleanup);}}
    @Override public void close() {
        AutoCloseable[] owned;
        synchronized(this){if(closed)return;closed=true;ready=false;owned=new AutoCloseable[]{choices,arguments,rows,directory,control};choices=null;arguments=rows=null;directory=null;control=null;source=null;admission=null;pages=null;resources=null;}
        var parent=owner;if(parent!=null)parent.release(this);Throwable failure=null;
        for(var value:owned)if(value!=null)try{value.close();}catch(Exception|Error cleanup){if(failure==null)failure=cleanup;else suppress(failure,cleanup);}
        if(failure instanceof RuntimeException error)throw error;if(failure instanceof Error error)throw error;if(failure!=null)throw new IllegalStateException("SOURCE expression cleanup",failure);
    }
}
