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
        blockText(length,block,(offset,destination,count)->source.readCharacters(reference,offset,destination,0,count));
    }
    /** Exact nominal dictionary payload, with no detached String projection. */
    void nominalText(io.github.gustavo2358.analysis.dependencies.SourceNominalCandidates source,long query,long candidate,char[] block)throws IOException {
        long length=source.rawLength(query,candidate);if(block.length==0)throw new IllegalArgumentException("nominal text buffer");
        blockText(length,block,(offset,destination,count)->source.readRaw(query,candidate,offset,destination,0,count));
    }
    void programText(io.github.gustavo2358.analysis.dependencies.SourceProgramSnapshot source,long item,long candidate,boolean raw,char[] block)throws IOException {
        long length=raw?source.rawLength(item,candidate):source.referenceLength(item,candidate);
        blockText(length,block,(offset,destination,count)->{if(raw)source.readRaw(item,candidate,offset,destination,0,count);else source.readReference(item,candidate,offset,destination,0,count);});
    }
    void programReason(io.github.gustavo2358.analysis.dependencies.SourceProgramSnapshot source,long item,long ordinal,char[] block)throws IOException {
        blockText(source.reasonLength(item,ordinal),block,(offset,destination,count)->source.readReason(item,ordinal,offset,destination,0,count));
    }
    void globalProgramReason(io.github.gustavo2358.analysis.dependencies.SourceProgramSnapshot source,long ordinal,char[] block)throws IOException {
        blockText(source.globalReasonLength(ordinal),block,(offset,destination,count)->source.readGlobalReason(ordinal,offset,destination,0,count));
    }
    void programMetricName(io.github.gustavo2358.analysis.dependencies.SourceProgramSnapshot source,long ordinal,char[] block)throws IOException {
        blockText(source.metricNameLength(ordinal),block,(offset,destination,count)->source.readMetricName(ordinal,offset,destination,0,count));
    }
    /** Same closed WireIds contract/lexical field order from borrowed text extents.
     * This does not admit the other sealed codec IDs into the published wire. */
    void identity(io.github.gustavo2358.analysis.dependencies.PagedDependencyFactStorage.IdentityView view,char[] block)throws IOException {
        String domain=view.domain();boolean unit=switch(domain){case "entry","label","object","operation","operand"->true;default->false;};
        switch(domain){case "publication","unit","entry","label","object","operation","resource","storage","origin","premise","operand","uncertainty"->{}default->throw new IllegalArgumentException("ID outside result contract");}
        identityObject(view,domain,io.github.gustavo2358.analysis.dependencies.PagedDependencyFactStorage.IdentityPart.LOCAL,domain.equals("operand"),unit,domain.equals("publication"),block);
    }
    /** DependencyJson's pre-existing artifact exception,not new WireIds admission. */
    void dependencyIdentity(io.github.gustavo2358.analysis.dependencies.PagedDependencyFactStorage.IdentityView view,char[] block)throws IOException {
        if(view==null){value(null);return;}
        if(view.domain().equals("artifact"))identityObject(view,"artifact",io.github.gustavo2358.analysis.dependencies.PagedDependencyFactStorage.IdentityPart.LOCAL,false,false,false,block);
        else identity(view,block);
    }
    void factText(io.github.gustavo2358.analysis.dependencies.PagedDependencyFactStorage.TextView view,char[] block)throws IOException {
        if(block.length==0)throw new IllegalArgumentException("fact text buffer");
        blockText(view.characterCount(),block,(offset,destination,count)->view.readCharacters(offset,destination,0,count),true);
    }
    void factCandidateText(io.github.gustavo2358.analysis.dependencies.PagedDependencyFactStorage.CandidateCursor view,boolean raw,char[] block)throws IOException {
        if(block.length==0)throw new IllegalArgumentException("candidate text buffer");
        blockText(raw?view.rawLength():view.referenceLength(),block,(offset,destination,count)->{if(raw)view.readRaw(offset,destination,0,count);else view.readReference(offset,destination,0,count);},true);
    }
    private void identityObject(io.github.gustavo2358.analysis.dependencies.PagedDependencyFactStorage.IdentityView view,String domain,
            io.github.gustavo2358.analysis.dependencies.PagedDependencyFactStorage.IdentityPart local,boolean operand,boolean unit,boolean publication,char[] block)throws IOException {
        octet('{');string("domain");octet(':');string(domain);octet(',');string("localId");octet(':');identityText(view,local,block);
        if(operand){octet(',');string("owner");octet(':');String owner=switch(view.ownerKind()){case ENTRY->"entry";case OPERATION->"operation";case NONE->throw new IllegalStateException("operand owner absent");};identityObject(view,owner,io.github.gustavo2358.analysis.dependencies.PagedDependencyFactStorage.IdentityPart.OWNER,false,true,false,block);}
        if(!publication){octet(',');string("publication");octet(':');identityText(view,io.github.gustavo2358.analysis.dependencies.PagedDependencyFactStorage.IdentityPart.PUBLICATION,block);}
        if(unit){octet(',');string("unit");octet(':');identityText(view,io.github.gustavo2358.analysis.dependencies.PagedDependencyFactStorage.IdentityPart.UNIT,block);}octet('}');
    }
    private void identityText(io.github.gustavo2358.analysis.dependencies.PagedDependencyFactStorage.IdentityView view,
            io.github.gustavo2358.analysis.dependencies.PagedDependencyFactStorage.IdentityPart part,char[] block)throws IOException {
        if(block.length==0)throw new IllegalArgumentException("identity text buffer");
        blockText(view.characterCount(part),block,(offset,destination,count)->view.readCharacters(part,offset,destination,0,count));
    }
    @FunctionalInterface private interface CharacterReader {void read(long offset,char[] destination,int count);}
    private void blockText(long length,char[] block,CharacterReader reader)throws IOException {
        blockText(length,block,reader,false);
    }
    /** Fact strings preserve strict Unicode scalar wire admission; SOURCE's
     * independent escaped-code-unit contract remains unchanged. */
    private void blockText(long length,char[] block,CharacterReader reader,boolean strict)throws IOException {
        octet('"');char pending=0;
        for(long at=0;at<length;) {
            int count=(int)Math.min(block.length,length-at);reader.read(at,block,count);at+=count;
            for(int i=0;i<count;i++) {
                char c=block[i];
                if(pending!=0) {
                    if(Character.isLowSurrogate(c)) {
                        int cp=Character.toCodePoint(pending,c);octet(0xf0|(cp>>>18));octet(0x80|((cp>>>12)&63));octet(0x80|((cp>>>6)&63));octet(0x80|(cp&63));pending=0;continue;
                    }
                    if(strict)throw new IllegalArgumentException("invalid Unicode scalar");escapedUnit(pending);pending=0;
                }
                if(Character.isHighSurrogate(c)){pending=c;continue;}
                if(Character.isLowSurrogate(c)){if(strict)throw new IllegalArgumentException("invalid Unicode scalar");escapedUnit(c);continue;}
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
        if(pending!=0){if(strict)throw new IllegalArgumentException("invalid Unicode scalar");escapedUnit(pending);}octet('"');
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
