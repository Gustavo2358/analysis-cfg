package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.validation.AirValidator;
import io.github.gustavo2358.air.validation.ValidationResult;
import io.github.gustavo2358.analysis.solver.AnalysisResources;
import io.github.gustavo2358.analysis.solver.PageStore;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

final class PagedAirStorageTest {
    @TempDir Path directory;
    private static AnalysisResources resources(long heap) {
        return new AnalysisResources(new AnalysisResources.Limits(heap, heap, 0,
                64_000_000, 2, 100_000_000, 1_000_000));
    }

    @Test void typedAirPayloadLargerThanManagedHeapMatchesBothBackendsAndIndependentUnicodeFacts() {
        String text = "synthetic:" + "A\u0000\uD83D\uDE00Z".repeat(32768);
        Publication publication = publication(text);
        assertEquals(ValidationResult.Status.STRUCTURALLY_VALID, AirValidator.validate(publication).status());
        var memoryResources = resources(32_000_000); var fileResources = resources(32768);
        try (var memory = new MemoryPageStore(128, memoryResources);
             var file = new FilePageStore(directory, 128, 1, fileResources)) {
            assertPayload(publication, text, memory, memoryResources);
            assertPayload(publication, text, file, fileResources);
            assertTrue(fileResources.used(AnalysisResources.Pool.TEMPORARY) > 32768);
            assertTrue(fileResources.heapPeak() <= 32768);
            assertTrue(file.statistics().evictions() > 1000);
            assertEquals(0, file.statistics().livePages());
        }
        assertEquals(0, fileResources.heapUsed()); assertEquals(0, memoryResources.heapUsed());
        assertEquals(0, fileResources.used(AnalysisResources.Pool.TEMPORARY));
    }
    private static void assertPayload(Publication p, String expected, PageStore pages, AnalysisResources resources) {
        long initial = resources.heapUsed();
        var port = new PagedAirStorage(pages, resources, AnalysisResources.Phase.DECODE);
        try (var builder = new AirSnapshotBuilder(port); var original = AirSnapshot.fromPublication(p)) {
            long root = copy(original, original.root(), null, builder);
            try (var snapshot = builder.finish(root)) {
                assertThrows(IllegalStateException.class, () -> port.set(AirSnapshotBuilder.Column.NODES, 0, 0));
                long origins = snapshot.field(root, AirShape.PUBLICATION, 8);
                long origin = snapshot.element(origins, AirShape.ORIGINS_ORIGIN, 0);
                long text = snapshot.field(origin, AirShape.ORIGINS_UNAVAILABLE, 1);
                assertEquals(expected.length(), snapshot.characterCount(text));
                char[] block = new char[107];
                for (int offset = 0; offset < expected.length(); offset += block.length) {
                    int count = snapshot.readCharacters(text, offset, block, 0, block.length);
                    assertEquals(expected.substring(offset, offset + count), new String(block, 0, count));
                }
                long units = snapshot.field(root, AirShape.PUBLICATION, 4);
                long unit;
                long scratch = resources.used(AnalysisResources.Pool.SCRATCH);
                try (var cursor = snapshot.elements(units, AirShape.UNIT)) {
                    assertTrue(cursor.advance()); unit = cursor.value(); assertFalse(cursor.advance()); assertFalse(cursor.advance());
                }
                assertEquals(scratch, resources.used(AnalysisResources.Pool.SCRATCH));
                long id = snapshot.field(unit, AirShape.UNIT, 0);
                long idText = snapshot.field(id, AirShape.IDS_UNIT_ID, 1);
                assertEquals(1, snapshot.readCharacters(idText, 0, block, 0, block.length)); assertEquals('u', block[0]);
                long sequences = snapshot.field(unit, AirShape.UNIT, 5);
                long sequence = snapshot.element(sequences, AirShape.SEQUENCE, 0);
                long halt = snapshot.field(sequence, AirShape.SEQUENCE, 2);
                assertEquals("NORMAL", snapshot.enumName(snapshot.field(halt, AirShape.OPERATIONS_HALT, 1)));
            }
        }
        // The explicit resident store retains funded reusable capacity until its own close.
        // The file store has a fixed cache, so closing input tables returns exactly their leases.
        if (pages instanceof FilePageStore) assertEquals(initial, resources.heapUsed());
        assertEquals(0, pages.statistics().livePages());
    }

