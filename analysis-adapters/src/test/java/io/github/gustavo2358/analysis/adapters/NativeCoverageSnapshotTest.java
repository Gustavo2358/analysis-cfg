package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.air.validation.*;
import io.github.gustavo2358.analysis.dependencies.*;
import io.github.gustavo2358.analysis.solver.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Native scalar output must borrow admitted headers, not reconstruct unrelated bodies. */
final class NativeCoverageSnapshotTest {
    @TempDir Path directory;

    @Test void fullCollidingIdentitiesKeepAllFourOriginalHeaderStatusesAndExpiredOwnersReject() throws Exception {
        var publicationId=new PublicationId("coverage-full-identities");var origin=ResultFixtures.origin(publicationId);
        var units=new ArrayList<io.github.gustavo2358.air.model.Unit>();var storages=new ArrayList<Memory.Storage>();
        var uncertainties=new ArrayList<Evidence.Uncertainty>();var expected=new LinkedHashMap<OperationId,String>();
        var statuses=List.of(Evidence.CoverageStatus.MODELED,Evidence.CoverageStatus.ABSTRACTED,Evidence.CoverageStatus.UNSUPPORTED,Evidence.CoverageStatus.INPUT_MISSING);
        for(int owner=0;owner<2;owner++) {
            var unit=new UnitId(publicationId,owner==0?"Aa":"BB");var object=new ObjectId(unit,"object-0");var cell=new StorageId(publicationId,"cell-"+owner);
            var gap=new UncertaintyId(publicationId,"gap-"+owner);
            uncertainties.add(new Evidence.Uncertainty(gap,"MODEL_GAP",List.of(Evidence.Dimension.DEPENDENCIES),new Scopes.UnitScope(unit),"synthetic header coverage",origin));
            var sequences=new ArrayList<Sequence>();
            for(int local=0;local<2;local++) {
                String name=local==0?"Aa":"BB";var call=W1dModelTest.call(unit,name,"end",object,false);var h=call.header();var status=statuses.get(2*owner+local);
                var header=new Operations.Header(h.id(),h.origin(),status,h.precision(),status==Evidence.CoverageStatus.MODELED?List.of():List.of(gap));
                var replacement=new Operations.Invoke(header,call.action(),call.target(),call.arguments(),call.results(),call.signature(),call.effectOperands(),call.effectBound(),call.outcomes(),call.contract());
                sequences.add(new Sequence(new LabelId(unit,local==0?"start":"cold"),List.of(),replacement,origin));expected.put(header.id(),status.name());
            }
            sequences.add(ResultFixtures.returning(unit,"end",List.of()));Collections.reverse(sequences);
            var declaration=new Memory.ObjectDeclaration(object,Optional.empty(),Types.known(Types.Builtin.TEXT),new Memory.CellBinding(cell),Memory.Visibility.PRIVATE,origin,Evidence.CoverageStatus.MODELED,ResultFixtures.header(unit,"meta").precision());
            storages.add(new Memory.Cell(new Memory.StorageHeader(cell,Optional.of(unit),Memory.Lifetime.ACTIVATION,Memory.Visibility.PRIVATE,origin),Types.known(Types.Builtin.TEXT)));
            units.add(ResultFixtures.unit(unit,List.of(ResultFixtures.entry(unit,"entry","start")),sequences,List.of(declaration)));
        }
        assertEquals(new OperationId(units.getFirst().id(),"Aa").hashCode(),new OperationId(units.getFirst().id(),"BB").hashCode());
        var publication=ResultFixtures.publication(publicationId,units,storages);
        publication=new Publication(publication.id(),publication.airVersion(),publication.capabilities(),publication.artifacts(),publication.units(),publication.storage(),publication.resources(),publication.artifactRelations(),publication.origins(),publication.coverage(),uncertainties,publication.premises());
        var resources=new AnalysisResources(new AnalysisResources.Limits(64L*1024*1024,16L*1024*1024,0,512L*1024*1024,8,Long.MAX_VALUE,0));
        SnapshotProgram expired;
        try(var pages=new FilePageStore(directory,128,1,resources);
            var checked=SnapshotValidator.check(AirSnapshot.fromPublication(publication),ValidationOptions.defaults(),new PagedSnapshotValidationStorage(pages,resources));
            var program=new SnapshotProgram(checked,new PagedSnapshotIdentityStorage(pages,resources),new PagedSnapshotOrderStorage(pages,resources),resources)) {
            assertTrue(checked.result().isStructurallyValid(),checked.result().toString());expired=program;
            var sites=new ArrayList<DependencySiteFact>();
            for(var entry:expected.entrySet()) {
                assertEquals(entry.getValue(),program.invokeCoverage(entry.getKey()).orElseThrow().name());
                sites.add(SourceTargetPlanTest.site(publication,entry.getKey(),false,true,false));
            }
            assertTrue(program.invokeCoverage(new OperationId(units.getFirst().id(),"absent")).isEmpty());
            assertTrue(program.invokeCoverage(new OperationId(units.getFirst().id(),"return-end")).isEmpty());
            assertTrue(program.invokeCoverage(new OperationId(new UnitId(new PublicationId("foreign"),"Aa"),"Aa")).isEmpty());
            Collections.reverse(sites);var bytes=new ByteArrayOutputStream();new DependencyJson().writeSnapshot(SourceTargetPlanTest.result(publication,sites),program,bytes);
            var json=new ObjectMapper().readTree(bytes.toByteArray());assertEquals(4,json.path("sites").size());
            for(var site:json.path("sites")) {
                var id=site.path("operation");var full=new OperationId(new UnitId(publicationId,id.path("unit").asText()),id.path("localId").asText());
                assertEquals(expected.get(full),site.path("coverage").asText(),id.toString());
            }
        }
        assertThrows(IllegalStateException.class,()->expired.invokeCoverage(expected.keySet().iterator().next()));
        for(var pool:AnalysisResources.Pool.values())assertEquals(0,resources.used(pool));
    }

