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

/** Closed, explicit dependency-input@1 adapter. Never searches for adjacent files. */
public final class DependencyInputJson {
    private final ObjectMapper mapper=JsonMapper.builder(JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build()).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    public boolean isBundle(Path path)throws IOException {
        try(var parser=mapper.getFactory().createParser(path.toFile())) {
            if(parser.nextToken()!=JsonToken.START_OBJECT)return false;
            while(parser.nextToken()==JsonToken.FIELD_NAME) {
                String name=parser.currentName();parser.nextToken();
                if(name.equals("schema"))return parser.currentToken()==JsonToken.VALUE_STRING&&parser.getText().equals("dependency-input");
                parser.skipChildren();
            }
            return false;
        }
    }
    public DependencyInput read(Path path,DataflowAirReader reader)throws IOException {
        var root=mapper.readTree(Files.readAllBytes(path));keys(root,"schema","version","air","qualifiedSource","sourceSha256","correlations");
        if(!text(root,"schema").equals("dependency-input")||!text(root,"version").equals("1.0.0"))throw new IllegalArgumentException("unsupported dependency input");
        var base=path.toAbsolutePath().getParent();var air=root.get("air");keys(air,"path","sha256");
        var read=reader.read(base.resolve(text(air,"path")));
        if(!read.sha256().equals(text(air,"sha256")))throw new IllegalArgumentException("AIR digest mismatch");
        var source=root.get("qualifiedSource");keys(source,"path","sha256");var bytes=Files.readAllBytes(base.resolve(text(source,"path")));
        if(!sha(bytes).equals(text(source,"sha256")))throw new IllegalArgumentException("source evidence digest mismatch");
        var evidence=new QualifiedSourceJson().decode(bytes);
        if(!evidence.source().sha256().equals(text(root,"sourceSha256"))||evidence.air().size()!=1||!evidence.air().getFirst().sha256().equals(read.sha256()))throw new IllegalArgumentException("source/AIR snapshot mismatch");
        var list=root.get("correlations");if(!list.isArray())throw new IllegalArgumentException("correlations array required");
        var links=new ArrayList<DependencyInput.StatementCorrelation>();
        for(var link:list) {
            keys(link,"source","operation","label","origin");
            var operation=link.get("operation");id(operation,"operation",true);var label=link.get("label");id(label,"label",true);var origin=link.get("origin");id(origin,"origin",false);
            links.add(new DependencyInput.StatementCorrelation(QualifiedSourceJson.readStatementId(link.get("source")),
                new OperationId(unit(operation),text(operation,"localId")),new LabelId(unit(label),text(label,"localId")),new OriginId(new PublicationId(text(origin,"publication")),text(origin,"localId"))));
        }
        return new DependencyInput(read.publication(),Optional.of(evidence),links);
    }
    private static UnitId unit(JsonNode n){return new UnitId(new PublicationId(text(n,"publication")),text(n,"unit"));}
    private static void id(JsonNode n,String domain,boolean unit){if(unit)keys(n,"domain","publication","unit","localId");else keys(n,"domain","publication","localId");if(!text(n,"domain").equals(domain))throw new IllegalArgumentException("identity domain");}
    private static String text(JsonNode n,String key){var v=n.get(key);if(v==null||!v.isTextual()||v.textValue().isBlank())throw new IllegalArgumentException("text required: "+key);return v.textValue();}
    private static void keys(JsonNode n,String... fields){if(n==null||!n.isObject())throw new IllegalArgumentException("object required");var actual=new HashSet<String>();n.fieldNames().forEachRemaining(actual::add);if(!actual.equals(Set.of(fields)))throw new IllegalArgumentException("closed dependency input fields");}
    private static String sha(byte[] bytes){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(NoSuchAlgorithmException ex){throw new IllegalStateException(ex);}}
}
