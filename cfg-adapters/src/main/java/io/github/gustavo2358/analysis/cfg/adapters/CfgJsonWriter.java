package io.github.gustavo2358.analysis.cfg.adapters;

import io.github.gustavo2358.air.model.Evidence;
import io.github.gustavo2358.air.model.Ids;
import io.github.gustavo2358.air.model.Operations;
import io.github.gustavo2358.air.model.Terminator;
import io.github.gustavo2358.analysis.cfg.application.CfgBuildResult;
import io.github.gustavo2358.analysis.cfg.domain.CfgNode;
import io.github.gustavo2358.analysis.cfg.domain.CfgNodeId;
import io.github.gustavo2358.analysis.cfg.domain.CfgTransition;
import io.github.gustavo2358.analysis.cfg.domain.ProjectionPolicy;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;

/** Explicit analysis-cfg-json 1.0.0/2.0.0 mapping; the AIR input remains the source of full AIR facts. */
public final class CfgJsonWriter {
    public static final int DEFAULT_MAXIMUM_BYTES = 64 * 1024 * 1024;
    private final int maximumBytes;

    public CfgJsonWriter() { this(DEFAULT_MAXIMUM_BYTES); }

    public CfgJsonWriter(int maximumBytes) {
        if (maximumBytes < 1) throw new IllegalArgumentException("positive maximumBytes required");
        this.maximumBytes = maximumBytes;
    }

    public byte[] encode(CfgBuildResult result) throws CfgJsonException {
        Objects.requireNonNull(result, "result");
        if (result.status() != CfgBuildResult.Status.CFG_BUILT)
            throw new CfgJsonException("only CFG_BUILT can be serialized");
        var graph = result.graph().orElseThrow();
        var out = new CfgJsonBytes(maximumBytes);
        // Token mappings carry their contract requirement. Inspect the product, not its source text.
        boolean requiresV2 = false;
        boolean requiresV3 = graph.nodes().stream().anyMatch(n -> n instanceof CfgNode.SequenceNode q && q.source().terminator() instanceof Operations.Opaque);
        for (var node : graph.nodes()) {
            if (node instanceof CfgNode.SequenceNode sequence)
                requiresV2 |= terminatorKind(sequence.source().terminator()).requiresV2;
        }
        for (var transition : graph.transitions())
            requiresV2 |= transitionKind(transition.kind()).requiresV2;
        out.raw("{\"schema\":\"analysis-cfg-json\",\"schemaVersion\":");
        out.string(requiresV3 ? "3.0.0" : requiresV2 ? "2.0.0" : "1.0.0");
        out.raw(",\"airVersion\":");
        var version = result.airVersion();
        out.string(version.major() + "." + version.minor() + "." + version.patch());
        out.raw(",\"publication\":"); airId(out, result.publicationId());
        out.raw(",\"buildStatus\":\"CFG_BUILT\",\"projectionPolicy\":"); out.string(policy(result.options().projectionPolicy()));
        out.raw(",\"sourceKnowledge\":{\"publicationInventory\":");
        out.string(inventory(graph.publication().coverage().inventory()));
        out.raw(",\"units\":[");
        boolean comma = false;
        for (var unit : graph.publication().units()) {
            if (comma) out.raw(","); comma = true;
            out.raw("{\"unit\":"); airId(out, unit.id());
            out.raw(",\"inventory\":"); out.string(inventory(unit.coverage().inventory())); out.raw("}");
        }
        out.raw("]},\"nodes\":["); comma = false;
        for (var node : graph.nodes()) {
            if (comma) out.raw(","); comma = true;
            node(out, node, requiresV3);
        }
        out.raw("],\"transitions\":["); comma = false;
        for (var transition : graph.transitions()) {
            if (comma) out.raw(","); comma = true;
            out.raw("{\"kind\":"); out.string(transitionKind(transition.kind()).token);
            out.raw(",\"from\":"); cfgId(out, transition.from());
            out.raw(",\"to\":"); cfgId(out, transition.to());
            out.raw(",\"activationEntry\":"); airId(out, transition.activationEntry()); out.raw("}");
        }
        out.raw("]}");
        return out.bytes();
    }

    public void write(CfgBuildResult result, Path destination) throws CfgJsonException, IOException {
        byte[] bytes = encode(result);
        publish(bytes, destination, Files::move);
    }

    @FunctionalInterface
    interface MoveOperation { void move(Path from, Path to, CopyOption... options) throws IOException; }

