package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.air.json.AirJson;
import io.github.gustavo2358.air.validation.*;
import io.github.gustavo2358.analysis.dependencies.*;
import java.util.*;
import java.math.BigInteger;
import java.io.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static io.github.gustavo2358.analysis.adapters.ResultFixtures.*;

/** D-AIR W8 counterproof: generic AIR arguments retain source SYSID without a new IR field. */
final class FileCicsContextOracleTest {
    static final PublicationId P=new PublicationId("fd-w8-context");static final UnitId U=new UnitId(P,"owner");static final OriginId O=origin(P);
    static Operations.Invoke file(String id,String next,boolean explicit,String sysid,String authority) {
        var h=header(U,id);var args=List.<Interactions.Argument>of(
            new Interactions.ValueArgument(new Expressions.Literal(operand(h.id(),"explicit",Operand.Role.ARGUMENT_VALUE),new Values.TextValue(explicit?"EXPLICIT":"DEFAULT"))),
            new Interactions.ValueArgument(new Expressions.Literal(operand(h.id(),"sysid",Operand.Role.ARGUMENT_VALUE),new Values.TextValue(sysid))));
        var params=new ArrayList<Interactions.Parameter>();
        for(int n=0;n<2;n++)params.add(new Interactions.Parameter(BigInteger.valueOf(n),new Interactions.KnownMode(Interactions.PassingMode.VALUE),Types.known(Types.Builtin.TEXT),Interactions.ExternalBinding.INSTANCE,O));
        return new Operations.Invoke(h,"read",new Interactions.LiteralTarget("file","cics.file","ACCOUNTS",new Interactions.ExtensionName("cics-ts.file","1"),O),args,List.of(),
            new Interactions.ExternalSignature(new Interactions.Signature(new Interactions.ParameterInventory(params,Interactions.NoRemainder.INSTANCE),new Interactions.ResultInventory(List.of(),Interactions.NoRemainder.INSTANCE),O)),List.of(),
            new Interactions.EffectBound(new Interactions.ForeignEffects(Scopes.NoMemory.INSTANCE,Scopes.NoMemory.INSTANCE,List.of()),List.of()),
            new Control.InvocationOutcomes(List.of(new Control.Normal(new LabelId(U,next))),Scopes.NoControl.INSTANCE),new Interactions.KnownContract(new Interactions.ContractRef(authority,"1",List.of(O))));
    }
    static Map<String,Object> analyze(String name,List<Operations.Invoke> invokes)throws Exception {
        var seq=new ArrayList<Sequence>();for(var i:invokes)seq.add(new Sequence(new LabelId(U,i.header().id().localId()),List.of(),i,O));seq.add(returning(U,"end",List.of()));
        var base=publication(P,List.of(unit(U,List.of(entry(U,"entry",invokes.getFirst().header().id().localId())),seq,List.of())),List.of());
        var artifact=new Origins.Artifact(new ArtifactId(P,"manual.air"),"manual.air",Optional.empty());
        var written=new Origins.Written(O,artifact.id(),Optional.empty(),List.of(),true);
        var p=new Publication(P,base.airVersion(),new Capabilities.Manifest(List.of(new Capabilities.Capability("cics-ts.file","1")),List.of()),List.of(artifact),base.units(),base.storage(),base.resources(),base.artifactRelations(),List.of(written),base.coverage(),base.uncertainties(),base.premises());
        return analyzePublication(name,p);
    }
    static Map<String,Object> analyzePublication(String name,Publication p)throws Exception {return analyzePublication(name,p,io.github.gustavo2358.analysis.values.StorageAnalysisMode.LOGICAL_ONLY);}
    static Map<String,Object> analyzePublication(String name,Publication p,io.github.gustavo2358.analysis.values.StorageAnalysisMode mode)throws Exception {
        var validity=AirValidator.validate(p);assertEquals(ValidationResult.Status.STRUCTURALLY_VALID,validity.status(),validity.issues().toString());
        var codec=new AirJson();var air=codec.encode(p);assertEquals(p,codec.decode(air));
        var result=new DependencyAnalysis(mode).prepare(codec.decode(air));var out=new ByteArrayOutputStream();new DependencyJson().write(result,out);
        var dir=Path.of("target/fd-w8/manual");Files.createDirectories(dir);Files.write(dir.resolve(name+".air.json"),air);Files.write(dir.resolve(name+".json"),out.toByteArray());
        return map(FileDependencyJson.value(result.fileDependencies()));
    }
    @SuppressWarnings("unchecked") static Map<String,Object> map(Object value){assertNotNull(value);return (Map<String,Object>)value;}
    @SuppressWarnings("unchecked") static List<Map<String,Object>> list(Object value){return (List<Map<String,Object>>)value;}
    static Map<String,Object> site(Map<String,Object> doc,String name){for(var s:list(doc.get("sites")))if(map(s.get("operation")).get("localId").equals(name))return s;throw new AssertionError(name);}
    @Test void sameFileKeepsDefaultAndTwoExplicitSystemsDistinct()throws Exception {
        var doc=analyze("systems",List.of(file("default","r1",false,"","cics-ts.file-control"),file("r1","r2",true,"R001","cics-ts.file-control"),file("r2","end",true,"R002","cics-ts.file-control")));
        assertEquals("DEFAULT",map(site(doc,"default").get("context")).get("selection"));
        for(var name:List.of("r1","r2")) {var c=map(site(doc,name).get("context"));assertEquals("EXPLICIT",c.get("selection"));assertEquals(false,c.get("unknownRemainder"));assertEquals(name.equals("r1")?"R001":"R002",list(c.get("candidates")).getFirst().get("referenceName"));}
        for(var e:list(doc.get("edges")))assertEquals(site(doc,(String)map(e.get("site")).get("localId")).get("context"),e.get("context"));
        assertEquals(0L,map(doc.get("metrics")).get("possibleValuesPreparations"));
    }
    @Test void badOrMissingContextCannotPretendToBeDefault()throws Exception {
        var doc=analyze("invalid",List.of(file("empty","wrong",true,"","cics-ts.file-control"),file("wrong","end",false,"","different-contract")));
        assertEquals("EXPLICIT",map(site(doc,"empty").get("context")).get("selection"));assertEquals(true,map(site(doc,"empty").get("context")).get("unknownRemainder"));
        assertEquals("UNAVAILABLE",map(site(doc,"wrong").get("context")).get("selection"));
    }

