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
