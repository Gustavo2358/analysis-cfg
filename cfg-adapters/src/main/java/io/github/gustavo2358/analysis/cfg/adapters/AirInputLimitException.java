package io.github.gustavo2358.analysis.cfg.adapters;

import java.io.IOException;

/** Physical IMPLEMENTATION_LIMIT before any codec call; no Publication has been materialized. */
public final class AirInputLimitException extends IOException {
    private static final long serialVersionUID = 1L;
    private final int maximumDocumentBytes;

    public AirInputLimitException(int maximumDocumentBytes) {
        super("AIR file exceeds maximumDocumentBytes=" + maximumDocumentBytes);
        this.maximumDocumentBytes = maximumDocumentBytes;
    }

    public int maximumDocumentBytes() { return maximumDocumentBytes; }
}
