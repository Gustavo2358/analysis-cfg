# Plano

## Fatiamento

CP1a. Checkpoint autorizado: verificar PR/head canônico da Analysis IR 2.0.0,
confirmar a hipótese arquitetural, atualizar lock/hashes, perfis, evals, invariantes,
backlog e harness; challenge, gate fast, PR e parada para review.
CP1b. Após review/autorização aplicável, definir ownership do modelo IR, porta e
limites de módulos, sem gerar Java.
CP2. Especificar schema de fixture e provas de equivalência/isolamento.
CP3. Vincular vetores/evals ao MVP e decompor implementação em pequenos checkpoints.
CP4. Challenge das decisões, gate fast, handoff e parada para review.

Somente CP1a está autorizado por este checkpoint. Não é permissão para resolver as
decisões físicas restantes nem para executar trabalhos posteriores.

## Dependências

Contrato Analysis IR 2.0.0 fixado por SHA e acesso aos trechos necessários. Não
exige CobolLower pronto.

## Superfície arquitetural provável

Modelo IR compartilhado, cfg-kernel/domain/application, cfg-adapters, cfg-launcher.
Definir nomes/coordenadas na decisão; não criar pastas Java/POM antes da autorização.

## Migrações requeridas

Neste checkpoint, migração documental de IR 1.0.0/@1 para 2.0.0/perfis @2,
preservando versões `@1` das extensões. Futuramente módulo/adapter de arquivo →
caller em memória, preservando mesma porta e evitando serialização intermediária.

## Artefatos esperados

ADRs proposed avaliados; decisões registradas; plano e eval do primeiro checkpoint
Java; contrato técnico de fixtures sem semântica oculta; riscos/questões residuais.
Não produzir um segundo documento gigante duplicando todo o harness.
