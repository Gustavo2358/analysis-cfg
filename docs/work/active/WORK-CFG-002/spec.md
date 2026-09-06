# Especificação — WORK-CFG-002

## Problema

O repositório ainda é somente documental e não prova fisicamente que o consumer
compila contra o modelo compartilhado e o validator do `air-java`. A dependência é
um SNAPSHOT sem release e precisa ser resolvida por um source SHA imutável.

## Objetivo

Criar o build mínimo Java 21/Maven no qual a boundary recebe exatamente
`io.github.gustavo2358.air.model.Publication` e o preflight devolve sem alteração o
`ValidationResult` produzido por `io.github.gustavo2358.air.validation.AirValidator`.

## Domínio de entrada suportado

Uma `Publication` já materializada em memória pelo modelo `air-java` fixado. Este
checkpoint só classifica a validação estrutural upstream; não interpreta controle.

## Classes semânticas

- `STRUCTURALLY_VALID`: a boundary estrutural aceita a publicação para preflight
  futuro, sem afirmar conformidade de CFG.
- `INVALID_IR`: rejeição permanece explícita, sem reparo, lookup ou reinterpretação.
- `INCOMPLETE_VALIDATION`: capability não suportada ou limite de validação permanece
  explícito.
- `SEMANTIC_OBLIGATION`: diagnóstico upstream preservado; não vira fato comprovado.

## Premissas

`analysis-ir` é a autoridade normativa; `air-java` é o modelo Java compartilhado.
A versão Maven `0.1.0-SNAPSHOT` é distinta da versão semântica AIR `2.0.0`.

## Comportamento esperado

A chamada ao preflight aceita uma `Publication` real e retorna o próprio resultado
tipado do `AirValidator`, incluindo status, issues e estatísticas. Nenhum DTO AIR ou
validator local participa da cadeia.

## Comportamento diante de incerteza

Incompletude, capability desconhecida, limite e obrigação semântica continuam nos
diagnósticos originais. A foundation não completa fatos pelo frontend ou transporte.

## Fora de escopo

Nós/arestas CFG, `BuildCfg`, lowering de controle, matching de retorno, dataflow,
JSON, arquivo, CLI, DOT, filesystem, frontend COBOL e publicação de artefato upstream.

## Regras de domínio relacionadas

Preservar integralmente a `Publication`; não derivar successor, valor, target ou
efeito neste checkpoint.

## ADRs/invariantes relacionados

ADR-0001, ADR-0002, ADR-0007, ADR-0008 e ADR-0009; INV-CFG-001 a
INV-CFG-005, INV-CFG-023, INV-CFG-025 a INV-CFG-028.
