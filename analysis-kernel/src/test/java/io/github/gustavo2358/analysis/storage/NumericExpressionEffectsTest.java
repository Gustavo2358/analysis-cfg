package io.github.gustavo2358.analysis.storage;
import io.github.gustavo2358.air.model.*;
import java.math.BigInteger;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static io.github.gustavo2358.analysis.storage.StorageFixtures.*;
class NumericExpressionEffectsTest {
    @Test void explicitParseRetainsTextAndInvalidFallbackReads() {
        var bases=new ArrayList<Memory.Storage>();var objects=new ArrayList<Memory.ObjectDeclaration>();
        for(var name:List.of("source","fallback","target")) {
            var type=Types.known(name.equals("source")?Types.Builtin.TEXT:Types.Builtin.INT);
            bases.add(new Memory.Cell(new Memory.StorageHeader(base(name),Optional.of(U),Memory.Lifetime.PERSISTENT,Memory.Visibility.PRIVATE,O),type));
            objects.add(new Memory.ObjectDeclaration(object(name),Optional.empty(),type,new Memory.CellBinding(base(name)),Memory.Visibility.PRIVATE,O,Evidence.CoverageStatus.MODELED,header("metadata").precision()));
        }
        var source=new Expressions.Read(operand("parse","text",Operand.Role.VALUE_READ),new Places.ObjectPlace(operand("parse","text-place",Operand.Role.VALUE_READ),object("source")));
        var fallback=new Expressions.Read(operand("parse","fallback",Operand.Role.VALUE_READ),new Places.ObjectPlace(operand("parse","fallback-place",Operand.Role.VALUE_READ),object("fallback")));
        var value=new Expressions.ParseInteger(operand("parse","value",Operand.Role.VALUE_READ),source,fallback);
        var operation=new Operations.Assign(header("parse"),place("parse","target"),value);
        var effects=new StatementEffects(new StorageIndex(session(publication(bases,objects,List.of(sequence("main",List.of(operation))),List.of()))));
        var statement=effects.statement(operation.header().id());
        assertEquals(Set.of(source.header().id(),fallback.header().id()),statement.reads().stream().map(r->r.occurrence().orElseThrow()).collect(java.util.stream.Collectors.toSet()));
        assertEquals(1,statement.writes().size());
    }
    @Test void decimalFitBinaryWrapAndFormattingRetainTheSingleSourceRead() {
        var type=Types.known(Types.Builtin.INT);var bases=new ArrayList<Memory.Storage>();var objects=new ArrayList<Memory.ObjectDeclaration>();
        for(var name:List.of("source","target")) {
            bases.add(new Memory.Cell(new Memory.StorageHeader(base(name),Optional.of(U),Memory.Lifetime.PERSISTENT,Memory.Visibility.PRIVATE,O),name.equals("target")?Types.known(Types.Builtin.TEXT):type));
            objects.add(new Memory.ObjectDeclaration(object(name),Optional.empty(),name.equals("target")?Types.known(Types.Builtin.TEXT):type,new Memory.CellBinding(base(name)),Memory.Visibility.PRIVATE,O,Evidence.CoverageStatus.MODELED,header("metadata").precision()));
        }
        var read=new Expressions.Read(operand("convert","read",Operand.Role.VALUE_READ),new Places.ObjectPlace(operand("convert","source-place",Operand.Role.VALUE_READ),object("source")));
        Expression value=new Expressions.Unary(operand("convert","decimal",Operand.Role.VALUE_READ),Expressions.UnaryOperator.TO_DECIMAL,read);
        for(int n=0;n<32;n++)value=new Expressions.FitDecimal(operand("convert","fit-"+n,Operand.Role.VALUE_READ),value,BigInteger.valueOf(8),BigInteger.TWO,false);
        value=new Expressions.Unary(operand("convert","integer",Operand.Role.VALUE_READ),Expressions.UnaryOperator.TO_INT,value);
        value=new Expressions.WrapInteger(operand("convert","wrap",Operand.Role.VALUE_READ),value,BigInteger.valueOf(16),true);
        value=new Expressions.Unary(operand("convert","format-input",Operand.Role.VALUE_READ),Expressions.UnaryOperator.TO_DECIMAL,value);
        value=new Expressions.FormatDecimal(operand("convert","format",Operand.Role.VALUE_READ),value,
            List.of(new DecimalText.Part(DecimalText.Kind.SUPPRESS_SPACE,BigInteger.valueOf(7),"",""),
                new DecimalText.Part(DecimalText.Kind.DIGITS,BigInteger.ONE,"","")));
        var operation=new Operations.Assign(header("convert"),place("convert","target"),value);
        var effects=new StatementEffects(new StorageIndex(session(publication(bases,objects,List.of(sequence("main",List.of(operation))),List.of()))));
        var statement=effects.statement(operation.header().id());
        assertEquals(1,statement.reads().size());assertEquals(Optional.of(read.header().id()),statement.reads().getFirst().occurrence());
        assertEquals(1,statement.writes().size());assertEquals(StatementEffects.Strength.MUST,statement.writes().getFirst().occurrenceStrength());
        assertTrue(effects.preparationMetrics().get("operandVisits")<50,"work follows expression size");
    }
}
