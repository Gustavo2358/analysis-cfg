package io.github.gustavo2358.analysis.values;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.analysis.application.*;
import io.github.gustavo2358.analysis.consumers.*;
import io.github.gustavo2358.analysis.plan.*;
import io.github.gustavo2358.analysis.query.*;
import io.github.gustavo2358.analysis.solver.Direction;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static io.github.gustavo2358.analysis.values.ValuesFixtures.*;
import static io.github.gustavo2358.analysis.values.PlanningFixtures.*;
import static io.github.gustavo2358.analysis.application.PreparedAnalysisResult.*;

class PlanningContractTest {
    @Test void completeSemanticKeyIdentityAndOrderingCannotCollide() {
        var p=vertical();var k=key(p);var options=new HashMap<String,String>();options.put("a","b, c=d");
        var variants=List.of(k,new AnalysisKey("other",k.version(),k.profile(),k.direction(),k.precisionPolicy(),k.options(),k.entry()),
            new AnalysisKey(k.implementation(),"2",k.profile(),k.direction(),k.precisionPolicy(),k.options(),k.entry()),
            new AnalysisKey(k.implementation(),k.version(),"other",k.direction(),k.precisionPolicy(),k.options(),k.entry()),
            new AnalysisKey(k.implementation(),k.version(),k.profile(),Direction.BACKWARD,k.precisionPolicy(),k.options(),k.entry()),
            new AnalysisKey(k.implementation(),k.version(),k.profile(),k.direction(),"other",k.options(),k.entry()),
            new AnalysisKey(k.implementation(),k.version(),k.profile(),k.direction(),k.precisionPolicy(),Map.of("a","b","c","d"),k.entry()),
            new AnalysisKey(k.implementation(),k.version(),k.profile(),k.direction(),k.precisionPolicy(),options,k.entry()),
            new AnalysisKey(k.implementation(),k.version(),k.profile(),k.direction(),k.precisionPolicy(),k.options(),new EntryId(k.entry().unit(),"other")));
        options.clear();var sorted=new TreeSet<AnalysisKey>(AnalysisKey.ORDER);sorted.addAll(variants);
        assertEquals(9,new HashSet<>(variants).size(),"all semantic dimensions participate in equality");assertEquals(9,sorted.size(),"ordering does not use ambiguous string concatenation");
        assertEquals(k,PossibleValuesProvider.key(k.entry()));assertEquals(0,AnalysisKey.ORDER.compare(k,PossibleValuesProvider.key(k.entry())));
        assertThrows(UnsupportedOperationException.class,()->variants.get(7).options().clear());
        try(var runtime=new PlanningExecution(session(p),registry())) {
            var resourceKey=new AnalysisKey(k.implementation(),k.version(),k.profile(),k.direction(),k.precisionPolicy(),Map.of("workerCount","4"),k.entry());
            assertThrows(IllegalArgumentException.class,()->runtime.plan(List.of(analysisOnly("A",resourceKey))),"infrastructure option cannot configure semantic run");
            var unknown=new AnalysisKey("missing","1",k.profile(),k.direction(),k.precisionPolicy(),Map.of(),k.entry());
            assertThrows(IllegalArgumentException.class,()->runtime.plan(List.of(analysisOnly("A",unknown))),"unknown analysis provider never succeeds");
        }
    }
    @Test void distinctProfilesOptionsAndEntriesRunSeparatelyWithNoPhantomContext() {
        var p=linear(1,1,1,20);var entry=p.units().getFirst().entries().getFirst().id();var next=p.units().getFirst().entries().get(1).id();
        var owner=session(p);var selected=owner.selectEntries(List.of(entry,next));
        assertSame(owner.index(),selected.index(),"scoping shares admitted W1 index");assertEquals(20,owner.contexts().size());assertEquals(2,selected.contexts().size());
        assertThrows(IllegalArgumentException.class,()->selected.selectEntries(List.of(p.units().getFirst().entries().get(2).id())));
        var provider=new VariantProvider();var registrations=new ArrayList<ConsumerRegistration<String>>();var expected=new HashSet<String>();
        for(var e:List.of(entry,next))for(String profile:List.of("a","b"))for(String option:List.of("first","second")) {
            var key=new AnalysisKey("Variant","1",profile,Direction.FORWARD,"test",Map.of("label",option),e);
            var batch=new ObservationBatchId<>(e.localId()+profile+option,key,"variant",ObjectId.class,String.class);
            var q=new PointQuery<>(ProgramPoint.before(e,p.units().getFirst().sequences().getFirst().terminator().header().id()),p.units().getFirst().objects().getFirst().id());
            for(int i=0;i<2;i++)registrations.add(new ConsumerRegistration<>(new ConsumerPlan(batch.id()+i,List.of(key),List.of(batch.id())),
                List.of(new SiteInterest(Operations.Return.class,e,s->true)),List.of(new ObservationRequest<>(batch,List.of(q))),
                (s,f,k)->k.emit(f.lookup(batch,q).observation().value())));
            expected.add(profile+":"+key.options()+":"+e.localId()+":[TextValue[value=literal]]");
        }
        try(var runtime=new PlanningExecution(selected,new AnalysisRegistry(List.of(provider)))) {
            var result=runtime.execute("keys",runtime.plan(registrations));
            assertEquals(8,provider.starts,"unique full key executes exactly once");assertEquals(8,metric(result,"analysis","analysisRuns"));assertEquals(8,metric(result,"analysis","analysisCacheHits"));
            assertEquals(8,result.analyses().size());
            for(var run:result.analyses()) {assertEquals(3L,run.metrics().get("analysisPoints"),"one selected Entry per logical run");assertEquals(1L,run.metrics().get("boundaryJoins"),"no phantom Entry boundary");}
            var values=new HashSet<String>();result.consumers().forEach(c->values.addAll(c.facts()));assertEquals(8,values.size(),"semantic options/profile/Entry cannot collide in cache");
            assertTrue(values.stream().anyMatch(v->v.startsWith("a:{label=first}:entry-0:")));assertTrue(values.stream().anyMatch(v->v.startsWith("b:{label=second}:entry-1:")));
            System.out.println("W4_KEYS {\"keys\":8,\"consumers\":16,\"analysisRuns\":"+metric(result,"analysis","analysisRuns")+",\"analysisCacheHits\":"+metric(result,"analysis","analysisCacheHits")+",\"pointsPerRun\":3,\"unselectedEntries\":18}");
        }
    }
    @Test void differentSessionsCannotShareRunsOrPlansEvenWithEqualIds() {
        var first=vertical();var second=graph(new String[]{"OTHER"},new int[][]{{}},1,false,false);var key=key(first);var batch=PossibleValuesProvider.batch("B",key);
        try(var a=new PlanningExecution(session(first),registry());var b=new PlanningExecution(session(second),registry())) {
            var planA=a.plan(List.of(queryConsumer("A",key,batch,List.of(query(first)))));
            assertThrows(IllegalStateException.class,()->b.execute("foreign",planA));
            var resultA=a.execute("A",planA);var resultB=b.execute("B",b.plan(List.of(queryConsumer("A",key,batch,List.of(query(second))))));
            assertEquals(List.of(new Values.TextValue("PROGA")),resultA.consumers().getFirst().facts().getFirst().observation().value().candidates());
            assertEquals(List.of(new Values.TextValue("OTHER")),resultB.consumers().getFirst().facts().getFirst().observation().value().candidates(),"cache owner is session lifetime, not publication ID");
            assertEquals(1,metric(resultB,"analysis","analysisRuns"));
        }
    }
    @Test void registrationInterestAndQueryOrderHaveDeterministicPlansAndFacts() {
        var p=vertical();var key=key(p);var batch=PossibleValuesProvider.batch("B",key);var q=query(p);
        var after=new PointQuery<>(ProgramPoint.after(key.entry(),q.point().operation()),q.subject());
        var results=new ArrayList<PreparedAnalysisResult<TestFact>>();var plans=new ArrayList<ExecutionPlan<TestFact>>();
        for(int i=0;i<2;i++)try(var runtime=new PlanningExecution(session(p),registry())) {
            var rs=new ArrayList<ConsumerRegistration<TestFact>>();
            for(String id:List.of("A","B")) {
                var base=queryConsumer(id,key,batch,i==0?List.of(q,after,q):List.of(after,q,q));
                // Consumers emit in canonical point order, independent of request registration order.
                var interests=new ArrayList<>(List.of(new SiteInterest(Operations.Return.class,key.entry(),s->true),new SiteInterest(Operations.Assign.class,key.entry(),s->false)));
                if(i==1)Collections.reverse(interests);
                rs.add(new ConsumerRegistration<>(base.dependencies(),interests,base.requests(),(s,f,k)->{k.emit(new TestFact(s.operationId(),f.lookup(batch,q).observation()));k.emit(new TestFact(s.operationId(),f.lookup(batch,after).observation()));}));
            }
            if(i==1)Collections.reverse(rs);var plan=runtime.plan(rs);plans.add(plan);results.add(runtime.execute("same",plan));
        }
        assertEquals(plans.get(0).observations(),plans.get(1).observations());assertEquals(plans.get(0).sites(),plans.get(1).sites());assertEquals(results.get(0),results.get(1),"deterministic complete prepared result");
        assertThrows(UnsupportedOperationException.class,()->plans.getFirst().sites().clear());
    }
    @Test void overlappingInterestsDispatchOnlyExactPairsAndKeepStructuralPresence() {
        var p=graph(new String[]{"A",null,null},new int[][]{{},{},{}},1,false,false);var key=key(p);var rs=new ArrayList<ConsumerRegistration<OperationId>>();
        List<Set<String>> accepted=List.of(Set.of("return-s0","return-s1"),Set.of("return-s1","return-s2"),Set.of("return-s2"));
        for(int c=0;c<3;c++) {
            var ids=accepted.get(c);var interest=new SiteInterest(Operations.Return.class,key.entry(),s->ids.contains(s.operationId().localId()));
            rs.add(new ConsumerRegistration<>(new ConsumerPlan("C"+c,List.of(),List.of()),List.of(interest,interest),List.of(),(site,f,k)->{assertEquals(SiteView.Presence.STRUCTURAL,site.presence());k.emit(site.operationId());}));
        }
        try(var runtime=new PlanningExecution(session(p),registry())) {
            var result=runtime.execute("overlap",runtime.plan(rs));assertEquals(3,metric(result,"planning","candidateSites"));assertEquals(5,metric(result,"planning","siteMatches"));assertEquals(5,metric(result,"consumer","consumerInvocations"));
            assertEquals(0,metric(result,"analysis","analysisRuns"));
            for(int i=0;i<3;i++)assertEquals(accepted.get(i),new HashSet<>(result.consumers().get(i).facts().stream().map(OperationId::localId).toList()));
            var batch=PossibleValuesProvider.batch("B",key);var orphan=new PointQuery<>(ProgramPoint.before(key.entry(),new OperationId(key.entry().unit(),"return-s2")),query(p).subject());
            var queried=runtime.execute("orphan",runtime.plan(List.of(queryConsumer("Q",key,batch,List.of(orphan)))));
            assertEquals(ValueFact.Reachability.UNREACHABLE_IN_MODEL,queried.results().getFirst().observations().getFirst().value() instanceof ValueFact fact?fact.reachability():null);
        }
    }
    @Test void batchesWithSameIdCannotAliasDifferentKeys() {
        var p=linear(1,1,1,2);var first=key(p);var second=PossibleValuesProvider.key(p.units().getFirst().entries().get(1).id());var q=query(p);
        var other=new PointQuery<>(ProgramPoint.before(second.entry(),q.point().operation()),q.subject());
        try(var runtime=new PlanningExecution(session(p),registry())) {
            assertThrows(IllegalArgumentException.class,()->runtime.plan(List.of(queryConsumer("A",first,PossibleValuesProvider.batch("same",first),List.of(q)),queryConsumer("B",second,PossibleValuesProvider.batch("same",second),List.of(other)))),"batch ID binds exact key");
            // Both keys are declared: the later dependency guard cannot mask an ID collision.
            var combined=new ConsumerRegistration<TestFact>(new ConsumerPlan("C",List.of(first,second),List.of("same")),List.of(),
                List.of(new ObservationRequest<>(PossibleValuesProvider.batch("same",first),List.of(q)),
                    new ObservationRequest<>(PossibleValuesProvider.batch("same",second),List.of(other))),(site,facts,sink)->{});
            assertThrows(IllegalArgumentException.class,()->runtime.plan(List.of(combined)),"batch ID binds exact key");
        }
    }
    @Test void perSiteQueriesAreCompiledBeforeExecutionAndDependenciesAreExplicit() {
        var p=vertical();var key=key(p);var batch=PossibleValuesProvider.batch("B",key);var q=query(p);var factories=new int[1];
        var declaration=new SiteInterest.SiteQuery<ObjectId,ValueFact>(batch,s->{factories[0]++;return new PointQuery<>(ProgramPoint.before(s.entry(),s.operationId()),q.subject());});
        var r=new ConsumerRegistration<TestFact>(new ConsumerPlan("A",List.of(key),List.of("B")),List.of(new SiteInterest(Operations.Return.class,key.entry(),s->true,List.of(declaration))),List.of(),
            (s,f,k)->k.emit(new TestFact(s.operationId(),f.lookup(batch,q).observation())));
        try(var runtime=new PlanningExecution(session(p),registry())) {
            var plan=runtime.plan(List.of(r));assertEquals(1,factories[0]);assertEquals(List.of(key),plan.consumers().getFirst().requiredAnalysisKeys());assertEquals(List.of("B"),plan.consumers().getFirst().requiredObservationBatchIds());
            runtime.execute("site-query",plan);assertEquals(1,factories[0],"no planning callback during consume");
            var nested=new ConsumerRegistration<TestFact>(r.dependencies(),r.interests(),List.of(),(s,f,k)->assertThrows(IllegalStateException.class,()->runtime.plan(List.of(r))));
            runtime.execute("late-registration",runtime.plan(List.of(nested)));
        }
    }
    @Test void modelCandidatesSupportsAndPremisesRemainAbstractAtConsumerBoundary() {
        var p=graph(new String[]{null,"A","B",null},new int[][]{{1,2},{3},{3},{}},2,true,true);var key=key(p);var batch=PossibleValuesProvider.batch("B",key);var q=query(p);
        try(var runtime=new PlanningExecution(session(p),registry())) {
            var result=runtime.execute("premises",runtime.plan(List.of(queryConsumer("A",key,batch,List.of(q)))));
            var fact=result.consumers().getFirst().facts().getFirst().observation().value();
            assertEquals(List.of(new Values.TextValue("B")),fact.candidates());assertTrue(fact.modelValueRemainder(),"missing other path remains open");
            assertEquals(List.of(new PremiseId(p.id(),"disjoint")),fact.premises());assertEquals(new OperationId(key.entry().unit(),"assign-2"),fact.candidateSupports().getFirst().producers().getFirst().evidence());
        }
    }
}
