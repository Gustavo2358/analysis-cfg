package io.github.gustavo2358.analysis.cfg.domain;

import io.github.gustavo2358.air.model.Ids.UnitId;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.RandomAccess;

/** Immutable factored known flow. Entry bindings are views; ordinary body rows are stored once. */
public final class CfgTransitionTable extends AbstractList<CfgTransition> implements RandomAccess {
    private record Group(UnitId unit,List<CfgTransition> entries,List<CfgNodeId> exits,List<CfgTransition> body) {
        Group {entries=List.copyOf(entries);exits=List.copyOf(exits);body=List.copyOf(body);}
    }
    static final class Builder {
        private final CfgSource source;
        private final List<Group> groups=new ArrayList<>();
        Builder(CfgSource source){this.source=source;}
        void add(UnitId unit,List<CfgTransition> entries,List<CfgNodeId> exits,List<CfgTransition> body) {
            if(entries.size()!=exits.size())throw new IllegalArgumentException("entry/normal-exit bindings");
            if(!entries.isEmpty())groups.add(new Group(unit,entries,exits,body));
        }
        CfgTransitionTable build(){return new CfgTransitionTable(source,groups);}
    }
    private final CfgSource source;
    private final Group[] groups;
    private final int[] ends;
    private final List<CfgTransition> stored;
    private final int size;
    private CfgTransitionTable(CfgSource source,List<Group> groups) {
        this.source=source;this.groups=groups.toArray(Group[]::new);ends=new int[groups.size()];
        long total=0;var rows=new ArrayList<CfgTransition>();
        for(int i=0;i<this.groups.length;i++) {
            var group=this.groups[i];total=Math.addExact(total,Math.multiplyExact((long)group.entries().size(),Math.addExact(1L,group.body().size())));
            ends[i]=Math.toIntExact(total);rows.addAll(group.entries());rows.addAll(group.body());
        }
        size=Math.toIntExact(total);stored=List.copyOf(rows);
    }
    /** Physical immutable rows. Generic body rows carry the first Entry as their validation representative. */
    public List<CfgTransition> stored(){return stored;}
    public int groups(){return groups.length;}
    public UnitId unit(int group){return groups[group].unit();}
    public int entries(int group){return groups[group].entries().size();}
    public CfgTransition entry(int group,int entry){return groups[group].entries().get(entry);}
    public CfgNodeId normalExit(int group,int entry){return groups[group].exits().get(entry);}
    CfgSource source(){return source;}
    @Override public int size(){return size;}
    @Override public CfgTransition get(int index) {
        Objects.checkIndex(index,size);int low=0,high=ends.length;
        while(low<high){int middle=(low+high)>>>1;if(index<ends[middle])high=middle;else low=middle+1;}
        var group=groups[low];int at=index-(low==0?0:ends[low-1]),width=group.body().size()+1;
        int entry=at/width,row=at%width;var binding=group.entries().get(entry);
        if(row==0)return binding;
        var prototype=group.body().get(row-1);if(entry==0)return prototype;
        var target=prototype.kind()==CfgTransition.Kind.RETURN||prototype.kind()==CfgTransition.Kind.OPAQUE_RETURN?group.exits().get(entry):prototype.to();
        return new CfgTransition(prototype.from(),target,prototype.kind(),binding.activationEntry());
    }
    @Override public boolean equals(Object other) {
        if(this==other)return true;
        if(other instanceof CfgTransitionTable table)return Arrays.equals(groups,table.groups);
        return super.equals(other);
    }
    /** List-compatible polynomial hash without enumerating Entry × body transitions. */
    @Override public int hashCode() {
        int result=1;
        for(var group:groups) {
            int power=1,constant=0,entryWeight=0,exitWeight=0;
            for(var row:group.body()) {
                power*=31;constant=31*constant+row.hashCode();entryWeight=31*entryWeight+1;
                exitWeight=31*exitWeight+(row.kind()==CfgTransition.Kind.RETURN||row.kind()==CfgTransition.Kind.OPAQUE_RETURN?31*31:0);
            }
            int representativeEntry=group.entries().getFirst().activationEntry().hashCode(),representativeExit=group.exits().getFirst().hashCode();
            for(int i=0;i<group.entries().size();i++) {
                var entry=group.entries().get(i);result=31*result+entry.hashCode();
                result=power*result+constant+(entry.activationEntry().hashCode()-representativeEntry)*entryWeight+(group.exits().get(i).hashCode()-representativeExit)*exitWeight;
            }
        }
        return result;
    }
}
