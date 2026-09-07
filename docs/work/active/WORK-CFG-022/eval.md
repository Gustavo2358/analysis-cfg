# WORK-CFG-022 — oracle e avaliação

## O que prova corretude

Expected independente, antes de produção.

M1: nós Entry(E), Sequence(entry), Sequence(tail), NormalExit(U,E).
Relações manuais: (E,entry,ENTRY,E), (entry,tail,JUMP,E),
(tail,NormalExit(U,E),RETURN,E). Ocorrências op1,op2 nessa ordem,
operandos/headers/origins/precision/gaps idênticos aos fatos AIR de entrada.
Contracaso Halt: (entry,HaltExit(halt OperationId),HALT,E), nunca RETURN nem
destino na próxima Sequence; normal exits continuam inventário por Entry.
Expected usa records/enums próprios de teste, sem builder/DOT/toString/serialização.

## Casos adversariais

Instructions zero/uma/muitas/todos cinco tipos; Jump forward/backward/self-loop,
alvo diferente do próximo físico e da ordenação de IDs; Halt normal/abnormal,
Return↔Halt, múltiplas Entries/Units e duas ocorrências Halt com mesma origem.
Órfãs com Jump/Halt/instructions permanecem; unsupported em órfãs recusa o build.
Negativas: target pendente, capability ausente/versão desconhecida/controle externo,
sem reparar a AIR ou apagar diagnósticos. Capability identity não implementa extensão.

## Metamorfismos e regressão

Permutar Sequences/Entries/Units; alpha rename explícito por domínio; split com
Jump conserva ordem nos pontos originais; display/origin presentation muda somente
metadados preservados. EVAL-CFG-025 integral, navegação O(1) e imutabilidade.

## Mutantes e limites

A Jump→próxima física; B Return→próxima; C Halt→NormalExit;
D Halt→próxima; E reordenar/perder instructions. Registrar RED real e restaurar.
Sem benchmark; cardinalidade não vira heurística. Sem perfis AIR, integração ou
performance promovidos. Gates mantêm suítes/métodos exatos e zero skip/missing.
