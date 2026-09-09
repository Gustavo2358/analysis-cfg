package io.github.gustavo2358.analysis.structure;

import java.lang.reflect.*;
import java.util.*;

/** Test-only identity-deduplicated walk. Counts logical containers, not JVM map backing nodes/bytes. */
final class RetentionWalk {
    private RetentionWalk() { }
    static Set<Object> walk(Object... roots) throws IllegalAccessException {
        Set<Object> seen=Collections.newSetFromMap(new IdentityHashMap<>());
        ArrayDeque<Object> pending=new ArrayDeque<>(Arrays.asList(roots));
        while(!pending.isEmpty()) {
            Object value=pending.removeLast(); if(!seen.add(value)) continue;
            if(value instanceof Map<?,?> map) {
                map.forEach((k,v)->{if(k!=null)pending.add(k);if(v!=null)pending.add(v);});
            } else if(value instanceof Collection<?> collection) {
                for(Object child:collection) if(child!=null) pending.add(child);
            } else if(value instanceof Optional<?> optional) {
                optional.ifPresent(pending::add);
            } else if(value instanceof Object[] array) {
                for(Object child:array) if(child!=null) pending.add(child);
            } else if(value.getClass().getName().startsWith("io.github.") && !value.getClass().isEnum()) {
                for(Field field:value.getClass().getDeclaredFields()) {
                    if(Modifier.isStatic(field.getModifiers()) || field.getType().isPrimitive()) continue;
                    field.setAccessible(true); Object child=field.get(value); if(child!=null) pending.add(child);
                }
            }
        }
        return seen;
    }
    static Map<String,Long> additional(Object root, Set<Object> baseline) throws IllegalAccessException {
        Map<String,Long> counts=new TreeMap<>();
        for(Object value:walk(root)) {
            if(baseline.contains(value)) continue;
            String name=value.getClass().getName(); counts.merge(name,1L,Math::addExact);
            if(value.getClass().isArray()) counts.merge("arraySlots",(long)Array.getLength(value),Math::addExact);
        }
        return counts;
    }
}
