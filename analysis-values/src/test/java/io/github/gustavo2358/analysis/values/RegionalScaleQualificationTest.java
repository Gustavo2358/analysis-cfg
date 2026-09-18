package io.github.gustavo2358.analysis.values;
import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.analysis.query.*;
import java.math.BigInteger;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static io.github.gustavo2358.analysis.values.ValuesFixtures.*;
import static io.github.gustavo2358.analysis.values.RegionalValuesTest.*;
import static io.github.gustavo2358.analysis.values.RegionalCompositionTest.with;
import static io.github.gustavo2358.analysis.values.RegionalInitialTest.*;

class RegionalScaleQualificationTest {
    static void measure(String shape,int n,Publication p,int expectedCandidates,int expectedSupports) {
        long start=System.nanoTime();var session=session(p);long indexed=System.nanoTime();var admission=RegionalValuesAnalysis.prepare(session,StorageAnalysisMode.EXPERIMENTAL_PHYSICAL);assertEquals(RegionalValuesAnalysis.Status.ACCEPTED,admission.status());long prepared=System.nanoTime();
        var execution=admission.analysis().orElseThrow().execute();long solved=System.nanoTime();var q=new PointQuery<>(ProgramPoint.before(new EntryId(U,"entry"),new OperationId(U,"return-exit")),WHOLE);var batch=execution.observe(Collections.nCopies(n,q));long observed=System.nanoTime();
        assertEquals(ObservationBatch.Status.COMPLETE,batch.status());var fact=batch.observations().getFirst().value();assertEquals(expectedCandidates,fact.candidates().size());assertFalse(fact.modelValueRemainder());assertEquals(expectedSupports,fact.candidateSupports().stream().mapToInt(s->s.producers().size()).sum());assertEquals(1,batch.metrics().uniqueQueries());
        System.out.printf(Locale.ROOT,"W8_REGIONAL_SCALE shape=%s n=%d objects=%d operations=%d candidates=%d supports=%d indexNs=%d prepareNs=%d solveNs=%d replayNs=%d heapUsed=%d preparation=%s solver=%s replay=%s%n",shape,n,p.units().getFirst().objects().size(),session.index().metrics().operationsIndexed(),fact.candidates().size(),expectedSupports,indexed-start,prepared-indexed,solved-prepared,observed-solved,java.lang.management.ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed(),execution.preparationMetrics(),execution.dataflow().metrics(),batch.metrics());
    }
    @Test void denseViewsAndOperationsDoNotMaterializeHugeUntouchedRegion() {
        for(int n:List.of(32,128,512)) {
            var ops=new ArrayList<Instruction>();for(int i=0;i<n;i++)ops.add(assign(U,"write-"+i,WHOLE,i%2==0?"ABCDEFGH":"WXYZEFGH"));
            var p=regional(List.of(with(jump(U,"s0","exit"),ops.toArray(Instruction[]::new)),returning(U,"exit",List.of())));var u=p.units().getFirst();var objects=new ArrayList<>(u.objects());for(int i=0;i<n;i++)objects.add(view(new ObjectId(U,"view-"+i),i,8,IBM));
            var original=(Memory.Region)p.storage().getFirst();var storage=new Memory.Region(original.header(),Optional.of(BigInteger.ONE.shiftLeft(100)),Optional.empty());
            p=new Publication(P,p.airVersion(),p.capabilities(),p.artifacts(),List.of(unit(U,u.entries(),u.sequences(),objects)),List.of(storage),p.resources(),p.artifactRelations(),p.origins(),p.coverage(),p.uncertainties(),p.premises());
            measure("dense-views",n,p,1,1);
        }
    }
    @Test void candidateAndSimultaneousSupportMultiplicityAreUncapped() {
        for(int n:List.of(32,128,512)) {
            var sequences=new ArrayList<Sequence>();
            for(int i=0;i<n-1;i++)sequences.add(branch(U,i==0?"s0":"tree-"+i,2*i+1<n-1?"tree-"+(2*i+1):"leaf-"+(2*i+1-(n-1)),2*i+2<n-1?"tree-"+(2*i+2):"leaf-"+(2*i+2-(n-1))));
            for(int i=0;i<n;i++)sequences.add(with(jump(U,"leaf-"+i,"exit"),assign(U,"producer-"+i,WHOLE,String.format(Locale.ROOT,"%08d",i))));sequences.add(returning(U,"exit",List.of()));measure("candidates",n,regional(sequences),n,n);
            var conditions=new ArrayList<Entries.InitialCondition>();for(int i=0;i<n;i++)conditions.add(seed("initial-"+i,0,"ABCDEFGH"));
            measure("initial-supports",n,seeded(List.of(jump(U,"s0","exit"),returning(U,"exit",List.of())),conditions),1,n);
        }
    }
}
