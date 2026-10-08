package io.github.gustavo2358.analysis.structure;

import io.github.gustavo2358.analysis.cfg.domain.CfgNode;
import io.github.gustavo2358.analysis.cfg.domain.CfgTransition;
import io.github.gustavo2358.analysis.solver.*;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static io.github.gustavo2358.analysis.structure.LocalControlTest.*;
import static org.junit.jupiter.api.Assertions.*;

class SolverFailureScopeTest {
    /** Inject an exhausted backing budget through the actual execution owner.
     * This is a failure-path fixture, not a alternate production solver or limit. */
    @Test void domainFailureSurvivesTeardownOfTheExhaustedActualSolver() throws Exception {
        failureSurvives(false);
    }
    @Test void conditionFailureSurvivesTheActualSolverPublicationScopes() throws Exception {
        failureSurvives(true);
    }
    @Test void conditionFailureInEdgeContributionsSurvivesNestedPublicationScopes() throws Exception {
        failureSurvives(true,false);
    }
    private void failureSurvives(boolean abortConditions) throws Exception {
        failureSurvives(abortConditions,true);
    }
    private void failureSurvives(boolean abortConditions,boolean fromBlock) throws Exception {
        for(var direction:Direction.values()){
            var owner=session(List.of(call("main","body","done"),resume("body"),ret("done")),"main");
            var context=owner.contexts().iterator().next();Object[] solver={null};
            AnalysisResources.Exhausted[] primary={null};AnalysisResources[] budget={null};
            var definition=new AnalysisDefinition<Integer>(){
                public Direction direction(){return direction;}
                public Integer bottom(){return 0;}
                public long stateFingerprint(Integer value){return value;}
                public Iterable<Boundary<Integer>> boundaries(AnalysisSession ignored){return List.of(new Boundary<>(context,direction==Direction.FORWARD?context.entryNode():context.normalExit(),1));}
                public Join<Integer> joinInto(Integer a,Integer b,DomainWork work){return new Join<>(a|b,(a|b)!=a);}
                public boolean equivalent(Integer a,Integer b,DomainWork work){return a.equals(b);}
                public Integer transferEdge(AnalysisPoint point,CfgTransition edge,Integer value,DomainWork work){
                    if(!fromBlock&&primary[0]==null&&point.node().source() instanceof CfgNode.SequenceNode node&&node.source().label().equals(label("main")))interrupt();
                    return value;
                }
                public Integer transferBlock(AnalysisPoint point,Integer value,DomainWork work){
                    if(fromBlock&&primary[0]==null&&point.node().source() instanceof CfgNode.SequenceNode node&&node.source().label().equals(label("body")))interrupt();
                    return value;
                }
                private void interrupt(){
                        try{
                            Object conditions=((Set<?>)field(solver[0],"ownedConditions")).iterator().next();
                            budget[0]=(AnalysisResources)field(conditions,"resources");
                            budget[0].work(budget[0].limits().workUnits()-budget[0].workUsed(),AnalysisResources.Phase.DOMAIN);
                            if(abortConditions){
                                var variable=conditions.getClass().getDeclaredMethod("variable",int.class);variable.setAccessible(true);
                                var interrupted=assertThrows(InvocationTargetException.class,()->variable.invoke(conditions,1000));
                                assertInstanceOf(AnalysisResources.Exhausted.class,interrupted.getCause());
                                primary[0]=(AnalysisResources.Exhausted)interrupted.getCause();
                            }
                        }catch(ReflectiveOperationException failure){throw new AssertionError(failure);}
                        if(!abortConditions)primary[0]=assertThrows(AnalysisResources.Exhausted.class,()->budget[0].work(1,AnalysisResources.Phase.DOMAIN));
                        throw primary[0];
                }
            };
            var type=Class.forName("io.github.gustavo2358.analysis.solver."+(direction==Direction.FORWARD?"ActivationSolver":"BackwardActivationSolver"));
            var constructor=type.getDeclaredConstructor(AnalysisSession.class,AnalysisDefinition.class);constructor.setAccessible(true);
            solver[0]=constructor.newInstance(owner,definition);var solve=type.getDeclaredMethod("solve");solve.setAccessible(true);
            var failed=assertThrows(InvocationTargetException.class,()->solve.invoke(solver[0]));
            assertNotNull(primary[0],"the injected domain failure must actually execute");
            assertSame(primary[0],failed.getCause(),"cleanup must preserve the exact primary failure for "+direction);
            assertEquals(abortConditions?AnalysisResources.Phase.CONTROL:AnalysisResources.Phase.DOMAIN,primary[0].phase());
            assertEquals(0,budget[0].heapUsed());
        }
    }
    private static Object field(Object owner,String name) throws ReflectiveOperationException {
        var field=owner.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(owner);
    }
}
