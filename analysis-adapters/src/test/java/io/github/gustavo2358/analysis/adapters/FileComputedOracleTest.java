package io.github.gustavo2358.analysis.adapters;
import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.air.json.AirJson;
import io.github.gustavo2358.air.validation.*;
import io.github.gustavo2358.analysis.dependencies.*;
import java.math.BigInteger;
import java.nio.file.*;
import java.io.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static io.github.gustavo2358.analysis.adapters.ResultFixtures.*;

/** A4/T37, hand-written AIR and expected names; no SP/output-derived expectations. */
final class FileComputedOracleTest {
    static final PublicationId P=new PublicationId("fd-w7-values");static final UnitId U=new UnitId(P,"caller");static final OriginId O=origin(P);
    static final UncertaintyId GAP=new UncertaintyId(P,"input");static final StorageId BASE=new StorageId(P,"base");
    static final Memory.Codec CODEC=new Memory.ExtensionCodec("text.ebcdic.ibm1047","1",Types.known(Types.Builtin.TEXT));
    static final Interactions.NamePolicy POLICY=new Interactions.ExtensionName("cics-ts.file","1");
    static ObjectId obj(String n){return new ObjectId(U,n);}static LabelId label(String n){return new LabelId(U,n);}
    static Place place(OperationId op,String shape){
        if(shape.equals("slice"))return new Places.RegionSlice(operand(op,"slice",Operand.Role.VALUE_READ),BASE,number(op,"offset",4),number(op,"length",8),CODEC,Types.known(Types.Builtin.TEXT));
        return new Places.ObjectPlace(operand(op,"place",Operand.Role.VALUE_READ),obj(shape));
    }
    static Expressions.Literal number(OperationId op,String n,int value){return new Expressions.Literal(operand(op,n,Operand.Role.ADDRESS_READ),new Values.IntValue(BigInteger.valueOf(value)));}
    static Operations.Invoke file(String id,String next,String shape,String literal,Interactions.NamePolicy policy,boolean writes){
        var h=header(U,id);Interactions.Target target=literal!=null?new Interactions.LiteralTarget("file","cics.file",literal,policy,O):new Interactions.ComputedTarget("file","cics.file",new Expressions.Read(operand(h.id(),"name",Operand.Role.CALL_TARGET),place(h.id(),shape)),policy,O);
        return new Operations.Invoke(h,"read",target,List.of(),List.of(),new Interactions.ExternalSignature(entry(U,"unused","start").signature()),List.of(),new Interactions.EffectBound(new Interactions.ForeignEffects(Scopes.NoMemory.INSTANCE,writes?new Scopes.WithinMemory(new Scopes.StorageMemory(List.of(BASE))):Scopes.NoMemory.INSTANCE,List.of()),List.of()),new Control.InvocationOutcomes(List.of(new Control.Normal(label(next))),Scopes.NoControl.INSTANCE),new Interactions.KnownContract(new Interactions.ContractRef("manual-effect-bound","1",List.of(O))));
    }
    static Sequence seq(String label,List<Instruction> ins,Terminator term){return new Sequence(label(label),ins,term,O);}
    static Sequence choose(String id,String a,String b){var h=header(U,"branch-"+id);return seq(id,List.of(),new Operations.Branch(h,new Expressions.Unknown(operand(h.id(),"condition",Operand.Role.PREDICATE),Types.known(Types.Builtin.BOOL),List.of(),Scopes.NoMemory.INSTANCE,GAP),label(a),label(b)));}
    static Sequence move(String label,String op,String value,String dest,String next){return seq(label,List.of(assign(U,op,obj(dest),value)),new Operations.Jump(header(U,"jump-"+label),label(next)));}
    static Publication publication(List<Sequence> seq){
        var objects=new ArrayList<Memory.ObjectDeclaration>();
        for(var n:List.of("whole","name","alias","short")){int size=n.equals("whole")?16:n.equals("short")?7:8;int offset=n.equals("whole")?0:4;
            objects.add(new Memory.ObjectDeclaration(obj(n),Optional.empty(),Types.known(Types.Builtin.TEXT),new Memory.ViewBinding(BASE,BigInteger.valueOf(offset),BigInteger.valueOf(size),CODEC),Memory.Visibility.PRIVATE,O,Evidence.CoverageStatus.MODELED,header(U,"metadata").precision()));}
        var artifact=new ArtifactId(P,"oracle");
        var caps=new ArrayList<Capabilities.Capability>(List.of(Capabilities.MEMORY_REGIONS,Capabilities.IBM1047));
        seq.stream().map(Sequence::terminator).filter(Operations.Invoke.class::isInstance).map(Operations.Invoke.class::cast).map(i->i.target() instanceof Interactions.LiteralTarget t?t.namePolicy():((Interactions.ComputedTarget)i.target()).namePolicy()).filter(Interactions.ExtensionName.class::isInstance).map(Interactions.ExtensionName.class::cast).map(e->new Capabilities.Capability(e.name(),e.version())).distinct().forEach(caps::add);
        return new Publication(P,SemanticVersion.AIR_2_0_0,new Capabilities.Manifest(caps,List.of()),List.of(new Origins.Artifact(artifact,"FileComputedOracleTest.java",Optional.empty())),List.of(unit(U,List.of(entry(U,"entry","start")),seq,objects)),List.of(new Memory.Region(new Memory.StorageHeader(BASE,Optional.of(U),Memory.Lifetime.PERSISTENT,Memory.Visibility.PRIVATE,O),Optional.of(BigInteger.valueOf(16)),Optional.empty())),List.of(),List.of(),List.of(new Origins.Written(O,artifact,Optional.empty(),List.of(),true)),coverage(new Scopes.PublicationScope(P)),List.of(new Evidence.Uncertainty(GAP,"UNPROVED_INPUT",List.of(Evidence.Dimension.VALUES),new Scopes.UnitScope(U),"unknown selector",O)),List.of());
    }
    static DependencyResult analyze(String name,List<Sequence> seq)throws Exception {return analyze(name,seq,io.github.gustavo2358.analysis.values.StorageAnalysisMode.LOGICAL_ONLY);}
    static DependencyResult analyze(String name,List<Sequence> seq,io.github.gustavo2358.analysis.values.StorageAnalysisMode mode)throws Exception {
        var p=publication(seq);var valid=AirValidator.validate(p);assertEquals(ValidationResult.Status.STRUCTURALLY_VALID,valid.status(),valid.issues().toString());
        var codec=new AirJson();var air=codec.encode(p);assertEquals(p,codec.decode(air));var r=new DependencyAnalysis(mode).prepare(codec.decode(air));
        var dir=Path.of("target/fd-w7/manual");Files.createDirectories(dir);Files.write(dir.resolve(name+".air.json"),air);var out=new ByteArrayOutputStream();new DependencyJson().write(r,out);Files.write(dir.resolve(name+".json"),out.toByteArray());return r;
    }
    static FileDependencyResult.Site site(DependencyResult r,String id){return r.fileDependencies().sites().stream().filter(s->s.operation().localId().equals(id)).findFirst().orElseThrow();}
    static void expect(DependencyResult r,String id,boolean remainder,String...names){var s=site(r,id);assertEquals(List.of(names),s.candidates().stream().map(FileDependencyResult.Candidate::referenceName).toList());assertEquals(remainder,s.unknownRemainder());}
    @Test void firstAndSecondCommandsObserveDifferentValuesAndShareOneProvider()throws Exception {
        var r=analyze("timing",List.of(move("start","seed-a","FIRST001","name","one"),seq("one",List.of(),file("file1","change","name",null,POLICY,false)),move("change","seed-b","SECOND02","name","two"),seq("two",List.of(),file("file2","call","name",null,POLICY,false)),seq("call",List.of(),W1dModelTest.call(U,"call","end",obj("name"),false)),returning(U,"end",List.of())));
        expect(r,"file1",true,"FIRST001");expect(r,"file2",true,"SECOND02");assertEquals("seed-a",site(r,"file1").candidates().getFirst().supports().getFirst().producer().localId());assertEquals("seed-b",site(r,"file2").candidates().getFirst().supports().getFirst().producer().localId());assertEquals(1L,r.fileDependencies().metrics().get("possibleValuesPreparations"));assertEquals("SECOND02",r.sites().getFirst().candidates().getFirst().referenceName());assertTrue(r.sites().getFirst().modelValueRemainder());
    }
    @Test void joinsDistinguishClosedPartialAndUnknown()throws Exception {
        for(String mode:List.of("closed","partial","unknown")){
            var seq=new ArrayList<Sequence>();seq.add(choose("start","a","b"));seq.add(mode.equals("unknown")?jump(U,"a","file"):move("a","seed-a","ALPHA001","name","file"));seq.add(mode.equals("closed")?move("b","seed-b","BETA0002","name","file"):jump(U,"b","file"));seq.add(seq("file",List.of(),file("file","end","name",null,POLICY,false)));seq.add(returning(U,"end",List.of()));
            var r=analyze(mode,seq);expect(r,"file",true,mode.equals("closed")?new String[]{"ALPHA001","BETA0002"}:mode.equals("partial")?new String[]{"ALPHA001"}:new String[]{});
            for(var c:site(r,"file").candidates())assertEquals(List.of(c.referenceName().equals("ALPHA001")?"seed-a":"seed-b"),c.supports().stream().map(s->s.producer().localId()).toList());
        }
    }
    @Test void cyclesRetainFiniteCandidatesWithoutCutoff()throws Exception {
        var r=analyze("cycle",List.of(move("start","seed-a","ALPHA001","name","choose"),choose("choose","b","file"),move("b","seed-b","BETA0002","name","choose"),seq("file",List.of(),file("file","end","name",null,POLICY,false)),returning(U,"end",List.of())));expect(r,"file",true,"ALPHA001","BETA0002");
    }
    @Test void aliasAndPhysicalRefmodUseTheSameStorageFacts()throws Exception {
        for(var shape:List.of("alias","slice")){var r=analyze(shape,List.of(move("start","seed","PREFFILE0001TAIL","whole","file"),seq("file",List.of(),file("file","end",shape,null,POLICY,false)),returning(U,"end",List.of())), io.github.gustavo2358.analysis.values.StorageAnalysisMode.EXPERIMENTAL_PHYSICAL);expect(r,"file",false,"FILE0001");}
    }
    @Test void targetIsObservedBeforeOverlappingForeignWrites()throws Exception {
        var r=analyze("effects",List.of(move("start","seed","BEFORE01","name","one"),seq("one",List.of(),file("first","two","name",null,POLICY,true)),seq("two",List.of(),file("second","end","name",null,POLICY,false)),returning(U,"end",List.of())));expect(r,"first",true,"BEFORE01");expect(r,"second",true,"BEFORE01");
    }
    @Test void literalDoesNotRunValuesAndUsesFilePolicy()throws Exception {
        var r=analyze("literal",List.of(seq("start",List.of(),file("file","end","name","1FILE   ",POLICY,false)),returning(U,"end",List.of())));expect(r,"file",false,"1FILE");assertEquals(0L,r.fileDependencies().metrics().get("possibleValuesPreparations"));assertEquals("1FILE   ",site(r,"file").candidates().getFirst().rawValue());
    }
    @Test void invalidPolicySpellingAndShortAreaRemainOpen()throws Exception {
        for(var raw:List.of("lower   "," ABC    ","TOO-LONG","        ")){var r=analyze("invalid-"+raw.trim().replace(' ','_'),List.of(move("start","seed",raw,"name","file"),seq("file",List.of(),file("file","end","name",null,POLICY,false)),returning(U,"end",List.of())));expect(r,"file",true);}
        for(var shape:List.of("short","name")){var r=analyze("unsupported-"+shape,List.of(move("start","seed","VALID001","name","file"),seq("file",List.of(),file("file","end",shape,null,shape.equals("short")?POLICY:new Interactions.ExtensionName("cics-ts.program","1"),false)),returning(U,"end",List.of())));expect(r,"file",true);}
    }
    @Test void physicalCallAndFileReuseTheSameGeneralAnalysisRun()throws Exception {
        var call=W1dModelTest.call(U,"call","end",obj("name"),false);
        var target=new Interactions.ComputedTarget("program","cobol.program",new Expressions.Read(operand(call.header().id(),"name",Operand.Role.CALL_TARGET),place(call.header().id(),"slice")),Interactions.ExactName.INSTANCE,O);
        var physical=new Operations.Invoke(call.header(),call.action(),target,call.arguments(),call.results(),call.signature(),call.effectOperands(),call.effectBound(),call.outcomes(),call.contract());
        var r=analyze("shared",List.of(move("start","seed","SHARED01","name","file"),seq("file",List.of(),file("file","call","slice",null,POLICY,false)),seq("call",List.of(),physical),returning(U,"end",List.of())), io.github.gustavo2358.analysis.values.StorageAnalysisMode.EXPERIMENTAL_PHYSICAL);
        expect(r,"file",false,"SHARED01");assertEquals("SHARED01",r.sites().getFirst().candidates().getFirst().referenceName());assertEquals(1L,r.metrics().get("possibleValuesPreparations"));assertEquals(0L,r.fileDependencies().metrics().get("analysis.analysisRuns"));assertTrue(r.fileDependencies().metrics().get("analysis.analysisCacheHits")>=2);
    }
    @Test void literalCannotBorrowCallPolicyOrRunValues()throws Exception {
        for(var policy:List.of(Interactions.ExactName.INSTANCE,new Interactions.ExtensionName("cics-ts.program","1"))){
            var r=analyze("wrong-literal-"+policy.getClass().getSimpleName(),List.of(seq("start",List.of(),file("file","end","name","VALID001",policy,false)),returning(U,"end",List.of())), io.github.gustavo2358.analysis.values.StorageAnalysisMode.EXPERIMENTAL_PHYSICAL);expect(r,"file",true);assertEquals(0L,r.fileDependencies().metrics().get("possibleValuesPreparations"));
        }
    }

}