    @Test void coverageReadFailurePreservesPrimaryRetiresEveryDirectoryTapeAndBorrowsInput() throws Exception {
        var publication=SourceCorrelationSnapshotTest.program(1);var operation=publication.units().getFirst().sequences().getFirst().terminator().header().id();
        var resources=new AnalysisResources(new AnalysisResources.Limits(64L*1024*1024,16L*1024*1024,0,512L*1024*1024,8,Long.MAX_VALUE,0));
        var primary=new IllegalStateException("injected native coverage operation-handle read");boolean[] armed={false};int[] opened={0},closed={0};
        try(var pages=new FilePageStore(directory,128,1,resources);
            var checked=SnapshotValidator.check(AirSnapshot.fromPublication(publication),ValidationOptions.defaults(),new PagedSnapshotValidationStorage(pages,resources))) {
            var delegate=new PagedSnapshotOrderStorage(pages,resources);
            var storage=new SnapshotOrderStorage(){
                @Override public Index open(Order order){return delegate.open(order);}
                @Override public Tape tape(){int number=++opened[0];var tape=delegate.tape();return new Tape(){
                    private boolean ended;
                    @Override public void append(long handle){tape.append(handle);}
                    @Override public long size(){return tape.size();}
                    @Override public long handle(long ordinal){long result=tape.handle(ordinal);if(armed[0]&&number==1)throw primary;return result;}
                    @Override public void close(){if(ended)return;ended=true;tape.close();closed[0]++;}
                };}
                @Override public void close(){delegate.close();}
            };
            try(var program=new SnapshotProgram(checked,new PagedSnapshotIdentityStorage(pages,resources),storage,resources)) {
                var catalogue=program.operationDirectory().orElseThrow();assertEquals(3,opened[0]);armed[0]=true;
                assertSame(primary,assertThrows(IllegalStateException.class,()->program.invokeCoverage(operation)));
                assertEquals(opened[0],closed[0]);assertThrows(IllegalStateException.class,catalogue::size);
                assertEquals(publication.id(),program.publicationId());assertEquals(AirShape.PUBLICATION,checked.snapshot().shape(checked.snapshot().root()));
                long page=pages.allocate();pages.release(page);assertEquals(3,closed[0]);
            }
        }
        for(var pool:AnalysisResources.Pool.values())assertEquals(0,resources.used(pool));
    }

