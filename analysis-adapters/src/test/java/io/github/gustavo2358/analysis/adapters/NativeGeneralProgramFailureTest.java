package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.analysis.cfg.application.CfgBuildResult;
import io.github.gustavo2358.analysis.dependencies.*;
import io.github.gustavo2358.analysis.solver.*;
import java.io.*;
import java.lang.reflect.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Independent graph failure/ownership laws. Not provider/global qualification. */
@SuppressWarnings("try")
final class NativeGeneralProgramFailureTest {
    @FunctionalInterface private interface Work {void run(SnapshotProgram program,CfgBuildResult cfg,PageStore pages,AnalysisResources execution,AnalysisResources root)throws Exception;}
    private static void input(Work action)throws Exception {input(true,action);}
    private static void input(boolean warm,Work action)throws Exception {
        var contract=Class.forName(NativeGeneralProgramCursorTest.class.getName()+"$NativeWork");
        var work=Proxy.newProxyInstance(contract.getClassLoader(),new Class<?>[]{contract},(proxy,method,args)->{
            var program=(SnapshotProgram)args[0];var cfg=(CfgBuildResult)args[1];var pages=(PageStore)args[2];var resources=(AnalysisResources)args[3];
            if(warm){var expected=new DependencyAnalysis().prepareLeased(program,cfg,pages,resources);assertEquals(4,expected.sites().size());for(var fact:expected.sites())assertEquals(java.util.List.of("PROGA"),fact.candidates().stream().map(DependencySiteFact.Candidate::referenceName).toList());}
            action.run(program,cfg,pages,resources,(AnalysisResources)args[4]);return null;});
        var method=NativeGeneralProgramCursorTest.class.getDeclaredMethod("nativeInput",int.class,contract);method.setAccessible(true);
        try{method.invoke(null,4,work);}catch(InvocationTargetException failure){if(failure.getCause() instanceof Exception e)throw e;if(failure.getCause() instanceof Error e)throw e;throw failure;}
    }
    @Test void everyInvalidProgramFieldAndBlockRangeLeavesOwnerAndSiblingScopesUsable()throws Exception {
        input((program,cfg,pages,resources,root)->{
            try(var owner=new DependencyAnalysis().prepareManaged(program,cfg,pages,resources);var cursor=owner.openProgram(0);var site=cursor.openSite(0);var support=cursor.openSupport(0,0);var producer=support.producer()){
                assertThrows(IndexOutOfBoundsException.class,()->owner.openProgram(-1));assertThrows(IndexOutOfBoundsException.class,()->owner.openProgram(owner.result().programDependencies().size()));
                assertThrows(IndexOutOfBoundsException.class,()->cursor.openSite(-1));assertThrows(IndexOutOfBoundsException.class,()->cursor.openSite(cursor.siteCount()));
                assertThrows(IndexOutOfBoundsException.class,()->cursor.openSupport(-1,0));assertThrows(IndexOutOfBoundsException.class,()->cursor.openSupport(0,cursor.supportCount(0)));
                assertThrows(IndexOutOfBoundsException.class,()->cursor.authority(cursor.authorityCount()));assertThrows(IndexOutOfBoundsException.class,()->cursor.reasonLength(cursor.reasonCount()));
                char[] block=new char[7];assertThrows(IndexOutOfBoundsException.class,()->cursor.readRaw(0,-1,block,0,1));assertThrows(IndexOutOfBoundsException.class,()->cursor.readRaw(0,Long.MAX_VALUE,block,0,1));
                assertThrows(IndexOutOfBoundsException.class,()->cursor.readRaw(0,0,block,-1,1));assertThrows(IndexOutOfBoundsException.class,()->cursor.readRaw(0,0,block,0,8));
                cursor.readRaw(0,cursor.rawLength(0),block,0,0);cursor.readReference(0,0,block,0,5);assertEquals("PROGA",new String(block,0,5));
                assertEquals(4,owner.result().sites().size());assertEquals(1,site.candidateCount());assertEquals("operation",producer.domain());assertEquals(0,owner.fullFactProjections());
            }
        });
    }
    @Test void nullDestinationIsCallerErrorAndDoesNotRetireTheGeneralProgramGraph()throws Exception {
        input((program,cfg,pages,resources,root)->{
            try(var owner=new DependencyAnalysis().prepareManaged(program,cfg,pages,resources);var cursor=owner.openProgram(0);var site=cursor.openSite(0);var support=cursor.openSupport(0,0);var producer=support.producer()){
                assertThrows(NullPointerException.class,()->cursor.readRaw(0,0,null,0,1));
                assertEquals(1,cursor.candidateCount(),"GENERAL_PROGRAM_NULL_RANGE_POISONED_OWNER_RED");assertEquals(4,owner.result().sites().size());assertEquals("operation",producer.domain());assertEquals(1,site.candidateCount());
                char[] value=new char[5];cursor.readReference(0,0,value,0,5);assertEquals("PROGA",new String(value));assertEquals(0,owner.fullFactProjections());
            }
        });
    }
    @Test void deniedResidentControlAndEarlyCarriersRetireOnlyTheDerivedOwner()throws Exception {
        for(long available:new long[]{4095,4096,4096+3072})input((program,cfg,pages,resources,root)->{
            long baseline=pages.statistics().livePages(),resident=root.heapUsed();
            try(var owner=new DependencyAnalysis().prepareManaged(program,cfg,pages,resources)){
                try(var pressure=root.reserve(AnalysisResources.Pool.RESIDENT,root.limits().heapBytes()-root.heapUsed()-available,AnalysisResources.Phase.SORT)){
                    assertNotNull(pressure);var failure=assertThrows(AnalysisResources.Exhausted.class,()->owner.openProgram(0));assertEquals(AnalysisResources.Resource.HEAP,failure.resource());
                    assertThrows(IllegalStateException.class,owner::result);assertEquals(baseline,pages.statistics().livePages());
                }
                assertEquals(resident,root.heapUsed());assertNotNull(program.admission().snapshot());long handle=pages.allocate();pages.release(handle);
            }
        });
    }
    @Test void deniedSiteAndSupportWrappersExpireAllDerivedScopesWithoutScratchLeaks()throws Exception {
        for(int port=0;port<2;port++){final boolean supportPort=port==1;input((program,cfg,pages,resources,root)->{
            long baseline=pages.statistics().livePages(),scratch=resources.used(AnalysisResources.Pool.SCRATCH),resident=root.heapUsed();
            try(var owner=new DependencyAnalysis().prepareManaged(program,cfg,pages,resources);var cursor=owner.openProgram(0);var site=cursor.openSite(0);var support=cursor.openSupport(0,0);var producer=support.producer()){
                try(var pressure=root.reserve(AnalysisResources.Pool.SCRATCH,root.limits().scratchBytes()-root.used(AnalysisResources.Pool.SCRATCH)-255,AnalysisResources.Phase.REPLAY)){
                    assertNotNull(pressure);long charged=pressure.amount();var failure=assertThrows(AnalysisResources.Exhausted.class,()->{if(supportPort)cursor.openSupport(0,0);else cursor.openSite(0);});assertEquals(AnalysisResources.Resource.SCRATCH,failure.resource());
                    assertEquals(charged+scratch,root.used(AnalysisResources.Pool.SCRATCH),"new wrappers and existing descendants must retire immediately");assertThrows(IllegalStateException.class,owner::result);assertThrows(IllegalStateException.class,cursor::candidateCount);assertThrows(IllegalStateException.class,site::candidateCount);assertThrows(IllegalStateException.class,producer::domain);assertEquals(baseline,pages.statistics().livePages());
                }
                assertEquals(resident,root.heapUsed());assertEquals(scratch,resources.used(AnalysisResources.Pool.SCRATCH));assertNotNull(program.admission().snapshot());
            }
        });}
    }
    @Test void allocationReadAndWriteFaultsDuringGeneralAssemblyPreservePrimaryAndBorrowedBackend()throws Exception {
        for(int primitive=0;primitive<3;primitive++){final int selected=primitive;input((program,cfg,pages,resources,root)->{
            var port=new SourceCorrelationSnapshotTest.FaultPages(pages);long baseline=pages.statistics().livePages(),resident=root.heapUsed(),scratch=resources.used(AnalysisResources.Pool.SCRATCH);
            try(var owner=new DependencyAnalysis().prepareManaged(program,cfg,port,resources)){
                port.arm(selected);assertSame(port.primary,assertThrows(PageStore.Failure.class,()->owner.openProgram(0)));assertThrows(IllegalStateException.class,owner::result);
                assertEquals(baseline,pages.statistics().livePages());assertEquals(resident,root.heapUsed());assertEquals(scratch,resources.used(AnalysisResources.Pool.SCRATCH));assertFalse(port.closed);assertNotNull(program.admission().snapshot());long handle=pages.allocate();pages.release(handle);
            }
        });}
    }
    @Test void lateTextReadFaultImmediatelyExpiresEveryProgramSiteSupportAndIdentityScope()throws Exception {
        input((program,cfg,pages,resources,root)->{
            var port=new SourceCorrelationSnapshotTest.FaultPages(pages);long baseline=pages.statistics().livePages(),resident=root.heapUsed(),scratch=resources.used(AnalysisResources.Pool.SCRATCH);
            try(var owner=new DependencyAnalysis().prepareManaged(program,cfg,port,resources);var cursor=owner.openProgram(0);var sibling=owner.openProgram(1);var site=cursor.openSite(0);var candidate=site.openCandidate(0);var support=cursor.openSupport(0,0);var producer=support.producer()){
                port.arm(1);assertSame(port.primary,assertThrows(PageStore.Failure.class,()->cursor.readRaw(0,0,new char[1],0,1)));
                assertEquals(scratch,resources.used(AnalysisResources.Pool.SCRATCH),"GENERAL_PROGRAM_READ_FAULT_LEFT_WRAPPER_FUNDING_RED");assertEquals(resident,root.heapUsed());assertEquals(baseline,pages.statistics().livePages());
                assertThrows(IllegalStateException.class,owner::result);assertThrows(IllegalStateException.class,cursor::candidateCount);assertThrows(IllegalStateException.class,sibling::candidateCount);assertThrows(IllegalStateException.class,site::candidateCount);assertThrows(IllegalStateException.class,candidate::rawLength);assertThrows(IllegalStateException.class,support::kind);assertThrows(IllegalStateException.class,producer::domain);assertFalse(port.closed);assertNotNull(program.admission().snapshot());
            }
        });
    }
    @Test void workExhaustionRetiresTheWholeGeneralGraphUsingCleanupBudgetOnly()throws Exception {
        input((program,cfg,pages,resources,root)->{
            long baseline=pages.statistics().livePages(),resident=root.heapUsed(),scratch=resources.used(AnalysisResources.Pool.SCRATCH);long borrowed=pages.allocate();
            try(var owner=new DependencyAnalysis().prepareManaged(program,cfg,pages,resources);var cursor=owner.openProgram(0);var site=cursor.openSite(0);var support=cursor.openSupport(0,0);var producer=support.producer()){
                root.work(root.limits().workUnits()-root.workUsed(),AnalysisResources.Phase.SORT);var failure=assertThrows(AnalysisResources.Exhausted.class,cursor::candidateCount);assertEquals(AnalysisResources.Resource.WORK,failure.resource());
                assertEquals(scratch,resources.used(AnalysisResources.Pool.SCRATCH));assertEquals(resident,root.heapUsed());assertEquals(baseline+1,pages.statistics().livePages());
                assertThrows(IllegalStateException.class,owner::result);assertThrows(IllegalStateException.class,site::candidateCount);assertThrows(IllegalStateException.class,support::kind);assertThrows(IllegalStateException.class,producer::domain);
                byte[] bytes=new byte[1];pages.readForCleanup(borrowed,0,bytes,0,1);assertEquals(0,bytes[0]);
            }finally{pages.releaseForCleanup(borrowed);}
        });
    }
    @Test void nativeProgramExpiryImmediatelyRetiresAllProgramDescendantsButNotBorrowedPageBackend()throws Exception {
        input((program,cfg,pages,resources,root)->{
            long scratch=resources.used(AnalysisResources.Pool.SCRATCH);
            try(var owner=new DependencyAnalysis().prepareManaged(program,cfg,pages,resources);var cursor=owner.openProgram(0);var sibling=owner.openProgram(1);var site=cursor.openSite(0);var candidate=site.openCandidate(0);var support=cursor.openSupport(0,0);var producer=support.producer()){
                long borrowed=pages.allocate();try{
                    program.close();assertEquals(scratch,resources.used(AnalysisResources.Pool.SCRATCH),"GENERAL_PROGRAM_NATIVE_EXPIRY_LEFT_WRAPPERS_RED");
                    assertThrows(IllegalStateException.class,owner::result);assertThrows(IllegalStateException.class,cursor::candidateCount);assertThrows(IllegalStateException.class,sibling::candidateCount);assertThrows(IllegalStateException.class,site::candidateCount);assertThrows(IllegalStateException.class,candidate::rawLength);assertThrows(IllegalStateException.class,producer::domain);
                    byte[] bytes=new byte[1];pages.read(borrowed,0,bytes,0,1);assertEquals(0,bytes[0]);
                }finally{pages.release(borrowed);}
            }
        });
    }
    @Test void wholeGeneralWriterOutputFailurePreservesPrimaryAndClosesTransientProgramsOnly()throws Exception {
        input((program,cfg,pages,resources,root)->{
            try(var owner=new DependencyAnalysis().prepareManaged(program,cfg,pages,resources)){
                long resident=root.heapUsed(),scratch=resources.used(AnalysisResources.Pool.SCRATCH),baseline=pages.statistics().livePages();var primary=new IOException("independent general writer output fault");
                var destination=new OutputStream(){long count;@Override public void write(int value)throws IOException{if(++count>1024)throw primary;}@Override public void write(byte[] value,int at,int length)throws IOException{for(int i=0;i<length;i++)write(value[at+i]);}};
                assertSame(primary,assertThrows(IOException.class,()->new DependencyJson().writeSnapshot(owner,program,resources,destination)));
                assertEquals(scratch,resources.used(AnalysisResources.Pool.SCRATCH));assertEquals(resident,root.heapUsed());assertEquals(baseline,pages.statistics().livePages());assertEquals(4,owner.result().sites().size());assertEquals(0,owner.fullFactProjections());assertNotNull(program.admission().snapshot());
            }
        });
    }
    @Test void firstPreparationGrowthIsBorrowedProgramMetadataAndWarmOwnersRetireExactly()throws Exception {
        input(false,(program,cfg,pages,resources,root)->{
            long beforePages=pages.statistics().livePages(),beforeResident=root.heapUsed();var borrowedAir=program.admission().snapshot();long airRoot=borrowedAir.root();
            long warmPages,warmResident;
            try(var first=new DependencyAnalysis().prepareManaged(program,cfg,pages,resources)){assertEquals(4,first.result().sites().size());assertEquals(0,first.fullFactProjections());}
            warmPages=pages.statistics().livePages();warmResident=root.heapUsed();assertTrue(warmPages>beforePages);assertTrue(warmResident>beforeResident);
            for(int repeat=0;repeat<3;repeat++){
                try(var owner=new DependencyAnalysis().prepareManaged(program,cfg,pages,resources)){assertEquals(4,owner.result().sites().size());assertEquals(0,owner.fullFactProjections());}
                assertEquals(warmPages,pages.statistics().livePages(),"unchanged warm provider has no retained derived-owner pages");assertEquals(warmResident,root.heapUsed());
            }
            program.close();assertTrue(pages.statistics().livePages()<beforePages,"program expiry releases its own first-use metadata");assertTrue(root.heapUsed()<beforeResident);assertEquals(airRoot,borrowedAir.root(),"checked AIR stays borrowed and live");long handle=pages.allocate();pages.release(handle);
            System.out.println("GENERAL_COLD_BASELINE borrowedPages="+(warmPages-beforePages)+" borrowedResident="+(warmResident-beforeResident)+" warmOwners=3_exact programExpiryReleased=true");
        });
    }
    @Test void outputFailureWhileRealWideProducerAndProgramAreStillOpenReleasesEveryTransientScopeOnly()throws Exception {
        var fixture=new SourceCorrelationSnapshotTest();fixture.directory=java.nio.file.Files.createTempDirectory(java.nio.file.Path.of("target"),"native-general-active-output-");
        var original=SourceCorrelationSnapshotTest.program(1);var unit=original.units().getFirst();String producer="seed-"+"x".repeat(100_000);var sequences=new java.util.ArrayList<io.github.gustavo2358.air.model.Sequence>();
        for(var sequence:unit.sequences())sequences.add(sequence.label().localId().equals("start")?new io.github.gustavo2358.air.model.Sequence(sequence.label(),java.util.List.of(ResultFixtures.assign(unit.id(),producer,new io.github.gustavo2358.air.model.Ids.ObjectId(unit.id(),"object-0"),"PROGA   ")),sequence.terminator(),sequence.origin()):sequence);
        var replacement=new io.github.gustavo2358.air.model.Unit(unit.id(),unit.containingUnit(),unit.objects(),unit.visibleObjects(),unit.entries(),sequences,unit.completionPorts(),unit.body(),unit.bodyUnavailable(),unit.coverage(),unit.origin());
        var publication=new io.github.gustavo2358.air.model.Publication(original.id(),original.airVersion(),original.capabilities(),original.artifacts(),java.util.List.of(replacement),original.storage(),original.resources(),original.artifactRelations(),original.origins(),original.coverage(),original.uncertainties(),original.premises());
        try(var context=fixture.new NativeCase(publication,fixture.source(publication),false,Long.MAX_VALUE)){
            var cfg=new io.github.gustavo2358.analysis.cfg.application.CfgBuildCoordinator(io.github.gustavo2358.analysis.cfg.extension.SemanticInterpreterRegistry.empty()).buildChecked(context.program,context.checked,io.github.gustavo2358.analysis.cfg.application.BuildOptions.defaults());assertEquals(CfgBuildResult.Status.CFG_BUILT,cfg.status());
            try(var owner=new DependencyAnalysis().prepareManaged(context.program,cfg,context.pages,context.execution)){
                // This fault law checks the native path against literal expectations. The
                // legacy wide-fact projection exceeds the unchanged SCRATCH limit before
                // output; complete legacy byte comparisons remain in the other laws.
                try(var cursor=owner.openProgram(0)){
                    assertEquals(1,cursor.siteCount());assertEquals(1,cursor.candidateCount());char[] reference=new char[5];cursor.readReference(0,0,reference,0,5);assertEquals("PROGA",new String(reference));
                    try(var support=cursor.openSupport(0,0);var identity=support.producer()){
                        assertEquals("operation",identity.domain());assertEquals(producer.length(),identity.characterCount(PagedDependencyFactStorage.IdentityPart.LOCAL));char[] block=new char[256];
                        for(int at=0;at<producer.length();at+=Math.min(block.length,producer.length()-at)){int count=Math.min(block.length,producer.length()-at);identity.readCharacters(PagedDependencyFactStorage.IdentityPart.LOCAL,at,block,0,count);assertEquals(producer.substring(at,at+count),new String(block,0,count));}
                    }
                }
                long resident=context.execution.used(AnalysisResources.Pool.RESIDENT),scratch=context.execution.used(AnalysisResources.Pool.SCRATCH),pages=context.pages.statistics().livePages();boolean[] active={false};var primary=new IOException("independent active native general output failure");
                var destination=new OutputStream(){private void fail()throws IOException{
                    assertTrue(context.execution.used(AnalysisResources.Pool.RESIDENT)-resident>=65536+4096+34176,"program controls must still be funded,beside the fixed root UTF8 reservation");assertTrue(context.execution.used(AnalysisResources.Pool.SCRATCH)>scratch,"producer identity/support must still be open at the failing flush");active[0]=true;throw primary;
                }@Override public void write(int value)throws IOException{fail();}@Override public void write(byte[] value,int at,int count)throws IOException{fail();}};
                assertSame(primary,assertThrows(IOException.class,()->new DependencyJson().writeSnapshot(owner,context.program,context.execution,destination)));assertTrue(active[0]);
                assertEquals(resident,context.execution.used(AnalysisResources.Pool.RESIDENT));assertEquals(scratch,context.execution.used(AnalysisResources.Pool.SCRATCH));assertEquals(pages,context.pages.statistics().livePages());assertEquals(1,owner.result().sites().size());assertEquals(0,owner.fullFactProjections());assertNotNull(context.program.admission().snapshot());assertTrue(context.source.unitCount()>0);
                System.out.println("GENERAL_ACTIVE_OUTPUT_FAULT realProducerChars="+producer.length()+" programAndSupportActive=true transientBaselineRestored=true fullFact=0");
            }
        }
    }
}
