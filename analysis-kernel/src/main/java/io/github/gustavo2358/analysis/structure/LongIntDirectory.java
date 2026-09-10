package io.github.gustavo2358.analysis.structure;

/** Sparse primitive key directory. Empty value is -1; keys are pairs of private dense ordinals. */
final class LongIntDirectory {
    private long[] keys = new long[16];
    private int[] values = new int[16];
    private int size;

    int get(long key) {
        int slot = slot(key);
        return values[slot] - 1;
    }

    int put(long key, int value) {
        int encoded = Math.incrementExact(value);
        if (value < 0) throw new IllegalArgumentException("negative directory value");
        if (size == keys.length / 2) grow();
        int slot = slot(key);
        int old = values[slot] - 1;
        if (old == -1) size = Math.incrementExact(size);
        keys[slot] = key;
        values[slot] = encoded;
        return old;
    }

    private int slot(long key) {
        // Overflow in this hash mixer is intentional modular arithmetic, never ordinal/count arithmetic.
        long hash = key;
        hash = (hash ^ (hash >>> 33)) * 0xff51afd7ed558ccdl;
        hash = (hash ^ (hash >>> 33)) * 0xc4ceb9fe1a85ec53l;
        int slot = (int) (hash ^ (hash >>> 33)) & (keys.length - 1);
        while (values[slot] != 0 && keys[slot] != key) slot = (slot + 1) & (keys.length - 1);
        return slot;
    }

    private void grow() {
        long[] oldKeys = keys;
        int[] oldValues = values;
        int capacity = Math.multiplyExact(keys.length, 2);
        keys = new long[capacity];
        values = new int[capacity];
        for (int i = 0; i < oldValues.length; i++) {
            if (oldValues[i] != 0) {
                int slot = slot(oldKeys[i]);
                keys[slot] = oldKeys[i];
                values[slot] = oldValues[i];
            }
        }
    }

    static long key(int entry, int node) { return ((long) entry << 32) | node; }
}
