package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.air.validation.*;
import io.github.gustavo2358.analysis.cfg.application.*;
import io.github.gustavo2358.analysis.cfg.extension.SemanticInterpreterRegistry;
import io.github.gustavo2358.analysis.dependencies.*;
import io.github.gustavo2358.analysis.solver.*;
import io.github.gustavo2358.analysis.structure.*;
import io.github.gustavo2358.analysis.values.StorageAnalysisMode;
import java.nio.file.Path;
import java.math.BigInteger;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Admission cost law only; independent semantic choices precede structural bounds. */
final class NativeMixedComputedAdmissionCostTest {
    @org.junit.jupiter.api.io.TempDir Path directory;
    private static final class ObservedSource implements AirSnapshot.Source {
        private final AirSnapshot.Source source;
        private boolean measuring;
        private long objectIdentityReads;
        private long assignHeaderReads,invokeHeaderReads;
        private final Set<Long> objects = new HashSet<>();
        ObservedSource(AirSnapshot.Source source) { this.source=source; }
        public AirShape shape(long handle) { return source.shape(handle); }
        public long length(long handle) { return source.length(handle); }
        public long child(long handle,long ordinal) {
            if(measuring&&ordinal==0) {
                var shape=source.shape(handle);
                if(shape==AirShape.OPERATIONS_ASSIGN)assignHeaderReads=Math.incrementExact(assignHeaderReads);
                if(shape==AirShape.OPERATIONS_INVOKE)invokeHeaderReads=Math.incrementExact(invokeHeaderReads);
            }
            if(measuring&&ordinal==0&&source.shape(handle)==AirShape.MEMORY_OBJECT_DECLARATION) {
                objectIdentityReads=Math.incrementExact(objectIdentityReads);objects.add(handle);
            }
            return source.child(handle,ordinal);
        }
        public long scalar(long handle) { return source.scalar(handle); }
        public int characters(long handle,long offset,char[] destination,int start,int count) { return source.characters(handle,offset,destination,start,count); }
        public AirSnapshot.Elements elements(long handle) { return source.elements(handle); }
        public void close() { source.close(); }
    }
    @org.junit.jupiter.api.Test
    void unsupportedStorageDoesNotMultiplyAcceptedScalarPreparationAcrossFortyControls() throws Exception {
        Path temporary=directory;
        var violations=new ArrayList<String>();
        for(boolean file:new boolean[]{false,true})for(String family:new String[]{"objects","subjects","both","repeated"})
            for(int n:new int[]{1,2,4,8,16}) {
                int declarations=family.equals("objects")||family.equals("both")?n:16;
                int calls=family.equals("objects")?16:n;
                int subjects=family.equals("objects")||family.equals("repeated")?1:Math.min(calls,declarations);
                one(temporary,file,family,declarations,calls,subjects,n%4==0,violations);
            }
        System.out.println("MIXED_ADMISSION_COST_CONTROLS_CLOSED total=40 violations="+violations.size());
        assertTrue(violations.isEmpty(),"MIXED_SCALAR_ADMISSION_RESCANS_PER_ACCEPTED_SUBJECT_RED "+violations);
    }
    private static void one(Path temporary,boolean file,String family,int declarations,int calls,int subjects,boolean reverse,List<String> violations) throws Exception {
        var original=W1dModelTest.model(declarations+1,unit->{
            var instructions=new ArrayList<Instruction>();
            for(int i=0;i<declarations;i++)instructions.add(ResultFixtures.assign(unit,"seed-"+i,new ObjectId(unit,"object-"+i),"PROGA   "));
            var sequences=new ArrayList<Sequence>();
            for(int i=0;i<calls;i++)sequences.add(new Sequence(new LabelId(unit,i==0?"start":"call-sequence-"+i),
                i==0?instructions:List.of(),W1dModelTest.call(unit,"call-"+i,i+1==calls?"bad-call-sequence":"call-sequence-"+(i+1),new ObjectId(unit,"object-"+(i%subjects)),false),ResultFixtures.origin(unit.publication())));
            sequences.add(new Sequence(new LabelId(unit,"bad-call-sequence"),List.of(),W1dModelTest.call(unit,"bad-call","end",new ObjectId(unit,"object-"+declarations),false),ResultFixtures.origin(unit.publication())));
            sequences.add(ResultFixtures.returning(unit,"end",List.of()));
            if(reverse)Collections.reverse(sequences);
            return sequences;
        });
        var originalUnit=original.units().getFirst();var objects=new ArrayList<>(originalUnit.objects());var bad=objects.getLast();
        var region=new StorageId(original.id(),"mixed-cost-view-region");
        var codec=new Memory.ExtensionCodec("text.ebcdic.ibm1047","1",Types.known(Types.Builtin.TEXT));
        objects.set(declarations,new Memory.ObjectDeclaration(bad.id(),bad.displayName(),bad.typeRef(),new Memory.ViewBinding(region,BigInteger.ZERO,BigInteger.valueOf(8),codec),bad.visibility(),bad.origin(),bad.coverage(),bad.precision()));
        var storage=new ArrayList<>(original.storage());
        storage.add(new Memory.Region(new Memory.StorageHeader(region,Optional.of(originalUnit.id()),Memory.Lifetime.ACTIVATION,Memory.Visibility.PRIVATE,ResultFixtures.origin(original.id())),Optional.of(BigInteger.valueOf(8)),Optional.empty()));
        var premises=new ArrayList<>(original.premises());
        premises.add(new Proofs.Premise(new PremiseId(original.id(),"mixed-cost-cell-region-disjoint"),"test-model","independent declared Cells and Region",ResultFixtures.origin(original.id()),new Proofs.DisjointStorage(storage.stream().map(value->value.header().id()).toList())));
        var publication=new Publication(original.id(),original.airVersion(),new Capabilities.Manifest(List.of(Capabilities.MEMORY_REGIONS,Capabilities.IBM1047),List.of()),original.artifacts(),List.of(ResultFixtures.unit(originalUnit.id(),originalUnit.entries(),originalUnit.sequences(),objects)),storage,original.resources(),original.artifactRelations(),original.origins(),original.coverage(),original.uncertainties(),premises);
        var resources=new AnalysisResources(new AnalysisResources.Limits(64L*1024*1024,16L*1024*1024,0,256L*1024*1024,8,Long.MAX_VALUE,Long.MAX_VALUE));
        var expected=new HashMap<CallDependencyPlan.SiteKey,CallDependencyPlan.Provider>();
        var unit=publication.units().getFirst();var entry=unit.entries().getFirst().id();
        for(int i=0;i<calls;i++)expected.put(new CallDependencyPlan.SiteKey(entry,new OperationId(unit.id(),"call-"+i)),CallDependencyPlan.Provider.SCALAR);
        expected.put(new CallDependencyPlan.SiteKey(entry,new OperationId(unit.id(),"bad-call")),CallDependencyPlan.Provider.REGIONAL);
        long started=System.nanoTime();
        try(PageStore pages=file?new FilePageStore(temporary,512,1,resources):new MemoryPageStore(512,resources);
                var snapshot=AirSnapshot.fromPublication(publication)) {
            var field=AirSnapshot.class.getDeclaredField("source");field.setAccessible(true);
            var observed=new ObservedSource((AirSnapshot.Source)field.get(snapshot));
            try(var checked=SnapshotValidator.check(AirSnapshot.attach(observed,snapshot.root()),ValidationOptions.defaults(),new PagedSnapshotValidationStorage(pages,resources));
                    var program=new SnapshotProgram(checked,new PagedSnapshotIdentityStorage(pages,resources),new PagedSnapshotOrderStorage(pages,resources),resources)) {
                assertTrue(checked.result().isStructurallyValid(),checked.result().toString());
                var cfg=new CfgBuildCoordinator(SemanticInterpreterRegistry.empty()).buildChecked(program,checked,BuildOptions.defaults());
                assertEquals(CfgBuildResult.Status.CFG_BUILT,cfg.status());
                var opened=AnalysisSession.open(cfg,program,cfg.options().projectionPolicy(),List.of(entry));
                assertEquals(AnalysisSession.Status.ACCEPTED,opened.status());
                observed.measuring=true;
                var selection=CallDependencyPlan.choose(opened.session().orElseThrow(),"cost",false,StorageAnalysisMode.LOGICAL_ONLY);
                observed.measuring=false;
                assertEquals(expected,selection.providers(),"manual full Entry/Operation admission decisions");
                assertEquals((long)subjects+1,selection.metrics().get("scalarAdmissionAttempts"));
                assertEquals((long)subjects,selection.metrics().get("scalarAdmissionAccepted"));
                assertEquals(1L,selection.metrics().get("scalarAdmissionRefused"));
                assertEquals(2,selection.registrations().size(),"accepted scalar demands and refused regional demand keep separate registrations");
                assertNotNull(program.admission().snapshot());
                System.out.println("COMPUTED_ADMISSION_BODY_HEADERS family="+family+" D="+declarations+" Q="+calls+" subjects="+subjects+" assignHeaders="+observed.assignHeaderReads+" invokeHeaders="+observed.invokeHeaderReads);
                if(observed.assignHeaderReads>2L*declarations)violations.add((file?"file1":"memory")+":"+family+":D"+declarations+":Q"+(calls+1)+":S"+subjects+":Assign"+observed.assignHeaderReads+">"+(2L*declarations));
                if(observed.invokeHeaderReads>5L*(calls+1))violations.add((file?"file1":"memory")+":"+family+":D"+declarations+":Q"+(calls+1)+":S"+subjects+":Invoke"+observed.invokeHeaderReads+">"+(5L*(calls+1)));
                System.out.println("COMPUTED_ADMISSION_COST {\"backend\":\""+(file?"file1":"memory")+"\",\"family\":\""+family+"\",\"declarations\":"+declarations+",\"calls\":"+calls+",\"distinctSubjects\":"+subjects+",\"reverse\":"+reverse+",\"objectIdentityChildReads\":"+observed.objectIdentityReads+",\"distinctDeclarationHandlesRead\":"+observed.objects.size()+",\"chargedWork\":"+resources.workUsed()+",\"heapPeakBytes\":"+resources.heapPeak()+",\"throughSelectionNanos\":"+(System.nanoTime()-started)+"}");
            }
        } finally {
            for(var pool:AnalysisResources.Pool.values())assertEquals(0,resources.used(pool),pool.toString());
        }
    }
}

