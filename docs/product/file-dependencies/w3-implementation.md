# FD-W3 — efeitos e storage qualificados localmente

Checkpoint QUALIFIED_LOCAL; pins/gates em [state](state.md). N-LR, source-only;
nenhuma mudança AIR/IR ou produção CFG foi necessária.

Frontend publica SP2.24/fileInventory1.3/storage1.8: FD/SD no storage geral,
múltiplos01 e SAME RECORD AREA por identidade, RECORD DEPENDING, prova geral de
alocação local e efeitos por fase/outcome. As regras lidas da revisão IBM fixada
estão em [D-EFFECT](d-effect.md). Efeitos desconhecidos de conteúdo/validade não
viram MUST de todo buffer. INTO é posterior ao READ/status; FROM precede I/O.

O lower admite os fatos em memória/JSON com validação bilateral de identidade,
geometria, ordem, destinatários e prova de escrita forte. Emite AIR geral:
COPY/fit antes do invoke, branches de outcome, MAY localizado e MUST dos bytes
admitidos. O endereço INTO não aparece como result place pré-invoke. Continuação
aberta pode alcançar entradas do fonte/saídas, sem salto para fases internas.
O controle completo de handlers/USE e os outcomes possíveis continuam na W4.
O bound provisório de entradas do fonte pode repetir O(FILE × statements) IDs no
JSON; não há cutoff. Esse custo do controle aberto deve ser revisto na W4 e
medido novamente no corpus final, sem prometer SLA.

FROM com alias não provado conserva leitura e MAY local; FROM não resolvido abre
o bound de leitura e o gap nominal, preservando o bound de escrita demonstrado.
Nenhum dos dois cria dependência de leitura do arquivo proprietário da origem.
Status/key/length numéricos foram exercitados sem assumir codec textual ou MUST.

Oráculos: frontend FileStorageLayoutTest, FileMemoryEffectsTest,
FileIoEffectPlanTest e FileEffectsContractTest; lower FileMemoryEffectsSuite;
CFG FileIoOutcomeOracleTest (O1–O5 manuais, validator/codec/consumer). O oracle
manual possui uma prova explícita de overwrite do prefixo de quatro bytes; o
lower não infere essa prova a partir do verbo READ. C-VALUES protege evidência,
joins e intervalos; C-DEP preserva CALL/FILE independentes. E-SELECTED W3 usa os
14 fontes W2 mais seis fontes manuais de memória, cada um duas vezes, nos pins
finais (PASS,20 fontes ×2).

Q-SHARED revelou falhas históricas, reproduzidas no HEAD W2 isolado: expectativas
de grammar/roles/facade e VALUE de COPY com origem aproximada. Os primeiros
foram reconciliados com contratos já existentes; o segundo conserva UNKNOWN
local com razão de proveniência e não aborta o produto. O guard de naming agora
aceita o identificador exato do repositório em documentação, sem modificar
evidências históricas; contracasos continuam recusando nomes de produto antigos.
Lower AirOutputSuite foi ajustada à distinção já existente entre publicação
PARTIAL e falha sem publicação, mantendo o teste real da CLI PARTIAL.

Sem cutoff ou solver de FILE. Dimensões de nome/owner e de efeito/controle são
independentes. Sem SLA, precisão/recall ou suposição de recurso físico/runtime.
Comandos, SHAs, resultados finais e logs ficam no handoff E2E da wave.
