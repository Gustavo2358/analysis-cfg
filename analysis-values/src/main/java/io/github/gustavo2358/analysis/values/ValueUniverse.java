package io.github.gustavo2358.analysis.values;

import io.github.gustavo2358.air.model.Values.TextValue;
import java.util.*;

/** Preparation-only interning; equality is exact Unicode scalar text, without normalization. */
final class ValueUniverse {
    private final Map<TextKey,Integer> ordinals=new HashMap<>();
    private final List<TextValue> values=new ArrayList<>();
    private final List<Candidates> singletons=new ArrayList<>();
    long poolHits,scalarsHashed;
    private record TextKey(String text,int hash) {
        @Override public int hashCode(){return hash;}
        @Override public boolean equals(Object other){return other instanceof TextKey k&&text.equals(k.text);}
    }
    Candidates intern(TextValue value,ValuesWork work) {
        int hash=0;String text=value.value();
        for(int offset=0;offset<text.length();) {
            int scalar=text.codePointAt(offset);offset+=Character.charCount(scalar);
            hash=31*hash+scalar; // Hash mixing is intentionally modular; counts/ordinals are checked.
            scalarsHashed=Math.incrementExact(scalarsHashed);
        }
        var key=new TextKey(text,hash);var existing=ordinals.get(key);
        if(existing!=null){poolHits=Math.incrementExact(poolHits);return singletons.get(existing);}
        int ordinal=values.size();Math.incrementExact(ordinal);
        var singleton=Candidates.singleton(ordinal,work);
        values.add(value);singletons.add(singleton);ordinals.put(key,ordinal);return singleton;
    }
    List<TextValue> materialize(Candidates candidates) {
        var selected=new ArrayList<TextValue>(candidates.size());
        for(int i=0;i<candidates.size();i++)selected.add(values.get(candidates.at(i)));
        selected.sort(Comparator.comparing(TextValue::value));return List.copyOf(selected);
    }
    int size(){return values.size();}
}
