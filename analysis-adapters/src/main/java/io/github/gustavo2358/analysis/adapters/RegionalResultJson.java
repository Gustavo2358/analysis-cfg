package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.analysis.dataflow.RegionalAnalysisResult;
import io.github.gustavo2358.analysis.query.*;
import io.github.gustavo2358.analysis.rd.*;
import io.github.gustavo2358.analysis.storage.*;
import io.github.gustavo2358.analysis.values.*;
import java.io.*;
import java.math.BigInteger;
import java.util.*;
import java.util.function.Function;
import static io.github.gustavo2358.analysis.adapters.ResultJson.object;
import static io.github.gustavo2358.analysis.adapters.WireIds.id;

/** Closed regional result v1. Facts are detached canonical projections; no AIR interpretation here. */
public final class RegionalResultJson {
    public static final String VERSION="1.4.0";
    public void write(RegionalAnalysisResult result,OutputStream output) throws IOException {
        var json=new JsonOutput(output);json.value(payload(result));json.finish();
    }
    private static Object payload(RegionalAnalysisResult r) {
        var inventory=r.inventory();
        var observations=r.observations().stream().sorted(Comparator.comparing((RegionalAnalysisResult.Observation o)->o.query().point(),ProgramPoint.ORDER).thenComparing(o->o.query().subject(),StorageSubject.ORDER)).toList();
        boolean occurrenceLogical=r.observations().stream().anyMatch(o->o.query().subject() instanceof StorageSubject.PlaceOccurrence
            &&(o.rd().value()!=null&&o.rd().value().definitions().stream().anyMatch(d->d.definition().logicalObject().isPresent())
                ||o.values().value()!=null&&!o.values().value().logicalAlternatives().isEmpty()));
        String version=occurrenceLogical?VERSION:r.observations().stream().anyMatch(o->o.values().value()!=null&&o.values().value().alternatives().stream().flatMap(a->a.fragments().stream()).anyMatch(f->f.logicalCapture().isPresent()))?"1.3.0":r.observations().stream().anyMatch(o->o.rd().value()!=null&&o.rd().value().definitions().stream().anyMatch(d->d.definition().logicalObject().isPresent())||o.values().value()!=null&&!o.values().value().logicalAlternatives().isEmpty())?"1.2.0":r.observations().stream().anyMatch(o->o.query().subject() instanceof StorageSubject.PlaceOccurrence)?"1.1.0":"1.0.0";
        return object("schema","regional-analysis-result","version",version,"resultId",r.resultId(),"publicationId",id(r.publicationId()),"profile",RegionalValuesAnalysis.PROFILE,
            "status","COMPLETE","pathWitness","NOT_PROVIDED","referenceAuthority","VALIDATED_AIR_PUBLICATION",
            "inventory",object("ids",ids(inventory.ids()),"storages",each(inventory.storages().stream().sorted(Comparator.comparing(s->s.header().id(),WireIds.ORDER)).toList(),RegionalResultJson::storage),
                "scopes",each(inventory.scopes().stream().sorted(Comparator.comparing(RegionalAnalysisResult.SourceScope::entry,WireIds.ORDER)).toList(),RegionalResultJson::scope)),
            "observations",each(observations,o->object("point",ResultJson.point(o.query().point()),"subject",subject(o,occurrenceLogical),
                "rd",observed(o.rd(),RegionalResultJson::rd),"values",observed(o.values(),RegionalResultJson::value))),"statistics",r.statistics());
    }
    private static Object storage(RegionalAnalysisResult.Storage s) {
        var h=s.header();return object("storageId",id(h.id()),"owner",h.owner().map(WireIds::id).orElse(null),"lifetime",h.lifetime().name(),"visibility",h.visibility().name(),"origin",id(h.origin()),
            "kind",s.region()?"REGION":"CELL","extent",s.extent().map(BigInteger::toString).orElse(null),"extentUnknown",s.extentUnknown().map(WireIds::id).orElse(null));
    }
    private static Object scope(RegionalAnalysisResult.SourceScope s) {
        return object("entryId",id(s.entry()),"publicationInventory",s.publicationInventory().name(),"unitInventory",s.unitInventory().name(),
            "publicationUncertainties",ids(s.publicationUncertainties()),"unitUncertainties",ids(s.unitUncertainties()),"entryUncertainties",ids(s.entryUncertainties()));
    }
    private static <V> Object observed(ObservationBatch.Observation<StorageSubject,V> o,Function<V,Object> projection) {
        return object("status",o.status().name(),"reason",o.reason()==null?null:o.reason().name(),"fact",o.value()==null?null:projection.apply(o.value()));
    }
    private static Object subject(RegionalAnalysisResult.Observation observation,boolean explicit) {
        return switch(observation.query().subject()) {
            case StorageSubject.NamedObject named -> object("kind","NAMED_OBJECT","objectId",id(named.object()));
            case StorageSubject.PlaceOccurrence occurrence -> explicit?object("kind","PLACE_OCCURRENCE","operandId",id(occurrence.occurrence()),"explicitObjectIds",ids(observation.explicitObjects())):object("kind","PLACE_OCCURRENCE","operandId",id(occurrence.occurrence()));
            case StorageSubject.PhysicalRange physical -> object("kind","PHYSICAL_RANGE","storageId",id(physical.storage()),"range",range(physical.range()),"codec",codec(physical.codec()));
        };
    }
    private static Object range(StorageRange r){return object("unit","OCTET","start",r.start().toString(),"end",r.end().map(BigInteger::toString).orElse(null));}
    private static Object location(StorageIndex.ContextualLocation c) {
        return object("storageId",id(c.location().base().id()),"activation",c.activation().map(WireIds::id).orElse(null),"kind",c.location().range().isPresent()?"BYTE_RANGE":"WHOLE_CELL","range",c.location().range().map(RegionalResultJson::range).orElse(null));
    }
    private static Object interpretation(RegionalValueFact.Interpretation i){return object("location",location(i.location()),"codec",i.codec().map(RegionalResultJson::codec).orElse(null));}
    private static Object rd(DefinitionFact f) {
        return object("reachability",f.reachability().name(),"unknownRemainder",f.unknownRemainder(),"resolutionRemainder",f.resolutionRemainder(),"sourceUnknownRemainder",f.sourceUnknownRemainder(),
            "definitions",each(f.definitions(),d->object("definition",event(d.definition()),"contributedRanges",each(d.contributedRanges(),RegionalResultJson::location))),
            "premiseRefs",ids(f.premises()),"provenanceRefs",ids(f.origins()),"uncertaintyRefs",ids(f.uncertainties()));
    }
    private static Object event(DefinitionEvent e) {
        var result=new TreeMap<>(object("entryId",id(e.entry()),"operationId",e.operation().map(WireIds::id).orElse(null),"destination",e.destination().map(WireIds::id).orElse(null),"slot",e.slot(),
            "outcome",outcome(e.outcome().orElse(null)),"storageId",id(e.storage().orElse(null)),"kind",e.kind().name(),"unknown",e.unknown(),"origin",id(e.origin()),"premiseRefs",ids(e.premises()),"uncertaintyRefs",ids(e.uncertainties()),"reasons",e.reasons()));
        e.logicalObject().ifPresent(o->result.put("logicalObjectId",id(o)));return result;
    }
    private static Object outcome(Control.OutcomeKey o) {
        if(o==null)return null;
        return switch(o) {
            case Control.NormalOutcome ignored -> object("kind","normal");
            case Control.ExceptionOutcome exception -> object("kind","exception","tag",exception.tag());
            case Control.OtherExceptionOutcome ignored -> object("kind","other-exception");
            case Control.HaltOutcome ignored -> object("kind","halt");
            case Control.DivergeOutcome ignored -> object("kind","diverge");
        };
    }
    private static Object value(StorageValueFact f) {
        var result=new TreeMap<>(object("reachability",f.reachability().name(),"interpretations",each(f.interpretations(),RegionalResultJson::interpretation),
            "candidates",f.candidates()==null?null:f.candidates().stream().map(Values.TextValue::value).toList(),"modelValueRemainder",f.modelValueRemainder(),
            "sourceUnknownRemainder",f.sourceUnknownRemainder(),"effectiveUnknownRemainder",f.effectiveUnknownRemainder(),
            "candidateSupports",each(f.candidateSupports(),s->object("candidate",s.candidate().value(),"producers",each(s.producers(),p->object("evidence",id(p.evidence()),"origin",id(p.origin()),"premiseRefs",ids(p.premises()))))),
            "premiseRefs",ids(f.premises()),"evidenceRefs",ids(f.evidence()),"provenanceRefs",ids(f.provenance()),"modelReasons",f.modelReasons(),
            "alternatives",each(f.alternatives(),a->object("interpretation",interpretation(a.interpretation()),"candidate",a.candidate().map(Values.TextValue::value).orElse(null),"fragments",each(a.fragments(),RegionalResultJson::fragment)))));
        if(!f.logicalAlternatives().isEmpty())result.put("logicalAlternatives",each(f.logicalAlternatives(),a->object("objectId",id(a.object()),"candidate",a.candidate().value(),
            "producers",each(a.producers(),p->object("evidence",id(p.evidence()),"origin",id(p.origin()),"premiseRefs",ids(p.premises()))))));
        return result;
    }
    private static Object fragment(StorageValueFact.Fragment f) {
        var result=new TreeMap<>(object("location",location(f.location()),"kind",f.kind().name(),"bytes",f.bytes().map(Values.BytesValue::octets).orElse(null),
            "producer",f.producer().map(p->object("definition",event(p.definition()),"contributedRange",location(p.contributedRange()))).orElse(null),
            "unknownWriter",f.unknownWriter().map(RegionalResultJson::event).orElse(null),"captures",each(f.captures(),RegionalResultJson::capture),
            "sourceGaps",each(f.sourceGaps(),g->object("affectedLocation",location(g.affectedLocation()),"origin",id(g.origin()),"uncertaintyRefs",ids(g.uncertainties()))),"modelReasons",f.modelReasons()));
        f.logicalCapture().ifPresent(c->result.put("logicalCapture",object("objectId",id(c.object()),"before",ResultJson.point(c.before()),"producers",each(c.producers(),p->object("evidence",id(p.evidence()),"origin",id(p.origin()),"premiseRefs",ids(p.premises()))))));
        return result;
    }
    private static Object capture(StorageValueFact.Capture c) {
        return object("definition",event(c.definition()),"before",ResultJson.point(c.before()),"sourceRange",location(c.sourceRange()),"destinationRange",location(c.destinationRange()),
            "sourceContribution",location(c.sourceContribution()),"destinationContribution",location(c.destinationContribution()));
    }
    private static Object codec(Memory.Codec c) {
        return switch(c) {
            case Memory.IdentityBytes ignored -> object("kind","IDENTITY_BYTES");
            case Memory.AsciiText ignored -> object("kind","ASCII_TEXT");
            case Memory.BinaryCodec b -> object("kind","BINARY","signed",b.signed(),"width",b.width().toString(),"order",b.order().name());
            case Memory.ExtensionCodec e -> object("kind","EXTENSION","name",e.name(),"version",e.version(),"logicalType",type(e.logicalType()));
            case Memory.UnknownCodec u -> object("kind","UNKNOWN","reason",id(u.reason()),"logicalType",type(u.logicalType()));
        };
    }
    private static Object type(Types.TypeRef ref) {
        if(ref instanceof Types.UnknownType u)return object("kind","UNKNOWN","uncertainty",id(u.uncertainty()));
        return switch(((Types.Known)ref).type()) {
            case Types.Builtin b -> object("kind","BUILTIN","name",b.name());
            case Types.ExtensionType e -> object("kind","EXTENSION","name",e.name(),"version",e.version());
            case Types.LabelType l -> object("kind","LABELS","unitId",id(l.unit()),"labels",ids(l.labels()));
        };
    }
    private static Object ids(Collection<? extends Id> ids){return each(ids.stream().sorted(WireIds.ORDER).toList(),WireIds::id);}
    private static <T,R> Iterable<R> each(Collection<T> items,Function<T,R> transform){return ()->items.stream().map(transform).iterator();}
}
