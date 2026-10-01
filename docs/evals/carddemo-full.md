# CardDemo completo — benchmark JSON e Zstandard

**73 variantes × 2 repetições × 2 formatos = 292 execuções completas e 1.168 chamadas de CLI.**

Por processamento do corpus, os 657 arquivos JSON passaram de **2690.967 MB para 164.341 MB**, redução de **93.89%**. São bytes físicos de arquivos; MB = 1.000.000 bytes. Os aliases publicados estão incluídos; os logs e metadados do benchmark estão fora da soma.

A média dos dois totais do corpus passou de **543.655 s para 546.195 s**: **+2.540 s (+0.47%)**. Esse é o efeito líquido nas quatro CLIs, incluindo I/O, compressão, descompressão e inicialização da JVM.

4 arquivos pequenos cresceram com a compressão, somando 24 bytes adicionais. Esses casos estão incluídos nos totais e discriminados na tabela por arquivo.

## Cobertura e equivalência

- CardDemo fixado em `59cc6c2fd7ebd7ef7925cad552a01a4b8b6e4d5e`: todos os 44 fontes COBOL de `app/` e os 29 de `migrated_app/cbl/` do ZIP UniKix. Entradas `__MACOSX/._*` são metadados do ZIP e não programas COBOL.
- As variantes UniKix são reportadas separadamente, mesmo quando compartilham o nome do programa com a aplicação principal.
- 146 pares completos: 1.168 produtos byte-idênticos após descompressão independente por `zstd`; 146 manifests equivalentes após remover apenas `.zst` dos dois caminhos de snapshots.
- Todos os exit codes são zero. Os produtos de cada variante são determinísticos entre as duas rodadas. Os 73 fontes, 112 arquivos nas pastas de copybooks e jars congelados mantiveram seus hashes.
- Equivalência de transporte preserva as limitações semânticas existentes: não reclassifica `PARTIAL`, gaps ou remainder.

## Dados para análise

- [Por programa: tamanho total e tempo](carddemo-full-programs.csv) — 73 linhas, mediana, min/max e delta pareado.
- [Por programa e etapa](carddemo-full-stages.csv) — 292 linhas, mediana e min/max.
- [Por arquivo gerado](carddemo-full-artifacts.csv) — 657 linhas, nomes, bytes, redução e hashes lógicos.
- [Todas as medições de tempo](carddemo-full-runs.csv) — 1.168 linhas, tempo de parede, CPU, RSS e exit code.
- [Agregados e revisões](carddemo-full.measurements.json.zst).

O tempo é medido por CLI produtora; uma CLI pode produzir vários arquivos. O benchmark não atribui artificialmente um tempo separado a cada arquivo.

## Tempo total por rodada

| Rodada | JSON (s) | Zstandard (s) | Delta (s) | Delta |
| --- | ---: | ---: | ---: | ---: |
| 1 | 540.400 | 544.120 | +3.720 | +0.69% |
| 2 | 546.910 | 548.270 | +1.360 | +0.25% |

Tempo de CPU agregado (mediana): 1726.520 s → 1735.530 s. A CPU pode somar mais segundos que o tempo de parede porque a JVM usa várias threads. Mediana dos deltas pareados do corpus: +2.540 s.

## Tempo por etapa — média dos totais de cada rodada

| Etapa | JSON (s) | Zstandard (s) | Delta (s) | Delta |
| --- | ---: | ---: | ---: | ---: |
| frontend | 189.695 | 190.725 | +1.030 | +0.54% |
| lower | 215.050 | 214.565 | -0.485 | -0.23% |
| cfg | 46.075 | 47.040 | +0.965 | +2.09% |
| dependencies | 92.835 | 93.865 | +1.030 | +1.11% |

Com duas observações, média e mediana coincidem. Os campos `median` e `*_median_seconds` dos dados representam esse mesmo valor; min/max e as duas observações estão preservados.

## Tamanho por família de arquivo — uma rodada

| Produto | JSON (MB) | Zstandard (MB) | Redução |
| --- | ---: | ---: | ---: |
| cobol-semantic-product | 299.288 | 12.004 | 95.99% |
| cobol-semantic-compilation | 299.640 | 12.065 | 95.97% |
| observed-dependencies | 0.107 | 0.030 | 72.03% |
| semantic-product | 299.288 | 12.004 | 95.99% |
| air | 1122.468 | 76.370 | 93.20% |
| qualifiedSource | 49.454 | 2.465 | 95.02% |
| manifest | 1.454 | 0.198 | 86.39% |
| cfg | 57.186 | 4.352 | 92.39% |
| dependencies | 562.083 | 44.854 | 92.02% |

