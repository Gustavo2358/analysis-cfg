# File Dependencies × EP-R2 — checkpoint pós-main

**CORE N+C: QUALIFIED_LOCAL_POST_EP_R2. EP-R2: PRESERVED. C06: PASS.**
STOP para revisão humana. W10 TODO / NOT_AUTHORIZED. PR #38 OPEN/DRAFT/UNMERGED;
nenhum merge do PR, auto-merge ou release. Work items continuam IN_PROGRESS lean.

## Baselines e identidade

- File Dependencies anterior: `749cef3d56cb084897970ef3301fec5b42dec53b`.
- main/EP-R2 integrado: `6ef181d0b67ae1e93fdd635b348e4a70277dc15e`.
- Merge normal: `b67df7664c4899488f1bbb0a00bc7be5de21cf95`.
- Código/testes qualificados: `830d41ab10573f2c16bcfb3d8aa916ba88b19e45`.
  O commit deste handoff só altera documentação/pins/evidência; não exige repin
  dos produtores. [Pins materiais completos](w11-pins.json).
- Branch persistente `feat/file-dependencies`; somente analysis-cfg alterado.
  Auditoria inicial limpa; sem reset/rebase nem alteração dos checkouts originais.
- RD, EntryFacts, FactorizedAlternatives, RegionalValuesAnalysis e
  CallNameInterpreter são byte a byte iguais ao main. Não houve correção produtiva
  adicional: a integração precisou de união do harness e oráculos de composição.

## Conflitos e união dos contratos

| Arquivo | Resolução | EP-R2 preservado | FILE preservado |
| --- | --- | --- | --- |
| docs/evals/cp6/fast-test-inventory.json | União estrutural das adições, sem retirar métodos/classes | DepthVertical/LeadingDollarProduct | Todas as classes FILE/computed/context/owner |
| docs/work/index.md | Ambas as entradas e estado observado | EP-R2 integrado em main | Campanha FILE e checkpoint pós-main |

Após resolução: `git diff --check` PASS, sem marcadores pendentes. Arquivos com
merge automático também auditados: AGENTS conserva confidencialidade/pré-release;
registry conserva ambas as campanhas; reader e testes conservam wire2.3/resource/
COBOL_SOURCE_ONLY e LeadingDollarWireTests. Regex CALL
`[A-Z_$][A-Z0-9_@#$]{0,7}`; política CICS FILE independente.
Consumer FILE separado, motor geral fatorizado, query BEFORE e entry simultâneo.

## Gates novos

[Ledger: comandos, ambiente, exits e classificação de tentativas](post-ep-r2/gates.json).
Todos os gates semânticos requeridos passaram; falhas ambientais/oráculos de teste
novos estão preservados, sem transformar tentativa falha em PASS.

| Gate | Resultado / propriedade |
| --- | --- |
| Baseline mecânico antes de ajustes | PASS: 586 Java, 20 wire, zero skips |
| FAST fixo | PASS: 544 métodos obrigatórios, Python e arquitetura |
| qualification-local | PASS: execução completa nova, 589 Java + integração/performance/CLIs |
| Wire independente | PASS: 21 testes, versões/negativos/leading-$ e documento CALL+FILE2.3 |
| Composição nova | PASS: 3 métodos, 7 produtos; 1/8/32 fatores, entry em ambas as ordens, `$PROGA` e políticas distintas |
| CICS | PASS: 41 fixtures ×2, quatro produtos determinísticos; literal/computed/SYSID/BEFORE/CALL |
| C06 e Program Control | PASS: 22 frontend, pares FILE/DATASET literal/computed; malformed/duplicate/outros comandos negativos |
| Scope | PASS: 8 fixtures ×2; MR1–MR9 e SG1–SG5 core, tracing real sem lookup de recurso |
| EP-R2 | PASS: 17 verticais em main e composição, depth/entry/factorization pelos gates Java |
| Escala | PASS: 9 witnesses (candidatos/aliases/unidades ×1/8/32), métricas observadas |
| Corpus | PASS: todos os 73 inputs novamente executados, comparação semântica abaixo |

