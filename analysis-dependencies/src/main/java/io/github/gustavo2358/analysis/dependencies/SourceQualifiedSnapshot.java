package io.github.gustavo2358.analysis.dependencies;

import io.github.gustavo2358.analysis.dependencies.source.*;
import io.github.gustavo2358.analysis.solver.*;
import java.util.Objects;
import static io.github.gustavo2358.analysis.dependencies.SourceQualifiedDependencyResult.Status;

/**
 * Complete source-only candidate facts, borrowing successful immutable SOURCE admission.
 * Current admitted occurrences/uses carry at most one literal/name each; there is
 * no derived sorting/deduplication across distinct input occurrences. Inventory
 * order, duplicate raw values in different uses, qualification and remainder
 * remain independent. Full variable result rows are paged, never resident Lists.
 */
public final class SourceQualifiedSnapshot extends SourceAdmissionSnapshot.Dependent {
    public enum Kind { OCCURRENCE,NATIVE_FILE }
    private static final AnalysisResources.Phase PHASE=AnalysisResources.Phase.INDEX;
    private static final int WIDTH=6;
    private static final Status[] STATUSES=Status.values();
    private volatile SourceAdmissionSnapshot admission;
    private volatile SourceSnapshot source;
    private volatile SourceControlSnapshot controlFacts;
    private volatile PagedLongArray units,items,candidates;
    private AnalysisResources.Reservation control;
    private char[] characters;
    private long itemCount,candidateCount;
    private volatile boolean closed,ready;
    private SourceQualifiedSnapshot(SourceAdmissionSnapshot admission,AnalysisResources.Reservation capacity){this.admission=admission;control=capacity;}
    public static SourceQualifiedSnapshot prepare(SourceAdmissionSnapshot admission,PageStore pages,AnalysisResources resources) {
        Objects.requireNonNull(admission);Objects.requireNonNull(pages);Objects.requireNonNull(resources);admission.requireContext(pages,resources);
        var capacity=resources.reserve(AnalysisResources.Pool.RESIDENT,2048,PHASE);SourceQualifiedSnapshot result=null;
        try {
            result=new SourceQualifiedSnapshot(admission,capacity);var registration=capacity;capacity=null;registration.onOwnerClose(result::close);
            result.initialize(pages,resources);admission.attach(result,pages,resources);result.open();return result;
        }catch(RuntimeException|Error failure){if(result!=null)result.abort(failure);else if(capacity!=null)try{capacity.close();}catch(RuntimeException|Error cleanup){suppress(failure,cleanup);}throw failure;}
    }
    private SourceAdmissionSnapshot parent(){var value=admission;if(value==null)throw new IllegalStateException("SOURCE candidates expired");return value;}
    private SourceSnapshot view(){var value=source;if(value==null)throw new IllegalStateException("SOURCE candidates expired");return value;}
    private PagedLongArray unitRows(){var value=units;if(value==null)throw new IllegalStateException("SOURCE candidates expired");return value;}
    private PagedLongArray itemRows(){var value=items;if(value==null)throw new IllegalStateException("SOURCE candidates expired");return value;}
    private PagedLongArray candidateRows(){var value=candidates;if(value==null)throw new IllegalStateException("SOURCE candidates expired");return value;}
    private SourceControlSnapshot facts(){var value=controlFacts;if(value==null)throw new IllegalStateException("SOURCE candidates expired");return value;}
    private char[] block(){var value=characters;if(value==null)throw new IllegalStateException("SOURCE candidates expired");return value;}
    private long field(long row,SourceShape shape,int column){return view().field(row,shape,column);}
    private long size(long list,SourceShape shape){return list==0?0:view().size(list,shape);}
    private long element(long list,SourceShape shape,long ordinal){return view().element(list,shape,ordinal);}
    private boolean matches(long text,String value){return view().matches(text,value);}
    private void initialize(PageStore pages,AnalysisResources resources) {
        if(closed)throw new IllegalStateException("SOURCE candidates expired before initialization");
        source=parent().snapshot();characters=new char[256];
        units=new PagedLongArray(pages,Long.MAX_VALUE,resources,PHASE);items=new PagedLongArray(pages,Long.MAX_VALUE,resources,PHASE);candidates=new PagedLongArray(pages,Long.MAX_VALUE,resources,PHASE);
        controlFacts=SourceControlSnapshot.prepare(parent(),pages,resources);
        for(long u=0,count=parent().unitCount();u<count;u++) {
            long unit=parent().unit(u);boolean available=view().bool(field(unit,SourceShape.UNIT_EVIDENCE,1));
            for(var kind:Kind.values()) {
                long list=field(unit,SourceShape.UNIT_EVIDENCE,kind==Kind.OCCURRENCE?3:13);var shape=kind==Kind.OCCURRENCE?SourceShape.OCCURRENCE:SourceShape.NATIVE_FILE_USE;
                long n=size(list,shape),at=Math.addExact(Math.multiplyExact(u,4),kind.ordinal()*2);unitRows().set(at,itemCount);unitRows().set(at+1,n);
                for(long i=0;i<n;i++)prepareItem(u,element(list,shape,i),kind,available);
            }
        }
        parent().snapshot();if(closed)throw new IllegalStateException("SOURCE candidates expired before completion");ready=true;
    }
    private Status status(long u,long qualifications,boolean available) {
        if(!available)return Status.CONTROL_UNAVAILABLE;
        if(size(qualifications,SourceShape.TEXT)==0)return Status.NOT_QUALIFIED_IN_SOURCE_MODEL;
        for(long i=0,n=size(qualifications,SourceShape.TEXT);i<n;i++)if(facts().affectedReference(u,element(qualifications,SourceShape.TEXT,i)))return Status.POSSIBLE_UNDER_UNKNOWN_CONTROL;
        return Status.QUALIFIED_POSSIBLE;
    }
    private void add(long raw,long length){long at=Math.multiplyExact(candidateCount,2);candidateRows().set(at,raw);candidateRows().set(at+1,length);candidateCount=Math.incrementExact(candidateCount);}
    /** Supported bounded prefix, retaining arbitrarily long CICS literal padding in raw SOURCE. */
    private long interpreted(long raw,boolean cics,boolean file) {
        long length=view().characterCount(raw);if(!cics)return length>0&&length<=8&&validPrefix(raw,length,false)?length:0;
        if(file&&length>8)return 0;
        long end=0;boolean padding=false;var characters=block();
        for(long at=0;at<length;) {
            int count=(int)Math.min(characters.length,length-at);view().readCharacters(raw,at,characters,0,count);
            for(int i=0;i<count;i++) {
                char c=characters[i];if(c==' '){padding=true;continue;}
                if(padding||end==8||!allowed(c,true,end==0))return 0;end++;
            }at+=count;
        }return end;
    }
    private boolean validPrefix(long raw,long length,boolean cics){var chars=block();view().readCharacters(raw,0,chars,0,Math.toIntExact(length));for(int i=0;i<length;i++)if(!allowed(chars[i],cics,i==0))return false;return true;}
    private static boolean allowed(char c,boolean cics,boolean first) {return c>='A'&&c<='Z'||c=='$'||c=='_'&&!cics||(!first||cics)&&(c>='0'&&c<='9'||c=='@'||c=='#');}
    private void prepareItem(long u,long reference,Kind kind,boolean available) {
        boolean nativeFile=kind==Kind.NATIVE_FILE;var shape=nativeFile?SourceShape.NATIVE_FILE_USE:SourceShape.OCCURRENCE;
        long qualifications=field(reference,shape,nativeFile?7:9),start=candidateCount;var status=status(u,qualifications,available);
        boolean open=false,valueOpen=!nativeFile&&view().bool(field(reference,shape,8));
        if(nativeFile) {
            long names=field(reference,shape,5);open=!view().bool(field(reference,shape,4))&&size(names,SourceShape.NATIVE_FILE_NAME)==0||size(field(reference,shape,8),SourceShape.TEXT)!=0||status==Status.POSSIBLE_UNDER_UNKNOWN_CONTROL;
            if(size(qualifications,SourceShape.TEXT)!=0)for(long i=0,n=size(names,SourceShape.NATIVE_FILE_NAME);i<n;i++) {
                long raw=field(element(names,SourceShape.NATIVE_FILE_NAME,i),SourceShape.NATIVE_FILE_NAME,3),length=view().characterCount(raw);
                if(length==0)open=true;else add(raw,length);
            }
        }else if(matches(field(reference,shape,3),"PROGRAM")) {
            if(available&&size(qualifications,SourceShape.TEXT)!=0&&matches(field(reference,shape,5),"LITERAL")) {
                boolean cics=matches(field(reference,shape,1),"CICS"),profile=matches(field(reference,shape,4),cics?CicsNameInterpreter.PROFILE:CallNameInterpreter.PROFILE);
                long values=field(reference,shape,7);
                for(long i=0,n=size(values,SourceShape.VALUE);i<n;i++) {
                    long raw=field(element(values,SourceShape.VALUE,i),SourceShape.VALUE,1),length=profile?interpreted(raw,cics,false):0;
                    open|=length==0||!cics;if(length!=0)add(raw,length);
                }
            }
        }else if(status==Status.QUALIFIED_POSSIBLE||status==Status.POSSIBLE_UNDER_UNKNOWN_CONTROL) {
            boolean profile=matches(field(reference,shape,3),"FILE")&&matches(field(reference,shape,4),"cics-ts.file@1");long values=field(reference,shape,7);
            for(long i=0,n=size(values,SourceShape.VALUE);i<n;i++) {
                long raw=field(element(values,SourceShape.VALUE,i),SourceShape.VALUE,1),length=profile?interpreted(raw,true,true):0;
                if(length==0)open=true;else add(raw,length);
            }
        }
        long at=Math.multiplyExact(itemCount,WIDTH);itemRows().set(at,reference);itemRows().set(at+1,status.ordinal());itemRows().set(at+2,start);itemRows().set(at+3,candidateCount-start);itemRows().set(at+4,open?1:0);itemRows().set(at+5,valueOpen?1:0);itemCount=Math.incrementExact(itemCount);
    }
    private void open(){if(closed||!ready)throw new IllegalStateException("SOURCE candidates expired or incomplete");parent().snapshot();}
    /** Successful admission that owns these prepared facts; never a caller-provided certificate. */
    public SourceAdmissionSnapshot admission(){open();return parent();}
    /** Borrow the already prepared control facts; no second control solve per unit. */
    SourceControlSnapshot controlForNominal(){open();return facts();}
    public long unitCount(){open();return parent().unitCount();}
    public long count(long unit,Kind kind){open();try{return unitRows().get(range(unit,kind)+1);}catch(RuntimeException|Error failure){abort(failure);throw failure;}}
    private long range(long unit,Kind kind){Objects.requireNonNull(kind);if(unit<0||unit>=parent().unitCount())throw new IndexOutOfBoundsException("SOURCE unit ordinal");return Math.addExact(Math.multiplyExact(unit,4),kind.ordinal()*2);}
    private long item(long unit,long ordinal,Kind kind) {long range=range(unit,kind),count=unitRows().get(range+1);if(ordinal<0||ordinal>=count)throw new IndexOutOfBoundsException("SOURCE item ordinal");return Math.multiplyExact(Math.addExact(unitRows().get(range),ordinal),WIDTH);}
    private long read(long unit,long ordinal,Kind kind,int slot){open();try{return itemRows().get(item(unit,ordinal,kind)+slot);}catch(RuntimeException|Error failure){abort(failure);throw failure;}}
    public long reference(long unit,long ordinal,Kind kind){return read(unit,ordinal,kind,0);}
    public Status status(long unit,long ordinal,Kind kind){return STATUSES[Math.toIntExact(read(unit,ordinal,kind,1))];}
    public long candidateCount(long unit,long ordinal,Kind kind){return read(unit,ordinal,kind,3);}
    public boolean remainder(long unit,long ordinal,Kind kind){return read(unit,ordinal,kind,4)!=0;}
    public boolean valueRemainder(long unit,long ordinal,Kind kind){return read(unit,ordinal,kind,5)!=0;}
    private long candidate(long unit,long ordinal,Kind kind,long candidate,int slot) {
        open();try{long row=item(unit,ordinal,kind),count=itemRows().get(row+3);if(candidate<0||candidate>=count)throw new IndexOutOfBoundsException("SOURCE candidate ordinal");return candidateRows().get(Math.addExact(Math.multiplyExact(Math.addExact(itemRows().get(row+2),candidate),2),slot));}
        catch(RuntimeException|Error failure){abort(failure);throw failure;}
    }
    public long rawValue(long unit,long ordinal,Kind kind,long candidate){return candidate(unit,ordinal,kind,candidate,0);}
    public long referenceLength(long unit,long ordinal,Kind kind,long candidate){return candidate(unit,ordinal,kind,candidate,1);}
    private static void suppress(Throwable primary,Throwable cleanup){if(primary!=cleanup)primary.addSuppressed(cleanup);}
    private void abort(Throwable primary){try{close();}catch(RuntimeException|Error cleanup){suppress(primary,cleanup);}}
    @Override public void close() {
        PagedLongArray a,b,c;SourceControlSnapshot facts;AnalysisResources.Reservation capacity;
        synchronized(this){if(closed)return;closed=true;ready=false;a=units;b=items;c=candidates;facts=controlFacts;capacity=control;units=items=candidates=null;controlFacts=null;control=null;source=null;admission=null;characters=null;}
        var parent=owner;if(parent!=null)parent.release(this);Throwable failure=null;
        for(AutoCloseable owned:new AutoCloseable[]{facts,c,b,a,capacity})if(owned!=null)try{owned.close();}catch(Exception|Error cleanup){if(failure==null)failure=cleanup;else suppress(failure,cleanup);}
        if(failure instanceof RuntimeException error)throw error;if(failure instanceof Error error)throw error;if(failure!=null)throw new IllegalStateException("SOURCE candidate cleanup",failure);
    }
}
