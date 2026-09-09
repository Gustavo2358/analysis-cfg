# CP5 — backlog e ownership

Confrontados registry/backlog atuais com propostas do discovery. Nenhum backlog
novo duplicado. [BACKLOG-CFG-020](backlog/BACKLOG-CFG-020.md) é o umbrella existente
promovido apenas para **preparação do harness** por WORK-CFG-028. Waves e follow-ups
abaixo não estão iniciados/autorizados. Dependência histórica de qualificação 011
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
| transporte/queries tardias multifase/streaming de facts | 019/020 após volume real e demanda; budgets já obrigatórios no CP5 |
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

O guard novo encontrou imports de Publication e AirJsonException em AnalysisCfg,
mas cfg-launcher/pom.xml declara apenas cfg-adapters/cfg-kernel. O inventário
arquitetural antigo exige exatamente esse DAG. Não alterar POM/Java neste harness.
Registrar para review W1 a correção declarativa do launcher e atualização focal do
inventário arquitetural existente, mediante escopo autorizado. Isso não muda H1.

check_analysis_architecture.py detecta a ausência em modo estrito; a rota de
preparação reporta CP5-F01 **NONBLOCKING_FOLLOW_UP_W1**, permitindo somente os bytes
Java/POM exatos do launcher da base CP4. Nenhum módulo novo recebe exceção e qualquer
drift do launcher a invalida. PASS dessa rota é validação do guard, sem afirmar que
o launcher já cumpre dependência direta. Não criar backlog duplicado: finding do
umbrella 020/WORK-CFG-028. A correção produtiva não foi iniciada.


Review humano reconheceu CP5-F01 como NONBLOCKING; o pós-audit não altera POM ou a
exceção limitada aos bytes CP4. [Remediações A–I](../architecture/cp5-post-audit.md)
roteiam integridade W1, replay/oracle W2–W3, completion W1–W5 e qualidade por fase.
PERFORM exige local context/return pairing próprio, além de Entry. GRBE byte ranges
e correlação de campos exigem capacidades futuras apropriadas, não substring de
TextValue ou produto cartesiano declarado concreto. Nenhum follow-up foi iniciado.
