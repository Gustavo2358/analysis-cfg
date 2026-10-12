package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.air.validation.*;
import io.github.gustavo2358.analysis.cfg.application.*;
import io.github.gustavo2358.analysis.cfg.extension.SemanticInterpreterRegistry;
import io.github.gustavo2358.analysis.dependencies.*;
import io.github.gustavo2358.analysis.query.*;
import io.github.gustavo2358.analysis.solver.*;
import io.github.gustavo2358.analysis.structure.*;
import io.github.gustavo2358.analysis.values.*;
import java.math.BigInteger;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static io.github.gustavo2358.analysis.adapters.ResultFixtures.*;

/** Prospective semantic challenge; not executed or part of the current frozen FAST. */
final class ScalarAdmissionSharingTest {
    @FunctionalInterface private interface Work {void run(AnalysisSession session)throws Exception;}
    private static void eachBackend(Publication publication,Work work)throws Exception {
        var cfg=new CfgBuildCoordinator(SemanticInterpreterRegistry.empty()).build(publication,BuildOptions.defaults());
        assertEquals(CfgBuildResult.Status.CFG_BUILT,cfg.status(),cfg.toString());
        var resident=AnalysisSession.open(cfg,publication,cfg.options().projectionPolicy(),publication.units().getFirst().entries());
        assertEquals(AnalysisSession.Status.ACCEPTED,resident.status());work.run(resident.session().orElseThrow());
        var temporary=Files.createTempDirectory(Path.of("target"),"scalar-admission-sharing-");
        for(boolean file:new boolean[]{false,true}) {
            var resources=new AnalysisResources(new AnalysisResources.Limits(64L*1024*1024,16L*1024*1024,0,256L*1024*1024,8,Long.MAX_VALUE,Long.MAX_VALUE));
            try(PageStore pages=file?new FilePageStore(temporary,512,1,resources):new MemoryPageStore(512,resources);
                    var snapshot=AirSnapshot.fromPublication(publication);
                    var checked=SnapshotValidator.check(snapshot,ValidationOptions.defaults(),new PagedSnapshotValidationStorage(pages,resources));
                    var program=new SnapshotProgram(checked,new PagedSnapshotIdentityStorage(pages,resources),new PagedSnapshotOrderStorage(pages,resources),resources)) {
                assertTrue(checked.result().isStructurallyValid(),checked.result().toString());
                var nativeCfg=new CfgBuildCoordinator(SemanticInterpreterRegistry.empty()).buildChecked(program,checked,BuildOptions.defaults());
                assertEquals(CfgBuildResult.Status.CFG_BUILT,nativeCfg.status());
                var opened=AnalysisSession.open(nativeCfg,program,nativeCfg.options().projectionPolicy(),List.of(publication.units().getFirst().entries().getFirst().id()));
                assertEquals(AnalysisSession.Status.ACCEPTED,opened.status());work.run(opened.session().orElseThrow());
            } finally {for(var pool:AnalysisResources.Pool.values())assertEquals(0,resources.used(pool),pool.toString());}
        }
    }
    private static PossibleValuesAnalysis.Status status(AnalysisSession session,Set<ObjectId> demand) {
        var admission=PossibleValuesAnalysis.prepare(session,PossibleValuesAnalysis.EFFECTS_PROFILE,demand);
        try{return admission.status();}finally{admission.analysis().ifPresent(PossibleValuesAnalysis::close);}
    }
    @Test void acceptedUnionPreservesEveryNonemptySubsetAndCompletePointFacts()throws Exception {
        var p=W1dModelTest.model(3,u->List.of(returning(u,"start",List.of(
            assign(u,"seed-0",new ObjectId(u,"object-0"),"ALPHA"),assign(u,"seed-1",new ObjectId(u,"object-1"),"BETA"),assign(u,"seed-2",new ObjectId(u,"object-2"),"GAMMA")))));
        eachBackend(p,session->{
            var u=p.units().getFirst();var objects=u.objects().stream().map(Memory.ObjectDeclaration::id).toList();
            var entry=u.entries().getFirst().id();var operation=u.sequences().getFirst().terminator().header().id();
            var queries=objects.stream().map(subject->new PointQuery<>(ProgramPoint.before(entry,operation),subject)).toList();
            var union=PossibleValuesAnalysis.prepare(session,PossibleValuesAnalysis.EFFECTS_PROFILE,Set.copyOf(objects));
            assertEquals(PossibleValuesAnalysis.Status.ACCEPTED,union.status());
            try(var prepared=union.analysis().orElseThrow();var execution=prepared.execute()) {
                var all=execution.observe(queries).batch().observations();
                assertEquals(List.of("ALPHA","BETA","GAMMA"),all.stream().map(fact->fact.value().candidates().getFirst().value()).toList());
                for(int mask=1;mask<8;mask++) {
                    var subset=new HashSet<ObjectId>();var requested=new ArrayList<PointQuery<ObjectId>>();
                    for(int at=0;at<3;at++)if((mask&(1<<at))!=0){subset.add(objects.get(at));requested.add(queries.get(at));}
                    var local=PossibleValuesAnalysis.prepare(session,PossibleValuesAnalysis.EFFECTS_PROFILE,subset);
                    assertEquals(PossibleValuesAnalysis.Status.ACCEPTED,local.status(),"nonvacuous accepted subset mask="+mask);
                    try(var owned=local.analysis().orElseThrow();var run=owned.execute()) {
                        var facts=run.observe(requested).batch().observations();
                        assertEquals(all.stream().filter(fact->subset.contains(fact.query().subject())).toList(),facts,"complete candidate/support/origin/premise/remainder/point equality");
                    }
                }
            }
        });
    }
    @Test void contradictoryInitialFactsAreRejectedBeforeAnyDemandSharing()throws Exception {
        var base=W1dModelTest.model(2,u->List.of(
            new Sequence(new LabelId(u,"start"),List.of(assign(u,"seed",new ObjectId(u,"object-0"),"PROGA   ")),W1dModelTest.call(u,"good-call","bad",new ObjectId(u,"object-0"),false),origin(u.publication())),
            new Sequence(new LabelId(u,"bad"),List.of(),W1dModelTest.call(u,"invalid-call","end",new ObjectId(u,"object-1"),false),origin(u.publication())),returning(u,"end",List.of())));
        var u=base.units().getFirst();var original=u.entries().getFirst();var bad=u.objects().get(1).id();var owner=new EntryOwner(original.id());
        var conditions=new ArrayList<Entries.InitialCondition>();
        for(int at=0;at<2;at++) {
            var place=new Places.ObjectPlace(new Operand.Header(new OperandId(owner,"condition-place-"+at),Operand.Role.VALUE_WRITE,original.origin()),bad);
            var literal=new Expressions.Literal(new Operand.Header(new OperandId(owner,"condition-value-"+at),Operand.Role.VALUE_READ,original.origin()),new Values.TextValue(at==0?"FIRST":"SECOND"));
            conditions.add(new Entries.InitialCondition(place,new Entries.LiteralInitial(literal),original.origin(),List.of()));
        }
        var entry=new Entries.Entry(original.id(),original.initialLabel(),original.signature(),new Entries.EntryState(conditions,List.of()),original.origin());
        var p=new Publication(base.id(),base.airVersion(),base.capabilities(),base.artifacts(),List.of(unit(u.id(),List.of(entry),u.sequences(),u.objects())),base.storage(),base.resources(),base.artifactRelations(),base.origins(),base.coverage(),base.uncertainties(),base.premises());
        var cfg=new CfgBuildCoordinator(SemanticInterpreterRegistry.empty()).build(p,BuildOptions.defaults());
        assertEquals(CfgBuildResult.Status.INVALID_IR,cfg.status());assertTrue(cfg.graph().isEmpty());
        assertTrue(cfg.preflight().issues().stream().anyMatch(issue->issue.rule().equals("I-17")));
        var failure=assertThrows(DependencyAnalysis.Failure.class,()->new DependencyAnalysis().prepare(p));
        assertEquals(DependencyAnalysis.Kind.INVALID_INPUT,failure.kind());assertEquals("INVALID_IR",failure.getMessage());
        var temporary=Files.createTempDirectory(Path.of("target"),"scalar-invalid-admission-");
        for(boolean file:new boolean[]{false,true}) {
            var resources=new AnalysisResources(new AnalysisResources.Limits(64L*1024*1024,16L*1024*1024,0,256L*1024*1024,8,Long.MAX_VALUE,Long.MAX_VALUE));
            try(PageStore pages=file?new FilePageStore(temporary,512,1,resources):new MemoryPageStore(512,resources);
                    var snapshot=AirSnapshot.fromPublication(p);
                    var checked=SnapshotValidator.check(snapshot,ValidationOptions.defaults(),new PagedSnapshotValidationStorage(pages,resources))) {
                assertEquals(ValidationResult.Status.INVALID_IR,checked.result().status());
                assertTrue(checked.result().issues().stream().anyMatch(issue->issue.rule().equals("I-17")));
            } finally {for(var pool:AnalysisResources.Pool.values())assertEquals(0,resources.used(pool),pool.toString());}
        }
    }
    @Test void unsupportedUnionCannotRefuseTheIndividuallyAcceptedScalarCall()throws Exception {
        var base=W1dModelTest.model(2,u->List.of(
            new Sequence(new LabelId(u,"start"),List.of(assign(u,"seed",new ObjectId(u,"object-0"),"PROGA   ")),W1dModelTest.call(u,"good-call","bad",new ObjectId(u,"object-0"),false),origin(u.publication())),
            new Sequence(new LabelId(u,"bad"),List.of(),W1dModelTest.call(u,"bad-call","end",new ObjectId(u,"object-1"),false),origin(u.publication())),returning(u,"end",List.of())));
        var u=base.units().getFirst();var objects=new ArrayList<>(u.objects());var second=objects.get(1);var region=new StorageId(base.id(),"view-region");
        var codec=new Memory.ExtensionCodec("text.ebcdic.ibm1047","1",Types.known(Types.Builtin.TEXT));
        objects.set(1,new Memory.ObjectDeclaration(second.id(),second.displayName(),second.typeRef(),new Memory.ViewBinding(region,BigInteger.ZERO,BigInteger.valueOf(8),codec),second.visibility(),second.origin(),second.coverage(),second.precision()));
        var storage=new ArrayList<>(base.storage());storage.add(new Memory.Region(new Memory.StorageHeader(region,Optional.of(u.id()),Memory.Lifetime.ACTIVATION,Memory.Visibility.PRIVATE,origin(base.id())),Optional.of(BigInteger.valueOf(8)),Optional.empty()));
        var premises=new ArrayList<>(base.premises());
        premises.add(new Proofs.Premise(new PremiseId(base.id(),"mixed-cell-region-disjoint"),"test-model","independent declared Cell and Region",origin(base.id()),new Proofs.DisjointStorage(storage.stream().map(value->value.header().id()).toList())));
        var p=new Publication(base.id(),base.airVersion(),new Capabilities.Manifest(List.of(Capabilities.MEMORY_REGIONS,Capabilities.IBM1047),List.of()),base.artifacts(),List.of(unit(u.id(),u.entries(),u.sequences(),objects)),storage,base.resources(),base.artifactRelations(),base.origins(),base.coverage(),base.uncertainties(),premises);
        eachBackend(p,session->{
            var good=objects.getFirst().id();var bad=objects.get(1).id();
            assertEquals(PossibleValuesAnalysis.Status.ACCEPTED,status(session,Set.of(good)));
            assertEquals(PossibleValuesAnalysis.Status.UNSUPPORTED,status(session,Set.of(bad)));
            assertEquals(PossibleValuesAnalysis.Status.UNSUPPORTED,status(session,Set.of(good,bad)));
            var choices=CallDependencyPlan.choose(session,"mixed",false,StorageAnalysisMode.LOGICAL_ONLY);var entry=u.entries().getFirst().id();
            assertEquals(Map.of(new CallDependencyPlan.SiteKey(entry,new OperationId(u.id(),"good-call")),CallDependencyPlan.Provider.SCALAR,new CallDependencyPlan.SiteKey(entry,new OperationId(u.id(),"bad-call")),CallDependencyPlan.Provider.REGIONAL),choices.providers());
            assertEquals(2L,choices.metrics().get("scalarAdmissionAttempts"));assertEquals(1L,choices.metrics().get("scalarAdmissionAccepted"));assertEquals(1L,choices.metrics().get("scalarAdmissionRefused"));
        });
        var good=new DependencyAnalysis().prepare(p).sites().stream().filter(fact->fact.operation().localId().equals("good-call")).findFirst().orElseThrow();
        assertEquals(List.of("PROGA"),good.candidates().stream().map(DependencySiteFact.Candidate::referenceName).toList());
        assertEquals(List.of("seed"),good.candidates().getFirst().supports().stream().map(support->support.producer().localId()).toList());assertFalse(good.modelValueRemainder());
    }
}
