# Avaliação do discovery

## O que prova corretude

Lock e perfis correspondem ao merge canônico V2; toda decisão se sustenta no
requisito ou contrato IR; arquitetura permite executar porta com Publication sem
I/O. Não existe Java/POM após o checkpoint.

## Classes positivas

MVP linear/diamond/return/halt; `unknown(known(bool))` alimenta branch com os dois
destinos; read de fixture e chamada direta compartilham porta.

## Classes negativas

Path na porta; JSON/AST no core; `unknown_type` usado como bool;
`Optional<Type>` no lugar de `TypeRef`; `sameDomain` tratado como igualdade de
valores; `DomainProofScope` dinâmico; modelo IR duplicado; gate sem testes;
AIR-STRUCTURE@2 completo declarado pelo MVP.

## Classes ambíguas

Ownership de modelo, formato de transporte e representação contextual ainda precisam
de decisão. Nenhuma é resolvida por copiar snippets da conversa.

## Casos adversariais

Dois callsites no mesmo trecho; unknown scope com reentrada; mesmo local ID em duas
units; duas alternativas para o mesmo destino; `unknown(known(bool))` versus
`unknown_type`; interseção vazia de provas; falta de source normativa.

## Casos de regressão

Gates do harness continuam validando links/IDs/manifestos, lock V2, perfis @2 e
O-69-STRUCT–O-85-STRUCT; a matriz permanece sem claims implementados e a fase
global continua docs-only.

## Propriedades/relações metamórficas

A arquitetura precisa admitir permutation/alpha-renaming/arquivo-memória; as
asserções executáveis serão implementadas depois, não declaradas verdes agora.

## Expectativas de escala

Não introduzir design que exige enumerar paths ou replicar todo corpo por callsite.
Custo estrutural em input+output e custo contextual tratados separadamente.

Gates: fast. Os demais permanecem indisponíveis por ausência de implementação.
