# CP5 — evidência da preparação

**HARNESS PREPARATION: ready for human review, with CP5-F01 open.**
Somente docs/harness/CI/lifecycle; nenhuma engine ou módulo/POM CP5 implementado.
[Baseline e autoridades](baseline.json), [recibo local](local-validation.json),
[auditoria de refs CP4E](result-source-audit.json), [lifecycle/branch/PR](../../cp5-lifecycle.json).

## Autoridade, revisão e escopo

Handoff 1.0 e discovery revisado lidos integralmente. Hashes no baseline; revisão
com propagação incremental/R1, H4 por propriedades/R2, Maven AIR direto e cinco
Waves sequenciais. Pedido humano de 09/09/2026 confirma APPROVED H1–H7/R1/R2 e
somente preparação. Mesmo branch/PR draft até W5, review/autorização entre Waves,
sem merge intermediário. W1–W5 NOT STARTED/NOT AUTHORIZED.

Main limpa antes/depois do fetch, ec525cbbad96d70c9663faa88e2672148fa8ee71. PR #11
MERGED confirmado via GitHub; sem CP5 existente. Fast-forward seguro antes de criar
feat/cp5-dataflow-engine. Falhas iniciais SSH config/DNS do sandbox foram contornadas
com ssh -F /dev/null em acesso autorizado, sem alterar configuração SSH.

WORK-CFG-028 alocado após 027; BACKLOG-CFG-020 reutilizado como umbrella focal.
012/019 e demais follow-ups reutilizados, nenhum backlog adicional criado ou
iniciado. 027 arquivado pelo merge confirmado, cinco documentos antigos preservados
no histórico; evidence 027 só recebeu correção de link. ADR-0010–0013 aceitos,
invariantes 030–034, EVAL-CFG-033 de harness; EVAL-CFG-034–038 planned.

## Gates locais executados

JDK Temurin 21.0.12.1+1, Maven 3.9.16, Python conforme recibo. air-java pinado
compilado por git archive em /tmp e repo Maven isolado inicialmente vazio, instalado
com clean install. Hashes JAR compilado/instalado/consumido iguais, fonte irmã intacta.

| Comando/gate | Resultado e alcance |
| --- | --- |
| check-full.sh → fast | PASS docs + 47 testes anteriores + 35 CP5 = 82 métodos nominais; subcasos parametrizados não inflados na contagem |
| check-full.sh → architecture | PASS 102 testes kernel; 22 classfiles, Java major 65, inventários/DAG/bytecode/porta e transporte preservados; CP5-F01 explicitamente reportado |
| check-full.sh → semantic | PASS 84 métodos obrigatórios, zero skips |
| check-full.sh → performance | UNAVAILABLE/exit 3, hook inexistente; full não é PASS |
| check-integration.sh separado | PASS 5 suítes/37 métodos nominais, além das regressões kernel |
| mvn -B -ntp clean verify | PASS 139 testes (102 kernel + 37 transporte), nenhum Java/POM alterado |
| check_cp5_gate.py / testes de roteamento | 20 combinações categoria/Wave retornam exit 3; sem execução de engine |
| check_scope.py / MANIFEST / diff --check | baseline/pins/Java/POM/diff revisados; recibo final no mesmo PR após atualização de manifest |

Logs brutos preservados sem edição em `logs/*.log.gz`, com hashes dos bytes
originais e arquivos gzip no recibo. Compressão lossless verificada. Builds/caches/
toolchain ficam somente em /tmp, não versionados.

## Falsificações e incidentes honestos

A suíte CP5 revisada tem 37 testes (84 com os 47 existentes), incluindo
entrypoint CLI real do validator e drift do contrato. O full anterior executou 35.
Os testes novos incluem cada métrica/challenge obrigatório removido, S4b ausente,
Wave iniciada/autorizada, review/draft/auto-start adulterados, Java/POM novos/alterados/
ausentes, bytecode vendorizado, inventory, hook/resultado falso, scopes e owners.
Contracasos operam em cópias temporárias e positivo restaurado; **não** são mutações
compiláveis da engine, que ainda não existe. Campanhas produtivas permanecem nulas.

A primeira suíte CP5 falhou porque o detector estrito encontrou o launcher atual
sem AIR direto. O teste positivo foi isolado no POM sintético correto; a dívida real
continua detectada e testada separadamente, não removida da evidência. Um scope run
retornou 1 por manifest desatualizado após adicionar result-source-audit.json;
manifest é regenerado após revisão e scope reexecutado. Ambos logs preservados.

## Finding para review e limites

**CP5-F01 OPEN_FOR_HUMAN_REVIEW:** AnalysisCfg importa Publication/AirJsonException
mas cfg-launcher declara somente adapters/kernel. Harness não pode alterar POM de
produto nesta entrega. A rota de preparação permite apenas bytes exatos do launcher
CP4 e reporta a dívida; detector estrito continua recusando transitividade. Review W1
deve decidir autorizar correção declarativa + inventário existente. H1 não foi reaberto.
[Detalhe/ownership](../../cp5-follow-ups.md).

O exemplo analysis-dataflow-result/1.0.0 é NOT_EXECUTED, statistics unavailable;
11 refs de IDs/owners foram conferidas na AIR CP4E sem calcular values. Sem PASS
de solver/PossibleValues, performance ou E2E novo. BACKLOG-LOWER-017/018 e multibase/
gaps/validação repetida externos não foram iniciados; não qualificar grandes programas.

[Integridade dos siblings](sibling-integrity.json): HEAD/branch/status iguais ao
snapshot inicial; hashes handoff/discovery conferidos, nenhum sibling modificado.

## HEAD final e CI remota

Depois do último push, registrar HEAD exato e recibo de checks no **mesmo PR**
apontado pelo lifecycle, evitando hash autorreferente no próprio commit. CI normal
executa fast (inclui harness CP5), scope/manifest, architecture, semantic e integration.
PASS local/remote, UNAVAILABLE e NOT_APPLICABLE_YET são distintos. Não há CI produtiva
de performance/solver CP5. Commit de metadados não muda Java/POM; fast/scope são
reexecutados no HEAD final e os checks remotos devem corresponder a esse SHA.

Nenhum merge/auto-merge/ready realizado. Parar para review humano; nenhuma Wave inicia.
