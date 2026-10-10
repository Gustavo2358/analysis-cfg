package io.github.gustavo2358.analysis.dependencies;

import io.github.gustavo2358.analysis.dependencies.source.*;
import io.github.gustavo2358.analysis.solver.*;
import java.util.*;

/** Builds immutable typed storage, never a successful source admission certificate. */
public final class SourceSnapshotBuilder implements AutoCloseable {
    private static final AnalysisResources.Phase PHASE=AnalysisResources.Phase.INDEX;
    private static final int LEAF=100,BRANCH=101,LIST_WIDTH=96;
    private final AnalysisResources resources;
    private final AnalysisResources.Reservation control;
    private SourceTextDictionary texts;
    private SourceRecordStore records;
    private PagedLongArray lists;
    private long[] recipe;
    private Expiry expiry;
    private long issuedLists,activeLists;
    private boolean closed;
    public SourceSnapshotBuilder(PageStore pages,AnalysisResources resources) {
        this.resources=Objects.requireNonNull(resources);Objects.requireNonNull(pages);
        control=resources.reserve(AnalysisResources.Pool.RESIDENT,1024,PHASE);
        try {recipe=new long[SourceRecordStore.FIELDS];texts=new SourceTextDictionary(pages,resources,PHASE);records=new SourceRecordStore(pages,resources);lists=new PagedLongArray(pages,Long.MAX_VALUE,resources,PHASE);expiry=new Expiry(this);control.onOwnerClose(expiry::expire);}
        catch(RuntimeException|Error failure){abort(failure);throw failure;}
    }
    private static long reference(long id,int kind){if(id<=0||id>Long.MAX_VALUE/4)throw new IllegalStateException("source handle address capacity");return (id<<2)|kind;}
    private void open(){if(closed)throw new IllegalStateException("source construction expired");}
    private void clear(){Arrays.fill(recipe,0);}
    private long record(int tag){return reference(records.intern(tag,recipe),2);}
    private long word(long ref,int field){if(ref<=0||(ref&3)!=2)throw new IllegalArgumentException("source record reference required");return records.field(ref>>>2,field);}
    private SourceShape shape(long ref){if(ref<=0)throw new IllegalArgumentException("positive source reference required");if((ref&3)==1){texts.characterCount(ref>>>2);return SourceShape.TEXT;}int tag=Math.toIntExact(word(ref,0));return tag==LEAF||tag==BRANCH?SourceShape.LIST:SourceShape.forTag(tag);}
    /** Sequence is borrowed and must remain unchanged until this call returns. */
    public synchronized long text(CharSequence value){open();try{return reference(texts.intern(Objects.requireNonNull(value)),1);}catch(RuntimeException|Error failure){abort(failure);throw failure;}}
    public synchronized long integer(int value){open();try{clear();recipe[0]=value;return record(SourceShape.INTEGER.tag());}catch(RuntimeException|Error failure){abort(failure);throw failure;}}
    public synchronized long bool(boolean value){open();try{clear();recipe[0]=value?1:0;return record(SourceShape.BOOLEAN.tag());}catch(RuntimeException|Error failure){abort(failure);throw failure;}}
    /** Fixed fields are borrowed for this call; each handle must belong to this builder. */
    public synchronized long record(SourceShape type,long[] fields) {
        return record(type,fields,0,fields==null?0:fields.length);
    }
    /** Exact-arity slice of a caller-funded fixed buffer; no per-record copy is retained. */
    public synchronized long record(SourceShape type,long[] fields,int offset,int count) {
        open();try {
            Objects.requireNonNull(type);Objects.requireNonNull(fields);
            Objects.checkFromIndexSize(offset,count,fields.length);
            if(!type.record()||count!=type.fieldCount())throw new IllegalArgumentException("source record shape/arity");
            for(int i=0;i<count;i++) {
                var slot=type.field(i);long value=fields[offset+i];
                if(value==0){if(!slot.optional())throw new IllegalArgumentException("required source field");continue;}
                if(shape(value)!=slot.shape())throw new IllegalArgumentException("source field shape");
                if(slot.shape()==SourceShape.LIST&&word(value,1)!=slot.element().tag())throw new IllegalArgumentException("source list element type");
            }
            clear();for(int i=0;i<count;i++)recipe[i]=fields[offset+i];return record(type.tag());
        }catch(RuntimeException|Error failure){abort(failure);throw failure;}
    }
    public synchronized long beginList(SourceShape element) {
        open();try {
            Objects.requireNonNull(element);if(element==SourceShape.LIST)throw new IllegalArgumentException("closed source lists have non-list elements");
            long id=Math.incrementExact(issuedLists),base=Math.multiplyExact(id-1,LIST_WIDTH);
            if(base>lists.length()-LIST_WIDTH)throw new IllegalStateException("source list address capacity");
            long ref=reference(id,3);issuedLists=id;lists.set(base,element.tag());lists.set(base+1,1);activeLists=Math.incrementExact(activeLists);return ref;
        }catch(RuntimeException|Error failure){abort(failure);throw failure;}
    }
    private long listBase(long ref){if(ref<=0||(ref&3)!=3||(ref>>>2)>issuedLists)throw new IllegalArgumentException("source construction list required");long base=Math.multiplyExact((ref>>>2)-1,LIST_WIDTH);if(lists.get(base+1)!=1)throw new IllegalStateException("source list already finished");return base;}
    public synchronized void append(long list,long item) {
        open();try {
            long base=listBase(list),tag=lists.get(base);
            if(shape(item).tag()!=tag)throw new IllegalArgumentException("source list element shape");
            long count=Math.incrementExact(lists.get(base+2));int size=Math.toIntExact(lists.get(base+3));
            lists.set(base+4+size,item);lists.set(base+2,count);lists.set(base+3,size+1);
            if(size==15)flushLeaf(base);
        }catch(RuntimeException|Error failure){abort(failure);throw failure;}
    }
    private long branch(long tag,long left,long right) {
        clear();recipe[0]=tag;recipe[1]=Math.addExact(word(left,2),word(right,2));recipe[2]=left;recipe[3]=right;return record(BRANCH);
    }
    private void flushLeaf(long base) {
        long tag=lists.get(base);int count=Math.toIntExact(lists.get(base+3));clear();recipe[0]=tag;recipe[1]=count;
        for(int i=0;i<count;i++)recipe[2+i]=lists.get(base+4+i);
        long node=record(LEAF);int level=0;
        while(true){if(level>=64)throw new IllegalStateException("source list carry capacity");long prior=lists.get(base+20+level);if(prior==0){lists.set(base+20+level,node);break;}lists.set(base+20+level,0);node=branch(tag,prior,node);level++;}
        for(int i=0;i<count;i++)lists.set(base+4+i,0);lists.set(base+3,0);
    }
    public synchronized long finishList(long list) {
        open();try {
            long base=listBase(list),tag=lists.get(base);if(lists.get(base+3)!=0)flushLeaf(base);
            long root=0;for(int level=63;level>=0;level--){long node=lists.get(base+20+level);if(node!=0)root=root==0?node:branch(tag,root,node);}
            if(root==0){clear();recipe[0]=tag;root=record(LEAF);}
            if(word(root,2)!=lists.get(base+2))throw new IllegalStateException("source list count disagreement");
            for(int i=0;i<LIST_WIDTH;i++)lists.set(base+i,0);activeLists--;return root;
        }catch(RuntimeException|Error failure){abort(failure);throw failure;}
    }
    /** Transfers all immutable stores. The builder expires; closing it cannot close the snapshot. */
    public synchronized SourceSnapshot build(long root) {
        open();try {
            if(shape(root)!=SourceShape.ROOT)throw new IllegalArgumentException("source root required");
            if(activeLists!=0)throw new IllegalStateException("unfinished source inventory");
            records.seal();lists.close();lists=null;open();recipe=null;
            var snapshot=new Snapshot(root,resources,control,texts,records,expiry);expiry.owner=snapshot;expiry=null;closed=true;texts=null;records=null;return snapshot;
        }catch(RuntimeException|Error failure){abort(failure);throw failure;}
    }
    private void abort(Throwable failure){try{close();}catch(RuntimeException|Error cleanup){if(cleanup!=failure)failure.addSuppressed(cleanup);}}
    @Override public synchronized void close() {
        if(closed)return;closed=true;recipe=null;if(expiry!=null){expiry.owner=null;expiry=null;}Throwable failure=null;
        for(AutoCloseable owner:new AutoCloseable[]{lists,records,texts,control})if(owner!=null)try{owner.close();}catch(Exception|Error cleanup){failure=add(failure,cleanup);}
        lists=null;records=null;texts=null;activeLists=0;rethrow(failure);
    }
    private static Throwable add(Throwable first,Throwable next){if(first==null)return next;if(first!=next)first.addSuppressed(next);return first;}
    private static void rethrow(Throwable failure){if(failure instanceof RuntimeException error)throw error;if(failure instanceof Error error)throw error;if(failure!=null)throw new IllegalStateException("source snapshot cleanup",failure);}

