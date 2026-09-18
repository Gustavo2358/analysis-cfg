package io.github.gustavo2358.analysis.values;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.analysis.query.*;
import io.github.gustavo2358.analysis.rd.*;
import io.github.gustavo2358.analysis.storage.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static io.github.gustavo2358.analysis.values.ValuesFixtures.*;
import static io.github.gustavo2358.analysis.values.RegionalValuesTest.*;

/** Representation law: explicit identity is independent of query syntax. */
class LogicalPlaceRepresentationTest {
    @Test void objectOccurrenceValues() { contrast(false, false); }
    @Test void choiceOccurrenceValues() { contrast(true, false); }
    @Test void objectOccurrenceDefinitions() { contrast(false, true); }
    @Test void choiceOccurrenceDefinitions() { contrast(true, true); }
    private void contrast(boolean choice, boolean definitions) {
            var h=header(U,"read");
            Place source=new Places.ObjectPlace(operand(h.id(),"source",Operand.Role.VALUE_READ),WHOLE);
            if(choice) source=new Places.Choice(operand(h.id(),"choice",Operand.Role.VALUE_READ),List.of(source),Scopes.NoMemory.INSTANCE,Types.known(Types.Builtin.TEXT));
            var read=new Operations.Assign(h,new Places.ObjectPlace(operand(h.id(),"destination",Operand.Role.VALUE_WRITE),WHOLE),
                new Expressions.Read(operand(h.id(),"read",Operand.Role.VALUE_READ),source));
            var p=EvidencePreservingEntryTest.logical(List.of(returning(U,"s0",List.of(read))));
            var session=session(p); // Normal AIR validation; observe BEFORE the first operation.
            var point=ProgramPoint.before(new EntryId(U,"entry"),h.id());
            var named=new PointQuery<StorageSubject>(point,new StorageSubject.NamedObject(WHOLE));
            var occurrence=new PointQuery<StorageSubject>(point,new StorageSubject.PlaceOccurrence(source.header().id()));
            if(!definitions) {
            var values=RegionalValuesAnalysis.prepare(session).analysis().orElseThrow().execute();
            var expected=values.observeStorage(List.of(named)).observations().getFirst().value();
            var actual=values.observeStorage(List.of(occurrence)).observations().getFirst().value();
            assertEquals(List.of(new Values.TextValue("PGM00001")),expected.candidates());
            assertEquals(expected.candidates(),actual.candidates(),"representation must not hide logical values; choice="+choice);
            assertEquals(expected.candidateSupports(),actual.candidateSupports());
            assertEquals(expected.logicalAlternatives(),actual.logicalAlternatives());
            assertEquals(expected.sourceUnknownRemainder(),actual.sourceUnknownRemainder());
            assertTrue(actual.modelValueRemainder());
            } else {
            var rd=new ReachingDefinitions(new StatementEffects(new StorageIndex(session))).execute();
            var namedRd=rd.observeStorage(List.of(named)).observations().getFirst().value();
            var occurrenceRd=rd.observeStorage(List.of(occurrence)).observations().getFirst().value();
            assertEquals(namedRd.definitions(),occurrenceRd.definitions());
            assertEquals(namedRd.sourceUnknownRemainder(),occurrenceRd.sourceUnknownRemainder());
            assertEquals(namedRd.premises(),occurrenceRd.premises());
            assertEquals(namedRd.uncertainties(),occurrenceRd.uncertainties());
            assertTrue(occurrenceRd.unknownRemainder());
        }
    }

