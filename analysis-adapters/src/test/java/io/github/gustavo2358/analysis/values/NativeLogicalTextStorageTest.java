package io.github.gustavo2358.analysis.values;

import io.github.gustavo2358.analysis.adapters.DataflowAirReader;
import io.github.gustavo2358.analysis.adapters.FilePageStore;
import io.github.gustavo2358.analysis.adapters.PagedSnapshotOrderStorage;
import io.github.gustavo2358.analysis.dependencies.SnapshotProgram;
import io.github.gustavo2358.analysis.solver.AnalysisResources;
import io.github.gustavo2358.analysis.cfg.application.BuildOptions;
import io.github.gustavo2358.analysis.cfg.application.CfgBuildCoordinator;
import io.github.gustavo2358.analysis.cfg.extension.SemanticInterpreterRegistry;
import io.github.gustavo2358.analysis.structure.AnalysisSession;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Real native admission and the same paged columns/ledger used by the CLI. */
final class NativeLogicalTextStorageTest {
    @TempDir Path directory;
    private static final Path INPUT=Path.of("src/test/resources/cp6/dynamic-x8.air.json");
    private static AnalysisResources resources(){return new AnalysisResources(new AnalysisResources.Limits(64L*1024*1024,16L*1024*1024,0,256L*1024*1024,8,Long.MAX_VALUE,Long.MAX_VALUE));}
    private static void released(AnalysisResources resources){for(var pool:AnalysisResources.Pool.values())assertEquals(0,resources.used(pool),pool.toString());}
    @Test void nativeCanonicalRunsMatchDenseUnicodeAndResidentHashOracles() throws Exception {
        var resources=resources();
        try(var input=new DataflowAirReader().readSnapshot(INPUT,resources);
            var program=new SnapshotProgram(input.checked(),input.newIdentityStorage(),input.newOrderStorage(),resources);
            var texts=new LogicalTextArena(program)) {
            for(String source:List.of("","aaaa","a😀😀βbb","ABCD","😀","\u0000\ud800A\udfff")) {
                int[] points=source.codePoints().toArray();
                for(int width=0;width<12;width++) {
                    int[] dense=java.util.Arrays.copyOf(points,width);if(width>points.length)java.util.Arrays.fill(dense,points.length,width,' ');
                    var fitted=texts.of(source).fit(width,' ');String expected=new String(dense,0,dense.length);
                    assertEquals(expected,fitted.text());assertEquals(LogicalText.of(expected),fitted);assertEquals(LogicalText.of(expected).hashCode(),fitted.hashCode());
                    for(int start=0;start<=width;start++)for(int size=0;size<=width-start;size++) {
                        var slice=fitted.slice(start,size);String anticipated=new String(dense,start,size);
                        assertEquals(anticipated,slice.text());assertEquals(LogicalText.of(anticipated),slice);assertEquals(LogicalText.of(anticipated).hashCode(),slice.hashCode());
                    }
                }
            }
            var joined=texts.of("A😀").concat(texts.of("😀β")).concat(LogicalText.of("βA"));
            assertEquals("A😀😀ββA",joined.text());assertEquals(4,joined.runCount());
            assertEquals(LogicalText.of(joined.text()).hashCode(),joined.hashCode());
            assertThrows(UnsupportedOperationException.class,()->joined.runs().set(0,new LogicalText.Run('X',1)));
        }
        released(resources);
    }
    @Test void geometricNonRepeatingTextsHaveOnePagedWordPerRunAndBoundedResidentControls() throws Exception {
        var resources=resources();
        try(var input=new DataflowAirReader().readSnapshot(INPUT,resources);
            var pages=new FilePageStore(directory,4096,2,resources);
            var program=new SnapshotProgram(input.checked(),input.newIdentityStorage(),new PagedSnapshotOrderStorage(pages,resources),resources);
            var texts=new LogicalTextArena(program)) {
            long before=resources.heapUsed(),temporary=resources.used(AnalysisResources.Pool.TEMPORARY);
            var initial=pages.statistics();
            int count=0;
            for(int width:new int[]{4096,8192,16384,32768,65536}) {
                long rows=texts.rowsWritten();var value=texts.of("AB".repeat(width/2));count++;
                assertEquals(width,value.length());assertEquals(width,value.runCount());assertEquals(width,texts.rowsWritten()-rows);
                assertEquals("ABABABAB",value.slice(width-8,8).text());count++;
                // Only fixed headers/views accumulate. All payload/directory pages
                // live in the already funded file cache, not an array of Run objects.
                assertEquals(before+96L*count,resources.heapUsed());
                System.out.println("NATIVE_TEXT_RUN_CURVE width="+width+" storedWords="+(texts.rowsWritten()-rows)+" residentHeaderGrowth="+(resources.heapUsed()-before)+" temporaryGrowth="+(resources.used(AnalysisResources.Pool.TEMPORARY)-temporary)+" evictions="+(pages.statistics().evictions()-initial.evictions())+" bytesRead="+(pages.statistics().bytesRead()-initial.bytesRead())+" bytesWritten="+(pages.statistics().bytesWritten()-initial.bytesWritten()));
            }
            assertTrue((pages.statistics().livePages()-initial.livePages())*pages.pageBytes()>=8L*texts.rowsWritten(),"all canonical run words must live in paged payloads");
            assertTrue(pages.statistics().evictions()-initial.evictions()>100,"two-page native order cache must force actual eviction");
            assertTrue(pages.statistics().bytesWritten()-initial.bytesWritten()>8L*65536,"required text payload must really be written, not only assigned a nominal file handle");
        }
        released(resources);
    }
    @Test void billionExtentPaddingAndUnknownProjectionKeepCanonicalCompressedRuns() throws Exception {
        var resources=resources();
        try(var input=new DataflowAirReader().readSnapshot(INPUT,resources);
            var program=new SnapshotProgram(input.checked(),input.newIdentityStorage(),input.newOrderStorage(),resources);
            var texts=new LogicalTextArena(program)) {
            var padded=texts.of("A").fit(1_000_000_000,' ');
            assertEquals(2,padded.runCount());assertEquals("    ",padded.slice(999_999_996,4).text());
            assertEquals(LogicalText.of("A ").fit(1_000_000_000,' '),padded);
            assertEquals(LogicalText.of("A ").fit(1_000_000_000,' ').hashCode(),padded.hashCode());
            assertEquals(1,texts.of("😀").fill(1_000_000_000).runCount());
            var partial=LogicalText.unknown(4).concat(texts.of("PROGA   "));
            assertFalse(partial.complete());assertEquals("PROGA   ",partial.slice(4,8).text());assertThrows(IllegalStateException.class,partial::text);
        }
        released(resources);
    }
    @Test void textCapacityIsCommonAndExplicitOrOwnerCloseExpiresRunsWithoutClosingInput() throws Exception {
        var resources=resources();
        try(var input=new DataflowAirReader().readSnapshot(INPUT,resources)) {
            var snapshot=input.checked().snapshot();long root=snapshot.root();var shape=snapshot.shape(root);long inputHeap=resources.heapUsed();
            var program=new SnapshotProgram(input.checked(),input.newIdentityStorage(),input.newOrderStorage(),resources);
            try {
                program.storageInventory();long before=resources.heapUsed();
                var texts=new LogicalTextArena(program);var value=texts.of("AB".repeat(4096));
                assertTrue(resources.heapUsed()>=before+256+128+96,"native text headers and workspace must use admitted capacity");
                texts.close();texts.close();assertEquals(before,resources.heapUsed());assertThrows(IllegalStateException.class,value::text);
                var expired=new LogicalTextArena(program);var old=expired.of("😀AB");program.close();
                assertThrows(IllegalStateException.class,old::text);expired.close();assertEquals(inputHeap,resources.heapUsed());assertEquals(shape,snapshot.shape(root));
            } finally{program.close();}
        }
        released(resources);
    }
    @Test void deniedTextHeaderCapacityCannotPublishOrPartiallyChargeNativeResult() throws Exception {
        var resources=resources();
        try(var input=new DataflowAirReader().readSnapshot(INPUT,resources);
            var program=new SnapshotProgram(input.checked(),input.newIdentityStorage(),input.newOrderStorage(),resources);
            var texts=new LogicalTextArena(program)) {
            var previous=texts.of("A");long before=resources.heapUsed();
            try(var pressure=resources.reserve(AnalysisResources.Pool.RESIDENT,resources.limits().heapBytes()-before,AnalysisResources.Phase.DOMAIN)) {
                assertEquals(resources.limits().heapBytes()-before,pressure.amount());
                var failure=assertThrows(AnalysisResources.Exhausted.class,()->texts.of("B"));
                assertEquals(AnalysisResources.Resource.HEAP,failure.resource());assertEquals(AnalysisResources.Phase.DOMAIN,failure.phase());assertEquals(96,failure.requested());
                assertEquals(resources.limits().heapBytes(),resources.heapUsed());assertEquals("A",previous.text());
            }
            assertEquals(before,resources.heapUsed());
        }
        released(resources);
    }
    @Test void closedRegionalExecutionsReleaseTextRootsOnExplicitOrNativeOwnerClose() throws Exception {
        for(boolean ownerExpiry:new boolean[]{false,true}) {
            var resources=resources();
            try(var input=new DataflowAirReader().readSnapshot(INPUT,resources)) {
                var program=new SnapshotProgram(input.checked(),input.newIdentityStorage(),input.newOrderStorage(),resources);
                try {
                var options=BuildOptions.defaults();
                var cfg=new CfgBuildCoordinator(SemanticInterpreterRegistry.empty()).buildChecked(program,input.checked(),options);
                var entry=program.units().getFirst().entries().getFirst();
                var session=AnalysisSession.open(cfg,program,options.projectionPolicy(),List.of(entry.id())).session().orElseThrow();
                var analysis=RegionalValuesAnalysis.prepare(session,StorageAnalysisMode.EXPERIMENTAL_PHYSICAL).analysis().orElseThrow();
                var execution=analysis.execute();try {
                    assertTrue(strongLogicalTexts(execution)>0,"audit must see real executed texts before closure");
                    var metrics=execution.metrics();var solveMetrics=execution.solveMetrics();
                    if(ownerExpiry)program.close();else execution.close();
                    assertEquals(0,strongLogicalTexts(execution),"retaining a closed execution must not retain text/interner roots after returning their capacity");
                    assertEquals(metrics,execution.metrics());assertEquals(solveMetrics,execution.solveMetrics());
                    assertThrows(IllegalStateException.class,()->execution.observe(List.of()));
                    execution.close();
                } finally{execution.close();}
                } finally{program.close();}
            }
            released(resources);
        }
    }
    /** Walk stored strong project fields, not lazy input views or weak observers. */
    @Test void scalarPreparedOwnershipAndTwoLiveExecutionsShareNativeTextLifetime() throws Exception {
        var resources=resources();
        try(var input=new DataflowAirReader().readSnapshot(INPUT,resources);
            var program=new SnapshotProgram(input.checked(),input.newIdentityStorage(),input.newOrderStorage(),resources)) {
            var options=BuildOptions.defaults();var cfg=new CfgBuildCoordinator(SemanticInterpreterRegistry.empty()).buildChecked(program,input.checked(),options);
            var entry=program.units().getFirst().entries().getFirst();
            var session=AnalysisSession.open(cfg,program,options.projectionPolicy(),List.of(entry.id())).session().orElseThrow();
            long before=resources.heapUsed();
            var analysis=PossibleValuesAnalysis.prepare(session,PossibleValuesAnalysis.EFFECTS_PROFILE).analysis().orElseThrow();
            try {
                assertTrue(resources.heapUsed()>=before+896,"preparation must reserve its native text/control capacity from the common ledger");
                var first=analysis.execute();var second=analysis.execute();
                try {
                    assertTrue(strongLogicalTexts(first)>0);assertTrue(strongLogicalTexts(second)>0);
                    var metrics=second.preparationMetrics();analysis.close();
                    assertThrows(IllegalStateException.class,analysis::execute);
                    first.close();assertEquals(0,strongLogicalTexts(first));
                    assertTrue(strongLogicalTexts(second)>0,"closing preparation and another execution must not invalidate a live run");
                    assertEquals(io.github.gustavo2358.analysis.query.ObservationBatch.Status.COMPLETE,second.observe(List.of()).batch().status());
                    second.close();assertEquals(0,strongLogicalTexts(second));assertEquals(metrics,second.preparationMetrics());
                    assertThrows(IllegalStateException.class,()->second.observe(List.of()));
                } finally{first.close();second.close();}
            } finally{analysis.close();}
        }
        released(resources);
    }
    @Test void scalarNativeOwnerExpiryClearsPreparedTextInternersAndRejectsReplay() throws Exception {
        var resources=resources();
        try(var input=new DataflowAirReader().readSnapshot(INPUT,resources)) {
            var program=new SnapshotProgram(input.checked(),input.newIdentityStorage(),input.newOrderStorage(),resources);
            try {
                var options=BuildOptions.defaults();var cfg=new CfgBuildCoordinator(SemanticInterpreterRegistry.empty()).buildChecked(program,input.checked(),options);
                var entry=program.units().getFirst().entries().getFirst();
                var session=AnalysisSession.open(cfg,program,options.projectionPolicy(),List.of(entry.id())).session().orElseThrow();
                var analysis=PossibleValuesAnalysis.prepare(session,PossibleValuesAnalysis.EFFECTS_PROFILE).analysis().orElseThrow();
                try {
                    var execution=analysis.execute();try {
                        assertTrue(strongLogicalTexts(analysis)>0);assertTrue(strongLogicalTexts(execution)>0);
                        var metrics=execution.preparationMetrics();program.close();
                        assertEquals(0,strongLogicalTexts(analysis));assertEquals(0,strongLogicalTexts(execution));
                        assertThrows(IllegalStateException.class,analysis::execute);assertThrows(IllegalStateException.class,()->execution.observe(List.of()));
                        assertEquals(metrics,execution.preparationMetrics());
                    } finally{execution.close();}
                } finally{analysis.close();}
            } finally{program.close();}
        }
        released(resources);
    }
    @Test void scalarPreparationChargesCommonCapacityAndAtomicRefusalReleasesPartialOwners() throws Exception {
        var resources=resources();
        try(var input=new DataflowAirReader().readSnapshot(INPUT,resources);
            var program=new SnapshotProgram(input.checked(),input.newIdentityStorage(),input.newOrderStorage(),resources)) {
            var options=BuildOptions.defaults();var cfg=new CfgBuildCoordinator(SemanticInterpreterRegistry.empty()).buildChecked(program,input.checked(),options);
            var entry=program.units().getFirst().entries().getFirst();
            var session=AnalysisSession.open(cfg,program,options.projectionPolicy(),List.of(entry.id())).session().orElseThrow();
            var warm=PossibleValuesAnalysis.prepare(session,PossibleValuesAnalysis.EFFECTS_PROFILE).analysis().orElseThrow();
            long livePreparation=resources.heapUsed();warm.close();
            long before=resources.heapUsed();
            long ownedPreparation=livePreparation-before;assertTrue(ownedPreparation>0,"old unfunded preparation must fail this law");
            // Keep the prior 4096-byte decode workspace in addition to, not in
            // place of, the newly owned preparation capacity. No product limit
            // changes; the separate 600-byte failure witness below is unchanged.
            long headroom=Math.addExact(4096,ownedPreparation);
            try(var occupied=resources.reserve(AnalysisResources.Pool.RESIDENT,resources.limits().heapBytes()-before-headroom,AnalysisResources.Phase.DEMAND)) {
                assertEquals(resources.limits().heapBytes()-before-headroom,occupied.amount());long baseline=resources.heapUsed();
                var accepted=PossibleValuesAnalysis.prepare(session,PossibleValuesAnalysis.EFFECTS_PROFILE).analysis().orElseThrow();
                try {assertEquals(ownedPreparation,resources.heapUsed()-baseline,"repeat preparation must own and account the same actual capacity");}
                finally{accepted.close();}
                assertEquals(baseline,resources.heapUsed(),"explicit preparation closure must return its own capacity");
            }
            assertEquals(before,resources.heapUsed());
            // Enough to start the value controls, not enough to publish the arena.
            // The earlier successful allocation must be retired, not stranded.
            try(var occupied=resources.reserve(AnalysisResources.Pool.RESIDENT,resources.limits().heapBytes()-before-600,AnalysisResources.Phase.DEMAND)) {
                assertEquals(resources.limits().heapBytes()-before-600,occupied.amount());long baseline=resources.heapUsed();
                var failure=assertThrows(AnalysisResources.Exhausted.class,()->PossibleValuesAnalysis.prepare(session,PossibleValuesAnalysis.EFFECTS_PROFILE));
                assertEquals(AnalysisResources.Resource.HEAP,failure.resource());assertEquals(AnalysisResources.Phase.DOMAIN,failure.phase());
                assertEquals(baseline,resources.heapUsed(),"failed preparation must not leave even its first allocated control lease");
            }
            assertEquals(before,resources.heapUsed());
        }
        released(resources);
    }
    private static int strongLogicalTexts(Object root) throws ReflectiveOperationException {
        var seen=java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Object,Boolean>());
        var pending=new java.util.ArrayDeque<Object>();pending.add(root);int texts=0;
        while(!pending.isEmpty()) {
            var value=pending.remove();if(!seen.add(value))continue;var type=value.getClass();var name=type.getName();
            if(value instanceof LogicalText)texts++;
            if(value instanceof java.util.Optional<?> optional){optional.ifPresent(pending::add);continue;}
            // Unmodifiable wrappers may wrap native/lazy views. Their iteration
            // would decode new objects rather than inspect stored strong roots.
            if(name.startsWith("java.util.Collections$"))continue;
            if(name.startsWith("java.util.")&&value instanceof java.util.Map<?,?> map){map.forEach((key,item)->{if(key!=null)pending.add(key);if(item!=null)pending.add(item);});continue;}
            if(name.startsWith("java.util.")&&value instanceof Iterable<?> items){items.forEach(item->{if(item!=null)pending.add(item);});continue;}
            if(type.isArray()){if(!type.componentType().isPrimitive())for(int at=0;at<java.lang.reflect.Array.getLength(value);at++){var item=java.lang.reflect.Array.get(value,at);if(item!=null)pending.add(item);}continue;}
            if(!name.startsWith("io.github.gustavo2358.analysis."))continue;
            for(var field:type.getDeclaredFields())if(!java.lang.reflect.Modifier.isStatic(field.getModifiers())&&!field.getType().isPrimitive()) {
                field.setAccessible(true);var item=field.get(value);if(item!=null)pending.add(item);
            }
        }
        return texts;
    }
}
