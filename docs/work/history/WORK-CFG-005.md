# WORK-CFG-005 — CFG-FIRST: Entry, Return e normal exit

Status: `completed`. Data: 06/09/2026. Autorização: `implementation`, pelo pedido
explícito de promover/implementar BACKLOG-CFG-005, abrir PR novo e parar para review
humano sem merge nem outro backlog. Base limpa da main/HEAD=origin/main:
`f3da26a5181d821e9531e88be239802421e93eb9`. Branch `feat/cfg-first-return`.

## Lifecycle anterior e promoção

PR #4 confirmado no GitHub como MERGED em 2026-09-06T22:51:28Z, com merge commit
igual à base acima. WORK-CFG-003 e BACKLOG-CFG-003 estavam completed,
registry.active estava vazio e BACKLOG-CFG-005 estava planned/work_item=null antes
da promoção. Não houve inconsistência a corrigir nem reabertura do trabalho anterior.

WORK-CFG-005 foi criado com exatamente work-item.json, spec.md, plan.md, eval.md e
state.md, autorização registrada e must_read exigido. Registry/index/backlog foram
sincronizados como active durante a implementação. O commit `da08234` preserva
essa promoção e o oracle anterior ao builder. No encerramento, conhecimento foi
promovido às docs de domínio/arquitetura/gates/evals, active foi removido e
registry.active voltou a `[]`; somente BACKLOG-CFG-005 foi concluído.

## Upstreams e contrato real

As mains foram verificadas via `git ls-remote` antes de código semântico:

- Analysis IR: `122ce54e1b9ef9b00646f93ece409ca8b63bc933`;
- air-java: `6a4091e5394fc22b3d2ada9abbdb530eb3572a58`.

Ambas coincidiam com sources.lock. Nenhum pin/upstream foi alterado. Checkouts
exatos detached e Maven repo ficaram fora do projeto, em /tmp. O air-java pinado
executou `mvn -B -ntp clean install` no repo isolado: 172 checks, exit 0.

Foram lidos Publication, Unit, Entries.Entry, Sequence, Operations.Return,
AirValidator e suas verificações pertinentes. API real: Sequence contém
instructions e terminator não nulo; `Operations.Return(Header, List<Expression>)`
não possui entryScope. AIR §01.2/01.3 e §04.8/05.1 governam o oracle. O retorno
segue a Entry da ativação corrente; a lista usada pelo validator não seleciona
caminhos. Nenhuma semântica veio de COBOL, JSON, ordem física ou nomes de display.

## Produto entregue e limites

```text
BuildCfg(Publication, BuildOptions)
    → EntryNode(E)
    → SequenceNode(initialLabel, Return)
    → NormalExit(PublicationId, UnitId, EntryId)
```

No namespace próprio domain, CfgNode é sealed com EntryNode, SequenceNode e
NormalExit; CfgNodeId é ordinal CFG namespaced pela Publication. Não substitui
LabelId nem EntryId. CfgGraph retém a Publication original e listas próprias
imutáveis; nós de Entry/Sequence retêm os mesmos objetos AIR, preservando IDs,
origins, header/operandos/outros fatos. Exits sintéticos não fabricam source span.

Cada Sequence origina um nó, inclusive órfãs. ENTRY usa apenas initialLabel;
RETURN é uma regra condicionada por activationEntry e termina somente no exit dessa
Entry. Return compartilhado conserva todas as Entries da Unit sem fundir seus
exits. Essas regras não são arestas incondicionais: compor caminhos deve preservar
activationEntry. Não há implementação de frames, reachability ou dataflow.

Índice de labels completo por Unit, sem fallback por nome. Ordenação por IDs
estabiliza os ordinais/listas, não cria controle. Custo derivado O(N log N + T),
com T incluindo as regras Return por Sequence/Entry e memória O(N + T). Nenhum
claim de performance medida ou perfil AIR completo.

