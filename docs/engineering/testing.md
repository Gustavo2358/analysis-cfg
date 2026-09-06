# TDD e evidência de corretude

## Ciclo de um slice

Regra normativa → classes semânticas → adversariais contra o atalho óbvio → esperado
independente → teste RED observado → implementação mínima geral → GREEN →
refatoração → challenge independente → gates e handoff.
Planejar testes não é RED executado. Os evals semânticos de CFG continuam
`planned`; EVAL-CFG-007 e EVAL-CFG-024 têm implementação de boundary, e
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

Na foundation da porta/seam, o oracle compila a assinatura exata, exige que o
resultado não contenha grafo/sucesso e registra uma capability sintética pelo mesmo
coordinator. O RED foi a ausência das classes produtivas. Mutantes de unsupported,
filesystem, AIR paralela e discovery reflexiva devem falhar antes de GREEN.

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
