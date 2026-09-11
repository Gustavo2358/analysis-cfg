# WORK-CFG-032 — state

## Onde estamos

CP6 discovery DISCOVERY_COMPLETE / AWAITING_HUMAN_REVIEW. authorization = discovery.
Implementation NOT_STARTED / NOT_AUTHORIZED. O work item permanece ativo com os
cinco arquivos exigidos, aguardando review; não equivale à promoção do backlog.

## Verde conhecido

Cinco SHAs/trees e SHA-256 do receipt conferidos; checkouts ocupados preservados.
Probes e 212 testes existentes passaram nas verificações descritas na evidência;
os cinco lower UNSUPPORTED_SLICE são findings esperados, não CP6 PASS.
Docs e fast PASS; scope/manifest PASS e diff --check PASS.
Os checks documentais finais são repetidos após consolidar o manifest. A CI remota
W1–W5 deve ser consultada no HEAD exato do PR; status remoto não é inferido destes gates.

## Restante

Review humano do documento e das decisões semânticas/compatibilidade. Entrega
externa somente por PR DRAFT; CI W1–W5 e URL/HEAD são informados no handoff.
Nenhum merge, auto-merge ou início de W1.

## Descobertas que afetam o plano

AIR normativa define Invoke como terminador. Modelo Java já existe; codec não
transporta e sua política recusa I-56 mesmo com KnownContract. SP perde literal,
argumentos e continuação tipada; lower descarta o target. X(8) exige fitting e
interpretação de nome explícitos. Perfil de valores recusa efeitos, não faz noop
silencioso. BEFORE por operação e W4 planner são reutilizáveis; solver change NONE.
