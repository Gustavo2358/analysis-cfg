# File Dependencies CORE N+C — CLOSED

**MERGED / COMPLETE. Main smoke PASS. EP-R2 PRESERVED. C06 PASS.**
Open core blockers: NONE. H0–H4 e W0–W9/W11 DONE após merges e smoke.
W10 **DEFERRED / OPTIONAL_EXTENSION / NOT_PART_OF_CORE**; requer nova autorização
de produto. Nenhuma implementação D, release ou auto-merge neste closeout.

## Repositórios e pins reais

| Repo | Merge commit / main SHA usado no smoke | Former PR |
| --- | --- | --- |
| analysis-ir | `3fff18e2c16663a3f599207457caa1946d2e0945` | #7 |
| air-java | `135d91f4d643c80eeb5d7bff9081fae229e9e62c` | #19 |
| proleap-poc | `fe88cc16f664c26d27e8f975476fc2dfc3e6eff8` | #54 |
| cobol-lower | `9e746df89027de4aa0ec452f4e9a733d57eda866` | #30 |
| analysis-cfg | `812bbc6bb55b872eff91e30ba25a21e5e3447d5c` | #38 |

[Pins completos do smoke](closeout-pins.json). Todos os merges foram explícitos,
na ordem IR → AIR → frontend → lower → CFG, com HEAD exato, CI requerido PASS,
review threads zero, worktrees limpos e bases remotas qualificadas. IR não possui
workflow remoto; extensão neutra AIR2.0.0/resource.bindings@1 qualificada bilateralmente.
Os commits posteriores de encerramento são somente documentais. Seus HEADs exatos
estão no handoff local `artefatos-e2e/file-dependencies-20260916/closeout/HANDOFF.md`;
não invalidam pins materiais nem exigem repin por documentação.

## Qualification

- **PASS novo:** AIR FAST187 model/127 codec/40 harness, incluindo A1–A4/A6;
  lower FAST2340 e adapters (CICS35, declarations/scope/native/CALL), arquitetura;
  CFG FAST544,29 focais de CALL+FILE/computed/context/timing/EP-R2, wire21;
  C06 literal/computed/READ FILE control ×2 nos produtores já mergeados;
  CI dos PRs nos HEADs finais; gates documentais de fechamento.
- **PASS pós-merge:** clone/build limpo dos SHAs da tabela, sem binários antigos;
  quatro CLIs reais COBOL→SP2.28/compilation1.0→AIR→CFG/values→dependencies2.3.
  Fixture misto confirma CALL `$PROGA`, declaração F/external name CLIENTDD,
  OPEN/READ/CLOSE, READ DATASET('ACCOUNTS') como cics.file com spelling DATASET,
  computed ENDBR FILE(FN) em BEFORE unknown após ACCEPT, supports e reader estrito.
  Trace de acessos das quatro CLIs não abre recursos de negócio; boundary COBOL_SOURCE_ONLY.
- **REUSED_WITH_EQUIVALENCE_PROOF:** qualification-local e corpus73 pós-EP-R2,
  41CICS×2, leis/mutantes/performance e gates não afetados. IR tree-idêntica;
  demais deltas restritos a docs/pins/workflow checkout. Nenhum fonte ou oracle
  produtivo alterado. [Evidência anterior](post-ep-r2.md).
- **NOT_RUN:** novo full/corpus/performance campaign (nenhuma lei produtiva mudou),
  W10/perfil D (opcional não autorizado), release. Não há full novo alegado.

Uma tentativa lower FAST recusou o pin CI desatualizado; workflow corrigido para
coincidir com o lock e gate integral repetido PASS. Tentativa original preservada.
Comandos/exits, equivalência, logs e produtos novos:
`artefatos-e2e/file-dependencies-20260916/closeout/` (Git somente local).

## Capability entregue

**Programa → programa:** COBOL CALL e CICS Program Control, targets literais e
computados pelo motor geral de possible-values, com supports/remainders/provenance.
Entry simultâneo, values fatorizados, depth robustness e leading `$` do EP-R2 preservados.

**Programa → arquivo:** declarações IBM, logical file/external file name source-level;
OPEN/CLOSE/READ/WRITE/REWRITE/DELETE/START; FILE STATUS, handlers/USE;
SORT/MERGE/RELEASE/RETURN; I-O-CONTROL selecionado; owners/contained units/COPY;
catálogo CICS File Control selecionado, computed CICS FILE e C06 READ DATASET.
Consumer FILE independente; wire2.3; query BEFORE; known, conjunto fechado,
partial com remainder ou unknown conforme prova intraprogram. Não generaliza
DATASET para outros comandos não qualificados.

DSNAME resolution, JCL correlation, runtime allocation e external lookup são
**OUT OF PRODUCT**. Sua ausência não é UNKNOWN/PARTIAL/GAP. W10 também não é gap
core. Limites anteriores do corpus continuam explícitos: duas recusas frontend,
seis CFG estrito,71 dependency PARTIAL,52 aliases fora do conjunto qualificado.
Sem afirmação de precisão/recall global, completude de fontes ou SLA.

## STOP

Campanha core encerrada. Nenhuma feature pendente core, nenhuma nova wave/refactor.
W10 permanece opcional e exige nova autorização. Checkouts originais preservados.
