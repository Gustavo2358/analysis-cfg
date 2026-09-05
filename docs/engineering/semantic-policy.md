# Política semântica e escolha de algoritmos

## Autoridade e ordem de trabalho

Antes de produção: definir regra IR, domínio, casos de equivalência, premissas,
invariantes, algoritmo candidato, oracle independente e limite da alegação.
Classificar premissas: `IR_GUARANTEED`, `ARCHITECTURE_GUARANTEED`,
`EXPLICIT_CONTRACT`, `OBSERVED_IN_FIXTURES_ONLY`, `UNCERTAIN`.
As duas últimas nunca se tornam regra de produção sem validação.

Prefira algoritmo semântico exato no domínio declarado. Quando necessário,
aproximação conservadora deve declarar conjunto de comportamentos, limite,
terminação e precisão. **Aproximação conservadora fundamentada não é heurística.**
Heurísticas por nome, source line, vizinhança textual, primeira ocorrência, último
MOVE, corpus ou limite artificial de profundidade são proibidas no core.
Protótipo heurístico eventual só pode existir isolado como experimento autorizado,
sem selo de conformidade e sem integração silenciosa na produção.
Regex é aceitável para validar a sintaxe de metadados do harness; não para inferir
semântica de programas ou substituir um algoritmo de controle.

## Quando pesquisar literatura

Obrigatório para matching contextual, recursão, sumarização de controle,
coalescing que altera pontos, análise de alcance sob fronteira aberta e escolha de
algoritmo não trivial. Pesquisa deve usar documentação oficial, papers ou autores;
blogs de terceiros são pistas, não autoridade suficiente.

Registro mínimo em ADR/discovery: problema preciso; candidatos; fonte e trecho
aplicável; precondições; argumento de soundness; limites de precisão; terminação;
complexidade incluindo tamanho da saída; adversariais; decisão e alternativas
rejeitadas. Não declarar “canônico” sem identificar algoritmo e condições de uso.

A bibliografia em [fontes](../sources/index.md) é ponto de partida. LLVM inspira
terminadores, não importa SSA ou restrições de entrada. Reps/Horwitz/Sagiv apoia
caminhos realizáveis sob condições específicas; IFDS não é solução universal nem
requisito para o MVP. Consultar o trabalho completo antes de adotar seu algoritmo.

## Separação de responsabilidades

O produtor resolve significado-fonte e destinos estruturais. Lowerer normaliza.
CFG projeta regras da IR. Efeitos/values/reaching definitions permanecem futuros.
O construtor não deve precisar de valores propagados para fazer CFG conservador;
controle indireto inicial usa o universo de labels contratual, não targets inferidos.

## Challenge obrigatório

Pergunte: a regra sobreviveria à troca de todas as fixtures? Há saída omitida?
Um label remoto foi unido por nome local? O caminho precisa de frame compatível?
Um desconhecido virou vazio? Alguma API de I/O entrou no núcleo? Um limite virou
propriedade do programa? Contrato e teste discordam: determinar a autoridade antes
de editar esperado. Não adaptar resultado ao que a implementação já produziu.
