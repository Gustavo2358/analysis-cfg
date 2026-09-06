# Java, Maven e integração futura

## Baseline decidida

Os novos módulos de `analysis-cfg` usarão **Java 21, sem preview**, e Maven. O POM
do `proleap-poc` continua compilando com `--release 17`; bytecode 17 é consumível
numa JVM 21 e não exige rebaixar o consumer nem alterar o frontend neste projeto.

O modelo compartilhado vem de:

```text
repository: Gustavo2358/air-java
commit:     2108294d9dfeb89d0019ce75fab27172b15a75b9
Maven:      io.github.gustavo2358:air-java:0.1.0-SNAPSHOT
AIR:        2.0.0 @ 0b2fbce7046010b22b32efa8cbc3e75ccba09442
JDK:        21, sem preview
```

`0.1.0-SNAPSHOT` é versão da biblioteca, não da AIR. Nenhuma tag/release foi
observada. O bootstrap autorizado deve provar uma resolução Maven reprodutível do
SHA fixado — publicação em repositório ou instalação controlada do checkout exato —
sem aceitar conteúdo SNAPSHOT flutuante e sem copiar fontes para `analysis-cfg`.

A implementação será Java/Maven; Python/Bash neste repo validam somente o harness.
Não há geração de Java ou solver alternativo em Python. Dependências de teste e
plugins serão versionadas no bootstrap; ANTLR e bibliotecas do frontend são
proibidos no kernel.

## Ownership e módulos

`air-java` possui `Publication`, `Unit`, `Entries.Entry`, `Sequence`, operações,
terminadores, tipos, IDs, premissas e `AirValidator`. `analysis-cfg` não cria um
módulo/modelo AIR local. O core pode criar apenas seus próprios tipos CFG, opções,
diagnósticos de consumer e índices derivados.

Planejamento enxuto: `cfg-kernel` com domínio/aplicação separados por packages;
`cfg-adapters` com reader AIR JSON/arquivos/exportadores quando o binding existir;
`cfg-launcher` com composition root/CLI quando necessário. Separar application em
artefato próprio somente se uma dependência concreta justificar. Não criar dezenas
de módulos antes de `CFG-FIRST`.

## Gates do bootstrap

O futuro gate de arquitetura inspeciona bytecode e build real para provar:

- target Java 21 e ausência de preview;
- kernel depende de `air-java` e `BuildCfg` recebe sua `Publication`;
- nenhum package/classe AIR duplicado neste repo;
- kernel sem Jackson, filesystem, CLI, ProLeap, ANTLR ou COBOL Semantic Product;
- reader JSON fora do kernel;
- preflight reutiliza `AirValidator`, sem validator AIR local divergente;
- testes falham quando não executam nenhum caso.

Mutação da AIR, retenção/cópia e correlações são verificadas também por testes
semânticos; um grafo de dependências sozinho não prova essas propriedades.

## Integração futura

`cobol-lower` dependerá de `air-java`, não do CFG. O orquestrador recebe
`Publication` do lowerer e chama `BuildCfg` diretamente, sem JSON ou arquivo
intermediário. O lowerer ainda não existe; sua readiness é dependência externa dos
itens E2E, não justificativa para adiar o kernel em memória.

Parent/reactor futuro agrega módulos e centraliza versões mantendo DAG. O perfil de
integração compila/testa kernel sem launcher. Container, REST/cloud e scheduler
continuam embalagens externas. A documentação Maven está nas [fontes](../sources/index.md).
