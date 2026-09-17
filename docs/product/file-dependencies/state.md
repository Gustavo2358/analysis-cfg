# Estado / handoff curto — CORE N+C

**W0–W9 e W11 QUALIFIED_LOCAL_POST_EP_R2. STOP para revisão humana final.**
H4 aprovado; execução core autorizada. W10 TODO / NOT_AUTHORIZED.
Nenhum merge/auto-merge/release. PRs Draft/unmerged; DONE lean não reivindicado.

| Wave | Estado técnico |
| --- | --- |
| W0 | QUALIFIED_LOCAL_POST_EP_R2 — declarativo N-LR e SP/decoder bilateral |
| W1 | QUALIFIED_LOCAL_POST_EP_R2 — D-AIR/D-WIRE, slice FILE/CALL/JSON |
| W2 | QUALIFIED_LOCAL_POST_EP_R2 — sete verbos nativos/operandos |
| W3 | QUALIFIED_LOCAL_POST_EP_R2 — buffers/efeitos regionais e CALL |
| W4 | QUALIFIED_LOCAL_POST_EP_R2 — handlers/status/USE |
| W5 | QUALIFIED_LOCAL_POST_EP_R2 — SORT/MERGE/SD local |
| W6 | QUALIFIED_LOCAL_POST_EP_R2 — auxiliares/checkpoint/SAME |
| W7 | QUALIFIED_LOCAL_POST_EP_R2 — computed CICS FILE no motor geral |
| W8 | QUALIFIED_LOCAL_POST_EP_R2 — C-FC literal/computed e C06 humano |
| W9 | QUALIFIED_LOCAL_POST_EP_R2 — unidades/owners/capturas/COPY |
| W11 | QUALIFIED_LOCAL_POST_EP_R2 — integração/corpus/CALL e reuso justificado |
| W10 | TODO / NOT_AUTHORIZED |

## Checkpoint pós-main atual

[Handoff pós-EP-R2](post-ep-r2.md), [pins materiais](w11-pins.json).
Main6ef181d integrado por mergeb67df76; código/testes830d41a.
EP-R2 PRESERVED; C06 PASS. FAST544, qualification-local589, wire21,41CICS×2,
73 corpus novos:133CALL iguais ao main EP-R2,411FILE/85declarations preservados,
35READ DATASET incluindo3COACTVWC unknown. Duas recusas frontend/seis CFG e
71 dependency PARTIAL permanecem explícitas. Nenhuma alteração produtiva adicional
nem nos demais repos. PR #38 OPEN/DRAFT/MERGEABLE/UNMERGED; Fast CI código PASS.

[Resultado W11/C06 anterior](w11-qualification.md) e
`artefatos-e2e/file-dependencies-20260916/w11-c06/HANDOFF.md` são históricos;
a qualificação compartilhada e o corpus foram reexecutados neste checkpoint.

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

Para revisão ou futura retomada autorizada: AGENTS → página local → [brief](brief.md) →
[FD-W11](../../work/active/FD-W11.yaml) → perfil/casos/gates necessários.
E2E local/sem remote; não executar runner CP3 nem reler discovery bruto.

## Decisões

D-AIR CLOSED: prova de perda do modelo, extensão neutra mínima e A1–A4/A6 PASS.
D-WIRE CLOSED: writer2.3/file-values-context@1, reader fechado, rejeição antiga e projeção CALL PASS.
D-EFFECT CLOSED (memória W3 e controle W4, aproximações explícitas); D-DYNAMIC/core CLOSED W7; consulta BEFORE e política CICS FILE própria. D-D-AUTH/captura D reservadas W10.
IBM N-LR: SC27-8713-03, atualização 2026-04-28; hash/seções em [perfis](profiles.md).

C06 CLOSED por C06-HUMAN-20260917 e RED→GREEN bilateral/E2E. W10 permanece
TODO / NOT_AUTHORIZED. STOP; nenhuma próxima wave autorizada nesta sessão.
