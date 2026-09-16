package io.github.gustavo2358.analysis.cfg.domain;

import io.github.gustavo2358.air.model.Evidence;

/** Inventory admission only; never relaxes AIR validity or supported control semantics. */
public enum ProjectionPolicy {
    /** Project supported published facts, retaining partial coverage without claiming exhaustive control. */
    KNOWN_SUBSET,
    /** Require complete inventory for this Publication and each Unit, regardless of language coverage. */
    STRICT,
    /** Preserve available evidence with explicit local uncertainty for unmodeled control. */
    PARTIAL_ANALYSIS;

    public boolean acceptsInventory(Evidence.InventoryStatus inventory) {
        return inventory == Evidence.InventoryStatus.COMPLETE
                || this != STRICT && inventory == Evidence.InventoryStatus.PARTIAL;
    }
}
