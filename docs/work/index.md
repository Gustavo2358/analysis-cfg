# Trabalho

## Item ativo

[WORK-CFG-005](active/WORK-CFG-005/work-item.json) — `active`, implementação
CFG-FIRST explicitamente autorizada. [Estado](active/WORK-CFG-005/state.md).

## Último item concluído

[WORK-CFG-003](history/WORK-CFG-003.md) — `completed`, porta
`BuildCfg(Publication, BuildOptions) → CfgBuildResult`, seam explícito por
capability/version e gate arquitetural ampliado; nenhum algoritmo CFG.

## Próximos candidatos

BACKLOG-CFG-004, BACKLOG-CFG-022 e BACKLOG-CFG-006 permanecem `planned`, sem
work item e sem autorização de execução neste checkpoint.

## Roteamento

[Backlog](backlog.md), [registry](registry.json),
[protocolo](../engineering/work-item-protocol.md), [templates](../templates/README.md).

Ao encerrar um trabalho, retirar active, registrar resumo quando útil em history,
atualizar registry/index/backlog e promover conhecimento durável. Não consultar
história por padrão. Não assumir merge apenas por indicação textual local.
