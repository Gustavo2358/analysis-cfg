package io.github.gustavo2358.analysis.solver;

import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/** Identity-keyed condition roots. One stable root token per live binding;
 * updates bind that token and never scan other keys or the historical catalog. */
final class ConditionBindings<K> extends AbstractMap<K,Integer> {
    private final BooleanConditions conditions;
    private final IdentityHashMap<K,Binding<K>> bindings=new IdentityHashMap<>();
    private static final class Binding<K> implements Map.Entry<K,Integer> {
        final K key;final long root;int condition;
        Binding(K key,int condition,long root){this.key=key;this.condition=condition;this.root=root;}
        public K getKey(){return key;}
        public Integer getValue(){return condition;}
        public Integer setValue(Integer value){throw new UnsupportedOperationException("use owned binding update");}
    }
    ConditionBindings(BooleanConditions conditions){this.conditions=conditions;}
    @Override public Integer get(Object key){var value=bindings.get(key);return value==null?null:value.condition;}
    @Override public boolean containsKey(Object key){return bindings.containsKey(key);}
    @Override public int size(){return bindings.size();}
    @Override public Integer put(K key,Integer condition){
        if(condition==null)throw new NullPointerException("condition");
        var old=bindings.get(key);
        if(old!=null){int previous=old.condition;if(old.root!=0)conditions.bindRoot(old.root,condition);old.condition=condition;return previous;}
        long root=conditions.ownershipEnabled()?conditions.retainRoot(condition):0;
        bindings.put(key,new Binding<>(key,condition,root));return null;
    }
    @Override public Integer remove(Object key){
        var old=bindings.get(key);if(old==null)return null;
        if(old.root!=0)conditions.releaseRoot(old.root);bindings.remove(key);return old.condition;
    }
    @Override public void clear(){for(var value:bindings.values())if(value.root!=0)conditions.releaseRoot(value.root);bindings.clear();}
    @Override public Set<Map.Entry<K,Integer>> entrySet(){
        return new AbstractSet<>(){
            public int size(){return bindings.size();}
            public Iterator<Map.Entry<K,Integer>> iterator(){
                var iterator=bindings.values().iterator();
                return new Iterator<>(){
                    public boolean hasNext(){return iterator.hasNext();}
                    public Map.Entry<K,Integer> next(){return iterator.next();}
                };
            }
        };
    }
}
