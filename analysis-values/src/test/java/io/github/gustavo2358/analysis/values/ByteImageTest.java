package io.github.gustavo2358.analysis.values;

import io.github.gustavo2358.analysis.storage.StorageRange;
import io.github.gustavo2358.air.model.Values;
import java.math.BigInteger;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Independent octet oracle for the image foundation; no AIR transfer or byte codec is invoked. */
class ByteImageTest {
    static StorageRange range(long start,long count){return StorageRange.exact(BigInteger.valueOf(start),BigInteger.valueOf(count));}
    @Test void unknownIsNotEmptyAndKnownSliceUsesItsOriginalOffset() {
        var empty=ByteImage.unknown(Optional.of(BigInteger.valueOf(8)),"ENTRY_UNKNOWN");
        assertTrue(empty.read(range(0,8)).bytes().isEmpty());
        var image=empty.write(range(0,8),ByteImage.literal(new Values.BytesValue(List.of(65,66,67,68,69,70,71,72)),1));
        var child=image.read(range(4,4));assertEquals(List.of(69,70,71,72),child.bytes().orElseThrow().octets());
        assertEquals(1,child.parts().size());assertEquals(BigInteger.valueOf(4),child.parts().getFirst().producerOffset());
        assertTrue(empty.read(range(0,8)).bytes().isEmpty(),"old immutable root unchanged");
    }
    @Test void partialWriteKeepsSuffixAndPartialUnknownInvalidatesWholeRead() {
        var image=ByteImage.literal(new Values.BytesValue(List.of(65,66,67,68,69,70,71,72)),1);
        var written=image.write(range(0,4),ByteImage.literal(new Values.BytesValue(List.of(87,88,89,90)),2));
        assertEquals(List.of(87,88,89,90,69,70,71,72),written.read(range(0,8)).bytes().orElseThrow().octets());
        assertEquals(List.of(2,1),written.parts().stream().map(ByteImage.Part::producer).toList());
        var unknown=written.write(range(0,4),ByteImage.unknown(Optional.of(BigInteger.valueOf(4)),"HAVOC_MUST"));
        assertTrue(unknown.read(range(0,8)).bytes().isEmpty());
        assertEquals(List.of(69,70,71,72),unknown.read(range(4,4)).bytes().orElseThrow().octets());
        assertEquals(Set.of("HAVOC_MUST"),unknown.read(range(0,8)).reasons());
    }
    @Test void copiesCaptureImmutableImageAndOverlapReadsOldBytes() {
        var x=ByteImage.literal(new Values.BytesValue(List.of(65,66,67,68)),1);
        var captured=x.slice(range(0,4)).copied(2);
        x=x.write(range(0,4),ByteImage.literal(new Values.BytesValue(List.of(87,88,89,90)),3));
        assertEquals(List.of(65,66,67,68),captured.read(range(0,4)).bytes().orElseThrow().octets());
        assertEquals(Set.of(2),captured.parts().getFirst().copies());
        var original=ByteImage.literal(new Values.BytesValue(List.of(65,66,67,68)),1);
        var overlap=original.write(range(1,3),original.slice(range(0,3)).copied(4));
        assertEquals(List.of(65,65,66,67),overlap.read(range(0,4)).bytes().orElseThrow().octets());
        assertEquals(List.of(87,88,89,90),x.read(range(0,4)).bytes().orElseThrow().octets());
    }
    @Test void unknownTailAndHugeExtentRemainSparseAndZeroIsIdentity() {
        var huge=BigInteger.ONE.shiftLeft(100);var image=ByteImage.unknown(Optional.of(huge),"UNINITIALIZED");
        assertEquals(1,image.parts().size());assertTrue(image.read(new StorageRange(BigInteger.ZERO,Optional.of(huge))).bytes().isEmpty());
        assertSame(image,image.write(range(0,0),ByteImage.literal(new Values.BytesValue(List.of()),1)));
        var tail=ByteImage.unknown(Optional.empty(),"UNKNOWN_EXTENT");
        assertTrue(tail.extent().isEmpty());assertTrue(tail.read(range(0,8)).bytes().isEmpty());
        assertThrows(IllegalArgumentException.class,()->image.write(range(0,4),ByteImage.literal(new Values.BytesValue(List.of(1,2)),1)));
    }
    @Test void copySupportsAloneChangeEqualityAndConvergeAsStaticSets() {
        var initial=ByteImage.literal(new Values.BytesValue(List.of(65,66,67,68)),1);
        var first=initial.copied(2);assertNotEquals(initial,first);
        assertEquals(initial.read(range(0,4)).bytes(),first.read(range(0,4)).bytes());
        assertSame(first,first.copied(2));
        var both=first.copied(3);assertNotEquals(first,both);assertSame(both,both.copied(2).copied(3));
        assertEquals(Set.of(2,3),both.parts().getFirst().copies());
        var shifted=initial;
        for(int i=0;i<30;i++)shifted=shifted.write(range(1,3),shifted.slice(range(0,3)).copied(4));
        assertEquals(List.of(65,65,65,65),shifted.read(range(0,4)).bytes().orElseThrow().octets());
        for(var part:shifted.parts()){assertTrue(part.producerOffset().compareTo(BigInteger.valueOf(4))<0);assertTrue(part.payloadOffset()<4);}
        assertEquals(shifted,shifted.write(range(1,3),shifted.slice(range(0,3)).copied(4)));
    }
    @Test void capturedSourceGapSplitsOnlyItsRangeAndSurvivesUnknownIndependently() {
        var initial=ByteImage.literal(new Values.BytesValue(List.of(65,66,67,68,69,70,71,72)),1);
        var marked=initial.withSourceGap(range(0,4),7);assertNotEquals(initial,marked);
        assertEquals(initial.read(range(0,8)).bytes(),marked.read(range(0,8)).bytes());
        assertSame(marked,marked.withSourceGap(range(0,4),7));assertEquals(2,marked.parts().size());
        assertEquals(Set.of(7),marked.slice(range(0,4)).copied(2).parts().getFirst().sourceGaps());
        assertEquals(Set.of(),marked.slice(range(4,4)).copied(2).parts().getFirst().sourceGaps());
        var repaired=marked.write(range(0,4),initial.slice(range(0,4)));assertEquals(initial,repaired);
        var unknown=ByteImage.unknown(Optional.of(BigInteger.valueOf(8)),"UNKNOWN").withSourceGap(range(2,3),7);
        assertTrue(unknown.read(range(0,8)).bytes().isEmpty());assertEquals(Set.of("UNKNOWN"),unknown.read(range(0,8)).reasons());
        assertEquals(Set.of(7),unknown.slice(range(2,3)).parts().getFirst().sourceGaps());assertTrue(unknown.slice(range(5,3)).parts().getFirst().sourceGaps().isEmpty());
    }
}
