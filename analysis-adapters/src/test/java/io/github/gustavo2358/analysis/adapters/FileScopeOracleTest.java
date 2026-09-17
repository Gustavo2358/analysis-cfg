package io.github.gustavo2358.analysis.adapters;
import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.air.json.AirJson;
import io.github.gustavo2358.analysis.dependencies.*;
import java.util.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static io.github.gustavo2358.analysis.adapters.ResourceBindingOracle.*;
/** Manual scope oracle, independent of COBOL/SP/lower and source program names. */
final class FileScopeOracleTest {
    static Operations.Invoke invoke(String unit,String name,String action,Interactions.Target target,LabelId next){return new Operations.Invoke(header(new OperationId(unit(unit),name)),action,target,List.of(),List.of(),new Interactions.ExternalSignature(signature()),List.of(),new Interactions.EffectBound(new Interactions.ForeignEffects(Scopes.NoMemory.INSTANCE,Scopes.NoMemory.INSTANCE,List.of()),List.of()),new Control.InvocationOutcomes(List.of(new Control.Normal(next)),Scopes.NoControl.INSTANCE),new Interactions.UnknownContract(GAP));}
    static Publication manual(){
        var p=publication("A3");var units=new ArrayList<Unit>();
        for(var u:p.units()){
            boolean child=u.id().equals(unit("U2"));var objects=new ArrayList<>(u.objects());
            if(child){var o=objects.getFirst();objects.set(0,new Memory.ObjectDeclaration(o.id(),o.displayName(),o.typeRef(),new Memory.AliasBinding(object("U1","record")),o.visibility(),o.origin(),o.coverage(),o.precision()));}
            var name=child?"U2":"U1";var label=new LabelId(u.id(),"call");var sequences=new ArrayList<Sequence>();
            var call=invoke(name,"call","call",new Interactions.LiteralTarget("program","cobol.program",child?"LEAF":"CHILD",Interactions.ExactName.INSTANCE,ORIGIN),u.sequences().getFirst().label());
            sequences.add(new Sequence(label,List.of(),call,ORIGIN));
            if(child){var read=invoke(name,"read","read",target("CLIENTDD"),label);label=new LabelId(u.id(),"read");sequences.add(0,new Sequence(label,List.of(),read,ORIGIN));}
            sequences.addAll(u.sequences());var old=u.entries().getFirst();var entry=new Entries.Entry(old.id(),Optional.of(label),old.signature(),old.state(),old.origin());
            units.add(new Unit(u.id(),child?Optional.of(unit("U1")):Optional.empty(),objects,child?List.of(object("U1","record")):List.of(),List.of(entry),sequences,u.completionPorts(),u.body(),u.bodyUnavailable(),u.coverage(),u.origin()));
        }
        var resource=declaration("U1-F","U1","F",target("CLIENTDD"),"cobol.fd",List.of(new Interactions.ResourceUse(new OperationId(unit("U2"),"read"),"input",ORIGIN)));
        return new Publication(p.id(),p.airVersion(),p.capabilities(),p.artifacts(),units,p.storage(),List.of(resource,p.resources().get(1)),p.artifactRelations(),p.origins(),p.coverage(),p.uncertainties(),p.premises());
    }
    @Test void declarationOwnerAndUseOwnerDifferWithoutTransitiveCallOrFile()throws Exception {
        var p=manual();var codec=new AirJson();assertEquals(p,codec.decode(codec.encode(p)));var result=new DependencyAnalysis().prepare(p);
        var f=result.fileDependencies();assertEquals(2,f.declarations().size());assertEquals(1,f.sites().size());assertEquals(unit("U2"),f.sites().getFirst().owner());assertEquals(new ResourceId(PUB,"U1-F"),f.sites().getFirst().bindings().getFirst().declaration());
        assertTrue(f.declarations().stream().anyMatch(d->d.id().localId().equals("U1-F")&&d.owner().equals(unit("U1"))));
        assertEquals(Set.of("U1:CHILD","U2:LEAF"),result.edges().stream().map(e->e.caller().localId()+":"+e.candidate().referenceName()).collect(java.util.stream.Collectors.toSet()));
        assertTrue(f.edges().stream().allMatch(e->e.owner().equals(unit("U2"))));
        var out=Path.of("target/fd-w9-manual");Files.createDirectories(out);new DependencyFileWriter().write(result,out.resolve("scope.json"));
    }
    @Test void enumerationOrderCannotReassignUses() {
        var p=manual();var units=new ArrayList<>(p.units());Collections.reverse(units);var resources=new ArrayList<>(p.resources());Collections.reverse(resources);
        var reversed=new Publication(p.id(),p.airVersion(),p.capabilities(),p.artifacts(),units,p.storage(),resources,p.artifactRelations(),p.origins(),p.coverage(),p.uncertainties(),p.premises());
        var a=new DependencyAnalysis().prepare(p);var b=new DependencyAnalysis().prepare(reversed);
        assertEquals(a.sites(),b.sites());assertEquals(a.edges(),b.edges());assertEquals(a.fileDependencies(),b.fileDependencies());
    }
}
