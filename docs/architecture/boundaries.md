# Clean Architecture: fronteiras verificáveis

## Regra de dependência

Domínio conhece apenas semântica de controle e tipos AIR vindos de `air-java`.
Aplicação conhece domínio e suas portas. Adapters dependem das portas. O launcher
conhece os concretos para compô-los. Nenhuma dependência aponta de dentro para fora.
A inspiração é a regra de dependência de Clean Architecture e o isolamento de
Ports & Adapters; referências em [fontes](../sources/index.md).

`cfg-kernel/domain` não importa `application`, `adapters` nem `launcher`.
`cfg-kernel/application` não importa adapters/launcher. `air-java` não depende de
CFG ou COBOL, e o kernel não declara classes alternativas no package AIR. Testes
arquiteturais devem inspecionar dependências reais de bytecode, inclusive
assinaturas, annotations e generic types; regex de imports pode complementar,
nunca ser única evidência de isolamento.

## Proibições no core e na aplicação

Sem `java.io`, `java.nio.file`, APIs de rede/HTTP, `System.exit`, console, variáveis
de ambiente, current working directory, reflection para descobrir plugins, Jackson,
Gson, Picocli, Spring, clientes AWS, banco ou tipos de biblioteca gráfica.
Não exigir annotations de serialização nos objetos de domínio.
Sem callbacks preguiçosos para consultar frontend/arquivo enquanto o CFG é construído.
JDK não é passe livre: filesystem do JDK continua infraestrutura.
Sem imports de ProLeap, ANTLR ou COBOL Semantic Product. A assinatura de `BuildCfg`
usa a `Publication` do `air-java`; um reader AIR JSON futuro vive em adapters.

Diagnostics tipados e contadores determinísticos são resultados de domínio.
Relógios, cronômetros, logging operacional, métricas exportadas e retries pertencem
à aplicação externa/adapters conforme necessidade; não alteram o grafo silenciosamente.
Limites de análise são opções explícitas e produzem status/razão, não truncamento.

## O que não é infraestrutura

A regra de `branch`, o matching de `local.boundary`, a interpretação de ControlScope
e os limites de precisão são **semântica de domínio**, não adapters intercambiáveis
com comportamento arbitrário. Bibliotecas de grafo podem apoiar armazenamento ou
algoritmos, mas seus tipos não atravessam a API pública sem decisão explícita.

## Gate que torna isso real

BACKLOG-CFG-002 implementou o primeiro limite físico no preflight; BACKLOG-CFG-003
fechou a porta do builder e o seam de composição semântica. Testes negativos e
falsificações injetam dependências proibidas e exigem falha.
Um teste de isolamento executa o caso de uso sem adapters no classpath de teste,
sem rede e sem leitura de fixture em disco.
Mudança de localização de Maven modules não dispensa as mesmas provas.

O gate atual verifica `--release 21`, dependência do kernel em `air-java`,
inventário exato de todas as classes produtivas, ausência de tipos AIR locais, uso
de `AirValidator`, assinatura bytecode da porta e ausência de filesystem, frontend,
reflection/`ServiceLoader` e tipos de projeção CFG. Mutabilidade ou deep copy da AIR
é testada semanticamente; não se infere apenas da estrutura de packages.

Interfaces por hábito não garantem Clean Architecture. Evitar repositories vazios,
factories em cascata e camada DTO duplicada sem conversão real necessária.
A composição deve ser pequena, explícita e testável.
