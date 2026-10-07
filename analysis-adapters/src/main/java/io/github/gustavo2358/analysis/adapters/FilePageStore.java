package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.analysis.solver.AnalysisResources;
import io.github.gustavo2358.analysis.solver.PageStore;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Objects;
import java.util.zip.CRC32C;

/**
 * Disposable positional-I/O page arena. The cache and its primitive directory have fixed capacity;
 * cold-page offsets are computed, not retained in a cardinality-sized heap map. Generation-tagged
 * handles never repeat, while freed physical slots form a checksummed on-disk free list. Disk grows
 * with peak live pages, not historical allocation count. No cross-session recovery.
 */
public final class FilePageStore implements PageStore {
    static final int FILE_HEADER_BYTES = 32;
    static final int PAGE_HEADER_BYTES = 32;
    private static final int MAGIC = 0x50414731;
    private static final int LIVE = 1;
    private static final int RETIRED = 2;
    private static final long SLOT_MASK = 0xffffffffL;
    private static final long GENERATION_STEP = 1L << 32;
    private static final long LAST_GENERATION = Integer.MAX_VALUE;
    private final int pageBytes;
    private final long stride;
    private final AnalysisResources resources;
    private final AnalysisResources.Phase phase;
    private byte[][] pages;
    private ByteBuffer[] payloadBuffers;
    private long[] handles;
    private boolean[] dirty, referenced;
    private long[] directoryKeys;
    private int[] directorySlots, freeSlots;
    private final int mask;
    private ByteBuffer header;
    private CRC32C checksum;
    private AnalysisResources.Reservation resident, temporary, descriptor;
    private Path file;
    private FileChannel channel;
    private int clock, freeCount;
    private long issued, physical, freeHead, live, hits, misses, evictions, bytesRead, bytesWritten;
    private boolean cleaning;
    private boolean closed, failed;
    private Reason failedReason;

    public FilePageStore(Path directory, int pageBytes, int cachePages, AnalysisResources resources) {
        this(directory, pageBytes, cachePages, resources, AnalysisResources.Phase.DOMAIN);
    }

    public FilePageStore(Path directory, int pageBytes, int cachePages, AnalysisResources resources,
                         AnalysisResources.Phase phase) {
        Objects.requireNonNull(directory);
        this.resources = Objects.requireNonNull(resources);
        this.phase = Objects.requireNonNull(phase);
        if (pageBytes <= 0 || cachePages <= 0 || cachePages > (1 << 28))
            throw new IllegalArgumentException("positive page/cache sizes required");
        this.pageBytes = pageBytes;
        stride = (long) pageBytes + PAGE_HEADER_BYTES;
        int slots = 2;
        while (slots < (long) cachePages * 2) slots <<= 1;
        mask = slots - 1;
        // Conservative capacity including arrays, per-page headers, cache directory and control state.
        long capacity = Math.addExact(512, Math.addExact(Math.multiplyExact((long) cachePages,
                (long) pageBytes + 128), Math.multiplyExact((long) slots, 16)));
        try {
            resident = resources.reserve(AnalysisResources.Pool.RESIDENT, capacity, phase);
            descriptor = resources.reserve(AnalysisResources.Pool.OPEN_FILES, 1, phase);
            temporary = resources.reserve(AnalysisResources.Pool.TEMPORARY, FILE_HEADER_BYTES, phase);
            pages = new byte[cachePages][pageBytes];
            payloadBuffers = new ByteBuffer[cachePages];
            for(int slot=0;slot<cachePages;slot++)payloadBuffers[slot]=ByteBuffer.wrap(pages[slot]);
            handles = new long[cachePages];
            dirty = new boolean[cachePages]; referenced = new boolean[cachePages];
            directoryKeys = new long[slots]; directorySlots = new int[slots];
            freeSlots = new int[cachePages]; freeCount = cachePages;
            for (int slot = 0; slot < cachePages; slot++) freeSlots[slot] = slot;
            header = ByteBuffer.allocate(FILE_HEADER_BYTES); checksum = new CRC32C();
            file = Files.createTempFile(directory, "analysis-pages-", ".tmp");
            channel = FileChannel.open(file, StandardOpenOption.READ, StandardOpenOption.WRITE);
            header.putInt(MAGIC).putInt(2).putInt(pageBytes).putInt(PAGE_HEADER_BYTES).putLong(0).putLong(0).flip();
            writeFully(header, 0);
        } catch (IOException | RuntimeException exception) {
            cleanupConstruction(exception);
            if (exception instanceof IOException io) throw ioFailure(io);
            throw (RuntimeException) exception;
        }
    }

