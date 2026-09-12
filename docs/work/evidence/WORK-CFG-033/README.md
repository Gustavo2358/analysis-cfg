# CP6 W1D — evidências para revisão

Repositório `analysis-cfg`, work item `WORK-CFG-033`, branch
`feat/cp6-w1d-call-dependency`, baseline
`c39a92f930b1c693857a0b30a1f5155f3f81520c` (tree
`1e49f1b731cb6e20dd9a2e0332b7fe2f9166884d`). Este pacote registra a implementação
local; a identidade imutável do commit e os receipts da CI são registrados após
o commit no handoff de entrega e na descrição do Draft PR, sem circularidade de
hash dentro do próprio commit.

## Autoridades e preservação

| Fronteira | Commit exato | Tree |
| --- | --- | --- |
| W1A proleap-poc / SP 1.3.0 | `53d774026a1e4bcd969c7783a1d277aaa87b5f2f` | `a43f0fc4ef8a227d47f012b9fcb4e410842ffcc0` |
| W1B air-java / AIR JSON | `2a37f5e980ba25fdc79614a66030a84d8bf5b8c9` | `8d248f4ccf207eb7b609aa9ff0cdbcd512e526c8` |
| W1C cobol-lower | `9de3825da64898258e647727393f01b9e9198d9e` | `2e3027df2fc4f9e550df8df9518e7e80f89324f1` |
| AIR normativa | `51b4d9a8ae0364232bd97103cd73a77e1a34996c` | `11b2361a40cb5ae9b6c201f0157f30ef2387d1b8` |

[Fontes exatas](exact-upstream.json), [produtores W1D](w1d-producers.json) e
[produtores históricos W5](w5-producers.json) registram sources, classpaths e
hashes. Builds usaram archives isolados com JDK 21. Repositórios Maven separados
evitaram colisão dos SNAPSHOTs de lower histórico/W1D. Os quatro checkouts ocupados
continuaram limpos e nos HEADs originais; os HEADs ocupados não foram confundidos
com as autoridades exportadas. [Antes](protected-before.json) e
[depois](protected-after.json) comprovam solver, lattice e DefaultValuePlan
byte-exact.

## RED → GREEN e challenges

O [RED vertical original](raw/red-vertical-commands.json) executou frontend e
lower reais com sucesso e obteve CFG exit 4, `UNSUPPORTED_TERMINATOR`, para o
Invoke. O [RED CFG nominal](raw/cfg-red-tests.xml) falhou nos três testes antes da
projeção tipada. O [RED values](raw/effects-red.xml) recusava o perfil solicitado;
os oracles de BEFORE, continuação, NoMemory e loop definiram o comportamento
esperado. O RED inicial do loop não foi um resultado incorreto fechado: foi
ausência de suporte. As mutações posteriores de Nop e replay-only demonstram a
necessidade do efeito no fixpoint.

O [RED dependency](raw/dependency-red.xml) confirmou que o ValueFact bruto já
existia e falhou na ausência do produto de dependência. Houve ainda um
[RED real do CFG JSON](raw/cfg-wire-red.log.gz): a projeção tipada já funcionava,
mas o serializer ainda recusava Invoke. O mapper explícito e o teste nominal
`W1dInvokeWireTest` fecham essa fronteira.

O gate W1D exige 34 testes nominais, sem ausências ou skips aceitos, mais quatro
testes do reader/oráculo Python independente. [Campanha final de mutações](mutation-final-receipt.json):
18 mutações compiláveis detectadas por falhas semânticas, duas mutações detectadas
por proteção de fonte, restauração exata e segundo GREEN de 33 testes do lado
analysis. O teste CFG/wire adicional é exigido separadamente no gate W1D.
[Challenge arquitetural de DefaultValuePlan](default-plan-architecture-receipt.json)
repete a tentativa de acrescentar lógica de Invoke pelo gate compilado de W1D.
Nenhum compile error foi aceito como detecção semântica.

As mutações cobrem: recusa de Invoke; jump genérico; destino físico indevido;
edge fictícia de callee; may-write como Nop, strong-kill e replay-only;
AFTER em lugar de BEFORE; dynamic como literal; ObjectId errado; perda de padding;
interpretação dentro de values; suporte globalizado; literal acionando values;
edge de órfão; descarte dos remainders source e interpretation; admissão no perfil
antigo; CALL em DefaultValuePlan; alteração do solver protegido.

