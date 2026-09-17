package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.air.json.AirJson;
import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.analysis.dependencies.*;
import java.nio.file.*;
import java.math.BigInteger;
import io.github.gustavo2358.analysis.dataflow.RegionalAnalysis;
import io.github.gustavo2358.analysis.query.PointQuery;
import io.github.gustavo2358.analysis.storage.StorageSubject;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static io.github.gustavo2358.analysis.adapters.W1dBoundaryTest.input;
import static io.github.gustavo2358.analysis.adapters.W1dEffectsTest.*;
import static io.github.gustavo2358.analysis.adapters.W1dAdversarialTest.*;

/** Same synthetic AIR, target text is the only change; no frontend or value reseeding. */
final class LeadingDollarProductTest {
    static Publication fixture(String raw,boolean computed) throws Exception {
        var p=input(computed?"dynamic-x8":"literal");
        if(!computed) {
            var t=(Interactions.LiteralTarget)invoke(p).target();
            return target(p,new Interactions.LiteralTarget(t.category(),t.namespace(),raw,t.namePolicy(),t.origin()));
        }
        var u=p.units().getFirst();
        return sequences(p,u.sequences().stream().map(s->new Sequence(s.label(),s.instructions().stream().map(i->{
            if(i instanceof Operations.Assign a)return (Instruction)new Operations.Assign(a.header(),a.destination(),new Expressions.Literal(a.value().header(),new Values.TextValue(raw)));
            return i;
        }).toList(),s.terminator(),s.origin())).toList(),u.entries());
    }
    static Publication regionalFixture(String raw) throws Exception {
        var p=fixture(raw,true);var u=p.units().getFirst();var extent=BigInteger.valueOf(raw.length());
        var objects=u.objects().stream().map(o->new Memory.ObjectDeclaration(o.id(),o.displayName(),o.typeRef(),
            new Memory.ViewBinding(((Memory.CellBinding)o.storage()).storage(),BigInteger.ZERO,extent,
                new Memory.ExtensionCodec("text.ebcdic.ibm1047","1",Types.known(Types.Builtin.TEXT))),
            o.visibility(),o.origin(),o.coverage(),o.precision())).toList();
        var unit=new io.github.gustavo2358.air.model.Unit(u.id(),u.containingUnit(),objects,u.visibleObjects(),u.entries(),u.sequences(),u.completionPorts(),u.body(),u.bodyUnavailable(),u.coverage(),u.origin());
        var storage=p.storage().stream().map(s->(Memory.Storage)new Memory.Region(s.header(),Optional.of(extent),Optional.empty())).toList();
        return new Publication(p.id(),p.airVersion(),new Capabilities.Manifest(List.of(Capabilities.MEMORY_REGIONS,Capabilities.IBM1047),List.of()),
            p.artifacts(),List.of(unit),storage,p.resources(),p.artifactRelations(),p.origins(),p.coverage(),p.uncertainties(),p.premises());
    }
    static DependencyResult product(String caseId,String raw,boolean computed,String expected) throws Exception {
        return product(caseId,raw,computed,expected,false);
    }
    static DependencyResult product(String caseId,String raw,boolean computed,String expected,boolean regional) throws Exception {
        var codec=new AirJson();var p=regional?regionalFixture(raw):fixture(raw,computed);var bytes=codec.encode(p);p=codec.decode(bytes);
        var result=new DependencyAnalysis().prepare(p);var site=result.sites().getFirst();
        assertEquals("cobol-zos-dynamic-call-minimal@1",site.nameProfile());
        assertEquals(List.of(raw),site.rawCandidates().stream().map(DependencySiteFact.RawCandidate::rawValue).toList());
        var supports=site.rawCandidates().getFirst().supports();assertFalse(supports.isEmpty());assertFalse(site.provenance().isEmpty());
        if(regional) {
            var query=new PointQuery<StorageSubject>(before(p,invoke(p).header().id()).point(),new StorageSubject.NamedObject(subject(p)));
            var source=new RegionalAnalysis().prepare(p,"f2-regional",List.of(query)).observations().getFirst().values().value();
            assertEquals(List.of(new Values.TextValue(raw)),source.candidates());assertEquals(query.point(),site.valuePoint());
            assertEquals(source.candidateSupports().getFirst().producers().getFirst().evidence(),supports.getFirst().producer());
            assertEquals(source.candidateSupports().getFirst().producers().getFirst().origin(),supports.getFirst().origin());
            assertFalse(source.provenance().isEmpty());
        } else if(computed) {
            var source=value(p,invoke(p).header().id());assertEquals(List.of(new Values.TextValue(raw)),source.candidates());
            assertEquals(source.candidateSupports().getFirst().producers().getFirst().evidence(),supports.getFirst().producer());
            assertEquals(source.candidateSupports().getFirst().producers().getFirst().origin(),supports.getFirst().origin());
            assertEquals(before(p,invoke(p).header().id()).point(),site.valuePoint());
        } else assertEquals(DependencySiteFact.SupportKind.CALL_LITERAL,supports.getFirst().kind());
        // Preserve the pre-fix output even when the expected interpretation below is RED.
        var out=Path.of("target/ep-r2-f2");Files.createDirectories(out);
        Files.write(out.resolve(caseId+".air.json"),bytes);Files.write(out.resolve(caseId+".dependencies.json"),wire(result));
        System.out.println("F2_POLICY_WITNESS case="+caseId+" raw="+raw+" candidates="+site.candidates().stream().map(DependencySiteFact.Candidate::referenceName).toList()+" supports="+supports.size()+" status="+site.targetStatus());
        if(expected==null) {
            assertTrue(site.candidates().isEmpty());assertTrue(result.edges().isEmpty());assertTrue(site.interpretationUnknownRemainder());
            assertEquals(DependencySiteFact.TargetStatus.OPEN_TARGET,site.targetStatus());
        } else {
            assertEquals(List.of(expected),site.candidates().stream().map(DependencySiteFact.Candidate::referenceName).toList());
            assertEquals(raw,site.candidates().getFirst().rawValue());assertEquals(supports,site.candidates().getFirst().supports());
            assertEquals(List.of(expected),result.edges().stream().map(e->e.candidate().referenceName()).toList());
            assertEquals(DependencySiteFact.TargetStatus.RESOLVED_CANDIDATES,site.targetStatus());
        }
        assertFalse(site.modelValueRemainder());assertTrue(site.sourceValueRemainder());
        assertTrue(site.interpretationUnknownRemainder(),"UnknownName remains open even when lexical interpretation succeeds");
        assertTrue(site.effectiveUnknownRemainder());return result;
    }
    @Test void regionalComputedDollarSurvivesBeforeSupportAndEdge() throws Exception {product("regional-dollar","$PROGA   ",true,"$PROGA",true);}
    @Test void literalDollarSurvivesFactJsonAndEdge() throws Exception {product("literal-dollar","$PROGA",false,"$PROGA");}
    @Test void computedDollarHasSupportedRawBeforeInterpretation() throws Exception {product("computed-dollar","$PROGA   ",true,"$PROGA");}
    @Test void eightCharacterDollarNameKeepsFullLength() throws Exception {product("eight-characters","$ABCDEFG",true,"$ABCDEFG");}
    @Test void nondollarAndMiddleDollarControlsKeepSupportAndRemainders() throws Exception {
        product("computed-control","PROGA   ",true,"PROGA");product("literal-control","PROGA",false,"PROGA");
        product("middle-dollar","A$PROG",true,"A$PROG");product("underscore","_PROGA",true,"_PROGA");
    }
    @Test void rejectedNamesStillPublishRawWithoutEdges() throws Exception {
        var invalid=List.of(" $PROGA","$proga","$PROG-A","$ABCDEFGH","","   ");
        for(int i=0;i<invalid.size();i++)product("negative-"+i,invalid.get(i),true,null);
        product("literal-padding","$PROGA   ",false,null);
    }
}
