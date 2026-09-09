# Java, Maven e módulos

## Baseline decidida

Os módulos de `analysis-cfg` usam **Java 21, sem preview**, e Maven. O POM
do `proleap-poc` continua compilando com `--release 17`; bytecode 17 é consumível
numa JVM 21 e não exige rebaixar o consumer nem alterar o frontend neste projeto.

O modelo compartilhado vem de:

```text
repository: Gustavo2358/air-java
commit:     ce530a7e17ab12b23c48f29425f503ff920b09fb
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
o kernel resolve apenas `io.github.gustavo2358:air-java`; cfg-adapters também
resolve `io.github.gustavo2358:air-json:0.1.0-SNAPSHOT`.

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
`AirJson` em `air-json/`; WORK-CFG-026 introduziu o consumo em cfg-adapters e WORK-CFG-027 atualiza o pin para 4B.
O core pode criar apenas seus próprios tipos CFG, opções,
diagnósticos de consumer e índices derivados.

Reactor físico: `cfg-kernel` com domínio/aplicação separados por packages;
`cfg-adapters` com reader AIR via shared AirJson e writer CFG JSON explícito;
`cfg-launcher` com composition root/CLI. A decisão humana do 2B libera o binding
DRAFT pinado para experimento sem esperar promoção. Não há outras bibliotecas JSON
ou módulos novos. [Execução e exit codes](../../README.md#executar-air-json--cfg-json).

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
intermediário. O lowerer existe no baseline CP4; sua readiness para novos slices continua
dependência externa, sem autorizar mudanças upstream no CP5.

O reactor atual agrega exatamente kernel/adapters/launcher mantendo DAG. O kernel
continua compilável/testável isoladamente. O integration gate executa arquivos e CLI
e verifica três suítes nominais; não é o E2E com o lowerer. Container, REST/cloud e scheduler
continuam embalagens externas. A documentação Maven está nas [fontes](../sources/index.md).

## Pin 4D

O merge 4B acima substitui o pin 1A ativo. O teste escalar usa somente o golden
mergeado, cuja proveniência está em
[scalar-assign.provenance.json](../../cfg-adapters/src/test/resources/air/scalar-assign.provenance.json).
Resolução local deve manter repositórios Maven separados para o RED antigo e o
GREEN 4B, pois ambos usam coordenadas SNAPSHOT iguais. Instalar sempre a raiz do
upstream no SHA exato; não reutilizar artefato de origem desconhecida.

## CP5 futuro

[Contrato de módulos/packages](../architecture/cp5-dataflow.md) e [plano do gate](../evals/cp5/architecture.json). Todo módulo que importar tipos AIR declara air-java diretamente. Módulos de análise ausentes até a Wave autorizada; nenhum POM antecipado.
