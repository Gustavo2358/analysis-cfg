# Escala, terminação e determinismo

O cenário futuro inclui publicações derivadas de programas grandes. Construir o
core sem varrer todas as sequences a cada target e sem enumerar todos os caminhos.
Índices por identidade completa e passes simples precedem otimizações especulativas.

Para projeção direta, acompanhar fatos lidos, nós criados e transições emitidas;
a meta é O(input + output), não limite de milissegundos sem ambiente controlado.
Um dispatch com K cases contribui K alternativas. Fronteiras simbólicas e estado
contextual têm custos próprios. Não prometer custo linear para matching recursivo.

Testes de performance devem exercitar séries crescentes de chains, diamonds,
cycles, dispatch e units, incluindo casos com muitos destinos. Contadores internos
podem complementar medição e inspeção de algoritmo; não são prova isolada de big-O.
Benchmark de tempo/memória serve como evidência com ambiente declarado, não oracle
funcional frágil. Não cortar candidates, ocorrências ou pilhas silenciosamente.

Ordens de construção, hashing e IDs derivados devem ser determinísticos para mesma
publicação/opções. Não prometer persistência de ID depois de editar a IR. Navegação
não deve ordenar a coleção inteira a cada query. Builds equivalentes não levam
clock, UUID aleatório ou paths absolutos de máquina no produto semântico.

Recursão Java proporcional ao número de sequences é risco em input grande. Preferir
traversal iterativo onde aplicável; não confundir pilha de implementação com pilha
semântica de local frames. [CORE-SIZE-001](../architecture/decisions/ADR-0014.md)
proíbe orçamento de recursos como término semântico. Convergência é obrigação
matemática; exaustão de processo/infra não produz grafo ou resultado parcial válido.

BACKLOG-CFG-012 estabelece baseline no core estrutural; o controle local exige
novo relatório de custo em seu discovery. Sem limite de hardware inventado neste
harness e sem promessa de throughput antes de medição.
