package io.github.gustavo2358.analysis.dependencies;

import io.github.gustavo2358.analysis.solver.*;
import java.util.Objects;

/** Exact owner-local UTF-16 text; payload and canonical index both spill. */
final class SourceTextDictionary implements AutoCloseable {
    private final AnalysisResources.Phase phase;
    private static final int WIDTH=2;
    private final AnalysisResources resources;
    private final AnalysisResources.Reservation control;
    private PagedLongArray metadata,words;
    private PagedLongIndex index;
    private CharSequence probe;
    private int probeHash;
    private Projection projections;
    private long issued,nextWord,comparisons,codeUnits;
    private boolean closed,failed;

    SourceTextDictionary(PageStore pages,AnalysisResources resources) {
        this(pages,resources,AnalysisResources.Phase.DOMAIN);
    }
    SourceTextDictionary(PageStore pages,AnalysisResources resources,AnalysisResources.Phase phase) {
        Objects.requireNonNull(pages);this.resources=Objects.requireNonNull(resources);
        this.phase=Objects.requireNonNull(phase);control=resources.reserve(AnalysisResources.Pool.RESIDENT,384,phase);
        try {
            metadata=new PagedLongArray(pages,Long.MAX_VALUE,resources,phase);
            words=new PagedLongArray(pages,Long.MAX_VALUE,resources,phase);
            index=new PagedLongIndex(pages,resources,phase,this::compare);
        }catch(RuntimeException|Error failure){suppress(this,failure);throw failure;}
    }
    /** Borrows a sequence stable for this call only; no reference survives the finally block. */
    synchronized long intern(CharSequence text) {
        open();Objects.requireNonNull(text);probe=text;
        try {
            probeHash=hashText(text);
            long found=index.find(0);if(found!=0)return found;
            long id=Math.incrementExact(issued),base=Math.multiplyExact(id-1,WIDTH);
            long count=(text.length()+3L)/4,end=Math.addExact(nextWord,count);
            if(end>words.length()||base>metadata.length()-WIDTH)throw new IllegalStateException("source text address space exhausted");
            issued=id;metadata.set(base,nextWord);metadata.set(base+1,((long)probeHash<<32)|(text.length()&0xffffffffL));
            for(int start=0;start<text.length();) {
                long word=0;
                for(int part=0;part<4&&start<text.length();part++,start++)word|=(long)text.charAt(start)<<(part*16);
                words.set(nextWord++,word);
            }
            if(index.intern(id,id)!=id)throw new IllegalStateException("source text canonical index disagreement");
            return id;
        }catch(RuntimeException|Error failure){failed=true;throw failure;}
        finally{probe=null;probeHash=0;}
    }
    synchronized long size(){open();return index.size();}
    synchronized long comparisons(){open();return comparisons;}
    synchronized long codeUnitsRead(){open();return codeUnits;}
    private int hashText(CharSequence text){int value=0;for(int at=0;at<text.length();at++){if((at&255)==0)resources.work(1,phase);value=31*value+text.charAt(at);}return value;}
    private int hash(long id){return id==0?probeHash:(int)(metadata.get((id-1)*WIDTH+1)>>>32);}
    private int length(long id){return id==0?probe.length():Math.toIntExact(metadata.get((id-1)*WIDTH+1)&0xffffffffL);}
    private long packed(long id,long start,int at,int count) {
        codeUnits=Math.addExact(codeUnits,count);
        if(id!=0)return words.get(start+(at>>>2));
        long value=0;for(int part=0;part<count;part++)value|=(long)probe.charAt(at+part)<<(part*16);
        return value;
    }
    private int compare(long first,long second) {
        comparisons=Math.incrementExact(comparisons);
        if(first==second)return 0;
        int hashed=Integer.compare(hash(first),hash(second));if(hashed!=0)return hashed;
        int a=length(first),b=length(second),common=Math.min(a,b);
        long startA=first==0?0:metadata.get((first-1)*WIDTH),startB=second==0?0:metadata.get((second-1)*WIDTH);
        for(int at=0;at<common;) {
            int count=Math.min(4,common-at);
            long left=packed(first,startA,at,count),right=packed(second,startB,at,count);
            long mask=count==4?-1L:(1L<<(count*16))-1;
            if((left&mask)!=(right&mask))for(int part=0;part<count;part++) {
                int difference=(char)(left>>>(part*16))-(char)(right>>>(part*16));if(difference!=0)return difference;
            }
            at+=count;
        }
        return Integer.compare(a,b);
    }
    synchronized Projection project(long id) {
        open();if(id<=0||id>issued)throw new IllegalArgumentException("unknown source text identity");
        AnalysisResources.Reservation scratch=null;
        try {
            int length=length(id);
            scratch=resources.reserve(AnalysisResources.Pool.SCRATCH,192+6L*length,phase);
            char[] data=new char[length];long start=metadata.get((id-1)*WIDTH);
            for(int at=0;at<length;){int count=Math.min(4,length-at);long word=packed(id,start,at,count);for(int part=0;part<count;part++)data[at++]=(char)(word>>>(part*16));}
            var result=new Projection(this,new String(data),scratch);scratch=null;
            result.next=projections;if(projections!=null)projections.previous=result;projections=result;
            return result;
        }catch(RuntimeException|Error failure){failed=true;if(scratch!=null)scratch.close();throw failure;}
    }
    synchronized int characterCount(long id) {
        open();if(id<=0||id>issued)throw new IllegalArgumentException("unknown source text identity");
        try{return length(id);}catch(RuntimeException|Error failure){failed=true;throw failure;}
    }
    synchronized void readCharacters(long id,long offset,char[] destination,int at,int count) {
        int length=characterCount(id);Objects.checkFromIndexSize(at,count,destination.length);
        if(offset<0||offset>length||count>length-offset)throw new IndexOutOfBoundsException("source character range");
        try {
            long start=metadata.get((id-1)*WIDTH),cached=-1,word=0;
            for(int i=0;i<count;i++){long unit=offset+i,index=unit>>>2;if(index!=cached){word=words.get(start+index);cached=index;}destination[at+i]=(char)(word>>>((unit&3)*16));}
        }catch(RuntimeException|Error failure){failed=true;throw failure;}
    }
    synchronized boolean matches(long id,String value) {
        int length=characterCount(id);Objects.requireNonNull(value);if(length!=value.length())return false;
        try {
            long start=metadata.get((id-1)*WIDTH);for(int at=0;at<length;){int count=Math.min(4,length-at);long word=packed(id,start,at,count);for(int part=0;part<count;part++,at++)if((char)(word>>>(part*16))!=value.charAt(at))return false;}return true;
        }catch(RuntimeException|Error failure){failed=true;throw failure;}
    }
    static final class Projection implements AutoCloseable {
        private SourceTextDictionary owner;
        private String text;
        private AnalysisResources.Reservation capacity;
        private Projection previous,next;
        private Projection(SourceTextDictionary owner,String text,AnalysisResources.Reservation capacity){this.owner=owner;this.text=text;this.capacity=capacity;}
        String value(){var dictionary=owner;if(dictionary==null)throw new IllegalStateException("source text projection is closed");dictionary.open();return text;}
        @Override public void close(){var dictionary=owner;if(dictionary!=null)dictionary.release(this);}
    }
    private synchronized void release(Projection projection) {
        if(projection.owner!=this)return;
        if(projection.previous==null)projections=projection.next;else projection.previous.next=projection.next;
        if(projection.next!=null)projection.next.previous=projection.previous;
        projection.owner=null;projection.text=null;projection.previous=projection.next=null;
        var owned=projection.capacity;projection.capacity=null;owned.close();
    }
    private synchronized void open(){if(closed||failed)throw new IllegalStateException("source text dictionary is closed or aborted");}
    private static void suppress(AutoCloseable resource,Throwable primary){try{resource.close();}catch(Exception|Error cleanup){if(primary!=cleanup)primary.addSuppressed(cleanup);}}
    @Override public synchronized void close() {
        if(closed)return;closed=true;probe=null;
        Throwable failure=null;
        while(projections!=null)try{release(projections);}catch(RuntimeException|Error cleanup){if(failure==null)failure=cleanup;else if(failure!=cleanup)failure.addSuppressed(cleanup);}
        for(AutoCloseable owned:new AutoCloseable[]{index,metadata,words,control})if(owned!=null)try{owned.close();}
        catch(Exception|Error cleanup){if(failure==null)failure=cleanup;else if(failure!=cleanup)failure.addSuppressed(cleanup);}
        index=null;metadata=words=null;issued=nextWord=0;
        if(failure instanceof RuntimeException exception)throw exception;
        if(failure instanceof Error error)throw error;
        if(failure!=null)throw new IllegalStateException("source text cleanup failed",failure);
    }
}