## Por programa — tempo médio de duas execuções

`core/` = `app/cbl/`; os diretórios de extensões têm o prefixo `app/app-` e `/cbl/` abreviados. `unikix/` = `samples/m2/unikix/UniKix_CardDemo_runtime_v1.zip!/migrated_app/cbl/`. Os CSVs contêm os caminhos completos.

| Programa | JSON (MB) | Zstd (MB) | Redução | JSON (s) | Zstd (s) | Delta tempo |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| authorization-ims-db2-mq/CBPAUP0C.cbl | 8.956 | 0.537 | 94.00% | 4.645 | 4.650 | +0.11% |
| authorization-ims-db2-mq/COPAUA0C.cbl | 101.329 | 5.397 | 94.67% | 12.690 | 12.710 | +0.16% |
| authorization-ims-db2-mq/COPAUS0C.cbl | 61.155 | 3.605 | 94.10% | 10.440 | 10.435 | -0.05% |
| authorization-ims-db2-mq/COPAUS1C.cbl | 33.606 | 1.896 | 94.36% | 7.635 | 7.755 | +1.57% |
| authorization-ims-db2-mq/COPAUS2C.cbl | 5.530 | 0.312 | 94.35% | 4.315 | 4.325 | +0.23% |
| authorization-ims-db2-mq/DBUNLDGS.CBL | 8.145 | 0.469 | 94.24% | 4.640 | 4.590 | -1.08% |
| authorization-ims-db2-mq/PAUDBLOD.CBL | 10.087 | 0.594 | 94.11% | 4.935 | 5.040 | +2.13% |
| authorization-ims-db2-mq/PAUDBUNL.CBL | 8.857 | 0.516 | 94.17% | 4.800 | 4.820 | +0.42% |
| transaction-type-db2/COBTUPDT.cbl | 4.970 | 0.289 | 94.19% | 4.140 | 4.165 | +0.60% |
| transaction-type-db2/COTRTLIC.cbl | 89.729 | 5.813 | 93.52% | 12.690 | 12.935 | +1.93% |
| transaction-type-db2/COTRTUPC.cbl | 43.426 | 2.667 | 93.86% | 8.675 | 8.855 | +2.07% |
| vsam-mq/COACCT01.cbl | 414.360 | 28.563 | 93.11% | 27.750 | 27.220 | -1.91% |
| vsam-mq/CODATE01.cbl | 259.185 | 17.158 | 93.38% | 19.500 | 19.350 | -0.77% |
| core/CBACT01C.cbl | 15.308 | 0.923 | 93.97% | 5.730 | 5.520 | -3.66% |
| core/CBACT02C.cbl | 4.687 | 0.286 | 93.89% | 4.175 | 4.320 | +3.47% |
| core/CBACT03C.cbl | 4.615 | 0.281 | 93.90% | 4.185 | 4.260 | +1.79% |
| core/CBACT04C.cbl | 22.641 | 1.383 | 93.89% | 6.385 | 6.410 | +0.39% |
| core/CBCUS01C.cbl | 5.166 | 0.307 | 94.05% | 4.180 | 4.300 | +2.87% |
| core/CBEXPORT.cbl | 20.785 | 1.228 | 94.09% | 5.965 | 6.040 | +1.26% |
| core/CBIMPORT.cbl | 17.708 | 1.035 | 94.15% | 5.800 | 5.935 | +2.33% |
| core/CBSTM03A.CBL | 27.059 | 1.331 | 95.08% | 6.940 | 6.980 | +0.58% |
| core/CBSTM03B.CBL | 4.900 | 0.274 | 94.40% | 4.195 | 4.185 | -0.24% |
| core/CBTRN01C.cbl | 17.590 | 1.043 | 94.07% | 5.870 | 5.840 | -0.51% |
| core/CBTRN02C.cbl | 25.275 | 1.564 | 93.81% | 6.485 | 6.615 | +2.00% |
| core/CBTRN03C.cbl | 24.315 | 1.483 | 93.90% | 6.725 | 6.705 | -0.30% |
| core/COACTUPC.cbl | 140.015 | 9.210 | 93.42% | 16.965 | 17.240 | +1.62% |
| core/COACTVWC.cbl | 43.115 | 2.490 | 94.22% | 8.950 | 8.955 | +0.06% |
| core/COADM01C.cbl | 22.528 | 1.157 | 94.86% | 6.370 | 6.400 | +0.47% |
| core/COBIL00C.cbl | 23.911 | 1.354 | 94.34% | 7.145 | 7.095 | -0.70% |
| core/COBSWAIT.cbl | 0.357 | 0.039 | 89.00% | 2.810 | 2.930 | +4.27% |
| core/COCRDLIC.cbl | 55.603 | 3.356 | 93.96% | 9.935 | 10.180 | +2.47% |
| core/COCRDSLC.cbl | 31.076 | 1.793 | 94.23% | 7.515 | 7.650 | +1.80% |
| core/COCRDUPC.cbl | 44.176 | 2.742 | 93.79% | 9.135 | 9.200 | +0.71% |
| core/COMEN01C.cbl | 24.591 | 1.230 | 95.00% | 6.540 | 6.595 | +0.84% |
| core/CORPT00C.cbl | 30.184 | 1.740 | 94.24% | 10.675 | 10.745 | +0.66% |
| core/COSGN00C.cbl | 15.714 | 0.824 | 94.76% | 5.610 | 5.735 | +2.23% |
| core/COTRN00C.cbl | 51.650 | 2.927 | 94.33% | 9.310 | 9.385 | +0.81% |
| core/COTRN01C.cbl | 23.006 | 1.203 | 94.77% | 6.670 | 6.765 | +1.42% |
| core/COTRN02C.cbl | 38.639 | 2.321 | 93.99% | 8.395 | 8.300 | -1.13% |
| core/COUSR00C.cbl | 51.405 | 2.903 | 94.35% | 9.435 | 9.335 | -1.06% |
| core/COUSR01C.cbl | 17.916 | 0.949 | 94.70% | 6.045 | 6.100 | +0.91% |
| core/COUSR02C.cbl | 21.226 | 1.166 | 94.51% | 6.680 | 6.795 | +1.72% |
| core/COUSR03C.cbl | 19.078 | 1.038 | 94.56% | 6.460 | 6.165 | -4.57% |
| core/CSUTLDTC.cbl | 3.285 | 0.198 | 93.98% | 3.885 | 3.930 | +1.16% |
| unikix/CBACT01C.cbl | 5.470 | 0.326 | 94.04% | 4.295 | 4.325 | +0.70% |
| unikix/CBACT02C.cbl | 4.675 | 0.285 | 93.90% | 4.165 | 4.220 | +1.32% |
| unikix/CBACT03C.cbl | 4.604 | 0.281 | 93.91% | 4.215 | 4.250 | +0.83% |
| unikix/CBACT04C.cbl | 22.622 | 1.382 | 93.89% | 6.360 | 6.390 | +0.47% |
| unikix/CBCUS01C.cbl | 5.152 | 0.306 | 94.06% | 4.160 | 4.270 | +2.64% |
| unikix/CBSTM03A.cbl | 26.075 | 1.259 | 95.17% | 6.745 | 6.855 | +1.63% |
| unikix/CBSTM03B.cbl | 4.900 | 0.274 | 94.40% | 4.065 | 4.170 | +2.58% |
| unikix/CBTRN01C.cbl | 17.566 | 1.042 | 94.07% | 5.820 | 5.845 | +0.43% |
| unikix/CBTRN02C.cbl | 25.255 | 1.563 | 93.81% | 6.535 | 6.625 | +1.38% |
| unikix/CBTRN03C.cbl | 24.289 | 1.483 | 93.90% | 6.905 | 6.730 | -2.53% |
| unikix/COACTUPC.cl2 | 139.286 | 9.174 | 93.41% | 17.880 | 17.245 | -3.55% |
| unikix/COACTVWC.cl2 | 42.974 | 2.491 | 94.20% | 8.925 | 9.205 | +3.14% |
| unikix/COADM01C.cl2 | 21.613 | 1.112 | 94.86% | 6.265 | 6.390 | +2.00% |
| unikix/COBIL00C.cl2 | 23.861 | 1.355 | 94.32% | 7.060 | 7.210 | +2.12% |
| unikix/COCRDLIC.cl2 | 55.450 | 3.358 | 93.94% | 10.085 | 10.055 | -0.30% |
| unikix/COCRDSLC.cl2 | 30.998 | 1.793 | 94.22% | 7.515 | 7.390 | -1.66% |
| unikix/COCRDUPC.cl2 | 44.105 | 2.741 | 93.79% | 9.070 | 9.095 | +0.28% |
| unikix/COMEN01C.cl2 | 23.296 | 1.189 | 94.90% | 6.355 | 6.480 | +1.97% |
| unikix/CORPT00C.cl2 | 30.120 | 1.742 | 94.22% | 10.615 | 10.625 | +0.09% |
| unikix/COSGN00C.cl2 | 15.668 | 0.824 | 94.74% | 5.515 | 5.585 | +1.27% |
| unikix/COTRN00C.cl2 | 51.473 | 2.930 | 94.31% | 9.310 | 9.570 | +2.79% |
| unikix/COTRN01C.cl2 | 22.930 | 1.205 | 94.75% | 6.640 | 6.665 | +0.38% |
| unikix/COTRN02C.cl2 | 38.560 | 2.324 | 93.97% | 8.210 | 8.505 | +3.59% |
| unikix/COUSR00C.cl2 | 51.229 | 2.907 | 94.33% | 9.565 | 9.355 | -2.20% |
| unikix/COUSR01C.cl2 | 17.866 | 0.949 | 94.69% | 5.935 | 6.165 | +3.88% |
| unikix/COUSR02C.cl2 | 21.176 | 1.167 | 94.49% | 6.655 | 6.685 | +0.45% |
| unikix/COUSR03C.cl2 | 19.031 | 1.038 | 94.54% | 6.305 | 6.255 | -0.79% |
| unikix/CSUTLDTC.cbl | 3.780 | 0.227 | 93.99% | 3.980 | 4.015 | +0.88% |
| unikix/SDSF.cbl | 0.086 | 0.017 | 80.67% | 2.485 | 2.560 | +3.02% |

