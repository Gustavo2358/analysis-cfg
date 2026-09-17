# Matriz de cobertura N/C/D

Todas as linhas implementáveis começam **PLANNED**. H0–H4 não promove nenhuma
a QUALIFIED. Estados: PLANNED, QUALIFIED (testes/pins citados), BLOCKED (causa/dono),
OUT_OF_PROFILE (autoridade/motivo; nunca usado para esconder obrigação pendente).
Cada célula de contrato é intenção, não tipo já implementado. Testes em
[catálogo](test-catalog.md); autoridades em [perfis](profiles.md); itens em [waves](waves.md).

## N — COBOL nativo e auditoria das alternativas do parser

N-LR governa N01–N03/N05–N17/N19–N33; estas linhas e C01–C07 são CORE REQUIRED.
N04/N18 mantêm IDs históricos, mas pertencem a D (EXTENDED PROFILE posterior).
D continua PLANNED e visível; não bloqueia o core em W11 nem conta como QUALIFIED.
N14/N17 incluem alternativas heterogêneas: W0 conserva e W6 classifica por cláusula
com fonte; nenhuma obrigação nativa pode ser encerrada por um status agregado.
Ponto de gramática: `proleap-poc/src/main/antlr4/Cobol.g4`. Frontend produz facts;
lower/consumer recebem descritores/efeitos, nunca esses nomes de regras.

