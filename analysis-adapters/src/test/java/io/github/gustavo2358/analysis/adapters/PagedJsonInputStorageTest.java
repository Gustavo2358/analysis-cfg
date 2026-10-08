package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.air.json.AirJson;
import io.github.gustavo2358.analysis.solver.*;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

final class PagedJsonInputStorageTest {
    @TempDir Path directory;
    private static AnalysisResources resources(long work){return new AnalysisResources(new AnalysisResources.Limits(65536,65536,0,64L*1024*1024,4,work,0));}
    private static void zero(AnalysisResources r){assertEquals(0,r.heapUsed());for(var pool:AnalysisResources.Pool.values())assertEquals(0,r.used(pool));}
    @Test void coldOrdinalsAndDecodedNameEqualityAreExactWithoutQueryInsertion() {
        var r=resources(Long.MAX_VALUE);
        try(var pages=new FilePageStore(directory,4096,4,r,AnalysisResources.Phase.DECODE);var storage=new PagedJsonInputStorage(pages,r)) {
            for(int i=0;i<4096;i++)storage.child(1,i,100000L+i);
            text(storage,2,0,"é");text(storage,3,1,"é");text(storage,4,2,"é");
            assertTrue(storage.firstField(1,2));assertFalse(storage.firstField(1,3));assertTrue(storage.firstField(1,4));assertTrue(storage.firstField(2,3));
            long heap=r.heapUsed(),temporary=r.used(AnalysisResources.Pool.TEMPORARY),pagesIssued=pages.statistics().pagesIssued();
            for(int q=0;q<8192;q++){int at=(q*2731)&4095;assertEquals(100000L+at,storage.child(1,at));}
            assertEquals(heap,r.heapUsed());assertEquals(temporary,r.used(AnalysisResources.Pool.TEMPORARY));assertEquals(pagesIssued,pages.statistics().pagesIssued());assertTrue(temporary>65536);assertTrue(r.heapPeak()<=65536);
            System.out.println("PAGED_JSON_INDEX heap="+r.heapPeak()+" temporary="+temporary+" rows=4096 reads=8192");
        }
        zero(r);
    }
    @Test void officialReaderUsesColdPagesAndPreservesFullAdmissionAndDigest()throws Exception {
        var input=Path.of("src/test/resources/cp6/dynamic-x8.air.json");byte[] raw=Files.readAllBytes(input);var expected=new AirJson().decodeCheckedForPartialAnalysis(raw);var r=resources(Long.MAX_VALUE);
        var actual=DataflowAirReader.forPartialAnalysis().read(input,r);
        assertEquals(expected.publication(),actual.publication());assertEquals(expected.result(),actual.checked().orElseThrow().result());assertEquals(raw.length,actual.airBytesObserved());assertEquals(1,actual.airReads());assertEquals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw)),actual.sha256());
        assertTrue(r.heapPeak()<=65536);assertTrue(r.peak(AnalysisResources.Pool.TEMPORARY)>65536);zero(r);
    }
    @Test void everyPrimitiveWorkFailureReleasesTransferredStorage() {
        var measured=resources(Long.MAX_VALUE);long calls;
        try(var pages=new MemoryPageStore(128,measured);var storage=new PagedJsonInputStorage(pages,measured)){sequence(storage);calls=measured.workUsed();}
        zero(measured);
        for(long at=0;at<calls;at++) {
            var r=resources(at);
            try(var pages=new MemoryPageStore(128,r)) {
                try(var storage=new PagedJsonInputStorage(pages,r)){assertThrows(AnalysisResources.Exhausted.class,()->sequence(storage));assertThrows(IllegalStateException.class,()->storage.child(1,0));}
                assertEquals(0,pages.statistics().livePages());
            }
            zero(r);
        }
    }
    @Test void deniedConstructionUnknownChildrenAndRepeatedOrdinalsFailClosed() {
        for(int left:new int[]{1000,5000,9000}) {
            var r=resources(Long.MAX_VALUE);
            try(var pages=new MemoryPageStore(128,r);var pressure=r.reserve(AnalysisResources.Pool.RESIDENT,r.limits().heapBytes()-r.heapUsed()-left,AnalysisResources.Phase.DECODE)) {
                assertTrue(pressure.amount()>0);long before=r.heapUsed();assertThrows(AnalysisResources.Exhausted.class,()->new PagedJsonInputStorage(pages,r));assertEquals(before,r.heapUsed());assertEquals(0,pages.statistics().livePages());
            }
            zero(r);
        }
        for(boolean unknown:new boolean[]{false,true}) {
            var r=resources(Long.MAX_VALUE);
            try(var pages=new MemoryPageStore(128,r);var storage=new PagedJsonInputStorage(pages,r)) {
                storage.child(1,0,2);assertThrows(IllegalStateException.class,()->{if(unknown)storage.child(1,1);else storage.child(1,0,3);});assertThrows(IllegalStateException.class,()->storage.get(AirJson.InputStorage.Column.NODES,0));
            }
            zero(r);
        }
    }
    private static void sequence(PagedJsonInputStorage storage){text(storage,1,0,"a");text(storage,2,1,"a");storage.child(10,0,1);assertEquals(1,storage.child(10,0));assertTrue(storage.firstField(10,1));assertFalse(storage.firstField(10,2));}
    private static void text(PagedJsonInputStorage storage,long token,long start,String value){long base=(token-1)*8;storage.set(AirJson.InputStorage.Column.NODES,base,2);storage.set(AirJson.InputStorage.Column.NODES,base+4,start);storage.set(AirJson.InputStorage.Column.NODES,base+5,value.length());for(int i=0;i<value.length();i++){long at=start+i,word=at>>>2,shift=(at&3)*16,old=storage.get(AirJson.InputStorage.Column.CHARACTERS,word);storage.set(AirJson.InputStorage.Column.CHARACTERS,word,(old&~(65535L<<shift))|((long)value.charAt(i)<<shift));}}
}
