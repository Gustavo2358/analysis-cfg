# Evidência de discovery CP6

**DISCOVERY / NOT IMPLEMENTED.** [Relatório](../../../architecture/cp6-call-dependency-discovery.md).
Todas as execuções foram probes descartáveis ou testes já existentes. Nenhum teste
de API futura ou arquivo produtivo foi adicionado. Códigos dos probes são texto de
evidência `.java.txt`, compilados fora do produto.

## Receipts

- [Discovery baseline](discovery-baseline.json): cinco commits/trees e clean status
  inicial; archives exatos e worktree isolada.
- [Receipt runtime original](frozen-runtime-receipt.json): SHA-256
  `4f61539c7bc09120ef05abf9efb818e61870097b506e173eaed38735bba331e0`.
- [Adendo documental PRECP6-F1](frozen-documentation-addendum.json): ponte entre
  runtime 89ee2ca e head documental obrigatório e9daaed; bytes preservados.
- [Producer build receipt congelado](raw/frozen-producers.json): main/classpath,
  source pins e SHA-256 dos JARs. Todos os JARs do frontend/lower foram conferidos
  contra esse receipt antes de executar os probes; não foram recompilados siblings.

## Execuções e interpretação

| Evidência | Resultado factual | O que não prova |
| --- | --- | --- |
| [frontend/lower raw](raw/frontend-lower-probes.tar.gz) | 5 frontend exits 0; 5 lower exits 4 UNSUPPORTED_SLICE | Não existe CP6 E2E PASS nem AIR CALL |
| [inventário dos bytes extraídos](raw/frontend-lower-files.json) | Hashes de cada COBOL, SP, AST/resolution, expanded source e logs | Não reescreve outputs para um oracle esperado |
| [PointProbe source](raw/PointProbe.java.txt), [log](raw/point-probe.log.gz) | BEFORE middle=A; AFTER middle=B; BEFORE Return=C; supports first/middle/last; uma Sequence replayed | Não é CALL lowering; só APIs genéricas existentes |
| [InvokeProbe source](raw/InvokeProbe.java.txt), [log](raw/invoke-probe.log.gz) | Fixture frozen KnownContract: validator STRUCTURALLY_VALID + I-56; codec IMPLEMENTATION_LIMIT; política interna isolada recusa INCOMPLETE_VALIDATION; CFG UNSUPPORTED_INPUT | Não valida semântica COBOL da fixture nem simula transporte implementado |
| [Probe executions](raw/probe-executions.json), [compilação](raw/probe-compilation.log.gz) | javac --release 21 e os dois probes exit 0; frozen air-java test sources compilados fora do produto | Reflection é ferramenta diagnóstica temporária, não dependência do produto |
| [Regressões existentes](raw/existing-regression-tests.log.gz), [contagens](raw/existing-tests-summary.json) | mvn offline -pl analysis-values -am test: 212 testes, zero failure/error/skip; cfg-kernel 108, analysis-kernel 44, analysis-values 60 | Subset local não substitui CI W1–W5 nem prova Invoke |

O tar conserva também tentativas iniciais falhas: `failed-jar-launch.json` registra
`java -jar` recusado por ausência de Main-Class; logs stdout/stderr correspondentes
foram mantidos. O run válido usa a classe principal explícita e classpath do receipt,
registrado em `runs.json` dentro do tar. Não contar a falha de launcher como falha
semântica do produto nem apagá-la.

Uma seleção inicial de testes por `-Dtest` falhou no módulo cfg-kernel porque não
havia teste com os nomes escolhidos e o POM exige failIfNoSpecifiedTests. O
[log dessa tentativa](raw/existing-values-tests.log.gz) é preservado. O rerun válido
executou o reactor até analysis-values sem filtro de nomes, com Maven repo isolado
copiado do cache congelado. Nenhum cache, JAR ou classfile é versionado aqui.

Os probes usam fixture COBOL com margens do formato aceito pelo frontend. `dynamic-x8`
é o oracle conceitual exato; `dynamic-x5-control` é exclusivamente diagnóstico de
admissão do MOVE, nunca substituto do caso solicitado. `using-modes` verifica AST e
perdas do SP; `multi-path` verifica controle/bindings e lacunas antes do lower.

## Resultados documentais

Trace, gaps, contrato de dependency, efeitos, waves e oráculos são propostas para
review, não cobertura nova. Gates e identidade final da entrega ficam no
[estado ativo](../../active/WORK-CFG-032/state.md) e no PR draft. Os logs de [docs](raw/docs-gate.log.gz) e [fast](raw/fast-gate.log.gz), com
[comandos e exits](raw/gate-executions.json), registram PASS; CI deve ser lida pelo HEAD exato publicado.
Nenhum merge, auto-merge ou início de W1 está autorizado.

O [audit dos checkouts ocupados](checkout-audit.json) confirma os cinco produtos
limpos e nos pins congelados. O repositório E2E conserva seus diretórios locais
não rastreados existentes; eles não foram editados.

O gate scope usa seu closeout histórico como base e imprime “no CP6”. Essa frase
continua significando ausência de implementação CP6; o work item de discovery
ativo é validado pelo harness. Nenhum checker foi enfraquecido.
