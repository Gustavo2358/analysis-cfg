package io.github.gustavo2358.analysis.values;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.analysis.application.*;
import io.github.gustavo2358.analysis.plan.*;
import io.github.gustavo2358.analysis.query.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static io.github.gustavo2358.analysis.values.ValuesFixtures.*;
import static io.github.gustavo2358.analysis.values.PlanningFixtures.*;
import static io.github.gustavo2358.analysis.application.PreparedAnalysisResult.*;

class PlanningScaleTest {
    @Test void sparseOverlappingConsumersShareBucketsAndOneActualAnalysis() {
        var base=linear(1,0,1,1);var u=base.units().getFirst();var seq=new ArrayList<Sequence>();var instructions=new ArrayList<Instruction>();
        for(int i=0;i<30;i++)instructions.add(assign(u.id(),"assign-"+i,u.objects().getFirst().id(),"PROGA"));
        for(int i=0;i<99940;i++)instructions.add(new Operations.Nop(header(u.id(),"nop-"+i)));
        for(int i=0;i<10;i++) {
            var branch=branch(u.id(),"s"+i,"s"+(10+i),i==9?"s29":"s"+(i+1));
            seq.add(new Sequence(branch.label(),i==0?instructions:List.of(),branch.terminator(),origin(base.id())));
        }
        for(int i=10;i<30;i++)seq.add(returning(u.id(),"s"+i,List.of()));
        var p=publication(base.id(),List.of(unit(u.id(),List.of(entry(u.id(),"entry","s0")),seq,u.objects())),base.storage());
        var session=session(p);var key=key(p);var batch=PossibleValuesProvider.batch("shared",key);var subject=u.objects().getFirst().id();
        long indexVisits=session.index().metrics().structuralVisits();
        for(int k:List.of(1,2,20))try(var runtime=new PlanningExecution(session,registry())) {
            var registrations=new ArrayList<ConsumerRegistration<TestFact>>();
            for(int i=0;i<k;i++) {
                var demand=new SiteInterest.SiteQuery<ObjectId,ValueFact>(batch,s->new PointQuery<>(ProgramPoint.before(s.entry(),s.operationId()),subject));
                registrations.add(new ConsumerRegistration<>(new ConsumerPlan("consumer-"+i,List.of(key),List.of(batch.id())),List.of(
                    new SiteInterest(Operations.Return.class,key.entry(),s->true,List.of(demand)),new SiteInterest(Operations.Assign.class,key.entry(),s->true),new SiteInterest(Operations.Branch.class,key.entry(),s->true)),List.of(),
                    (s,f,sink)-> {
                        var value=s.operation() instanceof Operations.Return?f.lookup(batch,new PointQuery<>(ProgramPoint.before(s.entry(),s.operationId()),subject)).observation():null;
                        sink.emit(new TestFact(s.operationId(),value));
                    }));
            }
            long start=System.nanoTime();var plan=runtime.plan(registrations);var result=runtime.execute("s5",plan);
            assertEquals(100000,session.index().metrics().operationsIndexed());assertEquals(indexVisits,session.index().metrics().structuralVisits(),"index work independent of K");
            assertEquals(60,metric(result,"planning","candidateSites"),"only three sparse AIR buckets");assertEquals(60,metric(result,"planning","structuralVisits"),"bucket scan once, not per consumer");
            assertEquals(60L*k,metric(result,"planning","siteMatches"));assertEquals(60L*k,metric(result,"consumer","consumerInvocations"),"callbacks equal exact sparse matches");
            assertEquals(1,metric(result,"analysis","analysisRuns"),"same key must not solve per consumer");assertEquals(k-1,metric(result,"analysis","analysisCacheHits"));
            assertEquals(20L*k,metric(result,"observation","queryRequests"));assertEquals(20,metric(result,"observation","uniqueQueries"));assertEquals(11,metric(result,"observation","sequencesReplayed"));
            assertEquals(PreparationStatus.COMPLETE,result.preparationStatus());
            for(var consumer:result.consumers())for(var fact:consumer.facts())if(fact.observation()!=null&&fact.observation().value().reachability()==ValueFact.Reachability.REACHABLE)
                assertEquals(List.of(new Values.TextValue("PROGA")),fact.observation().value().candidates(),"quality alongside S5 cost");
            emit("S5",100000,k,result,session.index().metrics().operationsIndexed(),indexVisits,System.nanoTime()-start);
        }
    }
    @Test void denseQueriesAcrossConsumersReplayOnlyTheUnion() {
        for(int n:List.of(1000,2000,4000))for(int fraction:List.of(2,1))for(int k:List.of(1,2,20)) {
            var p=linear(1,n,1,1);var session=session(p);var key=key(p);var batch=PossibleValuesProvider.batch("shared",key);var sequence=p.units().getFirst().sequences().getFirst();var subject=query(p).subject();
            var queries=new ArrayList<PointQuery<ObjectId>>();int count=n/fraction;
            for(int i=1;i<=count;i++)queries.add(new PointQuery<>(ProgramPoint.before(key.entry(),i==n?sequence.terminator().header().id():sequence.instructions().get(i).header().id()),subject));
            var registrations=new ArrayList<ConsumerRegistration<TestFact>>();
            for(int i=0;i<k;i++)registrations.add(queryConsumer("C"+i,key,batch,queries));
            try(var runtime=new PlanningExecution(session,registry())) {
                long start=System.nanoTime();var result=runtime.execute("s6",runtime.plan(registrations));
                assertEquals((long)count*k,metric(result,"observation","queryRequests"));assertEquals(count,metric(result,"observation","uniqueQueries"));
                assertEquals(1,metric(result,"observation","sequencesReplayed"),"union has one replay group");assertEquals(count,metric(result,"observation","operationsReplayed"),"union has one prefix, independent of K");
                assertEquals(1,metric(result,"analysis","analysisRuns"));assertEquals(count,metric(result,"observation","queriesAnswered"));assertEquals(0,metric(result,"observation","unsupportedQueries"));
                assertEquals((long)count*k,metric(result,"consumer","factsCommitted"));
                var retained=PlanningRetention.count(result);assertEquals((long)count,retained.get("ValueFact"),"shared observations, not K copies");
                assertEquals(0L,retained.getOrDefault("DataflowResult",0L));assertEquals(0L,retained.getOrDefault("PossibleValuesState",0L));
                emit(fraction==1?"S6-all":"S6-half",n,k,result,session.index().metrics().operationsIndexed(),session.index().metrics().structuralVisits(),System.nanoTime()-start);
            }
        }
    }
    @Test void consumersQueriesMatchesFactsAndBatchesHaveNoCapacitySemantics() {
        for(int n:List.of(32,64,128))for(boolean independent:List.of(false,true)) {
            var p=linear(1,n,1,1);var session=session(p);var key=key(p);var sequence=p.units().getFirst().sequences().getFirst();var subject=query(p).subject();
            var registrations=new ArrayList<ConsumerRegistration<TestFact>>();
            for(int i=1;i<=n;i++) {
                var batch=PossibleValuesProvider.batch(independent?"B"+i:"shared",key);
                var q=new PointQuery<>(ProgramPoint.before(key.entry(),i==n?sequence.terminator().header().id():sequence.instructions().get(i).header().id()),subject);
                registrations.add(queryConsumer("C"+i,key,batch,List.of(q)));
            }
            try(var runtime=new PlanningExecution(session,registry())) {
                long start=System.nanoTime();var result=runtime.execute("s16",runtime.plan(registrations));
                assertEquals(PreparationStatus.COMPLETE,result.preparationStatus(),"size never changes completion");assertEquals(n,result.consumers().size());
                assertEquals(n,metric(result,"consumer","factsCommitted"),"no fact cap");assertEquals(n,metric(result,"planning","siteMatches"),"no site or match cap");
                assertEquals(n,metric(result,"observation","uniqueQueries"),"no query cap");assertEquals(n,metric(result,"observation","queriesAnswered"));
                assertEquals(1,metric(result,"analysis","analysisRuns"),"independent batches share run");
                assertEquals(independent?n:1,metric(result,"observation","observationBatchesExecuted"),"no batch cap");
                assertEquals(independent?(long)n*(n+1)/2:n,metric(result,"observation","operationsReplayed"));
                for(var c:result.consumers()) {
                    var fact=c.facts().getFirst().observation().value();assertEquals(List.of(new Values.TextValue("literal")),fact.candidates());assertFalse(fact.effectiveUnknownRemainder());assertEquals(1,fact.candidateSupports().size());
                }
                emit(independent?"S16-batches":"S16-shared",n,n,result,session.index().metrics().operationsIndexed(),session.index().metrics().structuralVisits(),System.nanoTime()-start);
            }
        }
    }
    private static void emit(String probe,int n,int k,PreparedAnalysisResult<?> result,long indexed,long indexVisits,long elapsed) {
        var row=new TreeMap<String,Long>();row.put("N",(long)n);row.put("K",(long)k);row.put("index_operationsIndexed",indexed);row.put("index_structuralVisits",indexVisits);row.put("elapsedNanos",elapsed);
        result.metrics().forEach((phase,values)->values.forEach((name,value)->row.put(phase+"_"+name,value)));
        var retained=PlanningRetention.count(result);for(String name:List.of("ValueFact","DataflowResult","AnalysisSession","ProgramIndex","ConsumerRegistration","PossibleValuesState","ValueUniverse"))row.put("retained_"+name,retained.getOrDefault(name,0L));
        var fields=new ArrayList<String>();fields.add("\"probe\":\""+probe+"\"");row.forEach((name,value)->fields.add("\""+name+"\":"+value));
        System.out.println("W4_METRICS {"+String.join(",",fields)+"}");
    }
}
