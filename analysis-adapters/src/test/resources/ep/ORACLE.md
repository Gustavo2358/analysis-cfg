# EP scoped precondition counterexample

Derived from `cp6/dynamic-x8.air.json` by adding an Assign of Read(target) to an ASCII View on a new Region after the literal assignment, before CALL. Every new operand belongs to ep-unproved-codec; original support/IDs are retained. No producer claim of valid strict transport is made: the physical encoding/extent preconditions on a runtime Read are not statically discharged.

Independent expectations: strict codec returns INCOMPLETE_VALIDATION; opt-in partial decoder preserves that status and exactly scopes ep-unproved-codec. Primary dependencies exit 0 with PROGA plus remainder and PARTIAL. RD BEFORE still includes the original literal Assign, RV BEFORE still includes PROGA. A missing object reference remains structural INVALID_IR and must preserve the previous output. This is an AIR admission oracle; source evidence is qualified separately by the generic source vertical.
