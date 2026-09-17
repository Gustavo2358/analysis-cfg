package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.air.json.AirJson;
import io.github.gustavo2358.air.validation.*;
import io.github.gustavo2358.analysis.dependencies.*;
import java.math.BigInteger;
import java.nio.file.*;
import java.io.ByteArrayOutputStream;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static io.github.gustavo2358.analysis.adapters.FileComputedOracleTest.*;
import static io.github.gustavo2358.analysis.adapters.ResultFixtures.*;

/** Independent cross-contract witnesses: no source parser, observed-output expectations or private data. */
final class FileEpR2CompositionTest {
    static Publication skeleton(String literalFile, String literalCall) {
        var call=W1dModelTest.call(U,"call","end",obj("name"),false);
        Interactions.Target target=literalCall!=null
            ?new Interactions.LiteralTarget("program","cobol.program",literalCall,Interactions.ExactName.INSTANCE,O)
            :new Interactions.ComputedTarget("program","cobol.program",new Expressions.Read(
                operand(call.header().id(),"name",Operand.Role.CALL_TARGET),new Places.RegionSlice(
                    operand(call.header().id(),"range",Operand.Role.VALUE_READ),BASE,
                    number(call.header().id(),"offset",0),number(call.header().id(),"length",8),CODEC,Types.known(Types.Builtin.TEXT))),Interactions.ExactName.INSTANCE,O);
        // A physical CALL query selects the same general StorageValues provider as FILE.
        // Named CALL queries independently use RegionalValues; that is not a FILE-specific solver.
        call=new Operations.Invoke(call.header(),call.action(),target,
            call.arguments(),call.results(),call.signature(),call.effectOperands(),call.effectBound(),call.outcomes(),call.contract());
        return publication(List.of(seq("start",List.of(),file("file","call","name",literalFile,POLICY,false)),
            seq("call",List.of(),call),returning(U,"end",List.of())));
    }
    static Publication entry(Publication p,int dimensions,boolean strongAndOpen,boolean reverse) {
        var u=p.units().getFirst();var e=u.entries().getFirst();
        var objects=new ArrayList<Memory.ObjectDeclaration>();var conditions=new ArrayList<Entries.InitialCondition>();
        for(int i=0;i<dimensions;i++) {
            var object=obj(i==0?"name":"factor-"+i);
            objects.add(new Memory.ObjectDeclaration(object,Optional.empty(),Types.known(Types.Builtin.TEXT),
                new Memory.ViewBinding(BASE,BigInteger.valueOf(8L*i),BigInteger.valueOf(8),CODEC),Memory.Visibility.PRIVATE,O,
                Evidence.CoverageStatus.MODELED,header(U,"metadata").precision()));
            var owner=new EntryOwner(e.id());
            var place=new Places.ObjectPlace(new Operand.Header(new OperandId(owner,"initial-"+i),Operand.Role.VALUE_WRITE,O),object);
            var value=new Expressions.Literal(new Operand.Header(new OperandId(owner,"value-"+i),Operand.Role.VALUE_READ,O),
                new Values.TextValue(String.format(Locale.ROOT,"PROG%04d",i)));
            conditions.add(new Entries.InitialCondition(place,strongAndOpen?new Entries.LiteralInitial(value):new Entries.PossibleLiterals(List.of(value),GAP),O,List.of()));
            if(strongAndOpen)conditions.add(new Entries.InitialCondition(new Places.ObjectPlace(
                new Operand.Header(new OperandId(owner,"open-"+i),Operand.Role.VALUE_WRITE,O),object),new Entries.ExternalUnknown(GAP),O,List.of()));
        }
        if(reverse)Collections.reverse(conditions);
        var ee=new Entries.Entry(e.id(),e.initialLabel(),e.signature(),new Entries.EntryState(conditions,List.of()),e.origin());
        var uu=new io.github.gustavo2358.air.model.Unit(u.id(),u.containingUnit(),objects,u.visibleObjects(),List.of(ee),u.sequences(),u.completionPorts(),u.body(),u.bodyUnavailable(),u.coverage(),u.origin());
        var caps=new ArrayList<>(p.capabilities().required());caps.add(Capabilities.ENTRY_POSSIBILITIES_V2);
        return new Publication(p.id(),p.airVersion(),new Capabilities.Manifest(caps,List.of()),p.artifacts(),List.of(uu),
            List.of(new Memory.Region(new Memory.StorageHeader(BASE,Optional.of(U),Memory.Lifetime.PERSISTENT,Memory.Visibility.PRIVATE,O),Optional.of(BigInteger.valueOf(8L*dimensions)),Optional.empty())),
            p.resources(),p.artifactRelations(),p.origins(),p.coverage(),p.uncertainties(),p.premises());
    }
    static DependencyResult product(String name,Publication p)throws Exception {
        var valid=AirValidator.validate(p);assertEquals(ValidationResult.Status.STRUCTURALLY_VALID,valid.status(),valid.issues().toString());
        var codec=new AirJson();var bytes=codec.encode(p);var result=new DependencyAnalysis().prepare(codec.decode(bytes));
        var out=Path.of("target/fd-post-ep-r2");Files.createDirectories(out);Files.write(out.resolve(name+".air.json"),bytes);
        var wire=new ByteArrayOutputStream();new DependencyJson().write(result,wire);Files.write(out.resolve(name+".json"),wire.toByteArray());
        return result;
    }
    static void supportedBoth(DependencyResult result) {
        expect(result,"file",true,"PROG0000");var file=site(result,"file");var call=result.sites().getFirst();
        assertEquals(List.of("PROG0000"),call.candidates().stream().map(DependencySiteFact.Candidate::referenceName).toList());
        assertTrue(call.modelValueRemainder());assertFalse(call.candidates().getFirst().supports().isEmpty());
        assertEquals("initial-0",file.candidates().getFirst().supports().getFirst().producer().localId());
        assertEquals(file.candidates().getFirst().supports().getFirst().producer(),call.candidates().getFirst().supports().getFirst().producer());
        assertEquals(file.candidates().getFirst().supports().getFirst().origin(),call.candidates().getFirst().supports().getFirst().origin());
        assertEquals("BEFORE",file.valuePoint().kind().name());assertEquals("BEFORE",call.valuePoint().kind().name());
        assertEquals(1L,result.metrics().get("possibleValuesPreparations"));
        assertEquals(0L,result.fileDependencies().metrics().get("analysis.analysisRuns"));
    }
    @Test void independentEntryFactorsShareOneGeneralRunForCallAndFile()throws Exception {
        for(int dimensions:List.of(1,8,32)) {
            var result=product("factors-"+dimensions,entry(skeleton(null,null),dimensions,false,false));supportedBoth(result);
            // Each independent component has one supported literal plus an open alternative.
            // This is an additive structural bound, not a time/SLA or candidate cutoff.
            assertTrue(result.metrics().get("StorageValues.solve_boundaryAlternatives")<=2L*dimensions);
            assertTrue(result.metrics().get("StorageValues.solve_maxDecisionNodes")<=dimensions);
        }
    }
    @Test void simultaneousStrongAndOpenEntrySurvivesBothOrdersBeforeFile()throws Exception {
        var a=product("entry-forward",entry(skeleton(null,null),1,true,false));
        var b=product("entry-reverse",entry(skeleton(null,null),1,true,true));supportedBoth(a);supportedBoth(b);
        assertEquals(a.sites(),b.sites());assertEquals(a.fileDependencies().sites(),b.fileDependencies().sites());
    }
    @Test void leadingDollarCallAndIndependentCicsFilePolicyShareWire23()throws Exception {
        for(String raw:List.of("1FILE","_FILE")) {
            var result=product(raw.equals("1FILE")?"dollar-file":"dollar-file-negative",skeleton(raw,"$PROGA"));
            assertEquals(List.of("$PROGA"),result.sites().getFirst().candidates().stream().map(DependencySiteFact.Candidate::referenceName).toList());
            if(raw.equals("1FILE"))expect(result,"file",false,"1FILE");else expect(result,"file",true);
            assertEquals(0L,result.metrics().get("possibleValuesPreparations"));
        }
    }
}
