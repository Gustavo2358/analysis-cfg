package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.air.json.AirJson;
import io.github.gustavo2358.analysis.dependencies.*;
import io.github.gustavo2358.analysis.cfg.application.*;
import io.github.gustavo2358.analysis.cfg.domain.ProjectionPolicy;
import io.github.gustavo2358.analysis.structure.AnalysisSession;
import io.github.gustavo2358.analysis.values.PossibleValuesAnalysis;
import java.math.BigInteger;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static io.github.gustavo2358.analysis.adapters.ResultFixtures.*;

/** AIR-only oracle: explicit bytes, displaced view, and target evaluation before interaction. */
class RegionalDependencyTest {
    static Publication group(boolean literalOnly) {
        var p=new PublicationId("regional-dependency");var u=new UnitId(p,"unit");var o=origin(p);
        var region=new StorageId(p,"allocation");var codec=new Memory.ExtensionCodec("text.ebcdic.ibm1047","1",Types.known(Types.Builtin.TEXT));
        var whole=new Memory.ObjectDeclaration(new ObjectId(u,"unrelated-name-a"),Optional.of("same-name"),Types.known(Types.Builtin.TEXT),
            new Memory.ViewBinding(region,BigInteger.ZERO,BigInteger.valueOf(14),codec),Memory.Visibility.PRIVATE,o,Evidence.CoverageStatus.MODELED,header(u,"meta").precision());
        var child=new Memory.ObjectDeclaration(new ObjectId(u,"unrelated-name-b"),Optional.of("same-name"),Types.known(Types.Builtin.TEXT),
            new Memory.ViewBinding(region,BigInteger.valueOf(6),BigInteger.valueOf(8),codec),Memory.Visibility.PRIVATE,o,Evidence.CoverageStatus.MODELED,header(u,"meta").precision());
        var h=header(u,"group-write");
        var offset=new Expressions.Literal(operand(h.id(),"offset",Operand.Role.VALUE_READ),new Values.IntValue(BigInteger.ZERO));
        var extent=new Expressions.Literal(operand(h.id(),"extent",Operand.Role.VALUE_READ),new Values.IntValue(BigInteger.valueOf(14)));
        var destination=new Places.RegionSlice(operand(h.id(),"destination",Operand.Role.VALUE_WRITE),region,offset,extent,Memory.IdentityBytes.INSTANCE,Types.known(Types.Builtin.BYTES));
        var value=new Expressions.Literal(operand(h.id(),"literal",Operand.Role.VALUE_READ),new Values.BytesValue(List.of(193,194,195,196,197,198,215,199,212,240,240,240,240,241)));
        var first=W1dModelTest.call(u,"first-call","next",literalOnly?null:child.id(),true);
        var second=W1dModelTest.call(u,"second-call","end",literalOnly?null:child.id(),false);
        var sequences=List.of(new Sequence(new LabelId(u,"start"),List.of(new Operations.Assign(h,destination,value)),first,o),
            new Sequence(new LabelId(u,"next"),List.of(),second,o),returning(u,"end",List.of()));
        var base=publication(p,List.of(unit(u,List.of(entry(u,"entry","start")),sequences,List.of(whole,child))),
            List.of(new Memory.Region(new Memory.StorageHeader(region,Optional.of(u),Memory.Lifetime.PERSISTENT,Memory.Visibility.PRIVATE,o),Optional.of(BigInteger.valueOf(14)),Optional.empty())));
        var artifact=new ArtifactId(p,"manual-air-test");
        return new Publication(p,base.airVersion(),new Capabilities.Manifest(List.of(Capabilities.MEMORY_REGIONS,Capabilities.IBM1047),List.of()),
            List.of(new Origins.Artifact(artifact,"RegionalDependencyTest.java",Optional.empty())),base.units(),base.storage(),base.resources(),base.artifactRelations(),
            List.of(new Origins.Written(o,artifact,Optional.empty(),List.of(),true)),base.coverage(),base.uncertainties(),base.premises());
    }
    @Test void wholeWriteFeedsDisplacedCallViewBeforeForeignMayEffects() {
        var p=group(false);var codec=new AirJson();var restored=codec.decode(codec.encode(p));assertEquals(p,restored);
        var result=new DependencyAnalysis().prepare(restored);
        assertEquals(2,result.sites().size());assertEquals(1L,result.metrics().get("possibleValuesRuns"));
        var first=result.sites().stream().filter(s->s.operation().localId().equals("first-call")).findFirst().orElseThrow();
        assertEquals(List.of("PGM00001"),first.candidates().stream().map(DependencySiteFact.Candidate::referenceName).toList());
        assertFalse(first.modelValueRemainder());assertEquals("unrelated-name-b",first.subject().localId());
        assertEquals(Set.of("group-write"),new HashSet<>(first.candidates().getFirst().supports().stream().map(s->s.producer().localId()).toList()));
        var after=result.sites().stream().filter(s->s.operation().localId().equals("second-call")).findFirst().orElseThrow();
        assertEquals(List.of("PGM00001"),after.candidates().stream().map(DependencySiteFact.Candidate::referenceName).toList());
        assertTrue(after.modelValueRemainder(),"foreign MAY effects apply after target evaluation and preserve old possibilities");
    }
    @Test void literalOnlySitesDoNotDemandRegionalValues() {
        var result=new DependencyAnalysis().prepare(group(true));assertEquals(2,result.sites().size());assertEquals(0L,result.metrics().get("possibleValuesRuns"));
    }
    @Test void historicalScalarProfilesContinueToRefuseRegions() {
        var p=group(false);var cfg=W1dBoundaryTest.build(p);assertEquals(CfgBuildResult.Status.CFG_BUILT,cfg.status());
        var session=AnalysisSession.open(cfg,p,ProjectionPolicy.KNOWN_SUBSET,p.units().getFirst().entries()).session().orElseThrow();
        for(var profile:List.of(PossibleValuesAnalysis.PROFILE,PossibleValuesAnalysis.EFFECTS_PROFILE))
            assertEquals(PossibleValuesAnalysis.Status.UNSUPPORTED,PossibleValuesAnalysis.prepare(session,profile).status());
    }
}
