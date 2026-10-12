package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.validation.*;
import io.github.gustavo2358.analysis.dependencies.*;
import io.github.gustavo2358.analysis.solver.*;
import java.lang.reflect.*;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** A cold qualification boundary law, not a whole-provider heap/CLI claim. */
final class NativePlainQualificationTest {
    @TempDir Path directory;
    private static Object invoke(Method method,Object receiver,Object... arguments)throws Exception {
        method.setAccessible(true);
        try{return method.invoke(receiver,arguments);}
        catch(InvocationTargetException failure){if(failure.getCause() instanceof Exception error)throw error;if(failure.getCause() instanceof Error error)throw error;throw failure;}
    }
    private static final class GuardedSource implements AirSnapshot.Source {
        private final AirSnapshot.Source delegate;
        private boolean qualification;
        private long payloadReads;
        GuardedSource(AirSnapshot.Source delegate){this.delegate=delegate;}
        @Override public AirShape shape(long handle){return delegate.shape(handle);}
        @Override public long length(long handle){return delegate.length(handle);}
        @Override public long child(long handle,long ordinal){
            if(qualification&&shape(handle)==AirShape.OPERATIONS_INVOKE&&ordinal>=3){payloadReads++;throw new AssertionError("PLAIN_QUALIFICATION_MATERIALIZES_INVOKE_PAYLOAD_RED");}
            return delegate.child(handle,ordinal);
        }
        @Override public long scalar(long handle){return delegate.scalar(handle);}
        @Override public int characters(long handle,long offset,char[] destination,int start,int count){return delegate.characters(handle,offset,destination,start,count);}
        @Override public AirSnapshot.Elements elements(long handle){return delegate.elements(handle);}
        @Override public void close(){delegate.close();}
    }
    @Test void plainQualificationKeepsOnlyPagedAddressesAndReadsNoColdInvokePayload()throws Exception {
        long retained=-1;
        for(int count:new int[]{4,16,64}){
            var fixture=SnapshotDependencyAnalysisTest.class.getDeclaredMethod("directCalls",int.class);
            var publication=(Publication)invoke(fixture,null,count);
            var expected=new DependencyInput(publication,Optional.empty(),List.of()).occurrences();
            if(count==64){
                var units=new ArrayList<Unit>();var manual=new ArrayList<QualifiedDependencyOccurrence>();
                for(int at=0;at<count;at++){
                    var unit=publication.units().get(at);var start=unit.sequences().getFirst();var call=(Operations.Invoke)start.terminator();
                    int variant=at%7;boolean literal=variant==0||variant==1||variant==4||variant==5,cics=variant==1||variant==2;
                    String category=variant==4?"file":"program",namespace=variant==5?"other.program":cics?"cics.program":"cobol.program";
                    var original=(Interactions.ComputedTarget)call.target();
                    Interactions.Target target=literal?new Interactions.LiteralTarget(category,namespace,"PROGA",Interactions.ExactName.INSTANCE,unit.origin()):new Interactions.ComputedTarget(category,namespace,original.name(),original.namePolicy(),original.origin());
                    Terminator replacement=variant==6?new Operations.Jump(call.header(),unit.sequences().getLast().label()):new Operations.Invoke(call.header(),call.action(),target,call.arguments(),call.results(),call.signature(),call.effectOperands(),call.effectBound(),call.outcomes(),call.contract());
                    var sequences=new ArrayList<>(unit.sequences());sequences.set(0,new Sequence(start.label(),start.instructions(),replacement,start.origin()));Collections.reverse(sequences);
                    units.add(ResultFixtures.unit(unit.id(),unit.entries(),sequences,unit.objects()));
                    if(variant<4)manual.add(new QualifiedDependencyOccurrence(Optional.empty(),unit.id().localId(),cics?"CICS":"COBOL",cics?CicsNameInterpreter.PROFILE:CallNameInterpreter.PROFILE,literal?"LITERAL":"COMPUTED",List.of(),List.of(),List.of(call.header().id()),!literal));
                }
                Collections.reverse(units);Collections.reverse(manual);publication=ResultFixtures.publication(publication.id(),units,publication.storage());expected=List.copyOf(manual);
                assertEquals(expected,new DependencyInput(publication,Optional.empty(),List.of()).occurrences(),"manual exact selection and physical order,not lexical sorting");
            }
            var resources=new AnalysisResources(new AnalysisResources.Limits(64_000_000,16_000_000,0,256_000_000,8,Long.MAX_VALUE,Long.MAX_VALUE));
            try(var pages=new FilePageStore(directory,512,1,resources);var original=AirSnapshot.fromPublication(publication)){
                var field=AirSnapshot.class.getDeclaredField("source");field.setAccessible(true);
                var source=new GuardedSource((AirSnapshot.Source)field.get(original));
                try(var checked=SnapshotValidator.check(AirSnapshot.attach(source,original.root()),ValidationOptions.defaults(),new PagedSnapshotValidationStorage(pages,resources));
                    var program=new SnapshotProgram(checked,new PagedSnapshotIdentityStorage(pages,resources),new PagedSnapshotOrderStorage(pages,resources),resources)){
                    assertTrue(checked.result().isStructurallyValid());
                    var constructor=ManagedExecutableDependencyResult.class.getDeclaredConstructor(SnapshotProgram.class,PageStore.class,AnalysisResources.class);constructor.setAccessible(true);
                    long baseline=resources.heapUsed(),live=pages.statistics().livePages();
                    List<?> retired;
                    try(var owner=constructor.newInstance(program,pages,AnalysisResources.executionFor(program))){
                        source.qualification=true;
                        try{
                            var qualify=owner.getClass().getDeclaredMethod("qualifyPlain");invoke(qualify,owner);
                        }catch(NoSuchMethodException oldRuntime){
                            // Observe the original productive boundary on frozen RED, not an API-only failure.
                            var qualify=DependencyInput.class.getDeclaredMethod("qualify",io.github.gustavo2358.analysis.structure.ProgramStore.Structural.class,Optional.class,List.class);
                            invoke(qualify,null,program,Optional.empty(),List.of());
                            fail("original plain qualification unexpectedly avoided every cold payload");
                        }
                        assertEquals(0,source.payloadReads);
                        var occurrences=owner.getClass().getDeclaredField("occurrences");occurrences.setAccessible(true);
                        var values=(List<?>)occurrences.get(owner);retired=values;assertEquals(expected.size(),values.size());
                        long delta=resources.heapUsed()-baseline;if(retained<0)retained=delta;assertEquals(retained,delta,"catalogue resident control must not grow with required rows");
                        for(int i=expected.size()-1;i>=0;i--)assertEquals(expected.get(i),values.get(i),"all metadata,complete identity and original Unit/Sequence order");
                        assertThrows(UnsupportedOperationException.class,values::clear);
                        System.out.println("PLAIN_QUALIFICATION count="+count+" retained="+delta+" payloadReads="+source.payloadReads);
                    }
                    source.qualification=false;
                    assertThrows(IllegalStateException.class,retired::size);
                    assertEquals(baseline,resources.heapUsed());assertEquals(live,pages.statistics().livePages());assertNotNull(program.admission().snapshot());
                    try(var owner=constructor.newInstance(program,pages,AnalysisResources.executionFor(program));
                        var blocker=resources.reserve(AnalysisResources.Pool.RESIDENT,64_000_000-resources.heapUsed(),AnalysisResources.Phase.DEMAND)){
                        assertTrue(blocker.amount()>0);
                        var method=owner.getClass().getDeclaredMethod("qualifyPlain");
                        assertThrows(AnalysisResources.Exhausted.class,()->invoke(method,owner));
                        assertThrows(IllegalStateException.class,()->invoke(method,owner),"denial expires the derived owner");assertNotNull(program.admission().snapshot());
                    }
                    assertEquals(baseline,resources.heapUsed());assertEquals(live,pages.statistics().livePages());
                }
            }finally{for(var pool:AnalysisResources.Pool.values())assertEquals(0,resources.used(pool),pool.toString());}
        }
    }
}
