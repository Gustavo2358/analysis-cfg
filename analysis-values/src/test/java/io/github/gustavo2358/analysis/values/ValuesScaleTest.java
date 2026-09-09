package io.github.gustavo2358.analysis.values;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.analysis.query.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static io.github.gustavo2358.analysis.values.ValuesFixtures.*;
import static io.github.gustavo2358.analysis.values.ValuesTest.*;

class ValuesScaleTest {
    private static Map<String,Long> measurements(PossibleValuesAnalysis.Execution run,PossibleValuesAnalysis.Execution.Observations batch) {
        var m=new TreeMap<String,Long>();
        // Phase-labelled maps preserve preparation, solve and replay independently.
        run.solveMetrics().forEach((k,v)->m.put("solve_"+k,v));run.preparationMetrics().forEach((k,v)->m.put("prepare_"+k,v));batch.stateMetrics().forEach((k,v)->m.put("replay_"+k,v));m.putAll(batch.quality());
        var metrics=batch.batch().metrics();m.put("queryRequests",metrics.queryRequests());m.put("uniqueQueries",metrics.uniqueQueries());m.put("sequencesReplayed",metrics.sequencesReplayed());m.put("operationsReplayed",metrics.operationsReplayed());m.put("queriesAnswered",metrics.queriesAnswered());m.put("unsupportedQueries",metrics.unsupportedQueries());
        m.put("queriesNotMaterialized",metrics.queriesNotMaterialized());m.put("observationFailures",metrics.observationFailures());
        m.put("analysisPoints",run.dataflow().metrics().analysisPoints());m.put("operationsTransferred",run.dataflow().metrics().operationsTransferred());m.put("predecessorContributionReads",run.dataflow().metrics().predecessorContributionReads());m.put("edgeContributionJoins",run.dataflow().metrics().edgeContributionJoins());
        RetentionAudit.count(run.dataflow()).forEach((k,v)->m.put("retained_"+k,v));RetentionAudit.count(batch).forEach((k,v)->m.put("batchRetained_"+k,v));
        assertEquals(0,m.getOrDefault("batchRetained_PossibleValuesState",0L));assertEquals(0,m.getOrDefault("batchRetained_Node",0L));assertEquals(0,m.getOrDefault("batchRetained_ValueUniverse",0L));
        return m;
    }
    private static void report(String probe,int n,Map<String,Long> metrics,long elapsed) {
        var entries=new ArrayList<String>();entries.add("\"probe\":\""+probe+"\"");entries.add("\"N\":"+n);entries.add("\"elapsedNanos\":"+elapsed);
        for(var e:new TreeMap<>(metrics).entrySet())entries.add("\""+e.getKey()+"\":"+e.getValue());
        System.out.println("W3_METRICS {"+String.join(",",entries)+"}");
    }
    static Publication longSequence(int writes,int objects,boolean distinct,boolean uniqueText) {
        var p=graph(new String[]{null},new int[][]{{}},objects,distinct,distinct);var u=p.units().getFirst();var instructions=new ArrayList<Instruction>();
        for(int i=0;i<writes;i++)instructions.add(assign(u.id(),"i"+i,u.objects().get(distinct?i%objects:0).id(),uniqueText?"v"+i:"v"+(i%2)));
        return replace(p,List.of(unit(u.id(),u.entries(),List.of(returning(u.id(),"s0",instructions)),u.objects())),p.coverage(),p.uncertainties(),p.premises());
    }
    @Test void longSequenceDoesNotRetainInstructionStatesOrDeadCandidates() {
        for(int n:new int[]{10000,20000,100000,200000}) {
            long start=System.nanoTime();var p=longSequence(n,1,false,false);var run=execute(p);var batch=run.observe(List.of(before(p,0,0)));
            expected(batch.batch().observations().getFirst().value(),false,"v1");
            assertEquals(2,run.preparationMetrics().get("valuesInterned"));
            assertEquals(1,run.solveMetrics().get("maxSparseBindings"));
            assertTrue(RetentionAudit.count(run.dataflow()).get("PossibleValuesState")<=3,"no instruction state history");
            assertEquals(n,batch.batch().metrics().operationsReplayed());report("S1",n,measurements(run,batch),System.nanoTime()-start);
        }
    }
    @Test void unusedObjectInventoryDoesNotMultiplySparseState() {
        for(int n:new int[]{1000,2000,4000,10000}) {
            long start=System.nanoTime();var p=longSequence(1,n,true,false);var run=execute(p);var batch=run.observe(List.of(before(p,0,0),before(p,0,n-1)));
            expected(batch.batch().observations().stream().filter(o->o.query().equals(before(p,0,0))).findFirst().orElseThrow().value(),false,"v0");
            expected(fact(run,before(p,0,n-1)),true);
            assertEquals(1,run.solveMetrics().get("maxSparseBindings"),"inventory must not populate each state");
            assertEquals(1,RetentionAudit.count(run.dataflow()).get("Node"));report("S2-S16",n,measurements(run,batch),System.nanoTime()-start);
        }
    }
    @Test void manyLiveBindingsShareAcrossWritesAndBlockBoundaries() {
        for(int n:new int[]{1000,2000,4000})for(boolean chain:new boolean[]{false,true}) {
            long start=System.nanoTime();Publication p;
            if(!chain)p=longSequence(n,n,true,true);
            else {var writes=new String[n];var targets=new int[n][];for(int i=0;i<n;i++){writes[i]="v"+i;targets[i]=i==n-1?new int[0]:new int[]{i+1};}p=graph(writes,targets,n,true,true);}
            var run=execute(p);var batch=run.observe(List.of(before(p,chain?n-1:0,n-1)));expected(batch.batch().observations().getFirst().value(),false,"v"+(n-1));
            assertEquals(n,run.solveMetrics().get("maxSparseBindings"));
            assertTrue(run.solveMetrics().get("persistentNodesAllocated")<40L*n,"bounded update allocation excludes full copy");
            var retention=RetentionAudit.count(run.dataflow());assertTrue(retention.get("Node")<(chain?40L*n:n+1L),"sharing limits live persistent nodes");
            report(chain?"S3-chain":"S3-sequence",n,measurements(run,batch),System.nanoTime()-start);
        }
    }
    static Publication fanIn(int n) {
        // Binary Branch tree with N leaves, each defining one literal then joining J.
        int size=2*n;var writes=new String[size];var targets=new int[size][];
        for(int i=0;i<n-1;i++)targets[i]=new int[]{2*i+1,2*i+2};
        for(int i=n-1;i<2*n-1;i++){writes[i]="v"+(i-(n-1));targets[i]=new int[]{size-1};}
        targets[size-1]=new int[0];return graph(writes,targets,1,false,false);
    }
    @Test void branchCandidatesAndWideFanInPreserveEveryFiniteValue() {
        for(int n:new int[]{8,9,100,1000,2000,4000,10000}) {
            long start=System.nanoTime();var p=fanIn(n);var run=execute(p);var batch=run.observe(List.of(before(p,2*n-1,0)));var fact=batch.batch().observations().getFirst().value();
            assertFalse(fact.modelValueRemainder());assertEquals(n,fact.candidates().size(),"candidate count is not a precision policy");
            var expected=new HashSet<String>();for(int i=0;i<n;i++)expected.add("v"+i);
            assertEquals(expected,new HashSet<>(fact.candidates().stream().map(Values.TextValue::value).toList()));
            assertEquals(0,run.dataflow().metrics().predecessorContributionReads());
            report("S7-S4b",n,measurements(run,batch),System.nanoTime()-start);
        }
        for(int n:new int[]{1000,2000,4000}) {
            long start=System.nanoTime();var p=graph(new String[]{"A",null,null,null},new int[][]{{1,2},{3},{3},{}},n,true,true);
            var u=p.units().getFirst();var sequences=new ArrayList<>(u.sequences());var instructions=new ArrayList<Instruction>();
            for(int i=0;i<n;i++)instructions.add(assign(u.id(),"wide"+i,u.objects().get(i).id(),"v"+i));
            var first=sequences.getFirst();sequences.set(0,new Sequence(first.label(),instructions,first.terminator(),first.origin()));
            p=replace(p,List.of(unit(u.id(),u.entries(),sequences,u.objects())),p.coverage(),p.uncertainties(),p.premises());
            var run=execute(p);var batch=run.observe(List.of(before(p,3,n-1)));expected(batch.batch().observations().getFirst().value(),false,"v"+(n-1));
            report("S4b-wide",n,measurements(run,batch),System.nanoTime()-start);
        }
    }
    @Test void staggeredWideFanInCountsRealDeliveriesAndBindingWork() {
        for(int n:new int[]{1000,2000,4000}) {
            int bindings=n/125;var writes=new String[2*n];var targets=new int[2*n][];
            for(int i=0;i<n-1;i++)targets[i]=new int[]{2*i+1,2*i+2};
            for(int i=n-1;i<2*n-1;i++){writes[i]="v"+i;targets[i]=new int[]{2*n-1};}
            targets[2*n-1]=new int[0];var p=graph(writes,targets,bindings,true,true);var u=p.units().getFirst();
            var ins=new ArrayList<Instruction>();for(int i=0;i<bindings;i++)ins.add(assign(u.id(),"seed-"+i,u.objects().get(i).id(),"BASE"));
            var sequences=new ArrayList<>(u.sequences());sequences.add(new Sequence(new LabelId(u.id(),"seed"),ins,new Operations.Jump(header(u.id(),"seed-jump"),new LabelId(u.id(),"s0")),origin(p.id())));
            p=replace(p,List.of(unit(u.id(),List.of(entry(u.id(),"entry","seed")),sequences,u.objects())),p.coverage(),p.uncertainties(),p.premises());
            var session=session(p);var def=PossibleValuesAnalysis.prepare(session).analysis().orElseThrow();var join=session.index().sequence(new LabelId(u.id(),"s"+(2*n-1)));
            long start=System.nanoTime();var measured=io.github.gustavo2358.analysis.solver.WideScheduleBridge.solve(session,def,join);var result=measured.result();
            assertEquals(n,measured.joinDeliveries(),"wide staggered join deliveries");assertEquals(n+1,measured.joinTransfers(),"join revisited after each arrival");
            assertEquals(0,result.metrics().predecessorContributionReads());
            var finalState=result.out(session.contexts().iterator().next(),join);assertEquals(bindings,finalState.explicitBindings());
            for(int i=0;i<bindings;i++){var value=finalState.value(i,new ValuesWork());assertFalse(value.open());assertTrue(value.size()>=n/bindings);}
            System.out.println("W3_STAGGERED {\"N\":"+n+",\"bindings\":"+bindings+",\"deliveries\":"+measured.joinDeliveries()+",\"joinTransfers\":"+measured.joinTransfers()+",\"joinEntriesVisited\":"+result.metrics().joinEntriesVisited()+",\"predecessorContributionReads\":"+result.metrics().predecessorContributionReads()+",\"elapsedNanos\":"+(System.nanoTime()-start)+"}");
        }
    }
    @Test void denseDuplicateQueriesShareSingleReplayAndNoStateRoots() {
        for(int n:new int[]{1000,2000,4000}) {
            long start=System.nanoTime();var p=longSequence(n,1,false,false);var run=execute(p);var u=p.units().getFirst();var queries=new ArrayList<PointQuery<ObjectId>>();
            for(var op:u.sequences().getFirst().instructions())for(int repeat=0;repeat<2;repeat++)queries.add(new PointQuery<>(ProgramPoint.after(u.entries().getFirst().id(),op.header().id()),u.objects().getFirst().id()));
            var batch=run.observe(queries);assertEquals(n,batch.batch().observations().size());assertEquals(n,batch.batch().metrics().operationsReplayed(),"one union replay, not replay per query");
            assertEquals(1,batch.batch().metrics().sequencesReplayed());assertEquals(2*n,batch.batch().metrics().queryRequests());
            assertEquals(n,batch.quality().get("closedInModelResults"));report("S6-S16",n,measurements(run,batch),System.nanoTime()-start);
        }
    }
    @Test void unicodePoolCostFollowsBoundaryTextAndRetainsNoGlobalInterning() {
        for(int n:new int[]{1000,2000,4000}) {
            long start=System.nanoTime();String text="😀".repeat(n);var p=graph(new String[]{text,text},new int[][]{{1},{}},1,false,false);var run=execute(p);var batch=run.observe(List.of(before(p,1,0)));
            expected(batch.batch().observations().getFirst().value(),false,text);
            assertEquals(1,run.preparationMetrics().get("valuesInterned"));assertEquals(1,run.preparationMetrics().get("poolHits"));assertEquals(2L*n,run.preparationMetrics().get("unicodeScalarsHashed"));
            assertEquals(0,RetentionAudit.count(batch).getOrDefault("ValueUniverse",0L));report("S9",n,measurements(run,batch),System.nanoTime()-start);
        }
    }
}
