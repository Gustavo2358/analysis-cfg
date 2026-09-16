# Catálogo de testes — intenção, não execução

Todos os casos são **PLANNED / NOT_RUN para FILE** em H4. Expected deve ser escrito
a partir da regra e fixture autoral antes do produtor. Não usar JSON produzido como
único oráculo. Localização: frontend `src/test/...`, lower `core/adapters/src/test/...`,
AIR `air-model/air-json/src/test/...`, CFG `analysis-{values,dependencies,adapters}/src/test/...`.
COBOL E2E novo ficará em `analysis-adapters/src/test/resources/file-dependencies/` na W1.

Níveis: G1 focal → G2 família; G3 transporte; G4 CLI selecionado; G5 FAST fixo;
G6 qualification-local/corpus nos marcos da [estratégia](verification.md).
T focal por padrão; testes de alias/efeitos/handlers/dinâmicos/escopo ganham G4 no
checkpoint. T51 é G3; T52/T53 G3+G4; T54 G6 final após witness pequeno G1.

## T01–T54 — busca por ID ou família

| Caso | Situação / classe | Oráculo semântico independente | Dona |
| --- | --- | --- | --- |
| T01 | SELECT/FD sem statement | Declaração, sem leitura/escrita inventada. | W0 |
| T02 | SELECT e FD correspondentes | Um conector; duas origens conservadas. | W0 |
| T03 | FD sem SELECT/uso sem declaração | Diagnóstico/lacuna localizada, não binding inventado. | W0 |
| T04 | Dois FD com record-name homônimo | Resolução por qualificação/escopo; ambiguidade explícita. | W0 |
| T05 | WRITE REC FROM WS | Arquivo proprietário de REC, não WS. | W2 |
| T06 | FROM utiliza registro de outro arquivo | Nenhuma leitura desse outro arquivo inferida só pelo MOVE de buffer. | W2 |
| T07 | OPEN de vários arquivos com modos diferentes | Modo correto por arquivo; todos os sites/papéis conservados. | W2 |
| T08 | Apenas CLOSE/START | Uso presente, sem leitura de conteúdo fictícia. | W2 |
| T09 | DELETE RECORD | Exclusão de registro; não inferir qualquer operação sobre DSNAME/dataset fora do fonte. | W2 |
| T10 | READ chaveada com invalid key | Branch/efeitos/status coerentes. | W4 |
| T11 | READ NEXT AT END com CALL no handler | CALL e arquivo conservados; handler não incondicional. | W4 |
| T12 | READ INTO e EOF | Cópia não ocorre no caminho em que a regra a impede. | W3 |
| T13 | INTO indexado por campo afetado pela leitura | Avaliação na ordem semântica correta. | W3 |
| T14 | FROM/INTO com alias parcial | Sem assumir independência; efeito localizado. | W3 |
| T15 | Dois layouts do mesmo FD | Um arquivo; buffer compartilhado quando previsto. | W3 |
| T16 | SAME RECORD AREA entre arquivos | Aliasing de memória sem fusão dos recursos. | W3 |
| T17 | Registro variável maior/menor e cauda | Sem kill forte sobre intervalo não provado. | W3 |
| T18 | CALL por campo do registro após READ | Nenhuma precisão antiga indevida. | W3 |
| T19 | CALL por variável fora do buffer afetado | Evidência independente preservada. | W3 |
| T20 | Efeito MAY/UNKNOWN/MUST | Três comportamentos distintos; nenhuma equivalência acidental. | W3 |
| T21 | USE por arquivo versus por modo | Associação e precedência segundo contrato do perfil. | W4 |
| T22 | Saída não trivial de handler | Controle conservado ou gap de controle explícito. | W4 |
| T23 | SORT USING/GIVING sem READ/WRITE | Dependências de entrada/saída completas. | W5 |
| T24 | SORT com INPUT/OUTPUT PROCEDURE | Procedimentos locais; sem CALL externo fictício. | W5 |
| T25 | MERGE com múltiplas entradas | Todas as entradas e saída; restrições da forma. | W5 |
| T26 | RELEASE record FROM buffer | Recurso SD correto e ordem de transferência. | W5 |
| T27 | RETURN sort-file versus GOBACK/CICS RETURN | Sem colisão de comandos. | W5 |
| T28 | SORT de tabela | Não inventar arquivo. | W5 |
| T29 | RERUN ON sem EVERY | Parse/perfil e recurso de checkpoint tratados. | W6 |
| T30 | Assignment-name IBM com hífens | Normalização somente pela regra do perfil. | W0/W6 |
| T31 | ASSIGN dinâmico, alteração após OPEN | Binding da conexão não acompanha automaticamente a variável. | W7 |
| T32 | CLOSE e nova abertura com outro nome | Nova associação diferenciada. | W7 |
| T33 | Duas aberturas alternativas antes do mesmo READ | Candidatos e resto abertos quando necessário. | W7 |
| T34 | Path literal com case/espaços | Não aplicar normalização de programas ou DD. | W7 |
| T35 | Campo dinâmico sem valor conhecido | Site conservado com alvo aberto. | W7 |
| T36 | CICS FILE literal sem SELECT/FD | Dependência CICS correta. | W8 |
| T37 | CICS FILE variável, REDEFINES/refmod | Query no ponto e área corretos. | W8 |
| T38 | CICS SYSID distintos | Identidades/contextos não fundidos indevidamente. | W8 |
| T39 | CICS INTO versus SET | Memória e ponteiro tratados distintamente. | W8 |
| T40 | INQUIRE com operandos de saída | Saída não confundida com valor-alvo anterior ao comando. | W8 |
| T41 | READQ TS/TD e journal | Sem classificação automática como arquivo nativo/CICS FILE. | W8 |
| T42 | API OPEN/READ/CLOSE com handle copiado | Associação rastreável, sem usar o handle como filename. | W10 |
| T43 | Handle sobrescrito por chamada desconhecida | Resto aberto/lacuna; não reusar associação antiga como exata. | W10 |
| T44 | API RENAME/COPY | Origem e destino separados; CALL conservado. | W10 |
| T45 | Wrapper com nome FILE sem assinatura | Não produzir dependência por adivinhação. | W10 |
| T46 | Pai e filho com conector homônimo | Owners distintos; GLOBAL materializado quando aplicável. | W0/W9 |
| T47 | COPY de FD com REPLACING | Nome final e proveniência de expansão coerentes. | W0 |
| T48 | Programa com construção fora do CFG | Referências conhecidas sobrevivem; controle/inventário parcial explícito. | W1/W9 |
| T49 | Uso inalcançável no modelo | Presente no inventário; não aparece como aresta alcançável afirmada. | W1 |
| T50 | Comentários/literais com palavras de I/O | Nenhum falso positivo. | W2 |
| T51 | Campo novo removido no transporte | Teste contratual detecta perda; round-trip isolado não basta. | W0/W1 |
| T52 | Mesmo input duas vezes | Resultado determinístico dentro da mesma revisão/contexto. | W1/W11 |
| T53 | Falha de saída com arquivo antigo no destino | Exit/status determina falha; arquivo antigo não vira resultado novo. | W1/W11 |
| T54 | Muitas ocorrências de cada forma | Não existe cutoff semântico por quantidade. | W2 |

