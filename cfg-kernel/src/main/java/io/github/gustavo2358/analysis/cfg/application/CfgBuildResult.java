package io.github.gustavo2358.analysis.cfg.application;

import io.github.gustavo2358.air.model.Capabilities;
import io.github.gustavo2358.air.model.Ids.PublicationId;
import io.github.gustavo2358.air.model.SemanticVersion;
import io.github.gustavo2358.air.validation.ValidationResult;
import io.github.gustavo2358.analysis.cfg.domain.CfgGraph;
import io.github.gustavo2358.analysis.cfg.domain.CfgProjectionIssue;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** CFG_BUILT contains an actual core CFG product; every failure has no graph. No AIR profile claim. */
public record CfgBuildResult(
        Status status,
        PublicationId publicationId,
        SemanticVersion airVersion,
        BuildOptions options,
        ValidationResult preflight,
        List<Capabilities.Capability> unsupportedCapabilities,
        List<CfgProjectionIssue> projectionIssues,
        Optional<CfgGraph> graph) {

    public CfgBuildResult {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(publicationId, "publicationId");
        Objects.requireNonNull(airVersion, "airVersion");
        Objects.requireNonNull(options, "options");
        Objects.requireNonNull(preflight, "preflight");
        unsupportedCapabilities = List.copyOf(unsupportedCapabilities);
        projectionIssues = List.copyOf(projectionIssues);
        Objects.requireNonNull(graph, "graph");
        if ((status == Status.CFG_BUILT) != graph.isPresent()) {
            throw new IllegalArgumentException("only CFG_BUILT must contain a graph");
        }
        if (graph.isPresent()) {
            CfgGraph product = graph.orElseThrow();
            if (preflight.status() != ValidationResult.Status.STRUCTURALLY_VALID
                    || !unsupportedCapabilities.isEmpty() || !projectionIssues.isEmpty()
                    || !product.publication().id().equals(publicationId)
                    || !product.publication().airVersion().equals(airVersion)) {
                throw new IllegalArgumentException("CFG product disagrees with preflight or publication metadata");
            }
        }
    }

    public enum Status {
        CFG_BUILT,
        INVALID_IR,
        UNSUPPORTED_CAPABILITY,
        UNSUPPORTED_INPUT,
        VALIDATION_LIMIT,
        INCOMPLETE_VALIDATION
    }
}