    @Test void mixedChoiceReadsCurrentLogicalAndPhysicalAlternativesThenMustKillsCopy() {
        for(boolean physicalDestination:List.of(false,true)) for(boolean mixed:List.of(false,true)) for(boolean open:List.of(false,true)) {
            var h=header(U,"copy");var target=new ObjectId(U,"copied");var cell=new StorageId(P,"copy-cell");var physical=new ObjectId(U,"physical");var unknown=new ObjectId(U,"unknown");var secondPhysical=new ObjectId(U,"physical-2");
            var candidates=new ArrayList<Place>();
            candidates.add(new Places.ObjectPlace(operand(h.id(),"logical",Operand.Role.VALUE_READ),WHOLE));
            if(mixed) {
                candidates.add(new Places.ObjectPlace(operand(h.id(),"physical",Operand.Role.VALUE_READ),physical));
                candidates.add(new Places.ObjectPlace(operand(h.id(),"physical-2",Operand.Role.VALUE_READ),secondPhysical));
            }
            if(open)candidates.add(new Places.ObjectPlace(operand(h.id(),"unknown",Operand.Role.VALUE_READ),unknown));
            var choice=new Places.Choice(operand(h.id(),"choice",Operand.Role.VALUE_READ),candidates,
                Scopes.NoMemory.INSTANCE,Types.known(Types.Builtin.TEXT));
            var copy=new Operations.Assign(h,new Places.ObjectPlace(operand(h.id(),"destination",Operand.Role.VALUE_WRITE),target),
                physicalDestination?new Expressions.FitText(operand(h.id(),"fit",Operand.Role.VALUE_READ),new Expressions.Read(operand(h.id(),"read",Operand.Role.VALUE_READ),choice),java.math.BigInteger.valueOf(8)," "):new Expressions.Read(operand(h.id(),"read",Operand.Role.VALUE_READ),choice));
            var p=EvidencePreservingEntryTest.logical(List.of(returning(U,"s0",List.of(copy,assign(U,"must",target,"FINAL000")))));
            var u=p.units().getFirst();var e=u.entries().getFirst();var conditions=new ArrayList<>(e.state().conditions());
            var owner=new EntryOwner(e.id());
            if(mixed)conditions.add(new Entries.InitialCondition(
                new Places.ObjectPlace(new Operand.Header(new OperandId(owner,"physical-place"),Operand.Role.VALUE_WRITE,origin(P)),physical),
                new Entries.LiteralInitial(new Expressions.Literal(new Operand.Header(new OperandId(owner,"physical-text"),Operand.Role.VALUE_READ,origin(P)),new Values.TextValue("PHYS0002"))),origin(P),List.of()));
            if(mixed)conditions.add(new Entries.InitialCondition(
                new Places.ObjectPlace(new Operand.Header(new OperandId(owner,"physical-place-2"),Operand.Role.VALUE_WRITE,origin(P)),secondPhysical),
                new Entries.LiteralInitial(new Expressions.Literal(new Operand.Header(new OperandId(owner,"physical-text-2"),Operand.Role.VALUE_READ,origin(P)),new Values.TextValue("PHYS0003"))),origin(P),List.of()));
            var entry=new Entries.Entry(e.id(),e.initialLabel(),e.signature(),new Entries.EntryState(conditions,e.state().uncertainties()),e.origin());
            var objects=new ArrayList<>(u.objects());objects.add(view(physical,0,8,IBM));objects.add(view(secondPhysical,8,8,IBM));
            objects.add(new Memory.ObjectDeclaration(unknown,Optional.empty(),Types.known(Types.Builtin.TEXT),new Memory.UnknownBinding(new Scopes.AllMemory(P,true),PossibleEntryTest.GAP),Memory.Visibility.UNKNOWN,origin(P),Evidence.CoverageStatus.ABSTRACTED,h.precision()));
            objects.add(new Memory.ObjectDeclaration(target,Optional.empty(),Types.known(Types.Builtin.TEXT),physicalDestination?new Memory.ViewBinding(cell,java.math.BigInteger.ZERO,java.math.BigInteger.valueOf(8),IBM):new Memory.CellBinding(cell),Memory.Visibility.PRIVATE,origin(P),Evidence.CoverageStatus.MODELED,h.precision()));
            var storage=new ArrayList<Memory.Storage>(p.storage().stream().map(b->b.header().id().equals(R)?new Memory.Region(b.header(),Optional.of(java.math.BigInteger.valueOf(16)),Optional.empty()):b).toList());storage.add(physicalDestination?new Memory.Region(new Memory.StorageHeader(cell,Optional.of(U),Memory.Lifetime.PERSISTENT,Memory.Visibility.PRIVATE,origin(P)),Optional.of(java.math.BigInteger.valueOf(8)),Optional.empty()):new Memory.Cell(new Memory.StorageHeader(cell,Optional.of(U),Memory.Lifetime.PERSISTENT,Memory.Visibility.PRIVATE,origin(P)),Types.known(Types.Builtin.TEXT)));
            p=new Publication(p.id(),p.airVersion(),p.capabilities(),p.artifacts(),List.of(unit(U,List.of(entry),u.sequences(),objects)),storage,p.resources(),p.artifactRelations(),p.origins(),p.coverage(),p.uncertainties(),p.premises());
            // Strong simultaneous byte facts require their actual base separation contract.
            // Unknown logical binding remains open; IDs themselves prove no separation.
            p=replace(p,p.units(),p.coverage(),p.uncertainties(),List.of(new Proofs.Premise(new PremiseId(P,"source-destination-separation"),"fixture contract","distinct source and destination storage",origin(P),new Proofs.DisjointStorage(List.of(R,cell)))));
            var run=physicalDestination?partialRun(p):run(p);var expected=mixed?List.of("PGM00001","PHYS0002","PHYS0003"):List.of("PGM00001");
            var query=new PointQuery<StorageSubject>(ProgramPoint.before(e.id(),h.id()),new StorageSubject.PlaceOccurrence(choice.header().id()));
            assertEquals(expected,run.observeStorage(List.of(query)).observations().getFirst().value().candidates().stream().map(Values.TextValue::value).toList());
            var copied=at(run,"must",target);
            var copiedStorage=run.observeStorage(List.of(new PointQuery<StorageSubject>(ProgramPoint.before(e.id(),new OperationId(U,"must")),new StorageSubject.NamedObject(target)))).observations().getFirst().value();
            var logicalFragment=copiedStorage.alternatives().stream().filter(a->a.candidate().equals(Optional.of(new Values.TextValue("PGM00001"))))
                .flatMap(a->a.fragments().stream()).filter(f->f.logicalCapture().isPresent()).findFirst().orElseThrow();
            assertEquals(WHOLE,logicalFragment.logicalCapture().orElseThrow().object());
            assertEquals(ProgramPoint.before(e.id(),h.id()),logicalFragment.logicalCapture().orElseThrow().before());
            assertTrue(logicalFragment.captures().isEmpty());
            if(mixed)for(int i=2;i<=3;i++) {
                var text=new Values.TextValue("PHYS000"+i);var offset=java.math.BigInteger.valueOf((i-2)*8);
                var physicalFragment=copiedStorage.alternatives().stream().filter(a->a.candidate().equals(Optional.of(text))).flatMap(a->a.fragments().stream()).findFirst().orElseThrow();
                assertTrue(physicalFragment.logicalCapture().isEmpty());
                assertEquals(offset,physicalFragment.captures().getFirst().sourceRange().location().range().orElseThrow().start());
            }
            assertEquals(expected,texts(copied));assertTrue(copied.modelValueRemainder());
            var finalValue=at(run,"return-s0",target);assertEquals(List.of("FINAL000"),texts(finalValue));
            assertFalse(finalValue.modelValueRemainder());
        }
    }

