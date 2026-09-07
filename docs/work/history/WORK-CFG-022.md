# WORK-CFG-022 — Fluxo linear, Jump e Halt

Status: `completed`. Data: 06/09/2026. Autorização: `implementation`, pelo pedido
explícito do usuário para promover/implementar BACKLOG-CFG-022, fazer push normal,
abrir PR novo e parar para human review, sem merge/auto-merge nem iniciar 004/006.
Base limpa main=origin/main: `0b0d02a5f913bf90e84730b1e458bac7edb69ee5`.
Branch nova: `feat/cfg-linear-jump-halt`.

## Lifecycle e autoridade

[PR #5](https://github.com/Gustavo2358/analysis-cfg/pull/5) confirmado no GitHub como
MERGED em 2026-09-07T00:02:50Z, merge commit igual à base acima. Antes de promover:
WORK-CFG-005/BACKLOG-CFG-005 completed, registry.active=[], 022 planned/work_item=null.
Não houve inconsistência a corrigir, mudança local alheia, reset, stash, force push,
reabertura do 005 ou reutilização da branch antiga.

O commit `6908ff9` criou os cinco arquivos exigidos do WORK-CFG-022, must_read e
autorização implementation, sincronizou registry/index/backlog e registrou oracle
RED antes de produção. O work permaneceu active durante implementação/CI.
Conhecimento durável foi promovido para arquitetura/domínio/engenharia/evals.
O encerramento arquiva este resumo, remove active e deixa registry.active=[].

## Upstreams e API real

Mains verificadas via git ls-remote antes de código semântico, iguais aos pins:

- Analysis IR: `122ce54e1b9ef9b00646f93ece409ca8b63bc933`;
- air-java: `6a4091e5394fc22b3d2ada9abbdb530eb3572a58`.

Nenhum pin/upstream foi alterado. Checkout detached pinado em /tmp/work-cfg-022-air-java;
`mvn -B -ntp -Dmaven.repo.local=/tmp/analysis-cfg-work-cfg-022-m2 clean install`
executado da raiz upstream: exit 0, 172 checks. Repo Maven novo/isolado fora do projeto.

Lidos Sequence/Instruction/Operation e Assign/HavocMust/HavocMay/Nop/CopyBytes,
Jump/Return/Halt, AirValidator e seus checks pertinentes. Instruction é sealed com
cinco variantes não terminantes; Sequence tem lista ordenada e exatamente um
terminador. Jump.destination é LabelId; Halt possui NORMAL/ABNORMAL; Return não tem
entryScope. AIR §04.1–6/8 e §08.2 governam as regras; nomes/texto não selecionam fluxo.

## Produto e decisões

```text
Entry(E) --ENTRY--> Sequence(op1, op2, Jump(destination=tail))
                   --JUMP, activationEntry=E--> Sequence(tail, Return)
                   --RETURN, activationEntry=E--> NormalExit(Unit, E)

Entry(E) --ENTRY--> Sequence(Halt)
                   --HALT, activationEntry=E--> HaltExit(Halt OperationId)
```

CoreCfgProjection substitui CfgFirstProjection como único caminho de produção.
Entry/Jump/Return/Halt são primitives core, sem registry fictício. Jump usa somente
LabelId explícito, inclusive target anterior/posterior e self-loop; sem fallthrough.
Return completa normalmente apenas a ativação pertinente. Halt termina execução,
nunca retorna normalmente nem segue a próxima Sequence.

Uma Sequence continua um SequenceNode; retém o mesmo objeto AIR e sua lista ordenada.
Todas as occurrences/IDs/operandos/headers/origins/precision/gaps ficam intactos, sem
interpretar memória. Sequence + posição explícita na lista bastam para correlacionar
os pontos antes/depois deste slice, conforme ADR-0003 e AIR §08.2. Nenhuma classe
ProgramPoint, bloco por instruction, leader detection ou API de dataflow foi criada.
O split nos testes mapeia explicitamente posições originais entre as novas Sequences.

HaltExit é um record próprio com CfgNodeId e Operations.Halt original. Há um nó de
término por ocorrência, sem singleton global nem source span sintético. HaltKind é
preservado. Uma ocorrência compartilhada tem uma regra HALT para cada activationEntry;
contextos ficam nas transições, sem duplicar/fundir a ocorrência. NormalExits continuam
inventariados por Entry, mesmo sem Return incidente; inventário não é reachability.

JUMP/RETURN/HALT são regras contextuais materializadas por Sequence × Entry da Unit,
inclusive órfãs, sem decidir quais Entries alcançam o source. Entries/Units homônimas
não se fundem. CfgGraph verifica fechamento e endpoints tipados, target explícito,
correlação Halt e Entry de ativação inventariada. Não implementa caminhos realizáveis.

Navegação entries()/normalExits()/haltExits() materializada uma vez: O(1) sem alocação
por consulta. Custo da projeção/validação do produto: O(N log N + T), memória O(N + T),
com T incluindo Sequence × Entry; não é claim sobre custo interno do AirValidator
nem resultado de benchmark/performance gate.

## Decisão de capability

CopyBytes requer memory.regions@1. AIR §04.5, §08.2/3 e §09.3/6, com ADR-0004,
sustentam interpretar precisamente apenas seu controle sequencial, conservando os
fatos de memória e o fallback sem executar efeitos nem fallback. O produto registra
esse modo restrito em preciseControlCapabilities(), lista imutável materializada.

A correção mínima está na negociação do consumer e no projector; o
SemanticInterpreterRegistry permanece byte a byte inalterado. Manifesto ausente
continua INVALID_IR; versão diferente, local/indirect e capabilities desconhecidas
continuam recusadas. Registro de identidade não implementa extensão. Preflight,
diagnostics, limites e obrigações upstream são preservados sem inventar suporte.

## Oracle/TDD e regressão

Expected manual estruturado de M1 e contracasos antecedeu implementação no commit
6908ff9. EVAL-CFG-028 usa records/enums próprios: nós correlacionados por EntryId,
LabelId/OperationId, kind, activationEntry, termination kind e ordem de instructions.
Não gera esperado com CfgGraph/CfgTransition, builder, DOT, toString ou serialização.

RED executado:
`mvn -B -ntp -Dmaven.repo.local=/tmp/analysis-cfg-work-cfg-022-m2 -pl :cfg-kernel
-Dtest=EvalCfg028Test test`, exit 1 por ausência de HaltExit, JUMP/HALT, haltExits e
preciseControlCapabilities. Falhas de download anteriores não contaram como RED.

GREEN: 22 testes do 028 + 20 do 025, zero skips. As três recusas históricas de
Jump/Halt/instructions do 025 evoluíram para asserts positivos sob autorização
explícita do 022; os outros 17 métodos e todas as obrigações CF1 foram preservados.
O commit `9a9cfbe` contém implementação, regressões, gates e conhecimento durável.

Contracasos: todos cinco tipos Instruction, 258 occurrences sem teto heurístico;
Jump forward/backward/decoy/self-loop/missing target; órfãs com instructions/Jump/Halt;
Return↔Halt, ambos HaltKinds, múltiplas ocorrências/Entries/Units; namespaces, metadados
e imutabilidade; permutação, alpha rename inclusive OperationId, split e display/origin
presentation. Branch/Dispatch/Raise/Invoke/Opaque em órfãs são todos diagnosticados por
OperationId sem grafo parcial; local/indirect são recusados na negociação existente.

## Mutantes e challenge

Todos executados com `mvn -B -ntp -Dmaven.repo.local=/tmp/analysis-cfg-work-cfg-022-m2
-pl :cfg-kernel -Dtest=<seleção abaixo> test`. Cada execução: exit 1, uma assertion
failure, zero errors. A guarda tipada de endpoints foi temporariamente desativada
para provar que o expected independente mata o defeito; fontes restauradas em finally.

| Mutante | Seleção do teste |
| --- | --- |
| A — Jump usa próxima Sequence física | EvalCfg028Test#jumpUsesExplicitForwardTargetInsteadOfPhysicalOrSortedNeighbor |
| B — Return ganha fallthrough | EvalCfg025Test#returnNeverFallsThroughToPhysicalNextSequence |
| C — Halt vira NormalExit/RETURN | EvalCfg028Test#haltDiffersFromReturnAndNeverFallsThroughOrReturnsNormally |
| D — Halt ganha fallthrough | EvalCfg028Test#haltDiffersFromReturnAndNeverFallsThroughOrReturnsNormally |
| E — reordenar instructions | EvalCfg028Test#m1MatchesManualControlAndOrderedOccurrences |
| E — perder instruction | EvalCfg028Test#m1MatchesManualControlAndOrderedOccurrences |

Semantic com MAVEN_ARGS=-DskipTests=true: exit 1, nenhum relatório aceito como PASS.
Branch injetado com nome Java qualificado, sem import: compilação/testes passaram,
architecture falhou por dependência bytecode proibida (exit 1). Removido antes dos
gates finais. Detector semantic rejeitou 61 fixtures de relatório: missing/duplicate/
foreign/skip/zero/erro, inclusive suíte ausente e extra. Architecture preserva inventários
exatos de 13 fontes/21 classfiles e detectores de todas as primitives ainda proibidas.

Challenge revisou target físico/nome/reparo, self-loop/backward, contexto/múltiplas
Entries, ordem/cardinalidade, identidade de pontos, distinção/saídas de Halt/Return,
órfãs/unsupported, exit global, navegação, fronteiras, capabilities e claims de eval/perfil.
Não ficaram findings sem correção. Ajuste documental adicional: a matriz local ainda
dizia CFG-FIRST planejado; alinhada ao estado completed já canônico, sem reabrir 005.

## Evals e limites

EVAL-CFG-028 implemented, sem atribuição de oracle upstream amplo. EVAL-CFG-025
continua implemented; foundation 007/024/027 preservada. 001/002/005/013/014 ficam
planned com evidência parcial explicitada no catálogo:

- 002/O-01-STRUCT exige a,b,k do X-01; k é invoke, excluído deste checkpoint;
- 005 exige ramo terminante e cenário invocador além de Return != Halt local;
- 013 inclui observações de interação/inventário parcial;
- 014 inclui O-66/equivalência de ingressos; 001 tem integridade além destes vetores.

MVP-CFG-01 incompleto: branch falta. Sem perfis AIR, performance, integration ou
aceitação de produtor promovidos. Sem branch/IF, dispatch, invoke/raise, opaque/local/
indirect control, JSON/filesystem/CLI, lowerer/Semantic Product, RD/PV/targets, leaders,
coalescing ou reachability implementados. Fixtures de recusa não são suporte.

## Gates e CI

Com MAVEN_OPTS=-Dmaven.repo.local=/tmp/analysis-cfg-work-cfg-022-m2;
Maven 3.9.16, runtime local Temurin 25.0.4 e bytecode 21 sem preview:

| Comando | Exit | Resultado |
| --- | --- | --- |
| bash scripts/harness/check-docs.sh | 0 | PASS |
| bash scripts/harness/check-harness.sh | 0 | 41 testes |
| bash scripts/harness/check-fast.sh | 0 | PASS |
| bash scripts/harness/check-architecture.sh | 0 | 60 testes, 13 fontes, 21 classfiles |
| bash scripts/harness/check-semantic.sh | 0 | 42 métodos 025/028, zero skip |
| mvn -B -ntp clean test | 0 | 60 testes |
| mvn -B -ntp clean verify | 0 | 60 testes |
| git diff --check | 0 | sem erro |
| bash scripts/harness/check-performance.sh | 3 | UNAVAILABLE |
| bash scripts/harness/check-integration.sh | 3 | UNAVAILABLE |
| bash scripts/harness/check-full.sh | 3 | fast/architecture/semantic PASS; para em performance |

CI remota Temurin 21 confirmada success para
`9a9cfbef8cf882af05b7aa9f4c8ee8227858c11c` antes do encerramento:
[run 34070250115](https://github.com/Gustavo2358/analysis-cfg/actions/runs/34070250115).
Verificação de SHA, instalação pinada, fast, architecture e semantic passaram.
O workflow mantém um job, o mesmo checkout/sha/repo Maven isolado e apenas atualiza
os nomes para o core crescente. A CI do HEAD documental final será conferida no PR
antes do handoff; sua evidência fica vinculada ao commit/check do próprio PR.

Após arquivar o work, fast e diff --check passaram novamente (exit 0), validando lifecycle.
A tabela acima documenta a bateria final local do código, executada nesta sessão.
Todos os comandos exigidos foram executados; performance/integration não têm
verificação de produto implementada, e full reporta esse limite com exit 3.

## Handoff

WORK-CFG-022/BACKLOG-CFG-022 completed; registry.active vazio. Novo PR contra main
para human review, sem merge/auto-merge. BACKLOG-CFG-004/006 permanecem planned e
sem work item; não foram iniciados. O próximo checkpoint é exclusivamente review
humano deste PR. Nenhuma autorização para consumir outro backlog foi inferida.
