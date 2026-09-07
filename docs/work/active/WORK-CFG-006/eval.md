# eval

## O que prova corretude

Expected em records/enums próprios de teste, anterior à produção. M2–M5 com conjuntos
exatos de nós/regras, alternativas e contexto; preservação de instructions e predicate.
029 será owner local, sem atribuir oráculos upstream amplos não satisfeitos.

## Casos adversariais

unknown_type e missing targets → INVALID_IR, graph absent, diagnostics preservados.
Role errado, bool literal sem pruning, órfão, 258 branches, duas Entries e namespaces.
Guarda do produto rejeita endpoints/targets/contexto inválidos e distingue braços iguais.

## Regressões e metamorfismos

025=17; 028=22. MR-BRANCH-1 permutation, 2 alpha rename com correlação por domínio,
3 display/origin presentation, 4 split preservando operações/pontos. Não apagar origem.
Mutantes A–G: braços trocados, FALSE ausente, vizinho físico, destino igual colapsado,
Halt reconvergente, pruning por literal, nested usando join externo. Exigir RED por
assertion independente, restaurar produção e guardas antes dos gates.

## Escala e limites

258 occurrences prova ausência de limite incidental, sem benchmark/performance claim.
Nenhum gate foi executado no momento da promoção; CI e gates são obrigações restantes.

## Execução RED

`mvn -B -ntp -Dmaven.repo.local=/tmp/analysis-cfg-work-cfg-006-m2 -pl :cfg-kernel
-Dtest=EvalCfg029Test test`: exit 1 por BRANCH_TRUE/BRANCH_FALSE ausentes, antes
de modificar produção. Expected manual de M2–M5 e 25 métodos já escritos.

## GREEN e mutantes

Maven normal 029: exit 0, 25 testes/zero skip. Semantic 025/028/029: exit 0,
64 métodos, 95 relatórios inválidos rejeitados. Produção restaurada antes do GREEN.
Mutantes A–G executados com -Dtest=EvalCfg029Test#<método>; cada um exit 1,
1 assertion failure, 0 errors/skip, com guarda tipada de endpoints desativada:
A diamond; B unknownBooleanRetainsPredicateDependenciesReasonTypeAndOriginByIdentity;
C physicalPermutationPreservesCorrelatedControlAndCfgIds; D sameDestination;
E terminatingHaltArm; F literalTrue; G nestedBranches. Evidência detalhada será
promovida ao history com gates finais/CI, sem inflar evals amplos.
