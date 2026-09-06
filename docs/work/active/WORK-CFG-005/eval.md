# eval

## O que prova corretude
Oracle estruturado manual antes do builder: entry(E) → sequence(L) → normal-exit(U,E).
As relações e correlações esperadas vêm da norma fixada, nunca do builder ou DOT.

## Classes positivas
Entry/Return mínimo; múltiplas Entries/Units; inventário órfão; Return entryScope
conforme contrato real a confirmar antes dos testes.

## Casos adversariais
C1 label ausente → INVALID_IR. C2 terminador ausente não construível/INVALID_IR.
C3 Return seguido fisicamente por Sequence sem edge. C4 permutação física.
C5 órfã preservada sem predecessor artificial. C6 entries e exits distintos.
Halt/Jump/instructions fora do slice; capability desconhecida não vira sucesso.

## Propriedades/relações metamórficas
Permutar Sequences mantendo IDs/referências não muda o CFG correlacionado.
Verificar identidade dos objetos AIR, listas e imutabilidade do produto.

## Casos de regressão
Preservar testes de boundary, registry e preflight. Matar temporariamente mutante
Return → próxima Sequence física; reverter antes do GREEN final. Gate sem suíte
ou com zero testes deve falhar.

## Expectativas de escala
Índices uma vez; custo proporcional ao inventário e transições emitidas mais
ordenação determinística se necessária. Sem claim de gate performance.

## RED observado
EVAL-CFG-025/15 testes escritos em EvalCfg025Test, expected manual anterior ao
builder. Compilação falhou com exit 1 por produto/API inexistentes. Sem golden
regenerado; fixture usa initialLabel explícito. Os testes incluem também retenção
de dois operandos Return e identities homônimas entre domínios/Units.
