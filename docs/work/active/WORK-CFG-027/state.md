# WORK-CFG-027 — Estado

## Onde estamos

Implementation 4D autorizada. Baseline limpo/atualizado 2b4df46d53ce5b21a5c315d3f691b183cb6bd124, branch chore/pin-air-json-scalar-assign. Merge 4B e PR #10 confirmados por GitHub.

## Verde conhecido

Nenhum gate de 4D ainda executado. Golden extraído do merge: 14554 bytes, SHA-256 40b9cec1bcc1c1e40cf3b9e3c48e834835e478e1d84bccafa575d63497ef3b60.

## Restante

RED baseline, integração, challenges, gates, PR/CI head exato.

## Descobertas que afetam o plano

ObjectPlace armazena ObjectId, não ponteiro para ObjectDeclaration; provar resolução por ID completo e mesma instância da declaração/célula na Publication retida. Upstream extraído em /tmp para builds; nenhuma fonte irmã alterada.
