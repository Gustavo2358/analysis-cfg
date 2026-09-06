# Provas arquiteturais

## Dependências

Na fundação atual, EVAL-CFG-007 prova que as sete fontes produtivas autorizadas
dependem apenas de `air-java`/`java.base`, sem modelo ou validator AIR duplicado,
transporte, frontend ou discovery reflexiva. O inventário exato de oito classfiles
rejeita expansão silenciosa. O build usa Java 21 sem preview; `BuildCfg` recebe a
`Publication` do `air-java`, e o preflight continua delegando ao `AirValidator`.

## Substituição

EVAL-CFG-008 usa o mesmo BuildCfg em memória e pelo adapter de arquivo. O teste do
kernel roda sem adapters no classpath de teste e sem filesystem/rede. Depois,
launcher de teste injeta outro caller sem modificar core.
Essa prova ocorre depois de `CFG-FIRST`; o binding JSON 1.0.0 presente, ainda DRAFT,
não bloqueia a via em memória e não foi implementado.

## Extensão

EVAL-CFG-009 adiciona uma capability sintética versionada em código de teste pelo
registry injetado no mesmo coordinator. Registro duplicado falha; versões distintas
não competem por ordem; falta de intérprete retorna `UNSUPPORTED_CAPABILITY`. O
`INCOMPLETE_VALIDATION` upstream continua visível mesmo quando o intérprete está
registrado. Nenhum fallback ou comportamento de operação foi implementado.

## Integração Maven

EVAL-CFG-020 compila módulos como DAG e liga produtor sintético → modelo IR → porta
CFG sem CLI/JSON. Integração com lowerer real é teste distinto e posterior. Nenhum
teste deve depender de paths de workspace, symlink de repo vizinho ou versão SNAPSHOT
não rastreada para fingir independência.

## O que existe hoje

O gate `architecture` executa build e 18 testes obrigatórios, exige inventário
Surefire exato, inspeciona classfiles/dependency tree/classpath e confirma as
assinaturas e chamadas com `javap`/`jdeps`. Fixtures e mutações reais exercitam
filesystem, AIR paralela, DTO local, reflection/`ServiceLoader`, frontend e falso
sucesso para unsupported. Isso preserva EVAL-CFG-007/EVAL-CFG-024 e implementa
EVAL-CFG-009 somente para o seam. EVAL-CFG-008/020 e todas as provas semânticas do
builder permanecem planejadas.

EVAL-CFG-026 reserva o micro-E2E externo `cobol-semantic-product.json` →
`cobol-lower` → `air-java Publication` → CFG-FIRST. O lowerer não importa o port
Java do frontend. O trecho CFG recebe somente `Publication`/`Return`; o mapeamento
de GOBACK pertence ao frontend/lowerer e é verificado separadamente.
