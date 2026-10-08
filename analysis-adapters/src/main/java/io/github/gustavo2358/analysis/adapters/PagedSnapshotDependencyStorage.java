package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.air.model.AirSnapshotBuilder;
import io.github.gustavo2358.analysis.dependencies.SnapshotDependencyAnalysis;
import io.github.gustavo2358.analysis.dependencies.DependencyProgramStore;
import io.github.gustavo2358.analysis.solver.*;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;

/** Managed object-to-candidate relation; text and complete producer chains are paged payload. */
public final class PagedSnapshotDependencyStorage implements SnapshotDependencyAnalysis.Storage {
    private static final AnalysisResources.Phase PHASE=AnalysisResources.Phase.DOMAIN;
    private static final int FIELDS=5,SUPPORT_FIELDS=2;
    private final AnalysisResources resources;
    private final AnalysisResources.Reservation resident;
    private PagedLongIndex objects;
    private PagedLongArray heads,rows,characters,supports;
    private long objectCount,count,characterCount,supportCount,cursor,textStart,textLength,currentSupportStart,currentSupportCount;
    private boolean closed,failed;

    public PagedSnapshotDependencyStorage(PageStore pages,AnalysisResources resources) {
        Objects.requireNonNull(pages);this.resources=Objects.requireNonNull(resources);resident=resources.reserve(AnalysisResources.Pool.RESIDENT,256,PHASE);
        try{objects=new PagedLongIndex(pages,resources,PHASE);heads=new PagedLongArray(pages,Long.MAX_VALUE,resources,PHASE);rows=new PagedLongArray(pages,Long.MAX_VALUE,resources,PHASE);characters=new PagedLongArray(pages,Long.MAX_VALUE,resources,PHASE);supports=new PagedLongArray(pages,Long.MAX_VALUE,resources,PHASE);clearCurrent();}
        catch(RuntimeException|Error failure){closeSuppressed(failure);throw failure;}
    }

    public synchronized void add(long object,String text,List<DependencyProgramStore.Producer> producers) {
        open();Objects.requireNonNull(text);Objects.requireNonNull(producers);if(producers.isEmpty())throw new IllegalArgumentException("candidate requires a producer");
        try {
            long objectOrdinal=objects.find(object);if(objectOrdinal==0){if(objectCount==Long.MAX_VALUE)throw new IllegalStateException("dependency object cardinality exceeds signed 64-bit range");objectOrdinal=++objectCount;objects.intern(object,objectOrdinal);}
            if(count==Long.MAX_VALUE/FIELDS||characterCount>Long.MAX_VALUE-text.length()||supportCount>Long.MAX_VALUE-producers.size())throw new IllegalStateException("dependency payload exceeds paged row space");
            long candidate=++count,at=(candidate-1)*FIELDS,firstCharacter=characterCount,firstSupport=supportCount;
            for(int i=0;i<text.length();i++)characters.set(characterCount++,text.charAt(i));
            for(var producer:producers){long supportAt=supportCount++*SUPPORT_FIELDS;supports.set(supportAt,producer.operation());supports.set(supportAt+1,producer.origin());}
            rows.set(at,firstCharacter);rows.set(at+1,text.length());rows.set(at+2,firstSupport);rows.set(at+3,producers.size());rows.set(at+4,heads.get(objectOrdinal-1));heads.set(objectOrdinal-1,candidate);
        } catch(RuntimeException|Error failure){failed=true;throw failure;}
    }
    public synchronized void select(long object){open();try{long objectOrdinal=objects.find(object);cursor=objectOrdinal==0?0:heads.get(objectOrdinal-1);clearCurrent();}catch(RuntimeException|Error failure){failed=true;throw failure;}}
    public synchronized boolean advance(){open();try{clearCurrent();if(cursor==0)return false;long at=(cursor-1)*FIELDS;textStart=rows.get(at);textLength=rows.get(at+1);currentSupportStart=rows.get(at+2);currentSupportCount=rows.get(at+3);cursor=rows.get(at+4);return true;}catch(RuntimeException|Error failure){failed=true;throw failure;}}
    public synchronized String rawText(){current();if(textLength>Integer.MAX_VALUE)throw new IllegalStateException("dependency candidate text is not representable");char[] text=new char[(int)textLength];for(int i=0;i<text.length;i++)text[i]=(char)characters.get(textStart+i);return new String(text);}
    public synchronized int producerCount(){current();if(currentSupportCount>Integer.MAX_VALUE)throw new IllegalStateException("dependency support count is not representable");return (int)currentSupportCount;}
    public synchronized long producer(int index){return support(index,0);}
    public synchronized long origin(int index){return support(index,1);}
    private long support(int index,int field){current();if(index<0||index>=currentSupportCount)throw new IndexOutOfBoundsException(index);return supports.get((currentSupportStart+index)*SUPPORT_FIELDS+field);}
    private void current(){open();if(textLength<0)throw new NoSuchElementException("no current definition");}
    private void clearCurrent(){textStart=currentSupportStart=currentSupportCount=0;textLength=-1;}
    public synchronized AirSnapshotBuilder.Lease claim(long bytes){open();try{var lease=resources.reserve(AnalysisResources.Pool.RESIDENT,bytes,PHASE);return lease::close;}catch(RuntimeException|Error failure){failed=true;throw failure;}}
    private void open(){if(closed||failed)throw new IllegalStateException("paged dependency definitions are closed or aborted");}
    private void closeSuppressed(Throwable failure){try{close();}catch(RuntimeException|Error cleanup){if(cleanup!=failure)failure.addSuppressed(cleanup);}}
    @Override public synchronized void close(){if(closed)return;closed=true;Throwable failure=null;for(var owner:new AutoCloseable[]{objects,heads,rows,characters,supports})try{if(owner!=null)owner.close();}catch(Exception|Error cleanup){if(failure==null)failure=cleanup;else if(failure!=cleanup)failure.addSuppressed(cleanup);}objects=null;heads=null;rows=null;characters=null;supports=null;objectCount=count=characterCount=supportCount=cursor=0;clearCurrent();resident.close();if(failure instanceof RuntimeException error)throw error;if(failure instanceof Error error)throw error;if(failure!=null)throw new IllegalStateException(failure);}
}
