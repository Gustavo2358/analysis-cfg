package io.github.gustavo2358.analysis.values;

import io.github.gustavo2358.analysis.storage.KillAuthority;

/** Immutable sparse root. Reached missing Cell is unknown, never bottom. Ordinals are internal. */
public final class PossibleValuesState {
    private static final PossibleValuesState BOTTOM=new PossibleValuesState(false,null,null,null);
    private static final PossibleValuesState UNKNOWN=new PossibleValuesState(true,null,null,null);
    private final boolean reached;
    final PersistentBindings.Node root;
    final ScalarRelations relations;
    final ScalarRelations.Roots relational;
    private PossibleValuesState(boolean reached,PersistentBindings.Node root,ScalarRelations relations,ScalarRelations.Roots relational) {
        this.reached=reached;this.root=root;this.relations=relations;this.relational=relational;
    }
    static PossibleValuesState unreachable() { return BOTTOM; }
    static PossibleValuesState reached() { return UNKNOWN; }
    long fingerprint(){return PersistentBindings.fingerprint(root)+(reached?0x9e3779b97f4a7c15L:0)+(relations==null?0:Long.rotateLeft(relations.fingerprint(relational),23));}
    public boolean isReached() { return reached; }
    public int explicitBindings() { return PersistentBindings.size(root); }
    Candidates value(int cell,ValuesWork w) {
        if(!reached)throw new IllegalStateException("unreachable has no value");
        var v=PersistentBindings.get(root,cell,w);
        if(v==null) { w.unknownDefaults=Math.incrementExact(w.unknownDefaults);return Candidates.UNKNOWN; }
        return v;
    }
    PossibleValuesState initialize(int cell,Candidates value,ValuesWork w) {
        if(!reached)return this;return store(cell,value,relations==null?null:relations.strong(relational,cell,value),w);
    }
    PossibleValuesState widenUnknown(int cell,ValuesWork w) {
        if(!reached)return this;var value=value(cell,w).withOpen(w);return store(cell,value,relations==null?null:relations.widen(relational,cell,w),w);
    }
    PossibleValuesState weakUpdate(int cell,Candidates supplied,ValuesWork w) {
        if(!reached)return this;var value=value(cell,w).join(supplied,w);return store(cell,value,relations==null?null:relations.weak(relational,cell,supplied),w);
    }
    PossibleValuesState strongOverwrite(int cell,Candidates replacement,KillAuthority.Permit authority,ValuesWork w) {
        var value=KillAuthority.strongOverwrite(authority,replacement);
        return store(cell,value,relations==null?null:relations.strong(relational,cell,value),w);
    }
    PossibleValuesState relationalOverwrite(int cell,Candidates replacement,ScalarRelations.Roots roots,ValuesWork w) {
        if(relations==null||roots==null)throw new IllegalStateException("missing scalar relation");return store(cell,replacement,roots,w);
    }
    PossibleValuesState attach(ScalarRelations domain,ValuesWork work) {
        if(!reached||!domain.active())return this;if(relations!=null)throw new IllegalStateException("scalar relation already attached");
        return new PossibleValuesState(true,root,domain,domain.attach(this,work));
    }
    private PossibleValuesState store(int cell,Candidates value,ScalarRelations.Roots roots,ValuesWork w) {
        if(!reached)return this;
        w.maxCandidates=Math.max(w.maxCandidates,value.size());
        var next=PersistentBindings.put(root,cell,value,w);
        if(next==root&&(relations==null||relational==roots)){w.rootsReused=Math.incrementExact(w.rootsReused);return this;}
        w.root(PersistentBindings.size(next));return new PossibleValuesState(true,next,relations,roots);
    }
    PossibleValuesState join(PossibleValuesState b,ValuesWork w) {
        if(!b.reached||this==b)return this;if(!reached)return b;
        if(relations!=b.relations)throw new IllegalArgumentException("different scalar relation domains");
        // Iterate explicit bindings only; absence in either reached root contributes unknown.
        var accumulator=new Object(){PossibleValuesState state=PossibleValuesState.this;};
        PersistentBindings.each(root,(key,a)->{
            w.joinEntries=Math.incrementExact(w.joinEntries);
            if(PersistentBindings.get(b.root,key,w)==null)accumulator.state=accumulator.state.storePointwise(key,accumulator.state.value(key,w).withOpen(w),w);
        });
        PersistentBindings.each(b.root,(key,value)->{
            w.joinEntries=Math.incrementExact(w.joinEntries);
            accumulator.state=accumulator.state.storePointwise(key,accumulator.state.value(key,w).join(value,w),w);
        });
        if(relations==null)return accumulator.state;
        var roots=relations.join(relational,b.relational);
        if(accumulator.state.root==root&&relations.equivalent(relational,roots))return this;
        return new PossibleValuesState(true,accumulator.state.root,relations,roots);
    }
    private PossibleValuesState storePointwise(int cell,Candidates value,ValuesWork work){
        work.maxCandidates=Math.max(work.maxCandidates,value.size());var next=PersistentBindings.put(root,cell,value,work);
        if(next==root){work.rootsReused=Math.incrementExact(work.rootsReused);return this;}
        work.root(PersistentBindings.size(next));return new PossibleValuesState(true,next,relations,relational);
    }
    boolean equivalent(PossibleValuesState b,ValuesWork w) {
        if(this==b)return true;
        if(root==b.root&&reached==b.reached&&relations==b.relations&&(relations==null||relations.equivalent(relational,b.relational)))return true;
        if(reached!=b.reached||relations!=b.relations||relations!=null&&!relations.equivalent(relational,b.relational)||explicitBindings()!=b.explicitBindings())return false;
        var result=new boolean[]{true};
        PersistentBindings.each(root,(key,value)->{
            w.compareEntries=Math.incrementExact(w.compareEntries);
            var other=PersistentBindings.get(b.root,key,w);
            if(other==null||!value.equivalent(other))result[0]=false;
        });
        return result[0];
    }
}
