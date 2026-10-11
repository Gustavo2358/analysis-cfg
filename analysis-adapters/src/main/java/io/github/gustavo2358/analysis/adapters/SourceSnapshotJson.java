package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.air.json.AirJson;
import io.github.gustavo2358.analysis.dependencies.SourceSnapshotBuilder;
import io.github.gustavo2358.analysis.dependencies.source.*;
import io.github.gustavo2358.analysis.solver.*;
import java.io.*;
import java.util.Objects;

/** Closed source mapping over the official physical parser; never an admission certificate. */
final class SourceSnapshotJson implements AutoCloseable {
    private static final AnalysisResources.Phase PHASE=AnalysisResources.Phase.INDEX;
    // token,shape,element,next,count,seen,list,parent-slot; then 30 fixed field slots.
    private static final int WIDTH=40,FIELDS=8;
    private final AnalysisResources resources;
    private final AnalysisResources.Reservation control;
    private SourceSnapshotBuilder builder;
    private PagedLongArray frames;
    private long[] frameHead;
    private long[] fields;
    private TextView text;
    private long depth;
    private boolean closed;
    private SourceSnapshotJson(PageStore pages,AnalysisResources resources) {
        this.resources=Objects.requireNonNull(resources);Objects.requireNonNull(pages);
        // Includes the mapper, fixed recipe, UTF16 block and its single reusable view.
        control=resources.reserve(AnalysisResources.Pool.RESIDENT,6656,PHASE);
        try {frameHead=new long[512];fields=new long[30];text=new TextView();frames=new PagedLongArray(pages,Long.MAX_VALUE,resources,PHASE);builder=new SourceSnapshotBuilder(pages,resources);}
        catch(RuntimeException|Error failure){abort(failure);throw failure;}
    }
    static SourceSnapshot decode(InputStream input,PageStore pages,AnalysisResources resources)throws IOException {
        Objects.requireNonNull(input);
        SourceSnapshot snapshot=null;
        try {
            try(var mapping=new SourceSnapshotJson(pages,resources);
                var physical=new AirJson().stageInput(input,new PagedJsonInputStorage(pages,resources),AirJson.PhysicalProfile.UTF16_JSON)) {
                snapshot=mapping.bind(physical);
            }
            // Teardown may expire the common owner or deadline; never hand out a dead result.
            snapshot.root();
        } catch(IOException|RuntimeException|Error failure) {
            if(snapshot!=null)try{snapshot.close();}catch(RuntimeException|Error cleanup){if(cleanup!=failure)failure.addSuppressed(cleanup);}
            throw failure;
        }
        return snapshot;
    }
    // Fixed prefix only; the full original address space remains page-backed.
    private long frameGet(long index){if(index>=0&&index<frameHead.length){resources.work(1,PHASE);return frameHead[(int)index];}return frames.get(index);}
    private void frameSet(long index,long value){if(index>=0&&index<frameHead.length){resources.work(1,PHASE);frameHead[(int)index]=value;}else frames.set(index,value);}
    private long base(){return Math.multiplyExact(depth-1,WIDTH);}
    private void push(AirJson.StagedInput input,long token,SourceShape type,SourceShape element,int parentSlot) {
        long base=Math.multiplyExact(depth,WIDTH);
        if(base>frames.length()-WIDTH)throw new IllegalStateException("source frame address capacity");
        var kind=input.kind(token);
        boolean valid=type.record()?kind==AirJson.PhysicalKind.OBJECT:switch(type){
            case TEXT->kind==AirJson.PhysicalKind.TEXT;
            case INTEGER->kind==AirJson.PhysicalKind.NUMBER;
            case BOOLEAN->kind==AirJson.PhysicalKind.TRUE||kind==AirJson.PhysicalKind.FALSE;
            case LIST->kind==AirJson.PhysicalKind.ARRAY;
            default->false;
        };
        if(!valid)throw new IllegalArgumentException("source field physical shape");
        long count=type.record()||type==SourceShape.LIST?input.childCount(token):0;
        if(type.record()&&count>type.fieldCount())throw new IllegalArgumentException("closed source fields");
        frameSet(base,token);frameSet(base+1,type.tag());frameSet(base+2,element==null?0:element.tag());
        frameSet(base+4,count);frameSet(base+7,parentSlot);
        if(type==SourceShape.LIST)frameSet(base+6,builder.beginList(Objects.requireNonNull(element)));
        depth=Math.incrementExact(depth);
    }
    private SourceSnapshot bind(AirJson.StagedInput input) {
        push(input,input.root(),SourceShape.ROOT,null,0);
        while(depth!=0) {
            resources.work(1,PHASE);long base=base(),token=frameGet(base);
            var type=SourceShape.forTag(Math.toIntExact(frameGet(base+1)));
            long next=frameGet(base+3),count=frameGet(base+4),result;
            if(type.record()||type==SourceShape.LIST) {
                if(next<count) {
                    long child=input.child(token,next);frameSet(base+3,next+1);
                    if(type==SourceShape.LIST)push(input,child,SourceShape.forTag(Math.toIntExact(frameGet(base+2))),null,0);
                    else {
                        long name=input.name(child);int slot=-1;
                        for(int i=0;i<type.fieldCount();i++)if(input.matches(name,type.field(i).name())){slot=i;break;}
                        if(slot<0)throw new IllegalArgumentException("closed source fields");
                        long bit=1L<<slot,seen=frameGet(base+5);
                        if((seen&bit)!=0)throw new IllegalArgumentException("duplicate source field");
                        frameSet(base+5,seen|bit);var field=type.field(slot);
                        push(input,child,field.shape(),field.shape()==SourceShape.LIST?field.element():null,slot);
                    }
                    continue;
                }
                if(type==SourceShape.LIST)result=builder.finishList(frameGet(base+6));
                else {
                    long seen=frameGet(base+5);
                    for(int i=0;i<type.fieldCount();i++) {
                        if((seen&(1L<<i))==0&&!type.field(i).optional())throw new IllegalArgumentException("required source field");
                        fields[i]=frameGet(base+FIELDS+i);
                    }
                    result=builder.record(type,fields,0,type.fieldCount());
                }
            } else if(type==SourceShape.TEXT) {
                text.open(input,token);try{result=builder.text(text);}finally{text.clear();}
            } else if(type==SourceShape.INTEGER)result=builder.integer(integer(input,token));
            else if(type==SourceShape.BOOLEAN)result=builder.bool(input.kind(token)==AirJson.PhysicalKind.TRUE);
            else throw new IllegalStateException("source closed shape");
            int parentSlot=Math.toIntExact(frameGet(base+7));
            int clearCount=type.record()?FIELDS+type.fieldCount():FIELDS;
            for(int i=0;i<clearCount;i++)frameSet(base+i,0);depth--;
            if(depth==0)return builder.build(result);
            long parent=base();if(frameGet(parent+1)==SourceShape.LIST.tag())builder.append(frameGet(parent+6),result);
            else frameSet(parent+FIELDS+parentSlot,result);
        }
        throw new IllegalStateException("missing source root");
    }
    private int integer(AirJson.StagedInput input,long token) {
        text.open(input,token);
        try {
            int length=text.length(),at=0;boolean negative=length!=0&&text.charAt(0)=='-';if(negative)at++;
            int limit=negative?Integer.MIN_VALUE:-Integer.MAX_VALUE,result=0;
            if(at==length)throw new IllegalArgumentException("source integer required");
            while(at<length) {
                char character=text.charAt(at++);if(character<'0'||character>'9')throw new IllegalArgumentException("source integral number required");
                int digit=character-'0';if(result<limit/10)throw new IllegalArgumentException("source integer range");
                result*=10;if(result<limit+digit)throw new IllegalArgumentException("source integer range");result-=digit;
            }
            return negative?result:-result;
        } finally {text.clear();}
    }
    private static final class TextView implements CharSequence {
        private AirJson.StagedInput input;
        private long token,start=-1;
        private int length,used;
        private final char[] block=new char[256];
        void open(AirJson.StagedInput input,long token){this.input=input;this.token=token;length=Math.toIntExact(input.characterCount(token));start=-1;used=0;}
        void clear(){input=null;token=0;length=used=0;start=-1;java.util.Arrays.fill(block,'\0');}
        @Override public int length(){if(input==null)throw new IllegalStateException("source text view expired");return length;}
        @Override public char charAt(int index){Objects.checkIndex(index,length());if(start<0||index<start||index>=start+used){start=(index/256L)*256;used=(int)Math.min(256,length-start);input.readCharacters(token,start,block,0,used);}return block[(int)(index-start)];}
        @Override public CharSequence subSequence(int start,int end){throw new UnsupportedOperationException("source mapping does not detach text");}
    }
    private void abort(Throwable failure){try{close();}catch(RuntimeException|Error cleanup){if(cleanup!=failure)failure.addSuppressed(cleanup);}}
    @Override public void close() {
        if(closed)return;closed=true;frameHead=null;fields=null;if(text!=null)text.clear();text=null;Throwable failure=null;
        for(AutoCloseable owner:new AutoCloseable[]{frames,builder,control})if(owner!=null)try{owner.close();}catch(Exception|Error cleanup){if(failure==null)failure=cleanup;else if(failure!=cleanup)failure.addSuppressed(cleanup);}
        frames=null;builder=null;depth=0;
        if(failure instanceof RuntimeException error)throw error;if(failure instanceof Error error)throw error;if(failure!=null)throw new IllegalStateException("source mapping cleanup",failure);
    }
}
