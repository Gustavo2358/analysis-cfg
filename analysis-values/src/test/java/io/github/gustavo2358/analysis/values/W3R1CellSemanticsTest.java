package io.github.gustavo2358.analysis.values;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.analysis.query.*;
import io.github.gustavo2358.analysis.storage.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static io.github.gustavo2358.analysis.values.ValuesFixtures.*;

/** Executable controls for distinct names sharing one positive logical Cell. */
class W3R1CellSemanticsTest {
    private static StorageValueFact value(Publication p,String operation,ObjectId object) {
        var admission=RegionalValuesAnalysis.prepare(session(p),StorageAnalysisMode.LOGICAL_ONLY);
        assertEquals(RegionalValuesAnalysis.Status.ACCEPTED,admission.status(),admission.reason());
        var unit=p.units().getFirst();var point=ProgramPoint.before(unit.entries().getFirst().id(),new OperationId(unit.id(),operation));
        var query=new PointQuery<StorageSubject>(point,new StorageSubject.NamedObject(object));
        return admission.analysis().orElseThrow().execute().observeStorage(List.of(query)).observations().getFirst().value();
    }
    private static List<String> names(StorageValueFact value) {
        return value.candidates().stream().map(Values.TextValue::value).toList();
    }
    @Test void mandatoryWritesThroughEitherViewStronglyReplaceTheSharedCell() {
        var p=graph(new String[]{"PROGA","PROGB",null},new int[][]{{1},{2},{}},2,false,false);
        var unit=p.units().getFirst();var a=unit.objects().get(0);var b=unit.objects().get(1);
        assertNotEquals(a.id(),b.id());assertEquals(a.storage(),b.storage());
        assertEquals(List.of("PROGB"),names(value(p,"return-s2",a.id())));
        assertEquals(List.of("PROGB"),names(value(p,"return-s2",b.id())));
    }
    @Test void trueMustAndMayUnknownRemainDifferentAcrossSharedViews() {
        for(boolean may:List.of(false,true)) {
            var p=graph(new String[]{"PROGA",null},new int[][]{{1},{}},2,false,false);var unit=p.units().getFirst();
            var a=unit.objects().get(0).id();var b=unit.objects().get(1).id();var gap=new UncertaintyId(p.id(),"external-unknown");
            var uncertainty=new Evidence.Uncertainty(gap,"EXTERNAL_UNKNOWN",List.of(Evidence.Dimension.EFFECTS,Evidence.Dimension.VALUES),
                new Scopes.UnitScope(unit.id()),"true external input",origin(p.id()));
            var h=header(unit.id(),"external");
            Instruction effect=may?new Operations.HavocMay(h,new Scopes.ObjectsMemory(List.of(b)),gap)
                :new Operations.HavocMust(h,new Places.ObjectPlace(operand(h.id(),"destination",Operand.Role.VALUE_WRITE),b),gap);
            var sequences=new ArrayList<>(unit.sequences());var last=sequences.get(1);
            sequences.set(1,new Sequence(last.label(),List.of(effect),last.terminator(),last.origin()));
            var changed=replace(p,List.of(unit(unit.id(),unit.entries(),sequences,unit.objects())),p.coverage(),List.of(uncertainty),p.premises());
            var observed=value(changed,"return-s1",a);
            assertEquals(may?List.of("PROGA"):List.of(),names(observed));
            assertTrue(observed.modelValueRemainder());
        }
    }
    @Test void copyToAnotherCellIsSnapshotEvenWhenSourceChanges() {
        var p=graph(new String[]{"PROGA",null,null,null},new int[][]{{1},{2},{3},{}},3,true,false);var unit=p.units().getFirst();
        var source=unit.objects().get(0);var target=unit.objects().get(1);
        assertNotEquals(source.storage(),target.storage());
        var h=header(unit.id(),"copy");
        var copy=new Operations.Assign(h,new Places.ObjectPlace(operand(h.id(),"destination",Operand.Role.VALUE_WRITE),target.id()),
            new Expressions.Read(operand(h.id(),"value",Operand.Role.VALUE_READ),
                new Places.ObjectPlace(operand(h.id(),"source",Operand.Role.VALUE_READ),source.id())));
        var sequences=new ArrayList<>(unit.sequences());var second=sequences.get(1);var third=sequences.get(2);
        sequences.set(1,new Sequence(second.label(),List.of(copy),second.terminator(),second.origin()));
        sequences.set(2,new Sequence(third.label(),List.of(assign(unit.id(),"overwrite",source.id(),"PROGB")),third.terminator(),third.origin()));
        var changed=replace(p,List.of(unit(unit.id(),unit.entries(),sequences,unit.objects())),p.coverage(),p.uncertainties(),p.premises());
        assertEquals(List.of("PROGA"),names(value(changed,"return-s3",target.id())));
        assertEquals(List.of("PROGB"),names(value(changed,"return-s3",source.id())));
    }
}
