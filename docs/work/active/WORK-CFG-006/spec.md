# spec

## Problema

O core ainda recusa Branch e não completa M2–M5.

## Objetivo

Completar o slice estrutural com Branch, sem semântica de linguagem-fonte.

## Domínio e classes semânticas

Publications validadas em memória, inventário completo, Instructions/Jump/Branch/Return/Halt.
M2 diamond; M3 vazio/nested; M4 terminante; M5 destinos iguais. Literal bool e valor
unknown(known(bool)) conservam ambos os braços. unknown_type, role incorreto e targets
pendentes falham no preflight, sem reparo. Órfãs e múltiplas Entries são inventário.

## Premissas e regra

IR_GUARANTEED: AIR §04.6/8 e §08.2; Branch tem dois LabelId explícitos e predicate
known(bool). ARCHITECTURE_GUARANTEED: uma Sequence por nó, AIR compartilhada,
AirValidator antes da projeção. EXPLICIT_CONTRACT: checkpoint estrutural não avalia
valores. Nenhuma premissa depende de corpus. Projeção direta em duas regras por
Branch × Entry; terminação por coleções finitas, custo O(N log N + T), incluindo saída.
Não exige algoritmo de joins/matching nem nova ADR: aplicação dos ADRs 0003/0008.

## Produto e incerteza

Kinds tipados BRANCH_TRUE/BRANCH_FALSE; CfgGraph valida endpoints/LabelId/contexto.
SequenceNode.source conserva Branch/predicate/dependencies/reason/origins por identidade.
Preflight conserva diagnostics integrais. Não duplicar type checker nem copiar expressão.

## Fora de escopo

Dispatch/Invoke/Raise/Opaque, controle local/indireto, transporte/CLI, dataflow,
reachability, joins sintéticos, lowerer e perfis AIR. Invariantes/ADRs no manifesto.
