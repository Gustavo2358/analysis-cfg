package io.github.gustavo2358.analysis.dependencies;

import io.github.gustavo2358.analysis.dependencies.source.*;
import io.github.gustavo2358.analysis.solver.*;
import java.util.Objects;

/**
 * Internal directed nominal-demand graph for one admitted SOURCE unit. No values,
 * executable reachability or admission authority is manufactured here. Complete
 * canonical SOURCE handles key paged directories; all graph/closure state spills.
 * Assignment expressions and condition expressions have separate layers, so a
 * shared expression cannot invent demand for an assignment receiver. Conditions
 * use bidirectional incidence, not a Cartesian product of their reads.
 */
final class SourceNominalDemand extends SourceAdmissionSnapshot.Dependent {
    private static final AnalysisResources.Phase PHASE=AnalysisResources.Phase.INDEX;
    private static final int SYMBOL=1,ASSIGNMENT_TERM=2,CONDITION_TERM=3,PREDICATE=4;
    private volatile SourceAdmissionSnapshot admission;
    private SourceSnapshot source;
    private AnalysisResources.Reservation control;
    private SourceAdmissionDirectory directory;
    private PagedLongArray nodes,edges,symbols,queue;
    private long scope,symbolCount,edgeCount,epoch,tail,nodeVisits,edgeVisits;
    private boolean building,complete;
    private volatile boolean closed,ready;

