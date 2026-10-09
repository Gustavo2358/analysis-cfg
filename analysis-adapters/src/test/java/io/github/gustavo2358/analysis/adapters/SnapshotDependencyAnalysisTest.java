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
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Executable proof of the first validated snapshot-to-dependency production slice. */
final class SnapshotDependencyAnalysisTest {
    @TempDir java.nio.file.Path directory;
    private static AnalysisResources resources(){return new AnalysisResources(new AnalysisResources.Limits(64_000_000,64_000_000,0,256_000_000,4,1_000_000_000,1_000_000));}

    @Test void sourceUnitInventoryBorrowsCanonicalPagesAndExpiresWithProgram() {
        for(int count:new int[]{1,16,64}) {
            var reference=directCalls(count);var ledger=resources();
            var publication=new Publication(reference.id(),reference.airVersion(),reference.capabilities(),reference.artifacts(),
                    reference.units().reversed(),reference.storage(),reference.resources(),reference.artifactRelations(),reference.origins(),reference.coverage(),reference.uncertainties(),reference.premises());
            try(var pages=new FilePageStore(directory,512,16,ledger);var snapshot=AirSnapshot.fromPublication(publication);
                var checked=SnapshotValidator.check(snapshot,ValidationOptions.defaults(),new PagedSnapshotValidationStorage(pages,ledger))) {
                assertEquals(ValidationResult.Status.STRUCTURALLY_VALID,checked.result().status(),checked.result().toString());
                List<io.github.gustavo2358.analysis.cfg.domain.CfgSource.UnitInventory> borrowed;
                var ordered=new PagedSnapshotOrderStorage(pages,ledger);int[] sorts={0};
                var tracked=new io.github.gustavo2358.analysis.dependencies.SnapshotOrderStorage() {
                    @Override public Index open(Order order){sorts[0]++;return ordered.open(order);}
                    @Override public Tape tape(){return ordered.tape();}
                    @Override public void close(){ordered.close();}
                };
                try(var program=new SnapshotProgram(checked,new PagedSnapshotIdentityStorage(pages,ledger),tracked)) {
                    var source=program.source();borrowed=source.units();
                    assertInstanceOf(io.github.gustavo2358.analysis.cfg.domain.CfgSource.UnitInventories.class,borrowed);
                    assertEquals(count,borrowed.size());
                    assertEquals(io.github.gustavo2358.analysis.cfg.domain.CfgSource.from(reference),source);
                    assertEquals(1,sorts[0]);
                    long fixed=ledger.heapUsed();
                    for(int scan=0;scan<3;scan++) {
                        int[] ordinal={0};
                        program.units(unit->assertEquals(borrowed.get(ordinal[0]++).id(),unit.id()));
                        assertEquals(count,ordinal[0]);
                    }
                    assertEquals(fixed,ledger.heapUsed(),"repeated canonical scans must retain no additional metadata");
                    assertEquals(1,sorts[0],"canonical Unit ordering must be reused, not rebuilt for every consumer");
                    assertThrows(UnsupportedOperationException.class,borrowed::clear);
                }
                assertThrows(IllegalStateException.class,borrowed::size);
                assertThrows(IllegalStateException.class,borrowed::getFirst);
                assertEquals(0,pages.statistics().livePages());
            }
            assertEquals(0,ledger.heapUsed());
        }
    }

