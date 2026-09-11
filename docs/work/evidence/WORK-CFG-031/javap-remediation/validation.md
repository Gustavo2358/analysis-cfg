# W5 inventory harness remediation — verified locally

HARNESS-REGISTRY-DRIFT-001 and W5-JAVAP-DIAGNOSTIC-CONTAMINATION-001 are RESOLVED by the focal harness corrections. Production architecture changed: NO. Production/test Java, POMs, pins, expected inventories and validator logic remain unchanged.

Historical W5 failure: NOT REPRODUCED initially. Later independent inventory RED: REPRODUCED. Its captured cause is JAVAP STDERR DIAGNOSTIC CONTAMINATION. Causal identity with the oldest event is not established; historical architecture drift is NOT PROVEN. Earlier STOPs remain investigation history, byte-exact in the preservation receipt.

Both main at15bd3afe and the exact frozen candidate (manifest c88ac70d20be2eb1707d95e3a4535d9b999f7663b7a743e9d16bf0f05ed9cb4d) passed Maven270/0/0/0, focal/fast/docs/architecture/W5 isolated/semantic/integration/performance/full/scope/manifest/diff. Same patch, fresh isolated Maven repositories with no preinstalled first-party artifacts, clean upstream builds. Every command/log/hash is retained under validation.

The compiled adversary injects arbitrary stderr into every real javap/jdeps process: canonical inventory hash remains f7f268d52d267cd6a1a6052dbd4326588a9f87c58178a4074c498d0f6c969cee. Changed stdout descriptor is rejected. Nonzero exit preserves stdout/stderr and remains failure. No warning regex, inventory regeneration or semantic normalization.

Fresh CP4E A/B, CP3 and overwrite execute all producer stages, analyzer and memory oracle. CP4E remains PROGA with real Assign/origin and false/true/true remainders. CP3 has no analyses/queries/facts. Overwrite yields NEWER only supported by the last Assign. Memory/file and CP4E A/B are byte-identical. Compared against sealed CP5 products, COBOL/SP/AIR/result bytes remain identical.

Remote exact-head CI and merge receipts are published separately to avoid a self-referential commit. CP6 remains NOT_STARTED; no Invoke, CALL lowering or CallResolver.
