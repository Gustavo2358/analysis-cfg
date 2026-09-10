package io.github.gustavo2358.analysis.values;

import java.util.Arrays;

/** Finite producer ordinals; no paths, previous states, or global interning of unions. */
final class SupportSet {
    static final SupportSet EMPTY=new SupportSet(-1,null);
    private final int singleton;
    private final int[] many;
    private SupportSet(int singleton,int[] many){this.singleton=singleton;this.many=many;}
    static SupportSet singleton(int producer,ValuesWork work) {
        if(producer<0)throw new IllegalArgumentException("negative producer ordinal");
        work.support(1);return new SupportSet(producer,null);
    }
    int size(){return many!=null?many.length:singleton<0?0:1;}
    int at(int index){return many==null?singleton:many[index];}
    boolean equivalent(SupportSet other){return this==other||singleton==other.singleton&&Arrays.equals(many,other.many);}
    SupportSet join(SupportSet other,ValuesWork work) {
        if(this==other||other.size()==0)return this;
        if(size()==0)return other;
        int capacity=Math.addExact(size(),other.size());work.supportArray(capacity);
        int[] merged=new int[capacity];int i=0,j=0,k=0;
        while(i<size()||j<other.size()) {
            work.supportUnionEntries=Math.incrementExact(work.supportUnionEntries);
            if(j==other.size()||(i<size()&&at(i)<other.at(j)))merged[k++]=at(i++);
            else if(i==size()||other.at(j)<at(i))merged[k++]=other.at(j++);
            else {merged[k++]=at(i++);j++;}
        }
        if(k==size())return this;
        if(k==other.size())return other;
        work.support(k);work.supportArray(k);return new SupportSet(-1,Arrays.copyOf(merged,k));
    }
}
