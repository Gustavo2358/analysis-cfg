# WORK-CFG-028 — evidência da remediação pós-auditoria

Preparação anterior/discovery/B1 APPROVED; autorização humana de 2026-09-09 somente
para remediações A–I. Checkpoint CP5_POST_AUDIT_HARNESS_REMEDIATION aguarda review.
Branch feat/cp5-dataflow-engine, PR #12 OPEN/DRAFT, starting HEAD
feb79d59cc72d7dbc6269b7cacfb74db58f90867, base main
ec525cbbad96d70c9663faa88e2672148fa8ee71. Sem autorização de qualquer Wave.

## Autoridades e confronto

[Baseline](baseline.json) registra SHAs/status dos seis repositórios e a auditoria
lida integralmente após handoff/discovery, sem cópia do documento externo.
Fonte: artefatos-e2e/cp5/cp5-architectural-audit.md, SHA-256
32d4542adcf31445cf35196a5f6d2496e8e3962c0bfa9d10c26615f6ceea5bc1.

No HEAD inicial, CfgGraph verifica transições presentes mas não inventário obrigatório;
CfgBuildResult público verifica status/preflight/metadados, não é certificado.
CoreCfgProjection materializa Sequence/Entry/exit e transições por activationEntry,
inclusive órfãos e Branch com destinos iguais. BuildCfg/CfgBuildCoordinator são a
fronteira suportada e não serão reexecutados pela sessão. CfgTransition/CfgNode
confirmam correlação contextual e fontes tipadas com owners, sem garantia de fonte
canônica só pelo ID. A/W1 acrescenta verificação linear de integridade no índice.

Em air-java ce530a7e17ab12b23c48f29425f503ff920b09fb, Sequence conserva instruções
ordenadas e terminador; Entries/EntryState conserva seeds de ativação; Operations.Invoke
contém target/args/results/signature/effectOperands/effectBound/outcomes/contract,
mas CoreCfgProjection não admite esse terminador. Memory.Cell não tem layout físico;
ViewBinding/Region/Codec/ByteRange distinguem bytes. Proofs distingue sameDomain
com escopo e DisjointStorage, sem fornecer automaticamente correlação de valores.

AIR normativa pinada 122ce54e1b9ef9b00646f93ece409ca8b63bc933: confrontados 01 §5,
03 (Cell, regiões/codecs, seeds e effects), 04 §7, 05 §5/§7, 08 §3/§5. O diff local
entre o pin e HEAD 51b4d9a para especificacao/conformidade é vazio; nenhum pin/sibling
alterado. Auditoria não substitui essas autoridades. Experimentos relatados por ela
não são testes executados da futura engine Java nesta remediação.

## Remediações aceitas e realizadas

A–I estão em [contrato normativo local](../../../../architecture/cp5-post-audit.md)
e [manifest verificável](../../../../evals/cp5/post-audit-contracts.json): integridade,
replay por direção, effects antes de fixpoint, limites GRBE/contexto, completion por
fase, três oracles, qualidade e recibo de checkout. Roadmap/lifecycle/ADRs qualificam
esses limites preservando arquitetura, H4 e cinco Waves. B1 não foi sobrescrito.
CP5-F01 permanece NONBLOCKING_FOLLOW_UP_W1 com exceção dos bytes exatos CP4.

## Validação

TDD: suite anterior com dois novos testes executou 49 testes e falhou em 10 assertions
nominais (ausência A–I e completion). Após implementação, 59 testes CP5 passaram,
incluindo remoção individual/restauração de obrigações e Git temporário para recibos.
[Recibos locais](gates.json) guardam comandos/exits, logs brutos comprimidos sem
perda e hashes raw/gzip. [Ambiente](environment.log): Temurin 21.0.12.1+1, Maven
3.9.16, Python 3.14.4, repositório Maven isolado com air-java pinado já instalado.

| Gate | Resultado observado |
| --- | --- |
| fast | PASS; 47 testes originais + 59 CP5 = 106 |
| architecture | PASS; inventários/bytecode/Maven, 102 testes kernel |
| semantic | PASS; 84 métodos nominais, zero skips |
| integration | PASS; 5 suítes / 37 métodos |
| Maven clean verify | PASS; 139 testes, zero falhas/erros/skips |
| scope/manifest/diff | PASS; Java/POM/fixtures/pins inalterados e escopo focal desde feb79d59 |
| performance / full | UNAVAILABLE, exit 3; full parou na ausência do hook performance |

O primeiro fast detectou título obrigatório Fatiamento renomeado em plan.md;
a seção foi restaurada e fast repetido com sucesso. Log da falha foi preservado.
O full repete fast/architecture/semantic por contrato do runner; integration foi
executado separadamente porque performance interrompe full. Nenhum PASS de engine.
A segunda testemunha backward tem IN={Y} e OUT={}, matando âncora errada
independentemente do oracle original def X; use X, que mata ordem errada.

[Hashes do código/AIR confrontados](code-and-normative-hashes.json) e
[integridade dos siblings](sibling-integrity.json): HEAD/branch/status preservados,
incluindo cp5/ e checkpoint-4e/ já não versionados em artefatos-e2e. Inventário
tracked/untracked comparou também bytes de 1983 arquivos versionáveis dos siblings.
[Arquivos alterados](files-changed.json) identifica o delta desta remediação; escopo
agregado do PR conserva edições históricas de AGENTS/ARCHITECTURE, que esta rodada
não modificou. Relatórios brutos completos adicionais permanecem em /tmp/cp5-audit-remediation.

## Recibo remoto final

PR #12 foi reconsultado antes do commit/push: OPEN, DRAFT, auto_merge=null,
head feb79d59cc72d7dbc6269b7cacfb74db58f90867 e base ec525cbbad96d70c9663faa88e2672148fa8ee71.
O último HEAD e a conclusão não podem ser conhecidos dentro do próprio commit.
Após o último push, o recibo final é acrescentado no mesmo PR, conforme lifecycle,
com PR head/base, checkout real, ambas as árvores, run ID/event/conclusion e link
para o artifact ci-source-receipt. Recibo local ignorado em
.harness-results/WORK-CFG-028/architectural-audit-remediation/remote-ci.json.
A afirmação antiga baseada apenas no head metadata não prova checkout literal;
merge sintético com árvore igual será descrito como conteúdo-fonte equivalente ao HEAD.

## Limites

Somente harness/docs/contracts/scripts/CI; nenhum Java, POM, engine ou writer.
Hooks de engine/oracles concretos reais e performance permanecem UNAVAILABLE;
full CP5 product não recebe PASS. authorized_wave=null; W1–W5 NOT_STARTED/NOT_AUTHORIZED.
Nenhum sibling ou fixture de produto alterado. Último SHA/CI remoto será registrado
no mesmo PR após push, distinguindo checkout literal de equivalência de árvore,
sem commit autorreferente. Parar para review humano após essa evidência.
