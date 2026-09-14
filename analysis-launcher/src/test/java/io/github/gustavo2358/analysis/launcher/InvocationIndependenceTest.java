package io.github.gustavo2358.analysis.launcher;

import io.github.gustavo2358.air.json.AirJson;
import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.analysis.adapters.*;
import io.github.gustavo2358.analysis.dependencies.*;
import io.github.gustavo2358.analysis.values.*;
import io.github.gustavo2358.analysis.query.*;
import io.github.gustavo2358.analysis.plan.*;
import java.util.*;
import java.nio.file.*;
import java.io.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** RF-W3: target BEFORE is independent of signature, argument and normal-return writes. */
final class InvocationIndependenceTest {
    @TempDir Path dir;
    static Publication fixture(boolean computed,String shape) throws Exception {
        var p=new AirJson().decode(Files.readAllBytes(Path.of("../analysis-adapters/src/test/resources/cp6/dynamic-x8.air.json")));
        var u=p.units().getFirst();var i=(Operations.Invoke)u.sequences().stream().map(Sequence::terminator).filter(Operations.Invoke.class::isInstance).findFirst().orElseThrow();
        var name=(Interactions.ComputedTarget)i.target();var object=((Places.ObjectPlace)((Expressions.Read)name.name()).place()).object();
        var header=i.header();var owner=new OperationOwner(header.id());var origin=header.origin();
        Interactions.Target target=computed?name:new Interactions.LiteralTarget(name.category(),name.namespace(),"PROGA",name.namePolicy(),name.origin());
        var args=new ArrayList<Interactions.Argument>();var results=new ArrayList<Place>();var objects=new ArrayList<>(u.objects());
        var gap=p.uncertainties().stream().filter(g->g.dimensions().contains(Evidence.Dimension.VALUES)&&g.scope() instanceof Scopes.UnitScope).findFirst().orElseThrow().id();
        if(shape.equals("argument"))args.add(new Interactions.ReferenceArgument(new Places.ObjectPlace(new Operand.Header(new OperandId(owner,"rf-arg"),Operand.Role.ARGUMENT_REFERENCE,origin),object)));
        if(shape.equals("unknown-argument"))args.add(new Interactions.ValueArgument(new Expressions.Unknown(new Operand.Header(new OperandId(owner,"rf-arg"),Operand.Role.ARGUMENT_VALUE,origin),Types.known(Types.Builtin.TEXT),List.of(),new Scopes.WithinMemory(new Scopes.AllMemory(p.id(),true)),gap)));
        if(shape.contains("result")) {
            ObjectId destination=object;
            if(shape.equals("unknown-result")) {
                destination=new ObjectId(u.id(),"rf-unbound-result");var template=objects.getFirst();
                objects.add(new Memory.ObjectDeclaration(destination,Optional.empty(),Types.known(Types.Builtin.TEXT),new Memory.UnknownBinding(new Scopes.AllMemory(p.id(),true),gap),Memory.Visibility.UNKNOWN,origin,template.coverage(),template.precision()));
            }
            results.add(new Places.ObjectPlace(new Operand.Header(new OperandId(owner,"rf-result"),Operand.Role.RESULT_TARGET,origin),destination));
        }
        var updated=new Operations.Invoke(header,i.action(),target,args,results,i.signature(),i.effectOperands(),i.effectBound(),i.outcomes(),i.contract());
        var unit=new io.github.gustavo2358.air.model.Unit(u.id(),u.containingUnit(),objects,u.visibleObjects(),u.entries(),u.sequences().stream().map(s->s.terminator()==i?new Sequence(s.label(),s.instructions(),updated,s.origin()):s).toList(),u.completionPorts(),u.body(),u.bodyUnavailable(),u.coverage(),u.origin());
        return new Publication(p.id(),p.airVersion(),p.capabilities(),p.artifacts(),List.of(unit),p.storage(),p.resources(),p.artifactRelations(),p.origins(),p.coverage(),p.uncertainties(),p.premises());
    }
    @Test void memoryEightShapesPreserveTargetAndRemainder() throws Exception {
        for(boolean computed:List.of(false,true))for(String shape:List.of("none","argument","result",computed?"unknown-result":"unknown-argument")) {
            var p=fixture(computed,shape);assertTarget(p,computed+":"+shape);
        }
    }
    static DependencyResult assertTarget(Publication p,String label) {
        var result=new DependencyAnalysis().prepare(p);var fact=result.sites().getFirst();
        assertEquals(List.of("PROGA"),fact.candidates().stream().map(DependencySiteFact.Candidate::referenceName).toList(),label);
        assertEquals(DependencySiteFact.TargetStatus.RESOLVED_CANDIDATES,fact.targetStatus(),label);
        assertTrue(fact.effectiveUnknownRemainder(),label);assertEquals(1,result.edges().size(),label);
        if(fact.targetKind()==DependencySiteFact.TargetKind.COMPUTED)assertEquals(ProgramPoint.Kind.BEFORE,fact.valuePoint().kind());
        return result;
    }
    @Test void jsonCliEightShapesRetainOperandsAndEqualMemory() throws Exception {
        for(boolean computed:List.of(false,true))for(String shape:List.of("none","argument","result",computed?"unknown-result":"unknown-argument")) {
            var p=fixture(computed,shape);var result=assertTarget(p,computed+":"+shape);
            var wire=new AirJson().encode(p);assertEquals(p,new AirJson().decode(wire));
            var input=dir.resolve("input");var output=dir.resolve("dependencies.json");Files.write(input,wire);
            var errors=new ByteArrayOutputStream();assertEquals(0,AnalysisDependencies.run(new String[]{input.toString(),output.toString()},new PrintStream(errors)),errors.toString());
            var expected=new ByteArrayOutputStream();new DependencyJson().write(result,expected);assertArrayEquals(expected.toByteArray(),Files.readAllBytes(output));
        }
    }
}
