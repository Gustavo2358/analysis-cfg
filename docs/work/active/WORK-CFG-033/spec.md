# CP6 W1D — especificação

## Problema

No baseline `c39a92f930b1c693857a0b30a1f5155f3f81520c`, a AIR real produzida por W1A/W1C preserva Invoke e o MOVE fitted, mas CFG recusa o terminador, o perfil de valores recusa o efeito e falta um produto de dependências.

## Objetivo

Executar CALL literal e dynamic scalar text de COBOL real até dependency site facts. O fixture X(8) deve fornecer raw `PROGA   ` BEFORE o Invoke real, candidato `PROGA` e suporte do Assign/MOVE original. Literal deve executar somente reachability, com zero PossibleValues. Dynamic sem MOVE permanece aberto e sem candidato inventado.

[Contrato do produto](../../../architecture/analysis-dependency-result-v1.md) e [política de nome](../../../domain/cp6-call-name-policy.md) definem interfaces, semântica, wire, nulabilidade, ordering, complexidade e limites. `INTERNAL-CONTRACT-DEV-001`: primeira versão interna explícita, sem framework de compatibilidade.

IR_GUARANTEED: Invoke é terminador; outcomes possuem o controle; expressão é pura. EXPLICIT_CONTRACT: um Normal local e remainder NoControl/AllControl; sem callee ou fallthrough. ARCHITECTURE_GUARANTEED: o mesmo transfer de effects participa do solver e replay; may-write abre Cells sem matar valores ou produtores.

## Fora de escopo

W2, análise interprocedural, lookup de callee/corpus, IF COBOL real multi-candidate, USING/RETURNING, finite exception outcomes, perOutcome e mustOverwrite, formas de armazenamento fora do perfil, regras IBM completas e mudanças normativas AIR. Solver, lattice, DefaultValuePlan, resultado W5 e golden bytes anteriores ficam preservados. A aprovação desta implementação pertence ao review humano.
