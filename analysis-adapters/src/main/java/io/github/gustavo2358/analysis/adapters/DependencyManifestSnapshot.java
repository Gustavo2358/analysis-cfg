package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.air.json.AirJson;
import io.github.gustavo2358.analysis.dependencies.*;
import io.github.gustavo2358.analysis.solver.*;
import java.io.*;
import java.nio.file.*;
import java.util.Objects;

/** Closed dependency-input@1 mapping over official physical staging. All
 * variable correlation tokens, text and path ordinals remain cold. The physical
 * backend transfers to the checked AIR read on success; only staging is retained
 * here. No alternate parser, resident JSON tree or nominal identity DTO. */
final class DependencyManifestSnapshot implements StatementCorrelations,AutoCloseable {
    private static final AnalysisResources.Phase PHASE=AnalysisResources.Phase.DECODE;
    private static final int WIDTH=13;
    private static final String[] ROOT={"schema","version","air","qualifiedSource","sourceSha256","correlations"};
    private static final String[] FILE={"path","sha256"},LINK={"source","operation","label","origin"};
    private static final String[] STATEMENT={"unit","handle"},SOURCE_UNIT={"compilationUnitId","structuralPath","canonicalProgramName"};
    private static final String[] UNIT_ID={"domain","publication","unit","localId"},PUBLICATION_ID={"domain","publication","localId"};
    private final AnalysisResources resources;
    private AnalysisResources.Reservation control,binding;
    private FilePageStore pages;
    private Path directory,airPath,sourcePath;
    private String airSha,sourceSha,documentSha;
    private AirJson.StagedInput physical;
    private PagedLongArray rows;
    private char[] block;
    private long count;
    private volatile SnapshotProgram program;
    private boolean transferred,closed;
    private DependencyManifestSnapshot(AnalysisResources resources){this.resources=resources;}
    @SuppressWarnings("try")
    static DependencyManifestSnapshot read(Path path,AnalysisResources resources)throws IOException {
        Objects.requireNonNull(path);Objects.requireNonNull(resources);var owner=new DependencyManifestSnapshot(resources);
        try {
            owner.control=resources.reserve(AnalysisResources.Pool.RESIDENT,2048,PHASE);owner.block=new char[256];
            owner.directory=Files.createTempDirectory("analysis-dependency-input-");
            long available=resources.limits().heapBytes()-resources.heapUsed(),capacity=Math.max(1,(available/3-512)/4256);int cache=1;
            while(cache<4096&&(long)cache*2<=capacity)cache*=2;
            owner.pages=new FilePageStore(owner.directory,4096,cache,resources,PHASE);
            owner.rows=new PagedLongArray(owner.pages,Long.MAX_VALUE,resources,PHASE);
            try(var file=resources.reserve(AnalysisResources.Pool.OPEN_FILES,1,PHASE);
                var stream=new FilterInputStream(JsonFiles.input(path)) {
                    @Override public int read()throws IOException{resources.work(1,PHASE);return in.read();}
                    @Override public int read(byte[] value,int offset,int length)throws IOException{resources.work(1,PHASE);return in.read(value,offset,length);}
                }) {
                owner.physical=new AirJson().stageInput(stream,new PagedJsonInputStorage(owner.pages,resources),AirJson.PhysicalProfile.UTF16_JSON);
                owner.map(path.toAbsolutePath().getParent());
            }
            owner.physical.root();return owner;
        }catch(IOException|RuntimeException|Error failure){owner.abort(failure);throw failure;}
    }
    private void object(long token,String[] fields){
        if(physical.kind(token)!=AirJson.PhysicalKind.OBJECT||physical.childCount(token)!=fields.length)throw new IllegalArgumentException("closed dependency input fields");
        long seen=0;for(long i=0;i<fields.length;i++){
            long child=physical.child(token,i),name=physical.name(child);int slot=-1;
            for(int j=0;j<fields.length;j++)if(physical.matches(name,fields[j])){slot=j;break;}
            if(slot<0||(seen&(1L<<slot))!=0)throw new IllegalArgumentException("closed dependency input fields");seen|=1L<<slot;
        }
    }
    private long field(long object,String name){
        for(long i=0,n=physical.childCount(object);i<n;i++){long child=physical.child(object,i);if(physical.matches(physical.name(child),name))return child;}
        throw new IllegalArgumentException("required dependency input field");
    }
    private long text(long object,String name){long token=field(object,name);textToken(token);return token;}
    private void textToken(long token){
        if(physical.kind(token)!=AirJson.PhysicalKind.TEXT)throw new IllegalArgumentException("dependency input text required");
        long length=physical.characterCount(token);boolean nonblank=false;
        // String.isBlank's code-point semantics: surrogate pairs cannot denote
        // a Java whitespace code point; isolated UTF-16 units remain exact data.
        for(long offset=0;offset<length;){int n=(int)Math.min(block.length,length-offset);physical.readCharacters(token,offset,block,0,n);for(int i=0;i<n;i++)if(!Character.isWhitespace(block[i]))nonblank=true;offset+=n;}
        if(!nonblank)throw new IllegalArgumentException("dependency input nonblank text required");
    }
    private String scalar(long token,long extra){
        long length=physical.characterCount(token);control.grow(Math.addExact(192,Math.multiplyExact(6,Math.addExact(length,extra))),PHASE);
        try(var value=physical.project(token)){return value.value();}
    }
    private Path filePath(long object,Path base){return base.resolve(scalar(text(object,"path"),base.toString().length()));}
    private void map(Path base){
        long root=physical.root();object(root,ROOT);
        if(!physical.matches(text(root,"schema"),"dependency-input")||!physical.matches(text(root,"version"),"1.0.0"))throw new IllegalArgumentException("unsupported dependency input");
        long air=field(root,"air"),source=field(root,"qualifiedSource");object(air,FILE);object(source,FILE);
        airPath=filePath(air,base);airSha=scalar(text(air,"sha256"),0);sourcePath=filePath(source,base);sourceSha=scalar(text(source,"sha256"),0);documentSha=scalar(text(root,"sourceSha256"),0);
        long links=field(root,"correlations");if(physical.kind(links)!=AirJson.PhysicalKind.ARRAY)throw new IllegalArgumentException("correlations array required");count=physical.childCount(links);
        for(long i=0;i<count;i++){
            resources.work(1,PHASE);long row=physical.child(links,i);object(row,LINK);long statement=field(row,"source");object(statement,STATEMENT);long unit=field(statement,"unit");object(unit,SOURCE_UNIT);
            long at=Math.multiplyExact(i,WIDTH);rows.set(at,text(unit,"compilationUnitId"));rows.set(at+1,text(unit,"canonicalProgramName"));rows.set(at+2,text(statement,"handle"));
            long operation=field(row,"operation"),label=field(row,"label"),origin=field(row,"origin");identity(operation,"operation",true);identity(label,"label",true);identity(origin,"origin",false);
            rows.set(at+3,text(operation,"publication"));rows.set(at+4,text(operation,"unit"));rows.set(at+5,text(operation,"localId"));
            rows.set(at+6,text(label,"publication"));rows.set(at+7,text(label,"unit"));rows.set(at+8,text(label,"localId"));rows.set(at+9,text(origin,"publication"));rows.set(at+10,text(origin,"localId"));
            long path=field(unit,"structuralPath");if(physical.kind(path)!=AirJson.PhysicalKind.ARRAY||physical.childCount(path)==0)throw new IllegalArgumentException("unit path");
            long n=physical.childCount(path);for(long p=0;p<n;p++)if(integer(physical.child(path,p))<0)throw new IllegalArgumentException("unit path");rows.set(at+11,path);rows.set(at+12,n);
        }
    }
    private void identity(long token,String domain,boolean unit){object(token,unit?UNIT_ID:PUBLICATION_ID);if(!physical.matches(text(token,"domain"),domain))throw new IllegalArgumentException("identity domain");}
    private int integer(long token){
        if(physical.kind(token)!=AirJson.PhysicalKind.NUMBER)throw new IllegalArgumentException("integer required");long n=physical.characterCount(token),offset=0;boolean negative=false;int result=0,limit=-Integer.MAX_VALUE;
        for(long at=0;at<n;){int count=(int)Math.min(block.length,n-at);physical.readCharacters(token,at,block,0,count);
            for(int i=0;i<count;i++,offset++){char c=block[i];if(offset==0&&c=='-'){negative=true;limit=Integer.MIN_VALUE;continue;}if(c<'0'||c>'9'||result<limit/10)throw new IllegalArgumentException("integer required");int digit=c-'0';result*=10;if(result<limit+digit)throw new IllegalArgumentException("integer required");result-=digit;}at+=count;
        }
        if(n==0||(negative&&n==1))throw new IllegalArgumentException("integer required");return negative?result:-result;
    }
    private void unboundOpen(){if(closed)throw new IllegalStateException("dependency manifest expired");physical.root();}
    FilePageStore pages(){unboundOpen();return pages;}Path directory(){unboundOpen();return directory;}
    Path airPath(){unboundOpen();return airPath;}String airSha(){unboundOpen();return airSha;}
    Path sourcePath(){unboundOpen();return sourcePath;}String sourceSha(){unboundOpen();return sourceSha;}String documentSha(){unboundOpen();return documentSha;}
    void transferBackend(){unboundOpen();transferred=true;}
    synchronized void bind(SnapshotProgram value,AnalysisResources execution){
        unboundOpen();Objects.requireNonNull(value);Objects.requireNonNull(execution);value.admission();execution.requireExecutionProgram(value);
        if(program!=null){if(program!=value)throw new IllegalArgumentException("foreign correlation program");return;}
        try {binding=execution.reserve(AnalysisResources.Pool.RESIDENT,128,AnalysisResources.Phase.INDEX);program=value;binding.onOwnerClose(this::close);open();}
        catch(RuntimeException|Error failure){abort(failure);throw failure;}
    }
    private void open(){unboundOpen();var value=program;if(value==null)throw new IllegalStateException("unbound dependency correlations");value.admission();}
    private long row(long ordinal){if(ordinal<0||ordinal>=count)throw new IndexOutOfBoundsException("correlation ordinal");return Math.multiplyExact(ordinal,WIDTH);}
    private long token(long ordinal,Field field){return rows.get(Math.addExact(row(ordinal),Objects.requireNonNull(field).ordinal()));}
    @Override public synchronized SnapshotProgram program(){open();return program;}
    @Override public synchronized long size(){open();return count;}
    @Override public synchronized long characterCount(long ordinal,Field field){open();try{return physical.characterCount(token(ordinal,field));}catch(RuntimeException|Error failure){abort(failure);throw failure;}}
    @Override public synchronized void readCharacters(long ordinal,Field field,long offset,char[] destination,int at,int length){open();try{physical.readCharacters(token(ordinal,field),offset,destination,at,length);}catch(RuntimeException|Error failure){abort(failure);throw failure;}}
    @Override public synchronized long structuralPathCount(long ordinal){open();try{return rows.get(row(ordinal)+12);}catch(RuntimeException|Error failure){abort(failure);throw failure;}}
    @Override public synchronized int structuralPath(long ordinal,long coordinate){open();try{long base=row(ordinal),n=rows.get(base+12);if(coordinate<0||coordinate>=n)throw new IndexOutOfBoundsException("correlation path ordinal");return integer(physical.child(rows.get(base+11),coordinate));}catch(RuntimeException|Error failure){abort(failure);throw failure;}}
    private void abort(Throwable failure){try{close();}catch(RuntimeException|Error cleanup){if(failure!=cleanup)failure.addSuppressed(cleanup);}}
    @Override public synchronized void close(){
        if(closed)return;closed=true;program=null;block=null;airPath=sourcePath=null;airSha=sourceSha=documentSha=null;Throwable failure=null;
        for(AutoCloseable owner:new AutoCloseable[]{binding,rows,physical,control,transferred?null:pages})if(owner!=null)try{owner.close();}catch(Exception|Error cleanup){if(failure==null)failure=cleanup;else if(failure!=cleanup)failure.addSuppressed(cleanup);}
        binding=null;rows=null;physical=null;control=null;pages=null;if(!transferred&&directory!=null)try{Files.deleteIfExists(directory);}catch(IOException cleanup){if(failure==null)failure=cleanup;else failure.addSuppressed(cleanup);}directory=null;
        if(failure instanceof RuntimeException error)throw error;if(failure instanceof Error error)throw error;if(failure!=null)throw new IllegalStateException("dependency manifest cleanup",failure);
    }
}
