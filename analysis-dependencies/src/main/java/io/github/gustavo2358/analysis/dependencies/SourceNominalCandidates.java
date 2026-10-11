package io.github.gustavo2358.analysis.dependencies;

import io.github.gustavo2358.analysis.dependencies.source.*;
import io.github.gustavo2358.analysis.solver.*;
import java.util.Objects;
import java.util.function.LongPredicate;

/**
 * Read-only, ordered delivery of complete conditional nominal results. All
 * cardinality-dependent inventories, ordering and support deduplication are
 * paged. Raw values remain in the nominal dictionary; evidence/provenance and
 * uncertainty handles borrow the exact admitted SOURCE owner. No mutable value
 * store, detached Candidate/Support/UnitEvidence or whole payload escapes.
 * This is conditional source authority, never an executable AIR proof.
 */
public final class SourceNominalCandidates extends SourceAdmissionSnapshot.Dependent {
    private static final AnalysisResources.Phase PHASE=AnalysisResources.Phase.SORT;
    private static final int WIDTH=5;
    private volatile SourceAdmissionSnapshot admission;
    private SourceSnapshot source;
    private SourceNominalSnapshot nominal;
    private AnalysisResources resources;
    private PagedLongArray ranges,candidates,evidence;
    private AnalysisResources.Reservation control;
    private char[] left,right;
    private long unitOrdinal,queries,queryCount,candidateCount,evidenceCount,uncertainties;
    private volatile boolean closed,ready;