A primeira qualification falhou no setup Maven tardio por cache isolado incorreto;
a repetição integral com W2D_MAVEN_REPO explícito passou. MR1–MR9 passaram antes
da recusa ptrace do sandbox; SG foram reexecutados com tracing autorizado e passaram.
Não houve regressão semântica B/C/F. Expected antigos não foram enfraquecidos.
Gates históricos dos produtores/AIR não afetados são REUSED; W10, release/merge,
dados privados e mutantes EP não relacionados são NOT_RUN, com motivos no ledger.

## Corpus e oracle CALL atual

[Comparação por programa](post-ep-r2/corpus-reconciliation.json): 73 inputs;
71 produtos comparáveis, **133 sites CALL, 411 FILE, 85 declarations**.
CALL sites/edges completos (candidates, supports, premises, remainders,
reachability), origins/artifacts iguais ao main EP-R2. FILE declarations/sites/edges
iguais aos produtos C06 anteriores. Nenhum delta semântico inexplicado.

O main sem FILE recusa a capability nova resource.bindings@1. A tentativa bruta
foi preservada. Para o controle CALL, cópias separadas da AIR removem somente
metadata de declarações FILE/resources e essa capability; prova por input exige
igualdade de todas as operações/argumentos/effects/control, units/entries/storage,
uncertainties/origins/premises e demais capabilities. Não há referências resource
residuais. Código main intacto e codec AIR final comum aos dois controles.
O produto composto mantém a AIR integral. Reader estrito valida os outputs.
Só se normaliza a omissão histórica de analysisStatus/analysisReasons no wire1.1
para COMPLETE/[]; nenhum candidate/support/remainder é normalizado.
Assim, a referência CALL é EP-R2 atual, não o histórico anterior da campanha FILE.

C06: **35 READ DATASET preservados**; COACTVWC linhas727/776/826 permanecem
cics.file/read, spelling DATASET, computed unknown sem candidatos inventados.
52 observações DATASET de outros comandos continuam NOT_QUALIFIED. Sem DSNAME,
JCL, runtime binding ou lookup externo.

Limites mantidos: duas recusas frontend (sem AIR), seis recusas CFG estritas;
71 produtos dependency PARTIAL. Não se afirma completude/precision/recall do corpus.
São dados públicos CardDemo fixados e fixtures sintéticas, sem dados corporativos.

## Performance e evidência

[Comparação EP-R2](post-ep-r2/performance-comparison.json): sites e contadores de
estrutura retida iguais nos 17 witnesses; F5 continua INVALID_IR pelo oracle.
Contagens cumulativas de interning variam também em repetição do próprio main G7;
não indicam crescimento da estrutura retida. RV main/composição observado (ms):
G7 262.3/310.3, I 703.7/768.5, E3 229.0/261.8. Sem SLA inferido.
Composição 1/8/32 verifica crescimento aditivo e compartilhamento de query,
sem nova execução do provider por FILE. Nenhuma materialização de mundos Store.
RSS máximo do corpus: dependency composto 1188556 KiB; main 913924 KiB;
medidas incluem carga/arranque e main não executa o consumer FILE.

Logs, inputs/outputs, projeções, métricas e tentativas brutas estão no arquivo
local [metadata/hash](post-ep-r2/raw-evidence.json), em
`.harness-results/fd-post-ep-r2/evidence/raw.tar.gz`: 4987 arquivos, todos
reabertos e conferidos por tamanho/SHA256. Não inclui caches/toolchains/JAR/classes.
Manifesto e scripts de comparação preservados no mesmo diretório de evidência.
678 arquivos dos runtimes foram fixados por hash; clones materiais isolados limpos.

Fast CI do código830d41a: [run295 PASS](https://github.com/Gustavo2358/analysis-cfg/actions/runs/35241493965).
[PR #38](https://github.com/Gustavo2358/analysis-cfg/pull/38) observado OPEN,
DRAFT, MERGEABLE, UNMERGED. Revisão humana é o próximo passo; W10 não autorizado.