    @Test void borrowedSourceInventoryKeepsCanonicalAndForeignIdentityChecks() {
        var publication=new PublicationId("canonical-source");
        var a=new io.github.gustavo2358.analysis.cfg.domain.CfgSource.UnitInventory(new UnitId(publication,"a"),Evidence.InventoryStatus.COMPLETE);
        var b=new io.github.gustavo2358.analysis.cfg.domain.CfgSource.UnitInventory(new UnitId(publication,"b"),Evidence.InventoryStatus.UNAVAILABLE);
        var foreign=new io.github.gustavo2358.analysis.cfg.domain.CfgSource.UnitInventory(new UnitId(new PublicationId("foreign"),"c"),Evidence.InventoryStatus.COMPLETE);
        for(var invalid:List.of(List.of(a,a),List.of(b,a),List.of(a,foreign))) {
            var units=new io.github.gustavo2358.analysis.cfg.domain.CfgSource.UnitInventories(invalid.size(),invalid::get,()->{});
            assertThrows(IllegalArgumentException.class,()->new io.github.gustavo2358.analysis.cfg.domain.CfgSource(
                    publication,new SemanticVersion(BigInteger.ONE,BigInteger.ZERO,BigInteger.ZERO),Evidence.InventoryStatus.COMPLETE,units,List.of()));
        }
        var ordered=List.of(a,b);boolean[] alive={true};
        var units=new io.github.gustavo2358.analysis.cfg.domain.CfgSource.UnitInventories(2,ordered::get,
                ()->{if(!alive[0])throw new IllegalStateException("expired source owner");});
        var source=new io.github.gustavo2358.analysis.cfg.domain.CfgSource(publication,new SemanticVersion(BigInteger.ONE,BigInteger.ZERO,BigInteger.ZERO),Evidence.InventoryStatus.COMPLETE,units,List.of());
        assertSame(units,source.units());assertEquals(ordered,source.units());
        assertThrows(UnsupportedOperationException.class,()->units.set(0,b));
        alive[0]=false;assertThrows(IllegalStateException.class,source.units()::size);
    }

    @Test void cfgProjectionDoesNotReadEveryInstructionIdentityBeforeAQuery() {
        long baseline=-1;
        for(int count:new int[]{16,64,256,1024,4096}) {
            var publication=directCallWithNops(count);var ledger=resources();
            try(var pages=new FilePageStore(directory,512,16,ledger);var original=AirSnapshot.fromPublication(publication);
                var builder=new AirSnapshotBuilder(new PagedAirStorage(pages,ledger,AnalysisResources.Phase.DECODE))) {
                long root=PagedAirStorageTest.copy(original,original.root(),null,builder);
                try(var checked=SnapshotValidator.check(builder.finish(root),ValidationOptions.defaults(),new PagedSnapshotValidationStorage(pages,ledger))) {
                    assertEquals(ValidationResult.Status.STRUCTURALLY_VALID,checked.result().status(),checked.result().toString());
                    List<OperationId> borrowed;
                    try(var program=new SnapshotProgram(checked,new PagedSnapshotIdentityStorage(pages,ledger),new PagedSnapshotOrderStorage(pages,ledger))) {
                        long before=ledger.workUsed();
                        var graph=CoreCfgProjection.project(program);
                        long work=ledger.workUsed()-before;
                        if(baseline<0)baseline=work;
                        assertTrue(work<=2*baseline+1024,"CFG eagerly read instruction inventory: count="+count+" work="+work+" baseline="+baseline);
                        var sequence=graph.nodes().stream().filter(node->node instanceof io.github.gustavo2358.analysis.cfg.domain.CfgNode.SequenceNode s&&s.label().localId().equals("start"))
                                .map(io.github.gustavo2358.analysis.cfg.domain.CfgNode.SequenceNode.class::cast).findFirst().orElseThrow();
                        borrowed=sequence.operations();
                        assertEquals(count+1,sequence.operations().size());
                        assertEquals("seed",sequence.operations().getFirst().localId());
                        assertEquals("nop-"+(count-1),sequence.operations().getLast().localId());
                        for(int scan=0;scan<2;scan++) {
                            int ordinal=0;
                            for(var operation:sequence.operations()) {
                                assertEquals(ordinal==0?"seed":"nop-"+(ordinal-1),operation.localId());ordinal++;
                            }
                            assertEquals(count+1,ordinal);
                        }
                        assertThrows(UnsupportedOperationException.class,()->sequence.operations().clear());
                        System.out.println("SNAPSHOT_CFG_OPERATION_VIEW_METRICS instructions="+(count+1)+" preparationWork="+work);
                    }
                    assertThrows(IllegalStateException.class,borrowed::size);
                    assertThrows(IllegalStateException.class,borrowed::getFirst);
                }
            }
            assertEquals(0,ledger.heapUsed());
        }
    }

