# Baseline factual dos repositórios

Observada em 06/09/2026. Este documento é contexto de integração, não contrato CFG.
Revisões, coordenadas e estado verificável estão no [lock](sources.lock.json).

## Analysis IR

`Gustavo2358/analysis-ir/main` continua no merge canônico
`0b2fbce7046010b22b32efa8cbc3e75ccba09442`, Analysis IR **2.0.0**. Essa revisão
permanece a autoridade normativa deste projeto; nenhum `main` móvel a substitui.
Os blobs normativos continuam fixados individualmente no lock.

A busca no snapshot completo não encontrou diretório `bindings/`, schema nem binding
JSON oficial. Portanto, `json-v1` é trabalho upstream futuro do `analysis-ir`.
Exemplos `.air` não constituem esse binding, e sua ausência não bloqueia uma
`Publication` construída em memória.

## air-java

`Gustavo2358/air-java/main` foi verificado no commit
`2108294d9dfeb89d0019ce75fab27172b15a75b9`. O snapshot referencia exatamente a
AIR 2.0.0 normativa em `0b2fbce7046010b22b32efa8cbc3e75ccba09442`.

| Propriedade | Evidência observada |
| --- | --- |
| Maven | `io.github.gustavo2358:air-java:0.1.0-SNAPSHOT` no POM |
| Java | `maven.compiler.release=21`, sem preview |
| `Publication` | record público imutável em `io.github.gustavo2358.air.model`, com `id`, `airVersion`, capabilities, artifacts, units, storage, resources, relações, origins, coverage, uncertainties, premises e contracts |
| `Sequence` | `Sequence(LabelId, List<Instruction>, Terminator, OriginId)` |
| `Return` | `Operations.Return(Header, List<Expression>, List<EntryId> entryScope)` implementa `Terminator` |
| Validação | `AirValidator.validate(Publication)` e `validate(Publication, ValidationOptions)` retornam `ValidationResult` |
| Fronteira | `model` e `validation`; nenhum codec, filesystem, frontend ou CFG de produção |

Assinatura pública observada de `Publication`: `Publication(Ids.PublicationId id,
SemanticVersion airVersion, Capabilities.Manifest capabilities,
List<Origins.Artifact> artifacts, List<Unit> units, List<Memory.Storage> storage,
List<Interactions.Resource> resources, List<Artifacts.Relation> artifactRelations,
List<Origins.Origin> origins, Evidence.Coverage coverage,
List<Evidence.Uncertainty> uncertainties, List<Proofs.Premise> premises,
List<Interactions.Contract> contracts)`.

`Publication` e suas listas são snapshots imutáveis. `Operation`, `Instruction`,
`Terminator` e vários vocabulários são sealed; `Sequence` exige um terminador não
nulo e não oferece fallthrough intersequence. O validator verifica, entre outros
fatos, fechamento do `initialLabel` de uma Entry e referências internas.

O código de `src/main` não importa Jackson/Gson, `java.io`, `java.nio`, ProLeap,
ANTLR, Semantic Product, COBOL ou tipos CFG. O gate do próprio repositório confirmou
94 checks determinísticos e `jdeps` limitado a `java.base`; `mvn verify` também
passou nesta inspeção. O check remoto `contracts` do mesmo SHA terminou com
`success`. Esses resultados validam o modelo/validator do `air-java`, não a
interpretação futura do consumer CFG.

Limites documentados pelo próprio repositório permanecem explícitos: API inicial
`0.1.0-SNAPSHOT` ainda revisável, sem tag/release observada; nenhum binding/codec
JSON; validação incompleta para algumas precondições de memória/conversão,
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
