package io.github.gustavo2358.analysis.values;

/** Immutable sparse root. Reached missing Cell is unknown, never bottom. Ordinals are internal. */
public final class PossibleValuesState {
    private static final PossibleValuesState BOTTOM=new PossibleValuesState(false,null);
    private static final PossibleValuesState UNKNOWN=new PossibleValuesState(true,null);
    private final boolean reached;
    final PersistentBindings.Node root;
    private PossibleValuesState(boolean reached,PersistentBindings.Node root) { this.reached=reached;this.root=root; }
    static PossibleValuesState unreachable() { return BOTTOM; }
    static PossibleValuesState reached() { return UNKNOWN; }
    public boolean isReached() { return reached; }
    public int explicitBindings() { return PersistentBindings.size(root); }
    Candidates value(int cell,ValuesWork w) {
        if(!reached)throw new IllegalStateException("unreachable has no value");
        var v=PersistentBindings.get(root,cell,w);
        if(v==null) { w.unknownDefaults=Math.incrementExact(w.unknownDefaults);return Candidates.UNKNOWN; }
        return v;
    }
    PossibleValuesState assign(int cell,Candidates value,ValuesWork w) {
        if(!reached)return this;
        w.maxCandidates=Math.max(w.maxCandidates,value.size());
        var next=PersistentBindings.put(root,cell,value,w);
        if(next==root){w.rootsReused=Math.incrementExact(w.rootsReused);return this;}
        w.root(PersistentBindings.size(next));return new PossibleValuesState(true,next);
    }
    PossibleValuesState join(PossibleValuesState b,ValuesWork w) {
        if(!b.reached||this==b)return this;if(!reached)return b;
        // Iterate explicit bindings only; absence in either reached root contributes unknown.
        var accumulator=new Object(){PossibleValuesState state=PossibleValuesState.this;};
        PersistentBindings.each(root,(key,a)->{
            w.joinEntries=Math.incrementExact(w.joinEntries);
            if(PersistentBindings.get(b.root,key,w)==null)accumulator.state=accumulator.state.assign(key,a.withOpen(w),w);
        });
        PersistentBindings.each(b.root,(key,value)->{
            w.joinEntries=Math.incrementExact(w.joinEntries);
            accumulator.state=accumulator.state.assign(key,value(key,w).join(value,w),w);
        });
        return accumulator.state;
    }
    boolean equivalent(PossibleValuesState b,ValuesWork w) {
        if(this==b||root==b.root&&reached==b.reached)return true;
        if(reached!=b.reached||explicitBindings()!=b.explicitBindings())return false;
        var result=new boolean[]{true};
        PersistentBindings.each(root,(key,value)->{
            w.compareEntries=Math.incrementExact(w.compareEntries);
            var other=PersistentBindings.get(b.root,key,w);
            if(other==null||!value.equivalent(other))result[0]=false;
        });
        return result[0];
    }
}
