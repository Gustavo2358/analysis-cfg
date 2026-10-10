package io.github.gustavo2358.analysis.dependencies;

import io.github.gustavo2358.analysis.dependencies.source.SourceShape;
import io.github.gustavo2358.analysis.dependencies.source.SourceSnapshot;
import io.github.gustavo2358.analysis.solver.*;
import java.util.Objects;

/** Reachable closed-wire/local laws ONLY. Borrows storage; never issues a certificate. */
final class SourceSnapshotConstraints implements AutoCloseable {
    private static final AnalysisResources.Phase PHASE=AnalysisResources.Phase.VALIDATION;
    private SourceSnapshot source;
    private final PageStore pages;
    private final AnalysisResources resources;
    private AnalysisResources.Reservation control;
    private PagedLongArray queue;
    private PagedLongIndex seen;
    private char[] characters;
    private long read,write;
    private boolean closed;

    static void check(SourceSnapshot source,PageStore pages,AnalysisResources resources) {
        Objects.requireNonNull(source);Objects.requireNonNull(pages);Objects.requireNonNull(resources);
        var capacity=resources.reserve(AnalysisResources.Pool.RESIDENT,2048,PHASE);
        try(var checker=new SourceSnapshotConstraints(source,pages,resources,capacity)){checker.walk();}
    }
    private SourceSnapshotConstraints(SourceSnapshot source,PageStore pages,AnalysisResources resources,AnalysisResources.Reservation capacity) {
        this.source=source;this.pages=pages;this.resources=resources;control=capacity;
        try {
            characters=new char[256];queue=new PagedLongArray(pages,Long.MAX_VALUE,resources,PHASE);
            seen=new PagedLongIndex(pages,resources,PHASE,this::compareQueued);
            control.onOwnerClose(this::close);
        }catch(RuntimeException|Error failure){cleanup(failure);throw failure;}
    }
    private int compareQueued(long first,long second) {
        long a=Math.multiplyExact(first-1,3),b=Math.multiplyExact(second-1,3);
        int order=Long.compare(queue.get(a),queue.get(b));
        return order!=0?order:Long.compare(queue.get(a+2),queue.get(b+2));
    }
    private void push(long ref,SourceShape element,int authority) {
        if(ref==0)return;
        var shape=source.shape(ref);if(shape!=SourceShape.LIST&&!shape.record())return;
        long id=Math.incrementExact(write),offset=Math.multiplyExact(id-1,3);
        queue.set(offset,ref);queue.set(offset+1,element==null?0:element.tag());queue.set(offset+2,authority);
        // Queue ordinal is a stable complete (reference, authority) key. The next
        // uncommitted slot is a probe; a duplicate is not retained as a queue item.
        if(seen.intern(id,id)==id)write=id;
    }
    private void walk() {
        push(source.root(),null,0);
        while(read<write) {
            if(closed)throw new IllegalStateException("source constraint walk expired");
            resources.work(1,PHASE);
            long at=Math.multiplyExact(read++,3),ref=queue.get(at);int authority=Math.toIntExact(queue.get(at+2));
            var shape=source.shape(ref);
            if(shape==SourceShape.LIST) {
                var element=SourceShape.forTag(Math.toIntExact(queue.get(at+1)));
                for(long i=0,n=source.size(ref,element);i<n;i++){resources.work(1,PHASE);push(source.element(ref,element,i),null,authority);}
                continue;
            }
            if(shape==SourceShape.NOMINAL_VALUES)authority=nominalAuthority(field(ref,0));
            local(ref,shape,authority);
            for(int i=0;i<shape.fieldCount();i++) {
                var descriptor=shape.field(i);long child=source.field(ref,shape,i);
                if(child==0)continue;
                if(descriptor.shape()==SourceShape.TEXT&&!payload(shape,i))nonblank(child);
                if(descriptor.shape().record())push(child,null,authority);
                else if(descriptor.shape()==SourceShape.LIST&&descriptor.element().record())push(child,descriptor.element(),authority);
            }
        }
        source.root(); // A native owner cannot expire during final teardown and become success.
    }
    private static boolean payload(SourceShape shape,int ordinal) {
        return ordinal==1&&(shape==SourceShape.VALUE||shape==SourceShape.TERM||shape==SourceShape.SEED||shape==SourceShape.INITIAL);
    }
    private long field(long ref,int ordinal){return source.field(ref,source.shape(ref),ordinal);}
    private int number(long ref,int ordinal){return source.integer(field(ref,ordinal));}
    private boolean flag(long ref,int ordinal){return source.bool(field(ref,ordinal));}
    private long size(long ref,int ordinal) {
        var shape=source.shape(ref);long list=source.field(ref,shape,ordinal);
        return list==0?0:source.size(list,shape.field(ordinal).element());
    }
    private long item(long ref,int ordinal,long at) {
        var shape=source.shape(ref);return source.element(source.field(ref,shape,ordinal),shape.field(ordinal).element(),at);
    }
    private boolean is(long ref,int ordinal,String value){return source.matches(field(ref,ordinal),value);}
    private boolean any(long ref,int ordinal,String... values){long text=field(ref,ordinal);for(String value:values)if(source.matches(text,value))return true;return false;}
    private void one(long ref,int ordinal){require(size(ref,ordinal)<=1,"optional cardinality");}
    private static void require(boolean yes,String message){if(!yes)throw new IllegalArgumentException(message);}
    private int nominalAuthority(long ref) {
        for(int version=1;version<=4;version++)if(source.matches(ref,"NOMINAL_TEXT_SOURCE_V"+version))return version;
        throw new IllegalArgumentException("nominal value authority");
    }
    private void local(long ref,SourceShape shape,int authority) {
        switch(shape) {
            case ROOT -> {require(is(ref,0,"qualified-source-dependencies")&&any(ref,1,"1.0.0","1.1.0","1.2.0","1.3.0","1.4.0","1.5.0","1.6.0"),"unsupported source contract");one(ref,4);}
            case DOCUMENT -> {require(any(ref,0,"cobol-semantic-product","cobol-semantic-compilation"),"source schema");digest(field(ref,2));}
            case AIR_CORRELATION -> digest(field(ref,1));
            case UNIT_ID -> {require(size(ref,1)>0,"unit path");for(long i=0,n=size(ref,1);i<n;i++){resources.work(1,PHASE);require(source.integer(item(ref,1,i))>=0,"unit path");}}
            case LOCATION -> require(number(ref,1)>=1&&number(ref,3)>=number(ref,1)&&number(ref,2)>=0&&number(ref,4)>=0,"source location");
            case INCLUDE -> require(number(ref,3)>=1,"include line");
            case VALUE -> require(is(ref,0,"TEXT")&&number(ref,2)>=0&&codePoints(field(ref,1))==number(ref,2),"qualified text value");
            case OCCURRENCE -> {
                one(ref,6);one(ref,7);require(any(ref,5,"LITERAL","COMPUTED","UNAVAILABLE"),"target kind");
                require(size(ref,7)==0||is(ref,5,"LITERAL")&&size(ref,6)==1,"literal value authority");
                require(flag(ref,8)==(size(ref,7)==0),"value remainder");
                for(long i=0,n=size(ref,6);i<n;i++)require(field(field(item(ref,6,i),0),0)==field(ref,0),"operand owner");
                require(is(ref,1,"COBOL")?is(ref,2,"CALL")&&is(ref,3,"PROGRAM"):is(ref,1,"CICS")&&any(ref,3,"PROGRAM","FILE"),"dependency family");
            }
            case CONDITION_STATE -> require(any(ref,0,"PGMIDERR","ERROR")&&size(ref,3)>0,"supported condition state");
            case SUPPORT -> {
                one(ref,1);one(ref,2);require(any(ref,0,"ENTRY_UNKNOWN","ACTIVE","CANCELED","DEACTIVATED","CANCELED_UNKNOWN","UNKNOWN"),"support kind");
                require(any(ref,3,"NONE","RESET_HISTORY_UNAVAILABLE","RESET_WITHOUT_CANCELED_EVIDENCE","CALL_EFFECT_UNAVAILABLE","HANDLER_OPERATION_UNAVAILABLE","SOURCE_REENTRY_UNDEFINED"),"support cause");
                require(any(ref,0,"ACTIVE","CANCELED","DEACTIVATED")==(size(ref,1)>0)&&size(ref,1)==size(ref,2),"support correlation");
                require(is(ref,0,"UNKNOWN")!=is(ref,3,"NONE"),"unknown cause");
                boolean pgmiderr=false,error=false;
                for(long i=0,n=size(ref,4);i<n;i++) {
                    long condition=item(ref,4,i);resources.work(1,PHASE);
                    if(is(condition,0,"PGMIDERR")){require(!pgmiderr,"duplicate condition");pgmiderr=true;}
                    else {require(is(condition,0,"ERROR")&&!error,"invalid/duplicate condition");error=true;}
                }
            }
            case TARGET -> {one(ref,2);one(ref,4);one(ref,5);require(any(ref,1,"LABEL_LOCAL","LABEL_UNRESOLVED","PROGRAM_LITERAL","PROGRAM_DATA","PROGRAM_UNRESOLVED"),"target form");require(size(ref,3)>0,"registration authority");}
            case PROOF -> require(any(ref,1,"LOCAL_GRAMMAR","RESOLVED_TARGET","EXPANDED_INCLUDE","INPUT_REGION_ISOLATION","PARTIAL_UNKNOWN","CONTROL_POSSIBILITY"),"proof kind");
            case GUARD -> require(any(ref,2,"CONDITION_RAISED","DEFAULT_DISPOSITION_APPLIES"),"runtime guard");
            case EVENT -> {
                require(any(ref,2,"EXPLICIT_ABEND","XCTL_PGMIDERR","LINK_PGMIDERR"),"event origin");
                require(is(ref,3,"TASK_ABEND")&&is(ref,5,"CURRENT_EXECUTION_LOGICAL_LEVEL")&&is(ref,6,"UNAVAILABLE"),"event scope");
                require(any(ref,4,"HANDLER_ELIGIBLE","HANDLERS_BYPASSED")&&size(ref,8)>0,"event authority");
            }
            case SELECTION -> {one(ref,3);one(ref,4);one(ref,5);require(size(ref,3)==size(ref,4)&&(size(ref,3)>0||size(ref,5)==0),"selection shape");}
            case DERIVATION -> {one(ref,1);one(ref,3);one(ref,6);require(size(ref,5)>0&&(size(ref,3)==0||size(ref,1)>0),"derivation authority");}
            case NATIVE_FILE_NAME -> require(size(ref,4)>0,"native file declaration provenance");
            case NATIVE_FILE_USE -> {require(number(ref,1)>=0,"native file ordinal");require(any(ref,3,"OPEN","READ","WRITE","REWRITE","DELETE_RECORD","START","CLOSE","RELEASE","RETURN","SORT","MERGE"),"native file command");one(ref,5);require(!flag(ref,4)||size(ref,5)==0,"local sort work is not an external file");}
            case SEED -> require(any(ref,2,"DECLARATIVE_POSSIBILITY","DECLARATIVE_INVARIANT","DECLARATIVE_VALUE"),"source seed authority");
            case UNCERTAINTY -> require(any(ref,1,"MISSING_COPY","MODEL_STORAGE","OPAQUE_INCLUDE","UNLOCATED_INPUT"),"source uncertainty kind");
            case NOMINAL_VALUES -> {
                require(authority==4?field(ref,5)!=0:field(ref,5)==0,"conditional table fields");
                for(long i=0,n=size(ref,1);i<n;i++){resources.work(1,PHASE);long symbol=item(ref,1,i);require(authority==1?field(symbol,2)==0:field(symbol,2)!=0,"conditional model marker");}
            }
            case SYMBOL -> require(number(ref,1)>0,"positive nominal text extent");
            case TERM -> {
                boolean unary=any(ref,0,"UPPER_ASCII","TRIM_SPACES","TRIM_LEADING_SPACES","TRIM_TRAILING_SPACES");
                require(is(ref,0,"CHOICE")?size(ref,2)>=2:unary?size(ref,2)==1:size(ref,2)==0,"nominal expression arity");
                require(is(ref,0,"CHOICE")||unary||any(ref,0,"READ","LITERAL","SPACES","LOW_VALUES","HIGH_VALUES","UNKNOWN"),"nominal term kind");
                if(is(ref,0,"READ"))nonblank(field(ref,1));else if(!is(ref,0,"LITERAL"))require(source.characterCount(field(ref,1))==0,"nonliteral payload");
                require(authority>=3||size(ref,2)==0,"expression requires V3");require(authority==4||!is(ref,0,"CHOICE"),"choice requires V4");
            }
            case PREDICATE -> require(is(ref,0,"EQ")?size(ref,1)==2&&size(ref,2)==0:is(ref,0,"NOT")?size(ref,1)==0&&size(ref,2)==1:any(ref,0,"AND","OR")&&size(ref,1)==0&&size(ref,2)>=2,"nominal predicate shape");
            case TABLE_FIELD -> {
                if(size(ref,1)>1)try(var initial=new PagedLongIndex(pages,resources,PHASE)) {
                    for(long i=0,n=size(ref,1);i<n;i++){resources.work(1,PHASE);long ordinal=Math.incrementExact(i);require(initial.intern(item(ref,1,i),ordinal)==ordinal,"duplicate table initializer");}
                }
            }
            case STATEMENT_ID,OPERAND_ID,PROVENANCE,STATEMENT,OPERAND,NODE,REGISTRATION,FRONTIER,
                    UNIT_EVIDENCE,NOMINAL_VALUE_EVIDENCE,DECLARATION,BRANCH,ASSIGNMENT,CONDITION,QUERY,INITIAL -> { /* Type presence and nonblank descriptors checked below. */ }
            default -> throw new IllegalArgumentException("source record required");
        }
    }
    private void nonblank(long ref) {
        for(long offset=0,length=source.characterCount(ref);offset<length;) {
            int count=(int)Math.min(characters.length,length-offset);source.readCharacters(ref,offset,characters,0,count);resources.work(count,PHASE);
            for(int i=0;i<count;i++)if(!Character.isWhitespace(characters[i]))return;
            offset+=count;
        }
        throw new IllegalArgumentException("empty identity/descriptor");
    }
    private long codePoints(long ref) {
        long count=0;boolean high=false;
        for(long offset=0,length=source.characterCount(ref);offset<length;) {
            int n=(int)Math.min(characters.length,length-offset);source.readCharacters(ref,offset,characters,0,n);resources.work(n,PHASE);
            for(int i=0;i<n;i++){char value=characters[i];if(!(high&&Character.isLowSurrogate(value)))count=Math.incrementExact(count);high=Character.isHighSurrogate(value);}
            offset+=n;
        }
        return count;
    }
    private void digest(long ref) {
        require(source.characterCount(ref)==64,"SHA-256 digest");source.readCharacters(ref,0,characters,0,64);resources.work(64,PHASE);
        for(int i=0;i<64;i++){char c=characters[i];require(c>='0'&&c<='9'||c>='a'&&c<='f',"SHA-256 digest");}
    }
    private void cleanup(Throwable failure){try{close();}catch(RuntimeException|Error cleanup){if(cleanup!=failure)failure.addSuppressed(cleanup);}}
    @Override public void close() {
        if(closed)return;closed=true;source=null;characters=null;read=write=0;Throwable failure=null;
        for(AutoCloseable owner:new AutoCloseable[]{seen,queue,control})if(owner!=null)try{owner.close();}catch(Exception|Error cleanup){if(failure==null)failure=cleanup;else if(failure!=cleanup)failure.addSuppressed(cleanup);}
        seen=null;queue=null;control=null;
        if(failure instanceof RuntimeException error)throw error;if(failure instanceof Error error)throw error;if(failure!=null)throw new IllegalStateException("source constraints cleanup",failure);
    }
}
