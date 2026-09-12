package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.analysis.dependencies.*;
import java.io.*;
import java.util.*;
import static io.github.gustavo2358.analysis.dependencies.DependencySiteFact.*;

/** Explicit, deterministic dependency-result mapping. No bean, enum-name or AIR parsing shortcuts. */
public final class DependencyJson {
    public void write(DependencyResult result,OutputStream stream) throws IOException {
        var out=new JsonOutput(stream);
        out.value(object("schema","analysis-dependency-result","version","1.0.0","airVersion",version(result.airVersion()),
            "publication",id(result.publication()),"interpretationProfile",CallNameInterpreter.PROFILE,"valuesProfile","scalar-text-effects@1",
            "modelScope","KNOWN_GRAPH_ENTRY","publicationInventory",inventory(result.publicationInventory()),
            "sites",result.sites().stream().sorted(Comparator.comparing(DependencySiteFact::entry,io.github.gustavo2358.analysis.plan.AnalysisKey.ENTRY_ORDER).thenComparing(f->f.operation().localId())).map(DependencyJson::site).toList(),
            "edges",result.edges().stream().sorted(Comparator.comparing(DependencyResult.Edge::entry,io.github.gustavo2358.analysis.plan.AnalysisKey.ENTRY_ORDER).thenComparing(e->e.site().localId()).thenComparing(e->e.candidate().referenceName()).thenComparing(e->e.candidate().rawValue())).map(e->object("caller",id(e.caller()),"entry",id(e.entry()),"site",id(e.site()),"candidate",candidate(e.candidate()),"openSite",e.openSite())).toList(),
            "metrics",result.metrics(),"origins",result.origins().stream().sorted(Comparator.comparing(o->o.id().localId())).map(DependencyJson::origin).toList(),
            "artifacts",result.artifacts().stream().sorted(Comparator.comparing(a->a.id().localId())).map(a->object("id",id(a.id()),"logicalName",a.logicalName(),"contentDigest",a.contentDigest().orElse(null))).toList(),
            "sourceUncertaintyRefs",ids(result.sourceUncertaintyRefs())));
        out.finish();
    }
    private static Object site(DependencySiteFact f) {
        return object("caller",id(f.caller()),"entry",id(f.entry()),"sequence",id(f.sequence()),"operation",id(f.operation()),"offset",f.offset(),
            "siteOrigin",id(f.siteOrigin()),"targetOrigin",id(f.targetOrigin()),"targetKind",switch(f.targetKind()){case LITERAL->"LITERAL";case COMPUTED->"COMPUTED";},
            "subject",id(f.subject()),"valuePoint",f.valuePoint()==null?null:ResultJson.point(f.valuePoint()),
            "reachability",switch(f.reachability()){case REACHABLE->"REACHABLE";case UNREACHABLE_IN_MODEL->"UNREACHABLE_IN_MODEL";},
            "targetStatus",switch(f.targetStatus()){case RESOLVED_CANDIDATES->"RESOLVED_CANDIDATES";case OPEN_TARGET->"OPEN_TARGET";case UNREACHABLE_IN_MODEL->"UNREACHABLE_IN_MODEL";case UNSUPPORTED_TARGET_EXPRESSION->"UNSUPPORTED_TARGET_EXPRESSION";case UNSUPPORTED_INVOCATION_SHAPE->"UNSUPPORTED_INVOCATION_SHAPE";},
            "rawCandidates",f.rawCandidates().stream().sorted(Comparator.comparing(RawCandidate::rawValue)).map(r->object("rawValue",r.rawValue(),"supports",supports(r.supports()))).toList(),
            "candidates",f.candidates().stream().sorted(Comparator.comparing(Candidate::referenceName).thenComparing(Candidate::rawValue)).map(DependencyJson::candidate).toList(),
            "modelValueRemainder",f.modelValueRemainder(),"sourceValueRemainder",f.sourceValueRemainder(),"interpretationUnknownRemainder",f.interpretationUnknownRemainder(),
            "effectiveUnknownRemainder",f.effectiveUnknownRemainder(),"openControlRemainder",f.openControlRemainder(),"evidence",ids(f.evidence()),"provenance",ids(f.provenance()),"premises",ids(f.premises()),"uncertaintyRefs",ids(f.uncertaintyRefs()));
    }
    private static Object candidate(Candidate c){return object("referenceName",c.referenceName(),"rawValue",c.rawValue(),"supports",supports(c.supports()));}
    private static Object supports(List<Support> supports){return supports.stream().sorted(Comparator.comparing(Support::producer,WireIds.ORDER).thenComparing(Support::origin,WireIds.ORDER)).map(s->object("kind",switch(s.kind()){case VALUE_PRODUCER->"VALUE_PRODUCER";case CALL_LITERAL->"CALL_LITERAL";},"producer",id(s.producer()),"origin",id(s.origin()),"premises",ids(s.premises()))).toList();}
    private static Object ids(List<? extends Id> ids){return ids.stream().sorted(WireIds.ORDER).map(DependencyJson::id).toList();}
    private static Object id(Id id){return id instanceof ArtifactId a?object("domain","artifact","localId",a.localId(),"publication",a.publication().localId()):WireIds.id(id);}
    private static String version(SemanticVersion v){return v.major()+"."+v.minor()+"."+v.patch();}
    private static String inventory(Evidence.InventoryStatus s){return switch(s){case COMPLETE->"COMPLETE";case PARTIAL->"PARTIAL";case UNAVAILABLE->"UNAVAILABLE";};}
    private static Object origin(Origins.Origin o) {
        return switch(o) {
            case Origins.Derived d -> object("id",id(d.id()),"kind","DERIVED","inputs",ids(d.inputs()),"rule",d.rule());
            case Origins.Unavailable u -> object("id",id(u.id()),"kind","UNAVAILABLE","reason",u.reason());
            case Origins.Contractual c -> object("id",id(c.id()),"kind","CONTRACTUAL","authority",c.authority(),"version",c.version());
            case Origins.Written w -> object("id",id(w.id()),"kind","WRITTEN","artifact",id(w.artifact()),"location",w.location().map(DependencyJson::location).orElse(null),"exact",w.exact(),
                "includes",w.includes().stream().map(i->object("including",id(i.including()),"included",id(i.included()),"requestedName",i.requestedName(),"site",i.site().map(DependencyJson::location).orElse(null))).toList());
        };
    }
    private static Object location(Origins.Location l) {
        return switch(l) {
            case Origins.Offsets o -> object("kind","OFFSETS","start",o.start().toString(),"end",o.end().toString(),"unit",o.unit(),"endExclusive",o.endExclusive());
            case Origins.LineColumns p -> {
                var s=p.span();yield object("kind","LINE_COLUMNS","startLine",s.start().line().toString(),"startColumn",s.start().column().toString(),"endLine",s.end().line().toString(),"endColumn",s.end().column().toString(),"lineBase",s.lineBase().toString(),"columnBase",s.columnBase().toString(),
                    "columnUnit",switch(s.columnUnit()){case UNICODE_SCALAR->"UNICODE_SCALAR";case UTF16_CODE_UNIT->"UTF16_CODE_UNIT";case OCTET->"OCTET";},"endExclusive",s.endExclusive());
            }
        };
    }
    private static Map<String,Object> object(Object... fields){var result=new TreeMap<String,Object>();for(int i=0;i<fields.length;i+=2)result.put((String)fields[i],fields[i+1]);return result;}
}
