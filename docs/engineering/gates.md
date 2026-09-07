# Gates estáveis e honestos

## Estado atual

| Gate | Estado | Verifica |
| --- | --- | --- |
| check-docs.sh | executável | links, IDs, manifestos, lifecycle, DAG, source lock e fase |
| check-harness.sh | executável | 41 testes adversariais do harness |
| check-fast.sh | executável | docs + harness |
| check-architecture.sh | executável | Maven/testes, inventários exatos, dependências, bytecode 21 e boundary air-java |
| check-semantic.sh | executável | 20 testes do EVAL-CFG-025 + 22 do EVAL-CFG-028, métodos/suítes exatos |
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
WORK-CFG-022. Hooks reais de architecture/semantic em scripts/project; performance
e integration permanecem sem hook. O checker verifica consistência local, não
comprova autorização humana nem estado GitHub.

## Arquitetura

O hook executa clean/test e exige seis suítes, 60 testes e zero skips. Verifica
13 fontes e 21 classfiles exatos, incluindo tipos aninhados/sintéticos. Inspeciona
major 65/minor 0, dependências/classpath, javap e jdeps. Imports complementam bytecode.

- Java 21 sem preview, dependência externa compile somente air-java;
- porta exata BuildCfg(Publication, BuildOptions) → CfgBuildResult;
- CfgPreflight delega diretamente a AirValidator, sem AIR/validator paralelo;
- sem filesystem, JSON, CLI, rede, frontend, reflection ou ServiceLoader;
- domain não conhece application/extension; registry explícito preservado;
- CoreCfgProjection substitui CfgFirstProjection, sem projectors concorrentes;
- inventário de operações permite explicitamente Jump/Return/Halt, HaltKind,
  Header e os cinco tipos Instruction; Branch/Dispatch/Invoke/Raise/Opaque,
  LocalInvoke/Boundary/Resume/Unwind e IndirectJump continuam proibidos;
- descriptors da porta, resultado, HaltExit.source e listas tipadas do produto;
- CI fixa/verifica air-java SHA e roda fast, architecture e semantic em Temurin 21.

Fixtures negativas testam todas essas primitives excluídas, I/O, frontend, modelo
paralelo, reflection/ServiceLoader e domain→application. Classes novas exigem evolução
deliberada do inventário. Navegação materializada/imutabilidade também têm testes.

## Semântica

check_semantic.py seleciona exatamente EvalCfg025Test e EvalCfg028Test em clean/test.
Exige cada um dos 42 métodos manualmente enumerados, dois relatórios exatos e zero
missing/duplicate/foreign/skip/failure/error. Não é alias para mvn test inteiro.
O detector rejeita 61 fixtures adversariais, inclusive cada método ausente, suíte
ausente/extra, duplicata, foreign testcase e skip/failure/error.

O 025 preserva CF1 integral; suas três antigas recusas de escopo agora provam
suporte a Jump/Halt/instructions, conforme autorização do 022. O 028 exige M1,
operandos/ordem/origins/gaps, Jump por label forward/backward/self-loop, Halt distinto,
contextos múltiplos, órfãs/unsupported, metamorfismos e negociação memory.regions@1
restrita ao papel de controle. Nenhum perfil AIR completo é reivindicado.

## Escalonamento e limites

Docs/harness: fast. Kernel: architecture + semantic. Adapters futuros acrescentam
integration; escala exige performance. Full deve reportar honestamente o primeiro
gate indisponível. Gates offline não provam merge, CI remota, conclusão de backlog
ou conformidade bilateral. Skip/UNAVAILABLE não é PASS.
