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
    private static final int FIELDS=3,SUPPORT_FIELDS=12,CALL_FIELDS=19;
    private final PageStore pages;
    private final AnalysisResources resources;
    private final AnalysisResources.Reservation resident;
    private PagedLongIndex candidateOrder,supportOrder,supportInsertionOrder,callOrder;
    private PagedLongArray rows,characters,supportRows,supportCharacters,callRows,callCharacters;
    private PagedHandleOrder artifactOrder,originOrder;
    private PagedOwnedHandleOrder originInputOrder;
    private PagedLongIndex.Cursor candidateCursor,supportCursor,callCursor;
    private long count,characterCount,supportCount,supportCharacterCount,selectedObject,currentCandidate,textStart,textLength;
    private long selectedCandidate,currentSupport;
    private long callRowsCount,callCharacterCount,currentCall,candidateCount,unknownRemainderCount;
    private boolean closed,failed;

    public PagedSnapshotDependencyStorage(PageStore pages,AnalysisResources resources) {
        this.pages=Objects.requireNonNull(pages);this.resources=Objects.requireNonNull(resources);resident=resources.reserve(AnalysisResources.Pool.RESIDENT,256,PHASE);
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
            long candidates=candidateCount(call.objectKey());
            long row=++callRowsCount,at=(row-1)*CALL_FIELDS;callRows.set(at,call.objectKey());callRows.set(at+1,call.coverage().ordinal());
            putCallText(at+2,call.caller().localId());putCallText(at+4,call.entry().localId());putCallText(at+6,call.sequence().localId());
            putCallText(at+8,call.operation().localId());putCallText(at+10,call.siteOrigin().localId());putCallText(at+12,call.targetOrigin().localId());
            putCallText(at+14,call.namespace());putCallText(at+16,call.subject().localId());callRows.set(at+18,candidates);callOrder.intern(row,row);
            candidateCount=Math.addExact(candidateCount,candidates);
            if(call.coverage()!=io.github.gustavo2358.air.model.Evidence.CoverageStatus.MODELED||candidates==0)unknownRemainderCount=Math.addExact(unknownRemainderCount,1);
        } catch(RuntimeException|Error failure){failed=true;throw failure;}
    }
    private long candidateCount(long object) {
        closeCandidateCursor();long query=1,at=0;rows.set(at,0);rows.set(at+1,0);rows.set(at+2,object);long result=0;
        try(var cursor=candidateOrder.cursor(query)){while(cursor.advance()){long candidate=cursor.value();if(rows.get((candidate-1)*FIELDS+2)!=object)break;result=Math.addExact(result,1);}}
        return result;
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
    @Override public synchronized long callCandidateCount(){return callField(18);}
    @Override public synchronized long callCount(){open();return callRowsCount;}
    @Override public synchronized long candidateCount(){open();return candidateCount;}
    @Override public synchronized long unknownRemainderCount(){open();return unknownRemainderCount;}
    @Override public synchronized void addArtifact(long handle,String localId){open();try{if(artifactOrder==null)artifactOrder=new PagedHandleOrder(pages);artifactOrder.add(handle,localId);}catch(RuntimeException|Error failure){failed=true;throw failure;}}
    @Override public synchronized void selectArtifacts(){open();if(artifactOrder!=null)artifactOrder.select();}
    @Override public synchronized boolean advanceArtifact(){open();return artifactOrder!=null&&artifactOrder.advance();}
    @Override public synchronized long artifactHandle(){open();if(artifactOrder==null)throw new NoSuchElementException("no current dependency artifact");return artifactOrder.handle();}
    @Override public synchronized void addOrigin(long handle,String localId){open();try{if(originOrder==null)originOrder=new PagedHandleOrder(pages);originOrder.add(handle,localId);}catch(RuntimeException|Error failure){failed=true;throw failure;}}
    @Override public synchronized void selectOrigins(){open();if(originOrder!=null)originOrder.select();}
    @Override public synchronized boolean advanceOrigin(){open();return originOrder!=null&&originOrder.advance();}
    @Override public synchronized long originHandle(){open();if(originOrder==null)throw new NoSuchElementException("no current dependency origin");return originOrder.handle();}
    @Override public synchronized void addOriginInput(long originHandle,long inputHandle,String localId){open();try{if(originInputOrder==null)originInputOrder=new PagedOwnedHandleOrder(pages);originInputOrder.add(originHandle,inputHandle,localId);}catch(RuntimeException|Error failure){failed=true;throw failure;}}
    @Override public synchronized void selectOriginInputs(long originHandle){open();if(originInputOrder!=null)originInputOrder.select(originHandle);}
    @Override public synchronized boolean advanceOriginInput(){open();return originInputOrder!=null&&originInputOrder.advance();}
    @Override public synchronized long originInputHandle(){open();if(originInputOrder==null)throw new NoSuchElementException("no current dependency origin input");return originInputOrder.handle();}
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
    @Override public synchronized void close(){if(closed)return;closed=true;Throwable failure=null;try{closeCallCursor();closeSupportCursor();closeCandidateCursor();}catch(RuntimeException|Error cleanup){failure=cleanup;}for(var owner:new AutoCloseable[]{candidateOrder,supportOrder,supportInsertionOrder,rows,characters,supportRows,supportCharacters,callOrder,callRows,callCharacters,artifactOrder,originOrder,originInputOrder})try{if(owner!=null)owner.close();}catch(Exception|Error cleanup){if(failure==null)failure=cleanup;else if(failure!=cleanup)failure.addSuppressed(cleanup);}candidateOrder=supportOrder=supportInsertionOrder=callOrder=null;rows=characters=supportRows=supportCharacters=callRows=callCharacters=null;artifactOrder=originOrder=null;originInputOrder=null;count=characterCount=supportCount=supportCharacterCount=selectedObject=selectedCandidate=currentSupport=callRowsCount=callCharacterCount=currentCall=candidateCount=unknownRemainderCount=0;clearCurrent();resident.close();if(failure instanceof RuntimeException error)throw error;if(failure instanceof Error error)throw error;if(failure!=null)throw new IllegalStateException(failure);}

    private final class PagedHandleOrder implements AutoCloseable {
        private static final int HANDLE_FIELDS=3;
        private PagedLongArray handleRows,handleCharacters;private PagedLongIndex order;private PagedLongIndex.Cursor cursor;
        private long rowCount,characterCount,current;
        private PagedHandleOrder(PageStore pages){handleRows=new PagedLongArray(pages,Long.MAX_VALUE,resources,PHASE);handleCharacters=new PagedLongArray(pages,Long.MAX_VALUE,resources,PHASE);order=new PagedLongIndex(pages,resources,PHASE,this::compare);}
        private void add(long handle,String localId){Objects.requireNonNull(localId);if(handle<=0)throw new IllegalArgumentException("metadata handle must be positive");closeCursor();if(rowCount==Long.MAX_VALUE/HANDLE_FIELDS||characterCount>Long.MAX_VALUE-localId.length())throw new IllegalStateException("dependency metadata exceeds paged row space");long row=++rowCount,at=(row-1)*HANDLE_FIELDS,start=characterCount;handleRows.set(at,handle);for(int i=0;i<localId.length();i++)handleCharacters.set(characterCount++,localId.charAt(i));handleRows.set(at+1,start);handleRows.set(at+2,localId.length());order.intern(row,row);}
        private int compare(long first,long second){long a=(first-1)*HANDLE_FIELDS,b=(second-1)*HANDLE_FIELDS,an=handleRows.get(a+2),bn=handleRows.get(b+2),limit=Math.min(an,bn);for(long i=0;i<limit;i++){int comparison=Long.compare(handleCharacters.get(handleRows.get(a+1)+i),handleCharacters.get(handleRows.get(b+1)+i));if(comparison!=0)return comparison;}int comparison=Long.compare(an,bn);return comparison!=0?comparison:Long.compare(handleRows.get(a),handleRows.get(b));}
        private void select(){closeCursor();cursor=order.cursor();current=0;}
        private boolean advance(){if(cursor==null)throw new IllegalStateException("dependency metadata cursor is not selected");if(!cursor.advance()){closeCursor();return false;}current=cursor.value();return true;}
        private long handle(){if(current==0)throw new NoSuchElementException("no current dependency metadata");return handleRows.get((current-1)*HANDLE_FIELDS);}
        private void closeCursor(){if(cursor!=null)cursor.close();cursor=null;current=0;}
        @Override public void close(){closeCursor();Throwable failure=null;for(var owner:new AutoCloseable[]{order,handleRows,handleCharacters})try{if(owner!=null)owner.close();}catch(Exception|Error cleanup){if(failure==null)failure=cleanup;else if(failure!=cleanup)failure.addSuppressed(cleanup);}order=null;handleRows=handleCharacters=null;if(failure instanceof RuntimeException runtime)throw runtime;if(failure instanceof Error error)throw error;if(failure!=null)throw new IllegalStateException(failure);}
    }

    private final class PagedOwnedHandleOrder implements AutoCloseable {
        private static final int HANDLE_FIELDS=4;
        private PagedLongArray handleRows,handleCharacters;private PagedLongIndex order;private PagedLongIndex.Cursor cursor;
        private long rowCount,characterCount,selectedOwner,current;
        private PagedOwnedHandleOrder(PageStore pages){handleRows=new PagedLongArray(pages,Long.MAX_VALUE,resources,PHASE);handleCharacters=new PagedLongArray(pages,Long.MAX_VALUE,resources,PHASE);order=new PagedLongIndex(pages,resources,PHASE,this::compare);}
        private void add(long owner,long handle,String localId){Objects.requireNonNull(localId);if(owner<=0||handle<=0)throw new IllegalArgumentException("metadata owner and handle must be positive");closeCursor();if(rowCount==Long.MAX_VALUE/HANDLE_FIELDS-1||characterCount>Long.MAX_VALUE-localId.length())throw new IllegalStateException("dependency nested metadata exceeds paged row space");long row=++rowCount+1,at=(row-1)*HANDLE_FIELDS,start=characterCount;handleRows.set(at,owner);handleRows.set(at+1,handle);for(int i=0;i<localId.length();i++)handleCharacters.set(characterCount++,localId.charAt(i));handleRows.set(at+2,start);handleRows.set(at+3,localId.length());order.intern(row,row);}
        private int compare(long first,long second){long a=(first-1)*HANDLE_FIELDS,b=(second-1)*HANDLE_FIELDS;int comparison=Long.compare(handleRows.get(a),handleRows.get(b));if(comparison!=0)return comparison;long an=handleRows.get(a+3),bn=handleRows.get(b+3),limit=Math.min(an,bn);for(long i=0;i<limit;i++){comparison=Long.compare(handleCharacters.get(handleRows.get(a+2)+i),handleCharacters.get(handleRows.get(b+2)+i));if(comparison!=0)return comparison;}comparison=Long.compare(an,bn);return comparison!=0?comparison:Long.compare(handleRows.get(a+1),handleRows.get(b+1));}
        private void select(long owner){if(owner<=0)throw new IllegalArgumentException("metadata owner must be positive");closeCursor();long at=0;handleRows.set(at,owner);handleRows.set(at+1,0);handleRows.set(at+2,0);handleRows.set(at+3,0);selectedOwner=owner;cursor=order.cursor(1);}
        private boolean advance(){if(cursor==null)throw new IllegalStateException("dependency nested metadata cursor is not selected");if(!cursor.advance()){closeCursor();return false;}long row=cursor.value();if(handleRows.get((row-1)*HANDLE_FIELDS)!=selectedOwner){closeCursor();return false;}current=row;return true;}
        private long handle(){if(current==0)throw new NoSuchElementException("no current dependency nested metadata");return handleRows.get((current-1)*HANDLE_FIELDS+1);}
        private void closeCursor(){if(cursor!=null)cursor.close();cursor=null;selectedOwner=current=0;}
        @Override public void close(){closeCursor();Throwable failure=null;for(var owner:new AutoCloseable[]{order,handleRows,handleCharacters})try{if(owner!=null)owner.close();}catch(Exception|Error cleanup){if(failure==null)failure=cleanup;else if(failure!=cleanup)failure.addSuppressed(cleanup);}order=null;handleRows=handleCharacters=null;if(failure instanceof RuntimeException runtime)throw runtime;if(failure instanceof Error error)throw error;if(failure!=null)throw new IllegalStateException(failure);}
    }
}
