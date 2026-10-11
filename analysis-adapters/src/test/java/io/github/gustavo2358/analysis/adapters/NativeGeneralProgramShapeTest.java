package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.analysis.cfg.application.CfgBuildResult;
import io.github.gustavo2358.analysis.dependencies.*;
import io.github.gustavo2358.analysis.query.ProgramPoint;
import io.github.gustavo2358.analysis.solver.*;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Caller-staged shape adversaries, not general-provider/root qualification. */
@SuppressWarnings("try")
final class NativeGeneralProgramShapeTest {
    @FunctionalInterface private interface Work {void run(SnapshotProgram program,CfgBuildResult cfg,PageStore pages,AnalysisResources execution,AnalysisResources root)throws Exception;}
    private static void input(Work action)throws Exception {
        var contract=Class.forName(NativeGeneralProgramPressureTest.class.getName()+"$Work");var work=Proxy.newProxyInstance(contract.getClassLoader(),new Class<?>[]{contract},(proxy,method,args)->{action.run((SnapshotProgram)args[0],(CfgBuildResult)args[1],(PageStore)args[2],(AnalysisResources)args[3],(AnalysisResources)args[4]);return null;});
        var method=NativeGeneralProgramPressureTest.class.getDeclaredMethod("nativeInput",contract);method.setAccessible(true);invoke(method,null,work);
    }
    private static Object invoke(Method method,Object receiver,Object... args)throws Exception {
        try{return method.invoke(receiver,args);}catch(InvocationTargetException failure){if(failure.getCause() instanceof Exception e)throw e;if(failure.getCause() instanceof Error e)throw e;throw failure;}
    }
    private static DependencySiteFact candidates(DependencySiteFact fact,List<DependencySiteFact.Candidate> values)throws Exception {
        var method=NativeGeneralProgramPressureTest.class.getDeclaredMethod("candidates",DependencySiteFact.class,List.class);method.setAccessible(true);return (DependencySiteFact)invoke(method,null,fact,values);
    }
    private static ManagedExecutableDependencyResult staged(SnapshotProgram program,PageStore pages,AnalysisResources resources,DependencyResult base,DependencySiteFact fact,QualifiedDependencyOccurrence occurrence)throws Exception {
        var method=NativeGeneralProgramPressureTest.class.getDeclaredMethod("staged",SnapshotProgram.class,PageStore.class,AnalysisResources.class,DependencyResult.class,DependencySiteFact.class,QualifiedDependencyOccurrence.class);method.setAccessible(true);return (ManagedExecutableDependencyResult)invoke(method,null,program,pages,resources,base,fact,occurrence);
    }
    private static byte[] legacy(TargetResolver.Resolution resolution)throws Exception {
        var method=NativeGeneralProgramPressureTest.class.getDeclaredMethod("legacy",TargetResolver.Resolution.class);method.setAccessible(true);return (byte[])invoke(method,null,resolution);
    }
    private static byte[] encoded(ManagedExecutableDependencyResult.ProgramCursor cursor,PageStore pages,AnalysisResources resources)throws Exception {
        try(var funding=resources.reserve(AnalysisResources.Pool.RESIDENT,65536+4096,AnalysisResources.Phase.ENCODE)){
            assertEquals(65536+4096,funding.amount());var bytes=new ByteArrayOutputStream();var out=new JsonOutput(bytes);new DependencyJson.FactWriter(out,pages,resources).program(cursor);out.finish();return bytes.toByteArray();
        }
    }
    private static String text(ManagedExecutableDependencyResult.ProgramCursor cursor,long ordinal,boolean raw){
        int count=Math.toIntExact(raw?cursor.rawLength(ordinal):cursor.referenceLength(ordinal));char[] value=new char[count];for(int at=0;at<count;at+=Math.min(7,count-at)){int length=Math.min(7,count-at);if(raw)cursor.readRaw(ordinal,at,value,at,length);else cursor.readReference(ordinal,at,value,at,length);}return new String(value);
    }
    private static String local(PagedDependencyFactStorage.IdentityView identity){
        int count=Math.toIntExact(identity.characterCount(PagedDependencyFactStorage.IdentityPart.LOCAL));char[] value=new char[count];for(int at=0;at<count;at+=Math.min(7,count-at))identity.readCharacters(PagedDependencyFactStorage.IdentityPart.LOCAL,at,value,at,Math.min(7,count-at));return new String(value);
    }
    @Test void fiveUtf16WidthsKeepLegacyLengthPrefixedKeysFullRawAndWideProofIdentityCold()throws Exception {
        input((program,cfg,pages,resources,root)->{
            var base=new DependencyAnalysis().prepareLeased(program,cfg,pages,resources);var original=base.sites().getFirst();var occurrence=base.programDependencies().getFirst().occurrence();long fixed=-1;
            for(int width:new int[]{64,256,1024,4096,16384}){
                String suffix="\u03c0\u0000\uFFFF\uD83D\uDE42".repeat(width);var publication=new PublicationId("wide-publication"+suffix);var unit=new UnitId(publication,"wide-unit"+suffix);var origin=new OriginId(publication,"wide-origin"+suffix);var support=new DependencySiteFact.Support(DependencySiteFact.SupportKind.VALUE_PRODUCER,new OperandId(new OperationOwner(new OperationId(unit,"wide-operation"+suffix)),"operand"+suffix),origin,List.of(new PremiseId(publication,"Aa"+suffix),new PremiseId(publication,"BB"+suffix),new PremiseId(publication,"Aa"+suffix)));
                var values=List.of(new DependencySiteFact.Candidate("BB","raw-BB"+suffix,List.of(support)),new DependencySiteFact.Candidate("Aa","raw-Aa"+suffix,List.of(support)),new DependencySiteFact.Candidate("ABCDEFGHIJ","raw-long"+suffix,List.of(support)),new DependencySiteFact.Candidate("Aa"+suffix,"wide-raw"+suffix,List.of(support)));
                var ordered=new ArrayList<>(values);ordered.sort(Comparator.comparing(v->v.referenceName().length()+":"+v.referenceName()+v.rawValue()));assertEquals("ABCDEFGHIJ",ordered.getFirst().referenceName(),"decimal length prefix is normative, not reference-name order");
                var fact=candidates(original,values);byte[] expected=legacy(TargetResolver.resolve(occurrence,List.of(fact)));
                try(var owner=staged(program,pages,resources,base,fact,occurrence)){
                    long resident=resources.used(AnalysisResources.Pool.RESIDENT),scratch=resources.used(AnalysisResources.Pool.SCRATCH);
                    try(var cursor=owner.openProgram(0)){
                        long delta=resources.used(AnalysisResources.Pool.RESIDENT)-resident;if(fixed<0)fixed=delta;assertEquals(fixed,delta);assertEquals(4,cursor.candidateCount());
                        for(int i=ordered.size()-1;i>=0;i--){assertEquals(ordered.get(i).referenceName(),text(cursor,i,false));assertEquals(ordered.get(i).rawValue(),text(cursor,i,true));try(var proof=cursor.openSupport(i,0);var producer=proof.producer()){assertEquals("operand",producer.domain());assertEquals(support.producer().localId(),local(producer));assertEquals(3,proof.premiseCount());for(int p=2;p>=0;p--)try(var premise=proof.premise(p)){assertEquals(support.premises().get(p).localId(),local(premise));}}}
                        assertArrayEquals(expected,encoded(cursor,pages,resources));assertEquals(0,owner.fullFactProjections());
                    }assertEquals(resident,resources.used(AnalysisResources.Pool.RESIDENT));assertEquals(scratch,resources.used(AnalysisResources.Pool.SCRATCH));System.out.println("GENERAL_PROGRAM_UTF16 width="+width+" fixedResident="+fixed);
                }
            }
        });
    }
    @Test void fiveSupportCountsPreserveSingleContributionDuplicatesButMergedFullIdentityUnionAndStableWireTies()throws Exception {
        input((program,cfg,pages,resources,root)->{
            var base=new DependencyAnalysis().prepareLeased(program,cfg,pages,resources);var original=base.sites().getFirst();var prior=base.programDependencies().getFirst().occurrence();long fixed=-1;
            for(int count:new int[]{64,256,1024,4096,16384}){
                var proofs=new ArrayList<DependencySiteFact.Support>();var publication=original.operation().publication();var origin=original.siteOrigin();
                var premises=List.of(new PremiseId(publication,"BB"),new PremiseId(publication,"Aa"),new PremiseId(publication,"BB"));
                for(int i=count-1;i>=0;i--)proofs.add(new DependencySiteFact.Support(DependencySiteFact.SupportKind.VALUE_PRODUCER,new OperationId(original.caller(),String.format("producer-%05d",i)),origin,premises));
                var same=proofs.getFirst();proofs.add(same);proofs.add(new DependencySiteFact.Support(DependencySiteFact.SupportKind.CICS_LITERAL,same.producer(),same.origin(),same.premises()));proofs.add(new DependencySiteFact.Support(DependencySiteFact.SupportKind.VALUE_PRODUCER,same.producer(),same.origin(),List.of(premises.get(1),premises.getFirst(),premises.get(1))));
                var fact=candidates(original,List.of(new DependencySiteFact.Candidate("PG1","PG1",proofs)));
                for(boolean merged:new boolean[]{false,true}){
                    var occurrence=new QualifiedDependencyOccurrence(prior.source(),prior.caller(),prior.technology(),prior.nameProfile(),prior.targetKind(),prior.literals(),prior.qualifications(),merged?List.of(original.operation(),original.operation()):List.of(original.operation()),prior.sourceValueRemainder(),prior.sourceControlRemainder());
                    var expectedProofs=merged?new ArrayList<>(new LinkedHashSet<>(proofs)):new ArrayList<>(proofs);expectedProofs.sort(Comparator.comparing(DependencySiteFact.Support::producer,WireIds.ORDER).thenComparing(DependencySiteFact.Support::origin,WireIds.ORDER));assertEquals(count+(merged?2:3),expectedProofs.size());
                    byte[] expected=legacy(TargetResolver.resolve(occurrence,merged?List.of(fact,fact):List.of(fact)));
                    try(var owner=staged(program,pages,resources,base,fact,occurrence)){
                        long resident=resources.used(AnalysisResources.Pool.RESIDENT),scratch=resources.used(AnalysisResources.Pool.SCRATCH);
                        try(var cursor=owner.openProgram(0)){
                            long delta=resources.used(AnalysisResources.Pool.RESIDENT)-resident;if(fixed<0)fixed=delta;assertEquals(fixed,delta);assertEquals(expectedProofs.size(),cursor.supportCount(0));assertEquals(merged?2:1,cursor.siteCount());
                            for(int i=expectedProofs.size()-1;i>=0;i--)try(var support=cursor.openSupport(0,i);var producer=support.producer()){
                                var want=expectedProofs.get(i);assertEquals(want.kind(),support.kind());assertEquals(want.producer().localId(),local(producer));assertEquals(want.premises().size(),support.premiseCount());for(int p=want.premises().size()-1;p>=0;p--)try(var premise=support.premise(p)){assertEquals(want.premises().get(p).localId(),local(premise));}
                            }assertArrayEquals(expected,encoded(cursor,pages,resources));assertEquals(0,owner.fullFactProjections());
                        }assertEquals(resident,resources.used(AnalysisResources.Pool.RESIDENT));assertEquals(scratch,resources.used(AnalysisResources.Pool.SCRATCH));System.out.println("GENERAL_PROGRAM_SUPPORTS count="+count+" merged="+merged+" fixedResident="+fixed);
                    }
                }
            }
        });
    }
    @Test void fivePremiseCountsRetainEveryCollidingDuplicateOrdinalWithoutGrowingLiveScopes()throws Exception {
        input((program,cfg,pages,resources,root)->{
            var base=new DependencyAnalysis().prepareLeased(program,cfg,pages,resources);var original=base.sites().getFirst();var occurrence=base.programDependencies().getFirst().occurrence();long fixed=-1;
            for(int count:new int[]{64,256,1024,4096,16384}){
                var premises=new ArrayList<PremiseId>();for(int i=0;i<count;i++)premises.add(new PremiseId(original.caller().publication(),i%3==0?"BB":i%3==1?"Aa":"\u03c0"));assertEquals("BB".hashCode(),"Aa".hashCode());
                var proof=new DependencySiteFact.Support(DependencySiteFact.SupportKind.VALUE_PRODUCER,original.operation(),original.siteOrigin(),premises);var fact=candidates(original,List.of(new DependencySiteFact.Candidate("PG1","PG1",List.of(proof,proof))));byte[] expected=legacy(TargetResolver.resolve(occurrence,List.of(fact)));
                try(var owner=staged(program,pages,resources,base,fact,occurrence)){
                    long resident=resources.used(AnalysisResources.Pool.RESIDENT),scratch=resources.used(AnalysisResources.Pool.SCRATCH);
                    try(var cursor=owner.openProgram(0)){
                        long delta=resources.used(AnalysisResources.Pool.RESIDENT)-resident;if(fixed<0)fixed=delta;assertEquals(fixed,delta);assertEquals(2,cursor.supportCount(0));
                        for(int s=1;s>=0;s--)try(var support=cursor.openSupport(0,s)){
                            assertEquals(count,support.premiseCount());long baseline=resources.used(AnalysisResources.Pool.SCRATCH);
                            for(int p=count-1;p>=0;p--){try(var premise=support.premise(p)){assertEquals(premises.get(p).localId(),local(premise));assertTrue(resources.used(AnalysisResources.Pool.SCRATCH)-scratch<=8192);}assertEquals(baseline,resources.used(AnalysisResources.Pool.SCRATCH));}
                        }assertArrayEquals(expected,encoded(cursor,pages,resources));assertEquals(0,owner.fullFactProjections());
                    }assertEquals(resident,resources.used(AnalysisResources.Pool.RESIDENT));assertEquals(scratch,resources.used(AnalysisResources.Pool.SCRATCH));System.out.println("GENERAL_PROGRAM_PREMISES count="+count+" fixedResident="+fixed);
                }
            }
        });
    }
    @Test void allSeventeenSealedProducerKindsRemainReadableAndWireAdmissionStillRejectsTheOriginalUnsupportedKinds()throws Exception {
        input((program,cfg,pages,resources,root)->{
            var base=new DependencyAnalysis().prepareLeased(program,cfg,pages,resources);var original=base.sites().getFirst();var occurrence=base.programDependencies().getFirst().occurrence();var method=PagedDependencyFactStorageTest.class.getDeclaredMethod("fact",int.class);method.setAccessible(true);var all=(DependencySiteFact)invoke(method,null,0);var proofs=all.candidates().getFirst().supports();assertEquals(17,proofs.size());
            var fact=candidates(original,List.of(new DependencySiteFact.Candidate("PG1","PG1",proofs)));var resolution=TargetResolver.resolve(occurrence,List.of(fact));var expected=assertThrows(IllegalArgumentException.class,()->legacy(resolution));
            try(var owner=staged(program,pages,resources,base,fact,occurrence)){
                long resident=resources.used(AnalysisResources.Pool.RESIDENT),scratch=resources.used(AnalysisResources.Pool.SCRATCH);
                try(var cursor=owner.openProgram(0)){
                    assertEquals(17,cursor.supportCount(0));var seen=new HashSet<String>();for(int i=0;i<17;i++)try(var support=cursor.openSupport(0,i);var producer=support.producer()){seen.add(producer.domain());assertFalse(local(producer).isEmpty());}assertEquals(15,seen.size(),"operation-owner and entry-owner operand variants share one domain");
                    var failure=assertThrows(IllegalArgumentException.class,()->encoded(cursor,pages,resources));assertEquals(expected.getMessage(),failure.getMessage());assertEquals(17,cursor.supportCount(0));assertEquals(0,owner.fullFactProjections());
                }assertEquals(resident,resources.used(AnalysisResources.Pool.RESIDENT));assertEquals(scratch,resources.used(AnalysisResources.Pool.SCRATCH));
            }
        });
    }
    @Test void everyGeneralReferenceRawAndReasonRejectsUnpairedUnicodeExactlyLikeLegacyWithoutExpiringItsOwner()throws Exception {
        input((program,cfg,pages,resources,root)->{
            var base=new DependencyAnalysis().prepareLeased(program,cfg,pages,resources);var original=base.sites().getFirst();var occurrence=base.programDependencies().getFirst().occurrence();
            for(String invalid:List.of("\uD800","\uDC00","a".repeat(127)+"\uD800x","a".repeat(127)+"\uDC00","a".repeat(255)+"\uD800"))for(int field=0;field<3;field++){
                var value=new DependencySiteFact.Candidate(field==0?invalid:"PG1",field==1?invalid:"PG1",original.candidates().getFirst().supports());var fact=candidates(original,List.of(value));if(field==2)fact=fact.withPartialAnalysis(invalid);
                var resolution=TargetResolver.resolve(occurrence,List.of(fact));var expected=assertThrows(IllegalArgumentException.class,()->legacy(resolution));
                try(var owner=staged(program,pages,resources,base,fact,occurrence)){
                    long resident=resources.used(AnalysisResources.Pool.RESIDENT),scratch=resources.used(AnalysisResources.Pool.SCRATCH);
                    try(var cursor=owner.openProgram(0)){
                        var failure=assertThrows(IllegalArgumentException.class,()->encoded(cursor,pages,resources));assertEquals(expected.getMessage(),failure.getMessage());assertEquals(1,cursor.candidateCount());assertEquals(0,owner.fullFactProjections());
                    }assertEquals(resident,resources.used(AnalysisResources.Pool.RESIDENT));assertEquals(scratch,resources.used(AnalysisResources.Pool.SCRATCH));
                }
            }
        });
    }
    private static ManagedExecutableDependencyResult stagedSites(SnapshotProgram program,PageStore pages,AnalysisResources resources,DependencyResult base,List<DependencySiteFact> facts,QualifiedDependencyOccurrence occurrence)throws Exception {
        var constructor=ManagedExecutableDependencyResult.class.getDeclaredConstructor(SnapshotProgram.class,PageStore.class,AnalysisResources.class);constructor.setAccessible(true);var owner=constructor.newInstance(program,pages,resources);
        try{
            var field=ManagedExecutableDependencyResult.class.getDeclaredField("facts");field.setAccessible(true);var storage=(PagedDependencyFactStorage)field.get(owner);for(var fact:facts)storage.append(fact);
            var index=ManagedExecutableDependencyResult.class.getDeclaredMethod("indexPrepared");index.setAccessible(true);invoke(index,owner);
            var finish=ManagedExecutableDependencyResult.class.getDeclaredMethod("finishIndex",java.util.function.ToIntFunction.class);finish.setAccessible(true);invoke(finish,owner,(java.util.function.ToIntFunction<Object>)h->0);
            var bind=NativeGeneralProgramCursorTest.class.getDeclaredMethod("bind",ManagedExecutableDependencyResult.class,DependencyResult.class,QualifiedDependencyOccurrence.class);bind.setAccessible(true);invoke(bind,null,owner,base,occurrence);return owner;
        }catch(Exception|Error failure){owner.close();throw failure;}
    }
    private static DependencySiteFact inEntry(DependencySiteFact f,EntryId entry){
        var point=f.valuePoint();var copied=point==null?null:new ProgramPoint(entry,point.kind(),point.operation(),point.outcome());
        return new DependencySiteFact(f.caller(),entry,f.sequence(),f.operation(),f.offset(),f.siteOrigin(),f.targetOrigin(),f.technology(),f.command(),f.namespace(),f.nameProfile(),f.targetKind(),f.subject(),copied,f.reachability(),f.targetStatus(),f.rawCandidates(),f.candidates(),f.modelValueRemainder(),f.sourceValueRemainder(),f.interpretationUnknownRemainder(),f.effectiveUnknownRemainder(),f.openControlRemainder(),f.evidence(),f.provenance(),f.premises(),f.uncertaintyRefs(),f.analysisStatus(),f.analysisReasons());
    }
    @Test void fiveCallerStagedEntrySiteCountsKeepWholeProgramUnionAndEverySiteOrdinalWithFixedResidentControls()throws Exception {
        input((program,cfg,pages,resources,root)->{
            var base=new DependencyAnalysis().prepareLeased(program,cfg,pages,resources);var original=base.sites().getFirst();var occurrence=base.programDependencies().getFirst().occurrence();long fixed=-1;
            for(int count:new int[]{64,256,1024,4096,16384}){
                // Typed caller-owned synthetic Entry result rows, not AIR entry admission
                // or general-provider production. Keeps original actual4096 admission
                // frontier separate; does not replace or shrink that experiment.
                var facts=new ArrayList<DependencySiteFact>();for(int i=count-1;i>=0;i--)facts.add(inEntry(original,new EntryId(original.caller(),String.format("entry-%05d",i))));
                var ordered=new ArrayList<>(facts);ordered.sort(Comparator.comparing(f->f.entry().localId()));byte[] expected=legacy(TargetResolver.resolve(occurrence,ordered));
                try(var owner=stagedSites(program,pages,resources,base,facts,occurrence)){
                    long resident=resources.used(AnalysisResources.Pool.RESIDENT),scratch=resources.used(AnalysisResources.Pool.SCRATCH);
                    try(var cursor=owner.openProgram(0)){
                        long delta=resources.used(AnalysisResources.Pool.RESIDENT)-resident;if(fixed<0)fixed=delta;assertEquals(fixed,delta);assertEquals(count,cursor.siteCount());assertEquals(1,cursor.candidateCount());assertEquals(1,cursor.supportCount(0),"same proof from different entries is full-identity unioned");assertEquals("PROGA",text(cursor,0,false));
                        for(int i=count-1;i>=0;i--)try(var site=cursor.openSite(i);var entry=site.entry();var operation=site.operation()){
                            assertEquals(String.format("entry-%05d",i),local(entry));assertEquals(original.operation().localId(),local(operation));assertEquals(original.reachability(),site.reachability());
                        }assertArrayEquals(expected,encoded(cursor,pages,resources));assertEquals(0,owner.fullFactProjections());
                    }assertEquals(resident,resources.used(AnalysisResources.Pool.RESIDENT));assertEquals(scratch,resources.used(AnalysisResources.Pool.SCRATCH));System.out.println("GENERAL_PROGRAM_STAGED_SITES count="+count+" fixedResident="+fixed);
                }
            }
        });
    }
}
