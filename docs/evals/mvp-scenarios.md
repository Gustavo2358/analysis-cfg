# Vetores de aceitação do MVP

Notação **conceitual**, sem parser `.air` previsto. O work item de transporte deve
materializar Publications completas e válidas com IDs, origens, entries, tipos,
coverage e contratos. As tabelas abaixo são esperados independentes do builder.
Omitir boilerplate aqui não autoriza omitir semântica no arquivo de teste.

## M1 — Sequência linear

`entry: op1; op2; jump tail` e `tail: return`.
Dois nós de Sequence, entry→tail por jump, tail→saída normal. op1/op2 preservados e
ordenados. Permutar a posição física de entry e tail não muda a relação.

## M2 — Diamond

```text
entry: branch pure-unknown then yes else no
yes:   opY; jump join
no:    opN; jump join
join:  return
```

Esperado: entry→yes TRUE; entry→no FALSE; yes→join JUMP; no→join JUMP;
join→saída normal RETURN. Não existe yes→no, no→yes ou entry→join direta.
Predicado desconhecido não impede grafo estrutural com destinos fechados.

## M3 — Ramo ausente e nested

Se FALSE já aponta para join, preservar entry→join FALSE sem inventar nó ELSE.
No nested, cada terminador define a sua continuação. Não escolher join pelo
“próximo label” nem reutilizar join externo em todo branch.

## M4 — Ramo que termina

Trocar `yes` por `opY; halt normal`. Não há yes→join. O branch falso ainda alcança
join. O halt é distinto da saída normal da ativação. Include unidade com return
seguido fisicamente de outro label: esse label não ganha predecessor implícito.

## M5 — Duas alternativas, um destino

`entry: branch pure-unknown then join else join`.
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

Label pendente/ID duplicado/terminador faltante → INVALID_IR.
Input-file ausente/JSON malformado → INPUT_ERROR do adapter.
Capability fora do subset → UNSUPPORTED_CAPABILITY ou fallback sustentado;
nenhum desses resultados equivale a grafo vazio completo.

## M8 — Equivalência de adapters

Construir a Publication de M2 em memória e decodificar a mesma Publication de um
arquivo. Executar mesma porta com mesmas opções; comparar transições, operações,
entries/saídas, IDs correlacionados, gaps e precisão. Rodar teste isolado do kernel
sem classes de adapter no classpath de teste. Esse é o teste antecipado da migração Maven.

M1–M5, M7 e M8 delimitam o MVP-CFG-01. P1 entra no slice seguinte de `invoke`.
Catálogo relacionado: EVAL-CFG-001 a EVAL-CFG-009 e EVAL-CFG-013/014; cada work item seleciona apenas os evals do seu slice.
