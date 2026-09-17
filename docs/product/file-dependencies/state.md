# Estado / handoff curto — CORE N+C

**H4 aprovado; W0/W1/W2/W3/W4 QUALIFIED_LOCAL; próxima W5.**
Core W0–W9/W11 autorizado em 2026-09-16, sem aprovação mecânica entre waves.
W10 TODO / NOT_AUTHORIZED. Sem merge/auto-merge/release. STOP após W11.
Waves qualificadas permanecem IN_PROGRESS lean enquanto PRs estiverem unmerged.

| Wave | Estado técnico |
| --- | --- |
| W0 | QUALIFIED_LOCAL; declaração N-LR, SP/decoder/admission bilateral |
| W1 | QUALIFIED_LOCAL; A1–A4/A6, OPEN/READ/CLOSE, consumer FILE e wire2.0 |
| W2 | QUALIFIED_LOCAL; sete verbos nativos e operandos/handlers estruturais |
| W3 | QUALIFIED_LOCAL; memória regional, D-EFFECT/memória e CALL |
| W4 | QUALIFIED_LOCAL; handlers/status/USE, retorno delimitado |
| W5–W9, W11 | TODO; próxima W5 |
| W10 | TODO / NOT_AUTHORIZED |

## Checkpoint W2

| Repo | Commit / pin qualificado |
| --- | --- |
| proleap-poc | `b559292c97e004504fb867c4724298dc1637b6f2`; SP2.23/fileInventory1.2 |
| cobol-lower | `ee38519283a6b762b86704a6ee198748424b6825` |
| air-java | `d215d2bafbbabc714a3e9d0f2ff9e8927e0bf8f4` (W1, inalterado) |
| analysis-ir | `fb153ae50f343022db45d20d627e1afac85de916` (W1, inalterado) |
| analysis-cfg | commit W2 no Git; SHA exato no handoff local E2E |

W2: frontend focal147/FAST335; lower FAST core2340 + adapters; CFG C-DEP22 /
FAST485 / reader11; zero skips inesperados. E-SELECTED14 fontes duas vezes,
SP/AIR/CFG/dependency determinísticos. Record owner/FROM, sete ações, handlers
sem duplicação, CALL+FILE e composição IF/EVALUATE/PERFORM/GO TO PASS.

Evidência: `artefatos-e2e/file-dependencies-20260916/w2/HANDOFF.md`.
Contrato/limites: [W1](w1-implementation.md), [W2](w2-implementation.md).
Efeitos/status/outcomes abertos W3/W4; computed W7; CICS W8; escopos W9.
Qualification-local/corpus/performance NOT_RUN W2, previstos nos checkpoints.
Sem blocker. Próximo: D-EFFECT com autoridade exata e oráculos O1–O5/CALL-X.

## Checkpoint W3

| Repo | Commit / pin qualificado |
| --- | --- |
| proleap-poc | `a9f8fbe4fb4f9e2097c01b1e8f6f992a5041ee5f`; SP2.24/fileInventory1.3/storage1.8 |
| cobol-lower | `7165beb556b616287ea62ff58d65cc630bc9dab1` |
| AIR / norma | pins W1 acima, sem alteração |
| analysis-cfg | commit W3 no Git; SHA exato no handoff E2E |

FD/SD e aliases no storage geral; efeitos antes/depois por outcome; FROM anterior,
INTO após READ/status, MAY localizado e MUST somente com prova. B-SP fechado e
O1–O5 AIR manual PASS; nenhuma mudança produtiva CFG/AIR/IR. CALL disjunto conserva
suporte, READ não mantém singleton antigo exato; FROM não cria READ de outro FILE.

Frontend focal final111/FAST336/Q-SHARED887 (um skip histórico previsto) PASS.
Lower FAST core2340+adapters/Q-SHARED semântica244296/performance39215 PASS.
CFG C-VALUES17/C-DEP27/FAST/reader11 PASS. E-SELECTED20 fontes ×2, SP/AIR/CFG/
dependency determinísticos nos pins acima PASS. Logs/tentativas históricas e
outputs brutos preservados em `artefatos-e2e/file-dependencies-20260916/w3/`.

[D-EFFECT memória](d-effect.md) fechada; controle/status refinado/USE segue W4.
Limite provisório: bound aberto repete entradas do fonte no wire O(FILE×statements).
Corpus amplo NOT_RUN até W11; ausência de semântica W4 não é ocultada.
[Implementação e limites](w3-implementation.md). Sem blocker. Próxima wave W4.

## Checkpoint W4

Produtores commitados/pushed: frontend `1c21f21750aa3572e39fce71ccc156a357699d81`
(SP2.25/fileInventory1.4) e lower `9f80a1ff4f25f23d8ff2088a638f5e0f4dcf1ffc`.
Focal frontend162 + GO TO7 + entrada20, FAST336, Q-SHARED898 (um skip previsto)
PASS. Lower seis fixtures/negativos wire+memory, FAST2340+adapters, Q-SHARED
semântica244296/performance39215 PASS. C-VALUES17 e O1–O5 + USE2 manuais PASS.
AIR/IR e produção CFG inalterados. Clones imutáveis W4 preparados nos pins acima.
FAST CFG, reader11 e E-SELECTED28 fontes×2 PASS; quatro produtos determinísticos,
CALL+FILE/handler/USE/status/EOP nos pins exatos. [W4](w4-implementation.md).
Retorno compartilhado usa redução AIR com bound somente dos resumes reais e gap
de contexto, sem duplicar CALL ou efeitos. Regras/limites em D-EFFECT; logs novos
em `.harness-results/fd-w4`. PRs persistentes continuam Draft/unmerged.

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
[FD-W5](../../work/active/FD-W5.yaml) → perfil/casos/gates necessários.
E2E local/sem remote; não executar runner CP3 nem reler discovery bruto.

## Decisões

D-AIR CLOSED: prova de perda do modelo, extensão neutra mínima e A1–A4/A6 PASS.
D-WIRE CLOSED: writer2.0, reader novo fechado, rejeição antiga e projeção CALL PASS.
D-EFFECT CLOSED (memória W3 e controle W4, aproximações explícitas); D-DYNAMIC/core aberta W7. D-D-AUTH/captura D reservadas W10.
IBM N-LR: SC27-8713-03, atualização 2026-04-28; hash/seções em [perfis](profiles.md).
