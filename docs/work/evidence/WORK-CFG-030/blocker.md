# Novo bloqueio da sincronização — review humano necessário

O PR13 resolveu o bloqueio anterior de classificação RESOURCE_LIMIT. O rebuild limpo do CFG passou270 testes, sem falhas, erros ou skips. A sincronização avançou até os merges não semânticos do AIR e do lower.

O full do CFG com os novos pins falhou no gate de arquitetura: `W5 compiled inventory drift` (exit1). Esse gate compara fontes/classes, dependências e assinaturas compiladas com o inventário aprovado. Uma conferência isolada, somente de leitura, dos mesmos classfiles passou. A causa exata da diferença entre o fluxo completo e a checagem isolada ainda não foi determinada; nenhuma mudança semântica foi demonstrada por esse resultado.

A condição de parada solicitada pelo usuário é explícita: section49, `full gate failure`. O full permanece FAIL. Inventário esperado, Java produtivo/de testes e POMs permanecem intactos. Não houve novo full, atualização automática do esperado ou correção silenciosa após a falha.

## Estado preservado

- analysis-cfg main/source:15bd3afe1affdcb5ec49956960f884bde8c89498 (PR13). Branch chore/cp6-baseline-sync com fechamento CP5 e pins locais, não commitados; WORK-CFG-030 blocked.
- air-java main:3bafe3978f0f392e842038ad5628e85dfd91d00d, PR8 MERGED, CI exata PASS.
- cobol-lower main:18016f16b4f63149eb1bb4ca13db7e12593d8909, PR10 MERGED. HEAD final36b0020cd26e14f42fa8334eb37f936dcd5986bd, CI34519741431 PASS; certificado-pai cd55cbdf também PASS34516870209. Tree0139edc52e34c703b643ff484a33e70871ef091d corresponde ao HEAD validado.
- analysis-ir51b4d9a8ae0364232bd97103cd73a77e1a34996c e proleap-poc8722945cc4cd2052c6091533f6ee6989278aa2f8 permanecem no snapshot congelado.
- CP5 aprovado/mergeado; closeout documental do WORK-CFG-028 está preparado localmente e ainda não publicado no main.
- E2E final não alcançado pelo full sincronizado; PR de baseline não criado; CP6_BASELINE NOT_FROZEN. CP6 NOT_STARTED / NOT_AUTHORIZED.

[Receipt detalhado](full-blocker.json), [log original comprimido](logs/cfg-full-synchronized-FAILED.log.gz). O estado compilado e log legível estão preservados em .harness-results/WORK-CFG-030/blocked-full-w5-inventory, fora do versionamento de builds. Provas anteriores continuam em full-candidate.json e candidate-semantic-assertions.json, com seus pins originais explícitos.