    private SourceNominalCandidates(SourceAdmissionSnapshot admission,AnalysisResources.Reservation capacity){this.admission=admission;control=capacity;}
    public static SourceNominalCandidates prepare(SourceAdmissionSnapshot admission,long unitOrdinal,LongPredicate requested,
            SourceControlSnapshot controlFacts,PageStore pages,AnalysisResources resources) {
        Objects.requireNonNull(admission);Objects.requireNonNull(requested);Objects.requireNonNull(controlFacts);Objects.requireNonNull(pages);Objects.requireNonNull(resources);
        admission.requireContext(pages,resources);controlFacts.requireAdmission(admission);
        var capacity=resources.reserve(AnalysisResources.Pool.RESIDENT,2048,PHASE);SourceNominalCandidates result=null;
        try {
            result=new SourceNominalCandidates(admission,capacity);var registration=capacity;capacity=null;registration.onOwnerClose(result::close);
            result.initialize(unitOrdinal,requested,controlFacts,pages,resources);admission.attach(result,pages,resources);result.open();return result;
        }catch(RuntimeException|Error failure) {
            if(result!=null)result.abort(failure);else if(capacity!=null)try{capacity.close();}catch(RuntimeException|Error cleanup){suppress(failure,cleanup);}throw failure;
        }
    }
    private SourceAdmissionSnapshot parent(){var value=admission;if(value==null)throw new IllegalStateException("nominal SOURCE candidates expired");return value;}
    private void open(){if(closed||!ready)throw new IllegalStateException("nominal SOURCE candidates expired or incomplete");parent().snapshot();nominal.workItems();}
    private long field(long row,SourceShape shape,int slot){return source.field(row,shape,slot);}
    private void initialize(long unitOrdinal,LongPredicate requested,SourceControlSnapshot controlFacts,PageStore pages,AnalysisResources resources) {
        if(closed)throw new IllegalStateException("nominal candidates expired before initialization");this.unitOrdinal=unitOrdinal;this.resources=resources;source=parent().snapshot();left=new char[128];right=new char[128];
        nominal=SourceNominalSnapshot.prepare(parent(),unitOrdinal,requested,controlFacts,pages,resources);
        long facts=field(nominal.nominalEvidence(),SourceShape.NOMINAL_VALUE_EVIDENCE,0);queries=field(facts,SourceShape.NOMINAL_VALUES,4);queryCount=source.size(queries,SourceShape.QUERY);uncertainties=field(nominal.nominalEvidence(),SourceShape.NOMINAL_VALUE_EVIDENCE,4);
        if(nominal.activeQueries()!=0) {
            ranges=new PagedLongArray(pages,Long.MAX_VALUE,resources,PHASE);candidates=new PagedLongArray(pages,Long.MAX_VALUE,resources,PHASE);evidence=new PagedLongArray(pages,Long.MAX_VALUE,resources,PHASE);
            for(long q=0;q<queryCount;q++) {
                ranges.set(q*2,candidateCount);long value=nominal.value(q);
                if(value!=0)prepareQuery(q,value,pages,resources);
                ranges.set(q*2+1,candidateCount-ranges.get(q*2));
            }
        }
        nominal.workItems();parent().snapshot();if(closed)throw new IllegalStateException("nominal candidates expired before completion");ready=true;
    }
    private void prepareQuery(long query,long value,PageStore pages,AnalysisResources resources) {
        var values=nominal.values();
        // Values are already exact/deduplicated. The temporary index changes only
        // delivery order from dictionary IDs to String.compareTo's UTF16 order.
        try(var order=new PagedLongIndex(pages,resources,PHASE,this::compareRaw)) {
            try(var cursor=values.candidates(value)) {while(cursor.advance()) {
                resources.work(1,PHASE);long row=++candidateCount,at=row*WIDTH;
                candidates.set(at,cursor.key());candidates.set(at+1,cursor.value());order.intern(row,row);
            }}
            long first=ranges.get(query*2),next=first;
            // The row directory and ordered references are separate: original
            // immutable index keys must not change underneath its comparator.
            try(var cursor=order.cursor()){while(cursor.advance()) {
                long row=cursor.value(),at=row*WIDTH;candidates.set(next*WIDTH+4,row);next++;
                candidates.set(at+2,evidenceCount);boolean declaration=prepareEvidence(candidates.get(at+1),pages,resources);
                candidates.set(at+3,Math.addExact(Math.multiplyExact(evidenceCount-candidates.get(at+2),2),declaration?1:0));
            }}
        }
    }
    private boolean prepareEvidence(long support,PageStore pages,AnalysisResources resources) {
        boolean declaration=false;
        try(var order=new PagedLongIndex(pages,resources,PHASE,this::compareProof);var distinct=new SourceAdmissionDirectory(pages,resources)) {
            try(var cursor=nominal.values().supports(support)){while(cursor.advance()){long atom=cursor.key();order.intern(atom,atom);}}
            try(var cursor=order.cursor()){while(cursor.advance()) {
                resources.work(1,PHASE);long atom=cursor.value();int kind=nominal.proofKind(atom);long reference=kind==1?nominal.proofDetail(atom):nominal.proofOwner(atom),provenance=nominal.proofProvenance(atom),before=distinct.size();
                // Immutable SOURCE recipes (including complete ordered lists)
                // and text are canonical. Identity here is the exact resident
                // Evidence(kind,reference,provenance), not merely ProofKey.
                distinct.intern(0,kind==2?2:1,reference,provenance,atom,0);
                if(distinct.size()!=before){evidence.set(evidenceCount++,atom);declaration|=kind!=2;}
            }}
        }return declaration;
    }
    private int compareRaw(long a,long b) {
        if(a==b)return 0;var values=nominal.values();long x=candidates.get(a*WIDTH),y=candidates.get(b*WIDTH),na=values.characterCount(x),nb=values.characterCount(y),n=Math.min(na,nb);
        for(long at=0;at<n;){resources.work(1,PHASE);int count=(int)Math.min(left.length,n-at);values.readCharacters(x,at,left,0,count);values.readCharacters(y,at,right,0,count);for(int i=0;i<count;i++){int c=Character.compare(left[i],right[i]);if(c!=0)return c;}at+=count;}return Long.compare(na,nb);
    }
    private int compareText(long a,long b) {
        if(a==b)return 0;long na=a==0?0:source.characterCount(a),nb=b==0?0:source.characterCount(b),n=Math.min(na,nb);
        for(long at=0;at<n;){resources.work(1,PHASE);int count=(int)Math.min(left.length,n-at);source.readCharacters(a,at,left,0,count);source.readCharacters(b,at,right,0,count);for(int i=0;i<count;i++){int c=Character.compare(left[i],right[i]);if(c!=0)return c;}at+=count;}return Long.compare(na,nb);
    }
    private int compareProof(long a,long b){if(a==b)return 0;int c=Integer.compare(nominal.proofKind(a),nominal.proofKind(b));if(c==0)c=compareText(nominal.proofOwner(a),nominal.proofOwner(b));return c==0?compareText(nominal.proofDetail(a),nominal.proofDetail(b)):c;}
    private void queryOrdinal(long q){if(q<0||q>=queryCount)throw new IndexOutOfBoundsException("nominal query ordinal");}
    private long row(long q,long c){queryOrdinal(q);long n=ranges==null?0:ranges.get(q*2+1);if(c<0||c>=n)throw new IndexOutOfBoundsException("nominal candidate ordinal");return candidates.get((ranges.get(q*2)+c)*WIDTH+4)*WIDTH;}
    private long atom(long q,long c,long e){long row=row(q,c),count=candidates.get(row+3)>>>1;if(e<0||e>=count)throw new IndexOutOfBoundsException("nominal evidence ordinal");return evidence.get(candidates.get(row+2)+e);}
    public SourceAdmissionSnapshot admission(){open();return parent();}
    public long unitOrdinal(){open();return unitOrdinal;}
    public long queryCount(){open();return queryCount;}
    public long activeQueries(){open();return nominal.activeQueries();}
    public long workItems(){open();return nominal.workItems();}
    public long query(long q){open();try{queryOrdinal(q);return source.element(queries,SourceShape.QUERY,q);}catch(RuntimeException|Error failure){abort(failure);throw failure;}}
    public long candidateCount(long q){open();try{queryOrdinal(q);return ranges==null?0:ranges.get(q*2+1);}catch(RuntimeException|Error failure){abort(failure);throw failure;}}
    public long rawLength(long q,long c){open();try{return nominal.values().characterCount(candidates.get(row(q,c)));}catch(RuntimeException|Error failure){abort(failure);throw failure;}}
    public void readRaw(long q,long c,long offset,char[] destination,int at,int count){open();try{nominal.values().readCharacters(candidates.get(row(q,c)),offset,destination,at,count);}catch(RuntimeException|Error failure){abort(failure);throw failure;}}
    public long evidenceCount(long q,long c){open();try{return candidates.get(row(q,c)+3)>>>1;}catch(RuntimeException|Error failure){abort(failure);throw failure;}}
    public String evidenceKind(long q,long c,long e){open();try{return nominal.proofKind(atom(q,c,e))==2?"ASSIGNMENT":"DECLARATION_VALUE";}catch(RuntimeException|Error failure){abort(failure);throw failure;}}
    public long evidenceReference(long q,long c,long e){open();try{long atom=atom(q,c,e);return nominal.proofKind(atom)==1?nominal.proofDetail(atom):nominal.proofOwner(atom);}catch(RuntimeException|Error failure){abort(failure);throw failure;}}
    public long evidenceProvenance(long q,long c,long e){open();try{return nominal.proofProvenance(atom(q,c,e));}catch(RuntimeException|Error failure){abort(failure);throw failure;}}
    private int assumptions(long q,long c) {
        long row=row(q,c),value=nominal.value(q);int flags=nominal.values().flags(value);
        return (nominal.affected(q)?1:0)|((flags&SourceValueStore.TABLE)!=0||nominal.summary(q)?2:0)|((flags&SourceValueStore.MODEL)!=0?4:0)|((candidates.get(row+3)&1)!=0?8:0);
    }
    public int assumptionCount(long q,long c){open();try{return 2+Integer.bitCount(assumptions(q,c));}catch(RuntimeException|Error failure){abort(failure);throw failure;}}
    public String assumption(long q,long c,int ordinal) {
        open();try {
            int mask=assumptions(q,c);if(ordinal<0||ordinal>=2+Integer.bitCount(mask))throw new IndexOutOfBoundsException("nominal assumption ordinal");
            if(ordinal==0)return "NOMINAL_DECLARATIONS_PRESERVE_MEANING";if(ordinal==1)return "NO_UNMODELED_STORAGE_INTERFERENCE";
            int next=2;for(int bit=0;bit<4;bit++)if((mask&(1<<bit))!=0)if(next++==ordinal)return switch(bit){case 0->"UNKNOWN_CONTROL_CAN_COMPLETE";case 1->"TABLE_INDEX_NOT_REFINED";case 2->"SYNTHETIC_MODEL_IS_NOT_KILL_PROOF";default->"DECLARATIVE_INITIAL_VALUES_APPLY";};
            throw new IllegalStateException("nominal assumption flags");
        }catch(RuntimeException|Error failure){abort(failure);throw failure;}
    }
    public long uncertaintyCount(){open();try{return source.size(uncertainties,SourceShape.UNCERTAINTY);}catch(RuntimeException|Error failure){abort(failure);throw failure;}}
    public long uncertainty(long ordinal){open();try{if(ordinal<0||ordinal>=source.size(uncertainties,SourceShape.UNCERTAINTY))throw new IndexOutOfBoundsException("nominal uncertainty ordinal");return source.element(uncertainties,SourceShape.UNCERTAINTY,ordinal);}catch(RuntimeException|Error failure){abort(failure);throw failure;}}
    private static void suppress(Throwable primary,Throwable cleanup){if(primary!=cleanup)primary.addSuppressed(cleanup);}
    private void abort(Throwable primary){try{close();}catch(RuntimeException|Error cleanup){suppress(primary,cleanup);}}
    @Override public void close() {
        AutoCloseable[] owned;
        synchronized(this){if(closed)return;closed=true;ready=false;owned=new AutoCloseable[]{evidence,candidates,ranges,nominal,control};evidence=candidates=ranges=null;nominal=null;control=null;source=null;admission=null;resources=null;left=right=null;}
        var parent=owner;if(parent!=null)parent.release(this);Throwable failure=null;
        for(var value:owned)if(value!=null)try{value.close();}catch(Exception|Error cleanup){if(failure==null)failure=cleanup;else suppress(failure,cleanup);}
        if(failure instanceof RuntimeException error)throw error;if(failure instanceof Error error)throw error;if(failure!=null)throw new IllegalStateException("nominal delivery cleanup",failure);
    }
}
