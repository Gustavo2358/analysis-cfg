package io.github.gustavo2358.analysis.dependencies;

import io.github.gustavo2358.analysis.dependencies.source.*;
import io.github.gustavo2358.analysis.solver.*;
import java.util.Objects;

/**
 * Exact managed SOURCE control facts, borrowing one successful immutable admission.
 * Unknown-proof ancestry, affected nodes and assumed-only nodes retain their existing
 * distinct meanings. These facts are not executable AIR/CFG reachability. All
 * variable directories/queues/edges are paged; no SOURCE resident model is rebuilt.
 */
public final class SourceControlSnapshot extends SourceAdmissionSnapshot.Dependent {
    private static final AnalysisResources.Phase PHASE=AnalysisResources.Phase.INDEX;
    private static final int PROOF=1,NODE=2;
    private static final long AFFECTED=1,PROVED=2;
    private volatile SourceAdmissionSnapshot admission;
    private volatile SourceSnapshot source;
    private AnalysisResources.Reservation control;
    private volatile SourceAdmissionDirectory facts;
    private volatile PagedLongArray seeded;
    private volatile boolean closed,ready;

    private SourceControlSnapshot(SourceAdmissionSnapshot admission,AnalysisResources.Reservation capacity) {
        this.admission=admission;control=capacity;
    }
    /** Borrows the admission/backend; rejection never steals an unrelated owner. */
    public static SourceControlSnapshot prepare(SourceAdmissionSnapshot admission,PageStore pages,AnalysisResources resources) {
        Objects.requireNonNull(admission);Objects.requireNonNull(pages);Objects.requireNonNull(resources);
        admission.requireContext(pages,resources);
        var capacity=resources.reserve(AnalysisResources.Pool.RESIDENT,1024,PHASE);
        SourceControlSnapshot result=null;
        try {
            result=new SourceControlSnapshot(admission,capacity);
            var registration=capacity;capacity=null;
            registration.onOwnerClose(result::close);
            result.initialize(pages,resources);
            admission.attach(result,pages,resources);result.open();
            return result;
        }catch(RuntimeException|Error failure) {
            if(result!=null)result.abort(failure);else if(capacity!=null)try{capacity.close();}catch(RuntimeException|Error cleanup){suppress(failure,cleanup);}
            throw failure;
        }
    }
    private void initialize(PageStore pages,AnalysisResources resources) {
        if(closed)throw new IllegalStateException("source control consumer expired before initialization");
        source=parent().snapshot();facts=new SourceAdmissionDirectory(pages,resources);
        seeded=new PagedLongArray(pages,Long.MAX_VALUE,resources,PHASE);
        try(var edges=new PagedLongArray(pages,Long.MAX_VALUE,resources,PHASE);
            var queue=new PagedLongArray(pages,Long.MAX_VALUE,resources,PHASE);
            var derivations=new PagedLongArray(pages,Long.MAX_VALUE,resources,PHASE)) {
            for(long i=0,count=parent().unitCount();i<count;i++) {
                long unit=parent().unit(i),proofs=field(unit,UNIT,10);boolean hasSeed=false;
                for(long j=0,n=size(proofs,SourceShape.PROOF);j<n;j++)
                    if(snapshotView().matches(field(element(proofs,SourceShape.PROOF,j),SourceShape.PROOF,1),"CONTROL_POSSIBILITY"))hasSeed=true;
                unitSeeds().set(i,hasSeed?1:0);
                if(hasSeed)compute(unit,proofs,edges,queue,derivations);
            }
        }
        parent().snapshot(); // Includes teardown of all transient graph owners.
        if(closed)throw new IllegalStateException("source control consumer expired before completion");
        ready=true;
    }
    private static final SourceShape UNIT=SourceShape.UNIT_EVIDENCE;
    private SourceAdmissionSnapshot parent(){var value=admission;if(value==null)throw new IllegalStateException("source control consumer expired");return value;}
    private SourceSnapshot snapshotView(){var value=source;if(value==null)throw new IllegalStateException("source control consumer expired");return value;}
    private SourceAdmissionDirectory directory(){var value=facts;if(value==null)throw new IllegalStateException("source control consumer expired");return value;}
    private PagedLongArray unitSeeds(){var value=seeded;if(value==null)throw new IllegalStateException("source control consumer expired");return value;}
    private long field(long row,SourceShape shape,int ordinal){return snapshotView().field(row,shape,ordinal);}
    private long size(long list,SourceShape shape){return snapshotView().size(list,shape);}
    private long element(long list,SourceShape shape,long ordinal){return snapshotView().element(list,shape,ordinal);}
    private long lookup(long unit,int namespace,long id) {
        long row=directory().find(unit,namespace,id,0);
        if(row==0)throw new IllegalStateException("admitted SOURCE reference missing from control directory");
        return row;
    }
    private static long edge(PagedLongArray edges,long count,long dependent,long head) {
        long next=Math.incrementExact(count),at=Math.multiplyExact(next,2);
        edges.set(at,dependent);edges.set(at+1,head);return next;
    }
    private long mark(long row,long flag,PagedLongArray queue,long tail) {
        long flags=directory().state(row,0);
        if((flags&flag)!=0)return tail;
        directory().state(row,0,flags|flag);queue.set(tail,row);return Math.incrementExact(tail);
    }
    private void compute(long unit,long proofs,PagedLongArray edges,PagedLongArray queue,PagedLongArray derivations) {
        long scope=field(unit,UNIT,0),nodes=field(unit,UNIT,5),proofCount=size(proofs,SourceShape.PROOF);
        long tail=0;
        for(long j=0;j<proofCount;j++) {
            long proof=element(proofs,SourceShape.PROOF,j),row=directory().intern(scope,PROOF,field(proof,SourceShape.PROOF,0),0,proof,j);
            if(snapshotView().matches(field(proof,SourceShape.PROOF,1),"CONTROL_POSSIBILITY"))tail=mark(row,AFFECTED,queue,tail);
        }
        long edgeCount=0;
        for(long j=0;j<proofCount;j++) {
            long proof=element(proofs,SourceShape.PROOF,j),dependent=lookup(scope,PROOF,field(proof,SourceShape.PROOF,0));
            long dependencies=field(proof,SourceShape.PROOF,4);
            for(long k=0,n=size(dependencies,SourceShape.TEXT);k<n;k++) {
                long premise=lookup(scope,PROOF,element(dependencies,SourceShape.TEXT,k));
                edgeCount=edge(edges,edgeCount,dependent,directory().state(premise,1));directory().state(premise,1,edgeCount);
            }
        }
        for(long head=0;head<tail;head++) {
            long row=queue.get(head);
            for(long e=directory().state(row,1);e!=0;e=edges.get(e*2+1))tail=mark(edges.get(e*2),AFFECTED,queue,tail);
        }
        for(long j=0,n=size(nodes,SourceShape.NODE);j<n;j++) {
            long node=element(nodes,SourceShape.NODE,j);directory().intern(scope,NODE,field(node,SourceShape.NODE,0),0,node,j);
        }
        tail=0;edgeCount=0;
        long steps=field(unit,UNIT,6),count=size(steps,SourceShape.DERIVATION);
        for(long j=0;j<count;j++) {
            long step=element(steps,SourceShape.DERIVATION,j),destination=lookup(scope,NODE,field(step,SourceShape.DERIVATION,2));
            long supports=field(step,SourceShape.DERIVATION,5);boolean hypothetical=false;
            for(long k=0,n=size(supports,SourceShape.TEXT);k<n;k++)
                if((directory().state(lookup(scope,PROOF,element(supports,SourceShape.TEXT,k)),0)&AFFECTED)!=0)hypothetical=true;
            if(hypothetical)tail=mark(destination,AFFECTED,queue,tail);
            long sources=field(step,SourceShape.DERIVATION,1),callers=field(step,SourceShape.DERIVATION,3);
            long a=size(sources,SourceShape.TEXT)==0?0:element(sources,SourceShape.TEXT,0);
            long b=size(callers,SourceShape.TEXT)==0?0:element(callers,SourceShape.TEXT,0);
            long pending=(a==0?0:1)+(b==0||b==a?0:1),at=j*2;
            derivations.set(at,hypothetical?-1:pending);derivations.set(at+1,destination);
            if(a!=0) {long row=lookup(scope,NODE,a);edgeCount=edge(edges,edgeCount,j+1,directory().state(row,1));directory().state(row,1,edgeCount);}
            if(b!=0&&b!=a) {long row=lookup(scope,NODE,b);edgeCount=edge(edges,edgeCount,j+1,directory().state(row,1));directory().state(row,1,edgeCount);}
        }
        // Affected means any alternative/premise carries a hypothetical proof.
        for(long head=0;head<tail;head++) {
            long row=queue.get(head);
            for(long e=directory().state(row,1);e!=0;e=edges.get(e*2+1)) {
                long step=edges.get(e*2)-1;tail=mark(derivations.get(step*2+1),AFFECTED,queue,tail);
            }
        }
        // Independent grounding: source/caller AND, incoming derivations OR.
        tail=0;
        for(long j=0;j<count;j++)if(derivations.get(j*2)==0)tail=mark(derivations.get(j*2+1),PROVED,queue,tail);
        for(long head=0;head<tail;head++) {
            long row=queue.get(head);
            for(long e=directory().state(row,1);e!=0;e=edges.get(e*2+1)) {
                long at=(edges.get(e*2)-1)*2,pending=derivations.get(at);
                if(pending<=0)continue;
                derivations.set(at,pending-1);
                if(pending==1)tail=mark(derivations.get(at+1),PROVED,queue,tail);
            }
        }
    }
    private void open(){if(closed||!ready)throw new IllegalStateException("source control consumer expired or incomplete");parent().snapshot();}
    private long fact(long unitOrdinal,long itemOrdinal,int namespace,SourceShape shape,int fieldOrdinal) {
        var current=parent();if(unitOrdinal<0||unitOrdinal>=current.unitCount())throw new IndexOutOfBoundsException("source unit ordinal");
        long unit=current.unit(unitOrdinal),list=field(unit,UNIT,fieldOrdinal);
        if(itemOrdinal<0||itemOrdinal>=size(list,shape))throw new IndexOutOfBoundsException("source item ordinal");
        long item=element(list,shape,itemOrdinal);
        if(unitSeeds().get(unitOrdinal)==0)return 0;
        return lookup(field(unit,UNIT,0),namespace,field(item,shape,0));
    }
    public boolean affected(long unitOrdinal,long nodeOrdinal) {
        open();try{long row=fact(unitOrdinal,nodeOrdinal,NODE,SourceShape.NODE,5);return row!=0&&(directory().state(row,0)&AFFECTED)!=0;}
        catch(RuntimeException|Error failure){abort(failure);throw failure;}
    }
    /** Internal canonical node reference from this already-admitted SOURCE owner. */
    boolean affectedReference(long unitOrdinal,long canonicalId) {
        open();try {
            var parent=parent();if(unitOrdinal<0||unitOrdinal>=parent.unitCount())throw new IndexOutOfBoundsException("source unit ordinal");
            if(unitSeeds().get(unitOrdinal)==0)return false;
            long scope=field(parent.unit(unitOrdinal),UNIT,0),row=lookup(scope,NODE,canonicalId);
            return (directory().state(row,0)&AFFECTED)!=0;
        }catch(RuntimeException|Error failure){abort(failure);throw failure;}
    }
    public boolean assumedOnly(long unitOrdinal,long nodeOrdinal) {
        open();try{long row=fact(unitOrdinal,nodeOrdinal,NODE,SourceShape.NODE,5);return row!=0&&(directory().state(row,0)&PROVED)==0;}
        catch(RuntimeException|Error failure){abort(failure);throw failure;}
    }
    public boolean hypothetical(long unitOrdinal,long proofOrdinal) {
        open();try{long row=fact(unitOrdinal,proofOrdinal,PROOF,SourceShape.PROOF,10);return row!=0&&(directory().state(row,0)&AFFECTED)!=0;}
        catch(RuntimeException|Error failure){abort(failure);throw failure;}
    }
    /** Primitive retained directory rows; no-seed units retain no proof/node index. */
    public long retainedEntries(){open();try{return directory().size();}catch(RuntimeException|Error failure){abort(failure);throw failure;}}
    private static void suppress(Throwable primary,Throwable cleanup){if(primary!=cleanup)primary.addSuppressed(cleanup);}
    private void abort(Throwable primary){try{close();}catch(RuntimeException|Error cleanup){suppress(primary,cleanup);}}
    @Override public void close() {
        SourceAdmissionDirectory rows;PagedLongArray units;AnalysisResources.Reservation capacity;
        synchronized(this){if(closed)return;closed=true;ready=false;rows=facts;units=seeded;capacity=control;facts=null;seeded=null;control=null;admission=null;source=null;}
        var parent=owner;if(parent!=null)parent.release(this);
        Throwable failure=null;
        for(AutoCloseable value:new AutoCloseable[]{rows,units,capacity})if(value!=null)try{value.close();}catch(Exception|Error cleanup){if(failure==null)failure=cleanup;else suppress(failure,cleanup);}
        if(failure instanceof RuntimeException error)throw error;if(failure instanceof Error error)throw error;
        if(failure!=null)throw new IllegalStateException("source control cleanup",failure);
    }
}
