# CARDDEMO-CONTROL-W7 — precondição de reentrada local

Status: IN_PROGRESS. Escopo autorizado: W7/W8, sem merge.

## Descoberta que revisa o plano

A referência IBM Enterprise COBOL6.4 Basic PERFORM proíbe que um PERFORM provoque sua própria reexecução e classifica o resultado como imprevisível. A performance guide6.3 desaconselha a mesma construção; não fornece contrato de retorno alternativo. Portanto o plano não pode prometer semântica executável de pilha para recursão real. A AIR05.7 oferece local.invoke, mas05.7.5 exige correspondência da semântica fonte. Acrescentar suporte dessa extensão não consertaria essa ausência de autoridade.

Fontes verificadas em28/09/2026: [Basic PERFORM](https://www.ibm.com/docs/en/cobol-zos/6.4.0?topic=statement-basic-perform), [Using PERFORM](https://www.ibm.com/docs/en/cobol-zos/6.3.0?topic=v6-using-perform). A autoridade continua IBM; outro dialeto exigiria contrato próprio.

## Representação proposta antes do código

SP2.57 adiciona `Binding.reentryPolicy`: UNSPECIFIED para fatos históricos; SOURCE_UNDEFINED para uma invocação cuja repetição com a mesma identidade ainda ativa não tem semântica definida pelo produtor. A política vale para a identidade do binding, não para grafia do paragraph, linha, programa ou simples repetição sequencial.

O produtor publica SOURCE_UNDEFINED nos PERFORMs que traduz. O lower detecta a identidade ativa pelo contexto já existente e publica o diagnóstico genérico `LOCAL_REENTRY_SOURCE_UNDEFINED`, em vez de atribuir o corte somente à falta de implementação de recursão. O envelope conserva controle/efeitos/dependências abertos, sem normal return, halt, diverge, MUST-write ou kill inventado. Contextos não recursivos e contratos históricos conservam a tradução anterior.

A evidência fonte continua um domínio de possibilidades condicionado: não é execução IBM comprovada e não pode conferir autoridade executável a um retorno após reentrada indefinida. Não eliminar candidatos porque a projeção para nessa fronteira. Nenhuma nova aresta será acrescentada apenas para conectar os grafos.

## Algoritmo e limites

A detecção reutiliza o conjunto de bindings ativos em LocalIds. Repetir uma invocação depois de sua conclusão não encontra essa identidade no pai e permanece válido. Cada caminho de expansão admite cada binding no máximo uma vez; o universo de caminhos é finito, mas a expansão pode ser exponencial em bindings distintos. Não é uma solução de capacidade por resumos/pushdown, nem será reportada como tal. Não impor cutoff numérico silencioso. W8 mede esse custo e mantém a limitação explícita.

Isso revisa W7.1–W7.4: recursão real passa a ser uma precondição de linguagem explicitamente violada/possível, sem fingir implementação de retornos não definidos. Uma eventual expansão a dialeto recursivo e redução de contextos exige trabalho separado. W8 pode qualificar a preservação atual; não pode declarar cumprido o critério original de retornos recursivos.

## Oráculos antes da implementação

RED: publicação da política tipada/wire; versão nova; política ausente histórica; valores inválidos/downgrade rejeitados. Casos reais do produtor: recursão direta, mútua, condicional, dois chamadores, ranges/SECTION, handlers, repetição sequencial válida e término anterior à reentrada. Nenhum diagnóstico em repetição sequencial nem retorno cruzado. Fonte calculada/literal e FILE preservados na incerteza; GOBACK/STOP não ganham continuação. Comparação W6→W7 por candidatos/supports/provenance e topologia, seguida dos gates e W8.

## Refinamento confirmado por RED

O frontier antigo usa o footprint da operação PERFORM isolada e pode declarar memória/dependências vazias. Isso não é autoridade para os efeitos da reentrada indefinida. O teste `undefined source cannot close memory or prove kills` falhou antes do ajuste. SOURCE_UNDEFINED conserva leituras/escritas possíveis em toda a memória (incluindo ambiente), recursos abertos, zero MUST-overwrite e zero retorno conhecido. A política histórica UNSPECIFIED mantém a tradução anterior. Este delta de precisão exige nova qualificação dos produtos afetados; não é somente renomear o diagnóstico.

## Preservação da continuação fonte — desenho anterior ao ajuste

O teste de recursão incondicional expôs uma segunda fronteira: a qualificação fonte por menor ponto fixo não produz summary de retorno e deixa vazio o candidato escrito depois do PERFORM. Recursão com semântica indefinida não prova não retorno. O CFG continuará sem sucessor conhecido; a evidência fonte deve preservar a continuação como CONTROL_POSSIBILITY.

O tabulador fonte mantém grafo finito de dependências entre contextos de invocação (binding + estado abstrato + ingresso de handler). Quando uma aresta fecha ciclo, a política SOURCE_UNDEFINED autoriza uma hipótese de conclusão com estado de handler desconhecido. A hipótese entra como summary do callee e usa os subscribers e callerPremise existentes para associar cada continuação ao seu chamador. Nenhuma ligação global entre todos os retornos/callers; o domínio é de possibilidades, não pilha IBM executável. A prova derivada referencia o binding publicado e a ocorrência, com rule undefined-active-reentry-may-complete e kind CONTROL_POSSIBILITY. O consumer transporta a nova causa de incerteza sob versão própria.

Terminação: número finito de bindings, estados de handler e ingressos; cada contexto/ponto/support entra uma única vez. A detecção de ciclos usa alcançabilidade do grafo finito, sem enumerar profundidades nem impor cutoff. O materializador AIR permanece separado, com sua limitação exponencial. A hipótese não fecha memória, não concede MUST e não pode ser promovida a reachability executável. Oráculos RED: candidato AFTERP sobrevive à recursão direta/mútua, com support condicional; término anterior ao ciclo permanece negativo; ordem de worklist não muda resultado; loops sequenciais não geram hipótese.
