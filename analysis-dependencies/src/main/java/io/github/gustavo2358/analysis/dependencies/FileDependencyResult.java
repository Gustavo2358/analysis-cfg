package io.github.gustavo2358.analysis.dependencies;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.analysis.query.ProgramPoint;
import java.util.*;

/** Detached FILE facts; CALL sites/edges retain their existing contract. */
public record FileDependencyResult(Evidence.InventoryStatus declarationInventory,List<Declaration> declarations,
        List<Site> sites,List<Edge> edges,Map<String,Long> metrics) {
    public FileDependencyResult {Objects.requireNonNull(declarationInventory);declarations=List.copyOf(declarations);sites=List.copyOf(sites);edges=List.copyOf(edges);metrics=Map.copyOf(metrics);}
    public static FileDependencyResult unavailable(){return new FileDependencyResult(Evidence.InventoryStatus.UNAVAILABLE,List.of(),List.of(),List.of(),Map.of());}
    public record Declaration(ResourceId id,UnitId owner,String logicalFile,String classification,String sourceKind,
            String targetKind,String namespace,String name,List<Interactions.ResourceObject> objects,OriginId origin) {
        public Declaration {objects=List.copyOf(objects);}
    }
    public record Binding(ResourceId declaration,String role,OriginId origin) { }
    public record Support(String kind,Id producer,OriginId origin,List<PremiseId> premises) {public Support {premises=List.copyOf(premises);}}
    public record Candidate(String referenceName,String rawValue,List<Support> supports) {public Candidate {supports=List.copyOf(supports);}}
    public enum Reachability { REACHABLE, UNREACHABLE_IN_MODEL, UNKNOWN }
    public record Site(UnitId owner,EntryId entry,LabelId sequence,OperationId operation,String action,String namespace,
            String targetKind,List<Binding> bindings,ProgramPoint valuePoint,List<Candidate> candidates,boolean unknownRemainder,
            Reachability reachability,Evidence.PrecisionStatus effects,Evidence.PrecisionStatus control,
            OriginId origin,OriginId targetOrigin,List<UncertaintyId> uncertaintyRefs,List<String> analysisReasons) {
        public Site {bindings=List.copyOf(bindings);candidates=List.copyOf(candidates);uncertaintyRefs=List.copyOf(uncertaintyRefs);analysisReasons=List.copyOf(analysisReasons);}
    }
    public record Edge(UnitId owner,EntryId entry,OperationId site,Candidate candidate,boolean openSite) { }
}
