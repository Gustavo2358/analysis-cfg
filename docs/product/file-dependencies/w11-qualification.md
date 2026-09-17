# FD-W11 — qualificação final N+C

**Checkpoint atual: [QUALIFIED_LOCAL_POST_EP_R2](post-ep-r2.md).**
A evidência C06 abaixo é histórica; gates compartilhados/corpus foram reexecutados no checkpoint atual.

**QUALIFIED_LOCAL; STOP para revisão humana final.** W0–W9/W11 qualificados.
[C06-HUMAN-20260917](c06-read-dataset.md) resolve o blocker anterior e autoriza
somente READ DATASET como alias de READ FILE, preservando os aliases SET já
provados. W10 TODO / NOT_AUTHORIZED. Nenhum merge/auto-merge/release; os work
items ficam IN_PROGRESS pela política lean, pois os PRs não foram mergeados.

Evidência nova: `artefatos-e2e/file-dependencies-20260916/w11-c06/HANDOFF.md`.
O diretório anterior `w11/`, incluindo QUALIFICATION.md/logs/manifests, permanece
imutável como evidência dos gates REUSED e das tentativas históricas.

## Pins e frontier

[pins materiais](w11-pins.json), bundle isolado `fd-w11-c06/pipeline`:

| Camada | Commit exercitado |
| --- | --- |
| frontend | `17323f4718cbd957d7abe52e988b01ece6a9463f` |
| lower | `5565e10c23a8b023a16ddb8556604dc331ef89a4` |
| AIR | `5fe0224e5d2514286d6d23d486655334300383da` |
| consumer | `e0e7559d90692446b8fb7ecc8135432ffd44a018` |
| norma AIR | `fb153ae50f343022db45d20d627e1afac85de916` |
| CardDemo | `59cc6c2fd7ebd7ef7925cad552a01a4b8b6e4d5e` |

SP2.28/fileInventory1.6/compilation1.0 e wire2.3 inalterados. Java21.0.12,
frontend release17. Checkouts de runtime limpos;345 arquivos hasheados antes e
depois dos runs, sem substituição por build de outra campanha.

Delta produtivo restrito a duas regras: reconhecimento canônico READ/DATASET no
frontend e admissão tipada do mesmo alias no lower. Scanner, efeitos, controle,
solver, AIR, codec e consumer inalterados. `frontier.json` conserva os dois diffs.
CFG final acrescenta somente testes/oráculos/docs/pins ao pin exercitado; a
identidade produtiva é verificada. Não reexecutar Q amplo por esses deltas.

## Gates novos

Todos os resultados PASS finais abaixo têm exit0. Comandos completos, pins,
logs e tentativas substituídas constam de `w11-c06/gates.json` e `execution/`.

| Comando / gate | Propriedade e resultado |
| --- | --- |
| frontend `mvn -B -ntp -Dtest=CicsFileControlTest,CicsProgramControlTest -Dcics.file.fixtures.dir=<output>/sp test` | PASS22/zero skips: literal/computed, spelling/offsets, duplicação/operand ausente, comando não autorizado, Program Control |
| frontend `python3 -B scripts/harness/lean.py fast` | PASS355/zero skips |
| lower mesmo FAST fixo | PASS core2340+adapters;35CICS, negativos DSNAME/READNEXT e arquitetura |
| `carddemo_setup.py --work .../pipeline --maven-repo .../m2 --pins .../pins.json` | PASS bundle imutável/pins/345 hashes; barreira3 fontes×2, quatro produtos determinísticos |
| `e2e_cics_files.py --runtime .../runtime.json --work .../selected --producers .../pipeline/producers/producers.json` | PASS41 fontes×2, FILE/CALL/Program Control; source-only e ausência de declarações físicas |
| `compare-alias-pairs.py` | PASS literal/computed: mesmas projeções semânticas FILE e vetores CALL entre FILE e DATASET, normalizando somente o nome da fixture |
| `run-affected-corpus.py` | PASS21 fontes,19 afetadas+2 controles, CLIs reais e reader independente; source/ZIP intactos |
| `summarize-delta.py` | PASS35 sites recuperados,41 CALL inalterados;3COACTVWC bindings/provenance; controles com SP/AIR/CFG/dependency byte a byte iguais |
| `python3 -B scripts/harness/lean.py docs` | PASS política/pins/links/14 testes lean; revisão final no handoff |

Frontend RED:12 testes/3 falhas semânticas anteriores à implementação. O log
registra Maven BUILD FAILURE; exit1 é do wrapper que depois usou a variável zsh
readonly `status`, não um exit Maven separado. Lower RED: admissão bilateral
rejeitou o alias (FAST exit1). Oracle manual COACTVWC contra corpus anterior:
exit1 por ausência dos três fatos. Todas as evidências RED permanecem brutas.

