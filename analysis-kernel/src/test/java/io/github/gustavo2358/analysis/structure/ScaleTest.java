package io.github.gustavo2358.analysis.structure;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.analysis.cfg.domain.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static io.github.gustavo2358.analysis.structure.StructuralFixtures.*;

/** Real BuildCfg and product index; independent arithmetic oracles, never elapsed-time admission. */
class ScaleTest {
    static AnalysisSession probe(String probe,int sequences,int instructions,int objects,int entries,int selected) throws Exception {
        var p=linear(sequences,instructions,objects,entries); var b=build(p); var baseline=RetentionWalk.walk(p,b.graph().orElseThrow());
        long start=System.nanoTime();
        var a=AnalysisSession.open(b,p,ProjectionPolicy.KNOWN_SUBSET,p.units().getFirst().entries().subList(0,selected));
        long elapsed=System.nanoTime()-start;
        assertEquals(AnalysisSession.Status.ACCEPTED,a.status(),"size invariant admission "+probe+": "+a.reason());
        var session=a.session().orElseThrow(); var m=session.index().metrics();
        long v=sequences+2L*entries,e=entries*(1L+sequences),o=sequences+(long)instructions;
        long c=objects>0?1:0,refs=c+objects+entries+(objects>0?instructions:0);
        long buckets=(sequences>1?2:1)+(instructions>0?1:0);
        long visits=3+3L*objects+2*c+2L*sequences+(objects>0?4L:1L)*instructions+2L*entries+v+2*e+buckets;
        assertEquals(v,m.nodesIndexed(),"nodes indexed once"); assertEquals(e,m.edgesIndexed(),"edges indexed once");
        assertEquals(o,m.operationsIndexed(),"operations indexed once"); assertEquals(objects,m.objectsIndexed());
        assertEquals(c,m.locationsIndexed()); assertEquals(refs,m.referencesResolved(),"reference work follows occurrences");
        assertEquals(visits,m.structuralVisits(),"linear construction ledger");
        long queryReads=0;
        for(var context:session.contexts()) for(var node:session.index().nodes) {
            var forward=context.successors(node); while(forward.advance()) assertEquals(context.entry().id(),forward.transition().activationEntry());
            var backward=context.predecessors(node); while(backward.advance()) assertEquals(context.entry().id(),backward.transition().activationEntry());
            queryReads+=forward.edgesVisited()+backward.edgesVisited();
        }
        assertEquals(2L*selected*(sequences+1L),queryReads,"indexed adjacency reads only contextual edges");
        assertEquals(selected,session.contexts().size(),"only selected contexts materialized");
        var retained=RetentionWalk.additional(session,baseline);
        assertEquals(v,retained.getOrDefault(ProgramIndex.Node.class.getName(),0L));
        assertEquals(o,retained.getOrDefault(ProgramIndex.Site.class.getName(),0L));
        assertEquals(selected,retained.getOrDefault(ContextView.class.getName(),0L));
        assertTrue(retained.keySet().stream().noneMatch(k->k.startsWith("io.github.gustavo2358.air.")||k.startsWith("io.github.gustavo2358.analysis.cfg.")),"zero additional AIR/CFG payload instances");
        System.out.println("W1_METRICS {\"probe\":\""+probe+"\",\"sequences\":"+sequences+",\"instructions\":"+instructions+",\"objects\":"+objects+",\"entries\":"+entries+",\"selected\":"+selected+",\"nodesIndexed\":"+v+",\"edgesIndexed\":"+e+",\"operationsIndexed\":"+m.operationsIndexed()+",\"referencesResolved\":"+m.referencesResolved()+",\"objectsIndexed\":"+m.objectsIndexed()+",\"locationsIndexed\":"+m.locationsIndexed()+",\"structuralVisits\":"+m.structuralVisits()+",\"queryEdgeReads\":"+queryReads+",\"elapsedNanosObservation\":"+elapsed+",\"retainedNodeHandles\":"+retained.get(ProgramIndex.Node.class.getName())+",\"retainedSiteHandles\":"+retained.get(ProgramIndex.Site.class.getName())+",\"retainedArraySlots\":"+retained.get("arraySlots")+",\"additionalAirCfgPayloads\":0}");
        System.out.println("W1_RETENTION "+probe+" "+retained);
        return session;
    }
    @Test void s1LongSequence() throws Exception {
        for(int n:new int[]{10000,20000,100000,200000}) probe("S1",1,n,1,1,1);
    }
    @Test void s2WideDeclarations() throws Exception {
        for(int n:new int[]{1000,2000,10000}) probe("S2",1,8,n,1,1);
    }
    @Test void s4AdjacencyIsLinear() throws Exception {
        for(int n:new int[]{1000,2000,10000}) probe("S4",n,0,0,1,1);
    }
    @Test void s8DemandedContextsOnly() throws Exception {
        for(int k:new int[]{1,2,20}) probe("S8",100,8,8,k,Math.min(k,2));
    }
    @Test void s16EverySizeOfSameProfileIsAdmitted() throws Exception {
        for(int n:new int[]{1000,2000,4000}) {
            probe("S16-nodes-edges",n,0,0,1,1);
            probe("S16-operations",1,n,1,1,1);
            probe("S16-objects",1,8,n,1,1);
            probe("S16-references",1,n,n,1,1);
        }
    }
    @Test void primitiveDirectoryRetainsSparseKeysAcrossGrowth() {
        var map=new LongIntDirectory(); Map<Long,Integer> expected=new HashMap<>();
        for(int i=0;i<10000;i++) { long key=LongIntDirectory.key(i%23,i*137); expected.put(key,i); assertEquals(-1,map.put(key,i)); }
        for(var row:expected.entrySet()) assertEquals(row.getValue().intValue(),map.get(row.getKey()));
        assertEquals(-1,map.get(Long.MAX_VALUE));
        assertEquals(0,map.put(0,99)); assertEquals(99,map.get(0));
    }
}
