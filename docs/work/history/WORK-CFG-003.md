# WORK-CFG-003 — Fronteiras Clean e seam semântico de extensões

Status: `completed`. Data de conclusão: 06/09/2026. Autorização executada:
`implementation`, exclusivamente para BACKLOG-CFG-003. Base da `main`:
`b468fb48e6f3e9346776c7b8bd65616838902069`. Branch:
`feat/cfg-build-port-extension-seam`.

## Pré-condições e upstreams

O PR #3 foi confirmado como `MERGED` no GitHub, com merge commit igual à base
acima. Antes da promoção, WORK-CFG-002/BACKLOG-CFG-002 estavam `completed`, o
registry não possuía item ativo e BACKLOG-CFG-003 estava `planned` sem work item.

Os `main` remotos permaneciam nos pins do lock: Analysis IR 2.0.0 em
`122ce54e1b9ef9b00646f93ece409ca8b63bc933` e `air-java` em
`6a4091e5394fc22b3d2ada9abbdb530eb3572a58`. Nenhum pin ou upstream foi alterado.
O checkout exato de `air-java` foi instalado em repositório Maven novo e isolado;
seus 172 checks passaram antes da validação do consumer.

## Contrato físico entregue

No package `io.github.gustavo2358.analysis.cfg.application`, a porta compilada é:

```text
CfgBuildResult BuildCfg.build(
    io.github.gustavo2358.air.model.Publication,
    BuildOptions
)
```

`CfgBuildCoordinator` implementa a porta. `BuildOptions` contém somente a
`ValidationOptions` realmente aplicada no preflight. `CfgBuildResult` preserva
PublicationId, versão AIR, options, `ValidationResult` integral e capabilities sem
suporte; distingue `READY_FOR_CFG_PROJECTION`, `INVALID_IR`,
`UNSUPPORTED_CAPABILITY`, `VALIDATION_LIMIT` e `INCOMPLETE_VALIDATION`.
`READY_FOR_CFG_PROJECTION` é uma boundary sem campo de grafo e sem booleano de
sucesso.

## Seam de extensão

`SemanticInterpreter` identifica uma implementação pela
`Capabilities.Capability` compartilhada, incluindo versão.
`SemanticInterpreterRegistry` é composto explicitamente, copia seu estado, ordena
identidades de forma determinística e rejeita dois intérpretes para o mesmo par.
Versões distintas não competem por ordem de inserção. Capability requerida sem
registro produz `UNSUPPORTED_CAPABILITY` explícito.

A capability `test.synthetic-control@7` existe somente nos testes. Com seu
intérprete registrado, o mesmo coordinator reconhece o suporte do consumer sem
alteração; o `INCOMPLETE_VALIDATION` emitido pelo `AirValidator` para a extensão
desconhecida continua preservado. O seam não executa semântica, não apaga diagnostics
e não afirma que capability AIR real de controle foi implementada.

## TDD, testes e falsificações

O RED inicial foi observado com `mvn -B -ntp -pl :cfg-kernel test`, exit 1 e erros
de compilação para os contratos ainda ausentes. Após a implementação, quatro suites
executaram 18 testes sem falha ou skip: contrato físico, coordinator/result,
registry sintético e regressão do preflight.

Falsificações realmente executadas:

- import temporário de `java.nio.file.Path`: gate rejeitou, exit 1;
- classe AIR `Publication` local temporária: inventário rejeitou, exit 1;
- imports temporários de reflection e `ServiceLoader`: gate rejeitou, exit 1;
- mutação `unsupported → READY_FOR_CFG_PROJECTION`: teste focal falhou, exit 1;
- suite inexistente `NoSuchBoundaryTest`: Surefire falhou, exit 1;
- duplicata/conflito, remoção do intérprete e permutação de registro: regressões
  automatizadas verdes no comportamento esperado;
- DTO local na porta e dependências frontend/JSON/CLI: fixtures internas do gate
  rejeitadas pelo descritor/inventário/dependências.

Todas as mutações temporárias foram revertidas antes dos gates verdes.

## Validação e claims

Usando `/tmp/analysis-cfg-work-cfg-003-m2`, preparado a partir do upstream pinado:

- `air-java mvn -B -ntp clean install`: 172 checks, exit 0;
- `check-docs.sh`: PASS, exit 0;
- `check-harness.sh`: 41/41, exit 0;
- `check-fast.sh`: PASS, exit 0;
- `check-architecture.sh`: PASS, 18 testes, oito classfiles exatos, exit 0;
- `mvn -B -ntp clean test`: 18/18, exit 0;
- `mvn -B -ntp clean verify`: 18/18, exit 0;
- `git diff --check`: exit 0;
- `semantic`, `performance` e `integration`: `UNAVAILABLE`, exit 3;
- `check-full.sh`: encerrou honestamente no primeiro gate ausente (`semantic`),
  exit 3, após `fast` e `architecture` passarem.

O gate arquitetural mantém compile dependency externa somente em `air-java`, Java
21 sem preview, inventários exatos, bytecode/descriptors da porta e preflight,
imports produtivos e `jdeps`. EVAL-CFG-009 está implementado somente para o seam;
EVAL-CFG-007 e EVAL-CFG-024 permanecem verdes. EVAL-CFG-025 continua `planned` e
nenhum perfil AIR foi declarado implementado.

## Escopo negativo e handoff

Nenhum `Return`, Entry, Sequence, terminador, node, edge, successor, predecessor,
branch, jump, halt, invoke, controle local/indireto, JSON, arquivo, CLI, COBOL,
lowerer ou algoritmo CFG foi implementado. O gate semântico global permanece
indisponível.

**BACKLOG-CFG-003 concluído. BACKLOG-CFG-005 NÃO iniciado. CFG-FIRST NÃO
implementado.** O trabalho para em PR aberto, sem merge nem auto-merge, aguardando
review humano.