## CFG, values e planejamento

Invoke permanece terminador com a AIR original retida pelo CfgGraph. A única
continuação conhecida admitida é `Normal(label)` com `NoControl` ou
`WithinControl(AllControl)`. A transição `INVOKE_NORMAL` aponta para a label AIR
local, conserva activationEntry e não inventa fallthrough, callee ou arestas para
o remainder aberto. Outros outcomes finitos são recusados explicitamente.

`scalar-text-direct@1` mantém a recusa de Invoke. `scalar-text-effects@1` interpreta
NoMemory/AllMemory de forma genérica; writes AllMemory abre cada Cell modelada
preservando candidatos e respectivos produtores. NoMemory preserva valores.
Escopos menores, mustOverwrite, perOutcome e results permanecem fora da slice.
O transfer participa tanto do solver quanto do replay. Sem loop, BEFORE conserva
`"PROGA   "` fechado no modelo; no fixpoint do loop o efeito da chamada anterior
abre o mesmo candidato, sem apagá-lo. Solver/lattice não foram alterados.

O planner W4 consulta o bucket indexado de Invoke, filtra program/cobol.program,
declara a query BEFORE para o ObjectId real de Read(ObjectPlace) e compartilha
AnalysisKey/batches entre consumidores e queries duplicadas. Há uma execução
de PossibleValues por chave. Literal declara zero queries/execuções de valores,
inclusive em teste sem provider de valores instalado. Reachability é BFS
independente por Entry no ContextView. Órfãos geram site explícito, sem edges.

## Produto e vertical real

O contrato separado é `analysis-dependency-result` 1.0.0. O site conserva UnitId,
EntryId, Sequence, OperationId, offset, site/target origins e reachability. Cada
candidato bruto/interpretado conserva seu próprio suporte. Evidence, provenance,
premises, uncertainty refs e catálogos de origens/artifacts permitem a auditoria
até o MOVE original. Não há confidence score, lookup de deployment nem call graph
interprocedural. O wire anterior W5 e seus goldens permanecem preservados.

| Campo do dynamic X8 real | Resultado |
| --- | --- |
| caller | `unit` na publicação `437c54421d9cd62278589ab67b6c7064` |
| EntryId | `c4de8a797697cb8ddf3012f2248da01f` |
| Sequence | `4d98ca286e7b8a4f406eae34f261a21b` |
| Invoke OperationId | `5357696c937658b9eac2d662086444cd` |
| ObjectId / BEFORE | `d277a147621da9e9d6d7e52986bbb8ee` / BEFORE do Invoke |
| site / target origins | `03604a019cfc597e2d3a551583a29e96` / `0e9557a6c8ad9c3c4c024e177fddc655` |
| raw candidate | `"PROGA   "` (oito caracteres) |
| reference candidate | `PROGA` |
| Assign produtor | `e52226b01b4afc8f6c6214aafd63e5ab` |
| origin do produtor | `3c6963c67a2591c7d79e90cb27c95681` → MOVE original, linha 7 |
| model / source / interpretation / effective remainder | false / true / true / true |
| open control remainder | true |

O perfil `cobol-zos-dynamic-call-minimal@1` remove apenas U+0020 à direita de
computed values. Preserva o raw e admite somente o subconjunto canônico
documentado. UnknownName continua mantendo interpretation remainder aberto.
Literal não passa por trim. Fora da política mínima, o raw permanece disponível,
sem candidato exato inventado. A política autorizada e o alcance das autoridades
IBM estão em [name policy](../../../domain/cp6-call-name-policy.md).

[E2E receipt](e2e-receipt.json) e [bytes e logs](raw/w1d-real-e2e-final.tar.gz)
registram duas execuções frescas de COBOL → SP 1.3.0 → AIR → CFG → query BEFORE →
ValueFact → dependency, sem injections. Os bytes das duas execuções são idênticos
em cada fronteira. No literal real, o target AIR é LiteralTarget PROGA, há um
candidato PROGA e zero execuções de PossibleValues; reachability é verificada.

