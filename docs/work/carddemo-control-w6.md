# CARDDEMO-CONTROL-W6 — sentenças, busca e terminações

Status: DONE — implementação deste repositório mergeada em 28/09/2026; [fechamento da integração](carddemo-control-integration.md).

Escopo W6. Recursão/ALTER executável e UI permanecem fora.

Os checkpoints abaixo registram a qualificação da onda na data em que foi executada. Seus estados de revisão e paradas são históricos; o estado vigente está no [fechamento da integração](carddemo-control-integration.md).

## Regra e desenho antes do código

NEXT SENTENCE transfere para o ponto imediatamente após o próximo período separador, não para o statement após END-IF/END-SEARCH. A AST já possui Sentence e NextSentenceStatement. Criar uma região SENTENCE somente quando existe essa transferência na sentença; sua conclusão aponta para a sentença seguinte ou para a conclusão do paragraph/section. ESCAPE da sentença abandona ativações inline internas; a conclusão do paragraph continua ligada ao PERFORM correto. Não reinterpretar texto ou posição no lower.

SEARCH ALL possui uma decisão abstrata de busca, com alternativas mutuamente exclusivas WHEN e AT END (ou continuação na ausência de AT END). A iteração interna que calcula o índice fica resumida nessa operação; o corpo WHEN não é corpo de iteração e executa somente no match. Nenhuma comparação/ordenação/índice runtime é avaliada. Admitir a forma gramatical ALL sem VARYING com um WHEN; preservar efeitos desconhecidos de índice e possíveis falhas, sem MUST. Região SEARCH/SEARCH_ARM compõe corpos e continuidade com os mesmos contratos regionais.

STOP RUN deve produzir PROGRAM_HALT → AIR Opaque com HaltAlternative e controle fechado, sem continuação ordinária nem retorno de PERFORM. EXIT PROGRAM retorna em programa contido. Em unidade externa, fonte/posição na compilação não prova se ela é main ou chamada: publicar alternativas de retorno e continuação com premissa contextual explícita. Nenhum perfil vigente informa o papel runtime; não inferir main do primeiro PROGRAM-ID. GOBACK/RETURN/XCTL mantêm contratos existentes.

ENTRY alternativo é auditado contra a entrada principal já publicada. Não adicionar entrada artificial nem conectar trecho morto depois de término. Multi-entry exige outro desenho. Caso o ENTRY seja encontrado no fluxo sequencial, sua natureza declarativa permite continuar, sem materializar chamada externa.

SP2.56: regiões SENTENCE/SEARCH/SEARCH_ARM, escape SENTENCE, PROGRAM_HALT. Porta tipada/wire verificam forma, escopo e versão. Lower consome destinos/provas e representa halt pela alternativa AIR existente. Sem alteração de AIR/CFG contratos. Algoritmo finito linear em nós/arestas da AST; não desenrolar tabela, banco ou recursão.

## Oráculos RED

NEXT SENTENCE em IF/EVALUATE/SEARCH/inline PERFORM; pular irmãos até período, concluir paragraph/range correto, dois chamadores sem retorno cruzado. SEARCH match/miss, AT END ausente, corpo com transferência/terminal, índice desconhecido e continuidade. STOP RUN/GOBACK não alcançam instrução seguinte; EXIT PROGRAM externo mantém alternativa main, contido só retorna. ENTRY sem chamador permanece ausente da execução principal. Contratos antigos, versão futura rejeitada, escopo inválido, roundtrip AIR/CFG/dependencies, supports preservados.

## Autoridades IBM verificadas

- [SEARCH e NEXT SENTENCE](https://www.ibm.com/docs/en/cobol-zos/6.4.0?topic=statements-search-statement).
- [Referência COBOL 6.4, IF/NEXT SENTENCE](https://publibfp.dhe.ibm.com/epubs/pdf/igy6lr40.pdf).
- [Papel main/chamado depende de execução](https://www.ibm.com/docs/en/cobol-zos/6.4.0?topic=subprograms-main-programs-calls).
- [Terminações](https://www.ibm.com/docs/en/cobol-zos/6.3.0?topic=subprograms-ending-reentering-main-programs), [STOP](https://www.ibm.com/docs/en/cobol-zos/6.3.0?topic=statements-stop-statement).
- [ENTRY](https://www.ibm.com/docs/en/cobol-zos/6.4.0?topic=statements-entry-statement).

## Validação prevista

FAST dos repositórios alterados, corpus CardDemo 73 inteiro, PERFORM39/Chaos48/aliases14/PERFORM adversarial25, fixtures do frontend e contratos FILE/CICS aplicáveis. Comparar candidatos, supports e hipóteses, classificação das ausências legítimas e limites W7; nenhum delta inesperado aberto.

### Ajuste após sondagem do codec

A operação AIR Halt existe no modelo, mas está fora do perfil JSON vigente. O modelo já publica HaltAlternative em envelopes de controle. STOP RUN usa essa alternativa terminal fechada e preserva efeitos de finalização de memória/recursos como desconhecidos, sem MUST; não usa Return nem adiciona sucessor. Código de saída não é avaliado. SEARCH usa SEARCH_INDEX_MAY (READ/WRITE ALL, exposure NONE, ambiente NONE), pois o índice implícito ainda não tem célula provada.

### Oráculos históricos verificados no gate completo

O primeiro Maven completo encontrou três expectativas desatualizadas. O entrypoint `embeddedIdentifier : identifier EOF`, acrescentado em W4, já estava classificado no manifesto versionado: os dois testes de inventário conservaram as comparações exatas de conjuntos e agora também exigem a classificação dessa regra (601 COBOL + 31 preprocessing). O teste FILE confundia memória NO_OP de EXIT PROGRAM com controle sequencial; agora exige separadamente NO_OP e os outcomes NORMAL/PROGRAM_RETURN da unidade externa. Não houve mudança de produção para satisfazer esses testes. O rerun Maven passou: 1.220 testes, zero falhas/erros, um skip histórico.

## Escopo deste consumer

A produção de analysis-cfg mudou somente em W1, para consumir as premissas de possibilidade de controle na evidência qualificada e publicar candidatos condicionais. W2–W6 exigem somente repin/documentação; nenhum reconhecimento de nomes COBOL/CICS/SQL foi acrescentado ao CFG ou solver. O FAST final passou; pins/documentação foram conferidos após a qualificação.

## Checkpoint qualificado

Implementação W6 concluída e qualificada; status IN_PROGRESS até revisão/merge. Resultado integrado, deltas e limites em [W1–W6](carddemo-control-w1-w6.md). Parada antes de W7.
