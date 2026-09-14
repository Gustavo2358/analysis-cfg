package io.github.gustavo2358.analysis.values;
import io.github.gustavo2358.air.model.*;
import java.math.BigInteger;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static io.github.gustavo2358.analysis.values.ValuesFixtures.*;
import static io.github.gustavo2358.analysis.values.RegionalValuesTest.*;
import static io.github.gustavo2358.analysis.values.RegionalCompositionTest.*;
class RegionalFitTest {
    static Operations.Assign fit(String id,int sourceLength,int destinationLength) {
        var h=header(U,id);
        var source=new Places.RegionSlice(operand(h.id(),"source-place",Operand.Role.VALUE_READ),R,
            new Expressions.Literal(operand(h.id(),"source-offset",Operand.Role.VALUE_READ),new Values.IntValue(BigInteger.ZERO)),
            new Expressions.Literal(operand(h.id(),"source-length",Operand.Role.VALUE_READ),new Values.IntValue(BigInteger.valueOf(sourceLength))),IBM,Types.known(Types.Builtin.TEXT));
        var destination=new Places.RegionSlice(operand(h.id(),"destination",Operand.Role.VALUE_WRITE),Y,
            new Expressions.Literal(operand(h.id(),"destination-offset",Operand.Role.VALUE_READ),new Values.IntValue(BigInteger.ZERO)),
            new Expressions.Literal(operand(h.id(),"destination-length",Operand.Role.VALUE_READ),new Values.IntValue(BigInteger.valueOf(destinationLength))),IBM,Types.known(Types.Builtin.TEXT));
        var read=new Expressions.Read(operand(h.id(),"read",Operand.Role.VALUE_READ),source);
        return new Operations.Assign(h,destination,new Expressions.FitText(operand(h.id(),"fit",Operand.Role.VALUE_READ),read,BigInteger.valueOf(destinationLength)," "));
    }
    @Test void exactFitPreservesOutsideBytesAndCapturesBeforeLaterSourceWrites() {
        for(var example:List.of(List.of(2,4,"AB  YYYY"),List.of(4,2,"ABYYYYYY"),List.of(4,4,"ABCDYYYY"))) {
            var p=twoBases(List.of(returning(U,"s0",List.of(assign(U,"source",WHOLE,"ABCDEFGH"),assign(U,"target",YWHOLE,"YYYYYYYY"),fit("fit",(Integer)example.get(0),(Integer)example.get(1)),assign(U,"later",WHOLE,"XXXXXXXX")))));
            var value=at(run(p),"return-s0",YWHOLE);assertEquals(List.of(example.get(2)),texts(value));assertFalse(value.modelValueRemainder());
        }
    }
    @Test void truncationCanRemoveAnUnknownTailWithoutInventingInputBytes() {
        var p=twoBases(List.of(returning(U,"s0",List.of(assign(U,"known-prefix",PREFIX,"ABCD"),assign(U,"target",YWHOLE,"YYYYYYYY"),fit("fit",8,2)))));
        var value=at(run(p),"return-s0",YWHOLE);assertEquals(List.of("ABYYYYYY"),texts(value));assertFalse(value.modelValueRemainder());
    }
    @Test void branchAlternativesRemainCorrelatedThroughFits() {
        var p=twoBases(List.of(branch(U,"s0","a","b"),
            with(jump(U,"a","join"),assign(U,"a-source",WHOLE,"AAAABBBB"),assign(U,"a-target",YWHOLE,"CCCCDDDD")),
            with(jump(U,"b","join"),assign(U,"b-source",WHOLE,"WWWWXXXX"),assign(U,"b-target",YWHOLE,"YYYYZZZZ")),
            returning(U,"join",List.of(fit("fit",2,4)))));
        var value=at(run(p),"return-join",YWHOLE);assertEquals(List.of("AA  DDDD","WW  ZZZZ"),texts(value));assertFalse(value.modelValueRemainder());
    }

}
