package io.github.gustavo2358.analysis.cfg.application;

import io.github.gustavo2358.air.model.Capabilities;
import io.github.gustavo2358.air.model.Ids.PublicationId;
import io.github.gustavo2358.air.model.SemanticVersion;
import io.github.gustavo2358.air.validation.ValidationResult;

import java.util.List;
import java.util.Objects;

/**
 * Result of the build boundary before a CFG product exists.
 *
 * <p>{@link Status#READY_FOR_CFG_PROJECTION} means only that the next semantic
 * stage may start. It never represents an empty graph or a successful CFG build.
 */
public record CfgBuildResult(
        Status status,
        PublicationId publicationId,
        SemanticVersion airVersion,
        BuildOptions options,
        ValidationResult preflight,
        List<Capabilities.Capability> unsupportedCapabilities) {

    public CfgBuildResult {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(publicationId, "publicationId");
        Objects.requireNonNull(airVersion, "airVersion");
        Objects.requireNonNull(options, "options");
        Objects.requireNonNull(preflight, "preflight");
        unsupportedCapabilities = List.copyOf(unsupportedCapabilities);
    }

    public enum Status {
        READY_FOR_CFG_PROJECTION,
        INVALID_IR,
        UNSUPPORTED_CAPABILITY,
        VALIDATION_LIMIT,
        INCOMPLETE_VALIDATION
    }
}
