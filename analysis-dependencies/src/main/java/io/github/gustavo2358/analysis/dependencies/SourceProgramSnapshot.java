package io.github.gustavo2358.analysis.dependencies;

import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.analysis.dependencies.source.*;
import io.github.gustavo2358.analysis.structure.ProgramStore;
import io.github.gustavo2358.analysis.solver.*;
import java.util.Objects;

/**
 * Complete resolved PROGRAM inventory with paged cardinality-dependent state.
 * SOURCE facts are borrowed by canonical handle, executable supports by their
 * existing immutable site ordinal. Only one selected unit's nominal solve lives
 * at a time. No resident SOURCE, candidate or support tree is reconstructed.
 * Interpretation and union laws match TargetResolver; open evidence never
 * replaces known candidates. Native support sorting/writing uses funded scopes
 * and full text blocks through paged directories,not complete support DTOs.
 * Resolve still projects a complete site; this is not bounded fullfact/provider
 * or whole-program qualification. Resident support compatibility stays explicit.
 */
public final class SourceProgramSnapshot extends SourceAdmissionSnapshot.Dependent {
    private static final AnalysisResources.Phase PHASE=AnalysisResources.Phase.SORT;
    private static final int ITEM=6,CANDIDATE=8,SUPPORT=5,CONDITIONAL=4;
    private static final String[] AUTHORITIES={"SOURCE_QUALIFIED","SOURCE_CONTROL_POSSIBLE","EXECUTABLE_FLOW","EXECUTABLE_OCCURRENCE","CONDITIONAL_SOURCE_VALUES"};
    private static final String[] ASSUMPTIONS={"NOMINAL_DECLARATIONS_PRESERVE_MEANING","NO_UNMODELED_STORAGE_INTERFERENCE","UNKNOWN_CONTROL_CAN_COMPLETE","TABLE_INDEX_NOT_REFINED","SYNTHETIC_MODEL_IS_NOT_KILL_PROOF","DECLARATIVE_INITIAL_VALUES_APPLY"};
    private volatile SourceAdmissionSnapshot admission;
    private SourceTargetPlan plan;
    private SourceSnapshot source;
    private SourceNominalCandidates nominal;
    private AnalysisResources resources;
    private PageStore pages;
    private AnalysisResources.Reservation control;
    private ProgramStore.CapacityLease airLifetime;
    private SourceTextDictionary text;
    private PagedLongArray items,candidates,candidateOrder,supports,supportOrder,conditionals,evidence,reasons,globalReasons,metrics,metricOrder;
    private char[] left,right;
    private BorrowedText probe;
    private long candidateRows,candidateCount,supportRows,supportCount,conditionalCount,evidenceCount,reasonCount,globalReasonCount,metricCount,workItems,reused,qualifiedComputed;
    private volatile boolean closed,ready;