    /** One fixed cleanup action follows transfer; explicit builder close cannot expire its result. */
    private static final class Expiry {
        private AutoCloseable owner;
        private Expiry(AutoCloseable owner){this.owner=owner;}
        private void expire(){var current=owner;if(current==null)return;try{current.close();}catch(Exception|Error failure){rethrow(failure);}}
    }

    private static final class Snapshot implements SourceSnapshot {
        private long root;
        private final AnalysisResources resources;
        private AnalysisResources.Reservation control;
        private SourceTextDictionary texts;
        private SourceRecordStore records;
        private Expiry expiry;
        private Projection projections;
        private Snapshot(long root,AnalysisResources resources,AnalysisResources.Reservation control,SourceTextDictionary texts,SourceRecordStore records,Expiry expiry){this.root=root;this.resources=resources;this.control=control;this.texts=texts;this.records=records;this.expiry=expiry;}
        private void open(){if(records==null)throw new IllegalStateException("source snapshot expired");}
        private void abort(Throwable failure){try{close();}catch(RuntimeException|Error cleanup){if(cleanup!=failure)failure.addSuppressed(cleanup);}}
        private long word(long ref,int field){if(ref<=0||(ref&3)!=2)throw new IllegalArgumentException("source record reference required");return records.field(ref>>>2,field);}
        @Override public synchronized long root(){open();try{resources.work(1,PHASE);return root;}catch(RuntimeException|Error failure){abort(failure);throw failure;}}
        @Override public synchronized SourceShape shape(long ref){open();try{if(ref<=0)throw new IllegalArgumentException("positive source reference required");if((ref&3)==1){texts.characterCount(ref>>>2);return SourceShape.TEXT;}int tag=Math.toIntExact(word(ref,0));return tag==LEAF||tag==BRANCH?SourceShape.LIST:SourceShape.forTag(tag);}catch(RuntimeException|Error failure){abort(failure);throw failure;}}
        @Override public synchronized long field(long ref,SourceShape expected,int ordinal){open();try{if(!expected.record()||shape(ref)!=expected)throw new IllegalArgumentException("source record shape");Objects.checkIndex(ordinal,expected.fieldCount());return word(ref,ordinal+1);}catch(RuntimeException|Error failure){abort(failure);throw failure;}}
        @Override public synchronized long size(long list,SourceShape element){open();try{if(shape(list)!=SourceShape.LIST||word(list,1)!=element.tag())throw new IllegalArgumentException("source list element type");return word(list,2);}catch(RuntimeException|Error failure){abort(failure);throw failure;}}
        @Override public synchronized long element(long list,SourceShape type,long ordinal) {
            open();try {
                long size=size(list,type);if(ordinal<0||ordinal>=size)throw new IndexOutOfBoundsException("source list ordinal");
                long node=list;while(word(node,0)==BRANCH){long left=word(node,3),count=word(left,2);if(ordinal<count)node=left;else{ordinal-=count;node=word(node,4);}}
                return word(node,3+Math.toIntExact(ordinal));
            }catch(RuntimeException|Error failure){abort(failure);throw failure;}
        }
        @Override public synchronized int integer(long ref){open();try{if(shape(ref)!=SourceShape.INTEGER)throw new IllegalArgumentException("source integer required");return Math.toIntExact(word(ref,1));}catch(RuntimeException|Error failure){abort(failure);throw failure;}}
        @Override public synchronized boolean bool(long ref){open();try{if(shape(ref)!=SourceShape.BOOLEAN)throw new IllegalArgumentException("source boolean required");return word(ref,1)!=0;}catch(RuntimeException|Error failure){abort(failure);throw failure;}}
        private long text(long ref){if(shape(ref)!=SourceShape.TEXT)throw new IllegalArgumentException("source text required");return ref>>>2;}
        @Override public synchronized long characterCount(long ref){open();try{return texts.characterCount(text(ref));}catch(RuntimeException|Error failure){abort(failure);throw failure;}}
        @Override public synchronized void readCharacters(long ref,long offset,char[] destination,int at,int count){open();try{texts.readCharacters(text(ref),offset,destination,at,count);}catch(RuntimeException|Error failure){abort(failure);throw failure;}}
        @Override public synchronized boolean matches(long ref,String value){open();try{return texts.matches(text(ref),value);}catch(RuntimeException|Error failure){abort(failure);throw failure;}}
        @Override public synchronized TextProjection project(long ref) {
            open();AnalysisResources.Reservation capacity=null;SourceTextDictionary.Projection text=null;
            try {
                long id=text(ref);capacity=resources.reserve(AnalysisResources.Pool.SCRATCH,160,PHASE);text=texts.project(id);
                var projection=new Projection(this,text,capacity);text=null;capacity=null;
                projection.next=projections;if(projections!=null)projections.previous=projection;projections=projection;return projection;
            }catch(RuntimeException|Error failure){if(text!=null)try{text.close();}catch(RuntimeException|Error cleanup){add(failure,cleanup);}if(capacity!=null)try{capacity.close();}catch(RuntimeException|Error cleanup){add(failure,cleanup);}abort(failure);throw failure;}
        }
        private synchronized void release(Projection value) {
            if(value.owner!=this)return;if(value.previous==null)projections=value.next;else value.previous.next=value.next;if(value.next!=null)value.next.previous=value.previous;
            value.owner=null;value.previous=value.next=null;var text=value.text;value.text=null;var lease=value.control;value.control=null;Throwable failure=null;
            try{text.close();}catch(RuntimeException|Error cleanup){failure=cleanup;}try{lease.close();}catch(RuntimeException|Error cleanup){failure=add(failure,cleanup);}rethrow(failure);
        }
        @Override public synchronized void close() {
            if(records==null)return;expiry.owner=null;expiry=null;Throwable failure=null;
            while(projections!=null)try{release(projections);}catch(RuntimeException|Error cleanup){failure=add(failure,cleanup);}
            var rows=records;records=null;var text=texts;texts=null;var lease=control;control=null;root=0;
            for(AutoCloseable owner:new AutoCloseable[]{rows,text,lease})try{owner.close();}catch(Exception|Error cleanup){failure=add(failure,cleanup);}rethrow(failure);
        }
    }
    private static final class Projection implements SourceSnapshot.TextProjection {
        private Snapshot owner;
        private Projection previous,next;
        private SourceTextDictionary.Projection text;
        private AnalysisResources.Reservation control;
        private Projection(Snapshot owner,SourceTextDictionary.Projection text,AnalysisResources.Reservation control){this.owner=owner;this.text=text;this.control=control;}
        @Override public String value(){var current=owner;if(current==null)throw new IllegalStateException("source text projection expired");current.open();return text.value();}
        @Override public void close(){var current=owner;if(current!=null)current.release(this);}
    }
}
