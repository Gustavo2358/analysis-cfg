package io.github.gustavo2358.analysis.storage;

import io.github.gustavo2358.air.model.Ids.StorageId;
import io.github.gustavo2358.analysis.structure.ProgramStore;
import java.math.BigInteger;
import java.util.*;

/** Finite boundaries demanded by this program. Never materializes one cell per octet. */
public final class StoragePartition {
    public record Segment(int ordinal,StorageIndex.Location location) { }
    private final List<Segment> segments;
    private final Map<StorageId,List<Segment>> byBase;
    private final NativePartition nativePartition;
    public StoragePartition(StatementEffects effects) {
        var inventory=effects.storage().session().index().store().storageInventory();
        if(inventory.isPresent()){
            nativePartition=new NativePartition(effects,inventory.orElseThrow());
            segments=nativePartition.segments;byBase=null;return;
        }
        nativePartition=null;
        var storage=effects.storage();var boundaries=new LinkedHashMap<StorageId,TreeSet<BigInteger>>();
        for(var base:storage.bases()) {
            var set=new TreeSet<BigInteger>();var whole=storage.whole(base.header().id());
            whole.range().ifPresent(r->{set.add(r.start());r.end().ifPresent(set::add);});boundaries.put(base.header().id(),set);
        }
        for(var object:storage.declarations())for(var candidate:storage.object(object.id()).candidates())add(boundaries,candidate.location());
        for(var statement:effects.statements()) {
            for(var read:statement.reads())for(var candidate:read.location().candidates())add(boundaries,candidate.location());
            addWrites(boundaries,statement.writes());addWrites(boundaries,statement.otherwise());
            for(var writes:statement.outcomes().values())addWrites(boundaries,writes);
        }
        for(var context:storage.session().contexts())for(var condition:context.entry().state().conditions())
            for(var candidate:storage.resolve(condition.place()).candidates())add(boundaries,candidate.location());
        var all=new ArrayList<Segment>();var groups=new LinkedHashMap<StorageId,List<Segment>>();
        for(var base:storage.bases()) {
            var location=storage.whole(base.header().id());var group=new ArrayList<Segment>();
            if(location.range().isEmpty())group.add(new Segment(all.size(),location));
            else {
                var points=new ArrayList<>(boundaries.get(base.header().id()));
                for(int i=1;i<points.size();i++)group.add(new Segment(Math.addExact(all.size(),group.size()),new StorageIndex.Location(base.header(),Optional.of(new StorageRange(points.get(i-1),Optional.of(points.get(i)))))));
                if(location.range().get().end().isEmpty())group.add(new Segment(Math.addExact(all.size(),group.size()),new StorageIndex.Location(base.header(),Optional.of(new StorageRange(points.getLast(),Optional.empty())))));
            }
            all.addAll(group);groups.put(base.header().id(),List.copyOf(group));
        }
        segments=List.copyOf(all);byBase=Map.copyOf(groups);
    }
    private static void add(Map<StorageId,TreeSet<BigInteger>> boundaries,StorageIndex.Location location) {
        var set=boundaries.get(location.base().id());if(set==null)throw new IllegalArgumentException("foreign location");
        location.range().ifPresent(r->{set.add(r.start());r.end().ifPresent(set::add);});
    }
    private static void addWrites(Map<StorageId,TreeSet<BigInteger>> boundaries,List<StatementEffects.Write> writes) {
        for(var write:writes)for(var target:write.targets())add(boundaries,target.location());
    }
    public List<Segment> segments(){return segments;}
    public List<Segment> intersecting(StorageIndex.BaseAddress address,Optional<StorageRange> range){
        Objects.requireNonNull(address);Objects.requireNonNull(range);
        if(nativePartition==null||address.owner()!=nativePartition.inventory)throw new IllegalArgumentException("foreign storage descriptor owner");
        return nativePartition.group(address.ordinal(),range);
    }
    /** Primitive structural projection; no header is needed by an internal transfer. */
    public List<Integer> ordinals(StorageIndex.BaseAddress address,Optional<StorageRange> range){
        Objects.requireNonNull(address);Objects.requireNonNull(range);
        if(nativePartition==null||address.owner()!=nativePartition.inventory)throw new IllegalArgumentException("foreign storage descriptor owner");
        return nativePartition.ordinals(address.ordinal(),range);
    }
    public Optional<StorageRange> range(int ordinal){
        Objects.checkIndex(ordinal,segments.size());return nativePartition==null?segments.get(ordinal).location().range():nativePartition.range(ordinal);
    }
    public List<Segment> intersecting(StorageIndex.Location location) {
        if(nativePartition!=null)return nativePartition.intersecting(location);
        var group=byBase.get(location.base().id());if(group==null)throw new IllegalArgumentException("foreign base");
        if(location.range().isEmpty())return group;
        var range=location.range().get();if(range.empty())return List.of();
        int low=0,high=group.size();
        while(low<high) {
            int middle=(low+high)>>>1;var end=group.get(middle).location().range().orElseThrow().end();
            if(end.isPresent()&&end.get().compareTo(range.start())<=0)low=middle+1;else high=middle;
        }
        int last=low;
        while(last<group.size()&&(range.end().isEmpty()||group.get(last).location().range().orElseThrow().start().compareTo(range.end().get())<0))last++;
        return group.subList(low,last);
    }
    /** Full finite cuts, spilled as primitive words. Headers are reconstructed only
     * at the public Segment boundary, never keys in another owning catalogue. */
    private static final class NativePartition {
        private final ProgramStore.StorageInventory inventory;
        private final List<ProgramStore.OrdinalColumn> owned=new ArrayList<>();
        private ProgramStore.OrdinalColumn numbers,points,starts,ends,segmentBases,segmentStarts,segmentEnds;
        private long wordCount,pointCount;
        private int segmentCount;
        private List<Segment> segments;
        NativePartition(StatementEffects effects,ProgramStore.StorageInventory inventory){
            this.inventory=inventory;
            try{
                numbers=column(Long.MAX_VALUE);points=column(Long.MAX_VALUE);
                starts=column(inventory.size());ends=column(inventory.size());
                segmentBases=column(Long.MAX_VALUE);segmentStarts=column(Long.MAX_VALUE);segmentEnds=column(Long.MAX_VALUE);
                for(int base=0;base<inventory.size();base++)if(inventory.regionAt(base)){
                    point(base,BigInteger.ZERO);var extent=inventory.extentAt(base);if(extent.isPresent())point(base,extent.orElseThrow());
                }
                var storage=effects.storage();
                for(var object:storage.declarations())candidates(storage.object(object.id()).candidates());
                for(var statement:effects.statements()){
                    for(var read:statement.reads())candidates(read.location().candidates());
                    writes(statement.writes());writes(statement.otherwise());for(var writes:statement.outcomes().values())writes(writes);
                }
                for(var context:storage.session().contexts())for(var condition:context.entry().state().conditions())candidates(storage.resolve(condition.place()).candidates());
                try(var ordered=inventory.order(pointCount,(a,b)->{
                    long first=points.get(a),second=points.get(b);
                    int base=Long.compare(numbers.get(first),numbers.get(second));
                    return base!=0?base:number(first).compareTo(number(second));
                })){
                    long at=0;
                    for(int base=0;base<inventory.size();base++){
                        starts.set(base,segmentCount);
                        if(!inventory.regionAt(base))segment(base,0,0);
                        else{
                            long previous=-1;
                            while(at<pointCount){
                                long address=points.get(ordered.get(at));if(numbers.get(address)!=base)break;at++;
                                if(previous<0)previous=address;
                                else if(!number(previous).equals(number(address))){segment(base,previous,address);previous=address;}
                            }
                            if(inventory.extentAt(base).isEmpty())segment(base,previous,-1);
                        }
                        ends.set(base,segmentCount);
                    }
                    if(at!=pointCount)throw new IllegalStateException("unassigned storage boundaries");
                }
                points.close();owned.remove(points);points=null;
                segments=new ProgramStore.BorrowedList<>(segmentCount,this::segment,this::available);
            }catch(RuntimeException|Error failure){
                for(var column:owned)try{column.close();}catch(RuntimeException|Error cleanup){if(cleanup!=failure)failure.addSuppressed(cleanup);}throw failure;
            }
        }
        private void available(){inventory.size();}
        private ProgramStore.OrdinalColumn column(long length){var result=inventory.column(length);owned.add(result);return result;}
        private void point(int base,BigInteger value){
            var bytes=value.toByteArray();long address=wordCount;
            numbers.set(wordCount++,base);numbers.set(wordCount++,bytes.length);
            for(int at=0;at<bytes.length;){
                long word=0;for(int shift=0;shift<Long.SIZE&&at<bytes.length;shift+=Byte.SIZE)word|=(bytes[at++]&255L)<<shift;
                numbers.set(wordCount++,word);
            }
            points.set(pointCount++,address);
        }
        private BigInteger number(long address){
            int length=Math.toIntExact(numbers.get(address+1));var bytes=new byte[length];
            for(int at=0;at<length;){long word=numbers.get(address+2+at/Long.BYTES);
                for(int shift=0;shift<Long.SIZE&&at<length;shift+=Byte.SIZE)bytes[at++]=(byte)(word>>>shift);
            }
            return new BigInteger(bytes);
        }
        private void location(StorageIndex.Location location){
            int base=inventory.ordinal(location.base().id());if(base<0)throw new IllegalArgumentException("foreign location");
            location.range().ifPresent(range->{point(base,range.start());range.end().ifPresent(end->point(base,end));});
        }
        private void candidates(List<StorageIndex.Candidate> candidates){
            if(StorageIndex.wholeCandidates(candidates))return;for(var candidate:candidates)location(candidate.location());
        }
        private void writes(List<StatementEffects.Write> writes){
            for(var write:writes)if(!StatementEffects.wholeTargets(write.targets()))for(var target:write.targets())location(target.location());
        }
        private void segment(int base,long start,long end){
            segmentBases.set(segmentCount,base);segmentStarts.set(segmentCount,start);segmentEnds.set(segmentCount,end);segmentCount=Math.incrementExact(segmentCount);
        }
        private Optional<StorageRange> range(int ordinal){
            int base=Math.toIntExact(segmentBases.get(ordinal));if(!inventory.regionAt(base))return Optional.empty();
            long end=segmentEnds.get(ordinal);
            return Optional.of(new StorageRange(number(segmentStarts.get(ordinal)),end<0?Optional.empty():Optional.of(number(end))));
        }
        private Segment segment(int ordinal){
            var base=inventory.at(Math.toIntExact(segmentBases.get(ordinal)));
            return new Segment(ordinal,new StorageIndex.Location(base.header(),range(ordinal)));
        }
        private List<Segment> intersecting(StorageIndex.Location location){
            available();int base=inventory.ordinal(location.base().id());if(base<0)throw new IllegalArgumentException("foreign base");
            return group(base,location.range());
        }
        private List<Segment> group(int base,Optional<StorageRange> requested){
            long bounds=bounds(base,requested);int start=(int)(bounds>>>Integer.SIZE),count=(int)bounds;
            return new ProgramStore.BorrowedList<>(count,at->segments.get(Math.addExact(start,at)),this::available);
        }
        private List<Integer> ordinals(int base,Optional<StorageRange> requested){
            long bounds=bounds(base,requested);int start=(int)(bounds>>>Integer.SIZE),count=(int)bounds;
            return new ProgramStore.BorrowedList<>(count,at->Math.addExact(start,at),this::available);
        }
        private long bounds(int base,Optional<StorageRange> requested){
            available();Objects.checkIndex(base,inventory.size());
            int first=Math.toIntExact(starts.get(base)),last=Math.toIntExact(ends.get(base));
            if(requested.isPresent()){
                var wanted=requested.orElseThrow();if(wanted.empty())return 0;
                int low=first,high=last;
                while(low<high){int middle=(low+high)>>>1;var end=range(middle).orElseThrow().end();
                    if(end.isPresent()&&end.orElseThrow().compareTo(wanted.start())<=0)low=middle+1;else high=middle;
                }
                first=low;last=first;
                int limit=Math.toIntExact(ends.get(base));
                while(last<limit&&(wanted.end().isEmpty()||range(last).orElseThrow().start().compareTo(wanted.end().orElseThrow())<0))last++;
            }
            return ((long)first<<Integer.SIZE)|(last-first&0xffffffffL);
        }
    }
}
