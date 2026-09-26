package io.github.gustavo2358.analysis.values;
import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import java.math.BigInteger;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static io.github.gustavo2358.analysis.values.ValuesFixtures.*;
import static io.github.gustavo2358.analysis.values.RegionalValuesTest.*;
class MixedStorageTest {
    @Test void unknownRegionAndCodecRetainInventoryWithoutCrossBaseInterference() {
        for(boolean separated:List.of(false,true)) {
            var cell=new StorageId(P,"cell");var open=new StorageId(P,"open-region");var logical=new ObjectId(U,"logical");var opaque=new ObjectId(U,"opaque");var reason=new UncertaintyId(P,"unsupported-storage");
            var h=header(U,"opaque-write");var havoc=new Operations.HavocMay(h,new Scopes.ObjectsMemory(List.of(opaque)),reason);
            var p=regional(List.of(returning(U,"s0",List.of(assign(U,"cell-value",logical,"CELL"),assign(U,"region-value",WHOLE,"PGM00001"),havoc))));
            var u=p.units().getFirst();var objects=new ArrayList<>(u.objects());
            objects.add(new Memory.ObjectDeclaration(logical,Optional.empty(),Types.known(Types.Builtin.TEXT),new Memory.CellBinding(cell),Memory.Visibility.PRIVATE,origin(P),Evidence.CoverageStatus.MODELED,header(U,"meta").precision()));
            objects.add(new Memory.ObjectDeclaration(opaque,Optional.empty(),Types.known(Types.Builtin.TEXT),new Memory.ViewBinding(open,BigInteger.ZERO,BigInteger.valueOf(4),new Memory.UnknownCodec(Types.known(Types.Builtin.TEXT),reason)),Memory.Visibility.PRIVATE,origin(P),Evidence.CoverageStatus.MODELED,header(U,"meta").precision()));
            var storage=new ArrayList<>(p.storage());storage.add(new Memory.Cell(new Memory.StorageHeader(cell,Optional.of(U),Memory.Lifetime.PERSISTENT,Memory.Visibility.PRIVATE,origin(P)),Types.known(Types.Builtin.TEXT)));
            storage.add(new Memory.Region(new Memory.StorageHeader(open,Optional.of(U),Memory.Lifetime.PERSISTENT,Memory.Visibility.PRIVATE,origin(P)),Optional.empty(),Optional.of(reason)));
            var uncertainties=List.of(new Evidence.Uncertainty(reason,"UNSUPPORTED_STORAGE",List.of(Evidence.Dimension.STORAGE,Evidence.Dimension.VALUES),new Scopes.UnitScope(U),"explicit unknown extent/codec",origin(P)));
            var premises=separated?List.of(new Proofs.Premise(new PremiseId(P,"separate"),"manual oracle","independent allocations",origin(P),new Proofs.DisjointStorage(List.of(R,cell,open)))):List.<Proofs.Premise>of();
            p=new Publication(P,p.airVersion(),p.capabilities(),p.artifacts(),List.of(unit(U,u.entries(),u.sequences(),objects)),storage,p.resources(),p.artifactRelations(),p.origins(),p.coverage(),uncertainties,premises);
            assertEquals(3,p.storage().size());assertEquals(5,p.units().getFirst().objects().size());
            var execution=run(p);
            for(var object:List.of(logical,WHOLE)) {var fact=at(execution,"return-s0",object);assertEquals(List.of(object.equals(logical)?"CELL":"PGM00001"),texts(fact));assertFalse(fact.modelValueRemainder());}
            var query=new io.github.gustavo2358.analysis.query.PointQuery<>(io.github.gustavo2358.analysis.query.ProgramPoint.before(new EntryId(U,"entry"),new OperationId(U,"return-s0")),opaque);
            var observation=execution.observe(List.of(query)).observations().getFirst();
            assertTrue(observation.status()!=io.github.gustavo2358.analysis.query.ObservationBatch.QueryStatus.VALUE||observation.value().modelValueRemainder(),"unknown codec never becomes a closed empty value");
        }
    }
}
