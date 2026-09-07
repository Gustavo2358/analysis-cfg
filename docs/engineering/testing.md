# TDD e evidência de corretude

## Ciclo de um slice

Regra normativa → classes semânticas → adversariais contra o atalho óbvio → esperado
independente → teste RED observado → implementação mínima geral → GREEN →
refatoração → challenge independente → gates e handoff.
Planejar testes não é RED executado. EVAL-CFG-025 implementa CFG-FIRST e
EVAL-CFG-028 implementa o slice linear/Jump/Halt; EVAL-CFG-029 prova Branch
estrutural. O 004 tem suas obrigações completas provadas no 029; os demais evals
semânticos sem evidência integral continuam `planned`. EVAL-CFG-007 e EVAL-CFG-024 têm boundary, e
EVAL-CFG-027 implementa somente a prova arquitetural local do registry.
Não usar mocks do próprio algoritmo para provar o algoritmo.

## Camadas

**Domínio:** `air-java Publication` em memória, sem filesystem. Casos pequenos com successors,
outcomes, correlação de IDs e incerteza definidos à mão a partir do contrato.
**Aplicação:** porta pública, negociação, opções, erros e resultado imutável.
**Adapters:** decoding/encoding, dados inválidos e equivalência com entrada em memória.
**Integração:** arquivo real → CLI/driver → porta → CFG; exportação sem governar regra.
**Arquitetura:** dependências reais e execução sem adapters disponíveis.

`CFG-FIRST` nasce nessa camada de domínio: Entry/initialLabel, uma Sequence com
`Return` e normal exit escoped por Unit/Entry. O expected é escrito antes do builder.
Missing label é rejeitado por `AirValidator`; terminador ausente é não construível
ou inválido; sequences posteriores e permutações físicas não criam transições;
sequence sem predecessor permanece no inventário. JSON e CLI não entram nesse RED.

Tabelas de esperado não devem ser regeneradas pelo builder. Um interpreter de
referência independente, exato em casos pequenos/limitados e escrito só nos testes,
pode apoiar falsificação. Ele não certifica recursão ilimitada nem toda a linguagem.
Golden files só sob regras/correlações explícitas; contagens de corpus são telemetria.

Na foundation da porta/seam, o oracle exigia ausência de grafo porque ainda não
havia projeção. CFG-FIRST evolui explicitamente essa obrigação: sucesso exige
produto real; cada falha exige ausência. A assinatura e o teste sintético do seam
são preservados. O oracle de EVAL-CFG-025 foi commitado antes do builder com RED
por símbolos/API ausentes. O mutante de fallthrough após Return falhou tanto na
guarda do produto quanto no expected independente quando essa guarda foi
temporariamente desativada; ambas as mutações foram revertidas antes do GREEN.

## O que comparar

Relações rotuladas, outcomes, entries/saídas, operações/pontos, origem, precisão e
gaps. Igualdade de IDs locais não substitui correlação de identidade. Sequence split
pode mudar topologia e IDs mantendo comportamento nos pontos originais; não exigir
mesma quantidade de nós em representações equivalentes.

Metamorfismo atua na IR de entrada. Mutação atua na implementação Java e avalia
força dos testes. Não confundir os dois. Ver [metamorfismo](../evals/metamorphic.md).
PIT ou biblioteca equivalente é opcional/futuro, com domínio focalizado e versões
verificadas. Não adotar score global de vaidade.

## Oráculos de controle, não de dataflow

`AirValidator` e evals CFG têm papéis distintos. O primeiro verifica validade
estrutural da Publication no preflight; os segundos falsificam a interpretação do
consumer. Reutilizar o validator não autoriza omitir oráculo próprio nem copiar seus
diagnósticos como expected do grafo.

AIR-STRUCTURE@2 referencia cenários que também falam de RD/PV. Neste projeto,
implementar primeiro **a projeção estrutural** desses cenários: caminhos,
saídas e pontos. Os asserts RD/PV ficam explicitamente fora do papel Consumer/CFG,
não simulados com valores hardcoded. [Matriz de perfis](../evals/profile-matrix.md).

Os sub-requisitos `O-69-STRUCT` a `O-91-STRUCT` verificam preservação e validade
de `TypeRef`, `sameDomain` e `DomainProofScope`; não autorizam calcular valores,
reaching definitions, efeitos escalares ou storage. O adversarial inicial de branch
contrasta `unknown(known(bool))` válido com `unknown_type` inválido.

