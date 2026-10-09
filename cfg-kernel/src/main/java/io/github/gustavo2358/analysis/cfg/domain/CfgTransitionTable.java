package io.github.gustavo2358.analysis.cfg.domain;

import io.github.gustavo2358.air.model.Ids.UnitId;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.RandomAccess;

/** Immutable factored known flow. Entry bindings are views; ordinary body rows are stored once. */
public final class CfgTransitionTable extends AbstractList<CfgTransition> implements RandomAccess {
    /** Typed physical storage port. Borrowed stores must check their owner on every query. */
    public interface Storage {
        void add(UnitId unit,List<CfgTransition> entries,List<CfgNodeId> exits,List<CfgTransition> body);
        void seal();
        int groups();
        UnitId unit(int group);
        int entries(int group);
        CfgTransition entry(int group,int entry);
        CfgNodeId normalExit(int group,int entry);
        int bodySize(int group);
        CfgTransition body(int group,int row);
        int logicalEnd(int group);
        int storedSize();
        CfgTransition stored(int row);
        /** Exact duplicate proof over physical rows, not a trusted uniqueness flag. */
        void validateUnique();
    }
    private record Group(UnitId unit,List<CfgTransition> entries,List<CfgNodeId> exits,List<CfgTransition> body) {
        Group {entries=List.copyOf(entries);exits=List.copyOf(exits);body=List.copyOf(body);}
    }
    static final class Builder {
        private final CfgSource source;
        private final Storage storage;
        Builder(CfgSource source){this(source,null);}
        Builder(CfgSource source,Storage storage){this.source=source;this.storage=storage==null?new ResidentStorage():storage;}
        void add(UnitId unit,List<CfgTransition> entries,List<CfgNodeId> exits,List<CfgTransition> body) {
            if(entries.size()!=exits.size())throw new IllegalArgumentException("entry/normal-exit bindings");
            if(!entries.isEmpty())storage.add(unit,entries,exits,body);
        }
        CfgTransitionTable build(){storage.seal();return new CfgTransitionTable(source,storage);}
    }
    private final CfgSource source;
    private final Storage storage;
    private final List<CfgTransition> stored=new PhysicalRows();
    private CfgTransitionTable(CfgSource source,Storage storage) {this.source=Objects.requireNonNull(source);this.storage=Objects.requireNonNull(storage);}
    private static final class ResidentStorage implements Storage {
        private final List<Group> groups=new ArrayList<>();
        private int[] ends;
        private List<CfgTransition> rows;
        @Override public void add(UnitId unit,List<CfgTransition> entries,List<CfgNodeId> exits,List<CfgTransition> body) {
            if(ends!=null)throw new IllegalStateException("sealed transitions");groups.add(new Group(unit,entries,exits,body));
        }
        @Override public void seal() {
            if(ends!=null)throw new IllegalStateException("sealed transitions");ends=new int[groups.size()];
            long total=0;var all=new ArrayList<CfgTransition>();
            for(int i=0;i<groups.size();i++) {
                var group=groups.get(i);total=Math.addExact(total,Math.multiplyExact((long)group.entries().size(),Math.addExact(1L,group.body().size())));
                ends[i]=Math.toIntExact(total);all.addAll(group.entries());all.addAll(group.body());
            }
            rows=List.copyOf(all);
        }
        @Override public int groups(){return groups.size();}
        @Override public UnitId unit(int group){return groups.get(group).unit();}
        @Override public int entries(int group){return groups.get(group).entries().size();}
        @Override public CfgTransition entry(int group,int entry){return groups.get(group).entries().get(entry);}
        @Override public CfgNodeId normalExit(int group,int entry){return groups.get(group).exits().get(entry);}
        @Override public int bodySize(int group){return groups.get(group).body().size();}
        @Override public CfgTransition body(int group,int row){return groups.get(group).body().get(row);}
        @Override public int logicalEnd(int group){return ends[Objects.checkIndex(group,ends.length)];}
        @Override public int storedSize(){return rows.size();}
        @Override public CfgTransition stored(int row){return rows.get(row);}
        @Override public void validateUnique(){if(new HashSet<>(rows).size()!=rows.size())throw new IllegalArgumentException("duplicate CFG transition");}
    }
    private final class PhysicalRows extends AbstractList<CfgTransition> implements RandomAccess {
        @Override public int size(){return storage.storedSize();}
        @Override public CfgTransition get(int row){return storage.stored(row);}
        @Override public void clear(){throw new UnsupportedOperationException("immutable physical CFG rows");}
    }
    /** Physical immutable rows. Generic body rows carry the first Entry as their validation representative. */
    public List<CfgTransition> stored(){return stored;}
    public int groups(){return storage.groups();}
    public UnitId unit(int group){return storage.unit(group);}
    public int entries(int group){return storage.entries(group);}
    public CfgTransition entry(int group,int entry){return storage.entry(group,entry);}
    public CfgNodeId normalExit(int group,int entry){return storage.normalExit(group,entry);}
    void validateUnique(){storage.validateUnique();}
    CfgSource source(){return source;}
    @Override public int size(){int groups=groups();return groups==0?0:storage.logicalEnd(groups-1);}
    @Override public CfgTransition get(int index) {
        Objects.checkIndex(index,size());int low=0,high=groups();
        while(low<high){int middle=(low+high)>>>1;if(index<storage.logicalEnd(middle))high=middle;else low=middle+1;}
        int at=index-(low==0?0:storage.logicalEnd(low-1)),width=storage.bodySize(low)+1;
        int entry=at/width,row=at%width;var binding=entry(low,entry);
        if(row==0)return binding;
        var prototype=storage.body(low,row-1);if(entry==0)return prototype;
        var target=prototype.kind()==CfgTransition.Kind.RETURN||prototype.kind()==CfgTransition.Kind.OPAQUE_RETURN?normalExit(low,entry):prototype.to();
        return new CfgTransition(prototype.from(),target,prototype.kind(),binding.activationEntry());
    }
    @Override public boolean equals(Object other) {
        if(this==other)return true;
        if(other instanceof CfgTransitionTable table) {
            if(groups()!=table.groups())return false;
            for(int g=0;g<groups();g++) {
                if(!unit(g).equals(table.unit(g))||entries(g)!=table.entries(g)||storage.bodySize(g)!=table.storage.bodySize(g))return false;
                for(int e=0;e<entries(g);e++)if(!entry(g,e).equals(table.entry(g,e))||!normalExit(g,e).equals(table.normalExit(g,e)))return false;
                for(int r=0;r<storage.bodySize(g);r++)if(!storage.body(g,r).equals(table.storage.body(g,r)))return false;
            }
            return true;
        }
        return super.equals(other);
    }
    /** List-compatible polynomial hash without enumerating Entry × body transitions. */
    @Override public int hashCode() {
        int result=1;
        for(int g=0;g<groups();g++) {
            int power=1,constant=0,entryWeight=0,exitWeight=0;
            for(int r=0;r<storage.bodySize(g);r++) {var row=storage.body(g,r);
                power*=31;constant=31*constant+row.hashCode();entryWeight=31*entryWeight+1;
                exitWeight=31*exitWeight+(row.kind()==CfgTransition.Kind.RETURN||row.kind()==CfgTransition.Kind.OPAQUE_RETURN?31*31:0);
            }
            int representativeEntry=entry(g,0).activationEntry().hashCode(),representativeExit=normalExit(g,0).hashCode();
            for(int i=0;i<entries(g);i++) {
                var entry=entry(g,i);result=31*result+entry.hashCode();
                result=power*result+constant+(entry.activationEntry().hashCode()-representativeEntry)*entryWeight+(normalExit(g,i).hashCode()-representativeExit)*exitWeight;
            }
        }
        return result;
    }
}
