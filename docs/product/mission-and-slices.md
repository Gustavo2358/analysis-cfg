# Missão e entrega por slices

## Objetivo

Entregar uma aplicação independente que consome **Analysis IR** e produz um CFG
utilizável por análises futuras. Não criar analisador COBOL, lowerer, dataflow ou
visualizador como núcleo. Java 17/Maven permite futura integração como monólito
modular; a definição física dos módulos precede Java.

## MVP-CFG-01: fluxo linear e IF/ELSE

Domínio positivo deliberadamente estreito: uma publicação válida, units/entries
explicitadas; sequences com operações comuns; `jump`, `branch` e saídas
`return`/`halt`. `unknown(known(bool))` como predicado puro conserva os dois destinos;
`unknown_type` não satisfaz a assinatura booleana. IFs aninhados
são combinações das mesmas primitives, sem limite artificial de cardinalidade.
O primeiro MVP **não precisa de `invoke`, `dispatch`, controle aberto, local ou
indireto** para provar a arquitetura. Não inventar estado de dados para desenhar o
grafo.

Aceitação: fixture em arquivo → decode → mesma porta usada por fixture em memória →
CFG com nós, transições rotuladas, pontos/origens e relatório de suporte. O diamond
reconverge só onde o terminador diz; ramo que termina não ganha join artificial.
Isso prova a fronteira IR→CFG e a troca de adapter antes de aumentar a semântica.

## Slice seguinte: invocações e outcomes

Depois do primeiro MVP, `invoke` entra como terminador com outcomes materializados
(normal, excepcional, halt, diverge e restante aberto quando sustentado). Esse slice
não resolve programa chamado nem afirma ausência de efeitos; ele amplia a projeção
de controle sem reabrir o produtor ou alterar a porta.

Antes de anunciar o MVP, falhas de integridade e capabilities fora do slice têm
resposta explícita. Rejeitar o escopo não suportado é aceitável; ignorá-lo não.
A fixture fechada tem premissas de controle escritas, não contratos inventados com
base em CALL supostamente “normal”.

**MVP-CFG-01 não é um perfil normativo IR.** É um marco local de implementação.
Não declarar `AIR-STRUCTURE@2/PRECISE_FOR_PROFILE` até cumprir todas as obrigações
estruturais dos oráculos daquele perfil.

## Evolução planejada

Depois do MVP: `dispatch`, ciclos e múltiplas entradas; envelopes abertos e extensões;
controle local com frames/ports/retornos; controle indireto limitado; integração
Maven em memória. Cada etapa tem evidência independente de COBOL.

Motivadores upstream: EVALUATE pode virar dispatch ou branches ordenados; GO TO e
NEXT SENTENCE viram jump quando seus destinos forem estabelecidos pelo produtor;
PERFORM/THRU usam controle local se a disciplina de conclusão for compatível.
Esses nomes orientam testes de aceitação bilateral, não classes do CFG.

Dataflow, reaching definitions, storage analysis, dependências calculadas, dominância
e coalescing ficam adiados. Preservar seus insumos não significa implementá-los.
O objetivo é fechar produto observável cedo; não terminar horizontalmente a V2 inteira.
Não há promessa de data de conclusão ou estimativa inferida da contagem de classes.

Fontes: [contrato e estado upstream](../sources/index.md); execução: [backlog](../work/backlog.md).
