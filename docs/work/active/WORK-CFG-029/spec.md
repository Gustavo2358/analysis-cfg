# RESOURCE_LIMIT compatibility

## Problema
CP6-BASELINE-COMPAT-001: typed upstream RESOURCE_LIMIT falls through to generic incomplete.

## Objetivo
Preserve typed operational taxonomy and total diagnostic counts. No CFG, session, key, solver, facts or semantic/partial result after resource preflight failure. Same Publication succeeds with sufficient resources. CORE-SIZE-001 remains absolute.

## Fora de escopo
CP5 remains APPROVED/MERGED; no W6, CP6, Invoke, CallResolver, baseline sync, solver/lowering changes, other repins or merge.
