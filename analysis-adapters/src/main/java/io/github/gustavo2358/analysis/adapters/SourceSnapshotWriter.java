package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.analysis.dependencies.SourceAdmissionSnapshot;
import io.github.gustavo2358.analysis.dependencies.SourceQualifiedSnapshot;
import io.github.gustavo2358.analysis.dependencies.SourceNominalCandidates;
import io.github.gustavo2358.analysis.dependencies.SourceValuesProvider;
import io.github.gustavo2358.analysis.dependencies.SourceProgramSnapshot;
import io.github.gustavo2358.air.model.Ids.Id;
import io.github.gustavo2358.analysis.dependencies.source.*;
import io.github.gustavo2358.analysis.solver.*;
import java.io.*;
import java.util.Objects;
import java.util.List;

/** Closed canonical SOURCE mapping; explicit paged frames, never a resident certificate tree. */
final class SourceSnapshotWriter implements AutoCloseable {
    private static final AnalysisResources.Phase PHASE=AnalysisResources.Phase.ENCODE;
    private static final int WIDTH=6;
    private SourceAdmissionSnapshot admission;
    private SourceSnapshot source;
    private JsonOutput output;
    private char[] characters;
    private char[] comparisonCharacters;
    private PagedLongArray frames;
    private AnalysisResources.Reservation control;
    private AnalysisResources.Reservation programControl;
    private SourceAdmissionSnapshot.ConsumerLease lifetime;
    private SourceQualifiedSnapshot qualified;
    private SourceNominalCandidates nominal;
    private SourceProgramSnapshot programs;
    private PageStore pages;
    private AnalysisResources resources;
    private volatile boolean closed;
    private long depth=-1;
    private SourceSnapshotWriter(SourceAdmissionSnapshot admission,SourceSnapshot source,AnalysisResources.Reservation control){this.admission=admission;this.source=source;this.control=control;}
    static void write(SourceAdmissionSnapshot admission,OutputStream output,PageStore pages,AnalysisResources resources)throws IOException {
        Objects.requireNonNull(admission);Objects.requireNonNull(output);Objects.requireNonNull(pages);Objects.requireNonNull(resources);
        var source=admission.snapshot(pages,resources);
        var capacity=resources.reserve(AnalysisResources.Pool.RESIDENT,65536+1024,PHASE);
        SourceSnapshotWriter writer=null;
        try {
            writer=new SourceSnapshotWriter(admission,source,capacity);var registration=capacity;capacity=null;
            registration.onOwnerClose(writer::close);
            writer.initialize(new JsonOutput(ManagedOutputStream.metered(output,resources)),pages,resources);
            var owned=writer;try(owned) {owned.writeValue(owned.view().root(),SourceShape.ROOT,0);owned.out().finish();owned.verify();}
        }catch(IOException|RuntimeException|Error failure) {
            if(writer!=null)try{writer.close();}catch(RuntimeException|Error cleanup){suppress(failure,cleanup);}
            else if(capacity!=null)try{capacity.close();}catch(RuntimeException|Error cleanup){suppress(failure,cleanup);}
            throw failure;
        }
    }
    /** Shares the root's already funded buffer and output meter; never owns either. */
    static SourceSnapshotWriter open(SourceAdmissionSnapshot admission,JsonOutput output,PageStore pages,AnalysisResources resources) {
        Objects.requireNonNull(admission);Objects.requireNonNull(output);Objects.requireNonNull(pages);Objects.requireNonNull(resources);
        var source=admission.snapshot(pages,resources);var capacity=resources.reserve(AnalysisResources.Pool.RESIDENT,1024,PHASE);SourceSnapshotWriter writer=null;
        try {
            writer=new SourceSnapshotWriter(admission,source,capacity);var registration=capacity;capacity=null;registration.onOwnerClose(writer::close);
            writer.initialize(output,pages,resources);return writer;
        }catch(RuntimeException|Error failure) {
            if(writer!=null)try{writer.close();}catch(RuntimeException|Error cleanup){suppress(failure,cleanup);}
            else if(capacity!=null)try{capacity.close();}catch(RuntimeException|Error cleanup){suppress(failure,cleanup);}throw failure;
        }
    }
    private void initialize(JsonOutput stream,PageStore pages,AnalysisResources resources) {
        open();output=stream;this.pages=pages;this.resources=resources;characters=new char[256];frames=new PagedLongArray(pages,Long.MAX_VALUE,resources,PHASE);
        lifetime=admitted().attachResource(this,pages,resources);open();
    }
    void verify(){admitted().snapshot();var facts=qualified;if(facts!=null)require(facts);var values=nominal;if(values!=null)require(values);var resolved=programs;if(resolved!=null)require(resolved);}
    private void require(SourceQualifiedSnapshot facts) {
        if(Objects.requireNonNull(facts).admission()!=admitted())throw new IllegalArgumentException("foreign SOURCE candidate admission");
        if(qualified!=null&&qualified!=facts)throw new IllegalArgumentException("multiple SOURCE candidate owners");qualified=facts;
    }
    private void same(JsonOutput target){if(target!=out())throw new IllegalArgumentException("foreign SOURCE root buffer");}
    JsonOutput.EncodedValue evidence(){open();return target->{same(target);writeValue(view().root(),SourceShape.ROOT,0);};}
    JsonOutput.EncodedValue qualified(SourceQualifiedSnapshot facts){require(facts);return target->{same(target);writeQualified(facts);};}
    JsonOutput.EncodedValue nativeFiles(SourceQualifiedSnapshot facts){require(facts);return target->{same(target);writeItems(facts,SourceQualifiedSnapshot.Kind.NATIVE_FILE);};}
    private void require(SourceNominalCandidates facts) {
        if(Objects.requireNonNull(facts).admission()!=admitted())throw new IllegalArgumentException("foreign nominal SOURCE admission");
        if(nominal!=null&&nominal!=facts)throw new IllegalArgumentException("multiple nominal SOURCE owners in one wire scope");nominal=facts;
    }
    JsonOutput.EncodedValue nominalRaw(SourceNominalCandidates facts,long query,long candidate) {
        require(facts);facts.rawLength(query,candidate);return target->{same(target);require(facts);out().nominalText(facts,query,candidate,block());require(facts);};
    }
    /** Existing program-candidate conditionalSupport wire, without a resident support tree. */
    JsonOutput.EncodedValue conditionalSupport(SourceNominalCandidates facts,long query,long candidate) {
        require(facts);facts.evidenceCount(query,candidate);return target->{same(target);writeConditionalSupport(facts,query,candidate);};
    }
    private void writeConditionalSupport(SourceNominalCandidates facts,long query,long candidate)throws IOException {
        require(facts);out().symbol('{');field("analysisBoundary");out().value("NON_EXECUTABLE_SOURCE");out().symbol(',');field("assumptions");out().symbol('[');
        for(int i=0,n=facts.assumptionCount(query,candidate);i<n;i++){if(i!=0)out().symbol(',');out().value(facts.assumption(query,candidate,i));}out().symbol(']');
        out().symbol(',');field("evidence");out().symbol('[');
        for(long i=0,n=facts.evidenceCount(query,candidate);i<n;i++) {
            if(i!=0)out().symbol(',');out().symbol('{');field("kind");out().value(facts.evidenceKind(query,candidate,i));out().symbol(',');
            field("provenance");writeValue(facts.evidenceProvenance(query,candidate,i),SourceShape.PROVENANCE,0);out().symbol(',');
            field("reference");out().sourceText(view(),facts.evidenceReference(query,candidate,i),block());out().symbol('}');
        }out().symbol(']');out().symbol(',');field("provider");out().value(SourceValuesProvider.PROFILE);out().symbol(',');field("uncertainties");out().symbol('[');
        for(long i=0,n=facts.uncertaintyCount();i<n;i++){if(i!=0)out().symbol(',');writeValue(facts.uncertainty(i),SourceShape.UNCERTAINTY,0);}out().symbol(']');out().symbol('}');require(facts);
    }
    private void field(String name)throws IOException{out().string(name);out().symbol(':');}
    private void require(SourceProgramSnapshot facts) {
        if(Objects.requireNonNull(facts).admission()!=admitted())throw new IllegalArgumentException("foreign SOURCE program admission");
        if(programs!=null&&programs!=facts)throw new IllegalArgumentException("multiple SOURCE program owners");
        if(programControl==null){programControl=resources.reserve(AnalysisResources.Pool.RESIDENT,8192,PHASE);programControl.onOwnerClose(this::close);open();comparisonCharacters=new char[256];}programs=facts;
    }
    JsonOutput.EncodedValue programs(SourceProgramSnapshot facts){require(facts);return target->{same(target);require(facts);out().symbol('[');for(long i=0,n=facts.count();i<n;i++){if(i!=0)out().symbol(',');writeProgram(facts,i);}out().symbol(']');require(facts);};}
    JsonOutput.EncodedValue globalReasons(SourceProgramSnapshot facts){require(facts);return target->{same(target);out().symbol('[');for(long i=0,n=facts.globalReasonCount();i<n;i++){if(i!=0)out().symbol(',');out().globalProgramReason(facts,i,block());}out().symbol(']');require(facts);};}
    JsonOutput.EncodedValue metrics(SourceProgramSnapshot facts){require(facts);return target->{same(target);out().symbol('{');for(long i=0,n=facts.metricCount();i<n;i++){if(i!=0)out().symbol(',');out().programMetricName(facts,i,block());out().symbol(':');out().value(facts.metricValue(i));}out().symbol('}');require(facts);};}
    private JsonOutput.EncodedValue encoded(Io action){return target->{same(target);action.write();};}
    @FunctionalInterface private interface Io {void write()throws IOException;}
    private void ids(List<? extends Id> values)throws IOException {
        // The borrowed list is not copied or stream-sorted into an unmetered array.
        try(var order=new PagedLongIndex(pages,resources,PHASE,(a,b)->{int c=WireIds.ORDER.compare(values.get(Math.toIntExact(a)),values.get(Math.toIntExact(b)));return c==0?Long.compare(a,b):c;})) {
            for(int i=0,n=values.size();i<n;i++){resources.work(1,PHASE);order.intern(i,i+1L);}
            out().symbol('[');boolean first=true;try(var cursor=order.cursor()){while(cursor.advance()){if(!first)out().symbol(',');first=false;out().value(WireIds.id(values.get(Math.toIntExact(cursor.value()-1))));}}out().symbol(']');
        }
    }
    private void operations(SourceProgramSnapshot facts,long item)throws IOException {
        var plan=facts.plan();try(var order=new PagedLongIndex(pages,resources,PHASE,(a,b)->{int c=WireIds.ORDER.compare(plan.operation(item,a),plan.operation(item,b));return c==0?Long.compare(a,b):c;})) {
            for(long i=0,n=plan.operationCount(item);i<n;i++)order.intern(i,i+1);
            out().symbol('[');boolean first=true;try(var cursor=order.cursor()){while(cursor.advance()){if(!first)out().symbol(',');first=false;out().value(WireIds.id(plan.operation(item,cursor.value()-1)));}}out().symbol(']');
        }
    }
    private void qualifications(SourceProgramSnapshot facts,long item)throws IOException {
        var plan=facts.plan();if(plan.qualified(item))writeValue(view().field(plan.sourceOccurrence(item),SourceShape.OCCURRENCE,9),SourceShape.LIST,SourceShape.TEXT.tag());else{out().symbol('[');out().symbol(']');}
    }
    private boolean standaloneCics(SourceProgramSnapshot facts,long item) {
        var plan=facts.plan();var air=plan.program().admission().snapshot();long invoke=plan.standaloneInvoke(item),target=air.field(invoke,io.github.gustavo2358.air.model.AirShape.OPERATIONS_INVOKE,2),namespace=air.field(target,air.shape(target),1);
        if(air.characterCount(namespace)!=12)return false;air.readCharacters(namespace,0,block(),0,12);String name="cics.program";for(int i=0;i<12;i++)if(block()[i]!=name.charAt(i))return false;return true;
    }
    private void writeProgram(SourceProgramSnapshot facts,long item)throws IOException {
        require(facts);var plan=facts.plan();long occurrence=plan.sourceOccurrence(item),unit=occurrence==0?0:view().field(view().field(occurrence,SourceShape.OCCURRENCE,0),SourceShape.STATEMENT_ID,0);
        var fields=new JsonOutput.Fields("analysisReasons",encoded(()->{out().symbol('[');for(long r=0,n=facts.reasonCount(item);r<n;r++){if(r!=0)out().symbol(',');out().programReason(facts,item,r,block());}out().symbol(']');}),
            "authorities",encoded(()->{out().symbol('[');for(int a=0,n=facts.authorityCount(item);a<n;a++){if(a!=0)out().symbol(',');out().value(facts.authority(item,a));}out().symbol(']');}),
            "caller",occurrence==0?plan.operation(item,0).unit().localId():encoded(()->out().sourceText(view(),view().field(unit,SourceShape.UNIT_ID,2),block())),
            "candidates",encoded(()->{out().symbol('[');for(long c=0,n=facts.candidateCount(item);c<n;c++){if(c!=0)out().symbol(',');writeProgramCandidate(facts,item,c);}out().symbol(']');}),
            "executableOperations",encoded(()->operations(facts,item)),
            "executableSites",encoded(()->executableSites(plan,item)),
            "interpretationRemainder",facts.interpretationRemainder(item),
            "nameProfile",occurrence==0?(standaloneCics(facts,item)?io.github.gustavo2358.analysis.dependencies.CicsNameInterpreter.PROFILE:io.github.gustavo2358.analysis.dependencies.CallNameInterpreter.PROFILE):encoded(()->out().sourceText(view(),view().field(occurrence,SourceShape.OCCURRENCE,4),block())),
            "qualifications",encoded(()->qualifications(facts,item)),
            "sourceOccurrence",occurrence==0?null:encoded(()->writeValue(view().field(occurrence,SourceShape.OCCURRENCE,0),SourceShape.STATEMENT_ID,0)),
            "targetKind",plan.targetKind(item),"technology",occurrence==0?(standaloneCics(facts,item)?"CICS":"COBOL"):encoded(()->out().sourceText(view(),view().field(occurrence,SourceShape.OCCURRENCE,1),block())),"valueRemainder",facts.valueRemainder(item));
        if(plan.sourceControlRemainder(item))fields.put("controlRemainder",true);out().value(fields);require(facts);
    }
    private void executableSites(io.github.gustavo2358.analysis.dependencies.SourceTargetPlan plan,long item)throws IOException {
        out().symbol('[');
        if(plan.hasManagedFactCursors()){
            var writer=new DependencyJson.FactWriter(out(),pages,resources);
            for(long s=0,n=plan.siteCount(item);s<n;s++)try(var site=plan.openFact(item,s)){if(s!=0)out().symbol(',');writer.executableSite(site);}
        }else for(long s=0,n=plan.siteCount(item);s<n;s++)try(var projection=plan.projectSite(item,s)){
            if(s!=0)out().symbol(',');var site=projection.fact();out().value(new JsonOutput.Fields("entry",WireIds.id(site.entry()),"operation",WireIds.id(site.operation()),"reachability",site.reachability().name(),"valuePoint",site.valuePoint()==null?null:ResultJson.point(site.valuePoint()),"premises",encoded(()->ids(site.premises())),"provenance",encoded(()->ids(site.provenance()))));
        }out().symbol(']');
    }
    private void writeProgramCandidate(SourceProgramSnapshot facts,long item,long candidate)throws IOException {
        var fields=new JsonOutput.Fields("referenceName",encoded(()->out().programText(facts,item,candidate,false,block())),"rawValue",encoded(()->out().programText(facts,item,candidate,true,block())),"qualifications",encoded(()->qualifications(facts,item)),
            "supports",encoded(()->writeProgramSupports(facts,item,candidate)));
        if(facts.hasConditionalSupport(item,candidate))fields.put("conditionalSupports",encoded(()->{out().symbol('[');writeProgramConditional(facts,item,candidate);out().symbol(']');}));out().value(fields);
    }
    private void writeProgramSupports(SourceProgramSnapshot facts,long item,long candidate)throws IOException {
        out().symbol('[');for(long s=0,n=facts.supportCount(item,candidate);s<n;s++){
            if(s!=0)out().symbol(',');
            if(facts.plan().hasManagedSupportCursors())try(var support=facts.openSupport(item,candidate,s)){
                out().symbol('{');field("kind");out().value(support.kind().name());out().symbol(',');field("origin");try(var origin=support.origin()){out().identity(origin,block());}
                out().symbol(',');field("premises");writePremises(support);out().symbol(',');field("producer");try(var producer=support.producer()){out().identity(producer,block());}out().symbol('}');
            }else try(var projection=facts.projectSupport(item,candidate,s)){
                var support=projection.support();out().value(new JsonOutput.Fields("kind",support.kind().name(),"producer",WireIds.id(support.producer()),"origin",WireIds.id(support.origin()),"premises",encoded(()->ids(support.premises()))));
            }
        }out().symbol(']');
    }
    private void writePremises(io.github.gustavo2358.analysis.dependencies.SourceTargetPlan.SupportCursor support)throws IOException {
        // Full canonical identity plus original ordinal tie keeps duplicate bytes.
        // O(P log P) sort is separate from O(1) direct premise extent lookup.
        try(var order=new PagedLongIndex(pages,resources,PHASE,(a,b)->{int c=support.comparePremises(Math.toIntExact(a),Math.toIntExact(b),block(),comparisonCharacters);return c==0?Long.compare(a,b):c;})){
            for(int i=0,n=support.premiseCount();i<n;i++){resources.work(1,PHASE);order.intern(i,i+1L);}
            out().symbol('[');boolean first=true;try(var cursor=order.cursor()){while(cursor.advance())try(var premise=support.premise(Math.toIntExact(cursor.value()-1))){if(!first)out().symbol(',');first=false;out().identity(premise,block());}}out().symbol(']');
        }
    }
    private void writeProgramConditional(SourceProgramSnapshot facts,long item,long candidate)throws IOException {
        out().value(new JsonOutput.Fields("analysisBoundary","NON_EXECUTABLE_SOURCE","provider",SourceValuesProvider.PROFILE,
            "assumptions",encoded(()->{out().symbol('[');for(int a=0,n=facts.conditionalAssumptionCount(item,candidate);a<n;a++){if(a!=0)out().symbol(',');out().value(facts.conditionalAssumption(item,candidate,a));}out().symbol(']');}),
            "evidence",encoded(()->{out().symbol('[');for(long e=0,n=facts.conditionalEvidenceCount(item,candidate);e<n;e++){if(e!=0)out().symbol(',');long ordinal=e;out().value(new JsonOutput.Fields("kind",facts.conditionalEvidenceKind(item,candidate,e),"reference",encoded(()->out().sourceText(view(),facts.conditionalEvidenceReference(item,candidate,ordinal),block())),"provenance",encoded(()->writeValue(facts.conditionalEvidenceProvenance(item,candidate,ordinal),SourceShape.PROVENANCE,0))));}out().symbol(']');}),
            "uncertainties",encoded(()->{out().symbol('[');for(long u=0,n=facts.conditionalUncertaintyCount(item,candidate);u<n;u++){if(u!=0)out().symbol(',');writeValue(facts.conditionalUncertainty(item,candidate,u),SourceShape.UNCERTAINTY,0);}out().symbol(']');})));
    }
    private void writeQualified(SourceQualifiedSnapshot facts)throws IOException {
        require(facts);out().symbol('{');field("analysisBoundary");out().value("NON_EXECUTABLE_SOURCE");out().symbol(',');field("evidence");writeValue(view().root(),SourceShape.ROOT,0);
        out().symbol(',');field("occurrences");writeItems(facts,SourceQualifiedSnapshot.Kind.OCCURRENCE);out().symbol('}');require(facts);
    }
    private void writeItems(SourceQualifiedSnapshot facts,SourceQualifiedSnapshot.Kind kind)throws IOException {
        require(facts);out().symbol('[');boolean first=true;
        for(long u=0,n=facts.unitCount();u<n;u++)for(long i=0,count=facts.count(u,kind);i<count;i++) {
            if(!first)out().symbol(',');first=false;boolean file=kind==SourceQualifiedSnapshot.Kind.NATIVE_FILE;long reference=facts.reference(u,i,kind);
            out().symbol('{');if(file){field("analysisBoundary");out().value("NON_EXECUTABLE_SOURCE");out().symbol(',');}
            field("candidates");writeCandidates(facts,u,i,kind,reference);out().symbol(',');
            if(!file){field("interpretationRemainder");out().value(facts.remainder(u,i,kind));out().symbol(',');field("occurrence");writeValue(view().field(reference,SourceShape.OCCURRENCE,0),SourceShape.STATEMENT_ID,0);out().symbol(',');}
            else{field("remainder");out().value(facts.remainder(u,i,kind));out().symbol(',');field("source");writeValue(reference,SourceShape.NATIVE_FILE_USE,0);out().symbol(',');}
            field("status");out().value(facts.status(u,i,kind).name());
            if(!file){out().symbol(',');field("valueRemainder");out().value(facts.valueRemainder(u,i,kind));}
            out().symbol('}');
        }out().symbol(']');require(facts);
    }
    private void writeCandidates(SourceQualifiedSnapshot facts,long unit,long ordinal,SourceQualifiedSnapshot.Kind kind,long reference)throws IOException {
        boolean file=kind==SourceQualifiedSnapshot.Kind.NATIVE_FILE;out().symbol('[');
        for(long c=0,n=facts.candidateCount(unit,ordinal,kind);c<n;c++) {
            if(c!=0)out().symbol(',');out().symbol('{');
            if(!file){field("occurrence");writeValue(view().field(reference,SourceShape.OCCURRENCE,0),SourceShape.STATEMENT_ID,0);out().symbol(',');}
            field("qualifications");writeValue(view().field(reference,file?SourceShape.NATIVE_FILE_USE:SourceShape.OCCURRENCE,file?7:9),SourceShape.LIST,SourceShape.TEXT.tag());out().symbol(',');
            long raw=facts.rawValue(unit,ordinal,kind,c);field("rawValue");out().sourceText(view(),raw,block());out().symbol(',');field("referenceName");out().sourceText(view(),raw,facts.referenceLength(unit,ordinal,kind,c),block());out().symbol('}');
        }out().symbol(']');
    }
    private void open(){if(closed)throw new IllegalStateException("SOURCE wire writer expired");}
    private SourceAdmissionSnapshot admitted(){open();var value=admission;if(value==null)throw new IllegalStateException("SOURCE wire writer expired");return value;}
    private SourceSnapshot view(){open();var value=source;if(value==null)throw new IllegalStateException("SOURCE wire writer expired");return value;}
    private JsonOutput out(){open();var value=output;if(value==null)throw new IllegalStateException("SOURCE wire writer expired");return value;}
    private PagedLongArray work(){open();var value=frames;if(value==null)throw new IllegalStateException("SOURCE wire writer expired");return value;}
    private char[] block(){open();var value=characters;if(value==null)throw new IllegalStateException("SOURCE wire writer expired");return value;}
    private long get(int slot){return work().get(Math.addExact(Math.multiplyExact(depth,WIDTH),slot));}
    private void set(int slot,long value){work().set(Math.addExact(Math.multiplyExact(depth,WIDTH),slot),value);}
    private void push(long reference,SourceShape shape,int elementTag) {
        depth=Math.incrementExact(depth);set(0,reference);set(1,shape.tag());set(2,0);set(3,-1);set(4,elementTag);set(5,0);
    }
    private void writeValue(long reference,SourceShape shape,int elementTag)throws IOException {
        if(depth!=-1)throw new IllegalStateException("SOURCE wire reentry");push(reference,shape,elementTag);walk();
    }
    /** Empty optional lists normalize exactly like the existing canonical resident writer. */
    private boolean omitted(SourceShape shape,int ordinal,long reference) {
        if(reference==0)return true;
        boolean emptyList=shape==SourceShape.UNIT_EVIDENCE&&ordinal==13||shape==SourceShape.SUPPORT&&ordinal==4||shape==SourceShape.TERM&&ordinal==2;
        return emptyList&&view().size(reference,shape.field(ordinal).element())==0;
    }
    private int nextField(long reference,SourceShape shape,int previous) {
        int chosen=-1;
        for(int i=0;i<shape.fieldCount();i++) {
            String name=shape.field(i).name();
            if(previous>=0&&name.compareTo(shape.field(previous).name())<=0)continue;
            if(chosen>=0&&name.compareTo(shape.field(chosen).name())>=0)continue;
            if(!omitted(shape,i,view().field(reference,shape,i)))chosen=i;
        }
        return chosen;
    }
    private void walk()throws IOException {
        while(depth>=0) {
            long reference=get(0);var shape=SourceShape.forTag(Math.toIntExact(get(1)));
            if(shape==SourceShape.TEXT) {out().sourceText(view(),reference,block());depth--;continue;}
            if(shape==SourceShape.INTEGER) {out().value(view().integer(reference));depth--;continue;}
            if(shape==SourceShape.BOOLEAN) {out().value(view().bool(reference));depth--;continue;}
            if(shape==SourceShape.LIST) {
                var type=SourceShape.forTag(Math.toIntExact(get(4)));
                if(get(2)==0){out().symbol('[');set(2,1);set(3,0);set(5,view().size(reference,type));}
                long next=get(3);
                if(next==get(5)){out().symbol(']');depth--;continue;}
                if(next!=0)out().symbol(',');set(3,Math.incrementExact(next));push(view().element(reference,type,next),type,0);continue;
            }
            if(!shape.record())throw new IllegalStateException("unmapped admitted SOURCE shape");
            if(get(2)==0){out().symbol('{');set(2,1);}
            int next=nextField(reference,shape,Math.toIntExact(get(3)));
            if(next<0){out().symbol('}');depth--;continue;}
            if(get(5)!=0)out().symbol(',');set(5,1);set(3,next);
            var field=shape.field(next);out().string(field.name());out().symbol(':');
            push(view().field(reference,shape,next),field.shape(),field.shape()==SourceShape.LIST?field.element().tag():0);
        }
    }
    private static void suppress(Throwable primary,Throwable cleanup){if(primary!=cleanup)primary.addSuppressed(cleanup);}
    @Override public void close() {
        PagedLongArray work;AnalysisResources.Reservation capacity,programCapacity;SourceAdmissionSnapshot.ConsumerLease link;
        synchronized(this){if(closed)return;closed=true;work=frames;frames=null;capacity=control;control=null;programCapacity=programControl;programControl=null;link=lifetime;lifetime=null;admission=null;source=null;characters=comparisonCharacters=null;output=null;qualified=null;nominal=null;programs=null;pages=null;resources=null;depth=-1;}
        Throwable failure=null;
        for(AutoCloseable value:new AutoCloseable[]{link,work,programCapacity,capacity})if(value!=null)try{value.close();}catch(Exception|Error cleanup){if(failure==null)failure=cleanup;else suppress(failure,cleanup);}
        if(failure instanceof RuntimeException error)throw error;if(failure instanceof Error error)throw error;
        if(failure!=null)throw new IllegalStateException("SOURCE wire cleanup",failure);
    }
}
