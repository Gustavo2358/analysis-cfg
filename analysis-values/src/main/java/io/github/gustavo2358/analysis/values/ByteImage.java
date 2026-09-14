package io.github.gustavo2358.analysis.values;

import io.github.gustavo2358.air.model.Values;
import io.github.gustavo2358.analysis.storage.StorageRange;
import java.math.BigInteger;
import java.util.*;

/** One immutable byte image, represented by spans rather than one allocation per octet. */
final class ByteImage {
    record Part(StorageRange range,Optional<Values.BytesValue> payload,int payloadOffset,int producer,
                BigInteger producerOffset,Map<Integer,Set<BigInteger>> capturedOffsets,Set<String> reasons,Set<Integer> sourceGaps,Map<Integer,BigInteger> coInitial) {
        Part {
            Objects.requireNonNull(range);Objects.requireNonNull(payload);Objects.requireNonNull(producerOffset);coInitial=Map.copyOf(coInitial);
            var copies=new HashMap<Integer,Set<BigInteger>>();capturedOffsets.forEach((event,offsets)->{
                if(event<0||offsets.isEmpty()||offsets.stream().anyMatch(o->o.signum()<0))throw new IllegalArgumentException("invalid capture contribution");
                copies.put(event,Set.copyOf(offsets));
            });capturedOffsets=Map.copyOf(copies);reasons=Set.copyOf(reasons);sourceGaps=Set.copyOf(sourceGaps);
        }
        Part(StorageRange range,Optional<Values.BytesValue> payload,int payloadOffset,int producer,BigInteger producerOffset,Map<Integer,Set<BigInteger>> capturedOffsets,Set<String> reasons,Set<Integer> sourceGaps) {
            this(range,payload,payloadOffset,producer,producerOffset,capturedOffsets,reasons,sourceGaps,Map.of());
        }
        Map<Integer,BigInteger> contributors() {
            var all=new HashMap<>(coInitial);if(producer>=0)all.put(producer,producerOffset);return Map.copyOf(all);
        }
        Part contributor(int event,BigInteger offset) {
            return new Part(range,payload,payloadOffset,event,offset,capturedOffsets,reasons,sourceGaps);
        }
        Part(StorageRange range,Optional<Values.BytesValue> payload,int payloadOffset,int producer,BigInteger producerOffset,Map<Integer,Set<BigInteger>> capturedOffsets,Set<String> reasons) {
            this(range,payload,payloadOffset,producer,producerOffset,capturedOffsets,reasons,Set.of());
        }
        Set<Integer> copies(){return capturedOffsets.keySet();}
        Part crop(StorageRange slice) {
            var delta=slice.start().subtract(range.start());
            return new Part(slice,payload,payload.isPresent()?Math.addExact(payloadOffset,delta.intValueExact()):0,producer,
                payload.isPresent()?producerOffset.add(delta):BigInteger.ZERO,advance(capturedOffsets,delta),reasons,sourceGaps,advanceInitial(coInitial,delta));
        }
        Part shift(BigInteger offset) {
            return new Part(new StorageRange(range.start().add(offset),range.end().map(e->e.add(offset))),payload,payloadOffset,producer,producerOffset,capturedOffsets,reasons,sourceGaps,coInitial);
        }
    }
    record Read(Optional<Values.BytesValue> bytes,List<Part> parts,Set<String> reasons) {
        Read { parts=List.copyOf(parts);reasons=Set.copyOf(reasons); }
    }
    private final Optional<BigInteger> extent;
    private final List<Part> parts;
    private ByteImage(Optional<BigInteger> extent,List<Part> parts) {
        this.extent=Objects.requireNonNull(extent);this.parts=normalize(parts);
        extent.ifPresent(e->{if(e.signum()<0)throw new IllegalArgumentException("negative extent");});
        var cursor=Optional.of(BigInteger.ZERO);
        for(var part:this.parts) {
            if(cursor.isEmpty()||!cursor.get().equals(part.range().start())||part.range().empty())throw new IllegalArgumentException("image spans must cover the extent exactly");
            cursor=part.range().end();
            if(part.payload().isPresent()) {
                if(cursor.isEmpty()||part.producer()<0||!part.reasons().isEmpty()||part.payloadOffset()<0
                    ||BigInteger.valueOf(part.payloadOffset()).add(cursor.get().subtract(part.range().start())).compareTo(BigInteger.valueOf(part.payload().get().octets().size()))>0)
                    throw new IllegalArgumentException("known fragment must fit its immutable payload");
            } else if(part.reasons().isEmpty())throw new IllegalArgumentException("unknown fragment needs a reason");
        }
        if(!cursor.equals(extent))throw new IllegalArgumentException("image extent mismatch");
    }
    Optional<BigInteger> extent(){return extent;}
    List<Part> parts(){return parts;}
    static ByteImage unknown(Optional<BigInteger> extent,String reason) {return unknown(extent,reason,-1);}
    static ByteImage unknown(Optional<BigInteger> extent,String reason,int writer) {
        return new ByteImage(extent,extent.filter(e->e.signum()==0).isPresent()?List.of():List.of(new Part(new StorageRange(BigInteger.ZERO,extent),Optional.empty(),0,writer,BigInteger.ZERO,Map.of(),Set.of(reason))));
    }
    static ByteImage literal(Values.BytesValue bytes,int producer) {
        var size=BigInteger.valueOf(bytes.octets().size());
        return new ByteImage(Optional.of(size),size.signum()==0?List.of():List.of(new Part(StorageRange.exact(BigInteger.ZERO,size),Optional.of(bytes),0,producer,BigInteger.ZERO,Map.of(),Set.of())));
    }
    ByteImage slice(StorageRange selected) {
        requireBounds(selected);var result=new ArrayList<Part>();
        for(var part:parts)part.range().intersect(selected).ifPresent(r->result.add(part.crop(r).shift(selected.start().negate())));
        return new ByteImage(selected.end().map(e->e.subtract(selected.start())),result);
    }
    /** Total one-byte repertoire fit after the caller proves matching text codecs. */
    ByteImage fit(BigInteger length,int pad,int producer) {
        var size=extent.orElseThrow();var kept=size.min(length);
        var result=new ArrayList<>(slice(StorageRange.exact(BigInteger.ZERO,kept)).parts);
        if(length.compareTo(kept)>0) {
            var count=length.subtract(kept);
            var payload=new Values.BytesValue(Collections.nCopies(count.intValueExact(),pad));
            result.add(new Part(StorageRange.exact(kept,count),Optional.of(payload),0,producer,kept,Map.of(),Set.of()));
        }
        return new ByteImage(Optional.of(length),result);
    }
    ByteImage copied(int event) {return copied(event,BigInteger.ZERO);}
    ByteImage copied(int event,BigInteger sourceStart) {
        var result=new ArrayList<Part>();
        for(var part:parts) {
            var copies=new HashMap<>(part.capturedOffsets());var offsets=new HashSet<>(copies.getOrDefault(event,Set.of()));
            offsets.add(sourceStart.add(part.range().start()));copies.put(event,Set.copyOf(offsets));
            result.add(new Part(part.range(),part.payload(),part.payloadOffset(),part.producer(),part.producerOffset(),copies,part.reasons(),part.sourceGaps(),part.coInitial()));
        }
        var next=new ByteImage(extent,result);return equals(next)?this:next;
    }
    /** Source uncertainty belongs only to the captured intersection, independently of known bytes. */
    ByteImage withSourceGap(StorageRange selected,int gap) {
        requireBounds(selected);if(selected.empty())return this;var result=new ArrayList<Part>();
        for(var part:parts) {
            var overlap=part.range().intersect(selected);if(overlap.isEmpty()){result.add(part);continue;}
            var range=overlap.get();
            if(part.range().start().compareTo(range.start())<0)result.add(part.crop(new StorageRange(part.range().start(),Optional.of(range.start()))));
            var middle=part.crop(range);var gaps=new HashSet<>(middle.sourceGaps());gaps.add(gap);
            result.add(new Part(middle.range(),middle.payload(),middle.payloadOffset(),middle.producer(),middle.producerOffset(),middle.capturedOffsets(),middle.reasons(),gaps,middle.coInitial()));
            if(range.end().isPresent()&&(part.range().end().isEmpty()||range.end().get().compareTo(part.range().end().get())<0))
                result.add(part.crop(new StorageRange(range.end().get(),part.range().end())));
        }
        var next=new ByteImage(extent,result);return equals(next)?this:next;
    }
    /** Simultaneous consistent literals refine open bytes and union provenance at intersections. */
    ByteImage initialize(StorageRange destination,ByteImage replacement) {
        var prior=slice(destination);var merged=new ArrayList<Part>();int i=0,j=0;
        while(i<prior.parts.size()&&j<replacement.parts.size()) {
            var a=prior.parts.get(i);var b=replacement.parts.get(j);var intersection=a.range().intersect(b.range()).orElseThrow();
            var left=a.crop(intersection);var right=b.crop(intersection);
            if(left.payload().isPresent()&&right.payload().isPresent()) {
                var contributors=new HashMap<>(left.contributors());contributors.putAll(right.coInitial());contributors.remove(right.producer());
                right=new Part(right.range(),right.payload(),right.payloadOffset(),right.producer(),right.producerOffset(),right.capturedOffsets(),right.reasons(),right.sourceGaps(),contributors);
            }
            merged.add(right);
            if(a.range().end().equals(intersection.end()))i++;
            if(b.range().end().equals(intersection.end()))j++;
        }
        return write(destination,new ByteImage(replacement.extent,merged));
    }
    ByteImage write(StorageRange destination,ByteImage replacement) {
        requireBounds(destination);
        if(!replacement.extent.equals(destination.end().map(e->e.subtract(destination.start()))))throw new IllegalArgumentException("replacement extent differs from destination");
        if(destination.empty())return this;
        var result=new ArrayList<Part>();
        for(var part:parts) {
            var overlap=part.range().intersect(destination);
            if(overlap.isEmpty()){result.add(part);continue;}
            var r=overlap.get();
            if(part.range().start().compareTo(r.start())<0)result.add(part.crop(new StorageRange(part.range().start(),Optional.of(r.start()))));
            if(r.end().isPresent()&&(part.range().end().isEmpty()||r.end().get().compareTo(part.range().end().get())<0))
                result.add(part.crop(new StorageRange(r.end().get(),part.range().end())));
        }
        for(var part:replacement.parts)result.add(part.shift(destination.start()));
        result.sort(Comparator.comparing(p->p.range().start()));
        var next=new ByteImage(extent,result);return equals(next)?this:next;
    }
    Read read(StorageRange selected) {
        var image=slice(selected);var reasons=new HashSet<String>();image.parts.forEach(p->reasons.addAll(p.reasons()));
        if(image.parts.stream().anyMatch(p->p.payload().isEmpty()))return new Read(Optional.empty(),image.parts,reasons);
        var bytes=new ArrayList<Integer>();
        for(var part:image.parts) {
            int count=part.range().end().orElseThrow().subtract(part.range().start()).intValueExact();
            bytes.addAll(part.payload().orElseThrow().octets().subList(part.payloadOffset(),Math.addExact(part.payloadOffset(),count)));
        }
        return new Read(Optional.of(new Values.BytesValue(bytes)),image.parts,reasons);
    }
    private void requireBounds(StorageRange selected) {
        if(!new StorageRange(BigInteger.ZERO,extent).contains(selected))throw new IllegalArgumentException("range outside image");
    }
    private static Map<Integer,BigInteger> advanceInitial(Map<Integer,BigInteger> offsets,BigInteger delta) {
        if(delta.signum()==0||offsets.isEmpty())return offsets;var result=new HashMap<Integer,BigInteger>();offsets.forEach((k,v)->result.put(k,v.add(delta)));return Map.copyOf(result);
    }
    private static Map<Integer,Set<BigInteger>> advance(Map<Integer,Set<BigInteger>> captures,BigInteger delta) {
        if(delta.signum()==0||captures.isEmpty())return captures;var next=new HashMap<Integer,Set<BigInteger>>();
        captures.forEach((event,offsets)->{var shifted=new HashSet<BigInteger>();offsets.forEach(o->shifted.add(o.add(delta)));next.put(event,Set.copyOf(shifted));});return Map.copyOf(next);
    }
    private static List<Part> normalize(List<Part> parts) {
        var result=new ArrayList<Part>();
        for(var part:parts) {
            if(!result.isEmpty()) {
                var previous=result.getLast();
                boolean adjacent=previous.range().end().equals(Optional.of(part.range().start()));
                boolean metadata=advanceInitial(previous.coInitial(),part.range().start().subtract(previous.range().start())).equals(part.coInitial())&&previous.producer()==part.producer()&&advance(previous.capturedOffsets(),part.range().start().subtract(previous.range().start())).equals(part.capturedOffsets())&&previous.reasons().equals(part.reasons())&&previous.sourceGaps().equals(part.sourceGaps());
                boolean payload=previous.payload().equals(part.payload())&&(part.payload().isEmpty()
                    ||BigInteger.valueOf(previous.payloadOffset()).add(part.range().start().subtract(previous.range().start())).equals(BigInteger.valueOf(part.payloadOffset()))
                        &&previous.producerOffset().add(part.range().start().subtract(previous.range().start())).equals(part.producerOffset()));
                if(adjacent&&metadata&&payload) {
                    result.set(result.size()-1,new Part(new StorageRange(previous.range().start(),part.range().end()),previous.payload(),previous.payloadOffset(),previous.producer(),previous.producerOffset(),previous.capturedOffsets(),previous.reasons(),previous.sourceGaps(),previous.coInitial()));continue;
                }
            }
            result.add(part);
        }
        return List.copyOf(result);
    }
    @Override public boolean equals(Object other){return this==other||other instanceof ByteImage image&&extent.equals(image.extent)&&parts.equals(image.parts);}
    @Override public int hashCode(){return Objects.hash(extent,parts);}
}