    @Test void choiceCopyIntoLogicalDestinationUsesCurrentSupportAndWeakUpdates() {
        var target=new ObjectId(U,"logical-destination");var h=header(U,"copy-logical");
        var choice=new Places.Choice(operand(h.id(),"choice",Operand.Role.VALUE_READ),List.of(
            new Places.ObjectPlace(operand(h.id(),"source",Operand.Role.VALUE_READ),WHOLE)),Scopes.NoMemory.INSTANCE,Types.known(Types.Builtin.TEXT));
        var copy=new Operations.Assign(h,new Places.ObjectPlace(operand(h.id(),"destination",Operand.Role.VALUE_WRITE),target),
            new Expressions.Read(operand(h.id(),"read",Operand.Role.VALUE_READ),choice));
        var p=EvidencePreservingEntryTest.logical(List.of(returning(U,"s0",List.of(copy,assign(U,"weak",target,"OTHERPGM")))));
        var u=p.units().getFirst();var source=u.objects().stream().filter(o->o.id().equals(WHOLE)).findFirst().orElseThrow();
        var objects=new ArrayList<>(u.objects());objects.add(new Memory.ObjectDeclaration(target,Optional.empty(),source.typeRef(),source.storage(),source.visibility(),source.origin(),source.coverage(),source.precision()));
        p=replace(p,List.of(unit(U,u.entries(),u.sequences(),objects)),p.coverage(),p.uncertainties(),p.premises());
        var run=run(p);var before=at(run,"weak",target);
        assertEquals(List.of("PGM00001"),texts(before));assertTrue(before.modelValueRemainder());
        assertTrue(before.candidateSupports().getFirst().producers().stream().anyMatch(support->support.evidence().equals(h.id())));
        assertEquals(List.of("OTHERPGM","PGM00001"),texts(at(run,"return-s0",target)),"unproved destination cannot kill the copied support");
    }