    @Test void instructionQueriesReadLocalIdentityWithoutRepeatingValidatedNamespaces() {
        var publication=directCallWithNops(16);var ledger=resources();long[] characterReads={0};
        try(var pages=new MemoryPageStore(128,ledger);var original=AirSnapshot.fromPublication(publication)) {
            var storage=new PagedAirStorage(pages,ledger,AnalysisResources.Phase.DECODE);
            var tracked=new AirSnapshotBuilder.Storage() {
                @Override public long get(AirSnapshotBuilder.Column column,long index) {
                    if(column==AirSnapshotBuilder.Column.CHARACTERS)characterReads[0]++;
                    return storage.get(column,index);
                }
                @Override public void set(AirSnapshotBuilder.Column column,long index,long value){storage.set(column,index,value);}
                @Override public AirSnapshotBuilder.Lease claim(long bytes){return storage.claim(bytes);}
                @Override public AirSnapshotBuilder.Lease readLease(long bytes){return storage.readLease(bytes);}
                @Override public void freeze(){storage.freeze();}
                @Override public void close(){storage.close();}
            };
            try(var builder=new AirSnapshotBuilder(tracked)) {
                long root=PagedAirStorageTest.copy(original,original.root(),null,builder);
                try(var checked=SnapshotValidator.check(builder.finish(root),ValidationOptions.defaults(),new PagedSnapshotValidationStorage(pages,ledger))) {
                    assertEquals(ValidationResult.Status.STRUCTURALLY_VALID,checked.result().status(),checked.result().toString());
                    var snapshot=checked.snapshot();
                    try(var program=new SnapshotProgram(checked,new PagedSnapshotIdentityStorage(pages,ledger),new PagedSnapshotOrderStorage(pages,ledger))) {
                        var graph=CoreCfgProjection.project(program);
                        var sequence=graph.nodes().stream().filter(node->node instanceof io.github.gustavo2358.analysis.cfg.domain.CfgNode.SequenceNode s&&s.label().localId().equals("start"))
                                .map(io.github.gustavo2358.analysis.cfg.domain.CfgNode.SequenceNode.class::cast).findFirst().orElseThrow();
                        long unit=snapshot.element(snapshot.field(root,AirShape.PUBLICATION,4),AirShape.UNIT,0);
                        long start=snapshot.element(snapshot.field(unit,AirShape.UNIT,5),AirShape.SEQUENCE,0);
                        long instructions=snapshot.field(start,AirShape.SEQUENCE,1);
                        for(int scan=0;scan<2;scan++)for(int ordinal=0;ordinal<sequence.operations().size();ordinal++) {
                            long instruction=snapshot.element(instructions,AirShape.INSTRUCTION,ordinal);
                            long header=snapshot.field(instruction,snapshot.shape(instruction),0);
                            long id=snapshot.field(header,AirShape.OPERATIONS_HEADER,0);
                            characterReads[0]=0;
                            String expected=program.textValue(snapshot.field(id,AirShape.IDS_OPERATION_ID,1));
                            long localReads=characterReads[0];characterReads[0]=0;
                            var actual=sequence.operations().get(ordinal);
                            assertEquals(new OperationId(publication.units().getFirst().id(),expected),actual);
                            assertEquals(localReads,characterReads[0],"validated Unit/Publication namespaces must not be reread for each instruction");
                        }
                    }
                }
            }
        }
        assertEquals(0,ledger.heapUsed());
    }

