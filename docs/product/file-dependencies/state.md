# Estado / handoff curto — CORE N+C

**W0–W9 e W11 QUALIFIED_LOCAL. STOP para revisão humana final.**
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
| W8 | QUALIFIED_LOCAL — C-FC literal/computed e C06 humano |
| W9 | QUALIFIED_LOCAL — unidades/owners/capturas/COPY |
| W11 | QUALIFIED_LOCAL — integração/corpus/CALL e reuso justificado |
| W10 | TODO / NOT_AUTHORIZED |

## Checkpoint final

[Resultado W11](w11-qualification.md), [pins materiais](w11-pins.json),
[matriz](coverage.md), [decisão C06](c06-read-dataset.md). Handoff/evidência durável:
`artefatos-e2e/file-dependencies-20260916/w11-c06/HANDOFF.md`.
O diretório anterior `w11/` permanece histórico e imutável.

Bundle C06: frontend17323f4, lower5565e10, AIR5fe0224e, CFGe0e7559,
IRfb153ae; SHAs completos nos pins. Commit final CFG contém somente
harness/testes/docs/pins, sem alteração Java produtiva. SP2.28/compilation1.0,
wire2.3 inalterados. READ DATASET canônico FILE conserva spelling/provenance.

Novos gates PASS: F-CICS22, FAST frontend355/lower2340+adapters35, barreiras,
41 fixtures CICS×2 e pares FILE/DATASET literal/computed. Corpus21 reexecutados
(19 afetados+2 controles),52 REUSED:35 sites recuperados, incluindo os três de
COACTVWC;41 vetores CALL comparados inalterados. Total combinado133CALL/411FILE.
COACTVWC preserva nomes computados unknown, sem inferir inicializadores como
valor no comando.52 aliases de outros comandos continuam NOT_QUALIFIED.

Q anterior dos quatro repos, FAST AIR/CFG,101 fixtures×2, MR1–MR9/SG core,
21 witnesses de escala,5 mutantes e wire/reader são REUSED por delta produtivo
restrito aos aliases frontend/admission. Duas recusas frontend e seis CFG
estrito do corpus original permanecem;71 produtos dependency PARTIAL.
Nenhuma precisão/recall, completude de corpus ou SLA afirmados.

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
