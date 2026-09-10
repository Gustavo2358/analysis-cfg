package io.github.gustavo2358.analysis.solver;

import io.github.gustavo2358.analysis.cfg.domain.CfgTransition;
import java.lang.reflect.*;
import java.util.*;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static io.github.gustavo2358.analysis.solver.SyntheticAnalyses.*;
import static io.github.gustavo2358.analysis.structure.SolverFixtureBridge.*;

class SolverScaleTest {
    @Test void s4bStaggeredFanInHasLinearDeliveriesAndQuadraticRecompositionReads() throws Exception {
        for(int n:new int[]{1000,2000,4000,10000}) {
            int join=2*n-1; int[][] edges=new int[2*n][]; Op[][] ops=new Op[2*n][];
            for(int i=0;i<2*n;i++) {
                edges[i]=i<n-1?new int[]{2*i+1,2*i+2}:i<join?new int[]{join}:new int[0]; ops[i]=new Op[0];
            }
            var p=new Program(edges,new int[]{0},1,ops,false); var c=p.contexts.getFirst();
            long[] joinDeliveries={0};
            var definition=new Backward(p) {
                @Override public Direction direction() { return Direction.FORWARD; }
                @Override public Join<Long> joinInto(Long a,Long b,DomainWork w) { w.joinEntryVisited(); return new Join<>(Math.max(a,b),b>a); }
                @Override public Long transferBlock(AnalysisPoint point,Long a,DomainWork w) {
                    Integer number=p.numbers.get(point.node());
                    return number!=null && number>=n-1 && number<join?(long)(number-(n-1)+1):a;
                }
                @Override public Long transferEdge(AnalysisPoint point,CfgTransition edge,Long a,DomainWork w) {
                    if(edge.to().equals(node(p.session,join).source().id())) joinDeliveries[0]++;
                    return a;
                }
            };
            Function<SolverTopology,IntWorklist> staggered=g->{
                int[] ranks=new int[g.points.length];
                for(var point:g.points) {
                    Integer number=p.numbers.get(point.node());
                    ranks[point.ordinal]=point.node()==node(p.session,join)?0:point.node()==c.normalExit()?1:number==null?2:3+number;
                }
                return Schedules.priority(ranks);
            };
            long started=System.nanoTime();
            var result=DataflowSolver.solve(p.session,definition,staggered); long elapsed=System.nanoTime()-started;
            SlowFanIn slow=new SlowFanIn(n,edges);
            assertEquals((long)n,result.in(c,node(p.session,join)),"S4b exact join fact");
            for(int i=0;i<edges.length;i++) {
                assertEquals(slow.in[i],result.in(c,node(p.session,i)),"S4b oracle IN");
                assertEquals(slow.out[i],result.out(c,node(p.session,i)),"S4b oracle OUT");
            }
            var m=result.metrics();
            assertEquals(4L*n,m.edgeContributionJoins(),"S4b total deliveries including auxiliary edges");
            assertEquals(0,m.predecessorContributionReads(),"S4b no production predecessor recomposition");
            assertEquals((long)n*(n+1),slow.joinReads,"S4b staggered oracle N squared");
            assertEquals(4L*n+2,m.worklistPushes(),"S4b staggered pushes");
            Map<String,Object> extra=new LinkedHashMap<>();
            assertEquals(n,joinDeliveries[0],"S4b measured deliveries specifically at J");
            extra.put("joinPointDeliveries",joinDeliveries[0]); extra.put("auxiliaryDeliveries",m.edgeContributionJoins()-n);
            extra.put("slowJoinPredecessorReads",slow.joinReads); extra.put("slowPredecessorReads",slow.reads);
            extra.put("slowJoinIntoCalls",slow.reads); extra.put("slowWorklistPushes",slow.pushes); extra.put("slowWorklistPops",slow.pops);
            emit("S4b",n,result,elapsed,extra);
        }
    }
    /** Fixture-level recomposition, no product graph, worklist, transfer or join reuse. */
    static final class SlowFanIn {
        final long[] in,out; long reads,joinReads,pushes,pops;
        SlowFanIn(int n,int[][] graph) {
            int count=graph.length+2,entry=graph.length,exit=entry+1,join=graph.length-1;
            List<List<Integer>> preds=new ArrayList<>(),succs=new ArrayList<>();
            for(int i=0;i<count;i++) { preds.add(new ArrayList<>()); succs.add(new ArrayList<>()); }
            for(int i=0;i<graph.length;i++) for(int target:graph[i]) { preds.get(target).add(i); succs.get(i).add(target); }
            preds.get(0).add(entry); succs.get(entry).add(0); preds.get(exit).add(join); succs.get(join).add(exit);
            PriorityQueue<Integer> agenda=new PriorityQueue<>(Comparator.comparingInt(i->i==join?-3:i==exit?-2:i==entry?-1:i));
            boolean[] queued=new boolean[count]; in=new long[count]; out=new long[count];
            for(int i=0;i<count;i++) { agenda.add(i); queued[i]=true; pushes++; }
            while(!agenda.isEmpty()) {
                int i=agenda.remove(); queued[i]=false; pops++; long anchor=0;
                for(int source:preds.get(i)) { reads++; if(i==join) joinReads++; anchor=Math.max(anchor,out[source]); }
                in[i]=anchor; long value=i>=n-1 && i<join?i-n+2:anchor;
                if(value!=out[i]) {
                    out[i]=value;
                    for(int target:succs.get(i)) if(!queued[target]) { agenda.add(target); queued[target]=true; pushes++; }
                }
            }
        }
    }
    @Test void s4AndS16LinearAndCycleSizesRemainStableInBothDirections() throws Exception {
        for(int n:new int[]{1000,2000,4000,10000}) for(boolean cycle:new boolean[]{false,true}) {
            int[][] edges=new int[n][]; Op[][] ops=new Op[n][];
            for(int i=0;i<n;i++) { edges[i]=i+1<n?new int[]{i+1}:cycle?new int[]{0}:new int[0]; ops[i]=i+1==n?new Op[]{Op.USE_Y}:new Op[0]; }
            var p=new Program(edges,new int[]{0},1,ops,false); var c=p.contexts.getFirst();
            for(Direction direction:Direction.values()) {
                AnalysisDefinition<Long> definition=direction==Direction.FORWARD?SolverPropertiesTest.rank(p,3):new Backward(p);
                long start=System.nanoTime(); var result=DataflowSolver.solve(p.session,definition); long elapsed=System.nanoTime()-start;
                assertEquals(DataflowResult.Status.STABLE,result.status());
                for(int i=0;i<n;i++) {
                    if(direction==Direction.FORWARD) assertEquals(cycle?3L:1L,result.out(c,node(p.session,i)),"scale exact forward fact");
                    else assertEquals(2L,result.in(c,node(p.session,i)),"scale exact backward fact");
                }
                assertEquals(0,result.metrics().predecessorContributionReads()); assertEquals(0,result.metrics().successorContributionReads());
                assertTrue(result.metrics().nodesPopped()<=4*result.metrics().analysisPoints(),"finite-height work bound");
                emit(cycle?"S4-S16-cycle":"S4-S16-linear",n,result,elapsed,Map.of("direction",direction.toString()));
            }
        }
    }
    @Test void s8ResultRetainsOnlyEffectivePointsAndTwoRootArrays() throws Exception {
        for(int entries:new int[]{1,2,20}) {
            int[] roots=new int[entries]; int selected=Math.min(entries,2);
            var p=new Program(new int[][]{{1},{},{}},roots,selected,new Op[][]{new Op[0],new Op[0],new Op[0]},false);
            long start=System.nanoTime(); var result=DataflowSolver.solve(p.session,new Forward(p)); long elapsed=System.nanoTime()-start;
            assertEquals(4L*selected,result.metrics().analysisPoints());
            emit("S8",entries,result,elapsed,Map.of("selected",selected));
        }
    }
    static Map<String,Long> retained(DataflowResult<?> result) throws Exception {
        Set<Object> visited=Collections.newSetFromMap(new IdentityHashMap<>()); ArrayDeque<Object> todo=new ArrayDeque<>(); todo.add(result);
        long arrays=0,slots=0,points=0;
        while(!todo.isEmpty()) {
            Object value=todo.remove(); if(!visited.add(value)) continue;
            if(value instanceof Object[] a) { arrays++; slots+=a.length; continue; } // roots are opaque, domain-owned
            if(value instanceof AnalysisPoint) { points++; continue; } // W1 references are borrowed baseline
            if(value instanceof Map<?,?> map) { todo.addAll(map.values()); continue; }
            String name=value.getClass().getName();
            assertFalse(value instanceof SolverTopology || value instanceof IntWorklist || name.contains("DataflowSolver$Run"),"scratch topology/worklist/history released");
            if(!name.startsWith("io.github.gustavo2358.analysis.solver.")) continue;
            for(Field field:value.getClass().getDeclaredFields()) if(!Modifier.isStatic(field.getModifiers()) && !field.getType().isPrimitive()) {
                field.setAccessible(true); Object child=field.get(value); if(child!=null) todo.add(child);
            }
        }
        assertEquals(2,arrays,"two root arrays only"); assertEquals(2*result.metrics().analysisPoints(),slots,"two roots per effective point");
        assertEquals(result.metrics().analysisPoints(),points,"dense contextual handles without Cartesian matrix");
        return Map.of("retainedRootArrays",arrays,"retainedRootSlots",slots,"retainedPointHandles",points);
    }
    static void emit(String probe,int n,DataflowResult<?> result,long elapsed,Map<String,?> extra) throws Exception {
        Map<String,Object> row=new LinkedHashMap<>(); row.put("probe",probe); row.put("N",n); row.put("status",result.status().toString());
        for(RecordComponent c:SolverMetrics.class.getRecordComponents()) row.put(c.getName(),c.getAccessor().invoke(result.metrics()));
        row.put("elapsedNanosObservation",elapsed); row.putAll(retained(result)); row.putAll(extra);
        StringJoiner json=new StringJoiner(",","{","}");
        for(var e:row.entrySet()) json.add("\""+e.getKey()+"\":"+(e.getValue() instanceof String?"\""+e.getValue()+"\"":e.getValue()));
        System.out.println("W2_METRICS "+json);
    }
}
