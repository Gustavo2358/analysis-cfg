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
    @Test void fixedSliceCallQueriesOnlyItsBytesBeforeForeignEffects() {
        var p=group(false);var u=p.units().getFirst();var first=u.sequences().getFirst();var call=(Operations.Invoke)first.terminator();var old=(Interactions.ComputedTarget)call.target();
        var read=(Expressions.Read)old.name();var codec=((Memory.ViewBinding)u.objects().getFirst().storage()).codec();
        var slice=new Places.RegionSlice(((Places.ObjectPlace)read.place()).header(),p.storage().getFirst().header().id(),
            new Expressions.Literal(operand(call.header().id(),"slice-offset",Operand.Role.VALUE_READ),new Values.IntValue(BigInteger.valueOf(6))),
            new Expressions.Literal(operand(call.header().id(),"slice-length",Operand.Role.VALUE_READ),new Values.IntValue(BigInteger.valueOf(3))),codec,Types.known(Types.Builtin.TEXT));
        var target=new Interactions.ComputedTarget(old.category(),old.namespace(),new Expressions.Read(read.header(),slice),old.namePolicy(),old.origin());
        var changed=new Operations.Invoke(call.header(),call.action(),target,call.arguments(),call.results(),call.signature(),call.effectOperands(),call.effectBound(),call.outcomes(),call.contract());
        var sequences=new ArrayList<>(u.sequences());sequences.set(0,new Sequence(first.label(),first.instructions(),changed,first.origin()));
        var unit=new io.github.gustavo2358.air.model.Unit(u.id(),u.containingUnit(),u.objects(),u.visibleObjects(),u.entries(),sequences,u.completionPorts(),u.body(),u.bodyUnavailable(),u.coverage(),u.origin());
        p=new Publication(p.id(),p.airVersion(),p.capabilities(),p.artifacts(),List.of(unit),p.storage(),p.resources(),p.artifactRelations(),p.origins(),p.coverage(),p.uncertainties(),p.premises());
        var result=new DependencyAnalysis().prepare(p);var site=result.sites().stream().filter(x->x.operation().equals(call.header().id())).findFirst().orElseThrow();
        assertEquals(List.of("PGM"),site.candidates().stream().map(DependencySiteFact.Candidate::referenceName).toList());assertFalse(site.modelValueRemainder());
        assertEquals(1L,result.metrics().get("possibleValuesRuns"));assertNull(site.subject());assertNotNull(site.valuePoint());
    }
    @Test void literalOnlySitesDoNotDemandRegionalValues() {
        var result=new DependencyAnalysis().prepare(group(true));assertEquals(2,result.sites().size());assertEquals(0L,result.metrics().get("possibleValuesRuns"));
    }
    @Test void literalCallsSurviveUnknownMixedStorageWithoutDemandingValueAnalysis() {
        var p=group(true);var u=p.units().getFirst();var o=origin(p.id());var gap=new UncertaintyId(p.id(),"unknown-storage");var typeGap=new UncertaintyId(p.id(),"unknown-type");var open=new StorageId(p.id(),"open-storage");var cell=new StorageId(p.id(),"legacy-cell");
        var objects=new ArrayList<>(u.objects());objects.add(new Memory.ObjectDeclaration(new ObjectId(u.id(),"unsupported"),Optional.empty(),new Types.UnknownType(typeGap),new Memory.UnknownBinding(new Scopes.StorageMemory(List.of(open)),gap),Memory.Visibility.UNKNOWN,o,Evidence.CoverageStatus.ABSTRACTED,header(u.id(),"meta").precision()));
        objects.add(new Memory.ObjectDeclaration(new ObjectId(u.id(),"legacy"),Optional.empty(),Types.known(Types.Builtin.INT),new Memory.CellBinding(cell),Memory.Visibility.PRIVATE,o,Evidence.CoverageStatus.MODELED,header(u.id(),"meta").precision()));
        var storage=new ArrayList<>(p.storage());storage.add(new Memory.Region(new Memory.StorageHeader(open,Optional.of(u.id()),Memory.Lifetime.PERSISTENT,Memory.Visibility.UNKNOWN,o),Optional.empty(),Optional.of(gap)));storage.add(new Memory.Cell(new Memory.StorageHeader(cell,Optional.of(u.id()),Memory.Lifetime.PERSISTENT,Memory.Visibility.PRIVATE,o),Types.known(Types.Builtin.INT)));
        var uncertainties=List.of(new Evidence.Uncertainty(gap,"UNSUPPORTED_STORAGE",List.of(Evidence.Dimension.STORAGE),new Scopes.UnitScope(u.id()),"unknown physical representation",o),new Evidence.Uncertainty(typeGap,"TYPE_UNKNOWN",List.of(Evidence.Dimension.VALUES),new Scopes.UnitScope(u.id()),"unknown declaration type",o));
        p=new Publication(p.id(),p.airVersion(),p.capabilities(),p.artifacts(),List.of(unit(u.id(),u.entries(),u.sequences(),objects)),storage,p.resources(),p.artifactRelations(),p.origins(),p.coverage(),uncertainties,p.premises());
        var validation=io.github.gustavo2358.air.validation.AirValidator.validate(p);assertEquals(io.github.gustavo2358.air.validation.ValidationResult.Status.STRUCTURALLY_VALID,validation.status(),validation.issues().toString());
        var result=new DependencyAnalysis().prepare(p);assertEquals(2,result.sites().size());assertEquals(0L,result.metrics().get("possibleValuesRuns"));
        for(var site:result.sites())assertFalse(site.candidates().isEmpty());
    }
    @Test void historicalScalarProfilesContinueToRefuseRegions() {
        var p=group(false);var cfg=W1dBoundaryTest.build(p);assertEquals(CfgBuildResult.Status.CFG_BUILT,cfg.status());
        var session=AnalysisSession.open(cfg,p,ProjectionPolicy.KNOWN_SUBSET,p.units().getFirst().entries()).session().orElseThrow();
        for(var profile:List.of(PossibleValuesAnalysis.PROFILE,PossibleValuesAnalysis.EFFECTS_PROFILE))
            assertEquals(PossibleValuesAnalysis.Status.UNSUPPORTED,PossibleValuesAnalysis.prepare(session,profile).status());
    }
}