    private SourceNominalDemand(SourceAdmissionSnapshot admission,AnalysisResources.Reservation capacity){this.admission=admission;control=capacity;}
    static SourceNominalDemand prepare(SourceAdmissionSnapshot admission,long unitOrdinal,PageStore pages,AnalysisResources resources) {
        Objects.requireNonNull(admission);Objects.requireNonNull(pages);Objects.requireNonNull(resources);
        admission.requireContext(pages,resources);
        if(unitOrdinal<0||unitOrdinal>=admission.unitCount())throw new IndexOutOfBoundsException("source unit ordinal");
        long unit=admission.unit(unitOrdinal),nominal=admission.snapshot().field(unit,SourceShape.UNIT_EVIDENCE,12);
        if(nominal==0)throw new IllegalArgumentException("nominal SOURCE evidence unavailable");
        var capacity=resources.reserve(AnalysisResources.Pool.RESIDENT,1024,PHASE);SourceNominalDemand result=null;
        try {
            result=new SourceNominalDemand(admission,capacity);var registration=capacity;capacity=null;
            registration.onOwnerClose(result::close);result.initialize(unit,nominal,pages,resources);
            admission.attach(result,pages,resources);result.open();return result;
        }catch(RuntimeException|Error failure) {
            if(result!=null)result.abort(failure);else if(capacity!=null)try{capacity.close();}catch(RuntimeException|Error cleanup){suppress(failure,cleanup);}
            throw failure;
        }
    }
    private void initialize(long unit,long nominal,PageStore pages,AnalysisResources resources) {
        if(closed)throw new IllegalStateException("nominal demand expired before initialization");
        source=parent().snapshot();scope=field(unit,SourceShape.UNIT_EVIDENCE,0);
        directory=new SourceAdmissionDirectory(pages,resources);
        nodes=new PagedLongArray(pages,Long.MAX_VALUE,resources,PHASE);edges=new PagedLongArray(pages,Long.MAX_VALUE,resources,PHASE);
        symbols=new PagedLongArray(pages,Long.MAX_VALUE,resources,PHASE);queue=new PagedLongArray(pages,Long.MAX_VALUE,resources,PHASE);
        long facts=field(nominal,SourceShape.NOMINAL_VALUE_EVIDENCE,0),inventory=field(facts,SourceShape.NOMINAL_VALUES,1);
        symbolCount=size(inventory,SourceShape.SYMBOL);
        for(long i=0;i<symbolCount;i++) {
            long symbol=element(inventory,SourceShape.SYMBOL,i),row=node(SYMBOL,field(symbol,SourceShape.SYMBOL,0));symbols.set(i,row);
        }
        long writes=field(facts,SourceShape.NOMINAL_VALUES,2);
        for(long i=0,n=size(writes,SourceShape.ASSIGNMENT);i<n;i++) {
            long write=element(writes,SourceShape.ASSIGNMENT,i);
            edge(symbol(field(write,SourceShape.ASSIGNMENT,1)),node(ASSIGNMENT_TERM,field(write,SourceShape.ASSIGNMENT,2)));
        }
        long conditions=field(facts,SourceShape.NOMINAL_VALUES,3);
        for(long i=0,n=size(conditions,SourceShape.CONDITION);i<n;i++)node(PREDICATE,field(element(conditions,SourceShape.CONDITION,i),SourceShape.CONDITION,1));
        // New nodes append once. The moving upper bound expands all shared AST
        // handles iteratively, without recursion or a resident pending container.
        for(long row=1;row<=directory.size();row++) {
            int kind=Math.toIntExact(nodes.get(row*2));long reference=nodes.get(row*2+1);
            if(kind==SYMBOL)continue;
            if(kind==PREDICATE) {
                long children=field(reference,SourceShape.PREDICATE,2),terms=field(reference,SourceShape.PREDICATE,1);
                for(long i=0,n=size(children,SourceShape.PREDICATE);i<n;i++)incidence(row,node(PREDICATE,element(children,SourceShape.PREDICATE,i)));
                for(long i=0,n=size(terms,SourceShape.TERM);i<n;i++)incidence(row,node(CONDITION_TERM,element(terms,SourceShape.TERM,i)));
            }else {
                boolean condition=kind==CONDITION_TERM;
                if(view().matches(field(reference,SourceShape.TERM,0),"READ")) {
                    long read=symbol(field(reference,SourceShape.TERM,1));if(condition)incidence(row,read);else edge(row,read);
                }
                long children=field(reference,SourceShape.TERM,2);
                if(children!=0)for(long i=0,n=size(children,SourceShape.TERM);i<n;i++) {
                    long child=node(kind,element(children,SourceShape.TERM,i));if(condition)incidence(row,child);else edge(row,child);
                }
            }
        }
        parent().snapshot();if(closed)throw new IllegalStateException("nominal demand expired before completion");ready=true;
    }
    private SourceAdmissionSnapshot parent(){var value=admission;if(value==null)throw new IllegalStateException("nominal demand expired");return value;}
    private SourceSnapshot view(){var value=source;if(value==null)throw new IllegalStateException("nominal demand expired");return value;}
    private long field(long row,SourceShape shape,int ordinal){return view().field(row,shape,ordinal);}
    private long size(long list,SourceShape shape){return view().size(list,shape);}
    private long element(long list,SourceShape shape,long ordinal){return view().element(list,shape,ordinal);}
    private long node(int kind,long reference) {
        long found=directory.find(scope,kind,reference,0);if(found!=0)return found;
        long row=directory.intern(scope,kind,reference,0,reference,directory.size());
        nodes.set(Math.multiplyExact(row,2),kind);nodes.set(Math.addExact(Math.multiplyExact(row,2),1),reference);return row;
    }
    private long symbol(long reference) {
        long row=directory.find(scope,SYMBOL,reference,0);
        if(row==0)throw new IllegalStateException("admitted nominal symbol missing from demand directory");return row;
    }
    private void edge(long from,long to) {
        long row=Math.incrementExact(edgeCount),at=Math.multiplyExact(row,2);
        edges.set(at,to);edges.set(at+1,directory.state(from,0));directory.state(from,0,row);edgeCount=row;
    }
    private void incidence(long left,long right){edge(left,right);edge(right,left);}
    private void open(){if(closed||!ready)throw new IllegalStateException("nominal demand expired or incomplete");parent().snapshot();}
    private long symbolOrdinal(long ordinal){if(ordinal<0||ordinal>=symbolCount)throw new IndexOutOfBoundsException("nominal symbol ordinal");return symbols.get(ordinal);}
    long symbolCount(){open();return symbolCount;}
    long nodeCount(){open();try{return directory.size();}catch(RuntimeException|Error failure){abort(failure);throw failure;}}
    long edgeCount(){open();return edgeCount;}
    /** Single scoped query, reused paged storage; no result Set escapes. */
    void begin() {
        open();try {epoch=Math.incrementExact(epoch);tail=nodeVisits=edgeVisits=0;building=true;complete=false;}
        catch(RuntimeException|Error failure){abort(failure);throw failure;}
    }
    private void mark(long row){if(directory.state(row,1)!=epoch){directory.state(row,1,epoch);queue.set(tail,row);tail=Math.incrementExact(tail);}}
    void request(long ordinal) {
        open();try{if(!building)throw new IllegalStateException("nominal demand query not open");mark(symbolOrdinal(ordinal));}
        catch(RuntimeException|Error failure){abort(failure);throw failure;}
    }
    /** Internal canonical symbol reference from this same admitted unit. */
    void requestReference(long reference) {
        open();try{if(!building)throw new IllegalStateException("nominal demand query not open");mark(symbol(reference));}
        catch(RuntimeException|Error failure){abort(failure);throw failure;}
    }
    void finish() {
        open();try {
            if(!building)throw new IllegalStateException("nominal demand query not open");
            for(long head=0;head<tail;head++) {
                long row=queue.get(head);nodeVisits=Math.incrementExact(nodeVisits);
                for(long edge=directory.state(row,0);edge!=0;edge=edges.get(edge*2+1)){edgeVisits=Math.incrementExact(edgeVisits);mark(edges.get(edge*2));}
            }building=false;complete=true;parent().snapshot();
        }catch(RuntimeException|Error failure){abort(failure);throw failure;}
    }
    private void query(){if(!complete)throw new IllegalStateException("nominal demand query not complete");}
    boolean contains(long ordinal) {
        open();try{query();return directory.state(symbolOrdinal(ordinal),1)==epoch;}
        catch(RuntimeException|Error failure){abort(failure);throw failure;}
    }
    boolean containsReference(long reference) {
        open();try{query();return directory.state(symbol(reference),1)==epoch;}
        catch(RuntimeException|Error failure){abort(failure);throw failure;}
    }
    long nodeVisits(){open();query();return nodeVisits;}
    long edgeVisits(){open();query();return edgeVisits;}
    private static void suppress(Throwable primary,Throwable cleanup){if(primary!=cleanup)primary.addSuppressed(cleanup);}
    private void abort(Throwable primary){try{close();}catch(RuntimeException|Error cleanup){suppress(primary,cleanup);}}
    @Override public void close() {
        AutoCloseable[] owned;
        synchronized(this){if(closed)return;closed=true;ready=false;owned=new AutoCloseable[]{queue,symbols,edges,nodes,directory,control};queue=symbols=edges=nodes=null;directory=null;control=null;admission=null;source=null;}
        var parent=owner;if(parent!=null)parent.release(this);Throwable failure=null;
        for(var value:owned)if(value!=null)try{value.close();}catch(Exception|Error cleanup){if(failure==null)failure=cleanup;else suppress(failure,cleanup);}
        if(failure instanceof RuntimeException error)throw error;if(failure instanceof Error error)throw error;if(failure!=null)throw new IllegalStateException("nominal demand cleanup",failure);
    }
}
