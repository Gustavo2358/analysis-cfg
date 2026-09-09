# Evals: o que deverá provar o produto

[Catálogo](catalog.md) descreve intenção e estado; [metadados](catalog.json) permitem
verificar referências. EVAL-CFG-007 e EVAL-CFG-024 possuem prova executável da
boundary; EVAL-CFG-027 prova localmente o registry de capability/version.
EVAL-CFG-025 prova CFG-FIRST; EVAL-CFG-028 prova instructions lineares, Jump e
Halt. EVAL-CFG-029 prova Branch estrutural/M2–M5 e satisfaz EVAL-CFG-004.
EVAL-CFG-030 prova somente a política KNOWN_SUBSET/STRICT e preservação de evidence,
sem completar os demais evals sobre interações/parcialidade ou perfis AIR.
EVAL-CFG-001/002/003/005/013/014 continuam planned, com evidência parcial; os
outros evals semânticos sem prova integral permanecem planejados. Nenhum perfil AIR completo foi implementado.

Rotas: [MVP](mvp-scenarios.md), [controle local](local-control.md),
[metamorfismo](metamorphic.md), [perfis](profile-matrix.md),
[arquitetura](architecture-evals.md). O work item seleciona os evals do slice.

O primeiro eval de produto é EVAL-CFG-025, CFG-FIRST em memória. EVAL-CFG-026
reserva o micro-E2E externo; EVAL-CFG-027 é uma prova arquitetural local, sem claim
de produto. EVAL-CFG-031 prova o arquivo AIR → CFG JSON do GOBACK e equivalência
de controle/coverage com memória; Branch tem oracle próprio no 029.

Asserções estruturais dos oráculos upstream não exigem implementar RD/PV agora.
A força dos testes será comprovada com falsificações/mutantes focalizados; contagem
de testes e porcentagem de linhas cobertas não bastam.

EVAL-CFG-032 acrescenta a prova 4D: seis métodos, golden escalar 4B, payload por identidade, limites default e smoke com 4096 Assigns. Não executa 4C/4E.

## CP5

[CP5 — harness e oracles planejados](cp5/index.md): EVAL-CFG-033 implementado somente no harness; Waves 1–5 sem produto.
