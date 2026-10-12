package io.github.gustavo2358.analysis.values;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import java.math.BigInteger;
import java.util.*;
import org.junit.jupiter.api.Test;
import static io.github.gustavo2358.analysis.values.ValuesFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

/** Decision-phase precedence and the shared, allocation-free assignment guards. */
final class ScalarAdmissionGuardTest {
    @Test void badDemandRefusesBeforeGlobalExpressionOverflow() {
        var p=overflowWithView();var s=session(p);
        var refusal=PossibleValuesAnalysis.prepare(s,PossibleValuesAnalysis.EFFECTS_PROFILE,
            Set.of(p.units().getFirst().objects().get(1).id()));
        assertEquals(PossibleValuesAnalysis.Status.UNSUPPORTED,refusal.status());
        assertEquals("UNSUPPORTED_DEMAND_STORAGE",refusal.reason());
        assertEquals(1,refusal.unsupportedStorageProfiles());assertEquals(0,refusal.unsupportedEffectProfiles());
        assertTrue(refusal.analysis().isEmpty());
    }
    @Test void validDemandPropagatesOverflowEvenForUnselectedWrite() {
        var p=overflowWithView();var s=session(p);
        for(int root:new int[]{0,2})assertThrows(ArithmeticException.class,
            ()->PossibleValuesAnalysis.prepare(s,PossibleValuesAnalysis.EFFECTS_PROFILE,
                Set.of(p.units().getFirst().objects().get(root).id())));
        // An unsupported profile is decided before any preparation or expression work.
        var refusal=PossibleValuesAnalysis.prepare(s,"not-a-profile",Set.of(p.units().getFirst().objects().get(0).id()));
        assertEquals("UNSUPPORTED_EFFECT_PROFILE",refusal.reason());assertEquals(1,refusal.unsupportedEffectProfiles());
    }
    @Test void demandStorageEligibilityDoesNotCertifyOperationsOrTraverseExpressions() {
        var p=overflowWithView();var s=session(p);var objects=p.units().getFirst().objects();
        assertTrue(PossibleValuesAnalysis.supportsDemandStorage(s,objects.get(0).id()));
        assertFalse(PossibleValuesAnalysis.supportsDemandStorage(s,objects.get(1).id()));
        assertTrue(PossibleValuesAnalysis.supportsDemandStorage(s,objects.get(2).id()));
        assertFalse(PossibleValuesAnalysis.supportsDemandStorage(s,new ObjectId(objects.getFirst().id().unit(),"absent")));
        // A necessary storage guard is never a certificate for full scalar admission.
        assertThrows(ArithmeticException.class,()->PossibleValuesAnalysis.prepare(s,PossibleValuesAnalysis.EFFECTS_PROFILE,Set.of(objects.getFirst().id())));
    }
    @Test void demandedStorageGuardAdmitsOnlyWholeTextOrIntegerCells() {
        var p=graph(new String[]{null},new int[][]{{}},1,true,false);var declaration=p.units().getFirst().objects().getFirst();
        var original=(Memory.Cell)p.storage().getFirst();
        for(var builtin:Types.Builtin.values()) {
            var type=Types.known(builtin);
            var object=new Memory.ObjectDeclaration(declaration.id(),declaration.displayName(),type,declaration.storage(),declaration.visibility(),declaration.origin(),declaration.coverage(),declaration.precision());
            var cell=new Memory.Cell(original.header(),type);
            assertEquals(builtin==Types.Builtin.TEXT||builtin==Types.Builtin.INT,ScalarStorageAdmission.cell(object,cell)!=null,builtin.toString());
        }
        assertNull(ScalarStorageAdmission.cell(null,original));
    }
    @Test void assignmentGuardPreservesDestinationThenValueReasons() {
        var p=graph(new String[]{null},new int[][]{{}},3,true,false);var u=p.units().getFirst();
        var h=header(u.id(),"shape");var a=u.objects().get(0).id();var b=u.objects().get(1).id();
        var location=new TextProfile.Location(0,(Memory.Cell)p.storage().getFirst());
        var subjects=Map.of(a,location);
        var read=new Expressions.Read(operand(h.id(),"read",Operand.Role.VALUE_READ),
            new Places.ObjectPlace(operand(h.id(),"source",Operand.Role.VALUE_READ),b));
        var destination=new Places.ObjectPlace(operand(h.id(),"destination",Operand.Role.VALUE_WRITE),a);
        var copy=new Operations.Assign(h,destination,read);
        assertSame(location,ScalarAssignmentAdmission.destination(copy,subjects));
        assertEquals("UNSUPPORTED_STORAGE_PROFILE",assertThrows(TextProfile.Refusal.class,
            ()->ScalarAssignmentAdmission.source(copy,subjects)).getMessage());
        var missingDestination=new Operations.Assign(h,new Places.ObjectPlace(destination.header(),b),read);
        assertEquals("UNSUPPORTED_STORAGE_PROFILE",assertThrows(TextProfile.Refusal.class,
            ()->ScalarAssignmentAdmission.destination(missingDestination,subjects)).getMessage());
        var bareConcat=new Operations.Assign(h,destination,new Expressions.Binary(operand(h.id(),"concat",Operand.Role.VALUE_READ),
            Expressions.BinaryOperator.CONCAT,new Expressions.Literal(operand(h.id(),"left",Operand.Role.VALUE_READ),new Values.TextValue("A")),
            new Expressions.Literal(operand(h.id(),"right",Operand.Role.VALUE_READ),new Values.TextValue("B"))));
        assertEquals("UNSUPPORTED_EFFECT_PROFILE",assertThrows(TextProfile.Refusal.class,
            ()->ScalarAssignmentAdmission.source(bareConcat,subjects)).getMessage());
    }
    @Test void supportedShapesBorrowLocationWithoutProducingCandidates() {
        var p=graph(new String[]{null},new int[][]{{}},2,true,false);var u=p.units().getFirst();
        var a=u.objects().get(0).id();var b=u.objects().get(1).id();var h=header(u.id(),"shape");
        var location=new TextProfile.Location(0,(Memory.Cell)p.storage().getFirst());
        // Aliases share their exact Cell location; admission does not create a candidate pool.
        var subjects=Map.of(a,location,b,location);var destination=new Places.ObjectPlace(operand(h.id(),"destination",Operand.Role.VALUE_WRITE),a);
        var read=new Expressions.Read(operand(h.id(),"read",Operand.Role.VALUE_READ),new Places.ObjectPlace(operand(h.id(),"source",Operand.Role.VALUE_READ),b));
        assertSame(location,ScalarAssignmentAdmission.source(new Operations.Assign(h,destination,read),subjects));
        assertNull(ScalarAssignmentAdmission.source(assign(u.id(),"literal",a,"A"),subjects));
        var fit=new Expressions.FitText(operand(h.id(),"fit",Operand.Role.VALUE_READ),read,BigInteger.valueOf(8)," ");
        assertNull(ScalarAssignmentAdmission.source(new Operations.Assign(h,destination,fit),subjects));
    }
    static Publication overflowWithView() {
        var p=graph(new String[]{null},new int[][]{{}},3,true,false);var u=p.units().getFirst();
        var region=new StorageId(p.id(),"view-region");var objects=new ArrayList<>(u.objects());var old=objects.get(1);
        objects.set(1,new Memory.ObjectDeclaration(old.id(),old.displayName(),old.typeRef(),
            new Memory.ViewBinding(region,BigInteger.ZERO,BigInteger.valueOf(8),new Memory.ExtensionCodec("text.ebcdic.ibm1047","1",Types.known(Types.Builtin.TEXT))),
            old.visibility(),old.origin(),old.coverage(),old.precision()));
        var storage=new ArrayList<>(p.storage());storage.add(new Memory.Region(new Memory.StorageHeader(region,Optional.of(u.id()),Memory.Lifetime.ACTIVATION,Memory.Visibility.PRIVATE,origin(p.id())),Optional.of(BigInteger.valueOf(8)),Optional.empty()));
        var h=header(u.id(),"oversized-fit");
        var fit=new Expressions.FitText(operand(h.id(),"fit",Operand.Role.VALUE_READ),
            new Expressions.Literal(operand(h.id(),"literal",Operand.Role.VALUE_READ),new Values.TextValue("A")),BigInteger.valueOf(Integer.MAX_VALUE).add(BigInteger.ONE)," ");
        var write=new Operations.Assign(h,new Places.ObjectPlace(operand(h.id(),"destination",Operand.Role.VALUE_WRITE),objects.get(2).id()),fit);
        var seq=u.sequences().getFirst();
        return new Publication(p.id(),p.airVersion(),new Capabilities.Manifest(List.of(Capabilities.MEMORY_REGIONS,Capabilities.IBM1047),List.of()),p.artifacts(),
            List.of(unit(u.id(),u.entries(),List.of(new Sequence(seq.label(),List.of(write),seq.terminator(),seq.origin())),objects)),storage,
            p.resources(),p.artifactRelations(),p.origins(),p.coverage(),p.uncertainties(),p.premises());
    }
}
