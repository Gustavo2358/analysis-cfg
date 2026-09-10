package io.github.gustavo2358.analysis.adapters;

import java.io.*;
import java.nio.charset.*;
import java.util.*;

/** Only explicit JSON primitives/containers. No bean/record/enum/reflection serialization. */
final class JsonOutput {
    private final Writer writer;
    JsonOutput(OutputStream output) {
        writer=new BufferedWriter(new OutputStreamWriter(output,StandardCharsets.UTF_8.newEncoder()
            .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)));
    }
    void finish() throws IOException { writer.write('\n');writer.flush(); }
    void value(Object value) throws IOException {
        if(value==null)writer.write("null");
        else if(value instanceof String text)string(text);
        else if(value instanceof Boolean bool)writer.write(bool?"true":"false");
        else if(value instanceof Long number)writer.write(Long.toString(number));
        else if(value instanceof Integer number)writer.write(Integer.toString(number));
        else if(value instanceof Map<?,?> map) {
            writer.write('{');boolean first=true;
            var keys=new ArrayList<String>();for(var key:map.keySet())keys.add((String)key);keys.sort(String::compareTo);
            for(String key:keys) { if(!first)writer.write(',');first=false;string(key);writer.write(':');value(map.get(key)); }writer.write('}');
        } else if(value instanceof Iterable<?> list) {
            writer.write('[');boolean first=true;for(Object item:list){if(!first)writer.write(',');first=false;value(item);}writer.write(']');
        } else throw new IllegalArgumentException("unsupported explicit JSON value");
    }
    private void string(String text) throws IOException {
        writer.write('"');
        for(int i=0;i<text.length();i++) {
            char c=text.charAt(i);
            if(Character.isHighSurrogate(c)) {
                if(i+1==text.length()||!Character.isLowSurrogate(text.charAt(i+1)))throw new IllegalArgumentException("invalid Unicode scalar");
                writer.write(c);writer.write(text.charAt(++i));
            } else if(Character.isLowSurrogate(c))throw new IllegalArgumentException("invalid Unicode scalar");
            else switch(c) {
                case '"' -> writer.write("\\\"");case '\\' -> writer.write("\\\\");
                case '\b' -> writer.write("\\b");case '\f' -> writer.write("\\f");case '\n' -> writer.write("\\n");case '\r' -> writer.write("\\r");case '\t' -> writer.write("\\t");
                default -> { if(c<32){writer.write("\\u00");writer.write(Character.forDigit(c>>>4,16));writer.write(Character.forDigit(c&15,16));}else writer.write(c); }
            }
        }
        writer.write('"');
    }
}