    @Test void primitiveOccurrenceTraversalPreservesOrderAndReleasesCursorOnCallbackFailureAcrossBackends() {
        var p = publication("small synthetic traversal input"); var body = p.units().get(0);
        var originalSequence = body.sequences().get(0); var operationHeader = originalSequence.terminator().header();
        var values = new java.util.ArrayList<Expression>();
        for (int n=0;n<64;n++) values.add(new Expressions.Literal(
            new Operand.Header(new Ids.OperandId(new Ids.OperationOwner(operationHeader.id()),"value-"+n),Operand.Role.VALUE_READ,body.origin()),
            new Values.TextValue("v"+n)));
        var sequence = new Sequence(originalSequence.label(),List.of(),new Operations.Return(operationHeader,values),body.origin());
        var unit = new Unit(body.id(),body.containingUnit(),body.objects(),body.visibleObjects(),body.entries(),List.of(sequence),body.completionPorts(),
            body.body(),body.bodyUnavailable(),body.coverage(),body.origin());
        // A traversal-only fixture, not an admission certificate: signature/type rules are separate.
        p = new Publication(p.id(),p.airVersion(),p.capabilities(),p.artifacts(),List.of(unit),p.storage(),p.resources(),p.artifactRelations(),p.origins(),p.coverage(),p.uncertainties(),p.premises());
        var memoryResources = resources(32_000_000); var fileResources = resources(32768);
        try (var memory = new MemoryPageStore(128,memoryResources);
             var file = new FilePageStore(directory,128,1,fileResources)) {
            assertTraversal(p,memory,memoryResources); assertTraversal(p,file,fileResources);
            assertTrue(file.statistics().evictions()>0); assertTrue(fileResources.heapPeak()<=32768);
        }
        assertEquals(0,memoryResources.heapUsed()); assertEquals(0,fileResources.heapUsed());
    }
    private static void assertTraversal(Publication p,PageStore pages,AnalysisResources resources) {
        try (var original = AirSnapshot.fromPublication(p);
             var builder = new AirSnapshotBuilder(new PagedAirStorage(pages,resources,AnalysisResources.Phase.DECODE))) {
            long root = copy(original,original.root(),null,builder);
            try (var snapshot = builder.finish(root)) {
                long unit = snapshot.element(snapshot.field(root,AirShape.PUBLICATION,4),AirShape.UNIT,0);
                long sequence = snapshot.element(snapshot.field(unit,AirShape.UNIT,5),AirShape.SEQUENCE,0);
                long operation = snapshot.field(sequence,AirShape.SEQUENCE,2);
                long scratch = resources.used(AnalysisResources.Pool.SCRATCH); int[] seen = {0};
                SnapshotOperands.roots(snapshot,operation,value -> {
                    assertEquals(AirShape.EXPRESSIONS_LITERAL,snapshot.shape(value));
                    long header = snapshot.field(value,AirShape.EXPRESSIONS_LITERAL,0);
                    long id = snapshot.field(header,AirShape.OPERAND_HEADER,0);
                    long local = snapshot.field(id,AirShape.IDS_OPERAND_ID,1); char[] text = new char[16];
                    int count = snapshot.readCharacters(local,0,text,0,text.length);
                    assertEquals("value-"+seen[0]++,new String(text,0,count));
                    SnapshotOperands.children(snapshot,value,child -> fail("literal has no operand children"));
                });
                assertEquals(64,seen[0]); assertEquals(scratch,resources.used(AnalysisResources.Pool.SCRATCH));
                var failure = new IllegalStateException("injected callback failure");
                assertSame(failure,assertThrows(IllegalStateException.class,() ->
                    SnapshotOperands.roots(snapshot,operation,value -> { throw failure; })));
                assertEquals(scratch,resources.used(AnalysisResources.Pool.SCRATCH));
                assertEquals(AirShape.PUBLICATION,snapshot.shape(root));
            }
        }
        assertEquals(0,pages.statistics().livePages());
    }

    @Test void failedColumnConstructionReleasesEveryPreviouslyReservedOwner() {
        var resources = resources(6000);
        try (var pages = new FilePageStore(directory, 128, 1, resources)) {
            long before = resources.heapUsed();
            assertEquals(AnalysisResources.Phase.DECODE, assertThrows(AnalysisResources.Exhausted.class,
                    () -> new PagedAirStorage(pages, resources, AnalysisResources.Phase.DECODE)).phase());
            assertEquals(before, resources.heapUsed()); assertEquals(0, pages.statistics().livePages());
        }
        assertEquals(0, resources.heapUsed());
    }

