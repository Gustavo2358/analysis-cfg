package io.github.gustavo2358.analysis.dependencies;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.OperationId;
import io.github.gustavo2358.analysis.dependencies.source.*;
import io.github.gustavo2358.analysis.structure.ProgramStore;
import io.github.gustavo2358.analysis.solver.*;
import java.util.Objects;
import static io.github.gustavo2358.air.model.AirShape.*;

/**
 * Complete source/executable target inventory and conditional-query selection.
 * It borrows the existing immutable executable application result, never copies
 * its SiteFacts, and indexes it by the native program's exact operation directory.
 * All new inventories, incidence and query demand are paged. SOURCE order and
 * unmapped AIR order are preserved. This is planning, not resolved candidates,
 * product serialization or a claim that the borrowed executable result spills.
 */
public final class SourceTargetPlan extends SourceAdmissionSnapshot.Dependent {
    private static final AnalysisResources.Phase PHASE=AnalysisResources.Phase.DEMAND;
    private static final int WIDTH=9,COMPUTED=1,LITERAL=2,UNAVAILABLE=3,QUALIFIED=8,CONTROL_OPEN=16,REQUEST=32;
    private volatile SourceAdmissionSnapshot admission;
    private SnapshotProgram program;
    private SourceSnapshot source;
    private DependencyResult executable;
    private ProgramStore.OperationDirectory directory;
    private PagedLongArray rows,operations,sites,queryRanges,requested;
    private AnalysisResources.Reservation control;
    private ProgramStore.CapacityLease airLifetime;
    private long count,operationCount,siteCount,totalQueries,conditionalRuns,conditionalQueries;
    private volatile boolean closed,ready;

