package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.air.json.AirJson;
import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.analysis.dependencies.*;
import java.io.ByteArrayOutputStream;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static io.github.gustavo2358.analysis.adapters.ResourceBindingOracle.*;

/** W5 independent AIR oracle: a nominal local use never invents an external name. */
final class FileLocalUseOracleTest {
    static Publication local(boolean opaque,boolean associated) {
        var p=publication("A1");var u=p.units().getFirst();var id=new OperationId(u.id(),"arbitrary-operation");
        var end=new LabelId(u.id(),"end");
        Terminator op=opaque?new Operations.Opaque(header(id),"CALL EXTERNAL-FAKE",List.of(),List.of(),
            new Envelopes.Envelope(new Envelopes.MemoryEnvelope(List.of(),Scopes.NoMemory.INSTANCE,List.of(),Scopes.NoMemory.INSTANCE,List.of()),
                new Control.ControlEnvelope(List.of(new Control.JumpAlternative(end)),Scopes.NoControl.INSTANCE),
                new Envelopes.DependencyEnvelope(List.of(),Scopes.NoResources.INSTANCE))):new Operations.Jump(header(id),end);
        var qs=List.of(new Sequence(u.sequences().getFirst().label(),List.of(),op,ORIGIN),new Sequence(end,List.of(),u.sequences().getFirst().terminator(),ORIGIN));
        var replacement=new io.github.gustavo2358.air.model.Unit(u.id(),u.containingUnit(),u.objects(),u.visibleObjects(),u.entries(),qs,u.completionPorts(),u.body(),u.bodyUnavailable(),u.coverage(),u.origin());
        return replace(p,List.of(replacement),List.of(declaration("work", "U1","S",new Interactions.LocalResource("file"),"cobol.sd",associated?List.of(new Interactions.ResourceUse(id,"release",ORIGIN)):List.of())));
    }
    static Publication replace(Publication p,List<io.github.gustavo2358.air.model.Unit> units,List<Interactions.Resource> resources) {
        var required=new ArrayList<>(p.capabilities().required());if(!required.contains(Capabilities.RESOURCE_BINDINGS))required.add(Capabilities.RESOURCE_BINDINGS);
        return new Publication(p.id(),p.airVersion(),new Capabilities.Manifest(required,p.capabilities().provided()),p.artifacts(),units,p.storage(),resources,p.artifactRelations(),p.origins(),p.coverage(),p.uncertainties(),p.premises());
    }
    @Test void opaqueAndJumpUseTheAssociationNotOperationSpelling()throws Exception {
        for(boolean opaque:List.of(true,false)) {
            var p=local(opaque,true);var codec=new AirJson();assertEquals(p,codec.decode(codec.encode(p)));
            var result=new DependencyAnalysis().prepare(p);var f=result.fileDependencies();
            assertEquals(1,f.sites().size());var s=f.sites().getFirst();
            assertEquals("LOCAL",s.targetKind());assertEquals("resource-use",s.action());assertNull(s.namespace());assertNull(s.valuePoint());
            assertEquals(List.of(),s.candidates());assertFalse(s.unknownRemainder());assertEquals(List.of(),f.edges());
            assertEquals("release",s.bindings().getFirst().role());assertEquals(FileDependencyResult.Reachability.REACHABLE,s.reachability());
            assertTrue(result.sites().isEmpty());assertTrue(result.edges().isEmpty());
            var dir=Path.of("target/fd-w5/manual");Files.createDirectories(dir);Files.write(dir.resolve("local-"+opaque+".air.json"),codec.encode(p));
            var out=new ByteArrayOutputStream();new DependencyJson().write(result,out);Files.write(dir.resolve("local-"+opaque+".json"),out.toByteArray());
        }
        assertTrue(new DependencyAnalysis().prepare(local(true,false)).fileDependencies().sites().isEmpty());
    }
    @Test void localBindingOnCallDoesNotReclassifyItsExternalTarget()throws Exception {
        var p=W1dBoundaryTest.input("literal");var u=p.units().getFirst();
        var call=u.sequences().stream().map(Sequence::terminator).filter(Operations.Invoke.class::isInstance).findFirst().orElseThrow();
        var r=new Interactions.Resource(new ResourceId(p.id(),"work"),new Interactions.LocalResource("file"),call.header().origin(),Optional.of(new Interactions.ResourceDeclaration(u.id(),"S","cobol.sd","cobol.sort-comment",List.of(),List.of(new Interactions.ResourceUse(call.header().id(),"work",call.header().origin())))));
        var before=new DependencyAnalysis().prepare(p);var after=new DependencyAnalysis().prepare(replace(p,p.units(),List.of(r)));
        assertEquals(before.sites(),after.sites());assertEquals(before.edges(),after.edges());
        assertEquals(1,after.fileDependencies().sites().size());assertEquals("LOCAL",after.fileDependencies().sites().getFirst().targetKind());assertTrue(after.fileDependencies().edges().isEmpty());
    }
}
