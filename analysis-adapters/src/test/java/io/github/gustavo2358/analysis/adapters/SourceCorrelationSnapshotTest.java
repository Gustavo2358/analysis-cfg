package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.air.validation.*;
import io.github.gustavo2358.analysis.dependencies.*;
import io.github.gustavo2358.analysis.dependencies.source.QualifiedSourceDependencies;
import io.github.gustavo2358.analysis.solver.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Full owner/identity/ancestry/canonical-correlation laws against the resident public port. */
final class SourceCorrelationSnapshotTest {
    @TempDir Path directory;
    private static final QualifiedSourceJson CODEC=new QualifiedSourceJson();
    private static void zero(AnalysisResources resources){for(var pool:AnalysisResources.Pool.values())assertEquals(0,resources.used(pool));}
    private static void expire(SnapshotProgram program){program.close();}
    QualifiedSourceDependencies source(Publication publication)throws Exception {
        var q=CODEC.decode(Files.readAllBytes(Path.of("src/test/resources/qualified-source-r9/computed.source.json")));
        return new QualifiedSourceDependencies(q.schema(),q.version(),q.producer(),q.source(),List.of(new QualifiedSourceDependencies.AirCorrelation(publication.id().localId(),"0".repeat(64))),q.units());
    }
    static Publication program(int n) {
        return W1dModelTest.model(1,u->{var sequences=new ArrayList<Sequence>();for(int i=n-1;i>=0;i--)sequences.add(new Sequence(new LabelId(u,i==0?"start":"s"+i),List.of(),W1dModelTest.call(u,"call-"+i,"end",new ObjectId(u,"object-0"),false),ResultFixtures.origin(u.publication())));sequences.add(ResultFixtures.returning(u,"end",List.of()));return sequences;});
    }
    static List<DependencyInput.StatementCorrelation> links(Publication p,QualifiedSourceDependencies q) {
        var result=new ArrayList<DependencyInput.StatementCorrelation>();var statement=q.units().getFirst().occurrences().getFirst().id();
        for(var sequence:p.units().getFirst().sequences())if(sequence.terminator() instanceof Operations.Invoke invoke)result.add(new DependencyInput.StatementCorrelation(statement,invoke.header().id(),sequence.label(),invoke.header().origin()));return result;
    }
    final class NativeCase implements AutoCloseable {
        final AnalysisResources resources,execution;final SnapshotValidator.CheckedSnapshot checked;final SnapshotProgram program;final FilePageStore airPages,pages;final PageStore backend;final SourceAdmissionSnapshot source;
        NativeCase(Publication publication,QualifiedSourceDependencies evidence)throws Exception {
            this(publication,evidence,false);
        }
        NativeCase(Publication publication,QualifiedSourceDependencies evidence,boolean faults)throws Exception {
            this(publication,evidence,faults,0);
        }
        NativeCase(Publication publication,QualifiedSourceDependencies evidence,boolean faults,long outputBytes)throws Exception {
            resources=new AnalysisResources(new AnalysisResources.Limits(64L*1024*1024,16L*1024*1024,0,512L*1024*1024,8,Long.MAX_VALUE,outputBytes));
            // The resident fixture intentionally includes Origins.Unavailable,
            // outside the JSON binding profile. Copy its exact official typed
            // shapes into the managed AIR store, not a changed origin fixture.
            airPages=new FilePageStore(directory,4096,4,resources,AnalysisResources.Phase.INDEX);
            try(var original=AirSnapshot.fromPublication(publication);var builder=new AirSnapshotBuilder(new PagedAirStorage(airPages,resources,AnalysisResources.Phase.DECODE))) {
                long root=PagedAirStorageTest.copy(original,original.root(),null,builder);
                checked=SnapshotValidator.check(builder.finish(root),ValidationOptions.defaults(),new PagedSnapshotValidationStorage(airPages,resources));
                assertTrue(checked.result().isStructurallyValid(),checked.result().toString());
            }
            program=new SnapshotProgram(checked,new PagedSnapshotIdentityStorage(airPages,resources),new PagedSnapshotOrderStorage(airPages,resources),resources);execution=AnalysisResources.executionFor(program);pages=new FilePageStore(directory,4096,4,resources,AnalysisResources.Phase.INDEX);
            backend=faults?new FaultPages(pages):pages;
            source=SourceAdmissionSnapshot.admit(CODEC.decodeSnapshot(new ByteArrayInputStream(CODEC.encode(evidence)),backend,execution),backend,execution);
        }
        @Override public void close(){try{source.close();}finally{try{program.close();}finally{try{checked.close();}finally{try{pages.close();}finally{airPages.close();zero(execution);zero(resources);}}}}}
    }
    @Test void allSortedExecutableCorrelationsAndReverseRegistrationMatchIndependentResidentPort()throws Exception {
        for(int n:new int[]{1,4,16,64,256,1024}) {
            var publication=program(n);var evidence=source(publication);var links=links(publication,evidence);var expected=new DependencyInput(publication,Optional.of(evidence),links).occurrences().getFirst().executableOperations();
            try(var input=new NativeCase(publication,evidence)) {
                long before=input.pages.statistics().livePages();try(var prepared=SourceCorrelationSnapshot.prepare(input.source,input.program,links,input.pages,input.execution)) {
                    assertSame(input.source,prepared.admission());assertSame(input.program,prepared.program());assertEquals(n,prepared.operationCount(0,0));
                    for(int i=0;i<n;i++)assertEquals(expected.get(i),prepared.operation(0,0,i));for(var operation:expected)assertTrue(prepared.mapped(operation));
                }assertEquals(before,input.pages.statistics().livePages());Collections.reverse(links);
                try(var prepared=SourceCorrelationSnapshot.prepare(input.source,input.program,links,input.pages,input.execution)){for(int i=0;i<n;i++)assertEquals(expected.get(i),prepared.operation(0,0,i));}
                assertEquals(before,input.pages.statistics().livePages());
            }
        }
    }
    @Test void emptyAndPartialCorrelationsNeverInferExecutableAuthorityFromSourceNames()throws Exception {
        var publication=program(4);var evidence=source(publication);var links=links(publication,evidence);
        try(var input=new NativeCase(publication,evidence)) {
            try(var prepared=SourceCorrelationSnapshot.prepare(input.source,input.program,List.of(),input.pages,input.execution)){assertEquals(0,prepared.operationCount(0,0));for(var link:links)assertFalse(prepared.mapped(link.operation()));}
            try(var prepared=SourceCorrelationSnapshot.prepare(input.source,input.program,links.subList(0,3),input.pages,input.execution)){assertEquals(3,prepared.operationCount(0,0));assertFalse(prepared.mapped(links.getLast().operation()));}
        }
    }
    @Test void duplicateForeignOperationLabelOriginAndFullSourceOwnerRejectBeforeFactsEscape()throws Exception {
        var publication=program(1);var evidence=source(publication);var link=links(publication,evidence).getFirst();var statement=link.source();var unit=statement.unit();
        var foreignSource=new QualifiedSourceDependencies.StatementId(new QualifiedSourceDependencies.UnitId(unit.compilationUnitId()+"foreign",unit.structuralPath(),unit.canonicalProgramName()),statement.handle());
        for(var bad:List.of(List.of(link,link),List.of(new DependencyInput.StatementCorrelation(foreignSource,link.operation(),link.label(),link.origin())),List.of(new DependencyInput.StatementCorrelation(statement,new OperationId(link.operation().unit(),"absent"),link.label(),link.origin())),List.of(new DependencyInput.StatementCorrelation(statement,link.operation(),new LabelId(link.label().unit(),"absent"),link.origin())),List.of(new DependencyInput.StatementCorrelation(statement,link.operation(),link.label(),new OriginId(publication.id(),"absent"))))) {
            assertThrows(IllegalArgumentException.class,()->new DependencyInput(publication,Optional.of(evidence),bad));
            try(var input=new NativeCase(publication,evidence)){long before=input.pages.statistics().livePages();assertThrows(IllegalArgumentException.class,()->SourceCorrelationSnapshot.prepare(input.source,input.program,bad,input.pages,input.execution));assertEquals(before,input.pages.statistics().livePages());assertTrue(input.source.unitCount()>0);assertEquals(publication.id(),input.program.publicationId());}
        }
    }
    @Test void deepSharedDerivedOriginIsExactAndIndependentSiblingCannotAuthorizeAnySite()throws Exception {
        int depth=1024,n=256;var base=program(n);var origins=new ArrayList<>(base.origins());var anchor=ResultFixtures.origin(base.id());var previous=anchor;
        for(int i=0;i<depth;i++){var id=new OriginId(base.id(),"derived-"+i);origins.add(new Origins.Derived(id,List.of(previous),"independent-step"));previous=id;}
        var sibling=new OriginId(base.id(),"independent-sibling");origins.add(new Origins.Unavailable(sibling,"independent negative"));var unit=base.units().getFirst();var sequences=new ArrayList<Sequence>();
        for(var sequence:unit.sequences())if(sequence.terminator() instanceof Operations.Invoke invoke){var h=invoke.header();var header=new Operations.Header(h.id(),previous,h.coverage(),h.precision(),h.uncertainties());var changed=new Operations.Invoke(header,invoke.action(),invoke.target(),invoke.arguments(),invoke.results(),invoke.signature(),invoke.effectOperands(),invoke.effectBound(),invoke.outcomes(),invoke.contract());sequences.add(new Sequence(sequence.label(),sequence.instructions(),changed,previous));}else sequences.add(sequence);
        var changedUnit=new io.github.gustavo2358.air.model.Unit(unit.id(),unit.containingUnit(),unit.objects(),unit.visibleObjects(),unit.entries(),sequences,unit.completionPorts(),unit.body(),unit.bodyUnavailable(),unit.coverage(),unit.origin());
        var publication=new Publication(base.id(),base.airVersion(),base.capabilities(),base.artifacts(),List.of(changedUnit),base.storage(),base.resources(),base.artifactRelations(),origins,base.coverage(),base.uncertainties(),base.premises());var evidence=source(publication);var original=links(publication,evidence);var links=new ArrayList<DependencyInput.StatementCorrelation>();
        for(var link:original)links.add(new DependencyInput.StatementCorrelation(link.source(),link.operation(),link.label(),anchor));assertEquals(n,new DependencyInput(publication,Optional.of(evidence),links).occurrences().getFirst().executableOperations().size());
        try(var input=new NativeCase(publication,evidence)) {
            try(var prepared=SourceCorrelationSnapshot.prepare(input.source,input.program,links,input.pages,input.execution)){assertEquals(n,prepared.operationCount(0,0));}
            var first=links.getFirst();links.set(0,new DependencyInput.StatementCorrelation(first.source(),first.operation(),first.label(),sibling));assertThrows(IllegalArgumentException.class,()->SourceCorrelationSnapshot.prepare(input.source,input.program,links,input.pages,input.execution));assertTrue(input.source.unitCount()>0);
        }
    }
    @Test void nativeOwnerExpiryRetiresSourceAndCorrelationPagesBeforeAnotherQuery()throws Exception {
        var publication=program(4);var evidence=source(publication);
        try(var input=new NativeCase(publication,evidence);var prepared=SourceCorrelationSnapshot.prepare(input.source,input.program,links(publication,evidence),input.pages,input.execution)) {
            assertEquals(4,prepared.operationCount(0,0));expire(input.program);assertEquals(0,input.pages.statistics().livePages());zero(input.execution);assertThrows(IllegalStateException.class,prepared::admission);assertThrows(IllegalStateException.class,input.source::snapshot);
        }
    }
    private static QualifiedSourceDependencies minimal(Publication p,List<QualifiedSourceDependencies.UnitEvidence> units) {
        return new QualifiedSourceDependencies("qualified-source-dependencies","1.0.0","synthetic-correlation",new QualifiedSourceDependencies.Document("cobol-semantic-product","2.67.0","0".repeat(64)),List.of(new QualifiedSourceDependencies.AirCorrelation(p.id().localId(),"0".repeat(64))),units);
    }
    private static QualifiedSourceDependencies.UnitEvidence minimalUnit(QualifiedSourceDependencies.UnitId unit,List<String> handles,String kind,String raw,boolean cics,boolean file) {
        var location=new QualifiedSourceDependencies.Location("synthetic.cbl",1,0,1,1);var provenance=new QualifiedSourceDependencies.Provenance(location,location,List.of(),true);
        var statements=new ArrayList<QualifiedSourceDependencies.Statement>();var occurrences=new ArrayList<QualifiedSourceDependencies.Occurrence>();
        for(var handle:handles) {
            var id=new QualifiedSourceDependencies.StatementId(unit,handle);statements.add(new QualifiedSourceDependencies.Statement(id,provenance));
            var operands=raw==null?List.<QualifiedSourceDependencies.Operand>of():List.of(new QualifiedSourceDependencies.Operand(new QualifiedSourceDependencies.OperandId(id,"name"),provenance));
            var values=raw==null?List.<QualifiedSourceDependencies.Value>of():List.of(new QualifiedSourceDependencies.Value("TEXT",raw,raw.codePointCount(0,raw.length())));
            occurrences.add(new QualifiedSourceDependencies.Occurrence(id,cics?"CICS":"COBOL",file?"READ":cics?"LINK":"CALL",file?"FILE":"PROGRAM",file?"cics-ts.file@1":cics?CicsNameInterpreter.PROFILE:CallNameInterpreter.PROFILE,kind,operands,values,raw==null,List.of()));
        }
        return new QualifiedSourceDependencies.UnitEvidence(unit,false,statements,occurrences,List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of());
    }
    private static QualifiedSourceDependencies.UnitId syntheticUnit(){return new QualifiedSourceDependencies.UnitId("synthetic.cbl",List.of(0),"SYNTHETIC");}
    private static Publication literal(String raw,String category,String namespace) {
        return W1dModelTest.model(0,u->{var original=W1dModelTest.call(u,"invoke","end",null,false);var target=new Interactions.LiteralTarget(category,namespace,raw,Interactions.ExactName.INSTANCE,original.header().origin());var invoke=new Operations.Invoke(original.header(),original.action(),target,original.arguments(),original.results(),original.signature(),original.effectOperands(),original.effectBound(),original.outcomes(),original.contract());return List.of(new Sequence(new LabelId(u,"start"),List.of(),invoke,ResultFixtures.origin(u.publication())),ResultFixtures.returning(u,"end",List.of()));});
    }
    @Test void literalAgreementIsFullUtf16NotProfileInterpretationAndAllTargetRejectionsMatchResident()throws Exception {
        String raw="synthetic:"+"X\0\uD83D\uDE00".repeat(4096);
        for(int scenario=0;scenario<7;scenario++) {
            var publication=literal(raw,scenario==4?"file":"program",scenario==3?"cics.program":scenario==5?"foreign.program":"cobol.program");
            var unit=minimalUnit(syntheticUnit(),List.of("site"),scenario==1?"COMPUTED":"LITERAL",scenario==1?null:scenario==2?raw+"different":raw,scenario==6,scenario==6);
            var evidence=minimal(publication,List.of(unit));var link=links(publication,evidence).getFirst();boolean accepted=scenario==0;
            if(accepted)assertEquals(1,new DependencyInput(publication,Optional.of(evidence),List.of(link)).occurrences().getFirst().executableOperations().size());
            else assertThrows(IllegalArgumentException.class,()->new DependencyInput(publication,Optional.of(evidence),List.of(link)));
            try(var input=new NativeCase(publication,evidence)) {
                long before=input.pages.statistics().livePages();
                if(accepted)try(var prepared=SourceCorrelationSnapshot.prepare(input.source,input.program,List.of(link),input.pages,input.execution)){assertEquals(link.operation(),prepared.operation(0,0,0));}
                else assertThrows(IllegalArgumentException.class,()->SourceCorrelationSnapshot.prepare(input.source,input.program,List.of(link),input.pages,input.execution));
                assertEquals(before,input.pages.statistics().livePages());assertTrue(input.source.unitCount()>0);
            }
        }
    }
    @Test void fullSourceTupleAndCollidingHandlesSelectExactlyOneOccurrenceAndConflictingOwnersReject()throws Exception {
        var publication=program(1);var owner=syntheticUnit();var units=List.of(minimalUnit(owner,List.of("Aa","BB"),"COMPUTED",null,false,false),minimalUnit(new QualifiedSourceDependencies.UnitId(owner.compilationUnitId(),List.of(0,1),owner.canonicalProgramName()),List.of("BB"),"COMPUTED",null,false,false),minimalUnit(new QualifiedSourceDependencies.UnitId(owner.compilationUnitId(),owner.structuralPath(),"OTHER"),List.of("BB"),"COMPUTED",null,false,false));
        var evidence=minimal(publication,units);var original=links(publication,evidence).getFirst();var second=units.getFirst().occurrences().get(1).id();var link=new DependencyInput.StatementCorrelation(second,original.operation(),original.label(),original.origin());
        var expected=new DependencyInput(publication,Optional.of(evidence),List.of(link)).occurrences();assertEquals(4,expected.size());assertEquals(List.of(link.operation()),expected.get(1).executableOperations());
        try(var input=new NativeCase(publication,evidence)) {
            long before=input.pages.statistics().livePages();try(var prepared=SourceCorrelationSnapshot.prepare(input.source,input.program,List.of(link),input.pages,input.execution)){assertEquals(0,prepared.operationCount(0,0));assertEquals(1,prepared.operationCount(0,1));assertEquals(0,prepared.operationCount(1,0));assertEquals(0,prepared.operationCount(2,0));}
            assertEquals(before,input.pages.statistics().livePages());var conflict=List.of(link,original);assertThrows(IllegalArgumentException.class,()->new DependencyInput(publication,Optional.of(evidence),conflict));assertThrows(IllegalArgumentException.class,()->SourceCorrelationSnapshot.prepare(input.source,input.program,conflict,input.pages,input.execution));assertEquals(before,input.pages.statistics().livePages());
        }
    }
    @Test void nonInvokeCorrelationIsValidatedButCannotMarkOrYieldAnExecutableDependency()throws Exception {
        var publication=program(1);var evidence=source(publication);var unit=publication.units().getFirst();var sequence=unit.sequences().getLast();var link=new DependencyInput.StatementCorrelation(evidence.units().getFirst().occurrences().getFirst().id(),sequence.terminator().header().id(),sequence.label(),sequence.terminator().header().origin());
        var resident=new DependencyInput(publication,Optional.of(evidence),List.of(link));assertEquals(0,resident.occurrences().getFirst().executableOperations().size());
        try(var input=new NativeCase(publication,evidence);var prepared=SourceCorrelationSnapshot.prepare(input.source,input.program,List.of(link),input.pages,input.execution)){assertEquals(0,prepared.operationCount(0,0));assertFalse(prepared.mapped(link.operation()));}
    }
    @Test void correlationControlsStayFixedUnderForcedEvictionAndSuccessfulCloseBorrowsBothOwners()throws Exception {
        long fixed=-1;
        for(int n:new int[]{1,4,16,64,256}) {
            var publication=program(n);var evidence=source(publication);
            try(var input=new NativeCase(publication,evidence)) {
                input.program.operationDirectory().orElseThrow();long before=input.execution.heapUsed(),pages=input.pages.statistics().livePages();
                try(var prepared=SourceCorrelationSnapshot.prepare(input.source,input.program,links(publication,evidence),input.pages,input.execution)){long delta=input.execution.heapUsed()-before;if(fixed<0)fixed=delta;assertEquals(fixed,delta);assertEquals(n,prepared.operationCount(0,0));assertTrue(input.pages.statistics().evictions()>0);}
                assertEquals(pages,input.pages.statistics().livePages());assertEquals(before,input.execution.heapUsed());assertTrue(input.source.unitCount()>0);assertEquals(publication.id(),input.program.publicationId());
            }
        }
    }
    @Test void foreignContextAndPreallocationDenialDoNotClaimEitherAdmission()throws Exception {
        var publication=program(1);var evidence=source(publication);
        try(var input=new NativeCase(publication,evidence);var other=new FilePageStore(directory,4096,4,input.resources,AnalysisResources.Phase.INDEX)) {
            long before=input.pages.statistics().livePages();var foreign=new AnalysisResources(input.resources.limits());
            assertThrows(IllegalArgumentException.class,()->SourceCorrelationSnapshot.prepare(input.source,input.program,List.of(),other,input.execution));assertThrows(IllegalArgumentException.class,()->SourceCorrelationSnapshot.prepare(input.source,input.program,List.of(),input.pages,foreign));zero(foreign);
            try(var pressure=input.resources.reserve(AnalysisResources.Pool.RESIDENT,input.resources.limits().heapBytes()-input.resources.heapUsed()-2047,AnalysisResources.Phase.INDEX)){assertNotNull(pressure);assertThrows(AnalysisResources.Exhausted.class,()->SourceCorrelationSnapshot.prepare(input.source,input.program,List.of(),input.pages,input.execution));}
            assertEquals(before,input.pages.statistics().livePages());assertTrue(input.source.unitCount()>0);assertEquals(publication.id(),input.program.publicationId());
        }
    }
    @Test void sourceExpiryAndInvalidOrdinalRetireOnlyDerivedMappingsBeforeAnotherQuery()throws Exception {
        var publication=program(1);var evidence=source(publication);
        try(var input=new NativeCase(publication,evidence)) {
            long before=input.pages.statistics().livePages();try(var prepared=SourceCorrelationSnapshot.prepare(input.source,input.program,links(publication,evidence),input.pages,input.execution)){assertThrows(IndexOutOfBoundsException.class,()->prepared.operation(0,0,1));assertThrows(IllegalStateException.class,prepared::admission);assertEquals(before,input.pages.statistics().livePages());assertTrue(input.source.unitCount()>0);}
            try(var prepared=SourceCorrelationSnapshot.prepare(input.source,input.program,links(publication,evidence),input.pages,input.execution)){input.source.close();assertEquals(0,input.pages.statistics().livePages());assertThrows(IllegalStateException.class,prepared::program);assertEquals(publication.id(),input.program.publicationId());}
        }
    }
    @Test void primitiveFailurePreservesPrimaryAndNeverClosesBorrowedBackendOrPublishesPartialMappings()throws Exception {
        var publication=program(1);var evidence=source(publication);
        for(int fault=0;fault<4;fault++)try(var input=new NativeCase(publication,evidence,true)) {
            var port=(FaultPages)input.backend;long before=input.pages.statistics().livePages();
            if(fault<3){port.arm(fault);assertSame(port.primary,assertThrows(PageStore.Failure.class,()->SourceCorrelationSnapshot.prepare(input.source,input.program,links(publication,evidence),port,input.execution)));}
            else try(var prepared=SourceCorrelationSnapshot.prepare(input.source,input.program,links(publication,evidence),port,input.execution)){port.arm(1);assertSame(port.primary,assertThrows(PageStore.Failure.class,()->prepared.operationCount(0,0)));assertThrows(IllegalStateException.class,prepared::admission);}
            try{input.source.snapshot();assertEquals(before,input.pages.statistics().livePages());}catch(IllegalStateException expired){assertEquals(0,input.pages.statistics().livePages());}
            assertFalse(port.closed);assertEquals(publication.id(),input.program.publicationId());long borrowed=input.pages.allocate();input.pages.release(borrowed);
        }
    }
    static final class FaultPages implements PageStore {
        private final PageStore delegate;final Failure primary=new Failure(Reason.IO,"independent SOURCE correlation primitive failure");private int operation=-1;boolean closed;
        FaultPages(PageStore delegate){this.delegate=delegate;}
        void arm(int value){operation=value;}private void fail(int candidate){if(candidate==operation){operation=-1;throw primary;}}
        @Override public int pageBytes(){return delegate.pageBytes();}
        @Override public long allocate(){fail(0);return delegate.allocate();}
        @Override public void release(long handle){delegate.release(handle);}
        @Override public void read(long handle,int offset,byte[] out,int at,int count){fail(1);delegate.read(handle,offset,out,at,count);}
        @Override public void write(long handle,int offset,byte[] in,int at,int count){fail(2);delegate.write(handle,offset,in,at,count);}
        @Override public Statistics statistics(){return delegate.statistics();}
        @Override public void flush(){delegate.flush();}
        @Override public void readForCleanup(long handle,int offset,byte[] out,int at,int count){delegate.readForCleanup(handle,offset,out,at,count);}
        @Override public void releaseForCleanup(long handle){delegate.releaseForCleanup(handle);}
        @Override public void close(){closed=true;delegate.close();}
    }
    @Test void equalLocalOperationIdsInDistinctUnitsNeverUnifyAndHavePermutationIndependentTypedTie()throws Exception {
        var p=new PublicationId("typed-unit-collision");var units=new ArrayList<io.github.gustavo2358.air.model.Unit>();
        for(var name:List.of("BB","Aa")){var u=new UnitId(p,name);units.add(ResultFixtures.unit(u,List.of(ResultFixtures.entry(u,"entry","start")),List.of(new Sequence(new LabelId(u,"start"),List.of(),W1dModelTest.call(u,"invoke","end",null,false),ResultFixtures.origin(p)),ResultFixtures.returning(u,"end",List.of())),List.of()));}
        var expected=List.of(new OperationId(new UnitId(p,"Aa"),"invoke"),new OperationId(new UnitId(p,"BB"),"invoke"));
        for(int permutation=0;permutation<2;permutation++) {
            var publication=ResultFixtures.publication(p,units,List.of());var evidence=minimal(publication,List.of(minimalUnit(syntheticUnit(),List.of("site"),"LITERAL","PROGA",false,false)));var statement=evidence.units().getFirst().occurrences().getFirst().id();var links=new ArrayList<DependencyInput.StatementCorrelation>();
            for(var unit:units){var sequence=unit.sequences().getFirst();links.add(new DependencyInput.StatementCorrelation(statement,sequence.terminator().header().id(),sequence.label(),sequence.terminator().header().origin()));}
            // The resident comparator defines only localId. Its tied HashSet
            // iteration is not an authority for which distinct Unit comes first.
            assertEquals(new HashSet<>(expected),new HashSet<>(new DependencyInput(publication,Optional.of(evidence),links).occurrences().getFirst().executableOperations()));
            try(var input=new NativeCase(publication,evidence);var prepared=SourceCorrelationSnapshot.prepare(input.source,input.program,links,input.pages,input.execution)){assertEquals(2,prepared.operationCount(0,0));assertEquals(expected.get(0),prepared.operation(0,0,0));assertEquals(expected.get(1),prepared.operation(0,0,1));assertTrue(prepared.mapped(expected.get(0)));assertTrue(prepared.mapped(expected.get(1)));}
            Collections.reverse(units);
        }
    }
    @Test void differentValidOriginAnchorsDoNotDuplicateASiteAndForeignPublicationNeverMatches()throws Exception {
        var base=program(1);var ancestor=ResultFixtures.origin(base.id());var derived=new OriginId(base.id(),"derived");var origins=new ArrayList<>(base.origins());origins.add(new Origins.Derived(derived,List.of(ancestor),"synthetic correlation rule"));
        var publication=withCallOrigins(base,origins,Map.of("call-0",derived));var evidence=source(publication);var original=links(publication,evidence).getFirst();var first=new DependencyInput.StatementCorrelation(original.source(),original.operation(),original.label(),ancestor);var valid=List.of(first,original);
        assertEquals(1,new DependencyInput(publication,Optional.of(evidence),valid).occurrences().getFirst().executableOperations().size());
        try(var input=new NativeCase(publication,evidence)) {
            long before=input.pages.statistics().livePages();try(var prepared=SourceCorrelationSnapshot.prepare(input.source,input.program,valid,input.pages,input.execution)){assertEquals(1,prepared.operationCount(0,0));assertEquals(original.operation(),prepared.operation(0,0,0));}
            assertEquals(before,input.pages.statistics().livePages());var foreign=new DependencyInput.StatementCorrelation(original.source(),original.operation(),original.label(),new OriginId(new PublicationId("foreign"),ancestor.localId()));
            assertThrows(IllegalArgumentException.class,()->new DependencyInput(publication,Optional.of(evidence),List.of(foreign)));assertThrows(IllegalArgumentException.class,()->SourceCorrelationSnapshot.prepare(input.source,input.program,List.of(foreign),input.pages,input.execution));assertEquals(before,input.pages.statistics().livePages());
            var bad=new QualifiedSourceDependencies(evidence.schema(),evidence.version(),evidence.producer(),evidence.source(),List.of(new QualifiedSourceDependencies.AirCorrelation("foreign","0".repeat(64))),evidence.units());
            try(var other=new NativeCase(publication,bad)){long prior=other.pages.statistics().livePages();assertThrows(IllegalArgumentException.class,()->SourceCorrelationSnapshot.prepare(other.source,other.program,List.of(),other.pages,other.execution));assertEquals(prior,other.pages.statistics().livePages());assertTrue(other.source.unitCount()>0);}
        }
    }
    private static Publication withCallOrigins(Publication base,List<Origins.Origin> origins,Map<String,OriginId> replacements) {
        var unit=base.units().getFirst();var sequences=new ArrayList<Sequence>();
        for(var sequence:unit.sequences())if(sequence.terminator() instanceof Operations.Invoke invoke&&replacements.containsKey(invoke.header().id().localId())) {
            var h=invoke.header();var origin=replacements.get(h.id().localId());var header=new Operations.Header(h.id(),origin,h.coverage(),h.precision(),h.uncertainties());var changed=new Operations.Invoke(header,invoke.action(),invoke.target(),invoke.arguments(),invoke.results(),invoke.signature(),invoke.effectOperands(),invoke.effectBound(),invoke.outcomes(),invoke.contract());sequences.add(new Sequence(sequence.label(),sequence.instructions(),changed,sequence.origin()));
        }else sequences.add(sequence);
        var changed=new io.github.gustavo2358.air.model.Unit(unit.id(),unit.containingUnit(),unit.objects(),unit.visibleObjects(),unit.entries(),sequences,unit.completionPorts(),unit.body(),unit.bodyUnavailable(),unit.coverage(),unit.origin());
        return new Publication(base.id(),base.airVersion(),base.capabilities(),base.artifacts(),List.of(changed),base.storage(),base.resources(),base.artifactRelations(),origins,base.coverage(),base.uncertainties(),base.premises());
    }
    @Test void successfulAncestryMemoCannotAuthorizeUnrelatedVisitedSiblingOnAnotherOperation()throws Exception {
        var base=program(2);var ancestor=ResultFixtures.origin(base.id());var sibling=new OriginId(base.id(),"sibling");var derived=new OriginId(base.id(),"derived");var origins=new ArrayList<>(base.origins());origins.add(new Origins.Unavailable(sibling,"synthetic independent leaf"));origins.add(new Origins.Derived(derived,List.of(sibling,ancestor),"synthetic independent alternatives"));
        var publication=withCallOrigins(base,origins,Map.of("call-1",derived,"call-0",sibling));var evidence=source(publication);var links=new ArrayList<DependencyInput.StatementCorrelation>();
        for(var link:links(publication,evidence))links.add(new DependencyInput.StatementCorrelation(link.source(),link.operation(),link.label(),ancestor));
        assertEquals("call-1",links.getFirst().operation().localId());assertThrows(IllegalArgumentException.class,()->new DependencyInput(publication,Optional.of(evidence),links));
        try(var input=new NativeCase(publication,evidence)){long before=input.pages.statistics().livePages();try(var prepared=SourceCorrelationSnapshot.prepare(input.source,input.program,links.subList(0,1),input.pages,input.execution)){assertEquals(1,prepared.operationCount(0,0));}assertThrows(IllegalArgumentException.class,()->SourceCorrelationSnapshot.prepare(input.source,input.program,links,input.pages,input.execution));assertEquals(before,input.pages.statistics().livePages());assertTrue(input.source.unitCount()>0);}
    }
}
