package io.github.gustavo2358.analysis.values;
import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.analysis.storage.StorageRange;
import java.math.BigInteger;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class LogicalTextEncodingTest {
    @Test void billionPositionsUseTwoSpansWithExactOffsets() {
        var extent=BigInteger.valueOf(1000000000);
        var image=LogicalTextEncoding.encode(LogicalText.of("A").fit(extent.intValueExact(),' '),RegionalValuesTest.IBM,extent,7).orElseThrow();
        assertEquals(2,image.parts().size());
        assertEquals(List.of(0xc1,0x40,0x40),image.read(StorageRange.exact(BigInteger.ZERO,BigInteger.valueOf(3))).bytes().orElseThrow().octets());
        var tail=image.read(StorageRange.exact(extent.subtract(BigInteger.TWO),BigInteger.TWO));
        assertEquals(List.of(0x40,0x40),tail.bytes().orElseThrow().octets());
        assertEquals(extent.subtract(BigInteger.TWO),tail.parts().getFirst().producerOffset());
        assertEquals(7,tail.parts().getFirst().producer());
    }
    @Test void asciiBillionPositionsRetainTheExistingCodec() {
        var extent=BigInteger.valueOf(1000000000);
        var image=LogicalTextEncoding.encode(LogicalText.of("A").fit(extent.intValueExact(),' '),Memory.AsciiText.INSTANCE,extent,9).orElseThrow();
        assertEquals(2,image.parts().size());
        assertEquals(List.of(0x41,0x20,0x20),image.read(StorageRange.exact(BigInteger.ZERO,BigInteger.valueOf(3))).bytes().orElseThrow().octets());
        var tail=image.read(StorageRange.exact(extent.subtract(BigInteger.TWO),BigInteger.TWO));
        assertEquals(List.of(0x20,0x20),tail.bytes().orElseThrow().octets());
        assertEquals(extent.subtract(BigInteger.TWO),tail.parts().getFirst().producerOffset());
        assertEquals(9,tail.parts().getFirst().producer());
    }
    @Test void noImplicitEncodingOrReplacementForUnknownScalars() {
        assertTrue(LogicalTextEncoding.encode(LogicalText.unknown(1),RegionalValuesTest.IBM,BigInteger.ONE,0).isEmpty());
        assertTrue(LogicalTextEncoding.encode(LogicalText.of("😀"),RegionalValuesTest.IBM,BigInteger.ONE,0).isEmpty());
        assertTrue(LogicalTextEncoding.encode(LogicalText.of("A"),RegionalValuesTest.IBM,BigInteger.TWO,0).isEmpty());
        assertTrue(LogicalTextEncoding.encode(LogicalText.of("é"),Memory.AsciiText.INSTANCE,BigInteger.ONE,0).isEmpty());
    }
}
