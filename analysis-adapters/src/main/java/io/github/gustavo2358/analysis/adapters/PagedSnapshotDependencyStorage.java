package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.air.model.AirSnapshotBuilder;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.analysis.dependencies.SnapshotDependencyAnalysis;
import io.github.gustavo2358.analysis.dependencies.DependencyProgramStore;
import io.github.gustavo2358.analysis.solver.*;
import java.util.NoSuchElementException;
import java.util.Objects;

/** Managed object-to-candidate relation; text and complete producer chains are paged payload. */
public final class PagedSnapshotDependencyStorage implements SnapshotDependencyAnalysis.Storage {
    private static final AnalysisResources.Phase PHASE=AnalysisResources.Phase.DOMAIN;
    private static final int FIELDS=3,SUPPORT_FIELDS=12,CALL_FIELDS=18;
    private final AnalysisResources resources;
    private final AnalysisResources.Reservation resident;
    private PagedLongIndex candidateOrder,supportOrder,supportInsertionOrder,callOrder;
    private PagedLongArray rows,characters,supportRows,supportCharacters,callRows,callCharacters;
    private PagedLongIndex.Cursor candidateCursor,supportCursor,callCursor;
    private long count,characterCount,supportCount,supportCharacterCount,selectedObject,currentCandidate,textStart,textLength;
    private long selectedCandidate,currentSupport;
    private long callRowsCount,callCharacterCount,currentCall;
    private boolean closed,failed;

    public PagedSnapshotDependencyStorage(PageStore pages,AnalysisResources resources) {
        Objects.requireNonNull(pages);this.resources=Objects.requireNonNull(resources);resident=resources.reserve(AnalysisResources.Pool.RESIDENT,256,PHASE);
        try{rows=new PagedLongArray(pages,Long.MAX_VALUE,resources,PHASE);characters=new PagedLongArray(pages,Long.MAX_VALUE,resources,PHASE);candidateOrder=new PagedLongIndex(pages,resources,PHASE,this::compareCandidates);
            supportRows=new PagedLongArray(pages,Long.MAX_VALUE,resources,PHASE);supportCharacters=new PagedLongArray(pages,Long.MAX_VALUE,resources,PHASE);supportOrder=new PagedLongIndex(pages,resources,PHASE,this::compareSupports);supportInsertionOrder=new PagedLongIndex(pages,resources,PHASE,this::compareSupportInsertion);
            callRows=new PagedLongArray(pages,Long.MAX_VALUE,resources,PHASE);callCharacters=new PagedLongArray(pages,Long.MAX_VALUE,resources,PHASE);callOrder=new PagedLongIndex(pages,resources,PHASE,this::compareCalls);clearCurrent();}
        catch(RuntimeException|Error failure){closeSuppressed(failure);throw failure;}
    }

