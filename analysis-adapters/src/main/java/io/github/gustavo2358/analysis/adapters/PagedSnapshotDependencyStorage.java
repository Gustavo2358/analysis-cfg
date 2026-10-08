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
    private static final int FIELDS=5,SUPPORT_FIELDS=2,CALL_FIELDS=18;
    private final AnalysisResources resources;
    private final AnalysisResources.Reservation resident;
    private PagedLongIndex candidateOrder,callOrder;
    private PagedLongArray rows,characters,supports,callRows,callCharacters;
    private PagedLongIndex.Cursor candidateCursor,callCursor;
    private long count,characterCount,supportCount,selectedObject,textStart,textLength,currentSupportStart,currentSupportCount;
    private long callRowsCount,callCharacterCount,currentCall;
    private boolean closed,failed;

    public PagedSnapshotDependencyStorage(PageStore pages,AnalysisResources resources) {
        Objects.requireNonNull(pages);this.resources=Objects.requireNonNull(resources);resident=resources.reserve(AnalysisResources.Pool.RESIDENT,256,PHASE);
        try{rows=new PagedLongArray(pages,Long.MAX_VALUE,resources,PHASE);characters=new PagedLongArray(pages,Long.MAX_VALUE,resources,PHASE);supports=new PagedLongArray(pages,Long.MAX_VALUE,resources,PHASE);candidateOrder=new PagedLongIndex(pages,resources,PHASE,this::compareCandidates);
            callRows=new PagedLongArray(pages,Long.MAX_VALUE,resources,PHASE);callCharacters=new PagedLongArray(pages,Long.MAX_VALUE,resources,PHASE);callOrder=new PagedLongIndex(pages,resources,PHASE,this::compareCalls);clearCurrent();}
        catch(RuntimeException|Error failure){closeSuppressed(failure);throw failure;}
    }

    public synchronized void add(long object,String text,List<DependencyProgramStore.Producer> producers) {
        open();Objects.requireNonNull(text);Objects.requireNonNull(producers);if(producers.isEmpty())throw new IllegalArgumentException("candidate requires a producer");
        try {
            closeCandidateCursor();
            if(count==Long.MAX_VALUE/FIELDS||characterCount>Long.MAX_VALUE-text.length()||supportCount>Long.MAX_VALUE-producers.size())throw new IllegalStateException("dependency payload exceeds paged row space");
            long candidate=++count+1,at=(candidate-1)*FIELDS,firstCharacter=characterCount,firstSupport=supportCount;
            for(int i=0;i<text.length();i++)characters.set(characterCount++,text.charAt(i));
            for(var producer:producers){long supportAt=supportCount++*SUPPORT_FIELDS;supports.set(supportAt,producer.operation());supports.set(supportAt+1,producer.origin());}
            rows.set(at,firstCharacter);rows.set(at+1,text.length());rows.set(at+2,firstSupport);rows.set(at+3,producers.size());rows.set(at+4,object);candidateOrder.intern(candidate,candidate);
        } catch(RuntimeException|Error failure){failed=true;throw failure;}
    }
    private int compareCandidates(long first,long second){long a=(first-1)*FIELDS,b=(second-1)*FIELDS;int comparison=Long.compare(rows.get(a+4),rows.get(b+4));if(comparison!=0)return comparison;comparison=compareText(a,b);return comparison!=0?comparison:Long.compare(first,second);}
    private int compareText(long a,long b){long an=rows.get(a+1),bn=rows.get(b+1),limit=Math.min(an,bn);for(long i=0;i<limit;i++){int comparison=Long.compare(characters.get(rows.get(a)+i),characters.get(rows.get(b)+i));if(comparison!=0)return comparison;}return Long.compare(an,bn);}
    public synchronized void select(long object){open();try{closeCandidateCursor();long query=1,at=0;rows.set(at,0);rows.set(at+1,0);rows.set(at+2,0);rows.set(at+3,0);rows.set(at+4,object);selectedObject=object;candidateCursor=candidateOrder.cursor(query);clearCurrent();}catch(RuntimeException|Error failure){failed=true;throw failure;}}
    public synchronized boolean advance(){open();try{clearCurrent();if(candidateCursor==null)return false;if(!candidateCursor.advance()){closeCandidateCursor();return false;}long candidate=candidateCursor.value(),at=(candidate-1)*FIELDS;if(rows.get(at+4)!=selectedObject){closeCandidateCursor();return false;}textStart=rows.get(at);textLength=rows.get(at+1);currentSupportStart=rows.get(at+2);currentSupportCount=rows.get(at+3);return true;}catch(RuntimeException|Error failure){failed=true;throw failure;}}
    public synchronized String rawText(){current();if(textLength>Integer.MAX_VALUE)throw new IllegalStateException("dependency candidate text is not representable");char[] text=new char[(int)textLength];for(int i=0;i<text.length;i++)text[i]=(char)characters.get(textStart+i);return new String(text);}
    public synchronized int producerCount(){current();if(currentSupportCount>Integer.MAX_VALUE)throw new IllegalStateException("dependency support count is not representable");return (int)currentSupportCount;}
    public synchronized long producer(int index){return support(index,0);}
    public synchronized long origin(int index){return support(index,1);}

    @Override public synchronized void addCall(DependencyProgramStore.ComputedCall call) {
        open();Objects.requireNonNull(call);
        try {
            if(callRowsCount==Long.MAX_VALUE/CALL_FIELDS)throw new IllegalStateException("dependency call cardinality exceeds paged row space");
            long row=++callRowsCount,at=(row-1)*CALL_FIELDS;callRows.set(at,call.objectKey());callRows.set(at+1,call.coverage().ordinal());
            putCallText(at+2,call.caller().localId());putCallText(at+4,call.entry().localId());putCallText(at+6,call.sequence().localId());
            putCallText(at+8,call.operation().localId());putCallText(at+10,call.siteOrigin().localId());putCallText(at+12,call.targetOrigin().localId());
            putCallText(at+14,call.namespace());putCallText(at+16,call.subject().localId());callOrder.intern(row,row);
        } catch(RuntimeException|Error failure){failed=true;throw failure;}
    }
    private void putCallText(long field,String text) {
        Objects.requireNonNull(text);if(callCharacterCount>Long.MAX_VALUE-text.length())throw new IllegalStateException("dependency call text exceeds paged row space");
        long start=callCharacterCount;for(int i=0;i<text.length();i++)callCharacters.set(callCharacterCount++,text.charAt(i));
        callRows.set(field,start);callRows.set(field+1,text.length());
    }
    private int compareCalls(long first,long second) {
        for(int field:new int[]{2,4,8,6,16,10,12,14}){int comparison=compareCallText(first,second,field);if(comparison!=0)return comparison;}
        return Long.compare(first,second);
    }
    private int compareCallText(long first,long second,int field) {
        long a=(first-1)*CALL_FIELDS,b=(second-1)*CALL_FIELDS,an=callRows.get(a+field+1),bn=callRows.get(b+field+1),limit=Math.min(an,bn);
        for(long i=0;i<limit;i++){int comparison=Long.compare(callCharacters.get(callRows.get(a+field)+i),callCharacters.get(callRows.get(b+field)+i));if(comparison!=0)return comparison;}
        return Long.compare(an,bn);
    }
    @Override public synchronized void selectCalls(){open();closeCallCursor();callCursor=callOrder.cursor();currentCall=0;}
    @Override public synchronized boolean advanceCall(){open();if(callCursor==null)throw new IllegalStateException("dependency call cursor is not selected");
        try{if(!callCursor.advance()){closeCallCursor();return false;}currentCall=callCursor.value();return true;}catch(RuntimeException|Error failure){failed=true;throw failure;}}
    @Override public synchronized long callObjectKey(){return callField(0);}
    @Override public synchronized String callCaller(){return callText(2);}
    @Override public synchronized String callEntry(){return callText(4);}
    @Override public synchronized String callSequence(){return callText(6);}
    @Override public synchronized String callOperation(){return callText(8);}
    @Override public synchronized String callSiteOrigin(){return callText(10);}
    @Override public synchronized String callTargetOrigin(){return callText(12);}
    @Override public synchronized io.github.gustavo2358.air.model.Evidence.CoverageStatus callCoverage(){return io.github.gustavo2358.air.model.Evidence.CoverageStatus.values()[(int)callField(1)];}
    @Override public synchronized String callNamespace(){return callText(14);}
    @Override public synchronized String callSubject(){return callText(16);}
    @Override public synchronized long callCount(){open();return callRowsCount;}
    private long callField(int field){currentCall();return callRows.get((currentCall-1)*CALL_FIELDS+field);}
    private String callText(int field){currentCall();long at=(currentCall-1)*CALL_FIELDS,start=callRows.get(at+field),length=callRows.get(at+field+1);if(length>Integer.MAX_VALUE)throw new IllegalStateException("dependency call text is not representable");char[] text=new char[(int)length];for(int i=0;i<text.length;i++)text[i]=(char)callCharacters.get(start+i);return new String(text);}
    private void currentCall(){open();if(currentCall==0)throw new NoSuchElementException("no current dependency call");}
    private void closeCallCursor(){if(callCursor!=null)callCursor.close();callCursor=null;currentCall=0;}
    private void closeCandidateCursor(){if(candidateCursor!=null)candidateCursor.close();candidateCursor=null;selectedObject=0;}
    private long support(int index,int field){current();if(index<0||index>=currentSupportCount)throw new IndexOutOfBoundsException(index);return supports.get((currentSupportStart+index)*SUPPORT_FIELDS+field);}
    private void current(){open();if(textLength<0)throw new NoSuchElementException("no current definition");}
    private void clearCurrent(){textStart=currentSupportStart=currentSupportCount=0;textLength=-1;}
    public synchronized AirSnapshotBuilder.Lease claim(long bytes){open();try{var lease=resources.reserve(AnalysisResources.Pool.RESIDENT,bytes,PHASE);return lease::close;}catch(RuntimeException|Error failure){failed=true;throw failure;}}
    private void open(){if(closed||failed)throw new IllegalStateException("paged dependency definitions are closed or aborted");}
    private void closeSuppressed(Throwable failure){try{close();}catch(RuntimeException|Error cleanup){if(cleanup!=failure)failure.addSuppressed(cleanup);}}
    @Override public synchronized void close(){if(closed)return;closed=true;Throwable failure=null;try{closeCallCursor();closeCandidateCursor();}catch(RuntimeException|Error cleanup){failure=cleanup;}for(var owner:new AutoCloseable[]{candidateOrder,rows,characters,supports,callOrder,callRows,callCharacters})try{if(owner!=null)owner.close();}catch(Exception|Error cleanup){if(failure==null)failure=cleanup;else if(failure!=cleanup)failure.addSuppressed(cleanup);}candidateOrder=callOrder=null;rows=characters=supports=callRows=callCharacters=null;count=characterCount=supportCount=selectedObject=callRowsCount=callCharacterCount=currentCall=0;clearCurrent();resident.close();if(failure instanceof RuntimeException error)throw error;if(failure instanceof Error error)throw error;if(failure!=null)throw new IllegalStateException(failure);}
}
