package io.github.gustavo2358.analysis.dependencies;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.analysis.dependencies.source.*;
import io.github.gustavo2358.analysis.solver.*;
import io.github.gustavo2358.analysis.structure.ProgramStore;
import java.util.List;
import java.util.Objects;
import static io.github.gustavo2358.air.model.AirShape.*;

/**
 * Complete SOURCE/AIR correlation admission against two legitimate immutable
 * owners. All variable indexes, ancestry work and retained mappings are paged.
 * No source names, lines or sequence order infer executable authority. A query
 * reconstructs only its requested nominal OperationId, never an Operation body.
 * SOURCE, AIR and page backends remain borrowed; expiry of either owner retires
 * this derived result. This is not conditional nominal value evaluation.
 */
public final class SourceCorrelationSnapshot extends SourceAdmissionSnapshot.Dependent {
    private static final AnalysisResources.Phase PHASE=AnalysisResources.Phase.INDEX;
    private static final int SOURCE_WIDTH=4,OP_WIDTH=4,ORIGIN_WIDTH=3;
    private volatile SourceAdmissionSnapshot admission;
    private volatile SnapshotProgram program;
    private SourceSnapshot source;
    private AirSnapshot air;
    private ProgramStore.OperationDirectory operations;
    private AnalysisResources.Reservation control;
    private ProgramStore.CapacityLease airLifetime;
    private PagedLongArray units,occurrences,code,ordered;
    private PagedLongArray origins,queue,mappings;
    private PagedLongIndex sourceIndex,originIndex,mappingIndex;
    private SourceAdmissionDirectory duplicates,ancestry;
    private QualifiedSourceDependencies.StatementId sourceProbe;
    private OriginId originProbe;
    private char[] left,right;
    private long occurrenceCount,originCount,mappingCount,orderedCount,epoch;
    private volatile boolean closed,ready;

