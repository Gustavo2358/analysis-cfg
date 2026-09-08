# 4D — contrato

Autoridade AIR 2.0.0 @ 122ce54e1b9ef9b00646f93ece409ca8b63bc933, binding 1.0.0 DRAFT preservados. Runtime 4B @ ce530a7e17ab12b23c48f29425f503ff920b09fb. Golden escalar lido como bytes upstream, nunca reencodificado. Reader real → shared decode → BuildCfg defaults → CFG_BUILT/KNOWN_SUBSET.

Oracle independente: três nós Sequence(body, Return leave), Entry(start), NormalExit(alpha,start); ENTRY 1→0 e RETURN 0→2. Source Sequence e Publication idênticas às recebidas. Um Assign(set-program), ObjectPlace(data-slot), Literal TextValue(PROGA), Cell(backing-cell); referências por IDs completos. PARTIAL nos dois inventários, uncertainties/coverage preservadas. Zero delta de produção. CFG JSON v1 não contém instructions. Smoke com 4096 Assigns mantém três nós/duas transições sem threshold temporal. Input default 16 MiB/depth128 permanece.

## Fora de escopo

4C/4E, algoritmo CFG, dataflow, novo schema/nó/edge, streaming e mudança de limites.

## Objetivo

Consumir reprodutivelmente o transporte escalar 4B mantendo semântica CFG.

## Problema

Codec 1A recusa inventário de storage com IMPLEMENTATION_LIMIT, antes do kernel.
