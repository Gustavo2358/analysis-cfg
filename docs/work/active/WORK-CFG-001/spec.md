# WORK-CFG-001 — Discovery antes de Java

## Problema

O contrato IR existe como especificação; não há modelo Java/codec/CFG neste repo.
Criar classes antes de fechar ownership, porta e perfis pode duplicar o contrato,
acoplar arquivo ao core ou congelar successors sem contexto.

## Objetivo

Produzir plano executivo e decisões revisáveis para o primeiro MVP, sem Java/POM.

## Domínio de entrada suportado

Documentação IR fixada, harness local e baseline de integração. Fixtures de exemplo
são notação informativa; não um schema de transporte pronto. Nenhuma aplicação
passa a ser suportada por concluir este discovery.

## Classes semânticas

Linear, branch/diamond, saídas e invoke delimitado; reservar semântica de capability
para dispatch/cycles/local/indirect/open sem implementá-la.

## Premissas

Java 17; Maven; Publication compartilhável; mesma porta para arquivo e memória;
origem COBOL não participa do core. ADRs proposed são recomendações a avaliar.

## Comportamento esperado

Fechar ownership/coordenadas do modelo IR; fronteiras e módulos; porta e lifetime;
formato técnico de fixture; domínio exato do MVP; estratégia para gates bytecode;
plano TDD com paths concretos após autorização. Registrar alternativa rejeitada.

## Comportamento diante de incerteza

Lacuna de especificação vira finding com evidência e proposta, não default inventado.
Fonte normativa inacessível bloqueia decisão semântica correspondente. Não transformar
limite upstream em mudança da IR ou hacks dentro do CFG.

## Fora de escopo

Todo Java/POM, produto CFG, codec/parser, biblioteca/infra cloud, mudança upstream,
controle local implementado e dataflow. Sem commits/PRs remotos automáticos.

## Regras de domínio relacionadas

[Frente IR](../../../domain/ir-boundary.md), [controle](../../../domain/core-control.md),
[local](../../../domain/local-control.md), [gates](../../../engineering/gates.md).

## ADRs/invariantes relacionados

Constam no manifesto. O checkpoint termina em review humano, não em build Java.
