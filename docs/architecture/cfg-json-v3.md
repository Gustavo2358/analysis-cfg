# analysis-cfg-json 3.0.0 — envelopes conservadores

WORK-CFG-038 encontrou um RED de contrato: o domínio fechado de
[v2](cfg-json-v2.md) não contém OPAQUE nem suas transições. O teste independente
`test_each_conservative_token_requires_v3_independently` rejeita cada token novo
sob v1/v2. A expansão usa major 3; os bytes anteriores permanecem intactos.

| Campo | Extensão v3 |
| --- | --- |
| terminator.kind | OPAQUE, além dos tokens v2 |
| transition.kind | OPAQUE_JUMP e OPAQUE_RETURN, além dos tokens v2 |
| terminator.openControlRemainder | Boolean obrigatório em cada terminador de Sequence no documento v3 |

O boolean é true para Opaque/Invoke com WithinControl e false nos demais casos.
Não substitui o scope: o envelope integral permanece na operação AIR correlacionada.
OPAQUE_JUMP representa JumpAlternative/Normal conhecidos; OPAQUE_RETURN alcança
NormalExit da ativação. Edges desconhecidas da ContextView não são materializadas
nesse wire. Opaque não vira Nop, Jump fictício ou sink que fecha destinos internos.

O writer escolhe v3 quando há Opaque no grafo, v2 quando há Invoke e v1 para o
restante do slice antigo. Inventaria órfãos também; nomes e array order não decidem
controle nem versão. Identidades, correlações, encoding e publicação atômica seguem
v1/v2. `e2e_partial.py` valida SP/AIR/CFG/dependency A/B de fontes reais.

O wire de dependency result permanece 1.0.0, com coverage, uncertainties e todos os
remainders existentes. Esta versão CFG é necessária para representar a operação
conservadora, não para acrescentar um rótulo global de parcialidade.
