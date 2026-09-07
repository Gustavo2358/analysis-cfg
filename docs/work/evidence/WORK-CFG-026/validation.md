# WORK-CFG-026 — Evidência 2B

## Baseline, autorização e autoria do oracle

Main limpa após fetch --all --prune, checkout main e pull --ff-only:
`b614712fda55fef12639cbe18fd90793faa1fb3b`. Ancestralidade do PR #9 comprovada;
merge confirmado via GitHub, sem fabricar review. 025 encerrado no histórico.
Branch própria `feat/air-json-cfg-cli`; autorização implementation nova explícita
para somente 2B. Roadmap pai foi lido e não editado; seus registros antigos de
execução não substituem o pedido atual nem autorizam flags CLI extras.

Commit `7d22315` registra contrato, fixture AIR, golden CFG, testes e RED antes do
writer/reader/launcher. [RED observado](../WORK-CFG-026-red.md).
A fixture AIR é o blob `8f852589b7e2286beee2c8569e348d2b56301dab` do repositório
Gustavo2358/air-java, commit `b78f4068d8a479f48eb048b8d76fa60a0997dc4a`, path
`air-json/src/test/resources/goback.canonical.json`, capturado com git show.
SHA-256 `fa299c2e5f3fae75afe365363b9f16925f0cfea591f631768ace82f0fb9a1075`.
[Proveniência local](../../../../cfg-adapters/src/test/resources/air/PROVENANCE.md).
Nenhum teste gera AIR com encode, copia input de 2A ou lê o lower.

Golden CFG escrito manualmente antes de produção, com Sequence(0)/Entry(1)/NormalExit(2),
ENTRY 1→0 e RETURN 0→2, activationEntry primary-entry e inventories PARTIAL.
[Oracle](../../../../cfg-adapters/src/test/resources/cfg/ORACLE.md) e
[bytes](../../../../cfg-adapters/src/test/resources/cfg/goback.manual.json).
Os mocks de envelopes não-CFG_BUILT testam somente dispatch de exit codes; a prova
de CFG e a recusa UNAVAILABLE passam pelo kernel real. A prova memória/arquivo
usa construção independente com os mesmos fatos de controle/coverage, sem alegar
igualdade dos fatos AIR omitidos no transporte CFG v1.

## Build e segundo GREEN

Upstream obtido em clone separado `/tmp/cfg-2b/air-java-pin`, checkout detached e
HEAD verificado no pin; nenhum checkout irmão foi modificado. Maven repository
`/tmp/cfg-2b/m2` começou vazio e recebeu somente o upstream autorizado via
`mvn -B -ntp -Dmaven.repo.local=/tmp/cfg-2b/m2 clean install` (exit 0).
Primeiro GREEN e challenges com Temurin 25 / release 21. Segundo GREEN e execução
operacional com **Temurin 21.0.12.1+1**, Maven 3.9.16, Python local 3.14.4.
CI mantém setup explícito Java 21 / Python 3.12 antes do install upstream.

No diretório analysis-cfg, com JAVA_HOME/PATH do JDK 21 e
`MAVEN_OPTS=-Dmaven.repo.local=/tmp/cfg-2b/m2`:

| Comando | Exit | Observação |
| --- | --- | --- |
| bash scripts/harness/check-docs.sh | 0 | lifecycle, manifestos, links e pins |
| bash scripts/harness/check-fast.sh | 0 | 47 testes do harness |
| bash scripts/harness/check-architecture.sh | 0 | 102 testes kernel + 31 transporte; 20 fontes/30 classfiles exatos; DAG, javap/jdeps, Java 21 |
| bash scripts/harness/check-semantic.sh | 0 | 84 métodos obrigatórios, 125 contracasos de reports |
| bash scripts/harness/check-integration.sh | 0 | 31 métodos/3 suítes nominais; 74 contracasos de reports; fluxo real |
| mvn -B -ntp clean verify | 0 | 133 testes Java, sem falhas/errors/skips |
| bash scripts/harness/check-performance.sh | 3 | UNAVAILABLE |
| bash scripts/harness/check-full.sh | 3 | fast/architecture/semantic passaram; parada em performance UNAVAILABLE |
| mvn -B -ntp package dependency:copy-dependencies -DincludeScope=runtime | 0 | preparação real da CLI, sem uber JAR |
| bash scripts/analysis-cfg cfg-adapters/src/test/resources/air/goback.canonical.json /tmp/cfg-2b/goback.cfg.json | 0 | fluxo completo local, sem stderr/stdout |
| cmp cfg-adapters/src/test/resources/cfg/goback.manual.json /tmp/cfg-2b/goback.cfg.json | 0 | golden byte a byte |
| git diff --check | 0 | higiene |

[Recibo dos comandos e hashes de fontes produtivas](local-validation.json).
Os logs locais ficam em `/tmp/cfg-2b/logs/final-*.log`; este recibo preserva comandos,
contagens, exits e limites em vez de versionar milhares de linhas de Maven/jdeps.
A finalização documental/manifesto repete fast e o check de scope após cada mudança.

