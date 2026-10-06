package io.github.gustavo2358.analysis.cfg.adapters;

import io.github.gustavo2358.air.model.Evidence;
import io.github.gustavo2358.air.model.Ids;
import io.github.gustavo2358.air.model.Operations;
import io.github.gustavo2358.air.model.Terminator;
import io.github.gustavo2358.analysis.cfg.application.CfgBuildResult;
import io.github.gustavo2358.analysis.cfg.domain.CfgNode;
import io.github.gustavo2358.analysis.cfg.domain.LocalControlRules;
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
        if (result.options().projectionPolicy() == ProjectionPolicy.PARTIAL_ANALYSIS)
            throw new CfgJsonException("PARTIAL_ANALYSIS requires the partial analysis result contract; legacy CFG JSON cannot encode contextual uncertainty");
        var graph = result.graph().orElseThrow();
        var out = new CfgJsonBytes(maximumBytes);
        // Token mappings carry their contract requirement. Inspect the product, not its source text.
        boolean requiresV5=!graph.localRules().isEmpty();
        boolean requiresV7=graph.localRules().values().stream().anyMatch(r->r instanceof LocalControlRules.Invoke i&&!i.resumeRoutes().isEmpty()
            ||r instanceof LocalControlRules.Boundary b&&b.resumeKey().isPresent()
            ||r instanceof LocalControlRules.Resume x&&x.resumeKey().isPresent()||r instanceof LocalControlRules.Unwind u&&u.all());
        boolean requiresV6=graph.localRules().values().stream().anyMatch(r->r instanceof LocalControlRules.Invoke i&&i.reentryGuard().isPresent());
        boolean requiresV4=graph.nodes().stream().anyMatch(CfgNode.OutcomeExit.class::isInstance)
            ||graph.transitions().stream().anyMatch(t->t.kind()==CfgTransition.Kind.EXCEPTION);
        boolean requiresV2 = false;
        boolean requiresV3 = graph.nodes().stream().anyMatch(n -> n instanceof CfgNode.SequenceNode q && q.source().terminator() instanceof Operations.Opaque);
        for (var node : graph.nodes()) {
            if (node instanceof CfgNode.SequenceNode sequence)
                requiresV2 |= terminatorKind(sequence.source().terminator()).requiresV2;
        }
        for (var transition : graph.transitions())
            requiresV2 |= transitionKind(transition.kind()).requiresV2;
        out.raw("{\"schema\":\"analysis-cfg-json\",\"schemaVersion\":");
        out.string(requiresV7 ? "7.0.0" : requiresV6 ? "6.0.0" : requiresV5 ? "5.0.0" : requiresV4 ? "4.0.0" : requiresV3 ? "3.0.0" : requiresV2 ? "2.0.0" : "1.0.0");
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
            node(out, node, requiresV3||requiresV4||requiresV5);
        }
        out.raw("],\"transitions\":["); comma = false;
        for (var transition : graph.transitions()) {
            if (comma) out.raw(","); comma = true;
            out.raw("{\"kind\":"); out.string(transitionKind(transition.kind()).token);
            out.raw(",\"from\":"); cfgId(out, transition.from());
            out.raw(",\"to\":"); cfgId(out, transition.to());
            out.raw(",\"activationEntry\":"); airId(out, transition.activationEntry()); out.raw("}");
        }
        out.raw("]");
        if(requiresV5) {
            out.raw(",\"localControl\":[");comma=false;
            for(var rule:graph.localRules().values()) {
                if(comma)out.raw(",");comma=true;localRule(out,rule);
            }
            out.raw("]");
        }
        out.raw("}");
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
            JsonFiles.write(temporary,destination,bytes);
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
            case CfgNode.OutcomeExit exit -> {
                out.raw(",\"kind\":\"OUTCOME_EXIT\",\"operation\":");airId(out,exit.source().header().id());
                out.raw(",\"outcome\":");
                out.string(exit.outcome() instanceof io.github.gustavo2358.air.model.Control.HaltAlternative?"HALT":exit.outcome() instanceof io.github.gustavo2358.air.model.Control.Exceptional?"EXCEPTION":"ANY_EXCEPTION");
                if(exit.outcome() instanceof io.github.gustavo2358.air.model.Control.Exceptional e){out.raw(",\"tag\":");out.string(e.tag());}
            }
            case CfgNode.HaltExit exit -> {
                out.raw(",\"kind\":\"HALT_EXIT\",\"operation\":"); airId(out, exit.source().header().id());
                out.raw(",\"haltKind\":"); out.string(haltKind(exit.source().haltKind()));
            }
        }
        out.raw("}");
    }

    private static void localRule(CfgJsonBytes out,LocalControlRules.Rule rule) throws CfgJsonException {
        out.raw("{\"source\":");cfgId(out,rule.source());out.raw(",\"operation\":");airId(out,rule.operation());
        switch(rule) {
            case LocalControlRules.Invoke i -> {
                out.raw(",\"kind\":\"LOCAL_INVOKE\",\"entry\":");cfgId(out,i.entry());
                out.raw(",\"resume\":");cfgId(out,i.resume());out.raw(",\"ports\":[");
                boolean comma=false;for(var port:i.ports()){if(comma)out.raw(",");comma=true;airId(out,port);}out.raw("]");
                if(i.reentryGuard().isPresent()) {
                    var guard=i.reentryGuard().orElseThrow();
                    out.raw(",\"reentryGuard\":{\"activationKey\":");out.string(guard.activationKey());
                    out.raw(",\"destination\":");cfgId(out,guard.destination());out.raw("}");
                }
                if(!i.resumeRoutes().isEmpty()) {
                    out.raw(",\"resumeRoutes\":[");boolean routeComma=false;
                    for(var entry:i.resumeRoutes().entrySet().stream().sorted(java.util.Map.Entry.comparingByKey()).toList()) {
                        if(routeComma)out.raw(",");routeComma=true;out.raw("{\"key\":");out.string(entry.getKey());
                        out.raw(",\"destination\":");cfgId(out,entry.getValue());out.raw("}");
                    }
                    out.raw("]");
                }
            }
            case LocalControlRules.Boundary b -> {
                out.raw(",\"kind\":\"LOCAL_BOUNDARY\",\"port\":");airId(out,b.port());
                out.raw(",\"defaultDestination\":");cfgId(out,b.defaultDestination());
                if(b.resumeKey().isPresent()) {
                    out.raw(",\"resumeKey\":");out.string(b.resumeKey().orElseThrow());
                    out.raw(",\"invalidExit\":");cfgId(out,b.invalidExit());
                }
            }
            case LocalControlRules.Resume r -> {
                out.raw(",\"kind\":\"LOCAL_RESUME\",\"invalidExit\":");cfgId(out,r.invalidExit());
                if(r.resumeKey().isPresent()){out.raw(",\"resumeKey\":");out.string(r.resumeKey().orElseThrow());}
            }
            case LocalControlRules.Unwind u -> {
                out.raw(",\"kind\":\"LOCAL_UNWIND\",\"count\":");out.string(u.count().toString(10));
                out.raw(",\"destination\":");cfgId(out,u.destination());out.raw(",\"invalidExit\":");cfgId(out,u.invalidExit());
                if(u.all())out.raw(",\"all\":true");
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
            case Ids.CompletionPortId port -> scopedId(out,port.unit(),port.localId());
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
        return switch (policy) { case KNOWN_SUBSET -> "KNOWN_SUBSET"; case STRICT -> "STRICT"; case PARTIAL_ANALYSIS -> "PARTIAL_ANALYSIS"; };
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
        EXCEPTION("EXCEPTION", true), CONTROL_EXIT("CONTROL_EXIT", true),
        LOCAL_INVOKE("LOCAL_INVOKE",true), LOCAL_BOUNDARY("LOCAL_BOUNDARY",true),
        LOCAL_RESUME("LOCAL_RESUME",true), LOCAL_UNWIND("LOCAL_UNWIND",true),
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
            case EXCEPTION -> WireKind.EXCEPTION; case CONTROL_EXIT -> WireKind.CONTROL_EXIT;
            case INVOKE_NORMAL -> WireKind.INVOKE_NORMAL;
            case OPAQUE_JUMP -> WireKind.OPAQUE_JUMP; case OPAQUE_RETURN -> WireKind.OPAQUE_RETURN;
            case OPAQUE_UNKNOWN, LOCAL -> throw new IllegalArgumentException("symbolic control is retained on AIR");
        };
    }
    private static WireKind terminatorKind(Terminator terminator) throws CfgJsonException {
        return switch (terminator) {
            case Operations.LocalInvoke ignored -> WireKind.LOCAL_INVOKE;
            case Operations.LocalBoundary ignored -> WireKind.LOCAL_BOUNDARY;
            case Operations.LocalResume ignored -> WireKind.LOCAL_RESUME;
            case Operations.LocalUnwind ignored -> WireKind.LOCAL_UNWIND;
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
