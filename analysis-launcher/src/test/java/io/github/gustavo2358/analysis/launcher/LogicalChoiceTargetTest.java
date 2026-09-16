package io.github.gustavo2358.analysis.launcher;

import io.github.gustavo2358.air.json.AirJson;
import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.analysis.adapters.*;
import io.github.gustavo2358.analysis.dependencies.*;
import io.github.gustavo2358.analysis.query.*;
import io.github.gustavo2358.analysis.storage.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Strict AIR → RD/values BEFORE → dependency fact → actual CLI, across query forms. */
class LogicalChoiceTargetTest {
    @TempDir Path dir;
    private static Operand.Header operand(OperandOwner owner,String name,Operand.Role role,OriginId origin) {
        return new Operand.Header(new OperandId(owner,name),role,origin);
    }
    private static Evidence.Coverage coverage(Scopes.FactScope scope) {
        return new Evidence.Coverage(Evidence.InventoryStatus.COMPLETE,scope,List.of(),List.of());
    }
    @Test void equivalentFormsAndMixedAlternativesRetainLogicalSupportInMemoryAndCli() throws Exception {
        for(String variant:List.of("direct","singleton","mixed","open")) {
            var codec=new AirJson();var old=codec.decode(Files.readAllBytes(Path.of("../analysis-adapters/src/test/resources/cp6/dynamic-x8.air.json")));
            var u=old.units().getFirst();var e=u.entries().getFirst();var source=u.objects().getFirst();var seq=u.sequences().getFirst();
            var call=(Operations.Invoke)seq.terminator();var origin=source.origin();var gap=new UncertaintyId(old.id(),"ep-binding");
            var logical=new Memory.ObjectDeclaration(source.id(),source.displayName(),source.typeRef(),new Memory.UnknownBinding(new Scopes.AllMemory(old.id(),true),gap),source.visibility(),origin,Evidence.CoverageStatus.ABSTRACTED,source.precision());
            var physical=new ObjectId(u.id(),"physical");var unknown=new ObjectId(u.id(),"unknown");
            var objects=new ArrayList<Memory.ObjectDeclaration>();objects.add(logical);
            objects.add(new Memory.ObjectDeclaration(physical,Optional.empty(),source.typeRef(),source.storage(),source.visibility(),origin,source.coverage(),source.precision()));
            objects.add(new Memory.ObjectDeclaration(unknown,Optional.empty(),source.typeRef(),logical.storage(),source.visibility(),origin,Evidence.CoverageStatus.ABSTRACTED,source.precision()));
            var entryOwner=new EntryOwner(e.id());var conditions=new ArrayList<Entries.InitialCondition>();
            conditions.add(new Entries.InitialCondition(new Places.ObjectPlace(operand(entryOwner,"logical-place",Operand.Role.VALUE_WRITE,origin),source.id()),
                new Entries.PossibleLiterals(List.of(new Expressions.Literal(operand(entryOwner,"logical-text",Operand.Role.VALUE_READ,origin),new Values.TextValue("PROGA"))),gap),origin,List.of()));
            boolean mixed=variant.equals("mixed")||variant.equals("open");
            if(mixed)conditions.add(new Entries.InitialCondition(new Places.ObjectPlace(operand(entryOwner,"physical-place",Operand.Role.VALUE_WRITE,origin),physical),
                new Entries.LiteralInitial(new Expressions.Literal(operand(entryOwner,"physical-text",Operand.Role.VALUE_READ,origin),new Values.TextValue("PROGB"))),origin,List.of()));
            e=new Entries.Entry(e.id(),e.initialLabel(),e.signature(),new Entries.EntryState(conditions,List.of()),e.origin());
            var opOwner=new OperationOwner(call.header().id());var choices=new ArrayList<Place>();
            choices.add(new Places.ObjectPlace(operand(opOwner,"logical",Operand.Role.VALUE_READ,origin),source.id()));
            if(mixed)choices.add(new Places.ObjectPlace(operand(opOwner,"physical",Operand.Role.VALUE_READ,origin),physical));
            if(variant.equals("open"))choices.add(new Places.ObjectPlace(operand(opOwner,"unknown",Operand.Role.VALUE_READ,origin),unknown));
            Place place=variant.equals("direct")?choices.getFirst():new Places.Choice(operand(opOwner,"choice",Operand.Role.VALUE_READ,origin),choices,Scopes.NoMemory.INSTANCE,Types.known(Types.Builtin.TEXT));
            var read=new Expressions.Read(operand(opOwner,"read",Operand.Role.CALL_TARGET,origin),place);
            var target=(Interactions.ComputedTarget)call.target();
            call=new Operations.Invoke(call.header(),call.action(),new Interactions.ComputedTarget(target.category(),target.namespace(),read,target.namePolicy(),target.origin()),call.arguments(),call.results(),call.signature(),call.effectOperands(),call.effectBound(),call.outcomes(),call.contract());
            var sequences=new ArrayList<>(u.sequences());sequences.set(0,new Sequence(seq.label(),List.of(),call,seq.origin()));
            var unit=new Unit(u.id(),u.containingUnit(),objects,u.visibleObjects(),List.of(e),sequences,u.completionPorts(),u.body(),u.bodyUnavailable(),coverage(new Scopes.UnitScope(u.id())),u.origin());
            var gaps=new ArrayList<>(old.uncertainties());gaps.add(new Evidence.Uncertainty(gap,"UNKNOWN_BINDING",List.of(Evidence.Dimension.STORAGE,Evidence.Dimension.VALUES),new Scopes.UnitScope(u.id()),"fixture logical evidence without physical proof",origin));
            var required=new ArrayList<>(old.capabilities().required());required.add(Capabilities.ENTRY_POSSIBILITIES_V2);
            var p=new Publication(old.id(),old.airVersion(),new Capabilities.Manifest(required,old.capabilities().provided()),old.artifacts(),List.of(unit),old.storage(),old.resources(),old.artifactRelations(),old.origins(),coverage(new Scopes.PublicationScope(old.id())),gaps,old.premises());
            var validation=io.github.gustavo2358.air.validation.AirValidator.validate(p);assertEquals(io.github.gustavo2358.air.validation.ValidationResult.Status.STRUCTURALLY_VALID,validation.status(),validation.toString());
            var bytes=codec.encode(p);assertEquals(p,codec.decode(bytes)); // No partial-admission bypass.
            var point=ProgramPoint.before(e.id(),call.header().id());var occurrence=new StorageSubject.PlaceOccurrence(place.header().id());
            var regional=new io.github.gustavo2358.analysis.dataflow.RegionalAnalysis().prepare(p,"logical-"+variant,List.of(new PointQuery<>(point,occurrence),new PointQuery<>(point,new StorageSubject.NamedObject(source.id()))));
            var observed=regional.observations().stream().filter(o->o.query().subject().equals(occurrence)).findFirst().orElseThrow();
            var expected=mixed?List.of("PROGA","PROGB"):List.of("PROGA");
            assertEquals(expected,observed.values().value().candidates().stream().map(Values.TextValue::value).toList());
            assertTrue(observed.values().value().modelValueRemainder());assertEquals(List.of(source.id()),observed.values().value().logicalAlternatives().stream().map(a->a.object()).toList());
            assertTrue(observed.rd().value().definitions().stream().anyMatch(d->d.definition().logicalObject().equals(Optional.of(source.id()))));
            var dependencies=new DependencyAnalysis().prepare(p);var site=dependencies.sites().getFirst();
            assertEquals(expected,site.candidates().stream().map(DependencySiteFact.Candidate::referenceName).toList());assertEquals(point,site.valuePoint());assertTrue(site.effectiveUnknownRemainder());
            assertTrue(site.candidates().stream().allMatch(c->!c.supports().isEmpty()));
            var input=dir.resolve(variant+".air.json");var output=dir.resolve(variant+".dependencies.json");Files.write(input,bytes);
            var errors=new ByteArrayOutputStream();assertEquals(0,AnalysisDependencies.run(new String[]{input.toString(),output.toString()},new PrintStream(errors)),errors.toString());
            var memory=new ByteArrayOutputStream();new DependencyJson().write(dependencies,memory);assertArrayEquals(memory.toByteArray(),Files.readAllBytes(output));
            var out=Path.of("target/ep-representation");Files.createDirectories(out);Files.write(out.resolve(variant+".air.json"),bytes);
            Files.copy(output,out.resolve(variant+".dependencies.json"),StandardCopyOption.REPLACE_EXISTING);
            try(var stream=Files.newOutputStream(out.resolve(variant+".regional.json"))){new RegionalResultJson().write(regional,stream);}
        }
    }
}
