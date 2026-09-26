# Explicit dependency input fixture

Synthetic `TARGET.cbl` uses a fixed eight-character target, a MOVE of PROGA and CALL.
The checked-in AIR, R9 certificate and manifest were produced through the default
frontend and `CobolDependencyInput`. Expected target PROGA and two qualification
authorities are specified independently in DependencyInputCliTest.

Frontend baseline: 26db42a40976e930749c869a6f2dfbcce37ed878, SP 2.45.0.
AIR 2.0.0 and R9 1.0.0 remain unchanged contracts. The manifest hashes bind the exact
checked-in snapshots; mutation tests reject altered digests, IDs, versions and keys.
