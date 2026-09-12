# CP5 — storage, PossibleValues e alcance dos resultados

Contrato aceito H2/H3/H5, [ADR-0012](../architecture/decisions/ADR-0012.md).
Autoridade AIR 2.0.0 no source lock: identidades §01, tipos §02, memória §03,
operações §04, incompletude §06 e consumidores §08. Este domínio não redefine AIR.

## Admissão e locations

Primeiro profile `scalar-text-direct@1`: ObjectPlace inteiro, CellBinding direto,
Object/Cell known(text), Assign(destination, Literal(TextValue)) ou
Assign(destination, Read(ObjectPlace(source))) para Cells TEXT diretas admitidas,
sem conversão, e Nop. Jump/Branch/
Return/Halt controlam o grafo já existente. Outras expressões/writes, HavocMust/May,
CopyBytes, aliases indiretos, views, choices, binding desconhecido e memória regional
são recusados explicitamente. Aceitação estrutural pelo CFG não prova semântica de
valor; efeito desconhecido nunca vira Nop.

Location = Cell inteira no contexto da Entry/run. Object é declaração/subject de
query, Storage é base; OperandId é ocorrência. Dois Objects com a mesma Cell
compartilham estado. Bases distintas não provam disjunção. Uma base dispensa prova
entre pares; múltiplas bases admitidas exigem **uma** DisjointStorage cobrindo todo
seu conjunto, verificada em O(D+P) membros, sem materializar pares. A⊥B e B⊥C não
implica A⊥C. Provas fragmentadas podem ser recusadas por limitação do profile,
sem chamar a AIR de inválida. Premissas assumidas/obrigações estruturais ficam
rastreáveis e não se tornam prova de verdade do produtor.

O lower traduz a evidência source-derived IndependentStorageSet para essa premissa
multi-Cell nos profiles admitidos; não a fabrica por IDs distintos. Corpus sintético
pode declarar premissa explicitamente.
[Backlog e ownership](../work/cp5-follow-ups.md).

## Domínio e boundary

PossibleValues é a análise; FiniteProgramTextValues descreve seu primeiro domínio.
O nome não congela uma classe; a finitude vem do programa, sem cap configurável. Ponto
inalcançado é separado de valor em ponto alcançado:

| Valor conceitual | Significado |
| --- | --- |
| Candidates({}, true) | alcançado desconhecido |
| Candidates({PROGA}, false) | singleton fechado no modelo |
| Candidates({A,B}, false) | alternativas fechadas no modelo |
| Candidates({A}, true) | candidato sustentado e restante desconhecido |

Candidates({}, false) não é valor normal de Cell alcançada. Ausência de chave em
estado alcançado significa o default Candidates({}, true), **não bottom**.
Boundary alcançado desconhecido, salvo condições iniciais literais admitidas.
Preserve/externo/não inicializado/parâmetro desconhecido mantêm causas distintas;
não inventar zero, não reaplicar seed no loop, recusar condições contraditórias.

Join une candidatos e OR do open. Chave presente só em um dos estados alcançados
faz join com default desconhecido do outro: {A} fechado + ausência = {A} aberto.
⊥p adota a primeira contribuição alcançada sem abrir; inalcançável não adiciona
restante. Ordem por inclusão de candidatos e false≤true.
Open universal não autoriza apagar evidência enumerada por igualdade denotacional.

Strong Assign **substitui** o valor corrente. Literal produz singleton fechado,
inclusive após open. Read direto copia os Candidates da source **antes da escrita**:
destination := valor anterior de source, preservando candidates, open remainder e
candidate supports. É cópia de valor, sem criar alias; escritas posteriores na source
não alteram o valor copiado. Outras Expressions continuam não suportadas.
Strong update não acumula valores mortos. Join preserva todos os candidatos, seja sua
cardinalidade 9, 100, 10.000 ou maior. Não há maxCandidates, k produtivo,
CARDINALITY_LIMIT ou Saturated por contagem. Unknown remainder vem de incerteza
semântica/escopo aberto, nunca da economia de memória.

O conjunto U(P) de literais/condições iniciais admitidos é finito porque P é finito.
Cada S ⊆ U(P); inclusão + open tem altura finita. O produto por locations finitas,
com bottom de ponto separado e transfers monotônicos, converge sob agenda justa.
Não impor teto a U(P). Domínios futuros de cadeia infinita precisam de abstração
ou widening semanticamente justificados, sem usar heap como política de precisão.
[Decisão e supersessão focal H4](../architecture/decisions/ADR-0014.md).

