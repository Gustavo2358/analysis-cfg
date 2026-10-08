package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.air.model.Origins;
import io.github.gustavo2358.analysis.dependencies.DirectDependencyResult;
import java.io.*;
import java.util.Comparator;
import static io.github.gustavo2358.analysis.adapters.JsonOutput.Fields;

/** Streaming wire projection for the validated snapshot-native dependency result. */
public final class SnapshotDependencyJson {
    public void write(DirectDependencyResult result,OutputStream stream)throws IOException {
        var out=new JsonOutput(stream);boolean partial=result.coverage()!=io.github.gustavo2358.air.model.Evidence.InventoryStatus.COMPLETE||result.sites().stream().anyMatch(DirectDependencyResult.Site::unknownRemainder);
        out.value(object("schema","analysis-dependency-result","version","3.0.0","airVersion","2.0.0",
            "publication",id(result.publication()),"analysisStatus",partial?"PARTIAL":"COMPLETE","analysisBoundary","COBOL_SOURCE_ONLY",
            "modelScope","VALIDATED_SNAPSHOT_DEPENDENCY","valuesProfile","snapshot-text-relations@2","publicationInventory",result.coverage().name(),
            "sites",result.sites().stream().sorted(Comparator.comparing(DirectDependencyResult.Site::entry,io.github.gustavo2358.analysis.plan.AnalysisKey.ENTRY_ORDER).thenComparing(s->s.operation().localId())).map(SnapshotDependencyJson::site),
            "edges",result.sites().stream().sorted(Comparator.comparing(DirectDependencyResult.Site::entry,io.github.gustavo2358.analysis.plan.AnalysisKey.ENTRY_ORDER).thenComparing(s->s.operation().localId())).flatMap(s->s.candidates().stream().sorted(Comparator.comparing(DirectDependencyResult.Candidate::referenceName).thenComparing(DirectDependencyResult.Candidate::rawValue)).map(c->edge(s,c))),
            "origins",result.origins().stream().sorted(Comparator.comparing(o->o.id().localId())).map(SnapshotDependencyJson::origin),
            "artifacts",result.artifacts().stream().sorted(Comparator.comparing(a->a.id().localId())).map(a->object("id",id(a.id()),"logicalName",a.logicalName(),"contentDigest",a.contentDigest().orElse(null))),
            "metrics",object("sites",(long)result.sites().size(),"candidates",result.sites().stream().mapToLong(s->s.candidates().size()).sum())));
        out.finish();
    }
    private static Fields site(DirectDependencyResult.Site site){return object("caller",id(site.caller()),"entry",id(site.entry()),"sequence",id(site.sequence()),"operation",id(site.operation()),
        "siteOrigin",id(site.siteOrigin()),"targetOrigin",id(site.targetOrigin()),"coverage",site.coverage().name(),"technology","COBOL","command","CALL","namespace",site.namespace(),"targetKind","COMPUTED","subject",id(site.subject()),
        "targetStatus",site.candidates().isEmpty()?"OPEN_TARGET":"RESOLVED_CANDIDATES","modelValueRemainder",site.unknownRemainder(),"effectiveUnknownRemainder",site.unknownRemainder(),"analysisStatus",site.unknownRemainder()?"PARTIAL":"COMPLETE",
        "candidates",site.candidates().stream().sorted(Comparator.comparing(DirectDependencyResult.Candidate::referenceName).thenComparing(DirectDependencyResult.Candidate::rawValue)).map(SnapshotDependencyJson::candidate));}
    private static Fields candidate(DirectDependencyResult.Candidate candidate){return object("referenceName",candidate.referenceName(),"rawValue",candidate.rawValue(),"supports",candidate.supports().stream().sorted(Comparator.comparing(s->s.producer().localId())).map(s->object("kind","VALUE_PRODUCER","producer",id(s.producer()),"origin",id(s.origin()),"premises",s.premises().stream().sorted(WireIds.ORDER).map(SnapshotDependencyJson::id))));}
    private static Fields edge(DirectDependencyResult.Site site,DirectDependencyResult.Candidate candidate){return object("caller",id(site.caller()),"entry",id(site.entry()),"site",id(site.operation()),"candidate",candidate(candidate),"openSite",site.unknownRemainder());}
    private static Fields origin(Origins.Origin origin){return switch(origin){
        case Origins.Unavailable value->object("id",id(value.id()),"kind","UNAVAILABLE","reason",value.reason());
        case Origins.Derived value->object("id",id(value.id()),"kind","DERIVED","inputs",value.inputs().stream().sorted(WireIds.ORDER).map(SnapshotDependencyJson::id),"rule",value.rule());
        case Origins.Contractual value->object("id",id(value.id()),"kind","CONTRACTUAL","authority",value.authority(),"version",value.version());
        case Origins.Written value->object("id",id(value.id()),"kind","WRITTEN","artifact",id(value.artifact()),"location",value.location().map(SnapshotDependencyJson::location).orElse(null),"exact",value.exact(),"includes",value.includes().stream().map(i->object("including",id(i.including()),"included",id(i.included()),"requestedName",i.requestedName(),"site",i.site().map(SnapshotDependencyJson::location).orElse(null))));
    };}
    private static Object id(Id value){return value instanceof ArtifactId artifact?object("domain","artifact","localId",artifact.localId(),"publication",artifact.publication().localId()):WireIds.id(value);}
    private static Fields location(Origins.Location location){return switch(location){
        case Origins.Offsets value->object("kind","OFFSETS","start",value.start().toString(),"end",value.end().toString(),"unit",value.unit(),"endExclusive",value.endExclusive());
        case Origins.LineColumns value->{var span=value.span();yield object("kind","LINE_COLUMNS","startLine",span.start().line().toString(),"startColumn",span.start().column().toString(),"endLine",span.end().line().toString(),"endColumn",span.end().column().toString(),"lineBase",span.lineBase().toString(),"columnBase",span.columnBase().toString(),"columnUnit",span.columnUnit().name(),"endExclusive",span.endExclusive());}
    };}
    private static Fields object(Object... values){return new Fields(values);}
}
