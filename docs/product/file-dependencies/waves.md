# FD-W0–W11 — grafo e execução

**H4 aprovado; core W0–W9/W11 autorizado. W0–W9/W11 QUALIFIED_LOCAL; C06 humano fechado; STOP para revisão final; W10 NOT_AUTHORIZED.**
Cada link abre o item lean executável. Os campos do item + brief + gate indicado
são suficientes; discovery bruto é opcional. Não converter TODO em IN_PROGRESS
por ter escrito o plano. Wave qualificada não é DONE lean antes de merge/testes.
Escopo aprovado na revisão H4: **core N+C = W0–W9 → W11**; W10 conserva a
extensão D para autorização posterior. A numeração histórica não impõe ordem.

| Wave / produto | Depende de contrato/capability | Repos que provavelmente mudam |
| --- | --- | --- |
| [W0](../../work/active/FD-W0.yaml) declaração/binding SP | H4 aprovado; nominal existente | frontend + lower decoder |
| [W1](../../work/active/FD-W1.yaml) vertical/JSON | W0; D-AIR e D-WIRE fechadas antes de emissão | AIR codec + lower + CFG; IR somente com gap normativo; frontend só ajuste bilateral |
| [W2](../../work/active/FD-W2.yaml) operações nativas | W1 transporte/inventário | frontend + lower; CFG se nova necessidade real |
| [W3](../../work/active/FD-W3.yaml) memória/efeitos | W2 operandos; D-EFFECT | frontend + lower; CFG/effects/values por gap demonstrado |
| [W4](../../work/active/FD-W4.yaml) handlers/status | W3 outcome/storage | frontend + lower; CFG se controle atual insuficiente |
| [W5](../../work/active/FD-W5.yaml) SORT/MERGE | W0 SD, W3 efeitos, W4 controle local | frontend + lower; consumer se papéis faltarem |
| [W6](../../work/active/FD-W6.yaml) I-O-CONTROL/auxiliares | W0 declarações, W3 área, W5 sort | frontend + lower; sem dependência de W7/W8 |
| [W7](../../work/active/FD-W7.yaml) nomes computados do core | W1 computed contract, W3–W4, D-DYNAMIC/core | lower + CFG; frontend apenas se faltar fato tipado CICS; independe de D/W10 e W5/W6 |
| [W8](../../work/active/FD-W8.yaml) CICS FILE | W3–W4 efeitos + C-FC para estáticos; W7 somente para computados | frontend + lower + CFG; pode iniciar/qualificar subset literal sem W7; fechamento completo exige computed |
| [W9](../../work/active/FD-W9.yaml) escopos/multi-unit | W0 IDs; W1 associação; integração W5–W8 pertinentes | frontend + lower + agregação CFG |
| [W10](../../work/active/FD-W10.yaml) extensão D posterior | core W11, autorização posterior, W7–W9 e D-D-AUTH | frontend + lower + consumer; AIR só por necessidade; ASSIGN DYNAMIC/captura, Report Writer e APIs |
| [W11](../../work/active/FD-W11.yaml) qualificação final do core | W0–W9 no perfil N+C qualificadas; não depende de W10 | testes/docs/integração; produção só remediar falha demonstrada |

Isto não é uma fila estritamente linear: W5/W6 e W7 formam ramos; W9 pode
preparar testes de escopo logo após W0, mas só qualifica composição final após os
consumers pertinentes. Não delegar ou editar worktrees alheios por causa do grafo.
O agente executa apenas a wave autorizada, mesmo se houver ramo desbloqueado.
`depends_on` registra pré-requisitos para iniciar; `conditional_dependencies` de
W8 registra W7 para os casos computados e fechamento completo, sem bloquear o
ramo estático. W11 não aceita um W8 somente literal como core completo.

## Política comum dos work items

Aplicam-se integralmente os invariantes do [brief](brief.md), os [contratos](contracts.md)
e as regras locais de cada repo. Os itens referenciam essas fontes sem copiá-las.
Desenhar oráculo antes de implementar; specs de tests futuros não são PASS atual.
Parar a wave diante de ambiguidade material, falha CALL inexplicada, autoridade
ausente para MUST, pin inconsistente ou representação dependente de parsing
downstream. Investigar e corrigir no escopo; BLOCKED somente com causa real.
Não renomear gap obrigatório para OUT_OF_PROFILE para fechar a wave.

## Pins e ordem entre repositórios

1. Se D-AIR provar alteração normativa: analysis-ir primeiro, em novo PR persistente
   não vazio; air-java pin normativo imutável depois. Codec apenas pode usar norma atual.
2. Frontend produz SP versionado; lower recebe pin + decoder/admission no mesmo
   checkpoint bilateral. Não repinar lower só para consumir docs de H.
3. Lower depende de air-java; alinhar modelo/codec e contract/version antes de
   atualizar seus pins. Frontend nunca depende do lower.
4. CFG recebe pins AIR/lower/SP realmente exercitados; E2E usa os mesmos commits.
   Mudança apenas no consumer não demanda bump/repin de SP ou AIR.
5. Depois de futura autorização de merge: normativo (se mudou) → AIR → frontend
   (independente de AIR) → lower → CFG. Comparar conteúdo após merge; repin de
   conteúdo equivalente usa gate contratual mínimo e evidência REUSED explícita.

Não modificar analysis-ir por organização de docs nem criar primitivas COBOL na
AIR. Repos sem delta na wave não recebem commit/PR artificial. Não criar PR por
wave. Usar a branch e os Drafts de [estado](state.md); H é seu primeiro checkpoint.

## Evidência e custo

Loop: G1/G2 focais; fronteira: G3 contrato + E2E selecionado; estabilização: FAST
fixo por repo produtivo. Qualification-local em W3/W4 (shared semantics), W8/W10
quando ampliam controle/contrato, e W11 final. As exigências locais prevalecem.
O [catálogo](test-catalog.md) liga casos às waves; [verificação](verification.md)
define comandos, escalada e buckets PASS/FAIL/NOT_RUN/REUSED/BLOCKED.

Condições de parada dos itens delimitam a wave e seus bloqueios técnicos. Se uma
futura autorização cobrir várias waves, registrar o checkpoint e seguir as
dependências sem solicitar nova confirmação mecânica. H4 e revisão final W11
continuam pontos de parada humana explícitos desta campanha.
