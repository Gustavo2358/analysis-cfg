# CP5 — fronteiras da análise

Arquitetura `accepted`; W1/W2 APPROVED. W3 autorizada e em validação.
W4/W5 permanecem ausentes. H1–H7/R1/R2 foram aprovados
pelo pedido humano de 09/09/2026 que autoriza somente esta preparação.
[Proveniência e hashes](../work/evidence/WORK-CFG-028/baseline.json) identificam o
handoff 1.0 e o discovery revisado, lidos integralmente em `artefatos-e2e/cp5/`.
A aprovação registrada no [lifecycle](../work/cp5-lifecycle.json) sucede o status
“aguardando confirmação” do discovery. Não copiar nem atualizar o documento externo.

```text
CFG construction (BuildCfg)
  → analysis session/index
  → generic dataflow solver ← analysis/domain
  → stable queries
  → fact consumers
```

## Módulos e dependências

| Camada futura | Dependências / responsabilidades | Limites |
| --- | --- | --- |
| cfg-kernel existente | AIR/JDK; construção, controle e payload original | não depende de analysis-kernel/analysis-values |
| analysis-kernel/structure | cfg-kernel + air-java; índices, identidade e views | sem semântica de domínio/consumidor |
| analysis-kernel/solver e SPI | estado opaco, visão ordinal mínima, JDK | sem Operations concretas, TextValue, PossibleValues, consumers, adapters, filesystem ou frontend/lower |
| analysis-kernel/application | sessão, factories registradas, solver/query/extração genérica | não importa analysis-values concreto, codec ou CLI |
| analysis-values | analysis-kernel + air-java; admissão, domínio e transfer | sem consumers, adapters, frontend/lower ou construção CFG |
| consumers | SiteView restrita + queries tipadas + FactSink | não possuem/invocam BuildCfg, solver interno ou APIs de travessia integral |
| adapters/launcher | composição das portas e transporte | sem regra de transfer/join |

DAG lógico: `cfg-kernel → analysis-kernel → analysis-values` em ordem de consumo.
A direção das dependências Maven é a inversa: `analysis-kernel → {cfg-kernel,
air-java}`; `analysis-values → {analysis-kernel, air-java, cfg-kernel}`. Todo módulo com imports
AIR declara `air-java` diretamente; transitividade não basta. Os gates de packages
continuam obrigatórios mesmo com a dependência Maven permitida.

Solver não é PossibleValues nem resolver CALL, FILE, DB2, CICS ou GRBE. É neutro
quanto à linguagem de origem. Os nomes de APIs são responsabilidades aprovadas,
sem assinaturas Java antecipadas nesta preparação. Não criar módulos/POMs antes W1.

## Entrada, indexação e ownership

Sessão sobre CfgBuildResult bem-sucedido da porta BuildCfg, com opções e seleção de
Entry explícitas; arquivo usa AirJsonFileReader → BuildCfg. `cfg.json` isolado não
carrega instructions e não é entrada suficiente. Não duplicar AIR ou criar reader CFG.
Verificar coerência de IDs, membership Publication/Unit/Entry/Sequence e endpoints ao
indexar: CfgBuildResult publicamente construível não é certificado de validade.
Não reconstruir CFG nem copiar AirValidator; codec/build já validam, sem terceira validação.

Indexar uma vez por snapshot e reutilizar Publication/Sequence/Object/Cell/Origins
por identidade. IDs completos tornam-se ordinais internos densos com tradução reversa,
aritmética/overflow checados, sem teto de produto. CfgNodeId público não precisa ser contíguo; ordem física
não determina execução. Hot path não usa display name, hash profundo do grafo ou
hashing repetido de IDs textuais. Operações usam instructions() seguido de terminator(),
sem materializar operations() por visita, wrappers por transfer ou nó por Assign.

Diretórios por Unit/Entry, buckets de arestas e adjacência contextual forward/backward
são construídos uma vez; nunca scan global para successors ou todos Objects por Assign.
OperationId resolve Sequence/offset incluindo terminador; OperandId é ocorrência.
Contextos são demandados, sem matriz eager Entries×Nodes×Locations. Facetas opcionais
(provenance, referências usadas, queries por ID) têm passes e retenção contabilizados.

## Execução e extensões

[CORE-SIZE-001](decisions/ADR-0014.md) exclui tamanho/capacidade da admissão e da
precisão. Falha de processo/infra não é resultado semântico.