    @Override public int pageBytes() { return pageBytes; }
    Path backingFile() { return file; }

    @Override public synchronized long allocate() {
        open();
        if (issued == Long.MAX_VALUE) throw new Failure(Reason.INVALID_HANDLE, "page handle space exhausted");
        long page, nextFree = 0;
        boolean reuse = freeHead != 0;
        if (reuse) {
            readHeader(freeHead);
            long old = header.getLong(0);
            if (header.getInt(12) != 0 || (old >>> 32) == LAST_GENERATION)
                throw corruption("invalid free page state");
            nextFree = header.getLong(16);
            page = old + GENERATION_STEP;
        } else {
            if (physical == SLOT_MASK) throw new Failure(Reason.INVALID_HANDLE, "physical page address space exhausted");
            page = physical + 1;
            position(page); // Prove the complete range fits in a signed long before reserving disk.
            temporary.grow(stride, phase);
        }
        int slot = replacement();
        Arrays.fill(pages[slot], (byte) 0);
        handles[slot] = page; dirty[slot] = true; referenced[slot] = true;
        insert(page, slot);
        if (reuse) freeHead = nextFree; else physical++;
        issued++; live++;
        return page;
    }

    @Override public synchronized void readForCleanup(long page,int offset,byte[] target,int start,int length){
        boolean previous=cleaning;cleaning=true;try{read(page,offset,target,start,length);}finally{cleaning=previous;}
    }
    @Override public synchronized void releaseForCleanup(long page){
        boolean previous=cleaning;cleaning=true;try{release(page);}finally{cleaning=previous;}
    }
    private void transferWork(){if(cleaning)resources.cleanupWork(1,phase);else resources.work(1,phase);}

    @Override public synchronized void read(long page, int offset, byte[] target, int targetOffset, int length) {
        open();
        Objects.checkFromIndexSize(offset, length, pageBytes);
        Objects.checkFromIndexSize(targetOffset, length, Objects.requireNonNull(target).length);
        int slot = slot(page);
        System.arraycopy(pages[slot], offset, target, targetOffset, length);
    }

    @Override public synchronized void write(long page, int offset, byte[] source, int sourceOffset, int length) {
        open();
        Objects.checkFromIndexSize(offset, length, pageBytes);
        Objects.checkFromIndexSize(sourceOffset, length, Objects.requireNonNull(source).length);
        int slot = slot(page);
        System.arraycopy(source, sourceOffset, pages[slot], offset, length);
        if (length != 0) dirty[slot] = true;
    }

    @Override public synchronized void release(long page) {
        open();
        requireAddress(page);
        int slot = find(page);
        if (slot >= 0 && handles[slot] != page) throw new Failure(Reason.INVALID_HANDLE, "obsolete page generation");
        try {
            if (slot < 0) {
                readHeader(page);
                if (header.getLong(0) != page || header.getInt(12) != LIVE)
                    throw new Failure(Reason.INVALID_HANDLE, "released or obsolete page handle");
            }
            boolean reusable = (page >>> 32) != LAST_GENERATION;
            writeHeader(page, 0, reusable ? 0 : RETIRED, reusable ? freeHead : 0);
            if (reusable) freeHead = page & SLOT_MASK;
            if (slot >= 0) {
                remove(page);
                handles[slot] = 0; dirty[slot] = false; referenced[slot] = false;
                freeSlots[freeCount++] = slot;
            }
            live--;
        } catch (IOException io) { throw ioFailure(io); }
        // Slots remain charged until file deletion; reuse needs neither more disk nor a heap map.
    }

