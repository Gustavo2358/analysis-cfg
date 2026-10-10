package io.github.gustavo2358.analysis.solver;

import io.github.gustavo2358.analysis.adapters.DataflowAirReader;
import io.github.gustavo2358.analysis.dependencies.SnapshotProgram;
import io.github.gustavo2358.analysis.cfg.application.*;
import io.github.gustavo2358.analysis.cfg.extension.SemanticInterpreterRegistry;
import io.github.gustavo2358.analysis.cfg.domain.CfgTransition;
import io.github.gustavo2358.analysis.structure.*;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Original synthetic shared-key family, N=3/M=3, read by the actual native admission. */
final class NativeActivationLifetimeTest {
    private static final Path INPUT=Path.of("src/test/resources/cp6/native-activation-shared-key-3.air.json");
    private static AnalysisResources resources(){return new AnalysisResources(new AnalysisResources.Limits(64L*1024*1024,16L*1024*1024,0,256L*1024*1024,8,Long.MAX_VALUE,Long.MAX_VALUE));}
    private static AnalysisSession session(SnapshotProgram program,io.github.gustavo2358.air.validation.SnapshotValidator.CheckedSnapshot checked) {
        var options=BuildOptions.defaults();var cfg=new CfgBuildCoordinator(SemanticInterpreterRegistry.empty()).buildChecked(program,checked,options);
        var entry=program.units().getFirst().entries().getFirst();
        var session=AnalysisSession.open(cfg,program,options.projectionPolicy(),List.of(entry.id())).session().orElseThrow();
        assertTrue(session.hasLocalControl(),"must not accidentally exercise only ordinary topology");return session;
    }
    private static AnalysisDefinition<Integer> definition(AnalysisSession session,Direction direction) {
        var context=session.contexts().iterator().next();
        return new AnalysisDefinition<>() {
            public Direction direction(){return direction;} public Integer bottom(){return 0;}
            public long stateFingerprint(Integer value){return value;}
            public Iterable<Boundary<Integer>> boundaries(AnalysisSession ignored){return List.of(new Boundary<>(context,direction==Direction.FORWARD?context.entryNode():context.normalExit(),1));}
            public Join<Integer> joinInto(Integer a,Integer b,DomainWork work){int joined=a|b;return new Join<>(joined,joined!=a);}
            public boolean equivalent(Integer a,Integer b,DomainWork work){return a.equals(b);}
            public Integer transferBlock(AnalysisPoint point,Integer state,DomainWork work){return state;}
            public Integer transferEdge(AnalysisPoint point,CfgTransition edge,Integer state,DomainWork work){return state;}
        };
    }
    private static void released(AnalysisResources resources){for(var pool:AnalysisResources.Pool.values())assertEquals(0,resources.used(pool),pool.toString());}
    @Test void nativeActivationReferenceArraysStayFundedUntilResultClose() throws Exception {
        for(var direction:Direction.values()) {
            var resources=resources();
            try(var input=new DataflowAirReader().readSnapshot(INPUT,resources);
                var program=new SnapshotProgram(input.checked(),input.newIdentityStorage(),input.newOrderStorage(),resources)) {
                var session=session(program,input.checked());var context=session.contexts().iterator().next();
                long before=resources.heapUsed();var result=DataflowSolver.solve(session,definition(session,direction));
                try {
                    assertEquals(10,result.metrics().analysisPoints(),"original family retains all feasible roots");
                    assertEquals(List.of(1),result.states(context,context.entryNode(),true));
                    assertTrue(resources.heapUsed()>=before+64+16*result.metrics().analysisPoints(),"both stable reference arrays belong to the admitted common ledger");
                }finally{result.close();}
                assertEquals(before,resources.heapUsed());result.close();assertEquals(before,resources.heapUsed());
                assertThrows(IllegalStateException.class,()->result.states(context,context.entryNode(),true));
            }
            released(resources);
        }
    }
    @Test void nativeActivationOwnerExpiryClearsStateSlotsAndPreservesBorrowedInput() throws Exception {
        for(var direction:Direction.values()) {
            var resources=resources();
            try(var input=new DataflowAirReader().readSnapshot(INPUT,resources)) {
                long before=resources.heapUsed();var snapshot=input.checked().snapshot();long root=snapshot.root();var shape=snapshot.shape(root);
                var program=new SnapshotProgram(input.checked(),input.newIdentityStorage(),input.newOrderStorage(),resources);
                try {
                    var session=session(program,input.checked());var context=session.contexts().iterator().next();var node=context.entryNode();
                    var result=DataflowSolver.solve(session,definition(session,direction));
                    assertEquals(List.of(1),result.states(context,node,true));
                    var expiredSlots=new java.util.ArrayList<Object[]>();
                    for(String name:List.of("in","out")){var field=DataflowResult.class.getDeclaredField(name);field.setAccessible(true);expiredSlots.add((Object[])field.get(result));}
                    program.close();
                    assertThrows(IllegalStateException.class,()->result.states(context,node,true));
                    for(var slots:expiredSlots)for(Object state:slots)assertNull(state,"expired state slot");
                    for(String name:List.of("in","out","lookup")){var field=DataflowResult.class.getDeclaredField(name);field.setAccessible(true);assertNull(field.get(result),"expired result still owns backing capacity "+name);}
                    assertEquals(before,resources.heapUsed());assertEquals(shape,snapshot.shape(root));
                    result.close();assertEquals(DataflowResult.Status.STABLE,result.status());assertEquals(10,result.metrics().analysisPoints());
                }finally{program.close();}
            }
            released(resources);
        }
    }
}