CfgBuildResult substitui READY_FOR_CFG_PROJECTION por CFG_BUILT com
Optional<CfgGraph> obrigatório. Falhas não contêm graph. PublicationId, versão,
BuildOptions (somente ValidationOptions), preflight integral e diagnostics são
preservados. INVALID_IR, UNSUPPORTED_CAPABILITY, VALIDATION_LIMIT e
INCOMPLETE_VALIDATION continuam distintos; UNSUPPORTED_INPUT acrescenta recusa
correlacionada de forma fora do slice. Complete/zero Units produz inventário
realmente vazio; body/inventário indisponível não vira sucesso vazio.

Registry e negociação do WORK-CFG-003 permanecem. Return é core, não registrado.
Identidade de capability registrada não finge semântica implementada; EVAL-CFG-027
continua prova de composição, e EVAL-CFG-009 permanece planned.

## Oracle, RED/GREEN e contracasos

O commit `da08234` escreveu 15 testes de EVAL-CFG-025 e expected estruturado manual
antes do builder. RED: `mvn -B -ntp -pl :cfg-kernel -Dtest=EvalCfg025Test test`,
exit 1 por CfgGraph/CfgNode/CfgTransition/graph/CFG_BUILT ausentes. A tentativa
anterior impedida por download de dependência não foi contada como RED semântico.
Após implementação: 15 casos focais + 18 regressões, todos verdes. Quatro casos
adversariais adicionais fecharam 19 semânticos e 37 testes totais sem skip.

Cobertura: Publication válida; initialLabel; uma Sequence por nó; Return/normal
exit; correlação Publication/Unit/Entry; missing label INVALID_IR; terminador
null rejeitado no construtor AIR; no fallthrough; permutação física; órfã
inventariada sem predecessor artificial; múltiplas Entries compartilhando Return;
Units com IDs locais homônimos; IDs CFG próprios; AIR/containers imutáveis e
assertSame sem deep copy; dois operandos Return preservados em ordem; Halt, Jump
e instructions recusados, inclusive Halt em órfã. Sem interpretação dessas formas.

Os metamorfismos permutam Sequences, Units e Entries mantendo IDs/referências.
As observações correlacionadas e IDs/transições determinísticos permanecem iguais.
EVAL-CFG-025 está implemented com sua expectativa integral preservada;
EVAL-CFG-001, EVAL-CFG-009 e EVAL-CFG-014 continuam planned, com evidência apenas
parcial deste work. Nenhum perfil foi promovido.

## Mutante e challenge

Mutação temporária em CfgFirstProjection: para Return, usar a próxima Sequence da
coleção AIR como target, senão exit. Comando focal:
`mvn -B -ntp -pl :cfg-kernel -Dtest=EvalCfg025Test#returnNeverFallsThroughToPhysicalNextSequence test`.

- Guarda do produto ativa: exit 1, 1 erro por endpoints incompatíveis.
- Guarda temporariamente desativada: exit 1, 1 falha de assertEquals, expected
  L → normal exit, observed L → other. O oracle mata o defeito independentemente
  da guarda produtiva.
- Ambos os arquivos restaurados em finally; semantic posterior: exit 0, 19/19.

Outras falsificações reais: imports de filesystem/reflection/ServiceLoader,
classe CFG ausente no inventário e Publication AIR paralela, cada uma rejeitada
com exit 1. Semantic com MAVEN_ARGS=-DskipTests=true: Maven sem testes, hook exit 1.
O detector semantic rejeita 25 fixtures de relatório, incluindo zero, cada método
obrigatório ausente, duplicata, suíte errada e skip/failure/error.

Finding corrigido: test_19_unavailable_never_passes herdava semantic=implemented
e esperava exit 3. Sua fixture agora materializa unavailable/hook=null para cada
gate, incluindo architecture. A obrigação e assertiva não foram enfraquecidas;
fast continua limitado ao harness. Demais perguntas do challenge não encontraram
controle implícito, mutação AIR, primitive fora do slice ou claims inflados.

## Gates e CI

