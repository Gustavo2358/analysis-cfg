# state

## Onde estamos

WORK-CFG-006 active, implementation autorizada. Promoção após encerramento do 022 e
merge GitHub do PR #6 em 2026-09-07T01:43:08Z, SHA 50847a27628e90665eaa04871c58d0c27bcc4836.
Índice corrente reconciliado; histórico do 022 preservado. Branch nova e base limpa.

## Verde conhecido

air-java clean install no repo isolado /tmp/analysis-cfg-work-cfg-006-m2: exit 0, 172 checks.
RED do 029 executado com Maven: exit 1 por BRANCH_TRUE/BRANCH_FALSE ausentes.
A tentativa anterior falhou por resolução de plugin e não foi contada como RED. Upstreams confirmados sem avanço:
analysis-ir 122ce54e1b9ef9b00646f93ece409ca8b63bc933;
air-java 6a4091e5394fc22b3d2ada9abbdb530eb3572a58.

## Restante

Oracle/RED, implementação, GREEN, mutantes/metamorfismos, challenge, gates e CI,
review do diff, lifecycle e PR. Sem claim antecipado de milestone/evals.

## Descobertas que afetam o plano

AirValidator verifica PREDICATE, known(bool) e dois labels na Unit; kernel depende
do preflight. 028 contém recusa de Branch e switch exaustivo: pergunta de reconciliação
focal enviada ao usuário, sem mudar os 22 métodos nem transferir provas positivas.
