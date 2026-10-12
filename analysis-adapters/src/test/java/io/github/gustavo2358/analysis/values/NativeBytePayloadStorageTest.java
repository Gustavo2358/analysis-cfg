package io.github.gustavo2358.analysis.values;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.analysis.adapters.DataflowAirReader;
import io.github.gustavo2358.analysis.adapters.FilePageStore;
import io.github.gustavo2358.analysis.adapters.PagedSnapshotOrderStorage;
import io.github.gustavo2358.analysis.dependencies.SnapshotProgram;
import io.github.gustavo2358.analysis.solver.AnalysisResources;
import io.github.gustavo2358.analysis.storage.StorageRange;
import java.math.BigInteger;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Real admitted native bytes versus the independently pinned resident codec. */
final class NativeBytePayloadStorageTest {
    @TempDir Path directory;
    private static final Path INPUT=Path.of("src/test/resources/cp6/dynamic-x8.air.json");
    private static final Memory.Codec IBM=new Memory.ExtensionCodec(Capabilities.IBM1047.name(),Capabilities.IBM1047.version(),Types.known(Types.Builtin.TEXT));
    private static final Memory.Codec ASCII=Memory.AsciiText.INSTANCE;
    private static AnalysisResources resources(){return new AnalysisResources(new AnalysisResources.Limits(64L*1024*1024,16L*1024*1024,0,256L*1024*1024,8,Long.MAX_VALUE,Long.MAX_VALUE));}
    private static BigInteger extent(String value){return BigInteger.valueOf(value.codePointCount(0,value.length()));}
    private static ByteImage encoded(BytePayloadArena arena,String value,Memory.Codec codec,int producer){return arena.encode(new Values.TextValue(value),codec,extent(value),producer).orElseThrow();}
    private static ByteImage resident(String value,Memory.Codec codec,int producer){return ByteImage.literal(MemoryCodecs.encodeText(codec,new Values.TextValue(value),extent(value)).value().orElseThrow(),producer);}
    private static StorageRange range(long start,long size){return StorageRange.exact(BigInteger.valueOf(start),BigInteger.valueOf(size));}
    private static void released(AnalysisResources resources){for(var pool:AnalysisResources.Pool.values())assertEquals(0,resources.used(pool),pool.toString());}
    @Test void allPinnedOctetsAndPartialWordsHaveExactResidentBytesAndStructuralHash() throws Exception {
        var resources=resources();
        try(var input=new DataflowAirReader().readSnapshot(INPUT,resources);
            var program=new SnapshotProgram(input.checked(),input.newIdentityStorage(),input.newOrderStorage(),resources);
            var bytes=new BytePayloadArena(program)) {
            var repertoire=new StringBuilder();for(int scalar=0;scalar<256;scalar++)repertoire.append((char)scalar);
            for(var codec:List.of(IBM,ASCII))for(int length=0;length<=(codec==IBM?256:128);length++) {
                String source=repertoire.substring(0,length);var actual=encoded(bytes,source,codec,7);var expected=resident(source,codec,7);
                assertEquals(expected,actual);assertEquals(expected.hashCode(),actual.hashCode());
                assertEquals(expected.read(range(0,length)),actual.read(range(0,length)));
                if(length>0){var payload=actual.parts().getFirst().payload().orElseThrow().octets();assertTrue(BytePayloadArena.stored(payload));assertThrows(UnsupportedOperationException.class,()->payload.set(0,3));}
            }
        }
        released(resources);
    }
    @Test void failuresPreservePriorWordsAndNeverPublishUnsupportedOrPartialEncoding() throws Exception {
        var resources=resources();
        try(var input=new DataflowAirReader().readSnapshot(INPUT,resources);
            var program=new SnapshotProgram(input.checked(),input.newIdentityStorage(),input.newOrderStorage(),resources);
            var bytes=new BytePayloadArena(program)) {
            var previous=encoded(bytes,"ABCD",IBM,3);long before=bytes.wordsWritten(),heap=resources.heapUsed();
            var unsupported=new Memory.ExtensionCodec("unknown","1",Types.known(Types.Builtin.TEXT));
            assertThrows(IllegalArgumentException.class,()->new Values.TextValue("A\ud800"));
            for(var codec:List.of(IBM,ASCII,unsupported))for(String source:List.of("A😀","Aβ","Aÿ")) {
                var value=new Values.TextValue(source);var expected=MemoryCodecs.encodeText(codec,value,extent(source));
                var actual=bytes.encode(value,codec,extent(source),4);assertEquals(expected.value().isPresent(),actual.isPresent());
                if(actual.isPresent()){assertEquals(ByteImage.literal(expected.value().orElseThrow(),4),actual.orElseThrow());before=bytes.wordsWritten();heap=resources.heapUsed();}
                else{assertEquals(before,bytes.wordsWritten());assertEquals(heap,resources.heapUsed());}
            }
            assertTrue(bytes.encode(new Values.TextValue("AB"),IBM,BigInteger.ONE,4).isEmpty());
            assertThrows(IllegalArgumentException.class,()->bytes.encode(new Values.TextValue(""),IBM,BigInteger.valueOf(-1),4));
            assertEquals(resident("ABCD",IBM,3),previous);
        }
        released(resources);
    }
    @Test void geometricDenseBytesPackEightOctetsPerWordInTheExistingTwoPageBackend() throws Exception {
        var resources=resources();
        try(var input=new DataflowAirReader().readSnapshot(INPUT,resources);
            var pages=new FilePageStore(directory,4096,2,resources);
            var program=new SnapshotProgram(input.checked(),input.newIdentityStorage(),new PagedSnapshotOrderStorage(pages,resources),resources);
            var bytes=new BytePayloadArena(program)) {
            encoded(bytes,"A",IBM,0);long before=resources.heapUsed(),temporary=resources.used(AnalysisResources.Pool.TEMPORARY);var initial=pages.statistics();int count=0;
            for(int width:new int[]{4096,8192,16384,32768,65536}) {
                String source="AB".repeat(width/2);long words=bytes.wordsWritten();var actual=encoded(bytes,source,IBM,1);count++;
                assertEquals(width/8,bytes.wordsWritten()-words);assertEquals(before+96L*count,resources.heapUsed());
                assertEquals(resident(source,IBM,1).hashCode(),actual.hashCode());
                assertEquals(resident("ABABABAB",IBM,1).read(range(0,8)).bytes(),actual.read(range(width-8,8)).bytes());
                System.out.println("NATIVE_BYTE_PAYLOAD_CURVE width="+width+" storedWords="+(bytes.wordsWritten()-words)+" residentHeaderGrowth="+(resources.heapUsed()-before)+" temporaryGrowth="+(resources.used(AnalysisResources.Pool.TEMPORARY)-temporary)+" evictions="+(pages.statistics().evictions()-initial.evictions())+" bytesRead="+(pages.statistics().bytesRead()-initial.bytesRead())+" bytesWritten="+(pages.statistics().bytesWritten()-initial.bytesWritten()));
            }
            assertTrue((pages.statistics().livePages()-initial.livePages())*pages.pageBytes()>=8L*(bytes.wordsWritten()-1));
            assertTrue(pages.statistics().evictions()-initial.evictions()>20);assertTrue(pages.statistics().bytesWritten()-initial.bytesWritten()>65536);
        }
        released(resources);
    }
    @Test void slicingCopyingWritesAndProvenanceMatchTheResidentOracle() throws Exception {
        var resources=resources();
        try(var input=new DataflowAirReader().readSnapshot(INPUT,resources);
            var program=new SnapshotProgram(input.checked(),input.newIdentityStorage(),input.newOrderStorage(),resources);
            var bytes=new BytePayloadArena(program)) {
            var nativeImage=encoded(bytes,"0123456789ABCDEF",IBM,3);var expected=resident("0123456789ABCDEF",IBM,3);
            for(int start=0;start<=16;start++)for(int size=0;size<=16-start;size++) {
                var selected=range(start,size);var actual=nativeImage.slice(selected).copied(9,BigInteger.valueOf(start),2);var oracle=expected.slice(selected).copied(9,BigInteger.valueOf(start),2);
                assertEquals(oracle,actual);assertEquals(oracle.hashCode(),actual.hashCode());assertEquals(oracle.read(range(0,size)),actual.read(range(0,size)));
                var overwritten=nativeImage.write(selected,actual);var residentWritten=expected.write(selected,oracle);
                assertEquals(residentWritten,overwritten);assertEquals(residentWritten.read(range(0,16)),overwritten.read(range(0,16)));
            }
            assertEquals(expected.fit(BigInteger.valueOf(22),32,4),nativeImage.fit(BigInteger.valueOf(22),32,4));
            assertEquals(expected.withSourceGap(range(3,7),11),nativeImage.withSourceGap(range(3,7),11));
            var support=Set.of(new ByteImage.LogicalSupport(program.units().getFirst().objects().getFirst().id(),8));
            assertEquals(expected.withLogicalSupport(support).read(range(2,9)),nativeImage.withLogicalSupport(support).read(range(2,9)));
        }
        released(resources);
    }
    @Test void explicitAndNativeOwnerClosureExpireBorrowedBytesButLeaveInputAndHashReadable() throws Exception {
        var resources=resources();
        try(var input=new DataflowAirReader().readSnapshot(INPUT,resources)) {
            var snapshot=input.checked().snapshot();long root=snapshot.root();var shape=snapshot.shape(root);long inputHeap=resources.heapUsed();
            var program=new SnapshotProgram(input.checked(),input.newIdentityStorage(),input.newOrderStorage(),resources);
            try {
                program.storageInventory();long before=resources.heapUsed();var bytes=new BytePayloadArena(program);var image=encoded(bytes,"0123456789",IBM,2);int hash=image.hashCode();
                var payload=image.parts().getFirst().payload().orElseThrow().octets();assertTrue(resources.heapUsed()>=before+256+96+1040+96);
                bytes.close();bytes.close();assertEquals(before,resources.heapUsed());assertEquals(hash,image.hashCode());assertThrows(IllegalStateException.class,()->payload.get(0));
                var expired=new BytePayloadArena(program);var old=encoded(expired,"ABC",IBM,4);var oldPayload=old.parts().getFirst().payload().orElseThrow().octets();program.close();
                assertThrows(IllegalStateException.class,()->oldPayload.get(0));assertThrows(IllegalStateException.class,()->expired.encode(new Values.TextValue("A"),IBM,BigInteger.ONE,5));expired.close();
                assertEquals(inputHeap,resources.heapUsed());assertEquals(shape,snapshot.shape(root));
            } finally{program.close();}
        }
        released(resources);
    }
    @Test void refusedHeaderAndPartialConstructorReturnCapacityWithoutMutatingLiveImage() throws Exception {
        var resources=resources();
        try(var input=new DataflowAirReader().readSnapshot(INPUT,resources);
            var program=new SnapshotProgram(input.checked(),input.newIdentityStorage(),input.newOrderStorage(),resources)) {
            program.storageInventory();long baseline=resources.heapUsed();
            try(var occupied=resources.reserve(AnalysisResources.Pool.RESIDENT,resources.limits().heapBytes()-baseline-300,AnalysisResources.Phase.DEMAND)) {
                assertTrue(occupied.amount()>0);long before=resources.heapUsed();var failure=assertThrows(AnalysisResources.Exhausted.class,()->new BytePayloadArena(program));
                assertEquals(AnalysisResources.Phase.DOMAIN,failure.phase());assertEquals(96,failure.requested());assertEquals(before,resources.heapUsed());
            }
            try(var bytes=new BytePayloadArena(program)) {
                var previous=encoded(bytes,"A",ASCII,0);long before=resources.heapUsed();
                try(var occupied=resources.reserve(AnalysisResources.Pool.RESIDENT,resources.limits().heapBytes()-before,AnalysisResources.Phase.DOMAIN)) {
                    assertTrue(occupied.amount()>0);var failure=assertThrows(AnalysisResources.Exhausted.class,()->encoded(bytes,"B",ASCII,1));
                    assertEquals(AnalysisResources.Resource.HEAP,failure.resource());assertEquals(AnalysisResources.Phase.DOMAIN,failure.phase());assertEquals(96,failure.requested());
                    assertEquals(resources.limits().heapBytes(),resources.heapUsed());assertEquals(resident("A",ASCII,0),previous);
                }
                assertEquals(before,resources.heapUsed());
            }
            assertEquals(baseline,resources.heapUsed());
        }
        released(resources);
    }
    @Test void explicitResidentCompatibilityIsNotReclassifiedAsNativeStorage() throws Exception {
        var resources=resources();
        try(var input=new DataflowAirReader().readSnapshot(INPUT,resources);
            var program=new SnapshotProgram(input.checked(),input.newIdentityStorage(),input.newOrderStorage());
            var bytes=new BytePayloadArena(program)) {
            assertTrue(program.executionBudget().isEmpty());var actual=encoded(bytes,"ABCÿ",IBM,3);assertEquals(resident("ABCÿ",IBM,3),actual);
            assertFalse(BytePayloadArena.stored(actual.parts().getFirst().payload().orElseThrow().octets()));assertEquals(0,bytes.wordsWritten());
        }
        released(resources);
    }
}
