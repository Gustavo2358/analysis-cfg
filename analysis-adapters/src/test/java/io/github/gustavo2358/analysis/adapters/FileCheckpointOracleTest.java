package io.github.gustavo2358.analysis.adapters;
import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.air.json.AirJson;
import io.github.gustavo2358.analysis.dependencies.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static io.github.gustavo2358.analysis.adapters.ResultFixtures.*;

/** Independent rule: optional repeated read-only checkpoint cannot change COBOL memory.
 * The adversary permits writes and must open the value; source external CALL contracts
 * are deliberately absent here so they cannot masquerade as checkpoint writes. */
final class FileCheckpointOracleTest {
    @Test void optionalLoopPreservesExactValueUnlessWritesAreActuallyAllowed()throws Exception {
        for(boolean writes:List.of(false,true)) {
            var model=W1dModelTest.model(1,u->{var obj=new ObjectId(u,"object-0");var o=origin(u.publication());
                var ordinary=W1dModelTest.call(u,"checkpoint","choose",null,false);
                var checkpoint=new Operations.Invoke(ordinary.header(),"checkpoint",new Interactions.LiteralTarget("file","cobol.external-file-name","CHKPT",Interactions.ExactName.INSTANCE,o),ordinary.arguments(),ordinary.results(),ordinary.signature(),List.of(),
                    new Interactions.EffectBound(new Interactions.ForeignEffects(new Scopes.WithinMemory(new Scopes.VisibleMemory(u,true)),writes?new Scopes.WithinMemory(new Scopes.VisibleMemory(u,true)):Scopes.NoMemory.INSTANCE,List.of()),List.of()),ordinary.outcomes(),ordinary.contract());
                return List.of(new Sequence(new LabelId(u,"start"),List.of(assign(u,"seed",obj,"SAFE0001")),new Operations.Jump(header(u,"go"),new LabelId(u,"choose")),o),
                    branch(u,"choose","checkpoint","call"),new Sequence(new LabelId(u,"checkpoint"),List.of(),checkpoint,o),
                    new Sequence(new LabelId(u,"call"),List.of(),W1dModelTest.call(u,"probe","end",obj,false),o),returning(u,"end",List.of()));});
            var u=model.units().getFirst();var gap=new UncertaintyId(model.id(),"checkpoint-occurrence");
            var sequences=u.sequences().stream().map(q->{if(!(q.terminator() instanceof Operations.Branch b))return q;
                return new Sequence(q.label(),q.instructions(),new Operations.Branch(b.header(),new Expressions.Unknown(operand(b.header().id(),"predicate",Operand.Role.PREDICATE),Types.known(Types.Builtin.BOOL),List.of(),Scopes.NoMemory.INSTANCE,gap),b.trueDestination(),b.falseDestination()),q.origin());}).toList();
            var artifact=new ArtifactId(model.id(),"manual-checkpoint");
            var p=new Publication(model.id(),model.airVersion(),model.capabilities(),List.of(new Origins.Artifact(artifact,"FileCheckpointOracleTest.java",Optional.empty())),List.of(unit(u.id(),u.entries(),sequences,u.objects())),model.storage(),model.resources(),model.artifactRelations(),List.of(new Origins.Written(origin(model.id()),artifact,Optional.empty(),List.of(),true)),model.coverage(),List.of(new Evidence.Uncertainty(gap,"CHECKPOINT_COUNT_NOT_PROVEN",List.of(Evidence.Dimension.CONTROL),new Scopes.UnitScope(u.id()),"zero or more checkpoints",origin(model.id()))),model.premises());
            var codec=new AirJson();var bytes=codec.encode(p);assertEquals(p,codec.decode(bytes));
            var result=new DependencyAnalysis().prepare(codec.decode(bytes));var call=result.sites().getFirst();
            assertEquals(List.of("SAFE0001"),call.candidates().stream().map(DependencySiteFact.Candidate::referenceName).toList());assertEquals(writes,call.modelValueRemainder());
            assertEquals(List.of("seed"),call.candidates().getFirst().supports().stream().map(s->s.producer().localId()).toList());
            assertEquals("CHKPT",result.fileDependencies().sites().getFirst().candidates().getFirst().referenceName());
            var dir=java.nio.file.Path.of("target/fd-w6/manual");java.nio.file.Files.createDirectories(dir);java.nio.file.Files.write(dir.resolve("checkpoint-writes-"+writes+".air.json"),bytes);
        }
    }
}
