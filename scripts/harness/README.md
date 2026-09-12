# Harness

`lean.py` and `lean_project.py` define current gates. `run_gate.py` and shell scripts
provide compatibility command names. `test_lean.py` covers the current policy.

`validate_docs.py`, `validate_cp5.py`, `cp5_*_contract.py`, their legacy harness
tests and retrospective scope helpers are historical diagnostics only. They are
not called by FAST, build, qualification-local or lifecycle. Their results are
BEST_EFFORT and cannot block development. No historical normalization is required.
Technical tests under scripts/project and all Java product tests remain available.
