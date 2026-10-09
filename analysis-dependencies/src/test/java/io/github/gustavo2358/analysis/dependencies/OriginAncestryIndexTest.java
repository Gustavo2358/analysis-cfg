package io.github.gustavo2358.analysis.dependencies;

import io.github.gustavo2358.air.model.Origins;
import io.github.gustavo2358.air.model.Ids.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OriginAncestryIndexTest {
    static final PublicationId P=new PublicationId("origin-index-test");
    static OriginId id(int n){return new OriginId(P,"o"+n);}
    static final class CountedOrigins extends HashMap<OriginId,Origins.Origin> {
        private static final long serialVersionUID=1L;
        long lookups;
        @Override public Origins.Origin get(Object key){lookups++;return super.get(key);}
    }
    @Test void sharedDeepAncestryIsPreparedOnceForEveryCorrelation() {
        int depth=1024,queries=1024;var origins=new CountedOrigins();
        origins.put(id(0),new Origins.Unavailable(id(0),"independent root"));
        for(int i=1;i<=depth;i++)origins.put(id(i),new Origins.Derived(id(i),List.of(id(i-1)),"typed-input"));
        for(int i=1;i<=queries;i++)origins.put(id(depth+i),new Origins.Derived(id(depth+i),List.of(id(depth)),"shared-tail"));
        try(var index=new OriginAncestryIndex(origins,Set.of(id(0)))) {
            for(int i=1;i<=queries;i++)assertTrue(index.derivedFrom(id(depth+i),id(0)));
            assertTrue(origins.lookups<=4L*origins.size(),"origin reads="+origins.lookups);
        }
    }
    @Test void requestedOriginsMatchIndependentTypedGraphWalksIncludingCyclesAndMissingInputs() {
        var random=new Random(432719);
        for(int round=0;round<50;round++) {
            int n=1+random.nextInt(35);var origins=new HashMap<OriginId,Origins.Origin>();
            for(int i=0;i<n;i++) {
                var inputs=new ArrayList<OriginId>();for(int j=0;j<n+2;j++)if(random.nextInt(9)==0)inputs.add(id(j));
                origins.put(id(i),inputs.isEmpty()?new Origins.Unavailable(id(i),"leaf"):new Origins.Derived(id(i),inputs,"opaque-rule-name"));
            }
            var requested=new HashSet<OriginId>();for(int i=0;i<n+2;i++)if(i%2==0)requested.add(id(i));
            requested.add(new OriginId(new PublicationId("foreign"),"o0"));
            try(var index=new OriginAncestryIndex(origins,requested)) {
                for(int actual=0;actual<n+2;actual++)for(var expected:requested) {
                    boolean found=false;var seen=new HashSet<OriginId>();var queue=new ArrayDeque<OriginId>();queue.add(id(actual));
                    if(origins.containsKey(expected))while(!queue.isEmpty()) {
                        var current=queue.removeFirst();if(current.equals(expected)){found=true;break;}
                        if(!seen.add(current))continue;if(origins.get(current) instanceof Origins.Derived d)queue.addAll(d.inputs());
                    }
                    assertEquals(found,index.derivedFrom(id(actual),expected),"round="+round+" actual="+actual+" expected="+expected);
                }
            }
        }
    }
}
