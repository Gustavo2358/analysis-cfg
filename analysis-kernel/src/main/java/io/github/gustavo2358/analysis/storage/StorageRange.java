package io.github.gustavo2358.analysis.storage;

import java.math.BigInteger;
import java.util.Objects;
import java.util.Optional;

/** Physical octet interval. An absent end is a safe unknown tail, not an allocation fact. */
public record StorageRange(BigInteger start, Optional<BigInteger> end) {
    public StorageRange {
        Objects.requireNonNull(start); Objects.requireNonNull(end);
        if (start.signum() < 0 || end.isPresent() && end.get().compareTo(start) < 0)
            throw new IllegalArgumentException("negative or reversed storage interval");
    }
    public static StorageRange exact(BigInteger start, BigInteger length) {
        if (length.signum() < 0) throw new IllegalArgumentException("negative extent");
        return new StorageRange(start, Optional.of(start.add(length)));
    }
    public boolean empty() { return end.isPresent() && start.equals(end.get()); }
    public boolean contains(StorageRange other) {
        return start.compareTo(other.start) <= 0
                && (end.isEmpty() || other.end.isPresent() && end.get().compareTo(other.end.get()) >= 0);
    }
    public Optional<StorageRange> intersect(StorageRange other) {
        var from=start.max(other.start);
        var until=end.isEmpty()?other.end:other.end.isEmpty()?end:Optional.of(end.get().min(other.end.get()));
        return until.isPresent() && until.get().compareTo(from)<=0 ? Optional.empty()
                : Optional.of(new StorageRange(from,until));
    }
}