Com `MAVEN_OPTS=-Dmaven.repo.local=/tmp/analysis-cfg-work-cfg-005-m2`;
runtime local Temurin 25.0.4, target 21 sem preview:

| Comando | Exit | Evidência |
| --- | --- | --- |
| bash scripts/harness/check-docs.sh | 0 | PASS |
| bash scripts/harness/check-harness.sh | 0 | 41/41 |
| bash scripts/harness/check-fast.sh | 0 | PASS |
| bash scripts/harness/check-architecture.sh | 0 | 37 testes, 13 fontes e 20 classfiles exatos |
| bash scripts/harness/check-semantic.sh | 0 | 19 testes EVAL-CFG-025, zero skips |
| mvn -B -ntp clean test | 0 | 37/37 |
| mvn -B -ntp clean verify | 0 | 37/37 |
| git diff --check | 0 | sem erro |
| bash scripts/harness/check-performance.sh | 3 | UNAVAILABLE |
| bash scripts/harness/check-integration.sh | 3 | UNAVAILABLE |
| bash scripts/harness/check-full.sh | 3 | fast/architecture/semantic PASS; para em performance |

A CI remota reportou success para `76bcaef6e4bbb5eaf92c600b1ce2d720559815c2`,
confirmado no GitHub antes do encerramento do work:
[run 34066840936](https://github.com/Gustavo2358/analysis-cfg/actions/runs/34066840936).
Os passos de instalação do air-java pinado, fast, architecture e semantic passaram
em Temurin 21, usando o mesmo Maven repo isolado. A autorização de encerramento
não se baseou apenas no ambiente local. O PR também executa a CI no seu HEAD final.

## Correção solicitada no review do PR #5

O usuário autorizou uma correção focal: entries()/normalExits() percorriam todos
os nós e alocavam listas em cada consulta, contrariando graph-product.md. A
correção permanece no mesmo WORK-CFG-005/PR, sem promover outro backlog.

Regressão `navigationReusesMaterializedImmutableInventories` escrita antes da
correção: RED observado com exit 1, falhando nas duas assertivas de identidade das
listas. Comando: `mvn -B -ntp -pl :cfg-kernel
-Dtest=EvalCfg025Test#navigationReusesMaterializedImmutableInventories test`, no
mesmo Maven repo isolado acima. O teste também verifica conteúdo e imutabilidade.

CfgGraph passou de record a classe final para guardar as duas listas em campos
final privados. Elas são preenchidas na passagem de validação dos nós e congeladas
com List.copyOf; accessors retornam diretamente os campos, sem lazy cache. O
construtor e accessors públicos, igualdade por valor, invariantes e retenção da AIR
foram preservados. A regressão de cópia defensiva também verifica equals/hashCode.
Javap confirmou que ambas as consultas contêm apenas aload_0/getfield/areturn.

GREEN focal: 20 testes EVAL-CFG-025, zero skips, exit 0. Os inventários dos gates
passam a exigir 20 testes semânticos/38 totais e continuam enumerando exatamente
as mesmas 13 fontes e 20 classfiles. Performance gate/benchmarks não foram ativados.
Validação da correção no Maven repo isolado: fast, architecture, semantic,
`mvn -B -ntp clean verify` (38 testes) e `git diff --check` passaram com exit 0.
Performance/integration/full não foram reexecutados nesta correção focal.
A CI Temurin 21 do commit da correção será conferida no PR antes do handoff;
a evidência do HEAD revisado fica registrada no próprio PR.

## Handoff

WORK-CFG-005 e BACKLOG-CFG-005 completed; registry.active vazio. Novo PR contra
main, aguardando review humano. Nenhum merge, auto-merge ou publicação de pacote.
BACKLOG-CFG-022 não foi iniciado; 004/006 também não foram promovidos.
Sem jump, halt, branch/IF, dispatch, invoke/raise, controle local/indireto,
JSON/filesystem/CLI/DOT, lowerer/Semantic Product, instructions como feature,
dataflow/RD/values, leaders ou coalescing. Próximo checkpoint é review humano;
outro slice exige nova autorização.
