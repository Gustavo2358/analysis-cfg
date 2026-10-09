package io.github.gustavo2358.analysis.adapters;
import java.util.*;
import io.github.gustavo2358.analysis.dependencies.source.NominalValueEvidence;
import io.github.gustavo2358.analysis.dependencies.source.NominalValues;
import java.io.*;
import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.github.gustavo2358.analysis.dependencies.source.QualifiedSourceDependencies;
import io.github.gustavo2358.analysis.dependencies.source.QualifiedSourceDependencies.*;
/** Closed v1 codec with explicit field mapping; no polymorphic/bean deserialization. */
public final class QualifiedSourceJson {
    private final ObjectMapper mapper=JsonMapper.builder(JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build()).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    private final ObjectReader elementReader=mapper.reader().without(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    public QualifiedSourceDependencies decode(byte[] bytes) throws IOException {
        return decode(new ByteArrayInputStream(bytes));
    }
    /** One inventory element at a time; the input belongs to the caller. */
    public QualifiedSourceDependencies decode(InputStream input) throws IOException {
        return decode(input,()->{});
    }
    /** Productive decode safepoints, including one element's nested token tree. No owner is retained. */
    public QualifiedSourceDependencies decode(InputStream input,Runnable progress) throws IOException {
        return decode(input,progress,progress);
    }
    /** Decoding and mandatory model admission use the same caller's budget, with distinct phases. */
    public QualifiedSourceDependencies decode(InputStream input,Runnable progress,Runnable validation) throws IOException {
        Objects.requireNonNull(input);Objects.requireNonNull(validation);Objects.requireNonNull(progress).run();
        var checkedInput=new FilterInputStream(input) {
            @Override public int read() throws IOException {progress.run();return in.read();}
            @Override public int read(byte[] bytes,int offset,int length) throws IOException {progress.run();return in.read(bytes,offset,length);}
        };
        try(var parser=new com.fasterxml.jackson.core.util.JsonParserDelegate(mapper.getFactory().createParser(checkedInput)) {
            @Override public JsonToken nextToken() throws IOException {progress.run();return delegate.nextToken();}
            @Override public JsonToken nextValue() throws IOException {
                var token=nextToken();return token==JsonToken.FIELD_NAME?nextToken():token;
            }
            @Override public JsonParser skipChildren() throws IOException {
                if(currentToken()==JsonToken.START_ARRAY||currentToken()==JsonToken.START_OBJECT) {
                    int depth=1;
                    while(depth!=0) {
                        var token=nextToken();if(token==null)throw new EOFException("unfinished source element");
                        if(token.isStructStart())depth++;else if(token.isStructEnd())depth--;
                    }
                }
                return this;
            }
        }) {
            parser.disable(JsonParser.Feature.AUTO_CLOSE_SOURCE);
            if(parser.nextToken()!=JsonToken.START_OBJECT)throw new IllegalArgumentException("object required");
            var result=readSourceStream(parser,validation);
            if(parser.nextToken()!=null)throw new IllegalArgumentException("trailing source content");
            return result;
        }
    }
    @FunctionalInterface private interface Element<T> {T read(JsonParser parser)throws IOException;}
    private <T> List<T> array(JsonParser parser,Element<T> element)throws IOException {
        if(parser.currentToken()!=JsonToken.START_ARRAY)throw new IllegalArgumentException("array required");
        var result=new ArrayList<T>();
        while(parser.nextToken()!=JsonToken.END_ARRAY) {
            if(parser.currentToken()==null)throw new EOFException("unfinished source inventory");
            result.add(element.read(parser));
        }
        return List.copyOf(result);
    }
    private <T> void rows(JsonParser parser,Element<T> element,java.util.function.Consumer<T> append)throws IOException {
        if(parser.currentToken()!=JsonToken.START_ARRAY)throw new IllegalArgumentException("array required");
        while(parser.nextToken()!=JsonToken.END_ARRAY) {
            if(parser.currentToken()==null)throw new EOFException("unfinished source inventory");
            append.accept(element.read(parser));
        }
    }
    private JsonNode tree(JsonParser parser)throws IOException {
        return elementReader.readTree(parser);
    }
    private QualifiedSourceDependencies readSourceStream(JsonParser parser,Runnable validation)throws IOException {
        var admission=new SourceAdmissionReader(validation);
        String schema=null,version=null,producer=null;Document source=null;
        List<AirCorrelation> air=null;List<UnitEvidence> units=null;var fields=new HashSet<String>();
        while(parser.nextToken()!=JsonToken.END_OBJECT) {
            if(parser.currentToken()!=JsonToken.FIELD_NAME)throw new IllegalArgumentException("source field required");
            var name=parser.currentName();if(!fields.add(name))throw new IllegalArgumentException("duplicate source field");
            if(parser.nextToken()==null)throw new EOFException("missing source value");
            switch(name) {
                case "schema" -> schema=text(tree(parser));
                case "version" -> version=text(tree(parser));
                case "producer" -> producer=text(tree(parser));
                case "source" -> source=admission.readDocument(tree(parser));
                case "air" -> air=array(parser,p->admission.readAirCorrelation(tree(p)));
                case "units" -> units=array(parser,p->readUnitStream(p,validation,admission));
                default -> throw new IllegalArgumentException("closed source evidence fields");
            }
        }
        if(!fields.equals(Set.of("schema","version","producer","source","air","units")))throw new IllegalArgumentException("closed source evidence fields");
        if(!"qualified-source-dependencies".equals(schema)||!Set.of("1.0.0","1.1.0","1.2.0","1.3.0","1.4.0","1.5.0","1.6.0").contains(version))throw new IllegalArgumentException("unsupported source contract");
        return new QualifiedSourceDependencies(schema,version,producer,source,air,units,validation);
    }
    private UnitEvidence readUnitStream(JsonParser parser,Runnable validation,SourceAdmissionReader admission)throws IOException {
        if(parser.currentToken()!=JsonToken.START_OBJECT)throw new IllegalArgumentException("unit object required");
        UnitId unit=null;Boolean controlAvailable=null;
        Optional<NominalValueEvidence> nominalValues=Optional.empty();List<NativeFileUse> nativeFiles=List.of();
        List<Statement> statements=null;
        List<Occurrence> occurrences=null;
        List<Target> targets=null;
        var inventory=new io.github.gustavo2358.analysis.dependencies.source.SourceInventories.Builder(validation);

        List<Selection> selections=null;
        List<Event> events=null;
        List<Guard> guards=null;
        List<Proof> proofs=null;
        List<Frontier> frontiers=null;
        var fields=new HashSet<String>();
        while(parser.nextToken()!=JsonToken.END_OBJECT) {
            if(parser.currentToken()!=JsonToken.FIELD_NAME)throw new IllegalArgumentException("unit field required");
            var name=parser.currentName();if(!fields.add(name))throw new IllegalArgumentException("duplicate unit field");
            if(parser.nextToken()==null)throw new EOFException("missing unit value");
            switch(name) {
                case "unit" -> unit=admission.readUnitId(tree(parser));
                case "controlAvailable" -> controlAvailable=bool(tree(parser));
                case "nominalValues" -> nominalValues=Optional.of(admission.readNominalValueEvidence(tree(parser),validation));
                case "nativeFiles" -> nativeFiles=array(parser,p->admission.readNativeFile(tree(p)));
                case "statements" -> statements=array(parser,p->admission.readStatement(tree(p)));
                case "occurrences" -> occurrences=array(parser,p->admission.readOccurrence(tree(p)));
                case "targets" -> targets=array(parser,p->admission.readTarget(tree(p)));
                case "nodes" -> rows(parser,p->admission.readNode(tree(p)),inventory::addNode);
                case "derivations" -> rows(parser,p->admission.readDerivation(tree(p)),inventory::addDerivation);
                case "selections" -> selections=array(parser,p->admission.readSelection(tree(p)));
                case "events" -> events=array(parser,p->admission.readEvent(tree(p)));
                case "guards" -> guards=array(parser,p->admission.readGuard(tree(p)));
                case "proofs" -> proofs=array(parser,p->admission.readProof(tree(p)));
                case "frontiers" -> frontiers=array(parser,p->admission.readFrontier(tree(p)));
                default -> throw new IllegalArgumentException("closed source evidence fields");
            }
        }
        fields.removeAll(Set.of("nominalValues","nativeFiles"));
        if(!fields.equals(Set.of("unit","controlAvailable","statements","occurrences","targets","nodes","derivations","selections","events","guards","proofs","frontiers")))throw new IllegalArgumentException("closed source evidence fields");
        var owned=inventory.build();
        return new UnitEvidence(unit,controlAvailable,statements,occurrences,targets,owned.nodes(),owned.derivations(),selections,events,guards,proofs,frontiers,nominalValues,nativeFiles,validation);
    }
    public byte[] encode(QualifiedSourceDependencies value) throws IOException {return mapper.writeValueAsBytes(value(value));}
    /** Stream exactly the legacy mapping; the caller retains output ownership. */
    public void write(QualifiedSourceDependencies value,OutputStream output) throws IOException {
        try(var generator=mapper.getFactory().createGenerator(Objects.requireNonNull(output))) {
            generator.disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET);
            mapper.writeValue(generator,value(value));
        }
    }
    private static <T,R> List<R> mapped(List<T> source,java.util.function.Function<T,R> mapping) {
        return new AbstractList<>() {
            @Override public R get(int index){return mapping.apply(source.get(index));}
            @Override public int size(){return source.size();}
        };
    }
    public static Map<String,Object> value(QualifiedSourceDependencies v) {return object("schema", v.schema(), "version", v.version(), "producer", v.producer(), "source", value(v.source()), "air", mapped(v.air(),QualifiedSourceJson::value), "units", mapped(v.units(),QualifiedSourceJson::value));}
    public static Map<String,Object> value(Document v) {return object("schema", v.schema(), "version", v.version(), "sha256", v.sha256());}
    public static Map<String,Object> value(AirCorrelation v) {return object("publication", v.publication(), "sha256", v.sha256());}
    public static Map<String,Object> value(UnitId v) {return object("compilationUnitId", v.compilationUnitId(), "structuralPath", v.structuralPath(), "canonicalProgramName", v.canonicalProgramName());}
    public static Map<String,Object> value(StatementId v) {return object("unit", value(v.unit()), "handle", v.handle());}
    public static Map<String,Object> value(OperandId v) {return object("statement", value(v.statement()), "handle", v.handle());}
    public static Map<String,Object> value(Location v) {return object("file", v.file(), "startLine", v.startLine(), "startColumn", v.startColumn(), "endLine", v.endLine(), "endColumn", v.endColumn());}
    public static Map<String,Object> value(Include v) {return object("includingFile", v.includingFile(), "requestedName", v.requestedName(), "includedFile", v.includedFile(), "includeLine", v.includeLine());}
    public static Map<String,Object> value(Provenance v) {return object("expanded", value(v.expanded()), "original", value(v.original()), "includeChain", v.includeChain().stream().map(QualifiedSourceJson::value).toList(), "exact", v.exact());}
    public static Map<String,Object> value(Statement v) {return object("id", value(v.id()), "provenance", value(v.provenance()));}
    public static Map<String,Object> value(Operand v) {return object("id", value(v.id()), "provenance", value(v.provenance()));}
    public static Map<String,Object> value(Value v) {return object("logicalDomain", v.logicalDomain(), "value", v.value(), "logicalExtent", v.logicalExtent());}
    public static Map<String,Object> value(Occurrence v) {return object("id", value(v.id()), "technology", v.technology(), "command", v.command(), "namespace", v.namespace(), "nameProfile", v.nameProfile(), "targetKind", v.targetKind(), "operands", v.operands().stream().map(QualifiedSourceJson::value).toList(), "values", v.values().stream().map(QualifiedSourceJson::value).toList(), "valueRemainder", v.valueRemainder(), "qualifications", v.qualifications());}
    public static Map<String,Object> value(Support v) {var out=object("kind",v.kind(),"target",v.target(),"activation",v.activation().stream().map(QualifiedSourceJson::value).toList(),"cause",v.cause());if(!v.conditions().isEmpty())out.put("conditions",v.conditions().stream().map(c->object("condition",c.condition(),"registration",value(c.registration()),"uncertain",c.uncertain(),"proofs",c.proofs())).toList());return out;}
    public static Map<String,Object> value(Node v) {return object("id", v.id(), "context", v.context(), "location", v.location(), "support", value(v.support()));}
    public static Map<String,Object> value(Registration v) {return object("statement", value(v.statement()), "statementOrigin", value(v.statementOrigin()), "operandOrigin", value(v.operandOrigin()));}
    public static Map<String,Object> value(Target v) {return object("id", v.id(), "form", v.form(), "entry", v.entry().stream().map(QualifiedSourceJson::value).toList(), "registrations", v.registrations().stream().map(QualifiedSourceJson::value).toList(), "programOperands", v.programOperands().stream().map(QualifiedSourceJson::value).toList(), "programValues", v.programValues().stream().map(QualifiedSourceJson::value).toList());}
    public static Map<String,Object> value(Proof v) {return object("id", v.id(), "kind", v.kind(), "rule", v.rule(), "provenance", value(v.provenance()), "dependencies", v.dependencies());}
    public static Map<String,Object> value(Guard v) {return object("id", v.id(), "event", v.event(), "kind", v.kind());}
    public static Map<String,Object> value(Event v) {return object("id", v.id(), "statement", value(v.statement()), "origin", v.origin(), "disposition", v.disposition(), "eligibility", v.eligibility(), "scope", v.scope(), "runtimeIdentity", v.runtimeIdentity(), "guards", v.guards(), "proofs", v.proofs());}
    public static Map<String,Object> value(Selection v) {return object("id", v.id(), "event", v.event(), "source", v.source(), "target", v.target(), "stateOnEntry", v.stateOnEntry().stream().map(QualifiedSourceJson::value).toList(), "localEntry", v.localEntry(), "guards", v.guards(), "proofs", v.proofs(), "unknownLocalRemainder", v.unknownLocalRemainder(), "localInactivePossible", v.localInactivePossible(), "outerLevelRemainder", v.outerLevelRemainder(), "bypassed", v.bypassed());}
    public static Map<String,Object> value(Derivation v) {return object("id", v.id(), "source", v.source(), "destination", v.destination(), "callerPremise", v.callerPremise(), "authority", v.authority(), "proofs", v.proofs(), "selection", v.selection());}
    public static Map<String,Object> value(Frontier v) {return object("source", v.source(), "authority", v.authority(), "reference", v.reference(), "proofs", v.proofs());}
    public static Map<String,Object> value(UnitEvidence v) {var out=object("unit", value(v.unit()), "controlAvailable", v.controlAvailable(), "statements", mapped(v.statements(),QualifiedSourceJson::value), "occurrences", mapped(v.occurrences(),QualifiedSourceJson::value), "targets", mapped(v.targets(),QualifiedSourceJson::value), "nodes", mapped(v.nodes(),QualifiedSourceJson::value), "derivations", mapped(v.derivations(),QualifiedSourceJson::value), "selections", mapped(v.selections(),QualifiedSourceJson::value), "events", mapped(v.events(),QualifiedSourceJson::value), "guards", mapped(v.guards(),QualifiedSourceJson::value), "proofs", mapped(v.proofs(),QualifiedSourceJson::value), "frontiers", mapped(v.frontiers(),QualifiedSourceJson::value));v.nominalValues().ifPresent(n->out.put("nominalValues",value(n)));if(!v.nativeFiles().isEmpty())out.put("nativeFiles",mapped(v.nativeFiles(),QualifiedSourceJson::value));return out;}

