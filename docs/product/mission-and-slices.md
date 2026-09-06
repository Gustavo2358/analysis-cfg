# Missão e entrega por slices

## Objetivo

Entregar uma aplicação Java 21/Maven independente que recebe a `Publication`
imutável do `air-java` e produz um CFG utilizável por análises futuras. Não criar
analisador COBOL, lowerer, modelo AIR paralelo, dataflow ou visualizador como núcleo.
Arquivo e memória convergem para a mesma porta sem serialização obrigatória.

## CFG-FIRST: Entry → Return → normal exit

Primeira prova executável deliberadamente mínima:

```text
Publication
└── Unit
    └── Entry
        └── initialLabel → Sequence
                           └── Return
```

Resultado: `entry(E) → node(sequence L) → normal exit`. A saída preserva
`PublicationId`, `UnitId` e o `EntryId`/entry scope da análise. Entradas não são
fundidas num exit global. `Return` encerra a ativação da Unit e não tem fallthrough,
mesmo quando outra Sequence aparece depois na coleção.

O caso é construído diretamente em memória com `air-java`. `AirValidator` executa
antes do preflight do slice; referência pendente é rejeitada, terminador ausente não
é reparado e conteúdo sem predecessor permanece inventariado. Sem jump, halt,
branch, IF, múltiplas instructions, JSON ou CLI neste marco.

## MVP-CFG-01: subset estrutural útil

Marco posterior: operações lineares, `jump`, `branch`, saídas `return`/`halt` e
IF/ELSE estrutural. `unknown(known(bool))` como predicado puro conserva os dois
destinos; `unknown_type` não satisfaz a assinatura booleana. IFs aninhados são
combinações das mesmas primitives, sem limite artificial de cardinalidade.

O diamond reconverge somente onde os terminadores dizem; ramo que termina não ganha
join artificial. A permutação física de sequences não altera transições. `Return`
e `Halt` continuam saídas semanticamente distintas.

CLI, adapter AIR JSON e a equivalência arquivo/memória continuam importantes, mas
são milestones de infraestrutura posteriores e não condição para provar
`Publication → CFG`.

## Slices seguintes

Depois do MVP, `invoke` entra com outcomes materializados (normal, excepcional,
halt, diverge e restante aberto quando sustentado). `raise(tag, values)` é saída
excepcional sem fallthrough. O slice não resolve programa chamado nem afirma
ausência de efeitos.

Depois vêm `dispatch`, ciclos e múltiplas entradas; envelopes abertos; controle
local com frames/ports/retornos; controle indireto limitado; integração Maven em
memória. Cada etapa tem evidência independente de COBOL.

Motivadores upstream podem baixar para primitives AIR: EVALUATE para dispatch ou
branches, GO TO/NEXT SENTENCE para jump e PERFORM/THRU para controle local quando
seu contrato permitir. Esses nomes pertencem ao frontend/lowerer e a testes
bilaterais, não a classes nem regras do CFG. O primeiro motivador E2E é GOBACK no
frontend, mas a expectativa do consumer começa somente em `AIR Return → normal
exit`.

Antes de anunciar qualquer marco, integridade inválida e capabilities fora do slice
têm resposta explícita. Rejeitar escopo não suportado é aceitável; ignorá-lo não.
`AirValidator` prova validade estrutural implementada do input, enquanto evals CFG
provam a interpretação do consumer.

**CFG-FIRST e MVP-CFG-01 não são perfis normativos AIR.** Não declarar
`AIR-STRUCTURE@2/PRECISE_FOR_PROFILE` até cumprir todas as obrigações estruturais
daquele perfil.

Dataflow, reaching definitions, possible values, storage analysis, targets
dinâmicos, dominância e coalescing ficam adiados. Preservar insumos não significa
implementá-los.

Fontes: [contrato e estado upstream](../sources/index.md); execução:
[backlog](../work/backlog.md).
