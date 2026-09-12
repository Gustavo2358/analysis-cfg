package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.analysis.dependencies.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static io.github.gustavo2358.analysis.adapters.ResultFixtures.*;

/** Restricted PERFORM normalization expressed exclusively with existing AIR. */
class PerformBasicModelTest {
    static Publication model(boolean copy, boolean overwrite) {
        return W1dModelTest.model(copy ? 2 : 1, u -> {
            var source = new ObjectId(u, "object-0");
            var destination = new ObjectId(u, copy ? "object-1" : "object-0");
            var body = new ArrayList<Instruction>();
            body.add(assign(u, "literal-producer", source, "PROGA   "));
            if (copy) {
                var h = header(u, "copy");
                body.add(new Operations.Assign(h,
                    new Places.ObjectPlace(operand(h.id(), "destination", Operand.Role.VALUE_WRITE), destination),
                    new Expressions.Read(operand(h.id(), "read", Operand.Role.VALUE_READ),
                        new Places.ObjectPlace(operand(h.id(), "source", Operand.Role.VALUE_READ), source))));
            }
            return List.of(
                new Sequence(new LabelId(u, "start"), overwrite ? List.of(assign(u, "old", destination, "OLDPROG ")) : List.of(),
                    new Operations.Jump(header(u, "perform"), new LabelId(u, "define")), origin(u.publication())),
                new Sequence(new LabelId(u, "define"), body,
                    new Operations.Jump(header(u, "resume"), new LabelId(u, "call")), origin(u.publication())),
                new Sequence(new LabelId(u, "call"), List.of(), W1dModelTest.call(u, "invoke", "end", destination, true), origin(u.publication())),
                returning(u, "end", List.of()));
        });
    }
    static DependencyResult oracle(Publication p) {
        var result = new DependencyAnalysis().prepare(p);
        assertEquals(1, result.sites().size()); assertEquals(1, result.edges().size());
        var site = result.sites().getFirst();
        assertEquals("invoke", site.operation().localId()); assertEquals("call", site.sequence().localId());
        assertEquals(List.of("PROGA"), site.candidates().stream().map(DependencySiteFact.Candidate::referenceName).toList());
        assertEquals(List.of("PROGA   "), site.rawCandidates().stream().map(DependencySiteFact.RawCandidate::rawValue).toList());
        assertFalse(site.modelValueRemainder());
        assertEquals(List.of("literal-producer"), site.candidates().getFirst().supports().stream().map(s -> s.producer().localId()).toList());
        return result;
    }
    @Test void literalParagraphBeforeCall() { oracle(model(false, false)); }
    @Test void literalThenCopyParagraphBeforeCall() { oracle(model(true, false)); }
    @Test void performedStrongWriteKillsOldProgram() { oracle(model(false, true)); }
    @Test void explicitEntryAndAllTwentyFourSequencePermutationsPreserveResult() {
        var p = model(true, true); var expected = oracle(p);
        var sequences = p.units().getFirst().sequences();
        for (int a = 0; a < 4; a++) for (int b = 0; b < 4; b++)
            for (int c = 0; c < 4; c++) for (int d = 0; d < 4; d++) {
                if (a == b || a == c || a == d || b == c || b == d || c == d) continue;
                var reordered = List.of(sequences.get(a), sequences.get(b), sequences.get(c), sequences.get(d));
                var result = oracle(W1dEffectsTest.sequences(p, reordered, p.units().getFirst().entries()));
                assertEquals(expected.sites(), result.sites()); assertEquals(expected.edges(), result.edges());
            }
    }
    @Test void oracleKillsSkippedBodyAndWrongReturnChallenges() {
        var p = model(false, true); var seq = new ArrayList<>(p.units().getFirst().sequences());
        var main = seq.getFirst(); var u = p.units().getFirst().id();
        seq.set(0, new Sequence(main.label(), main.instructions(), new Operations.Jump(header(u, "perform"), new LabelId(u, "call")), main.origin()));
        assertThrows(AssertionError.class, () -> oracle(W1dEffectsTest.sequences(p, seq, p.units().getFirst().entries())));
        seq.set(0, main); var target = seq.get(1);
        seq.set(1, new Sequence(target.label(), target.instructions(), new Operations.Jump(header(u, "resume"), new LabelId(u, "end")), target.origin()));
        assertThrows(AssertionError.class, () -> oracle(W1dEffectsTest.sequences(p, seq, p.units().getFirst().entries())));
    }
}