| ID / família | Estado / perfil | Gramática → fato esperado; efeito/consumer | Oráculo | Wave |
| --- | --- | --- | --- | --- |
| N01 `SELECT`, `SELECT OPTIONAL` | QUALIFIED / N-LR ([W6/auditoria e limites](w6-implementation.md)) | `selectClause` → Conector lógico; opcionalidade; origem; Declaração isolada não vira leitura; ausência runtime não apaga dependência. | T01,T02 | W0 |
| N02 `ASSIGN TO assignment-name` IBM | QUALIFIED / N-LR ([W6/auditoria e limites](w6-implementation.md)) | `assignClause` → Nome escrito; componentes interpretados segundo perfil; external file name conhecido, namespace conceitual cobol.external-file-name/sourceKind ASSIGNMENT_NAME; não afirmar mecanismo DD/environment nem bindingMechanism UNKNOWN; não tratar nome sem aspas como data item COBOL. | T02,T30 | W0 |
| N03 Prefixos/comentários da assignment-name | QUALIFIED / N-LR ([W6/auditoria e limites](w6-implementation.md)) | `assignClause` → Assignment-name original e external file name interpretado, regra/revisão; não inferir mecanismo externo nem aplicar corte universal por hífen. | T30 | W0/W6 |
| N04 ASSIGN literal/dinâmico/externo em dialetos | PLANNED / D-GC32 EXTENDED | `assignClause` → Variante tipada e expressão/alvo de associação no próprio fonte; Dialeto selecionado; literal, data item e ambiente não colapsam. | T31–T35 | W10 |
| N05 Organização e modo de acesso | QUALIFIED / N-LR ([W6/auditoria e limites](w6-implementation.md)) | `organizationClause/accessModeClause` → SEQUENTIAL/LINE SEQUENTIAL/INDEXED/RELATIVE e modo; Não chamar todo arquivo indexed de VSAM sem autoridade de plataforma. | T07,T10 | W0 |
| N06 `RECORD KEY`, `ALTERNATE RECORD KEY`, `RELATIVE KEY` | QUALIFIED / N-LR ([W6/auditoria e limites](w6-implementation.md)) | `recordKeyClause/alternateRecordKeyClause/relativeKeyClause` → Referências resolvidas, duplicatas e papéis; Chave não vira arquivo nem alvo executável. | T10 | W0 |
| N07 `FILE STATUS`, status adicional | QUALIFIED / N-LR ([W6/auditoria e limites](w6-implementation.md)) | `fileStatusClause` → Destinos e tipos/áreas pertinentes; Efeitos sobre status chegam ao dataflow. | T10,T18 | W0/W3/W4 |
| N08 FD versus SD | QUALIFIED / N-LR ([W5](w5-implementation.md)) | `fileDescriptionEntry` → Espécie explícita e associação a SELECT; SD/sort-work não produz nome externo ou alvo persistente inventado quando o fonte não o fornece. | T01,T23 | W0/W5 |
| N09 Registros 01, vários layouts, COPY | QUALIFIED / N-LR ([W6/auditoria e limites](w6-implementation.md)) | `dataDescriptionEntry` → Relação registro–conector e áreas de memória; WRITE por registro resolve o arquivo proprietário. | T04,T05,T15 | W0/W3 |
| N10 `GLOBAL`, `EXTERNAL`, programas contidos | DECLARATIVE QUALIFIED W0; scope/captures W9 TODO / N-LR | `globalClause/externalClause` → Visibilidade e identidade semântica; Mesma grafia não é prova de compartilhamento; captures explícitos. | T46 | W0/W9 |
| N11 `RECORD CONTAINS`, `RECORD VARYING`, DEPENDING | QUALIFIED / N-LR ([W6/auditoria e limites](w6-implementation.md)) | `recordContainsClause` → Comprimentos, dependências e desconhecimento localizado; Não sobrescrever bytes fora do intervalo provado. | T17 | W0/W3 |
| N12 BLOCK/RECORDING MODE/LABEL/DATA RECORDS/CODE-SET | QUALIFIED / N-LR ([W6/auditoria e limites](w6-implementation.md)) | `fileDescriptionEntryClause` → Cláusulas conservadas; impacto classificado; Metadado sem efeito no alvo não bloqueia esse alvo; não apagar efeito real. | T51 | W0/W6 |
| N13 `LINAGE`, contador, WRITE ADVANCING/EOP | QUALIFIED / N-LR ([W6/auditoria e limites](w6-implementation.md)) | `linageClause/writeStatement` → Metadados de impressão, estado e controle pertinente; EOP não confundido com EOF; operandos de paginação não são arquivos. | T10,T22 | W3/W4/W6 |
| N14 PADDING/RESERVE/PASSWORD/delimiter e outras alternativas observadas | QUALIFIED / N-LR ([W6/auditoria e limites](w6-implementation.md)) | `fileControlClause` → Papéis tipados ou desconhecimento específico; Texto bruto não promovido a semântica fechada. | T50,T51 | W0/W6 |
| N15 `SAME RECORD AREA` e demais SAME por perfil | QUALIFIED / N-LR ([W6/auditoria e limites](w6-implementation.md)) | `sameClause` → Relações de alias de buffers ou classificação documental conforme autoridade; Compartilhar memória não funde identidades/alvos de arquivo. | T16 | W3/W6 |
| N16 `RERUN`/checkpoint | QUALIFIED / N-LR ([W6/auditoria e limites](w6-implementation.md)) | `rerunClause` → Referência de checkpoint, arquivo gatilho quando aplicável, origem; Forma sem EVERY; dependência sem SELECT quando o perfil assim definir. | T29 | W6 |
| N17 MULTIPLE FILE, APPLY/legado, commitment control | QUALIFIED / N-LR ([W6/auditoria e limites](w6-implementation.md)) | `ioControlClause` → Referências e classificação por dialeto; Não inventar um único alvo source-level quando a sintaxe representa múltiplos arquivos. | T51 | W6 |
| N18 REPORT(S)/RD em dialetos com Report Writer | PLANNED / D-GC32 EXTENDED | `reportClause/reportSection` → Ligação relatório → arquivo; registros/controle pertinentes; Relatório não vira programa chamado. | D-RW | W10 |
| N19 `OPEN INPUT/OUTPUT/I-O/EXTEND` | PLANNED / N-LR | `openStatement` → Um ou vários file-names agrupados por modo; Modo por arquivo; associação no OPEN; resultados/status; opções do perfil. | T07 | W2 |
| N20 `CLOSE` e suas opções | PLANNED / N-LR | `closeStatement` → Um ou vários file-names; Cada uso; efeitos de fechamento; opções de mídia/lock, quando admitidas. | T08 | W2 |
| N21 `READ` sequencial, NEXT e aleatório | PLANNED / N-LR | `readStatement` → File-name; Buffer, INTO, chave, lock, EOF/invalid key, delimitadores explícitos/implícitos. | T10–T14 | W2/W3/W4 |
| N22 `WRITE` | PLANNED / N-LR | `writeStatement` → **Record-name**; Resolver proprietário; FROM; paginação; resultados/status e handlers. | T05,T06,T14 | W2/W3 |
| N23 `REWRITE` | PLANNED / N-LR | `rewriteStatement` → **Record-name**; Resolver proprietário; FROM; atualização de registro; estado e invalid key. | T05,T14 | W2/W3 |
| N24 `DELETE ... RECORD` | PLANNED / N-LR | `deleteStatement` → File-name; site de uso que pode sustentar programa→arquivo, inclusive quando é o único uso; operação DELETE_RECORD. Exclusão de registro, nunca remoção de dataset/DSNAME/recurso físico. | T09 | W2 |
| N25 `START` | PLANNED / N-LR | `startStatement` → File-name; Posicionamento/chave; não equivale a leitura de conteúdo. | T08 | W2 |
| N26 `SORT ... USING ... GIVING ...` | QUALIFIED / N-LR ([W5](w5-implementation.md)) | `sortStatement` → SD + arquivos de entrada e saída; Todos os recursos e papéis, inclusive I/O implícito. | T23 | W5 |
| N27 `SORT ... INPUT/OUTPUT PROCEDURE` | QUALIFIED / N-LR ([W5](w5-implementation.md)) | `sortStatement` → SD + procedimentos locais; Invocação/retorno de procedimentos locais; não criar dependência programa–programa fictícia. | T24 | W5 |
| N28 `MERGE ... USING ... GIVING/OUTPUT PROCEDURE` | QUALIFIED / N-LR ([W5](w5-implementation.md)) | `mergeStatement` → SD + entradas/saídas; Múltiplas entradas e restrições próprias; não copiar sintaxe de SORT indiscriminadamente. | T25 | W5 |
| N29 `RELEASE [FROM]` | QUALIFIED / N-LR ([W5](w5-implementation.md)) | `releaseStatement` → Sort record; Resolver SD proprietário; semântica de transferência e memória. | T26 | W5 |
| N30 `RETURN sort-file [INTO]` | QUALIFIED / N-LR ([W5](w5-implementation.md)) | `returnStatement` → SD; Próximo registro de sort; AT END; não é GOBACK. | T27 | W5 |
| N31 `USE AFTER ... ERROR/EXCEPTION` | PLANNED / N-LR | `useStatement` → Arquivo/modo conforme forma; Associação handler–evento; disparo, precedência e retorno. | T21,T22 | W4 |
| N32 `AT END`, `INVALID KEY`, formas NOT, END-* | PLANNED / N-LR | `readStatement/writeStatement` → Operação envolvente; Sucessores corretos; statements internos visitados uma única vez. | T11,T12 | W2/W4 |
| N33 Referências em IF/EVALUATE/PERFORM/GO TO | PLANNED / N-LR | `statement/IF/EVALUATE/PERFORM/GO TO` → Sites I/O dentro desses corpos; Reutilizar controle atual; não achatar todos os acessos em execução linear. | CALL-X5 | W2/W4 |

