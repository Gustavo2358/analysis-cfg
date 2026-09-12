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

## Provas da remediação

W1dInvokeWireTest exige cinco métodos: Invoke e seus dois tokens sob 2.0.0; dois
goldens v1 byte-exact; todos os kinds antigos com IDs enganosos; Invoke sem
transições; domínio publicado separado da AIR retida e writer sem estado sticky.
RED observado: 5 testes, 3 failures de assertions, 0 errors/skips. GREEN do
transporte CFG: 42 métodos. Nenhum expected histórico foi alterado.

O oracle Python independente rejeita combinação versão/token inválida, versão/token
desconhecido, upgrade desnecessário e chave duplicada. O E2E real exige v2 para
Invoke, tokens corretos e ausência de PROGA no CFG; dependency permanece produto separado.
O gate W1D passa a exigir 38 testes Java (33 analysis + 5 CFG wire) e quatro
testes do oracle dependency, sem reduzir a campanha histórica de mutações.

Guards de orquestração verificam planos full local/remoto iguais, triggers fixos,
Fast sem producers/E2E/performance/mutations e inventário de 286 métodos. Contracasos
de receipt verificam exit 7, log bruto/hash, FAIL e próximas fases NOT_RUN, fonte
suja e remoto sem workflow_dispatch. O Fast mantém boundaries compilados completos.

O pacote [remediação](../../evidence/WORK-CFG-033/remediation/README.md) contém os
resultados de desenvolvimento. O receipt final da Full Qualification e os receipts
do Fast remoto são vinculados ao commit/tree no handoff externo e no PR após a
execução; não são inferidos da existência de scripts nem antecipados neste documento.

Fast local observado: PASS em 245.893 s, 286 métodos, zero skips, todos os
boundaries. Challenge de versão: três mutantes compiláveis detectados por
assertions (3/3/1 failures), restauração byte-exact e segundo GREEN. Receipts e
primeiro Fast falho estão preservados no pacote de remediação.

O HEAD 16d4397 passou Full Qualification local (707.316 s), mas os Fast CIs
remotos 34657400477/34657403312 falharam no inventário, antes de Maven: fontes
AIR exportadas sob .harness-results foram confundidas com fontes do reactor.
A correção separa stores de build na raiz; três testes provam essa separação e
a detecção de fontes extras reais, inclusive com nome interno .harness-results.
Fast com a mesma disposição do GitHub: PASS em 273.324 s, 286 métodos
e boundaries completos. Evidência em
[managed-output-fix](../../evidence/WORK-CFG-033/remediation/managed-output-fix/README.md).
O novo HEAD será qualificado integralmente antes do novo push; o receipt de
16d4397 permanece histórico e não é atribuído ao novo commit.