    @Test void instructionOwnerMismatchCannotEnterTheBorrowedCfgView() {
        var base=directCall();var unit=base.units().getFirst();var start=unit.sequences().getFirst();
        var instructions=new java.util.ArrayList<Instruction>(start.instructions());
        instructions.add(new Operations.Nop(ResultFixtures.header(new UnitId(base.id(),"foreign-unit"),"foreign-nop")));
        var replacement=new Sequence(start.label(),instructions,start.terminator(),start.origin());
        var changed=ResultFixtures.unit(unit.id(),unit.entries(),List.of(replacement,unit.sequences().getLast()),unit.objects());
        var invalid=new Publication(base.id(),base.airVersion(),base.capabilities(),base.artifacts(),List.of(changed),base.storage(),base.resources(),base.artifactRelations(),base.origins(),base.coverage(),base.uncertainties(),base.premises());
        var ledger=resources();
        try(var pages=new MemoryPageStore(128,ledger);var snapshot=AirSnapshot.fromPublication(invalid);
            var checked=SnapshotValidator.check(snapshot,ValidationOptions.defaults(),new PagedSnapshotValidationStorage(pages,ledger))) {
            assertEquals(ValidationResult.Status.INVALID_IR,checked.result().status());
            assertTrue(checked.result().issues().stream().anyMatch(issue->issue.rule().equals("I-03")),checked.result().toString());
            assertThrows(IllegalArgumentException.class,()->new SnapshotProgram(checked,new PagedSnapshotIdentityStorage(pages,ledger),new PagedSnapshotOrderStorage(pages,ledger)));
        }
        assertEquals(0,ledger.heapUsed());
    }

