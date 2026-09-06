# Baseline factual dos repositórios

Observada em 05/09/2026. Este documento é contexto de integração, não contrato CFG.

## Analysis IR

O [PR #1](https://github.com/Gustavo2358/analysis-ir/pull/1),
`spec!: modelar tipos desconhecidos e domínio comum (2.0.0)`, foi
verificado no GitHub e está **mergeado** desde `2026-09-06T01:41:24Z`. Seu head era
`500766e8901e3ca8e5adc987254ab2bf646751e5`; o merge commit e head canônico de
`main` observado é `0b2fbce7046010b22b32efa8cbc3e75ccba09442`. O harness adota
esse merge commit imutável como Analysis IR **2.0.0**, com hashes de blobs no
[lock](sources.lock.json). O repositório continua sendo especificação, exemplos,
invariantes e oráculos, não uma biblioteca Java disponível.

A revisão confirmou os perfis `AIR-STRUCTURE@2`, `AIR-SCALAR-FLOW@2`,
`AIR-REGION-FLOW@2`, `AIR-LOCAL-CONTROL@2` e `AIR-INDIRECT-CONTROL@2`. As
capacidades padronizadas preservam suas versões próprias: `memory.regions@1`,
`control.local@1` e `control.indirect@1`.

### Impacto semântico revisado para o CFG

| Conceito | Delta 1.0.0 → 2.0.0 relevante ao consumer |
| --- | --- |
| `Publication` | `premises` agora materializa fatos tipados `sameDomain`, sujeitos, autoridade e `DomainProofScope`; `TypeRef` aparece transversalmente. |
| `Unit` / `Entry` | Posições de parâmetros/resultados usam `TypeRef`; assinatura parcial não apaga aridade, modo ou objetos conhecidos. |
| `Sequence` / `ProgramPoint` | Nenhuma mudança material na topologia: terminador único, ordem intrassequência e pontos antes/depois continuam normativos. |
| `jump` | Sem mudança material; transferência incondicional ao label explícito. |
| `branch` | Exige `known(bool)`: `unknown(known(bool))` preserva ambos os destinos; `unknown_type` é inválido como predicate. |
| `dispatch` | A topologia cases/default não mudou; seletor e literais exigem domínio conhecido. |
| `return` / `halt` | Continuam sem fallthrough; compatibilidade dos valores de `return` passa pelas provas de domínio aplicáveis, enquanto `halt` permanece distinto de retorno. |
| `invoke` | Controle/outcomes não mudaram; assinaturas, argumentos e resultados passam a preservar `TypeRef` e provas `sameDomain` nos sites estáticos corretos. |
| `ControlEnvelope` | Sem mudança topológica; tipo desconhecido não abre controle automaticamente nem permite perder alternatives/remainder. |
| `control.local@1` | Sem mudança de versão ou regra de frames/ports; o perfil consumidor correspondente é `AIR-LOCAL-CONTROL@2`. |
| `control.indirect@1` | Mantém universo fechado `label(S)`; a assinatura exige `known(label(S))`, e `unknown_type` não estabelece `S`. O perfil consumidor é `AIR-INDIRECT-CONTROL@2`. |

Logo, o algoritmo estrutural permanece `Publication → Unit/Entry → Sequence →
terminator → successors → CFG`. A major exige modelo/validator/evals novos, não
leader detection, fallthrough intersequence, dataflow ou semântica COBOL no core.

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

O checkpoint apenas sincroniza o contrato. Ownership físico do modelo Java,
coordenadas Maven, binding versionado das fixtures e forma física da porta continuam
decisões do discovery; nenhum perfil ou algoritmo CFG foi implementado.

Fontes e permalinks: [índice](index.md).