Os negativos incluem dynamic sem MOVE (aberto, sem nome/edge inventado), órfão
literal/computed, expressão não suportada, namespace/category errados, nome
lowercase/não canônico, outcomes finitos fora da slice e USING (lower exit 4,
nenhuma AIR publicada). A fonte real continua PARTIAL. O consumer não infere
semântica a partir de texto COBOL, linhas ou nomes de arquivo; a linha de MOVE é
usada somente pelo oráculo independente para auditar a proveniência.

O reader Python estrito valida campos, duplicatas, versões, enums, IDs, nulls,
ordenação, suporte por candidato, OR dos remainders e derivação de edges. O adapter
grava temporário no diretório de destino e substitui atomicamente após sucesso;
falhas preservam o destino e removem o temporário. Há teste real do limite
operacional do codec, exit 7, sem produto parcial, seguido de recuperação.

## Gates e limites

Os resultados finais dos gates, logs e probes estão no [receipt de validação](validation-receipt.json), todos com exit 0.
O full canônico reúne docs/fast, arquitetura/boundaries, semântica W1–W5,
performance e integração CP5 (CP3, CP4E e overwrite). O gate W1D adiciona a nova
vertical e desafios sem alterar as autoridades dos produtores históricos.

Os probes model-level usam N=256 e 2N=512 Invokes, com uma execução de values,
queries 512/1024 e transfers 258/514. São observações específicas dessa família,
sem alegação de qualificação ampla. A preparação do índice de Cells é única;
effects visitam as Cells modeladas sem varredura repetida de objetos por Cell.
Candidate interpretation percorre somente os textos e suportes observados.

W1A/W1B/W1C permanecem APPROVED / MERGED. W1D e CP6 W1 estão
IMPLEMENTED / AWAITING_HUMAN_REVIEW, com entrega em PR OPEN / DRAFT. W2 permanece
NOT_STARTED / NOT_AUTHORIZED. Não houve mudanças de produção em siblings,
semântica de solver/lattice, CALL em DefaultValuePlan, resolução dinâmica no
lower, lógica COBOL de nomes em PossibleValues, callee CFG, deployment lookup,
IF W2, merge ou auto-merge.

## Resultados locais verificados

| Gate | Resultado |
| --- | --- |
| docs / fast | PASS na execução full contínua |
| architecture / boundary | PASS; 108 testes kernel, zero skips; inventários de fonte e bytecode |
| CFG oracles | PASS; 84 testes nominais |
| W1 / W2 / W3 / W4 / W5 | PASS em semântica e performance |
| integration | PASS; 38 testes e CP3 / duas CP4E / overwrite |
| W1D | PASS; 34 testes nominais + quatro do oráculo Python |
| real W1D E2E | PASS; duas execuções com bytes idênticos e proveniência MOVE linha 7 |
| mutations | PASS; 18 semânticas + duas de fonte; segundo GREEN |
| CLI script | PASS; mesmos bytes da vertical Java |
| full | PASS contínuo em raw/full-4.log.gz |

Scope, MANIFEST e diff são conferidos após montar este pacote; seus resultados e os receipts de CI/HEAD são registrados no handoff de entrega.

### SHA-256 do dynamic X8 real

| Artefato | SHA-256 (igual nas duas execuções) |
| --- | --- |
| dynamic-x8.cbl | `e322e5af1ec7dd4380bded9a44329b10e82c0dcc851ad18e61807f8475847725` |
| cobol-semantic-product.json | `e45c6fe191c6e9be4b35da793663fc908b0b600c848a236d797170e286614010` |
| program.air.json | `bef5d8979c0ffd3fb96272dd405b18d7b873e4e4d9244eeeb9ac98587fb973d2` |
| cfg.json | `22d199bece7eb53ba45f720e723ba5cf33b7b3292ac496372fb5dfc707032090` |
| dependencies.json | `9e546ba99d0d8747e06433f281a04ca62b3776fcdc67e155ea3f98697511a8c7` |

### Probes model-level

| Invokes | Sites | Queries | Transfers | Tempo observado |
| --- | --- | --- | --- | --- |
| 256 | 256 | 512 | 258 | 56.412 ms |
| 512 | 512 | 1024 | 514 | 78.010 ms |

Uma execução de values por caso. Tempos são observações locais, sem critério de qualificação ampla. As três falhas anteriores de full estão preservadas no receipt de validação com suas causas e os logs completos.
