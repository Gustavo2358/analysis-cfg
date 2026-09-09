# CP5 — protocolo de challenges

Integra o ritual de [testes existente](testing.md) e o padrão de
`scripts/project/challenge_scalar.py`/`challenge_transport.py`. Nenhum mutante de
engine foi executado na preparação: classes ainda não existem.
[Inventário declarativo](../evals/cp5/challenges.json), com ativação por Wave e
oracle/probe decisivo. IDs locais de challenge não são work items novos.

## Ciclo de evidência obrigatório

1. Baseline GREEN no HEAD/árvore e gate nominal definidos; salvar fontes/hash/diff.
2. Aplicar uma mutação focal em alvo concreto; compilar com sucesso. Erro incidental
   de build/configuração não prova RED semântico/performance. Dependência proibida
   pode legitimamente falhar no gate arquitetural após compilação bem-sucedida.
3. Executar gate esperado; exigir exit não zero **e** diagnóstico/asserção nominal
   relacionado à mutação. Timeout/OOM incidental ou outra falha não conta.
4. Restaurar byte-exact em finally; comparar inventário/hashes, inclusive em falha.
5. Segundo GREEN no mesmo conteúdo restaurado; preservar logs e manifest completos.

Mutante sobrevivente exige corrigir oracle com regressão permanente; não alterar
log passado nem enfraquecer semântica. Análises/oracles lentos test-only podem usar
recomposição/mapas completos em grafos pequenos; não entram em produção.

## Ativação segura

Cada Wave substitui target/hook nulos por paths/classes reais e registra command,
compile command, expected diagnostic e referência do teste. Guards de inventory,
javap/jdeps/imports complementam comportamento; regex não prova complexidade.
S4b deve matar recomposição semanticamente correta com agenda adversa; S3 confronta
contadores com alocação/retention externa; S5/S6 usam solver/domínio reais em W4.
Não criar Java de mutantes contra APIs futuras só para preencher inventário.

B1 acrescenta dois challenges W3/S6 de queries: `unsupported-query-aborts-batch`
e `unsupported-query-disappears`. O oracle independente solicita before(Return)
e after(Return) sobre o mesmo subject: run STABLE, primeira query VALUE {PROGA},
segunda UNSUPPORTED_POINT com motivo e value=null. Comparar o plano completo às
respostas para matar omissão/substituição; checar só as respostas presentes não basta.
Na preparação, contracasos alteram o snapshot em cópias temporárias e exigem RED
do validator com restauração byte-exact/segundo GREEN. Hooks/alvos produtivos seguem
unavailable: isso não é campanha de mutantes compiláveis da engine.

Recibo de campanha: source HEAD e hash da árvore, baseline command/exit/log/hash,
mutant ID/diff, compile command/exit/log, gate/diagnostic/exit/log, hashes before/after,
restore byte-exact, second GREEN command/exit/log. Report completo e falhas são
preservados em evidence do work item; raw logs em .harness-results com hashes no
recibo. Ausente/não executado/skip não é PASS nem RED legítimo.

## Arquitetura futura

[Plano de dependências](../evals/cp5/architecture.json) prepara o DAG, packages e
inventários. W1 vincula jdeps/javap e sources/classfiles exatos do módulo; W2 amplia
solver/SPI sem AIR concreto; W3 values; W4 API restrita de consumers; W5 composição.
Um módulo que importa tipo AIR sem dependência direta air-java deve falhar, mesmo
compilando por transitividade: contracaso Maven específico no inventário.
Sem freeze de nomes de container/policy/default: evolução legítima dos inventários
acompanha propriedades e evidências aprovadas.

CP5-F01 registra o launcher preexistente com dependências AIR transitivas. O
contracaso do harness usa POM/source sintéticos em diretório temporário e não afirma
compilação de engine. A prova Maven compilável será ativada em W1; o detector estrito
já detecta a dívida existente, cuja exceção de preparação exige bytes do baseline.
[Detalhe e parada para review](../work/cp5-follow-ups.md#cp5-f01--launcher-com-dependência-air-transitiva-no-baseline).

## Remediação pós-auditoria

S11/W1 cobre missing required edge/Sequence, fonte substituída/estrangeira e contexto
incorreto/duplicado. S12/W2–W3 cobre replay backward com IN ou ordem forward; witness
manual def X; use X. S13/W2–W3 exige oracle concreto finito independente para matar
transfer errado compartilhado pelos dois solvers abstratos. S14/W3–W5 distingue
fixpoint, replay, consumers e confirmação de output; S15/W3–W5 detecta ganho aparente
por perda de candidatos/unsupported-everything. Invoke desconhecido e reparo de state
por consumer permanecem POST_CP5_INVOKE_EFFECTS, sem target/hook ou Wave fictícia.
[Obrigações A–I](../evals/cp5/post-audit-contracts.json) e detalhes em
[contrato pós-auditoria](../architecture/cp5-post-audit.md).

Self-validation atual remove cada obrigação separadamente em cópia descartável,
exige RED nominal, restaura bytes e exige segundo GREEN. Também altera snapshot de
completion e o recibo de CI, inclusive merge sintético com árvore idêntica e claim
falso de checkout literal. São challenges de harness, não mutantes de engine.

F2/F3 preservam `prepared-payload-certifies-own-delivery` (W5/S14) e
`observation-failure-blocks-independent-consumer` (W4/S14). Witnesses de harness
exigem recibo externo FAILED sem alterar payload e StructuralConsumer COMPLETE após
query-batch FAILED controlado. Batches independentes e consumers sem análise ou
dependentes apenas do solver conservam seus contracasos.

CORE-SIZE-001 supersede o antigo `unbounded-values`: o substituto
`non-convergent-semantic-domain` protege convergência matemática, representação
eficiente e ausência de truncagem; um conjunto grande finito é válido. S7 preserva
todos os literais e S16 exige N/2N/4N admitidos em cada dimensão devida na Wave.
Os novos challenges de size admission, candidatos, queries, visitas, output e
OOM mapping estão no inventário; hooks/targets seguem NOT_AVAILABLE_UNTIL_IMPLEMENTED.

O guard `cp5_size_contract.py` verifica campos e comportamentos por papel: critérios
de admissão, identidade semântica da AnalysisKey, término do solver, enumeração de
valores, partialidade, composição e fronteira externa. Não proíbe palavras como max:
maxWorklistSize e maxSparseBindings permanecem métricas obrigatórias. Testes mutam
manifestos parseáveis e respostas de review contra um oracle independente do input;
N/N+1 que abre o modelo ou altera admissão deve dar RED. Isso verifica o harness,
não substitui oracles de produto nas Waves. Não há solver Python ou mutante de
classe futura. [Decisão](../architecture/decisions/ADR-0014.md).
