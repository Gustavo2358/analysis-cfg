# CP5 — correção focal B1

Review humano do PR #12 no HEAD 6675b1f9ff9d11e8f405f6dcf49c5acb0096346a:
REQUEST CHANGES somente B1; restante da preparação aprovado, CP5-F01 não bloqueante.
Esta correção continua na mesma branch/PR draft e aguarda novo review humano.
Nenhuma Wave autorizada ou iniciada.

Contrato mantém observations e separa queryStatus/queryReason de executionStatus.
Oracle manual misto: before(Return) VALUE {PROGA}; after(Return) UNSUPPORTED_POINT,
motivo explícito, campos semânticos null; run STABLE. Plano de queries independente
permite detectar omissão, duplicação e substituição. Dois challenges W3/S6 ficam
declarados com hook/target nulos, sem alegar execução de engine.

[Recibo local, inputs e hashes de logs](local-validation.json),
[campanha dos dois contracasos](batch-challenges.json),
[auditoria do novo snapshot](result-source-audit.json) e
[integridade dos siblings](sibling-integrity.json).

## Prova do contrato e gates

TDD inicial RED: 39 testes CP5, duas falhas esperadas — a representação mista era
recusada e o aborto global era aceito. Após a correção, 47 testes CP5 PASS, incluindo
preservação de PROGA, recusa explícita, plano independente, duplicação/substituição,
motivo obrigatório, semântica null e IDs/owners da query recusada. O validator ainda
admite falhas reais do run com observations=[], sem confundir essa regra com B1.

Cada contracaso executou o entrypoint CLI real do validator em cópia temporária:
baseline GREEN, JSON mutante parseável, exit 1 com diagnóstico nominal, restauração
byte-exact do snapshot, segundo GREEN. `unsupported-query-aborts-batch` falha em
`mixed batch must remain STABLE`; `unsupported-query-disappears` falha em
`requested query coverage`. São provas do harness, não mutantes de uma engine.

| Gate local | Resultado e alcance |
| --- | --- |
| full → fast | PASS docs + 47 testes antigos + 47 CP5 = 94 métodos nominais |
| full → architecture | PASS inventários/bytecode/DAG; regressões existentes; CP5-F01 explícito |
| full → semantic | PASS 84 métodos obrigatórios, zero skips |
| full → performance | UNAVAILABLE, exit 3; full não recebe PASS |
| integration separado | PASS 5 suítes/37 métodos de transporte, além de 102 kernel |
| mvn -B -ntp clean verify | PASS 139 testes Java, zero failures/errors/skips |
| future CP5 gate routing | 20 combinações testadas retornam exit 3; hooks continuam nulos |
| scope/manifest, diff e fast finais | reexecutados após fechar este recibo; saída vinculada no recibo exact-head do PR |

JDK 21 e cache Maven isolado da preparação anterior, com air-java pinado no mesmo
SHA. Onze logs brutos preservados por compressão lossless em `logs/`, hashes dos
bytes originais e gzip no recibo. Toolchain/builds/caches permanecem fora do Git.
Os 23 usos de IDs, incluindo o plano solicitado, resolvem para definições da AIR
CP4E; o ponto consultado é Return. PROGA e os outcomes são oracles manuais de design,
não resultado calculado. Evidências anteriores permanecem históricas e intactas,
inclusive o hash do snapshot original na auditoria anterior.

## Limites e parada

CP5-F01 permanece detectado, com exceção restrita aos bytes do launcher CP4. Review
humano o aceitou como não bloqueante e indicou W1 para regularização das dependências
diretas; nenhum POM foi alterado nesta correção. Nenhum Java, módulo ou engine CP5
implementado, nenhum sibling modificado, nenhuma outra decisão arquitetural reaberta.

HEAD final e CI serão registrados no mesmo PR após o último push, sem SHA
autorreferente no commit. Performance de engine permanece UNAVAILABLE.
Preparação aguarda novo review humano; W1–W5 NOT_STARTED / NOT_AUTHORIZED. Mesmo
PR #12 draft, sem merge, auto-merge ou mudança para ready.