[Contrato do solver](../domain/cp5-solver.md), [storage/valores/claims](../domain/cp5-values.md)
e [ADRs](decisions/index.md) governam leis e admissão. PossibleValues é uma análise;
RD, Liveness e Taint futuros podem ter outros tipos de estado e direção. Não fundir
análises num megaestado nem obrigá-las ao container textual. Registro explícito,
sem ServiceLoader/reflection dinâmica ou framework universal.

AnalysisKey inclui implementação/versão, profile, opções semânticas,
direção, precisão e Entry; snapshot/lifetime pertencem à sessão. Mesma chave e contexto
reutilizam run; outra configuração exige run distinto. Não cachear apenas por
PublicationId ou graph.hashCode(). Execução inicialmente sequencial por contexto/análise.

## Planejamento, queries e consumidores

Compilar interesses declarativos → selecionar sites em buckets AIR reais → unir
pedidos → estabilizar cada AnalysisKey requerida uma vez → materializar queries em
batch → dispatch aos interessados → FactSink. Consumidor estrutural pode pedir zero
análises. Dependências entre análises são explícitas e acíclicas; não há fixpoint
recursivo entre análises neste CP5.

Buckets primários por espécie da operação; filtros estáticos por campos tipados
existentes. Não inventar enum CALL/SQL/GRBE. Chaves de Invoke são extensão futura:
action/category/namespace e variante literal/computed, sem declarar suporte CFG.
Target calculado continua candidato antes da estabilização; filtros dinâmicos só
refinam sites já selecionados. Callbacks seguem matches M, inclusive sobreposição,
nunca broadcast I×K ou uma travessia completa por consumidor.

ObservationPlan une `(AnalysisKey, Entry, Sequence, point, subject, projection)`.
Deduplicar e ordenar offsets; replay FORWARD do IN estável em ordem primeira → última,
ou BACKWARD do OUT estável em ordem última → primeira, até o ponto extremo do lote,
capturando apenas facts solicitados. Não copiar estado completo por ponto. Queries
repetidas compartilham facts imutáveis. Lookup não planejado retorna NOT_REQUESTED;
registro tardio exige novo batch/observation epoch explícito e medido. Consumidores
não recebem Publication/graph.nodes() integrais nem executam análise/replay escondido.

FactSink recebe apenas observações finais estabilizadas, com IDs completos, escopos semânticos,
premissas e evidência pertinente; erro de consumidor identifica fase e impede saída
incompleta apresentada como sucesso. Ordenação/encoding ficam fora do fixpoint.
[Snapshot do resultado](analysis-dataflow-result-v1.md) é design, sem writer/codec.

## Roteamento de implementação

[Roadmap](../product/cp5-roadmap.md), [work item](../work/active/WORK-CFG-028/work-item.json),
[performance/métricas](../engineering/cp5-performance.md),
[challenges](../engineering/cp5-challenges.md) e
[evals/manifests](../evals/cp5/index.md). A aprovação arquitetural não autoriza Wave.

## Reforços pós-auditoria

O [contrato pós-auditoria](cp5-post-audit.md) exige integridade da projeção em W1,
replay por direção e completion por fase. Storage/effect semantics participa de
preparation/transfer antes do fixpoint; consumers interpretam fatos estáveis depois
e não corrigem estado obsoleto. Entry context não fornece local invocation frames.

## Handoff estrutural W1

`AnalysisSession.open` recebe CfgBuildResult, snapshot esperado, ProjectionPolicy e
Entries canônicas explicitamente selecionadas. `ProgramIndex` retém handles privados
por nó e sites por operação, com ID completo na borda. `ContextView` fornece cursores
sem scan global nem hashes AIR no hot path. Buckets usam classes de Operation AIR,
sem interpretação de nomes. Nenhuma interface de solver/state nasceu nesta Wave.

[Ledger e fronteira exata de referências](../engineering/cp5-w1-index-ledger.md),
[oracles e evidência](../work/evidence/WORK-CFG-028/wave-1/validation.md).

## W3 — domínio e observação

[Ledger W3](../engineering/cp5-w3-values-ledger.md): analysis-values implementa a SPI
W2, com estado por Cell e subjects ObjectId. analysis-kernel/query implementa replay
genérico por direção; W1/W2 Java intactos. A dependência direta cfg-kernel em values
é exigida pelas assinaturas CfgTransition/CfgNode existentes, sem inversão do DAG.
