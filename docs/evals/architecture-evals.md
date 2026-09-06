# Provas arquiteturais

## Dependências

EVAL-CFG-007 deve provar no bytecode que domain não depende de application/adapters,
application não depende de adapters/launcher e kernel depende do artefato
`air-java`, sem modelo/validator AIR duplicado. Testes detectam tipos de
infraestrutura, ProLeap/ANTLR/Semantic Product, JSON/filesystem/CLI em assinaturas,
annotations e generics. JDK I/O continua proibido no núcleo. O build usa Java 21 sem
preview e a porta recebe a `Publication` do `air-java`.

## Substituição

EVAL-CFG-008 usa o mesmo BuildCfg em memória e pelo adapter de arquivo. O teste do
kernel roda sem adapters no classpath de teste e sem filesystem/rede. Depois,
launcher de teste injeta outro caller sem modificar core.
Essa prova ocorre depois de `CFG-FIRST`; a ausência atual do binding JSON normativo
não bloqueia a via em memória.

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

Somente especificação desses testes. O gate architecture retorna UNAVAILABLE.
O validador documental não é um substitute de ArchUnit/bytecode ou de prova de
isolamento Java. Sua única verificação preventiva é ausência de Java/POM na fase docs-only.

EVAL-CFG-026 reserva o micro-E2E externo `cobol-semantic-product.json` →
`cobol-lower` → `air-java Publication` → CFG-FIRST. O lowerer não importa o port
Java do frontend. O trecho CFG recebe somente `Publication`/`Return`; o mapeamento
de GOBACK pertence ao frontend/lowerer e é verificado separadamente.
