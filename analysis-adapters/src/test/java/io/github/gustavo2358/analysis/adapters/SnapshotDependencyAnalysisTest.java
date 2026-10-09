package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.air.validation.*;
import io.github.gustavo2358.air.json.AirJson;
import io.github.gustavo2358.analysis.dependencies.SnapshotDependencyAnalysis;
import io.github.gustavo2358.analysis.dependencies.SnapshotProgram;
import io.github.gustavo2358.analysis.dependencies.DirectDependencyResult;
import io.github.gustavo2358.analysis.cfg.domain.CoreCfgProjection;
import io.github.gustavo2358.analysis.cfg.application.*;
import io.github.gustavo2358.analysis.cfg.extension.SemanticInterpreterRegistry;
import io.github.gustavo2358.analysis.solver.*;
import io.github.gustavo2358.analysis.structure.ProgramStore;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.math.BigInteger;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Executable proof of the first validated snapshot-to-dependency production slice. */
final class SnapshotDependencyAnalysisTest {
    private static AnalysisResources resources(){return new AnalysisResources(new AnalysisResources.Limits(64_000_000,64_000_000,0,256_000_000,4,1_000_000_000,1_000_000));}

    @Test void dependencyProgramConstructionDoesNotPrepareInputSizedCfgMetadata() {
        long fixed=-1;
        for(int units:new int[]{1,16,64}) {
            var ledger=resources();
            try(var pages=new MemoryPageStore(128,ledger);var snapshot=AirSnapshot.fromPublication(directCalls(units));
                var checked=SnapshotValidator.check(snapshot,ValidationOptions.defaults(),new PagedSnapshotValidationStorage(pages,ledger))) {
                assertEquals(ValidationResult.Status.STRUCTURALLY_VALID,checked.result().status(),checked.result().toString());
                long before=ledger.workUsed();
                try(var program=new SnapshotProgram(checked,new PagedSnapshotIdentityStorage(pages,ledger))) {
                    assertEquals(units,program.inventory()==Evidence.InventoryStatus.COMPLETE?units:-1);
                    long construction=ledger.workUsed()-before;
                    if(fixed<0)fixed=construction;else assertEquals(fixed,construction,"dependency construction must not scan CFG metadata for every Unit");
                }
            }
            assertEquals(0,ledger.heapUsed());
        }
    }

