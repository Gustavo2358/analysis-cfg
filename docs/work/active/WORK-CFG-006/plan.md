# plan

## Fatiamento

Oracle, implementação, challenge e gates delimitados neste item.

## Dependências

PR #6 MERGED confirmado; 022 completed e registry vazio antes da promoção.
Base limpa main=origin/main=50847a27628e90665eaa04871c58d0c27bcc4836.
Branch nova feat/cfg-structural-branch. Mains upstream iguais aos pins; sem delta.

Oracle manual M2–M5 e contracasos → RED real → mudança mínima nos três tipos do
core → GREEN → mutantes A–G → metamorfismos e challenge → gates → CI → lifecycle.

## Superfície e migração

Único CoreCfgProjection. Nenhum nó novo, storage de predicate ou adapter.
Inventários manuais dos gates evoluem somente para Branch e a suíte 029.
025 intacto; 028 requer reconciliação focal da recusa de Branch e switch exaustivo.
Não alterar contagens nem adicionar provas positivas ao 028.

## Artefatos e checkpoint

Cinco arquivos do work, teste/fixture 029, docs duráveis e PR com evidência.
MVP e evals só promovidos após avaliar expectativas/oráculos completos.
O-74-STRUCT inclui concat/not/read unknown_type e sameDomain; não presumir cobertura
por testar só predicate unknown_type. O-19-STRUCT será reavaliado no papel CFG.
