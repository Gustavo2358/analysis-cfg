package io.github.gustavo2358.analysis.adapters;

import java.io.*;
import java.util.*;
import java.util.stream.Stream;

/** Explicit JSON values, emitted directly into a bounded UTF-8 byte buffer. */
final class JsonOutput {
    private final OutputStream output;
    private final byte[] buffer=new byte[64*1024];
    private int used;
    JsonOutput(OutputStream output) { this.output=Objects.requireNonNull(output); }
    /** Explicit package-owned mapping into this buffer, not a detached value tree. */
    @FunctionalInterface interface EncodedValue {void write(JsonOutput output)throws IOException;}
    void finish() throws IOException { octet('\n');flush();output.flush(); }
    private void flush() throws IOException { output.write(buffer,0,used);used=0; }
    private void octet(int value) throws IOException { if(used==buffer.length)flush();buffer[used++]=(byte)value; }
    private void ascii(String value) throws IOException { for(int i=0;i<value.length();i++)octet(value.charAt(i)); }

    /** A single object's explicit fields, without map entries or a retained document tree. */
    static final class Fields {
        private Object[] pairs;
        Fields(Object... pairs) {
            this.pairs=pairs;
            for(int i=2;i<pairs.length;i+=2) {
                Object key=pairs[i],value=pairs[i+1];int j=i;
                while(j>0&&((String)pairs[j-2]).compareTo((String)key)>0) {
                    pairs[j]=pairs[j-2];pairs[j+1]=pairs[j-1];j-=2;
                }
                pairs[j]=key;pairs[j+1]=value;
            }
        }
        void put(String key,Object value) {
            int i=0;
            while(i<pairs.length&&((String)pairs[i]).compareTo(key)<0)i+=2;
            if(i<pairs.length&&pairs[i].equals(key)){pairs[i+1]=value;return;}
            pairs=Arrays.copyOf(pairs,pairs.length+2);
            System.arraycopy(pairs,i,pairs,i+2,pairs.length-i-2);pairs[i]=key;pairs[i+1]=value;
        }
        void write(JsonOutput out) throws IOException {
            out.octet('{');
            for(int i=0;i<pairs.length;i+=2){if(i>0)out.octet(',');out.string((String)pairs[i]);out.octet(':');out.value(pairs[i+1]);}
            out.octet('}');
        }
    }
    void value(Object value) throws IOException {
        if(value==null)ascii("null");
        else if(value instanceof String text)string(text);
        else if(value instanceof Boolean bool)ascii(bool?"true":"false");
        else if(value instanceof Long number)ascii(Long.toString(number));
        else if(value instanceof Integer number)ascii(Integer.toString(number));
        else if(value instanceof EncodedValue encoded)encoded.write(this);
        else if(value instanceof Fields fields)fields.write(this);
        else if(value instanceof Map<?,?> map) {
            octet('{');boolean first=true;
            var keys=new ArrayList<String>();for(var key:map.keySet())keys.add((String)key);keys.sort(String::compareTo);
            for(String key:keys){if(!first)octet(',');first=false;string(key);octet(':');value(map.get(key));}octet('}');
        } else if(value instanceof Stream<?> stream) {
            try(stream) { array(stream.iterator()); }
        } else if(value instanceof Iterable<?> list)array(list.iterator());
        else throw new IllegalArgumentException("unsupported explicit JSON value");
    }
    private void array(Iterator<?> items) throws IOException {
        octet('[');boolean first=true;
        while(items.hasNext()){if(!first)octet(',');first=false;value(items.next());}octet(']');
    }
    void symbol(char value)throws IOException {
        if("{}[],:".indexOf(value)<0)throw new IllegalArgumentException("JSON structural symbol");octet(value);
    }
    /** Borrowed fixed UTF16 block; SOURCE permits escaped isolated code units. */
    void sourceText(io.github.gustavo2358.analysis.dependencies.source.SourceSnapshot source,long reference,char[] block)throws IOException {
        sourceText(source,reference,source.characterCount(reference),block);
    }
    /** Exact interpreted prefix, leaving the arbitrarily large raw value in managed SOURCE. */
    void sourceText(io.github.gustavo2358.analysis.dependencies.source.SourceSnapshot source,long reference,long length,char[] block)throws IOException {
        if(length<0||length>source.characterCount(reference)||block.length==0)throw new IllegalArgumentException("SOURCE text prefix");
        octet('"');char pending=0;
        for(long at=0;at<length;) {
            int count=(int)Math.min(block.length,length-at);source.readCharacters(reference,at,block,0,count);at+=count;
            for(int i=0;i<count;i++) {
                char c=block[i];
                if(pending!=0) {
                    if(Character.isLowSurrogate(c)) {
                        int cp=Character.toCodePoint(pending,c);octet(0xf0|(cp>>>18));octet(0x80|((cp>>>12)&63));octet(0x80|((cp>>>6)&63));octet(0x80|(cp&63));pending=0;continue;
                    }
                    escapedUnit(pending);pending=0;
                }
                if(Character.isHighSurrogate(c)){pending=c;continue;}
                if(Character.isLowSurrogate(c)){escapedUnit(c);continue;}
                switch(c) {
                    case '"' -> ascii("\\\"");case '\\' -> ascii("\\\\");
                    case '\b' -> ascii("\\b");case '\f' -> ascii("\\f");case '\n' -> ascii("\\n");case '\r' -> ascii("\\r");case '\t' -> ascii("\\t");
                    default -> {
                        if(c<32)escapedUnit(c);
                        else if(c<128)octet(c);
                        else if(c<0x800){octet(0xc0|(c>>>6));octet(0x80|(c&63));}
                        else{octet(0xe0|(c>>>12));octet(0x80|((c>>>6)&63));octet(0x80|(c&63));}
                    }
                }
            }
        }
        if(pending!=0)escapedUnit(pending);octet('"');
    }
    private void escapedUnit(char value)throws IOException {
        ascii("\\u");for(int shift=12;shift>=0;shift-=4)octet(Character.forDigit((value>>>shift)&15,16));
    }
    void string(String text) throws IOException {
        octet('"');
        for(int i=0;i<text.length();i++) {
            char c=text.charAt(i);
            // Copy ordinary ASCII in blocks without Writer calls or intermediate byte arrays.
            if(c>=32&&c<128&&c!='"'&&c!='\\') {
                if(used==buffer.length)flush();
                do {
                    buffer[used++]=(byte)c;
                    if(used==buffer.length||i+1==text.length())break;
                    c=text.charAt(i+1);
                    if(c<32||c>=128||c=='"'||c=='\\')break;
                    i++;
                } while(true);
                continue;
            }
            switch(c) {
                case '"' -> ascii("\\\"");case '\\' -> ascii("\\\\");
                case '\b' -> ascii("\\b");case '\f' -> ascii("\\f");case '\n' -> ascii("\\n");case '\r' -> ascii("\\r");case '\t' -> ascii("\\t");
                default -> {
                    if(c<32){ascii("\\u00");octet(Character.forDigit(c>>>4,16));octet(Character.forDigit(c&15,16));}
                    else if(c<0x800){octet(0xc0|(c>>>6));octet(0x80|(c&63));}
                    else if(Character.isHighSurrogate(c)) {
                        if(i+1==text.length()||!Character.isLowSurrogate(text.charAt(i+1)))throw new IllegalArgumentException("invalid Unicode scalar");
                        int cp=Character.toCodePoint(c,text.charAt(++i));
                        octet(0xf0|(cp>>>18));octet(0x80|((cp>>>12)&63));octet(0x80|((cp>>>6)&63));octet(0x80|(cp&63));
                    } else if(Character.isLowSurrogate(c))throw new IllegalArgumentException("invalid Unicode scalar");
                    else {octet(0xe0|(c>>>12));octet(0x80|((c>>>6)&63));octet(0x80|(c&63));}
                }
            }
        }
        octet('"');
    }
}
