# Gates estáveis e honestos

## Estado atual

| Gate | Estado | Verifica |
| --- | --- | --- |
| `check-docs.sh` | executável | links locais, IDs, manifestos, lifecycle, DAG do backlog, source lock e fase |
| `check-harness.sh` | executável | testes adversariais do próprio validador e runner |
| `check-fast.sh` | executável | docs + harness |
| `check-architecture.sh` | executável | Maven/testes, inventários exatos, dependências, bytecode Java 21 e boundary `air-java` |
| `check-semantic.sh` | executável | 20 testes obrigatórios de EVAL-CFG-025, por suíte e nomes exatos |
| `check-performance.sh` | UNAVAILABLE | futuras propriedades algorítmicas/escala |
| `check-integration.sh` | UNAVAILABLE | futuro arquivo→porta e equivalência em memória |
| `check-full.sh` | UNAVAILABLE | executa fast, architecture e semantic; para em performance com exit 3 |

Todos estão em `scripts/harness/`; usar `bash scripts/harness/check-fast.sh` funciona
mesmo se a extração não conservar permissões. Architecture e semantic requerem
Maven, JDK 21+ e `air-java` instalado do SHA fixado em repo Maven isolado.
O CI usa Temurin 21 e o mesmo repo isolado para upstream, architecture e semantic.
`PYTHON_BIN` seleciona um executável alternativo, sem permitir shell command arbitrário.

Exit codes: `0=PASS`, `1=FAIL`, `2=erro de uso/configuração`, `3=UNAVAILABLE`.
Qualquer não zero impede anunciar o gate como verde. Skip de gate obrigatório não
é aprovação. Output humano do runner não governa semântica de produto.

## Estado explícito

[gate-state.json](gate-state.json) declara fase `implementation`, autorização
`WORK-CFG-005` e hooks reais para architecture/semantic em `scripts/project/`.
Performance e integration continuam sem hook. O checker prova consistência local,
não a veracidade de autorização humana.

Hooks têm caminho exato, sem `eval` de comando vindo de metadados. Versões
Maven/JUnit ficam fixadas; ausência de testes, perfil inexistente ou suíte excluída
falha. Não habilitar gate com script que imprime PASS sem executar verificação.

## Arquitetura

O hook executa Maven com clean/test, exige cinco suítes e 38 testes sem skip,
inspeciona inventários exatos de 13 fontes e 20 classfiles (incluindo tipos
aninhados/sintéticos), major 65/minor 0, árvore de dependências, classpath,
`javap` e `jdeps`. Guardas de imports complementam a inspeção de bytecode.
Continua provando:

- Java 21 sem preview; dependência externa compile somente `air-java`;
- `BuildCfg(Publication, BuildOptions) → CfgBuildResult`, com Publication compartilhada;
- `CfgPreflight` delega diretamente a `AirValidator`, sem validator/modelo AIR local;
- nenhuma dependência de filesystem, JSON, CLI, rede, frontend, reflection ou ServiceLoader;
- tipos CFG no namespace próprio e domain sem dependência de application/extension;
- registry por capability/version preservado e Return tratado diretamente como core;
- nenhuma primitive concreta além de Return em produção;
- CI fixa/verifica o SHA e executa fast, architecture e semantic em Temurin 21.

Fixtures internas negativas incluem I/O, frontend, DTO local, AIR paralela,
reflection/ServiceLoader, domain → application, Jump e Halt. Classes adicionadas ou
removidas exigem evolução deliberada do inventário. Retenção/imutabilidade são
provadas também nos testes semânticos, não inferidas apenas de packages.

## Semântica

`check_semantic.py` executa `clean test` selecionando exatamente
`io.github.gustavo2358.analysis.cfg.domain.EvalCfg025Test`. Confere o único relatório
Surefire, os 20 nomes obrigatórios, contagens e ausência de failure/error/skip,
inclusive em cada testcase. Não é um alias para `mvn test` inteiro.
O detector rejeita 26 relatórios adversariais, incluindo zero casos, cada obrigação
ausente, duplicata, suíte errada e skip/failure/error.

A suíte prova Entry/initialLabel, Sequence, Return/normal exit por Unit/Entry,
referência inválida rejeitada, terminador ausente não construível, ausência de
fallthrough, invariância por permutação, órfãs, IDs próprios e imutabilidade.
Prova também recusa de Jump/Halt/instructions, capability sem suporte e formas
indisponíveis, sem implementar sua semântica.

O gate semântico declara somente CFG-FIRST. EVAL-CFG-001 e EVAL-CFG-009 continuam
planned; nenhum perfil AIR completo é reivindicado.

## Escalonamento e limites

Documentação/harness: fast. Código do kernel: architecture + semantic.
Adapters/composição acrescentarão integration; propriedades de escala exigirão
performance. Full deve ser executado e seu primeiro gate indisponível reportado,
sem promover status fictício para concluir um slice.

Gates offline não provam merge, CI remota, completude de backlog ou conformidade
bilateral de produtores. Os resultados são complementares ao review humano e
restritos às obrigações realmente executadas.
