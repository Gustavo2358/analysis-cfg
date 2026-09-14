package io.github.gustavo2358.analysis.storage;

import java.math.BigInteger;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StorageRangeTest {
    static StorageRange range(long a,long b) { return StorageRange.exact(BigInteger.valueOf(a),BigInteger.valueOf(b-a)); }
    @Test void essentialPartialIntervalAndAdjacency() {
        assertEquals(Optional.of(range(0,4)),range(0,8).intersect(range(0,4)));
        assertEquals(Optional.empty(),range(0,4).intersect(range(4,8)));
        assertTrue(range(0,8).contains(range(4,8)));
        assertFalse(range(0,4).contains(range(0,8)));
        assertTrue(range(4,4).empty());
        assertTrue(range(4,4).intersect(range(0,8)).isEmpty());
    }
    @Test void unboundedTailAndArbitraryIntegerRemainDistinctFromZero() {
        var large=new BigInteger("184467440737095516170");
        var known=StorageRange.exact(large,BigInteger.TEN);
        var tail=new StorageRange(BigInteger.ZERO,Optional.empty());
        assertTrue(tail.contains(known)); assertFalse(known.contains(tail));
        assertEquals(Optional.of(known),tail.intersect(known));
        assertEquals(Optional.of(tail),tail.intersect(tail));
        assertThrows(IllegalArgumentException.class,()->StorageRange.exact(BigInteger.ZERO,BigInteger.valueOf(-1)));
    }
}