    public synchronized long add(long object,String text) {
        open();Objects.requireNonNull(text);
        try {
            closeCandidateCursor();
            if(count==Long.MAX_VALUE/FIELDS||characterCount>Long.MAX_VALUE-text.length())throw new IllegalStateException("dependency candidate payload exceeds paged row space");
            long candidate=++count+1,at=(candidate-1)*FIELDS,firstCharacter=characterCount;
            for(int i=0;i<text.length();i++)characters.set(characterCount++,text.charAt(i));
            rows.set(at,firstCharacter);rows.set(at+1,text.length());rows.set(at+2,object);return candidateOrder.intern(candidate,candidate);
        } catch(RuntimeException|Error failure){failed=true;throw failure;}
    }
    private int compareCandidates(long first,long second){long a=(first-1)*FIELDS,b=(second-1)*FIELDS;int comparison=Long.compare(rows.get(a+2),rows.get(b+2));return comparison!=0?comparison:compareText(a,b);}
    private int compareText(long a,long b){long an=rows.get(a+1),bn=rows.get(b+1),limit=Math.min(an,bn);for(long i=0;i<limit;i++){int comparison=Long.compare(characters.get(rows.get(a)+i),characters.get(rows.get(b)+i));if(comparison!=0)return comparison;}return Long.compare(an,bn);}
    public synchronized void select(long object){open();try{closeCandidateCursor();long query=1,at=0;rows.set(at,0);rows.set(at+1,0);rows.set(at+2,object);selectedObject=object;candidateCursor=candidateOrder.cursor(query);clearCurrent();}catch(RuntimeException|Error failure){failed=true;throw failure;}}
    public synchronized boolean advance(){open();try{clearCurrent();if(candidateCursor==null)return false;if(!candidateCursor.advance()){closeCandidateCursor();return false;}long candidate=candidateCursor.value(),at=(candidate-1)*FIELDS;if(rows.get(at+2)!=selectedObject){closeCandidateCursor();return false;}currentCandidate=candidate;textStart=rows.get(at);textLength=rows.get(at+1);return true;}catch(RuntimeException|Error failure){failed=true;throw failure;}}
    @Override public synchronized long candidate(){current();return currentCandidate;}
    public synchronized String rawText(){current();if(textLength>Integer.MAX_VALUE)throw new IllegalStateException("dependency candidate text is not representable");char[] text=new char[(int)textLength];for(int i=0;i<text.length;i++)text[i]=(char)characters.get(textStart+i);return new String(text);}

