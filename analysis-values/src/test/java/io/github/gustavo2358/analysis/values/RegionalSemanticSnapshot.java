package io.github.gustavo2358.analysis.values;

import java.lang.reflect.InvocationTargetException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

/** Test-only, lossless field walk. No record toString, producer erasure or hashCode keys.
 * Lists retain order; only Map/Set iteration order is normalized. Unsupported types fail closed.
 * Length-prefixed tokens keep empty/null, delimiters, types and field boundaries distinct.
 */
final class RegionalSemanticSnapshot {
    private RegionalSemanticSnapshot() { }
    static String encode(Object value) {
        var out=new StringBuilder();append(out,value);return out.toString();
    }
    static String digest(Object value) {
        try {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(encode(value).getBytes(StandardCharsets.UTF_8)));}
        catch(NoSuchAlgorithmException e){throw new AssertionError(e);}
    }
    private static void token(StringBuilder out,String value){out.append(value.length()).append(':').append(value);}
    private static void append(StringBuilder out,Object value) {
        if(value==null){token(out,"null");return;}
        if(value instanceof ByteImage image) {
            token(out,"ByteImage");append(out,image.extent());append(out,image.parts());return;
        }
        if(value instanceof Optional<?> optional) {
            token(out,optional.isPresent()?"present":"absent");optional.ifPresent(v->append(out,v));return;
        }
        if(value instanceof List<?> list) {
            token(out,"list");token(out,Integer.toString(list.size()));list.forEach(v->append(out,v));return;
        }
        if(value instanceof Set<?> set) {
            token(out,"set");token(out,Integer.toString(set.size()));set.stream().map(RegionalSemanticSnapshot::encode).sorted().forEach(v->token(out,v));return;
        }
        if(value instanceof Map<?,?> map) {
            token(out,"map");token(out,Integer.toString(map.size()));
            map.entrySet().stream().map(e->encode(List.of(e.getKey(),e.getValue()))).sorted().forEach(v->token(out,v));return;
        }
        var type=value.getClass();token(out,type.getName());
        if(value instanceof String s){token(out,s);return;}
        if(value instanceof Integer||value instanceof Long||value instanceof Boolean||value instanceof BigInteger) {token(out,value.toString());return;}
        if(value instanceof Enum<?> e){token(out,e.name());return;}
        if(!type.isRecord())throw new IllegalArgumentException("Unsupported snapshot type: "+type.getName());
        try {
            for(var field:type.getRecordComponents()) {
                token(out,field.getName());append(out,field.getAccessor().invoke(value));
            }
        } catch(IllegalAccessException|InvocationTargetException e){throw new AssertionError(e);}
    }
}
