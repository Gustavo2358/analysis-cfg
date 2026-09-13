package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.air.json.AirJson;
import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.analysis.cfg.application.*;
import io.github.gustavo2358.analysis.cfg.domain.ProjectionPolicy;
import io.github.gustavo2358.analysis.cfg.extension.SemanticInterpreterRegistry;
import io.github.gustavo2358.analysis.query.*;
import io.github.gustavo2358.analysis.rd.*;
import io.github.gustavo2358.analysis.storage.*;
import io.github.gustavo2358.analysis.structure.AnalysisSession;
import java.math.BigInteger;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Real file composition belongs to the outer module; no transport dependency enters the kernel. */
class RegionalFileTest {
    @TempDir Path directory;
    static Memory.ObjectDeclaration view(io.github.gustavo2358.air.model.Unit unit,String name,StorageId base,long offset,long length) {
        return new Memory.ObjectDeclaration(new ObjectId(unit.id(),name),Optional.empty(),Types.known(Types.Builtin.BYTES),new Memory.ViewBinding(base,BigInteger.valueOf(offset),BigInteger.valueOf(length),Memory.IdentityBytes.INSTANCE),Memory.Visibility.PRIVATE,unit.origin(),Evidence.CoverageStatus.MODELED,unit.objects().getFirst().precision());
    }
    static Operations.Assign assign(io.github.gustavo2358.air.model.Unit unit,String name,ObjectId destination,int... bytes) {
        var old=unit.sequences().getFirst().instructions().getFirst().header();var id=new OperationId(unit.id(),name);var owner=new OperationOwner(id);
        var header=new Operations.Header(id,old.origin(),old.coverage(),old.precision(),old.uncertainties());
        var place=new Places.ObjectPlace(new Operand.Header(new OperandId(owner,"destination"),Operand.Role.VALUE_WRITE,old.origin()),destination);
        var literal=new Expressions.Literal(new Operand.Header(new OperandId(owner,"value"),Operand.Role.VALUE_READ,old.origin()),new Values.BytesValue(Arrays.stream(bytes).boxed().toList()));
        return new Operations.Assign(header,place,literal);
    }
    @Test void realAirFilePreservesPhysicalOffsetsAndSurvivingDefinitions() throws Exception {
        var codec=new AirJson();Publication p;
        try(var input=getClass().getResourceAsStream("/air/regional.canonical.json")){p=codec.decode(Objects.requireNonNull(input).readAllBytes());}
        var old=p.units().getFirst();var base=p.storage().getFirst().header().id();var all=view(old,"all-eight",base,0,8);var right=view(old,"right-four",base,4,4);
        var sequence=old.sequences().getFirst();var second=assign(old,"d2",right.id(),87,88,89,90);
        var instructions=new ArrayList<>(sequence.instructions());instructions.add(assign(old,"d1",all.id(),65,66,67,68,69,70,71,72));instructions.add(second);
        var body=new Sequence(sequence.label(),instructions,sequence.terminator(),sequence.origin());
        var objects=new ArrayList<>(old.objects());objects.add(all);objects.add(right);
        var unit=new io.github.gustavo2358.air.model.Unit(old.id(),old.containingUnit(),objects,old.visibleObjects(),old.entries(),List.of(body),old.completionPorts(),old.body(),old.bodyUnavailable(),old.coverage(),old.origin());
        p=new Publication(p.id(),p.airVersion(),p.capabilities(),p.artifacts(),List.of(unit),p.storage(),p.resources(),p.artifactRelations(),p.origins(),p.coverage(),p.uncertainties(),p.premises());
        var validation=io.github.gustavo2358.air.validation.AirValidator.validate(p);assertEquals(io.github.gustavo2358.air.validation.ValidationResult.Status.STRUCTURALLY_VALID,validation.status(),validation.toString());
        var path=directory.resolve("regional.air.json");Files.write(path,codec.encode(p));var restored=codec.decode(Files.readAllBytes(path));assertEquals(p,restored);assertArrayEquals(Files.readAllBytes(path),codec.encode(restored));
        var built=new CfgBuildCoordinator(SemanticInterpreterRegistry.empty()).build(restored,BuildOptions.defaults());assertEquals(CfgBuildResult.Status.CFG_BUILT,built.status());
        var session=AnalysisSession.open(built,restored,ProjectionPolicy.KNOWN_SUBSET,restored.units().getFirst().entries()).session().orElseThrow();
        var run=new ReachingDefinitions(new StatementEffects(new StorageIndex(session))).execute();
        var query=new PointQuery<>(ProgramPoint.after(unit.entries().getFirst().id(),second.header().id()),all.id());var batch=run.observe(List.of(query));
        assertEquals(ObservationBatch.QueryStatus.VALUE,batch.observations().getFirst().status());var result=batch.observations().getFirst().value();
        var contributions=new TreeSet<String>();
        for(var definition:result.definitions())for(var location:definition.contributedRanges()) {
            var range=location.location().range().orElseThrow();contributions.add(definition.definition().operation().orElseThrow().localId()+":"+range.start()+".."+range.end().orElseThrow());
        }
        assertEquals(Set.of("d1:0..4","d2:4..8"),contributions);assertFalse(result.unknownRemainder());assertTrue(result.sourceUnknownRemainder());
        System.out.println("ST_RD_FILE "+result);
    }
}
