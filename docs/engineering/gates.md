# Gates estáveis e honestos

## Estado atual

| Gate | Estado | Verifica |
| --- | --- | --- |
| `check-docs.sh` | executável | links locais, IDs, manifestos, lifecycle, DAG do backlog, source lock e fase |
| `check-harness.sh` | executável | testes adversariais do próprio validador e runner |
| `check-fast.sh` | executável | docs + harness |
| `check-architecture.sh` | executável | Maven/testes, dependências, bytecode Java 21 e boundary `air-java` |
| `check-semantic.sh` | UNAVAILABLE | futuros oráculos do CFG |
| `check-performance.sh` | UNAVAILABLE | futuras propriedades algorítmicas/escala |
| `check-integration.sh` | UNAVAILABLE | futuro arquivo→porta e equivalência em memória |
| `check-full.sh` | UNAVAILABLE | roda os gates em ordem e para no primeiro gate de produto ainda indisponível |

Todos estão em `scripts/harness/`; usar `bash scripts/harness/check-fast.sh` funciona
mesmo se a extração não conservar permissões. O gate de arquitetura também requer
Maven e JDK 21+ e que o SNAPSHOT pinado de `air-java` esteja no repositório Maven.
O CI usa exatamente JDK 21.
`PYTHON_BIN` seleciona um executável alternativo, sem permitir shell command arbitrário.

Exit codes: `0=PASS`, `1=FAIL`, `2=erro de uso/configuração`, `3=UNAVAILABLE`.
Qualquer não zero impede anunciar o gate como verde. Skip de gate obrigatório não
é aprovação. Output humano do runner não governa semântica de produto.

## Estado explícito

[gate-state.json](gate-state.json) declara fase `implementation`, autorização
`WORK-CFG-002` e hook real para `architecture`. Semantic, performance e integration
continuam sem hook. O checker prova consistência local, não a veracidade de
autorização humana.

Gates de produto só se tornam disponíveis quando o slice correspondente cria hook
real em `scripts/project/`. Hooks têm caminho exato,
sem `eval` de comando vindo de metadados. Versões Maven/JUnit ficam fixadas; ausência
de testes, perfil inexistente ou suite excluída deve falhar. Não habilitar gate com
script que imprime PASS ou faz `exit 0` sem executar a verificação.

O gate de arquitetura prova no build/bytecode real deste checkpoint:

- Java target 21, sem preview;
- nenhum package/classe AIR duplicado em `analysis-cfg`;
- kernel depende exclusivamente de `air-java` em compile e `CfgPreflight` recebe
  sua `Publication`;
- kernel sem Jackson, filesystem, CLI, ProLeap, ANTLR ou COBOL Semantic Product;
- AIR JSON reader fora do kernel;
- produto CFG não muta a AIR;
- `AirValidator` usado no preflight, sem validator AIR local divergente;
- suíte falha quando executa zero casos.

O hook executa Maven, exige relatórios Surefire com contagem não zero, lê classfiles,
confere major 65/minor sem preview, inspeciona a árvore/classpath, executa `javap` e
`jdeps`, aplica uma guarda complementar aos imports produtivos e roda fixtures
internas negativas do detector. `BuildCfg` ainda não existe e não é exigido por
este gate.

## Escalonamento

Documentação/harness: fast. Fundação da boundary: architecture. Código futuro de
modelo/domínio CFG: architecture + semantic.
Adapters/composição: acrescentar integration. Mudança algorítmica/escala: performance.
Encerramento de marco implementado: full, com todas suites realmente disponíveis.
Enquanto um gate requerido não existe, criar evidência/gate adequado no checkpoint;
não promover status fictício para concluir o trabalho.

## Limites dos gates atuais

O gate documental não valida semântica IR. O gate arquitetural valida a boundary
compilada, mas não soundness, algoritmo CFG, mutação semântica, direitos de acesso,
URLs remotas, merge de PR ou completude real do backlog. São guardas
complementares ao review e aos futuros testes semânticos.
