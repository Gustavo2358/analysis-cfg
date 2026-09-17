# Estado / handoff curto — CORE N+C

**H4 aprovado; W0 QUALIFIED_LOCAL; W1 IN_PROGRESS.**
Core W0–W9/W11 autorizado em 2026-09-16, sem nova aprovação entre waves.
W10 TODO / NOT_AUTHORIZED. Sem merge/auto-merge/release. STOP após W11.
Waves qualificadas permanecem IN_PROGRESS lean enquanto os PRs estiverem unmerged.

## Checkpoint confirmado

Auditoria inicial dos cinco worktrees persistentes: limpos, branch
`feat/file-dependencies`, HEADs exatamente nos anchors H4 fornecidos.
Nenhum checkout original alterado. Detalhes em
`artefatos-e2e/file-dependencies-20260916/w0/initial-audit.json`.

| Wave | Estado técnico / evidência |
| --- | --- |
| W0 | QUALIFIED_LOCAL; SP2.21.0/fileInventory1.0.0, decoder/admission bilateral |
| W1 | IN_PROGRESS; prova D-AIR e auditoria D-WIRE antes de produção |
| W2–W9, W11 | TODO |
| W10 | TODO / NOT_AUTHORIZED |

| Repo | Último commit produtivo / pin |
| --- | --- |
| proleap-poc | `9f9021fdf79854a1c097f3a06268824ca8aa489d` |
| cobol-lower | `22753af9ab45572612542251f93bbd298d3912d3`; SP no commit acima; AIR `5b8a5c231958b62de34583d871cbb10b473d53e8` |
| air-java | `baf848ab71a34f3dd666f60f5c59ae99cf95fb62`; sem delta W0 |
| analysis-cfg | sem delta produtivo W0; checkpoint documental no Git |

W0: frontend focal 129 testes e FAST fixo 335 testes, sem falhas/skips;
lower FAST fixo PASS (2340 checks core e suites adapter/FILE/CALL), quatro fixtures
regenerados no pin frontend byte a byte iguais aos fixtures lower. CFG docs gate
PASS (14 contracasos). Logs, comandos, exits, hashes e tentativas RED preservados em
`artefatos-e2e/file-dependencies-20260916/w0/HANDOFF.md` e `w0/logs/`.
NOT_RUN em W0: qualification-local, AIR FILE/consumer, E2E/corpus/performance.
Nenhum blocker atual. Nenhum PASS histórico reutilizado como prova FILE.

Autoridade N-LR obtida: SC27-8713-03, atualização 2026-04-28; hash e seções em
[perfis](profiles.md). W0 não publica execução FILE, efeitos nem dataflow de nomes.

## Retomada

Worktrees: `<workspace>/.file-dependencies/worktrees/<repo>`.
Branch persistente em todos: `feat/file-dependencies`. Draft PRs abertos:

| Repositório | PR persistente |
| --- | --- |
| proleap-poc | [#54](https://github.com/Gustavo2358/proleap-poc/pull/54) |
| cobol-lower | [#30](https://github.com/Gustavo2358/cobol-lower/pull/30) |
| air-java | [#19](https://github.com/Gustavo2358/air-java/pull/19) |
| analysis-cfg | [#38](https://github.com/Gustavo2358/analysis-cfg/pull/38) |

Próximo percurso: AGENTS → página local → [brief](brief.md) →
[FD-W1](../../work/active/FD-W1.yaml) → contratos/gates necessários.
W1 materializa A1–A4/A6, fecha D-AIR/D-WIRE e executa slice estático com
incompletude de efeitos/controle explícita até W3/W4. Não iniciar W10.
`analysis-ir` ainda intacto/sem PR; extensão somente após prova D-AIR.
E2E permanece local/sem remote. Handoff H4 histórico preservado no Git e em
`artefatos-e2e/file-dependencies-20260916/HANDOFF.md`; não reler discovery.

## Decisões

D-AIR e D-WIRE abertas, prazo W1 antes de contrato/saída.
D-EFFECT aberta para W3/W4; D-DYNAMIC/core aberta para W7.
D-D-AUTH e captura D reservadas a W10. Nenhuma envolve JCL/DSNAME.
