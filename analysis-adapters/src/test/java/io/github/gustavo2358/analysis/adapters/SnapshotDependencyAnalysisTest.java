package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.air.validation.*;
import io.github.gustavo2358.analysis.dependencies.SnapshotDependencyAnalysis;
import io.github.gustavo2358.analysis.solver.*;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Executable proof of the first validated snapshot-to-dependency production slice. */
final class SnapshotDependencyAnalysisTest {
    private static AnalysisResources resources(){return new AnalysisResources(new AnalysisResources.Limits(64_000_000,64_000_000,0,256_000_000,4,1_000_000_000,1_000_000));}

    @Test void validatedPagedSnapshotPreservesVariableCallEvidenceWithoutPublicationMaterialization() {
        var publication=directCall();var ledger=resources();
        try(var pages=new MemoryPageStore(128,ledger);var source=AirSnapshot.fromPublication(publication);
            var builder=new AirSnapshotBuilder(new PagedAirStorage(pages,ledger,AnalysisResources.Phase.DECODE))) {
            long root=PagedAirStorageTest.copy(source,source.root(),null,builder);
            try(var checked=SnapshotValidator.check(builder.finish(root),ValidationOptions.defaults(),new PagedSnapshotValidationStorage(pages,ledger))) {
                assertEquals(ValidationResult.Status.STRUCTURALLY_VALID,checked.result().status(),checked.result().toString());
                var result=new SnapshotDependencyAnalysis().analyze(checked,new PagedSnapshotIdentityStorage(pages,ledger),new PagedSnapshotDependencyStorage(pages,ledger));
                assertEquals(publication.id(),result.publication());assertEquals(Evidence.InventoryStatus.COMPLETE,result.coverage());
                assertEquals(publication.origins(),result.origins());assertEquals(publication.artifacts(),result.artifacts());
                var site=result.sites().getFirst();assertEquals("caller",site.caller().localId());assertEquals("entry",site.entry().localId());assertEquals("start",site.sequence().localId());assertEquals("invoke",site.operation().localId());
                assertEquals(ResultFixtures.origin(publication.id()),site.siteOrigin());assertEquals(ResultFixtures.origin(publication.id()),site.targetOrigin());assertEquals(Evidence.CoverageStatus.MODELED,site.coverage());assertEquals("cobol.program",site.namespace());assertEquals("program-name",site.subject().localId());assertFalse(site.unknownRemainder());
                var candidate=site.candidates().getFirst();assertEquals("PROGA",candidate.referenceName());assertEquals("PROGA   ",candidate.rawValue());
                var support=candidate.supports().getFirst();assertEquals("seed",support.producer().localId());assertEquals(ResultFixtures.origin(publication.id()),support.origin());assertEquals(List.of(),support.premises());
            }
        }
        assertEquals(0,ledger.heapUsed());
    }

    @Test void incompleteAdmissionCannotProduceDependencies() {
        var ledger=resources();
        try(var pages=new MemoryPageStore(128,ledger);var snapshot=AirSnapshot.fromPublication(PagedAirStorageTest.publication("no direct call"));
            var checked=SnapshotValidator.check(snapshot,ValidationOptions.defaults(),new PagedSnapshotValidationStorage(pages,ledger))) {
            assertEquals(ValidationResult.Status.INCOMPLETE_VALIDATION,checked.result().status());
            assertThrows(IllegalArgumentException.class,()->new SnapshotDependencyAnalysis().analyze(checked,new PagedSnapshotIdentityStorage(pages,ledger),new PagedSnapshotDependencyStorage(pages,ledger)));
        }
        assertEquals(0,ledger.heapUsed());
    }

    private static Publication directCall() {
        var publication=new PublicationId("snapshot-direct");var unit=new UnitId(publication,"caller");var cell=new StorageId(publication,"program-name-cell");var object=new ObjectId(unit,"program-name");var origin=ResultFixtures.origin(publication);
        var declaration=new Memory.ObjectDeclaration(object,Optional.of("PROGRAM-NAME"),new Types.Known(Types.Builtin.TEXT),new Memory.CellBinding(cell),Memory.Visibility.PRIVATE,origin,Evidence.CoverageStatus.MODELED,ResultFixtures.header(unit,"metadata").precision());
        var storage=new Memory.Cell(new Memory.StorageHeader(cell,Optional.of(unit),Memory.Lifetime.ACTIVATION,Memory.Visibility.PRIVATE,origin),new Types.Known(Types.Builtin.TEXT));
        var start=new Sequence(new LabelId(unit,"start"),List.of(ResultFixtures.assign(unit,"seed",object,"PROGA   ")),W1dModelTest.call(unit,"invoke","end",object,false),origin);
        var end=new Sequence(new LabelId(unit,"end"),List.of(),new Operations.Halt(ResultFixtures.header(unit,"halt"),Operations.HaltKind.NORMAL),origin);
        return ResultFixtures.publication(publication,List.of(ResultFixtures.unit(unit,List.of(ResultFixtures.entry(unit,"entry","start")),List.of(start,end),List.of(declaration))),List.of(storage));
    }
}