    @Override public synchronized void flush() {
        open();
        for (int slot = 0; slot < handles.length; slot++) flush(slot);
    }

    @Override public synchronized Statistics statistics() {
        return new Statistics(issued, live, hits, misses, evictions, bytesRead, bytesWritten);
    }

    private int slot(long page) {
        requireAddress(page);
        int cached = find(page);
        if (cached >= 0) {
            if (handles[cached] != page) throw new Failure(Reason.INVALID_HANDLE, "obsolete page generation");
            hits++; referenced[cached] = true; return cached;
        }
        misses++;
        long position = position(page);
        try {
            readHeader(page);
            int expected = header.getInt(8);
            if (header.getLong(0) != page || header.getInt(12) != LIVE)
                throw new Failure(Reason.INVALID_HANDLE, "released or obsolete page handle");
            int slot = replacement();
            readFully(payloadBuffers[slot].clear(), position + PAGE_HEADER_BYTES);
            if (crc(pages[slot]) != expected) throw corruption("page checksum mismatch");
            handles[slot] = page; referenced[slot] = true; dirty[slot] = false;
            insert(page, slot);
            return slot;
        } catch (IOException io) { throw ioFailure(io); }
    }

    private int replacement() {
        if (freeCount != 0) return freeSlots[--freeCount];
        while (referenced[clock]) { referenced[clock] = false; clock = (clock + 1) % handles.length; }
        int slot = clock; clock = (clock + 1) % handles.length;
        flush(slot);
        remove(handles[slot]); handles[slot] = 0; evictions++;
        return slot;
    }

    private void flush(int slot) {
        if (!dirty[slot] || handles[slot] == 0) return;
        try {
            long position = position(handles[slot]);
            writeFully(payloadBuffers[slot].clear(), position + PAGE_HEADER_BYTES);
            writeHeader(handles[slot], crc(pages[slot]), LIVE, 0);
            dirty[slot] = false; // Only after both successful writes.
        } catch (IOException io) { throw ioFailure(io); }
    }

    private int crc(byte[] data) { checksum.reset(); checksum.update(data, 0, data.length); return (int) checksum.getValue(); }

    private void requireAddress(long page) {
        long slot = page & SLOT_MASK;
        if (page <= 0 || slot == 0 || slot > physical)
            throw new Failure(Reason.INVALID_HANDLE, "unknown page address");
    }

    private void writeHeader(long page, int payloadCrc, int state, long nextFree) throws IOException {
        header.clear().limit(PAGE_HEADER_BYTES);
        header.putLong(page).putInt(payloadCrc).putInt(state).putLong(nextFree).putInt(0).putInt(0);
        header.putInt(24, crc(header.array())); header.flip();
        writeFully(header, position(page));
    }

    private void readHeader(long page) {
        try {
            header.clear().limit(PAGE_HEADER_BYTES);
            readFully(header, position(page)); header.flip();
            int expected = header.getInt(24); header.putInt(24, 0);
            if (crc(header.array()) != expected) throw corruption("page header checksum mismatch");
            long stored = header.getLong(0), next = header.getLong(16);
            int state = header.getInt(12);
            if (stored <= 0 || (stored & SLOT_MASK) != (page & SLOT_MASK)
                    || header.getInt(28) != 0 || state < 0 || state > RETIRED
                    || next < 0 || next > physical || next != 0 && next == (page & SLOT_MASK)
                    || state != 0 && next != 0 || state != LIVE && header.getInt(8) != 0)
                throw corruption("page header identity or state mismatch");
        } catch (IOException io) { throw ioFailure(io); }
    }

    private long position(long page) {
        try {
            long position = Math.addExact(FILE_HEADER_BYTES, Math.multiplyExact((page & SLOT_MASK) - 1, stride));
            Math.addExact(position, stride);
            return position;
        } catch (ArithmeticException overflow) {
            throw new Failure(Reason.INVALID_HANDLE, "page offset exceeds signed 64-bit range", overflow);
        }
    }

    private void readFully(ByteBuffer buffer, long position) throws IOException {
        while (buffer.hasRemaining()) {
            transferWork();
            int count = channel.read(buffer, position);
            if (count < 0) throw corruption("truncated page");
            position += count; bytesRead += count;
        }
    }

