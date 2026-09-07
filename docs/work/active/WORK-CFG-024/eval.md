# eval

## O que prova corretude

EVAL-CFG-030: matriz de admissão pela porta, grafo Return manual de três nós e duas transições,
coverage/evidence por identidade e ausência de produto nas recusas. EVAL-CFG-025/028/029 preservam
Entry/Return/Jump/Branch TRUE/FALSE, same-target, multiple entries/activationEntry, Halt, self-loop e ordem.

## Casos adversariais

PARTIAL em um ou ambos escopos; COMPLETE nos dois modos; UNAVAILABLE nunca vira vazio completo;
PARTIAL sem razão e targets inválidos; body ausente; terminador órfão não suportado; capability sem
intérprete e registrada sem semântica; validação incompleta e limitada; texto/origem de gap permutados.
Mutantes focalizados: default STRICT, STRICT admite PARTIAL, KNOWN_SUBSET recusa PARTIAL,
remover guarda de terminador/body e mascarar coverage. Expected não deriva do builder.

## Evidência de execução

RED executado antes de código: mvn -B -ntp -Dmaven.repo.local=/tmp/cfg-0a-m2
-pl :cfg-kernel -Dtest=EvalCfg030Test test (log /tmp/cfg-0a-red.log), exit 1:
1 teste, 1 assertion failure, zero errors/skips; expected CFG_BUILT, actual UNSUPPORTED_INPUT,
com duas INCOMPLETE_INVENTORY (P/U). Fixture passou pelo validator real.
Instalação da cópia pinada: mvn -B -ntp -Dmaven.repo.local=/tmp/cfg-0a-m2 clean install,
exit 0, 172 contract checks. Fast exit 0 (41 testes harness). GREEN ainda não executado.