## C — CICS TS 5.6 confirmado

Parser canônico de embedded language/host operands, SP tipado → lower invoke/
effects/conditions → consumer FILE. C-FC/autoridade SPI específica antes do código.

| ID / família | Estado | Contrato / efeito | Oráculo | Wave |
| --- | --- | --- | --- | --- |
| C01 Acesso direto | QUALIFIED / C-FC ([W8](w8-implementation.md)) | Todos os comandos da família acima, com recurso literal ou computado. | T36,T37 | W8 |
| C02 Browse | QUALIFIED / C-FC ([W8](w8-implementation.md)) | Associação de contexto/REQID quando necessária à análise; não exigir FD/SELECT nativo. | T36,T38 | W8 |
| C03 Operand roles | QUALIFIED / C-FC ([W8](w8-implementation.md)) | FILE, SYSID, INTO/SET, FROM, RIDFLD, LENGTH, TOKEN e demais opções por comando. | T38,T39 | W8 |
| C04 Efeitos e resposta | QUALIFIED / C-FC ([W8](w8-implementation.md)) | Memória, ponteiros e códigos de resposta; não inventar sucesso nem ignorar controle de exceção. | T39,CALL-X1 | W8 |
| C05 `INQUIRE FILE`, `SET FILE` | QUALIFIED / C-FC ([W8](w8-implementation.md)) | Uso administrativo distinto de leitura/escrita de registros; saída de INQUIRE não é nome de entrada. | T40 | W8 |
| C06 Aliases e configurações | QUALIFIED / C-FC ([W8](w8-implementation.md)) | FILE/DATASET e demais aliases da própria sintaxe, quando admitidos pela autoridade exata | T40,T41 | W8 |
| C07 Tratamento de condições | QUALIFIED / C-FC ([W8](w8-implementation.md)) | RESP/RESP2, NOHANDLE/HANDLE e escopos relevantes; preservar desconhecimento onde não houver contrato preciso. | CALL-X2,T41 | W8 |