    private SourceTargetPlan(SourceAdmissionSnapshot admission,SnapshotProgram program,DependencyResult executable,AnalysisResources.Reservation capacity){this.admission=admission;this.program=program;this.executable=executable;control=capacity;}
    public static SourceTargetPlan prepare(SourceCorrelationSnapshot correlations,SourceQualifiedSnapshot qualified,
            DependencyResult executable,PageStore pages,AnalysisResources resources) {
        Objects.requireNonNull(correlations);Objects.requireNonNull(qualified);Objects.requireNonNull(executable);Objects.requireNonNull(pages);Objects.requireNonNull(resources);
        var admission=correlations.admission();var program=correlations.program();admission.requireContext(pages,resources);
        if(qualified.admission()!=admission||!executable.publication().equals(program.publicationId()))throw new IllegalArgumentException("foreign target-plan source or executable result");
        var capacity=resources.reserve(AnalysisResources.Pool.RESIDENT,2048,PHASE);SourceTargetPlan result=null;
        try {
            result=new SourceTargetPlan(admission,program,executable,capacity);var registration=capacity;capacity=null;registration.onOwnerClose(result::close);
            result.initialize(correlations,qualified,pages,resources);admission.attach(result,pages,resources);result.open();return result;
        }catch(RuntimeException|Error failure){if(result!=null)result.abort(failure);else if(capacity!=null)try{capacity.close();}catch(RuntimeException|Error cleanup){suppress(failure,cleanup);}throw failure;}
    }
    private SourceAdmissionSnapshot parent(){var value=admission;if(value==null)throw new IllegalStateException("SOURCE target plan expired");return value;}
    private void open(){if(closed||!ready)throw new IllegalStateException("SOURCE target plan expired or incomplete");parent().snapshot();program.admission();}
    private PagedLongArray array(PageStore pages,AnalysisResources resources){return new PagedLongArray(pages,Long.MAX_VALUE,resources,PHASE);}
    private long sf(long row,SourceShape shape,int column){return source.field(row,shape,column);}
    private void initialize(SourceCorrelationSnapshot correlations,SourceQualifiedSnapshot qualified,PageStore pages,AnalysisResources resources) {
        if(closed)throw new IllegalStateException("target plan expired before initialization");source=parent().snapshot();directory=program.operationDirectory().orElseThrow();
        var budget=program.executionBudget();if(budget.isPresent()){airLifetime=budget.orElseThrow().reserve(ProgramStore.CapacityPool.RESIDENT,512,ProgramStore.ExecutionPhase.DEMAND);airLifetime.onOwnerClose(this::close);}
        rows=array(pages,resources);operations=array(pages,resources);sites=array(pages,resources);queryRanges=array(pages,resources);requested=array(pages,resources);
        try(var heads=array(pages,resources);var next=array(pages,resources);var queries=new SourceAdmissionDirectory(pages,resources)) {
            for(int s=0,n=executable.sites().size();s<n;s++) {
                resources.work(1,PHASE);int op=directory.ordinal(executable.sites().get(s).operation());if(op<0)throw new IllegalArgumentException("foreign executable site operation");
                long at=op*2L,last=heads.get(at+1),id=s+1L;if(last==0)heads.set(at,id);else next.set(last,id);heads.set(at+1,id);
            }
            indexQueries(queries,resources);
            for(long u=0,n=parent().unitCount();u<n;u++) {
                long unit=parent().unit(u),scope=sf(unit,SourceShape.UNIT_EVIDENCE,0);boolean available=source.bool(sf(unit,SourceShape.UNIT_EVIDENCE,1));
                for(long o=0,size=qualified.count(u,SourceQualifiedSnapshot.Kind.OCCURRENCE);o<size;o++) {
                    resources.work(1,PHASE);long occurrence=qualified.reference(u,o,SourceQualifiedSnapshot.Kind.OCCURRENCE);if(!source.matches(sf(occurrence,SourceShape.OCCURRENCE,3),"PROGRAM"))continue;
                    long at=count*WIDTH;rows.set(at,u+1);rows.set(at+1,o);rows.set(at+2,occurrence);rows.set(at+3,operationCount);rows.set(at+5,siteCount);
                    for(long i=0,c=correlations.operationCount(u,o);i<c;i++){int op=directory.ordinal(correlations.operation(u,o,i));if(op<0)throw new IllegalStateException("admitted correlation operation disappeared");addOperation(op,heads,next,resources);}
                    rows.set(at+4,operationCount-rows.get(at+3));rows.set(at+6,siteCount-rows.get(at+5));
                    long quals=sf(occurrence,SourceShape.OCCURRENCE,9);boolean qualifiedControl=available&&source.size(quals,SourceShape.TEXT)!=0,controlOpen=qualified.status(u,o,SourceQualifiedSnapshot.Kind.OCCURRENCE)==SourceQualifiedDependencyResult.Status.POSSIBLE_UNDER_UNKNOWN_CONTROL;
                    int kind=source.matches(sf(occurrence,SourceShape.OCCURRENCE,5),"COMPUTED")?COMPUTED:source.matches(sf(occurrence,SourceShape.OCCURRENCE,5),"LITERAL")?LITERAL:UNAVAILABLE;
                    boolean request=kind==COMPUTED&&qualifiedControl&&(controlOpen||rows.get(at+6)==0||openExecutable(at,resources));
                    rows.set(at+7,kind|(qualifiedControl?QUALIFIED:0)|(controlOpen?CONTROL_OPEN:0)|(request?REQUEST:0));
                    long statement=sf(sf(occurrence,SourceShape.OCCURRENCE,0),SourceShape.STATEMENT_ID,1),q=queries.find(scope,1,statement,0);
                    if(q!=0){long ordinal=queries.ordinal(q);rows.set(at+8,ordinal+1);if(request)request(u,ordinal);}
                    count=Math.incrementExact(count);
                }
            }
            appendStandalone(correlations,heads,next,resources);
        }
        parent().snapshot();program.admission();if(closed)throw new IllegalStateException("target plan expired before completion");ready=true;
    }
    private void indexQueries(SourceAdmissionDirectory index,AnalysisResources resources) {
        for(long u=0,n=parent().unitCount();u<n;u++) {
            resources.work(1,PHASE);long unit=parent().unit(u),scope=sf(unit,SourceShape.UNIT_EVIDENCE,0),nominal=sf(unit,SourceShape.UNIT_EVIDENCE,12),size=0;
            queryRanges.set(u*2,totalQueries);
            if(nominal!=0) {
                long facts=sf(nominal,SourceShape.NOMINAL_VALUE_EVIDENCE,0),queries=sf(facts,SourceShape.NOMINAL_VALUES,4);size=source.size(queries,SourceShape.QUERY);
                for(long q=0;q<size;q++){resources.work(1,PHASE);long query=source.element(queries,SourceShape.QUERY,q);index.intern(scope,1,sf(query,SourceShape.QUERY,0),0,query,q);}
            }queryRanges.set(u*2+1,size);totalQueries=Math.addExact(totalQueries,size);
        }
    }
    private void request(long unit,long query) {
        long key=Math.addExact(queryRanges.get(unit*2),query);
        if(requested.get(key)==0){requested.set(key,1);conditionalQueries=Math.incrementExact(conditionalQueries);if(queryRanges.get(unit*2+1)>0&&requested.get(totalQueries+unit)==0){requested.set(totalQueries+unit,1);conditionalRuns=Math.incrementExact(conditionalRuns);}}
    }
    private void addOperation(int op,PagedLongArray heads,PagedLongArray next,AnalysisResources resources) {
        operations.set(operationCount++,op);
        for(long s=heads.get(op*2L);s!=0;s=next.get(s)){resources.work(1,PHASE);sites.set(siteCount++,s-1);}
    }
    private boolean openExecutable(long at,AnalysisResources resources) {
        for(long i=0,n=rows.get(at+6);i<n;i++) {
            resources.work(1,PHASE);
            var site=executable.sites().get(Math.toIntExact(sites.get(rows.get(at+5)+i)));
            if(site.reachability()!=DependencySiteFact.Reachability.UNREACHABLE_IN_MODEL&&!Boolean.FALSE.equals(site.modelValueRemainder()))return true;
        }return false;
    }
    private void appendStandalone(SourceCorrelationSnapshot correlations,PagedLongArray heads,PagedLongArray next,AnalysisResources resources) {
        var air=program.admission().snapshot();long ordinal=0;char[] text=new char[16];
        try(var units=air.elements(air.field(air.root(),air.shape(air.root()),4),UNIT)) {
            while(units.advance())try(var sequences=air.elements(air.field(units.value(),UNIT,5),SEQUENCE)) {
                while(sequences.advance()) {
                    resources.work(1,PHASE);long sequence=sequences.value(),instructions=air.field(sequence,SEQUENCE,1),operation=air.field(sequence,SEQUENCE,2);ordinal=Math.addExact(ordinal,air.size(instructions));
                    if(air.shape(operation)==OPERATIONS_INVOKE) {
                        long target=air.field(operation,OPERATIONS_INVOKE,2);var shape=air.shape(target);
                        boolean literal=shape==INTERACTIONS_LITERAL_TARGET,computed=shape==INTERACTIONS_COMPUTED_TARGET;
                        if((literal||computed)&&matches(air,air.field(target,shape,0),"program",text)&&(matches(air,air.field(target,shape,1),"cobol.program",text)||matches(air,air.field(target,shape,1),"cics.program",text))) {
                            int op=Math.toIntExact(ordinal);if(!correlations.mapped(directory.identity(op))) {
                                long at=count*WIDTH;rows.set(at,0);rows.set(at+1,op);rows.set(at+2,operation);rows.set(at+3,operationCount);rows.set(at+5,siteCount);addOperation(op,heads,next,resources);
                                rows.set(at+4,1);rows.set(at+6,siteCount-rows.get(at+5));rows.set(at+7,literal?LITERAL:COMPUTED);count=Math.incrementExact(count);
                            }
                        }
                    }ordinal=Math.incrementExact(ordinal);
                }
            }
        }if(ordinal!=directory.size())throw new IllegalStateException("native operation directory changed during target planning");
    }
    private static boolean matches(AirSnapshot air,long reference,String value,char[] buffer){if(air.characterCount(reference)!=value.length())return false;air.readCharacters(reference,0,buffer,0,value.length());for(int i=0;i<value.length();i++)if(buffer[i]!=value.charAt(i))return false;return true;}
    private long row(long item){if(item<0||item>=count)throw new IndexOutOfBoundsException("target-plan occurrence ordinal");return Math.multiplyExact(item,WIDTH);}
    private long word(long item,int column){open();try{return rows.get(row(item)+column);}catch(RuntimeException|Error failure){abort(failure);throw failure;}}
    public SourceAdmissionSnapshot admission(){open();return parent();}
    public SnapshotProgram program(){open();return program;}
    DependencyResult executable(){open();return executable;}
    public long count(){open();return count;}
    public long sourceUnit(long item){return word(item,0)-1;}
    public long sourceOrdinal(long item){long row=word(item,0);if(row==0)return -1;return word(item,1);}
    /** SOURCE occurrence handle, or zero for a standalone executable occurrence. */
    public long sourceOccurrence(long item){return word(item,0)==0?0:word(item,2);}
    /** Native AIR Invoke handle only for standalone executable occurrences. */
    public long standaloneInvoke(long item){return word(item,0)==0?word(item,2):0;}
    public String targetKind(long item){return switch((int)word(item,7)&3){case COMPUTED->"COMPUTED";case LITERAL->"LITERAL";default->"UNAVAILABLE";};}
    public boolean qualified(long item){return (word(item,7)&QUALIFIED)!=0;}
    public boolean sourceControlRemainder(long item){return (word(item,7)&CONTROL_OPEN)!=0;}
    public boolean requiresNominal(long item){return (word(item,7)&REQUEST)!=0;}
    public long queryOrdinal(long item){return word(item,8)-1;}
    public long operationCount(long item){return word(item,4);}
    public OperationId operation(long item,long ordinal){open();try{long row=row(item);if(ordinal<0||ordinal>=rows.get(row+4))throw new IndexOutOfBoundsException("target-plan operation ordinal");return directory.identity(Math.toIntExact(operations.get(rows.get(row+3)+ordinal)));}catch(RuntimeException|Error failure){abort(failure);throw failure;}}
    public long siteCount(long item){return word(item,6);}
    public DependencySiteFact site(long item,long ordinal){open();try{long row=row(item);if(ordinal<0||ordinal>=rows.get(row+6))throw new IndexOutOfBoundsException("target-plan site ordinal");return executable.sites().get(Math.toIntExact(sites.get(rows.get(row+5)+ordinal)));}catch(RuntimeException|Error failure){abort(failure);throw failure;}}
    public long queryCount(long unit){open();try{if(unit<0||unit>=parent().unitCount())throw new IndexOutOfBoundsException("target-plan source unit ordinal");return queryRanges.get(unit*2+1);}catch(RuntimeException|Error failure){abort(failure);throw failure;}}
    public boolean requestsQuery(long unit,long query){open();try{if(query<0||query>=queryCount(unit))throw new IndexOutOfBoundsException("target-plan nominal query ordinal");return requested.get(queryRanges.get(unit*2)+query)!=0;}catch(RuntimeException|Error failure){abort(failure);throw failure;}}
    public long conditionalRuns(){open();return conditionalRuns;}
    public boolean requestsAnyQuery(long unit){queryCount(unit);return requested.get(totalQueries+unit)!=0;}
    public long conditionalQueries(){open();return conditionalQueries;}
    private static void suppress(Throwable primary,Throwable cleanup){if(primary!=cleanup)primary.addSuppressed(cleanup);}
    private void abort(Throwable primary){try{close();}catch(RuntimeException|Error cleanup){suppress(primary,cleanup);}}
    @Override public void close() {
        AutoCloseable[] owned;
        synchronized(this){if(closed)return;closed=true;ready=false;owned=new AutoCloseable[]{requested,queryRanges,sites,operations,rows,airLifetime,control};requested=queryRanges=sites=operations=rows=null;airLifetime=null;control=null;source=null;admission=null;program=null;directory=null;executable=null;}
        var parent=owner;if(parent!=null)parent.release(this);Throwable failure=null;
        for(var value:owned)if(value!=null)try{value.close();}catch(Exception|Error cleanup){if(failure==null)failure=cleanup;else suppress(failure,cleanup);}
        if(failure instanceof RuntimeException error)throw error;if(failure instanceof Error error)throw error;if(failure!=null)throw new IllegalStateException("target-plan cleanup",failure);
    }
}
