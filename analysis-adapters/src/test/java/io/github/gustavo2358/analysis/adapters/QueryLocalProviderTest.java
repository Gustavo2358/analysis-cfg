package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.analysis.dependencies.*;
import java.math.BigInteger;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class QueryLocalProviderTest {
    @Test void unrelatedRegionsCannotChangeScalarProviderCandidateOrSupports() {
        var base=DependencyPreservationTest.linear(true,false);
        var expected=new DependencyAnalysis().prepare(base).sites().getFirst();
        for(int count:new int[]{1,0,100,1000}) {
            var storage=new ArrayList<>(base.storage());
            for(int n=0;n<count;n++)storage.add(new Memory.Region(new Memory.StorageHeader(new StorageId(base.id(),"unrelated-"+n),Optional.of(base.units().getFirst().id()),Memory.Lifetime.PERSISTENT,Memory.Visibility.PRIVATE,base.origins().getFirst().id()),Optional.of(BigInteger.TEN),Optional.empty()));
            var capabilities=new ArrayList<>(base.capabilities().required());
            if(count>0)capabilities.add(Capabilities.MEMORY_REGIONS);
            var p=new Publication(base.id(),base.airVersion(),new Capabilities.Manifest(capabilities,base.capabilities().provided()),base.artifacts(),base.units(),storage,base.resources(),base.artifactRelations(),base.origins(),base.coverage(),base.uncertainties(),base.premises());
            var result=new DependencyAnalysis().prepare(p);var actual=result.sites().getFirst();
            assertEquals(expected.candidates(),actual.candidates(),"count="+count);
            assertTrue(result.metrics().containsKey("PossibleValues.nodesTransferred"),"scalar provider must run for count="+count);
            assertEquals(1L,result.metrics().get("scalarSelections"),"count="+count);
            assertEquals(0L,result.metrics().get("regionalSelections"));
            assertFalse(actual.analysisReasons().contains("PHYSICAL_PROPAGATION_DISABLED"));
            assertEquals(0L,result.metrics().get("physicalGroupsApplied"));
            assertEquals(0L,result.metrics().get("physicalWritesApplied"));
        }
    }
    @Test void unusedPhysicalViewDoesNotRefuseLogicalDemand() {
        var base=DependencyPreservationTest.linear(true,false);var u=base.units().getFirst();var model=u.objects().getFirst();
        var region=new StorageId(base.id(),"unrelated-region");
        var storage=new ArrayList<>(base.storage());storage.add(new Memory.Region(new Memory.StorageHeader(region,Optional.of(u.id()),Memory.Lifetime.ACTIVATION,Memory.Visibility.PRIVATE,model.origin()),Optional.of(BigInteger.TEN),Optional.empty()));
        var objects=new ArrayList<>(u.objects());objects.add(new Memory.ObjectDeclaration(new ObjectId(u.id(),"unused-view"),Optional.empty(),model.typeRef(),new Memory.ViewBinding(region,BigInteger.ZERO,BigInteger.valueOf(8),new Memory.ExtensionCodec("text.ebcdic.ibm1047","1",model.typeRef())),model.visibility(),model.origin(),model.coverage(),model.precision()));
        var caps=new ArrayList<>(base.capabilities().required());caps.add(Capabilities.MEMORY_REGIONS);caps.add(Capabilities.IBM1047);
        var p=new Publication(base.id(),base.airVersion(),new Capabilities.Manifest(caps,List.of()),base.artifacts(),List.of(ResultFixtures.unit(u.id(),u.entries(),u.sequences(),objects)),storage,base.resources(),base.artifactRelations(),base.origins(),base.coverage(),base.uncertainties(),base.premises());
        var result=new DependencyAnalysis().prepare(p);
        assertEquals(new DependencyAnalysis().prepare(base).sites().getFirst().candidates(),result.sites().getFirst().candidates());
        assertEquals(1L,result.metrics().get("scalarSelections"));assertEquals(0L,result.metrics().get("regionalSelections"));
    }
    @Test void anotherUnitCannotRefuseThisEntryDemand() {
        var base=DependencyPreservationTest.linear(true,false);var foreign=new UnitId(base.id(),"other-unit");var origin=base.origins().getFirst().id();
        var region=new StorageId(base.id(),"other-region");var target=new ObjectId(foreign,"physical-object");
        var model=base.units().getFirst().objects().getFirst();
        var object=new Memory.ObjectDeclaration(target,Optional.empty(),model.typeRef(),new Memory.ViewBinding(region,BigInteger.ZERO,BigInteger.valueOf(8),Memory.AsciiText.INSTANCE),Memory.Visibility.PRIVATE,origin,model.coverage(),ResultFixtures.header(foreign,"metadata").precision());
        var other=ResultFixtures.unit(foreign,List.of(ResultFixtures.entry(foreign,"entry","start")),List.of(ResultFixtures.returning(foreign,"start",List.of(ResultFixtures.assign(foreign,"unrelated-write",target,"OTHER   ")))),List.of(object));
        var storage=new ArrayList<>(base.storage());storage.add(new Memory.Region(new Memory.StorageHeader(region,Optional.of(foreign),Memory.Lifetime.ACTIVATION,Memory.Visibility.PRIVATE,origin),Optional.of(BigInteger.valueOf(8)),Optional.empty()));
        var p=new Publication(base.id(),base.airVersion(),new Capabilities.Manifest(List.of(Capabilities.MEMORY_REGIONS),List.of()),base.artifacts(),List.of(base.units().getFirst(),other),storage,base.resources(),base.artifactRelations(),base.origins(),base.coverage(),base.uncertainties(),base.premises());
        var result=new DependencyAnalysis().prepare(p);assertEquals(1L,result.metrics().get("scalarSelections"));assertEquals(0L,result.metrics().get("regionalSelections"));
        assertEquals(new DependencyAnalysis().prepare(base).sites().getFirst().candidates(),result.sites().getFirst().candidates());
    }
    @Test void anIndirectMayWriteCannotBeDiscardedAsAnUnusedDeclaration() {
        var base=DependencyPreservationTest.linear(true,false);var u=base.units().getFirst();var object=u.objects().getFirst();
        var alias=new ObjectId(u.id(),"indirect");var gap=new UncertaintyId(base.id(),"unknown-write");
        var objects=new ArrayList<>(u.objects());objects.add(new Memory.ObjectDeclaration(alias,Optional.empty(),object.typeRef(),new Memory.AliasBinding(object.id()),object.visibility(),object.origin(),object.coverage(),object.precision()));
        var sequences=new ArrayList<>(u.sequences());var first=sequences.getFirst();var instructions=new ArrayList<>(first.instructions());
        instructions.add(new Operations.HavocMay(ResultFixtures.header(u.id(),"indirect-write"),new Scopes.ObjectsMemory(List.of(alias)),gap));
        sequences.set(0,new Sequence(first.label(),instructions,first.terminator(),first.origin()));
        var uncertainty=new Evidence.Uncertainty(gap,"UNKNOWN_WRITE",List.of(Evidence.Dimension.EFFECTS),new Scopes.UnitScope(u.id()),"possible indirect write",object.origin());
        var p=new Publication(base.id(),base.airVersion(),base.capabilities(),base.artifacts(),List.of(ResultFixtures.unit(u.id(),u.entries(),sequences,objects)),base.storage(),base.resources(),base.artifactRelations(),base.origins(),base.coverage(),List.of(uncertainty),base.premises());
        var result=new DependencyAnalysis().prepare(p);assertEquals(1L,result.metrics().get("scalarAdmissionRefused"));
        assertEquals(1L,result.metrics().get("regionalSelections"));assertTrue(result.sites().getFirst().modelValueRemainder());
    }
    @Test void literalNeverRequestsValueAdmission() throws Exception {
        var result=new DependencyAnalysis().prepare(W1dBoundaryTest.input("literal"));
        assertEquals(0L,result.metrics().get("scalarAdmissionAttempts"));
        assertEquals(0L,result.metrics().get("possibleValuesRuns"));
    }
}