## D — EXTENDED PROFILE posterior, autoridade D-GC32/D-v1

Cada API D-v1 da página de perfis exige uma sublinha no catálogo da W10 com
assinatura/operandos/efeitos/outcomes/oráculo, após autorização posterior.
D07 tem somente teste negativo; seu guardrail anti-heurística vale também no core.
As linhas abaixo não são requisitos do DoD core W11.

| ID / família | Estado | Contrato / efeito | Oráculo | Wave |
| --- | --- | --- | --- | --- |
| D01 ASSIGN dinâmico/ambiente e remapeamento | PLANNED | Política versionada, contexto explícito; nenhum uso do ambiente real do analisador. | T31–T35 | W10 |
| D02 READ PREVIOUS, locking, SHARING, UNLOCK | PLANNED | Habilitar somente em perfis cuja autoridade admita a forma. | D-LOCK | W10 |
| D03 COMMIT/ROLLBACK e sincronização | PLANNED | Não inventar alvo literal; relacionar ao conjunto de recursos quando o contrato fornecer esse contexto. | D-TXN | W10 |
| D04 Report Writer: INITIATE/GENERATE/TERMINATE | PLANNED | Resolver relatório → arquivo e preservar escrita/controle gerados. | D-RW | W10 |
| D05 APIs com nome: OPEN/CREATE/DELETE/RENAME/COPY/CHECK | PLANNED | Extrair argumentos por assinatura documentada; origem/destino separados. | T44,T45 | W10 |
| D06 APIs com handle: READ/WRITE/CLOSE/FLUSH | PLANNED | Rastrear associação handle–abertura, aliases e desconhecimento. | T42,T43 | W10 |
| D07 Wrappers proprietários | OUT_OF_PROFILE: nenhum wrapper contratado | Somente contratos fornecidos e versionados; sem heurística por `FILE`, `OPEN` ou prefixo no nome. | T45 | W10 |
| D08 Streams/dispositivos e ACCEPT/DISPLAY | PLANNED | Classificar como recurso lógico/source-level apropriado; redirecionamento externo desconhecido não faz parte do produto. | D-STREAM | W10 |

## Regra de atualização e riscos

