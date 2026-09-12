# WORK-CFG-033 — remediação do wire e CI

Baseline `1ab16bdeae8d8af23e723d0b239ba191a695764a`, tree
`ba8416dbe4d38f725d76b0a13278e5acb5296340`, PR #18 na branch existente.

O [receipt de desenvolvimento](development-receipt.json) preserva RED e GREEN
do versionamento. Três falhas de assertions, nenhuma de compilação: Invoke era
emitido sob v1. Os dois casos antigos já passavam. O GREEN inclui os cinco testes
W1dInvokeWireTest e todo o transporte CFG (42 métodos), sem editar nenhum golden.

A [decisão v2](../../../../architecture/cfg-json-v2.md) define seleção determinística
pelo produto. A [orquestração](../../../../engineering/qualification.md) separa
FAST_CI, LOCAL_QUALIFICATION e REMOTE_MANUAL_QUALIFICATION.

A pasta original de evidências W1D fica byte-exact. Seus CFGs com Invoke rotulado
v1 documentam o defeito agora confirmado; não são reclassificados nem substituídos.
O produto analysis-dependency-result continua 1.0.0.

O receipt final é gerado pelo check-qualification.sh sobre o HEAD commitado e limpo,
antes do push, em `<work>/evidence/receipt.json`. HEAD/tree, caminho/hash desse
receipt e resultado do Fast remoto ficam no handoff externo e no PR, conforme o
protocolo que evita circularidade. Este pacote não antecipa o resultado dessa execução.

W2 permanece NOT_STARTED / NOT_AUTHORIZED. PR OPEN / DRAFT, sem merge ou auto-merge.

## Validação local observada antes do commit

Fast local completo: PASS em 245.893 s, 286 métodos Java sem skips e todos os
boundaries compilados. O primeiro Fast falhou porque o ponto de injeção do teste
de enum.name havia mudado; a mutação agora exige ponto único efetivamente aplicado.
Ambos os receipts/logs estão preservados nos pacotes fast-1/fast-2.

Challenge focal: forçar v1 detectado por três failures; forçar v2 por três;
INVOKE_NORMAL como JUMP por uma. Todos compilaram, foram restaurados byte-exact e
seguidos de segundo GREEN. O script, logs e reports estão no pacote version-challenge.

[Preservação](preservation.json) enumera hashes atuais idênticos à baseline.
Somente CfgJsonWriter e W1dInvokeWireTest diferem entre fontes Java/POM/fixtures
e evidências históricas examinadas. O contrato CFG v1 também permanece intacto.
A Full Qualification final do HEAD commitado será vinculada pelo protocolo acima.
