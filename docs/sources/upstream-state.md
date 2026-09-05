# Baseline factual dos repositórios

Observada em 05/09/2026. Este documento é contexto de integração, não contrato CFG.

## Analysis IR

Commit `fe38d30db9e95ce85d039600d543644c7f563014`, versão semântica 1.0.0. Repositório de especificação, exemplos,
invariantes e oráculos; não uma biblioteca Java já disponível. Define Sequence,
terminadores, local frames, controle indireto e precisão/envelopes.
Esta preparação verificou por conector o commit e os capítulos de controle/perfis;
a revisão anterior desta conversa também leu modelo, operações, consumidores,
extensões, exemplos e oráculos. Não foi executada implementação IR/CFG upstream.

## Proleap POC — main e PR são baselines distintas

Main consultado: `3972c669fe187004769becbd6bff00af90d6c9dd`. Java release 17 no POM; o harness usa contexto roteado,
work items, política semântica, testes adversariais e gates estáveis.
PR #27, head `9c53948089a6040d1666a5df812ba993ea3ca50b`, estava aberto. Não interpretar título/descrição inicial
“somente documentação” como estado atual: o `state.md` registra CP1–CP7 executados,
integração de publicação no composition root e `SemanticProductJsonWriter` no CP7.
Os resultados de testes lá citados são **relatados pelo projeto**, não reexecutados
na elaboração deste ZIP.

O produto publica coleção imutável de facts DATA/MOVE/CALL/IF e ObservedStatement,
identidades, provenance, binding nominal, containment e readiness. IF preserva
estrutura/referências; ConditionSemantics ainda falta. Literal kind pode permanecer
UNKNOWN por falta do fato tipado upstream. CALL variável mantém target de runtime
UNKNOWN; argumentos/RETURNING/exception flow têm lacunas. Sob estruturas como
PERFORM ainda não modeladas, containment pode ser UNKNOWN.

O JSON emitido é `cobol-semantic-product`, **não Analysis IR**. CobolLower e CFG
continuam trabalho futuro. A fixture relatada tem observações parciais que bloqueiam
claims agregadas; não inferir que o frontend já produz controle inteiro exato.

## Consequência para o novo projeto

Construir consumidor com fixtures IR próprias é correto e independente. Premissas
de fixtures sintéticas fechadas devem ser explícitas; elas não provam cobertura
COBOL real nem eliminam trabalho do lowerer. Integração posterior confrontará a
matriz de capabilities; gaps não serão “corrigidos” pelo CFG.
O suporte a PERFORM/THRU na IR é condicionado à disciplina de frames/ports declarada;
não é certificado universal para todos os casos e dialetos COBOL.

Fontes e permalinks: [índice](index.md).
