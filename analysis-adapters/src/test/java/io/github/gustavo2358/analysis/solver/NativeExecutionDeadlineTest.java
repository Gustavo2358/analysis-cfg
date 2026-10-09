package io.github.gustavo2358.analysis.solver;

import io.github.gustavo2358.analysis.adapters.DataflowAirReader;
import io.github.gustavo2358.analysis.dependencies.SnapshotProgram;
import io.github.gustavo2358.analysis.cfg.application.*;
import io.github.gustavo2358.analysis.cfg.extension.SemanticInterpreterRegistry;
import io.github.gustavo2358.analysis.structure.AnalysisSession;
import io.github.gustavo2358.analysis.query.*;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** A real admitted paged source and a lawful finite OR domain, not a watchdog. */
final class NativeExecutionDeadlineTest {
    @Test void expiryInsideNativeObservationTransferCannotPublishACompleteBatch() throws Exception {
        long[] now={0};var resources=new AnalysisResources(new AnalysisResources.Limits(64L*1024*1024,16L*1024*1024,0,256L*1024*1024,8,Long.MAX_VALUE,Long.MAX_VALUE),5,()->now[0]);
        try(var input=new DataflowAirReader().readSnapshot(Path.of("src/test/resources/cp6/dynamic-x8.air.json"),resources);
            var program=new SnapshotProgram(input.checked(),input.newIdentityStorage(),input.newOrderStorage(),resources)) {
            var options=BuildOptions.defaults();var cfg=new CfgBuildCoordinator(SemanticInterpreterRegistry.empty()).buildChecked(program,input.checked(),options);
            var unit=program.units().getFirst();var entry=unit.entries().getFirst();
            var session=AnalysisSession.open(cfg,program,options.projectionPolicy(),List.of(entry.id())).session().orElseThrow();
            var context=session.contexts().iterator().next();
            var definition=new AnalysisDefinition<Integer>() {
                public Direction direction(){return Direction.FORWARD;}
                public Integer bottom(){return 0;}
                public long stateFingerprint(Integer state){return state;}
                public Iterable<Boundary<Integer>> boundaries(AnalysisSession ignored){return List.of(new Boundary<>(context,context.entryNode(),1));}
                public Join<Integer> joinInto(Integer a,Integer b,DomainWork work){int joined=a|b;return new Join<>(joined,joined!=a);}
                public boolean equivalent(Integer a,Integer b,DomainWork work){return a.equals(b);}
                public Integer transferBlock(AnalysisPoint point,Integer state,DomainWork work){return state;}
                public Integer transferEdge(AnalysisPoint point,io.github.gustavo2358.analysis.cfg.domain.CfgTransition edge,Integer state,DomainWork work){return state;}
            };
            var stable=DataflowSolver.solve(session,definition);
            var operation=unit.sequences().getFirst().instructions().getFirst().header().id();
            var query=new PointQuery<>(ProgramPoint.after(entry.id(),operation),"subject");
            var projection=new BatchReplayer.Projection<Integer,String,Integer>() {
                public boolean supports(PointQuery<String> ignored){return true;}
                public Integer project(PointQuery<String> ignored,Integer state){return state;}
            };
            var failure=assertThrows(AnalysisResources.Exhausted.class,()->BatchReplayer.materialize(session,stable,Direction.FORWARD,0,List.of(query),String::compareTo,
                (state,instruction)->{now[0]=5;return state;},projection));
            assertEquals(AnalysisResources.Resource.TIME,failure.resource());assertEquals(AnalysisResources.Phase.REPLAY,failure.phase());
            assertEquals(DataflowResult.Status.STABLE,stable.status(),"the completed solve is not reclassified as an incomplete semantic answer");
        }
        for(var pool:AnalysisResources.Pool.values())assertEquals(0,resources.used(pool));
    }
    @Test void expiryInsideTransferNeverPublishesEitherDirectionAndReleasesInput() throws Exception {
        for(var direction:Direction.values())for(boolean explicitOwner:new boolean[]{false,true}) {
            long[] now={0};
            var resources=new AnalysisResources(new AnalysisResources.Limits(64L*1024*1024,16L*1024*1024,0,256L*1024*1024,8,Long.MAX_VALUE,Long.MAX_VALUE),5,()->now[0]);
            try(var input=new DataflowAirReader().readSnapshot(Path.of("src/test/resources/cp6/dynamic-x8.air.json"),resources);
                var program=new SnapshotProgram(input.checked(),input.newIdentityStorage(),input.newOrderStorage(),explicitOwner?resources:null)) {
                var options=BuildOptions.defaults();
                var cfg=new CfgBuildCoordinator(SemanticInterpreterRegistry.empty()).buildChecked(program,input.checked(),options);
                var entries=program.units().getFirst().entries().stream().map(e->e.id()).toList();
                var session=AnalysisSession.open(cfg,program,options.projectionPolicy(),entries).session().orElseThrow();
                var context=session.contexts().iterator().next();
                var definition=new AnalysisDefinition<Integer>() {
                    public Direction direction(){return direction;}
                    public Integer bottom(){return 0;}
                    public long stateFingerprint(Integer state){return state;}
                    public Iterable<Boundary<Integer>> boundaries(AnalysisSession ignored){return List.of(new Boundary<>(context,direction==Direction.FORWARD?context.entryNode():context.normalExit(),1));}
                    public Join<Integer> joinInto(Integer a,Integer b,DomainWork work){int joined=a|b;return new Join<>(joined,joined!=a);}
                    public boolean equivalent(Integer a,Integer b,DomainWork work){return a.equals(b);}
                    public Integer transferBlock(AnalysisPoint point,Integer state,DomainWork work){now[0]=5;return state;}
                    public Integer transferEdge(AnalysisPoint point,io.github.gustavo2358.analysis.cfg.domain.CfgTransition edge,Integer state,DomainWork work){return state;}
                };
                var failure=assertThrows(AnalysisResources.Exhausted.class,()->DataflowSolver.solve(session,definition));
                assertEquals(AnalysisResources.Resource.TIME,failure.resource());
                assertEquals(explicitOwner?AnalysisResources.Phase.DOMAIN:AnalysisResources.Phase.DECODE,failure.phase());
                assertEquals(5,failure.limit());assertEquals(5,failure.used());
            }
            for(var pool:AnalysisResources.Pool.values())assertEquals(0,resources.used(pool),direction+" "+pool);
        }
    }
}
