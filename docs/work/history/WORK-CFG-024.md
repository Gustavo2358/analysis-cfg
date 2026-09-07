# WORK-CFG-024 — Checkpoint 0A: política de projeção

Status: `completed`. Data: 07/09/2026. Autorização: `implementation`, pelo pedido
explícito desta sessão para executar discovery e implementação do 0A, commits,
push e PR; parar para review humano, sem merge/auto-merge.
[PR #8](https://github.com/Gustavo2358/analysis-cfg/pull/8), aberto e não draft.

## Lifecycle, base e escopo

Main inicialmente limpa; checkout main e pull --ff-only concluídos antes de código.
Base `7edb1cd7827531ab30548d878cea3a06a483bc04`; PR #7 confirmado MERGED via GitHub,
com esse mesmo merge SHA. Branch nova `feat/cfg-known-subset-policy`.

O handoff foi localizado como `roadmap.md` na raiz do workspace; sua seção 5,
Checkpoint 0A, governa escopo. O pedido desta sessão autoriza discovery e implementação
no mesmo item, sem nova parada de autorização; exige parada no PR, antes de merge.
AGENTS e protocolo lidos integralmente. Nenhum backlog adjacente foi promovido.

`3b4db1c` registrou os cinco arquivos, autorização, must_read, findings, plano e RED
antes de produção. `34d6647` implementou policy, testes, docs e inventários dos gates.
O item permaneceu active durante a implementação, challenge, gates e abertura do PR.
Encerramento promove conhecimento para porta/pipeline/domínio/evals, remove os cinco
arquivos ativos e sincroniza registry/index/backlog. Artefatos originais de discovery
continuam inspecionáveis no commit `3b4db1c`; evidências completas de challenge no `34d6647`.

Não foram alterados outros repositórios, branches/commits upstream, pins, profiles,
POMs ou o roadmap externo (que exige registrar conclusão após merge).

## Findings do discovery e decisão

Na base, `CoreCfgProjection.unsupported` exigia COMPLETE em exatamente dois pontos:
coverage da Publication e de cada Unit. O coordinator só chega ali após preflight,
capabilities e verificação de validação incompleta. `CfgGraph` verifica IDs, endpoints,
namespace e activationEntry; não consulta inventory. `project` usa Entry.initialLabel,
Jump.destination e destinos Branch explícitos; Return/Halt têm saídas distintas.
Nenhuma regra precisa de completude global para enumerar essas relações conhecidas.

AirValidator/ReferenceChecks exige razões para PARTIAL/UNAVAILABLE, cobertura
explicada e fechamento de referências. Unit AVAILABLE requer Entry/Sequence e os
labels são validados upstream. Essas garantias locais permanecem obrigatórias.
Alegações de ausência/exaustividade, alcance ou independência causal das lacunas
exigiriam outras provas; não são produzidas por este CFG.

Autoridade semântica: AIR 2.0.0 no SHA
`122ce54e1b9ef9b00646f93ece409ca8b63bc933`, §06.1/2/8 e §08.2/6.3.
Premissas classificadas: regras AIR são IR_GUARANTEED; preflight/snapshot são
ARCHITECTURE_GUARANTEED; default KNOWN_SUBSET é EXPLICIT_CONTRACT do 0A.
Consulta readonly ao EntryGobackLowerer confirmou Publication/Unit PARTIAL, Return
com controle EXACT e outras dimensões UNAVAILABLE. Isso é evidência upstream;
nenhum código de gap, grafia ou convenção desse produtor foi copiado para decisão/teste CFG.

`BuildOptions` é a entrada pública da escolha. `ProjectionPolicy` pertence ao domínio
para preservar application → domain. O coordinator passa a policy ao único
CoreCfgProjection, depois do preflight/capabilities. Não existe rota alternativa.

Decidiu-se não adicionar status: `CfgBuildResult.graph().publication()` já retém
a Publication original integral, incluindo coverages, items, uncertainties,
premises/origins e headers com precision/gaps. `options()` contém a policy efetiva.
CFG_BUILT significa existência do produto, não completude global ou perfil AIR.

## Produto entregue

- KNOWN_SUBSET default aceita COMPLETE/PARTIAL e projeta todos os fatos suportados
  publicados; não cria nós/arestas para regiões desconhecidas nem conclui ausência.
- STRICT opt-in exige COMPLETE na Publication e em todas as Units concretas recebidas;
  PARTIAL é UNSUPPORTED_INPUT com INCOMPLETE_INVENTORY e subject AIR tipado.
- UNAVAILABLE permanece recusado nos dois modos. PARTIAL sem Units admite zero nós
  conhecidos somente em KNOWN_SUBSET; a cobertura continua PARTIAL, sem provar vazio global.
- AIR inválida, referências quebradas, limites/validação incompleta, capabilities sem
  intérprete, extensões sem semântica, body ausente e terminadores não suportados
  inclusive órfãos continuam bloqueando produto.
- Construtor BuildOptions(ValidationOptions) preservado e usa o novo default. Overload
  unsupported(Publication) também usa KNOWN_SUBSET; não dispensa preflight.
- Algoritmo de nodes/edges, assinatura BuildCfg, CfgPreflight, registry e pins intactos.
  Apenas dois checks de inventário e passagem de opção mudam comportamento.

Contrato durável em [portas](../../architecture/ports-and-adapters.md#política-de-projeção),
[domínio](../../domain/core-control.md) e [pipeline](../../architecture/pipeline.md).

## Oracle, RED/GREEN e challenge

EVAL-CFG-030 contém 20 métodos, expected manual em records/enums de observação.
CF1 esperado: Entry(E) → Sequence(L) → RETURN → NormalExit(E), três nós/duas transições.
Matriz inclui COMPLETE/PARTIAL/UNAVAILABLE global/local, defaults, STRICT subjects,
falhas independentes da policy e evidence/premise não vazia por identidade.
Caso misto parcial preserva Branch TRUE/FALSE no mesmo destino, duas Entries e
activationEntry, Return, Halt, self-loop, órfãs e ordenação. Metamorfismo altera
código/motivo de gap e texto de origem sem mudar admissão/controle.

EVAL-CFG-025 conserva seus 17 métodos: antiga recusa de PARTIAL/UNAVAILABLE passa
a selecionar STRICT explicitamente; ambos os casos mantidos, não apagados.
EVAL-CFG-030 prova UNAVAILABLE nos dois modos e o novo comportamento PARTIAL.
EVAL-CFG-028/029 e seus 22/25 métodos permanecem byte a byte intactos.

RED e GREEN:

```sh
mvn -B -ntp -Dmaven.repo.local=/tmp/cfg-0a-m2 -pl :cfg-kernel -Dtest=EvalCfg030Test test
```

Antes da produção: exit 1, 1 assertion failure, zero errors/skips; validator passou,
expected CFG_BUILT, actual UNSUPPORTED_INPUT com duas INCOMPLETE_INVENTORY (P/U).
Depois: exit 0, 20 testes e zero skips. RED commitado em `3b4db1c`.

Cada challenge usou `-Dtest=EvalCfg030Test#MÉTODO` no mesmo comando. Todos produziram
exit 1, 1 assertion failure e zero errors/skips. Produção restaurada byte a byte em finally.

| Mutante | Método |
| --- | --- |
| Default passa a STRICT | allPublicDefaultsSelectKnownSubsetAndNullPolicyIsRejected |
| STRICT aceita PARTIAL | strictRejectsPartialPublicationWithTypedSubject |
| KNOWN_SUBSET recusa PARTIAL | defaultProjectsBothPartialInventoriesWithExactReturnOracle |
| CfgGraph promove coverage global a COMPLETE | partialCoverageItemsPremisesAndDimensionalEvidenceRemainOriginal |
| Coordinator elimina issues e publica grafo vazio na recusa | unsupportedOrphanTerminatorIsNeverSilentlyOmitted |
| Mesma aceitação genérica diante de body ausente | unavailableBodyStillBlocksKnownControlProjection |

## Gates e revisão

Air-java do workspace estava além do pin. Foi usado `git archive` do SHA
`6a4091e5394fc22b3d2ada9abbdb530eb3572a58` em `/tmp/cfg-0a-air-java`, sem escrever
no checkout upstream. `mvn -B -ntp -Dmaven.repo.local=/tmp/cfg-0a-m2 clean install`
a partir dessa cópia: exit 0, 172 checks. Repo Maven novo/isolado, sem SNAPSHOT flutuante.
Maven local 3.9.16 / Temurin 25.0.4; bytecode release 21 sem preview.

Gates finais executados após restauração dos mutantes e revisão:

```sh
bash scripts/harness/check-fast.sh
MAVEN_OPTS=-Dmaven.repo.local=/tmp/cfg-0a-m2 bash scripts/harness/check-architecture.sh
MAVEN_OPTS=-Dmaven.repo.local=/tmp/cfg-0a-m2 bash scripts/harness/check-semantic.sh
git diff --check
```

Todos exit 0: fast 41 testes harness; architecture 102 testes/zero skips,
14 fontes/22 classfiles, major 65/minor 0, dependência só air-java/java.base,
porta/preflight e fronteiras intactas; semantic 84 testes obrigatórios/zero skips,
125 relatórios adversariais rejeitados. Inventários dos gates evoluíram somente
para o enum e a suíte novos; nenhum guard foi enfraquecido. O lifecycle final também
passou pelo fast. Diff integral revisado, sem alterações não relacionadas.

CI Temurin 21 SUCCESS no commit produtivo `34d6647771ad642c0823b23ce7179bc8e460584d`:
[PR run](https://github.com/Gustavo2358/analysis-cfg/actions/runs/34138979525) e
[push run](https://github.com/Gustavo2358/analysis-cfg/actions/runs/34138962148).
Todos os passos de instalação pinada, fast, architecture e semantic confirmados SUCCESS.
Os checks remotos são evidência observada via GitHub, distinta dos gates locais.
O fechamento documental é enviado ao mesmo PR e seus checks finais são verificados antes do handoff.

## Limitações e próximo checkpoint

Não há reader/writer JSON, codec, CLI, lowering, novas operações, dataflow,
reachability, dominators, visualização ou provenance adicional. A API recebe toda
a Publication; não seleciona escopo menor. Não há claim de perfil AIR completo.
Performance/integration/full continuam UNAVAILABLE; não executados nem contados
como PASS. E2E por arquivo e outros checkpoints permanecem fora desta entrega.

Próximo passo autorizado: review humano do PR #8. Sem merge/auto-merge realizado.
