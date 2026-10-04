# Integração da Prioridade 2

Revisão aprovada em 2026-10-04. O merge do [PR #64](https://github.com/Gustavo2358/analysis-cfg/pull/64) efetiva DONE com os testes técnicos aprovados.

Os pins em `docs/sources/sources.lock.json` apontam para commits integrados de IR,
AIR, frontend e lower. O fechamento troca apenas pins e documentação; produção,
testes e POMs permanecem idênticos ao head revisado `ae97aa4`.
FAST local PASS (116,803 s) com AIR integrada e lower `94d4f86`; a árvore do merge
`b4551bec673957a73ca1d2533e830b0584f2088b` é idêntica. O CI valida o pin desse merge.

A qualificação anterior de 73 fontes/292 etapas é reutilizada. A reexecução após
o cache processou 73 dependencies e reutilizou 219 produtos; 71 resultados foram
byte-idênticos e dois mudaram somente métricas. Esses resultados não são uma nova
execução do corpus durante o merge.

Permanecem 14.528 gaps, 65 PARTIAL e 8 COMPLETE. A Prioridade 2 conclui o escopo
causal finito: valor desconhecido conserva leitura e efeito conhecido; representação
inválida mantém sua fronteira própria. Restam 1.329 MOVEs em fronteiras declaradas.
A exclusão comprovada de COSGN00C no caminho COPAUS0C e as cinco ampliações de
openControlRemainder permanecem explícitas na [qualificação](numeric-text-values.md).

O finding DecimalPart.kind foi fechado em IR/AIR: os sete tokens canônicos são
maiúsculos; teste literal independente e duas mutações detectam divergências.
O fechamento não inicia a próxima prioridade nem alega cobertura completa de COBOL.
