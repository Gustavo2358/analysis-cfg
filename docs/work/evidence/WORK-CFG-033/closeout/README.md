# CP6 W1 / W1D — closeout formal

**APPROVED / MERGED / CLOSED**. WORK-CFG-033 consta como `completed` no registry.
W2 permanece **NOT_STARTED / NOT_AUTHORIZED**. Baseline para a próxima wave: **FROZEN**.

## Identidade e autoridade

[PR #18](https://github.com/Gustavo2358/analysis-cfg/pull/18): MERGED / CLOSED,
merge explícito com dois pais, sem auto-merge, force push ou rebase.
Aprovação humana foi fornecida pelo usuário nesta tarefa; não é inferida dos gates
nem apresentada como um review GitHub inexistente.

- Source HEAD qualificado: `d8f9652f79c3aefb1eeae25f48760256564e081e`.
- Source tree qualificada: `943c8eb77b63ce08c7d5bdc685e08d3a4f7d7a45`.
- Base anterior: `c39a92f930b1c693857a0b30a1f5155f3f81520c`.
- Merge e main imediatamente após fast-forward: `61065d55642d68320afaa1f9f6c46b322912e151`.
- Timestamp do merge: `2026-09-12T03:00:53Z`.
- Tree do merge: `943c8eb77b63ce08c7d5bdc685e08d3a4f7d7a45`.
- Relação: **QUALIFIED_TREE_PRESERVED**.

[Receipt do merge](merge-receipt.json), [preflight](preflight-local.json),
[lifecycle e baseline](../../../cp6-lifecycle.json). O baseline de produto é o
merge acima. O commit administrativo posterior altera documentação/roteamento;
sua tree total é distinta e não recebe a identidade nem a qualificação do produto.
A única mudança no harness troca o caminho do manifesto ativo pelo mesmo manifesto
arquivado; inventários e proteções de fontes permanecem integrais.

[Full Qualification existente](qualified-source-receipt.json): LOCAL_QUALIFICATION,
PASS, 695.547 s, 13 fases; SHA-256 `de496f5d112d8ee3df3836a8f8e7c334e134f0e10c677291d98c6df7d19ac81d`.
Cópia byte-exact do receipt original, emitido em d8f9652 antes do push. Seus 382
hashes foram verificados no preflight. Os logs/artefatos continuam nos caminhos
originais do receipt e no pacote externo
`teste-e2e/.cp6-w1d-remediation/delivery/local-qualification-final-evidence.tar.gz`.
Nenhum output é reatribuído ao merge. Full e qualification remota manual não foram
executadas neste fechamento.

[Fast CIs revisados](reviewed-fast-ci.json): ambos PASS no source HEAD qualificado.
[Smoke pós-merge](postmerge-fast-receipt.json): somente `check-fast.sh`,
docs/harness, PASS em 166.255 s; [log bruto comprimido](postmerge-fast.log.gz).
Após o arquivamento, a [mesma suíte documental/harness](archive-fast-receipt.json)
passou novamente sobre o diff administrativo; [log](archive-fast.log.gz). FAST_CI remoto é uma
autoridade diferente da qualificação local; continua automático em push/PR.
A full continua explícita via `check-qualification.sh` ou `qualification.yml`
(`workflow_dispatch`), com a mesma lógica e todos os gates pesados preservados.

## Capacidade entregue e limites

A vertical real prova COBOL → Semantic Product → AIR → CFG com Invoke →
PossibleValues/fixpoint → BEFORE(CALL) → valor literal com producer provenance →
dependency candidate.

```cobol
MOVE 'PROGA' TO WS-PGM
CALL WS-PGM
```

No caso X(8), o raw candidate ajustado é `"PROGA   "` e o interpreted candidate é
`"PROGA"`. Um dependency site REACHABLE/RESOLVED_CANDIDATES é produzido;
VALUE_PRODUCER aponta para o Assign/MOVE original (linha 7 do COBOL), com origin
e producer IDs preservados. A consulta é BEFORE do Invoke real.

Os remainders observados são preservados separadamente: sourceValueRemainder=true,
modelValueRemainder=false, interpretationUnknownRemainder=true,
effectiveUnknownRemainder=true e openControlRemainder=true. Portanto a fonte/site
continua PARTIAL/open; o singleton não vira uma prova de completude do programa.
A capacidade é **Possible Values com candidate-specific producer provenance**,
suficiente para o CALL dinâmico singleton desta slice. W1 não implementa um
framework genérico de Reaching Definitions clássico.

A [vertical qualificada](qualified-e2e-receipt.json) executou dynamic-x8, literal,
dynamic-no-move e a recusa USING duas vezes; determinismo BYTE_IDENTICAL, sem
injeção de SP/AIR. Literal usa reachability sem PossibleValues; sem MOVE não há
candidato inventado. O resultado não prova IF COBOL real/multi-candidate de W2.

## Contratos e preservação

CFG legado continua `analysis-cfg-json 1.0.0`; domínio com INVOKE/INVOKE_NORMAL
seleciona `2.0.0`. A major reconhece os conjuntos fechados de tokens v1;
contrato/goldens v1 permanecem byte-exact. PROGA não pertence ao CFG JSON;
`analysis-dependency-result` continua em `1.0.0`.

Pins W1A `53d774026a1e4bcd969c7783a1d277aaa87b5f2f`, W1B `2a37f5e980ba25fdc79614a66030a84d8bf5b8c9`,
W1C `9de3825da64898258e647727393f01b9e9198d9e` e AIR normativa `51b4d9a8ae0364232bd97103cd73a77e1a34996c` permanecem iguais.
[Preservação](preservation.json) e [verificação final de 2.231 arquivos](administrative-preservation.json): siblings limpos/inalterados; nenhum pin, Java,
POM, fixture, solver, lattice ou DefaultValuePlan mudou no fechamento.
Os cinco arquivos do work item foram movidos byte-exact para history; seu state
anterior é um snapshot histórico. Estado vigente: [encerramento](../../../history/WORK-CFG-033.md).
As evidências anteriores de W1D, inclusive o wire Invoke v1 não conforme, REDs,
mutações e CI intermediário falho, não foram apagadas ou reescritas.

## Handoff conceitual

W2 — IF COBOL real, multi-candidate e open remainder. A futura slice poderá
confrontar um IF com MOVE PROGA/PROGB nos ramos seguido por CALL WS-PGM.
Não há branch, work item, RED, código de IF/Branch/predicate/lowering ou extensão
multi-candidate nova. Nenhum sibling foi alterado. A próxima wave exige nova
autorização; este fechamento termina aqui.
