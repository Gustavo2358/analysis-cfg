package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.analysis.dataflow.AnalysisDataflow;
import io.github.gustavo2358.analysis.values.ValueFact;
import io.github.gustavo2358.air.model.Ids.*;
import java.nio.file.*;
import java.util.*;

/** Test-only independent semantic projection from public Java facts, never from result JSON. */
public final class MemoryOracle {
    private MemoryOracle() { }
    public static void main(String[] args) throws Exception {
        var input=new DataflowAirReader().read(Path.of(args[0]));
        var prepared=new AnalysisDataflow().prepare(input.publication(),args[2]);var r=prepared.result();
        var rows=new ArrayList<Object>();
        for(var b:r.results())for(var o:b.observations()) {
            var f=(ValueFact)o.value();var q=o.query();var row=new LinkedHashMap<String,Object>();
            row.put("batch",b.batchId().id());row.put("entry",identity(q.point().entry()));
            row.put("position",q.point().kind().name());row.put("operation",identity(q.point().operation()));
            row.put("object",identity((Id)q.subject()));row.put("queryStatus",o.status().name());
            row.put("reason",o.reason()==null?null:o.reason().name());
            row.put("reachability",f==null?null:f.reachability().name());row.put("cell",f==null?null:identity(f.cell()));
            row.put("candidates",f==null||f.candidates()==null?null:f.candidates().stream().map(v->v.value()).sorted().toList());
            row.put("model",f==null?null:f.modelValueRemainder());row.put("source",f==null?null:f.sourceUnknownRemainder());row.put("effective",f==null?null:f.effectiveUnknownRemainder());
            row.put("premises",f==null?List.of():identities(f.premises()));row.put("evidence",f==null?List.of():identities(f.evidence()));row.put("origins",f==null?List.of():identities(f.provenance()));
            var supports=new ArrayList<Object>();if(f!=null)for(var s:f.candidateSupports())for(var p:s.producers())supports.add(List.of(s.candidate().value(),identity(p.evidence()),identity(p.origin()),identities(p.premises())));
            row.put("supports",supports);rows.add(row);
        }
        var summary=new LinkedHashMap<String,Object>();summary.put("resultId",r.resultId());summary.put("publication",identity(r.publicationId()));summary.put("status",r.preparationStatus().name());summary.put("epoch",r.planningEpoch());summary.put("observations",rows);
        summary.put("analyses",r.analyses().stream().map(a->List.of(identity(a.key().entry()),a.key().implementation(),a.key().version(),a.key().profile(),a.key().direction().name(),a.key().precisionPolicy(),a.key().options(),a.status().name(),a.reason()==null?"":a.reason(),a.metrics())).toList());
        summary.put("batches",r.results().stream().map(b->List.of(b.batchId().id(),b.batchId().projection(),b.status().name(),b.reason()==null?"":b.reason(),b.metrics())).toList());
        summary.put("consumers",r.consumers().stream().map(c->List.of(c.consumerId(),c.status().name(),c.reason()==null?"":c.reason(),c.facts().stream().map(f->List.of(identity(f.sequence()),f.observationBatchId(),f.query().point().kind().name(),identity(f.query().point().entry()),identity(f.query().point().operation()),identity(f.query().subject()))).toList())).toList());
        summary.put("metrics",r.metrics());summary.put("composition",prepared.compositionMetrics());summary.put("airReads",input.airReads());summary.put("airBytesObserved",input.airBytesObserved());
        try(var out=Files.newOutputStream(Path.of(args[1]))){var json=new JsonOutput(out);json.value(summary);json.finish();}
        try(var out=Files.newOutputStream(Path.of(args[3]))){new ResultJson().write(prepared,out);}
    }
    private static List<String> identities(Collection<? extends Id> ids){return ids.stream().map(MemoryOracle::identity).sorted().toList();}
    private static String identity(Id value) {
        if(value==null)return "";
        // Length-prefixed owner components are independent of the wire ID encoder.
        var components=new ArrayList<String>();components.add(value.getClass().getSimpleName());components.add(value.publication().localId());
        if(value instanceof EntryId x)components.add(x.unit().localId());
        else if(value instanceof OperationId x)components.add(x.unit().localId());
        else if(value instanceof ObjectId x)components.add(x.unit().localId());
        else if(value instanceof LabelId x)components.add(x.unit().localId());
        else if(value instanceof OperandId x){components.add(x.owner().unit().localId());components.add(x.owner() instanceof OperationOwner o?identity(o.operation()):identity(((EntryOwner)x.owner()).entry()));}
        components.add(value.localId());return components.stream().map(s->s.length()+":"+s).reduce("",String::concat);
    }
}
