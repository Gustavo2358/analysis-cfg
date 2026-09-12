package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.air.model.Values.TextValue;
import io.github.gustavo2358.analysis.application.PreparedAnalysisResult;
import io.github.gustavo2358.analysis.application.PreparedAnalysisResult.*;
import io.github.gustavo2358.analysis.dataflow.*;
import io.github.gustavo2358.analysis.query.*;
import io.github.gustavo2358.analysis.values.ValueFact;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

final class WireAdversarialTest {
    @Test void unavailableBatchRetainsItsExplicitDependencyReason()throws Exception {
        var base=ResultFixtures.linear(1,1,1,1);var unit=base.units().getFirst();var seq=unit.sequences().getFirst();
        var assign=(io.github.gustavo2358.air.model.Operations.Assign)seq.instructions().getFirst();
        var read=new io.github.gustavo2358.air.model.Expressions.Read(ResultFixtures.operand(assign.header().id(),"read",io.github.gustavo2358.air.model.Operand.Role.VALUE_READ),new io.github.gustavo2358.air.model.Places.ObjectPlace(ResultFixtures.operand(assign.header().id(),"read-place",io.github.gustavo2358.air.model.Operand.Role.VALUE_READ),unit.objects().getFirst().id()));
        // Direct Read is supported; fitting remains outside the scalar copy profile.
        var fit=new io.github.gustavo2358.air.model.Expressions.FitText(ResultFixtures.operand(assign.header().id(),"fit",io.github.gustavo2358.air.model.Operand.Role.VALUE_READ),read,java.math.BigInteger.ONE," ");
        var changed=new io.github.gustavo2358.air.model.Sequence(seq.label(),List.of(new io.github.gustavo2358.air.model.Operations.Assign(assign.header(),assign.destination(),fit)),seq.terminator(),seq.origin());
        var publication=ResultFixtures.publication(base.id(),List.of(ResultFixtures.unit(unit.id(),unit.entries(),List.of(changed),unit.objects())),base.storage());
        var result=new AnalysisDataflow().prepare(publication,"unsupported-profile");
        assertEquals(PreparationStatus.INCOMPLETE,result.result().preparationStatus());assertEquals("DEPENDENCY_UNAVAILABLE",result.result().results().getFirst().reason());
        var text=new String(bytes(result),StandardCharsets.UTF_8);
        assertTrue(text.contains("\"observation\":{\"reason\":\"DEPENDENCY_UNAVAILABLE\",\"status\":\"NOT_STARTED\"}"),"preserve the actual W4 batch reason");
        assertTrue(text.contains("\"preparationStatus\":\"INCOMPLETE\""));
        var path=java.nio.file.Path.of("target/w5-cases/unsupported-profile.json");java.nio.file.Files.createDirectories(path.getParent());
        var attempt=new LocalResultWriter().write(result,path);assertEquals(DeliveryReceipt.Status.COMPLETE,attempt.receipt().status());assertEquals(PreparationStatus.INCOMPLETE,result.result().preparationStatus());
    }
    @Test void distinctCandidatesKeepTheirOwnProducerSupport()throws Exception {
        var p=WireTest.fixture();var o=p.result().results().getFirst().observations().getFirst();var old=(ValueFact)o.value();var u=o.query().point().entry().unit();
        var a=new OperationId(u,"producer-a");var b=new OperationId(u,"producer-b");var origin=new OriginId(u.publication(),"origin");
        var f=new ValueFact(old.cell(),ValueFact.Reachability.REACHABLE,List.of(new TextValue("A"),new TextValue("B")),false,true,true,List.of(),List.of(a,b),List.of(origin),List.of(new ValueFact.CandidateSupport(new TextValue("A"),List.of(new ValueFact.Support(a,origin,List.of()))),new ValueFact.CandidateSupport(new TextValue("B"),List.of(new ValueFact.Support(b,origin,List.of())))));
        var out=new ByteArrayOutputStream();var json=new JsonOutput(out);json.value(ResultJson.observation(p,new ObservationBatch.Observation<>(o.query(),o.status(),null,f)));json.finish();var text=out.toString(StandardCharsets.UTF_8);
        assertTrue(text.contains("\"candidate\":\"A\",\"producers\":[{\"evidence\":{\"domain\":\"operation\",\"localId\":\"producer-a\""));
        assertTrue(text.contains("\"candidate\":\"B\",\"producers\":[{\"evidence\":{\"domain\":\"operation\",\"localId\":\"producer-b\""));
    }
    static byte[] bytes(PreparedDataflowResult r)throws IOException {var out=new ByteArrayOutputStream();new ResultJson().write(r,out);return out.toByteArray();}
    static <T> List<T> reverse(List<T> values){var r=new ArrayList<>(values);Collections.reverse(r);return r;}
    @Test void permutedInputRegistrationQueriesCandidatesSupportsAndFactsAreByteStable()throws Exception {
        var original=ResultFixtures.linear(1,2,2,2);
        var prepared=new AnalysisDataflow().prepare(original,"permuted");var r=prepared.result();
        var batches=new ArrayList<BatchResult>();
        for(var batch:r.results()) {
            var observations=new ArrayList<ObservationBatch.Observation<?,?>>();
            for(var o:batch.observations()){
                var f=(ValueFact)o.value();var support=f.candidateSupports().getFirst().producers().getFirst();
                var candidates=List.of(new TextValue("z"),new TextValue("á\n\t\"\\𝄞 e\u0301"));
                var value=new ValueFact(f.cell(),f.reachability(),candidates,false,false,false,f.premises(),f.evidence(),f.provenance(),candidates.stream().map(v->new ValueFact.CandidateSupport(v,List.of(support))).toList());
                observations.add(new ObservationBatch.Observation<>(o.query(),o.status(),o.reason(),value));
            }
            batches.add(new BatchResult(batch.batchId(),batch.status(),batch.reason(),observations,batch.metrics()));
        }
        var full=new PreparedAnalysisResult<>(r.resultId(),r.publicationId(),r.planningEpoch(),r.analyses(),batches,r.consumerPlan(),r.consumers(),r.preparationStatus(),r.metrics());
        var normal=new PreparedDataflowResult(full,prepared.sourceScopes(),prepared.subjectCells(),prepared.compositionMetrics());
        var flipped=new ArrayList<BatchResult>();for(var b:reverse(batches)){
            var observations=new ArrayList<ObservationBatch.Observation<?,?>>();for(var o:reverse(b.observations())){var f=(ValueFact)o.value();var value=new ValueFact(f.cell(),f.reachability(),reverse(f.candidates()),f.modelValueRemainder(),f.sourceUnknownRemainder(),f.effectiveUnknownRemainder(),reverse(f.premises()),reverse(f.evidence()),reverse(f.provenance()),reverse(f.candidateSupports()));observations.add(new ObservationBatch.Observation<>(o.query(),o.status(),o.reason(),value));}
            flipped.add(new BatchResult(b.batchId(),b.status(),b.reason(),observations,b.metrics()));
        }
        var consumers=reverse(r.consumers()).stream().map(c->new ConsumerOutcome<>(c.consumerId(),c.status(),c.reason(),reverse(c.facts()))).toList();
        var reordered=new PreparedAnalysisResult<>(r.resultId(),r.publicationId(),r.planningEpoch(),reverse(r.analyses()),flipped,reverse(r.consumerPlan()),consumers,r.preparationStatus(),new LinkedHashMap<>(r.metrics()));
        assertArrayEquals(bytes(normal),bytes(new PreparedDataflowResult(reordered,prepared.sourceScopes(),prepared.subjectCells(),prepared.compositionMetrics())));
        String text=new String(bytes(normal),StandardCharsets.UTF_8);assertTrue(text.contains("á\\n\\t\\\"\\\\𝄞 e\u0301"));assertTrue(text.endsWith("\n"));assertFalse(text.endsWith("\n\n"));
        var u=original.units().getFirst();var permuted=ResultFixtures.publication(original.id(),List.of(ResultFixtures.unit(u.id(),reverse(u.entries()),reverse(u.sequences()),reverse(u.objects()))),original.storage());
        assertArrayEquals(bytes(prepared),bytes(new AnalysisDataflow().prepare(permuted,"permuted")));
    }
    @Test void fullIdentitySeparatesOwnersEvenWhenLocalIdsCollide() {
        var p=new PublicationId("same");var other=new PublicationId("other");var u=new UnitId(p,"same");var v=new UnitId(p,"other");
        var ids=List.<Id>of(new ObjectId(u,"same"),new ObjectId(v,"same"),new ObjectId(new UnitId(other,"same"),"same"),new OperationId(u,"same"),new OperandId(new OperationOwner(new OperationId(u,"same")),"same"),new OperandId(new EntryOwner(new EntryId(u,"same")),"same"));
        assertEquals(ids.size(),ids.stream().map(WireIds::id).distinct().count());var ordered=new TreeSet<>(WireIds.ORDER);ordered.addAll(ids);assertEquals(ids.size(),ordered.size());
        assertEquals("same",WireIds.id(ids.getFirst()).get("publication"));assertEquals("same",WireIds.id(ids.getFirst()).get("unit"));assertInstanceOf(Map.class,WireIds.id(ids.get(4)).get("owner"));
    }
    @Test void unsupportedAndUnreachableHaveDifferentExplicitNullSemantics()throws Exception {
        var p=WireTest.fixture();var q=p.result().results().getFirst().observations().getFirst().query();var out=new ByteArrayOutputStream();var json=new JsonOutput(out);
        json.value(ResultJson.observation(p,new ObservationBatch.Observation<>(q,ObservationBatch.QueryStatus.UNSUPPORTED_POINT,ObservationBatch.PointReason.AFTER_TERMINATOR,null)));json.finish();String refused=out.toString(StandardCharsets.UTF_8);
        for(String f:List.of("value","reachability","precision","sourceUnknownRemainder","effectiveUnknownRemainder"))assertTrue(refused.contains("\""+f+"\":null"));
        var cell=p.subjectCells().values().iterator().next();var value=new ValueFact(cell,ValueFact.Reachability.UNREACHABLE_IN_MODEL,null,null,true,true,List.of(),List.of(),List.of(),List.of());out.reset();json=new JsonOutput(out);json.value(ResultJson.observation(p,new ObservationBatch.Observation<>(q,ObservationBatch.QueryStatus.VALUE,null,value)));json.finish();
        String unreachable=out.toString(StandardCharsets.UTF_8);assertTrue(unreachable.contains("\"value\":null"));assertTrue(unreachable.contains("UNREACHABLE_IN_MODEL"));assertTrue(unreachable.contains("\"sourceUnknownRemainder\":true"));
    }
    @Test void invalidUnicodeIsEncodingFailureAndDoesNotCertifyDelivery()throws Exception {
        var p=new AnalysisDataflow().prepare(ResultFixtures.linear(1,1,1,1),"bad-\ud800");assertThrows(IllegalArgumentException.class,()->bytes(p));
    }
}
