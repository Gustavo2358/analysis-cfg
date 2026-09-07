# WORK-CFG-025 — Estado

## Onde estamos

CP0, modo single-checkpoint, remediação do [PR #9](https://github.com/Gustavo2358/analysis-cfg/pull/9) validada localmente; fase ready_for_review.
O usuário autorizou setup explícito de Python 3.12 antes do build upstream e documentação do requisito Python 3.10+, sem ampliar o checkpoint.
Baseline `141b8270f54558a24ee561281598e53c48a0ff6b`; branch `chore/pin-air-java-1a`; recibos de CI do head ficam no PR, sem autoinscrição de SHA.

## Verde conhecido

Main limpa/atualizada; merge upstream confirmado. Audit de paths/URLs, cache normativo, fast (41 testes), architecture (102 testes), semantic (84 métodos), Maven clean verify e revisão de scope passaram. [Evidência](../../evidence/WORK-CFG-025/validation.md).

Após a correção de Python, `check-fast.sh`, `check-architecture.sh` e `check-semantic.sh`
em `scripts/harness/`, `mvn -B -ntp clean verify` e o audit de proveniência foram
reexecutados: exit 0, mesmas contagens, sem falhas/skips. Ambiente local: JDK 21,
Python 3.14.4 e repositório Maven isolado com o upstream pinado. Parsing do YAML
confirmou setup-python@v5 com 3.12 antes do install; diff/scope revisados.

## Restante

Commit/push e recibo de CI do novo head serão registrados no PR #9; a execução com Python 3.12 será confirmada pela CI. Depois somente re-review humano, sem merge. 2A/2B não iniciados; integration/performance/full permanecem indisponíveis pelo contrato do harness.

## Descobertas que afetam o plano

O POM raiz do air-java pinado chama python3 já em validate. O bootstrap deve configurar Python explicitamente antes de mvn clean install. Sem mudança normativa, POM, kernel ou harness.
