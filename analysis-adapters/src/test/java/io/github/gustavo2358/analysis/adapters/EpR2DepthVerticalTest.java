package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.air.json.AirJson;
import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.analysis.dataflow.*;
import io.github.gustavo2358.analysis.query.*;
import io.github.gustavo2358.analysis.storage.*;
import io.github.gustavo2358.analysis.values.*;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static io.github.gustavo2358.analysis.adapters.ResultFixtures.*;

class EpR2DepthVerticalTest {
    static final PublicationId P=new PublicationId("ep-r2-depth");
    static final UnitId U=new UnitId(P,"unit");
    static final StorageId R=new StorageId(P,"region");
    static final EntryId ENTRY=new EntryId(U,"entry");
    static ObjectId field(int i) {return new ObjectId(U,"field-"+i);}
    static Publication fixture(int segments) {
        var fields=new ArrayList<Memory.ObjectDeclaration>();
        for(int i=0;i<segments;i++)fields.add(new Memory.ObjectDeclaration(field(i),Optional.empty(),Types.known(Types.Builtin.TEXT),
            new Memory.ViewBinding(R,BigInteger.valueOf(8L*i),BigInteger.valueOf(8),new Memory.ExtensionCodec("text.ebcdic.ibm1047","1",Types.known(Types.Builtin.TEXT))),
            Memory.Visibility.UNKNOWN,origin(P),Evidence.CoverageStatus.MODELED,header(U,"metadata").precision()));
        var gap=new UncertaintyId(P,"may-value");var last=field(segments-1);var h=header(U,"copy");
        var copy=new Operations.Assign(h,new Places.ObjectPlace(operand(h.id(),"destination",Operand.Role.VALUE_WRITE),field(0)),
            new Expressions.FitText(operand(h.id(),"fit",Operand.Role.VALUE_READ),new Expressions.Read(operand(h.id(),"read",Operand.Role.VALUE_READ),new Places.ObjectPlace(operand(h.id(),"source",Operand.Role.VALUE_READ),last)),BigInteger.valueOf(8)," "));
        var instructions=List.<Instruction>of(assign(U,"seed",last,"OLDPROG1"),
            new Operations.HavocMay(header(U,"may"),new Scopes.ObjectsMemory(List.of(last)),gap),copy,assign(U,"must",last,"NEWPROG1"));
        var region=new Memory.Region(new Memory.StorageHeader(R,Optional.of(U),Memory.Lifetime.PERSISTENT,Memory.Visibility.UNKNOWN,origin(P)),Optional.of(BigInteger.valueOf(8L*segments)),Optional.empty());
        var p=publication(P,List.of(unit(U,List.of(entry(U,"entry","body")),List.of(returning(U,"body",instructions)),fields)),List.of(region));
        var reason=new Evidence.Uncertainty(gap,"SYNTHETIC_MAY",List.of(Evidence.Dimension.VALUES),new Scopes.UnitScope(U),"possible external value",origin(P));
        var artifact=new ArtifactId(P,"synthetic-depth");
        return new Publication(P,p.airVersion(),new Capabilities.Manifest(List.of(Capabilities.MEMORY_REGIONS,Capabilities.IBM1047),List.of()),
            List.of(new Origins.Artifact(artifact,"EpR2DepthVerticalTest.java",Optional.empty())),p.units(),p.storage(),p.resources(),p.artifactRelations(),
            List.of(new Origins.Written(origin(P),artifact,Optional.empty(),List.of(),true)),p.coverage(),List.of(reason),List.of());
    }
    static PointQuery<StorageSubject> before(String operation,ObjectId object) {
        return new PointQuery<>(ProgramPoint.before(ENTRY,new OperationId(U,operation)),new StorageSubject.NamedObject(object));
    }
    static StorageValueFact fact(RegionalAnalysisResult result,PointQuery<StorageSubject> query,String literal,boolean remainder,String producer) {
        var value=result.observations().stream().filter(o->o.query().equals(query)).findFirst().orElseThrow().values().value();
        assertEquals(List.of(new Values.TextValue(literal)),value.candidates());assertEquals(remainder,value.modelValueRemainder());
        assertEquals(List.of(producer),value.candidateSupports().getFirst().producers().stream().map(s->s.evidence().localId()).toList());
        assertEquals(query.point(),value.point());assertFalse(value.provenance().isEmpty());return value;
    }
    @Test void manySegmentsPreserveMayMustBeforeAndCapturedSourceThroughJson() throws Exception {
        int n=8192;var codec=new AirJson();var original=fixture(n);assertTrue(io.github.gustavo2358.air.validation.AirValidator.validate(original).issues().isEmpty());var input=codec.encode(original);var p=codec.decode(input);
        var last=field(n-1);var queries=List.of(before("may",last),before("copy",last),before("must",last),before("return-body",last),before("return-body",field(0)));
        var result=new RegionalAnalysis().prepare(p,"deep-regional",queries);
        fact(result,queries.get(0),"OLDPROG1",false,"seed");
        fact(result,queries.get(1),"OLDPROG1",true,"seed");
        fact(result,queries.get(2),"OLDPROG1",true,"seed");
        fact(result,queries.get(3),"NEWPROG1",false,"must");
        var copied=fact(result,queries.get(4),"OLDPROG1",true,"seed");
        var captures=copied.alternatives().stream().flatMap(a->a.fragments().stream()).flatMap(f->f.captures().stream()).toList();
        assertFalse(captures.isEmpty());
        for(var capture:captures) {
            assertEquals(ProgramPoint.before(ENTRY,new OperationId(U,"copy")),capture.before());
            assertEquals(StorageRange.exact(BigInteger.valueOf(8L*(n-1)),BigInteger.valueOf(8)),capture.sourceRange().location().range().orElseThrow());
            assertEquals(StorageRange.exact(BigInteger.ZERO,BigInteger.valueOf(8)),capture.destinationRange().location().range().orElseThrow());
        }
        var metrics=result.statistics().get("values");
        assertEquals((long)n,metrics.get("prepare_partitionSegments"));assertEquals(1L,metrics.get("prepare_maxGroupBases"));
        assertTrue(metrics.get("maxStateAlternatives")<=2L*n+4,"two local alternatives, not combinatorial growth");
        var output=new ByteArrayOutputStream();new RegionalResultJson().write(result,output);
        var reversed=new ArrayList<>(queries);Collections.reverse(reversed);var second=new ByteArrayOutputStream();
        new RegionalResultJson().write(new RegionalAnalysis().prepare(p,"deep-regional",reversed),second);
        assertArrayEquals(output.toByteArray(),second.toByteArray());
        var dir=Path.of("target/ep-r2-depth");Files.createDirectories(dir);Files.write(dir.resolve("input.air.json"),input);Files.write(dir.resolve("result.json"),output.toByteArray());
        System.out.println("EP_R2_DEPTH_VERTICAL segments="+n+" "+metrics);
    }
}
