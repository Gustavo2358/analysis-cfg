# CP5 — backlog e ownership

Confrontados registry/backlog atuais com propostas do discovery. Nenhum backlog
novo duplicado. [BACKLOG-CFG-020](backlog/BACKLOG-CFG-020.md) é o umbrella existente
agora em **W3 IMPLEMENTED / AWAITING_HUMAN_REVIEW** por WORK-CFG-028. W1/W2 aprovadas;
W4/W5 e os follow-ups externos abaixo não estão iniciados/autorizados. Dependência histórica de qualificação 011
valia para análises gerais; CP5 tem profile próprio sobre base 027 aprovada, sem
reivindicar AIR-STRUCTURE completo.

| Proposta | Registro/owner e trigger |
| --- | --- |
| CP5 index/solver/PV/query/consumers | BACKLOG-CFG-020, cinco checkpoints no mesmo work item |
| expansão de arestas por Entries/sorting, escala geral CFG | [BACKLOG-CFG-012](backlog/BACKLOG-CFG-012.md), medir quando E dominar; CP5 não encerra qualificação ampla |
| sumários, delta de bindings, WTO, otimizações/caches incrementais | [BACKLOG-CFG-019](backlog/BACKLOG-CFG-019.md), somente quando métricas justificarem; raiz incremental já é requisito CP5 |
| Read/captura/havoc/outros literais, RD/Def-Use/causalidade, outras análises | residual de BACKLOG-CFG-020, depois do profile inicial; test-only backward não vira Liveness produto |
| memória regional/weak update, local/interprocedural | residual de 020 e [BACKLOG-CFG-013](backlog/BACKLOG-CFG-013.md)/[014](backlog/BACKLOG-CFG-014.md)/[015](backlog/BACKLOG-CFG-015.md), com contrato próprio |
| CALL/FILE/DB2/CICS/GRBE consumers | residual de 020, CP6 CALL dependency slice e posteriores, incluindo contratos prévios de effects; controle Invoke em [BACKLOG-CFG-007](backlog/BACKLOG-CFG-007.md) é distinto da interpretação dos consumers |
| transporte/queries tardias multifase/streaming de facts | 019/020 após volume real e demanda; sem caps de capacidade no CP5 (CORE-SIZE-001) |
| disjunção multi-Cell upstream | external follow-up lower→AIR quando necessário; sem criar premissa/contrato no CFG; Cell única não bloqueada |
| escopo/causalidade de gaps CONTROL | external follow-up AIR/lower antes de claim exaustivo; texto de gap não é prova |
| validação repetida codec→BuildCfg | follow-up bilateral air-json/CFG se profiling justificar; não exportar índice privado nem terceira validação |
| AIR JSON >16 MiB | BACKLOG-LOWER-017, owner principalmente air-java/air-json; impacto lower/CFG; planned/NOT STARTED |
| amplificação de heap SP/AIR | BACKLOG-LOWER-018, owners lower e air-json; planned/NOT STARTED |
| digest de artifacts e frontend headless | debts externos já no roadmap E2E; não duplicar no CFG |

Paths externos, resolvidos a partir da raiz do workspace: `cobol-lower/docs/work/backlog/BACKLOG-LOWER-017.md`
e `BACKLOG-LOWER-018.md` no mesmo diretório. Foram lidos, não editados. Baseline com
SHAs em [evidence](evidence/WORK-CFG-028/baseline.json). Engines in-memory e E2E mínimo
não qualificam grandes Publications nem iniciam esses follow-ups.

## CP5-F01 — launcher com dependência AIR transitiva no baseline

**FIXED_W1 / APPROVED no HEAD b84389b6.** O launcher declara diretamente `air-java` e
`air-json`. Sua produção Java e comportamento não mudaram. O detector estrito
check_analysis_architecture.py não contém mais a exceção byte-exact de preparação.
O DAG explícito de transporte foi atualizado e os inventários javap/jdeps verificam
os tipos AIR efetivamente usados. O challenge `transitive-air-only` remove a
dependência direta, compila por transitividade e exige RED arquitetural.
[Evidência W1](evidence/WORK-CFG-028/wave-1/validation.md).

O estado NONBLOCKING_FOLLOW_UP_W1 nos reviews/evidências anteriores é histórico e
permanece preservado. CORE-SIZE-001 mantém as dívidas de runtime abaixo.

