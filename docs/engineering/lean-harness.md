# LEAN HARNESS / GIT-IS-THE-RECORD

Git, commits, Pull Requests, testes e merge são a fonte de verdade do desenvolvimento.
Para uma mudança normal bastam commit SHA, PR, resultado dos testes, revisão humana
quando aplicável e merge SHA. Não há uma segunda infraestrutura de comprovação.

## Fluxo

1. Implemente em branch dedicada e preserve trabalho concorrente.
2. Execute `python3 -B scripts/harness/lean.py fast`.
3. Para mudança tecnicamente significativa, execute localmente
   `python3 -B scripts/harness/lean.py qualification-local`.
4. Abra PR, obtenha a revisão humana aplicável, faça merge: DONE.

**PR merged AND required technical tests passed = DONE.** Correções documentais
pós-merge são optional cleanup. Não existe bloqueio administrativo de fechamento.
Revisão humana permanece necessária para semântica de produto, contratos públicos,
AIR/SP/CFG, lowering, solver/lattice/dataflow, API cross-repo e arquitetura importante.
Arquivar item, corrigir índice ou typo não exige cerimônia de revisão.
Não fazer merge/auto-merge sem autorização. Sem force-push ou escrita em siblings.

## Validação proporcional

**REMOTE CI = FAST ONLY. FULL REMOTE = NONE**, inclusive workflow_dispatch.
O classificador conservador seleciona DOCS_ONLY para Markdown convencional e
registros documentais de trabalho. Código, scripts, workflows, build, testes,
recursos produtivos, locks e arquivos desconhecidos selecionam CODE_CHANGE.
DOCS_ONLY roda Python e higiene Git, sem Maven/Gradle, bootstrap ou testes pesados.
CODE_CHANGE compila e executa o perfil técnico fixo, com contratos rápidos,
regressão focal e fronteiras arquiteturais. A meta é minutos.

Full é LOCAL / ON-DEMAND e recusa ambiente CI. Sua saída normativa é PASS / FAIL.
Use full para parser/SP, modelo/codec AIR, lowering, CFG, solver/lattice/dataflow,
contratos cross-repo ou núcleo sensível a desempenho. Para docs, lifecycle, testes
pequenos e refatoração não semântica, FAST é suficiente. Mutation, challenges e
E2E de produtores continuam ferramentas locais sob demanda, sem obrigação universal.

## Trabalho e história

O novo work item pode ser um único JSON/YAML com `id`, `title`, `status` e `scope`:

```yaml
id: WORK-EXAMPLE-001
title: Mudança delimitada
status: TODO
scope:
  - harness
```

Status: TODO | IN_PROGRESS | BLOCKED | DONE. `pr` e `notes` são opcionais.
`lean.py close --work <arquivo> --merged --tests-passed` registra DONE após
confirmação desses dois fatos no Git/GitHub/testes; as flags são declarações do
operador, não certificados nem uma consulta remota. O comando recusa fatos ausentes.
Registros legados são aceitos READ_ONLY / BEST_EFFORT sem conversão ou validação
do schema novo. A autorização da sessão e o escopo do PR governam a execução.

`docs/work/registry.json` permanece **NAVIGATION**, útil para encontrar itens.
Valida-se apenas JSON, IDs ativos únicos e caminhos ativos existentes. Índice e
history são documentação, nunca autoridade executável. Mover para history é opcional.
História incompleta não bloqueia FAST, build, início, merge, fechamento ou próximo item.

**NO RECEIPT MAY BLOCK DEVELOPMENT.** Receipts remotos, de qualification, source,
execution e merge sintético; receipt/archive SHA; bundles, tarballs e hash chains;
CP0/CP1/CPn.json e CPn-manifest.yaml deixam de ser requisitos. Não fabricar
compatibilidade. Evidência antiga permanece intacta como história.

O MANIFEST.sha256 global foi removido: replicava integridade já fornecida pelos
blobs/trees Git. Não foi substituído. Source locks, dependency pins e versões de
contrato com valor técnico continuam obrigatórios: repositório + SHA imutável +
contrato/versão. Nunca usar main flutuante para dependência de produto.

Logs temporários ficam em `.harness-results/`, `target/`, `build/` ou `tmp/`, ignorados
pelo Git. Uma falha importante pode merecer pequeno resumo Markdown escrito manualmente.
Esta política substitui requisitos administrativos anteriores; evidência histórica
não é instrução atual. Reporte somente testes realmente executados e seus limites.
