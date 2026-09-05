# Gates estáveis e honestos

## Nesta entrega

| Gate | Estado | Verifica |
| --- | --- | --- |
| `check-docs.sh` | executável | links locais, IDs, manifestos, lifecycle, DAG do backlog, lock e estado docs-only |
| `check-harness.sh` | executável | testes adversariais do próprio validador e runner |
| `check-fast.sh` | executável | docs + harness |
| `check-architecture.sh` | UNAVAILABLE | futuro bytecode/boundaries Java |
| `check-semantic.sh` | UNAVAILABLE | futuros oráculos do CFG |
| `check-performance.sh` | UNAVAILABLE | futuras propriedades algorítmicas/escala |
| `check-integration.sh` | UNAVAILABLE | futuro arquivo→porta e equivalência em memória |
| `check-full.sh` | UNAVAILABLE | agregador de produto; não simula sucesso sem implementação |

Todos estão em `scripts/harness/`; usar `bash scripts/harness/check-fast.sh` funciona
mesmo se a extração não conservar permissões. Requisitos: Bash e Python 3.9+.
`PYTHON_BIN` seleciona um executável alternativo, sem permitir shell command arbitrário.

Exit codes: `0=PASS`, `1=FAIL`, `2=erro de uso/configuração`, `3=UNAVAILABLE`.
Qualquer não zero impede anunciar o gate como verde. Skip de gate obrigatório não
é aprovação. Output humano do runner não governa semântica de produto.

## Estado explícito

[gate-state.json](gate-state.json) declara fase `docs_only` e hooks de produto nulos.
Um `.java` ou `pom.xml` no repo nessa fase falha em docs. A mudança de fase exige
work item de implementação autorizado com evidência registrada; o checker só prova
consistência local, não a veracidade de autorização humana.

Os gates de produto só se tornam disponíveis quando BACKLOG-CFG-002/003 e slices
correspondentes criam hooks reais em `scripts/project/`. Hooks têm caminho exato,
sem `eval` de comando vindo de metadados. Versões Maven/JUnit ficam fixadas; ausência
de testes, perfil inexistente ou suite excluída deve falhar. Não habilitar gate com
script que imprime PASS ou faz `exit 0` sem executar a verificação.

## Escalonamento

Documentação/harness: fast. Código de modelo/domínio: architecture + semantic.
Adapters/composição: acrescentar integration. Mudança algorítmica/escala: performance.
Encerramento de marco implementado: full, com todas suites realmente disponíveis.
Enquanto um gate requerido não existe, criar evidência/gate adequado no checkpoint;
não promover status fictício para concluir o trabalho.

## Limites dos gates atuais

Não validam semântica IR, soundness, algoritmos, bytecode Java, direitos de acesso,
URLs remotas, merge de PR ou completude real de backlog. Não executam Maven.
Podem detectar inconsistências locais úteis e impedir alegações documentais óbvias.
São guardas complementares ao review e aos futuros testes de produto.
