# WORK-CFG-032 — spec

## Problema

O baseline CP5 resolve valores de writes, mas não entrega dependency de CALL real.
O discovery deve separar perdas de frontend/lower/transporte/controle/efeitos do
consumer, usando exclusivamente os cinco SHAs congelados pelo usuário.

## Objetivo

Discovery factual SP → AIR → CFG → PossibleValues → dependency fact. Distinguir contrato normativo, modelo, validator e codec; analisar efeitos e provenance por candidato.
Entrega arquitetural: [relatório](../../../architecture/cp6-call-dependency-discovery.md),
com trace, gaps, contrato proposto, soundness, waves e futuros oráculos vinculados.

## Fora de escopo

Implementação, testes futuros, novos contratos normativos, solver changes, siblings, merge e auto-merge.
