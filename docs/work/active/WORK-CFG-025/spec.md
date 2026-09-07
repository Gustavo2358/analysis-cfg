# WORK-CFG-025 — Spec

## Problema

O pin ativo anterior e os paths anteriores à modularização não representam o upstream autorizado para os próximos checkpoints.

## Objetivo

Adotar exclusivamente `air-java@b78f4068d8a479f48eb048b8d76fa60a0997dc4a` (merge PR #5 / 1A). Revalidar paths e URLs nesse objeto Git; manter `analysis-ir@122ce54e1b9ef9b00646f93ece409ca8b63bc933` como autoridade normativa separada.

## Autoridade e baseline

Pedido explícito do usuário em 2026-09-07: preparar somente proveniência/pinning para 2A/2B, com commit, push, CI e PR próprios; parar para review humano, sem merge.
Main limpa após `git pull --ff-only`: `141b8270f54558a24ee561281598e53c48a0ff6b`. Branch `chore/pin-air-java-1a`. CP0 deste item é apenas proveniência, não os checkpoints de implementação 2A/2B.

## Contrato de sucesso

O parent `air-java-parent` agrega `air-model` (artefato `air-java`, model/validator) e `air-json` (codec compartilhado, cobertura 1A). Confirmar POMs, `AirJson.java`, todos os paths pinados por `git cat-file -e SHA:path` e URLs `blob/SHA/path`. Comparar o bloco normativo com a baseline e o lock upstream. Referências antigas corretas em evidências históricas permanecem byte a byte.

## Fora de escopo

Nenhum POM, Java, teste de produto, modelo, algoritmo ou capability novo. Não implementar 2A, 2B, CLI, AIR JSON reader/writer, CFG JSON ou E2E; não adicionar dependência Maven de air-json. Não alterar air-java, analysis-ir ou proleap-poc.
