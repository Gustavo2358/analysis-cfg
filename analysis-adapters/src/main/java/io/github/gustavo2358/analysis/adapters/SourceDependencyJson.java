package io.github.gustavo2358.analysis.adapters;

import java.util.*;
import io.github.gustavo2358.analysis.dependencies.SourceDependencyResult;
import io.github.gustavo2358.air.model.Ids.ArtifactId;

/** Explicit source-dependencies@1 projection; original origins remain in the parent document. */
final class SourceDependencyJson {
    private SourceDependencyJson() {}
    static Object value(SourceDependencyResult result) {
        return object("profile","source-dependencies@1","available",result.available(),"remainder",result.partial(),
            "gapCodes",result.gapCodes(),"occurrences",result.occurrences(),"dependencies",result.dependencies().stream().map(d->object(
                "program",WireIds.id(d.program()),"kind",d.kind().name(),"name",d.name(),"qualification",d.qualification(),"remainder",d.remainder(),
                "supports",d.supports().stream().map(s->object("occurrence",WireIds.id(s.occurrence()),"origin",WireIds.id(s.origin()),
                    "sourceOwner",artifact(s.sourceOwner()),"relationship",s.transitive()?"TRANSITIVE":"DIRECT","resolution",s.resolution().name(),
                    "resolvedArtifact",s.resolvedArtifact(),"classificationAuthority",s.authority(),"operation",s.operation().name(),"access",s.access().name())).toList())).toList());
    }
    private static Object artifact(ArtifactId id){return object("domain","artifact","localId",id.localId(),"publication",id.publication().localId());}
    private static Map<String,Object> object(Object... fields){var result=new TreeMap<String,Object>();for(int i=0;i<fields.length;i+=2)result.put((String)fields[i],fields[i+1]);return result;}
}
