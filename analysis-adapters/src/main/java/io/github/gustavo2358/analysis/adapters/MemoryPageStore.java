package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.analysis.solver.AnalysisResources;
import io.github.gustavo2358.analysis.solver.PageStore;
import io.github.gustavo2358.analysis.solver.ResidentPageStore;

/** Outer resident adapter; the exact in-process page implementation belongs to the kernel. */
public final class MemoryPageStore implements PageStore {
    private final ResidentPageStore pages;
    public MemoryPageStore(int pageBytes, AnalysisResources resources) {
        this(pageBytes, resources, AnalysisResources.Phase.DOMAIN);
    }
    public MemoryPageStore(int pageBytes, AnalysisResources resources, AnalysisResources.Phase phase) {
        pages = new ResidentPageStore(pageBytes, resources, phase);
    }
    @Override public int pageBytes() { return pages.pageBytes(); }
    @Override public long allocate() { return pages.allocate(); }
    @Override public void read(long page, int offset, byte[] target, int start, int length) { pages.read(page, offset, target, start, length); }
    @Override public void readForCleanup(long page, int offset, byte[] target, int start, int length) { pages.readForCleanup(page, offset, target, start, length); }
    @Override public void write(long page, int offset, byte[] source, int start, int length) { pages.write(page, offset, source, start, length); }
    @Override public void release(long page) { pages.release(page); }
    @Override public void releaseForCleanup(long page) { pages.releaseForCleanup(page); }
    @Override public void flush() { pages.flush(); }
    @Override public Statistics statistics() { return pages.statistics(); }
    @Override public void close() { pages.close(); }
}
