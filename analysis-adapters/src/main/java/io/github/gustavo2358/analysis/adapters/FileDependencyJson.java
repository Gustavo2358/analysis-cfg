package io.github.gustavo2358.analysis.adapters;
import io.github.gustavo2358.air.model.Evidence;
import io.github.gustavo2358.analysis.dependencies.FileDependencyResult;
import java.util.*;

/** Closed explicit FILE projection; no reinterpretation of source or CALL result fields. */
final class FileDependencyJson {
    private FileDependencyJson() { }
    static Object value(FileDependencyResult f){
        return object("valuesProfile","file-literal@1","declarationInventory",inventory(f.declarationInventory()),
            "declarations",f.declarations().stream().sorted(Comparator.comparing(d->d.id().localId())).map(d->object("id",WireIds.id(d.id()),"owner",WireIds.id(d.owner()),"logicalFile",d.logicalFile(),"classification",d.classification(),"sourceKind",d.sourceKind(),"targetKind",d.targetKind(),"namespace",d.namespace(),"name",d.name(),"objects",d.objects().stream().sorted(Comparator.comparing((io.github.gustavo2358.air.model.Interactions.ResourceObject o)->o.object(),WireIds.ORDER).thenComparing(io.github.gustavo2358.air.model.Interactions.ResourceObject::role)).map(o->object("object",WireIds.id(o.object()),"role",o.role())).toList(),"origin",WireIds.id(d.origin()))).toList(),
            "sites",f.sites().stream().map(s->object("owner",WireIds.id(s.owner()),"entry",WireIds.id(s.entry()),"sequence",WireIds.id(s.sequence()),"operation",WireIds.id(s.operation()),"action",s.action(),"namespace",s.namespace(),"targetKind",s.targetKind(),"bindings",s.bindings().stream().map(b->object("declaration",WireIds.id(b.declaration()),"role",b.role(),"origin",WireIds.id(b.origin()))).toList(),"valuePoint",s.valuePoint()==null?null:ResultJson.point(s.valuePoint()),"candidates",s.candidates().stream().map(FileDependencyJson::candidate).toList(),"unknownRemainder",s.unknownRemainder(),"reachability",switch(s.reachability()){case REACHABLE->"REACHABLE";case UNREACHABLE_IN_MODEL->"UNREACHABLE_IN_MODEL";case UNKNOWN->"UNKNOWN";},"effects",precision(s.effects()),"control",precision(s.control()),"origin",WireIds.id(s.origin()),"targetOrigin",WireIds.id(s.targetOrigin()),"uncertaintyRefs",s.uncertaintyRefs().stream().sorted(WireIds.ORDER).map(WireIds::id).toList(),"analysisReasons",s.analysisReasons().stream().distinct().sorted().toList())).toList(),
            "edges",f.edges().stream().map(e->object("owner",WireIds.id(e.owner()),"entry",WireIds.id(e.entry()),"site",WireIds.id(e.site()),"candidate",candidate(e.candidate()),"openSite",e.openSite())).toList(),"metrics",f.metrics());
    }
    private static Object candidate(FileDependencyResult.Candidate c){return object("referenceName",c.referenceName(),"rawValue",c.rawValue(),"supports",c.supports().stream().map(s->object("kind",s.kind(),"producer",WireIds.id(s.producer()),"origin",WireIds.id(s.origin()),"premises",s.premises().stream().sorted(WireIds.ORDER).map(WireIds::id).toList())).toList());}
    private static String inventory(Evidence.InventoryStatus s){return switch(s){case COMPLETE->"COMPLETE";case PARTIAL->"PARTIAL";case UNAVAILABLE->"UNAVAILABLE";};}
    private static String precision(Evidence.PrecisionStatus s){return switch(s){case EXACT->"EXACT";case CONSERVATIVE->"CONSERVATIVE";case OPEN->"OPEN";case UNAVAILABLE->"UNAVAILABLE";case NOT_APPLICABLE->"NOT_APPLICABLE";};}
    private static Map<String,Object> object(Object... fields){var result=new TreeMap<String,Object>();for(int n=0;n<fields.length;n+=2)result.put((String)fields[n],fields[n+1]);return result;}
}
