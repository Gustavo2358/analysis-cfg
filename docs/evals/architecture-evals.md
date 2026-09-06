# Provas arquiteturais

## Dependências

Na fundação atual, EVAL-CFG-007 prova que a única classe produtiva,
`CfgPreflight`, depende apenas de `air-java`/`java.base`, sem modelo ou validator AIR
duplicado e sem transporte ou frontend. O inventário exato rejeita qualquer segunda
fonte/classe produtiva; packages futuros exigirão evolução deliberada do gate para
provar as relações entre camadas quando elas existirem. O build usa Java 21 sem
preview e o preflight recebe a `Publication` do `air-java`.

## Substituição

EVAL-CFG-008 usa o mesmo BuildCfg em memória e pelo adapter de arquivo. O teste do
kernel roda sem adapters no classpath de teste e sem filesystem/rede. Depois,
launcher de teste injeta outro caller sem modificar core.
Essa prova ocorre depois de `CFG-FIRST`; o binding JSON 1.0.0 presente, ainda DRAFT,
não bloqueia a via em memória e não foi implementado.

## Extensão

EVAL-CFG-009 adiciona uma capability sintética versionada em código de teste, sem
editar o orquestrador. Registro duplicado deve falhar. Falta de handler aplica
fallback uma única vez ou recusa explicitamente. Teste de compatibilidade compara
comportamento das operações antigas antes/depois da extensão.

## Integração Maven

EVAL-CFG-020 compila módulos como DAG e liga produtor sintético → modelo IR → porta
CFG sem CLI/JSON. Integração com lowerer real é teste distinto e posterior. Nenhum
teste deve depender de paths de workspace, symlink de repo vizinho ou versão SNAPSHOT
não rastreada para fingir independência.

## O que existe hoje

O gate `architecture` executa build e quatro testes, exige Surefire não vazio,
inspeciona classfiles/dependency tree/classpath e confirma a chamada exata a
`AirValidator` com `javap` e `jdeps`. Fixtures negativas exercitam representantes de
filesystem, Jackson, Gson, ProLeap, ANTLR, Semantic Product e classe extra; o
inventário exato complementa essa amostra rejeitando expansão produtiva. Uma mutação
real com filesystem também foi observada RED no WORK-CFG-002. Isso implementa
EVAL-CFG-007 e EVAL-CFG-024 somente para a fundação. EVAL-CFG-008/009/020 e as
provas do builder permanecem planejadas.

EVAL-CFG-026 reserva o micro-E2E externo `cobol-semantic-product.json` →
`cobol-lower` → `air-java Publication` → CFG-FIRST. O lowerer não importa o port
Java do frontend. O trecho CFG recebe somente `Publication`/`Return`; o mapeamento
de GOBACK pertence ao frontend/lowerer e é verificado separadamente.
