# WORK-CFG-027 — Estado

## Onde estamos

Implementation 4D autorizada. Baseline limpo/atualizado 2b4df46d53ce5b21a5c315d3f691b183cb6bd124, branch chore/pin-air-json-scalar-assign. Merge 4B e PR #10 confirmados por GitHub.

## Verde conhecido

RED baseline em c48a898: IMPLEMENTATION_LIMIT em $.publication.storage. Segundo GREEN em Temurin 21: fast (47), scope/manifest/diff check, architecture, semantic (84), integration (37), Maven clean verify (139). 14 challenges RED com restore byte a byte. Produção kernel/adapters/launcher sem delta. Scalar 14554 bytes, SHA-256 40b9cec1bcc1c1e40cf3b9e3c48e834835e478e1d84bccafa575d63497ef3b60; CLI determinística, payload por identidade, smoke 4096 Assigns com três nós/duas transições. GOBACK intacto. Performance UNAVAILABLE/exit 3. [Evidência](../../evidence/WORK-CFG-027/validation.md).

## Restante

Commits/push/PR e CI no head exato; depois apenas review humano. Sem merge/auto-merge. Recibo remoto final no PR evita autorreferência do SHA no próprio commit. 4C/4E não iniciados.

## Descobertas que afetam o plano

ObjectPlace armazena ObjectId, não ponteiro para ObjectDeclaration; provar resolução por ID completo e mesma instância da declaração/célula na Publication retida. Upstream extraído em /tmp para builds; nenhuma fonte irmã alterada.
