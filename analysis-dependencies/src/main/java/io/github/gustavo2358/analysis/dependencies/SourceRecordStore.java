package io.github.gustavo2358.analysis.dependencies;

import io.github.gustavo2358.analysis.solver.*;
import java.util.*;

/** Exact private recipes. The fingerprint orders descent; all thirty fields resolve collisions. */
final class SourceRecordStore implements AutoCloseable {
    static final int FIELDS=30;
    private static final int WIDTH=32,HASH=31;
    private static final AnalysisResources.Phase PHASE=AnalysisResources.Phase.INDEX;
    private final AnalysisResources resources;
    private final AnalysisResources.Reservation control;
    private PagedLongArray cells;
    private PagedLongIndex index;
    private long[] probe;
    private long issued;
    private boolean closed,failed,sealed;
    SourceRecordStore(PageStore pages,AnalysisResources resources) {
        this.resources=Objects.requireNonNull(resources);control=resources.reserve(AnalysisResources.Pool.RESIDENT,768,PHASE);
        try {probe=new long[WIDTH];cells=new PagedLongArray(pages,Long.MAX_VALUE,resources,PHASE);index=new PagedLongIndex(pages,resources,PHASE,this::compare);}
        catch(RuntimeException|Error failure){suppress(this,failure);throw failure;}
    }
    synchronized long intern(long tag,long[] fields) {
        open();if(sealed)throw new IllegalStateException("source records sealed");
        if(tag<=0||fields.length!=FIELDS)throw new IllegalArgumentException("exact source recipe required");
        probe[0]=tag;for(int i=0;i<FIELDS;i++)probe[i+1]=fields[i];
        long hash=0x9e3779b97f4a7c15L;
        for(int i=0;i<=FIELDS;i++){long value=probe[i];value^=value>>>33;value*=0xff51afd7ed558ccdL;value^=value>>>33;hash=Long.rotateLeft(hash,11)^value;}
        probe[HASH]=(int)(hash^(hash>>>32));
        try {
            resources.work(1,PHASE);long known=index.find(0);if(known!=0)return known;
            long id=Math.incrementExact(issued),base=Math.multiplyExact(id-1,WIDTH);
            if(base>cells.length()-WIDTH)throw new IllegalStateException("source recipe address capacity");
            issued=id;for(int i=0;i<WIDTH;i++)cells.set(base+i,probe[i]);
            if(index.intern(id,id)!=id)throw new IllegalStateException("source recipe canonical disagreement");return id;
        }catch(RuntimeException|Error failure){failed=true;throw failure;}
        finally{Arrays.fill(probe,0);}
    }
    private long cell(long id,int field){return id==0?probe[field]:cells.get(Math.addExact(Math.multiplyExact(id-1,WIDTH),field));}
    private int compare(long a,long b) {
        if(a==b)return 0;int difference=Long.compare(cell(a,HASH),cell(b,HASH));if(difference!=0)return difference;
        for(int i=0;i<=FIELDS;i++){difference=Long.compare(cell(a,i),cell(b,i));if(difference!=0)return difference;}return 0;
    }
    synchronized long field(long id,int field) {
        open();if(id<=0||id>issued||field<0||field>FIELDS)throw new IllegalArgumentException("unknown source recipe/field");
        try{return cell(id,field);}catch(RuntimeException|Error failure){failed=true;throw failure;}
    }
    synchronized void seal(){open();sealed=true;}
    private void open(){if(closed||failed)throw new IllegalStateException("source records closed or aborted");}
    private static void suppress(AutoCloseable owner,Throwable failure){try{owner.close();}catch(Exception|Error cleanup){if(cleanup!=failure)failure.addSuppressed(cleanup);}}
    @Override public synchronized void close() {
        if(closed)return;closed=true;probe=null;Throwable failure=null;
        for(AutoCloseable owner:new AutoCloseable[]{index,cells,control})if(owner!=null)try{owner.close();}catch(Exception|Error cleanup){if(failure==null)failure=cleanup;else if(cleanup!=failure)failure.addSuppressed(cleanup);}
        cells=null;index=null;issued=0;
        if(failure instanceof RuntimeException error)throw error;if(failure instanceof Error error)throw error;if(failure!=null)throw new IllegalStateException("source records cleanup",failure);
    }
}
