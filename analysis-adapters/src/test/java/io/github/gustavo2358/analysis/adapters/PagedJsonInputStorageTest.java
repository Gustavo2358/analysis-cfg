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
            for(int i=0;i<16384;i++)storage.child(1,i,100000L+i);
            text(storage,2,0,"é");text(storage,3,1,"é");text(storage,4,2,"é");
            assertTrue(storage.firstField(1,2));assertFalse(storage.firstField(1,3));assertTrue(storage.firstField(1,4));assertTrue(storage.firstField(2,3));
            long heap=r.heapUsed(),temporary=r.used(AnalysisResources.Pool.TEMPORARY),pagesIssued=pages.statistics().pagesIssued();
            for(int q=0;q<32768;q++){int at=(q*2731)&16383;assertEquals(100000L+at,storage.child(1,at));}
            assertEquals(heap,r.heapUsed());assertEquals(temporary,r.used(AnalysisResources.Pool.TEMPORARY));assertEquals(pagesIssued,pages.statistics().pagesIssued());assertTrue(temporary>65536);assertTrue(r.heapPeak()<=65536);
            System.out.println("PAGED_JSON_INDEX heap="+r.heapPeak()+" temporary="+temporary+" rows=16384 reads=32768");
        }
        zero(r);
    }
    @Test void officialReaderUsesColdPagesAndPreservesFullAdmissionAndDigest()throws Exception {
        var input=Path.of("src/test/resources/cp6/dynamic-x8.air.json");byte[] raw=Files.readAllBytes(input);var expected=new AirJson().decodeCheckedForPartialAnalysis(raw);var r=resources(Long.MAX_VALUE);
        var actual=DataflowAirReader.forPartialAnalysis().read(input,r);
        assertEquals(expected.publication(),actual.publication());assertEquals(expected.result(),actual.checked().orElseThrow().result());assertEquals(raw.length,actual.airBytesObserved());assertEquals(1,actual.airReads());assertEquals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw)),actual.sha256());
        assertTrue(r.heapPeak()<=65536);assertTrue(r.peak(AnalysisResources.Pool.TEMPORARY)>65536);zero(r);
    }
    @Test void officialPhysicalProfileReadsEveryColdSourceMetadataUnitWithoutResidentTree()throws Exception {
        // Physical contract only: complete literal wire and independent fields, not source admission.
        for(int n:new int[]{1,4,16,64,256}) {
            var wire=directory.resolve("metadata-"+n+".json");
            try(var writer=Files.newBufferedWriter(wire,java.nio.charset.StandardCharsets.UTF_8)) {
                writer.write("{\"locations\":[");
                for(int i=0;i<n;i++) {if(i!=0)writer.write(',');writer.write("{\"file\":\""+metadataPath(n,i)+"\",\"startLine\":"+(i+1)+",\"startColumn\":0,\"endLine\":"+(i+2)+",\"endColumn\":13}");}
                writer.write("],\"unicode\":\"\\u0000\\uD800x\\uDC00\\uD83D\\uDE00\",\"number\":-0.25e+10}");
            }
            var r=new AnalysisResources(new AnalysisResources.Limits(65536,16384,0,64L*1024*1024,4,Long.MAX_VALUE,0));
            try(var pages=new FilePageStore(directory,4096,4,r,AnalysisResources.Phase.DECODE);
                var bufferCapacity=r.reserve(AnalysisResources.Pool.SCRATCH,1024,AnalysisResources.Phase.DECODE);
                var stream=Files.newInputStream(wire);
                var input=new AirJson().stageInput(stream,new PagedJsonInputStorage(pages,r),AirJson.PhysicalProfile.UTF16_JSON)) {
                assertEquals(1024,bufferCapacity.amount());char[] buffer=new char[256];long rows=input.child(input.root(),0);
                assertEquals(n,input.childCount(rows));long heap=r.heapUsed();
                for(int i=n-1;i>=0;i--) {
                    long row=input.child(rows,i),file=input.child(row,0);String expected=metadataPath(n,i);
                    assertTrue(input.matches(input.name(file),"file"));assertEquals(expected.length(),input.characterCount(file));
                    for(int at=0;at<expected.length();at+=buffer.length) {
                        int count=Math.min(buffer.length,expected.length()-at);input.readCharacters(file,at,buffer,0,count);
                        for(int offset=0;offset<count;offset++)assertEquals(expected.charAt(at+offset),buffer[offset],"complete path N="+n+" row="+i+" unit="+(at+offset));
                    }
                    for(int field=1;field<5;field++) {
                        long value=input.child(row,field);assertEquals(AirJson.PhysicalKind.NUMBER,input.kind(value));
                        assertTrue(input.matches(value,switch(field){case 1->Integer.toString(i+1);case 2->"0";case 3->Integer.toString(i+2);case 4->"13";default->throw new AssertionError();}));
                    }
                }
                assertEquals(heap,r.heapUsed());
                try(var unicode=input.project(input.child(input.root(),1));var numeric=input.project(input.child(input.root(),2))) {
                    assertEquals("\u0000\uD800x\uDC00😀",unicode.value());assertEquals("-0.25e+10",numeric.value());
                }
                assertEquals(heap,r.heapUsed());assertTrue(r.heapPeak()<=65536);
                if(n>=16)assertTrue(r.peak(AnalysisResources.Pool.TEMPORARY)>r.limits().heapBytes());
                System.out.println("PHYSICAL_SOURCE_METADATA rows="+n+" heapPeak="+r.heapPeak()+" temporaryPeak="+r.peak(AnalysisResources.Pool.TEMPORARY)+" bytesRead="+pages.statistics().bytesRead());
                expire(input);assertThrows(IllegalStateException.class,input::root);assertEquals(0,pages.statistics().livePages());
                long borrowed=pages.allocate();pages.release(borrowed);
            }
            zero(r);
        }
    }
    @Test void physicalProfileProjectionDenialExpiresOwnerAndKeepsBorrowedStore()throws Exception {
        var r=new AnalysisResources(new AnalysisResources.Limits(65536,16384,0,64L*1024*1024,4,Long.MAX_VALUE,0));
        try(var pages=new FilePageStore(directory,4096,4,r,AnalysisResources.Phase.DECODE)) {
            String raw="{\"large\":\""+"x".repeat(4096)+"\",\"small\":\"live\"}";
            var input=new AirJson().stageInput(new ByteArrayInputStream(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8)),new PagedJsonInputStorage(pages,r),AirJson.PhysicalProfile.UTF16_JSON);
            var live=input.project(input.child(input.root(),1));
            var failure=assertThrows(AnalysisResources.Exhausted.class,()->input.project(input.child(input.root(),0)));
            assertEquals(AnalysisResources.Resource.SCRATCH,failure.resource());assertEquals(AnalysisResources.Phase.DECODE,failure.phase());
            assertThrows(IllegalStateException.class,live::value);assertThrows(IllegalStateException.class,input::root);
            live.close();input.close();assertEquals(0,pages.statistics().livePages());
            long borrowed=pages.allocate();pages.release(borrowed);
        }
        zero(r);
    }
    private static String metadataPath(int n,int row){return "synthetic-"+n+"-"+row+"-"+"x".repeat(4096)+"-"+(n-row)+".cbl";}
    private static void expire(AirJson.StagedInput input){input.close();}
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
    @Test void fixedContainerQueriesHaveOnlyBoundedColumnAddressingCost() {
        for(int n:new int[]{1,16,64,256,1024,4096}) {
            var r=resources(Long.MAX_VALUE);
            try(var pages=new FilePageStore(directory,4096,4,r,AnalysisResources.Phase.DECODE)) {
                var observed=new CountingPages(pages);
                try(var storage=new PagedJsonInputStorage(observed,r)) {
                    storage.child(1,0,777);
                    for(int i=0;i<n;i++)storage.child(i+2,0,10000L+i);
                    for(int q=0;q<8;q++)assertEquals(777,storage.child(1,0));
                    long before=r.workUsed(),reads=observed.reads;
                    for(int q=0;q<64;q++)assertEquals(777,storage.child(1,0));
                    long queryWork=r.workUsed()-before,queryReads=observed.reads-reads;
                    // Four primitive words: count, root, height and selected child. Sparse
                    // columns here need at most two directory levels (address <2^24).
                    // Count logical page reads, independently of backend checksum/I/O WORK.
                    assertTrue(queryReads<=64L*4*3,"fixed lookup has at most four leaf paths of three pages N="+n+" reads="+queryReads);
                    System.out.println("PAGED_JSON_LOCAL_QUERY unrelated="+n+" logicalReads="+queryReads+" totalWork="+queryWork);
                }
            }
            zero(r);
        }
    }
    private static final class CountingPages implements PageStore {
        final PageStore delegate;long reads;
        CountingPages(PageStore delegate){this.delegate=delegate;}
        public int pageBytes(){return delegate.pageBytes();}public long allocate(){return delegate.allocate();}
        public void read(long page,int offset,byte[] value,int start,int size){reads++;delegate.read(page,offset,value,start,size);}
        public void write(long page,int offset,byte[] value,int start,int size){delegate.write(page,offset,value,start,size);}
        public void release(long page){delegate.release(page);}public void flush(){delegate.flush();}
        public void readForCleanup(long page,int offset,byte[] value,int start,int size){delegate.readForCleanup(page,offset,value,start,size);}
        public void releaseForCleanup(long page){delegate.releaseForCleanup(page);}public Statistics statistics(){return delegate.statistics();}public void close(){delegate.close();}
    }
    @Test void interleavedContainersAndGrowingExactNameSetsPreserveIndependentOccurrences() {
        var r=resources(Long.MAX_VALUE);
        try(var pages=new FilePageStore(directory,4096,4,r,AnalysisResources.Phase.DECODE);var storage=new PagedJsonInputStorage(pages,r)) {
            long start=0;
            for(int i=0;i<1024;i++) {
                storage.child(1,i,10000L+i);storage.child(2,i,20000L+i);
                String name="f"+i;long token=1000L+i;text(storage,token,start,name);start+=name.length();
                assertTrue(storage.firstField(1,token));assertTrue(storage.firstField(2,token));
            }
            for(int q=0;q<2048;q++){int at=(q*2731)&1023;assertEquals(10000L+at,storage.child(1,at));assertEquals(20000L+at,storage.child(2,at));assertFalse(storage.firstField(1,1000L+at));assertFalse(storage.firstField(2,1000L+at));}
        }
        zero(r);
    }
    private static void sequence(PagedJsonInputStorage storage){text(storage,1,0,"a");text(storage,2,1,"a");storage.child(10,0,1);assertEquals(1,storage.child(10,0));assertTrue(storage.firstField(10,1));assertFalse(storage.firstField(10,2));}
    private static void text(PagedJsonInputStorage storage,long token,long start,String value){long base=(token-1)*8;storage.set(AirJson.InputStorage.Column.NODES,base,2);storage.set(AirJson.InputStorage.Column.NODES,base+4,start);storage.set(AirJson.InputStorage.Column.NODES,base+5,value.length());for(int i=0;i<value.length();i++){long at=start+i,word=at>>>2,shift=(at&3)*16,old=storage.get(AirJson.InputStorage.Column.CHARACTERS,word);storage.set(AirJson.InputStorage.Column.CHARACTERS,word,(old&~(65535L<<shift))|((long)value.charAt(i)<<shift));}}
}