## Código medido

| Repositório | Baseline | Candidato |
| --- | --- | --- |
| proleap-poc | `35ebc1a1485c08f6801f12f68a5b7456cdb51335` | `e6d1fa7f54bee07469bdb7ebd4a98701ca68419b` |
| cobol-lower | `f0ddfc8641b643998a92b4744142e41de0c7a113` | `4d7c7f29ae3c0824402f8fa18f32a3f36bd43101` |
| air-java | `6c4a6eb225fb4bb4fdc5871bc5232f03387ef099` | `6c4a6eb225fb4bb4fdc5871bc5232f03387ef099` |
| analysis-ir | `2c7f31f19efbe3211a2aea5bbda90173a9666fe2` | `2c7f31f19efbe3211a2aea5bbda90173a9666fe2` |
| analysis-cfg | `3df8cc8fcf4ac1de923a2a4711d99bc31cdf81d9` | `16cf3d5835809d855124c44132e0e47f2672badb` |

## Método e limites

- Mesmos runtimes congelados da campanha de implementação; baselines e candidatos identificados por SHA no agregado. Commits posteriores que alteram apenas documentação não mudam o código medido.
- Java 21.0.12.1, `-Xms256m -Xmx2g -XX:+UseG1GC`; AMD Ryzen 5 5600GT, 12 CPUs lógicas; Zstandard nível 3, checksum, uma thread.
- Execuções sequenciais: frontend → lower/bundle → CFG → dependências. Cada CLI inicia uma JVM. A ordem dos formatos alterna por programa e por rodada, reduzindo o viés de cache. Não houve build concorrente iniciado por este benchmark.
- Caches do sistema não foram esvaziados e não houve rodada de aquecimento separada. O host também executa aplicações de desktop. Duas repetições mostram a variabilidade local; não sustentam uma garantia geral de desempenho ou significância estatística.
- A métrica principal usa o tempo de parede do processo medido pelo GNU time (`%e`, resolução de 0,01 s), com CPU e pico RSS. O cronômetro externo Python também está preservado em `wrapper_wall_seconds`: inclui o pequeno atraso de observação do término do processo. Hashes, comparações, preparação, builds e relatório ficam fora dos tempos publicados.
- Conferência com o cronômetro externo: mediana do corpus 552.563 s → 555.335 s. As medições brutas originais não foram alteradas.
- A economia representa os nove artefatos JSON persistidos pelas quatro CLIs. Dataflow e relatório regional auxiliares não integram esta execução; seus testes focais constam da campanha anterior.
- O benchmark não executa as transações de negócio COBOL; mede o processamento de cada fonte pela pipeline de análise.

A execução foi iniciada com três rodadas; por solicitação do usuário, foi encerrada após salvar os 146 pares das duas rodadas completas. O registro de conclusão documenta esse ajuste. Nenhuma medição incompleta entra nas tabelas.

## Reprodução

No diretório local `artefatos-e2e/json-zstd-20261001/`, com os jars e entradas fixados:

```sh
python3 run-comparison.py carddemo-full-novo --benchmark \
  --selection carddemo-full-selection.json --repeats 2 --alternate-per-case
```

O diretório de destino deve ser novo. Logs, comandos completos, medições e produtos brutos desta execução estão preservados em `carddemo-full/`. A seleção e os hashes de copybooks estão em `carddemo-full-selection.json` e `carddemo-full-inputs.json.zst`. O repositório de evidências permanece local.
