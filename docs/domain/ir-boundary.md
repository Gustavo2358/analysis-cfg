# Fronteira de consumo da Analysis IR

## Autoridade e representação física

A semântica adotada é Analysis IR 2.0.0 no commit fixado em
[sources.lock.json](../sources/sources.lock.json). O [mapa de fontes](../sources/index.md)
indica os capítulos normativos. `air-java` é a implementação Java compartilhada
dessa revisão, não uma segunda autoridade.

O tipo recebido pelo consumer é exatamente
`io.github.gustavo2358.air.model.Publication`, do `air-java` no SHA fixado. Este
repo não redefine `Publication`, `Unit`, `Entry`, `Sequence`, `Operation`,
`Instruction`, `Terminator`, `TypeRef`, IDs, `Premise`, `DomainProofScope` ou outro
tipo pertencente à AIR.

`Publication` é fechada, imutável e independente do produtor. `Unit` admite
múltiplas Entry; cada `Sequence` tem `LabelId`, instructions ordenadas e exatamente
um `Terminator`. Nomes de display, linhas e posição na coleção não são chaves de
junção nem semântica.

## Modelo compartilhado observado

`air-java` materializa `TypeRef = Known(Type) | UnknownType(UncertaintyId)`,
premissas `sameDomain`, seus sujeitos, autoridade, origem e `DomainProofScope`.
Operações/terminadores são sealed. Uma extensão precisa nova pode exigir nova versão
da biblioteca; o consumer desconhecedor usa reduction, envelope ou incompatibilidade
conforme o contrato, nunca `Map<String,Object>` nem tipos AIR paralelos.

O CFG inicial não interpreta storage/values. Preserva operandos e metadata
pertinentes sem adulteração e aceita apenas o subset declarado. Capability fora do
subset não vira silenciosamente publicação menor. A quantidade de ocorrências não é
limitada pelo nome do capability.

## Validação e preflight

Toda via de entrada passa por `io.github.gustavo2358.air.validation.AirValidator`
no início do caso de uso. O consumer não duplica esse validator. O fluxo é:

```text
Publication → AirValidator → capability/version/options preflight → CFG
```

O validator cobre validade estrutural implementada: namespaces/identidades,
referências internas, Entry/initialLabel, terminador materializável, tipos, gaps e
provas aplicáveis, entre outras regras. `INVALID_IR` encerra o build; validação
incompleta permanece diagnóstico explícito. O CFG não recupera label por nome,
posição, frontend ou JSON.

`AirValidator` não prova que o lowerer preservou COBOL nem que o consumer projetou
controle corretamente. Evals CFG independentes continuam necessários e não geram
seus expected a partir do builder.

## Preservação e lifetime

Manter operações, ordem intrassequência, program points, origins/derivações,
identidade/revisão da publicação, capabilities, `TypeRef`, premises, escopos e
incertezas. Valores/storage abertos não autorizam apagar controle fechado.

O CFG não deep-copia a AIR inteira por padrão. Pode reter referências imutáveis e
IDs, mais índices derivados próprios, sempre registrando `PublicationId`, versão,
`UnitId`, entry scope e correlações necessárias. Não muta a publicação, exige JSON
para lifetime nem consulta o produtor de forma lazy.

## Publicação não é arquivo

O core não conhece encoding, schema JSON, bytes, diretório ou URL. O binding JSON
normativo pertence ao `analysis-ir` e ainda não existe no commit fixado. O futuro
reader é adapter do `analysis-cfg` que materializa `air-java Publication`; o modelo
compartilhado continua transport-independent. A notação dos exemplos e
`cobol-semantic-product.json` não são AIR JSON. Consultar
[portas](../architecture/ports-and-adapters.md) antes de criar adapter.
