# state

## Onde estamos
Oracle estruturado de EVAL-CFG-025 escrito antes de qualquer tipo/builder CFG.
RED observado: mvn -B -ntp -Dmaven.repo.local=/tmp/analysis-cfg-work-cfg-005-m2
-pl :cfg-kernel -Dtest=EvalCfg025Test test, exit 1, símbolos CFG ausentes
(CfgGraph/CfgNode/CfgTransition e graph/CFG_BUILT). Não é somente RED planejado.

## Verde conhecido
Lifecycle anterior e SHAs confirmados no plan. Fast PASS, exit 0 (41 testes).
air-java clean install pinado no Maven isolado: 172 checks, exit 0.

## Restante
Implementar contratos/projeção mínimos; GREEN, mutante, metamorfismo, challenge;
evoluir gates/CI, docs, encerramento condicional e PR sem merge.

## Descobertas que afetam o plano
AIR §04.8 não tem return.entryScope. Retorno segue Entry da ativação corrente;
transições CFG preservam essa condição, inclusive para Return compartilhado.
A primeira tentativa Maven falhou por download bloqueado, sem contar como RED
semântico; a repetição com dependências disponíveis produziu o RED acima.
