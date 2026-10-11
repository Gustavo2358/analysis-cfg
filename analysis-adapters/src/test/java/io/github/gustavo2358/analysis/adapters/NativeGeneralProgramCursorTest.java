package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.analysis.cfg.application.CfgBuildResult;
import io.github.gustavo2358.analysis.dependencies.*;
import io.github.gustavo2358.analysis.solver.*;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Genuine native general owner/writer laws, not a dummy SOURCE staging bridge.
 * Caller-owned synthetic qualification isolates aggregation, not provider production. */
@SuppressWarnings("try")
final class NativeGeneralProgramCursorTest {
    @FunctionalInterface private interface NativeWork {void run(SnapshotProgram program,CfgBuildResult cfg,PageStore pages,AnalysisResources execution,AnalysisResources root)throws Exception;}
    private static void nativeInput(int count,NativeWork action)throws Exception {
        var harness=new ManagedExecutableDependencyResultTest();harness.directory=Files.createTempDirectory(Path.of("target"),"native-general-program-law-");
        var contract=Class.forName(ManagedExecutableDependencyResultTest.class.getName()+"$Work");
        var work=Proxy.newProxyInstance(contract.getClassLoader(),new Class<?>[]{contract},(proxy,method,args)->{action.run((SnapshotProgram)args[0],(CfgBuildResult)args[1],(PageStore)args[2],(AnalysisResources)args[3],(AnalysisResources)args[4]);return null;});
        var input=ManagedExecutableDependencyResultTest.class.getDeclaredMethod("nativeInput",int.class,contract);input.setAccessible(true);
        try{input.invoke(harness,count,work);}catch(InvocationTargetException failure){raise(failure.getCause());}
    }
    private static void raise(Throwable failure)throws Exception {if(failure instanceof Exception e)throw e;if(failure instanceof Error e)throw e;throw new AssertionError(failure);}
    private static Object call(Object target,String name,Class<?>[] signature,Object... args)throws Exception {
        Method method;
        try{method=target.getClass().getMethod(name,signature);}catch(NoSuchMethodException absent){throw new AssertionError("GENERAL_PROGRAM_SCOPED_PORT_ABSENT_RED "+name,absent);}
        try{return method.invoke(target,args);}catch(InvocationTargetException failure){raise(failure.getCause());throw new AssertionError();}
    }
    private static Object call(Object target,String name)throws Exception{return call(target,name,new Class<?>[0]);}
    private static long number(Object target,String name)throws Exception{return ((Number)call(target,name)).longValue();}
    private static AutoCloseable open(ManagedExecutableDependencyResult owner,int ordinal)throws Exception{return (AutoCloseable)call(owner,"openProgram",new Class<?>[]{int.class},ordinal);}
    private static String text(Object cursor,String name,long candidate)throws Exception {
        int length=Math.toIntExact(((Number)call(cursor,name+"Length",new Class<?>[]{long.class},candidate)).longValue());
        // Caller-owned expected/output buffer; not a productive materialization port.
        char[] chars=new char[length];for(int at=0;at<length;at+=Math.min(256,length-at))call(cursor,"read"+Character.toUpperCase(name.charAt(0))+name.substring(1),new Class<?>[]{long.class,long.class,char[].class,int.class,int.class},candidate,(long)at,chars,at,Math.min(256,length-at));return new String(chars);
    }
    private static DependencyResult bind(ManagedExecutableDependencyResult owner,DependencyResult base,QualifiedDependencyOccurrence occurrence)throws Exception {
        var type=Class.forName("io.github.gustavo2358.analysis.dependencies.DependencyInput$Qualification");var constructor=type.getDeclaredConstructor(Optional.class,List.class);constructor.setAccessible(true);
        var qualification=constructor.newInstance(Optional.empty(),List.of(occurrence));var method=ManagedExecutableDependencyResult.class.getDeclaredMethod("bind",DependencyResult.class,type);method.setAccessible(true);
        try{return (DependencyResult)method.invoke(owner,base,qualification);}catch(InvocationTargetException failure){raise(failure.getCause());throw new AssertionError();}
    }
    @Test void actualGeneralWriterAcrossFiveGeometriesDoesNotProjectCompleteFactsForProgramAggregation()throws Exception {
        var assertions=new ArrayList<org.junit.jupiter.api.function.Executable>();
        for(int count:new int[]{1,2,4,8,16})assertions.add(()->nativeInput(count,(program,cfg,pages,resources,root)->{
            var expected=new DependencyAnalysis().prepareLeased(program,cfg,pages,resources);var reference=new ByteArrayOutputStream();new DependencyJson().writeSnapshot(expected,program,reference);
            try(var owner=new DependencyAnalysis().prepareManaged(program,cfg,pages,resources)) {
                long before=owner.fullFactProjections(),scratch=resources.used(AnalysisResources.Pool.SCRATCH);var bytes=new ByteArrayOutputStream();new DependencyJson().writeSnapshot(owner,program,resources,bytes);
                assertArrayEquals(reference.toByteArray(),bytes.toByteArray(),"whole legacy product bytes remain independent");
                assertEquals(before,owner.fullFactProjections(),"GENERAL_PROGRAM_FULL_FACT_PROJECTION_RED count="+count);
                assertEquals(scratch,resources.used(AnalysisResources.Pool.SCRATCH));
            }
        }));assertAll("GENERAL_PROGRAM_FIVE_GEOMETRIES",assertions);
    }
    @Test void scopedGeneralProgramFieldsBoundsAndParentExpiryPreserveBorrowedOwners()throws Exception {
        nativeInput(4,(program,cfg,pages,resources,root)->{
            var expected=new DependencyAnalysis().prepareLeased(program,cfg,pages,resources).programDependencies().getFirst();
            try(var owner=new DependencyAnalysis().prepareManaged(program,cfg,pages,resources)) {
                long before=owner.fullFactProjections(),scratch=resources.used(AnalysisResources.Pool.SCRATCH);
                var cursor=open(owner,0);
                try(cursor) {
                    assertEquals(1,number(cursor,"siteCount"));assertEquals(1,number(cursor,"candidateCount"));
                    assertEquals("PROGA",text(cursor,"reference",0));assertEquals("PROGA   ",text(cursor,"raw",0));
                    assertEquals(expected.valueRemainder(),call(cursor,"valueRemainder"));assertEquals(expected.interpretationRemainder(),call(cursor,"interpretationRemainder"));
                    assertThrows(IndexOutOfBoundsException.class,()->call(cursor,"supportCount",new Class<?>[]{long.class},-1L));assertEquals(1,number(cursor,"candidateCount"));
                    var site=(AutoCloseable)call(cursor,"openSite",new Class<?>[]{long.class},0L);
                    var child=(AutoCloseable)call(site,"openCandidate",new Class<?>[]{int.class},0);
                    cursor.close();assertThrows(IllegalStateException.class,()->number(cursor,"candidateCount"));assertThrows(IllegalStateException.class,()->call(site,"candidateCount"));assertThrows(IllegalStateException.class,()->call(child,"supportCount"));child.close();site.close();
                }
                assertEquals(before,owner.fullFactProjections());assertEquals(scratch,resources.used(AnalysisResources.Pool.SCRATCH));
                var second=open(owner,0);owner.close();assertThrows(IllegalStateException.class,()->number(second,"candidateCount"));second.close();assertNotNull(program.admission().snapshot());assertTrue(pages.statistics().livePages()>0);
            }
        });
    }
    @Test void oneProgramWithReversedDuplicateOperationsPreservesFullLegacyResolutionAndWire()throws Exception {
        nativeInput(16,(program,cfg,pages,resources,root)->{
            var base=new DependencyAnalysis().prepareLeased(program,cfg,pages,resources);var operations=new ArrayList<io.github.gustavo2358.air.model.Ids.OperationId>();for(var fact:base.sites())operations.add(fact.operation());Collections.reverse(operations);operations.add(operations.getFirst());
            var occurrence=new QualifiedDependencyOccurrence(Optional.empty(),"CALLER","COBOL",CallNameInterpreter.PROFILE,"COMPUTED",List.of(),List.of("QUALIFIED"),operations,true,true);
            var selected=new ArrayList<DependencySiteFact>();for(var operation:operations)for(var fact:base.sites())if(fact.operation().equals(operation))selected.add(fact);
            var resolved=TargetResolver.resolve(occurrence,selected);assertEquals(17,resolved.executableSites().size());assertEquals(1,resolved.candidates().size());
            assertEquals(selected.stream().anyMatch(s->!Boolean.FALSE.equals(s.modelValueRemainder())||s.sourceValueRemainder()),resolved.valueRemainder(),"closed model is not automatically closed SOURCE");
            assertEquals(selected.stream().anyMatch(DependencySiteFact::interpretationUnknownRemainder),resolved.interpretationRemainder());
            var reasons=new TreeSet<String>();reasons.add("SOURCE_CONTROL_UNAVAILABLE");for(var fact:selected)reasons.addAll(fact.analysisReasons());assertEquals(List.copyOf(reasons),resolved.analysisReasons());
            try(var owner=new DependencyAnalysis().prepareManaged(program,cfg,pages,resources)) {
                var bound=bind(owner,base,occurrence);var expected=new DependencyResult(base.publication(),base.airVersion(),base.sites(),base.edges(),bound.metrics(),base.publicationInventory(),base.origins(),base.artifacts(),base.sourceUncertaintyRefs(),base.analysisReasons(),base.fileDependencies(),base.sourceDependencies(),base.sourceQualifiedDependencies(),List.of(resolved));
                var reference=new ByteArrayOutputStream();new DependencyJson().writeSnapshot(expected,program,reference);long before=owner.fullFactProjections();
                try(var cursor=open(owner,0)) {
                    assertEquals(17,number(cursor,"siteCount"));assertEquals(1,number(cursor,"candidateCount"));assertEquals(resolved.candidates().getFirst().executableSupports().size(),((Number)call(cursor,"supportCount",new Class<?>[]{long.class},0L)).longValue());
                    assertEquals("PROGA",text(cursor,"reference",0));assertEquals("PROGA   ",text(cursor,"raw",0));
                }
                var bytes=new ByteArrayOutputStream();new DependencyJson().writeSnapshot(owner,program,resources,bytes);assertArrayEquals(reference.toByteArray(),bytes.toByteArray(),"single merged program candidates/proofs/sites/order/reasons/authorities/metrics exact");assertEquals(before,owner.fullFactProjections());
            }
        });
    }
}
