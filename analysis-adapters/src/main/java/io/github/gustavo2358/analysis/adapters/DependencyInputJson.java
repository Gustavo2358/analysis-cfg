package io.github.gustavo2358.analysis.adapters;

import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.analysis.dependencies.DependencyInput;
import io.github.gustavo2358.analysis.dependencies.SourceAdmissionSnapshot;
import io.github.gustavo2358.analysis.dependencies.SnapshotProgram;
import io.github.gustavo2358.analysis.dependencies.StatementCorrelations;
import io.github.gustavo2358.analysis.dependencies.source.QualifiedSourceDependencies;
import io.github.gustavo2358.analysis.solver.AnalysisResources;

/** Closed, explicit dependency-input@1 adapter. Never searches for adjacent files. */
public final class DependencyInputJson {
    private final ObjectMapper mapper=JsonMapper.builder(JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build()).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    public boolean isBundle(Path path)throws IOException {
        return isBundle(path,()->{});
    }
    private boolean isBundle(Path path,Runnable progress)throws IOException {
        try(var input=new FilterInputStream(JsonFiles.input(path)) {
            @Override public int read()throws IOException{progress.run();return in.read();}
            @Override public int read(byte[] bytes,int offset,int length)throws IOException{progress.run();return in.read(bytes,offset,length);}
        };var parser=mapper.getFactory().createParser(input)) {
            if(parser.nextToken()!=JsonToken.START_OBJECT)return false;
            while(parser.nextToken()==JsonToken.FIELD_NAME) {
                String name=parser.currentName();parser.nextToken();
                // AIR has its own closed codec. Do not scan its complete body merely
                // to distinguish the small manifest envelope from an AIR input.
                if(name.equals("airVersion"))return false;
                if(name.equals("schema"))return parser.currentToken()==JsonToken.VALUE_STRING&&parser.getText().equals("dependency-input");
                parser.skipChildren();
            }
            return false;
        }
    }
    /** Native bundle ownership; closing it releases the checked AIR and manifest budget. */
    public static final class NativeInput implements AutoCloseable {
        private DataflowAirReader.SnapshotRead read;
        private SourceDescriptor descriptor;
        private SourceAdmissionSnapshot source;
        private SnapshotProgram sourceProgram;
        private DependencyManifestSnapshot manifest;
        private EmptyCorrelations empty;
        private final AnalysisResources.Reservation manifestCapacity;
        private NativeInput(DataflowAirReader.SnapshotRead read,SourceDescriptor source,
                DependencyManifestSnapshot manifest,AnalysisResources.Reservation capacity) {
            this.read=read;this.descriptor=source;this.manifest=manifest;manifestCapacity=capacity;
        }
        private void open(){if(read==null)throw new IllegalStateException("native dependency input is closed");}
        public DataflowAirReader.SnapshotRead air(){open();return read;}
        public boolean hasSource(){open();return descriptor!=null;}
        /** Decode/admit once, after the legitimate checked program exists. */
        public Optional<SourceAdmissionSnapshot> source(SnapshotProgram program,AnalysisResources resources)throws IOException {
            open();Objects.requireNonNull(program);Objects.requireNonNull(resources);resources.requireExecutionProgram(program);
            if(descriptor==null)return Optional.empty();
            if(source!=null){if(sourceProgram!=program)throw new IllegalArgumentException("foreign SOURCE input program");source.snapshot(read.borrowedPages(),resources);return Optional.of(source);}
            if(program.admission()!=read.checked())throw new IllegalArgumentException("foreign SOURCE input admission");
            source=SourceSnapshotInput.read(descriptor.path(),program,read.borrowedPages(),resources,read.sha256(),Optional.of(descriptor.wireSha256()),Optional.of(descriptor.documentSha256()));sourceProgram=program;return Optional.of(source);
        }
        /** Bound immutable data; the core admits complete correlation authority. */
        public StatementCorrelations correlations(SnapshotProgram program,AnalysisResources resources){
            open();Objects.requireNonNull(program);Objects.requireNonNull(resources);program.admission();resources.requireExecutionProgram(program);
            if(program.admission()!=read.checked())throw new IllegalArgumentException("foreign correlation input admission");
            if(manifest!=null){manifest.bind(program,resources);return manifest;}
            if(empty==null)empty=new EmptyCorrelations(this,program);else if(empty.program()!=program)throw new IllegalArgumentException("foreign correlation input program");return empty;
        }
        @Override public void close(){
            if(read==null)return;var owned=read;var sourceOwner=source;var manifestOwner=manifest;read=null;source=null;sourceProgram=null;descriptor=null;manifest=null;empty=null;Throwable failure=null;
            for(AutoCloseable owner:new AutoCloseable[]{sourceOwner,manifestOwner,owned,manifestCapacity})if(owner!=null)try{owner.close();}catch(Exception|Error cleanup){if(failure==null)failure=cleanup;else if(failure!=cleanup)failure.addSuppressed(cleanup);}
            if(failure instanceof RuntimeException error)throw error;if(failure instanceof Error error)throw error;if(failure!=null)throw new IllegalStateException("native dependency input cleanup",failure);
        }
    }
    private static final class EmptyCorrelations implements StatementCorrelations {
        private final NativeInput input;private final SnapshotProgram program;
        private EmptyCorrelations(NativeInput input,SnapshotProgram program){this.input=input;this.program=program;}
        private void open(){input.open();program.admission();}
        @Override public SnapshotProgram program(){open();return program;}
        @Override public long size(){open();return 0;}
        @Override public long characterCount(long row,Field field){open();throw new IndexOutOfBoundsException("empty correlations");}
        @Override public void readCharacters(long row,Field field,long offset,char[] out,int at,int count){open();throw new IndexOutOfBoundsException("empty correlations");}
        @Override public long structuralPathCount(long row){open();throw new IndexOutOfBoundsException("empty correlations");}
        @Override public int structuralPath(long row,long coordinate){open();throw new IndexOutOfBoundsException("empty correlations");}
    }
    private record SourceDescriptor(Path path,String wireSha256,String documentSha256){ }
    /** Preserve dependency-input@1 hashes/correlations without any resident AIR reconstruction. */
    @SuppressWarnings("try") // File-capacity leases govern lifetime, not the decoded value.
    public NativeInput readSnapshot(Path path,DataflowAirReader reader,AnalysisResources resources)throws IOException {
        var capacity=resources.reserve(AnalysisResources.Pool.RESIDENT,256,AnalysisResources.Phase.DECODE);
        DataflowAirReader.SnapshotRead read=null;DependencyManifestSnapshot manifest=null;
        try {
            resources.work(1,AnalysisResources.Phase.DECODE);
            boolean bundle;
            try(var file=resources.reserve(AnalysisResources.Pool.OPEN_FILES,1,AnalysisResources.Phase.DECODE)){bundle=isBundle(path,()->resources.work(1,AnalysisResources.Phase.DECODE));}
            if(!bundle){read=reader.readSnapshot(path,resources);return new NativeInput(read,null,null,capacity);}
            manifest=DependencyManifestSnapshot.read(path,resources);
            read=reader.readSnapshot(manifest.airPath(),resources,manifest.directory(),JsonFiles::input,manifest.pages());manifest.transferBackend();
            if(!read.sha256().equals(manifest.airSha()))throw new IllegalArgumentException("AIR digest mismatch");
            var descriptor=new SourceDescriptor(manifest.sourcePath(),manifest.sourceSha(),manifest.documentSha());
            return new NativeInput(read,descriptor,manifest,capacity);
        }catch(IOException|RuntimeException|Error failure) {
            if(manifest!=null)try{manifest.close();}catch(RuntimeException|Error cleanup){if(failure!=cleanup)failure.addSuppressed(cleanup);}
            if(read!=null)try{read.close();}catch(RuntimeException|Error cleanup){failure.addSuppressed(cleanup);}
            capacity.close();throw failure;
        }
    }
    public DependencyInput read(Path path,DataflowAirReader reader)throws IOException {
        var root=mapper.readTree(JsonFiles.read(path));keys(root,"schema","version","air","qualifiedSource","sourceSha256","correlations");
        if(!text(root,"schema").equals("dependency-input")||!text(root,"version").equals("1.0.0"))throw new IllegalArgumentException("unsupported dependency input");
        var base=path.toAbsolutePath().getParent();var air=root.get("air");keys(air,"path","sha256");
        var read=reader.read(base.resolve(text(air,"path")));
        if(!read.sha256().equals(text(air,"sha256")))throw new IllegalArgumentException("AIR digest mismatch");
        var source=root.get("qualifiedSource");keys(source,"path","sha256");
        var digest=sha256();io.github.gustavo2358.analysis.dependencies.source.QualifiedSourceDependencies evidence;
        try(var input=new DigestInputStream(JsonFiles.input(base.resolve(text(source,"path"))),digest)) {
            evidence=new QualifiedSourceJson().decode(input);
        }
        if(!HexFormat.of().formatHex(digest.digest()).equals(text(source,"sha256")))throw new IllegalArgumentException("source evidence digest mismatch");
        if(!evidence.source().sha256().equals(text(root,"sourceSha256"))||evidence.air().size()!=1||!evidence.air().getFirst().sha256().equals(read.sha256()))throw new IllegalArgumentException("source/AIR snapshot mismatch");
        return new DependencyInput(read.publication(),Optional.of(evidence),correlations(root),read.checked());
    }
    private static List<DependencyInput.StatementCorrelation> correlations(JsonNode root) {
        var list=root.get("correlations");if(!list.isArray())throw new IllegalArgumentException("correlations array required");
        var links=new ArrayList<DependencyInput.StatementCorrelation>();
        for(var link:list) {
            keys(link,"source","operation","label","origin");
            var operation=link.get("operation");id(operation,"operation",true);var label=link.get("label");id(label,"label",true);var origin=link.get("origin");id(origin,"origin",false);
            links.add(new DependencyInput.StatementCorrelation(QualifiedSourceJson.readStatementId(link.get("source")),
                new OperationId(unit(operation),text(operation,"localId")),new LabelId(unit(label),text(label,"localId")),new OriginId(new PublicationId(text(origin,"publication")),text(origin,"localId"))));
        }
        return List.copyOf(links);
    }
    private static UnitId unit(JsonNode n){return new UnitId(new PublicationId(text(n,"publication")),text(n,"unit"));}
    private static void id(JsonNode n,String domain,boolean unit){if(unit)keys(n,"domain","publication","unit","localId");else keys(n,"domain","publication","localId");if(!text(n,"domain").equals(domain))throw new IllegalArgumentException("identity domain");}
    private static String text(JsonNode n,String key){var v=n.get(key);if(v==null||!v.isTextual()||v.textValue().isBlank())throw new IllegalArgumentException("text required: "+key);return v.textValue();}
    private static void keys(JsonNode n,String... fields){if(n==null||!n.isObject())throw new IllegalArgumentException("object required");var actual=new HashSet<String>();n.fieldNames().forEachRemaining(actual::add);if(!actual.equals(Set.of(fields)))throw new IllegalArgumentException("closed dependency input fields");}
    private static MessageDigest sha256(){try{return MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException ex){throw new IllegalStateException(ex);}}
}