    @Test void validatedPagedSnapshotPreservesVariableCallEvidenceWithoutPublicationMaterialization() {
        var publication=directCall();var ledger=resources();
        try(var pages=new MemoryPageStore(128,ledger);var source=AirSnapshot.fromPublication(publication);
            var builder=new AirSnapshotBuilder(new PagedAirStorage(pages,ledger,AnalysisResources.Phase.DECODE))) {
            long root=PagedAirStorageTest.copy(source,source.root(),null,builder);
            try(var checked=SnapshotValidator.check(builder.finish(root),ValidationOptions.defaults(),new PagedSnapshotValidationStorage(pages,ledger))) {
                assertEquals(ValidationResult.Status.STRUCTURALLY_VALID,checked.result().status(),checked.result().toString());
                try(var snapshotProgram=new SnapshotProgram(checked,new PagedSnapshotIdentityStorage(pages,ledger))) {
                    ProgramStore store=snapshotProgram;
                    assertEquals(publication.id(),store.publicationId());
                    assertEquals(Evidence.InventoryStatus.COMPLETE,store.inventory());
                    assertEquals(publication.artifacts(),store.artifacts());
                    assertEquals(publication.origins(),store.origins());
                    assertFalse(store instanceof ProgramStore.Structural,
                            "paged dependency store must not claim resident structural payload");
                    assertEquals(CoreCfgProjection.project(publication),CoreCfgProjection.project(snapshotProgram));
                    var built=new CfgBuildCoordinator(SemanticInterpreterRegistry.empty()).buildChecked(
                            snapshotProgram,checked,BuildOptions.defaults());
                    assertEquals(CfgBuildResult.Status.CFG_BUILT,built.status());
                    assertEquals(CoreCfgProjection.project(publication),built.graph().orElseThrow());
                }
                var result=new SnapshotDependencyAnalysis().analyze(
                        new SnapshotProgram(checked,new PagedSnapshotIdentityStorage(pages,ledger)),
                        new PagedSnapshotDependencyStorage(pages,ledger));
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

    @Test void admittedDiamondPreservesWholeConcatAlternativesAndTheirSupports() {
        var publication=correlatedCall();var ledger=resources();
        try(var pages=new MemoryPageStore(128,ledger);var source=AirSnapshot.fromPublication(publication);
            var builder=new AirSnapshotBuilder(new PagedAirStorage(pages,ledger,AnalysisResources.Phase.DECODE))) {
            long root=PagedAirStorageTest.copy(source,source.root(),null,builder);
            try(var checked=SnapshotValidator.check(builder.finish(root),ValidationOptions.defaults(),new PagedSnapshotValidationStorage(pages,ledger))) {
                assertEquals(ValidationResult.Status.STRUCTURALLY_VALID,checked.result().status(),checked.result().toString());
                try(var snapshotProgram=new SnapshotProgram(checked,new PagedSnapshotIdentityStorage(pages,ledger))) {
                    assertEquals(CoreCfgProjection.project(publication),CoreCfgProjection.project(snapshotProgram));
                }
                var result=new SnapshotDependencyAnalysis().analyze(checked,new PagedSnapshotIdentityStorage(pages,ledger),new PagedSnapshotDependencyStorage(pages,ledger));
                var site=result.sites().getFirst();assertFalse(site.unknownRemainder());
                assertEquals(List.of("AX","BY"),site.candidates().stream().map(DirectDependencyResult.Candidate::referenceName).toList());
                assertEquals(List.of(List.of("seed-A","seed-X","fit-concat"),List.of("seed-B","seed-Y","fit-concat")),site.candidates().stream()
                    .map(candidate->candidate.supports().stream().map(support->support.producer().localId()).toList()).toList());
                assertTrue(site.candidates().stream().noneMatch(candidate->candidate.referenceName().equals("AY")||candidate.referenceName().equals("BX")));
            }
        }
        assertEquals(0,ledger.heapUsed());
    }

    @Test void managedJsonBindingFeedsTheSameCorrelatedProduct() throws Exception {
        var input=Files.createTempFile("snapshot-correlated-",".air.json");var ledger=resources();
        try {Files.write(input,new AirJson().encode(correlatedCall()));
            try(var read=new DataflowAirReader().readSnapshot(input,ledger)) {
                assertEquals(ValidationResult.Status.STRUCTURALLY_VALID,read.checked().result().status());
                var result=new SnapshotDependencyAnalysis().analyze(read.checked(),read.newIdentityStorage(),read.newDependencyStorage());
                assertEquals(List.of("AX","BY"),result.sites().getFirst().candidates().stream().map(DirectDependencyResult.Candidate::referenceName).toList());
                var wire=new ByteArrayOutputStream();new SnapshotDependencyJson().write(result,wire);var json=wire.toString(StandardCharsets.UTF_8);
                assertTrue(json.contains("\"modelScope\":\"VALIDATED_SNAPSHOT_DEPENDENCY\""));
                assertTrue(json.contains("\"valuesProfile\":\"snapshot-text-relations@2\""));
                assertTrue(json.contains("\"referenceName\":\"AX\""));assertTrue(json.contains("\"referenceName\":\"BY\""));
                assertFalse(json.contains("\"referenceName\":\"AY\""));assertFalse(json.contains("\"referenceName\":\"BX\""));
            }
        } finally {Files.deleteIfExists(input);}
        assertEquals(0,ledger.heapUsed());
    }

    @Test void correlatedProductIsIdenticalWhenCandidatePayloadSpills() throws Exception {
        var publication=correlatedCall();var ledger=resources();var directory=Files.createTempDirectory("snapshot-candidate-spill-");
        try(var sourcePages=new MemoryPageStore(128,ledger);var source=AirSnapshot.fromPublication(publication);
            var builder=new AirSnapshotBuilder(new PagedAirStorage(sourcePages,ledger,AnalysisResources.Phase.DECODE));
            var spillPages=new FilePageStore(directory,128,1,ledger)) {
            long root=PagedAirStorageTest.copy(source,source.root(),null,builder);
            try(var checked=SnapshotValidator.check(builder.finish(root),ValidationOptions.defaults(),new PagedSnapshotValidationStorage(sourcePages,ledger))) {
                var result=new SnapshotDependencyAnalysis().analyze(checked,new PagedSnapshotIdentityStorage(sourcePages,ledger),new PagedSnapshotDependencyStorage(spillPages,ledger));
                assertEquals(List.of("AX","BY"),result.sites().getFirst().candidates().stream().map(DirectDependencyResult.Candidate::referenceName).toList());
                assertTrue(spillPages.statistics().evictions()>0,spillPages.statistics().toString());
            }
        } finally {Files.deleteIfExists(directory);}
        assertEquals(0,ledger.heapUsed());
    }

    @Test void leasedCursorWireMatchesExplicitMaterialization() throws Exception {
        var publication=correlatedCall();var ledger=resources();
        try(var pages=new MemoryPageStore(128,ledger);var source=AirSnapshot.fromPublication(publication);
            var builder=new AirSnapshotBuilder(new PagedAirStorage(pages,ledger,AnalysisResources.Phase.DECODE))) {
            long root=PagedAirStorageTest.copy(source,source.root(),null,builder);
            try(var checked=SnapshotValidator.check(builder.finish(root),ValidationOptions.defaults(),new PagedSnapshotValidationStorage(pages,ledger));
                var cursor=new SnapshotDependencyAnalysis().open(checked,new PagedSnapshotIdentityStorage(pages,ledger),new PagedSnapshotDependencyStorage(pages,ledger))) {
                assertEquals(new io.github.gustavo2358.analysis.dependencies.SnapshotDependencyCursorResult.Metrics(1,2),cursor.metrics());
                var expected=new ByteArrayOutputStream();new SnapshotDependencyJson().write(cursor.materialize(),expected);
                var actual=new ByteArrayOutputStream();new SnapshotDependencyJson().write(cursor,actual);
                assertArrayEquals(expected.toByteArray(),actual.toByteArray());
            }
        }
        assertEquals(0,ledger.heapUsed());
    }

    private static Publication directCall() {
        return directCalls(1);
    }

    private static Publication directCalls(int count) {
        var publication=new PublicationId("snapshot-direct");var origin=ResultFixtures.origin(publication);
        var units=new java.util.ArrayList<Unit>();var storage=new java.util.ArrayList<Memory.Storage>();
        for(int i=0;i<count;i++) {
            String suffix=String.format("%04d",i);var unit=new UnitId(publication,count==1?"caller":"caller-"+suffix);var cell=new StorageId(publication,count==1?"program-name-cell":"program-name-cell-"+suffix);var object=new ObjectId(unit,"program-name");
            var declaration=new Memory.ObjectDeclaration(object,Optional.of("PROGRAM-NAME"),new Types.Known(Types.Builtin.TEXT),new Memory.CellBinding(cell),Memory.Visibility.PRIVATE,origin,Evidence.CoverageStatus.MODELED,ResultFixtures.header(unit,"metadata").precision());
            storage.add(new Memory.Cell(new Memory.StorageHeader(cell,Optional.of(unit),Memory.Lifetime.ACTIVATION,Memory.Visibility.PRIVATE,origin),new Types.Known(Types.Builtin.TEXT)));
            var start=new Sequence(new LabelId(unit,"start"),List.of(ResultFixtures.assign(unit,"seed",object,"PROGA   ")),W1dModelTest.call(unit,"invoke","end",object,false),origin);
            var end=ResultFixtures.returning(unit,"end",List.of());
            units.add(ResultFixtures.unit(unit,List.of(ResultFixtures.entry(unit,"entry","start")),List.of(start,end),List.of(declaration)));
        }
        return ResultFixtures.publication(publication,units,storage);
    }

    private static Publication correlatedCall() {
        var publication=new PublicationId("snapshot-correlated");var unit=new UnitId(publication,"caller");var origin=ResultFixtures.origin(publication);
        var objects=new java.util.ArrayList<Memory.ObjectDeclaration>();var storage=new java.util.ArrayList<Memory.Storage>();
        for(String name:List.of("x","y","z","condition")) {var cell=new StorageId(publication,name+"-cell");var object=new ObjectId(unit,name);var type=new Types.Known(name.equals("condition")?Types.Builtin.BOOL:Types.Builtin.TEXT);
            objects.add(new Memory.ObjectDeclaration(object,Optional.of(name),type,new Memory.CellBinding(cell),Memory.Visibility.PRIVATE,origin,Evidence.CoverageStatus.MODELED,ResultFixtures.header(unit,"metadata").precision()));
            storage.add(new Memory.Cell(new Memory.StorageHeader(cell,Optional.of(unit),Memory.Lifetime.ACTIVATION,Memory.Visibility.PRIVATE,origin),type));}
        var x=new ObjectId(unit,"x");var y=new ObjectId(unit,"y");var z=new ObjectId(unit,"z");var conditionObject=new ObjectId(unit,"condition");var reason=new UncertaintyId(publication,"branch-input");
        var choose=ResultFixtures.header(unit,"choose");var condition=new Expressions.Read(ResultFixtures.operand(choose.id(),"condition",Operand.Role.PREDICATE),new Places.ObjectPlace(ResultFixtures.operand(choose.id(),"condition-place",Operand.Role.VALUE_READ),conditionObject));
        var branch=new Operations.Branch(choose,condition,new LabelId(unit,"left"),new LabelId(unit,"right"));
        var start=new Sequence(new LabelId(unit,"start"),List.of(),branch,origin);
        var left=new Sequence(new LabelId(unit,"left"),List.of(ResultFixtures.assign(unit,"seed-A",x,"A"),ResultFixtures.assign(unit,"seed-X",y,"X")),new Operations.Jump(ResultFixtures.header(unit,"left-jump"),new LabelId(unit,"join")),origin);
        var right=new Sequence(new LabelId(unit,"right"),List.of(ResultFixtures.assign(unit,"seed-B",x,"B"),ResultFixtures.assign(unit,"seed-Y",y,"Y")),new Operations.Jump(ResultFixtures.header(unit,"right-jump"),new LabelId(unit,"join")),origin);
        var fit=ResultFixtures.header(unit,"fit-concat");var readX=new Expressions.Read(ResultFixtures.operand(fit.id(),"read-x",Operand.Role.VALUE_READ),new Places.ObjectPlace(ResultFixtures.operand(fit.id(),"place-x",Operand.Role.VALUE_READ),x));
        var readY=new Expressions.Read(ResultFixtures.operand(fit.id(),"read-y",Operand.Role.VALUE_READ),new Places.ObjectPlace(ResultFixtures.operand(fit.id(),"place-y",Operand.Role.VALUE_READ),y));
        var concat=new Expressions.Binary(ResultFixtures.operand(fit.id(),"concat",Operand.Role.VALUE_READ),Expressions.BinaryOperator.CONCAT,readX,readY);
        var fitted=new Expressions.FitText(ResultFixtures.operand(fit.id(),"fit",Operand.Role.VALUE_READ),concat,BigInteger.valueOf(2)," ");
        var assignment=new Operations.Assign(fit,new Places.ObjectPlace(ResultFixtures.operand(fit.id(),"destination",Operand.Role.VALUE_WRITE),z),fitted);
        var join=new Sequence(new LabelId(unit,"join"),List.of(assignment),W1dModelTest.call(unit,"invoke","end",z,false),origin);
        var end=ResultFixtures.returning(unit,"end",List.of());
        var entry=ResultFixtures.entry(unit,"entry","start");var initialPlace=new Places.ObjectPlace(new Operand.Header(new OperandId(new EntryOwner(entry.id()),"condition"),Operand.Role.VALUE_WRITE,origin),conditionObject);
        entry=new Entries.Entry(entry.id(),entry.initialLabel(),entry.signature(),new Entries.EntryState(List.of(new Entries.InitialCondition(initialPlace,new Entries.ExternalUnknown(reason),origin,List.of())),List.of(reason)),entry.origin());
        var base=ResultFixtures.publication(publication,List.of(ResultFixtures.unit(unit,List.of(entry),List.of(start,left,right,join,end),objects)),storage);
        var artifact=new ArtifactId(publication,"source");var included=new ArtifactId(publication,"included");var derived=new OriginId(publication,"derived");
        return new Publication(base.id(),base.airVersion(),base.capabilities(),List.of(new Origins.Artifact(artifact,"SnapshotDependencyAnalysisTest.java",Optional.empty()),new Origins.Artifact(included,"included.copy",Optional.empty())),base.units(),base.storage(),base.resources(),base.artifactRelations(),
            List.of(new Origins.Written(origin,artifact,Optional.empty(),List.of(new Origins.IncludeFrame(artifact,included,"included.copy",Optional.empty())),true),new Origins.Derived(derived,List.of(origin),"test-derivation")),base.coverage(),
            List.of(new Evidence.Uncertainty(reason,"VALUE_UNKNOWN",List.of(Evidence.Dimension.VALUES),new Scopes.UnitScope(unit),"branch input",origin)),base.premises());
    }
}
