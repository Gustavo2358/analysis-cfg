# WORK-CFG-025 — Proveniência e validação

Baseline main limpa após `git pull --ff-only`: `141b8270f54558a24ee561281598e53c48a0ff6b`.
Branch própria: `chore/pin-air-java-1a`. Autorização e escopo no [manifesto](../../active/WORK-CFG-025/work-item.json).

O upstream autorizado para os próximos checkpoints é `air-java@b78f4068d8a479f48eb048b8d76fa60a0997dc4a`.
[PR upstream #5](https://github.com/Gustavo2358/air-java/pull/5): MERGED em 2026-09-07T21:30:12Z;
head `b8d0d953024dceb37782944886ac02d5ec6d16f6`, merge conforme o pin.
[contracts](https://github.com/Gustavo2358/air-java/actions/runs/34163367232/job/101869534574) e
[harness](https://github.com/Gustavo2358/air-java/actions/runs/34163367187) success no merge.

## Prova física e autoridade

[audit-provenance.py](audit-provenance.py) consulta blobs diretamente no objeto Git; [resultado completo](provenance.log)
lista todos os 21 paths do lock com SHA do blob e SHA-256. Também verifica 17 URLs ativas,
POMs parent/model/codec, pins CI, blocos normativos inalterados e 8 arquivos históricos intactos.
O SHA antigo no ADR-0007 continua explicitamente como evidência histórica da decisão original.
Mutantes de pin, topologia antiga e path ausente foram rejeitados; segundo GREEN e digest restaurado registrados.

Reprodução, a partir da raiz deste repo, com checkout temporário upstream no merge:

```sh
python3 docs/work/evidence/WORK-CFG-025/audit-provenance.py . /tmp/pin-air-java-1a-upstream 141b8270f54558a24ee561281598e53c48a0ff6b
```

O bloco `analysis_ir` inteiro e os demais sources foram comparados à baseline sem alterações.
Norma: `122ce54e1b9ef9b00646f93ece409ca8b63bc933`. O role estável do modelo permanece;
a seção `modules` distingue parent `air-java-parent`, modelo `air-java` e codec `air-json`.
O path do checkout CI permanece `air-java` porque o reactor deve ser instalado pela raiz.

## Gates executados

Ambiente: Temurin 21.0.12.1, Maven 3.9.16, Python 3.14; repo Maven isolado `/tmp/consumer-pinning/cfg-m2`.
Comandos abaixo exit 0; logs contêm os marcadores do output real e SHA-256 do log local integral.

| Comando / diretório | Resultado | Evidência |
| --- | --- | --- |
| `mvn -B -ntp -Dmaven.repo.local=/tmp/consumer-pinning/cfg-m2 clean install`, raiz upstream | Reactor completo BUILD SUCCESS | [upstream](upstream-install.log) |
| `python3 scripts/harness/cache_ir.py --from-dir /tmp/work-cfg-005-analysis-ir`, CFG | Cache dos blobs normativos verificado | [cache](cache.log) |
| `bash scripts/harness/check-fast.sh`, CFG | Docs + 41 testes do harness | [fast](fast.log) |
| `bash scripts/harness/check-architecture.sh`, CFG | 102 testes, zero skips; 22 classfiles Java 21; somente air-java/java.base | [architecture](architecture.log) |
| `bash scripts/harness/check-semantic.sh`, CFG | 84 métodos obrigatórios; zero skips | [semantic](semantic.log) |
| `mvn -B -ntp clean verify`, CFG | 102 testes; BUILD SUCCESS | [verify](verify.log) |

Os três últimos builds usam `MAVEN_OPTS=-Dmaven.repo.local=/tmp/consumer-pinning/cfg-m2`.
Performance/integration/full continuam indisponíveis pelo contrato do harness e não são gates deste checkpoint.
Não há MANIFEST.sha256 nem gate Git separado neste repo; o audit acima verifica scope e o manifesto passa no fast.
`git diff --check` e revisão integral do candidato completam a verificação Git local.

## Review local e limite humano

Self-review por Codex nesta tarefa: diff completo de código/documentos e todos os novos artefatos de evidência.
Nenhum POM, Java, teste de produto ou script do harness mudou. Nenhuma dependência air-json foi adicionada.
Nenhum finding bloqueante permanece; fontes históricas mantidas e descrições ativas coerentes com a modularização.
A CI do novo head será observada após push e registrada no PR, sem presumir resultado remoto neste commit.
Parar para review humano; sem merge/auto-merge. Próximos 2A/2B, CLI, AIR reader/writer, CFG JSON e E2E não iniciados.
