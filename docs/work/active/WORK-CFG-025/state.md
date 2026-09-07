# WORK-CFG-025 — Estado

## Onde estamos

CP0 com validação local concluída; fase ready_for_review, modo single-checkpoint. Pedido explícito do usuário em 2026-09-07: preparar somente proveniência/pinning para 2A/2B, com commit, push, CI e PR próprios; parar para review humano, sem merge.
Baseline `141b8270f54558a24ee561281598e53c48a0ff6b`; branch `chore/pin-air-java-1a`; PR próprio será aberto após o commit certificado localmente; seu recibo de CI fica no PR, sem autoinscrição de SHA.

## Verde conhecido

Main limpa/atualizada; merge upstream confirmado. Audit de paths/URLs, cache normativo, fast (41 testes), architecture (102 testes), semantic (84 métodos), Maven clean verify e revisão de scope passaram. [Evidência](../../evidence/WORK-CFG-025/validation.md).

## Restante

Commit/push, abertura do PR e confirmação da CI do head serão registrados no PR; depois somente review humano. 2A/2B não iniciados.

## Descobertas que afetam o plano

Modularização muda paths de evidência, preserva coordenada Maven do modelo e acrescenta artefato separado air-json. Sem mudança normativa.