Igualdade textual segue escalares Unicode; sem trim/case folding/normalização.
Pool de valores por sessão/run, U proporcional aos valores do programa, sem String.intern global, cópia de
chars por ocorrência ou pool de todos sets históricos. Provenance não cresce por
caminhos. [H4, ledger e retenção](../engineering/cp5-performance.md).

## Pontos e claims

Query tem Entry + OperationId completo + before/after/outcome. Before/after de
Assign e before(terminator) são admitidos; after(Return/Halt) sem semântica de
memória posterior retorna UNSUPPORTED_POINT. Não contaminar retroativamente
before(Return) com dimensões posteriores indisponíveis. Não usar stateAt(node)
como se distinguisse todas as operações da Sequence.

Para CP4E em before(Return):

```text
modelScope = KNOWN_GRAPH_ENTRY
modelValue = {PROGA}
modelValueRemainder = false
sourceScope = open (Unit CONTROL, Publication/Unit PARTIAL)
effectiveUnknownRemainder = true
```

CFG_BUILT e KNOWN_SUBSET não certificam completude de fonte. Gap code textual não
é prova de irrelevância: nenhuma whitelist de ALTERNATE_ENTRIES_NOT_PROJECTED.
Resultado estabilizado do modelo conserva candidato e o restante efetivo exigido
por controle aberto; não recusar todos PARTIAL nem promovê-los a exatos globalmente.
Não alcançado no modelo não significa fonte inalcançável. Restante efetivo inclui
restante do modelo **ou** abertura relevante de fonte; limites/recusa não são vazio.

Evidence mínima: snapshot, regra/profile, ponto/subject, premises, refs AIR/origins
pertinentes. No linear, Assign identificado pelo replay sustenta PROGA; um literal
arbitrário do pool não prova definição alcançável. Sem árvore de caminhos ou claim
de testemunho concreto; causalidade RD/Def-Use completa é futura.
[Contrato de resultado para review](../architecture/analysis-dataflow-result-v1.md).

## Limites explicitados pela auditoria

[Regras B–H](../architecture/cp5-post-audit.md): replay usa âncora/ordem da direção;
Cell/texto lógico não define bytes ou codec para GRBE. PossibleValues não relacional
produz candidatos abstratos, sem provar pares de campos nem caminhos concretos.
Effects semantics alimenta o transfer antes do fixpoint e não pode ser substituída
por reparo de consumers. Invoke depende de slice futuro de controle/effects; Entry
não é frame de retorno local. W3 agrega oracle concreto finito e métricas de
qualidade ao custo, sem domains relacionais, regions ou Liveness de produto.


## Implementação W3 autorizada

O profile efetivo `scalar-text-direct@1`, AVL persistente, sets finitos sem cap e
replay contextual estão detalhados no [ledger W3](../engineering/cp5-w3-values-ledger.md).
O texto conceitual acima não amplia as formas admitidas: storage direto textual,
premissa única cobrindo bases distintas e effects explicitamente enumerados.
Outcomes de query reutilizam Control.OutcomeKey da AIR fixada; after terminator e
outcomes não materializáveis produzem UNSUPPORTED_POINT. A camada wire continua W5.

Na remediação W3-F1/F2, suporte acompanha o domínio: join une produtores por
candidato, equivalência detecta mudança só de suporte e strong Assign mata suporte
anterior. Assign literal contribui OperationId/OriginId; Read direto preserva os
suportes do valor copiado. InitialCondition literal contribui
place OperandId, origin e premises. ValueFact expõe a associação por candidato e
refs agregadas, sem reconstrução externa nem árvore de caminhos. O conjunto finito
de produtores acrescenta uma dimensão de inclusão à prova de convergência acima.

EntryState.uncertainties não vazio abre sourceUnknownRemainder nessa Entry, sem
abrir o modelo. Coverage/precision relevante de qualquer alias Object da mesma
Cell participa da abertura da fonte. Dimensão apenas dependencies, mantendo
storage/values EXACT e coverage MODELED, não abre values; outra Cell não contamina
a consulta. Nenhuma decisão usa o texto informal do gap. Evidência focal e limites
ficam no ledger W3 e em wave-3/review-f1-f2.
