# Regional analysis result — ST-W5.3

Status: contrato em implementação; não declara M3. Esta rota é separada do
resultado escalar [1.1.0](analysis-dataflow-result-v1.md), que permanece intacto.
O schema regional será fechado e testado por reader independente antes da
qualificação. Nenhuma declaração AIR é criada para atender uma consulta.

## Subject e resolução compartilhada

`StorageSubject.NamedObject(ObjectId)` consulta objeto/vista ou Cell lógica.
`StorageSubject.PhysicalRange(StorageId, StorageRange, Memory.Codec)` consulta
bytes com interpretação explicitamente escolhida. StorageRange usa BigInteger,
meio-aberto, unidade OCTET; fim ausente não é zero. A forma física exige Region,
nunca Cell, e deve caber no extent conhecido. Faixa aberta só cabe em extent
aberto. Zero length é distinto de unknown. A projeção pode recortar partições
existentes; não altera storage nem cria novas declarações, views ou ObjectIds.

StorageIndex é a autoridade compartilhada de resolução/admissão para RD e values.
A Unit da Entry deve existir. Objeto deve ser próprio ou explicitamente visível.
A faixa é acessível quando sua base pertence à Unit, é SHARED/EXTERNAL no modelo,
ou está contida numa vista própria/visível. Uma vista não concede acesso a bytes
fora de sua faixa. Base global PRIVATE sem vista visível não fica acessível apenas
porque seu StorageId existe. IDs, limites ou subjects incompatíveis produzem
UNSUPPORTED_SUBJECT, sem mutação de AIR, coerção para Cell ou valor vazio fechado.

`ReachingDefinitions.Execution.observeStorage` usa os mesmos estados estáveis e
BatchReplayer da API por ObjectId. `DefinitionFact` mantém sua forma, eventos,
intervalos sobreviventes, before/after/outcome e origem. O subject está na query.
Comparação de subjects usa owners completos, limites e codec discriminado; não
usa nome de exibição, toString de records ou ordinais internos. Cada logical
query é materializada uma vez, mesmo com pedidos duplicados.

## Próximas partes do contrato

O produto de values deve acrescentar alternativas da leitura e fragmentos com
intervalo contribuinte original, produtor literal, capturas e source gaps. O
resultado é destacado dos roots do solver. A projeção textual de CALL permanece
compatível e não afirma path witness. O wire regional-analysis-result 1.0.0 terá
campos fechados, IDs completos e intervalos decimais sem truncamento. O desenho
e os desafios estão registrados na evidência da campanha; a implementação do
writer só ocorrerá após fechar seu shape e os oracles independentes.
