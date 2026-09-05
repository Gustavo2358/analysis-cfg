# Fronteira de consumo da Analysis IR

## Autoridade

Versionamento inicial: Analysis IR 1.0.0 no commit fixado em
[sources.lock.json](../sources/sources.lock.json). O [mapa de fontes](../sources/index.md)
indica os capítulos normativos. Este documento é orientação do consumidor,
não uma nova versão da IR.

Publication é fechada, imutável e independente do produtor. Unit admite múltiplas
Entry; cada Sequence tem LabelId, operações comuns ordenadas e um terminador.
Operações e operandos têm identidades próprias. Nome de exibição, linha e posição
na coleção não são chaves de junção nem semântica.

## Modelo concreto compartilhado

Ainda não existe biblioteca de modelo neste pacote. Antes do código, escolher
ownership/coordenadas Maven do modelo IR e separar seu codec. Uma biblioteca comum
pode morar inicialmente em módulo isolado e ser movida depois, sem duplicar classes
no lowerer/CFG. O domínio pode depender desse contrato semântico puro; não pode
herdar classes AST/ProLeap ou DTOs de transporte.

O MVP não precisa interpretar storage/values. Deve preservar operandos/metadata
sem adulteração e validar as precondições estruturais do subset declarado.
Uma publicação com capability fora do subset não vira silenciosamente publicação
menor. O resultado declara o escopo aceito, incompatível ou conservador.

## Validações mínimas

Namespaces completos, unicidade por domínio, referências internas fechadas,
Entry apontando para label da Unit, terminador único, ausência de operação posterior,
assinatura dos control operands, destinos/casos válidos e discriminadores versionados.
Recurso externo é representação legítima própria, não referência interna pendente.
Tipo/semântica não verificável não recebe selo de validação completa.

## Preservação

Manter operações, ordenação intrassequência, program points before/after(outcome),
origens/derivações, identidade da publicação, capacidades, premissas e incertezas.
Valores, storage e efeitos abertos não obrigam apagar um controle já fechado.
`UNKNOWN` do target de chamada não autoriza remover o invoke.

## Publicação não é arquivo

O core não conhece encoding, schema JSON, bytes, diretório, URL ou objeto GitHub.
O codec de fixtures tem versão própria e testes de round-trip/equivalência.
A notação dos exemplos IR não é um formato de transporte contratado. Consultar
[portas](../architecture/ports-and-adapters.md) antes de criar adapter.
