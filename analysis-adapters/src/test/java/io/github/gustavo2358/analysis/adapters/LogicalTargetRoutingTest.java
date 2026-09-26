package io.github.gustavo2358.analysis.adapters;

import java.util.*;
import java.math.BigInteger;
import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.analysis.dependencies.*;
import io.github.gustavo2358.analysis.values.StorageAnalysisMode;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static io.github.gustavo2358.analysis.adapters.ResultFixtures.*;

/** Synthetic counterpart of the incident: two logical roots, extraction, copy and target read. */
class LogicalTargetRoutingTest {
    private static class ExpressionBuilder {
        final Operations.Header h;int n;ExpressionBuilder(UnitId u,String name){h=header(u,name);}
        Operand.Header next(){return operand(h.id(),"read-"+n++,Operand.Role.VALUE_READ);}
        Expression read(ObjectId id){return new Expressions.Read(next(),new Places.ObjectPlace(next(),id));}
        Expression text(String value){return new Expressions.Literal(next(),new Values.TextValue(value));}
        Expression integer(int value){return new Expressions.Literal(next(),new Values.IntValue(BigInteger.valueOf(value)));}
        Expression slice(Expression value,int start,int count){return new Expressions.SliceText(next(),new Expressions.FitText(next(),value,BigInteger.valueOf(start+count)," "),integer(start),integer(count));}
        Expression concat(Expression a,Expression b){return new Expressions.Binary(next(),Expressions.BinaryOperator.CONCAT,a,b);}
        Instruction set(ObjectId target,Expression value,int length){return new Operations.Assign(h,new Places.ObjectPlace(operand(h.id(),"destination",Operand.Role.VALUE_WRITE),target),new Expressions.FitText(next(),value,BigInteger.valueOf(length)," "));}
    }
    @Test void logicalRootCopyResolvesWithRealProducerChainInBothModes() {
        var base=W1dModelTest.model(4,u->{
            var g2=new ObjectId(u,"object-0");var p2=new ObjectId(u,"object-1");var g1=new ObjectId(u,"object-2");var p1=new ObjectId(u,"object-3");
            var a=new ExpressionBuilder(u,"producer");var b=new ExpressionBuilder(u,"extract-source");var c=new ExpressionBuilder(u,"copy-root");var d=new ExpressionBuilder(u,"extract-target");
            return List.of(new Sequence(new LabelId(u,"start"),List.of(
                a.set(g2,a.concat(a.text("PROGC   "),a.slice(a.read(g2),8,4)),12),
                b.set(p2,b.slice(b.read(g2),0,8),8),
                c.set(g1,c.concat(c.read(p2),c.slice(c.read(g1),8,4)),12),
                d.set(p1,d.slice(d.read(g1),0,8),8),assign(u,"later-source-overwrite",p2,"OTHER   ")),
                W1dModelTest.call(u,"call-target","end",p1,false),origin(u.publication())),returning(u,"end",List.of()));
        });
        for(int regions:new int[]{0,1,100,1000})for(var mode:StorageAnalysisMode.values()) {
            var storage=new ArrayList<>(base.storage());for(int n=0;n<regions;n++)storage.add(new Memory.Region(new Memory.StorageHeader(new StorageId(base.id(),"region-"+n),Optional.empty(),Memory.Lifetime.PERSISTENT,Memory.Visibility.PRIVATE,origin(base.id())),Optional.of(BigInteger.TEN),Optional.empty()));
            var p=new Publication(base.id(),base.airVersion(),new Capabilities.Manifest(regions==0?List.of():List.of(Capabilities.MEMORY_REGIONS),List.of()),base.artifacts(),base.units(),storage,base.resources(),base.artifactRelations(),base.origins(),base.coverage(),base.uncertainties(),base.premises());
            var result=new DependencyAnalysis(mode).prepare(p);var site=result.sites().getFirst();
            assertEquals(List.of("PROGC"),site.candidates().stream().map(DependencySiteFact.Candidate::referenceName).toList());
            assertEquals(Set.of("producer","extract-source","copy-root","extract-target"),site.candidates().getFirst().supports().stream().map(s->s.producer().localId()).collect(java.util.stream.Collectors.toSet()));
            assertEquals(1L,result.metrics().get("scalarSelections"));assertEquals(0L,result.metrics().get("regionalSelections"));assertEquals(1L,result.metrics().get("possibleValuesRuns"));
            assertEquals(0L,result.metrics().get("physicalGroupsApplied"));assertEquals(0L,result.metrics().get("physicalWritesApplied"));
            assertFalse(site.analysisReasons().contains("PHYSICAL_PROPAGATION_DISABLED"));
        }
    }
}
