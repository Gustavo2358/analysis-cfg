package io.github.gustavo2358.analysis.structure;

import io.github.gustavo2358.air.model.Ids.OperationId;
import java.util.*;

/** Working values addressed through the one admitted complete-ID directory.
 * Native tables retain no decoded keys. Values are still resident working metadata;
 * this table does not assert a spill backend for arbitrary values or solver state. */
public final class OperationTable<V> extends AbstractMap<OperationId,V> {
    private final ProgramStore.OperationDirectory directory;
    private final Map<OperationId,V> resident;
    private final Object[] values;
    private final BitSet present;
    private int count;
    public OperationTable(ProgramStore.Structural store){
        directory=Objects.requireNonNull(store).operationDirectory().orElse(null);
        resident=directory==null?new LinkedHashMap<>():null;
        values=directory==null?null:new Object[directory.size()];
        present=directory==null?null:new BitSet(values.length);
    }
    private void available(){if(directory!=null)directory.size();}
    private int ordinal(Object key){available();return key instanceof OperationId identity?directory.ordinal(identity):-1;}
    @SuppressWarnings("unchecked") private V value(int ordinal){return (V)values[ordinal];}
    @Override public int size(){available();return directory==null?resident.size():count;}
    @Override public boolean containsKey(Object key){
        if(directory==null)return resident.containsKey(key);int ordinal=ordinal(key);return ordinal>=0&&present.get(ordinal);
    }
    @Override public V get(Object key){
        if(directory==null)return resident.get(key);int ordinal=ordinal(key);return ordinal<0?null:value(ordinal);
    }
    @Override public V put(OperationId key,V value){
        Objects.requireNonNull(key);
        if(directory==null)return resident.put(key,value);
        int ordinal=ordinal(key);if(ordinal<0)throw new IllegalArgumentException("operation outside admitted directory");
        V previous=value(ordinal);values[ordinal]=value;
        if(!present.get(ordinal)){present.set(ordinal);count=Math.incrementExact(count);}return previous;
    }
    @Override public V remove(Object key){
        if(directory==null)return resident.remove(key);int ordinal=ordinal(key);if(ordinal<0||!present.get(ordinal))return null;
        V previous=value(ordinal);values[ordinal]=null;present.clear(ordinal);count--;return previous;
    }
    @Override public void clear(){available();if(directory==null){resident.clear();return;}Arrays.fill(values,null);present.clear();count=0;}
    /** Iterating values must not decode complete identity payloads merely to discard their keys. */
    @Override public Collection<V> values(){
        if(directory==null)return resident.values();available();return new AbstractCollection<>() {
            @Override public int size(){return OperationTable.this.size();}
            @Override public Iterator<V> iterator(){return rows(false);}
        };
    }
    private <T> Iterator<T> rows(boolean entries){
        available();return new Iterator<>() {
            private int next=present.nextSetBit(0),last=-1;
            @Override public boolean hasNext(){available();return next>=0;}
            @SuppressWarnings("unchecked") @Override public T next(){
                if(!hasNext())throw new NoSuchElementException();int ordinal=next;last=ordinal;next=present.nextSetBit(ordinal+1);
                return (T)(entries?new SimpleImmutableEntry<>(directory.identity(ordinal),value(ordinal)):value(ordinal));
            }
            @Override public void remove(){
                available();if(last<0)throw new IllegalStateException();
                values[last]=null;present.clear(last);count--;last=-1;
            }
        };
    }
    @Override public Set<Entry<OperationId,V>> entrySet(){
        if(directory==null)return resident.entrySet();available();return new AbstractSet<>() {
            @Override public int size(){return OperationTable.this.size();}
            @Override public Iterator<Entry<OperationId,V>> iterator(){return rows(true);}
        };
    }
}
