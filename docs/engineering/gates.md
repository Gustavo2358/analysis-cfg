# Gates estáveis e honestos

## Estado atual

| Gate | Estado | Verifica |
| --- | --- | --- |
| check-docs.sh | executável | links, IDs, manifestos, lifecycle, DAG, source lock e fase |
| check-harness.sh | executável | 47 testes existentes + suíte adversarial CP5 do harness |
| check-fast.sh | executável | docs + harness |
| check-architecture.sh | executável | Maven/testes, inventários exatos, dependências, bytecode 21 e boundary air-java |
| check-semantic.sh | executável | oracles CFG/W1/W2/W3, métodos/suítes exatos |
| check-performance.sh | W1–W3 executáveis; global UNAVAILABLE/3 | escala real de W1/W2/W3; W4/W5 indisponíveis |
| check-integration.sh | executável | AIR file → shared AirJson → Publication → BuildCfg → CFG JSON file, CLI/process e equivalência de controle/coverage em memória |
| check-full.sh | UNAVAILABLE | fast/architecture/semantic; para em performance, exit 3 |

Todos ficam em scripts/harness. Architecture e semantic exigem Maven, JDK 21+ e
instalação do air-java pinado em repositório Maven isolado. CI usa Temurin 21,
verifica o SHA antes da instalação e compartilha o mesmo repo isolado entre passos.
PYTHON_BIN seleciona outro executável Python, sem comando shell arbitrário.
Exit codes: 0=PASS, 1=FAIL, 2=erro de uso/configuração, 3=UNAVAILABLE.

## Estado explícito

[gate-state.json](gate-state.json) registra fase implementation e autorização
WORK-CFG-028 (WAVE_3; produto CFG existente preservado). Hooks reais de
architecture/semantic/integration em scripts/project; performance invoca os hooks
W1/W2/W3 e mantém o status global UNAVAILABLE por W4/W5. O checker verifica consistência local, não
comprova autorização humana nem estado GitHub.

## Arquitetura

O hook executa clean/test: oito suítes/102 testes no kernel e cinco suítes/37 métodos
nominais de transporte, zero skips. Verifica 14 fontes/22 classfiles exatos do kernel
e 6 fontes/8 classfiles dos adapters/launcher, incluindo tipos aninhados/sintéticos. Inspeciona
major 65/minor 0, dependências/classpath, javap e jdeps. Imports complementam bytecode.

- Java 21 sem preview; kernel compile somente air-java + java.base;
- porta exata BuildCfg(Publication, BuildOptions) → CfgBuildResult;
- CfgPreflight delega diretamente a AirValidator, sem AIR/validator paralelo;
- kernel sem filesystem, JSON, CLI, rede, frontend, reflection ou ServiceLoader;
- domain não conhece application/extension; registry explícito preservado;
- CoreCfgProjection substitui CfgFirstProjection, sem projectors concorrentes;
- ProjectionPolicy no domínio; BuildOptions carrega KNOWN_SUBSET default ou STRICT;
- inventário de operações permite explicitamente Jump/Branch/Return/Halt, HaltKind,
  Header e os cinco tipos Instruction; Dispatch/Invoke/Raise/Opaque,
  LocalInvoke/Boundary/Resume/Unwind e IndirectJump continuam proibidos;
- descriptors da porta, resultado, HaltExit.source e listas tipadas do produto;
- adapters depende de kernel/air-java/air-json; launcher de adapters/kernel/air-java/air-json;
- inventário explícito em scripts/project/transport-inventory.json contém cada fonte,
  import, classfile e dependência bytecode dos módulos externos; Maven DAG efetivo
  também é exato, sem frontend ou ciclo; modelo air-java não aponta para CFG/codec;
- reader compilado deve chamar AirJson.decode(byte[]), com bound físico e sem parser AIR local;
- mappings do writer não podem usar enum.name()/toString()/reflection como wire authority;
- CI fixa/verifica air-java SHA e roda fast, architecture, semantic e integration
  em Temurin 21 com Python 3.12 e repositório Maven isolado.

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

Docs/harness: fast. Kernel: architecture + semantic. Adapters/launcher exigem
integration separadamente; escala exige performance. Full deve reportar honestamente o primeiro
gate indisponível. Gates offline não provam merge, CI remota, conclusão de backlog
ou conformidade bilateral. Skip/UNAVAILABLE não é PASS.


## Integração executável 2B

check_integration.py executa clean/test real do reactor. Além das regressões do kernel,
exige exatamente TransportTest (9), WriterDomainTest (6) e EvalCfg031Test (16), com
cada método enumerado manualmente e zero missing/duplicate/foreign/skip/failure/error.
Reports de execuções anteriores são eliminados pelo clean; nenhum report é sintetizado
como evidência de produto. O detector se testa contra reports negativos antes de Maven.
O harness tem contracasos de hook ausente, detector de reports, dependências externas,
parser AIR local e enum.name como wire authority. Contagens e método nominal são
checados após execução; simples saída exit 0 de Maven não basta.

O fluxo usa fixture AIR canônica estática do pin b78f4068, compara bytes com golden CFG
manual e observa Entry→Sequence(Return)→NormalExit, ENTRY/RETURN/activationEntry,
PARTIAL/KNOWN_SUBSET. Testa input físico/codec, recusa real do kernel, todos os status
não-CFG_BUILT, output I/O/limite, CLI/usage e processo Java. Os demais tipos atuais do
writer são exercitados por Publications em memória e topologia manual contextual.