## Regressão transversal CALL-X (W1 em diante)

Obrigatória quando se alteram efeitos, CFG, dataflow ou serialization para FILE.
Comparar candidatos brutos/interpretados, support produtor/origem/premissas, ponto,
remainders e alcance. Correção de efeito de I/O pode alterar CALL; exigir prova e
expected independente. CALL-only deve conservar a projeção semântica.

| Caso | Witness / obrigação |
| --- | --- |
| CALL-X1 | MOVE PROGA em campo do record; READ; CALL campo: nada de singleton antigo indevido; CALL disjunto conserva suporte |
| CALL-X2 | CALL nos AT END/INVALID KEY/USE e I/O no handler: inventário presente, controle condicional, sem visita dupla |
| CALL-X3 | READ INTO e alias parcial/refmod: afeta somente área/outcome provados; MAY/UNKNOWN/MUST distintos |
| CALL-X4 | WRITE REC FROM campo de outro record também usado por CALL: não inventa READ do outro arquivo nem apaga evidência disjunta |
| CALL-X5 | IF/EVALUATE guiado por status/branches I/O define nome de CALL: conjunto + remainder corresponde aos caminhos; não linearizar |
| CALL-X6 | Mesmo input CALL literal/computado e FILE; API conhecida produz ambos; erro em FILE não apaga CALL independente |

