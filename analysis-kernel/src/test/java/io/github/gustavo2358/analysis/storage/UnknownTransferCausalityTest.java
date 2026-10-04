package io.github.gustavo2358.analysis.storage;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.analysis.rd.*;
import io.github.gustavo2358.analysis.cfg.application.*;
import io.github.gustavo2358.analysis.cfg.domain.*;
import io.github.gustavo2358.analysis.cfg.extension.SemanticInterpreterRegistry;
import io.github.gustavo2358.analysis.structure.AnalysisSession;
import java.math.BigInteger;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static io.github.gustavo2358.analysis.storage.StorageFixtures.*;
import static io.github.gustavo2358.analysis.storage.ReachingDefinitionsTest.*;

/** Value precision is independent from a proved write and its source expression. */
class UnknownTransferCausalityTest {
    private static Expressions.Read read(String op,String name) {
        return new Expressions.Read(operand(op,"read-"+name,Operand.Role.VALUE_READ),
            new Places.ObjectPlace(operand(op,"source-"+name,Operand.Role.VALUE_READ),object(name)));
    }
    private static Operations.Assign transfer(String op,String source,String destination) {
        var value=new Expressions.Unknown(operand(op,"converted",Operand.Role.VALUE_READ),BYTES,
            List.of(read(op,source)),Scopes.NoMemory.INSTANCE,UNKNOWN);
        return new Operations.Assign(uncertainHeader(op),place(op,destination),value);
    }
    private static Set<String> definitions(DefinitionFact fact) {
        return fact.definitions().stream().map(c->c.definition().operation().map(OperationId::localId).orElse("ENTRY")).collect(java.util.stream.Collectors.toSet());
    }
    @Test void unknownValueKeepsCauseAndMustOverwriteAcrossSequentialReceivers() {
        var first=transfer("first","a","b");var second=transfer("second","b","c");
        var cells=List.of("a","b","c").stream().map(name->(Memory.Storage)new Memory.Cell(new Memory.StorageHeader(base(name),Optional.of(U),Memory.Lifetime.ACTIVATION,Memory.Visibility.PRIVATE,O),BYTES)).toList();
        var objects=List.of("a","b","c").stream().map(name->declaration(name,new Memory.CellBinding(base(name)))).toList();
        var p=publication(cells,objects,
            List.of(sequence("s",List.of(assign("source","a",1,2,3,4),assign("old","b",8,8,8,8),first,second))),List.of());
        var effects=new StatementEffects(new StorageIndex(session(p)));var run=new ReachingDefinitions(effects).execute();
        for(var operation:List.of(first,second)) {
            var effect=effects.statement(operation.header().id());assertEquals(1,effect.reads().size());
            assertEquals(StatementEffects.ReadKind.VALUE,effect.reads().getFirst().kind());
            var write=effect.writes().getFirst();assertEquals(StatementEffects.Strength.MUST,write.targets().getFirst().strength());
            assertSame(operation.value(),((StatementEffects.ExpressionSource)write.source()).value());
        }
        assertEquals(Set.of("source"),definitions(fact(run,before("first","a"))));
        assertEquals(Set.of("first"),definitions(fact(run,before("second","b"))));
        assertEquals(Set.of("second"),definitions(fact(run,before("return-s","c"))));
        assertEquals(Set.of("source"),definitions(fact(run,before("return-s","a"))),"independent neighbor preserved");
    }
    @Test void unknownValueAtAlternativeDestinationCannotKillEitherOldDefinition() {
        var choice=new Memory.AlternativesBinding(List.of(new Memory.AliasBinding(object("left")),new Memory.AliasBinding(object("right"))),Scopes.NoMemory.INSTANCE);
        var copy=transfer("copy","source","choice");
        var p=publication(List.of(region("r",12L,Memory.Lifetime.ACTIVATION)),
            List.of(view("source","r",0,4),view("left","r",4,4),view("right","r",8,4),declaration("choice",choice)),
            List.of(sequence("s",List.of(assign("init-source","source",1,2,3,4),assign("old-left","left",5,6,7,8),assign("old-right","right",9,10,11,12),copy))),List.of());
        var effects=new StatementEffects(new StorageIndex(session(p)));var write=effects.statement(copy.header().id()).writes().getFirst();
        assertInstanceOf(StatementEffects.ExpressionSource.class,write.source());assertEquals(2,write.targets().size());
        assertTrue(write.targets().stream().allMatch(t->t.strength()==StatementEffects.Strength.MAY));
        var run=new ReachingDefinitions(effects).execute();
        assertEquals(Set.of("old-left:4..8","copy:4..8"),contributions(fact(run,before("return-s","left"))));
        assertEquals(Set.of("old-right:8..12","copy:8..12"),contributions(fact(run,before("return-s","right"))));
    }
    @Test void unknownTransformPreservesIndexReadSeparatelyFromValueRead() {
        var integer=Types.known(Types.Builtin.INT);
        var indexCell=new Memory.Cell(new Memory.StorageHeader(base("index"),Optional.of(U),Memory.Lifetime.ACTIVATION,Memory.Visibility.PRIVATE,O),integer);
        var index=new Memory.ObjectDeclaration(object("index"),Optional.empty(),integer,new Memory.CellBinding(base("index")),Memory.Visibility.PRIVATE,O,Evidence.CoverageStatus.MODELED,header("metadata").precision());
        var offset=read("indexed","index");
        var size=new Expressions.Literal(operand("indexed","length",Operand.Role.ADDRESS_READ),new Values.IntValue(BigInteger.valueOf(4)));
        var destination=new Places.RegionSlice(operand("indexed","destination",Operand.Role.VALUE_WRITE),base("r"),offset,size,Memory.IdentityBytes.INSTANCE,BYTES);
        var copy=new Operations.Assign(uncertainHeader("indexed"),destination,transfer("indexed","source","ignored").value());
        var p=publication(List.of(region("r",12L,Memory.Lifetime.ACTIVATION),indexCell),List.of(view("source","r",0,4),index),List.of(sequence("s",List.of(copy))),List.of());
        var policy=ProjectionPolicy.PARTIAL_ANALYSIS;
        var build=new CfgBuildCoordinator(SemanticInterpreterRegistry.empty()).build(p,new BuildOptions(io.github.gustavo2358.air.validation.ValidationOptions.defaults(),policy));
        assertEquals(CfgBuildResult.Status.CFG_BUILT,build.status());
        var admitted=AnalysisSession.open(build,p,policy,p.units().getFirst().entries());
        var effects=new StatementEffects(new StorageIndex(admitted.session().orElseThrow()));var effect=effects.statement(copy.header().id());
        assertEquals(Set.of(StatementEffects.ReadKind.VALUE,StatementEffects.ReadKind.ADDRESS),effect.reads().stream().map(StatementEffects.Read::kind).collect(java.util.stream.Collectors.toSet()));
        assertEquals(2,effect.reads().size());assertInstanceOf(StatementEffects.ExpressionSource.class,effect.writes().getFirst().source());
        assertTrue(effect.writes().getFirst().targets().stream().allMatch(t->t.strength()==StatementEffects.Strength.MAY));
    }
}