    static final StorageId STORAGE=new StorageId(P,"names");static final ObjectId F=new ObjectId(U,"file"), S=new ObjectId(U,"system");
    static final UncertaintyId GAP=new UncertaintyId(P,"selector");
    static final Memory.Codec CODEC=new Memory.ExtensionCodec("text.ebcdic.ibm1047","1",Types.known(Types.Builtin.TEXT));
    static Operations.Invoke computed(String id,String next) {
        var i=file(id,next,true,"placeholder","cics-ts.file-control");var args=new ArrayList<>(i.arguments());
        args.set(1,new Interactions.ValueArgument(new Expressions.Read(operand(i.header().id(),"sysid",Operand.Role.ARGUMENT_VALUE),new Places.ObjectPlace(operand(i.header().id(),"system-place",Operand.Role.VALUE_READ),S))));
        var target=new Interactions.ComputedTarget("file","cics.file",new Expressions.Read(operand(i.header().id(),"file-target",Operand.Role.CALL_TARGET),new Places.ObjectPlace(operand(i.header().id(),"file-place",Operand.Role.VALUE_READ),F)),new Interactions.ExtensionName("cics-ts.file","1"),O);
        return new Operations.Invoke(i.header(),i.action(),target,args,i.results(),i.signature(),i.effectOperands(),i.effectBound(),i.outcomes(),i.contract());
    }
    static Sequence seq(String name,List<Instruction> instructions,Terminator term){return new Sequence(new LabelId(U,name),instructions,term,O);}
    static Operations.Jump jumpTo(String from,String next){return new Operations.Jump(header(U,"jump-"+from),new LabelId(U,next));}
    static Publication names(List<Sequence> sequences,int width) {
        var objects=new ArrayList<Memory.ObjectDeclaration>();
        for(var id:List.of(F,S))objects.add(new Memory.ObjectDeclaration(id,Optional.empty(),Types.known(Types.Builtin.TEXT),new Memory.ViewBinding(STORAGE,BigInteger.valueOf(id.equals(F)?0:8),BigInteger.valueOf(id.equals(F)?8:width),CODEC),Memory.Visibility.PRIVATE,O,Evidence.CoverageStatus.MODELED,header(U,"metadata").precision()));
        var artifact=new Origins.Artifact(new ArtifactId(P,"manual"),"manual",Optional.empty());
        return new Publication(P,SemanticVersion.AIR_2_0_0,new Capabilities.Manifest(List.of(new Capabilities.Capability("cics-ts.file","1"),new Capabilities.Capability("memory.regions","1"),new Capabilities.Capability("text.ebcdic.ibm1047","1")),List.of()),List.of(artifact),List.of(unit(U,List.of(entry(U,"entry","start")),sequences,objects)),
            List.of(new Memory.Region(new Memory.StorageHeader(STORAGE,Optional.of(U),Memory.Lifetime.PERSISTENT,Memory.Visibility.PRIVATE,O),Optional.of(BigInteger.valueOf(12)),Optional.empty())),List.of(),List.of(),List.of(new Origins.Written(O,artifact.id(),Optional.empty(),List.of(),true)),coverage(new Scopes.PublicationScope(P)),
            List.of(new Evidence.Uncertainty(GAP,"MANUAL_UNKNOWN",List.of(Evidence.Dimension.VALUES),new Scopes.UnitScope(U),"input selector",O)),List.of());
    }
    @Test void computedContextUsesBeforeAndSameGeneralProviderAsFile()throws Exception {
        var doc=analyzePublication("computed-systems",names(List.of(
            seq("start",List.of(assign(U,"file-seed",F,"ACCOUNTS"),assign(U,"sysid-first",S,"R001")),jumpTo("start","one")),
            seq("one",List.of(),computed("one","change")),seq("change",List.of(assign(U,"sysid-second",S,"R002")),jumpTo("change","two")),seq("two",List.of(),computed("two","end")),returning(U,"end",List.of())),4));
        for(var id:List.of("one","two")) {
            var c=map(site(doc,id).get("context"));assertEquals("COMPUTED",c.get("targetKind"));assertEquals(true,c.get("unknownRemainder"));
            var candidate=list(c.get("candidates")).getFirst();assertEquals(id.equals("one")?"R001":"R002",candidate.get("referenceName"));
            assertEquals(id.equals("one")?"sysid-first":"sysid-second",map(list(candidate.get("supports")).getFirst().get("producer")).get("localId"));
            assertEquals("BEFORE",map(c.get("valuePoint")).get("position"));assertEquals("ACCOUNTS",list(site(doc,id).get("candidates")).getFirst().get("referenceName"));
        }
        assertEquals(1L,map(doc.get("metrics")).get("possibleValuesPreparations"));
    }
    @Test void contextJoinsKeepIndependentCandidatesAndRemainder()throws Exception {
        for(var mode:List.of("closed","partial","unknown")) {
            var h=header(U,"choose");var branch=new Operations.Branch(h,new Expressions.Unknown(operand(h.id(),"condition",Operand.Role.PREDICATE),Types.known(Types.Builtin.BOOL),List.of(),Scopes.NoMemory.INSTANCE,GAP),new LabelId(U,"a"),new LabelId(U,"b"));
            var doc=analyzePublication("systems-"+mode,names(List.of(seq("start",List.of(assign(U,"file-seed",F,"ACCOUNTS")),branch),
                seq("a",mode.equals("unknown")?List.of():List.of(assign(U,"seed-a",S,"R001")),jumpTo("a","file")),
                seq("b",mode.equals("closed")?List.of(assign(U,"seed-b",S,"R002")):List.of(),jumpTo("b","file")),seq("file",List.of(),computed("file","end")),returning(U,"end",List.of())),4));
            var c=map(site(doc,"file").get("context"));assertEquals(true,c.get("unknownRemainder"));
            assertEquals(mode.equals("closed")?List.of("R001","R002"):mode.equals("partial")?List.of("R001"):List.of(),list(c.get("candidates")).stream().map(x->x.get("referenceName")).toList());
            assertEquals(true,site(doc,"file").get("unknownRemainder"),"physical completeness remains open, independently of context candidates");
        }
    }
    @Test void sourceInventoryGapOpensEvidenceWithoutInventingModelValues()throws Exception {
        var base=names(List.of(seq("start",List.of(assign(U,"file-seed",F,"ACCOUNTS"),assign(U,"system-seed",S,"R001")),jumpTo("start","file")),seq("file",List.of(),computed("file","end")),returning(U,"end",List.of())),4);
        var closed=analyzePublication("source-closed-counterproof",base,io.github.gustavo2358.analysis.values.StorageAnalysisMode.EXPERIMENTAL_PHYSICAL);
        var gap=new UncertaintyId(P,"inventory");var gaps=new ArrayList<>(base.uncertainties());
        gaps.add(new Evidence.Uncertainty(gap,"MANUAL_INVENTORY_GAP",List.of(Evidence.Dimension.CONTROL),new Scopes.PublicationScope(P),"source inventory has an unrepresented remainder",O));
        var partial=new Publication(base.id(),base.airVersion(),base.capabilities(),base.artifacts(),base.units(),base.storage(),base.resources(),base.artifactRelations(),base.origins(),new Evidence.Coverage(Evidence.InventoryStatus.PARTIAL,new Scopes.PublicationScope(P),List.of(),List.of(gap)),gaps,base.premises());
        var opened=analyzePublication("source-partial-counterproof",partial,io.github.gustavo2358.analysis.values.StorageAnalysisMode.EXPERIMENTAL_PHYSICAL);
        assertEquals(site(closed,"file").get("candidates"),site(opened,"file").get("candidates"));
        assertEquals(false,site(closed,"file").get("unknownRemainder"));assertEquals(false,site(opened,"file").get("unknownRemainder"));
        assertEquals(List.of("FILE_SOURCE_VALUE_REMAINDER"),site(opened,"file").get("analysisReasons"));
        var a=map(site(closed,"file").get("context"));var b=map(site(opened,"file").get("context"));
        assertEquals(a.get("candidates"),b.get("candidates"));assertEquals(false,a.get("unknownRemainder"));assertEquals(false,b.get("unknownRemainder"));
        assertEquals(List.of("CICS_SYSID_SOURCE_VALUE_REMAINDER"),b.get("analysisReasons"));
    }

}
