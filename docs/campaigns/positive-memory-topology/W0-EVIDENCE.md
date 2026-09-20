# W0 — evidence index

Campaign: POSITIVE_MEMORY_TOPOLOGY. All links freeze the effective baseline, not a moving branch. Code and fixtures were read locally at these SHAs. This is an architectural inventory, not a claim that every possible future frontend is covered.

## Source index

| ID | Source | Repository path |
| --- | --- | --- |
| IR1 | [analysis-ir: 03-memoria-e-aliases.md][IR1] | `especificacao/03-memoria-e-aliases.md` |
| IR2 | [analysis-ir: 07-contrato-de-produtores.md][IR2] | `especificacao/07-contrato-de-produtores.md` |
| IR3 | [analysis-ir: 06-incompletude-e-proveniencia.md][IR3] | `especificacao/06-incompletude-e-proveniencia.md` |
| IR4 | [analysis-ir: 09-extensibilidade-e-compatibilidade.md][IR4] | `especificacao/09-extensibilidade-e-compatibilidade.md` |
| A1 | [air-java: Memory.java][A1] | `air-model/src/main/java/io/github/gustavo2358/air/model/Memory.java` |
| A2 | [air-java: Proofs.java][A2] | `air-model/src/main/java/io/github/gustavo2358/air/model/Proofs.java` |
| A3 | [air-java: OperationChecks.java][A3] | `air-model/src/main/java/io/github/gustavo2358/air/validation/OperationChecks.java` |
| A4 | [air-java: BindingReader.java][A4] | `air-json/src/main/java/io/github/gustavo2358/air/json/BindingReader.java` |
| F1 | [proleap-poc: StorageComponents.java][F1] | `src/main/java/io/github/gustavo2358/cobolexplorer/StorageComponents.java` |
| F2 | [proleap-poc: StorageLayoutSemantics.java][F2] | `src/main/java/io/github/gustavo2358/cobolexplorer/StorageLayoutSemantics.java` |
| F3 | [proleap-poc: StorageRenames.java][F3] | `src/main/java/io/github/gustavo2358/cobolexplorer/StorageRenames.java` |
| F4 | [proleap-poc: StatementEffectSummary.java][F4] | `src/main/java/io/github/gustavo2358/cobolexplorer/StatementEffectSummary.java` |
| F5 | [proleap-poc: FileStorageGroups.java][F5] | `src/main/java/io/github/gustavo2358/cobolexplorer/FileStorageGroups.java` |
| L1 | [cobol-lower: RegionalDataTranslator.java][L1] | `core/src/main/java/io/github/gustavo2358/lower/application/RegionalDataTranslator.java` |
| L2 | [cobol-lower: StoragePremise.java][L2] | `core/src/main/java/io/github/gustavo2358/lower/application/StoragePremise.java` |
| L3 | [cobol-lower: LogicalTextMove.java][L3] | `core/src/main/java/io/github/gustavo2358/lower/application/LogicalTextMove.java` |
| L4 | [cobol-lower: LogicalTextIndex.java][L4] | `core/src/main/java/io/github/gustavo2358/lower/application/LogicalTextIndex.java` |
| L5 | [cobol-lower: OpaqueOperands.java][L5] | `core/src/main/java/io/github/gustavo2358/lower/application/OpaqueOperands.java` |
| L6 | [cobol-lower: InvokeHandler.java][L6] | `core/src/main/java/io/github/gustavo2358/lower/application/InvokeHandler.java` |
| L7 | [cobol-lower: CicsInvokeHandler.java][L7] | `core/src/main/java/io/github/gustavo2358/lower/application/CicsInvokeHandler.java` |
| L8 | [cobol-lower: PartialProgramAssembler.java][L8] | `core/src/main/java/io/github/gustavo2358/lower/application/PartialProgramAssembler.java` |
| C1 | [analysis-cfg: StorageIndex.java][C1] | `analysis-kernel/src/main/java/io/github/gustavo2358/analysis/storage/StorageIndex.java` |
| C2 | [analysis-cfg: StatementEffects.java][C2] | `analysis-kernel/src/main/java/io/github/gustavo2358/analysis/storage/StatementEffects.java` |
| C3 | [analysis-cfg: RegionalValuesAnalysis.java][C3] | `analysis-values/src/main/java/io/github/gustavo2358/analysis/values/RegionalValuesAnalysis.java` |
| C4 | [analysis-cfg: RegionalAlternatives.java][C4] | `analysis-values/src/main/java/io/github/gustavo2358/analysis/values/RegionalAlternatives.java` |
| C5 | [analysis-cfg: TextProfile.java][C5] | `analysis-values/src/main/java/io/github/gustavo2358/analysis/values/TextProfile.java` |
| C6 | [analysis-cfg: DefinitionEvent.java][C6] | `analysis-kernel/src/main/java/io/github/gustavo2358/analysis/rd/DefinitionEvent.java` |
| C7 | [analysis-cfg: KillAuthority.java][C7] | `analysis-kernel/src/main/java/io/github/gustavo2358/analysis/storage/KillAuthority.java` |
| C8 | [analysis-cfg: ConservativeEffectTransfer.java][C8] | `analysis-values/src/main/java/io/github/gustavo2358/analysis/values/ConservativeEffectTransfer.java` |
| C9 | [analysis-cfg: ForeignEffectTransfer.java][C9] | `analysis-values/src/main/java/io/github/gustavo2358/analysis/values/ForeignEffectTransfer.java` |
| C10 | [analysis-cfg: CallDependencyPlan.java][C10] | `analysis-dependencies/src/main/java/io/github/gustavo2358/analysis/dependencies/CallDependencyPlan.java` |
| C11 | [analysis-cfg: CallDependencyConsumer.java][C11] | `analysis-dependencies/src/main/java/io/github/gustavo2358/analysis/dependencies/CallDependencyConsumer.java` |
| C12 | [analysis-cfg: CoreCfgProjection.java][C12] | `cfg-kernel/src/main/java/io/github/gustavo2358/analysis/cfg/domain/CoreCfgProjection.java` |
| C13 | [analysis-cfg: IndexBuilder.java][C13] | `analysis-kernel/src/main/java/io/github/gustavo2358/analysis/structure/IndexBuilder.java` |
| T1 | [analysis-cfg: StorageIndexTest.java][T1] | `analysis-kernel/src/test/java/io/github/gustavo2358/analysis/storage/StorageIndexTest.java` |
| T2 | [analysis-cfg: RegionalExplosionFixturesTest.java][T2] | `analysis-values/src/test/java/io/github/gustavo2358/analysis/values/RegionalExplosionFixturesTest.java` |
| T3 | [analysis-cfg: RegionalCostProbe.java][T3] | `analysis-values/src/test/java/io/github/gustavo2358/analysis/values/RegionalCostProbe.java` |
| T4 | [analysis-cfg: RegionalFallbackStressTest.java][T4] | `analysis-values/src/test/java/io/github/gustavo2358/analysis/values/RegionalFallbackStressTest.java` |
| D1 | [analysis-cfg: logical-text-w2.md][D1] | `docs/product/logical-text-w2.md` |
| D2 | [analysis-cfg: discovery-regional-explosion-fixtures.md][D2] | `docs/discovery-regional-explosion-fixtures.md` |
| D3 | [analysis-cfg: dependency-preservation.md][D3] | `docs/domain/dependency-preservation.md` |
| D4 | [analysis-cfg: extensibility.md][D4] | `docs/architecture/extensibility.md` |

