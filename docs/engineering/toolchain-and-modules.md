# Java, Maven e integração futura

## Baseline decidida

Os módulos de `analysis-cfg` usam **Java 21, sem preview**, e Maven. O POM
do `proleap-poc` continua compilando com `--release 17`; bytecode 17 é consumível
numa JVM 21 e não exige rebaixar o consumer nem alterar o frontend neste projeto.

O modelo compartilhado vem de:

```text
repository: Gustavo2358/air-java
commit:     b78f4068d8a479f48eb048b8d76fa60a0997dc4a
Maven:      io.github.gustavo2358:air-java:0.1.0-SNAPSHOT
AIR:        2.0.0 @ 122ce54e1b9ef9b00646f93ece409ca8b63bc933
JDK:        21, sem preview
Python:     >= 3.10, disponível como python3
Python CI:  3.12
```

`0.1.0-SNAPSHOT` é versão da biblioteca, não da AIR. Nenhuma tag/release foi
observada.

## Resolução temporária e reproduzível do SNAPSHOT

O CI faz checkout de `analysis-cfg` e `air-java` como diretórios irmãos. O ref do
upstream é o SHA completo acima e o workflow confirma `git rev-parse HEAD` antes de
instalar. Antes do build upstream, o workflow configura Temurin 21 e Python 3.12
com `actions/setup-python@v5`. O POM raiz do upstream executa `python3` já na fase
`validate`; Python 3.10+ é pré-requisito do build Maven, inclusive na reprodução
local. Ambos os builds compartilham um repositório Maven inicialmente vazio e
isolado, selecionado por
`MAVEN_OPTS=-Dmaven.repo.local=${runner.temp}/analysis-cfg-m2`.

A instalação é executada **com o working directory no checkout de `air-java`**:

```text
mvn -B -ntp clean install
```

Depois, o build e os gates do consumer rodam com working directory em
`analysis-cfg`, usando o mesmo repositório Maven isolado.
O build da raiz instala `air-java-parent`, `air-model` (artefato `air-java`) e
`air-json` (artefato `air-json`). Os paths de fontes são relativos a cada módulo;
o consumer continua resolvendo apenas `io.github.gustavo2358:air-java`.

A reprodução local exige JDK 21 e Python 3.10+ disponível como `python3` e usa a
mesma sequência: clone de `air-java`, checkout detached
do SHA fixado, confirmação de `HEAD`, diretório Maven temporário vazio, instalação
a partir da raiz upstream e então build do consumer com o mesmo diretório Maven.
Nenhum JAR é vendorizado ou publicado por este trabalho, e o core não depende do
path do checkout irmão. A estratégia é temporária: um artefato versionado ou um
reactor Maven futuro poderá substituir a preparação sem alterar a boundary.

A fundação é Java/Maven; Python/Bash neste repo validam somente o harness e a
arquitetura compilada.
Não há geração de Java ou solver alternativo em Python. Dependências de teste e
plugins serão versionadas no bootstrap; ANTLR e bibliotecas do frontend são
proibidos no kernel.

## Ownership e módulos

`air-java` possui `Publication`, `Unit`, `Entries.Entry`, `Sequence`, operações,
terminadores, tipos, IDs, premissas e `AirValidator`. `analysis-cfg` não cria um
módulo/modelo AIR local. O repositório upstream também contém o codec compartilhado
`AirJson` em `air-json/`; sua adoção Maven no CFG e o reader não fazem parte deste pin.
O core pode criar apenas seus próprios tipos CFG, opções,
diagnósticos de consumer e índices derivados.

Planejamento enxuto: `cfg-kernel` com domínio/aplicação separados por packages;
`cfg-adapters` com reader AIR JSON/arquivos/exportadores somente após promoção do
binding DRAFT e autorização de checkpoint;
`cfg-launcher` com composition root/CLI quando necessário. Separar application em
artefato próprio somente se uma dependência concreta justificar. Não criar dezenas
de módulos antes de `CFG-FIRST`.

## Gates do bootstrap

O gate de arquitetura deste checkpoint inspeciona bytecode e build real para provar:

- target Java 21 e ausência de preview;
- kernel depende de `air-java` e o preflight recebe sua `Publication`;
- nenhum package/classe AIR duplicado neste repo;
- kernel sem Jackson, filesystem, CLI, ProLeap, ANTLR ou COBOL Semantic Product;
- nenhuma boundary de entrada JSON no kernel;
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
