package io.github.gustavo2358.analysis.application;

import io.github.gustavo2358.analysis.consumers.FactStorage;
import java.util.*;

/** Only the runtime can construct this committed, immutable transaction view. */
final class StoredFacts<F> extends AbstractList<F> implements RandomAccess {
    private final FactStorage<F> storage;
    private final int count;
    private final Runnable owner;
    StoredFacts(FactStorage<F> storage,Runnable owner) {
        this.storage=Objects.requireNonNull(storage);this.owner=Objects.requireNonNull(owner);
        owner.run();count=storage.size();if(count<0)throw new IllegalArgumentException("negative fact inventory");
    }
    private void open(){owner.run();if(storage.size()!=count)throw new IllegalStateException("committed fact storage changed");}
    @Override public int size(){open();return count;}
    @Override public F get(int ordinal){open();Objects.checkIndex(ordinal,count);return Objects.requireNonNull(storage.get(ordinal));}
    @Override public void clear(){throw new UnsupportedOperationException("immutable stored facts");}
    static <F> List<F> copyOrBorrow(List<F> facts){return facts instanceof StoredFacts<?>?facts:List.copyOf(facts);}
}