    @Test void scalarCoverageNeverReprojectsAnAdmittedColdInvokeBody() throws Exception {
        var original=SourceCorrelationSnapshotTest.program(1);var body=original.units().getFirst();
        var sequences=new ArrayList<Sequence>();Operations.Invoke marked=null;
        for(var sequence:body.sequences()) {
            if(sequence.terminator() instanceof Operations.Invoke call) {
                marked=new Operations.Invoke(call.header(),call.action(),call.target(),call.arguments(),call.results(),call.signature(),call.effectOperands(),call.effectBound(),call.outcomes(),
                    new Interactions.KnownContract(new Interactions.ContractRef("x".repeat(65536),"1",List.of(body.origin()))));
                sequences.add(new Sequence(sequence.label(),sequence.instructions(),marked,sequence.origin()));
            } else sequences.add(sequence);
        }
        assertNotNull(marked);var header=marked.header();
        var unit=new io.github.gustavo2358.air.model.Unit(body.id(),body.containingUnit(),body.objects(),body.visibleObjects(),body.entries(),sequences,body.completionPorts(),body.body(),body.bodyUnavailable(),body.coverage(),body.origin());
        var publication=new Publication(original.id(),original.airVersion(),original.capabilities(),original.artifacts(),List.of(unit),original.storage(),original.resources(),original.artifactRelations(),original.origins(),original.coverage(),original.uncertainties(),original.premises());
        var resources=new AnalysisResources(new AnalysisResources.Limits(64L*1024*1024,16L*1024*1024,0,512L*1024*1024,8,Long.MAX_VALUE,0));
        try(var pages=new FilePageStore(directory,4096,4,resources,AnalysisResources.Phase.INDEX)) {
            var storage=new ColdBodyStorage(new PagedAirStorage(pages,resources,AnalysisResources.Phase.DECODE));
            SnapshotValidator.CheckedSnapshot checked;
            try(var source=AirSnapshot.fromPublication(publication);var builder=new AirSnapshotBuilder(storage)) {
                long root=PagedAirStorageTest.copy(source,source.root(),null,builder);
                checked=SnapshotValidator.check(builder.finish(root),ValidationOptions.defaults(),new PagedSnapshotValidationStorage(pages,resources));
                assertTrue(checked.result().isStructurallyValid(),checked.result().toString());
            }
            try(checked;var program=new SnapshotProgram(checked,new PagedSnapshotIdentityStorage(pages,resources),new PagedSnapshotOrderStorage(pages,resources),resources)) {
                program.operationDirectory().orElseThrow();
                var site=SourceTargetPlanTest.site(publication,header.id(),false,true,false);
                var result=SourceTargetPlanTest.result(publication,List.of(site));
                assertTrue(storage.firstCold>=0&&storage.lastCold-storage.firstCold>=16383);
                storage.armed=true;var bytes=new ByteArrayOutputStream();
                new DependencyJson().writeSnapshot(result,program,bytes);
                assertEquals(0,storage.coldReads,"coverage lookup must not reread cold Invoke contract text");
                var product=new ObjectMapper().readTree(bytes.toByteArray());
                assertEquals("3.0.0",product.path("version").asText());assertEquals(1,product.path("sites").size());
                assertEquals("MODELED",product.path("sites").get(0).path("coverage").asText());
                assertEquals(header.id().localId(),product.path("sites").get(0).path("operation").path("localId").asText());
                assertFalse(bytes.toString(java.nio.charset.StandardCharsets.UTF_8).contains("x".repeat(128)));
                storage.armed=false;
            }
            assertEquals(0,pages.statistics().livePages());
        }
        for(var pool:AnalysisResources.Pool.values())assertEquals(0,resources.used(pool));
    }
    private static final class ColdBodyStorage implements AirSnapshotBuilder.Storage {
        private final AirSnapshotBuilder.Storage delegate;
        private static final long COLD_WORD=0x0078007800780078L;
        long firstCold=-1,lastCold=-1,coldReads;boolean armed;
        ColdBodyStorage(AirSnapshotBuilder.Storage delegate){this.delegate=delegate;}
        @Override public long get(AirSnapshotBuilder.Column column,long index) {
            if(armed&&column==AirSnapshotBuilder.Column.CHARACTERS&&index>=firstCold&&index<=lastCold){coldReads++;throw new AssertionError("NATIVE_COVERAGE_COLD_INVOKE_BODY_READ_RED: scalar header output projected unrelated complete contract text");}
            return delegate.get(column,index);
        }
        @Override public void set(AirSnapshotBuilder.Column column,long index,long value) {
            delegate.set(column,index,value);
            if(column==AirSnapshotBuilder.Column.CHARACTERS&&value==COLD_WORD){if(firstCold<0)firstCold=index;lastCold=index;}
        }
        @Override public AirSnapshotBuilder.Lease claim(long bytes){return delegate.claim(bytes);}
        @Override public AirSnapshotBuilder.Lease readLease(long bytes){return delegate.readLease(bytes);}
        @Override public void freeze(){delegate.freeze();}
        @Override public void close(){delegate.close();}
    }
}
