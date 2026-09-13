# Valores regionais — desenho ST-W3–ST-W5

Status: desenho registrado antes do código e fundação implementada em ST-W3.3,
WORK-STORAGE-CFG-001. W2/M0 está qualificado; este documento não declara M1/M2/M3. A campanha autoriza o trabalho
até W5 e mantém revisão humana para o final, sem merge.

## Autoridade e premissas

AIR 2.0.0 no source lock: memória, operações, incompletude e consumidores;
extensão opcional IBM1047 já qualificada em W1. StorageIndex, StatementEffects e
StoragePartition de W2 são a interpretação física compartilhada. O solver e o
batch/replay existentes governam pontos, contextos e estabilização. Esses fatos
são IR_GUARANTEED ou ARCHITECTURE_GUARANTEED, conforme sua origem. Extents,
codecs, visibilidade e disjunção publicados continuam EXPLICIT_CONTRACT:
nenhuma independência é inferida de IDs. Não há fato COBOL neste domínio.

Como base metodológica, a revisão de
[Cousot, Abstract Interpretation, ACM Computing Surveys 28(2), 1996](https://www.di.ens.fr/~cousot/COUSOTpapers/publications.www/Cousot-ACM-Computing-Surveys-v28-n2-p324-328-1996.pdf),
lida integralmente, distingue semântica concreta, aproximação e ponto fixo
monotônico. Usamos essa obrigação de soundness para definir a abstração abaixo;
o artigo não é autoridade da representação AIR nem prova desta implementação.
A [apresentação do autor, §§3.2–3.4](https://www.di.ens.fr/~cousot/AI/)
também distingue domínios finitos, semântica coletora e enriquecimento disjuntivo.

## Abstração escolhida

Por base física e Entry/run, manter alternativas de imagens imutáveis da memória,
com fragmentos conhecidos e desconhecidos. Não manter árvores de caminhos.
Uma imagem representa uma combinação de fragmentos no mesmo estado abstrato;
join une imagens completas, preservando correlações dentro da base. Fragmentos
de duas imagens de branches diferentes não são reunidos silenciosamente como
se pertencessem a uma execução. Unknown é um fragmento explícito; ausência de
base em estado alcançado tem imagem totalmente desconhecida. Bottom é somente
o ponto inalcançado. Células lógicas continuam distintas de regiões de bytes.

Fragmentos conhecidos referenciam payload imutável, intervalo de contribuição
e supports. Byte strings provêm de literais AIR ou codificação explicitamente
interpretada. O código não usa Charset default nem converte bytes de fonte.
Uma view textual é decodificada com seu codec após recortar a imagem da base.
Bytes compartilhados não implicam igualdade de interpretação entre views.

Supports distinguem produtor literal/condição inicial, operação de cópia,
intervalo original contribuinte e intervalo observado. Identidades de eventos
são finitas por Entry/operação/outcome/slot; não se encadeiam árvores de cópias.
Sets de supports fazem parte da ordem e da igualdade do domínio, para que uma
mudança de evidência também se propague até estabilizar.

W3 introduz a fundação e a projeção de views totalmente cobertas por uma faixa
contígua de um único produtor literal, inclusive após cópia integral. O motor
preserva os demais fragmentos, mas a projeção integral com vários produtores
ou offsets de contribuição não contíguos mantém `FRAGMENT_COMPOSITION_PENDING`.
Isso limita a precisão publicada sem perder o efeito nem inventar testemunho. W5 qualifica
composição de múltiplos produtores, mudanças parciais, captura e correlação;
o domínio inicial será estendido, sem outro solver ou motor por REDEFINES.

## Transferências e captura

Preparar planos por operação a partir de StatementEffects, fora do solver.
MUST substitui somente a faixa coberta em cada imagem; bytes restantes ficam
intactos. MAY conserva a imagem anterior e as imagens possíveis após a escrita.
Um destino alternativo não é convertido em vários MUST. Alias entre bases sem
disjunção provada introduz efeitos MAY conforme o resolver W2.

Assign literal de bytes, e Assign TEXT codificável no codec da view, produzem
fragmentos conhecidos. HavocMust instala desconhecimento somente no intervalo
provado. Valores/representações não interpretados conservam efeito e motivo.
Um desconhecido no prefixo não sustenta um candidato integral anterior, mas a
view do sufixo ainda pode ler bytes conhecidos. Unknown nunca vira conjunto
vazio fechado, tamanho zero, Nop ou escolha do primeiro candidato.

CopyBytes recorta a origem no estado anterior à escrita. Na mesma base, fonte e
destino são processados dentro da mesma imagem: self-copy e overlap AIR não
consultam a origem já alterada. Entre bases, a abstração por componente não
retém correlação global. Se a composição exige combinar partes de bases
distintas sem prova de compatibilidade, o resultado é uma combinação abstrata,
com remainder/classificação explícitos, nunca um testemunho concreto. Uma cópia
integral dispensa bytes antigos do destino; uma escrita literal integral pode
restabelecer conhecimento fechado no modelo.

Invoke separa leitura do target/argumentos dos efeitos por outcome. Transfer
de bloco não aplica antecipadamente efeitos externos; a aresta/outcome usa o
plano correspondente, como RD W2. Before, after e outcome usam o mesmo plano e
mesma execução no replay. Seeds de Entry são aplicados uma vez; loops e resumes
não reinicializam memória. Controle permanece o grafo modelado, sem avaliação
de condições por nomes nem construção de novo CFG.

## Ordem, finitude e custo

A ordem é inclusão de alternativas e supports, com presença de unknown como
possibilidade aberta. Join é união, associativa, comutativa e idempotente.
As transferências operam sobre cada alternativa e preservam essa ordem; MAY
inclui identidade, MUST substitui a projeção escrita de cada imagem. Ausência
alcançada participa do join como unknown, não como bottom.

No perfil de bytes de extent fixo, cada posição tem um alfabeto finito (octeto ou
unknown); posições, bases e eventos do programa são finitos. Supports usam
subconjuntos de eventos/intervalos finitos, sem comprimento de caminho. Assim o
domínio tem altura finita. Essa prova não exige enumerar todo o universo: roots
e fragmentos são materializados sob demanda. Extent desconhecido permanece
aberto; não cria uma enumeração infinita de posições nem um zero fictício.

O custo depende dos fragmentos e alternativas efetivamente produzidos e pode
crescer combinatoriamente; finitude não equivale a pequeno custo. Reutilizar
segmentos/payloads/roots imutáveis e registrar preparação, transferências,
joins, replay, alternativas e retenção. Não há cap K de candidatos, limite
de profundidade ou descarte de supports para estabilizar. Exaustão operacional
é falha explícita, sem resultado semântico truncado ou PASS inventado.

## Produto e compatibilidade

O perfil regional tem chave e produto explícitos, separados dos profiles
`scalar-text-direct@1` e `scalar-text-effects@1`, que mantêm suas recusas.
Uma região não é serializada como WHOLE_CELL nem no campo legado `cell`.
O consumidor de CALL recebe uma projeção comum de candidatos textuais,
supports e remainders; não executa análise, não interpreta layout e não
remove padding. Política de nome continua em CallNameInterpreter.

M1 usará registro explícito do provider regional na composição de dependências,
com batch por Entry. Unidades somente com CALL literal não demandam values.
Consultas públicas por faixa, detalhes intervalares e transporte de resultados
regionais serão fechados em W5, preservando o wire escalar existente. Gaps de
fonte e de modelo permanecem separados; strings de gap COBOL não são whitelist
de irrelevância em um consumidor language-agnostic.

## Oracles e critérios antes de qualificar

- AIR manual: escrita de grupo, view deslocada, aliases, IBM1047, codec distinto,
  partial unknown, MAY/MUST, zero AIR, huge/unknown extent, antes/depois/outcome.
- Porta em memória e arquivos reais do lower: grupo/FILLER/CALL, múltiplos sites,
  mistura Cells/regiões e comparação com o corpus escalar pinado.
- Leis de join/equivalência e monotonicidade, incluindo supports que mudam sem
  mudar texto; permutação de inventário e ordem de worklist.
- Oracle concreto finito independente para branches/loops: nenhuma possibilidade
  concreta pode desaparecer; combinação abstrata não pode alegar testemunho.
- W5: WXYZEFGH com duas contribuições RD, Y preserva ABCD após mudar X, self-copy,
  overlap, zero/uma/várias iterações e duas ativações do mesmo paragraph.
- Mutantes dirigidos e métricas sem extrapolação. Regressão W0..Wn e receipts
  continuam exigidos; desenho ou compilação isolados não qualificam milestones.

## Implementação e gates focais ST-W3.3

`RegionalValuesAnalysis` prepara os efeitos, eventos finitos e bases uma vez;
`ByteImage` compartilha payloads imutáveis e conserva offsets de contribuição.
`RegionalValuesProvider` é registrado somente na composição explícita de
CALL; `TextValueFact` é a projeção de leitura compartilhada. `ValueFact` mantém
seus campos/wire e `RegionalValueFact` identifica local físico e codec sem
usar o campo `cell`. O solver, o planejador e o replay não foram substituídos.

A admissão de condições iniciais reutiliza a verificação existente de RD;
condições simultâneas iguais preservam os dois suportes. Inicializadores
regionais sobrepostos continuam sujeitos ao `VALIDATION_LIMIT` da AIR. Esta
campanha não o converte em prova nem amplia VALUE/initializers de W7.

Os testes pequenos cobrem bytes independentes do codec, grupo deslocado,
MAY após Invoke, prefixo desconhecido e sufixo preservado, Cells junto de
regiões com e sem disjunção explícita, evidência em ciclos, duas condições
iniciais iguais, conflito recusado e captura de cópia integral. Um oracle
coletor de strings, independente do resolver e dos efeitos, compara 48 grafos
com branches/loops em 240 consultas. Finitude e exatidão desses cenários não
qualificam composição de fragmentos entre bases de W5.

Os inventários compilados incluem explicitamente o domínio e o novo provider.
O domínio pode depender do storage/effects e da admissão RD no kernel; os
providers continuam na fronteira de composição. Os checks de dependência,
Java 21, fontes exatas, javap/jdeps e ausência de I/O permanecem ativos.

## Qualificação de overlays ST-W4

O consumidor recebe SP 2.8.0 pelo lower pinado; a análise continua recebendo
somente AIR. Region/View preserva mesma base e offsets próprios, incluindo
FILLER e componentes de footprint máximo. O E2E de overlays reutiliza os
mesmos oracles físicos de grupos e adiciona goldens independentes para relações,
origens e precisão escalar localizada. Fixtures sem alvo comprovado ou com
footprint desconhecido conservam resultado aberto. Comparações A/B e permutações
incluem o inventário de relações SP; nenhum nome fonte governa RD/values.

O caso de escrita no prefixo consulta o sufixo e verifica sua definição antiga;
a consulta composta integral é qualificada em W5. Views com codecs distintos
continuam separadas no resolver e na interpretação, mesmo sobre a mesma faixa.
`StorageIndexTest` e `RegionalValuesTest` são controles pequenos dessa regra.

## Decisão de domínio ST-W5.1, antes da implementação

Estender a fundação W3 para um produto de conjuntos de stores conjuntos. Um
fator reúne bases ligadas por uma dependência de leitura→escrita interpretada,
obtida exclusivamente dos efeitos canônicos antes da análise (CopyBytes e Read
lógico). União transitiva dessas dependências é uma partição de correlação da
análise: não é prova de alias, alocação ou disjunção. Fatores singleton preservam
o caso barato. Cada store guarda uma imagem imutável por base do fator; join une
stores completos. A transferência lê um único store anterior, recorta a origem
e escreve somente o destino, mantendo os demais membros. A escolha descarta a
alternativa de simplesmente remover a guarda W3: o produto cartesiano X/Y após
um join pode fabricar combinações que nunca coexistiram no modelo.

StatementEffects publicará também a seleção do efeito: SINGLE_DESTINATION
significa uma Place avaliada, mesmo com vários candidatos; MAY_SET significa
escopo que pode atingir vários locais. A obrigatoriedade da ocorrência é
distinta da força de cada target. Para SINGLE_DESTINATION, cada alternativa
direta recebe uma escrita, e identidade permanece quando a ocorrência é MAY,
há remainder, o outcome é possível ou o destino pode estar fora do fator.
Efeitos indiretos sem disjunção provada continuam fracos e desconhecidos.
MAY_SET admite subconjuntos dos locais, sempre com conteúdo não interpretado
nos efeitos admitidos. RD continua fazendo união de definições possíveis e
nunca promove todos os candidatos a MUST. Não inferir essa distinção de nomes,
strings de gaps ou ausência de occurrence. Todos os reads de uma operação usam
seu mesmo store anterior; não há leitura tardia após atualizar o destino.

A ordem é inclusão de stores (incluindo metadados), o join é união e transfer
distribui sobre alternativas. Unknown alcançado é store explícito, bottom é
inalcançado. O universo é finito: bases, eventos, payloads literais e resultados
de codecs são finitos; offsets de payload/produtor ficam limitados ao payload,
crop avança ambos, shift muda somente a posição do destino. Unknown usa offsets
zero e conjuntos de motivos estáticos; cópias usam sets de eventos estáticos,
sem cadeia de ocorrências. Em extent E, fronteiras pertencem a [0,E]. Em extent
desconhecido, todas as escritas interpretadas têm limite constante finito B;
o sufixo desconhecido além de B nunca é refinado por deslocamentos ilimitados.
Isso dá imagens finitas, stores finitos e powersets de altura finita, embora
potencialmente grandes. Supports participam de igualdade e estabilização. Não
há K, poda de candidatos ou apagamento de supports para garantir terminação.

Essa construção aplica a distinção entre semântica coletora e solução por ponto
fixo da referência de Cousot acima (§§3.3–3.4, consultada novamente para W5).
A prova específica é nossa obrigação, não conclusão importada da referência.
O oracle independente executa strings/stores concretos finitos em branches e
loops; testes também cobrem leis, permutações e mudanças somente de suporte.
Complexidade depende de stores e fragmentos produzidos; registrar crescimento,
transferência e replay, e reportar exaustão como falha operacional. Os produtos
continuam sendo supports abstratos sem path witness. W5.3 fechará o transporte
de fragmentos, intervalos e eventos capturados; esta decisão não declara M3.

A implementação e o profile passam a `RegionalValues` versão `2`,
`regional-text-images@2`, precisão `FINITE_CORRELATED_STORAGE_IMAGES`.
`RegionalValueFact@1` conserva sua forma de projeção textual compatível; os
profiles escalares e o wire legado não mudam. A chave antiga não é aceita como
sinônimo silencioso do novo domínio. A remoção da guarda de composição W3 é
acompanhada por testes independentes de branch, Choice, cópia e finitude.
