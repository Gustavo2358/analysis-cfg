# FD-W0–W11 — grafo e execução

**Todas TODO. H4 requer revisão humana; esta sessão não inicia W0.**
Cada link abre o item lean executável. Os campos do item + brief + gate indicado
são suficientes; discovery bruto é opcional. Não converter TODO em IN_PROGRESS
por ter escrito o plano. Wave qualificada não é DONE lean antes de merge/testes.

| Wave / produto | Depende de contrato/capability | Repos que provavelmente mudam |
| --- | --- | --- |
| [W0](../../work/file-dependencies/FD-W0.yaml) declaração/binding SP | H4 aprovado; nominal existente | frontend + lower decoder |
| [W1](../../work/file-dependencies/FD-W1.yaml) vertical/JSON | W0; D-AIR e D-WIRE fechadas antes de emissão | AIR codec + lower + CFG; IR somente com gap normativo; frontend só ajuste bilateral |
| [W2](../../work/file-dependencies/FD-W2.yaml) operações nativas | W1 transporte/inventário | frontend + lower; CFG se nova necessidade real |
| [W3](../../work/file-dependencies/FD-W3.yaml) memória/efeitos | W2 operandos; D-EFFECT | frontend + lower; CFG/effects/values por gap demonstrado |
| [W4](../../work/file-dependencies/FD-W4.yaml) handlers/status | W3 outcome/storage | frontend + lower; CFG se controle atual insuficiente |
| [W5](../../work/file-dependencies/FD-W5.yaml) SORT/MERGE | W0 SD, W3 efeitos, W4 controle local | frontend + lower; consumer se papéis faltarem |
| [W6](../../work/file-dependencies/FD-W6.yaml) I-O-CONTROL/auxiliares | W0 declarações, W3 área, W5 sort | frontend + lower; sem dependência de W7/W8 |
| [W7](../../work/file-dependencies/FD-W7.yaml) nomes dinâmicos | W1 computed contract, W3–W4, D-DYNAMIC | frontend + lower + CFG; independe de W5/W6 |
| [W8](../../work/file-dependencies/FD-W8.yaml) CICS FILE | W3–W4 efeitos; W7 consultas; C-FC | frontend + lower + CFG; CICS literal não precisa esperar valores para desenvolvimento focal |
| [W9](../../work/file-dependencies/FD-W9.yaml) escopos/multi-unit | W0 IDs; W1 associação; integração W5–W8 pertinentes | frontend + lower + agregação CFG |
| [W10](../../work/file-dependencies/FD-W10.yaml) extensões/APIs | W7–W9, autoridade D-v1 | frontend + lower + consumer; AIR só por necessidade |
| [W11](../../work/file-dependencies/FD-W11.yaml) qualificação final | W0–W10 obrigatórias qualificadas | testes/docs/integração; produção só remediar falha demonstrada |

Isto não é uma fila estritamente linear: W5/W6 e W7 formam ramos; W9 pode
preparar testes de escopo logo após W0, mas só qualifica composição final após os
consumers pertinentes. Não delegar ou editar worktrees alheios por causa do grafo.
O agente executa apenas a wave autorizada, mesmo se houver ramo desbloqueado.

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
