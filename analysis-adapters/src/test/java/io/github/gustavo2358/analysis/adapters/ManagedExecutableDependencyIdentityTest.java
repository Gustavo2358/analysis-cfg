package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.air.validation.*;
import io.github.gustavo2358.analysis.cfg.application.*;
import io.github.gustavo2358.analysis.cfg.extension.SemanticInterpreterRegistry;
import io.github.gustavo2358.analysis.dependencies.*;
import io.github.gustavo2358.analysis.solver.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Full-key ordering/lookup against same local operation and colliding names. */
final class ManagedExecutableDependencyIdentityTest {
    @Test void collidingUnitAndEntryNamesUnicodeAndReversedInventoryKeepEveryFullSiteAndProducer()throws Exception {
        assertEquals("Aa".hashCode(),"BB".hashCode());
        var publication=new PublicationId("managed-key-law");var origin=ResultFixtures.origin(publication);
        var units=new ArrayList<Unit>();var storages=new ArrayList<Memory.Storage>();var names=List.of("Aa","BB","Ω","😀");
        for(int index=names.size()-1;index>=0;index--){
            var unit=new UnitId(publication,names.get(index));var cell=new StorageId(publication,"cell-"+index);var object=new ObjectId(unit,"target");
            var declaration=new Memory.ObjectDeclaration(object,Optional.of("TARGET"),new Types.Known(Types.Builtin.TEXT),new Memory.CellBinding(cell),Memory.Visibility.PRIVATE,origin,Evidence.CoverageStatus.MODELED,ResultFixtures.header(unit,"metadata").precision());
            storages.add(new Memory.Cell(new Memory.StorageHeader(cell,Optional.of(unit),Memory.Lifetime.ACTIVATION,Memory.Visibility.PRIVATE,origin),new Types.Known(Types.Builtin.TEXT)));
            var start=new Sequence(new LabelId(unit,"start"),List.of(ResultFixtures.assign(unit,"seed",object,"PG"+index+"   ")),W1dModelTest.call(unit,"invoke","end",object,false),origin);
            units.add(ResultFixtures.unit(unit,List.of(ResultFixtures.entry(unit,"BB","start"),ResultFixtures.entry(unit,"Aa","start")),List.of(ResultFixtures.returning(unit,"end",List.of()),start),List.of(declaration)));
        }
        var input=ResultFixtures.publication(publication,units,storages);
        var root=new AnalysisResources(new AnalysisResources.Limits(64_000_000,16_000_000,0,256_000_000,8,Long.MAX_VALUE,Long.MAX_VALUE));
        var directory=Files.createTempDirectory(Path.of("target"),"managed-fullkey-law-");
        try(var pages=new FilePageStore(directory,512,1,root);var original=AirSnapshot.fromPublication(input)){
            var copy=PagedAirStorageTest.class.getDeclaredMethod("copy",AirSnapshot.class,long.class,AirShape.class,AirSnapshotBuilder.class);copy.setAccessible(true);
            try(var builder=new AirSnapshotBuilder(new PagedAirStorage(pages,root,AnalysisResources.Phase.DECODE))){
                long head=(long)copy.invoke(null,original,original.root(),null,builder);
                try(var checked=SnapshotValidator.check(builder.finish(head),ValidationOptions.defaults(),new PagedSnapshotValidationStorage(pages,root));
                    var program=new SnapshotProgram(checked,new PagedSnapshotIdentityStorage(pages,root),new PagedSnapshotOrderStorage(pages,root),root)){
                    assertTrue(checked.result().isStructurallyValid(),checked.result().toString());
                    var cfg=new CfgBuildCoordinator(SemanticInterpreterRegistry.empty()).buildChecked(program,checked,BuildOptions.defaults());assertEquals(CfgBuildResult.Status.CFG_BUILT,cfg.status());
                    var execution=AnalysisResources.executionFor(program);var expected=new DependencyAnalysis().prepareLeased(program,cfg,pages,execution);
                    assertEquals(8,expected.sites().size());assertEquals(4,expected.programDependencies().size());
                    assertEquals(8,expected.edges().size(),"fixture target names must be inside the explicit COBOL name profile before exercising ownership");
                    try(var owner=new DependencyAnalysis().prepareManaged(program,cfg,pages,execution)){
                        assertEquals(8,owner.result().sites().size(),"FULL_SITE_KEY_NAMESPACE_COLLAPSED_RED");assertEquals(8,owner.result().edges().size());assertEquals(0,owner.fullFactProjections());
                        int at=0;
                        try(var cursor=owner.cursor()){while(cursor.advance()){
                            var fact=cursor.fact();int index=at/2;
                            assertEquals(names.get(index),fact.caller().localId());assertEquals(at%2==0?"Aa":"BB",fact.entry().localId());
                            assertEquals(new OperationId(fact.caller(),"invoke"),fact.operation());assertEquals("PG"+index,fact.candidates().getFirst().referenceName());assertEquals("PG"+index+"   ",fact.candidates().getFirst().rawValue());
                            assertEquals(new OperationId(fact.caller(),"seed"),fact.candidates().getFirst().supports().getFirst().producer());assertEquals(expected.sites().get(at++),fact);
                        }}assertEquals(8,at);
                        long before=execution.used(AnalysisResources.Pool.SCRATCH);
                        for(int i=0;i<4;i++){try(var projection=owner.projectProgram(i)){
                            var value=projection.resolution();assertSame(value,projection.resolution());assertTrue(execution.used(AnalysisResources.Pool.SCRATCH)>before+1024,"matching complete DTOs and resolution stay funded while consumed");
                            assertEquals(expected.programDependencies().get(i),value);assertEquals(2,value.executableSites().size());
                            for(var site:value.executableSites())assertEquals(value.occurrence().executableOperations().getFirst(),site.operation());
                        }assertEquals(before,execution.used(AnalysisResources.Pool.SCRATCH));}
                        assertEquals(16,owner.fullFactProjections(),"only full-key matches, not every unrelated cold fact");
                        var reference=new java.io.ByteArrayOutputStream();new DependencyJson().writeSnapshot(expected,program,reference);
                        var actual=new java.io.ByteArrayOutputStream();new DependencyJson().writeSnapshot(owner,program,execution,actual);assertArrayEquals(reference.toByteArray(),actual.toByteArray());
                    }
                }
            }
        }finally{for(var pool:AnalysisResources.Pool.values())assertEquals(0,root.used(pool),pool.toString());}
    }
}
