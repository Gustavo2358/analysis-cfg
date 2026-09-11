# CP6 W1D — avaliação

## O que prova corretude

[REDs preservados](../../evidence/WORK-CFG-033/raw/red-vertical-commands.json), testes semânticos com expectativas manuais, reader Python estrito independente e [duas execuções verticais reais](../../evidence/WORK-CFG-033/e2e-receipt.json). Nenhum SP/AIR foi injetado na vertical. Fixtures AIR dos testes unitários são bytes capturados da execução real anterior à implementação.

O gate `check_w1d.py` exige 34 testes nominais: CFG/wire, perfil novo/antigo, fixpoint, consumer, planejamento compartilhado, literal sem provider de valores, candidato/suporte, escala, CLI/atomicidade e resource limit. Compile error não conta como detecção semântica. [Primeira campanha](../../evidence/WORK-CFG-033/mutation-receipt.json): 18 mutações compiláveis detectadas em testes, duas guardas de fonte, restauração byte-exact e segundo GREEN.

## Casos adversariais

Continuação permutada/decoy; Invoke órfão; outcomes finite fora da slice; may-write Nop/strong-kill/replay-only; AFTER indevido; objeto nominal errado; padding perdido; interpretação dentro de values; suporte globalizado em join de PROGA/PROGB; literal disparando solver; wrong namespace/category; expressão não suportada; nomes não canônicos; no-MOVE; source/interpretation remainder descartados. NoMemory preserva Cells, AllMemory abre as modeladas e formas menores/mustOverwrite/perOutcome têm recusa explícita.

A regressão canônica exige W1–W5, CFG goldens, CP3, CP4E e overwrite histórico. Resultados só são promovidos a PASS após execução. Remote CI, HEAD/tree e Draft PR são registrados no handoff; não são deduzidos de testes locais.

## Resultado local final

[Full contínuo, gate W1D e probes](../../evidence/WORK-CFG-033/validation-receipt.json): PASS. [Campanha final](../../evidence/WORK-CFG-033/mutation-final-receipt.json): 20 mutações detectadas, sem aceitar compile error, com restauração exata e segundo GREEN. [DefaultValuePlan pelo boundary](../../evidence/WORK-CFG-033/default-plan-architecture-receipt.json): lógica de Invoke detectada como alteração de fonte protegida. [E2E histórico](../../evidence/WORK-CFG-033/w5-final-receipt.json): CP3, duas execuções CP4E e overwrite passaram. [E2E W1D final](../../evidence/WORK-CFG-033/e2e-receipt.json): duas execuções idênticas e suporte até a linha 7 do MOVE original.

As três falhas de full anteriores são registradas no receipt com logs brutos. Suas correções atualizaram a contagem estrita de passos CI, adicionaram NameInterpreterTest aos seletores do reactor histórico sem permitir ausência de testes, e validaram os mesmos fixtures scalar/GOBACK contra o pin W1B atual sem alterar seus bytes/proveniência.
