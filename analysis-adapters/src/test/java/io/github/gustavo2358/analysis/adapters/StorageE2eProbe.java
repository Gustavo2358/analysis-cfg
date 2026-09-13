package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.air.json.AirJson;
import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.analysis.cfg.application.*;
import io.github.gustavo2358.analysis.cfg.domain.ProjectionPolicy;
import io.github.gustavo2358.analysis.cfg.extension.SemanticInterpreterRegistry;
import io.github.gustavo2358.analysis.query.*;
import io.github.gustavo2358.analysis.rd.ReachingDefinitions;
import io.github.gustavo2358.analysis.storage.*;
import io.github.gustavo2358.analysis.structure.AnalysisSession;
import io.github.gustavo2358.analysis.values.*;
import java.nio.file.*;
import java.util.*;

/** Test-only detached diagnostics for real producer files; not the public analysis-result wire. */
public final class StorageE2eProbe {
    private StorageE2eProbe() { }
    public static void main(String[] args) throws Exception {
        if(args.length!=2)throw new IllegalArgumentException("AIR input and test diagnostic output required");
        var codec=new AirJson();var bytes=Files.readAllBytes(Path.of(args[0]));var publication=codec.decode(bytes);
        var canonical=codec.encode(publication);
        if(!Arrays.equals(canonical,codec.encode(codec.decode(canonical))))throw new AssertionError("canonical AIR round-trip");
        var cfg=new CfgBuildCoordinator(SemanticInterpreterRegistry.empty()).build(publication,BuildOptions.defaults());
        if(cfg.status()!=CfgBuildResult.Status.CFG_BUILT)throw new AssertionError(cfg.status());
        var admission=AnalysisSession.open(cfg,publication,ProjectionPolicy.KNOWN_SUBSET,publication.units().stream().flatMap(u->u.entries().stream()).toList());
        if(admission.status()!=AnalysisSession.Status.ACCEPTED)throw new AssertionError(admission.reason());
        var session=admission.session().orElseThrow();var effects=new StatementEffects(new StorageIndex(session));
        var queries=new ArrayList<PointQuery<ObjectId>>();var skipped=new ArrayList<String>();
        for(var context:session.contexts())for(var site:session.index().sites(Operations.Invoke.class))if(site.owner().id().equals(context.entry().id().unit())) {
            var invoke=(Operations.Invoke)site.operation();
            if(invoke.target() instanceof Interactions.ComputedTarget target&&target.name() instanceof Expressions.Read read&&read.place() instanceof Places.ObjectPlace object)
                queries.add(new PointQuery<>(ProgramPoint.before(context.entry().id(),invoke.header().id()),object.object()));
            else skipped.add(invoke.header().id().localId());
        }
        var rd=ReachingDefinitions.prepare(effects);if(rd.status()!=ReachingDefinitions.Status.ACCEPTED)throw new AssertionError(rd.reason());
        var rdExecution=rd.analysis().orElseThrow().execute();var definitions=rdExecution.observe(queries);
        var values=RegionalValuesAnalysis.prepare(session);if(values.status()!=RegionalValuesAnalysis.Status.ACCEPTED)throw new AssertionError(values.reason());
        var valueExecution=values.analysis().orElseThrow().execute();var projected=valueExecution.observe(queries);
        var valueMap=new HashMap<PointQuery<ObjectId>,RegionalValueFact>();
        for(var observation:projected.observations()) {
            if(observation.status()!=ObservationBatch.QueryStatus.VALUE)throw new AssertionError(observation.status());
            valueMap.put(observation.query(),observation.value());
        }
        var output=new ArrayList<Map<String,Object>>();
        for(var observation:definitions.observations()) {
            if(observation.status()!=ObservationBatch.QueryStatus.VALUE)throw new AssertionError(observation.status());
            var query=observation.query();var fact=observation.value();var value=valueMap.get(query);var row=new TreeMap<String,Object>();
            row.put("unit",query.point().entry().unit().localId());row.put("operation",query.point().operation().localId());row.put("entry",query.point().entry().localId());row.put("object",query.subject().localId());
            row.put("reachability",fact.reachability().name());row.put("rdModelRemainder",fact.unknownRemainder());row.put("rdSourceRemainder",fact.sourceUnknownRemainder());
            row.put("values",value.candidates()==null?null:value.candidates().stream().map(Values.TextValue::value).toList());row.put("valueModelRemainder",value.modelValueRemainder());row.put("valueSourceRemainder",value.sourceUnknownRemainder());
            var contributions=new ArrayList<Map<String,Object>>();
            for(var contribution:fact.definitions()) {
                var event=contribution.definition();var entry=new TreeMap<String,Object>();entry.put("kind",event.kind().name());entry.put("operation",event.operation().map(OperationId::localId).orElse(null));entry.put("unknown",event.unknown());entry.put("origin",event.origin().localId());
                entry.put("ranges",contribution.contributedRanges().stream().map(StorageE2eProbe::location).toList());contributions.add(entry);
            }
            row.put("definitions",contributions);row.put("interpretations",value.interpretations().stream().map(i->location(i.location())).toList());
            row.put("producers",value.candidateSupports().stream().map(c->{var producer=new TreeMap<String,Object>();producer.put("value",c.candidate().value());producer.put("operations",c.producers().stream().map(p->p.evidence().localId()).sorted().toList());return producer;}).toList());output.add(row);
        }
        output.sort(Comparator.comparing((Map<String,Object> row)->row.get("unit").toString()).thenComparing(row->row.get("entry").toString()).thenComparing(row->row.get("operation").toString()).thenComparing(row->row.get("object").toString()));
        var result=new TreeMap<String,Object>();result.put("schema","storage-e2e-diagnostic@1");result.put("status","TEST_ONLY_NOT_PUBLIC_PRODUCT_WIRE");result.put("queries",output);result.put("unreadableOrLiteralCalls",skipped.stream().sorted().toList());result.put("rdMetrics",new TreeMap<>(rdExecution.metrics()));result.put("valueMetrics",new TreeMap<>(valueExecution.metrics()));
        try(var stream=Files.newOutputStream(Path.of(args[1]))) {
            var json=new JsonOutput(stream);json.value(result);json.finish();
        }
    }
    private static Map<String,Object> location(StorageIndex.ContextualLocation contextual) {
        var location=contextual.location();var row=new TreeMap<String,Object>();row.put("base",location.base().id().localId());row.put("activation",contextual.activation().map(EntryId::localId).orElse(null));
        row.put("kind",location.range().isEmpty()?"LOGICAL_CELL":"BYTE_RANGE");row.put("start",location.range().map(r->r.start().toString()).orElse(null));row.put("end",location.range().flatMap(StorageRange::end).map(Object::toString).orElse(null));return row;
    }
}
