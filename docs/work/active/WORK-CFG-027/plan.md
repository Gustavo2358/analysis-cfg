# Plano

1. Confirmar baseline limpo e merges, arquivar lifecycle 2B.
2. Antes do repin/materialização: RED usando reader baseline e scalar upstream extraído em /tmp.
3. Pin ativo 4B, materializar bytes e proveniência, oracle in-memory e golden manual/CLI.
4. Guardas pin/fixture/suítes e challenges compiláveis, restore byte a byte, segundo GREEN.
5. Gates e Maven verify, commits/push/PR, CI head exato e review humano sem merge.

## Dependências

Somente air-java 4B mergeado. Não depende do 4C.

## Fatiamento

RED antes do repin, integração/pin mínimo, challenges e segundo GREEN, PR.
