package io.github.gustavo2358.analysis.storage;

import io.github.gustavo2358.air.model.Entries;
import java.util.Comparator;
import java.util.List;

/** Simultaneous facts from an already validated AnalysisSession, before effect projection.
 * I-17 consistency belongs to AIR admission over original places and proved ranges.
 * A conservative MAY impact cannot supply positive evidence of a contradiction.
 */
public final class EntryFacts {
    public enum Kind { STRONG_LITERAL, POSSIBLE, OPEN }
    public record Fact(Entries.InitialCondition condition,StorageIndex.Resolution resolution,Kind kind) {
        public StatementEffects.Strength strength() {
            return kind==Kind.STRONG_LITERAL?StatementEffects.Strength.MUST:StatementEffects.Strength.MAY;
        }
    }
    private EntryFacts() { }
    public static List<Fact> admitted(StorageIndex storage,Entries.Entry entry) {
        // Canonical slots stabilize detached provenance. The rank controls refinement then
        // widening, never compatibility: no projected target participates in admission.
        return entry.state().conditions().stream().map(c->new Fact(c,storage.resolve(c.place()),
            c.value() instanceof Entries.LiteralInitial?Kind.STRONG_LITERAL:
            c.value() instanceof Entries.PossibleLiterals?Kind.POSSIBLE:Kind.OPEN))
            .sorted(Comparator.comparing((Fact f)->f.kind()==Kind.STRONG_LITERAL?0:1)
                .thenComparing(f->f.condition().place().header().id().localId())).toList();
    }
}
