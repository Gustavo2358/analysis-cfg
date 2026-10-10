package io.github.gustavo2358.analysis.values;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.analysis.structure.ProgramStore;
import java.math.BigInteger;
import java.util.*;

/** Execution-owned physical octets; native words pack eight octets in the
 * already admitted column backend, never a boxed input-sized catalogue. */
final class BytePayloadArena implements AutoCloseable {
    private static final ProgramStore.ExecutionPhase PHASE=ProgramStore.ExecutionPhase.DOMAIN;
    private final ProgramStore.Structural program;
    private final Rows rows;
    private boolean closed;
    BytePayloadArena(ProgramStore.Structural program) {
        this.program=Objects.requireNonNull(program);var budget=program.executionBudget();
        if(budget.isEmpty()){rows=null;return;}
        var retained=budget.orElseThrow().reserve(ProgramStore.CapacityPool.RESIDENT,256,PHASE);
        ProgramStore.CapacityLease scratch=null;ProgramStore.OrdinalColumn column=null;
        try {
            scratch=budget.orElseThrow().reserve(ProgramStore.CapacityPool.SCRATCH,96,PHASE);
            column=program.storageInventory().orElseThrow().column(Long.MAX_VALUE);
            rows=new Rows(column,retained,scratch);retained.onOwnerClose(rows::close);
        } catch(RuntimeException|Error failure) {
            for(var resource:new AutoCloseable[]{column,scratch,retained})if(resource!=null)try{resource.close();}
            catch(Exception|Error cleanup){if(cleanup!=failure)failure.addSuppressed(cleanup);}throw failure;
        }
    }
    private static final class Rows implements AutoCloseable {
        private ProgramStore.OrdinalColumn column;
        private ProgramStore.CapacityLease retained,scratch;
        private int[] ibm;
        private long next;
        private Runnable observer;
        Rows(ProgramStore.OrdinalColumn column,ProgramStore.CapacityLease retained,ProgramStore.CapacityLease scratch){this.column=column;this.retained=retained;this.scratch=scratch;}
        void available(){if(column==null)throw new IllegalStateException("byte payload owner is closed");}
        @Override public void close() {
            var owned=column;var live=retained;var work=scratch;var expiry=observer;
            column=null;retained=null;scratch=null;ibm=null;observer=null;
            Throwable failure=null;if(expiry!=null)try{expiry.run();}catch(RuntimeException|Error error){failure=error;}
            for(var resource:new AutoCloseable[]{owned,work,live})if(resource!=null)try{resource.close();}
            catch(Exception|Error error){if(failure==null)failure=error;else if(failure!=error)failure.addSuppressed(error);}
            if(failure instanceof RuntimeException error)throw error;if(failure instanceof Error error)throw error;
            if(failure!=null)throw new IllegalStateException("byte payload cleanup failed",failure);
        }
    }
    private void available(){if(closed)throw new IllegalStateException("byte payload arena is closed");if(rows!=null)rows.available();}
    void onOwnerClose(Runnable observer){available();if(rows!=null){if(rows.observer!=null)throw new IllegalStateException("byte owner observer already installed");rows.observer=Objects.requireNonNull(observer);}}
    long wordsWritten(){available();return rows==null?0:rows.next;}
    Optional<ByteImage> encode(Values.TextValue text,Memory.Codec codec,BigInteger extent,int producer) {
        Objects.requireNonNull(text);Objects.requireNonNull(codec);Objects.requireNonNull(extent);available();
        if(extent.signum()<0)throw new IllegalArgumentException("negative extent");
        if(rows==null)return MemoryCodecs.encodeText(codec,text,extent).value().map(value->ByteImage.literal(value,producer));
        boolean ibm=MemoryCodecs.isIbm1047(codec),ascii=codec instanceof Memory.AsciiText;
        if(!ibm&&!ascii)return Optional.empty();
        String value=text.value();if(!extent.equals(BigInteger.valueOf(value.codePointCount(0,value.length()))))return Optional.empty();
        // No partially encoded semantic value on unsupported repertoire. This
        // scan uses the exact proved one-byte repertoire of the pinned codec.
        for(int at=0;at<value.length();at++) {
            if((at&4095)==0)program.progress(PHASE);
            if(value.charAt(at)>(ascii?127:255))return Optional.empty();
        }
        if(value.isEmpty())return Optional.of(ByteImage.literal(new Values.BytesValue(List.of()),producer));
        if(ibm&&rows.ibm==null) {
            rows.retained.grow(16+4L*256,PHASE);var table=new int[256];
            for(int scalar=0;scalar<256;scalar++)table[scalar]=MemoryCodecs.encodeText(codec,new Values.TextValue(String.valueOf((char)scalar)),BigInteger.ONE).value().orElseThrow().octets().getFirst();
            rows.ibm=table;
        }
        long start=rows.next,word=0;int pending=0,hash=1;
        for(int at=0;at<value.length();at++) {
            int octet=ibm?rows.ibm[value.charAt(at)]:value.charAt(at);
            word|=(long)octet<<(8*pending++);hash=31*hash+octet;
            if(pending==8){append(word);word=0;pending=0;}
        }
        if(pending!=0)append(word);
        rows.retained.grow(96,PHASE);
        return Optional.of(ByteImage.storedLiteral(new Octets(start,value.length(),hash),producer));
    }
    private void append(long word){rows.available();long next=Math.incrementExact(rows.next);if((rows.next&255)==0)program.progress(PHASE);rows.column.set(rows.next,word);rows.next=next;}
    static boolean stored(List<Integer> octets){return octets instanceof BytePayloadArena.Octets;}
    private final class Octets extends AbstractList<Integer> implements RandomAccess {
        private final long start;private final int size,hash;
        Octets(long start,int size,int hash){this.start=start;this.size=size;this.hash=hash;}
        @Override public int size(){available();return size;}
        @Override public Integer get(int ordinal){available();Objects.checkIndex(ordinal,size);return (int)((rows.column.get(start+ordinal/8)>>>(8*(ordinal&7)))&255);}
        @Override public int hashCode(){return hash;}
    }
    @Override public void close(){if(closed)return;closed=true;if(rows!=null)rows.close();}
}
