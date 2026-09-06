# BACKLOG-CFG-002 — Bootstrap Java 21/Maven e boundary air-java

**Estado:** `active` em [WORK-CFG-002](../active/WORK-CFG-002/spec.md). **Fase:** `foundation`.
**Autorização:** implementação explicitamente autorizada em 06/09/2026 somente para este checkpoint.
Dependências: BACKLOG-CFG-001.

## Problema e objetivo observável

Criar o build Java 21/Maven reproduzível do consumer e provar a boundary física com
`air-java`, sem algoritmo CFG e sem modelo AIR local.

## Escopo e estratégia

Introduzir somente a fundação de módulos/packages, dependência no
`io.github.gustavo2358:air-java:0.1.0-SNAPSHOT` correspondente ao SHA fixado,
preflight com `AirValidator` e gates arquiteturais reais. Fechar como resolver de
forma reprodutível o SNAPSHOT ainda sem release/tag. O kernel usa Java 21 sem preview
e compila contra a `Publication` compartilhada.

## Critérios de aceitação

Build não passa com zero testes; um smoke de boundary compila contra a `Publication`
do `air-java`; `AirValidator` é reutilizado; nenhuma classe/package AIR ou validator
paralelo existe neste repo; kernel não depende de Jackson, filesystem, CLI, ProLeap,
ANTLR ou COBOL Semantic Product; gate negativo detecta violações. A porta `BuildCfg`
é fechada no BACKLOG-CFG-003.

## Evals e invariantes

EVAL-CFG-001, EVAL-CFG-007 e EVAL-CFG-024. Vincular invariantes
específicos ao promover. Ver [catálogo](../../evals/catalog.md) e
[invariantes](../../architecture/invariants.md). Expected arquitetural nasce antes
da configuração que deve satisfazê-lo.

## Fronteiras e extensibilidade

`air-java` possui `Publication`, `Unit`, `Entry`, `Sequence`, `Operation`,
`Instruction`, `Terminator`, `TypeRef`, IDs, `Premise`, `DomainProofScope` e demais
tipos AIR. Este item não os implementa. JSON continua fora do modelo/kernel; novas
variantes sealed exigem versão compatível ou fallback contratual, não payload livre.

## Discovery, checkpoints e handoff

Promover somente após autorização explícita, com paths concretos, resolução da
dependência, testes RED e gates. Ao concluir o checkpoint autorizado, parar para
review; não avançar automaticamente a BACKLOG-CFG-003/005.

## Fora de escopo

Sem builder CFG, nodes/edges, adapter JSON, CLI, frontend, lowerer, dataflow ou
publicação de pacote upstream. Não alterar `air-java`, `analysis-ir` ou
`proleap-poc` como efeito lateral.

## Evidência de conclusão

Commit/review, comandos e exit codes, árvore de dependências, bytecode verificado,
contador de testes e falsificações. Até lá o item está apenas pronto para
autorização; nenhum perfil AIR recebe claim.
