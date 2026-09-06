# Matriz de conformidade: não confundir marco com perfil

A fonte é IR §10. O papel deste projeto é **Consumer/CFG**. Nenhum perfil está
implementado agora. Registro verificável: [profile-obligations.json](profile-obligations.json).

| Marco/perfil | Exigência | Estado |
| --- | --- | --- |
| CFG-FIRST (local) | Entry → Sequence(Return) → normal exit; memória; sem fallthrough | planejado |
| MVP-CFG-01 (local) | linear, jump, branch, return/halt e IF/ELSE estrutural; unsupported honesto | planejado |
| AIR-STRUCTURE@2 | transferências, cycles, entries, invoke/outcomes, `return`/`raise`/`halt`, opaque/open, `TypeRef` e provas de domínio preservadas | não implementado |
| AIR-LOCAL-CONTROL@2 | STRUCTURE@2 + `control.local@1`, matching, ports, resume/unwind | não implementado |
| AIR-INDIRECT-CONTROL@2 | STRUCTURE@2 + `control.indirect@1`, universo de labels e transferência conservadora | não implementado |

AIR-STRUCTURE@2 exige O-01-STRUCT–O-10-STRUCT, O-18-STRUCT–O-22-STRUCT,
O-29-STRUCT–O-34-STRUCT, O-41-STRUCT–O-48-STRUCT e
O-69-STRUCT–O-91-STRUCT. O-01/O-08, por exemplo, não autorizam implementar PV
neste projeto: testar ordem e pontos necessários; a conclusão de valor pertence
àquele futuro consumer. Justificar explicitamente subasserts fora do papel CFG.
Não marcar o cenário inteiro “não aplicável” para evitar sua obrigação estrutural.

O bloco O-69-STRUCT–O-91-STRUCT exige preservar/validar `TypeRef`, tipo versus
valor desconhecido, `Premise`, `sameDomain`, `DomainProofScope`, contrato
materializado no `invoke`, targets, disjunção, envelopes e limites do validator. Não exige os
sub-requisitos `SCALAR`/`REGION`: não calcular values, reaching definitions,
efeitos escalares ou storage. `sameDomain` não é igualdade de valores e o escopo de
prova é estático, não um produto do CFG.

`AIR-SCALAR-FLOW@2` e `AIR-REGION-FLOW@2` continuam perfis upstream, mas não são
claims planejados para o papel Consumer/CFG. Controle local acrescenta O-56–O-60.
Controle indireto acrescenta O-61–O-63;
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
