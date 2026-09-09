# CP5 — preparação do harness

## Problema

Fixture PROGA não basta para provar engine genérica/eficiente. Discovery aprovado
precisa de contratos e gates duráveis, preservando seus limites.

## Objetivo

Formalizar H1–H7/R1/R2 em arquitetura/domínio, quatro ADRs coesos, métricas/ledgers,
probes/challenges, lifecycle e snapshot de resultado; validar somente o harness.
BACKLOG-CFG-020 é reaproveitado como umbrella, com cinco checkpoints sequenciais
no mesmo manifesto/branch/PR. [Estado canônico](../../cp5-lifecycle.json).

## Fora de escopo

Qualquer Java/POM novo ou alterado, módulo analysis-kernel/analysis-values, engine,
CLI/writer, mutantes produtivos e implementações W1–W5. Siblings read-only.
Sem solver Python alternativo. Performance ausente não é PASS ou falha de preparação.

## Remediação focal A–I autorizada

Esta rodada incorpora os findings da auditoria em contratos/gates/oracles, conforme
[decisões pós-auditoria](../../../architecture/cp5-post-audit.md). O histórico B1 é
preservado e APPROVED. A autorização atual é só de harness/docs/scripts/CI/evidência,
na mesma branch e PR #12 draft; não autoriza W1. Sem segundo builder/AirValidator,
sem solver Python alternativo; oracle concreto finito requerido para W2/W3, hook
NOT_AVAILABLE_UNTIL_IMPLEMENTED agora. CP5-F01 é NONBLOCKING e continua para W1.

## Correção focal F — histórica, APPROVED em e86a57c

Corrigir somente F1 (admission LIMIT), F2 (DeliveryReceipt externo) e F3
(dependências explícitas dos consumers). A/B/C/D/E/G/H/I aprovados permanecem
preservados; nenhum Java/POM, engine, writer ou Wave iniciado.

## CORE-SIZE-001 — checkpoint histórico, APPROVED em 4aeb4c0

[ADR-0014](../../../architecture/decisions/ADR-0014.md): tamanho não define admissão,
precisão ou término. Supersede budgets/outcomes de capacidade e cap de candidatos,
preservando A–I, F2/F3 e convergência matemática. Atualizar contratos/snapshots,
validators/challenges, métricas, scope guard e roteamento W1–W5; gates, push e review.
Não implementar engine, Java/POM, sibling, retry/ECS ou streaming.

## WAVE_1 — histórico aprovado

Implementar analysis-kernel estrutural: admissão correlacionada ao snapshot e policy,
identidades densas privadas, reverse handles, offsets, buckets por classes AIR,
Object/Cell e ObjectPlace, adjacência forward/backward por activationEntry e views
apenas das Entries selecionadas. Completude é inventário do profile core, sem
reachability calculada nem fechamento de fonte. Reusar payload AIR/CFG por identidade.
CP5-F01 permite somente dependências diretas do launcher e evolução focal dos checks.

W2–W5 permanecem proibidas: nenhum solver, state, PossibleValues, consumer runtime,
resolver ou CLI/writer de dataflow. Siblings read-only e source lock byte-exact.
[Autorização](../../evidence/WORK-CFG-028/wave-1/authorization.json).

## WAVE_2 — checkpoint autorizado atual

W1 APPROVED em b84389b6. Implementar solver genérico incremental, direction-aware,
IN/OUT na ordem de execução, boundaries declarativas, primeira publicação,
contextos isolados e agenda justa. Domínios forward/backward, oracle por recomposição
e oracle concreto finito são test-only; S4b, escala N/2N/4N, retenção e challenges
compiláveis com restore byte-exact. Gates locais, Maven, push e CI do HEAD final.
Sem W3–W5, sem repin ou alterações de siblings. Parar para review humano W2.

### Evidência W2 executada

[Validação](../../evidence/WORK-CFG-028/wave-2/validation.md): solver/oracles,
26 mutantes válidos e gates locais executados. W2 IMPLEMENTED / AWAITING_HUMAN_REVIEW.
Recibo CI do HEAD final no mesmo PR #12; W3–W5 não autorizadas.
