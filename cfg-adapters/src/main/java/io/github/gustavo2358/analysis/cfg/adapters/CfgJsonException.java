package io.github.gustavo2358.analysis.cfg.adapters;

/** Expected CFG transport rejection, including output limit and unrepresentable UTF-16 input. */
public final class CfgJsonException extends Exception {
    private static final long serialVersionUID = 1L;

    public CfgJsonException(String message) { super(message); }
}