    /** Package-private seam exercises the documented non-atomic fallback on any test filesystem. */
    static void publish(byte[] bytes, Path destination, MoveOperation mover) throws IOException {
        Path absolute = destination.toAbsolutePath();
        Path parent = absolute.getParent();
        if (parent == null) throw new IOException("destination must name a file");
        Path temporary = Files.createTempFile(parent, ".analysis-cfg-", ".tmp");
        try {
            Files.write(temporary, bytes);
            try {
                mover.move(temporary, absolute, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                // Explicit fallback: replacement here does NOT claim atomicity.
                mover.move(temporary, absolute, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void node(CfgJsonBytes out, CfgNode node, boolean v3) throws CfgJsonException {
        out.raw("{\"id\":"); cfgId(out, node.id());
        switch (node) {
            case CfgNode.EntryNode entry -> {
                out.raw(",\"kind\":\"ENTRY\",\"entry\":"); airId(out, entry.source().id());
            }
            case CfgNode.SequenceNode sequence -> {
                out.raw(",\"kind\":\"SEQUENCE\",\"label\":"); airId(out, sequence.source().label());
                out.raw(",\"terminator\":{\"kind\":"); out.string(terminatorKind(sequence.source().terminator()).token);
                out.raw(",\"operation\":"); airId(out, sequence.source().terminator().header().id());
                if(v3) {
                    boolean open=sequence.source().terminator() instanceof Operations.Opaque o && o.envelope().control().remainder() instanceof io.github.gustavo2358.air.model.Scopes.WithinControl
                        || sequence.source().terminator() instanceof Operations.Invoke i && i.outcomes().remainder() instanceof io.github.gustavo2358.air.model.Scopes.WithinControl;
                    out.raw(",\"openControlRemainder\":"+(open?"true":"false"));
                }
                out.raw("}");
            }
            case CfgNode.NormalExit exit -> {
                out.raw(",\"kind\":\"NORMAL_EXIT\",\"unit\":"); airId(out, exit.unitId());
                out.raw(",\"entry\":"); airId(out, exit.entryId());
            }
            case CfgNode.HaltExit exit -> {
                out.raw(",\"kind\":\"HALT_EXIT\",\"operation\":"); airId(out, exit.source().header().id());
                out.raw(",\"haltKind\":"); out.string(haltKind(exit.source().haltKind()));
            }
        }
        out.raw("}");
    }

    private static void cfgId(CfgJsonBytes out, CfgNodeId id) throws CfgJsonException {
        out.raw("{\"publication\":"); out.string(id.publicationId().localId());
        out.raw(",\"ordinal\":"); out.string(Long.toString(id.ordinal())); out.raw("}");
    }

    private static void airId(CfgJsonBytes out, Ids.Id id) throws CfgJsonException {
        switch (id) {
            case Ids.PublicationId publication -> { out.raw("{\"localId\":"); out.string(publication.localId()); }
            case Ids.UnitId unit -> {
                out.raw("{\"publication\":"); out.string(unit.publication().localId());
                out.raw(",\"localId\":"); out.string(unit.localId());
            }
            case Ids.EntryId entry -> scopedId(out, entry.unit(), entry.localId());
            case Ids.LabelId label -> scopedId(out, label.unit(), label.localId());
            case Ids.OperationId operation -> scopedId(out, operation.unit(), operation.localId());
            default -> throw new CfgJsonException("AIR correlation domain outside supported CFG JSON contracts");
        }
        out.raw("}");
    }

    private static void scopedId(CfgJsonBytes out, Ids.UnitId unit, String localId) throws CfgJsonException {
        out.raw("{\"publication\":"); out.string(unit.publication().localId());
        out.raw(",\"unit\":"); out.string(unit.localId());
        out.raw(",\"localId\":"); out.string(localId);
    }

    private static String inventory(Evidence.InventoryStatus status) {
        return switch (status) { case COMPLETE -> "COMPLETE"; case PARTIAL -> "PARTIAL"; case UNAVAILABLE -> "UNAVAILABLE"; };
    }
    private static String policy(ProjectionPolicy policy) {
        return switch (policy) { case KNOWN_SUBSET -> "KNOWN_SUBSET"; case STRICT -> "STRICT"; };
    }
    private static String haltKind(Operations.HaltKind kind) {
        return switch (kind) { case NORMAL -> "NORMAL"; case ABNORMAL -> "ABNORMAL"; };
    }
    /** Each explicit wire token declares whether it extends the closed v1 domain. */
    private enum WireKind {
        ENTRY("ENTRY", false), JUMP("JUMP", false), BRANCH("BRANCH", false),
        BRANCH_TRUE("BRANCH_TRUE", false), BRANCH_FALSE("BRANCH_FALSE", false),
        RETURN("RETURN", false), HALT("HALT", false),
        OPAQUE("OPAQUE", true), OPAQUE_JUMP("OPAQUE_JUMP", true), OPAQUE_RETURN("OPAQUE_RETURN", true),
        INVOKE("INVOKE", true), INVOKE_NORMAL("INVOKE_NORMAL", true);

        private final String token;
        private final boolean requiresV2;
        WireKind(String token, boolean requiresV2) {
            this.token = token;
            this.requiresV2 = requiresV2;
        }
    }

    private static WireKind transitionKind(CfgTransition.Kind kind) {
        return switch (kind) {
            case ENTRY -> WireKind.ENTRY; case JUMP -> WireKind.JUMP; case BRANCH_TRUE -> WireKind.BRANCH_TRUE;
            case BRANCH_FALSE -> WireKind.BRANCH_FALSE; case RETURN -> WireKind.RETURN; case HALT -> WireKind.HALT;
            case INVOKE_NORMAL -> WireKind.INVOKE_NORMAL;
            case OPAQUE_JUMP -> WireKind.OPAQUE_JUMP; case OPAQUE_RETURN -> WireKind.OPAQUE_RETURN;
            case OPAQUE_UNKNOWN -> throw new IllegalArgumentException("symbolic control is retained on AIR");
        };
    }
    private static WireKind terminatorKind(Terminator terminator) throws CfgJsonException {
        return switch (terminator) {
            case Operations.Opaque ignored -> WireKind.OPAQUE;
            case Operations.Jump ignored -> WireKind.JUMP;
            case Operations.Branch ignored -> WireKind.BRANCH;
            case Operations.Return ignored -> WireKind.RETURN;
            case Operations.Halt ignored -> WireKind.HALT;
            case Operations.Invoke ignored -> WireKind.INVOKE;
            default -> throw new CfgJsonException("terminator outside supported CFG JSON contracts");
        };
    }
}
