package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.analysis.cfg.application.CfgBuildResult;
import io.github.gustavo2358.analysis.consumers.FactStorage;
import io.github.gustavo2358.analysis.dependencies.*;
import io.github.gustavo2358.analysis.solver.*;
import java.lang.reflect.*;
import java.nio.file.Path;
import java.util.*;
import java.util.function.ToLongFunction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Internal same-row metadata and staged full-key laws. Not provider, AIR-wide
 * admission or all-store qualification; the separate real width test covers AIR. */
@SuppressWarnings("try")
final class NativeHeaderMetadataTest {
    @TempDir Path directory;
    private static Object invoke(Method method,Object receiver,Object... args)throws Exception {
        method.setAccessible(true);
        try{return method.invoke(receiver,args);}
        catch(InvocationTargetException failure){if(failure.getCause() instanceof Exception e)throw e;if(failure.getCause() instanceof Error e)throw e;throw failure;}
    }
    private static Object call(Object receiver,String name,Class<?>[] types,Object... args)throws Exception {
        return invoke(receiver.getClass().getDeclaredMethod(name,types),receiver,args);
    }
    private static long metadata(PagedDependencyFactStorage storage,int ordinal)throws Exception {
        return (long)call(storage,"ownerMetadata",new Class<?>[]{int.class},ordinal);
    }
    @SuppressWarnings("unchecked")
    private static FactStorage<DependencySiteFact> transaction(PagedDependencyFactStorage storage,ToLongFunction<DependencySiteFact> function)throws Exception {
        return (FactStorage<DependencySiteFact>)call(storage,"transaction",new Class<?>[]{ToLongFunction.class},function);
    }
    private static DependencySiteFact fixture(int number)throws Exception {
        return (DependencySiteFact)invoke(PagedDependencyFactStorageTest.class.getDeclaredMethod("fact",int.class),null,number);
    }
    private static AnalysisResources resources(){return new AnalysisResources(new AnalysisResources.Limits(650_000,8_000_000,0,256_000_000,4,Long.MAX_VALUE,Long.MAX_VALUE));}
    private static void released(AnalysisResources resources){for(var pool:AnalysisResources.Pool.values())assertEquals(0,resources.used(pool),pool.toString());}
    @Test void sameRowMetadataCommitsWithPrefixRollsBackWithTailAndNeverRevivesExpiredScopes()throws Exception {
        var resources=resources();var first=fixture(0);var second=fixture(1);var replacement=fixture(2);
        long packed=((Integer.MAX_VALUE+1L)<<3)|7;
        try(var pages=new FilePageStore(directory,256,2,resources);var storage=new PagedDependencyFactStorage(pages,resources)){
            try(var tx=transaction(storage,f->packed)){tx.append(first);call(storage,"seal",new Class<?>[0]);}
            assertEquals(1,storage.size());assertEquals(packed,metadata(storage,0));
            try(var prefix=storage.openFact(0)){
                var tx=transaction(storage,f->123);tx.append(second);var tail=storage.openFact(1);var child=tail.operation();
                assertEquals(123,metadata(storage,1));tx.close();tx.close();
                assertEquals(1,storage.size());assertEquals(packed,metadata(storage,0));
                assertEquals(first.candidates().size(),prefix.candidateCount());
                assertThrows(IllegalStateException.class,tail::candidateCount);
                assertThrows(IllegalStateException.class,()->child.characterCount(PagedDependencyFactStorage.IdentityPart.LOCAL));
                try(var reuse=transaction(storage,f->456)){reuse.append(replacement);call(storage,"seal",new Class<?>[0]);}
                assertEquals(456,metadata(storage,1));assertEquals(replacement,storage.get(1));
                assertThrows(IllegalStateException.class,tail::candidateCount);child.close();tail.close();
                assertThrows(IndexOutOfBoundsException.class,()->metadata(storage,2));
                storage.append(second);assertEquals(0,metadata(storage,2),"public compatibility append has no managed metadata");
                assertEquals(first,storage.get(0));assertEquals(second,storage.get(2));
            }
        }released(resources);
    }
    @Test void metadataCallbackFailureRemainsPrimaryExpiresOwnedScopesAndLeavesBorrowedBackendAlive()throws Exception {
        var resources=resources();var fact=fixture(0);var failure=new IllegalStateException("synthetic-metadata-callback");
        try(var pages=new FilePageStore(directory,256,2,resources);var borrowed=new PagedLongArray(pages,64,resources,AnalysisResources.Phase.DEMAND)){
            // Borrowed file owner retains released physical slots until its own
            // close. Live logical pages, not file high-water bytes, prove teardown.
            borrowed.set(0,91);long resident=resources.used(AnalysisResources.Pool.RESIDENT),live=pages.statistics().livePages();
            var storage=new PagedDependencyFactStorage(pages,resources);storage.append(fact);var prefix=storage.openFact(0);var child=prefix.operation();
            var tx=transaction(storage,f->{throw failure;});
            assertSame(failure,assertThrows(IllegalStateException.class,()->tx.append(fact)));
            assertThrows(IllegalStateException.class,storage::size);assertThrows(IllegalStateException.class,prefix::candidateCount);
            assertThrows(IllegalStateException.class,()->child.characterCount(PagedDependencyFactStorage.IdentityPart.LOCAL));
            tx.close();child.close();prefix.close();storage.close();storage.close();
            assertEquals(resident,resources.used(AnalysisResources.Pool.RESIDENT));
            assertEquals(live,pages.statistics().livePages());assertEquals(0,resources.used(AnalysisResources.Pool.SCRATCH));
            borrowed.set(0,92);assertEquals(92,borrowed.get(0));
        }released(resources);
    }
    @FunctionalInterface private interface Work {void run(SnapshotProgram program,CfgBuildResult cfg,PageStore pages,AnalysisResources execution,AnalysisResources root)throws Exception;}
    private static void input(Work action)throws Exception {
        var contract=Class.forName(NativeGeneralProgramPressureTest.class.getName()+"$Work");
        var proxy=Proxy.newProxyInstance(contract.getClassLoader(),new Class<?>[]{contract},(p,m,args)->{
            action.run((SnapshotProgram)args[0],(CfgBuildResult)args[1],(PageStore)args[2],(AnalysisResources)args[3],(AnalysisResources)args[4]);return null;
        });
        invoke(NativeGeneralProgramPressureTest.class.getDeclaredMethod("nativeInput",contract),null,proxy);
    }
    private static DependencySiteFact minimal(UnitId unit,EntryId entry,OperationId operation,String value){
        var origin=new OriginId(unit.publication(),"origin");
        return new DependencySiteFact(unit,entry,new LabelId(unit,"seq"),operation,0,origin,origin,"CALL","CALL","program","call@1",
            DependencySiteFact.TargetKind.LITERAL,null,null,DependencySiteFact.Reachability.REACHABLE,DependencySiteFact.TargetStatus.RESOLVED_CANDIDATES,
            List.of(),List.of(new DependencySiteFact.Candidate(value,value,List.of())),false,false,true,true,false,List.of(),List.of(),List.of(),List.of());
    }
    private static String text(PagedDependencyFactStorage.IdentityView value,PagedDependencyFactStorage.IdentityPart part){
        int size=Math.toIntExact(value.characterCount(part));char[] chars=new char[size];
        for(int at=0;at<size;){int count=Math.min(256,size-at);value.readCharacters(part,at,chars,at,count);at+=count;}return new String(chars);
    }
    @Test void fullCollidingHeaderKeysBeyondProjectionBudgetKeepCanonicalOrderLastFactWinsAndLookupExact()throws Exception {
        input((program,cfg,pages,execution,root)->{
            var base=new DependencyAnalysis().prepareLeased(program,cfg,pages,execution);var occurrence=base.programDependencies().getFirst().occurrence();
            String prefix="x".repeat(300005);assertEquals((prefix+"Aa").hashCode(),(prefix+"BB").hashCode());
            var aa=new UnitId(new PublicationId("Aa"),"Aa");var ab=new UnitId(aa.publication(),"BB");var ba=new UnitId(new PublicationId("BB"),"Aa");
            var entries=List.of(new EntryId(aa,"Aa"),new EntryId(aa,"BB"),new EntryId(ab,"Aa"),new EntryId(ba,"Aa"));
            var ops=List.of(new OperationId(aa,prefix+"Aa"),new OperationId(aa,prefix+"BB"),new OperationId(ab,prefix+"Aa"),new OperationId(ba,prefix+"Aa"));
            var constructor=ManagedExecutableDependencyResult.class.getDeclaredConstructor(SnapshotProgram.class,PageStore.class,AnalysisResources.class);constructor.setAccessible(true);
            long resident=execution.used(AnalysisResources.Pool.RESIDENT),temporary=execution.used(AnalysisResources.Pool.TEMPORARY),scratch=execution.used(AnalysisResources.Pool.SCRATCH);
            try(var owner=constructor.newInstance(program,pages,execution)){
                var field=ManagedExecutableDependencyResult.class.getDeclaredField("facts");field.setAccessible(true);var storage=(PagedDependencyFactStorage)field.get(owner);
                for(int i=3;i>=0;i--)storage.append(minimal(entries.get(i).unit(),entries.get(i),ops.get(i),"PG"+i));
                storage.append(minimal(aa,entries.getFirst(),ops.getFirst(),"REPLACED"));
                call(owner,"indexPrepared",new Class<?>[0]);call(owner,"finishIndex",new Class<?>[0]);
                invoke(NativeGeneralProgramCursorTest.class.getDeclaredMethod("bind",ManagedExecutableDependencyResult.class,DependencyResult.class,QualifiedDependencyOccurrence.class),null,owner,base,occurrence);
                assertEquals(4,owner.result().sites().size());assertEquals(4,owner.candidateCount());
                for(int i=0;i<4;i++){
                    assertEquals(true,call(owner,"contains",new Class<?>[]{EntryId.class,OperationId.class},entries.get(i),ops.get(i)));
                    try(var fact=owner.openFact(i);var entry=fact.entry();var operation=fact.operation();var candidate=fact.openCandidate(0)){
                        for(var part:List.of(PagedDependencyFactStorage.IdentityPart.PUBLICATION,PagedDependencyFactStorage.IdentityPart.UNIT,PagedDependencyFactStorage.IdentityPart.LOCAL)){
                            String expected=switch(part){case PUBLICATION->entries.get(i).publication().localId();case UNIT->entries.get(i).unit().localId();case LOCAL->entries.get(i).localId();case OWNER->throw new AssertionError("Entry has no Operand owner");};
                            assertEquals(expected,text(entry,part));
                        }
                        assertEquals(ops.get(i).localId(),text(operation,PagedDependencyFactStorage.IdentityPart.LOCAL));
                        char[] value=new char[i==0?8:3];candidate.readReference(0,value,0,value.length);assertEquals(i==0?"REPLACED":"PG"+i,new String(value));
                    }
                }
                assertEquals(false,call(owner,"contains",new Class<?>[]{EntryId.class,OperationId.class},entries.getFirst(),new OperationId(aa,prefix+"CC")));
                assertEquals(false,call(owner,"contains",new Class<?>[]{EntryId.class,OperationId.class},new EntryId(aa,"CC"),ops.getFirst()));
                assertThrows(IndexOutOfBoundsException.class,()->owner.openFact(4));assertEquals(0,owner.fullFactProjections());assertEquals(scratch,execution.used(AnalysisResources.Pool.SCRATCH));
            }
            assertEquals(resident,execution.used(AnalysisResources.Pool.RESIDENT));assertEquals(temporary,execution.used(AnalysisResources.Pool.TEMPORARY));program.admission();
        });
    }
}
