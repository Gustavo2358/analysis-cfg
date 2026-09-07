# WORK-CFG-022 — especificação

## Problema

Ampliar CFG-FIRST com instructions não terminantes, Jump por LabelId e Halt
como término distinto de normal completion, sem inferir ordem intersequence.

## Objetivo

Entregar as obrigações estreitas do EVAL-CFG-028 e manter CFG-FIRST verde.

## Domínio e premissas

Publication AIR 2.0.0 validada por air-java pinado, inventário completo e corpos
disponíveis. IR_GUARANTEED: Instruction é não terminante; Sequence contém ordem
explícita e exatamente um terminador; Jump.destination é a única fonte de target;
Return segue a Entry da ativação; Halt termina execução (§01.3, §04.1–6/8, §08.2).
ARCHITECTURE_GUARANTEED: uma Sequence por nó, AIR compartilhada imutável.
EXPLICIT_CONTRACT: autorização deste checkpoint; sem reachability ou dataflow.

## Produto proporcional

SequenceNode.source + índice na lista instructions correlaciona cada ocorrência e
seus pontos antes/depois, inclusive antes do terminador. Basta para este slice:
nenhum outcome intrassequence requer ponto tipado adicional (AIR §08.2 não prescreve
API). Não criar ProgramPoint nem reutilizar OperationId como ID de nó/ponto.
Renomear CfgFirstProjection para CoreCfgProjection: único caminho de produção.
JUMP conserva activationEntry; HALT alcança HaltExit próprio por ocorrência AIR,
compartilhado entre Entries, com contexto na transição. O exit retém Operations.Halt
original e seu kind; não é normal exit nem tem origem sintética inventada.
Inventariar inclusive órfãs; regras Sequence × Entry não afirmam reachability.
Custo derivado O(N log N + T), memória O(N + T); não há benchmark/claim de escala.

## Capabilities e incerteza

CopyBytes exige memory.regions@1 no validator. AIR §04.5, §08.2/3 e §09.3/6,
com ADR-0004, sustentam interpretação precisa apenas da continuação de controle e
retenção integral dos fatos de memória, sem executar fallback ou efeitos. Registrar
esse modo restrito no produto para a capability exata; registry de extensões não
muda. Versão diferente, controle local/indireto e desconhecidos continuam recusados.
A validação upstream integral precede qualquer projeção. Unsupported em órfãs
continua diagnosticado por OperationId, sem graph parcial.

## Fora de escopo e regras

Branch/IF, dispatch, invoke, raise, opaque/local/indirect control; JSON/filesystem,
CLI, frontend/lowerer/Semantic Product, RD/PV/targets, leaders/coalescing/reachability.
ADRs e invariantes específicos estão no manifesto; a AIR pinada governa semântica.
