package io.github.gustavo2358.analysis.solver;

/** Domain-owned observations: report actual work inside callbacks, never execution budgets. */
public final class DomainWork {
    private long operations, joinEntries, compareEntries;
    public void operationTransferred() { operations = Math.incrementExact(operations); }
    public void joinEntryVisited() { joinEntries = Math.incrementExact(joinEntries); }
    public void stateCompareEntry() { compareEntries = Math.incrementExact(compareEntries); }
    long operations() { return operations; }
    long joinEntries() { return joinEntries; }
    long compareEntries() { return compareEntries; }
}