## Scope guards SG1–SG5 (G1/G3 + G4 W1/W11)

| Caso | Negativo arquitetural / oracle |
| --- | --- |
| SG1 | SELECT F ASSIGN DD: alvo DD exato, sem motivo PARTIAL/UNKNOWN relacionado à falta de DSNAME |
| SG2 | ASSIGN path literal: instrumentar fronteira de acesso para provar zero open/stat/canonicalização do arquivo de negócio; leitura de fonte autorizada separada |
| SG3 | Mesmos fontes analisados sem JCL/variando ambiente do processo: mesmas identidades/candidates; nenhuma consulta externa por resolução FILE |
| SG4 | Reader/schema não contém DSNAME/physicalResource/physicalResolution/runtimeAllocation/JCL resolution state nem substitutos equivalentes |
| SG5 | Dinâmico de input: conhecidos + remainder ou unknown; não consultar ambiente para fechar conjunto |

## Casos adicionais de perfil D

D-LOCK: variantes lock/SHARING/PREVIOUS/UNLOCK com target e efeitos/controle
segundo perfil, mais forma fora do perfil como negativo. D-TXN: COMMIT/ROLLBACK
sem filename inventado; associação apenas se provada. D-RW: REPORT/RD e
INITIATE/GENERATE/TERMINATE ligam relatório ao arquivo com escrita/controle
derivados, não a programa. D-STREAM: identidade lógica de dispositivo no fonte,
sem resolver redirecionamento. Cada um exige positivo/negativo e fonte D-GC32.

## Metamórficos e mutação (qualificação, não todo checkpoint)

| ID | Transformação com pré-condição | Propriedade |
| --- | --- | --- |
| MR1 | rename conector/record e refs sem colisão | alvos/roles iguais; IDs locais podem mudar |
| MR2 | formatação/case COBOL válido | projeção semântica igual, não exigir IDs longitudinais |
| MR3 | COPY equivalente | targets iguais, provenance/include site muda corretamente |
| MR4 | duplicar uso alcançável | site novo, edge agregado sem duplicação indevida |
| MR5 | atribuição comprovadamente disjunta | evidence/candidates relevantes preservados |
| MR6 | jump/bloco equivalente | alcance/usos iguais sob mapeamento explícito |
| MR7 | WRITE FROM ↔ transferência equivalente, somente com prova alias/ordem | mesmo arquivo/efeitos |
| MR8 | renomear literal CICS vs só conector | só mudança de target altera identidade source-level |
| MR9 | adicionar construção desconhecida sem remover trecho conhecido | conhecido permanece; completude pode cair |

Mutantes focais: remover record-owner/participante SORT; FD↔SD; MAY→MUST; INTO
incondicional; apagar AT END; ASSIGN IBM→variável; query no READ em vez OPEN;
normalização CALL em path; fundir owners; eliminar site no CFG parcial. Executar
junto ao grupo sensível e W11. Sobrevivente exige investigação, nunca rótulo
equivalente automático. Não criar campanha full de mutação por edição.