    @Test void operationalWriteFailureAbortsTypedConstructionAndFinalOwnerCleansDisk() {
        var resources = new AnalysisResources(new AnalysisResources.Limits(32768, 32768, 0,
                64_000_000, 2, 1, 1_000_000));
        var pages = new FilePageStore(directory, 128, 1, resources);
        var builder = new AirSnapshotBuilder(new PagedAirStorage(pages, resources, AnalysisResources.Phase.DECODE));
        try {
            assertThrows(AnalysisResources.Exhausted.class, () -> builder.scalar(AirShape.BOOLEAN, 1));
            assertThrows(IllegalStateException.class, () -> builder.finish(1));
        } finally {
            // Cleanup may itself exceed work quota, but every coarse heap lease and final store must close.
            try { builder.close(); } catch (AnalysisResources.Exhausted expected) { assertEquals(AnalysisResources.Resource.WORK, expected.resource()); }
            try { pages.close(); } catch (AnalysisResources.Exhausted expected) { assertEquals(AnalysisResources.Resource.WORK, expected.resource()); }
        }
        assertEquals(0, resources.heapUsed()); assertEquals(0, resources.used(AnalysisResources.Pool.TEMPORARY));
        assertEquals(0, resources.used(AnalysisResources.Pool.OPEN_FILES));
    }

    private static Publication publication(String reason) {
        var pub = new Ids.PublicationId("paged"); var unit = new Ids.UnitId(pub, "u");
        var origin = new Ids.OriginId(pub, "o"); var label = new Ids.LabelId(unit, "start");
        var scope = new Scopes.UnitScope(unit);
        var claim = new Evidence.Claim(scope, Evidence.PrecisionStatus.EXACT, List.of());
        var precision = new Evidence.Precision(claim, claim, claim, claim, claim);
        var header = new Operations.Header(new Ids.OperationId(unit, "halt"), origin,
                Evidence.CoverageStatus.MODELED, precision, List.of());
        var halt = new Operations.Halt(header, Operations.HaltKind.NORMAL);
        var sequence = new Sequence(label, List.of(), halt, origin);
        var signature = new Interactions.Signature(new Interactions.ParameterInventory(List.of(), Interactions.NoRemainder.INSTANCE),
                new Interactions.ResultInventory(List.of(), Interactions.NoRemainder.INSTANCE), origin);
        var entry = new Entries.Entry(new Ids.EntryId(unit, "e"), Optional.of(label), signature,
                new Entries.EntryState(List.of(), List.of()), origin);
        var coverage = new Evidence.Coverage(Evidence.InventoryStatus.COMPLETE, scope, List.of(), List.of());
        var body = new Unit(unit, Optional.empty(), List.of(), List.of(), List.of(entry), List.of(sequence), List.of(),
                Unit.BodyAvailability.AVAILABLE, Optional.empty(), coverage, origin);
        return new Publication(pub, SemanticVersion.AIR_2_0_0, new Capabilities.Manifest(List.of(), List.of()),
                List.of(), List.of(body), List.of(), List.of(), List.of(), List.of(new Origins.Unavailable(origin, reason)),
                new Evidence.Coverage(Evidence.InventoryStatus.COMPLETE, new Scopes.PublicationScope(pub), List.of(), List.of()), List.of(), List.of());
    }
    // Test-only shallow model adapter. Production large-input decode must not construct Publication.
    private static long copy(AirSnapshot source, long node, AirShape element, AirSnapshotBuilder target) {
        AirShape shape = source.shape(node);
        return switch (shape.form()) {
            case RECORD -> {
                long[] children = new long[shape.fieldCount()];
                for (int n = 0; n < children.length; n++) children[n] = copy(source, source.field(node, shape, n), shape.field(n).element(), target);
                yield target.record(shape, children);
            }
            case LIST -> {
                try (var list = target.list(element)) {
                    for (long n = 0; n < source.size(node); n++) list.add(copy(source, source.element(node, element, n), null, target));
                    yield list.finish();
                }
            }
            case OPTIONAL -> target.optional(element, source.size(node) == 0 ? 0 : copy(source, source.element(node, element, 0), null, target));
            case TEXT, INTEGER -> {
                try (var text = target.text(shape)) {
                    char[] block = new char[128]; long at = 0;
                    while (at < source.characterCount(node)) { int n = source.readCharacters(node, at, block, 0, block.length); text.append(block, 0, n); at += n; }
                    yield text.finish();
                }
            }
            case BOOLEAN, SMALL_INTEGER, ENUM -> target.scalar(shape, source.scalar(node));
            case UNION -> throw new AssertionError("concrete value required");
        };
    }
}
