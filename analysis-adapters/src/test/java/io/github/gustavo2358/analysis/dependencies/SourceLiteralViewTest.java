package io.github.gustavo2358.analysis.dependencies;

import io.github.gustavo2358.analysis.adapters.*;
import io.github.gustavo2358.analysis.dependencies.source.*;
import io.github.gustavo2358.analysis.solver.*;
import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

final class SourceLiteralViewTest {
    @TempDir Path directory;
    private static AnalysisResources resources(){return new AnalysisResources(new AnalysisResources.Limits(1048576,16384,0,256L*1024*1024,2,Long.MAX_VALUE,0));}
    private static void zero(AnalysisResources resources){for(var pool:AnalysisResources.Pool.values())assertEquals(0,resources.used(pool));}
    private static SourceAdmissionSnapshot admit(String raw,PageStore pages,AnalysisResources resources)throws Exception {
        var facts=new NominalValues("NOMINAL_TEXT_SOURCE_V4",List.of(new NominalValues.Symbol("v0",8)),List.of(new NominalValues.Assignment("s","v0",new NominalValues.Term("LITERAL",raw))),List.of(),List.of());
        return SourceAdmissionSnapshot.admit(new QualifiedSourceJson().decodeSnapshot(new ByteArrayInputStream(SourceNominalDemandTest.wire(facts,1)),pages,resources),pages,resources);
    }
    private static long text(SourceAdmissionSnapshot admission) {
        var source=admission.snapshot();long nominal=source.field(admission.unit(0),SourceShape.UNIT_EVIDENCE,12),facts=source.field(nominal,SourceShape.NOMINAL_VALUE_EVIDENCE,0),writes=source.field(facts,SourceShape.NOMINAL_VALUES,2);
        return source.field(source.field(source.element(writes,SourceShape.ASSIGNMENT,0),SourceShape.ASSIGNMENT,2),SourceShape.TERM,1);
    }
    private static String projected(CharSequence sequence){var out=new StringBuilder(sequence.length());for(int i=0;i<sequence.length();i++)out.append(sequence.charAt(i));return out.toString();}
    private static String oracle(String raw,int mask,int extent) {
        int begin=0,end=raw.length();if((mask&2)!=0)while(begin<end&&raw.charAt(begin)==' ')begin++;if((mask&4)!=0)while(end>begin&&raw.charAt(end-1)==' ')end--;
        String value=raw.substring(begin,end);
        if((mask&1)!=0){var folded=new StringBuilder();for(int i=0;i<value.length();i++){char c=value.charAt(i);if(c>127)return null;folded.append(c>='a'&&c<='z'?(char)(c-32):c);}value=folded.toString();}
        if(extent<0)return value;var scalars=value.codePoints().toArray();var fitted=new StringBuilder();for(int i=0;i<extent;i++){if(i<scalars.length)fitted.appendCodePoint(scalars[i]);else fitted.append(' ');}return fitted.toString();
    }
    @Test void everyTransformFitAndUtf16BoundaryMatchesIndependentScalarOracle()throws Exception {
        for(String raw:List.of("","  aAz0  "," 🚀a\u0000\ud800 z\udc00 "," ".repeat(255)+"🚀"+" ".repeat(257),"a".repeat(255)+"🚀z","éab")) {
            var resources=resources();
            try(var pages=new FilePageStore(directory,4096,4,resources,AnalysisResources.Phase.DOMAIN);var admission=admit(raw,pages,resources);var view=SourceLiteralView.prepare(admission,pages,resources)) {
                for(int mask=0;mask<8;mask++)for(int extent:new int[]{-1,0,1,4,16,260,1024}) {
                    String expected=oracle(raw,mask,extent);assertEquals(expected!=null,view.selectSource(text(admission),mask,extent));
                    if(expected!=null)assertEquals(expected,projected(view),"mask="+mask+" extent="+extent);
                    else assertThrows(IllegalStateException.class,view::length,"unsupported uppercase cannot become an empty known value");
                }
            }zero(resources);
        }
    }
    @Test void borrowedCharacterCandidatesKeepAllFlagsProofsPaddingTruthAndCanonicalValues()throws Exception {
        String raw="  abc \u0000🚀\ud800 ";var resources=resources();
        try(var pages=new FilePageStore(directory,4096,4,resources,AnalysisResources.Phase.DOMAIN);var admission=admit(raw,pages,resources);var view=SourceLiteralView.prepare(admission,pages,resources);var values=new SourceValueStore(pages,resources)) {
            for(int extent:new int[]{0,1,4,8,16}) {
                assertTrue(view.selectSource(text(admission),6,extent));String expected=oracle(raw,6,extent);long value=values.literal(view,7,21);
                assertEquals(values.literal(expected,7,21),value);assertEquals(7,values.flags(value));
                try(var candidates=values.candidates(value)){assertTrue(candidates.advance());long id=candidates.key();try(var proofs=values.supports(candidates.value())){assertTrue(proofs.advance());assertEquals(21,proofs.key());assertFalse(proofs.advance());}
                    assertFalse(candidates.advance());assertTrue(view.selectValue(values,id,0,-1));assertEquals(expected,projected(view));
                    assertEquals(value,values.addCandidate(values.emptyValue(7),view,support(values,value)));
                }
                assertEquals(0,resources.used(AnalysisResources.Pool.SCRATCH));
            }
            assertTrue(view.selectSource(text(admission),0,-1));long value=values.literal(view,0,0);assertEquals(values.literal(raw,0,0),value);assertEquals(3,values.equality(value,values.unknown(false)));
        }zero(resources);
    }
    private static long support(SourceValueStore values,long value){try(var candidates=values.candidates(value)){assertTrue(candidates.advance());return candidates.value();}}
    @Test void largeLiteralExceedsProjectionScratchButFullBorrowedInputAndDictionaryViewsStayBounded()throws Exception {
        long fixed=-1;
        for(int length:new int[]{1,256,4096,65536}) {
            String raw="ab🚀\u0000".repeat(length)+"  ";var resources=resources();
            try(var pages=new FilePageStore(directory,4096,4,resources,AnalysisResources.Phase.DOMAIN);var admission=admit(raw,pages,resources);var values=new SourceValueStore(pages,resources)) {
                long before=resources.heapUsed(),baseline=pages.statistics().livePages();
                try(var view=SourceLiteralView.prepare(admission,pages,resources)) {
                    long delta=resources.heapUsed()-before;if(fixed<0)fixed=delta;assertEquals(fixed,delta);assertTrue(view.selectSource(text(admission),0,-1));long value=values.literal(view,0,1);
                    try(var candidates=values.candidates(value)){long cursorScratch=resources.used(AnalysisResources.Pool.SCRATCH);assertEquals(2048,cursorScratch);assertTrue(candidates.advance());long id=candidates.key();assertTrue(view.selectValue(values,id,0,-1));assertEquals(raw.length(),view.length());
                        for(int i=0;i<raw.length();i++)assertEquals(raw.charAt(i),view.charAt(i));assertEquals(cursorScratch,resources.used(AnalysisResources.Pool.SCRATCH));
                        if(length==65536){var denial=assertThrows(AnalysisResources.Exhausted.class,()->values.projectText(id));assertEquals(AnalysisResources.Resource.SCRATCH,denial.resource());}
                    }
                    assertEquals(0,resources.used(AnalysisResources.Pool.SCRATCH),"cursor and borrowed prefix controls retired");
                }assertTrue(pages.statistics().livePages()>=baseline,"value dictionary is independently owned,not stolen by view close");
            }zero(resources);
        }
    }
    @Test void foreignContextDenialWrongShapeAndParentExpiryNeverLeakViewCapacity()throws Exception {
        var resources=resources();var foreign=resources();
        try(var pages=new FilePageStore(directory,4096,4,resources,AnalysisResources.Phase.DOMAIN);var other=new FilePageStore(directory,4096,4,resources,AnalysisResources.Phase.DOMAIN);var admission=admit("X",pages,resources)) {
            long baseline=pages.statistics().livePages();assertThrows(IllegalArgumentException.class,()->SourceLiteralView.prepare(admission,other,resources));assertThrows(IllegalArgumentException.class,()->SourceLiteralView.prepare(admission,pages,foreign));
            try(var pressure=resources.reserve(AnalysisResources.Pool.RESIDENT,resources.limits().heapBytes()-resources.heapUsed()-1023,AnalysisResources.Phase.DOMAIN)) {assertNotNull(pressure);assertThrows(AnalysisResources.Exhausted.class,()->SourceLiteralView.prepare(admission,pages,resources));}
            try(var view=SourceLiteralView.prepare(admission,pages,resources)){assertThrows(IllegalArgumentException.class,()->view.selectSource(admission.unit(0),0,-1));assertThrows(IllegalStateException.class,view::length);}
            assertEquals(baseline,pages.statistics().livePages());assertEquals(1,admission.unitCount());var view=SourceLiteralView.prepare(admission,pages,resources);view.selectSource(text(admission),0,-1);
            admission.snapshot().close();assertEquals(0,pages.statistics().livePages());assertThrows(IllegalStateException.class,view::length);view.close();
        }zero(resources);zero(foreign);
    }
    @Test void exactDictionaryContextScratchDenialAndSingleSpaceNeverManufacturePartialCandidates()throws Exception {
        var resources=resources();var foreign=resources();
        try(var pages=new FilePageStore(directory,4096,4,resources,AnalysisResources.Phase.DOMAIN);var other=new FilePageStore(directory,4096,4,foreign,AnalysisResources.Phase.DOMAIN);var admission=admit("Aa",pages,resources);
            var values=new SourceValueStore(pages,resources);var wrong=new SourceValueStore(other,foreign)) {
            long foreignText;try(var candidates=wrong.candidates(wrong.literal("BB",0,1))){assertTrue(candidates.advance());foreignText=candidates.key();}
            try(var view=SourceLiteralView.prepare(admission,pages,resources)){assertThrows(IllegalArgumentException.class,()->view.selectValue(wrong,foreignText,0,-1));assertThrows(IllegalStateException.class,view::length);}
            assertEquals(1,admission.unitCount());
            try(var view=SourceLiteralView.prepare(admission,pages,resources)) {
                for(int mask=0;mask<8;mask++)for(int extent:new int[]{-1,0,1,4}){assertTrue(view.selectSpace(mask,extent));assertEquals(oracle(" ",mask,extent),projected(view));}
                assertTrue(view.selectSource(text(admission),0,-1));
                try(var pressure=resources.reserve(AnalysisResources.Pool.SCRATCH,resources.limits().scratchBytes()-127,AnalysisResources.Phase.DOMAIN)) {
                    assertNotNull(pressure);var denial=assertThrows(AnalysisResources.Exhausted.class,()->values.literal(view,0,1));assertEquals(AnalysisResources.Resource.SCRATCH,denial.resource());
                }
                assertEquals("Aa",projected(view));assertEquals(values.literal("Aa",0,1),values.literal(view,0,1));assertEquals(0,resources.used(AnalysisResources.Pool.SCRATCH));
            }
        }zero(resources);zero(foreign);
    }
    @Test void nativeOwnerExpiryAndPrimitiveReadFailureExpireViewWithoutClosingBorrowedBackend()throws Exception {
        var resources=new AnalysisResources(new AnalysisResources.Limits(64L*1024*1024,16L*1024*1024,0,256L*1024*1024,8,Long.MAX_VALUE,0));
        try(var input=new DataflowAirReader().readSnapshot(Path.of("src/test/resources/cp6/dynamic-x8.air.json"),resources);var pages=new FilePageStore(directory,4096,4,resources,AnalysisResources.Phase.DOMAIN)) {
            var program=new SnapshotProgram(input.checked(),input.newIdentityStorage(),input.newOrderStorage(),resources);
            try {
                var execution=AnalysisResources.executionFor(program);
                try(var admission=admit("borrowed",pages,execution);var view=SourceLiteralView.prepare(admission,pages,execution)){view.selectSource(text(admission),0,-1);program.close();assertEquals(0,pages.statistics().livePages());zero(execution);assertThrows(IllegalStateException.class,view::length);}
            }finally{program.close();}
        }zero(resources);
        resources=resources();
        try(var pages=new FilePageStore(directory,4096,4,resources,AnalysisResources.Phase.DOMAIN)) {
            var primary=new PageStore.Failure(PageStore.Reason.IO,"independent literal cache fault");var port=new FaultPages(pages,primary);
            try(var admission=admit("not-cached",port,resources);var view=SourceLiteralView.prepare(admission,port,resources)) {
                view.selectSource(text(admission),0,-1);port.fail=true;assertSame(primary,assertThrows(PageStore.Failure.class,()->view.charAt(0)));assertThrows(IllegalStateException.class,view::length);assertFalse(port.closed);
            }assertEquals(0,pages.statistics().livePages());long borrowed=pages.allocate();pages.release(borrowed);
        }zero(resources);
    }
    private static final class FaultPages implements PageStore {
        private final PageStore delegate;private final Failure primary;private boolean fail,closed;
        FaultPages(PageStore delegate,Failure primary){this.delegate=delegate;this.primary=primary;}
        @Override public int pageBytes(){return delegate.pageBytes();}
        @Override public long allocate(){return delegate.allocate();}
        @Override public void read(long p,int offset,byte[] bytes,int at,int count){if(fail){fail=false;throw primary;}delegate.read(p,offset,bytes,at,count);}
        @Override public void write(long p,int offset,byte[] bytes,int at,int count){delegate.write(p,offset,bytes,at,count);}
        @Override public void release(long p){delegate.release(p);}
        @Override public void readForCleanup(long p,int offset,byte[] bytes,int at,int count){delegate.readForCleanup(p,offset,bytes,at,count);}
        @Override public void releaseForCleanup(long p){delegate.releaseForCleanup(p);}
        @Override public void flush(){delegate.flush();}
        @Override public Statistics statistics(){return delegate.statistics();}
        @Override public void close(){closed=true;delegate.close();}
    }
}
