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

## Confidential incident data

Real or corporate identifiers observed during local incident investigation must
never become fixtures, tests, documentation, evidence or hardcoded regression
data. Replace them with synthetic representatives before versioning; do not
retain real-to-synthetic mappings. Confidential real-case evidence stays local.
Git may contain only non-sensitive aggregate observations. Real identifiers must
not be versioned, including in commit history or PR discussion.

## Dependency Preservation Principle

A failure to prove completeness, physical identity, exact value, or exact reachability
MUST NOT by itself remove dependency candidates supported by weaker valid evidence.
Unknown augments known information. Represent uncertainty through remainder, precision,
coverage, gaps, or analysis status. Remove a candidate only with positive evidence
that excludes it from the modeled site. Empty candidates plus unknown remainder is
appropriate only when no supported candidate is known.

Forbidden when semantic support exists: `if strongProofFails: return emptyUnknown()`;
also forbidden: replacing a known semantic target with an unstructured Unknown that
prevents fallback. A nominal Read does not assert physical allocation or independence.
Physical/dataflow proof may refine weaker evidence. Under
[positive-memory W1](../domain/positive-memory-topology.md), missing source-fidelity
proof is coverage; only uncertainty inside the supported model opens remainder.
This rule does not authorize lexical candidate scans, invented transfers, or closure.

Consumer/review checklist:

- [ ] Known candidates survive an open set and PARTIAL analysis.
- [ ] Unknown remainder complements candidates instead of replacing them.
- [ ] An unsupported local feature removes only the precision it invalidates.
- [ ] Candidate removal has positive exclusion evidence.
- [ ] Tests cover known + unknown and weakened physical metadata.
- [ ] Tests exclude a syntactic literal that cannot reach the site.
- [ ] Tests cover truly unknown → empty + remainder.

The executable regression and boundary contract are described in
[dependency preservation](../domain/dependency-preservation.md).

### VALUE under ordinary UNKNOWN entry

A WORKING-STORAGE VALUE declaration alone does not prove the current value at a
call site, persistent storage reinitialization, or a closed candidate set. It MUST
NOT be collected directly by a dependency consumer. The current RF/EP contract
nevertheless admits a recognized declarative value as an entry possibility
(`DECLARATIVE_POSSIBILITY`, `POSSIBLE_LITERAL_BYTES`/`POSSIBLE_LOGICAL_TEXT`) with
mandatory lifecycle remainder, without requiring invariance. Only the existing
entry/value solver can propagate that evidence to BEFORE(site); positive overwrite
or unreachability may exclude it. A provider must support the published representation;
byte evidence does not silently enable physical propagation in logical-only mode.
INITIAL or valid entry/invariance proof can strengthen precision under its own
preconditions; it is not required for a supported entry possibility. The historical
DVI “ordinary UNKNOWN has no VALUE candidate” restriction is superseded by RF/EP;
it must not be reintroduced as a blanket gate. See the producer's
`docs/domain/evidence-preserving-entry.md` and `RecallFirstEntryTest`.
