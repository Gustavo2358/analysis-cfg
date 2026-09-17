# File Dependencies × EP-R2 — checkpoint pós-main

**IN_PROGRESS.** Esta integração não substitui a qualificação anterior por um
PASS novo. Somente analysis-cfg; W10/merge do PR/auto-merge/release não autorizados.

## Baselines

- File Dependencies: `749cef3d56cb084897970ef3301fec5b42dec53b`, worktree limpo,
  branch persistente feat/file-dependencies, PR #38.
- main/EP-R2: `6ef181d0b67ae1e93fdd635b348e4a70277dc15e`, após fetch; merge de #39.
- Merge normal, sem rebase/reset ou alteração de checkouts de outras campanhas.
- Pins frontend17323f4/lower5565e10/AIR5fe0224e/IRfb153ae preservados.

## Primeiro checkpoint: união mecânica antes dos testes

| Arquivo | Causa/resolução | EP-R2 preservado | FILE preservado |
| --- | --- | --- | --- |
| docs/evals/cp6/fast-test-inventory.json | Duas adições no mesmo ponto; união estrutural sem remover classes/métodos | DepthVertical/LeadingDollarProduct | Todas as classes FILE/computed/context/owner |
| docs/work/index.md | Duas entradas na primeira posição; manter ambas e atualizar o estado observado | EP-R2 integrado no main | Campanha FILE e checkpoint pós-main |

Demais arquivos fizeram merge automático, sem provar por si só compatibilidade:
AGENTS mantém a entrada FILE e regras pré-release/confidencialidade do EP-R2;
registry mantém as duas campanhas. dependency_wire/test_dependency_wire mantêm
resource/FILE2.3/COBOL_SOURCE_ONLY e LeadingDollarWireTests, regex CALL
`[A-Z_$][A-Z0-9_@#$]{0,7}`, política CICS FILE independente.
RD/EntryFacts/FactorizedAlternatives/RegionalValues/CallNameInterpreter entram
com o conteúdo do main; consumer FILE permanece separado, usando motor geral BEFORE.

`git diff --check` PASS; conflitos resolvidos antes de refatorações adicionais.
Logs locais: `.harness-results/fd-post-ep-r2/` (auditoria, merge e gates novos).

## Gates planejados por impacto

C4 shared core + C3 wire/consumer. Baseline pós-merge antes de corrigir expected;
falhas classificadas A textual/harness, B EP-R2, C FILE, D semântica legítima
EP-R2, E oracle antigo, F composição. FAST e qualification-local novos obrigatórios;
values/entry/depth/CALL/FILE/wire,41CICS×2/C06, scope/CALL-X, witnesses fatorizados.
Corpus completo73: values compartilhado invalida seleção estreita C06. CALL será
comparado contra main+EP-R2, não contra FILE pré-EP-R2. Resultados ainda NOT_RUN.
Nenhum dado corporativo/confidencial será usado ou versionado.
