# spec

## Problema
A porta em memória está pronta; faltam transporte físico e CLI.

## Objetivo
Arquivo AIR → shared AirJson → Publication → BuildCfg defaults/KNOWN_SUBSET → CFG_BUILT → arquivo analysis-cfg-json v1. Contrato local definido antes de código em docs/architecture/cfg-json-v1.md.

## Domínio de entrada suportado
Snapshot DRAFT analysis-ir-json 1.0.0 / AIR 2.0.0 pinado, limitado às formas do shared air-json 1A. Writer cobre todos os nós e transições atuais do kernel.

## Classes semânticas
Sucesso CFG_BUILT com inventário COMPLETE/PARTIAL; falha física/codec; recusa do kernel; serialização; output I/O.

## Premissas
Autorização humana explícita permite DRAFT sem promoção normativa. Porta e kernel permanecem byte a byte. IDs/cobertura vêm de componentes públicos, nunca reflexão ou string runtime.

## Comportamento esperado
Dois argumentos posicionais. Exit 0/2/3/4/5/6. Bytes completos antes de temp/move, limites explícitos e determinismo.

## Comportamento diante de incerteza
PARTIAL continua PARTIAL; nenhuma afirmação de alcance, completude global ou perfil IR. AIR original mantém os fatos não duplicados no produto.

## Fora de escopo
2A, cross-repo E2E, parser AIR próprio, novas operações, reader CFG, dataflow, orquestrador, promoção DRAFT.
