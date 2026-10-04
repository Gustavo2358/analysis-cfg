# Valores textuais das conversões numéricas

Status: IN_PROGRESS. Escopo: conservar a estrutura dos valores de fit_text e
slice_text sem alocar as posições implícitas da PICTURE.

A regra é a sequência de escalares Unicode da AIR 02: fitting preserva o prefixo,
preenche com o escalar declarado e slicing seleciona posições. Uma representação
canônica por runs consecutivos de caracteres iguais conserva exatamente essa
sequência. O valor desconhecido usa run próprio, nunca espaço ou string vazia.
Concatenação funde apenas a fronteira de runs iguais; slicing visita os runs
sobrepostos; fitting acrescenta um run de padding. Igualdade e hash usam a forma
canônica. O custo depende dos runs presentes, sem enumerar a extensão implícita.
A saída textual, quando solicitada integralmente, continua tendo custo proporcional
aos caracteres efetivamente publicados. Não há orçamento semântico nem corte de
candidatos nesta mudança.

Oracles: desconhecido com um bilhão de posições permite projetar um campo conhecido
sem materializar o restante; PROGA ajustado para um bilhão conserva PROGA seguido
de espaços, verificados por slices pequenos. Exemplos Unicode e todos os pequenos
intervalos são comparados com um vetor denso independente. A representação comprimida não altera a topologia do CFG; as expressões AIR são as publicadas pelo contrato coordenado nesta prioridade.

A métrica do pool passa de unicodeScalarsHashed para textRunsHashed: o hash agora
visita runs canônicos e não posições expandidas. Um literal com mil ou um bilhão
de repetições do mesmo caractere possui um run; textos alternados conservam um
run por mudança de caractere. Nenhum orçamento de análise foi introduzido.

O teste RED da representação densa falhou por falta de heap com 256 MB. A versão
com runs passou com o mesmo limite. O E2E COBOL HUGE-CALL preenche PIC X(1000000000)
com PROGA, copia (1:8) para PIC X(8) e faz CALL: quatro etapas PASS e candidato
PROGA, com análise COMPLETE no fixture. Evidência bruta em priority2-evidence/
huge-fitting-call-proof. Isso não altera o estado PARTIAL do corpus CardDemo.

O caminho regional usa a mesma representação comprimida. A conversão explícita
para IBM1047 codifica uma vez cada run e mantém spans de bytes; caracteres sem
codificação exata, tamanho divergente e codec não provado permanecem abertos.
O oracle verifica C1 seguido de 40 para A + espaços, inclusive offsets de
provenance no final de um bilhão de posições. Não há inferência de CCSID.

O predicado is_digits compartilha a representação comprimida. A sequência vazia,
qualquer caractere conhecido fora de 0–9 e os ramos com conteúdo desconhecido
mantêm resultados distintos. O teste de um bilhão de zeros visita um run.
Os oracles em dataflow escalar e regional verificam escolha de ramo, sinais,
espaços, caracteres Unicode e a união de alternativas quando o input é aberto.
parse_integer conserva leituras de texto e do fallback explícito nos efeitos;
este kernel não promete avaliação de valores numéricos pelo domínio textual.

FillText usa a mesma forma por runs: um escalar arbitrário repetido não aloca
a extensão declarada. O predicado de dígitos conserva unknown; conhecido +
unknown + fatia conhecida mantém os caracteres independentes. Oracles com
um bilhão de posições e Unicode passaram com 256 MB. O avaliador regional
ainda não promete fechar todas as expressões Slice/Concat/Fill; a representação
e a codificação dos valores que ele já admite continuam comprimidas.

## Regressão de custo nos exits das conversões

O caso selecionado COTRN02C passou a exceder 300 s na saturação estrutural, em
`Partition.add`: para uma partição única com estado equivalente à contribuição,
a diferença C \ P é calculada antes da união P ∪ C. A diferença é descartada
imediatamente porque ambas as partes transportam o mesmo estado. Um experimento
isolado que usa diretamente a união terminou em 96,97 s, com os mesmos bounds e
sem alterar a AIR. O experimento é diagnóstico, não o gate final de produto.

Regra: se a partição contém somente (P, v) e a contribuição é (C, w), com v ≡ w,
a nova partição é (P ∪ C, v). Isso decorre da idempotência do join e da equivalência
do domínio. Se os estados diferem, mantém-se a partição ponto a ponto existente.
A mudança elimina um BDD intermediário e reutiliza o mesmo solver, suas condições
e seus retornos casados. Não enumera paths, não aproxima condições e não muda
limites de memória/controle. O custo continua dependendo do BDD efetivo; não há
claim de limite polinomial universal para todo programa.

Oracle: comparação de IN/OUT com pilhas explícitas nos casos gerados de guards,
ports, routes e unwinds; caso de diamantes com exits compartilhados, contribuições
iguais e caminhos alternativos; escala e reexecução do caso selecionado. O timeout
preservado da execução anterior é a evidência RED da regressão observada.

## Preservação de ASCII

O FAST detectou perda de PROGA na cópia lógica→física de LogicalEntryWireTest:
a codificação por runs admitia somente IBM1047, embora o caminho anterior também
admitisse ASCII explícito. Ambos são codecs de um byte por escalar representável.
A mesma rotina comprimida deve aceitar ambos, consultar MemoryCodecs por run e
recusar escalares não representáveis. Nenhuma codificação implícita é permitida.
Oracle: A + padding em um bilhão de posições usa 41/20 em ASCII e C1/40 em IBM1047;
o teste integrado existente conserva candidato, captura lógica e overwrite.

## Cache limitado das operações booleanas

A qualificação detectou 78,72/57,18 s nas duas variantes COTRN02C, contra
36,76/30,38 s na wave10. Um experimento isolado com a mesma AIR e a mesma análise,
alterando apenas o cache direto de 16.384 para 65.536 slots, concluiu o primeiro
caso em 18,55 s e produziu dependencies JSON integralmente idêntico. O cache de
quatro vetores int passa de 256 KiB para 1 MiB por manager. É memória fixa;
colisões continuam descartando somente memoização e jamais fatos/condições.
Não há cache global, orçamento semântico, novo solver ou limite de candidatos.

Os oracles existentes com duas posições forçam colisões; os de descarte exercitam
reuso de IDs e tabelas de verdade independentes. O FAST usa também a capacidade
default nova. A qualificação posterior repete apenas dependencies nos 73 inputs
congelados e compara todos os bytes; SP, AIR e CFG são materialmente inalterados.
