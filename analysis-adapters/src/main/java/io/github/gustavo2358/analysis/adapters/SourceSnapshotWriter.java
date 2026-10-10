package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.analysis.dependencies.SourceAdmissionSnapshot;
import io.github.gustavo2358.analysis.dependencies.SourceQualifiedSnapshot;
import io.github.gustavo2358.analysis.dependencies.source.*;
import io.github.gustavo2358.analysis.solver.*;
import java.io.*;
import java.util.Objects;

/** Closed canonical SOURCE mapping; explicit paged frames, never a resident certificate tree. */
final class SourceSnapshotWriter implements AutoCloseable {
    private static final AnalysisResources.Phase PHASE=AnalysisResources.Phase.ENCODE;
    private static final int WIDTH=6;
    private SourceAdmissionSnapshot admission;
    private SourceSnapshot source;
    private JsonOutput output;
    private char[] characters;
    private PagedLongArray frames;
    private AnalysisResources.Reservation control;
    private SourceAdmissionSnapshot.ConsumerLease lifetime;
    private SourceQualifiedSnapshot qualified;
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
            writer.initialize(new JsonOutput(new MeteredOutput(output,resources)),pages,resources);
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
        open();output=stream;characters=new char[256];frames=new PagedLongArray(pages,Long.MAX_VALUE,resources,PHASE);
        lifetime=admitted().attachResource(this,pages,resources);open();
    }
    void verify(){admitted().snapshot();var facts=qualified;if(facts!=null)require(facts);}
    private void require(SourceQualifiedSnapshot facts) {
        if(Objects.requireNonNull(facts).admission()!=admitted())throw new IllegalArgumentException("foreign SOURCE candidate admission");
        if(qualified!=null&&qualified!=facts)throw new IllegalArgumentException("multiple SOURCE candidate owners");qualified=facts;
    }
    private void same(JsonOutput target){if(target!=out())throw new IllegalArgumentException("foreign SOURCE root buffer");}
    JsonOutput.EncodedValue evidence(){open();return target->{same(target);writeValue(view().root(),SourceShape.ROOT,0);};}
    JsonOutput.EncodedValue qualified(SourceQualifiedSnapshot facts){require(facts);return target->{same(target);writeQualified(facts);};}
    JsonOutput.EncodedValue nativeFiles(SourceQualifiedSnapshot facts){require(facts);return target->{same(target);writeItems(facts,SourceQualifiedSnapshot.Kind.NATIVE_FILE);};}
    private void field(String name)throws IOException{out().string(name);out().symbol(':');}
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
    private static final class MeteredOutput extends FilterOutputStream {
        private final AnalysisResources resources;
        private MeteredOutput(OutputStream stream,AnalysisResources resources){super(stream);this.resources=resources;}
        @Override public void write(int value)throws IOException{resources.output(1,PHASE);out.write(value);}
        @Override public void write(byte[] values,int at,int count)throws IOException{resources.output(count,PHASE);out.write(values,at,count);}
    }
    @Override public void close() {
        PagedLongArray work;AnalysisResources.Reservation capacity;SourceAdmissionSnapshot.ConsumerLease link;
        synchronized(this){if(closed)return;closed=true;work=frames;frames=null;capacity=control;control=null;link=lifetime;lifetime=null;admission=null;source=null;characters=null;output=null;qualified=null;depth=-1;}
        Throwable failure=null;
        for(AutoCloseable value:new AutoCloseable[]{link,work,capacity})if(value!=null)try{value.close();}catch(Exception|Error cleanup){if(failure==null)failure=cleanup;else suppress(failure,cleanup);}
        if(failure instanceof RuntimeException error)throw error;if(failure instanceof Error error)throw error;
        if(failure!=null)throw new IllegalStateException("SOURCE wire cleanup",failure);
    }
}
