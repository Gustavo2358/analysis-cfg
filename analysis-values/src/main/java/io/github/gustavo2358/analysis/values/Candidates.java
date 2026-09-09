package io.github.gustavo2358.analysis.values;

import java.util.Arrays;

/** Internal finite set of universe ordinals. Singleton has no array; no historical set pool. */
final class Candidates {
    static final Candidates UNKNOWN=new Candidates(-1,null,true);
    private final int singleton;
    private final int[] many;
    private final boolean open;
    private Candidates(int singleton,int[] many,boolean open) { this.singleton=singleton;this.many=many;this.open=open; }
    static Candidates singleton(int value,ValuesWork w) {
        if(value<0)throw new IllegalArgumentException("negative value ordinal");
        w.candidate(1);return new Candidates(value,null,false);
    }
    int size() { return many!=null?many.length:singleton<0?0:1; }
    int at(int i) { return many==null?singleton:many[i]; }
    boolean open() { return open; }
    int[] ordinals() { return many==null?(singleton<0?new int[0]:new int[]{singleton}):many.clone(); }
    Candidates withOpen(ValuesWork w) {
        if(open)return this;
        w.candidate(0);return new Candidates(singleton,many,true);
    }
    boolean equivalent(Candidates b) { return this==b||(open==b.open&&singleton==b.singleton&&Arrays.equals(many,b.many)); }
    Candidates join(Candidates b,ValuesWork w) {
        if(this==b)return this;
        if(size()==0)return open?b.withOpen(w):b;
        if(b.size()==0)return b.open?withOpen(w):this;
        w.array(Math.addExact(size(),b.size()));
        int[] merged=new int[Math.addExact(size(),b.size())];int i=0,j=0,k=0;
        while(i<size()||j<b.size()) {
            w.unionEntries=Math.incrementExact(w.unionEntries);
            int value;
            if(j==b.size()||(i<size()&&at(i)<b.at(j)))value=at(i++);
            else if(i==size()||b.at(j)<at(i))value=b.at(j++);
            else { value=at(i++);j++; }
            merged[k++]=value;
        }
        boolean remainder=open||b.open;
        if(k==size())return remainder?withOpen(w):this;
        if(k==b.size())return remainder?b.withOpen(w):b;
        w.candidate(k);w.array(k);
        return new Candidates(-1,Arrays.copyOf(merged,k),remainder);
    }
}