## Challenges, restauração e escopo

Executado: `MAVEN_OPTS=-Dmaven.repo.local=/tmp/cfg-2b/m2 python3 scripts/project/challenge_transport.py --evidence /tmp/cfg-2b/challenges`.
17 mutações, todas exit 1 pelo detector esperado; runner final exit 0.
[Resultados, comandos e hashes de restauração](challenges.json). O executor exige
assertion failures (zero errors/skips) para mutações comportamentais, e mensagem do
gate para provas de arquitetura/inventário. Sempre restaura bytes em finally e
confirma igualdade integral. Segundo GREEN acima executado após restauração.

| Falha injetada | Prova RED |
| --- | --- |
| reader substitui decode por reconhecimento regex local | boundary exige shared AirJson.decode |
| kernel adiciona air-json | dependências exatas do kernel |
| kernel usa filesystem com nome totalmente qualificado, sem import | jdeps detecta dependência proibida |
| kernel importa JSON/Jackson | inventário de imports |
| não-CFG_BUILT publica arquivo | testes de recusa real/envelopes |
| PARTIAL mapeado para COMPLETE | golden manual e equivalência |
| remoção de ENTRY; remoção de RETURN | dois mutantes, golden/manual topology |
| activationEntry trocada | golden e topologia contextual |
| enum.name; enum.toString como wire | dois mutantes, boundary explícita |
| metadata de máquina adicionada | golden byte a byte |
| suíte de integração removida | Maven falha por zero testes; não PASS |
| suíte de integração skipped | reports nominais recusam skips |
| CLI retorna 0 na falha AIR | códigos/run/processo |
| CLI retorna 0 na recusa CFG | códigos e recusa real |
| destino tocado antes de bytes completos | sentinel preservado/limite de serialização |

Durante preparação dos challenges, foram corrigidos somente os classificadores do
runner: o primeiro filesystem sentinel foi pego pelo teste da porta antes do jdeps;
passou a método privado para exercer bytecode. Seleção Maven de suítes esbarrou no
failIfNoSpecifiedTests hardcoded do kernel; o runner passou a executar testes do
reactor sem relaxar o POM. Skip nominal foi corretamente pego por counts. Essas
iterações não foram declaradas prova concluída; o relatório final tem 17 RED reais.

Diff integral revisado: POMs/produção, testes/golden, gates/inventários/challenges,
CI e documentos/lifecycle. Ajustes de navegação preservam afirmações históricas.
Kernel inteiro idêntico ao baseline; BuildCfg/CfgBuildCoordinator/CfgGraph/CfgNode/
CfgTransition/ProjectionPolicy intactos. Não existe parser AIR próprio. Dois pins
preservados, binding DRAFT mantido e CFG JSON pertence a analysis-cfg. Nenhuma
escrita deste trabalho em repo irmão, branch 2A ou roadmap externo.
Scope e MANIFEST.sha256 são conferidos por `python3 scripts/project/check_scope.py`;
regeneração explícita somente depois de revisar diff: `--update-manifest`.

## PR e vínculo remoto factual

[PR #10](https://github.com/Gustavo2358/analysis-cfg/pull/10) aberto em 2026-09-07,
base main, branch feat/air-json-cfg-cli. Commit de implementação
`a3ff7c4e0408998ec6ca76a35c51436a16492e3a` publicado por push normal; `git ls-remote`
confirmou exatamente o mesmo SHA. Contrato/RED no commit anterior `7d22315`.
Consulta GitHub confirmou OPEN e autoMergeRequest null; nenhuma aprovação humana
foi inferida. O [CI push desse commit](https://github.com/Gustavo2358/analysis-cfg/actions/runs/34171200323)
concluiu SUCCESS. Isso é evidência remota distinta do segundo GREEN local acima.

Este vínculo documental exige novo commit e push. Fast, scope/manifest e diff check
são reexecutados; os hashes produtivos do recibo local permanecem iguais. A conclusão
dos checks no **head final** será registrada no corpo do PR com o SHA exato e links
aos runs. Não se usa o CI do commit anterior para declarar o head final verde.
Aguardar review humano; WORK-CFG-026 permanece ativo até encerramento autorizado.

## Limitações e checkpoint seguinte

Input limitado à cobertura 1A do codec pinado (16 MiB/depth 128); writer cobre os
quatro nós/seis transições atuais, output default 64 MiB. Sem CFG JSON reader,
promoção DRAFT, segunda implementação AIR ou interoperabilidade universal.
Fallback REPLACE_EXISTING não promete atomicidade nem durabilidade contra crash.
Coverage/inventory não certifica completude do grafo, precisão de dados ou perfil AIR.
Performance/full permanecem UNAVAILABLE; integration é executado obrigatoriamente
separado no CI. E2E com cobol-lower/2A, orquestrador, dataflow, possible values e
fact projection não foram iniciados. Próxima ação: review humano do PR próprio,
sem merge/auto-merge pelo agente.
