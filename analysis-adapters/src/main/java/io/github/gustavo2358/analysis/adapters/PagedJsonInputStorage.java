package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.air.json.AirJson;
import io.github.gustavo2358.air.model.AirSnapshotBuilder;
import io.github.gustavo2358.analysis.solver.*;
import java.util.Objects;

/** Shared page-backed physical JSON columns, exact names and container-local ordinal vectors. */
public final class PagedJsonInputStorage implements AirJson.InputStorage {
    private static final AnalysisResources.Phase PHASE=AnalysisResources.Phase.DECODE;
    private final AnalysisResources resources;
    private final AnalysisResources.Reservation resident;
    private PagedLongArray[] columns;
    private PagedLongArray values, containers;
    private long blocks, nameIds;
    private PagedLongIndex names;
    private boolean closed,failed;
    public PagedJsonInputStorage(PageStore pages,AnalysisResources resources) {
        Objects.requireNonNull(pages);this.resources=Objects.requireNonNull(resources);resident=resources.reserve(AnalysisResources.Pool.RESIDENT,512,PHASE);
        try {
            columns=new PagedLongArray[3];for(int i=0;i<3;i++)columns[i]=new PagedLongArray(pages,Long.MAX_VALUE,resources,PHASE);
            values=new PagedLongArray(pages,Long.MAX_VALUE,resources,PHASE);containers=new PagedLongArray(pages,Long.MAX_VALUE,resources,PHASE);
            names=new PagedLongIndex(pages,resources,PHASE,this::compareNames);
        }catch(RuntimeException|Error failure){closeSuppressed(failure);throw failure;}
    }
    private long base(long token){if(token<=0||token>Long.MAX_VALUE/8)throw new IllegalArgumentException("JSON token address capacity");return (token-1)*8;}
    private long word(long token,int field){return columns[0].get(base(token)+field);}
    private int character(long index){return (int)((columns[1].get(index>>>2)>>>((index&3)*16))&65535);}
    private int compareNames(long a,long b){if(a==b)return 0;long n=word(a,5),m=word(b,5),x=word(a,4),y=word(b,4);for(long i=0,limit=Math.min(n,m);i<limit;i++){int c=Integer.compare(character(x+i),character(y+i));if(c!=0)return c;}return Long.compare(n,m);}
    @Override public synchronized long get(Column column,long index){open();try{return columns[Objects.requireNonNull(column).ordinal()].get(index);}catch(RuntimeException|Error failure){failed=true;throw failure;}}
    @Override public synchronized void set(Column column,long index,long value){open();try{columns[Objects.requireNonNull(column).ordinal()].set(index,value);}catch(RuntimeException|Error failure){failed=true;throw failure;}}
    // Each parent owns child-root/height/count and name-membership-root/height.
    private long container(long parent){if(parent<=0||parent>Long.MAX_VALUE/5)throw new IllegalArgumentException("JSON parent address capacity");return (parent-1)*5;}
    private long block(){if(blocks==Long.MAX_VALUE/16)throw new IllegalStateException("JSON ordinal vector address capacity");return ++blocks;}
    private long slot(long block,long ordinal,int height){return (block-1)*16+((ordinal>>>(height*4))&15);}
    private long findVector(long meta,long ordinal){
        long node=containers.get(meta);int height=(int)containers.get(meta+1);
        if(node==0||(height<15&&(ordinal>>>((height+1)*4))!=0))return 0;
        while(height>0){node=values.get(slot(node,ordinal,height--));if(node==0)return 0;}
        return values.get(slot(node,ordinal,0));
    }
    private void putVector(long meta,long ordinal,long value){
        long root=containers.get(meta);int height=(int)containers.get(meta+1);if(root==0)root=block();
        while(height<15&&(ordinal>>>((height+1)*4))!=0){long next=block();values.set(slot(next,0,0),root);root=next;height++;}
        long node=root;for(int level=height;level>0;level--){long at=slot(node,ordinal,level),next=values.get(at);if(next==0){next=block();values.set(at,next);}node=next;}
        values.set(slot(node,ordinal,0),value);containers.set(meta,root);containers.set(meta+1,height);
    }
    /** Container-local radix vector; lookup depth depends on this ordinal, never total JSON nodes. */
    @Override public synchronized long child(long parent,long ordinal){open();try{
        long meta=container(parent),count=containers.get(meta+2);if(ordinal<0||ordinal>=count)throw new IllegalStateException("missing JSON child");
        long child=findVector(meta,ordinal);if(child<=0)throw new IllegalStateException("unfinished JSON child");return child;
    }catch(RuntimeException|Error failure){failed=true;throw failure;}}
    /** Append exactly one next ordinal; nested containers own independent vectors in shared columns. */
    @Override public synchronized void child(long parent,long ordinal,long child){open();try{
        if(child<=0)throw new IllegalArgumentException("positive JSON child");long meta=container(parent),count=containers.get(meta+2);
        if(ordinal<0||ordinal!=count)throw new IllegalStateException("JSON ordinals must append exactly once in order");
        if(count==Long.MAX_VALUE)throw new IllegalStateException("JSON child count capacity");putVector(meta,ordinal,child);containers.set(meta+2,count+1);
    }catch(RuntimeException|Error failure){failed=true;throw failure;}}
    @Override public synchronized boolean firstField(long parent,long nameToken){open();try{
        long meta=container(parent)+3;if(word(nameToken,0)!=2)throw new IllegalArgumentException("JSON name must be a text token");
        long name=names.find(nameToken);if(name==0){if(nameIds==Long.MAX_VALUE)throw new IllegalStateException("JSON name identity capacity");name=names.intern(nameToken,++nameIds);}
        if(findVector(meta,name)!=0)return false;putVector(meta,name,1);return true;
    }catch(RuntimeException|Error failure){failed=true;throw failure;}}
    @Override public synchronized AirSnapshotBuilder.Lease claim(long bytes){open();try{var lease=resources.reserve(AnalysisResources.Pool.SCRATCH,bytes,PHASE);return lease::close;}catch(RuntimeException|Error failure){failed=true;throw failure;}}
    private void open(){if(closed||failed)throw new IllegalStateException("paged JSON staging closed or aborted");}
    private void closeSuppressed(Throwable failure){try{close();}catch(RuntimeException|Error cleanup){if(failure!=cleanup)failure.addSuppressed(cleanup);}}
    @Override public synchronized void close(){if(closed)return;closed=true;Throwable failure=null;
        AutoCloseable[] owners={names,values,containers};for(var owner:owners)if(owner!=null)try{owner.close();}catch(Exception|Error error){if(failure==null)failure=error;else if(error!=failure)failure.addSuppressed(error);}
        if(columns!=null)for(var column:columns)if(column!=null)try{column.close();}catch(RuntimeException|Error error){if(failure==null)failure=error;else if(error!=failure)failure.addSuppressed(error);}
        columns=null;names=null;values=null;containers=null;resident.close();if(failure instanceof RuntimeException error)throw error;if(failure instanceof Error error)throw error;if(failure!=null)throw new IllegalStateException("JSON staging cleanup",failure);
    }
}
