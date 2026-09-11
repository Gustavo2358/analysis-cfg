# WORK-CFG-032 — eval

## O que prova corretude

Citações de arquivo/símbolo/campo nos SHAs congelados, probes existentes ou descartáveis e gates docs/fast/scope/manifest/diff. CI remota deve executar W1–W5 existentes.

[Evidência](../../evidence/WORK-CFG-032/README.md): frontend gera SP em cinco probes,
lower recusa todos; ponto interno real distingue BEFORE/AFTER; Invoke existente
expõe separadamente validator, codec e CFG. O [plano futuro](../../evidence/WORK-CFG-032/future-waves-oracles.md)
não cria testes nem APIs. O produto não ganhou suporte CALL neste work item.

## Casos adversariais

Literal versus variável; candidatos com supports distintos; remainder aberto; site inalcançável; chamada anterior em loop com efeitos; USING, RETURNING e exception flow; nenhuma inferência de target por texto nominal. Oráculos futuros serão apenas documentados.