    private void writeFully(ByteBuffer buffer, long position) throws IOException {
        while (buffer.hasRemaining()) {
            transferWork();
            int count = channel.write(buffer, position);
            position += count; bytesWritten += count;
        }
    }

    private int bucket(long key) {
        key ^= key >>> 33; key *= 0xff51afd7ed558ccdl;
        key ^= key >>> 33; key *= 0xc4ceb9fe1a85ec53l;
        return (int) (key ^ (key >>> 33)) & mask;
    }
    private int find(long key) {
        key &= SLOT_MASK;
        int at = bucket(key);
        for (int n = 0; n < directoryKeys.length; n++, at = (at + 1) & mask) {
            if (directoryKeys[at] == 0) return -1;
            if (directoryKeys[at] == key) return directorySlots[at];
        }
        return -1;
    }
    private void insert(long key, int slot) {
        key &= SLOT_MASK;
        int at = bucket(key);
        while (directoryKeys[at] != 0) at = (at + 1) & mask;
        directoryKeys[at] = key; directorySlots[at] = slot;
    }
    private void remove(long key) {
        key &= SLOT_MASK;
        int at = bucket(key);
        for (int n = 0; n < directoryKeys.length; n++, at = (at + 1) & mask) {
            if (directoryKeys[at] == 0) break;
            if (directoryKeys[at] == key) {
                // Close the probe cluster. Tombstones would eventually consume every bucket,
                // making each cache miss scan the entire cache despite a <= 50% live load.
                int hole = at;
                at = (at + 1) & mask;
                while (directoryKeys[at] != 0) {
                    int home = bucket(directoryKeys[at]);
                    if (((hole - home) & mask) < ((at - home) & mask)) {
                        directoryKeys[hole] = directoryKeys[at];
                        directorySlots[hole] = directorySlots[at];
                        hole = at;
                    }
                    at = (at + 1) & mask;
                }
                directoryKeys[hole] = 0;
                return;
            }
        }
        throw new IllegalStateException("page absent from cache directory");
    }

    private void open() {
        if (closed) throw new Failure(Reason.CLOSED, "page store is closed");
        if (failed) throw new Failure(failedReason, "page store previously failed");
    }
    private Failure corruption(String message) { failed = true; failedReason = Reason.CORRUPT; return new Failure(Reason.CORRUPT, message); }
    private Failure ioFailure(IOException cause) { failed = true; failedReason = Reason.IO; return new Failure(Reason.IO, "page I/O failed", cause); }

    private void cleanupConstruction(Exception original) {
        if (channel != null) try { channel.close(); } catch (IOException cleanup) { original.addSuppressed(cleanup); }
        boolean deleted = file == null;
        if (file != null) try { Files.deleteIfExists(file); deleted = true; } catch (IOException cleanup) { original.addSuppressed(cleanup); }
        if (resident != null) resident.close();
        if (descriptor != null) descriptor.close();
        if (temporary != null && deleted) temporary.close();
    }

    @Override public synchronized void close() {
        if (closed) return;
        RuntimeException error = null;
        // This file is temporary and deleted below. Flushing discarded payload
        // would spend analysis quota and create avoidable failure during teardown.
        closed = true;
        try { channel.close(); } catch (IOException exception) {
            if (error == null) error = ioFailure(exception); else error.addSuppressed(exception);
        }
        boolean deleted = false;
        try { Files.deleteIfExists(file); deleted = true; } catch (IOException exception) {
            if (error == null) error = ioFailure(exception); else error.addSuppressed(exception);
        }
        resident.close(); descriptor.close();
        // Closing transfers no page graph to the caller; even a retained closed adapter is small.
        pages = null; payloadBuffers = null; handles = null; dirty = null; referenced = null;live=0;
        directoryKeys = null; directorySlots = null; freeSlots = null;
        header = null; checksum = null;
        if (deleted) temporary.close(); // Failed cleanup must not pretend that disk was reclaimed.
        if (error != null) throw error;
    }
}
