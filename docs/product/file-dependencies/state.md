# Estado / handoff curto — CORE N+C

**H4 aprovado; W0/W1 QUALIFIED_LOCAL; W2 IN_PROGRESS.**
Core W0–W9/W11 autorizado em 2026-09-16, sem aprovação mecânica entre waves.
W10 TODO / NOT_AUTHORIZED. Sem merge/auto-merge/release. STOP após W11.
Waves qualificadas permanecem IN_PROGRESS lean enquanto PRs estiverem unmerged.

| Wave | Estado técnico |
| --- | --- |
| W0 | QUALIFIED_LOCAL; declaração N-LR, SP/decoder/admission bilateral |
| W1 | QUALIFIED_LOCAL; A1–A4/A6, OPEN/READ/CLOSE, consumer FILE e wire2.0 |
| W2 | IN_PROGRESS; família nativa e operandos/handlers estruturais |
| W3–W9, W11 | TODO |
| W10 | TODO / NOT_AUTHORIZED |

## Checkpoint W1

| Repo | Commit / pin qualificado |
| --- | --- |
| proleap-poc | `0d1d84f8d5018ade6f97daed680b5da642ce113f`; SP2.22/fileInventory1.1 |
| cobol-lower | `70be551724e23faba2474dee8bd7a088e63af2ed`; SP acima, AIR abaixo |
| air-java | `d215d2bafbbabc714a3e9d0f2ff9e8927e0bf8f4` |
| analysis-ir | `fb153ae50f343022db45d20d627e1afac85de916`; resource.bindings@1 |
| analysis-cfg | commit W1 no Git; SHA exato no handoff local E2E |

W1: AIR model187/codec124 + FAST PASS; frontend focal135/FAST335 PASS; lower FAST
PASS; CFG C-DEP31/FAST485, zero skips; reader11 PASS. E-SELECTED: seis fixtures
fonte em duas execuções CLI reais, SP/AIR/CFG/dependency idênticos byte a byte.
CALL+FILE, origens, owner/record/use, declaração sem uso, OPEN agrupado, scope e
falha de saída provados. Logs RED/falhas/correções preservados, sem false PASS.

Evidência local: `artefatos-e2e/file-dependencies-20260916/w1/HANDOFF.md`.
Contrato/algoritmo: [implementação W1](w1-implementation.md).
Limites: efeitos/controle abertos até W3/W4; computed FILE aberto até W7;
CICS completo W8, escopos W9. Qualification-local/corpus/performance NOT_RUN
em W1, reservados aos checkpoints previstos. Nenhum blocker atual.

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

Próximo: AGENTS → página local → [brief](brief.md) →
[FD-W2](../../work/active/FD-W2.yaml) → perfil/casos/gates necessários.
E2E local/sem remote; não executar runner CP3 nem reler discovery bruto.

## Decisões

D-AIR CLOSED: prova de perda do modelo, extensão neutra mínima e A1–A4/A6 PASS.
D-WIRE CLOSED: writer2.0, reader novo fechado, rejeição antiga e projeção CALL PASS.
D-EFFECT aberta W3/W4; D-DYNAMIC/core aberta W7. D-D-AUTH/captura D reservadas W10.
IBM N-LR: SC27-8713-03, atualização 2026-04-28; hash/seções em [perfis](profiles.md).