## Regressão adversarial mínima

Permutar sequences; label destino anterior; duas alternativas mesmo destino;
ramo terminante; unit/entry homônimas; invoke sem normal; opaco com reentrada;
frames com portas diferentes; retorno no callsite incorreto; gate sem testes.
Não esconder unsupported no setup de fixtures para manter o pipeline verde.

## Registro de evidência

Comando exato, revisão, conjunto de casos, exit status, falhas, limites e esperado.
CI ou relatório produzido por terceiros é evidência relatada; não dizer que foi
executada nesta sessão. Contador de testes zero deve falhar no gate de produto.

## Evidência do slice linear

EVAL-CFG-028 usa enums/records próprios para nós, papéis, contexto, ordem de
OperationId e termination kind. Expected manual de M1 precede produção;
observação do produto é separada da construção do esperado. Os testes retêm os
mesmos objetos AIR para provar operands, headers, precision, gaps e origins inteiros.

O RED por API ausente está no commit 6908ff9.
EVAL-CFG-025 permanece o oracle estável do CFG-FIRST, com 17 regressões.
As antigas recusas de Jump/Halt/instructions descreviam o limite da implementação
naquele checkpoint e não permanecem executáveis após a expansão legítima do produto.
A cobertura positiva dessas formas pertence exclusivamente ao EVAL-CFG-028, com 22 testes.

Mutantes Jump→vizinho físico, Return→vizinho, Halt→NormalExit, Halt→vizinho,
reordenação e perda de instruction falharam no oracle com exit 1 e assertion failure,
mesmo desativando temporariamente a guarda tipada de endpoints. Produção e guardas
foram restauradas; semantic passou novamente com zero skip.

EVAL-CFG-002 permanece planned: O-01-STRUCT conserva a,b,k do cenário X-01,
cujo k é invoke, fora deste slice. EVAL-CFG-005 recebe prova de ramo terminante no 029, mas ainda exige cenário
invocador de O-19-STRUCT; 013 inclui dependências/inventário parcial; 014 inclui O-66/transportes.
Esses evals recebem evidência parcial, nunca completion por linkage do backlog.

## Evidência da política de inventário (0A)

EVAL-CFG-030 adiciona 20 métodos pela porta real. O RED inicial foi comportamental:
AIR válida, duas coverages PARTIAL, expected CFG_BUILT e actual UNSUPPORTED_INPUT.
O oracle Return tem três nós e duas transições manuais; o caso misto parcial mantém
contextos, braços e órfãs. Evidence/premises são comparadas por identidade.
A recusa antiga no 025 passa a selecionar STRICT mantendo ambos os casos;
028/029 e seus expected não mudam. O gate exige todos os métodos do 030 além das regressões.

## Evidência do slice Branch

EVAL-CFG-029 mantém 25 métodos e expected manual em records/enums próprios. RED
por BRANCH_TRUE/BRANCH_FALSE ausentes foi commitado em 93dff9b antes de produção.
GREEN e mutantes A–G executados; quatro metamorfismos preservam correlações/origens.
025 fica byte a byte intacto. No 028, somente a recusa obsoleta de Branch e o switch
exaustivo foram reconciliados; seus 22 métodos e a fixture mista são preservados.
Pureza de unknown continua obrigação upstream no preflight; CFG não a descarrega.

## Evidência de transporte 2B

EVAL-CFG-031 executa três suítes com 31 métodos nominais. AIR estática do upstream
pinado não é regenerada pelo encoder; expected CFG manual antecedeu produção.
RED por APIs ausentes registrado no commit 7d22315. O golden exige os três nós,
as duas transições e cada correlation/contexto, PARTIAL e KNOWN_SUBSET.
O writer também recebe domínio completo atual (Branch/Jump/Halt/Return) por memória,
com expected contextual independente, escaping UTF-8 e fallback de move injetado.
Falhas de input/codec/build/output/usage e bugs inesperados têm testes separados.

Memória/arquivo geram as mesmas observações CFG de controle/coverage para o GOBACK;
os fatos AIR não transportados no v1 não são certificados por essa equivalência.
O kernel conserva seus 102 testes, inclusive os 84 métodos semânticos anteriores.
O harness possui 47 testes, incluindo contracasos do novo executor/inventário.