QUALIFIED requer caminho parser/fact/binding/transporte/lower/resultado nas
dimensões prometidas, com links de teste e commits. Parser-only não basta.
Outros efeitos/parcialidades podem permanecer intraprograma, com motivo preciso.
Semântica N+C obrigatória ainda UNSUPPORTED impede encerrar o core em W11.
D adiada não torna o core PARTIAL; sua qualificação será separada após W10, sem
alegar cobertura D no resultado N+C. Inputs D continuam com limites de perfil explícitos.

Riscos críticos e oráculos: record confundido com arquivo (T05/T06), alias fundido
com recurso (T16), CALL incorreto após READ (T18–T20), handler perdido (T11/T21),
participante SORT ausente (T23), conexão reavaliada (T31/T32), owner errado (T46),
reader tolerante demais (T51), scope externo (SG1–SG5).

## Checkpoint dimensional W2

N19–N25: sintaxe, binding, owner, opções e operandos QUALIFIED; N32/N33:
estrutura dos handlers e composição QUALIFIED. Efeitos/outcomes permanecem
PLANNED W3/W4, portanto as linhas agregadas não são promovidas integralmente.
[Oráculos, pins e resultados W2](w2-implementation.md): frontend12 novos,
F-DECL147, três FASTs, admission bilateral, 14 fontes duas vezes. N04/N18 e D
permanecem exclusivamente W10; não foram implementados.

## Checkpoint dimensional W3

N07/N09/N11/N15: storage/áreas e memória condicional QUALIFIED nos limites
explicitados em [W3](w3-implementation.md), com pins em [estado](state.md).
T12–T20: oráculos de EOF/INTO variável/alias/cauda/MAY/MUST e CALL disjunto PASS;
O1–O5 AIR manual PASS. N13 LINAGE permanece W6; seleção/status/handlers N31–N33
permanece W4. Não promover linhas agregadas antes dessas dimensões.
E-SELECTED20 fontes ×2 PASS; efeitos não comprovados permanecem MAY/unknown local.


## Checkpoint dimensional W4

N07/N21/N31–N33: status/outcomes, handlers e USE QUALIFIED nos limites de [W4](w4-implementation.md).
T10/T11/T21/T22: READ chaveada/NEXT, erro explícito/USE por arquivo/modo, IF/EVALUATE,
período, I/O em handler/USE e saídas GO TO/GOBACK PASS. N13 EOP/NOT EOP separado de
EOF QUALIFIED; LINAGE/contadores completos continuam W6. O1–O5 + dois AIRs de
controle manual PASS; E-SELECTED28 fontes×2 PASS. Retorno compartilhado tem
sobreaproximação de contexto declarada; modo corrente não inferido lexicalmente.
GLOBAL ancestral/captures W9. W10 permanece PLANNED/NOT_AUTHORIZED.

## Checkpoint dimensional W7

A4/T37 em AIR manual QUALIFIED: timing BEFORE, literal, conjunto fechado,
parcial+remainder, unknown, ciclos, aliases/refmod, supports e sharing CALL/FILE.
B-AIR/reader2.2, FAST e Q-SHARED PASS; [pins/gates](w7-implementation.md).
C01–C07 continuam PLANNED até W8 integrar o catálogo C-FC desde o fonte. Não se
promove parser/profile CICS por teste manual; ASSIGN DYNAMIC/N04 segue W10.

## Checkpoint dimensional W8

C01–C07/T36–T41 QUALIFIED_LOCAL:12 comandos, contextos SYSID/REQID, output SPI,
efeitos/controle e coexistência CALL;38 fontes CICS×2 +33 nativas×2 determinísticas.
T37 integra candidatos/joins/ciclo/alias/refmod/timing e desconhecimento no modelo.
Cobertura fonte PRIMARY_ONLY/PARTIAL mantém remainder efetivo; fechado no modelo
não é alegado como fechamento do fonte. Contraprova AIR COMPLETE/PARTIAL PASS.
Oráculos completos manuais preservam os quatro estados; [gates/limites W8](w8-implementation.md).
Sem lookup externo, DSNAME target ou ASSIGN DYNAMIC. W9/W11 pendentes.
