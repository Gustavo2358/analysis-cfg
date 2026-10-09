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

    @Test void nativeDeclarationIndexesBorrowColdPayloadsAndPreserveEveryIdentity() throws Exception {
        Publication seed;
        try(var input=getClass().getResourceAsStream("/cp6/dynamic-x8.air.json")){seed=new AirJson().decode(input.readAllBytes());}
        var unit=seed.units().getFirst();var template=unit.objects().getFirst();
        var objects=new java.util.ArrayList<>(unit.objects());
        for(int i=0;i<128;i++)objects.add(new Memory.ObjectDeclaration(new ObjectId(unit.id(),"unused-"+i),
            Optional.of("!".repeat(4096)+"/"+i),template.typeRef(),template.storage(),template.visibility(),template.origin(),template.coverage(),template.precision()));
        var storages=new java.util.ArrayList<>(seed.storage());
        var cell=(Memory.Cell)seed.storage().getFirst();var header=cell.header();
        for(int i=0;i<128;i++)storages.add(new Memory.Cell(new Memory.StorageHeader(
            new StorageId(seed.id(),"unused-storage-"+"!".repeat(4096)+"/"+i),
            header.owner(),header.lifetime(),header.visibility(),header.origin()),cell.typeRef()));
        for(int i=0;i<32;i++)storages.add(new Memory.Region(new Memory.StorageHeader(
            new StorageId(seed.id(),"unused-region-storage-"+"!".repeat(4096)+"/"+i),
            header.owner(),header.lifetime(),header.visibility(),header.origin()),
            Optional.of(BigInteger.valueOf(i%2==0?0:8)),Optional.empty()));
        var auditId=objects.getLast().id();var auditRoots=new java.util.HashMap<ObjectId,ObjectId>();
        auditRoots.put(auditId,new ObjectId(auditId.unit(),auditId.localId()));
        assertEquals(2,retainedUnusedObjectIds(auditRoots),"the audit must count distinct stored instances, not equal logical IDs");
        assertEquals(0,retainedUnusedObjectIds(java.util.Map.of(template.id(),template)));
        var replacement=new Unit(unit.id(),unit.containingUnit(),objects,unit.visibleObjects(),unit.entries(),unit.sequences(),unit.completionPorts(),unit.body(),unit.bodyUnavailable(),unit.coverage(),unit.origin());
        var units=new java.util.ArrayList<>(seed.units());units.set(0,replacement);
        var required=new java.util.ArrayList<>(seed.capabilities().required());required.add(Capabilities.MEMORY_REGIONS);
        var publication=new Publication(seed.id(),seed.airVersion(),new Capabilities.Manifest(required,seed.capabilities().provided()),seed.artifacts(),units,storages,seed.resources(),seed.artifactRelations(),seed.origins(),seed.coverage(),seed.uncertainties(),seed.premises());
        var ledger=resources();long[] reads={0};io.github.gustavo2358.analysis.structure.ProgramIndex borrowed;
        var allSelections=new java.util.ArrayList<io.github.gustavo2358.analysis.storage.StorageIndex.Resolution>();
        var allTargets=new java.util.ArrayList<List<io.github.gustavo2358.analysis.storage.StatementEffects.Target>>();
        List<io.github.gustavo2358.analysis.storage.StoragePartition.Segment> borrowedSegments;
        List<io.github.gustavo2358.analysis.storage.StorageIndex.Location> borrowedRegionalBases;
        try(var pages=new FilePageStore(directory,4096,32,ledger);var original=AirSnapshot.fromPublication(publication)) {
            var storage=new PagedAirStorage(pages,ledger,AnalysisResources.Phase.DECODE);
            var tracked=new AirSnapshotBuilder.Storage() {
                @Override public long get(AirSnapshotBuilder.Column column,long index){long value=storage.get(column,index);if(column==AirSnapshotBuilder.Column.CHARACTERS&&value==0x0021002100210021L)reads[0]++;return value;}
                @Override public void set(AirSnapshotBuilder.Column column,long index,long value){storage.set(column,index,value);}
                @Override public AirSnapshotBuilder.Lease claim(long bytes){return storage.claim(bytes);}
                @Override public AirSnapshotBuilder.Lease readLease(long bytes){return storage.readLease(bytes);}
                @Override public void freeze(){storage.freeze();}
                @Override public void close(){storage.close();}
            };
            try(var builder=new AirSnapshotBuilder(tracked)) {
                long root=PagedAirStorageTest.copy(original,original.root(),null,builder);
                try(var checked=SnapshotValidator.check(builder.finish(root),ValidationOptions.defaults(),new PagedSnapshotValidationStorage(pages,ledger))) {
                    assertTrue(checked.result().isStructurallyValid(),checked.result().toString());
                    try(var program=new SnapshotProgram(checked,new PagedSnapshotIdentityStorage(pages,ledger),new PagedSnapshotOrderStorage(pages,ledger),ledger)) {
                        var cfg=new CfgBuildCoordinator(SemanticInterpreterRegistry.empty()).buildChecked(program,checked,BuildOptions.defaults());
                        var opened=io.github.gustavo2358.analysis.structure.AnalysisSession.open(cfg,program,cfg.options().projectionPolicy(),program.units().stream().flatMap(u->u.entries().stream()).map(Entries.Entry::id).toList());
                        assertEquals(io.github.gustavo2358.analysis.structure.AnalysisSession.Status.ACCEPTED,opened.status(),opened.reason());
                        borrowed=opened.session().orElseThrow().index();reads[0]=0;
                        assertEquals(storages.getLast(),borrowed.storage(storages.getLast().header().id()));
                        assertTrue(reads[0]>0,"structural storage lookup retained a complete decoded identity payload");
                        reads[0]=0;assertEquals(storages.getLast(),borrowed.storage(storages.getLast().header().id()));
                        assertTrue(reads[0]>0,"repeated storage lookup must read its cold canonical occurrence");
                        assertNull(borrowed.storage(new StorageId(new PublicationId("foreign"),storages.getLast().header().id().localId())));
                        var storageCatalog=borrowed.storageDeclarations();
                        var nativeStorage=program.storageInventory().orElseThrow();
                        for(int ordinal=0;ordinal<storages.size();ordinal++){
                            var base=storages.get(ordinal);assertEquals(ordinal,nativeStorage.ordinal(base.header().id()));
                            assertEquals(base,nativeStorage.at(ordinal));assertEquals(base instanceof Memory.Region,nativeStorage.regionAt(ordinal));
                            if(base instanceof Memory.Region region)assertEquals(region.extent(),nativeStorage.extentAt(ordinal));
                            else {int at=ordinal;assertThrows(IllegalArgumentException.class,()->nativeStorage.extentAt(at));}
                        }
                        assertEquals(-1,nativeStorage.ordinal(new StorageId(new PublicationId("foreign"),storages.getLast().header().id().localId())));
                        assertEquals(storages,List.copyOf(storageCatalog.values()),"storage must preserve original AIR order");
                        assertThrows(UnsupportedOperationException.class,()->storageCatalog.put(storages.getLast().header().id(),storages.getLast()));
                        assertThrows(UnsupportedOperationException.class,storageCatalog::clear);
                        assertThrows(UnsupportedOperationException.class,()->storageCatalog.keySet().clear());
                        assertThrows(UnsupportedOperationException.class,()->storageCatalog.entrySet().iterator().next().setValue(storages.getLast()));
                        assertEquals(0,retainedUnusedStorageIds(borrowed),"native structural storage retained input-cardinality typed identity payloads");
                        reads[0]=0;
                        assertEquals(objects.getLast(),borrowed.object(objects.getLast().id()));
                        assertTrue(reads[0]>0,"structural declaration lookup retained a decoded display payload");
                        reads[0]=0;assertEquals(objects.getLast(),borrowed.object(objects.getLast().id()));
                        assertTrue(reads[0]>0,"repeated lookup must borrow the same canonical cold occurrence");
                        assertEquals(objects,List.copyOf(borrowed.objects()));
                        assertNull(borrowed.object(new ObjectId(new UnitId(new PublicationId("foreign"),unit.id().localId()),objects.getLast().id().localId())));
                        assertThrows(UnsupportedOperationException.class,()->borrowed.objects().clear());
                        var catalog=borrowed.objectDeclarations();
                        assertThrows(UnsupportedOperationException.class,()->catalog.put(objects.getLast().id(),objects.getLast()));
                        assertThrows(UnsupportedOperationException.class,catalog::clear);
                        assertThrows(UnsupportedOperationException.class,()->catalog.keySet().clear());
                        assertThrows(UnsupportedOperationException.class,()->catalog.entrySet().iterator().next().setValue(objects.getLast()));
                        var inventoryField=borrowed.getClass().getDeclaredField("objects");inventoryField.setAccessible(true);
                        var inventory=inventoryField.get(borrowed);
                        var addressesField=inventory.getClass().getDeclaredField("addresses");addressesField.setAccessible(true);
                        var residentAddresses=(java.util.Map<?,?>)addressesField.get(inventory);
                        assertEquals(0,residentAddresses.size(),
                            "native declaration addresses must use the page owner, not an input-cardinality heap map");
                        var cellField=borrowed.getClass().getDeclaredField("directCells");cellField.setAccessible(true);
                        var directCells=(java.util.Map<?,?>)cellField.get(borrowed);
                        assertEquals(objects.stream().filter(value->value.storage() instanceof Memory.CellBinding).count(),
                            (long)directCells.size());
                        assertEquals(0,retainedUnusedObjectIds(borrowed),
                            "native structural associations retained input-cardinality typed identity payloads");
                        var scalar=io.github.gustavo2358.analysis.values.PossibleValuesAnalysis.prepare(
                            opened.session().orElseThrow(),io.github.gustavo2358.analysis.values.PossibleValuesAnalysis.EFFECTS_PROFILE,
                            java.util.Set.of(template.id()));
                        assertEquals(io.github.gustavo2358.analysis.values.PossibleValuesAnalysis.Status.ACCEPTED,
                            scalar.status(),scalar.reason());
                        var analysis=scalar.analysis().orElseThrow();
                        var profileField=analysis.getClass().getDeclaredField("profile");profileField.setAccessible(true);
                        var profile=profileField.get(analysis);
                        var subjectsField=profile.getClass().getDeclaredField("subjects");subjectsField.setAccessible(true);
                        var scalarSubjects=(java.util.Map<?,?>)subjectsField.get(profile);
                        assertEquals(objects.size(),scalarSubjects.size());
                        assertEquals(0,retainedUnusedObjectIds(analysis),
                            "native scalar associations retained input-cardinality typed identity payloads");
                        var textField=profile.getClass().getDeclaredField("textSubjects");textField.setAccessible(true);
                        var textSubjects=(java.util.Set<?>)textField.get(profile);
                        assertEquals(objects.stream().filter(value->value.typeRef() instanceof Types.Known known
                            &&known.type()==Types.Builtin.TEXT).count(),(long)textSubjects.size());
                        var physical=new io.github.gustavo2358.analysis.storage.StorageIndex(opened.session().orElseThrow());reads[0]=0;
                        assertEquals(storages,List.copyOf(physical.bases()));
                        assertTrue(reads[0]>0,"physical bases retained another complete decoded storage inventory");
                        assertThrows(UnsupportedOperationException.class,()->physical.bases().clear());
                        assertEquals(0,retainedUnusedStorageIds(physical),"native physical bases retained unused storage identity payloads");
                        reads[0]=0;
                        assertEquals(objects,List.copyOf(physical.declarations()));
                        assertTrue(reads[0]>0,"physical declarations must share cold addresses rather than copy all bodies");
                        // Equal reconstructed IDs can retain another complete copy of every cold
                        // identity. Required physical edges must borrow the existing canonical keys.
                        var dependencies=physical.getClass().getDeclaredField("aliasDependencies");
                        dependencies.setAccessible(true);
                        var retained=(java.util.Map<?,?>)dependencies.get(physical);
                        assertEquals(catalog.size(),retained.size());
                        assertEquals(catalog.keySet(),retained.keySet(),"every full physical identity must survive projection");
                        assertEquals(128,retainedUnusedObjectIds(physical),
                            "physical preparation retained a second typed identity copy beside its canonical edges");
                        var effects=new io.github.gustavo2358.analysis.storage.StatementEffects(physical);
                        for(boolean environment:List.of(false,true)) {
                            var scope=new Scopes.AllMemory(seed.id(),environment);reads[0]=0;
                            var selection=physical.select(scope);
                            allSelections.add(selection);
                            assertEquals(storages.size(),selection.candidates().size());
                            assertEquals(storages.stream().map(value->new io.github.gustavo2358.analysis.storage.StorageIndex.Candidate(
                                new io.github.gustavo2358.analysis.storage.StorageIndex.Location(value.header(),
                                    value instanceof Memory.Region region?Optional.of(new io.github.gustavo2358.analysis.storage.StorageRange(BigInteger.ZERO,region.extent())):Optional.empty()),
                                Optional.empty(),List.of(value.header().origin()))).toList(),selection.candidates(),
                                "AllMemory must retain every complete header and its original AIR order");
                            assertEquals(environment?new Scopes.WithinMemory(scope):Scopes.NoMemory.INSTANCE,selection.remainder());
                            assertEquals(environment?List.of("ENVIRONMENT_STORAGE"):List.of(),selection.reasons());
                            assertFalse(selection.exact());
                            assertThrows(UnsupportedOperationException.class,()->selection.candidates().clear());
                            assertEquals(0,retainedUnusedStorageIds(selection),
                                "AllMemory retained complete cold storage identities instead of an owned catalogue view");
                            var targets=effects.targets(selection,io.github.gustavo2358.analysis.storage.StatementEffects.Strength.MUST);
                            allTargets.add(targets);
                            var expectedTargets=new java.util.ArrayList<io.github.gustavo2358.analysis.storage.StatementEffects.Target>();
                            for(boolean direct:environment?List.of(true,false):List.of(true))for(var base:storages) {
                                var range=base instanceof Memory.Region region?Optional.of(new io.github.gustavo2358.analysis.storage.StorageRange(BigInteger.ZERO,region.extent())):Optional.<io.github.gustavo2358.analysis.storage.StorageRange>empty();
                                if(direct&&range.filter(io.github.gustavo2358.analysis.storage.StorageRange::empty).isPresent())continue;
                                expectedTargets.add(new io.github.gustavo2358.analysis.storage.StatementEffects.Target(
                                    new io.github.gustavo2358.analysis.storage.StorageIndex.Location(base.header(),range),
                                    io.github.gustavo2358.analysis.storage.StatementEffects.Strength.MAY,direct,List.of(),selection.reasons()));
                            }
                            assertEquals(expectedTargets,targets,"preserve zero-range filtering, MAY strength, source applicability and environment expansion");
                            reads[0]=0;
                            for(int targetOrdinal=0;targetOrdinal<targets.size();targetOrdinal++){
                                var write=new io.github.gustavo2358.analysis.storage.StatementEffects.Write(0,Optional.empty(),selection,
                                    new io.github.gustavo2358.analysis.storage.StatementEffects.UnknownSource("owned-address-test"),targets,
                                    io.github.gustavo2358.analysis.storage.StatementEffects.Selection.MAY_SET,
                                    io.github.gustavo2358.analysis.storage.StatementEffects.Strength.MAY);
                                var address=io.github.gustavo2358.analysis.storage.StatementEffects.address(write,targetOrdinal).orElseThrow();
                                assertSame(nativeStorage,address.owner());
                                assertEquals(expectedTargets.get(targetOrdinal).location().base().id(),storages.get(address.ordinal()).header().id());
                                assertEquals(expectedTargets.get(targetOrdinal).sourceApplicable(),io.github.gustavo2358.analysis.storage.StatementEffects.sourceApplicable(write,targetOrdinal));
                                assertEquals(expectedTargets.get(targetOrdinal).premises(),io.github.gustavo2358.analysis.storage.StatementEffects.targetPremises(write,targetOrdinal));
                            }
                            assertEquals(0,reads[0],"owned target addresses must not decode full cold identity text");
                            assertThrows(UnsupportedOperationException.class,targets::clear);
                            assertEquals(0,retainedUnusedStorageIds(targets),"AllMemory targets retained cold storage headers");
                        }
                        var partition=new io.github.gustavo2358.analysis.storage.StoragePartition(effects);
                        borrowedSegments=partition.segments();
                        var expectedSegments=new java.util.ArrayList<io.github.gustavo2358.analysis.storage.StoragePartition.Segment>();
                        for(var base:storages){
                            var range=base instanceof Memory.Region region?Optional.of(new io.github.gustavo2358.analysis.storage.StorageRange(BigInteger.ZERO,region.extent())):Optional.<io.github.gustavo2358.analysis.storage.StorageRange>empty();
                            if(range.filter(io.github.gustavo2358.analysis.storage.StorageRange::empty).isPresent())continue;
                            expectedSegments.add(new io.github.gustavo2358.analysis.storage.StoragePartition.Segment(expectedSegments.size(),
                                new io.github.gustavo2358.analysis.storage.StorageIndex.Location(base.header(),range)));
                        }
                        assertEquals(expectedSegments,partition.segments(),"preserve every finite Cell/Region segment and its AIR ordinal");
                        for(var segment:expectedSegments)assertEquals(segment.location().range(),partition.range(segment.ordinal()));
                        assertEquals(0,retainedUnusedStorageIds(partition),"native partition retained complete cold storage identities");
                        assertThrows(UnsupportedOperationException.class,borrowedSegments::clear);
                        for(var base:storages){
                            var whole=new io.github.gustavo2358.analysis.storage.StorageIndex.Location(base.header(),
                                base instanceof Memory.Region region?Optional.of(new io.github.gustavo2358.analysis.storage.StorageRange(BigInteger.ZERO,region.extent())):Optional.empty());
                            assertEquals(expectedSegments.stream().filter(segment->segment.location().base().id().equals(base.header().id())).toList(),partition.intersecting(whole));
                            assertEquals(partition.intersecting(whole),partition.intersecting(new io.github.gustavo2358.analysis.storage.StorageIndex.BaseAddress(nativeStorage,storages.indexOf(base)),whole.range()));
                            reads[0]=0;
                            assertEquals(expectedSegments.stream().filter(segment->segment.location().base().id().equals(base.header().id()))
                                .map(io.github.gustavo2358.analysis.storage.StoragePartition.Segment::ordinal).toList(),
                                partition.ordinals(new io.github.gustavo2358.analysis.storage.StorageIndex.BaseAddress(nativeStorage,storages.indexOf(base)),whole.range()));
                            assertEquals(0,reads[0],"segment ordinal projection must not decode cold storage headers");
                        }
                        try(var other=new SnapshotProgram(checked,new PagedSnapshotIdentityStorage(pages,ledger),new PagedSnapshotOrderStorage(pages,ledger),ledger)){
                            var foreignOwner=other.storageInventory().orElseThrow();
                            assertThrows(IllegalArgumentException.class,()->partition.intersecting(new io.github.gustavo2358.analysis.storage.StorageIndex.BaseAddress(foreignOwner,0),Optional.empty()),
                                "matching nominal AIR IDs do not authorize a descriptor from another inventory owner");
                            assertThrows(IllegalArgumentException.class,()->partition.ordinals(new io.github.gustavo2358.analysis.storage.StorageIndex.BaseAddress(foreignOwner,0),Optional.empty()));
                        }
                        var regional=io.github.gustavo2358.analysis.values.RegionalValuesAnalysis.prepare(opened.session().orElseThrow(),
                            io.github.gustavo2358.analysis.values.StorageAnalysisMode.EXPERIMENTAL_PHYSICAL);
                        assertEquals(io.github.gustavo2358.analysis.values.RegionalValuesAnalysis.Status.ACCEPTED,regional.status(),regional.reason());
                        var preparedRegional=regional.analysis().orElseThrow();
                        assertEquals(0,retainedUnusedStorageIds(preparedRegional),"native physical plans retained full cold storage headers");
                        var regionalBases=preparedRegional.getClass().getDeclaredField("bases");regionalBases.setAccessible(true);
                        @SuppressWarnings("unchecked") var borrowedBases=(List<io.github.gustavo2358.analysis.storage.StorageIndex.Location>)regionalBases.get(preparedRegional);
                        borrowedRegionalBases=borrowedBases;
                        assertEquals(storages.stream().sorted(java.util.Comparator.comparing(base->base.header().id().localId()))
                            .map(base->new io.github.gustavo2358.analysis.storage.StorageIndex.Location(base.header(),base instanceof Memory.Region region
                                ?Optional.of(new io.github.gustavo2358.analysis.storage.StorageRange(BigInteger.ZERO,region.extent())):Optional.empty())).toList(),borrowedRegionalBases,
                            "DAG order must preserve complete canonical local IDs, not numeric intern order");
                    }
                    assertThrows(IllegalStateException.class,()->borrowed.object(objects.getLast().id()));
                    assertThrows(IllegalStateException.class,()->borrowed.directCell(objects.getLast().id()));
                    assertThrows(IllegalStateException.class,()->borrowed.storage(storages.getLast().header().id()));
                    assertThrows(IllegalStateException.class,()->borrowed.storageDeclarations().size());
                    assertThrows(IllegalStateException.class,()->borrowed.objectDeclarations().size());
                    assertThrows(IllegalStateException.class,borrowedSegments::size);
                    assertThrows(IllegalStateException.class,borrowedSegments::getFirst);
                    assertThrows(IllegalStateException.class,borrowedRegionalBases::size);
                    assertThrows(IllegalStateException.class,borrowedRegionalBases::getFirst);
                    for(var selection:allSelections){
                        assertThrows(IllegalStateException.class,()->selection.candidates().size());
                        assertThrows(IllegalStateException.class,()->selection.candidates().getFirst());
                    }
                    for(var targets:allTargets){
                        assertThrows(IllegalStateException.class,targets::size);
                        assertThrows(IllegalStateException.class,targets::getFirst);
                    }
                }
            }
            assertEquals(0,pages.statistics().livePages());
        }
        assertEquals(0,ledger.heapUsed());
    }

    @Test void nativeFinitePartitionPreservesArbitraryCutsAndUnknownTail() {
        var seed=directCall();var unit=seed.units().getFirst();var origin=seed.origins().getFirst().id();
        var huge=BigInteger.ONE.shiftLeft(256).add(BigInteger.valueOf(7));
        var unknownId=new UncertaintyId(seed.id(),"native-unknown-tail");
        var known=new Memory.Region(new Memory.StorageHeader(new StorageId(seed.id(),"native-large-cuts"),Optional.of(unit.id()),Memory.Lifetime.PERSISTENT,Memory.Visibility.PRIVATE,origin),Optional.of(huge),Optional.empty());
        var unknown=new Memory.Region(new Memory.StorageHeader(new StorageId(seed.id(),"native-open-cuts"),Optional.of(unit.id()),Memory.Lifetime.EXTERNAL,Memory.Visibility.PRIVATE,origin),Optional.empty(),Optional.of(unknownId));
        var empty=new Memory.Region(new Memory.StorageHeader(new StorageId(seed.id(),"native-empty"),Optional.of(unit.id()),Memory.Lifetime.PERSISTENT,Memory.Visibility.PRIVATE,origin),Optional.of(BigInteger.ZERO),Optional.empty());
        var objects=new java.util.ArrayList<>(unit.objects());var precision=objects.getFirst().precision();
        objects.add(new Memory.ObjectDeclaration(new ObjectId(unit.id(),"native-left"),Optional.empty(),Types.known(Types.Builtin.BYTES),new Memory.ViewBinding(known.header().id(),BigInteger.ZERO,BigInteger.valueOf(4),Memory.IdentityBytes.INSTANCE),Memory.Visibility.PRIVATE,origin,Evidence.CoverageStatus.MODELED,precision));
        objects.add(new Memory.ObjectDeclaration(new ObjectId(unit.id(),"native-right"),Optional.empty(),Types.known(Types.Builtin.BYTES),new Memory.ViewBinding(known.header().id(),huge.subtract(BigInteger.valueOf(3)),BigInteger.TWO,Memory.IdentityBytes.INSTANCE),Memory.Visibility.PRIVATE,origin,Evidence.CoverageStatus.MODELED,precision));
        objects.add(new Memory.ObjectDeclaration(new ObjectId(unit.id(),"native-tail"),Optional.empty(),Types.known(Types.Builtin.BYTES),new Memory.ViewBinding(unknown.header().id(),BigInteger.TWO,BigInteger.valueOf(3),Memory.IdentityBytes.INSTANCE),Memory.Visibility.PRIVATE,origin,Evidence.CoverageStatus.MODELED,precision));
        var replacement=new Unit(unit.id(),unit.containingUnit(),objects,unit.visibleObjects(),unit.entries(),unit.sequences(),unit.completionPorts(),unit.body(),unit.bodyUnavailable(),unit.coverage(),unit.origin());
        var storages=new java.util.ArrayList<>(seed.storage());storages.addAll(List.of(known,unknown,empty));
        var uncertainties=new java.util.ArrayList<>(seed.uncertainties());uncertainties.add(new Evidence.Uncertainty(unknownId,"UNPROVED",List.of(Evidence.Dimension.STORAGE),new Scopes.PublicationScope(seed.id()),"unknown native extent",origin));
        var required=new java.util.ArrayList<>(seed.capabilities().required());required.add(Capabilities.MEMORY_REGIONS);
        var publication=new Publication(seed.id(),seed.airVersion(),new Capabilities.Manifest(required,seed.capabilities().provided()),seed.artifacts(),List.of(replacement),storages,seed.resources(),seed.artifactRelations(),seed.origins(),seed.coverage(),uncertainties,seed.premises());
        var expected=new java.util.ArrayList<io.github.gustavo2358.analysis.storage.StoragePartition.Segment>();
        for(var base:seed.storage()){
            assertInstanceOf(Memory.Cell.class,base);
            expected.add(new io.github.gustavo2358.analysis.storage.StoragePartition.Segment(expected.size(),new io.github.gustavo2358.analysis.storage.StorageIndex.Location(base.header(),Optional.empty())));
        }
        var knownPoints=List.of(BigInteger.ZERO,BigInteger.valueOf(4),huge.subtract(BigInteger.valueOf(3)),huge.subtract(BigInteger.ONE),huge);
        for(int at=1;at<knownPoints.size();at++)expected.add(new io.github.gustavo2358.analysis.storage.StoragePartition.Segment(expected.size(),new io.github.gustavo2358.analysis.storage.StorageIndex.Location(known.header(),Optional.of(new io.github.gustavo2358.analysis.storage.StorageRange(knownPoints.get(at-1),Optional.of(knownPoints.get(at)))))));
        var unknownPoints=List.of(BigInteger.ZERO,BigInteger.TWO,BigInteger.valueOf(5));
        for(int at=1;at<unknownPoints.size();at++)expected.add(new io.github.gustavo2358.analysis.storage.StoragePartition.Segment(expected.size(),new io.github.gustavo2358.analysis.storage.StorageIndex.Location(unknown.header(),Optional.of(new io.github.gustavo2358.analysis.storage.StorageRange(unknownPoints.get(at-1),Optional.of(unknownPoints.get(at)))))));
        expected.add(new io.github.gustavo2358.analysis.storage.StoragePartition.Segment(expected.size(),new io.github.gustavo2358.analysis.storage.StorageIndex.Location(unknown.header(),Optional.of(new io.github.gustavo2358.analysis.storage.StorageRange(BigInteger.valueOf(5),Optional.empty())))));
        var ledger=resources();List<Integer> borrowed;
        try(var pages=new FilePageStore(directory,512,16,ledger);
            var checked=SnapshotValidator.check(AirSnapshot.fromPublication(publication),ValidationOptions.defaults(),new PagedSnapshotValidationStorage(pages,ledger))) {
            assertTrue(checked.result().isStructurallyValid(),checked.result().toString());
            try(var program=new SnapshotProgram(checked,new PagedSnapshotIdentityStorage(pages,ledger),new PagedSnapshotOrderStorage(pages,ledger),ledger)){
                var cfg=new CfgBuildCoordinator(SemanticInterpreterRegistry.empty()).buildChecked(program,checked,BuildOptions.defaults());
                var opened=io.github.gustavo2358.analysis.structure.AnalysisSession.open(cfg,program,cfg.options().projectionPolicy(),unit.entries().stream().map(Entries.Entry::id).toList());
                assertEquals(io.github.gustavo2358.analysis.structure.AnalysisSession.Status.ACCEPTED,opened.status(),opened.reason());
                var partition=new io.github.gustavo2358.analysis.storage.StoragePartition(new io.github.gustavo2358.analysis.storage.StatementEffects(new io.github.gustavo2358.analysis.storage.StorageIndex(opened.session().orElseThrow())));
                assertEquals(expected,partition.segments(),"arbitrary cuts, adjacent ranges and unbounded tail must survive spill without octet expansion");
                var inventory=program.storageInventory().orElseThrow();
                var knownAddress=new io.github.gustavo2358.analysis.storage.StorageIndex.BaseAddress(inventory,seed.storage().size());
                var unknownAddress=new io.github.gustavo2358.analysis.storage.StorageIndex.BaseAddress(inventory,seed.storage().size()+1);
                borrowed=partition.ordinals(knownAddress,Optional.of(new io.github.gustavo2358.analysis.storage.StorageRange(BigInteger.valueOf(4),Optional.of(huge.subtract(BigInteger.valueOf(3))))));
                assertEquals(List.of(seed.storage().size()+1),borrowed,"adjacency must not include either neighboring segment");
                assertEquals(List.of(seed.storage().size()+5,seed.storage().size()+6),partition.ordinals(unknownAddress,Optional.of(new io.github.gustavo2358.analysis.storage.StorageRange(BigInteger.valueOf(4),Optional.empty()))));
                assertTrue(partition.ordinals(knownAddress,Optional.of(new io.github.gustavo2358.analysis.storage.StorageRange(BigInteger.valueOf(4),Optional.of(BigInteger.valueOf(4))))).isEmpty());
                for(var segment:expected)assertEquals(segment.location().range(),partition.range(segment.ordinal()));
                assertThrows(IndexOutOfBoundsException.class,()->partition.range(-1));
                assertThrows(IndexOutOfBoundsException.class,()->partition.range(expected.size()));
            }
            assertThrows(IllegalStateException.class,borrowed::size);assertThrows(IllegalStateException.class,borrowed::getFirst);
        }
        for(var pool:AnalysisResources.Pool.values())assertEquals(0,ledger.used(pool),pool.toString());
    }

    @Test void failedNativePartitionColumnWriteReleasesAllOwnersAndPreservesPrimary() {
        var publication=directCall();var ledger=resources();var primary=new IllegalStateException("injected failure after native partition column write");
        int[] columns={0},written={0};
        try(var pages=new FilePageStore(directory,512,16,ledger);
            var checked=SnapshotValidator.check(AirSnapshot.fromPublication(publication),ValidationOptions.defaults(),new PagedSnapshotValidationStorage(pages,ledger))){
            var delegate=new PagedSnapshotOrderStorage(pages,ledger);
            var failing=new io.github.gustavo2358.analysis.dependencies.SnapshotOrderStorage(){
                @Override public Index open(Order order){return delegate.open(order);}
                @Override public Tape tape(){return delegate.tape();}
                @Override public ProgramStore.OrdinalColumn column(long length){
                    var column=delegate.column(length);if(++columns[0]!=4)return column;
                    return new ProgramStore.OrdinalColumn(){
                        @Override public long get(long ordinal){return column.get(ordinal);}
                        @Override public void set(long ordinal,long value){column.set(ordinal,value);if(value!=0){written[0]++;throw primary;}}
                        @Override public void close(){column.close();}
                    };
                }
                @Override public void close(){delegate.close();}
            };
            try(var program=new SnapshotProgram(checked,new PagedSnapshotIdentityStorage(pages,ledger),failing,ledger)){
                var cfg=new CfgBuildCoordinator(SemanticInterpreterRegistry.empty()).buildChecked(program,checked,BuildOptions.defaults());
                var opened=io.github.gustavo2358.analysis.structure.AnalysisSession.open(cfg,program,cfg.options().projectionPolicy(),publication.units().getFirst().entries().stream().map(Entries.Entry::id).toList());
                assertEquals(io.github.gustavo2358.analysis.structure.AnalysisSession.Status.ACCEPTED,opened.status(),opened.reason());
                var effects=new io.github.gustavo2358.analysis.storage.StatementEffects(new io.github.gustavo2358.analysis.storage.StorageIndex(opened.session().orElseThrow()));
                long originalHeap=ledger.heapUsed(),originalPages=pages.statistics().livePages();
                assertSame(primary,assertThrows(IllegalStateException.class,()->new io.github.gustavo2358.analysis.storage.StoragePartition(effects)));
                assertEquals(1,written[0],"fault must follow a real nonzero paged column write");
                assertEquals(originalHeap,ledger.heapUsed());assertEquals(originalPages,pages.statistics().livePages());
                assertSame(checked.snapshot(),program.admission().snapshot(),"partition failure must not close borrowed AIR");
                assertEquals(publication.storage().size(),program.storageInventory().orElseThrow().size());
            }
        }
        for(var pool:AnalysisResources.Pool.values())assertEquals(0,ledger.used(pool),pool.toString());
    }

    /** Walk stored fields, not custom lazy Map/List projections. Counts are actual
     * identity-deduplicated objects, never reported as measured heap bytes. */
    private static int retainedUnusedObjectIds(Object root) throws ReflectiveOperationException {
        return retainedUnusedIdentities(root,ObjectId.class);
    }
    private static int retainedUnusedStorageIds(Object root) throws ReflectiveOperationException {
        return retainedUnusedIdentities(root,StorageId.class);
    }
    private static int retainedUnusedIdentities(Object root,Class<?> identityType) throws ReflectiveOperationException {
        var seen=java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Object,Boolean>());
        var pending=new java.util.ArrayDeque<Object>();pending.add(root);int count=0;
        while(!pending.isEmpty()) {
            var value=pending.removeFirst();if(!seen.add(value))continue;
            if(value instanceof ObjectId id&&id.localId().startsWith("unused-")){
                if(identityType==ObjectId.class)count++;continue;
            }
            if(value instanceof StorageId id&&id.localId().startsWith("unused-")){
                if(identityType==StorageId.class)count++;continue;
            }
            var type=value.getClass();var name=type.getName();
            if(value instanceof java.util.Optional<?> optional){optional.ifPresent(pending::addLast);continue;}
            if(type.isArray()){
                if(!type.componentType().isPrimitive())for(var item:(Object[])value)if(item!=null)pending.addLast(item);
                continue;
            }
            if(name.startsWith("java.util.")&&!name.contains("$Unmodifiable")) {
                if(value instanceof java.util.Map<?,?> map){for(var entry:map.entrySet()){
                    if(entry.getKey()!=null)pending.addLast(entry.getKey());if(entry.getValue()!=null)pending.addLast(entry.getValue());
                }continue;}
                if(value instanceof Iterable<?> items){for(var item:items)if(item!=null)pending.addLast(item);continue;}
            }
            for(var owner=type;owner!=null&&owner.getName().startsWith("io.github.gustavo2358.");owner=owner.getSuperclass())
                for(var field:owner.getDeclaredFields())if(!java.lang.reflect.Modifier.isStatic(field.getModifiers())
                    &&!field.getType().isPrimitive()){
                    field.setAccessible(true);var item=field.get(value);if(item!=null)pending.addLast(item);
                }
        }
        return count;
    }

    @Test void generalMetadataUsesOrderedBorrowedPagesWithoutReadingUnusedPayloads() throws Exception {
        Publication seed;
        try(var input=getClass().getResourceAsStream("/cp6/dynamic-x8.air.json")){seed=new AirJson().decode(input.readAllBytes());}
        var origins=new java.util.ArrayList<>(seed.origins());
        for(int i=127;i>=0;i--)origins.add(new Origins.Derived(new OriginId(seed.id(),"unused-"+i),
            List.of(seed.origins().getFirst().id()),"!".repeat(4096)+"/"+i));
        var publication=new Publication(seed.id(),seed.airVersion(),seed.capabilities(),seed.artifacts().reversed(),
            seed.units(),seed.storage(),seed.resources(),seed.artifactRelations(),origins,seed.coverage(),seed.uncertainties(),seed.premises());
        var ledger=resources();long[] payloadReads={0};List<Origins.Origin> borrowed;
        try(var pages=new FilePageStore(directory,4096,32,ledger);var original=AirSnapshot.fromPublication(publication)) {
            var storage=new PagedAirStorage(pages,ledger,AnalysisResources.Phase.DECODE);
            var tracked=new AirSnapshotBuilder.Storage() {
                @Override public long get(AirSnapshotBuilder.Column column,long index) {
                    long value=storage.get(column,index);
                    // The official snapshot packs four UTF-16 characters into one word.
                    if(column==AirSnapshotBuilder.Column.CHARACTERS&&value==0x0021002100210021L)payloadReads[0]++;
                    return value;
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
                    assertTrue(checked.result().isStructurallyValid(),checked.result().toString());
                    var ordered=new PagedSnapshotOrderStorage(pages,ledger);int[] sorts={0};
                    var trackedOrders=new io.github.gustavo2358.analysis.dependencies.SnapshotOrderStorage() {
                        @Override public Index open(Order order){sorts[0]++;return ordered.open(order);}
                        @Override public Tape tape(){return ordered.tape();}
                        @Override public io.github.gustavo2358.analysis.structure.ProgramStore.OrdinalColumn column(long length){return ordered.column(length);}
                        @Override public void close(){ordered.close();}
                    };
                    try(var program=new SnapshotProgram(checked,new PagedSnapshotIdentityStorage(pages,ledger),trackedOrders,ledger)) {
                        var cfg=new CfgBuildCoordinator(SemanticInterpreterRegistry.empty()).buildChecked(program,checked,BuildOptions.defaults());
                        assertEquals(CfgBuildResult.Status.CFG_BUILT,cfg.status());payloadReads[0]=0;
                        program.origins().getLast();assertTrue(payloadReads[0]>0,"payload instrumentation must observe a real cold read");payloadReads[0]=0;
                        var result=new io.github.gustavo2358.analysis.dependencies.DependencyAnalysis().prepareLeased(program,cfg);
                        assertEquals(0,payloadReads[0],"preparation retained unused Origin bodies instead of borrowed addresses");
                        borrowed=result.origins();assertEquals(origins.size(),borrowed.size());
                        int preparedSorts=sorts[0];
                        assertSame(borrowed,result.withProgramInventory(result.programDependencies(),result.metrics()).origins());
                        assertEquals(seed.origins().getFirst(),program.origin(seed.origins().getFirst().id()));
                        assertNull(program.origin(new OriginId(seed.id(),"missing")));
                        assertNull(program.origin(new OriginId(new PublicationId("foreign"),seed.origins().getFirst().id().localId())));
                        assertEquals(origins.getLast(),program.origin(origins.getLast().id()));
                        assertEquals(preparedSorts,sorts[0],"origin lookup must reuse the canonical address order");
                        var expectedOrigins=origins.stream().sorted(java.util.Comparator.comparing(o->o.id().localId())).toList();
                        for(int i=0;i<expectedOrigins.size();i++)assertEquals(expectedOrigins.get(i),borrowed.get(i),"ordered Origin ordinal "+i);
                        assertEquals(seed.artifacts().stream()
                            .sorted(java.util.Comparator.comparing(a->a.id().localId())).toList(),result.artifacts());
                        var out=new ByteArrayOutputStream();new DependencyJson().writeSnapshot(result,program,out);
                        var detached=new io.github.gustavo2358.analysis.dependencies.DependencyAnalysis().prepare(publication);
                        var reference=new ByteArrayOutputStream();new DependencyJson().writeSnapshot(detached,program,reference);
                        var mapper=new com.fasterxml.jackson.databind.ObjectMapper();
                        var expected=mapper.readTree(reference.toByteArray());var actual=mapper.readTree(out.toByteArray());
                        for(var field:List.of("sites","edges","origins","artifacts","sourceUncertaintyRefs","fileDependencies",
                            "sourceDependencies","sourceQualifiedDependencies","dependencies","version","modelScope","metrics"))
                            assertEquals(expected.get(field),actual.get(field),"complete metadata-leased result section: "+field);
                        var repeated=new ByteArrayOutputStream();new DependencyJson().writeSnapshot(result,program,repeated);
                        assertArrayEquals(out.toByteArray(),repeated.toByteArray(),"native canonical output must be byte deterministic");
                        assertThrows(UnsupportedOperationException.class,borrowed::clear);
                    }
                    assertThrows(IllegalStateException.class,borrowed::size);
                    assertThrows(IllegalStateException.class,borrowed::getFirst);
                }
            }
            assertEquals(0,pages.statistics().livePages());
        }
        assertEquals(0,ledger.heapUsed());
    }

    @Test void exhaustedNativeMetadataOrderReleasesPartialPagesWithoutClosingInput() {
        for(int remaining:new int[]{0,1}) {
        var publication=directCall();var ledger=resources();
        try(var pages=new FilePageStore(directory,512,16,ledger);var snapshot=AirSnapshot.fromPublication(publication);
            var checked=SnapshotValidator.check(snapshot,ValidationOptions.defaults(),new PagedSnapshotValidationStorage(pages,ledger));
            var program=new SnapshotProgram(checked,new PagedSnapshotIdentityStorage(pages,ledger),new PagedSnapshotOrderStorage(pages,ledger),ledger)) {
            long before=ledger.heapUsed(),live=pages.statistics().livePages();
            ledger.work(ledger.limits().workUnits()-ledger.workUsed()-remaining,AnalysisResources.Phase.INDEX);
            var failure=assertThrows(AnalysisResources.Exhausted.class,program::orderedOrigins);
            assertEquals(remaining==0?AnalysisResources.Phase.INDEX:AnalysisResources.Phase.DOMAIN,failure.phase());
            assertEquals(before,ledger.heapUsed());assertEquals(live,pages.statistics().livePages());
            assertSame(snapshot,checked.snapshot());assertEquals(publication.id(),program.publicationId());
        }
        for(var pool:AnalysisResources.Pool.values())assertEquals(0,ledger.used(pool),pool.toString());
        }
    }

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
                    @Override public io.github.gustavo2358.analysis.structure.ProgramStore.OrdinalColumn column(long length){return ordered.column(length);}
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

    @Test void snapshotCfgNodesAndRoleInventoriesBorrowColdDescriptorsAndExpireWithTheirOwner() {
        long fixed=-1;
        for(int count:new int[]{1,4,16,64,256}) {
            var publication=directCalls(count);var ledger=resources();
            try(var pages=new FilePageStore(directory,512,16,ledger);
                var checked=SnapshotValidator.check(AirSnapshot.fromPublication(publication),ValidationOptions.defaults(),new PagedSnapshotValidationStorage(pages,ledger))) {
                List<io.github.gustavo2358.analysis.cfg.domain.CfgNode> borrowed;
                List<io.github.gustavo2358.analysis.cfg.domain.CfgNode.EntryNode> entries;
                try(var program=new SnapshotProgram(checked,new PagedSnapshotIdentityStorage(pages,ledger),new PagedSnapshotOrderStorage(pages,ledger))) {
                    program.source();long before=ledger.heapUsed();
                    var graph=CoreCfgProjection.project(program);borrowed=graph.nodes();entries=graph.entries();
                    assertEquals("CfgNodeInventory",borrowed.getClass().getSimpleName(),"snapshot nodes must not retain one resident object per node");
                    long retained=ledger.heapUsed()-before;
                    if(fixed<0)fixed=retained;else assertEquals(fixed,retained,"descriptor control metadata must not grow with Units");
                    assertEquals(4*count,borrowed.size());assertEquals(count,entries.size());assertEquals(count,graph.normalExits().size());assertEquals(0,graph.haltExits().size());
                    assertEquals(3*count,graph.transitions().size());
                    for(int ordinal=0;ordinal<count;ordinal++) {
                        var entry=entries.get(ordinal);assertEquals("entry",entry.entry().localId());assertEquals("start",entry.initialLabel().orElseThrow().localId());
                        assertEquals(4L*ordinal+2,entry.id().ordinal());
                        assertEquals(entry.entry(),graph.normalExits().get(ordinal).entryId());
                        assertEquals(4L*ordinal+3,graph.normalExits().get(ordinal).id().ordinal());
                        var end=assertInstanceOf(io.github.gustavo2358.analysis.cfg.domain.CfgNode.SequenceNode.class,borrowed.get(4*ordinal));
                        assertEquals("end",end.label().localId());assertInstanceOf(io.github.gustavo2358.analysis.cfg.domain.CfgControl.Return.class,end.control());
                        var start=assertInstanceOf(io.github.gustavo2358.analysis.cfg.domain.CfgNode.SequenceNode.class,borrowed.get(4*ordinal+1));
                        assertEquals("start",start.label().localId());assertEquals(List.of("seed"),start.operations().stream().map(OperationId::localId).toList());
                        assertInstanceOf(io.github.gustavo2358.analysis.cfg.domain.CfgControl.Invoke.class,start.control());
                    }
                    assertEquals(CoreCfgProjection.project(publication),graph);
                    var repeated=CoreCfgProjection.project(program);assertEquals(graph,repeated);
                    assertEquals(retained,ledger.heapUsed()-before,"repeat projection must reuse the immutable descriptor owner");
                    assertThrows(UnsupportedOperationException.class,borrowed::clear);assertThrows(UnsupportedOperationException.class,entries::clear);
                    System.out.println("SNAPSHOT_CFG_NODE_DESCRIPTOR_METRICS units="+count+" nodes="+borrowed.size()+" fixedHeap="+retained);
                }
                assertThrows(IllegalStateException.class,borrowed::size);assertThrows(IllegalStateException.class,borrowed::getFirst);
                assertThrows(IllegalStateException.class,entries::size);assertThrows(IllegalStateException.class,entries::getFirst);
                assertEquals(0,pages.statistics().livePages());
            }
            assertEquals(0,ledger.heapUsed());
        }
    }

    @Test void snapshotCfgPhysicalTransitionsStayFactoredColdAndExpireWithProjection() {
        long fixed=-1;
        for(int count:new int[]{1,4,16,64}) {
            var publication=directCalls(count);
            var ledger=resources();
            try(var pages=new FilePageStore(directory,512,16,ledger);
                var checked=SnapshotValidator.check(AirSnapshot.fromPublication(publication),ValidationOptions.defaults(),new PagedSnapshotValidationStorage(pages,ledger))) {
                assertEquals(ValidationResult.Status.STRUCTURALLY_VALID,checked.result().status(),checked.result().toString());
                try(var program=new SnapshotProgram(checked,new PagedSnapshotIdentityStorage(pages,ledger),new PagedSnapshotOrderStorage(pages,ledger))) {
                program.source();long before=ledger.heapUsed();var graph=CoreCfgProjection.project(program);
                var table=assertInstanceOf(io.github.gustavo2358.analysis.cfg.domain.CfgTransitionTable.class,graph.transitions());
                var physical=table.stored();
                assertEquals("PhysicalRows",physical.getClass().getSimpleName(),"no input-sized resident transition inventory");
                assertEquals(count,table.groups());assertEquals(3*count,physical.size());assertEquals(3*count,table.size());
                long retained=ledger.heapUsed()-before;if(fixed<0)fixed=retained;else assertEquals(fixed,retained);
                for(int g=0;g<count;g++) {
                    long offset=4L*g;var entry=table.entry(g,0);var activation=entry.activationEntry();
                    assertEquals("entry",activation.localId());assertEquals(offset+2,entry.from().ordinal());assertEquals(offset+1,entry.to().ordinal());
                    assertEquals(offset+3,table.normalExit(g,0).ordinal());
                    var returned=table.get(3*g+1);assertEquals(io.github.gustavo2358.analysis.cfg.domain.CfgTransition.Kind.RETURN,returned.kind());
                    assertEquals(offset,returned.from().ordinal());assertEquals(table.normalExit(g,0),returned.to());assertEquals(activation,returned.activationEntry());
                    var called=table.get(3*g+2);assertEquals(io.github.gustavo2358.analysis.cfg.domain.CfgTransition.Kind.INVOKE_NORMAL,called.kind());
                    assertEquals(offset+1,called.from().ordinal());assertEquals(offset,called.to().ordinal());assertEquals(activation,called.activationEntry());
                }
                var resident=CoreCfgProjection.project(publication);assertEquals(resident,graph);assertEquals(resident.hashCode(),graph.hashCode());
                var dense=new java.util.ArrayList<>(table);assertEquals(dense.hashCode(),table.hashCode());assertEquals(dense,table);assertEquals(table,dense);
                assertEquals(graph,CoreCfgProjection.project(program));assertEquals(retained,ledger.heapUsed()-before);
                assertThrows(UnsupportedOperationException.class,physical::clear);
                System.out.println("SNAPSHOT_CFG_TRANSITION_DESCRIPTOR_METRICS units="+count+" physical="+physical.size()+" logical="+table.size()+" fixedHeap="+retained);
                program.releaseCfgProjection(io.github.gustavo2358.analysis.cfg.domain.ProjectionPolicy.KNOWN_SUBSET);
                assertEquals(before,ledger.heapUsed());assertThrows(IllegalStateException.class,table::size);assertThrows(IllegalStateException.class,table::groups);
                assertThrows(IllegalStateException.class,physical::size);assertThrows(IllegalStateException.class,physical::getFirst);
                }
            }
            for(var pool:AnalysisResources.Pool.values())assertEquals(0,ledger.used(pool),pool.toString());
        }
    }

    @Test void deniedCfgDescriptorConstructionReleasesEveryPartialTape() {
        for(int left:new int[]{3000,7000,10000,14000,17000,20000}) {
            var ledger=resources();
            try(var pages=new FilePageStore(directory,512,16,ledger);
                var checked=SnapshotValidator.check(AirSnapshot.fromPublication(directCall()),ValidationOptions.defaults(),new PagedSnapshotValidationStorage(pages,ledger));
                var program=new SnapshotProgram(checked,new PagedSnapshotIdentityStorage(pages,ledger),new PagedSnapshotOrderStorage(pages,ledger))) {
                program.source();
                try(var pressure=ledger.reserve(AnalysisResources.Pool.RESIDENT,ledger.limits().heapBytes()-ledger.heapUsed()-left,AnalysisResources.Phase.CONTROL)) {
                    assertTrue(pressure.amount()>0);long before=ledger.heapUsed();
                    assertThrows(AnalysisResources.Exhausted.class,()->CoreCfgProjection.project(program));
                    assertEquals(before,ledger.heapUsed(),"partially created descriptor tapes leaked their fixed reservations");
                }
            }
            assertEquals(0,ledger.heapUsed());
            for(var pool:AnalysisResources.Pool.values())assertEquals(0,ledger.used(pool),pool.toString());
        }
    }

    @Test void sharedLabelEntriesKeepEveryDependencyContextAndColdReturnBinding() {
        for(int count:new int[]{1,4,16,64}) {
            var base=directCall();var unit=base.units().getFirst();
            var entries=java.util.stream.IntStream.range(0,count).mapToObj(i->ResultFixtures.entry(unit.id(),String.format("entry-%04d",i),"start")).toList().reversed();
            var changed=ResultFixtures.unit(unit.id(),entries,unit.sequences().reversed(),unit.objects());
            var publication=new Publication(base.id(),base.airVersion(),base.capabilities(),base.artifacts(),List.of(changed),base.storage(),base.resources(),base.artifactRelations(),base.origins(),base.coverage(),base.uncertainties(),base.premises());
            var ledger=resources();
            try(var pages=new FilePageStore(directory,512,16,ledger);
                var checked=SnapshotValidator.check(AirSnapshot.fromPublication(publication),ValidationOptions.defaults(),new PagedSnapshotValidationStorage(pages,ledger));
                var program=new SnapshotProgram(checked,new PagedSnapshotIdentityStorage(pages,ledger),new PagedSnapshotOrderStorage(pages,ledger))) {
                assertEquals(ValidationResult.Status.STRUCTURALLY_VALID,checked.result().status());
                var graph=CoreCfgProjection.project(program);var table=assertInstanceOf(io.github.gustavo2358.analysis.cfg.domain.CfgTransitionTable.class,graph.transitions());
                assertEquals(count+2,table.stored().size());assertEquals(3*count,table.size());
                assertEquals(CoreCfgProjection.project(publication),graph);assertEquals(new java.util.ArrayList<>(table).hashCode(),table.hashCode());
                for(int i=0;i<count;i++) {
                    var binding=table.entry(0,i);var returned=table.get(3*i+1);
                    assertEquals(String.format("entry-%04d",i),binding.activationEntry().localId());
                    assertEquals(binding.activationEntry(),returned.activationEntry());assertEquals(table.normalExit(0,i),returned.to());
                }
                int[] definitions={0};program.definitions(ignored->definitions[0]++);assertEquals(1,definitions[0],"the shared body must not be evaluated once per Entry");
                program.releaseCfgProjection(io.github.gustavo2358.analysis.cfg.domain.ProjectionPolicy.KNOWN_SUBSET);
                var result=new SnapshotDependencyAnalysis().analyze(program,new PagedSnapshotDependencyStorage(pages,ledger));
                assertEquals(count,result.sites().size(),"every Entry context must reach delivery");
                for(int i=0;i<count;i++) {
                    var site=result.sites().get(i);assertEquals(String.format("entry-%04d",i),site.entry().localId());
                    assertEquals("invoke",site.operation().localId());assertEquals("start",site.sequence().localId());
                    assertEquals(1,site.candidates().size());var candidate=site.candidates().getFirst();
                    assertEquals("PROGA",candidate.referenceName());assertEquals("PROGA   ",candidate.rawValue());
                    assertEquals("seed",candidate.supports().getFirst().producer().localId());assertEquals(unit.origin(),candidate.supports().getFirst().origin());
                }
                System.out.println("SHARED_ENTRY_DELIVERY_METRICS entries="+count+" physical="+(count+2)+" logical="+(3*count)+" definitions="+definitions[0]+" sites="+result.sites().size());
            }
            for(var pool:AnalysisResources.Pool.values())assertEquals(0,ledger.used(pool),pool.toString());
        }
    }

    @Test void pagedUnitRoutingIsColdExactAndOwnedByTheProjection() {
        for(int count:new int[]{1,4,16,64,256}) {
            var publication=directOrphans(count);var unit=publication.units().getFirst();var ledger=resources();
            try(var pages=new FilePageStore(directory,512,16,ledger);
                var checked=SnapshotValidator.check(AirSnapshot.fromPublication(publication),ValidationOptions.defaults(),new PagedSnapshotValidationStorage(pages,ledger));
                var program=new SnapshotProgram(checked,new PagedSnapshotIdentityStorage(pages,ledger),new PagedSnapshotOrderStorage(pages,ledger))) {
                assertEquals(ValidationResult.Status.STRUCTURALLY_VALID,checked.result().status());program.source();long original=ledger.heapUsed();
                var writer=program.nodes(io.github.gustavo2358.analysis.cfg.domain.ProjectionPolicy.KNOWN_SUBSET);
                program.units(view->view.sequences(sequence->{
                    writer.append(new io.github.gustavo2358.analysis.cfg.domain.CfgNode.SequenceNode(new io.github.gustavo2358.analysis.cfg.domain.CfgNodeId(publication.id(),writer.size()),sequence.label(),sequence.operations(),sequence.control()),sequence.sourceHandle(),0);
                    if(sequence.control() instanceof io.github.gustavo2358.analysis.cfg.domain.CfgControl.Halt halt)
                        writer.append(new io.github.gustavo2358.analysis.cfg.domain.CfgNode.HaltExit(new io.github.gustavo2358.analysis.cfg.domain.CfgNodeId(publication.id(),writer.size()),halt.operation(),halt.haltKind()),sequence.sourceHandle(),0);
                }));
                long before=ledger.heapUsed();var routing=writer.routing(unit.id(),0,writer.size());
                assertEquals(3072,ledger.heapUsed()-before,"one native ordinal tape, independent of Unit cardinality");
                int ordinal=0;
                for(var sequence:unit.sequences().stream().sorted(java.util.Comparator.comparing(s->s.label().localId())).toList()) {
                    var destination=routing.sequence(sequence.label());assertEquals(ordinal,destination.ordinal());
                    var node=(io.github.gustavo2358.analysis.cfg.domain.CfgNode.SequenceNode)writer.get(ordinal);
                    var outside=new java.util.ArrayList<io.github.gustavo2358.analysis.cfg.domain.CfgNodeId>();routing.outside(node,outside::add);assertTrue(outside.isEmpty());
                    if(node.control() instanceof io.github.gustavo2358.analysis.cfg.domain.CfgControl.Halt){assertEquals(ordinal+1,routing.halt(node).ordinal());ordinal++;}
                    else assertThrows(IllegalArgumentException.class,()->routing.halt(node));
                    ordinal++;
                }
                assertThrows(IllegalArgumentException.class,()->routing.sequence(new LabelId(new UnitId(new PublicationId("foreign-routing"),unit.id().localId()),"start")));
                assertThrows(IllegalArgumentException.class,()->routing.sequence(new LabelId(unit.id(),"absent")));
                assertThrows(IllegalStateException.class,()->writer.routing(unit.id(),0,writer.size()));
                System.out.println("SNAPSHOT_UNIT_ROUTING_METRICS sequences="+unit.sequences().size()+" fixedHeap="+(ledger.heapUsed()-before)+" nodes="+writer.size());
                routing.close();assertEquals(before,ledger.heapUsed());assertThrows(IllegalStateException.class,()->routing.sequence(unit.sequences().getFirst().label()));
                var borrowed=writer.routing(unit.id(),0,writer.size());
                program.releaseCfgProjection(io.github.gustavo2358.analysis.cfg.domain.ProjectionPolicy.KNOWN_SUBSET);
                assertEquals(original,ledger.heapUsed());assertThrows(IllegalStateException.class,()->borrowed.sequence(unit.sequences().getFirst().label()));borrowed.close();
            }
            for(var pool:AnalysisResources.Pool.values())assertEquals(0,ledger.used(pool),pool.toString());
        }
    }

    @Test void deniedUnitRoutingConstructionAbortsAndReleasesNativeDescriptors() {
        var publication=directCall();var ledger=resources();
        try(var pages=new FilePageStore(directory,512,16,ledger);
            var checked=SnapshotValidator.check(AirSnapshot.fromPublication(publication),ValidationOptions.defaults(),new PagedSnapshotValidationStorage(pages,ledger));
            var program=new SnapshotProgram(checked,new PagedSnapshotIdentityStorage(pages,ledger),new PagedSnapshotOrderStorage(pages,ledger))) {
            program.source();var writer=program.nodes(io.github.gustavo2358.analysis.cfg.domain.ProjectionPolicy.KNOWN_SUBSET);
            program.units(unit->unit.sequences(sequence->writer.append(new io.github.gustavo2358.analysis.cfg.domain.CfgNode.SequenceNode(new io.github.gustavo2358.analysis.cfg.domain.CfgNodeId(publication.id(),writer.size()),sequence.label(),sequence.operations(),sequence.control()),sequence.sourceHandle(),0)));
            int end=writer.size();
            try(var pressure=ledger.reserve(AnalysisResources.Pool.RESIDENT,ledger.limits().heapBytes()-ledger.heapUsed()-2500,AnalysisResources.Phase.CONTROL)) {
                assertTrue(pressure.amount()>0);long before=ledger.heapUsed();
                assertThrows(AnalysisResources.Exhausted.class,()->writer.routing(publication.units().getFirst().id(),0,end));assertEquals(before,ledger.heapUsed());
                assertThrows(IllegalStateException.class,writer::seal);
            }
        }
        for(var pool:AnalysisResources.Pool.values())assertEquals(0,ledger.used(pool),pool.toString());

        for(int targetTape:new int[]{2,3,4}) {
        var catalogLedger=resources();
        try(var pages=new FilePageStore(directory,512,16,catalogLedger);
            var checked=SnapshotValidator.check(AirSnapshot.fromPublication(publication),ValidationOptions.defaults(),new PagedSnapshotValidationStorage(pages,catalogLedger))) {
            long originalPages=pages.statistics().livePages(),originalHeap=catalogLedger.heapUsed();
            var delegate=new PagedSnapshotOrderStorage(pages,catalogLedger);
            var primary=new IllegalStateException("injected failure after native catalogue lookup append");
            int[] tapes={0},written={0};
            var failing=new io.github.gustavo2358.analysis.dependencies.SnapshotOrderStorage() {
                @Override public Index open(Order order){return delegate.open(order);}
                @Override public io.github.gustavo2358.analysis.structure.ProgramStore.OrdinalColumn column(long length){return delegate.column(length);}
                @Override public Tape tape(){
                    var tape=delegate.tape();
                    if(++tapes[0]!=targetTape)return tape;
                    return new Tape(){
                        @Override public void append(long handle){tape.append(handle);written[0]++;throw primary;}
                        @Override public long size(){return tape.size();}
                        @Override public long handle(long ordinal){return tape.handle(ordinal);}
                        @Override public void close(){tape.close();}
                    };
                }
                @Override public void close(){delegate.close();}
            };
            try(var program=new SnapshotProgram(checked,new PagedSnapshotIdentityStorage(pages,catalogLedger),failing,catalogLedger)) {
                assertSame(primary,assertThrows(IllegalStateException.class,()->{
                    if(targetTape==2)program.declarationInventory();
                    else {
                        var catalogue=program.storageInventory().orElseThrow();
                        if(targetTape==4)catalogue.nonEmptyStorage();
                    }
                }));
                assertEquals(1,written[0],"failure must occur after a real paged lookup write");
                assertSame(checked.snapshot(),program.admission().snapshot(),"partial catalogue cleanup must not close the AIR owner");
            }
            assertEquals(originalPages,pages.statistics().livePages(),"partial catalogue tapes or temporary index leaked pages");
            assertEquals(originalHeap,catalogLedger.heapUsed(),"partial catalogue leaked reservations");
        }
        for(var pool:AnalysisResources.Pool.values())assertEquals(0,catalogLedger.used(pool),pool.toString());
        }
    }

    @Test void pagedPhysicalTupleStorageRejectsDuplicatesAndForeignOrMissingBindings() {
        for(int mutation=0;mutation<8;mutation++) {
            var ledger=resources();
            try(var pages=new FilePageStore(directory,512,16,ledger);
                var checked=SnapshotValidator.check(AirSnapshot.fromPublication(directCall()),ValidationOptions.defaults(),new PagedSnapshotValidationStorage(pages,ledger));
                var program=new SnapshotProgram(checked,new PagedSnapshotIdentityStorage(pages,ledger),new PagedSnapshotOrderStorage(pages,ledger))) {
                var writer=program.nodes(io.github.gustavo2358.analysis.cfg.domain.ProjectionPolicy.KNOWN_SUBSET);
                var storage=writer.transitions();var publication=program.publication();
                program.units(unit->{
                    unit.sequences(sequence->writer.append(new io.github.gustavo2358.analysis.cfg.domain.CfgNode.SequenceNode(
                            new io.github.gustavo2358.analysis.cfg.domain.CfgNodeId(publication,writer.size()),sequence.label(),sequence.operations(),sequence.control()),sequence.sourceHandle(),0));
                    unit.entries(entry->{
                        writer.append(new io.github.gustavo2358.analysis.cfg.domain.CfgNode.EntryNode(new io.github.gustavo2358.analysis.cfg.domain.CfgNodeId(publication,writer.size()),entry.id(),entry.initialLabel()),entry.sourceHandle(),0);
                        writer.append(new io.github.gustavo2358.analysis.cfg.domain.CfgNode.NormalExit(new io.github.gustavo2358.analysis.cfg.domain.CfgNodeId(publication,writer.size()),publication,unit.id(),entry.id()),entry.sourceHandle(),0);
                    });
                });
                var inventory=writer.seal();var entry=(io.github.gustavo2358.analysis.cfg.domain.CfgNode.EntryNode)inventory.get(2);
                var binding=new io.github.gustavo2358.analysis.cfg.domain.CfgTransition(entry.id(),inventory.get(1).id(),io.github.gustavo2358.analysis.cfg.domain.CfgTransition.Kind.ENTRY,entry.entry());
                var returned=new io.github.gustavo2358.analysis.cfg.domain.CfgTransition(inventory.getFirst().id(),inventory.get(3).id(),io.github.gustavo2358.analysis.cfg.domain.CfgTransition.Kind.RETURN,entry.entry());
                if(mutation>=5) {
                    var group=storage.begin(entry.entry().unit());
                    if(mutation==5)assertThrows(IllegalStateException.class,()->group.body(returned));
                    else {
                        group.binding(binding,inventory.get(3).id());
                        if(mutation==6){group.body(returned);assertThrows(IllegalStateException.class,()->group.binding(binding,inventory.get(3).id()));}
                        else assertThrows(IllegalArgumentException.class,storage::seal);
                    }
                    assertThrows(IllegalStateException.class,storage::seal);
                } else if(mutation==0) {
                    storage.add(entry.entry().unit(),List.of(binding),List.of(inventory.get(3).id()),List.of(returned,returned));storage.seal();
                    var failure=assertThrows(IllegalArgumentException.class,storage::validateUnique);assertEquals("duplicate CFG transition",failure.getMessage());
                    assertThrows(IllegalStateException.class,storage::storedSize);
                } else {
                    var foreign=new PublicationId("foreign-flow");var foreignUnit=new UnitId(foreign,"caller");
                    var invalid=switch(mutation) {
                        case 1->new io.github.gustavo2358.analysis.cfg.domain.CfgTransition(new io.github.gustavo2358.analysis.cfg.domain.CfgNodeId(foreign,2),new io.github.gustavo2358.analysis.cfg.domain.CfgNodeId(foreign,1),binding.kind(),new EntryId(foreignUnit,"entry"));
                        case 2->new io.github.gustavo2358.analysis.cfg.domain.CfgTransition(new io.github.gustavo2358.analysis.cfg.domain.CfgNodeId(publication,99),binding.to(),binding.kind(),entry.entry());
                        case 3->new io.github.gustavo2358.analysis.cfg.domain.CfgTransition(inventory.getFirst().id(),binding.to(),binding.kind(),entry.entry());
                        default->new io.github.gustavo2358.analysis.cfg.domain.CfgTransition(binding.from(),binding.to(),binding.kind(),new EntryId(entry.entry().unit(),"absent-entry"));
                    };
                    Class<? extends RuntimeException> expected=mutation==2?IndexOutOfBoundsException.class:IllegalArgumentException.class;
                    assertThrows(expected,()->storage.add(entry.entry().unit(),List.of(invalid),List.of(inventory.get(3).id()),List.of(returned)));
                    assertThrows(IllegalStateException.class,storage::seal);
                }
            }
            for(var pool:AnalysisResources.Pool.values())assertEquals(0,ledger.used(pool),pool.toString());
        }
    }

    @Test void exportedCfgDescriptorsCanRetireBeforeDependenciesUseTheSameAir() {
        var ledger=resources();
        try(var pages=new FilePageStore(directory,512,16,ledger);
            var checked=SnapshotValidator.check(AirSnapshot.fromPublication(directCall()),ValidationOptions.defaults(),new PagedSnapshotValidationStorage(pages,ledger));
            var program=new SnapshotProgram(checked,new PagedSnapshotIdentityStorage(pages,ledger),new PagedSnapshotOrderStorage(pages,ledger))) {
            program.source();long before=ledger.heapUsed();var graph=CoreCfgProjection.project(program);
            assertTrue(ledger.heapUsed()>before);
            program.releaseCfgProjection(io.github.gustavo2358.analysis.cfg.domain.ProjectionPolicy.KNOWN_SUBSET);
            assertEquals(before,ledger.heapUsed());assertThrows(IllegalStateException.class,graph.nodes()::size);assertThrows(IllegalStateException.class,graph.entries()::getFirst);
            assertDoesNotThrow(()->program.releaseCfgProjection(io.github.gustavo2358.analysis.cfg.domain.ProjectionPolicy.KNOWN_SUBSET));
            var fresh=CoreCfgProjection.project(program);assertEquals(4,fresh.nodes().size());assertEquals("entry",fresh.entries().getFirst().entry().localId());
            program.releaseCfgProjection(io.github.gustavo2358.analysis.cfg.domain.ProjectionPolicy.KNOWN_SUBSET);
            assertEquals(before,ledger.heapUsed());
            var result=new SnapshotDependencyAnalysis().analyze(program,new PagedSnapshotDependencyStorage(pages,ledger));
            assertEquals("PROGA",result.sites().getFirst().candidates().getFirst().referenceName());
            assertEquals("seed",result.sites().getFirst().candidates().getFirst().supports().getFirst().producer().localId());
        }
        assertEquals(0,ledger.heapUsed());
        for(var pool:AnalysisResources.Pool.values())assertEquals(0,ledger.used(pool),pool.toString());
    }

    @Test void pagedHaltRoleKeepsItsActualOccurrenceAndExactEndpoint() {
        var base=directCall();var unit=base.units().getFirst();var end=unit.sequences().getLast();
        var halted=new Sequence(end.label(),List.of(),new Operations.Halt(ResultFixtures.header(unit.id(),"stop"),Operations.HaltKind.ABNORMAL),end.origin());
        var changed=ResultFixtures.unit(unit.id(),unit.entries(),List.of(unit.sequences().getFirst(),halted),unit.objects());
        var publication=new Publication(base.id(),base.airVersion(),base.capabilities(),base.artifacts(),List.of(changed),base.storage(),base.resources(),base.artifactRelations(),base.origins(),base.coverage(),base.uncertainties(),base.premises());
        var ledger=resources();
        try(var pages=new FilePageStore(directory,512,16,ledger);
            var checked=SnapshotValidator.check(AirSnapshot.fromPublication(publication),ValidationOptions.defaults(),new PagedSnapshotValidationStorage(pages,ledger));
            var program=new SnapshotProgram(checked,new PagedSnapshotIdentityStorage(pages,ledger),new PagedSnapshotOrderStorage(pages,ledger))) {
            assertEquals(ValidationResult.Status.STRUCTURALLY_VALID,checked.result().status());
            var graph=CoreCfgProjection.project(program);assertEquals(5,graph.nodes().size());assertEquals(1,graph.haltExits().size());
            var halt=graph.haltExits().getFirst();assertEquals(1,halt.id().ordinal());assertEquals("stop",halt.operation().localId());assertEquals(Operations.HaltKind.ABNORMAL,halt.haltKind());
            var edge=graph.transitions().stream().filter(row->row.kind()==io.github.gustavo2358.analysis.cfg.domain.CfgTransition.Kind.HALT).findFirst().orElseThrow();
            assertEquals(0,edge.from().ordinal());assertEquals(halt.id(),edge.to());assertEquals(unit.entries().getFirst().id(),edge.activationEntry());
            assertEquals(CoreCfgProjection.project(publication),graph);
        }
        assertEquals(0,ledger.heapUsed());
    }

    @Test void pagedOutsideOutcomeKeepsItsActualInvokeAndExactEndpoint() {
        var base=directCall();var unit=base.units().getFirst();var start=unit.sequences().getFirst();var invoke=(Operations.Invoke)start.terminator();
        var outside=new Operations.Invoke(invoke.header(),invoke.action(),invoke.target(),invoke.arguments(),invoke.results(),invoke.signature(),invoke.effectOperands(),invoke.effectBound(),
                new Control.InvocationOutcomes(List.of(invoke.outcomes().known().getFirst(),Control.HaltAlternative.INSTANCE),invoke.outcomes().remainder()),invoke.contract());
        var changed=ResultFixtures.unit(unit.id(),unit.entries(),List.of(new Sequence(start.label(),start.instructions(),outside,start.origin()),unit.sequences().getLast()),unit.objects());
        var publication=new Publication(base.id(),base.airVersion(),base.capabilities(),base.artifacts(),List.of(changed),base.storage(),base.resources(),base.artifactRelations(),base.origins(),base.coverage(),base.uncertainties(),base.premises());
        var ledger=resources();var coordinator=new CfgBuildCoordinator(SemanticInterpreterRegistry.empty());
        try(var pages=new FilePageStore(directory,512,16,ledger);
            var checked=SnapshotValidator.check(AirSnapshot.fromPublication(publication),ValidationOptions.defaults(),new PagedSnapshotValidationStorage(pages,ledger));
            var program=new SnapshotProgram(checked,new PagedSnapshotIdentityStorage(pages,ledger),new PagedSnapshotOrderStorage(pages,ledger))) {
            assertEquals(ValidationResult.Status.STRUCTURALLY_VALID,checked.result().status(),checked.result().toString());
            var built=coordinator.buildChecked(program,checked,BuildOptions.defaults());var graph=built.graph().orElseThrow();
            assertEquals(5,graph.nodes().size());var exit=assertInstanceOf(io.github.gustavo2358.analysis.cfg.domain.CfgNode.OutcomeExit.class,graph.nodes().get(2));
            assertEquals(invoke.header().id(),exit.operation());assertEquals(Control.HaltAlternative.INSTANCE,exit.outcome());
            var edge=graph.transitions().stream().filter(row->row.kind()==io.github.gustavo2358.analysis.cfg.domain.CfgTransition.Kind.CONTROL_EXIT).findFirst().orElseThrow();
            assertEquals(1,edge.from().ordinal());assertEquals(exit.id(),edge.to());assertEquals(unit.entries().getFirst().id(),edge.activationEntry());
            assertEquals(coordinator.build(publication,BuildOptions.defaults()).graph().orElseThrow(),graph);
        }
        assertEquals(0,ledger.heapUsed());
    }

    @Test void validatedPagedSnapshotPreservesVariableCallEvidenceWithoutPublicationMaterialization() {
        var publication=directCall();var ledger=resources();
        try(var pages=new MemoryPageStore(128,ledger);var source=AirSnapshot.fromPublication(publication);
            var builder=new AirSnapshotBuilder(new PagedAirStorage(pages,ledger,AnalysisResources.Phase.DECODE))) {
            long root=PagedAirStorageTest.copy(source,source.root(),null,builder);
            try(var checked=SnapshotValidator.check(builder.finish(root),ValidationOptions.defaults(),new PagedSnapshotValidationStorage(pages,ledger))) {
                assertEquals(ValidationResult.Status.STRUCTURALLY_VALID,checked.result().status(),checked.result().toString());
                ProgramStore.UnitView expiredUnit;
                ProgramStore.SequenceView expiredSequence;
                try(var snapshotProgram=new SnapshotProgram(checked,new PagedSnapshotIdentityStorage(pages,ledger),new PagedSnapshotOrderStorage(pages,ledger))) {
                    ProgramStore store=snapshotProgram;
                    assertEquals(publication.id(),store.publicationId());
                    assertEquals(Evidence.InventoryStatus.COMPLETE,store.inventory());
                    assertEquals(publication.artifacts(),store.artifacts());
                    assertEquals(publication.origins(),store.origins());
                    assertInstanceOf(ProgramStore.Structural.class,store);
                    var structural=(ProgramStore.Structural)store;
                    var borrowedUnit=structural.units().getFirst();
                    expiredUnit=borrowedUnit;
                    assertFalse(Unit.class.isInstance(borrowedUnit),"body remains a borrowed view, not a resident Unit");
                    var expectedUnit=publication.units().getFirst();
                    assertEquals(expectedUnit.id(),borrowedUnit.id());
                    assertEquals(expectedUnit.containingUnit(),borrowedUnit.containingUnit());
                    assertEquals(expectedUnit.objects(),borrowedUnit.objects());
                    assertEquals(expectedUnit.visibleObjects(),borrowedUnit.visibleObjects());
                    assertEquals(expectedUnit.entries(),borrowedUnit.entries());
                    assertEquals(expectedUnit.completionPorts(),borrowedUnit.completionPorts());
                    assertEquals(expectedUnit.body(),borrowedUnit.body());
                    assertEquals(expectedUnit.bodyUnavailable(),borrowedUnit.bodyUnavailable());
                    assertEquals(expectedUnit.coverage().scope(),borrowedUnit.coverage().scope());
                    assertEquals(expectedUnit.coverage().items(),borrowedUnit.coverage().items());
                    assertEquals(expectedUnit.coverage().uncertainties(),borrowedUnit.coverage().uncertainties());
                    assertEquals(expectedUnit.origin(),borrowedUnit.origin());
                    assertEquals(publication.storage(),structural.storage());
                    assertEquals(publication.resources(),structural.resources());
                    assertEquals(publication.uncertainties(),structural.uncertainties());
                    assertEquals(publication.premises(),structural.premises());
                    assertEquals(publication.capabilities(),structural.capabilities());
                    var borrowedSequence=borrowedUnit.sequences().getFirst();
                    expiredSequence=borrowedSequence;
                    assertFalse(Sequence.class.isInstance(borrowedSequence));
                    assertEquals(expectedUnit.sequences().getFirst().label(),borrowedSequence.label());
                    assertEquals(expectedUnit.sequences().getFirst().instructions(),borrowedSequence.instructions());
                    assertEquals(expectedUnit.sequences().getFirst().terminator(),borrowedSequence.terminator());
                    assertEquals(expectedUnit.sequences().getFirst().origin(),borrowedSequence.origin());
                    var first=borrowedSequence.instructions().getFirst();
                    var second=borrowedSequence.instructions().getFirst();
                    assertNotSame(first,second);assertEquals(first,second);
                    assertEquals(CoreCfgProjection.project(publication),CoreCfgProjection.project(snapshotProgram));
                    var built=new CfgBuildCoordinator(SemanticInterpreterRegistry.empty()).buildChecked(
                            snapshotProgram,checked,BuildOptions.defaults());
                    assertEquals(CfgBuildResult.Status.CFG_BUILT,built.status());
                    assertEquals(CoreCfgProjection.project(publication),built.graph().orElseThrow());
                }
                assertThrows(IllegalStateException.class,()->expiredSequence.instructions().getFirst());
                assertThrows(IllegalStateException.class,expiredUnit::entries);
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

    @Test void incompleteAdmissionCannotProduceDependencies() throws Exception {
        Publication publication;
        try(var input=getClass().getResourceAsStream("/ep/unproved-codec.air.json")) {
            publication=new AirJson().decodeForPartialAnalysis(java.util.Objects.requireNonNull(input).readAllBytes()).publication();
        }
        assertEquals(ValidationResult.Status.INCOMPLETE_VALIDATION,AirValidator.validate(publication).status(),
                "a genuinely undecided mandatory obligation, not an unsupported old snapshot slice");
        var ledger=resources();
        try(var pages=new MemoryPageStore(128,ledger);var snapshot=AirSnapshot.fromPublication(publication);
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

    @Test void snapshotReaderCloseFailureAfterAdmissionDoesNotLeakAnUnreturnedSession()throws Exception {
        var ledger=resources();byte[] raw=new AirJson().encode(correlatedCall());
        var staging=Files.createDirectory(directory.resolve("close-failure"));
        var failure=new java.io.IOException("synthetic stream close failure");int[] closes={0};
        var reader=new DataflowAirReader();
        var actual=assertThrows(java.io.IOException.class,()->reader.readSnapshot(staging.resolve("input.air.json"),ledger,staging,
                path->new java.io.ByteArrayInputStream(raw) {
                    @Override public void close()throws java.io.IOException {
                        closes[0]++;assertTrue(ledger.used(AnalysisResources.Pool.TEMPORARY)>0,"input must reach owned snapshot before close fails");
                        throw failure;
                    }
                }));
        assertSame(failure,actual);assertEquals(1,closes[0]);
        assertEquals(0,ledger.heapUsed(),"checked snapshot was never returned, so its owner must be released");
        for(var pool:AnalysisResources.Pool.values())assertEquals(0,ledger.used(pool),pool.toString());
        assertFalse(Files.exists(staging),"failed transfer must remove its exact temporary directory");
    }

    @Test void snapshotReaderPreservesPrimaryCodecFailureWhenInputCloseAlsoFails()throws Exception {
        var ledger=resources();var staging=Files.createDirectory(directory.resolve("codec-failure"));
        var closeFailure=new java.io.IOException("synthetic cleanup failure");int[] closes={0};
        var actual=assertThrows(io.github.gustavo2358.air.json.AirJsonException.class,()->new DataflowAirReader().readSnapshot(
                staging.resolve("input.air.json"),ledger,staging,path->new java.io.ByteArrayInputStream("{".getBytes(StandardCharsets.UTF_8)) {
                    @Override public void close()throws java.io.IOException{closes[0]++;throw closeFailure;}
                }));
        assertEquals(1,closes[0]);assertEquals(1,actual.getSuppressed().length);assertSame(closeFailure,actual.getSuppressed()[0]);
        assertEquals(0,ledger.heapUsed());
        for(var pool:AnalysisResources.Pool.values())assertEquals(0,ledger.used(pool),pool.toString());
        assertFalse(Files.exists(staging));
    }

    @Test void snapshotReaderTransfersOwnershipOnlyAfterInputCloseSucceeds()throws Exception {
        var ledger=resources();byte[] raw=new AirJson().encode(correlatedCall());
        var staging=Files.createDirectory(directory.resolve("successful-transfer"));int[] closes={0};
        var read=new DataflowAirReader().readSnapshot(staging.resolve("input.air.json"),ledger,staging,
                path->new java.io.ByteArrayInputStream(raw){@Override public void close(){closes[0]++;}});
        try(read) {
            assertEquals(1,closes[0]);assertTrue(ledger.heapUsed()>0);assertTrue(Files.exists(staging));
            assertEquals(ValidationResult.Status.STRUCTURALLY_VALID,read.checked().result().status());
            assertEquals(raw.length,read.airBytesObserved());assertEquals(1,read.airReads());
            assertEquals(java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(raw)),read.sha256());
            var result=new SnapshotDependencyAnalysis().analyze(read.checked(),read.newIdentityStorage(),read.newDependencyStorage());
            assertEquals(List.of("AX","BY"),result.sites().getFirst().candidates().stream().map(DirectDependencyResult.Candidate::referenceName).toList());
        }
        assertEquals(1,closes[0]);assertThrows(IllegalStateException.class,read::checked);assertDoesNotThrow(read::close);
        assertEquals(0,ledger.heapUsed());
        for(var pool:AnalysisResources.Pool.values())assertEquals(0,ledger.used(pool),pool.toString());
        assertFalse(Files.exists(staging));
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

    private static Publication directOrphans(int count) {
        var base=directCall();var unit=base.units().getFirst();var sequences=new java.util.ArrayList<>(unit.sequences());
        for(int i=0;i<count;i++) {
            String name=String.format("orphan-%04d",i);
            sequences.add(i%2==0?ResultFixtures.returning(unit.id(),name,List.of()):new Sequence(new LabelId(unit.id(),name),List.of(),new Operations.Halt(ResultFixtures.header(unit.id(),"halt-"+i),Operations.HaltKind.ABNORMAL),unit.origin()));
        }
        sequences.add(ResultFixtures.returning(unit.id(),"Aa",List.of()));sequences.add(ResultFixtures.returning(unit.id(),"BB",List.of()));
        sequences.add(ResultFixtures.returning(unit.id(),"long-"+"a".repeat(1030)+"😀",List.of()));
        java.util.Collections.reverse(sequences);
        var changed=ResultFixtures.unit(unit.id(),unit.entries(),sequences,unit.objects());
        return new Publication(base.id(),base.airVersion(),base.capabilities(),base.artifacts(),List.of(changed),base.storage(),base.resources(),base.artifactRelations(),base.origins(),base.coverage(),base.uncertainties(),base.premises());
    }

    private static Publication directCall() {
        return directCalls(1);
    }

    @Test void nativeGeneralPlannerPreservesAllUnitsColdOperationsAndCorrelatedCallFacts() throws Exception {
        for(int count:new int[]{1,4,16,64,256}) {
            var base=directCalls(count);var ledger=resources();
            // Written evidence is supported by the pinned wire; IDs/expected facts stay exact.
            var artifact=new ArtifactId(base.id(),"source");
            var publication=new Publication(base.id(),base.airVersion(),base.capabilities(),
                List.of(new Origins.Artifact(artifact,"native-general.synthetic",Optional.empty())),base.units(),base.storage(),
                base.resources(),base.artifactRelations(),List.of(new Origins.Written(ResultFixtures.origin(base.id()),artifact,Optional.empty(),List.of(),true)),
                base.coverage(),base.uncertainties(),base.premises());
            var input=directory.resolve("general-"+count+".air.json");Files.write(input,new AirJson().encode(publication));
            try(var read=new DataflowAirReader().readSnapshot(input,ledger);
                var program=new SnapshotProgram(read.checked(),read.newIdentityStorage(),read.newOrderStorage())) {
                var cfg=new CfgBuildCoordinator(SemanticInterpreterRegistry.empty()).buildChecked(program,read.checked(),BuildOptions.defaults());
                var result=new io.github.gustavo2358.analysis.dependencies.DependencyAnalysis().prepare(program,cfg);
                assertEquals(count,result.sites().size());assertEquals(count,result.edges().size());
                for(var site:result.sites()) {
                    assertEquals(io.github.gustavo2358.analysis.dependencies.DependencySiteFact.Reachability.REACHABLE,site.reachability());
                    assertEquals(List.of("PROGA"),site.candidates().stream().map(io.github.gustavo2358.analysis.dependencies.DependencySiteFact.Candidate::referenceName).toList());
                    assertEquals("PROGA   ",site.candidates().getFirst().rawValue());
                    var support=site.candidates().getFirst().supports().getFirst();
                    assertEquals(new OperationId(site.caller(),"seed"),support.producer());
                    assertEquals(ResultFixtures.origin(publication.id()),support.origin());
                }
                assertEquals(3L*count,result.metrics().get("indexedOperations"));
                assertEquals((long)count,result.metrics().get("entryCallCandidatesVisited"),"one Entry per Unit must not revisit other Units' CALL sites");
                System.out.println("NATIVE_GENERAL_GEOMETRY units="+count+" work="+ledger.workUsed()+" sites="+result.sites().size());
            }
            assertEquals(0,ledger.heapUsed());
        }
        var publication=correlatedCall();var ledger=resources();
        var input=directory.resolve("general-correlated.air.json");Files.write(input,new AirJson().encode(publication));
        try(var read=new DataflowAirReader().readSnapshot(input,ledger);
            var program=new SnapshotProgram(read.checked(),read.newIdentityStorage(),read.newOrderStorage())) {
            var cfg=new CfgBuildCoordinator(SemanticInterpreterRegistry.empty()).buildChecked(program,read.checked(),BuildOptions.defaults());
            var result=new io.github.gustavo2358.analysis.dependencies.DependencyAnalysis().prepare(program,cfg);
            var site=result.sites().getFirst();
            assertEquals(List.of("AX","BY"),site.candidates().stream().map(io.github.gustavo2358.analysis.dependencies.DependencySiteFact.Candidate::referenceName).toList());
            assertEquals(List.of(List.of("fit-concat","seed-A","seed-X"),List.of("fit-concat","seed-B","seed-Y")),site.candidates().stream()
                .map(candidate->candidate.supports().stream().map(support->support.producer().localId()).sorted().toList()).toList());
            assertFalse(site.candidates().stream().anyMatch(candidate->List.of("AY","BX").contains(candidate.referenceName())));
            var nativeOutput=new ByteArrayOutputStream();new DependencyJson().writeSnapshot(result,program,nativeOutput);
            var referenceOutput=new ByteArrayOutputStream();new DependencyJson().write(new io.github.gustavo2358.analysis.dependencies.DependencyAnalysis().prepare(publication),referenceOutput);
            var mapper=new com.fasterxml.jackson.databind.ObjectMapper();
            var actual=mapper.readTree(nativeOutput.toByteArray());var reference=mapper.readTree(referenceOutput.toByteArray());
            for(var expectedSite:reference.get("sites"))((com.fasterxml.jackson.databind.node.ObjectNode)expectedSite).put("coverage","MODELED");
            for(var field:List.of("sites","edges","origins","artifacts","sourceUncertaintyRefs","fileDependencies","sourceDependencies"))
                assertEquals(reference.get(field),actual.get(field),field);
        }
        assertEquals(0,ledger.heapUsed());
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
