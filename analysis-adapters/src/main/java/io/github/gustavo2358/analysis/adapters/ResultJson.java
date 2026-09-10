package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.analysis.dataflow.PreparedDataflowResult;
import java.io.*;
import java.util.*;
import java.util.function.Function;
import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.analysis.application.PreparedAnalysisResult.*;
import io.github.gustavo2358.analysis.plan.*;
import io.github.gustavo2358.analysis.query.*;
import io.github.gustavo2358.analysis.values.ValueFact;
import static io.github.gustavo2358.analysis.adapters.WireIds.id;

/** Explicit, versioned result encoding. Does not own or close the caller's stream. */
public final class ResultJson {
    public static final String VERSION="1.1.0";
    public void write(PreparedDataflowResult result,OutputStream output) throws IOException {
        var json=new JsonOutput(output);json.value(payload(result));json.finish();
    }
    private static Map<String,Object> payload(PreparedDataflowResult context) {
        var r=context.result();
        var analyses=new TreeMap<AnalysisKey,AnalysisOutcome>(AnalysisKey.ORDER);r.analyses().forEach(a->analyses.put(a.key(),a));
        var batches=new ArrayList<>(r.results());batches.sort(Comparator.comparing(b->b.batchId().id()));
        var plans=new ArrayList<>(r.consumerPlan());plans.sort(Comparator.comparing(ConsumerPlan::consumerId));
        var consumers=new ArrayList<>(r.consumers());consumers.sort(Comparator.comparing(ConsumerOutcome::consumerId));
        return object("schema","prepared-analysis-result","version",VERSION,"resultId",r.resultId(),"publicationId",id(r.publicationId()),
            "planningEpoch",r.planningEpoch(),"analyses",each(analyses.values(),a->run(context,a,null)),
            "results",each(batches,b->object("observationBatchId",b.batchId().id(),"projection",b.batchId().projection(),"result",run(context,Objects.requireNonNull(analyses.get(b.batchId().analysisKey())),b))),
            "consumerPlan",each(plans,p->object("consumerId",p.consumerId(),"requiredAnalysisKeys",each(p.requiredAnalysisKeys().stream().sorted(AnalysisKey.ORDER).toList(),ResultJson::key),"requiredObservationBatchIds",p.requiredObservationBatchIds().stream().sorted().toList())),
            "consumers",each(consumers,c->object("consumerId",c.consumerId(),"status",consumerStatus(c.status()),"reason",c.reason(),
                "facts",each(c.facts().stream().sorted(Comparator.comparing(io.github.gustavo2358.analysis.dataflow.ObservedValueFact::sequence,WireIds.ORDER).thenComparing(io.github.gustavo2358.analysis.dataflow.ObservedValueFact::observationBatchId).thenComparing(f->f.query().point(),ProgramPoint.ORDER).thenComparing(f->f.query().subject(),WireIds.ORDER)).toList(),f->object("kind","ObservedValueFact","sequenceId",id(f.sequence()),"observationBatchId",f.observationBatchId(),"query",query(f.query()))))),
            "preparationStatus",switch(r.preparationStatus()){case COMPLETE->"COMPLETE";case INCOMPLETE->"INCOMPLETE";},
            "partialPolicy","EXPLICIT_PARTIAL_BY_DEPENDENCY","statistics",object("composition",context.compositionMetrics(),"planning",r.metrics()));
    }
    private static Map<String,Object> run(PreparedDataflowResult c,AnalysisOutcome a,BatchResult batch) {
        var entry=a.key().entry();var scope=Objects.requireNonNull(c.sourceScopes().get(entry),"source scope absent");
        String execution=switch(a.status()){case STABLE->"STABLE";case UNSUPPORTED->"UNSUPPORTED";case INVALID_INPUT->"INVALID_INPUT";};
        boolean stable=a.status()==AnalysisOutcome.Status.STABLE;
        String observation=batch==null?"NOT_STARTED":switch(batch.status()){case COMPLETE->"COMPLETE";case FAILED->"FAILED";case NOT_STARTED->"NOT_STARTED";};
        String observationReason=batch==null?null:batch.reason();
        var ordered=batch==null?new ArrayList<ObservationBatch.Observation<?,?>>():new ArrayList<>(batch.observations());
        ordered.sort(Comparator.comparing((ObservationBatch.Observation<?,?> o)->o.query().point(),ProgramPoint.ORDER).thenComparing(o->(Id)o.query().subject(),WireIds.ORDER));
        return object("schema","analysis-dataflow-result","version",VERSION,"analysisKey",key(a.key()),"publicationId",id(entry.publication()),"unitId",id(entry.unit()),"entryId",id(entry),
            "executionStatus",execution,"modelScope","KNOWN_GRAPH_ENTRY",
            "sourceScope",object("scope",id(entry.unit()),"publicationInventory",inventory(scope.publicationInventory()),"unitInventory",inventory(scope.unitInventory()),"entryUncertaintyRefs",ids(scope.entryUncertaintyRefs()),"remainderPolicy","PER_OBSERVATION_W3"),
            "observations",each(ordered,o->observation(c,o)),"statistics",object("analysis",a.metrics(),"observation",batch==null?null:batch.metrics()),
            "completion",object("admission",phase(stable?"COMPLETE":"REJECTED",stable?null:a.status()==AnalysisOutcome.Status.INVALID_INPUT?"INVALID_STRUCTURE":"UNSUPPORTED_PROFILE"),
                "analysis",phase(stable?"STABLE":"NOT_STARTED",null),"observation",phase(observation,observationReason)),"analysisReason",a.reason());
    }
    static Map<String,Object> observation(PreparedDataflowResult c,ObservationBatch.Observation<?,?> o) {
        var q=o.query();var subject=(ObjectId)q.subject();var f=(ValueFact)o.value();
        boolean refused=o.status()==ObservationBatch.QueryStatus.UNSUPPORTED_POINT;
        return object("point",point(q.point()),"subject",object("place","ObjectPlace","objectId",id(subject),"storageId",id(f==null?c.subjectCells().get(subject):f.cell()),"locationKind","WHOLE_CELL"),
            "queryStatus",refused?"UNSUPPORTED_POINT":"VALUE","queryReason",refused?reason(o.reason()):null,
            "reachability",f==null?null:f.reachability()==ValueFact.Reachability.REACHABLE?"REACHABLE":"UNREACHABLE_IN_MODEL",
            "value",f==null||f.candidates()==null?null:object("domain","known(text)","kind","Candidates","enumerated",f.candidates().stream().map(Values.TextValue::value).sorted().toList(),"modelValueRemainder",f.modelValueRemainder()),
            "sourceUnknownRemainder",f==null?null:f.sourceUnknownRemainder(),"effectiveUnknownRemainder",f==null?null:f.effectiveUnknownRemainder(),
            "precision",f==null?null:object("model",f.modelValueRemainder()==null?"UNREACHABLE_IN_MODEL":f.modelValueRemainder()?"OPEN_IN_ADMITTED_MODEL":"CLOSED_IN_ADMITTED_MODEL","source",f.sourceUnknownRemainder()?"OPEN":"CLOSED","pathWitness","NOT_PROVIDED"),
            "candidateSupports",f==null?List.of():supports(f),"premiseRefs",f==null?List.of():ids(f.premises()),"evidenceRefs",f==null?List.of():ids(f.evidence()),"provenanceRefs",f==null?List.of():ids(f.provenance()));
    }
    private static Object supports(ValueFact fact) {
        var supports=new ArrayList<>(fact.candidateSupports());supports.sort(Comparator.comparing(s->s.candidate().value()));
        return each(supports,s->{var producers=new ArrayList<>(s.producers());producers.sort(Comparator.comparing(ValueFact.Support::evidence,WireIds.ORDER).thenComparing(ValueFact.Support::origin,WireIds.ORDER).thenComparing(ValueFact.Support::premises,ResultJson::compareIds));
            return object("candidate",s.candidate().value(),"producers",each(producers,p->object("evidence",id(p.evidence()),"origin",id(p.origin()),"premiseRefs",ids(p.premises()))));});
    }
    private static int compareIds(List<? extends Id> left,List<? extends Id> right) {
        var a=left.stream().sorted(WireIds.ORDER).toList();var b=right.stream().sorted(WireIds.ORDER).toList();
        for(int i=0;i<Math.min(a.size(),b.size());i++){int c=WireIds.ORDER.compare(a.get(i),b.get(i));if(c!=0)return c;}
        return Integer.compare(a.size(),b.size());
    }
    static Object key(AnalysisKey k) { return object("implementation",k.implementation(),"implementationVersion",k.version(),"profile",k.profile(),"direction",switch(k.direction()){case FORWARD->"FORWARD";case BACKWARD->"BACKWARD";},"precisionPolicy",k.precisionPolicy(),"options",k.options(),"entryId",id(k.entry())); }
    static Object query(PointQuery<?> q) { return object("point",point(q.point()),"objectId",id((ObjectId)q.subject())); }
    static Object point(ProgramPoint p) { return object("position",switch(p.kind()){case ENTRY->"ENTRY";case BEFORE->"BEFORE";case AFTER->"AFTER";case OUTCOME->"OUTCOME";},"entryId",id(p.entry()),"operationId",id(p.operation()),"outcome",outcome(p.outcome())); }
    private static Object outcome(Control.OutcomeKey k) {
        if(k==null)return null;
        return switch(k){case Control.NormalOutcome ignored->object("kind","normal");case Control.ExceptionOutcome e->object("kind","exception","tag",e.tag());case Control.OtherExceptionOutcome ignored->object("kind","other-exception");case Control.HaltOutcome ignored->object("kind","halt");case Control.DivergeOutcome ignored->object("kind","diverge");};
    }
    private static String reason(ObservationBatch.PointReason reason) { return switch(reason){case CONTEXT_NOT_SELECTED->"CONTEXT_NOT_SELECTED";case UNKNOWN_OPERATION->"UNKNOWN_OPERATION";case FOREIGN_UNIT->"FOREIGN_UNIT";case AFTER_TERMINATOR->"AFTER_TERMINATOR";case OUTCOME_UNAVAILABLE->"OUTCOME_UNAVAILABLE";case UNSUPPORTED_SUBJECT->"UNSUPPORTED_SUBJECT";}; }
    private static String inventory(Evidence.InventoryStatus s) { return switch(s){case COMPLETE->"COMPLETE";case PARTIAL->"PARTIAL";case UNAVAILABLE->"UNAVAILABLE";}; }
    private static String consumerStatus(ConsumerStatus s) { return switch(s){case COMPLETE->"COMPLETE";case FAILED->"FAILED";case NOT_STARTED->"NOT_STARTED";}; }
    private static Object phase(String status,String reason) { return object("status",status,"reason",reason); }
    private static Object ids(Collection<? extends Id> ids) { var sorted=new ArrayList<>(ids);sorted.sort(WireIds.ORDER);return each(sorted,WireIds::id); }
    static Map<String,Object> object(Object... pairs) {
        var map=new TreeMap<String,Object>();for(int i=0;i<pairs.length;i+=2)map.put((String)pairs[i],pairs[i+1]);return Collections.unmodifiableMap(map);
    }
    private static <T,R> Iterable<R> each(Collection<T> values,Function<T,R> map) { return ()->values.stream().map(map).iterator(); }
}
