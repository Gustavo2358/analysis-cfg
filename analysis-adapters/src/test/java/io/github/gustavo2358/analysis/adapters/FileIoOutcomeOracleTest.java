package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.air.json.AirJson;
import io.github.gustavo2358.air.validation.*;
import io.github.gustavo2358.analysis.dependencies.*;
import java.math.BigInteger;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static io.github.gustavo2358.analysis.adapters.ResultFixtures.*;

/** O1–O5, independent manual AIR. Four disjoint allocations B/D/X/status.
 * Success has an ADDITIONAL explicit four-byte overwrite proof in this oracle;
 * neither READ nor AIR Normal implies that proof. EOF/key/error do not copy INTO.
 * IBM SC27-8713-03 update 2026-04-28 pp153,430–434 governs the source ordering. */
final class FileIoOutcomeOracleTest {
    enum Outcome { SUCCESS, EOF, INVALID_KEY, ERROR, OPEN }
    static final PublicationId P=new PublicationId("fd-w3-outcomes");
    static final UnitId U=new UnitId(P,"program");
    static final OriginId O=origin(P);
    static final UncertaintyId UNKNOWN=new UncertaintyId(P,"external-content");
    static final Memory.Codec CODEC=new Memory.ExtensionCodec("text.ebcdic.ibm1047","1",Types.known(Types.Builtin.TEXT));
    static ObjectId object(String id){return new ObjectId(U,id);}
    static StorageId storage(String id){return new StorageId(P,id);}
    static Operations.HavocMust must(String id,String target) {
        var h=header(U,id);return new Operations.HavocMust(h,new Places.ObjectPlace(operand(h.id(),"destination",Operand.Role.VALUE_WRITE),object(target)),UNKNOWN);
    }
    static Sequence choose(String id,String yes,String no) {
        var h=header(U,"select-"+id);
        return new Sequence(new LabelId(U,id),List.of(),new Operations.Branch(h,
            new Expressions.Unknown(operand(h.id(),"predicate",Operand.Role.PREDICATE),Types.known(Types.Builtin.BOOL),List.of(),Scopes.NoMemory.INSTANCE,UNKNOWN),
            new LabelId(U,yes),new LabelId(U,no)),O);
    }
    static Publication manual(Outcome selected,boolean prefixMust) {
        var objects=new ArrayList<Memory.ObjectDeclaration>();var storage=new ArrayList<Memory.Storage>();
        for(var id:List.of("B","D","X","STATUS")) {
            int extent=id.equals("STATUS")?2:8;
            storage.add(new Memory.Region(new Memory.StorageHeader(storage(id),Optional.of(U),Memory.Lifetime.PERSISTENT,Memory.Visibility.PRIVATE,O),Optional.of(BigInteger.valueOf(extent)),Optional.empty()));
            objects.add(new Memory.ObjectDeclaration(object(id),Optional.empty(),Types.known(Types.Builtin.TEXT),new Memory.ViewBinding(storage(id),BigInteger.ZERO,BigInteger.valueOf(extent),CODEC),Memory.Visibility.PRIVATE,O,Evidence.CoverageStatus.MODELED,header(U,"metadata").precision()));
        }
        for(var id:List.of("PREFIX","TAIL"))objects.add(new Memory.ObjectDeclaration(object(id),Optional.empty(),Types.known(Types.Builtin.TEXT),
            new Memory.ViewBinding(storage("B"),BigInteger.valueOf(id.equals("PREFIX")?0:4),BigInteger.valueOf(4),CODEC),Memory.Visibility.PRIVATE,O,Evidence.CoverageStatus.MODELED,header(U,"metadata").precision()));
        var sequences=new ArrayList<Sequence>();
        sequences.add(new Sequence(new LabelId(U,"start"),List.of(assign(U,"seed-B",object("B"),"OLDBUF00"),assign(U,"seed-D",object("D"),"OLDINTO0"),
            assign(U,"seed-X",object("X"),"SAFE0001"),assign(U,"seed-status",object("STATUS"),"ZZ")),W1dModelTest.call(U,"before-read","io",object("B"),false),O));
        var h=header(U,"read");var target=new Interactions.LiteralTarget("file","cobol.external-file-name","INDD",Interactions.ExactName.INSTANCE,O);
        var invoke=new Operations.Invoke(h,"read",target,List.of(),List.of(),new Interactions.ExternalSignature(entry(U,"unused","start").signature()),List.of(),
            new Interactions.EffectBound(new Interactions.ForeignEffects(Scopes.NoMemory.INSTANCE,new Scopes.WithinMemory(new Scopes.StorageMemory(List.of(storage("B")))),List.of()),List.of()),
            new Control.InvocationOutcomes(List.of(new Control.Normal(new LabelId(U,"select"))),Scopes.NoControl.INSTANCE),
            new Interactions.KnownContract(new Interactions.ContractRef("manual-bounded-input","1",List.of(O))));
        sequences.add(new Sequence(new LabelId(U,"io"),List.of(),invoke,O));
        if(selected==Outcome.OPEN) {
            sequences.add(choose("select","SUCCESS","select-errors"));sequences.add(choose("select-errors","EOF","select-key"));sequences.add(choose("select-key","INVALID_KEY","ERROR"));
        } else sequences.add(jump(U,"select",selected.name()));
        for(var outcome:List.of(Outcome.SUCCESS,Outcome.EOF,Outcome.INVALID_KEY,Outcome.ERROR)) {
            var effects=new ArrayList<Instruction>();
            if(outcome==Outcome.SUCCESS&&prefixMust)effects.add(must("proved-prefix", "PREFIX"));
            String status=switch(outcome){case SUCCESS->"00";case EOF->"10";case INVALID_KEY->"23";case ERROR->"90";default->throw new AssertionError();};
            effects.add(assign(U,"status-"+outcome,object("STATUS"),status));
            if(outcome==Outcome.SUCCESS) {
                var copy=header(U,"into-after-read");
                java.util.function.BiFunction<String,Integer,Expressions.Literal> number=(id,value)->new Expressions.Literal(operand(copy.id(),id,Operand.Role.ADDRESS_READ),new Values.IntValue(BigInteger.valueOf(value)));
                var src=new Memory.ByteRange(storage("B"),number.apply("source-offset",0),number.apply("source-length",8));
                var dst=new Memory.ByteRange(storage("D"),number.apply("destination-offset",0),number.apply("destination-length",8));
                var bounds=new Envelopes.Envelope(new Envelopes.MemoryEnvelope(List.of(),new Scopes.WithinMemory(new Scopes.StorageMemory(List.of(storage("B")))),List.of(),
                    new Scopes.WithinMemory(new Scopes.StorageMemory(List.of(storage("D")))),List.of()),
                    new Control.ControlEnvelope(List.of(Control.ContinueAlternative.INSTANCE),Scopes.NoControl.INSTANCE),new Envelopes.DependencyEnvelope(List.of(),Scopes.NoResources.INSTANCE));
                effects.add(new Operations.CopyBytes(copy,dst,src,BigInteger.valueOf(8),bounds));
            }
            sequences.add(new Sequence(new LabelId(U,outcome.name()),effects,W1dModelTest.call(U,"handler-"+outcome,"probe-B",null,false),O));
        }
        var probes=List.of("B","D","X","STATUS","TAIL");
        for(int i=0;i<probes.size();i++) {
            var probe=probes.get(i);sequences.add(new Sequence(new LabelId(U,"probe-"+probe),List.of(),W1dModelTest.call(U,"probe-"+probe,i+1<probes.size()?"probe-"+probes.get(i+1):"end",object(probe),false),O));
        }
        sequences.add(returning(U,"end",List.of()));
        var artifact=new ArtifactId(P,"manual-oracle");
        var resource=new Interactions.Resource(new ResourceId(P,"F"),target,O,Optional.of(new Interactions.ResourceDeclaration(U,"F","cobol.fd","cobol.assignment-name",List.of(new Interactions.ResourceObject(object("B"),"record")),List.of(new Interactions.ResourceUse(h.id(),"input",O)))));
        return new Publication(P,SemanticVersion.AIR_2_0_0,new Capabilities.Manifest(List.of(Capabilities.MEMORY_REGIONS,Capabilities.IBM1047,Capabilities.RESOURCE_BINDINGS),List.of()),
            List.of(new Origins.Artifact(artifact,"FileIoOutcomeOracleTest.java",Optional.empty())),List.of(unit(U,List.of(entry(U,"entry","start")),sequences,objects)),storage,List.of(resource),List.of(),
            List.of(new Origins.Written(O,artifact,Optional.empty(),List.of(),true)),coverage(new Scopes.PublicationScope(P)),
            List.of(new Evidence.Uncertainty(UNKNOWN,"MANUAL_EXTERNAL_INPUT",List.of(Evidence.Dimension.VALUES),new Scopes.UnitScope(U),"No source value for external input or selector",O)),
            List.of(new Proofs.Premise(new PremiseId(P,"separate-allocations"),"manual storage oracle","four independently allocated source objects",O,new Proofs.DisjointStorage(List.of(storage("B"),storage("D"),storage("X"),storage("STATUS"))))));
    }
    static DependencyResult analyze(Outcome outcome,boolean must)throws Exception {
        var p=manual(outcome,must);var validation=AirValidator.validate(p);
        assertEquals(ValidationResult.Status.STRUCTURALLY_VALID,validation.status(),validation.issues().toString());
        var codec=new AirJson();var wire=codec.encode(p);var decoded=codec.decode(wire);assertEquals(p,decoded);assertArrayEquals(wire,codec.encode(decoded));
        var result=new DependencyAnalysis(io.github.gustavo2358.analysis.values.StorageAnalysisMode.EXPERIMENTAL_PHYSICAL).prepare(decoded);assertFalse(result.analysisReasons().contains("CFG_UNSUPPORTED"));
        assertEquals(1,result.fileDependencies().sites().size());assertEquals(List.of("INDD"),result.fileDependencies().sites().getFirst().candidates().stream().map(FileDependencyResult.Candidate::referenceName).toList());
        var dir=Path.of("target/fd-w3/manual");Files.createDirectories(dir);Files.write(dir.resolve(outcome+"-"+must+".air.json"),wire);
        return result;
    }
    static DependencySiteFact site(DependencyResult result,String id){return result.sites().stream().filter(s->s.operation().localId().equals(id)).findFirst().orElseThrow();}
    static List<String> names(DependencySiteFact site){return site.candidates().stream().map(DependencySiteFact.Candidate::referenceName).toList();}
    static List<String> values(DependencySiteFact site){return site.rawCandidates().stream().map(DependencySiteFact.RawCandidate::rawValue).toList();}
    static void preserved(DependencyResult result) {
        assertEquals(List.of("OLDBUF00"),names(site(result,"before-read")));assertFalse(site(result,"before-read").modelValueRemainder());
        var safe=site(result,"probe-X");assertEquals(List.of("SAFE0001"),names(safe));assertFalse(safe.modelValueRemainder());
        assertEquals(List.of("seed-X"),safe.candidates().getFirst().supports().stream().map(s->s.producer().localId()).toList());
    }
    @Test void o1SuccessCopiesOnlyAfterReadAndExplicitMustKillsOnlyItsProvedPrefix()throws Exception {
        var r=analyze(Outcome.SUCCESS,true);preserved(r);
        for(var id:List.of("probe-B","probe-D")){assertTrue(names(site(r,id)).isEmpty());assertTrue(site(r,id).modelValueRemainder());}
        assertEquals(List.of("UF00"),names(site(r,"probe-TAIL")));assertTrue(site(r,"probe-TAIL").modelValueRemainder(),"tail invalidity is MAY, never prefix MUST extended to the largest record");
        assertEquals(List.of("00"),values(site(r,"probe-STATUS")));assertFalse(site(r,"probe-STATUS").modelValueRemainder());
    }
    @Test void o2AndO3HaveDifferentStatusAndNoIntoCopy()throws Exception {
        for(var outcome:List.of(Outcome.EOF,Outcome.INVALID_KEY)) {
            var r=analyze(outcome,true);preserved(r);assertEquals(List.of("OLDINTO0"),names(site(r,"probe-D")));assertFalse(site(r,"probe-D").modelValueRemainder());
            assertTrue(site(r,"probe-B").modelValueRemainder());assertEquals(List.of(outcome==Outcome.EOF?"10":"23"),values(site(r,"probe-STATUS")));
            assertEquals(DependencySiteFact.Reachability.REACHABLE,site(r,"handler-"+outcome).reachability());
            assertEquals(DependencySiteFact.Reachability.UNREACHABLE_IN_MODEL,site(r,"handler-SUCCESS").reachability());
        }
    }
    @Test void o4OtherErrorCannotDefaultToSuccess()throws Exception {
        var r=analyze(Outcome.ERROR,true);preserved(r);assertEquals(List.of("90"),values(site(r,"probe-STATUS")));
        assertEquals(List.of("OLDINTO0"),names(site(r,"probe-D")));assertFalse(site(r,"probe-D").modelValueRemainder());
        assertEquals(DependencySiteFact.Reachability.UNREACHABLE_IN_MODEL,site(r,"handler-SUCCESS").reachability());
    }
    @Test void o5OpenSelectionKeepsIndependentSupportsAndOutcomeUnion()throws Exception {
        var r=analyze(Outcome.OPEN,true);preserved(r);
        assertEquals(List.of("OLDINTO0"),names(site(r,"probe-D")));assertTrue(site(r,"probe-D").modelValueRemainder());
        assertEquals(List.of("00","10","23","90"),values(site(r,"probe-STATUS")));assertFalse(site(r,"probe-STATUS").modelValueRemainder());
        for(var outcome:List.of("SUCCESS","EOF","INVALID_KEY","ERROR"))assertEquals(DependencySiteFact.Reachability.REACHABLE,site(r,"handler-"+outcome).reachability());
    }
    @Test void mayIsNotMustAndNeverLeavesAnExactOldSingleton()throws Exception {
        var r=analyze(Outcome.SUCCESS,false);preserved(r);
        assertEquals(List.of("OLDBUF00"),names(site(r,"probe-B")));assertTrue(site(r,"probe-B").modelValueRemainder());
        assertEquals(List.of("OLDBUF00"),names(site(r,"probe-D")));assertTrue(site(r,"probe-D").modelValueRemainder());
        assertFalse(names(site(r,"probe-D")).contains("OLDINTO0"),"the full exact receiving area is overwritten on the success path");
    }
}
