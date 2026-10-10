package io.github.gustavo2358.analysis.storage;

import io.github.gustavo2358.air.model.Entries;
import io.github.gustavo2358.analysis.structure.ProgramStore;
import java.util.Comparator;
import java.util.List;

/** Simultaneous facts from an already validated AnalysisSession, before effect projection.
 * I-17 consistency belongs to AIR admission over original places and proved ranges.
 * A conservative MAY impact cannot supply positive evidence of a contradiction.
 */
public final class EntryFacts {
    public enum Kind { STRONG_LITERAL, POSSIBLE, OPEN }
    public static final class Fact {
        private final ProgramStore.InitialConditionView reference;
        private final StorageIndex.Resolution resolution;
        private final Kind kind;
        public Fact(Entries.InitialCondition condition,StorageIndex.Resolution resolution,Kind kind){this(ProgramStore.residentInitial(condition),resolution,kind);}
        private Fact(ProgramStore.InitialConditionView reference,StorageIndex.Resolution resolution,Kind kind){this.reference=reference;this.resolution=resolution;this.kind=kind;}
        public Entries.InitialCondition condition(){return reference.materialize();}
        public ProgramStore.InitialConditionView conditionView(){return reference;}
        public StorageIndex.Resolution resolution(){return resolution;}
        public Kind kind(){return kind;}
        @Override public boolean equals(Object other){return this==other||other instanceof Fact fact&&condition().equals(fact.condition())&&resolution.equals(fact.resolution)&&kind==fact.kind;}
        @Override public int hashCode(){return java.util.Objects.hash(condition(),resolution,kind);}
        @Override public String toString(){return "Fact[condition="+condition()+", resolution="+resolution+", kind="+kind+"]";}
        public StatementEffects.Strength strength() {
            return kind==Kind.STRONG_LITERAL?StatementEffects.Strength.MUST:StatementEffects.Strength.MAY;
        }
    }
    private EntryFacts() { }
    public static List<Fact> admitted(StorageIndex storage,Entries.Entry entry) {
        return admitted(storage,ProgramStore.residentEntry(entry));
    }
    public static List<Fact> admitted(StorageIndex storage,ProgramStore.EntryView entry) {
        // Canonical slots stabilize detached provenance. The rank controls refinement then
        // widening, never compatibility: no projected target participates in admission.
        return entry.state().conditions().stream().map(c->new Fact(c,storage.resolve(c.place()),
            c.literal()?Kind.STRONG_LITERAL:c.possible()?Kind.POSSIBLE:Kind.OPEN))
            .sorted(Comparator.comparing((Fact f)->f.kind()==Kind.STRONG_LITERAL?0:1)
                .thenComparing(f->f.conditionView().place().header().id().localId())).toList();
    }
}
