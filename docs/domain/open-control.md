# Controle aberto, incerteza e limites

## Regra

ControlEnvelope conserva alternativas conhecidas e `remainder=none|ControlScope`.
Uma continuação conhecida pode coexistir com outros destinos desconhecidos. Conjunto
vazio aberto não é término; conjunto vazio fechado sem outcome compatível pode ser
inconsistência. A precisão de control não deve ser deduzida da de values/storage.

Fonte: Analysis IR §05.6 e §06.3, em [fontes](../sources/index.md).

## Não usar um sink como prova de fechamento

Um nó `UNKNOWN` terminal que deixa inacessíveis os labels interiores é insuficiente
se ControlScope admite reentrada ou transferência para eles. O produto deve conservar
escopo simbólico e todas as consultas influenciadas por ele. Não materializar por
conveniência O(N²) edges se um limite simbólico preserva significado.

Uma consulta que não consegue interpretar o escopo retorna estado aberto/indisponível,
não “não há caminho”. Preservar observações independentes exige justificativa, não
proximidade textual. Uma publicação parcialmente válida pode render um CFG parcial,
mas a fronteira que falta deve permanecer enumerável.

## Consumo de extensões

Escolher exatamente uma representação executável: semântica precisa, redução
publicada, envelope conservador ou incompatibilidade. Não executar extensão e seu
fallback duas vezes. Não ler payload ou nome de opcode desconhecido para inventar
comportamento. Uma extensão sem envelope exigido recebe diagnóstico localizado.

## Limites operacionais

Corte de profundidade contextual, número de estados ou orçamento de análise exige
`ANALYSIS_LIMIT`, escopo e perda de precisão visível. Não truncar silently nem
anunciar conformidade precisa por ter processado os primeiros N nós.
A ausência de capacidade no consumidor não reduz a cardinalidade da publicação.

## Política do MVP

Antes da implementação dos envelopes completos, rejeitar explicitamente o escopo
com controle aberto é aceitável. Esse MVP não satisfaz AIR-STRUCTURE completo.
Quando BACKLOG-CFG-010 entrar, a representação de fronteira/consulta deve ser
provada com reentrada em label interior, não apenas uma seta para o fim.
