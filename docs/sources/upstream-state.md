# Baseline factual dos repositórios

Observada em 06/09/2026; apenas air-java revalidado em 07/09/2026. Este documento é contexto de integração, não contrato CFG.
Revisões, coordenadas e estado verificável estão no [lock](sources.lock.json).

## Analysis IR

`Gustavo2358/analysis-ir/main` foi confirmado no merge canônico
`122ce54e1b9ef9b00646f93ece409ca8b63bc933`, Analysis IR **2.0.0**. Essa revisão
permanece a autoridade normativa deste projeto; nenhum `main` móvel a substitui.
Os 23 blobs observados estão fixados individualmente no lock.

O snapshot contém o **Analysis IR JSON Binding 1.0.0** em
`bindings/json-v1.md`. Seu próprio cabeçalho diz **Status: DRAFT** e
**targets AIR 2.0.0**: o merge do PR #2 não o tornou `NORMATIVE`/`ACCEPTED`.
Versão de transporte e versão semântica continuam distintas. Este checkpoint não
implementa reader, writer, schema ou adapter JSON no `analysis-cfg`.

A reconciliação normativa acrescentou I-55–I-61 e O-86–O-91. Entre as regras que
afetam a boundary estão contrato materializado por site de `invoke`, targets
executáveis fechados, disjunção de storage universal, outcomes de invocação
distintos do envelope genérico e identidades/owners completos. São fatos da AIR,
não licença para duplicar seu modelo no consumer.

## air-java

O upstream air-java autorizado para os próximos checkpoints é
`b78f4068d8a479f48eb048b8d76fa60a0997dc4a`, merge real do PR #5 / 1A.
O snapshot referencia exatamente a AIR 2.0.0 normativa em
`122ce54e1b9ef9b00646f93ece409ca8b63bc933`.

| Propriedade | Evidência observada |
| --- | --- |
| Maven | `io.github.gustavo2358:air-java:0.1.0-SNAPSHOT` no POM |
| Java | `maven.compiler.release=21`, sem preview |
| `Publication` | record público imutável em `io.github.gustavo2358.air.model`, com `id`, `airVersion`, capabilities, artifacts, units, storage, resources, relações, origins, coverage, uncertainties e premises; sem `contracts[]` |
| `Sequence` | `Sequence(LabelId, List<Instruction>, Terminator, OriginId)` |
| `Return` | `Operations.Return(Header, List<Expression>)` implementa `Terminator`; não seleciona entries |
| Contrato de chamada | `ContractRef` é valor de autoridade/versão/evidência; assinatura externa, effects e outcomes são materializados no `invoke` |
| Target executável | soma fechada `InternalTarget | LiteralTarget | ComputedTarget`; `ResourceId` permanece declarativo |
| Assinatura | inventários/restantes de parâmetros e resultados independentes; posições e demais naturais AIR sem teto usam `BigInteger` |
| Controle incompleto | `InvocationOutcomes` é distinto de `ControlEnvelope` |
| Identidades | relações usam `ArtifactRelationId`; `OperandId` carrega owner completo de operação ou entry |
| Premissa | `DisjointStorage` é a forma normativa universal entre pelo menos duas bases distintas, sem escopo seletivo |
| Proveniência | localização por linha/coluna ou por offsets com unidade explícita, sem fabricar uma forma a partir da outra |
| Validação | `AirValidator.validate(Publication)` e `validate(Publication, ValidationOptions)` retornam `ValidationResult` |
| Fronteira | `air-model/`: model/validator no artefato `air-java`; `air-json/`: codec compartilhado 1A; parent `air-java-parent` |

Assinatura pública observada de `Publication`: `Publication(Ids.PublicationId id,
SemanticVersion airVersion, Capabilities.Manifest capabilities,
List<Origins.Artifact> artifacts, List<Unit> units, List<Memory.Storage> storage,
List<Interactions.Resource> resources, List<Artifacts.Relation> artifactRelations,
List<Origins.Origin> origins, Evidence.Coverage coverage,
List<Evidence.Uncertainty> uncertainties, List<Proofs.Premise> premises)`.

