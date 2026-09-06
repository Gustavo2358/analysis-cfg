# Evals: o que deverá provar o produto

[Catálogo](catalog.md) descreve intenção e estado; [metadados](catalog.json) permitem
verificar referências. EVAL-CFG-007 e EVAL-CFG-024 possuem prova executável da
boundary; EVAL-CFG-027 prova localmente o registry de capability/version.
EVAL-CFG-009 e os demais evals semânticos do CFG continuam planejados.

Rotas: [MVP](mvp-scenarios.md), [controle local](local-control.md),
[metamorfismo](metamorphic.md), [perfis](profile-matrix.md),
[arquitetura](architecture-evals.md). O work item seleciona os evals do slice.

O primeiro eval de produto é EVAL-CFG-025, CFG-FIRST em memória. EVAL-CFG-026
reserva o micro-E2E externo; EVAL-CFG-027 é uma prova arquitetural local, sem claim
de produto. Branch e arquivo/memória continuam provas posteriores.

Asserções estruturais dos oráculos upstream não exigem implementar RD/PV agora.
A força dos testes será comprovada com falsificações/mutantes focalizados; contagem
de testes e porcentagem de linhas cobertas não bastam.