    @Override public synchronized void addSupport(long candidate,OperationId producer,OriginId origin) {
        open();Objects.requireNonNull(producer);Objects.requireNonNull(origin);if(candidate<2||candidate>count+1)throw new IllegalArgumentException("unknown dependency candidate");
        try {
            closeSupportCursor();if(supportCount==Long.MAX_VALUE/SUPPORT_FIELDS)throw new IllegalStateException("dependency support cardinality exceeds paged row space");
            long support=++supportCount+1,at=(support-1)*SUPPORT_FIELDS;supportRows.set(at,candidate);supportRows.set(at+1,1);
            putSupportText(at+2,producer.localId());putSupportText(at+4,producer.unit().publication().localId());putSupportText(at+6,producer.unit().localId());
            putSupportText(at+8,origin.publication().localId());putSupportText(at+10,origin.localId());if(supportOrder.intern(support,support)==support)supportInsertionOrder.intern(support,support);
        } catch(RuntimeException|Error failure){failed=true;throw failure;}
    }
    private void putSupportText(long field,String text){if(supportCharacterCount>Long.MAX_VALUE-text.length())throw new IllegalStateException("dependency support text exceeds paged row space");long start=supportCharacterCount;for(int i=0;i<text.length();i++)supportCharacters.set(supportCharacterCount++,text.charAt(i));supportRows.set(field,start);supportRows.set(field+1,text.length());}
    private int compareSupports(long first,long second){long a=(first-1)*SUPPORT_FIELDS,b=(second-1)*SUPPORT_FIELDS;int comparison=Long.compare(supportRows.get(a),supportRows.get(b));if(comparison!=0)return comparison;comparison=Long.compare(supportRows.get(a+1),supportRows.get(b+1));if(comparison!=0)return comparison;for(int field:new int[]{2,4,6,8,10}){comparison=compareSupportText(a,b,field);if(comparison!=0)return comparison;}return 0;}
    private int compareSupportInsertion(long first,long second){long a=(first-1)*SUPPORT_FIELDS,b=(second-1)*SUPPORT_FIELDS;int comparison=Long.compare(supportRows.get(a),supportRows.get(b));return comparison!=0?comparison:Long.compare(first,second);}
    private int compareSupportText(long a,long b,int field){long an=supportRows.get(a+field+1),bn=supportRows.get(b+field+1),limit=Math.min(an,bn);for(long i=0;i<limit;i++){int comparison=Long.compare(supportCharacters.get(supportRows.get(a+field)+i),supportCharacters.get(supportRows.get(b+field)+i));if(comparison!=0)return comparison;}return Long.compare(an,bn);}
    @Override public synchronized void selectSupports(long candidate){selectSupports(candidate,false);}
    @Override public synchronized void selectOrderedSupports(long candidate){selectSupports(candidate,true);}
    private void selectSupports(long candidate,boolean ordered){open();try{closeSupportCursor();long query=1,at=0;supportRows.set(at,candidate);supportRows.set(at+1,0);for(int field=2;field<SUPPORT_FIELDS;field++)supportRows.set(at+field,0);selectedCandidate=candidate;supportCursor=(ordered?supportOrder:supportInsertionOrder).cursor(query);}catch(RuntimeException|Error failure){failed=true;throw failure;}}
    @Override public synchronized boolean advanceSupport(){open();if(supportCursor==null)throw new IllegalStateException("dependency support cursor is not selected");try{if(!supportCursor.advance()){closeSupportCursor();return false;}long support=supportCursor.value(),at=(support-1)*SUPPORT_FIELDS;if(supportRows.get(at)!=selectedCandidate){closeSupportCursor();return false;}currentSupport=support;return true;}catch(RuntimeException|Error failure){failed=true;throw failure;}}
    @Override public synchronized OperationId supportProducer(){currentSupport();long at=(currentSupport-1)*SUPPORT_FIELDS;var publication=new PublicationId(supportText(at,4));return new OperationId(new UnitId(publication,supportText(at,6)),supportText(at,2));}
    @Override public synchronized OriginId supportOrigin(){currentSupport();long at=(currentSupport-1)*SUPPORT_FIELDS;return new OriginId(new PublicationId(supportText(at,8)),supportText(at,10));}
    private String supportText(long at,int field){long start=supportRows.get(at+field),length=supportRows.get(at+field+1);if(length>Integer.MAX_VALUE)throw new IllegalStateException("dependency support text is not representable");char[] text=new char[(int)length];for(int i=0;i<text.length;i++)text[i]=(char)supportCharacters.get(start+i);return new String(text);}
    private void currentSupport(){open();if(currentSupport==0)throw new NoSuchElementException("no current dependency support");}
    private void closeSupportCursor(){if(supportCursor!=null)supportCursor.close();supportCursor=null;selectedCandidate=currentSupport=0;}

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
    private void current(){open();if(textLength<0)throw new NoSuchElementException("no current definition");}
    private void clearCurrent(){currentCandidate=textStart=0;textLength=-1;}
    public synchronized AirSnapshotBuilder.Lease claim(long bytes){open();try{var lease=resources.reserve(AnalysisResources.Pool.RESIDENT,bytes,PHASE);return lease::close;}catch(RuntimeException|Error failure){failed=true;throw failure;}}
    private void open(){if(closed||failed)throw new IllegalStateException("paged dependency definitions are closed or aborted");}
    private void closeSuppressed(Throwable failure){try{close();}catch(RuntimeException|Error cleanup){if(cleanup!=failure)failure.addSuppressed(cleanup);}}
    @Override public synchronized void close(){if(closed)return;closed=true;Throwable failure=null;try{closeCallCursor();closeSupportCursor();closeCandidateCursor();}catch(RuntimeException|Error cleanup){failure=cleanup;}for(var owner:new AutoCloseable[]{candidateOrder,supportOrder,supportInsertionOrder,rows,characters,supportRows,supportCharacters,callOrder,callRows,callCharacters})try{if(owner!=null)owner.close();}catch(Exception|Error cleanup){if(failure==null)failure=cleanup;else if(failure!=cleanup)failure.addSuppressed(cleanup);}candidateOrder=supportOrder=supportInsertionOrder=callOrder=null;rows=characters=supportRows=supportCharacters=callRows=callCharacters=null;count=characterCount=supportCount=supportCharacterCount=selectedObject=selectedCandidate=currentSupport=callRowsCount=callCharacterCount=currentCall=0;clearCurrent();resident.close();if(failure instanceof RuntimeException error)throw error;if(failure instanceof Error error)throw error;if(failure!=null)throw new IllegalStateException(failure);}
}
