# Estado — WORK-CFG-002

## Onde estamos

Boundary mínima implementada em `codex/feat/cfg-java-air-boundary`: parent Maven,
`cfg-kernel`, `CfgPreflight`, quatro testes, CI pinado e gate arquitetural real. Não
há tipos nem algoritmo CFG.

## Verde conhecido

Refs remotos e blobs foram confirmados. O `air-java` exato instalou com 172 checks;
Maven do consumer passa quatro testes. `fast` passa 41 testes do harness e
`architecture` confirma major 65/sem preview, `java.base`, dependência exclusiva em
`air-java`, source pin e delegação a `AirValidator`.

## Restante

Executar o rehearsal final de checkout limpo, revisar o diff, arquivar este item e
abrir um único PR para review humano.

## Descobertas que afetam o plano

O JSON Binding 1.0.0 agora existe no `analysis-ir`, mas permanece explicitamente
DRAFT e fora do código. O `air-java` reconciliado removeu `contracts[]` de
`Publication`; assinatura, outcomes e `ContractRef` são fatos materializados no
site de invoke. A instalação upstream deve rodar na raiz de seu checkout. A máquina
local tem JDK 25 e compila com `--release 21`; o workflow prova execução em JDK 21.