## Exhaustive constructor-site scan at the inspected lowering boundary

Each row is a producer-published broad bound in the resulting AIR (**yes** at the consumer boundary). A publication may be motivated by a frontend gap; that is separately inventoried in W0-DISCOVERY. Read bounds do not themselves create writes. This scan covers every `new Scopes.AllMemory`, `new Scopes.VisibleMemory`, and `new Memory.UnknownBinding` constructor in current lower application production Java; helpers/callers are mapped in the discovery.

| File:line | Published construction |
| --- | --- |
| [CicsFileInvokeHandler.java:51](https://github.com/Gustavo2358/cobol-lower/blob/f8e181f95929c650181c989318f8ba23d1e68a1a/core/src/main/java/io/github/gustavo2358/lower/application/CicsFileInvokeHandler.java#L51) | VisibleMemory |
| [CicsFileMemory.java:19](https://github.com/Gustavo2358/cobol-lower/blob/f8e181f95929c650181c989318f8ba23d1e68a1a/core/src/main/java/io/github/gustavo2358/lower/application/CicsFileMemory.java#L19) | VisibleMemory |
| [CicsFileMemory.java:23](https://github.com/Gustavo2358/cobol-lower/blob/f8e181f95929c650181c989318f8ba23d1e68a1a/core/src/main/java/io/github/gustavo2358/lower/application/CicsFileMemory.java#L23) | VisibleMemory |
| [CicsFileMemory.java:27](https://github.com/Gustavo2358/cobol-lower/blob/f8e181f95929c650181c989318f8ba23d1e68a1a/core/src/main/java/io/github/gustavo2358/lower/application/CicsFileMemory.java#L27) | VisibleMemory |
| [CicsFileMemory.java:37](https://github.com/Gustavo2358/cobol-lower/blob/f8e181f95929c650181c989318f8ba23d1e68a1a/core/src/main/java/io/github/gustavo2358/lower/application/CicsFileMemory.java#L37) | VisibleMemory |
| [CicsInvokeHandler.java:75](https://github.com/Gustavo2358/cobol-lower/blob/f8e181f95929c650181c989318f8ba23d1e68a1a/core/src/main/java/io/github/gustavo2358/lower/application/CicsInvokeHandler.java#L75) | AllMemory |
| [CicsInvokeHandler.java:90](https://github.com/Gustavo2358/cobol-lower/blob/f8e181f95929c650181c989318f8ba23d1e68a1a/core/src/main/java/io/github/gustavo2358/lower/application/CicsInvokeHandler.java#L90) | AllMemory |
| [ConditionalGoToLowerer.java:55](https://github.com/Gustavo2358/cobol-lower/blob/f8e181f95929c650181c989318f8ba23d1e68a1a/core/src/main/java/io/github/gustavo2358/lower/application/ConditionalGoToLowerer.java#L55) | AllMemory |
| [EvaluateLowerer.java:34](https://github.com/Gustavo2358/cobol-lower/blob/f8e181f95929c650181c989318f8ba23d1e68a1a/core/src/main/java/io/github/gustavo2358/lower/application/EvaluateLowerer.java#L34) | AllMemory |
| [FileAuxiliaryLowering.java:33](https://github.com/Gustavo2358/cobol-lower/blob/f8e181f95929c650181c989318f8ba23d1e68a1a/core/src/main/java/io/github/gustavo2358/lower/application/FileAuxiliaryLowering.java#L33) | VisibleMemory |
| [FileMemoryLowering.java:52](https://github.com/Gustavo2358/cobol-lower/blob/f8e181f95929c650181c989318f8ba23d1e68a1a/core/src/main/java/io/github/gustavo2358/lower/application/FileMemoryLowering.java#L52) | VisibleMemory |
| [FileMemoryLowering.java:58](https://github.com/Gustavo2358/cobol-lower/blob/f8e181f95929c650181c989318f8ba23d1e68a1a/core/src/main/java/io/github/gustavo2358/lower/application/FileMemoryLowering.java#L58) | VisibleMemory |
| [FileResourceLowering.java:81](https://github.com/Gustavo2358/cobol-lower/blob/f8e181f95929c650181c989318f8ba23d1e68a1a/core/src/main/java/io/github/gustavo2358/lower/application/FileResourceLowering.java#L81) | VisibleMemory |
| [FileResourceLowering.java:84](https://github.com/Gustavo2358/cobol-lower/blob/f8e181f95929c650181c989318f8ba23d1e68a1a/core/src/main/java/io/github/gustavo2358/lower/application/FileResourceLowering.java#L84) | VisibleMemory |
| [FileResourceLowering.java:85](https://github.com/Gustavo2358/cobol-lower/blob/f8e181f95929c650181c989318f8ba23d1e68a1a/core/src/main/java/io/github/gustavo2358/lower/application/FileResourceLowering.java#L85) | VisibleMemory |
| [FileSortLowering.java:81](https://github.com/Gustavo2358/cobol-lower/blob/f8e181f95929c650181c989318f8ba23d1e68a1a/core/src/main/java/io/github/gustavo2358/lower/application/FileSortLowering.java#L81) | VisibleMemory |
| [InvokeHandler.java:67](https://github.com/Gustavo2358/cobol-lower/blob/f8e181f95929c650181c989318f8ba23d1e68a1a/core/src/main/java/io/github/gustavo2358/lower/application/InvokeHandler.java#L67) | AllMemory |
| [InvokeHandler.java:77](https://github.com/Gustavo2358/cobol-lower/blob/f8e181f95929c650181c989318f8ba23d1e68a1a/core/src/main/java/io/github/gustavo2358/lower/application/InvokeHandler.java#L77) | AllMemory |
| [InvokeHandler.java:87](https://github.com/Gustavo2358/cobol-lower/blob/f8e181f95929c650181c989318f8ba23d1e68a1a/core/src/main/java/io/github/gustavo2358/lower/application/InvokeHandler.java#L87) | AllMemory |
| [OpaqueOperands.java:49](https://github.com/Gustavo2358/cobol-lower/blob/f8e181f95929c650181c989318f8ba23d1e68a1a/core/src/main/java/io/github/gustavo2358/lower/application/OpaqueOperands.java#L49) | AllMemory |
| [RegionalDataTranslator.java:146](https://github.com/Gustavo2358/cobol-lower/blob/f8e181f95929c650181c989318f8ba23d1e68a1a/core/src/main/java/io/github/gustavo2358/lower/application/RegionalDataTranslator.java#L146) | AllMemory, UnknownBinding |

## Search coverage and provenance

- Searched all production Java in CFG kernel/analysis-kernel/analysis-values/analysis-dependencies, all lower application Java, frontend storage/effect facts, AIR model/validation/JSON, and normative storage/producer/incompleteness/compatibility chapters.
- Search terms: `UNPROVEN_BASE_SEPARATION`, `DisjointStorage`, `AllMemory`, `VisibleMemory`, `UnknownBinding`, `remainder`, `sourceApplicable`, `MAY_SET`, `unprovedPreconditions`, `fallback`, `Events`, `unknown`, `alias`, `REDEFINES`, `RENAMES`, `allocation`. No semantic inference from source spellings was added to product code.
- Historical decision lookup: CFG `git log --all -S UNPROVEN_BASE_SEPARATION -- .../StatementEffects.java` first points to `f80faac` (ST-W2.1). IR `git log --all -S disjoint_storage -- especificacao/03-memoria-e-aliases.md` points to `a6b771e` (normative JSON-binding reconciliation). Existing ID-implies-disjoint mutation evidence is historical in WORK-CFG-028; it is not rerun or rewritten.
- GitHub PR snapshots for the latest eight PRs per repository and exact-head checks were fetched on 2026-09-20 and retained locally under `.positive-memory-topology/evidence/*-prs.json`. Body text is historical context; current state/head/base/check fields determine the stack.
- Existing dirty E2E workspace: local HEAD `bcc47fea8b14eb78a8eb1c5af3081664b267838f`, branch `docs/cp6-w2-discovery`; `roadmap.md` and numerous untracked evidence directories pre-existed. They were not modified. The E2E repo has no remote.
- No corporate source, identifiers, mappings or raw corporate outputs were read or published in W0. Previously published aggregate incident limits are explicitly reused.

[IR1]: https://github.com/Gustavo2358/analysis-ir/blob/3fff18e2c16663a3f599207457caa1946d2e0945/especificacao/03-memoria-e-aliases.md
[IR2]: https://github.com/Gustavo2358/analysis-ir/blob/3fff18e2c16663a3f599207457caa1946d2e0945/especificacao/07-contrato-de-produtores.md
[IR3]: https://github.com/Gustavo2358/analysis-ir/blob/3fff18e2c16663a3f599207457caa1946d2e0945/especificacao/06-incompletude-e-proveniencia.md
[IR4]: https://github.com/Gustavo2358/analysis-ir/blob/3fff18e2c16663a3f599207457caa1946d2e0945/especificacao/09-extensibilidade-e-compatibilidade.md
[A1]: https://github.com/Gustavo2358/air-java/blob/646ca3ab1687d43f7d2063fc2a8f3837ab3cf9fa/air-model/src/main/java/io/github/gustavo2358/air/model/Memory.java
[A2]: https://github.com/Gustavo2358/air-java/blob/646ca3ab1687d43f7d2063fc2a8f3837ab3cf9fa/air-model/src/main/java/io/github/gustavo2358/air/model/Proofs.java
[A3]: https://github.com/Gustavo2358/air-java/blob/646ca3ab1687d43f7d2063fc2a8f3837ab3cf9fa/air-model/src/main/java/io/github/gustavo2358/air/validation/OperationChecks.java
[A4]: https://github.com/Gustavo2358/air-java/blob/646ca3ab1687d43f7d2063fc2a8f3837ab3cf9fa/air-json/src/main/java/io/github/gustavo2358/air/json/BindingReader.java
[F1]: https://github.com/Gustavo2358/proleap-poc/blob/edb64520a6269be9fa6d71cd47e6974112fbfece/src/main/java/io/github/gustavo2358/cobolexplorer/StorageComponents.java
[F2]: https://github.com/Gustavo2358/proleap-poc/blob/edb64520a6269be9fa6d71cd47e6974112fbfece/src/main/java/io/github/gustavo2358/cobolexplorer/StorageLayoutSemantics.java
[F3]: https://github.com/Gustavo2358/proleap-poc/blob/edb64520a6269be9fa6d71cd47e6974112fbfece/src/main/java/io/github/gustavo2358/cobolexplorer/StorageRenames.java
[F4]: https://github.com/Gustavo2358/proleap-poc/blob/edb64520a6269be9fa6d71cd47e6974112fbfece/src/main/java/io/github/gustavo2358/cobolexplorer/StatementEffectSummary.java
[F5]: https://github.com/Gustavo2358/proleap-poc/blob/edb64520a6269be9fa6d71cd47e6974112fbfece/src/main/java/io/github/gustavo2358/cobolexplorer/FileStorageGroups.java
[L1]: https://github.com/Gustavo2358/cobol-lower/blob/f8e181f95929c650181c989318f8ba23d1e68a1a/core/src/main/java/io/github/gustavo2358/lower/application/RegionalDataTranslator.java
[L2]: https://github.com/Gustavo2358/cobol-lower/blob/f8e181f95929c650181c989318f8ba23d1e68a1a/core/src/main/java/io/github/gustavo2358/lower/application/StoragePremise.java
[L3]: https://github.com/Gustavo2358/cobol-lower/blob/f8e181f95929c650181c989318f8ba23d1e68a1a/core/src/main/java/io/github/gustavo2358/lower/application/LogicalTextMove.java
[L4]: https://github.com/Gustavo2358/cobol-lower/blob/f8e181f95929c650181c989318f8ba23d1e68a1a/core/src/main/java/io/github/gustavo2358/lower/application/LogicalTextIndex.java
[L5]: https://github.com/Gustavo2358/cobol-lower/blob/f8e181f95929c650181c989318f8ba23d1e68a1a/core/src/main/java/io/github/gustavo2358/lower/application/OpaqueOperands.java
[L6]: https://github.com/Gustavo2358/cobol-lower/blob/f8e181f95929c650181c989318f8ba23d1e68a1a/core/src/main/java/io/github/gustavo2358/lower/application/InvokeHandler.java
[L7]: https://github.com/Gustavo2358/cobol-lower/blob/f8e181f95929c650181c989318f8ba23d1e68a1a/core/src/main/java/io/github/gustavo2358/lower/application/CicsInvokeHandler.java
[L8]: https://github.com/Gustavo2358/cobol-lower/blob/f8e181f95929c650181c989318f8ba23d1e68a1a/core/src/main/java/io/github/gustavo2358/lower/application/PartialProgramAssembler.java
[C1]: https://github.com/Gustavo2358/analysis-cfg/blob/98fa57c3db2edf9f70bb7a99bb667dbf36d28104/analysis-kernel/src/main/java/io/github/gustavo2358/analysis/storage/StorageIndex.java
[C2]: https://github.com/Gustavo2358/analysis-cfg/blob/98fa57c3db2edf9f70bb7a99bb667dbf36d28104/analysis-kernel/src/main/java/io/github/gustavo2358/analysis/storage/StatementEffects.java
[C3]: https://github.com/Gustavo2358/analysis-cfg/blob/98fa57c3db2edf9f70bb7a99bb667dbf36d28104/analysis-values/src/main/java/io/github/gustavo2358/analysis/values/RegionalValuesAnalysis.java
[C4]: https://github.com/Gustavo2358/analysis-cfg/blob/98fa57c3db2edf9f70bb7a99bb667dbf36d28104/analysis-values/src/main/java/io/github/gustavo2358/analysis/values/RegionalAlternatives.java
[C5]: https://github.com/Gustavo2358/analysis-cfg/blob/98fa57c3db2edf9f70bb7a99bb667dbf36d28104/analysis-values/src/main/java/io/github/gustavo2358/analysis/values/TextProfile.java
[C6]: https://github.com/Gustavo2358/analysis-cfg/blob/98fa57c3db2edf9f70bb7a99bb667dbf36d28104/analysis-kernel/src/main/java/io/github/gustavo2358/analysis/rd/DefinitionEvent.java
[C7]: https://github.com/Gustavo2358/analysis-cfg/blob/98fa57c3db2edf9f70bb7a99bb667dbf36d28104/analysis-kernel/src/main/java/io/github/gustavo2358/analysis/storage/KillAuthority.java
[C8]: https://github.com/Gustavo2358/analysis-cfg/blob/98fa57c3db2edf9f70bb7a99bb667dbf36d28104/analysis-values/src/main/java/io/github/gustavo2358/analysis/values/ConservativeEffectTransfer.java
[C9]: https://github.com/Gustavo2358/analysis-cfg/blob/98fa57c3db2edf9f70bb7a99bb667dbf36d28104/analysis-values/src/main/java/io/github/gustavo2358/analysis/values/ForeignEffectTransfer.java
[C10]: https://github.com/Gustavo2358/analysis-cfg/blob/98fa57c3db2edf9f70bb7a99bb667dbf36d28104/analysis-dependencies/src/main/java/io/github/gustavo2358/analysis/dependencies/CallDependencyPlan.java
[C11]: https://github.com/Gustavo2358/analysis-cfg/blob/98fa57c3db2edf9f70bb7a99bb667dbf36d28104/analysis-dependencies/src/main/java/io/github/gustavo2358/analysis/dependencies/CallDependencyConsumer.java
[C12]: https://github.com/Gustavo2358/analysis-cfg/blob/98fa57c3db2edf9f70bb7a99bb667dbf36d28104/cfg-kernel/src/main/java/io/github/gustavo2358/analysis/cfg/domain/CoreCfgProjection.java
[C13]: https://github.com/Gustavo2358/analysis-cfg/blob/98fa57c3db2edf9f70bb7a99bb667dbf36d28104/analysis-kernel/src/main/java/io/github/gustavo2358/analysis/structure/IndexBuilder.java
[T1]: https://github.com/Gustavo2358/analysis-cfg/blob/98fa57c3db2edf9f70bb7a99bb667dbf36d28104/analysis-kernel/src/test/java/io/github/gustavo2358/analysis/storage/StorageIndexTest.java
[T2]: https://github.com/Gustavo2358/analysis-cfg/blob/98fa57c3db2edf9f70bb7a99bb667dbf36d28104/analysis-values/src/test/java/io/github/gustavo2358/analysis/values/RegionalExplosionFixturesTest.java
[T3]: https://github.com/Gustavo2358/analysis-cfg/blob/98fa57c3db2edf9f70bb7a99bb667dbf36d28104/analysis-values/src/test/java/io/github/gustavo2358/analysis/values/RegionalCostProbe.java
[T4]: https://github.com/Gustavo2358/analysis-cfg/blob/98fa57c3db2edf9f70bb7a99bb667dbf36d28104/analysis-values/src/test/java/io/github/gustavo2358/analysis/values/RegionalFallbackStressTest.java
[D1]: https://github.com/Gustavo2358/analysis-cfg/blob/98fa57c3db2edf9f70bb7a99bb667dbf36d28104/docs/product/logical-text-w2.md
[D2]: https://github.com/Gustavo2358/analysis-cfg/blob/98fa57c3db2edf9f70bb7a99bb667dbf36d28104/docs/discovery-regional-explosion-fixtures.md
[D3]: https://github.com/Gustavo2358/analysis-cfg/blob/98fa57c3db2edf9f70bb7a99bb667dbf36d28104/docs/domain/dependency-preservation.md
[D4]: https://github.com/Gustavo2358/analysis-cfg/blob/98fa57c3db2edf9f70bb7a99bb667dbf36d28104/docs/architecture/extensibility.md