<a id="size-cap-debts"></a>

## Dívidas de capacidade observadas sob CORE-SIZE-001

A norma nova governa o desenho; não afirma que os binários legados ou a pipeline
inteira já a implementem. Nenhuma dívida abaixo é propriedade aceita do CP5.
O inventário abaixo foi registrado na remediação de harness CORE-SIZE-001, sem
alteração produtiva naquela fase. A W1 acrescenta o índice e corrige CP5-F01;
as dívidas de capacidade abaixo continuam abertas e nenhum sibling foi alterado.

| Dívida / classificação | Evidência no snapshot | Encaminhamento |
| --- | --- | --- |
| EXTERNAL SIZE-CAP DEBT — codec AIR | air-java@ce530a7e: AirJson.Limits default 16 MiB/depth128, máximo configurável depth256 | BACKLOG-LOWER-017, tarefa upstream própria; W5 reporta dependência se persistir |
| EXTERNAL SIZE-CAP DEBT — AirValidator | mesmo SHA: ValidationOptions default nesting128/entities2.000.000/issues10.000; nesting até512 | revisão própria upstream; preservar validade/IDs/owners, sem ignorar resultado incompleto |
| Dívida local herdada do preflight | analysis-cfg@e86a57c: BuildOptions.validation e CfgBuildCoordinator propagam VALIDATION_LIMIT do upstream | adequação bilateral futura; não converter em UNSUPPORTED/INVALID_INPUT CP5 nem fabricar build válido |
| Dívida local do reader | mesmo SHA: AirJsonFileReader/AirInputLimitException espelham maximumDocumentBytes e CLI reporta IMPLEMENTATION_LIMIT | migração produtiva autorizada antes de composição CP5 sem cap interno; não apenas elevar teto |
| Dívida local do writer CFG legado | mesmo SHA: CfgJsonWriter default 64 MiB, CfgJsonBytes.maximumBytes interrompe output | manter bytes/testes legados nesta sessão; não reutilizar a política no writer CP5; correção produtiva própria |
| EXTERNAL SIZE-CAP DEBT — lower (relatada) | handoff §15 relata SP32 MiB, JSON1.500.000 nodes, admission250.000 visits/depth64 no baseline 4C | evidência histórica referenciada, não revalidada ou corrigida nesta sessão |

A propagação de VALIDATION_LIMIT é dívida de capacidade, não validação de correção
por si só. A checagem de malformed JSON, Unicode válido, IDs, owners, grafo íntegro e
AirValidator permanece obrigatória. Truncagem de linha humana do stderr legado
(400 chars) é apresentação de diagnóstico; não trunca facts/resultado semântico.
Heap amplification (BACKLOG-LOWER-018) é dívida de eficiência distinta do cap.
W1 só pode medir a fronteira index/session sobre build íntegro disponível; grandes
inputs barrados antes dessa fronteira precisam ser registrados como dívida externa.
Não desabilitar guards ou inventar premissas para fabricar PASS.


## Review W3 de 8cb55b86

- W3-PERF-01 (não bloqueante): growing candidate-set union may exhibit quadratic
  cumulative work; representation remains replaceable and must be profiled before
  broad production qualification. Também observado no novo suporte crescente;
  medições no ledger W3. Sem trocar representação/cap nesta remediação.
- W3-METRICS-01 (não bloqueante): substituir classificação de Refusal por texto
  por RefusalReason tipado; fora dos blockers F1/F2.
- W4-BINDING-01 (não autorizado): planner/AnalysisKey deve vincular result, direction
  e transfer da mesma análise. Execution.observe W3 já fornece o binding correto;
  não modificar replay nesta remediação.


## Review W3 aprovado / W4 autorizada

Review humano do HEAD 855628200fba3851493991cec869dee899e82299 aprova W3 e registra
W3-F1 = RESOLVED; W3-F2 = RESOLVED. W3-PERF-01 e W3-METRICS-01
permanecem abertos e não bloqueantes. W4-BINDING-01 está autorizado para W4.
Este registro sucede o estado histórico acima, sem reescrever o review anterior.
W4 AUTHORIZED / STARTED; W5 NOT_STARTED / NOT_AUTHORIZED.
