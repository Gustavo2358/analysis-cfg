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
exit 0, 172 contract checks. Fast exit 0 (41 testes harness). GREEN pelo mesmo comando: exit 0, 20 testes/zero skips.
Gates fast, architecture (102 testes/22 classfiles) e semantic (84 testes/125 fixtures negativas) passaram.
Maven runtime Temurin 25.0.4, bytecode release 21 sem preview; CI Temurin 21 ainda pendente.

## Challenge executado

Cada mutante foi aplicado isoladamente; comando:
`mvn -B -ntp -Dmaven.repo.local=/tmp/cfg-0a-m2 -pl :cfg-kernel -Dtest=EvalCfg030Test#MÉTODO test`.
Todos retornaram exit 1 com 1 assertion failure e zero errors/skips; produção restaurada byte a byte em finally.

| Mutação | Método que a rejeitou |
| --- | --- |
| BuildOptions de um argumento escolhe STRICT | allPublicDefaultsSelectKnownSubsetAndNullPolicyIsRejected |
| STRICT passa a aceitar PARTIAL | strictRejectsPartialPublicationWithTypedSubject |
| KNOWN_SUBSET deixa de aceitar PARTIAL | defaultProjectsBothPartialInventoriesWithExactReturnOracle |
| CfgGraph substitui coverage global por COMPLETE | partialCoverageItemsPremisesAndDimensionalEvidenceRemainOriginal |
| Coordinator publica grafo vazio e remove issues na recusa | unsupportedOrphanTerminatorIsNeverSilentlyOmitted |
| Mesma aceitação genérica indevida diante de body ausente | unavailableBodyStillBlocksKnownControlProjection |

Semantic passou novamente após restauração. Logs locais: /tmp/cfg-0a-mutant-*.log,
/tmp/cfg-0a-challenge.json, /tmp/cfg-0a-semantic.log. Relato durável não depende desses arquivos temporários.
Performance/integration/full permanecem UNAVAILABLE; não executados nem contados como PASS.
