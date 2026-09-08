# WORK-CFG-026 — RED anterior à produção

Baseline b614712fda55fef12639cbe18fd90793faa1fb3b; branch feat/air-json-cfg-cli.
Contrato local, fixture upstream e golden manual escritos antes do writer.
`mvn -B -ntp -Dmaven.repo.local=/tmp/cfg-2b/m2 -pl :cfg-adapters -am test`
executado em 2026-09-07: exit 1, testCompile falha por AirJsonFileReader,
CfgJsonWriter, CfgJsonException e AirInputLimitException ausentes. Kernel: 102 testes,
zero falhas/errors/skips. Não é RED semântico do kernel: é falta da API de transporte.
Primeira tentativa esbarrou em plugin Maven sem rede e não conta como RED; repetição
com dependências resolvidas produziu a falha por símbolos acima.
Fixture AIR obtida do git blob pinado, sem encode; oracle CFG escrito manualmente.
Upstream isolado b78f4068d8a479f48eb048b8d76fa60a0997dc4a: clean install exit 0.
Docs e git diff --check: exit 0. Demais gates ainda não GREEN após módulos novos.
Correção de links após arquivar 025 preserva as afirmações da evidência original.
