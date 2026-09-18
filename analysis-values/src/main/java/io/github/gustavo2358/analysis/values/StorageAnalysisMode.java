package io.github.gustavo2358.analysis.values;

/** Product policy. Physical propagation is experimental and never a logical fallback. */
public enum StorageAnalysisMode {
    LOGICAL_ONLY("logical-text-candidates@1", "LOGICAL_CANDIDATES_OPEN"),
    EXPERIMENTAL_PHYSICAL(RegionalValuesAnalysis.PROFILE, "FINITE_CORRELATED_STORAGE_IMAGES");

    private final String profile, precision;
    StorageAnalysisMode(String profile,String precision) { this.profile=profile;this.precision=precision; }
    public String profile() { return profile; }
    public String precision() { return precision; }
    public boolean physical() { return this==EXPERIMENTAL_PHYSICAL; }
    public static StorageAnalysisMode fromPrecision(String precision) {
        for(var mode:values())if(mode.precision.equals(precision))return mode;
        throw new IllegalArgumentException("unsupported storage analysis policy");
    }
}