    private SourceCorrelationSnapshot(SourceAdmissionSnapshot admission,SnapshotProgram program,AnalysisResources.Reservation capacity){this.admission=admission;this.program=program;control=capacity;}
    public static SourceCorrelationSnapshot prepare(SourceAdmissionSnapshot admission,SnapshotProgram program,
            List<DependencyInput.StatementCorrelation> correlations,PageStore pages,AnalysisResources resources) {
        Objects.requireNonNull(admission);Objects.requireNonNull(program);Objects.requireNonNull(correlations);
        Objects.requireNonNull(pages);Objects.requireNonNull(resources);admission.requireContext(pages,resources);program.admission();
        var capacity=resources.reserve(AnalysisResources.Pool.RESIDENT,2048,PHASE);SourceCorrelationSnapshot result=null;
        try {
            result=new SourceCorrelationSnapshot(admission,program,capacity);var registration=capacity;capacity=null;registration.onOwnerClose(result::close);
            result.initialize(correlations,pages,resources);admission.attach(result,pages,resources);result.open();return result;
        }catch(RuntimeException|Error failure){if(result!=null)result.abort(failure);else if(capacity!=null)try{capacity.close();}catch(RuntimeException|Error cleanup){suppress(failure,cleanup);}throw failure;}
    }
    private PagedLongArray array(PageStore pages,AnalysisResources resources){return new PagedLongArray(pages,Long.MAX_VALUE,resources,PHASE);}
    private void initialize(List<DependencyInput.StatementCorrelation> correlations,PageStore pages,AnalysisResources resources) {
        if(closed)throw new IllegalStateException("SOURCE/AIR correlation expired before initialization");
        source=admission.snapshot();air=program.admission().snapshot();left=new char[256];right=new char[256];
        // Also bind AIR expiry when SOURCE was admitted under independent resources.
        var budget=program.executionBudget();if(budget.isPresent()){airLifetime=budget.orElseThrow().reserve(ProgramStore.CapacityPool.RESIDENT,512,ProgramStore.ExecutionPhase.INDEX);airLifetime.onOwnerClose(this::close);}
        long publications=sf(source.root(),SourceShape.ROOT,4);
        if(ss(publications,SourceShape.AIR_CORRELATION)!=1||!source.matches(sf(se(publications,SourceShape.AIR_CORRELATION,0),SourceShape.AIR_CORRELATION,0),program.publicationId().localId()))
            throw new IllegalArgumentException("AIR publication mismatch");
        units=array(pages,resources);occurrences=array(pages,resources);ordered=array(pages,resources);
        sourceIndex=new PagedLongIndex(pages,resources,PHASE,this::compareStatement);
        for(long u=0,n=admission.unitCount();u<n;u++) {
            long list=sf(admission.unit(u),SourceShape.UNIT_EVIDENCE,3),count=ss(list,SourceShape.OCCURRENCE);
            units.set(Math.multiplyExact(u,2),occurrenceCount);units.set(Math.addExact(Math.multiplyExact(u,2),1),count);
            for(long i=0;i<count;i++) {
                long ref=se(list,SourceShape.OCCURRENCE,i),id=sf(ref,SourceShape.OCCURRENCE,0),at=Math.multiplyExact(occurrenceCount,SOURCE_WIDTH);
                occurrences.set(at,ref);occurrences.set(at+1,id);
                if(sourceIndex.intern(id,Math.incrementExact(occurrenceCount))!=occurrenceCount+1)throw new IllegalArgumentException("duplicate admitted SOURCE occurrence");
                occurrenceCount=Math.incrementExact(occurrenceCount);
            }
        }
        operations=program.operationDirectory().orElseThrow();code=array(pages,resources);
        if(!correlations.isEmpty()) {
            indexCode();origins=array(pages,resources);queue=array(pages,resources);mappings=array(pages,resources);
            originIndex=new PagedLongIndex(pages,resources,PHASE,this::compareOrigin);
            duplicates=new SourceAdmissionDirectory(pages,resources);ancestry=new SourceAdmissionDirectory(pages,resources);
            mappingIndex=new PagedLongIndex(pages,resources,PHASE,this::compareMapping);
            indexOrigins();
            for(var link:correlations){resources.work(1,PHASE);correlate(Objects.requireNonNull(link));}
            freezeMappings();
        }
        retirePreparation();admission.snapshot();program.admission();if(closed)throw new IllegalStateException("SOURCE/AIR correlation expired before completion");ready=true;
    }
    private long sf(long row,SourceShape shape,int slot){return source.field(row,shape,slot);}
    private long ss(long list,SourceShape shape){return list==0?0:source.size(list,shape);}
    private long se(long list,SourceShape shape,long ordinal){return source.element(list,shape,ordinal);}
    private long af(long row,int slot){return air.field(row,air.shape(row),slot);}
    private void indexCode() {
        long ordinal=0;
        try(var us=air.elements(af(air.root(),4),UNIT)) {
            while(us.advance())try(var sequences=air.elements(af(us.value(),5),SEQUENCE)) {
                while(sequences.advance()) {
                    long sequence=sequences.value(),label=af(sequence,0);
                    try(var instructions=air.elements(af(sequence,1),INSTRUCTION)){while(instructions.advance())recordCode(ordinal++,instructions.value(),label);}
                    recordCode(ordinal++,af(sequence,2),label);
                }
            }
        }
        if(ordinal!=operations.size())throw new IllegalStateException("admitted AIR operation directory mismatch");
    }
    private void recordCode(long ordinal,long operation,long label){long at=Math.multiplyExact(ordinal,OP_WIDTH);code.set(at,operation);code.set(at+1,label);}
    private void indexOrigins() {
        try(var rows=air.elements(af(air.root(),8),ORIGINS_ORIGIN)) {
            while(rows.advance()) {
                long row=rows.value(),id=af(row,0),ordinal=Math.incrementExact(originCount),at=Math.multiplyExact(ordinal,ORIGIN_WIDTH);
                origins.set(at,id);origins.set(at+1,row);
                if(originIndex.intern(id,ordinal)!=ordinal)throw new IllegalArgumentException("duplicate admitted AIR origin");originCount=ordinal;
            }
        }
    }
    private int sourceText(long a,long b) {
        long na=source.characterCount(a),nb=source.characterCount(b),n=Math.min(na,nb);
        for(long at=0;at<n;){int count=(int)Math.min(left.length,n-at);source.readCharacters(a,at,left,0,count);source.readCharacters(b,at,right,0,count);for(int i=0;i<count;i++){int c=Character.compare(left[i],right[i]);if(c!=0)return c;}at+=count;}return Long.compare(na,nb);
    }
    private int sourceText(long a,String b) {
        long na=source.characterCount(a),n=Math.min(na,b.length());
        for(long at=0;at<n;){int count=(int)Math.min(left.length,n-at);source.readCharacters(a,at,left,0,count);for(int i=0;i<count;i++){int c=Character.compare(left[i],b.charAt(Math.toIntExact(at+i)));if(c!=0)return c;}at+=count;}return Long.compare(na,b.length());
    }
    private int compareStatement(long a,long b) {
        if(a==b)return 0;if(a==0)return -compareStatement(b,0);
        long ua=sf(a,SourceShape.STATEMENT_ID,0);long ub=b==0?0:sf(b,SourceShape.STATEMENT_ID,0);var probe=sourceProbe;
        int c=b==0?sourceText(sf(ua,SourceShape.UNIT_ID,0),probe.unit().compilationUnitId()):sourceText(sf(ua,SourceShape.UNIT_ID,0),sf(ub,SourceShape.UNIT_ID,0));if(c!=0)return c;
        long pa=sf(ua,SourceShape.UNIT_ID,1),pb=b==0?0:sf(ub,SourceShape.UNIT_ID,1),na=ss(pa,SourceShape.INTEGER),nb=b==0?probe.unit().structuralPath().size():ss(pb,SourceShape.INTEGER);
        for(long i=0,n=Math.min(na,nb);i<n;i++){int ia=source.integer(se(pa,SourceShape.INTEGER,i)),ib=b==0?probe.unit().structuralPath().get(Math.toIntExact(i)):source.integer(se(pb,SourceShape.INTEGER,i));c=Integer.compare(ia,ib);if(c!=0)return c;}c=Long.compare(na,nb);if(c!=0)return c;
        c=b==0?sourceText(sf(ua,SourceShape.UNIT_ID,2),probe.unit().canonicalProgramName()):sourceText(sf(ua,SourceShape.UNIT_ID,2),sf(ub,SourceShape.UNIT_ID,2));if(c!=0)return c;
        return b==0?sourceText(sf(a,SourceShape.STATEMENT_ID,1),probe.handle()):sourceText(sf(a,SourceShape.STATEMENT_ID,1),sf(b,SourceShape.STATEMENT_ID,1));
    }
    private int airText(long a,long b) {
        long na=air.characterCount(a),nb=air.characterCount(b),n=Math.min(na,nb);
        for(long at=0;at<n;){int count=(int)Math.min(left.length,n-at);air.readCharacters(a,at,left,0,count);air.readCharacters(b,at,right,0,count);for(int i=0;i<count;i++){int c=Character.compare(left[i],right[i]);if(c!=0)return c;}at+=count;}return Long.compare(na,nb);
    }
    private int airText(long a,String b) {
        long na=air.characterCount(a),n=Math.min(na,b.length());
        for(long at=0;at<n;){int count=(int)Math.min(left.length,n-at);air.readCharacters(a,at,left,0,count);for(int i=0;i<count;i++){int c=Character.compare(left[i],b.charAt(Math.toIntExact(at+i)));if(c!=0)return c;}at+=count;}return Long.compare(na,b.length());
    }
    private boolean matches(long text,String value){return airText(text,value)==0;}
    private int compareOrigin(long a,long b) {
        if(a==b)return 0;if(a==0)return -compareOrigin(b,0);
        int c=b==0?airText(af(af(a,0),0),originProbe.publication().localId()):airText(af(af(a,0),0),af(af(b,0),0));
        return c!=0?c:b==0?airText(af(a,1),originProbe.localId()):airText(af(a,1),af(b,1));
    }
    private boolean labelMatches(long label,LabelId expected){long unit=af(label,0);return matches(af(af(unit,0),0),expected.publication().localId())&&matches(af(unit,1),expected.unit().localId())&&matches(af(label,1),expected.localId());}
    private void correlate(DependencyInput.StatementCorrelation link) {
        long occurrence;
        sourceProbe=link.source();try{occurrence=sourceIndex.find(0);}finally{sourceProbe=null;}
        int operation=operations.ordinal(link.operation());
        if(occurrence==0||operation<0)throw new IllegalArgumentException("source/AIR correlation ownership");
        long row=Math.multiplyExact(occurrence-1,SOURCE_WIDTH),ref=occurrences.get(row),at=Math.multiplyExact(operation,OP_WIDTH);
        if(!source.matches(sf(ref,SourceShape.OCCURRENCE,3),"PROGRAM")||!labelMatches(code.get(at+1),link.label()))throw new IllegalArgumentException("source/AIR correlation ownership");
        long previous=code.get(at+2);if(previous!=0&&previous!=occurrence)throw new IllegalArgumentException("conflicting source/AIR correlation");code.set(at+2,occurrence);
        long expected;originProbe=link.origin();try{expected=originIndex.find(0);}finally{originProbe=null;}
        long actual=originIndex.find(af(af(code.get(at),0),1));
        if(expected==0||actual==0||!derivedFrom(actual,expected))throw new IllegalArgumentException("source/AIR origin correlation");
        if(duplicates.find(occurrence,1,operation,expected)!=0)throw new IllegalArgumentException("duplicate source/AIR correlation");duplicates.intern(occurrence,1,operation,expected,ref,0);
        long op=code.get(at);if(air.shape(op)!=OPERATIONS_INVOKE)return;
        long target=af(op,2);var shape=air.shape(target);boolean literal=shape==INTERACTIONS_LITERAL_TARGET;
        if((!literal&&shape!=INTERACTIONS_COMPUTED_TARGET)||!matches(af(target,0),"program")||(!matches(af(target,1),"cics.program")&&!matches(af(target,1),"cobol.program")))throw new IllegalArgumentException("source/AIR target category");
        if(literal!=source.matches(sf(ref,SourceShape.OCCURRENCE,5),"LITERAL"))throw new IllegalArgumentException("source/AIR target kind");
        if(matches(af(target,1),"cics.program")!=source.matches(sf(ref,SourceShape.OCCURRENCE,1),"CICS"))throw new IllegalArgumentException("source/AIR technology");
        if(literal) {
            long values=sf(ref,SourceShape.OCCURRENCE,7);boolean equal=false;
            for(long i=0,n=ss(values,SourceShape.VALUE);i<n;i++)if(equalText(sf(se(values,SourceShape.VALUE,i),SourceShape.VALUE,1),af(target,2)))equal=true;
            if(!equal)throw new IllegalArgumentException("source/AIR literal disagreement");
        }
        long mapping=Math.incrementExact(mappingCount),base=Math.multiplyExact(mapping,2);mappings.set(base,occurrence);mappings.set(base+1,operation);
        if(mappingIndex.intern(mapping,mapping)==mapping)mappingCount=mapping;code.set(at+3,1);
    }
    private boolean equalText(long a,long b) {
        long n=source.characterCount(a);if(n!=air.characterCount(b))return false;
        for(long at=0;at<n;){int count=(int)Math.min(left.length,n-at);source.readCharacters(a,at,left,0,count);air.readCharacters(b,at,right,0,count);for(int i=0;i<count;i++)if(left[i]!=right[i])return false;at+=count;}return true;
    }
    private boolean derivedFrom(long actual,long expected) {
        long memo=ancestry.find(0,1,actual,expected);if(memo!=0)return ancestry.state(memo,0)==2;
        epoch=Math.incrementExact(epoch);long head=0,tail=1;queue.set(0,actual);origins.set(Math.addExact(Math.multiplyExact(actual,ORIGIN_WIDTH),2),epoch);boolean found=false;
        while(head<tail) {
            long current=queue.get(head++);if(current==expected){found=true;break;}
            long row=origins.get(Math.addExact(Math.multiplyExact(current,ORIGIN_WIDTH),1));if(air.shape(row)!=ORIGINS_DERIVED)continue;
            try(var inputs=air.elements(af(row,1),IDS_ORIGIN_ID)) {
                while(inputs.advance()) {
                    long next=originIndex.find(inputs.value());if(next==0)throw new IllegalStateException("admitted AIR origin input absent");long visited=Math.addExact(Math.multiplyExact(next,ORIGIN_WIDTH),2);
                    if(origins.get(visited)!=epoch){origins.set(visited,epoch);queue.set(tail,next);tail=Math.incrementExact(tail);}
                }
            }
        }
        memo=ancestry.intern(0,1,actual,expected,actual,0);ancestry.state(memo,0,found?2:1);return found;
    }
    private int compareMapping(long a,long b) {
        long pa=Math.multiplyExact(a,2),pb=Math.multiplyExact(b,2);int c=Long.compare(mappings.get(pa),mappings.get(pb));if(c!=0)return c;
        long oa=mappings.get(pa+1),ob=mappings.get(pb+1);if(oa==ob)return 0;
        long ia=af(af(code.get(Math.multiplyExact(oa,OP_WIDTH)),0),0),ib=af(af(code.get(Math.multiplyExact(ob,OP_WIDTH)),0),0);
        c=airText(af(ia,1),af(ib,1));
        // The resident comparator specifies only localId. Resolve a cross-unit
        // tie by complete typed identity, independent of physical inventory order.
        if(c!=0)return c;c=airText(af(af(ia,0),1),af(af(ib,0),1));
        return c!=0?c:airText(af(af(af(ia,0),0),0),af(af(af(ib,0),0),0));
    }
    private void freezeMappings() {
        try(var rows=mappingIndex.cursor()) {
            while(rows.advance()) {
                long at=Math.multiplyExact(rows.value(),2),sourceRow=mappings.get(at),row=Math.multiplyExact(sourceRow-1,SOURCE_WIDTH),count=occurrences.get(row+3);
                if(count==0)occurrences.set(row+2,orderedCount);occurrences.set(row+3,Math.incrementExact(count));ordered.set(orderedCount,mappings.get(at+1));orderedCount=Math.incrementExact(orderedCount);
            }
        }
    }
    private void retirePreparation() {
        Throwable failure=null;
        for(AutoCloseable value:new AutoCloseable[]{mappingIndex,mappings,ancestry,duplicates,originIndex,queue,origins,sourceIndex})if(value!=null)try{value.close();}catch(Exception|Error cleanup){if(failure==null)failure=cleanup;else suppress(failure,cleanup);}
        mappingIndex=sourceIndex=originIndex=null;mappings=queue=origins=null;ancestry=duplicates=null;sourceProbe=null;originProbe=null;
        if(failure instanceof RuntimeException error)throw error;if(failure instanceof Error error)throw error;if(failure!=null)throw new IllegalStateException("SOURCE correlation preparation cleanup",failure);
    }
    private void open(){if(closed||!ready)throw new IllegalStateException("SOURCE/AIR correlation expired or incomplete");admission.snapshot();program.admission();}
    public SourceAdmissionSnapshot admission(){open();return admission;}
    public SnapshotProgram program(){open();return program;}
    private long row(long unit,long occurrence){if(unit<0||unit>=admission.unitCount())throw new IndexOutOfBoundsException("SOURCE correlation unit ordinal");long at=Math.multiplyExact(unit,2),count=units.get(at+1);if(occurrence<0||occurrence>=count)throw new IndexOutOfBoundsException("SOURCE correlation occurrence ordinal");return Math.multiplyExact(Math.addExact(units.get(at),occurrence),SOURCE_WIDTH);}
    public long operationCount(long unit,long occurrence){open();try{return occurrences.get(row(unit,occurrence)+3);}catch(RuntimeException|Error failure){abort(failure);throw failure;}}
    public OperationId operation(long unit,long occurrence,long ordinal) {
        open();try{long row=row(unit,occurrence),count=occurrences.get(row+3);if(ordinal<0||ordinal>=count)throw new IndexOutOfBoundsException("SOURCE correlation operation ordinal");long op=ordered.get(Math.addExact(occurrences.get(row+2),ordinal));return operations.identity(Math.toIntExact(op));}
        catch(RuntimeException|Error failure){abort(failure);throw failure;}
    }
    public boolean mapped(OperationId operation){open();try{int ordinal=operations.ordinal(Objects.requireNonNull(operation));return ordinal>=0&&code.get(Math.addExact(Math.multiplyExact(ordinal,OP_WIDTH),3))!=0;}catch(RuntimeException|Error failure){abort(failure);throw failure;}}
    private static void suppress(Throwable primary,Throwable cleanup){if(primary!=cleanup)primary.addSuppressed(cleanup);}
    private void abort(Throwable primary){try{close();}catch(RuntimeException|Error cleanup){suppress(primary,cleanup);}}
    @Override public void close() {
        AutoCloseable[] owned;
        synchronized(this){closed=true;ready=false;owned=new AutoCloseable[]{mappingIndex,mappings,ancestry,duplicates,originIndex,queue,origins,sourceIndex,ordered,code,occurrences,units,airLifetime,control};mappingIndex=sourceIndex=originIndex=null;mappings=queue=origins=ordered=code=occurrences=units=null;ancestry=duplicates=null;airLifetime=null;control=null;admission=null;program=null;source=null;air=null;operations=null;sourceProbe=null;originProbe=null;left=right=null;}
        var parent=owner;if(parent!=null)parent.release(this);Throwable failure=null;
        for(var value:owned)if(value!=null)try{value.close();}catch(Exception|Error cleanup){if(failure==null)failure=cleanup;else suppress(failure,cleanup);}
        if(failure instanceof RuntimeException error)throw error;if(failure instanceof Error error)throw error;if(failure!=null)throw new IllegalStateException("SOURCE correlation cleanup",failure);
    }
}