    public static Object value(NativeFileName v){return object("declaration",v.declaration(),"owner",value(v.owner()),"logicalFile",v.logicalFile(),"rawValue",v.rawValue(),"declarationOrigins",v.declarationOrigins().stream().map(QualifiedSourceJson::value).toList());}
    public static Object value(NativeFileUse v){return object("statement",value(v.statement()),"ordinal",v.ordinal(),"controlLocation",v.controlLocation(),"command",v.command(),"local",v.local(),"names",v.names().stream().map(QualifiedSourceJson::value).toList(),"provenance",value(v.provenance()),"qualifications",v.qualifications(),"gaps",v.gaps());}

    private static Object value(NominalValueEvidence v){return object("facts",value(v.facts()),"declarations",v.declarations().stream().map(d->object("node",d.node(),"provenance",value(d.provenance()))).toList(),"seeds",v.seeds().stream().map(d->object("node",d.node(),"value",d.value(),"authority",d.authority(),"provenance",value(d.provenance()))).toList(),"branches",v.branches().stream().map(d->object("derivation",d.derivation(),"whenTrue",d.whenTrue())).toList(),"uncertainties",v.uncertainties().stream().map(d->object("id",d.id(),"kind",d.kind(),"provenance",value(d.provenance()))).toList());}
    private static Object value(NominalValues v){var out=object("authority",v.authority(),"symbols",v.symbols().stream().map(d->!v.authority().equals("NOMINAL_TEXT_SOURCE_V1")?object("node",d.node(),"extent",d.extent(),"modelAssumed",d.modelAssumed()):object("node",d.node(),"extent",d.extent())).toList(),"assignments",v.assignments().stream().map(d->object("statement",d.statement(),"target",d.target(),"source",value(d.source()))).toList(),"conditions",v.conditions().stream().map(d->object("statement",d.statement(),"predicate",value(d.predicate()))).toList(),"queries",v.queries().stream().map(d->object("statement",d.statement(),"node",d.node())).toList());if(v.authority().equals("NOMINAL_TEXT_SOURCE_V4"))out.put("tableFields",v.tableFields().stream().map(f->object("node",f.node(),"initial",f.initial().stream().map(i->object("origin",i.origin(),"value",i.value())).toList())).toList());return out;}
    private static Object value(NominalValues.Term v){return v.arguments().isEmpty()?object("kind",v.kind(),"value",v.value()):object("kind",v.kind(),"value",v.value(),"arguments",v.arguments().stream().map(QualifiedSourceJson::value).toList());}
    private static Object value(NominalValues.Predicate v){return object("kind",v.kind(),"terms",v.terms().stream().map(QualifiedSourceJson::value).toList(),"children",v.children().stream().map(QualifiedSourceJson::value).toList());}
    /** Invocation-local field mapping. Progress never escapes into the codec or evidence. */
    private static final class SourceAdmissionReader {
        private final Runnable progress;
        SourceAdmissionReader(Runnable progress){this.progress=Objects.requireNonNull(progress);}
        private <T> List<T> list(JsonNode n,java.util.function.Function<JsonNode,T> mapping) {
            if(n==null||!n.isArray())throw new IllegalArgumentException("array required");
            var result=new ArrayList<T>();for(var element:n){progress.run();result.add(mapping.apply(element));}
            return List.copyOf(result);
        }
        private void keys(JsonNode n,String... expected) {
            progress.run();if(n==null||!n.isObject())throw new IllegalArgumentException("object required");
            var actual=new HashSet<String>();var names=n.fieldNames();
            while(names.hasNext()){progress.run();actual.add(names.next());}
            if(!actual.equals(Set.of(expected)))throw new IllegalArgumentException("closed source evidence fields");
        }
        private QualifiedSourceDependencies readQualifiedSourceDependencies(JsonNode n) {keys(n, "schema", "version", "producer", "source", "air", "units");return new QualifiedSourceDependencies(text(n.get("schema")), text(n.get("version")), text(n.get("producer")), readDocument(n.get("source")), list(n.get("air"), this::readAirCorrelation), list(n.get("units"), this::readUnitEvidence));}
        private Document readDocument(JsonNode n) {keys(n, "schema", "version", "sha256");return new Document(text(n.get("schema")), text(n.get("version")), text(n.get("sha256")));}
        private AirCorrelation readAirCorrelation(JsonNode n) {keys(n, "publication", "sha256");return new AirCorrelation(text(n.get("publication")), text(n.get("sha256")));}
        private UnitId readUnitId(JsonNode n) {keys(n, "compilationUnitId", "structuralPath", "canonicalProgramName");return new UnitId(text(n.get("compilationUnitId")), list(n.get("structuralPath"), QualifiedSourceJson::integer), text(n.get("canonicalProgramName")));}
        private StatementId readStatementId(JsonNode n) {keys(n, "unit", "handle");return new StatementId(readUnitId(n.get("unit")), text(n.get("handle")));}
        private OperandId readOperandId(JsonNode n) {keys(n, "statement", "handle");return new OperandId(readStatementId(n.get("statement")), text(n.get("handle")));}
        private Location readLocation(JsonNode n) {keys(n, "file", "startLine", "startColumn", "endLine", "endColumn");return new Location(text(n.get("file")), integer(n.get("startLine")), integer(n.get("startColumn")), integer(n.get("endLine")), integer(n.get("endColumn")));}
        private Include readInclude(JsonNode n) {keys(n, "includingFile", "requestedName", "includedFile", "includeLine");return new Include(text(n.get("includingFile")), text(n.get("requestedName")), text(n.get("includedFile")), integer(n.get("includeLine")));}
        private Provenance readProvenance(JsonNode n) {keys(n, "expanded", "original", "includeChain", "exact");return new Provenance(readLocation(n.get("expanded")), readLocation(n.get("original")), list(n.get("includeChain"), this::readInclude), bool(n.get("exact")));}
        private Statement readStatement(JsonNode n) {keys(n, "id", "provenance");return new Statement(readStatementId(n.get("id")), readProvenance(n.get("provenance")));}
        private Operand readOperand(JsonNode n) {keys(n, "id", "provenance");return new Operand(readOperandId(n.get("id")), readProvenance(n.get("provenance")));}
        private Value readValue(JsonNode n) {keys(n, "logicalDomain", "value", "logicalExtent");return new Value(text(n.get("logicalDomain")), text(n.get("value")), integer(n.get("logicalExtent")));}
        private Occurrence readOccurrence(JsonNode n) {keys(n, "id", "technology", "command", "namespace", "nameProfile", "targetKind", "operands", "values", "valueRemainder", "qualifications");return new Occurrence(readStatementId(n.get("id")), text(n.get("technology")), text(n.get("command")), text(n.get("namespace")), text(n.get("nameProfile")), text(n.get("targetKind")), list(n.get("operands"), this::readOperand), list(n.get("values"), this::readValue), bool(n.get("valueRemainder")), list(n.get("qualifications"), QualifiedSourceJson::text));}
        private Support readSupport(JsonNode n) {if(n.has("conditions"))keys(n,"kind","target","activation","cause","conditions");else keys(n,"kind","target","activation","cause");return new Support(text(n.get("kind")),list(n.get("target"),QualifiedSourceJson::text),list(n.get("activation"),this::readStatementId),text(n.get("cause")),n.has("conditions")?list(n.get("conditions"),this::readConditionState):List.of());}
        private ConditionState readConditionState(JsonNode n){keys(n,"condition","registration","uncertain","proofs");if(!n.get("uncertain").isBoolean())throw new IllegalArgumentException("condition uncertainty boolean");return new ConditionState(text(n.get("condition")),readStatementId(n.get("registration")),n.get("uncertain").booleanValue(),list(n.get("proofs"),QualifiedSourceJson::text));}
        private Node readNode(JsonNode n) {keys(n, "id", "context", "location", "support");return new Node(text(n.get("id")), text(n.get("context")), text(n.get("location")), readSupport(n.get("support")));}
        private Registration readRegistration(JsonNode n) {keys(n, "statement", "statementOrigin", "operandOrigin");return new Registration(readStatementId(n.get("statement")), readProvenance(n.get("statementOrigin")), readProvenance(n.get("operandOrigin")));}
        private Target readTarget(JsonNode n) {keys(n, "id", "form", "entry", "registrations", "programOperands", "programValues");return new Target(text(n.get("id")), text(n.get("form")), list(n.get("entry"), this::readStatementId), list(n.get("registrations"), this::readRegistration), list(n.get("programOperands"), this::readOperand), list(n.get("programValues"), this::readValue));}
        private Proof readProof(JsonNode n) {keys(n, "id", "kind", "rule", "provenance", "dependencies");return new Proof(text(n.get("id")), text(n.get("kind")), text(n.get("rule")), readProvenance(n.get("provenance")), list(n.get("dependencies"), QualifiedSourceJson::text));}
        private Guard readGuard(JsonNode n) {keys(n, "id", "event", "kind");return new Guard(text(n.get("id")), text(n.get("event")), text(n.get("kind")));}
        private Event readEvent(JsonNode n) {keys(n, "id", "statement", "origin", "disposition", "eligibility", "scope", "runtimeIdentity", "guards", "proofs");return new Event(text(n.get("id")), readStatementId(n.get("statement")), text(n.get("origin")), text(n.get("disposition")), text(n.get("eligibility")), text(n.get("scope")), text(n.get("runtimeIdentity")), list(n.get("guards"), QualifiedSourceJson::text), list(n.get("proofs"), QualifiedSourceJson::text));}
        private Selection readSelection(JsonNode n) {keys(n, "id", "event", "source", "target", "stateOnEntry", "localEntry", "guards", "proofs", "unknownLocalRemainder", "localInactivePossible", "outerLevelRemainder", "bypassed");return new Selection(text(n.get("id")), text(n.get("event")), text(n.get("source")), list(n.get("target"), QualifiedSourceJson::text), list(n.get("stateOnEntry"), this::readSupport), list(n.get("localEntry"), QualifiedSourceJson::text), list(n.get("guards"), QualifiedSourceJson::text), list(n.get("proofs"), QualifiedSourceJson::text), bool(n.get("unknownLocalRemainder")), bool(n.get("localInactivePossible")), bool(n.get("outerLevelRemainder")), bool(n.get("bypassed")));}
        private Derivation readDerivation(JsonNode n) {keys(n, "id", "source", "destination", "callerPremise", "authority", "proofs", "selection");return new Derivation(text(n.get("id")), list(n.get("source"), QualifiedSourceJson::text), text(n.get("destination")), list(n.get("callerPremise"), QualifiedSourceJson::text), text(n.get("authority")), list(n.get("proofs"), QualifiedSourceJson::text), list(n.get("selection"), QualifiedSourceJson::text));}
        private Frontier readFrontier(JsonNode n) {keys(n, "source", "authority", "reference", "proofs");return new Frontier(text(n.get("source")), text(n.get("authority")), text(n.get("reference")), list(n.get("proofs"), QualifiedSourceJson::text));}
        private UnitEvidence readUnitEvidence(JsonNode n) {var expected=new ArrayList<>(List.of("unit","controlAvailable","statements","occurrences","targets","nodes","derivations","selections","events","guards","proofs","frontiers"));for(String optional:List.of("nominalValues","nativeFiles"))if(n.has(optional))expected.add(optional);keys(n,expected.toArray(String[]::new));return new UnitEvidence(readUnitId(n.get("unit")), bool(n.get("controlAvailable")), list(n.get("statements"), this::readStatement), list(n.get("occurrences"), this::readOccurrence), list(n.get("targets"), this::readTarget), list(n.get("nodes"), this::readNode), list(n.get("derivations"), this::readDerivation), list(n.get("selections"), this::readSelection), list(n.get("events"), this::readEvent), list(n.get("guards"), this::readGuard), list(n.get("proofs"), this::readProof), list(n.get("frontiers"), this::readFrontier),n.has("nominalValues")?Optional.of(readNominalValueEvidence(n.get("nominalValues"))):Optional.empty(),n.has("nativeFiles")?list(n.get("nativeFiles"),this::readNativeFile):List.of());}
        private NativeFileUse readNativeFile(JsonNode n){keys(n,"statement","ordinal","controlLocation","command","local","names","provenance","qualifications","gaps");return new NativeFileUse(readStatementId(n.get("statement")),integer(n.get("ordinal")),text(n.get("controlLocation")),text(n.get("command")),bool(n.get("local")),list(n.get("names"),d->{keys(d,"declaration","owner","logicalFile","rawValue","declarationOrigins");return new NativeFileName(text(d.get("declaration")),readUnitId(d.get("owner")),text(d.get("logicalFile")),text(d.get("rawValue")),list(d.get("declarationOrigins"),this::readProvenance));}),readProvenance(n.get("provenance")),list(n.get("qualifications"),QualifiedSourceJson::text),list(n.get("gaps"),QualifiedSourceJson::text));}
        private NominalValueEvidence readNominalValueEvidence(JsonNode n){return readNominalValueEvidence(n,()->{});}
        private NominalValueEvidence readNominalValueEvidence(JsonNode n,Runnable validation){keys(n,"facts","declarations","seeds","branches","uncertainties");return new NominalValueEvidence(readNominalValues(n.get("facts"),validation),list(n.get("declarations"),d->{validation.run();keys(d,"node","provenance");return new NominalValueEvidence.Declaration(text(d.get("node")),readProvenance(d.get("provenance")));}),list(n.get("seeds"),d->{validation.run();keys(d,"node","value","authority","provenance");return new NominalValueEvidence.Seed(text(d.get("node")),text(d.get("value")),text(d.get("authority")),readProvenance(d.get("provenance")));}),list(n.get("branches"),d->{validation.run();keys(d,"derivation","whenTrue");return new NominalValueEvidence.Branch(text(d.get("derivation")),bool(d.get("whenTrue")));}),list(n.get("uncertainties"),d->{validation.run();keys(d,"id","kind","provenance");return new NominalValueEvidence.Uncertainty(text(d.get("id")),text(d.get("kind")),readProvenance(d.get("provenance")));}),validation);}
        private NominalValues readNominalValues(JsonNode n,Runnable validation){boolean tables=n.path("authority").asText().equals("NOMINAL_TEXT_SOURCE_V4");if(tables)keys(n,"authority","symbols","assignments","conditions","queries","tableFields");else keys(n,"authority","symbols","assignments","conditions","queries");return new NominalValues(text(n.get("authority")),list(n.get("symbols"),d->{validation.run();boolean model=!n.path("authority").asText().equals("NOMINAL_TEXT_SOURCE_V1");if(model)keys(d,"node","extent","modelAssumed");else keys(d,"node","extent");return new NominalValues.Symbol(text(d.get("node")),integer(d.get("extent")),model&&bool(d.get("modelAssumed")));}),list(n.get("assignments"),d->{validation.run();keys(d,"statement","target","source");return new NominalValues.Assignment(text(d.get("statement")),text(d.get("target")),readNominalTerm(d.get("source"),validation));}),list(n.get("conditions"),d->{validation.run();keys(d,"statement","predicate");return new NominalValues.Condition(text(d.get("statement")),readNominalPredicate(d.get("predicate"),validation));}),list(n.get("queries"),d->{validation.run();keys(d,"statement","node");return new NominalValues.Query(text(d.get("statement")),text(d.get("node")));}),tables?list(n.get("tableFields"),f->{validation.run();keys(f,"node","initial");return new NominalValues.TableField(text(f.get("node")),list(f.get("initial"),i->{validation.run();keys(i,"origin","value");return new NominalValues.Initial(text(i.get("origin")),text(i.get("value")));}));}):List.of(),validation);}
        private NominalValues.Term readNominalTerm(JsonNode n,Runnable validation){validation.run();if(n.has("arguments")){keys(n,"kind","value","arguments");return new NominalValues.Term(text(n.get("kind")),text(n.get("value")),list(n.get("arguments"),child->readNominalTerm(child,validation)));}keys(n,"kind","value");return new NominalValues.Term(text(n.get("kind")),text(n.get("value")));}
        private NominalValues.Predicate readNominalPredicate(JsonNode n,Runnable validation){validation.run();keys(n,"kind","terms","children");return new NominalValues.Predicate(text(n.get("kind")),list(n.get("terms"),child->readNominalTerm(child,validation)),list(n.get("children"),child->readNominalPredicate(child,validation)));}
        private NominalValues.Term readNominalTerm(JsonNode n){if(n.has("arguments")){keys(n,"kind","value","arguments");return new NominalValues.Term(text(n.get("kind")),text(n.get("value")),list(n.get("arguments"),this::readNominalTerm));}keys(n,"kind","value");return new NominalValues.Term(text(n.get("kind")),text(n.get("value")));}
        private NominalValues.Predicate readNominalPredicate(JsonNode n){keys(n,"kind","terms","children");return new NominalValues.Predicate(text(n.get("kind")),list(n.get("terms"),this::readNominalTerm),list(n.get("children"),this::readNominalPredicate));}
    }
    static StatementId readStatementId(JsonNode n){return new SourceAdmissionReader(()->{}).readStatementId(n);}
    private static Map<String,Object> object(Object... fields){var m=new TreeMap<String,Object>();for(int i=0;i<fields.length;i+=2)m.put((String)fields[i],fields[i+1]);return m;}
    private static String text(JsonNode n){if(n==null||!n.isTextual())throw new IllegalArgumentException("text required");return n.textValue();}
    private static int integer(JsonNode n){if(n==null||!n.isIntegralNumber()||!n.canConvertToInt())throw new IllegalArgumentException("integer required");return n.intValue();}
    private static boolean bool(JsonNode n){if(n==null||!n.isBoolean())throw new IllegalArgumentException("boolean required");return n.booleanValue();}
    private static <T> List<T> list(JsonNode n,java.util.function.Function<JsonNode,T> f){if(n==null||!n.isArray())throw new IllegalArgumentException("array required");var result=new ArrayList<T>();for(var x:n)result.add(f.apply(x));return List.copyOf(result);}
    private static void keys(JsonNode n,String... keys){if(n==null||!n.isObject())throw new IllegalArgumentException("object required");var actual=new HashSet<String>();n.fieldNames().forEachRemaining(actual::add);if(!actual.equals(Set.of(keys)))throw new IllegalArgumentException("closed source evidence fields");}
}