Performance não foi implementado. Full continua executando fast/architecture/semantic
e parando honestamente em performance com UNAVAILABLE/exit 3. A CI executa integration
como passo obrigatório independente, portanto full não o oculta.

## Integração escalar 4D

O gate preserva todos os 31 métodos nominais do 2B e exige ScalarAssignTest (5)
e ScalarAssignCliTest (1): cinco suítes/37 métodos no transporte, mais 102 testes
inalterados do kernel no reactor. A lista nominal não deriva do source nem dos reports.
O self-test também falsifica suites de um método (foreign name e duplicação).
Antes de Maven, check_scalar_contract.py verifica o merge 4B aprovado, lock/CI,
SHA-256, bytes e Git blob das fixtures. Com checkout upstream disponível, compara
os bytes locais com git show do merge exato; sem checkout, verifica hashes offline.
O source lock normativo da AIR e binding DRAFT permanecem fixos.

No 4D, check_scope.py governou o diff 4D desde 2b4df46d53ce5b21a5c315d3f691b183cb6bd124,
exigindo zero delta de produção no kernel/adapters/launcher. Evidência antiga do 2B
permanece histórica. Performance/full continuam UNAVAILABLE: o smoke estrutural
não cria benchmark nem implementa o gate performance.

## Preparação CP5

[CORE-SIZE-001](../architecture/decisions/ADR-0014.md) remove caps de capacidade
do desenho CP5. Os checks físicos de transporte descritos acima e seus testes
permanecem regressões do produto legado, com [dívida explícita](../work/cp5-follow-ups.md#size-cap-debts).
Não constituem aprovação de supported-size contract. S16 exigirá provas reais de
admissão/precisão invariantes ao volume nas Waves; hoje valida somente contratos.

fast inclui validate_cp5.py e test_cp5_harness.py: lifecycle/ordem/autorização,
Java/POM byte-exact, ADRs, métricas/probes/challenges, DAG e resultado de design.
check_scope.py agora usa a main CP4 ec525cbbad96d70c9663faa88e2672148fa8ee71,
compara inventory contra objetos Git e limita o diff a docs/harness/CI necessários.
CI inclui scope/manifest e fetch-depth 0 para checar a base exata.

check_analysis_architecture.py exige dependência Maven AIR direta em todos os módulos,
sem exceção residual de preparação. CP5-F01 foi corrigido declarativamente na W1.

## WAVE_1 produtiva

analysis-kernel integra o reactor e o gate arquitetural: sources/classfiles exatos,
DAG direto cfg-kernel + air-java, javap/jdeps e proibição de dependências W2+.
O launcher declara air-java/air-json diretamente; seu Java e CLI são byte-exact.

check_cp5_gate.py architecture/semantic/performance --wave 1 executa hooks reais.
Semântica W1 exige 20 métodos nominais; performance exige os 26 métodos W1, incluindo
S1/S2/S4/S8/S11/S16, contadores e walk de retenção. Nenhum gate infere PASS de hooks
ou documentos presentes. Campanha de 15 mutantes exige compile/RED/restore/GREEN.

Fast valida contratos W1 e W2–W5 indisponíveis. Architecture/integration/Maven
preservam as regressões 139 CFG/transporte, mais 26 testes W1. Semantic CFG conserva
seus 84 métodos nominais. CI executa separadamente o hook performance W1.
Full roda os probes W1 e registra W1 PASS, mas retorna UNAVAILABLE/3 pela ausência
W2–W5. Performance global permanece UNAVAILABLE. A W1 não certifica full CP5.
[Ledger](cp5-w1-index-ledger.md), [evidência](../work/evidence/WORK-CFG-028/wave-1/validation.md).

## WAVE_2 produtiva

[Ledger](cp5-w2-solver-ledger.md): solver/SPI genéricos, diretórios densos efetivos,
IN/OUT por direção e root propagation. check_w2.py valida métodos nominais, corpus
gerado, oracles concretos, S4b/escala e retenção lógica. Inventário W1 permanece
byte-exact; inventário W2 amplia o módulo com fronteiras javap/jdeps próprias.
Performance e full executam W1/W2, conservando UNAVAILABLE/3 para W3–W5.
A CI executa W2 separadamente, além dos gates/regressões existentes.


## WAVE_3 produtiva

Estado vigente: W1/W2 aprovadas; W3 autorizada. Os parágrafos das Waves anteriores
registram seus checkpoints históricos. O reactor acrescenta analysis-values e query
infra genérica. check_w3.py exige 15 métodos semânticos e 22 no perfil de performance,
com 48 grafos/213 pontos confrontados por dois oráculos independentes.
O inventário javap/jdeps/Maven é exato; W1/W2 Java ficam byte-exact.
Performance/full executam W1/W2/W3 e retornam UNAVAILABLE/3 somente por W4/W5.
CI executa os três hooks de performance separadamente, além de integration.
[Ledger W3](cp5-w3-values-ledger.md) explicita admission, custos, ownership e limites.


## WAVE_4 produtiva

check_w4.py executa seleção por buckets, provider real, keys/Entry, união de queries,
completion por dependências e FactSink atômico. Métricas de S5/S6/S8/S14/S15/S16
e inventários compilados são nominais. [Ledger](cp5-w4-planning-ledger.md).
Performance/full executam W1/W2/W3/W4 e preservam UNAVAILABLE/3 por W5.
CI acrescenta performance W4 obrigatória e mantém o collector de checkout intacto.
