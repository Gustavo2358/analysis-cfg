# Matriz de conformidade: não confundir marco com perfil

A fonte é IR §10. O papel deste projeto é **Consumer/CFG**. Nenhum perfil está
implementado agora. Registro verificável: [profile-obligations.json](profile-obligations.json).

| Marco/perfil | Exigência | Estado |
| --- | --- | --- |
| MVP-CFG-01 (local) | linear/branch/saídas/invoke delimitado; arquivo e memória; unsupported honesto | planejado |
| AIR-STRUCTURE@1 | transferências, cycles, entries, invoke/outcomes, saídas, opaque/open e estrutura preservada | não implementado |
| AIR-LOCAL-CONTROL@1 | STRUCTURE + control.local@1, matching, ports, resume/unwind | não implementado |
| AIR-INDIRECT-CONTROL@1 | STRUCTURE + universo de labels e transferência indireta conservadora | não implementado |

AIR-STRUCTURE exige O-01–O-10, O-18–O-22, O-29–O-34 e O-41–O-48, **no escopo
estrutural de cada cenário**. O-01/O-08, por exemplo, não autorizam implementar PV
neste projeto: testar ordem e pontos necessários; a conclusão de valor pertence
àquele futuro consumer. Justificar explicitamente subasserts fora do papel CFG.
Não marcar o cenário inteiro “não aplicável” para evitar sua obrigação estrutural.

Controle local acrescenta O-56–O-60. Controle indireto acrescenta O-61–O-63;
O-62 trata refinamento opcional: se não houver refinamento, registrar não suportado,
não fabricar RD ou claim de execução desse teste. O CFG inicial admite todo S.

`VALID`, `CONSERVATIVE` e `PRECISE_FOR_PROFILE` são níveis distintos. Fallback de
local retorna conservador, nunca preciso para perfil local. Um subset de STRUCTURE
pode ser entregue, mas não recebe seu selo completo. Contexto do relatório inclui
entries, revisão IR, premissas, limites, perfil e capabilities usadas.

Matriz futura de evidência deve registrar por obrigação: teste concreto, execução,
asserts estruturais, subasserts adiados, resultado e razão. Essa evidência, mais
invariantes aplicáveis, autoriza o claim — não o nome de uma classe ou a existência
de um arquivo de teste.
