# Gates estáveis e honestos

## Estado atual

| Gate | Estado | Verifica |
| --- | --- | --- |
| check-docs.sh | executável | links, IDs, manifestos, lifecycle, DAG, source lock e fase |
| check-harness.sh | executável | 41 testes adversariais do harness |
| check-fast.sh | executável | docs + harness |
| check-architecture.sh | executável | Maven/testes, inventários exatos, dependências, bytecode 21 e boundary air-java |
| check-semantic.sh | executável | 17 do EVAL-CFG-025 + 22 do EVAL-CFG-028 + 25 do EVAL-CFG-029 + 20 do EVAL-CFG-030, métodos/suítes exatos |
| check-performance.sh | UNAVAILABLE | futuras propriedades de escala |
| check-integration.sh | UNAVAILABLE | futuro arquivo→porta e equivalência em memória |
| check-full.sh | UNAVAILABLE | fast/architecture/semantic; para em performance, exit 3 |

Todos ficam em scripts/harness. Architecture e semantic exigem Maven, JDK 21+ e
instalação do air-java pinado em repositório Maven isolado. CI usa Temurin 21,
verifica o SHA antes da instalação e compartilha o mesmo repo isolado entre passos.
PYTHON_BIN seleciona outro executável Python, sem comando shell arbitrário.
Exit codes: 0=PASS, 1=FAIL, 2=erro de uso/configuração, 3=UNAVAILABLE.

## Estado explícito

[gate-state.json](gate-state.json) registra fase implementation e autorização
WORK-CFG-024. Hooks reais de architecture/semantic em scripts/project; performance
e integration permanecem sem hook. O checker verifica consistência local, não
comprova autorização humana nem estado GitHub.

## Arquitetura

O hook executa clean/test e exige oito suítes, 102 testes e zero skips. Verifica
14 fontes e 22 classfiles exatos, incluindo tipos aninhados/sintéticos. Inspeciona
major 65/minor 0, dependências/classpath, javap e jdeps. Imports complementam bytecode.

- Java 21 sem preview, dependência externa compile somente air-java;
- porta exata BuildCfg(Publication, BuildOptions) → CfgBuildResult;
- CfgPreflight delega diretamente a AirValidator, sem AIR/validator paralelo;
- sem filesystem, JSON, CLI, rede, frontend, reflection ou ServiceLoader;
- domain não conhece application/extension; registry explícito preservado;
- CoreCfgProjection substitui CfgFirstProjection, sem projectors concorrentes;
- ProjectionPolicy no domínio; BuildOptions carrega KNOWN_SUBSET default ou STRICT;
- inventário de operações permite explicitamente Jump/Branch/Return/Halt, HaltKind,
  Header e os cinco tipos Instruction; Dispatch/Invoke/Raise/Opaque,
  LocalInvoke/Boundary/Resume/Unwind e IndirectJump continuam proibidos;
- descriptors da porta, resultado, HaltExit.source e listas tipadas do produto;
- CI fixa/verifica air-java SHA e roda fast, architecture e semantic em Temurin 21.

Fixtures negativas testam todas essas primitives excluídas, I/O, frontend, modelo
paralelo, reflection/ServiceLoader e domain→application. Classes novas exigem evolução
deliberada do inventário. Navegação materializada/imutabilidade também têm testes.

## Semântica

check_semantic.py seleciona exatamente EvalCfg025Test, EvalCfg028Test, EvalCfg029Test e EvalCfg030Test
em clean/test. Exige cada um dos 84 métodos manualmente enumerados, quatro relatórios exatos e zero
missing/duplicate/foreign/skip/failure/error. Não é alias para mvn test inteiro.
O detector rejeita 125 fixtures adversariais, inclusive cada método ausente, suíte
ausente/extra, duplicata, foreign testcase e skip/failure/error.

O 025 mantém somente as 17 regressões estáveis de CFG-FIRST. As antigas recusas
de Jump/Halt/instructions não permanecem executáveis após a expansão do produto;
a cobertura positiva dessas formas pertence ao 028. O 028 exige M1,
operandos/ordem/origins/gaps, Jump por label forward/backward/self-loop, Halt distinto,
contextos múltiplos, órfãs/unsupported, metamorfismos e negociação memory.regions@1
restrita ao papel de controle. O 029 prova M2–M5, predicate conhecido/desconhecido,
invalidade de tipo/role/targets, contexto, órfãs, 258 branches, invariantes e quatro
metamorfismos. O 028 conserva 22 métodos: a fixture mista mantém Branch, mas ele
não é mais diagnosticado como unsupported; o observer rejeita os kinds de Branch
fora do domínio do 028. A prova do slice Branch pertence ao 029; o 030 acrescenta sua interação com a policy.
O 030 prova a matriz da policy com 20 métodos: inventários, evidence original, falhas
independentes de partialidade e grafo misto parcial com contextos/ordem preservados.
Nenhum perfil AIR completo é reivindicado.

## Escalonamento e limites

Docs/harness: fast. Kernel: architecture + semantic. Adapters futuros acrescentam
integration; escala exige performance. Full deve reportar honestamente o primeiro
gate indisponível. Gates offline não provam merge, CI remota, conclusão de backlog
ou conformidade bilateral. Skip/UNAVAILABLE não é PASS.
