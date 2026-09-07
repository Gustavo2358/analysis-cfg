package io.github.gustavo2358.analysis.cfg.domain;

import io.github.gustavo2358.air.model.Ids.Id;

import java.util.Objects;

/** A valid AIR shape outside CFG-FIRST, correlated without textual opcode dispatch. */
public record CfgProjectionIssue(Code code, Id subject) {
    public CfgProjectionIssue {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(subject, "subject");
    }

    public enum Code {
        UNSUPPORTED_TERMINATOR,
        INSTRUCTIONS_OUTSIDE_SLICE,
        BODY_UNAVAILABLE,
        INCOMPLETE_INVENTORY,
        EXTENSION_SEMANTICS_OUTSIDE_SLICE
    }
}
