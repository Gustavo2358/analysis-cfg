package io.github.gustavo2358.analysis.values;

import java.lang.reflect.*;
import java.util.*;

/** Actual reachability walk with identity deduplication. Logical counts only, no physical heap claim. */
final class PlanningRetention {
    private PlanningRetention() { }
    static Map<String,Long> count(Object root) {
        var seen=Collections.newSetFromMap(new IdentityHashMap<Object,Boolean>());var todo=new ArrayDeque<Object>();todo.add(root);
        var counts=new TreeMap<String,Long>();
        while(!todo.isEmpty()) {
            var value=todo.remove();if(!seen.add(value))continue;var type=value.getClass();String name=type.getName();
            counts.merge(type.getSimpleName(),1L,Math::addExact);
            if(name.startsWith("io.github.gustavo2358.air.")||name.startsWith("io.github.gustavo2358.analysis.cfg."))continue;
            if(value instanceof Map<?,?> map){map.forEach((k,v)->{add(todo,k);add(todo,v);});continue;}
            if(value instanceof Iterable<?> values){values.forEach(v->add(todo,v));continue;}
            if(type.isArray()){if(!type.componentType().isPrimitive())for(int i=0;i<Array.getLength(value);i++)add(todo,Array.get(value,i));continue;}
            if(!name.startsWith("io.github.gustavo2358.analysis."))continue;
            for(var field:type.getDeclaredFields())if(!Modifier.isStatic(field.getModifiers())&&!field.getType().isPrimitive()) {
                try{field.setAccessible(true);add(todo,field.get(value));}catch(ReflectiveOperationException e){throw new AssertionError(e);}
            }
        }
        return counts;
    }
    private static void add(Deque<Object> todo,Object value){if(value!=null)todo.add(value);}
}
