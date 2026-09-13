package io.github.gustavo2358.analysis.values;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.analysis.cfg.application.*;
import io.github.gustavo2358.analysis.cfg.extension.SemanticInterpreterRegistry;
import io.github.gustavo2358.analysis.query.*;
import java.math.BigInteger;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static io.github.gustavo2358.analysis.values.ValuesFixtures.*;
import static io.github.gustavo2358.analysis.values.RegionalValuesTest.*;
import static io.github.gustavo2358.analysis.values.RegionalCompositionTest.*;

class RegionalTransferTest {
    static final UncertaintyId UNKNOWN=new UncertaintyId(P,"unknown");
    static Publication uncertainty(Publication p) {
        return replace(p,p.units(),p.coverage(),List.of(new Evidence.Uncertainty(UNKNOWN,"UNINTERPRETED_CONTENT",List.of(Evidence.Dimension.VALUES),new Scopes.UnitScope(U),"manual unknown",origin(P))),p.premises());
    }
    static Operations.HavocMust havoc(String id,ObjectId destination) {
        var h=header(U,id);return new Operations.HavocMust(h,new Places.ObjectPlace(operand(h.id(),"destination",Operand.Role.VALUE_WRITE),destination),UNKNOWN);
    }
    static Operations.Assign slice(String id,StorageId base,int offset,String text) {
        var h=header(U,id);var position=new Expressions.Literal(operand(h.id(),"offset",Operand.Role.ADDRESS_READ),new Values.IntValue(BigInteger.valueOf(offset)));
        var extent=new Expressions.Literal(operand(h.id(),"length",Operand.Role.ADDRESS_READ),new Values.IntValue(BigInteger.valueOf(text.length())));
        var destination=new Places.RegionSlice(operand(h.id(),"destination",Operand.Role.VALUE_WRITE),base,position,extent,IBM,Types.known(Types.Builtin.TEXT));
        return new Operations.Assign(h,destination,new Expressions.Literal(operand(h.id(),"literal",Operand.Role.VALUE_READ),new Values.TextValue(text)));
    }
    @Test void unknownMustPrefixCannotKeepWholeCandidateAndLiteralCanRepairIt() {
        var p=uncertainty(regional(List.of(returning(U,"s0",List.of(assign(U,"old",WHOLE,"ABCDEFGH"),havoc("unknown",PREFIX),assign(U,"repair",PREFIX,"WXYZ"))))));
        var execution=run(p);var during=at(execution,"repair",WHOLE);
        assertTrue(during.modelValueRemainder());assertEquals(List.of(),texts(during));assertTrue(during.modelReasons().contains("HAVOC_MUST"));
        var suffix=at(execution,"repair",SUFFIX);assertEquals(List.of("EFGH"),texts(suffix));assertFalse(suffix.modelValueRemainder());
        var after=at(execution,"return-s0",WHOLE);assertEquals(List.of("WXYZEFGH"),texts(after));assertFalse(after.modelValueRemainder());
        assertEquals(Set.of("old","repair"),new HashSet<>(after.candidateSupports().getFirst().producers().stream().map(s->s.evidence().localId()).toList()));
    }
    @Test void scopedMayPreservesOldPossibilityAndUntouchedSuffix() {
        var may=new Operations.HavocMay(header(U,"may"),new Scopes.ObjectsMemory(List.of(PREFIX)),UNKNOWN);
        var execution=run(uncertainty(regional(List.of(returning(U,"s0",List.of(assign(U,"old",WHOLE,"ABCDEFGH"),may))))));
        var whole=at(execution,"return-s0",WHOLE);assertEquals(List.of("ABCDEFGH"),texts(whole));assertTrue(whole.modelValueRemainder());
        var suffix=at(execution,"return-s0",SUFFIX);assertEquals(List.of("EFGH"),texts(suffix));assertFalse(suffix.modelValueRemainder());
        var prefix=at(execution,"return-s0",PREFIX);assertEquals(List.of("ABCD"),texts(prefix));assertTrue(prefix.modelValueRemainder());
    }
    @Test void mandatoryUnknownChoiceAlwaysInvalidatesWholeAndKeepsPossibleHalves() {
        var h=header(U,"choice");var place=new Places.Choice(operand(h.id(),"destination",Operand.Role.VALUE_WRITE),List.of(
            new Places.ObjectPlace(operand(h.id(),"left",Operand.Role.VALUE_WRITE),PREFIX),new Places.ObjectPlace(operand(h.id(),"right",Operand.Role.VALUE_WRITE),SUFFIX)),Scopes.NoMemory.INSTANCE,Types.known(Types.Builtin.TEXT));
        var operation=new Operations.HavocMust(h,place,UNKNOWN);
        var execution=run(uncertainty(regional(List.of(returning(U,"s0",List.of(assign(U,"old",WHOLE,"ABCDEFGH"),operation))))));
        assertEquals(List.of(),texts(at(execution,"return-s0",WHOLE)));assertTrue(at(execution,"return-s0",WHOLE).modelValueRemainder());
        assertEquals(List.of("ABCD"),texts(at(execution,"return-s0",PREFIX)));assertTrue(at(execution,"return-s0",PREFIX).modelValueRemainder());
        assertEquals(List.of("EFGH"),texts(at(execution,"return-s0",SUFFIX)));assertTrue(at(execution,"return-s0",SUFFIX).modelValueRemainder());
    }
    @Test void rangeWritesAndCapturedPartialCopySurviveLaterSourceChanges() {
        var p=twoBases(List.of(returning(U,"s0",List.of(assign(U,"x",WHOLE,"ABCDEFGH"),assign(U,"y",YWHOLE,"12345678"),
            copy("copy-prefix",R,0,Y,0,4),assign(U,"later-x",WHOLE,"XXXXXXXX"),slice("new-suffix",Y,4,"WXYZ")))));
        var execution=run(p);var captured=at(execution,"later-x",YWHOLE);
        assertEquals(List.of("ABCD5678"),texts(captured));assertFalse(captured.modelValueRemainder());
        var finalValue=at(execution,"return-s0",YWHOLE);assertEquals(List.of("ABCDWXYZ"),texts(finalValue));assertFalse(finalValue.modelValueRemainder());
        assertEquals(Set.of("x","new-suffix"),new HashSet<>(finalValue.candidateSupports().getFirst().producers().stream().map(s->s.evidence().localId()).toList()));
        assertEquals(List.of("XXXXXXXX"),texts(at(execution,"return-s0",WHOLE)));
    }
    @Test void selfCopyAndZeroLengthPreserveTextWhileOverlapUsesPreWriteBytes() {
        var execution=run(regional(List.of(returning(U,"s0",List.of(assign(U,"original",WHOLE,"ABCDEFGH"),copy("self",R,0,R,0,8),copy("zero",R,8,R,8,0),copy("overlap",R,4,R,0,4))))));
        assertEquals(List.of("ABCDEFGH"),texts(at(execution,"zero",WHOLE)));
        assertEquals(List.of("ABCDEFGH"),texts(at(execution,"overlap",WHOLE)));
        var after=at(execution,"return-s0",WHOLE);assertEquals(List.of("EFGHEFGH"),texts(after));assertFalse(after.modelValueRemainder());
        assertEquals(List.of("original"),after.candidateSupports().getFirst().producers().stream().map(s->s.evidence().localId()).toList());
        var point=ProgramPoint.after(new EntryId(U,"entry"),new OperationId(U,"overlap"));
        var observed=execution.observe(List.of(new PointQuery<>(point,WHOLE))).observations().getFirst().value();assertEquals(texts(after),texts(observed));
    }
    @Test void copyingUnknownPrefixPreservesOnlyDestinationSuffix() {
        var p=uncertainty(twoBases(List.of(returning(U,"s0",List.of(assign(U,"x",WHOLE,"ABCDEFGH"),assign(U,"y",YWHOLE,"12345678"),
            havoc("unknown",PREFIX),copy("copy",R,0,Y,0,4))))));
        var execution=run(p);var destination=at(execution,"return-s0",YWHOLE);
        assertEquals(List.of(),texts(destination));assertTrue(destination.modelValueRemainder());assertTrue(destination.modelReasons().contains("HAVOC_MUST"));
        // Repair only the overwritten destination prefix; the original suffix must still be present.
        var u=p.units().getFirst();var ins=new ArrayList<>(u.sequences().getFirst().instructions());ins.add(slice("repair",Y,0,"WXYZ"));
        p=replace(p,List.of(unit(U,u.entries(),List.of(returning(U,"s0",ins)),u.objects())),p.coverage(),p.uncertainties(),p.premises());
        var repaired=at(run(p),"return-s0",YWHOLE);assertEquals(List.of("WXYZ5678"),texts(repaired));assertFalse(repaired.modelValueRemainder());
    }
    @Test void uncomputedCopyBoundsKeepValidationLimitAndExplicitOpaqueFallback() {
        var exact=copy("copy",R,0,Y,0,4);var original=exact.source();
        var offset=new Expressions.Unknown(original.offset().header(),Types.known(Types.Builtin.INT),List.of(),Scopes.NoMemory.INSTANCE,UNKNOWN);
        var copy=new Operations.CopyBytes(exact.header(),exact.destination(),new Memory.ByteRange(R,offset,original.extent()),exact.length(),exact.fallback());
        var p=uncertainty(twoBases(List.of(returning(U,"s0",List.of(assign(U,"x",WHOLE,"ABCDEFGH"),assign(U,"y",YWHOLE,"12345678"),copy)))));
        var result=new CfgBuildCoordinator(SemanticInterpreterRegistry.empty()).build(p,BuildOptions.defaults());
        assertEquals(CfgBuildResult.Status.VALIDATION_LIMIT,result.status());assertTrue(result.graph().isEmpty());
        assertTrue(result.preflight().issues().stream().anyMatch(i->i.rule().equals("PRECONDITION_NOT_DISCHARGED")));
        var fallback=new Envelopes.Envelope(copy.fallback().memory(),new Control.ControlEnvelope(List.of(new Control.JumpAlternative(new LabelId(U,"done"))),Scopes.NoControl.INSTANCE),copy.fallback().dependencies());
        var h=header(U,"fallback");var opaque=new Operations.Opaque(new Operations.Header(h.id(),h.origin(),h.coverage(),h.precision(),List.of(UNKNOWN)),"uninterpreted transfer",List.of(),List.of(),fallback);
        p=uncertainty(twoBases(List.of(new Sequence(new LabelId(U,"s0"),List.of(assign(U,"x",WHOLE,"ABCDEFGH"),assign(U,"y",YWHOLE,"12345678")),opaque,origin(P)),returning(U,"done",List.of()))));
        var execution=run(p);var destination=at(execution,"return-done",YWHOLE);
        assertEquals(List.of("12345678"),texts(destination));assertTrue(destination.modelValueRemainder());
        var source=at(execution,"return-done",WHOLE);
        assertEquals(List.of("ABCDEFGH"),texts(source));assertFalse(source.modelValueRemainder());
    }
    @Test void contradictoryCopyExtentAndOutOfBoundsAreRejectedBeforeAnalysis() {
        var good=copy("copy",R,0,Y,0,4);
        var tooShort=new Expressions.Literal(good.source().extent().header(),new Values.IntValue(BigInteger.valueOf(3)));
        var contradiction=new Operations.CopyBytes(good.header(),good.destination(),new Memory.ByteRange(R,good.source().offset(),tooShort),good.length(),good.fallback());
        for(var invalid:List.of(contradiction,copy("outside",R,6,Y,0,4))) {
            var p=twoBases(List.of(returning(U,"s0",List.of(invalid))));
            var result=new CfgBuildCoordinator(SemanticInterpreterRegistry.empty()).build(p,BuildOptions.defaults());
            assertEquals(CfgBuildResult.Status.INVALID_IR,result.status());assertTrue(result.graph().isEmpty());
            assertTrue(result.preflight().issues().stream().anyMatch(issue->issue.rule().equals("I-13")));
        }
    }
    @Test void unknownSourceExtentDoesNotBecomeZeroOrAClosedCopy() {
        var p=twoBases(List.of(returning(U,"s0",List.of(assign(U,"y",YWHOLE,"12345678"),copy("copy",R,0,Y,0,4)))));
        var storage=p.storage().stream().map(s->s.header().id().equals(R)?new Memory.Region(s.header(),Optional.empty(),Optional.of(UNKNOWN)):s).toList();
        var gap=new Evidence.Uncertainty(UNKNOWN,"EXTENT_UNKNOWN",List.of(Evidence.Dimension.STORAGE),new Scopes.UnitScope(U),"manual unknown extent",origin(P));
        p=new Publication(P,p.airVersion(),p.capabilities(),p.artifacts(),p.units(),storage,p.resources(),p.artifactRelations(),p.origins(),p.coverage(),List.of(gap),p.premises());
        var result=new CfgBuildCoordinator(SemanticInterpreterRegistry.empty()).build(p,BuildOptions.defaults());
        assertEquals(CfgBuildResult.Status.VALIDATION_LIMIT,result.status());assertTrue(result.graph().isEmpty());
        assertTrue(result.preflight().issues().stream().anyMatch(i->i.rule().equals("PRECONDITION_NOT_DISCHARGED")));
        assertTrue(((Memory.Region)p.storage().stream().filter(s->s.header().id().equals(R)).findFirst().orElseThrow()).extent().isEmpty());
    }
    @Test void destinationRemainderKeepsOpenAlternativesRatherThanChoosingTheKnownPlace() {
        var h=header(U,"partial-choice");var place=new Places.Choice(operand(h.id(),"choice",Operand.Role.VALUE_WRITE),
            List.of(new Places.ObjectPlace(operand(h.id(),"prefix",Operand.Role.VALUE_WRITE),PREFIX)),
            new Scopes.WithinMemory(new Scopes.ObjectsMemory(List.of(SUFFIX))),Types.known(Types.Builtin.TEXT));
        var write=new Operations.Assign(h,place,new Expressions.Literal(operand(h.id(),"literal",Operand.Role.VALUE_READ),new Values.TextValue("WXYZ")));
        var p=regional(List.of(returning(U,"s0",List.of(assign(U,"old",WHOLE,"ABCDEFGH"),write))));
        var proof=new Proofs.Premise(new PremiseId(P,"choice-domain"),"manual contract","both the known place and remaining destination have TEXT domain",origin(P),
            new Proofs.SameDomain(new Proofs.OperandDomain(place.header().id()),new Proofs.ObjectDomain(PREFIX),new Proofs.OperationDomain(h.id())));
        p=replace(p,p.units(),p.coverage(),p.uncertainties(),List.of(proof));
        var value=at(run(p),"return-s0",WHOLE);
        assertEquals(List.of("ABCDEFGH","WXYZEFGH"),texts(value));assertTrue(value.modelValueRemainder());
        assertTrue(value.modelReasons().contains("UNPROVEN_WRITE_DESTINATION"));
    }
    @Test void logicalReadCapturesAComposedRegionalValueBeforeLaterWrite() {
        var cell=new StorageId(P,"logical-copy");var object=new ObjectId(U,"logical-copy");var h=header(U,"read-copy");
        var read=new Expressions.Read(operand(h.id(),"read",Operand.Role.VALUE_READ),new Places.ObjectPlace(operand(h.id(),"source",Operand.Role.VALUE_READ),WHOLE));
        var capture=new Operations.Assign(h,new Places.ObjectPlace(operand(h.id(),"destination",Operand.Role.VALUE_WRITE),object),read);
        var p=regional(List.of(returning(U,"s0",List.of(assign(U,"old",WHOLE,"ABCDEFGH"),assign(U,"prefix",PREFIX,"WXYZ"),capture,assign(U,"later",WHOLE,"XXXXXXXX")))));
        var u=p.units().getFirst();var objects=new ArrayList<>(u.objects());objects.add(new Memory.ObjectDeclaration(object,Optional.empty(),Types.known(Types.Builtin.TEXT),new Memory.CellBinding(cell),Memory.Visibility.PRIVATE,origin(P),Evidence.CoverageStatus.MODELED,h.precision()));
        var storage=new ArrayList<>(p.storage());storage.add(new Memory.Cell(new Memory.StorageHeader(cell,Optional.of(U),Memory.Lifetime.PERSISTENT,Memory.Visibility.PRIVATE,origin(P)),Types.known(Types.Builtin.TEXT)));
        p=new Publication(P,p.airVersion(),p.capabilities(),p.artifacts(),List.of(unit(U,u.entries(),u.sequences(),objects)),storage,p.resources(),p.artifactRelations(),p.origins(),p.coverage(),p.uncertainties(),List.of(new Proofs.Premise(new PremiseId(P,"separate"),"manual contract","independent allocations",origin(P),new Proofs.DisjointStorage(List.of(R,cell)))));
        var execution=run(p);var value=at(execution,"return-s0",object);assertEquals(List.of("WXYZEFGH"),texts(value));assertFalse(value.modelValueRemainder());
        assertEquals(Set.of("old","prefix"),new HashSet<>(value.candidateSupports().getFirst().producers().stream().map(s->s.evidence().localId()).toList()));
        assertEquals(1L,execution.preparationMetrics().get("correlationGroups"));assertEquals(2L,execution.preparationMetrics().get("maxGroupBases"));
    }
}
