package io.github.gustavo2358.analysis.storage;

import io.github.gustavo2358.air.model.Ids.StorageId;
import java.math.BigInteger;
import java.util.*;

/** Finite boundaries demanded by this program. Never materializes one cell per octet. */
public final class StoragePartition {
    public record Segment(int ordinal,StorageIndex.Location location) { }
    private final List<Segment> segments;
    private final Map<StorageId,List<Segment>> byBase;
    public StoragePartition(StatementEffects effects) {
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
    public List<Segment> intersecting(StorageIndex.Location location) {
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
}
