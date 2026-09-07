package io.github.gustavo2358.analysis.cfg.domain;

import io.github.gustavo2358.air.model.Ids.Id;

import java.util.Objects;

/** A valid AIR shape rejected by the supported slice or inventory policy, correlated by AIR identity. */
public record CfgProjectionIssue(Code code, Id subject) {
    public CfgProjectionIssue {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(subject, "subject");
    }

    public enum Code {
        UNSUPPORTED_TERMINATOR,
        BODY_UNAVAILABLE,
        INCOMPLETE_INVENTORY,
        EXTENSION_SEMANTICS_OUTSIDE_SLICE
    }
}
