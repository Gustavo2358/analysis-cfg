package io.github.gustavo2358.analysis.values;

import java.lang.reflect.*;
import java.util.*;

/** Counts actual reachable logical objects with identity deduplication; never calls these heap bytes. */
final class RetentionAudit {
    private RetentionAudit() { }
    static Map<String,Long> count(Object root) {
        var seen=Collections.newSetFromMap(new IdentityHashMap<Object,Boolean>());var todo=new ArrayDeque<Object>();todo.add(root);
        var result=new TreeMap<String,Long>();
        var candidateArrays=Collections.newSetFromMap(new IdentityHashMap<Object,Boolean>());
        var supportArrays=Collections.newSetFromMap(new IdentityHashMap<Object,Boolean>());
        while(!todo.isEmpty()) {
            Object value=todo.remove();if(!seen.add(value))continue;
            var type=value.getClass();var name=type.getName();
            if(name.startsWith("io.github.gustavo2358.air.")||name.startsWith("io.github.gustavo2358.analysis.structure.")||name.startsWith("io.github.gustavo2358.analysis.cfg."))continue;
            result.merge(type.getSimpleName(),1L,Math::addExact);
            if(value instanceof Candidates c)payload(value,c.size(),candidateArrays,result,"setElementsRetained");
            if(value instanceof SupportSet s)payload(value,s.size(),supportArrays,result,"supportElementsRetained");
            if(value instanceof Map<?,?> map){for(var e:map.entrySet()){add(todo,e.getKey());add(todo,e.getValue());}continue;}
            if(value instanceof Iterable<?> iterable){for(var item:iterable)add(todo,item);continue;}
            if(type.isArray()){
                result.merge("arraySlots",(long)Array.getLength(value),Math::addExact);
                if(!type.componentType().isPrimitive())for(int i=0;i<Array.getLength(value);i++)add(todo,Array.get(value,i));continue;
            }
            if(!name.startsWith("io.github.gustavo2358.analysis."))continue;
            for(var field:type.getDeclaredFields())if(!Modifier.isStatic(field.getModifiers())&&!field.getType().isPrimitive()) {
                try{field.setAccessible(true);add(todo,field.get(value));}catch(ReflectiveOperationException e){throw new AssertionError(e);}
            }
        }
        long bytes=24L*result.getOrDefault("PossibleValuesState",0L)+40L*result.getOrDefault("Node",0L)+32L*result.getOrDefault("Candidates",0L)+24L*result.getOrDefault("SupportSet",0L);
        for(Object value:seen)if(value instanceof int[] a)bytes+=8L*((23L+4L*a.length)/8L);
        result.put("stateBytesRetainedEstimate",bytes);
        long supportBytes=24L*result.getOrDefault("SupportSet",0L);
        for(var a:supportArrays)supportBytes+=8L*((23L+4L*((int[])a).length)/8L);
        result.put("supportBytesRetainedEstimate",supportBytes);
        return result;
    }
    private static void payload(Object value,int size,Set<Object> arrays,Map<String,Long> result,String key) {
        try {
            var field=value.getClass().getDeclaredField("many");field.setAccessible(true);var array=field.get(value);
            if(array==null||arrays.add(array))result.merge(key,(long)size,Math::addExact);
        } catch(ReflectiveOperationException e){throw new AssertionError(e);}
    }
    static int nodeCount(PersistentBindings.Node n){return n==null?0:1+nodeCount(n.left)+nodeCount(n.right);}
    static int shared(PersistentBindings.Node a,PersistentBindings.Node b){var seen=Collections.newSetFromMap(new IdentityHashMap<PersistentBindings.Node,Boolean>());collect(a,seen);return shared(b,seen);}
    private static void collect(PersistentBindings.Node n,Set<PersistentBindings.Node> seen){if(n!=null&&seen.add(n)){collect(n.left,seen);collect(n.right,seen);}}
    private static int shared(PersistentBindings.Node n,Set<PersistentBindings.Node> seen){return n==null?0:(seen.contains(n)?1:0)+shared(n.left,seen)+shared(n.right,seen);}
    static boolean valid(PersistentBindings.Node n){return n==null||(Math.abs(PersistentBindings.height(n.left)-PersistentBindings.height(n.right))<=1&&n.size==1+PersistentBindings.size(n.left)+PersistentBindings.size(n.right)&&valid(n.left)&&valid(n.right));}
    private static void add(Deque<Object> todo,Object value){if(value!=null)todo.add(value);}
}
