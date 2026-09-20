package io.github.gustavo2358.analysis.values;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.analysis.query.*;
import io.github.gustavo2358.analysis.storage.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static io.github.gustavo2358.analysis.values.ValuesFixtures.*;
import static io.github.gustavo2358.analysis.values.RegionalValuesTest.*;
import static io.github.gustavo2358.analysis.values.RegionalCompositionTest.*;

/** Small positive witnesses for work that the physical model actually publishes. */
class W3LegitimateWorkTest {
    private static void measure(String shape,Publication publication,String point,ObjectId object,int candidates) {
        var session=session(publication);
        long start=System.nanoTime();
        var admission=RegionalValuesAnalysis.prepare(session,StorageAnalysisMode.EXPERIMENTAL_PHYSICAL);
        assertEquals(RegionalValuesAnalysis.Status.ACCEPTED,admission.status(),admission.reason());
        long prepared=System.nanoTime();
        var execution=admission.analysis().orElseThrow().execute();
        long solved=System.nanoTime();
        var batch=execution.observe(List.of(new PointQuery<>(ProgramPoint.before(new EntryId(U,"entry"),new OperationId(U,point)),object)));
        long replayed=System.nanoTime();
        assertEquals(ObservationBatch.Status.COMPLETE,batch.status());
        var value=batch.observations().getFirst().value();
        assertEquals(candidates,value.candidates().size(),shape);
        var targets=new StatementEffects(new StorageIndex(session)).statements().stream()
            .flatMap(statement->statement.writes().stream()).flatMap(write->write.targets().stream()).count();
        assertTrue(execution.preparationMetrics().get("eventsPrepared")>0,shape);
        assertTrue(execution.metrics().get("physicalGroupsApplied")>0,shape);
        System.out.printf(Locale.ROOT,"W3_WORK shape=%s prepareNs=%d solveNs=%d replayNs=%d targets=%d events=%d candidates=%d supports=%d preparation=%s solver=%s state=%s replay=%s%n",
            shape,prepared-start,solved-prepared,replayed-solved,targets,execution.preparationMetrics().get("eventsPrepared"),
            value.candidates().size(),value.candidateSupports().stream().mapToInt(s->s.producers().size()).sum(),
            execution.preparationMetrics(),execution.dataflow().metrics(),execution.metrics(),batch.metrics());
    }
    @Test void measuredLegitimatePhysicalFamilies() {
        measure("same-base-overlap",regional(List.of(returning(U,"s0",List.of(
            assign(U,"whole",WHOLE,"ABCDEFGH"),assign(U,"prefix",PREFIX,"WXYZ"),assign(U,"suffix",SUFFIX,"1234"))))),"return-s0",WHOLE,1);
        for(int n:List.of(2,4,8)) {
            var sequences=new ArrayList<Sequence>();
            for(int i=0;i<n-1;i++)sequences.add(branch(U,i==0?"s0":"tree-"+i,
                2*i+1<n-1?"tree-"+(2*i+1):"leaf-"+(2*i+1-(n-1)),
                2*i+2<n-1?"tree-"+(2*i+2):"leaf-"+(2*i+2-(n-1))));
            for(int i=0;i<n;i++)sequences.add(with(jump(U,"leaf-"+i,"exit"),
                assign(U,"choice-"+i,WHOLE,String.format(Locale.ROOT,"%08d",i))));
            sequences.add(returning(U,"exit",List.of()));
            measure("real-choice-"+n,regional(sequences),"return-exit",WHOLE,n);
        }
        measure("control-join",regional(List.of(branch(U,"s0","a","b"),
            with(jump(U,"a","join"),assign(U,"a-prefix",PREFIX,"AAAA"),assign(U,"a-suffix",SUFFIX,"BBBB")),
            with(jump(U,"b","join"),assign(U,"b-prefix",PREFIX,"CCCC"),assign(U,"b-suffix",SUFFIX,"DDDD")),
            returning(U,"join",List.of()))),"return-join",WHOLE,2);
        measure("copy-correlation",twoBases(List.of(branch(U,"s0","a","b"),
            with(jump(U,"a","join"),assign(U,"a-x",WHOLE,"AAAABBBB"),assign(U,"a-y",YWHOLE,"CCCCDDDD")),
            with(jump(U,"b","join"),assign(U,"b-x",WHOLE,"WWWWXXXX"),assign(U,"b-y",YWHOLE,"YYYYZZZZ")),
            returning(U,"join",List.of(copy("copy",R,0,Y,4,4))))),"return-join",YWHOLE,2);
        var reason=new UncertaintyId(P,"w3-may");
        var may=new Operations.HavocMay(header(U,"may"),new Scopes.ObjectsMemory(List.of(WHOLE,YWHOLE)),reason);
        var mayPublication=twoBases(List.of(returning(U,"s0",List.of(assign(U,"old-x",WHOLE,"ABCDEFGH"),
            assign(U,"old-y",YWHOLE,"12345678"),may))));
        mayPublication=replace(mayPublication,mayPublication.units(),mayPublication.coverage(),
            List.of(new Evidence.Uncertainty(reason,"EXPLICIT_MAY",List.of(Evidence.Dimension.EFFECTS),new Scopes.UnitScope(U),
                "Published MAY across two real bases",origin(P))),mayPublication.premises());
        measure("may-effect",mayPublication,"return-s0",WHOLE,1);
        measure("event-history",regional(List.of(returning(U,"s0",List.of(
            assign(U,"seed",WHOLE,"ABCDEFGH"),assign(U,"p1",PREFIX,"1111"),assign(U,"s1",SUFFIX,"2222"),
            assign(U,"p2",PREFIX,"3333"),assign(U,"s2",SUFFIX,"4444"),assign(U,"p3",PREFIX,"5555"),
            assign(U,"s3",SUFFIX,"6666"))))),"return-s0",WHOLE,1);
    }
}
