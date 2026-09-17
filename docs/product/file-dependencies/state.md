# Estado / handoff curto — CORE N+C

**W11 BLOCKED por C06 READ DATASET; demais gates finais PASS.**
H4 aprovado; execução core autorizada. W10 TODO / NOT_AUTHORIZED.
Nenhum merge/auto-merge/release. PRs Draft/unmerged; DONE lean não reivindicado.

| Wave | Estado técnico |
| --- | --- |
| W0 | QUALIFIED_LOCAL — declarativo N-LR e SP/decoder bilateral |
| W1 | QUALIFIED_LOCAL — D-AIR/D-WIRE, slice FILE/CALL/JSON |
| W2 | QUALIFIED_LOCAL — sete verbos nativos/operandos |
| W3 | QUALIFIED_LOCAL — buffers/efeitos regionais e CALL |
| W4 | QUALIFIED_LOCAL — handlers/status/USE |
| W5 | QUALIFIED_LOCAL — SORT/MERGE/SD local |
| W6 | QUALIFIED_LOCAL — auxiliares/checkpoint/SAME |
| W7 | QUALIFIED_LOCAL — computed CICS FILE no motor geral |
| W8 | BLOCKED somente na aceitação C06 READ DATASET; demais casos qualificados |
| W9 | QUALIFIED_LOCAL — unidades/owners/capturas/COPY |
| W11 | BLOCKED C06; gates executáveis restantes PASS |
| W10 | TODO / NOT_AUTHORIZED |

## Checkpoint final disponível

[Resultado W11](w11-qualification.md), [pins materiais](w11-pins.json),
[matriz](coverage.md). Handoff/evidência bruta durável no repo local:
`artefatos-e2e/file-dependencies-20260916/w11/HANDOFF.md`.

Bundle final: frontend aaecf8c1, lower24ee0ef9, AIR5fe0224e, CFG1ae5dfee,
IRfb153ae; SHAs completos nos pins. Frontend52ea26c acrescenta harness/docs,
sem delta src/pom. SP2.28/compilation1.0 e dependency wire2.3.

FAST/qualification-local dos quatro repos, barreira B-SP/B-AIR/B-WIRE,
101 fontes×2 determinísticas, MR1–MR9/SG core,21 witnesses de escala e5 mutantes
mortos PASS. Corpus73 inputs →71 produtos dependency PARTIAL,133CALL/376FILE;
vetores CALL comparáveis sem regressão. Duas recusas frontend e seis CFG estrito
preservadas. Nenhuma precisão/recall ou SLA afirmados.

C06: três READ DATASET em COACTVWC permanecem OBSERVED/EMBEDDED_LANGUAGE;
a autoridade5.6 encontrada prova DATASET somente para SET. Qualificação integral
W8 reaberta por esse achado W11; não mover para D, inferir o alias ou reduzir o
requisito. Necessária autoridade exata ou decisão humana explícita sobre C06.
Todo trabalho independente foi concluído. STOP por bloqueio material, sem W10.

## Retomada

Worktrees exclusivos: `<workspace>/.file-dependencies/worktrees/<repo>`.
Auditoria inicial dos cinco worktrees: limpos no branch feat/file-dependencies,
HEADs exatamente nos anchors H4. Registro `w0/initial-audit.json` no E2E.
Nenhum checkout original alterado. IR isolado criado somente após prova D-AIR.
Branch persistente em todos: `feat/file-dependencies`; PRs OPEN/DRAFT/UNMERGED:

| Repo | PR |
| --- | --- |
| proleap-poc | [#54](https://github.com/Gustavo2358/proleap-poc/pull/54) |
| cobol-lower | [#30](https://github.com/Gustavo2358/cobol-lower/pull/30) |
| air-java | [#19](https://github.com/Gustavo2358/air-java/pull/19) |
| analysis-cfg | [#38](https://github.com/Gustavo2358/analysis-cfg/pull/38) |
| analysis-ir | [#7](https://github.com/Gustavo2358/analysis-ir/pull/7) |

Retomada após resolver C06: AGENTS → página local → [brief](brief.md) →
[FD-W11](../../work/active/FD-W11.yaml) → perfil/casos/gates necessários.
E2E local/sem remote; não executar runner CP3 nem reler discovery bruto.

## Decisões

D-AIR CLOSED: prova de perda do modelo, extensão neutra mínima e A1–A4/A6 PASS.
D-WIRE CLOSED: writer2.3/file-values-context@1, reader fechado, rejeição antiga e projeção CALL PASS.
D-EFFECT CLOSED (memória W3 e controle W4, aproximações explícitas); D-DYNAMIC/core CLOSED W7; consulta BEFORE e política CICS FILE própria. D-D-AUTH/captura D reservadas W10.
IBM N-LR: SC27-8713-03, atualização 2026-04-28; hash/seções em [perfis](profiles.md).
