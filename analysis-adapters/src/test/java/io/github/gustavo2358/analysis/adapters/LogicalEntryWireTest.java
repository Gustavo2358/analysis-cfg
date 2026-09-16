package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.air.json.AirJson;
import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.analysis.dataflow.RegionalAnalysis;
import io.github.gustavo2358.analysis.query.*;
import io.github.gustavo2358.analysis.storage.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static io.github.gustavo2358.analysis.adapters.ResultFixtures.*;
import static io.github.gustavo2358.analysis.adapters.RegionalWireTest.*;

/** Logical facts have source support, never a fabricated physical interval. */
class LogicalEntryWireTest {
    @Test void sourcePossibilityRoundtripsWithoutPhysicalStorage() throws Exception {
        var p=fixture();var gap=new UncertaintyId(P,"unknown-binding");var e=entry(U,"entry","body");var owner=new EntryOwner(e.id());
        var place=new Places.ObjectPlace(new Operand.Header(new OperandId(owner,"place"),Operand.Role.VALUE_WRITE,origin(P)),WHOLE);
        var literal=new Expressions.Literal(new Operand.Header(new OperandId(owner,"literal"),Operand.Role.VALUE_READ,origin(P)),new Values.TextValue("PROGA"));
        e=new Entries.Entry(e.id(),e.initialLabel(),e.signature(),new Entries.EntryState(List.of(new Entries.InitialCondition(place,new Entries.PossibleLiterals(List.of(literal),gap),origin(P),List.of())),List.of()),e.origin());
        var object=new Memory.ObjectDeclaration(WHOLE,Optional.empty(),Types.known(Types.Builtin.TEXT),new Memory.UnknownBinding(new Scopes.AllMemory(P,true),gap),Memory.Visibility.UNKNOWN,origin(P),Evidence.CoverageStatus.ABSTRACTED,header(U,"meta").precision());
        var unit=unit(U,List.of(e),List.of(returning(U,"body",List.of())),List.of(object));
        var uncertainty=new Evidence.Uncertainty(gap,"PARTIAL_CONSTRUCT",List.of(Evidence.Dimension.STORAGE,Evidence.Dimension.VALUES),new Scopes.UnitScope(U),"unproved physical representation and entry content",origin(P));
        p=new Publication(P,p.airVersion(),new Capabilities.Manifest(List.of(Capabilities.ENTRY_POSSIBILITIES_V2),List.of()),p.artifacts(),List.of(unit),List.of(),p.resources(),p.artifactRelations(),p.origins(),p.coverage(),List.of(uncertainty),List.of());
        var validation=io.github.gustavo2358.air.validation.AirValidator.validate(p);
        assertTrue(validation.issues().isEmpty(),validation.toString());
        var codec=new AirJson();assertEquals(p,codec.decode(codec.encode(p)));
        var query=new PointQuery<StorageSubject>(ProgramPoint.before(e.id(),new OperationId(U,"return-body")),new StorageSubject.NamedObject(WHOLE));
        var result=new RegionalAnalysis().prepare(p,"logical-source",List.of(query));var observation=result.observations().getFirst();
        assertEquals(List.of(new Values.TextValue("PROGA")),observation.values().value().candidates());
        assertTrue(observation.values().value().modelValueRemainder());
        assertTrue(observation.rd().value().definitions().stream().allMatch(d->d.contributedRanges().isEmpty()));
        assertEquals(1,observation.values().value().logicalAlternatives().size());
        var bytes=encode(result);assertArrayEquals(bytes,encode(new RegionalAnalysis().prepare(p,"logical-source",List.of(query))));
        retained(result);
        var out=Path.of("target/regional-wire");Files.createDirectories(out);Files.write(out.resolve("logical.result.json"),bytes);
        // Independent logical-to-physical counterproof: a possible source is captured once.
        var h=header(U,"logical-copy");
        var read=new Expressions.Read(operand(h.id(),"read",Operand.Role.VALUE_READ),new Places.ObjectPlace(operand(h.id(),"source",Operand.Role.VALUE_READ),WHOLE));
        var fit=new Expressions.FitText(operand(h.id(),"fit",Operand.Role.VALUE_READ),read,java.math.BigInteger.valueOf(8)," ");
        var copy=new Operations.Assign(h,new Places.ObjectPlace(operand(h.id(),"destination",Operand.Role.VALUE_WRITE),DEST),fit);
        var dest=view(DEST,Y,0,8);var storage=fixture().storage().stream().filter(b->b.header().id().equals(Y)).toList();
        var sequences=List.of(returning(U,"body",List.of(copy,assign(U,"later-source",WHOLE,"NEWVALUE"),assign(U,"later-dest",DEST,"ONLYNEXT"))));
        var copied=new Publication(P,p.airVersion(),new Capabilities.Manifest(List.of(Capabilities.ENTRY_POSSIBILITIES_V2,Capabilities.MEMORY_REGIONS),List.of()),p.artifacts(),List.of(unit(U,List.of(e),sequences,List.of(object,dest))),storage,p.resources(),p.artifactRelations(),p.origins(),p.coverage(),p.uncertainties(),List.of());
        var beforeCopy=new PointQuery<StorageSubject>(ProgramPoint.before(e.id(),new OperationId(U,"later-source")),new StorageSubject.NamedObject(DEST));
        var afterSource=new PointQuery<StorageSubject>(ProgramPoint.before(e.id(),new OperationId(U,"later-dest")),new StorageSubject.NamedObject(DEST));
        var afterMust=new PointQuery<StorageSubject>(ProgramPoint.before(e.id(),new OperationId(U,"return-body")),new StorageSubject.NamedObject(DEST));
        var captured=new RegionalAnalysis().preparePartial(copied,"logical-copy",List.of(beforeCopy,afterSource,afterMust));
        for(var o:captured.observations()) {
            var f=o.values().value();
            if(o.query().point().equals(afterMust.point()))assertEquals(List.of(new Values.TextValue("ONLYNEXT")),f.candidates());
            else {
                assertTrue(f.candidates().contains(new Values.TextValue("PROGA   ")));assertTrue(f.modelValueRemainder());
                var fragment=f.alternatives().stream().filter(x->x.candidate().equals(Optional.of(new Values.TextValue("PROGA   ")))).findFirst().orElseThrow().fragments().getFirst();
                assertEquals(WHOLE,fragment.logicalCapture().orElseThrow().object());
                assertEquals(ProgramPoint.before(e.id(),h.id()),fragment.logicalCapture().orElseThrow().before());
                assertTrue(fragment.captures().isEmpty(),"no invented physical source interval");
            }
        }
        var copyBytes=encode(captured);assertArrayEquals(copyBytes,encode(new RegionalAnalysis().preparePartial(copied,"logical-copy",List.of(afterMust,afterSource,beforeCopy))));
        Files.write(out.resolve("logical-copy.result.json"),copyBytes);retained(captured);

    }
}
