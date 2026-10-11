package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.analysis.cfg.application.CfgBuildResult;
import io.github.gustavo2358.analysis.dependencies.*;
import io.github.gustavo2358.analysis.solver.*;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Caller-staged typed result pressure, NOT provider production qualification.
 * Native AIR/execution/backend context is real; facts are independently staged. */
@SuppressWarnings("try")
final class NativeGeneralProgramPressureTest {
    @FunctionalInterface private interface Work {void run(SnapshotProgram program,CfgBuildResult cfg,PageStore pages,AnalysisResources execution,AnalysisResources root)throws Exception;}
    private static void nativeInput(Work action)throws Exception {
        var contract=Class.forName(NativeGeneralProgramCursorTest.class.getName()+"$NativeWork");
        var work=Proxy.newProxyInstance(contract.getClassLoader(),new Class<?>[]{contract},(proxy,method,args)->{action.run((SnapshotProgram)args[0],(CfgBuildResult)args[1],(PageStore)args[2],(AnalysisResources)args[3],(AnalysisResources)args[4]);return null;});
        var method=NativeGeneralProgramCursorTest.class.getDeclaredMethod("nativeInput",int.class,contract);method.setAccessible(true);
        try{method.invoke(null,1,work);}catch(InvocationTargetException failure){if(failure.getCause() instanceof Exception e)throw e;if(failure.getCause() instanceof Error e)throw e;throw failure;}
    }
    private static byte[] legacy(TargetResolver.Resolution resolution)throws Exception {
        var method=DependencyJson.class.getDeclaredMethod("program",TargetResolver.Resolution.class);method.setAccessible(true);var bytes=new ByteArrayOutputStream();var out=new JsonOutput(bytes);out.value(method.invoke(null,resolution));out.finish();return bytes.toByteArray();
    }
    private static ManagedExecutableDependencyResult staged(SnapshotProgram program,PageStore pages,AnalysisResources execution,DependencyResult base,DependencySiteFact fact,QualifiedDependencyOccurrence occurrence)throws Exception {
        var constructor=ManagedExecutableDependencyResult.class.getDeclaredConstructor(SnapshotProgram.class,PageStore.class,AnalysisResources.class);constructor.setAccessible(true);var owner=constructor.newInstance(program,pages,execution);
        try{
            var field=ManagedExecutableDependencyResult.class.getDeclaredField("facts");field.setAccessible(true);((PagedDependencyFactStorage)field.get(owner)).append(fact);
            var index=ManagedExecutableDependencyResult.class.getDeclaredMethod("indexPrepared");index.setAccessible(true);index.invoke(owner);
            var finish=ManagedExecutableDependencyResult.class.getDeclaredMethod("finishIndex",java.util.function.ToIntFunction.class);finish.setAccessible(true);finish.invoke(owner,(java.util.function.ToIntFunction<Object>)h->0);
            var type=Class.forName("io.github.gustavo2358.analysis.dependencies.DependencyInput$Qualification");var ctor=type.getDeclaredConstructor(Optional.class,List.class);ctor.setAccessible(true);var qualification=ctor.newInstance(Optional.empty(),List.of(occurrence));
            var bind=ManagedExecutableDependencyResult.class.getDeclaredMethod("bind",DependencyResult.class,type);bind.setAccessible(true);bind.invoke(owner,base,qualification);return owner;
        }catch(Exception|Error failure){owner.close();throw failure;}
    }
    private static DependencySiteFact candidates(DependencySiteFact f,List<DependencySiteFact.Candidate> candidates){
        return new DependencySiteFact(f.caller(),f.entry(),f.sequence(),f.operation(),f.offset(),f.siteOrigin(),f.targetOrigin(),f.technology(),f.command(),f.namespace(),f.nameProfile(),f.targetKind(),f.subject(),f.valuePoint(),f.reachability(),f.targetStatus(),List.of(),candidates,f.modelValueRemainder(),f.sourceValueRemainder(),f.interpretationUnknownRemainder(),f.effectiveUnknownRemainder(),f.openControlRemainder(),f.evidence(),f.provenance(),f.premises(),f.uncertaintyRefs(),f.analysisStatus(),f.analysisReasons());
    }
    @Test void oneCallerStagedProgramEveryCandidateReverseReadAndFullLegacyWireHaveFixedResidentControls()throws Exception {
        nativeInput((program,cfg,pages,execution,root)->{
            var base=new DependencyAnalysis().prepareLeased(program,cfg,pages,execution);var original=base.sites().getFirst();var occurrence=base.programDependencies().getFirst().occurrence();long fixed=-1;
            for(int count:new int[]{64,256,1024,4096,16384}){
                var values=new ArrayList<DependencySiteFact.Candidate>();for(int i=count-1;i>=0;i--){String value=String.format("PG%05d",i);values.add(new DependencySiteFact.Candidate(value,value,original.candidates().getFirst().supports()));}
                var fact=candidates(original,values);byte[] expected=legacy(TargetResolver.resolve(occurrence,List.of(fact))); // Caller-owned oracle before managed staging.
                try(var owner=staged(program,pages,execution,base,fact,occurrence)){
                    long resident=execution.used(AnalysisResources.Pool.RESIDENT),scratch=execution.used(AnalysisResources.Pool.SCRATCH),projected=owner.fullFactProjections();
                    try(var cursor=owner.openProgram(0)){
                        long delta=execution.used(AnalysisResources.Pool.RESIDENT)-resident;if(fixed<0)fixed=delta;assertEquals(fixed,delta,"single-program cardinality cannot grow resident controls");assertEquals(count,cursor.candidateCount());assertEquals(1,cursor.siteCount());
                        char[] block=new char[7];for(int i=count-1;i>=0;i--){assertEquals(7,cursor.referenceLength(i));cursor.readReference(i,0,block,0,7);assertEquals(String.format("PG%05d",i),new String(block));assertEquals(1,cursor.supportCount(i));try(var support=cursor.openSupport(i,0);var producer=support.producer()){assertEquals("operation",producer.domain());}}
                        try(var encoding=execution.reserve(AnalysisResources.Pool.RESIDENT,65536+4096,AnalysisResources.Phase.ENCODE)){
                            assertEquals(65536+4096,encoding.amount());var bytes=new ByteArrayOutputStream();var out=new JsonOutput(bytes);new DependencyJson.FactWriter(out,pages,execution).program(cursor);out.finish();assertArrayEquals(expected,bytes.toByteArray(),"full program legacy bytes and every candidate/support/site field");
                        }
                    }assertEquals(projected,owner.fullFactProjections());assertEquals(resident,execution.used(AnalysisResources.Pool.RESIDENT));assertEquals(scratch,execution.used(AnalysisResources.Pool.SCRATCH));
                    System.out.println("GENERAL_SINGLE_STAGED_PROGRAM count="+count+" fixedResident="+fixed+" fullFact="+owner.fullFactProjections());
                }
            }
        });
    }
}