    @Test void operationViewConstructorsDoNotCopyAndExpiredOwnersRejectQueries() {
        var publication=new PublicationId("operation-view");var unit=new UnitId(publication,"unit");
        var control=new io.github.gustavo2358.analysis.cfg.domain.CfgControl.Return(new OperationId(unit,"return"));
        int[] reads={0};boolean[] alive={true};
        var operations=new io.github.gustavo2358.analysis.cfg.domain.CfgProgram.OperationIds(Integer.MAX_VALUE,
                ordinal->{reads[0]++;return new OperationId(unit,"operation-"+ordinal);},
                ()->{if(!alive[0])throw new IllegalStateException("expired synthetic owner");});
        var view=new io.github.gustavo2358.analysis.cfg.domain.CfgProgram.SequenceView(new LabelId(unit,"sequence"),operations,control);
        var node=new io.github.gustavo2358.analysis.cfg.domain.CfgNode.SequenceNode(
                new io.github.gustavo2358.analysis.cfg.domain.CfgNodeId(publication,0),view.label(),view.operations(),view.control());
        assertSame(operations,view.operations());assertSame(operations,node.operations());assertEquals(0,reads[0]);
        assertEquals("operation-2147483646",node.operations().getLast().localId());assertEquals(1,reads[0]);
        assertThrows(IndexOutOfBoundsException.class,()->operations.get(-1));
        assertThrows(IndexOutOfBoundsException.class,()->operations.get(Integer.MAX_VALUE));
        assertThrows(UnsupportedOperationException.class,()->operations.set(0,new OperationId(unit,"replacement")));
        assertThrows(UnsupportedOperationException.class,()->operations.add(new OperationId(unit,"extra")));
        alive[0]=false;
        assertThrows(IllegalStateException.class,operations::size);
        assertThrows(IllegalStateException.class,operations::getFirst);
        assertThrows(IllegalStateException.class,()->operations.iterator().hasNext());
        assertEquals(1,reads[0]);
    }

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
                try(var snapshotProgram=new SnapshotProgram(checked,new PagedSnapshotIdentityStorage(pages,ledger),new PagedSnapshotOrderStorage(pages,ledger))) {
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
                        new SnapshotProgram(checked,new PagedSnapshotIdentityStorage(pages,ledger),new PagedSnapshotOrderStorage(pages,ledger)),
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

    @Test void cfgBuildCannotReuseAdmissionForAnotherProgramWithTheSamePublicationId() {
        var one=directCalls(1);var two=directCalls(2);var ledger=resources();
        assertEquals(one.id(),two.id());assertNotEquals(one.units(),two.units());
        var coordinator=new CfgBuildCoordinator(SemanticInterpreterRegistry.empty());
        try(var pages=new MemoryPageStore(128,ledger);
            var first=SnapshotValidator.check(AirSnapshot.fromPublication(one),ValidationOptions.defaults(),new PagedSnapshotValidationStorage(pages,ledger));
            var second=SnapshotValidator.check(AirSnapshot.fromPublication(two),ValidationOptions.defaults(),new PagedSnapshotValidationStorage(pages,ledger));
            var firstProgram=new SnapshotProgram(first,new PagedSnapshotIdentityStorage(pages,ledger));
            var secondProgram=new SnapshotProgram(second,new PagedSnapshotIdentityStorage(pages,ledger))) {
            assertEquals(ValidationResult.Status.STRUCTURALLY_VALID,first.result().status());
            assertEquals(ValidationResult.Status.STRUCTURALLY_VALID,second.result().status());
            assertThrows(IllegalArgumentException.class,()->coordinator.buildChecked(secondProgram,first,BuildOptions.defaults()));
            assertThrows(IllegalArgumentException.class,()->coordinator.buildChecked(firstProgram,second,BuildOptions.defaults()));
            assertThrows(IllegalArgumentException.class,()->coordinator.buildChecked(
                    io.github.gustavo2358.analysis.cfg.domain.CfgProgram.resident(one),first,BuildOptions.defaults()));
            var original=first.options();
            var changed=new BuildOptions(new ValidationOptions(original.maximumNesting(),original.maximumEntities(),original.maximumIssues()+1));
            assertThrows(IllegalArgumentException.class,()->coordinator.buildChecked(firstProgram,first,changed));
            assertEquals(CoreCfgProjection.project(one),coordinator.buildChecked(firstProgram,first,BuildOptions.defaults()).graph().orElseThrow());
            assertEquals(CoreCfgProjection.project(two),coordinator.buildChecked(secondProgram,second,BuildOptions.defaults()).graph().orElseThrow());
        }
        assertEquals(0,ledger.heapUsed());
    }

    @SuppressWarnings("try") // Explicit closure is the lifetime counterexample under test.
    @Test void cfgBuildCannotReuseClosedSnapshotAdmissionOrProgram() {
        var ledger=resources();var coordinator=new CfgBuildCoordinator(SemanticInterpreterRegistry.empty());
        try(var pages=new MemoryPageStore(128,ledger);
            var checked=SnapshotValidator.check(AirSnapshot.fromPublication(directCall()),ValidationOptions.defaults(),new PagedSnapshotValidationStorage(pages,ledger));
            var program=new SnapshotProgram(checked,new PagedSnapshotIdentityStorage(pages,ledger))) {
            assertEquals(CfgBuildResult.Status.CFG_BUILT,coordinator.buildChecked(program,checked,BuildOptions.defaults()).status());
            checked.close();
            assertThrows(IllegalStateException.class,()->coordinator.buildChecked(program,checked,BuildOptions.defaults()));
            program.close();
            assertThrows(IllegalStateException.class,()->coordinator.buildChecked(program,checked,BuildOptions.defaults()));
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
                try(var snapshotProgram=new SnapshotProgram(checked,new PagedSnapshotIdentityStorage(pages,ledger),new PagedSnapshotOrderStorage(pages,ledger))) {
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

    private static Publication directCallWithNops(int count) {
        var base=directCall();var unit=base.units().getFirst();var start=unit.sequences().getFirst();
        var instructions=new java.util.ArrayList<Instruction>(start.instructions());
        for(int i=0;i<count;i++)instructions.add(new Operations.Nop(ResultFixtures.header(unit.id(),"nop-"+i)));
        var replacement=new Sequence(start.label(),instructions,start.terminator(),start.origin());
        var changed=ResultFixtures.unit(unit.id(),unit.entries(),List.of(replacement,unit.sequences().getLast()),unit.objects());
        return new Publication(base.id(),base.airVersion(),base.capabilities(),base.artifacts(),List.of(changed),base.storage(),base.resources(),base.artifactRelations(),base.origins(),base.coverage(),base.uncertainties(),base.premises());
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
