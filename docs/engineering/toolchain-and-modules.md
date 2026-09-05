# Java, Maven e integração futura

## Base confirmada e escolha local

O POM do proleap-poc consultado fixa `maven.compiler.release=17`; seu harness informa
Maven 3.9+. Adotar **Java 17 sem preview** para a primeira implementação do CFG.
Java 17 não suporta todos os exemplos de pattern-switch de versões posteriores;
não copiar pseudocódigo da conversa como código compilável.
Fonte: [baseline upstream](../sources/upstream-state.md).

A implementação será Java/Maven; Python/Bash neste pacote validam somente o harness.
Não há geração de Java ou solver alternativo em Python.
JUnit e um verificador de arquitetura serão selecionados e versionados no bootstrap;
não copiar todas as dependências do frontend (ANTLR é proibido no CFG).
Maven Wrapper e plugins com versões fixas serão introduzidos após autorização,
checando compatibilidade e testes não vazios. Não exigir Node, cloud ou Docker.

## Ownership e módulos

Antes do POM: ADR sobre modelo IR compartilhado, coordenadas, versão e localização.
Evitar dois artefatos diferentes contendo a mesma classe IR. Uma implementação
inicial local em módulo isolado pode ser extraída/movida sem mudar API.

Planejamento enxuto: modelo IR separado; `cfg-kernel` com domínio/aplicação separados
por packages; `cfg-adapters` com codec/arquivos/exportadores; `cfg-launcher` com
composition root/CLI. Separar application em artefato próprio só se necessário.
Não criar dezenas de Maven modules antes de um caso executável.

## Futura integração no monólito modular

Parent agrega os módulos e centraliza versões; dependências continuam formando DAG.
CobolLower depende do modelo IR, não do CFG. O orquestrador recebe Publication do
lowerer e invoca a porta do CFG. Não depende de CLI, filesystem ou codec.
O perfil de integração deve compilar e testar kernel sem launcher.
A documentação oficial do reactor Maven está em [fontes](../sources/index.md).

## Independência de implantação

Container é embalagem externa, não abstração de domínio. Adapters REST/cloud ficam
adiados até necessidade. Caso de uso não conhece framework de DI, rede ou scheduler.
Uma lib consumível em memória é o produto principal; executável CLI é uma face.