    private SourceProgramSnapshot(SourceAdmissionSnapshot admission,SourceTargetPlan plan,AnalysisResources.Reservation capacity){this.admission=admission;this.plan=plan;control=capacity;}
    public static SourceProgramSnapshot prepare(SourceTargetPlan plan,SourceQualifiedSnapshot qualified,PageStore pages,AnalysisResources resources) {
        Objects.requireNonNull(plan);Objects.requireNonNull(qualified);Objects.requireNonNull(pages);Objects.requireNonNull(resources);
        var admission=plan.admission();admission.requireContext(pages,resources);
        if(qualified.admission()!=admission)throw new IllegalArgumentException("foreign prepared SOURCE program facts");
        var capacity=resources.reserve(AnalysisResources.Pool.RESIDENT,4096,PHASE);SourceProgramSnapshot result=null;
        try {
            result=new SourceProgramSnapshot(admission,plan,capacity);var registration=capacity;capacity=null;registration.onOwnerClose(result::close);
            result.initialize(qualified,pages,resources);admission.attach(result,pages,resources);result.open();return result;
        }catch(RuntimeException|Error failure){if(result!=null)result.abort(failure);else if(capacity!=null)try{capacity.close();}catch(RuntimeException|Error cleanup){suppress(failure,cleanup);}throw failure;}
    }
    private SourceAdmissionSnapshot parent(){var value=admission;if(value==null)throw new IllegalStateException("SOURCE programs expired");return value;}
    private void open(){if(closed||!ready)throw new IllegalStateException("SOURCE programs expired or incomplete");try{parent().snapshot();plan.count();}catch(RuntimeException|Error failure){abort(failure);throw failure;}}
    private PagedLongArray array(){return new PagedLongArray(pages,Long.MAX_VALUE,resources,PHASE);}
    private long field(long row,SourceShape shape,int column){return source.field(row,shape,column);}
    private void initialize(SourceQualifiedSnapshot qualified,PageStore pages,AnalysisResources resources) {
        if(closed)throw new IllegalStateException("SOURCE programs expired before initialization");this.pages=pages;this.resources=resources;source=parent().snapshot();left=new char[128];right=new char[128];probe=new BorrowedText();
        var budget=plan.program().executionBudget();if(budget.isPresent()){airLifetime=budget.orElseThrow().reserve(ProgramStore.CapacityPool.RESIDENT,512,ProgramStore.ExecutionPhase.SORT);airLifetime.onOwnerClose(this::close);}
        text=new SourceTextDictionary(pages,resources,PHASE);items=array();candidates=array();candidateOrder=array();supports=array();supportOrder=array();conditionals=array();evidence=array();reasons=array();globalReasons=array();
        try(var global=new PagedLongIndex(pages,resources,PHASE,this::compareText)) {
            for(var reason:plan.executable().analysisReasons()){long id=text.intern(reason);global.intern(id,id);}
            for(long u=0,n=qualified.unitCount();u<n;u++)for(long f=0,size=qualified.count(u,SourceQualifiedSnapshot.Kind.NATIVE_FILE);f<size;f++)if(qualified.remainder(u,f,SourceQualifiedSnapshot.Kind.NATIVE_FILE)){long id=text.intern("SOURCE_NATIVE_FILE_REMAINDER");global.intern(id,id);}
            long previous=-1;
            for(long item=0,n=plan.count();item<n;item++) {
                resources.work(1,PHASE);long unit=plan.sourceUnit(item);
                if(unit!=previous){closeNominal();previous=unit;if(unit>=0&&plan.requestsAnyQuery(unit))nominal=SourceNominalCandidates.prepare(parent(),unit,q->plan.requestsQuery(unit,q),qualified.controlForNominal(),pages,resources);}
                resolve(item,global);
            }
            closeNominal();try(var cursor=global.cursor()){while(cursor.advance())globalReasons.set(globalReasonCount++,cursor.value());}
        }
        prepareMetrics();qualified.admission();plan.count();parent().snapshot();if(closed)throw new IllegalStateException("SOURCE programs expired before completion");ready=true;
    }
    private void prepareMetrics() {
        metrics=array();metricOrder=array();try(var order=new PagedLongIndex(pages,resources,PHASE,this::compareText)) {
            for(var entry:plan.executable().metrics().entrySet()){long id=text.intern(entry.getKey());order.intern(id,id);metrics.set(id,entry.getValue());}
            for(var key:ManagedDependencyResult.SOURCE_METRIC_KEYS){long id=text.intern(key);order.intern(id,id);metrics.set(id,sourceMetric(key));}
            try(var cursor=order.cursor()){while(cursor.advance())metricOrder.set(metricCount++,cursor.value());}
        }
    }
    private long sourceMetric(String key){return switch(key){case "sourceQualifiedResolvedByExistingQuery"->reused;case "qualifiedComputedOccurrences"->qualifiedComputed;case "conditionalSourceValueRuns"->plan.conditionalRuns();case "conditionalSourceValueQueries"->plan.conditionalQueries();case "conditionalSourceWorkItems"->workItems;case "conditionalSourceResourceLimits"->0;case "targetResolutionRequests"->plan.count();default->throw new IllegalArgumentException("unknown source metric");};}
    private void closeNominal(){var value=nominal;nominal=null;if(value!=null){workItems=Math.addExact(workItems,value.workItems());value.close();}}
    private void reason(PagedLongIndex order,PagedLongIndex global,String value){long id=text.intern(value);order.intern(id,id);if(value.startsWith("CONDITIONAL_")||value.equals("SOURCE_CONTROL_UNAVAILABLE"))global.intern(id,id);}
    /** Authority is encoded in insertion order, not sorted or inferred from names. */
    private void addAuthority(long item,int value){long at=item*ITEM+2,packed=items.get(at);for(long rest=packed;rest!=0;rest>>>=3)if((rest&7)==value+1)return;int shift=0;for(long rest=packed;rest!=0;rest>>>=3)shift+=3;items.set(at,packed|((long)value+1)<<shift);}
    private void resolve(long item,PagedLongIndex global) {
        long base=item*ITEM;items.set(base,candidateCount);items.set(base+4,reasonCount);boolean qualified=plan.qualified(item),computed=plan.targetKind(item).equals("COMPUTED"),observed=false,valueOpen=false,interpretationOpen=false,execCandidates=false;
        if(computed&&(qualified||plan.operationCount(item)>0))qualifiedComputed=Math.incrementExact(qualifiedComputed);
        try(var order=new PagedLongIndex(pages,resources,PHASE,this::compareCandidate);var reasonOrder=new PagedLongIndex(pages,resources,PHASE,this::compareText)) {
            if(qualified)addAuthority(item,plan.sourceControlRemainder(item)?1:0);
            if(plan.sourceControlRemainder(item))reason(reasonOrder,global,"SOURCE_CONTROL_UNAVAILABLE");
            for(long s=0,n=plan.siteCount(item);s<n;s++)try(var projection=plan.projectSite(item,s)) {
                resources.work(1,PHASE);var site=projection.fact();for(var r:site.analysisReasons())reason(reasonOrder,global,r);
                if(site.reachability()==DependencySiteFact.Reachability.UNREACHABLE_IN_MODEL)continue;
                observed=true;addAuthority(item,site.reachability()==DependencySiteFact.Reachability.REACHABLE?2:3);valueOpen|=!Boolean.FALSE.equals(site.modelValueRemainder())||site.sourceValueRemainder();interpretationOpen|=site.interpretationUnknownRemainder();
                for(int c=0,size=site.candidates().size();c<size;c++) {
                    resources.work(1,PHASE);var candidate=site.candidates().get(c);execCandidates|=!candidate.supports().isEmpty();long row=add(order,text.intern(candidate.referenceName()),text.intern(candidate.rawValue()));
                    for(int p=0,length=candidate.supports().size();p<length;p++)addSupport(row,item,s,c,p);
                }
            }
            if(qualified&&computed&&plan.sourceUnit(item)>=0&&execCandidates)reused=Math.incrementExact(reused);
            long occurrence=plan.sourceOccurrence(item);boolean cics=qualified&&source.matches(field(occurrence,SourceShape.OCCURRENCE,1),"CICS"),supported=qualified&&source.matches(field(occurrence,SourceShape.OCCURRENCE,4),cics?CicsNameInterpreter.PROFILE:CallNameInterpreter.PROFILE);
            if(qualified&&plan.targetKind(item).equals("LITERAL")) {
                long literals=field(occurrence,SourceShape.OCCURRENCE,7);
                for(long i=0,n=source.size(literals,SourceShape.VALUE);i<n;i++) {
                    resources.work(1,PHASE);long raw=field(source.element(literals,SourceShape.VALUE,i),SourceShape.VALUE,1);probe.source(raw);
                    int length=supported?interpret(cics,false):-1;interpretationOpen|=length<0||!cics;
                    if(length>=0)addInterpreted(order,length);
                }
            }
            long query=plan.queryOrdinal(item);
            if(plan.requiresNominal(item)&&nominal!=null&&query>=0&&nominal.candidateCount(query)>0) {
                valueOpen=true;addAuthority(item,4);reason(reasonOrder,global,"CONDITIONAL_NOMINAL_VALUE_EVIDENCE");
                for(long c=0,n=nominal.candidateCount(query);c<n;c++) {
                    resources.work(1,PHASE);probe.nominal(query,c);int length=supported?interpret(cics,true):-1;interpretationOpen|=length<0||!cics;
                    if(length>=0){long row=addInterpreted(order,length);candidates.set(row*CANDIDATE+5,copyConditional(query,c)+1);}
                }
            }
            if(qualified&&!observed&&computed){valueOpen=true;reason(reasonOrder,global,plan.siteCount(item)==0?"SOURCE_TARGET_WITHOUT_EXECUTABLE_QUERY":"SOURCE_TARGET_WITHOUT_REACHABLE_VALUE");}
            if(plan.targetKind(item).equals("UNAVAILABLE")){valueOpen=true;reason(reasonOrder,global,"SOURCE_TARGET_UNAVAILABLE");}
            if(qualified&&!observed)valueOpen|=source.bool(field(occurrence,SourceShape.OCCURRENCE,8));
            items.set(base+3,(valueOpen?1:0)|(interpretationOpen?2:0));
            try(var cursor=order.cursor()){while(cursor.advance()){long row=cursor.value();candidateOrder.set(candidateCount++,row);orderSupports(row);}}
            items.set(base+1,candidateCount-items.get(base));
            try(var cursor=reasonOrder.cursor()){while(cursor.advance())reasons.set(reasonCount++,cursor.value());}items.set(base+5,reasonCount-items.get(base+4));
        }probe.clear();
    }
    private long add(PagedLongIndex order,long reference,long raw) {
        long row=++candidateRows,at=row*CANDIDATE;candidates.set(at,reference);candidates.set(at+1,raw);long found=order.intern(row,row);candidates.set(found*CANDIDATE+4,Math.incrementExact(candidates.get(found*CANDIDATE+4)));return found;
    }
    private long addInterpreted(PagedLongIndex order,int length){long raw=text.intern(probe);int old=probe.length;probe.length=length;try{return add(order,text.intern(probe),raw);}finally{probe.length=old;}}
    private void addSupport(long candidate,long item,long site,int value,int support) {
        long row=++supportRows,at=row*SUPPORT;supports.set(at,item);supports.set(at+1,site);supports.set(at+2,value);supports.set(at+3,support);long base=candidate*CANDIDATE,last=candidates.get(base+3);
        if(last==0)candidates.set(base+2,row);else supports.set(last*SUPPORT+4,row);candidates.set(base+3,row);
    }
    private SourceTargetPlan.SupportProjection projectSupportRow(long row){long at=row*SUPPORT;return plan.projectSupport(supports.get(at),supports.get(at+1),Math.toIntExact(supports.get(at+2)),Math.toIntExact(supports.get(at+3)));}
    private SourceTargetPlan.SupportCursor openSupportRow(long row){long at=row*SUPPORT;return plan.openSupport(supports.get(at),supports.get(at+1),Math.toIntExact(supports.get(at+2)),Math.toIntExact(supports.get(at+3)));}
    private void orderSupports(long candidate) {
        long at=candidate*CANDIDATE;candidates.set(at+6,supportCount);
        try(var distinct=new PagedLongIndex(pages,resources,PHASE,this::compareSupportIdentity);var order=new PagedLongIndex(pages,resources,PHASE,this::compareSupportWire)) {
            boolean merged=candidates.get(at+4)>1;
            for(long row=candidates.get(at+2);row!=0;row=supports.get(row*SUPPORT+4)){resources.work(1,PHASE);if(!merged||distinct.intern(row,row)==row)order.intern(row,row);}
            try(var cursor=order.cursor()){while(cursor.advance())supportOrder.set(supportCount++,cursor.value());}
        }candidates.set(at+7,supportCount-candidates.get(at+6));
    }
    private long copyConditional(long query,long candidate) {
        long row=conditionalCount++,at=row*CONDITIONAL,mask=0;
        for(int i=0,n=nominal.assumptionCount(query,candidate);i<n;i++){String assumption=nominal.assumption(query,candidate,i);int found=-1;for(int a=0;a<ASSUMPTIONS.length;a++)if(ASSUMPTIONS[a].equals(assumption)){found=a;break;}if(found<0)throw new IllegalStateException("unsupported nominal assumption");mask|=1L<<found;}
        conditionals.set(at,mask);long unit=parent().unit(nominal.unitOrdinal()),nominalEvidence=field(unit,SourceShape.UNIT_EVIDENCE,12);conditionals.set(at+1,field(nominalEvidence,SourceShape.NOMINAL_VALUE_EVIDENCE,4));conditionals.set(at+2,evidenceCount);
        for(long i=0,n=nominal.evidenceCount(query,candidate);i<n;i++){long e=evidenceCount++*3;evidence.set(e,nominal.evidenceKind(query,candidate,i).equals("ASSIGNMENT")?1:0);evidence.set(e+1,nominal.evidenceReference(query,candidate,i));evidence.set(e+2,nominal.evidenceProvenance(query,candidate,i));}conditionals.set(at+3,evidenceCount-conditionals.get(at+2));return row;
    }
    private int interpret(boolean cics,boolean computed) {
        int end=probe.length;if(cics&&computed&&end!=8)return -1;
        if(cics||computed)while(end>0&&probe.charAt(end-1)==' '){resources.work(1,PHASE);end--;}
        if(end==0||end>8)return -1;
        for(int i=0;i<end;i++){char c=probe.charAt(i);boolean letter=c>='A'&&c<='Z',digit=c>='0'&&c<='9';if(cics?!(letter||digit||c=='$'||c=='@'||c=='#'):!(letter||c=='_'||c=='$'||i>0&&(digit||c=='@'||c=='#')))return -1;}return end;
    }
    private int compareText(long a,long b) {
        if(a==b)return 0;int na=text.characterCount(a),nb=text.characterCount(b),n=Math.min(na,nb);
        for(int at=0;at<n;){resources.work(1,PHASE);int count=Math.min(left.length,n-at);text.readCharacters(a,at,left,0,count);text.readCharacters(b,at,right,0,count);for(int i=0;i<count;i++){int c=Character.compare(left[i],right[i]);if(c!=0)return c;}at+=count;}return Integer.compare(na,nb);
    }
    private int compareCandidate(long a,long b){if(a==b)return 0;long x=candidates.get(a*CANDIDATE),y=candidates.get(b*CANDIDATE);int c=(Integer.toString(text.characterCount(x))+":").compareTo(Integer.toString(text.characterCount(y))+":");if(c==0)c=compareText(x,y);return c==0?compareText(candidates.get(a*CANDIDATE+1),candidates.get(b*CANDIDATE+1)):c;}
    private int compareSupportIdentity(long a,long b){
        if(a==b)return 0;
        if(plan.hasManagedSupportCursors())try(var first=openSupportRow(a);var second=openSupportRow(b)){
            int c=first.kind().compareTo(second.kind());if(c==0)c=compareSupportId(first,second,false);if(c==0)c=compareSupportId(first,second,true);
            for(int i=0,n=Math.min(first.premiseCount(),second.premiseCount());c==0&&i<n;i++)try(var x=first.premise(i);var y=second.premise(i)){c=PagedDependencyFactStorage.compareIdentity(x,y,left,right);}
            return c==0?Integer.compare(first.premiseCount(),second.premiseCount()):c;
        }
        // Explicit resident compatibility,not a hidden paged staging bridge.
        try(var first=projectSupportRow(a);var second=projectSupportRow(b)){var x=first.support();var y=second.support();int c=x.kind().compareTo(y.kind());if(c==0)c=compareId(x.producer(),y.producer());if(c==0)c=compareId(x.origin(),y.origin());for(int i=0,n=Math.min(x.premises().size(),y.premises().size());c==0&&i<n;i++)c=compareId(x.premises().get(i),y.premises().get(i));return c==0?Integer.compare(x.premises().size(),y.premises().size()):c;}
    }
    private int compareSupportWire(long a,long b){
        if(a==b)return 0;
        if(plan.hasManagedSupportCursors())try(var first=openSupportRow(a);var second=openSupportRow(b)){
            int c=compareSupportId(first,second,false);if(c==0)c=compareSupportId(first,second,true);return c==0?Long.compare(a,b):c;
        }
        try(var first=projectSupportRow(a);var second=projectSupportRow(b)){var x=first.support();var y=second.support();int c=compareId(x.producer(),y.producer());if(c==0)c=compareId(x.origin(),y.origin());return c==0?Long.compare(a,b):c;}
    }
    private int compareSupportId(SourceTargetPlan.SupportCursor a,SourceTargetPlan.SupportCursor b,boolean origin){
        try(var first=origin?a.origin():a.producer();var second=origin?b.origin():b.producer()){return PagedDependencyFactStorage.compareIdentity(first,second,left,right);}
    }
    /** Full typed identity in the established wire order; no hash-only equality. */
    private static int compareId(Id a,Id b){int c=a.publication().localId().compareTo(b.publication().localId());if(c==0)c=domain(a).compareTo(domain(b));if(c==0)c=unit(a).compareTo(unit(b));if(c==0&&a instanceof OperandId x&&b instanceof OperandId y){c=Integer.compare(x.owner() instanceof EntryOwner?0:1,y.owner() instanceof EntryOwner?0:1);if(c==0)c=owner(x).compareTo(owner(y));}return c==0?a.localId().compareTo(b.localId()):c;}
    private static String owner(OperandId id){return switch(id.owner()){case OperationOwner v->v.operation().localId();case EntryOwner v->v.entry().localId();};}
    private static String unit(Id id){return switch(id){case EntryId v->v.unit().localId();case LabelId v->v.unit().localId();case OperationId v->v.unit().localId();case ObjectId v->v.unit().localId();case CompletionPortId v->v.unit().localId();case OperandId v->v.owner().unit().localId();default->"";};}
    private static String domain(Id id){return switch(id){case PublicationId ignored->"publication";case UnitId ignored->"unit";case EntryId ignored->"entry";case LabelId ignored->"label";case OperationId ignored->"operation";case OperandId ignored->"operand";case ObjectId ignored->"object";case StorageId ignored->"storage";case ResourceId ignored->"resource";case ArtifactId ignored->"artifact";case ArtifactRelationId ignored->"artifactRelation";case OriginId ignored->"origin";case UncertaintyId ignored->"uncertainty";case PremiseId ignored->"premise";case CompletionPortId ignored->"completionPort";};}
    private final class BorrowedText implements CharSequence {
        private final char[] cache=new char[256];private long sourceReference,query,candidate;private int length,start=-1,count;private boolean fromNominal;
        void source(long reference){sourceReference=reference;length=Math.toIntExact(source.characterCount(reference));fromNominal=false;start=-1;}
        void nominal(long q,long c){query=q;candidate=c;length=Math.toIntExact(nominal.rawLength(q,c));fromNominal=true;start=-1;}
        void clear(){sourceReference=query=candidate=0;length=count=0;start=-1;}
        @Override public int length(){return length;}
        @Override public char charAt(int at){Objects.checkIndex(at,length);if(start<0||at<start||at>=start+count){start=(at/cache.length)*cache.length;count=Math.min(cache.length,length-start);if(fromNominal)nominal.readRaw(query,candidate,start,cache,0,count);else source.readCharacters(sourceReference,start,cache,0,count);}return cache[at-start];}
        @Override public CharSequence subSequence(int start,int end){throw new UnsupportedOperationException("borrowed block text only");}
    }
    private long item(long item){if(item<0||item>=plan.count())throw new IndexOutOfBoundsException("SOURCE program ordinal");return item*ITEM;}
    private long candidate(long item,long ordinal){long at=item(item);if(ordinal<0||ordinal>=items.get(at+1))throw new IndexOutOfBoundsException("SOURCE program candidate ordinal");return candidateOrder.get(items.get(at)+ordinal)*CANDIDATE;}
    private long conditional(long item,long candidate){long row=candidates.get(candidate(item,candidate)+5);if(row==0)throw new IllegalStateException("candidate has no conditional support");return (row-1)*CONDITIONAL;}
    @FunctionalInterface private interface Read {long get();}
    private long read(Read action){open();try{return action.get();}catch(RuntimeException|Error failure){abort(failure);throw failure;}}
    public SourceAdmissionSnapshot admission(){open();return parent();}
    public SourceTargetPlan plan(){open();return plan;}
    public long count(){open();return plan.count();}
    public long candidateCount(long item){return read(()->items.get(item(item)+1));}
    private long textId(long item,long candidate,boolean raw){return candidates.get(candidate(item,candidate)+(raw?1:0));}
    public long rawLength(long item,long candidate){return read(()->text.characterCount(textId(item,candidate,true)));}
    public long referenceLength(long item,long candidate){return read(()->text.characterCount(textId(item,candidate,false)));}
    public void readRaw(long item,long candidate,long offset,char[] destination,int at,int count){read(()->{text.readCharacters(textId(item,candidate,true),offset,destination,at,count);return 0;});}
    public void readReference(long item,long candidate,long offset,char[] destination,int at,int count){read(()->{text.readCharacters(textId(item,candidate,false),offset,destination,at,count);return 0;});}
    public long supportCount(long item,long candidate){return read(()->candidates.get(candidate(item,candidate)+7));}
    public SourceTargetPlan.SupportProjection projectSupport(long item,long candidate,long ordinal){open();try{long row=candidate(item,candidate);if(ordinal<0||ordinal>=candidates.get(row+7))throw new IndexOutOfBoundsException("SOURCE program support ordinal");return projectSupportRow(supportOrder.get(candidates.get(row+6)+ordinal));}catch(RuntimeException|Error failure){abort(failure);throw failure;}}
    /** Borrowed plan-owned scope; close after use,expires with its owning plan.
     * Invalid read ordinals are caller errors,not an interrupted derivation. */
    public SourceTargetPlan.SupportCursor openSupport(long item,long candidate,long ordinal){open();try{long row=candidate(item,candidate);if(ordinal<0||ordinal>=candidates.get(row+7))throw new IndexOutOfBoundsException("SOURCE program support ordinal");return openSupportRow(supportOrder.get(candidates.get(row+6)+ordinal));}catch(IndexOutOfBoundsException invalid){throw invalid;}catch(RuntimeException|Error failure){abort(failure);throw failure;}}
    /** Explicit detached compatibility; the native productive writer uses openSupport. */
    public DependencySiteFact.Support support(long item,long candidate,long ordinal){try(var projection=projectSupport(item,candidate,ordinal)){return projection.support();}}
    public boolean hasConditionalSupport(long item,long candidate){return read(()->candidates.get(candidate(item,candidate)+5))!=0;}
    public int conditionalAssumptionCount(long item,long candidate){return Math.toIntExact(read(()->Long.bitCount(conditionals.get(conditional(item,candidate)))));}
    public String conditionalAssumption(long item,long candidate,int ordinal){long mask=read(()->conditionals.get(conditional(item,candidate)));if(ordinal<0||ordinal>=Long.bitCount(mask)){var failure=new IndexOutOfBoundsException("conditional assumption ordinal");abort(failure);throw failure;}for(int i=0;i<ASSUMPTIONS.length;i++)if((mask&(1L<<i))!=0&&ordinal--==0)return ASSUMPTIONS[i];throw new AssertionError();}
    public long conditionalEvidenceCount(long item,long candidate){return read(()->conditionals.get(conditional(item,candidate)+3));}
    private long evidence(long item,long candidate,long ordinal){long row=conditional(item,candidate);if(ordinal<0||ordinal>=conditionals.get(row+3))throw new IndexOutOfBoundsException("conditional evidence ordinal");return (conditionals.get(row+2)+ordinal)*3;}
    public String conditionalEvidenceKind(long item,long candidate,long ordinal){return read(()->evidence.get(evidence(item,candidate,ordinal)))==1?"ASSIGNMENT":"DECLARATION_VALUE";}
    public long conditionalEvidenceReference(long item,long candidate,long ordinal){return read(()->evidence.get(evidence(item,candidate,ordinal)+1));}
    public long conditionalEvidenceProvenance(long item,long candidate,long ordinal){return read(()->evidence.get(evidence(item,candidate,ordinal)+2));}
    public long conditionalUncertaintyCount(long item,long candidate){return read(()->source.size(conditionals.get(conditional(item,candidate)+1),SourceShape.UNCERTAINTY));}
    public long conditionalUncertainty(long item,long candidate,long ordinal){return read(()->{long list=conditionals.get(conditional(item,candidate)+1);if(ordinal<0||ordinal>=source.size(list,SourceShape.UNCERTAINTY))throw new IndexOutOfBoundsException("conditional uncertainty ordinal");return source.element(list,SourceShape.UNCERTAINTY,ordinal);});}
    public boolean valueRemainder(long item){return (read(()->items.get(item(item)+3))&1)!=0;}
    public boolean interpretationRemainder(long item){return (read(()->items.get(item(item)+3))&2)!=0;}
    public int authorityCount(long item){long mask=read(()->items.get(item(item)+2));int n=0;for(;mask!=0;mask>>>=3)n++;return n;}
    public String authority(long item,int ordinal){long mask=read(()->items.get(item(item)+2));if(ordinal<0||ordinal>=authorityCount(item)){var failure=new IndexOutOfBoundsException("SOURCE authority ordinal");abort(failure);throw failure;}return AUTHORITIES[(int)((mask>>>(ordinal*3))&7)-1];}
    public long reasonCount(long item){return read(()->items.get(item(item)+5));}
    private long reason(long item,long ordinal){long at=item(item);if(ordinal<0||ordinal>=items.get(at+5))throw new IndexOutOfBoundsException("SOURCE reason ordinal");return reasons.get(items.get(at+4)+ordinal);}
    public long reasonLength(long item,long ordinal){return read(()->text.characterCount(reason(item,ordinal)));}
    public void readReason(long item,long ordinal,long offset,char[] destination,int at,int count){read(()->{text.readCharacters(reason(item,ordinal),offset,destination,at,count);return 0;});}
    private long globalReason(long ordinal){if(ordinal<0||ordinal>=globalReasonCount)throw new IndexOutOfBoundsException("global SOURCE reason ordinal");return globalReasons.get(ordinal);}
    public long globalReasonCount(){open();return globalReasonCount;}
    public long globalReasonLength(long ordinal){return read(()->text.characterCount(globalReason(ordinal)));}
    public void readGlobalReason(long ordinal,long offset,char[] destination,int at,int count){read(()->{text.readCharacters(globalReason(ordinal),offset,destination,at,count);return 0;});}
    public long conditionalRuns(){open();return plan.conditionalRuns();}
    public long conditionalQueries(){open();return plan.conditionalQueries();}
    public long conditionalWorkItems(){open();return workItems;}
    public long reusedByExistingQuery(){open();return reused;}
    public long qualifiedComputedOccurrences(){open();return qualifiedComputed;}
    private long metric(long ordinal){if(ordinal<0||ordinal>=metricCount)throw new IndexOutOfBoundsException("managed metric ordinal");return metricOrder.get(ordinal);}
    public long metricCount(){open();return metricCount;}
    public long metricNameLength(long ordinal){return read(()->text.characterCount(metric(ordinal)));}
    public void readMetricName(long ordinal,long offset,char[] destination,int at,int count){read(()->{text.readCharacters(metric(ordinal),offset,destination,at,count);return 0;});}
    public long metricValue(long ordinal){return read(()->metrics.get(metric(ordinal)));}
    private static void suppress(Throwable primary,Throwable cleanup){if(primary!=cleanup)primary.addSuppressed(cleanup);}
    private void abort(Throwable primary){try{close();}catch(RuntimeException|Error cleanup){suppress(primary,cleanup);}}
    @Override public void close() {
        AutoCloseable[] owned;
        synchronized(this){if(closed)return;closed=true;ready=false;owned=new AutoCloseable[]{nominal,metricOrder,metrics,globalReasons,reasons,evidence,conditionals,supportOrder,supports,candidateOrder,candidates,items,text,airLifetime,control};nominal=null;metricOrder=metrics=globalReasons=reasons=evidence=conditionals=supportOrder=supports=candidateOrder=candidates=items=null;text=null;airLifetime=null;control=null;plan=null;source=null;admission=null;resources=null;pages=null;probe=null;left=right=null;}
        var parent=owner;if(parent!=null)parent.release(this);Throwable failure=null;
        for(var value:owned)if(value!=null)try{value.close();}catch(Exception|Error cleanup){if(failure==null)failure=cleanup;else suppress(failure,cleanup);}
        if(failure instanceof RuntimeException error)throw error;if(failure instanceof Error error)throw error;if(failure!=null)throw new IllegalStateException("SOURCE program cleanup",failure);
    }
}