    private static RegionalValuesAnalysis.Execution partialRun(Publication p) {
        var validation=io.github.gustavo2358.air.validation.AirValidator.validate(p);
        assertEquals(io.github.gustavo2358.air.validation.ValidationResult.Status.INCOMPLETE_VALIDATION,validation.status());
        var defaults=io.github.gustavo2358.analysis.cfg.application.BuildOptions.defaults();
        var options=new io.github.gustavo2358.analysis.cfg.application.BuildOptions(defaults.validation(),io.github.gustavo2358.analysis.cfg.domain.ProjectionPolicy.PARTIAL_ANALYSIS);
        var cfg=new io.github.gustavo2358.analysis.cfg.application.CfgBuildCoordinator(io.github.gustavo2358.analysis.cfg.extension.SemanticInterpreterRegistry.empty()).build(p,options);
        var session=io.github.gustavo2358.analysis.structure.AnalysisSession.open(cfg,p,options.projectionPolicy(),p.units().getFirst().entries()).session().orElseThrow();
        return RegionalValuesAnalysis.prepare(session,StorageAnalysisMode.EXPERIMENTAL_PHYSICAL).analysis().orElseThrow().execute();
    }
    @Test void explicitIdentityDoesNotFollowAliasBindingsOrRegionAddressOperands() {
        var p=EvidencePreservingEntryTest.logical(List.of(returning(U,"s0",List.of())));var u=p.units().getFirst();
        var alias=new ObjectId(U,"alias");var objects=new ArrayList<>(u.objects());
        objects.add(new Memory.ObjectDeclaration(alias,Optional.of("whole"),Types.known(Types.Builtin.TEXT),new Memory.AliasBinding(WHOLE),Memory.Visibility.PRIVATE,origin(P),Evidence.CoverageStatus.MODELED,header(U,"meta").precision()));
        p=replace(p,List.of(unit(U,u.entries(),u.sequences(),objects)),p.coverage(),p.uncertainties(),p.premises());
        assertEquals(List.of(),texts(at(run(p),"return-s0",alias)),"alias binding alone is not explicit logical identity");
        var storage=new StorageIndex(session(p));var h=header(U,"projection-only");
        // A syntactic projection deliberately sees only the Place value, never its address subtree.
        var offset=new Expressions.Read(operand(h.id(),"address-read",Operand.Role.ADDRESS_READ),new Places.ObjectPlace(operand(h.id(),"address-object",Operand.Role.ADDRESS_READ),WHOLE));
        var length=new Expressions.Literal(operand(h.id(),"length",Operand.Role.ADDRESS_READ),new Values.IntValue(java.math.BigInteger.valueOf(8)));
        var slice=new Places.RegionSlice(operand(h.id(),"slice",Operand.Role.VALUE_READ),R,offset,length,IBM,Types.known(Types.Builtin.TEXT));
        assertEquals(List.of(),storage.explicitObjects(slice));
        assertEquals(List.of(),storage.explicitObjects(new StorageSubject.PhysicalRange(R,StorageRange.exact(java.math.BigInteger.ZERO,java.math.BigInteger.valueOf(8)),IBM)));
        var object=new Places.ObjectPlace(operand(h.id(),"object",Operand.Role.VALUE_READ),WHOLE);
        var inner=new Places.Choice(operand(h.id(),"inner",Operand.Role.VALUE_READ),List.of(object),Scopes.NoMemory.INSTANCE,Types.known(Types.Builtin.TEXT));
        var outer=new Places.Choice(operand(h.id(),"outer",Operand.Role.VALUE_READ),List.of(inner,slice,object),Scopes.NoMemory.INSTANCE,Types.known(Types.Builtin.TEXT));
        assertEquals(List.of(WHOLE),storage.explicitObjects(outer));
    }
}
