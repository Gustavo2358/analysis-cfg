package io.github.gustavo2358.analysis.dependencies;

import io.github.gustavo2358.air.model.Origins;
import io.github.gustavo2358.air.model.Ids.OriginId;
import io.github.gustavo2358.analysis.solver.*;
import java.util.*;

/** Preparation-scoped typed origin index. Only requested origin IDs label the graph. */
final class OriginAncestryIndex implements AutoCloseable {
    private final AnalysisResources resources;
    private final AnalysisResources.Reservation metadata;
    private Map<OriginId,Integer> vertices,labels;
    private ResidentPageStore store;
    private PersistentGraphClosure closure;
    private boolean closed;
    OriginAncestryIndex(Map<OriginId,Origins.Origin> origins,Set<OriginId> requested) {
        Objects.requireNonNull(origins);Objects.requireNonNull(requested);
        resources=new AnalysisResources(new AnalysisResources.Limits(Long.MAX_VALUE,Long.MAX_VALUE,Long.MAX_VALUE,Long.MAX_VALUE,Long.MAX_VALUE,Long.MAX_VALUE,Long.MAX_VALUE));
        metadata=resources.reserve(AnalysisResources.Pool.RESIDENT,512L+128L*origins.size()+64L*requested.size(),AnalysisResources.Phase.VALIDATION);
        try {
            vertices=new HashMap<>();labels=new HashMap<>();
            for(var id:origins.keySet())vertices.put(id,vertices.size());
            for(var id:requested)if(vertices.containsKey(id))labels.put(id,labels.size());
            int[] keys=new int[vertices.size()];Arrays.fill(keys,-1);int[][] parents=new int[vertices.size()][];
            for(var entry:vertices.entrySet()) {
                int ordinal=entry.getValue();keys[ordinal]=labels.getOrDefault(entry.getKey(),-1);
                if(origins.get(entry.getKey()) instanceof Origins.Derived derived) {
                    int count=0;for(var parent:derived.inputs())if(vertices.containsKey(parent))count++;
                    parents[ordinal]=new int[count];int slot=0;
                    for(var parent:derived.inputs()){var found=vertices.get(parent);if(found!=null)parents[ordinal][slot++]=found;}
                }else parents[ordinal]=new int[0];
            }
            store=new ResidentPageStore(4096,resources,AnalysisResources.Phase.VALIDATION);
            closure=new PersistentGraphClosure(store,resources,AnalysisResources.Phase.VALIDATION,keys,parents);
        }catch(RuntimeException|Error failure){suppress(closure,failure);suppress(store,failure);metadata.close();throw failure;}
    }
    boolean derivedFrom(OriginId actual,OriginId expected) {
        if(closed)throw new IllegalStateException("origin ancestry index closed");
        var vertex=vertices.get(actual);var key=labels.get(expected);
        return vertex!=null&&key!=null&&closure.contains(vertex,key);
    }
    private static void suppress(AutoCloseable value,Throwable failure) {
        if(value!=null)try{value.close();}catch(Exception|Error closing){failure.addSuppressed(closing);}
    }
    @Override public void close() {
        if(closed)return;closed=true;
        try{closure.close();}finally{try{store.close();}finally{vertices=null;labels=null;metadata.close();}}
    }
}
