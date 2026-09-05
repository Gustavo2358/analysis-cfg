# Plano

## Fatiamento

CP1. Verificar fontes fixadas e estado real do novo repo; classificar desvios.
CP2. Definir ownership do modelo IR, porta e limites de módulos, sem gerar Java.
CP3. Especificar schema de fixture e provas de equivalência/isolamento.
CP4. Vincular vetores/evals ao MVP e decompor implementação em pequenos checkpoints.
CP5. Challenge das decisões, gate fast, handoff e parada para review.

Esses passos só serão executados após autorização do discovery. Não são uma
permissão prévia para todos os trabalhos posteriores.

## Dependências

Contrato IR fixado e acesso aos trechos necessários. Não exige CobolLower pronto.

## Superfície arquitetural provável

Modelo IR compartilhado, cfg-kernel/domain/application, cfg-adapters, cfg-launcher.
Definir nomes/coordenadas na decisão; não criar pastas Java/POM antes da autorização.

## Migrações requeridas

Nenhuma nesta fase. Futuramente módulo/adapter de arquivo → caller em memória,
preservando mesma porta e evitando serialização intermediária.

## Artefatos esperados

ADRs proposed avaliados; decisões registradas; plano e eval do primeiro checkpoint
Java; contrato técnico de fixtures sem semântica oculta; riscos/questões residuais.
Não produzir um segundo documento gigante duplicando todo o harness.
