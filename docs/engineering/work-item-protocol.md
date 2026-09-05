# Protocolo de work items

## Estrutura e autoridade

Item ativo fica em `docs/work/active/WORK-CFG-NNN/`, com exatamente cinco arquivos:
`work-item.json`, `spec.md`, `plan.md`, `eval.md`, `state.md`.
JSON mantém a função do manifesto YAML do proleap-poc, com validação por biblioteca
padrão. Não é schema IR. O índice [registry.json](../work/registry.json) registra ativos
com status; docs e metadados devem concordar.

Estados: `active`, `blocked`; `completed` só no histórico. Autorização é separada:
`none`, `discovery`, `implementation`. O gate verifica consistência local, não prova
que uma pessoa realmente deu permissão. Evidência da autorização deve ser registrada.
Nenhum script converte bloqueado em ativo automaticamente.

## Manifesto

Campos: id, backlog_id, title, status, risk, goal, authorization, authorization_evidence,
checkpoint, must_read, related_decisions, related_invariants, evals, source_scope,
test_scope, must_not_change, gates, stop_condition. Fonte/test scope usa paths
relativos ou `planned:` para caminhos ainda inexistentes, claramente distintos.
O discovery não reserva uma classe Java arbitrária como se já fosse decisão aprovada.
Antes de código, substituir reserva por caminho concreto e definir guardas do diff.

## Checkpoints

Discovery: ler estado/código/fontes pertinentes, explicar problema, opções e testes;
preservar código; parar para review. Implementação: só com autorização posterior,
escopo preciso, testes RED e gate real. “Continue” deve ser interpretado no contexto
do checkpoint, nunca como autorização para consumir todo backlog.

## Estado curto

state registra Onde estamos, Verde conhecido, Restante e Descobertas que afetam o
plano. Não é diário de tokens, histórico de cada comando ou cópia dos contratos.
Distinguir gate executado de resultado apenas reportado por CI ou outro agente.

## Encerramento

Promover conhecimento durável para domínio/arquitetura/engenharia, manter evals,
registrar resumo curto em history, remover active e atualizar registry/index/backlog.
Não apagar evidência necessária nem deixar item encerrado roteado como ativo.
Verificação remota de merge é ação separada com fonte confiável; gate offline não
infere estado GitHub. Templates em [templates](../templates/README.md).
