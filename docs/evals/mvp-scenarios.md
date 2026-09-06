# Vetores de aceitação por milestone

Notação **conceitual**, sem parser `.air` previsto. O work item de transporte deve
materializar Publications completas e válidas com IDs, origens, entries, tipos,
coverage e contratos. As tabelas abaixo são esperados independentes do builder.
Omitir boilerplate aqui não autoriza omitir semântica no objeto de teste.

## CF1 — CFG-FIRST: Entry → Return → normal exit

Publication válida construída diretamente com `air-java`: uma Unit, uma Entry cujo
`initialLabel` aponta para `L`, e `Sequence L` sem instructions, terminada por
`Return`. Expected independente: `entry(E) → node(L) → normal exit`, preservando
PublicationId, UnitId, EntryId/entry scope, Sequence/terminator/origin e sem aresta
para qualquer outra Sequence.

Contracasos obrigatórios:

1. `initialLabel` inexistente: `AirValidator`/preflight rejeita; CFG não repara.
2. Sequence sem terminador: o modelo não a constrói ou o input é inválido; nenhum
   fallthrough é inventado.
3. Outra Sequence aparece fisicamente depois de `Return`: não há aresta implícita.
4. Permutar fisicamente as Sequences não altera as transições correlacionadas.
5. Sequence sem predecessor continua inventariada e não recebe predecessor
   artificial.
6. Controle futuro com `Halt` mantém uma saída distinta: `Return != Halt`.

Quando houver mais de uma Entry/escopo de consulta, normal exits não são fundidos.
O expected não vem do builder nem de DOT/JSON. Este cenário é EVAL-CFG-025 e não
depende de branch, jump, codec ou CLI.

## M1 — Linear + jump

`entry: op1; op2; jump tail` e `tail: return`.
Dois nós de Sequence, entry→tail por jump, tail→saída normal. op1/op2 preservados e
ordenados. Permutar a posição física de entry e tail não muda a relação.

## M2 — Diamond

```text
entry: branch unknown(known(bool), dependencies=..., remainingReads=..., reason=...)
       then yes else no
yes:   opY; jump join
no:    opN; jump join
join:  return
```

Esperado: entry→yes TRUE; entry→no FALSE; yes→join JUMP; no→join JUMP;
join→saída normal RETURN. Não existe yes→no, no→yes ou entry→join direta.
Predicado desconhecido não impede grafo estrutural com destinos fechados.

Contracaso obrigatório: substituir o predicate por uma expressão cujo `TypeRef`
seja `unknown_type(u)`. A publicação é `INVALID_IR`, pois o uso não satisfaz
`known(bool)`; o consumer não pode escolher bool por default, coerção, nome ou pelo
fato de a expressão aparecer em `branch`. A lacuna e as dependências conhecidas
continuam preservadas no diagnóstico.

## M3 — Ramo ausente e nested

Se FALSE já aponta para join, preservar entry→join FALSE sem inventar nó ELSE.
No nested, cada terminador define a sua continuação. Não escolher join pelo
“próximo label” nem reutilizar join externo em todo branch.

## M4 — Ramo que termina

Trocar `yes` por `opY; halt normal`. Não há yes→join. O branch falso ainda alcança
join. O halt é distinto da saída normal da ativação. Incluir unidade com return
seguido fisicamente de outro label: esse label não ganha predecessor implícito.

## M5 — Duas alternativas, um destino

`entry: branch unknown(known(bool)) then join else join`.
Uma relação topológica pode ser compartilhada, mas TRUE e FALSE permanecem
alternativas semânticas observáveis. Não apagar avaliação/operandos do predicate.

## Pós-MVP P1 — Chamada no ponto de reconvergência

`join` termina em invoke com `normal(after)`, `exception(E,handler)`, `halt` e
`diverge` declarados. `after` retorna; `handler` termina excepcionalmente.
Os quatro outcomes permanecem distintos. Quando não existe normal sustentado,
nenhuma continuação normal é fabricada. Target calculado permanece operando,
mesmo que seu valor não seja conhecido. O contrato da fixture não é pressuposto
universal sobre CALL COBOL real.

## M7 — Falhas e limites

Label pendente/ID duplicado/terminador faltante/predicate `unknown_type` → INVALID_IR.
Input-file ausente/JSON malformado → INPUT_ERROR do adapter.
Capability fora do subset → UNSUPPORTED_CAPABILITY ou fallback sustentado;
nenhum desses resultados equivale a grafo vazio completo.

## Infraestrutura posterior M8 — Equivalência de adapters

Construir a Publication de M2 em memória e decodificar a mesma Publication de um
arquivo. Executar mesma porta com mesmas opções; comparar transições, operações,
entries/saídas, IDs correlacionados, gaps e precisão. Rodar teste isolado do kernel
sem classes de adapter no classpath de teste. Esse é o teste antecipado da migração Maven.

CF1 delimita `CFG-FIRST`. M1–M5 e os limites estruturais pertinentes de M7
delimitam `MVP-CFG-01`. M8 prova transporte posteriormente e não é prerequisite de
nenhum desses dois marcos. P1 entra no slice de `invoke`.

Catálogo relacionado: EVAL-CFG-001 a EVAL-CFG-009, EVAL-CFG-013/014 e
EVAL-CFG-025/026; cada work item seleciona apenas os evals do seu slice.
