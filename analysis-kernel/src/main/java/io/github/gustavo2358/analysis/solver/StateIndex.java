package io.github.gustavo2358.analysis.solver;

import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.function.ToLongFunction;

/** Solve-owned equivalence index. Fingerprints must be congruent with supplied equivalence;
 * collisions always consult that equivalence, never Object.equals. Primitive open addressing
 * caches fingerprints during growth. Values/keys are live roots until index close; retirement
 * is a separate solver operation. This resident bridge reserves capacity before allocation. */
final class StateIndex<S,V> implements AutoCloseable {
    private final AnalysisResources resources;
    private final AnalysisResources.Phase phase;
    private final AnalysisResources.Reservation metadata;
    private AnalysisResources.Reservation capacity;
    private final ToLongFunction<? super S> fingerprint;
    private final BiPredicate<? super S,? super S> equivalent;
    private Object[] keys,values;
    private long[] fingerprints;
    private int size;
    private long probes;
    private boolean closed;
    StateIndex(AnalysisResources resources,AnalysisResources.Phase phase,ToLongFunction<? super S> fingerprint,BiPredicate<? super S,? super S> equivalent) {
        this.resources=Objects.requireNonNull(resources);this.phase=Objects.requireNonNull(phase);
        this.fingerprint=Objects.requireNonNull(fingerprint);this.equivalent=Objects.requireNonNull(equivalent);
        metadata=resources.reserve(AnalysisResources.Pool.RESIDENT,192,phase);
    }
    static long mix(long value) {
        value=(value^(value>>>30))*0xbf58476d1ce4e5b9L;
        value=(value^(value>>>27))*0x94d049bb133111ebL;
        return value^(value>>>31);
    }
    @SuppressWarnings("unchecked")
    private int slot(S state,long hash) {
        int mask=keys.length-1,slot=(int)mix(hash)&mask;
        while(keys[slot]!=null) {
            probes++;
            if(fingerprints[slot]==hash&&equivalent.test((S)keys[slot],state))return slot;
            slot=(slot+1)&mask;
        }
        probes++;return slot;
    }
    @SuppressWarnings("unchecked") V get(S state) {
        open();Objects.requireNonNull(state);if(size==0)return null;
        int slot=slot(state,fingerprint.applyAsLong(state));return (V)values[slot];
    }
    @SuppressWarnings("unchecked") V putIfAbsent(S state,V value) {
        open();Objects.requireNonNull(state);Objects.requireNonNull(value);long hash=fingerprint.applyAsLong(state);
        if(keys==null)grow(4);
        int slot=slot(state,hash);if(keys[slot]!=null)return (V)values[slot];
        if(size>=keys.length/2){grow(Math.multiplyExact(keys.length,2));slot=slot(state,hash);}
        keys[slot]=state;values[slot]=value;fingerprints[slot]=hash;size++;return value;
    }
    private void grow(int length) {
        var staged=resources.reserve(AnalysisResources.Pool.RESIDENT,64L+24L*length,phase);
        try {
            Object[] newKeys=new Object[length],newValues=new Object[length];long[] newHashes=new long[length];int mask=length-1;
            if(keys!=null)for(int i=0;i<keys.length;i++)if(keys[i]!=null) {
                int slot=(int)mix(fingerprints[i])&mask;while(newKeys[slot]!=null){probes++;slot=(slot+1)&mask;}
                newKeys[slot]=keys[i];newValues[slot]=values[i];newHashes[slot]=fingerprints[i];
            }
            keys=newKeys;values=newValues;fingerprints=newHashes;if(capacity!=null)capacity.close();capacity=staged;
        }catch(RuntimeException|Error failure){staged.close();throw failure;}
    }
    int size(){open();return size;}long probes(){open();return probes;}
    private void open(){if(closed)throw new IllegalStateException("state index closed");}
    @Override public void close(){if(closed)return;closed=true;keys=null;values=null;fingerprints=null;if(capacity!=null)capacity.close();metadata.close();}
}
