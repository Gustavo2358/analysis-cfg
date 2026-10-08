package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.air.json.AirJson;
import io.github.gustavo2358.air.model.AirSnapshotBuilder;
import io.github.gustavo2358.analysis.solver.*;
import java.util.Objects;

/** Shared page-backed physical JSON columns, exact names and random-access child index. */
public final class PagedJsonInputStorage implements AirJson.InputStorage {
    private static final AnalysisResources.Phase PHASE=AnalysisResources.Phase.DECODE;
    private final AnalysisResources resources;
    private final AnalysisResources.Reservation resident;
    private PagedLongArray[] columns;
    private PagedLongArray values;
    private CanonicalTupleArena addresses;
    private PagedLongIndex names;
    private long[] key;
    private boolean closed,failed;
    public PagedJsonInputStorage(PageStore pages,AnalysisResources resources) {
        Objects.requireNonNull(pages);this.resources=Objects.requireNonNull(resources);resident=resources.reserve(AnalysisResources.Pool.RESIDENT,512,PHASE);
        try {
            columns=new PagedLongArray[3];for(int i=0;i<3;i++)columns[i]=new PagedLongArray(pages,Long.MAX_VALUE,resources,PHASE);
            values=new PagedLongArray(pages,Long.MAX_VALUE,resources,PHASE);addresses=new CanonicalTupleArena(pages,resources,PHASE,3,new int[0]);key=new long[3];
            names=new PagedLongIndex(pages,resources,PHASE,this::compareNames);
        }catch(RuntimeException|Error failure){closeSuppressed(failure);throw failure;}
    }
    private long base(long token){if(token<=0||token>Long.MAX_VALUE/8)throw new IllegalArgumentException("JSON token address capacity");return (token-1)*8;}
    private long word(long token,int field){return columns[0].get(base(token)+field);}
    private int character(long index){return (int)((columns[1].get(index>>>2)>>>((index&3)*16))&65535);}
    private int compareNames(long a,long b){if(a==b)return 0;long n=word(a,5),m=word(b,5),x=word(a,4),y=word(b,4);for(long i=0,limit=Math.min(n,m);i<limit;i++){int c=Integer.compare(character(x+i),character(y+i));if(c!=0)return c;}return Long.compare(n,m);}
    @Override public synchronized long get(Column column,long index){open();try{return columns[Objects.requireNonNull(column).ordinal()].get(index);}catch(RuntimeException|Error failure){failed=true;throw failure;}}
    @Override public synchronized void set(Column column,long index,long value){open();try{columns[Objects.requireNonNull(column).ordinal()].set(index,value);}catch(RuntimeException|Error failure){failed=true;throw failure;}}
    private void address(int recipe,long parent,long ordinal){if(parent<=0||ordinal<0)throw new IllegalArgumentException("positive JSON parent and nonnegative ordinal/name required");key[0]=recipe;key[1]=parent;key[2]=ordinal;}
    @Override public synchronized long child(long parent,long ordinal){open();try{address(0,parent,ordinal);long handle=addresses.find(key);if(handle==0)throw new IllegalStateException("missing JSON child");long child=values.get(handle-1);if(child<=0)throw new IllegalStateException("unfinished JSON child");return child;}catch(RuntimeException|Error failure){failed=true;throw failure;}}
    @Override public synchronized void child(long parent,long ordinal,long child){open();try{if(child<=0)throw new IllegalArgumentException("positive JSON child");address(0,parent,ordinal);if(addresses.find(key)!=0)throw new IllegalStateException("duplicate JSON ordinal");long handle=addresses.intern(key);values.set(handle-1,child);}catch(RuntimeException|Error failure){failed=true;throw failure;}}
    @Override public synchronized boolean firstField(long parent,long nameToken){open();try{if(word(nameToken,0)!=2)throw new IllegalArgumentException("JSON name must be a text token");long name=names.intern(nameToken,nameToken);address(1,parent,name);if(addresses.find(key)!=0)return false;addresses.intern(key);return true;}catch(RuntimeException|Error failure){failed=true;throw failure;}}
    @Override public synchronized AirSnapshotBuilder.Lease claim(long bytes){open();try{var lease=resources.reserve(AnalysisResources.Pool.SCRATCH,bytes,PHASE);return lease::close;}catch(RuntimeException|Error failure){failed=true;throw failure;}}
    private void open(){if(closed||failed)throw new IllegalStateException("paged JSON staging closed or aborted");}
    private void closeSuppressed(Throwable failure){try{close();}catch(RuntimeException|Error cleanup){if(failure!=cleanup)failure.addSuppressed(cleanup);}}
    @Override public synchronized void close(){if(closed)return;closed=true;Throwable failure=null;
        AutoCloseable[] owners={names,addresses,values};for(var owner:owners)if(owner!=null)try{owner.close();}catch(Exception|Error error){if(failure==null)failure=error;else if(error!=failure)failure.addSuppressed(error);}
        if(columns!=null)for(var column:columns)if(column!=null)try{column.close();}catch(RuntimeException|Error error){if(failure==null)failure=error;else if(error!=failure)failure.addSuppressed(error);}
        columns=null;names=null;addresses=null;values=null;key=null;resident.close();if(failure instanceof RuntimeException error)throw error;if(failure instanceof Error error)throw error;if(failure!=null)throw new IllegalStateException("JSON staging cleanup",failure);
    }
}
