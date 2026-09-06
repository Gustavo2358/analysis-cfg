# WORK-CFG-001 — Discovery antes de Java

## Problema

O contrato Analysis IR 2.0.0 existe como especificação; não há modelo
Java/codec/CFG neste repo.
Criar classes antes de fechar ownership, porta e perfis pode duplicar o contrato,
acoplar arquivo ao core ou congelar successors sem contexto.

## Objetivo

Sincronizar primeiro a baseline normativa, harness, backlog e evals com a Analysis
IR 2.0.0; depois produzir as decisões revisáveis ainda necessárias ao primeiro MVP,
sempre sem Java/POM neste work item.

## Domínio de entrada suportado

Analysis IR 2.0.0 no merge commit
`0b2fbce7046010b22b32efa8cbc3e75ccba09442`, harness local e baseline de
integração. Fixtures de exemplo
são notação informativa; não um schema de transporte pronto. Nenhuma aplicação
passa a ser suportada por concluir este discovery.

## Classes semânticas

Primeiro MVP: linear, `jump`, branch/diamond, `return` e `halt`. `invoke`,
`dispatch`, ciclos, controle aberto, `control.local@1` e `control.indirect@1`
permanecem em slices posteriores.

## Premissas

Java 17; Maven; Publication V2 compartilhável com `TypeRef`, `Premise`,
`sameDomain` e `DomainProofScope`; mesma porta para arquivo e memória; origem COBOL
não participa do core. ADRs proposed são recomendações a avaliar.

## Conclusão da revisão 1.0.0 → 2.0.0

A major torna normativos `known(Type)` versus `unknown_type(UncertaintyId)` e fatos
relacionais de domínio. Ela muda modelo, validade e oráculos, mas não o algoritmo
topológico do CFG. Permanecem verdadeiras as propriedades revisadas na fonte:

- cada Sequence tem exatamente um terminador;
- não há fallthrough implícito nem execução definida por ordem física;
- `jump` é incondicional e `branch` mantém dois destinos;
- `unknown(known(bool))` preserva TRUE/FALSE; `unknown_type` não satisfaz bool;
- `return` e `halt` não têm fallthrough;
- controle local continua contextual;
- controle indireto preciso continua limitado pelo universo fechado `label(S)`.

`sameDomain` não é igualdade de valores. `DomainProofScope` é escopo estático de
prova e não depende do CFG ou de uma ativação dinâmica. Nenhuma mudança justifica
COBOL no consumer, leader detection, dataflow ou filesystem no core.

## Comportamento esperado

Pin/hashes/perfis/evals V2 ficam sincronizados neste checkpoint. Ainda é necessário
fechar ownership/coordenadas do modelo IR; fronteiras e módulos; porta e lifetime;
formato técnico de fixture; domínio exato do MVP; estratégia para gates bytecode;
plano TDD com paths concretos após autorização. Registrar alternativa rejeitada.

## Comportamento diante de incerteza

Lacuna de especificação vira finding com evidência e proposta, não default inventado.
Fonte normativa inacessível bloqueia decisão semântica correspondente. Não transformar
limite upstream em mudança da IR ou hacks dentro do CFG.

## Fora de escopo

Todo Java/POM, produto CFG, codec/parser, biblioteca/infra cloud, mudança no
repositório `analysis-ir`, controle local implementado e dataflow. O PR deste
checkpoint é permitido, mas permanece aberto para human review e não será mergeado.

## Regras de domínio relacionadas

[Frente IR](../../../domain/ir-boundary.md), [controle](../../../domain/core-control.md),
[local](../../../domain/local-control.md), [gates](../../../engineering/gates.md).

## ADRs/invariantes relacionados

Constam no manifesto. O checkpoint termina em review humano, não em build Java.