`Publication` e suas listas são snapshots imutáveis. `Operation`, `Instruction`,
`Terminator` e vários vocabulários são sealed; `Sequence` exige um terminador não
nulo e não oferece fallthrough intersequence. O validator verifica, entre outros
fatos, fechamento do `initialLabel` de uma Entry e referências internas.

O código de `air-model/src/main` não importa Jackson/Gson, `java.io`, `java.nio`, ProLeap,
ANTLR, Semantic Product, COBOL ou tipos CFG. A instalação antiga e seus 172 checks
pertencem à evidência histórica do bootstrap.
O merge 1A possui CI `contracts` e `harness` em success no SHA autorizado,
com [recibo de proveniência](../work/history/WORK-CFG-025.md) e
[URL do job contracts](https://github.com/Gustavo2358/air-java/actions/runs/34163367232/job/101869534574).
Esses resultados não certificam o consumer CFG. O codec `AirJson` existe em
`air-json/src/main/java/io/github/gustavo2358/air/json/AirJson.java`; a cobertura
é a 1A declarada upstream, sem habilitar reader, dependência air-json ou 2B aqui.

Limites documentados pelo próprio repositório permanecem explícitos: API inicial
`0.1.0-SNAPSHOT` ainda revisável, sem tag/release observada; nenhum codec ou
dependência JSON no modelo; validação incompleta para algumas precondições de memória/conversão,
extensões arbitrárias, verdade de premissas, cobertura do produtor e claims globais;
nenhuma certificação integral de perfil AIR. Nada disso impede o caso mínimo
estrutural `Entry → Sequence(Return)`; o preflight deve inspecionar o status e os
diagnósticos de `AirValidator`, não tratá-lo como selo de conformidade CFG.

## proleap-poc

`Gustavo2358/proleap-poc/main` foi observado em
`7a376f33f55127f53c63b86d3228671b9c6a348d`, merge do PR #30. O PR #27
materializou o Semantic Product, o PR #29 o auditou contra AIR 2.0.0 e o PR #30
arquivou esse discovery e explicitou a ownership cross-repo. O POM continua em
Java 17; isso não restringe consumers em JVM 21.

A boundary pública termina no **COBOL Semantic Product**. O frontend conhece COBOL,
produz fatos, coverage, gaps, provenance e binding nominal, e possui writer
determinístico de `cobol-semantic-product`. Esse JSON não é Analysis IR. Não há
dependência de `air-java` no POM nem produção de `Publication`/CFG no pipeline.

O audit atual também é claro sobre readiness: entrada executável e terminal
semantics ainda são trabalho de frontend; `GOBACK` permanece uma observação genérica,
não um `Return` AIR pronto. Essa lacuna pertence ao enriquecimento do Semantic
Product e ao lowerer, nunca ao `analysis-cfg`.

## cobol-lower

Não existe repositório `Gustavo2358/cobol-lower` no inventário atual do owner, nem
foi encontrado nome equivalente. O componente aparece somente como boundary/backlog
planejado no `proleap-poc`: deve consumir exclusivamente o COBOL Semantic Product,
depender de `air-java`, produzir `Publication` e não reabrir AST, símbolos,
resolvers, texto ou apresentação. Não há commit, API, gate ou readiness de
implementação a registrar.

## Pipeline e consequência para este projeto

```text
proleap-poc
    → COBOL Semantic Product
    → cobol-semantic-product.json
    → cobol-lower (planejado)
    → air-java Publication
    → analysis-cfg
    → CFG
```

O `analysis-cfg` pode implementar e testar `CFG-FIRST` antes do lowerer, construindo
uma `air-java Publication` diretamente em memória. A integração bilateral posterior
trata a disponibilidade do `cobol-lower` como dependência externa. Gaps upstream
não são reparados por nomes COBOL, JSON bruto, ordem física ou consultas ao
frontend.

Fontes e permalinks: [índice](index.md).
