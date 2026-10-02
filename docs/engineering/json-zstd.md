# Artefatos JSON com Zstandard

Os adapters de arquivo aceitam JSON UTF-8 e frames Zstandard. Uma saída cujo
nome termina em `.json.zst` usa `zstd-jni` **1.5.7-20**, nível **3**, uma thread,
sem dicionário e com checksum. O formato é interoperável com `zstd -d -c`.
Um input `.zst` sem frame válido, truncado ou com checksum incorreto é rejeitado.
Frames com nome legado também são reconhecidos pelos bytes de identificação.

O frontend gera SP, compilação, observed dependencies e alias em `.json.zst`
por padrão. `--json-compression none` permite gerar JSON legível para diagnóstico.
Nas CLIs que recebem um destino explícito, use `.json.zst`; um destino `.json`
continua sendo um pedido explícito de saída legível. Schemas, fixtures, locks,
configurações e evidência histórica continuam legíveis e preservados.
Runners legados que produzem fixtures para oracles JSON solicitam `none`
explicitamente; a execução operacional acima usa os artefatos comprimidos.

A compressão ocorre na persistência. APIs de bytes/streams dos codecs continuam
representando JSON. `air-java` e a especificação `analysis-ir` não recebem
conhecimento de arquivo nem dependência nativa. As cópias pequenas de `JsonFiles`
pertencem aos adapters de cada produto; não criam dependência lateral entre apps
ou entre os adapters de CFG e de análise. Os núcleos continuam sem compressão.

## Identidade e publicação

- Hashes do manifest `dependency-input` e das correlações de SP/AIR são calculados
  sobre o JSON descomprimido, preservando identidade semântica e correlação.
- Nomes dos snapshots do bundle usam esses hashes e o sufixo selecionado.
- O manifest referencia explicitamente os arquivos comprimidos; sua publicação
  ocorre depois dos snapshots. Não há busca implícita por arquivos vizinhos.
- O recibo de entrega de dataflow continua vinculando os **bytes físicos** finais:
  hash e `resultBytesWritten` incluem o frame comprimido e seu checksum.
- Os destinos AIR, CFG, source, dependency e dataflow conservam seu protocolo
  de staging/finalização. O frame é fechado antes da publicação final.
- Limites explícitos de leitura AIR se aplicam aos bytes **descomprimidos**.
  Não são introduzidos novos limites semânticos ou de cardinalidade.
- Sem mudança de schema, versão, IDs, provenance, `PARTIAL`, gaps ou remainder.

## Execução

```sh
# O frontend publica cobol-semantic-product.json.zst por padrão.
# Use os classpaths runtime produzidos pelo Maven, que incluem zstd-jni.
java -cp "$FRONTEND_CP" io.github.gustavo2358.cobolexplorer.ExplorerMain \
  --source input.cbl --copybooks copybooks --output out/frontend
java -cp "$LOWER_CP" io.github.gustavo2358.lower.adapters.cli.CobolDependencyInput \
  out/frontend/cobol-semantic-product.json.zst out/dependency-input.json.zst
java -cp "$ANALYSIS_CP" io.github.gustavo2358.analysis.launcher.AnalysisDependencies \
  out/dependency-input.json.zst out/dependencies.json.zst
zstd -d -c out/dependencies.json.zst | python3 -m json.tool
```

A biblioteca inclui as bibliotecas nativas das plataformas suportadas pelo
[zstd-jni](https://github.com/luben/zstd-jni). O CLI `zstd` é útil para inspeção e
validação independente, mas não é invocado pelas aplicações Java.

## Qualificação e fechamento

Fechamento e merge autorizados em 2026-10-02 pelo
[PR #60](https://github.com/Gustavo2358/analysis-cfg/pull/60).
A ordem de integração é [frontend #80](https://github.com/Gustavo2358/proleap-poc/pull/80)
→ [lower #54](https://github.com/Gustavo2358/cobol-lower/pull/54)
→ [CFG #60](https://github.com/Gustavo2358/analysis-cfg/pull/60).
O estado efetivo da integração, o HEAD final e o merge SHA são registrados nos PRs.

FAST local: 675 métodos obrigatórios, zero skips; contratos wire e arquitetura passaram. A qualificação anterior é reutilizada neste fechamento documental;
código, testes, configuração de build e pins executáveis permanecem iguais.
Os checks documentais locais e o Fast CI nos HEADs finais validam o fechamento.

O [CardDemo completo](https://github.com/Gustavo2358/analysis-cfg/blob/4c1b8552e4368aac0254e2079dd8088b40f8fbc8/docs/evals/carddemo-full.md) cobre 73 variantes, duas execuções por formato:
657 artefatos por processamento, **2.690,967 MB → 164,341 MB (−93,89%)**.
Tempo médio do corpus: **543,655 s → 546,195 s (+0,47%)**. São medições locais
com duas repetições, sem garantia estatística de tempo em outras máquinas.
Os 146 pares preservaram **1.168 produtos byte a byte** após descompressão
independente e 146 manifests após retirar apenas `.zst` dos caminhos de snapshots.

A campanha também inclui 14 casos integrados e 12 verificações adversariais e de
relatórios auxiliares. `PARTIAL`, gaps, candidates, supports e provenance foram
preservados. O corpus completo de 560 casos e o full não foram executados nesta
campanha; a mudança de transporte foi coberta pelos FASTs e comparações diretas.
A evidência bruta permanece em `artefatos-e2e/json-zstd-20261001/`, somente local.

Os locks mantêm os commits completos usados na qualificação: frontend
`e6d1fa7f54bee07469bdb7ebd4a98701ca68419b` e lower
`4d7c7f29ae3c0824402f8fa18f32a3f36bd43101`, conforme a dependência de cada produto.
Esses commits são incorporados à main pelos merges, sem trocar o código medido.
AIR e sua especificação permanecem nos pins anteriores; não há publicação Maven.