Tentativas FAIL preservadas: oracle computed inicialmente exigia modelo fechado
apesar de CALL local aberto; corrigido pela lei W9 e comparado ao FILE canônico.
Relatório inicial comparou startLine string do wire com inteiro SP; corrigido
no avaliador, sem alterar produtos ou relaxar a exigência de provenance.

## Evidência REUSED

Frontier restrita, pins exatos e dois controles byte idênticos justificam reuso,
conforme verification.md; não confundir REUSED com nova execução.

| Evidência anterior | Reuso e limite |
| --- | --- |
| frontend qualification-local946 (um skip histórico somente Q); lower Q244296 semânticos/39215 performance | Regras de parsing geral, lowering/efeitos/arquitetura inalteradas; alias novo coberto focalmente e pelo FAST fixo |
| AIR FAST187/127/40 e Q187/127; CFG FAST504 e Q completo | Código produtivo idêntico; novos bytes verificados por CLI/reader nas barreiras e corpus |
| B-SP/B-AIR41/B-WIRE18 | Contratos/versionamento inalterados; admissão READ alias refeita bilateralmente; rejeição wire antigo e projeção CALL preservadas |
|101 fontes×2; MR1–MR9/SG core;21 witnesses escala;5 mutantes KILLED | Native/storage/owner/BEFORE/SORT/naming/semântica de lookup inalterados; coorte CICS refeita41×2 e pares aliases acrescentados |
| Corpus original73 / oráculos49 usos nativos e2READ FILE |52 entradas sem impacto REUSED;21 reexecutadas, sem alegar nova execução completa73 |
| Arquivos prévios9180 e finais5324 | Manifests/hash/descompressão do checkpoint anterior preservados; novo archive separado |

NOT_RUN: novo full local/qualification-local amplo, full remoto, W10/D, gates
pós-merge e lookup externo. BLOCKED da campanha: nenhum; recusas de inputs e
formas não qualificadas continuam explícitas abaixo. FAIL históricos têm run
substituto identificado, sem serem apagados ou transformados em PASS.

## Corpus, CALL e limites

Inventário independente:35 READ DATASET em19 entradas físicas, incluindo cópias
ZIP. Reexecução dessas19+2 controles recuperou exatamente35 dependências FILE.
41 vetores CALL comparáveis permaneceram iguais (candidates/supports/remainders/
provenance); nenhuma adição/remoção/mudança. Os três sites de COACTVWC nas linhas
727/776/826 preservam os bindings LIT-CARDXREFNAME-ACCT-PATH, LIT-ACCTFILENAME e
LIT-CUSTFILENAME, com namespace `cics.file`, ação read e provenance original.
Os três nomes continuam computed com remainder unknown: inicializador declarado
não prova o valor no ponto do comando. Sem DSNAME/JCL/physical resource/lookup.

Observação combinada21 novos+52 REUSED:73 inputs,15973 statements,85 declarações,
133CALL/411FILE. Frontend71PARTIAL/2BLOCKED; lower71PARTIAL/2NOT_REACHED;
CFG65PARTIAL/6BLOCKED/2NOT_REACHED; dependency71PARTIAL/2NOT_REACHED.
Duas recusas frontend: fonte fixed com TAB COTRTLIC e CBSTM03A do ZIP. Seis
recusas CFG: logical-copy sem precondição física em CBTRN01C/02C/03C e cópias ZIP.
O ramo dependency independente conserva esses estados; não mascara CFG BLOCKED.

52 ocorrências DATASET em outros comandos continuam **NOT_QUALIFIED**:
REWRITE4, STARTBR12, READPREV12, ENDBR8, WRITE6, READNEXT8, DELETE2. Registradas
separadamente por instrução humana; sem generalizar o alias, mover para D ou
alegar suporte completo a todas as formas do corpus. A qualificação de W8/W11
cobre o catálogo selecionado e a decisão C06 explícita. Counts não são oracle
nem precisão/recall; ausência de DSNAME/JCL não reduz confiança do produto.

| Etapa /21 fontes novas | mediana / p95 / máximo (ms) | RSS máximo (KiB) |
| --- | --- | --- |
| frontend |2318.2 /5624.7 /5874.4|410936|
| lower |1768.6 /4024.0 /4072.0|644152|
| CFG |765.4 /1970.8 /2123.3|988288|
| dependency |1015.9 /2872.7 /2971.2|1110532|

Medição inclui startup JVM/carga compartilhada; sem SLA ou extrapolação. Reader
Python independente tem custo quadrático em origins e leva minutos nos produtos
maiores; stageMs mede CLI, tempo total do runner inclui validação.

D-AIR/D-WIRE/D-EFFECT/D-DYNAMIC core CLOSED; D-D-AUTH reservado W10.
Cinco PRs persistentes OPEN/DRAFT/UNMERGED. STOP para revisão humana final.
